package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamGuardTest {

    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamMemberRepository memberRepository = mock(TeamMemberRepository.class);
    private final TeamGuard guard = new TeamGuard(teamRepository, memberRepository);
    private final UUID teamId = UUID.randomUUID();

    private Team team(Team.TeamStatus status) {
        Team team = Team.builder()
                .status(status)
                .build();
        return team;
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void activeTeamStillRejectsMissingAndInactiveTeams() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());
        assertError(() -> guard.activeTeam(teamId), ErrorCode.TEAM_001);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.INACTIVE)));
        assertError(() -> guard.activeTeam(teamId), ErrorCode.TEAM_015);

        Team active = team(Team.TeamStatus.ACTIVE);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(active));
        assertThat(guard.activeTeam(teamId)).isSameAs(active);
    }

    @Test
    void activeTeamRejectsDisbandedTeamWithTeam016() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.DISBANDED)));

        assertError(() -> guard.activeTeam(teamId), ErrorCode.TEAM_016);
    }

    @Test
    void notDisbandedTeamRejectsMissingAndDisbandedButAllowsInactiveAndActive() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());
        assertError(() -> guard.notDisbandedTeam(teamId), ErrorCode.TEAM_001);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.DISBANDED)));
        assertError(() -> guard.notDisbandedTeam(teamId), ErrorCode.TEAM_016);

        Team inactive = team(Team.TeamStatus.INACTIVE);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(inactive));
        assertThat(guard.notDisbandedTeam(teamId)).isSameAs(inactive);

        Team active = team(Team.TeamStatus.ACTIVE);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(active));
        assertThat(guard.notDisbandedTeam(teamId)).isSameAs(active);
    }

    @Test
    void activeTeamForInviteReadsWithForceIncrementLockAndAppliesActiveTeamChecks() {
        when(teamRepository.findForInviteById(teamId)).thenReturn(Optional.empty());
        assertError(() -> guard.activeTeamForInvite(teamId), ErrorCode.TEAM_001);

        when(teamRepository.findForInviteById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.DISBANDED)));
        assertError(() -> guard.activeTeamForInvite(teamId), ErrorCode.TEAM_016);

        when(teamRepository.findForInviteById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.INACTIVE)));
        assertError(() -> guard.activeTeamForInvite(teamId), ErrorCode.TEAM_015);

        Team active = team(Team.TeamStatus.ACTIVE);
        when(teamRepository.findForInviteById(teamId)).thenReturn(Optional.of(active));
        assertThat(guard.activeTeamForInvite(teamId)).isSameAs(active);

        verify(teamRepository, never()).findById(any());
    }

    // Race: đội đọc ở Q1 còn ACTIVE, disband commit chen giữa → guard sau Q1 thất bại phải báo TEAM_016.

    @Test
    void disbandedOrRereadsCommittedStatus() {
        when(teamRepository.findCurrentStatusById(teamId)).thenReturn(Optional.of("DISBANDED"));
        assertThat(guard.disbandedOr(teamId, ErrorCode.TEAM_007).getErrorCode()).isEqualTo(ErrorCode.TEAM_016);

        when(teamRepository.findCurrentStatusById(teamId)).thenReturn(Optional.of("INACTIVE"));
        assertThat(guard.disbandedOr(teamId, ErrorCode.TEAM_007).getErrorCode()).isEqualTo(ErrorCode.TEAM_007);

        when(teamRepository.findCurrentStatusById(teamId)).thenReturn(Optional.empty());
        assertThat(guard.disbandedOr(teamId, ErrorCode.TEAM_007).getErrorCode()).isEqualTo(ErrorCode.TEAM_007);
    }

    @Test
    void requireCaptainReportsTeam016WhenTeamWasDisbandedConcurrently() {
        UUID callerId = UUID.randomUUID();
        when(memberRepository.findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.empty());

        when(teamRepository.findCurrentStatusById(teamId)).thenReturn(Optional.of("ACTIVE"));
        assertError(() -> guard.requireCaptain(teamId, callerId), ErrorCode.TEAM_002);

        when(teamRepository.findCurrentStatusById(teamId)).thenReturn(Optional.of("DISBANDED"));
        assertError(() -> guard.requireCaptain(teamId, callerId), ErrorCode.TEAM_016);
    }
}
