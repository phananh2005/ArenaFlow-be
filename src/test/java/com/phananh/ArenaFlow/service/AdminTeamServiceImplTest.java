package com.phananh.ArenaFlow.service;

import com.phananh.ArenaFlow.common.OptimisticLockRetrier;
import com.phananh.ArenaFlow.common.PageRequestFactory;
import com.phananh.ArenaFlow.dto.request.MarkInactiveRequest;
import com.phananh.ArenaFlow.dto.response.AdminTeamSummaryResponse;
import com.phananh.ArenaFlow.dto.response.PageResponse;
import com.phananh.ArenaFlow.dto.response.TeamResponse;
import com.phananh.ArenaFlow.entity.team.Team;
import com.phananh.ArenaFlow.entity.team.TeamMember;
import com.phananh.ArenaFlow.exception.BusinessException;
import com.phananh.ArenaFlow.exception.ErrorCode;
import com.phananh.ArenaFlow.mapper.TeamMapper;
import com.phananh.ArenaFlow.repository.TeamMemberRepository;
import com.phananh.ArenaFlow.repository.TeamRepository;
import com.phananh.ArenaFlow.repository.projection.TeamDetailView;
import com.phananh.ArenaFlow.repository.projection.TeamSummaryView;
import com.phananh.ArenaFlow.service.impl.AdminTeamServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminTeamServiceImplTest {

    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final OptimisticLockRetrier retrier = mock(OptimisticLockRetrier.class);
    private final TeamMapper teamMapper = Mappers.getMapper(TeamMapper.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID teamId = UUID.randomUUID();

    private AdminTeamServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new AdminTeamServiceImpl(teamRepository, teamMemberRepository, retrier, teamMapper,
                new PageRequestFactory());
        when(retrier.execute(any())).thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());
        when(teamRepository.findDetailById(teamId)).thenReturn(Optional.of(projections.createProjection(
                TeamDetailView.class, Map.of("teamId", teamId, "skillLevels", "[]", "memberCount", 1L))));
    }

    private Team team(Team.TeamStatus status) {
        Team team = new Team();
        ReflectionTestUtils.setField(team, "id", teamId);
        team.setStatus(status);
        return team;
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void markInactiveGuardsTeam001AndTeam011() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());
        assertError(() -> service.markInactive(teamId, new MarkInactiveRequest("x")), ErrorCode.TEAM_001);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.INACTIVE)));
        assertError(() -> service.markInactive(teamId, new MarkInactiveRequest("x")), ErrorCode.TEAM_011);
        verify(teamRepository, never()).saveAndFlush(any());
    }

    @Test
    void markInactiveStoresTrimmedReason() {
        Team team = team(Team.TeamStatus.ACTIVE);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        TeamResponse response = service.markInactive(teamId, new MarkInactiveRequest("  Vi phạm  "));

        assertThat(team.getStatus()).isEqualTo(Team.TeamStatus.INACTIVE);
        assertThat(team.getInactiveReason()).isEqualTo("Vi phạm");
        verify(teamRepository).saveAndFlush(team);
        assertThat(response.teamId()).isEqualTo(teamId);
    }

    @Test
    void reactivateGuardsTeam001Team012Team013() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());
        assertError(() -> service.reactivate(teamId), ErrorCode.TEAM_001);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.ACTIVE)));
        assertError(() -> service.reactivate(teamId), ErrorCode.TEAM_012);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.INACTIVE)));
        when(teamMemberRepository.findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.empty());
        assertError(() -> service.reactivate(teamId), ErrorCode.TEAM_013);
        verify(teamRepository, never()).saveAndFlush(any());
    }

    @Test
    void reactivateClearsReasonWhenCaptainStillActive() {
        Team team = team(Team.TeamStatus.INACTIVE);
        team.setInactiveReason("Vi phạm");
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.of(new TeamMember()));

        service.reactivate(teamId);

        assertThat(team.getStatus()).isEqualTo(Team.TeamStatus.ACTIVE);
        assertThat(team.getInactiveReason()).isNull();
        verify(teamRepository).saveAndFlush(team);
    }

    @Test
    void listTeamsUsesAdminSummaryWithReason() {
        when(teamRepository.findSummaries(isNull(), any())).thenReturn(new PageImpl<>(List.of(
                projections.createProjection(TeamSummaryView.class, Map.of("teamId", teamId, "skillLevels", "[]",
                        "inactiveReason", "Vi phạm", "memberCount", 0L))), PageRequest.of(0, 20), 1));

        PageResponse<AdminTeamSummaryResponse> page = service.listTeams(TeamStatusFilter.ALL, 1, 20);

        assertThat(page.data()).extracting(AdminTeamSummaryResponse::inactiveReason).containsExactly("Vi phạm");
    }
}
