package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.support.OptimisticLockRetrier;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.dto.request.CreateTeamRequest;
import com.phananh.arenaflow.application.dto.request.DisbandTeamRequest;
import com.phananh.arenaflow.application.dto.request.TransferCaptainRequest;
import com.phananh.arenaflow.application.dto.request.UpdateTeamRequest;
import com.phananh.arenaflow.application.dto.response.CaptainHistoryResponse;
import com.phananh.arenaflow.application.dto.response.CaptainTransferResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.TeamResponse;
import com.phananh.arenaflow.application.dto.response.TeamSummaryResponse;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamCaptainHistory;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.application.mapper.TeamMapper;
import com.phananh.arenaflow.domain.repository.TeamCaptainHistoryRepository;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import com.phananh.arenaflow.domain.projection.CaptainHistoryView;
import com.phananh.arenaflow.domain.projection.TeamDetailView;
import com.phananh.arenaflow.domain.projection.TeamSummaryView;
import com.phananh.arenaflow.application.service.impl.TeamServiceImpl;
import com.phananh.arenaflow.application.support.TeamGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final TeamCaptainHistoryRepository historyRepository = mock(TeamCaptainHistoryRepository.class);
    private final OptimisticLockRetrier retrier = mock(OptimisticLockRetrier.class);
    private final TeamMapper teamMapper = Mappers.getMapper(TeamMapper.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();

    private final UUID teamId = UUID.randomUUID();
    private final UUID captainId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();

    private TeamServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new TeamServiceImpl(userRepository, teamRepository, teamMemberRepository, historyRepository,
                retrier, teamMapper, new PageRequestFactory(), new TeamGuard(teamRepository, teamMemberRepository));
        when(retrier.execute(any())).thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), teamId));
    }

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private Team team(Team.TeamStatus status) {
        Team team = withId(new Team(), teamId);
        team.setName("Old");
        team.setStatus(status);
        return team;
    }

    private TeamMember member(UUID userId, TeamMember.Role role) {
        TeamMember member = withId(new TeamMember(), UUID.randomUUID());
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus(TeamMember.Status.ACTIVE);
        return member;
    }

    private TeamDetailView detail(String name) {
        return projections.createProjection(TeamDetailView.class,
                Map.of("teamId", teamId, "teamName", name, "skillLevels", "[]", "memberCount", 1L));
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void createTeamMakesCreatorActiveCaptainAndRecordsHistory() {
        User creator = withId(new User(), captainId);
        creator.setDisplayName("Captain A");
        when(userRepository.findByIdAndDeletedAtIsNull(captainId)).thenReturn(Optional.of(creator));

        TeamResponse response = service.createTeam(captainId,
                new CreateTeamRequest("Team Alpha", null, "desc", List.of("Chill")));

        ArgumentCaptor<Team> team = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(team.capture());
        assertThat(team.getValue().getName()).isEqualTo("Team Alpha");
        assertThat(team.getValue().getCreatedBy()).isEqualTo(captainId);
        assertThat(team.getValue().getSkillLevels()).containsExactly("Chill");
        ArgumentCaptor<TeamMember> member = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).save(member.capture());
        assertThat(member.getValue().getRole()).isEqualTo(TeamMember.Role.CAPTAIN);
        assertThat(member.getValue().getStatus()).isEqualTo(TeamMember.Status.ACTIVE);
        assertThat(member.getValue().getJoinedAt()).isEqualTo(member.getValue().getInvitedAt());
        ArgumentCaptor<TeamCaptainHistory> history = ArgumentCaptor.forClass(TeamCaptainHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getFromUserId()).isNull();
        assertThat(history.getValue().getToUserId()).isEqualTo(captainId);
        assertThat(response.teamId()).isEqualTo(teamId);
        assertThat(response.captainId()).isEqualTo(captainId);
        assertThat(response.captainName()).isEqualTo("Captain A");
        assertThat(response.memberCount()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void createTeamRequiresExistingUser() {
        when(userRepository.findByIdAndDeletedAtIsNull(captainId)).thenReturn(Optional.empty());

        assertError(() -> service.createTeam(captainId, new CreateTeamRequest("T", null, null, null)),
                ErrorCode.USER_001);
        verify(teamRepository, never()).save(any());
    }

    @Test
    void getTeamReturnsDetailOrTeam001() {
        when(teamRepository.findDetailById(teamId)).thenReturn(Optional.of(detail("Alpha")));
        assertThat(service.getTeam(teamId).teamName()).isEqualTo("Alpha");

        UUID missing = UUID.randomUUID();
        when(teamRepository.findDetailById(missing)).thenReturn(Optional.empty());
        assertError(() -> service.getTeam(missing), ErrorCode.TEAM_001);
    }

    @Test
    void listTeamsMapsFilterAndPagination() {
        TeamSummaryView row = projections.createProjection(TeamSummaryView.class,
                Map.of("teamId", teamId, "teamName", "Alpha", "skillLevels", "[]", "memberCount", 3L));
        when(teamRepository.findSummaries(isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(1, 1), 3));
        when(teamRepository.findSummaries(eq("ACTIVE"), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponse<TeamSummaryResponse> all = service.listTeams(TeamStatusFilter.ALL, 2, 1);

        assertThat(all.data()).extracting(TeamSummaryResponse::teamName).containsExactly("Alpha");
        assertThat(all.pagination().page()).isEqualTo(2);
        assertThat(all.pagination().pageSize()).isEqualTo(1);
        assertThat(all.pagination().totalItems()).isEqualTo(3);
        assertThat(all.pagination().totalPages()).isEqualTo(3);
        assertThat(service.listTeams(TeamStatusFilter.ACTIVE, 1, 20).data()).isEmpty();
    }

    @Test
    void updateTeamGuardsTeam001ThenTeam015ThenTeam002() {
        UpdateTeamRequest request = new UpdateTeamRequest("New", null, null, null);
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());
        assertError(() -> service.updateTeam(teamId, captainId, request), ErrorCode.TEAM_001);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.INACTIVE)));
        assertError(() -> service.updateTeam(teamId, captainId, request), ErrorCode.TEAM_015);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.ACTIVE)));
        when(teamMemberRepository.findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.of(member(captainId, TeamMember.Role.CAPTAIN)));
        assertError(() -> service.updateTeam(teamId, memberId, request), ErrorCode.TEAM_002);
        verify(teamRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateTeamAppliesPresentFieldsAndReplacesSkillLevels() {
        Team team = team(Team.TeamStatus.ACTIVE);
        team.setDescription("keep");
        team.setSkillLevels(List.of("Chill"));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.of(member(captainId, TeamMember.Role.CAPTAIN)));
        when(teamRepository.findDetailById(teamId)).thenReturn(Optional.of(detail(" New ".trim())));

        TeamResponse response = service.updateTeam(teamId, captainId, new UpdateTeamRequest(" New ", null, null, List.of()));

        assertThat(team.getName()).isEqualTo("New");
        assertThat(team.getDescription()).isEqualTo("keep");
        assertThat(team.getSkillLevels()).isEmpty();
        verify(teamRepository).saveAndFlush(team);
        assertThat(response.teamName()).isEqualTo("New");
    }

    @Test
    void transferCaptainGuardsInOrder() {
        TransferCaptainRequest toMember = new TransferCaptainRequest(memberId, "Bận");
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.INACTIVE)));
        assertError(() -> service.transferCaptain(teamId, captainId, toMember), ErrorCode.TEAM_015);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.ACTIVE)));
        when(teamMemberRepository.findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.of(member(captainId, TeamMember.Role.CAPTAIN)));
        assertError(() -> service.transferCaptain(teamId, memberId, toMember), ErrorCode.TEAM_002);
        assertError(() -> service.transferCaptain(teamId, captainId, new TransferCaptainRequest(captainId, null)),
                ErrorCode.TEAM_008);

        when(teamMemberRepository.findByTeamIdAndUserIdAndStatus(teamId, memberId, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.empty());
        assertError(() -> service.transferCaptain(teamId, captainId, toMember), ErrorCode.TEAM_007);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void transferCaptainSwapsRolesAndRecordsHistoryWithReason() {
        TeamMember oldCaptain = member(captainId, TeamMember.Role.CAPTAIN);
        TeamMember newCaptain = member(memberId, TeamMember.Role.MEMBER);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.ACTIVE)));
        when(teamMemberRepository.findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.of(oldCaptain));
        when(teamMemberRepository.findByTeamIdAndUserIdAndStatus(teamId, memberId, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.of(newCaptain));

        CaptainTransferResponse response = service.transferCaptain(teamId, captainId,
                new TransferCaptainRequest(memberId, "Bận công tác"));

        assertThat(oldCaptain.getRole()).isEqualTo(TeamMember.Role.MEMBER);
        assertThat(newCaptain.getRole()).isEqualTo(TeamMember.Role.CAPTAIN);
        verify(teamMemberRepository).saveAllAndFlush(List.of(oldCaptain, newCaptain));
        ArgumentCaptor<TeamCaptainHistory> history = ArgumentCaptor.forClass(TeamCaptainHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getFromUserId()).isEqualTo(captainId);
        assertThat(history.getValue().getToUserId()).isEqualTo(memberId);
        assertThat(history.getValue().getReason()).isEqualTo("Bận công tác");
        assertThat(response.fromUserId()).isEqualTo(captainId);
        assertThat(response.toUserId()).isEqualTo(memberId);
        assertThat(response.reason()).isEqualTo("Bận công tác");
        assertThat(response.transferredAt()).isEqualTo(history.getValue().getTransferredAt());
    }

    @Test
    void listCaptainHistoryRequiresTeamAndMapsPage() {
        when(teamRepository.existsById(teamId)).thenReturn(false);
        assertError(() -> service.listCaptainHistory(teamId, 1, 20), ErrorCode.TEAM_001);

        CaptainHistoryView row = projections.createProjection(CaptainHistoryView.class,
                Map.of("historyId", UUID.randomUUID(), "toUserName", "A", "toUserDeleted", false));
        when(teamRepository.existsById(teamId)).thenReturn(true);
        when(historyRepository.findHistory(eq(teamId), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        PageResponse<CaptainHistoryResponse> page = service.listCaptainHistory(teamId, 1, 20);

        assertThat(page.data()).extracting(CaptainHistoryResponse::toUserName).containsExactly("A");
        assertThat(page.pagination().totalItems()).isEqualTo(1);
    }

    // ---------- Giải tán đội ----------

    @Test
    void statusFilterSupportsDisbanded() {
        assertThat(TeamStatusFilter.valueOf("DISBANDED").toQueryValue()).isEqualTo("DISBANDED");
        assertThat(TeamStatusFilter.ALL.toQueryValue()).isNull();
    }

    @Test
    void disbandTeamGuardsTeam001ThenTeam016ThenTeam002() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());
        assertError(() -> service.disbandTeam(teamId, captainId, null), ErrorCode.TEAM_001);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.DISBANDED)));
        assertError(() -> service.disbandTeam(teamId, captainId, null), ErrorCode.TEAM_016);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.ACTIVE)));
        when(teamMemberRepository.findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.of(member(captainId, TeamMember.Role.CAPTAIN)));
        assertError(() -> service.disbandTeam(teamId, memberId, null), ErrorCode.TEAM_002);

        verify(teamRepository, never()).saveAndFlush(any());
        verify(teamMemberRepository, never()).saveAndFlush(any());
        verify(teamMemberRepository, never()).disbandRemaining(any(), any());
    }

    @Test
    void disbandTeamFromInactiveLeavesCaptainDisbandsTeamKeepsInactiveReasonThenClosesRemaining() {
        Team team = team(Team.TeamStatus.INACTIVE);
        team.setInactiveReason("Admin khoá");
        TeamMember captain = member(captainId, TeamMember.Role.CAPTAIN);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.of(captain));
        when(teamRepository.findDetailById(teamId)).thenReturn(Optional.of(detail("Old")));

        TeamResponse response = service.disbandTeam(teamId, captainId, new DisbandTeamRequest("  Hết người chơi  "));

        assertThat(captain.getStatus()).isEqualTo(TeamMember.Status.LEFT);
        assertThat(captain.getLeftAt()).isNotNull();
        assertThat(team.getStatus()).isEqualTo(Team.TeamStatus.DISBANDED);
        assertThat(team.getDisbandReason()).isEqualTo("Hết người chơi");
        assertThat(team.getDisbandedBy()).isEqualTo(captainId);
        assertThat(team.getDisbandedAt()).isNotNull();
        assertThat(team.getInactiveReason()).isEqualTo("Admin khoá");
        InOrder order = inOrder(teamMemberRepository, teamRepository);
        order.verify(teamMemberRepository).saveAndFlush(captain);
        order.verify(teamRepository).saveAndFlush(team);
        order.verify(teamMemberRepository).disbandRemaining(eq(teamId), any());
        order.verify(teamRepository).findDetailById(teamId);
        verify(retrier).execute(any());
        assertThat(response.teamId()).isEqualTo(teamId);
    }

    @Test
    void disbandTeamUsesDefaultReasonWhenBodyMissingReasonNullOrBlank() {
        when(teamMemberRepository.findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE))
                .thenAnswer(invocation -> Optional.of(member(captainId, TeamMember.Role.CAPTAIN)));
        when(teamRepository.findDetailById(teamId)).thenReturn(Optional.of(detail("Old")));

        for (DisbandTeamRequest request : new DisbandTeamRequest[]{null, new DisbandTeamRequest(null),
                new DisbandTeamRequest("   ")}) {
            Team team = team(Team.TeamStatus.ACTIVE);
            when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

            service.disbandTeam(teamId, captainId, request);

            assertThat(team.getDisbandReason()).isEqualTo("Đội trưởng giải tán đội");
        }
    }

    @Test
    void transferCaptainReportsTeam016WhenNewCaptainLeftBecauseTeamWasDisbandedConcurrently() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.ACTIVE)));
        when(teamMemberRepository.findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.of(member(captainId, TeamMember.Role.CAPTAIN)));
        when(teamMemberRepository.findByTeamIdAndUserIdAndStatus(teamId, memberId, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(teamRepository.findCurrentStatusById(teamId)).thenReturn(Optional.of("DISBANDED"));

        assertError(() -> service.transferCaptain(teamId, captainId, new TransferCaptainRequest(memberId, null)),
                ErrorCode.TEAM_016);
    }
}
