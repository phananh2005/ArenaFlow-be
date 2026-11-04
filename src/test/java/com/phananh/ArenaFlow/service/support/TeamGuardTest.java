package com.phananh.ArenaFlow.service.support;

import com.phananh.ArenaFlow.entity.team.Team;
import com.phananh.ArenaFlow.exception.BusinessException;
import com.phananh.ArenaFlow.exception.ErrorCode;
import com.phananh.ArenaFlow.repository.TeamMemberRepository;
import com.phananh.ArenaFlow.repository.TeamRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamGuardTest {

    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamGuard guard = new TeamGuard(teamRepository, mock(TeamMemberRepository.class));
    private final UUID teamId = UUID.randomUUID();

    private Team team(Team.TeamStatus status) {
        Team team = new Team();
        team.setStatus(status);
        return team;
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void findTeamReturnsTeamWhateverItsStatus() {
        Team inactive = team(Team.TeamStatus.INACTIVE);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(inactive));

        assertThat(guard.findTeam(teamId)).isSameAs(inactive);
    }

    @Test
    void findTeamRejectsMissingTeamWithTeam001() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertError(() -> guard.findTeam(teamId), ErrorCode.TEAM_001);
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
}
