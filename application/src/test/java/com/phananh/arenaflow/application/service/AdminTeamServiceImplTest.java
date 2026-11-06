package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.dto.request.MarkInactiveRequest;
import com.phananh.arenaflow.application.dto.response.AdminTeamSummaryResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.TeamResponse;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.application.mapper.TeamMapper;
import com.phananh.arenaflow.application.mapper.MembershipMapper;
import com.phananh.arenaflow.application.dto.response.MemberHistoryResponse;
import com.phananh.arenaflow.domain.projection.MemberHistoryView;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import com.phananh.arenaflow.domain.projection.TeamDetailView;
import com.phananh.arenaflow.domain.projection.TeamSummaryView;
import com.phananh.arenaflow.application.service.impl.AdminTeamServiceImpl;
import com.phananh.arenaflow.application.support.TeamGuard;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminTeamServiceImplTest {

    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final TeamService teamService = mock(TeamService.class);
    private final TeamMapper teamMapper = Mappers.getMapper(TeamMapper.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID teamId = UUID.randomUUID();

    private AdminTeamServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new AdminTeamServiceImpl(teamRepository, teamMemberRepository, retrier, teamMapper,
                Mappers.getMapper(MembershipMapper.class), new PageRequestFactory(),
                new TeamGuard(teamRepository, teamMemberRepository), teamService);
        when(retrier.execute(any())).thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());
        when(teamService.getTeam(teamId)).thenReturn(teamMapper.toResponse(projections.createProjection(
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

    @Test
    void markInactiveAndReactivateRejectDisbandedTeamWithTeam016() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.DISBANDED)));

        assertError(() -> service.markInactive(teamId, new MarkInactiveRequest("x")), ErrorCode.TEAM_016);
        assertError(() -> service.reactivate(teamId), ErrorCode.TEAM_016);

        verify(teamRepository, never()).saveAndFlush(any());
        verify(teamMemberRepository, never()).findByTeamIdAndRoleAndStatus(any(), any(), any());
    }

    @Test
    void memberHistoryStatusFilterMapsToQueryValues() {
        assertThat(MemberHistoryStatusFilter.ALL.toQueryValues()).containsExactly("ACTIVE", "LEFT");
        assertThat(MemberHistoryStatusFilter.ACTIVE.toQueryValues()).containsExactly("ACTIVE");
        assertThat(MemberHistoryStatusFilter.LEFT.toQueryValues()).containsExactly("LEFT");
    }

    @Test
    void listMemberHistoryRequiresTeamThenMapsPageWithStatusFilter() {
        when(teamRepository.existsById(teamId)).thenReturn(false);
        assertError(() -> service.listMemberHistory(teamId, MemberHistoryStatusFilter.ALL, 1, 20), ErrorCode.TEAM_001);

        when(teamRepository.existsById(teamId)).thenReturn(true);
        MemberHistoryView view = projections.createProjection(MemberHistoryView.class, Map.of(
                "memberId", UUID.randomUUID(), "teamId", teamId, "userId", UUID.randomUUID(),
                "displayName", "Left Member", "userDeleted", false, "role", "MEMBER", "status", "LEFT"));
        when(teamMemberRepository.findMemberHistory(eq(teamId), eq(List.of("LEFT")), any()))
                .thenReturn(new PageImpl<>(List.of(view), PageRequest.of(1, 1), 3));

        PageResponse<MemberHistoryResponse> page = service.listMemberHistory(teamId, MemberHistoryStatusFilter.LEFT, 2, 1);

        assertThat(page.data()).singleElement().satisfies(item -> {
            assertThat(item.displayName()).isEqualTo("Left Member");
            assertThat(item.status()).isEqualTo("LEFT");
        });
        assertThat(page.pagination().totalItems()).isEqualTo(3);
    }

    @Test
    void reactivateReportsTeam016WhenCaptainLeftBecauseTeamWasDisbandedConcurrently() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.INACTIVE)));
        when(teamMemberRepository.findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(teamRepository.findCurrentStatusById(teamId)).thenReturn(Optional.of("DISBANDED"));

        assertError(() -> service.reactivate(teamId), ErrorCode.TEAM_016);
    }
}
