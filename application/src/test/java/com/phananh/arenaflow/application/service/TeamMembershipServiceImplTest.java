package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.dto.request.InviteMemberRequest;
import com.phananh.arenaflow.application.dto.response.InvitationResponse;
import com.phananh.arenaflow.application.dto.response.MemberResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.application.mapper.MembershipMapper;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import com.phananh.arenaflow.domain.projection.MemberView;
import com.phananh.arenaflow.application.service.impl.TeamMembershipServiceImpl;
import com.phananh.arenaflow.application.support.TeamGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamMembershipServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final MembershipMapper membershipMapper = Mappers.getMapper(MembershipMapper.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();

    private final UUID teamId = UUID.randomUUID();
    private final UUID captainId = UUID.randomUUID();
    private final UUID inviteeId = UUID.randomUUID();
    private final UUID invitationId = UUID.randomUUID();

    private TeamMembershipServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new TeamMembershipServiceImpl(userRepository, teamMemberRepository, retrier,
                membershipMapper, new PageRequestFactory(), new TeamGuard(teamRepository, teamMemberRepository));
        when(retrier.execute(any())).thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());
    }

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private Team team(Team.TeamStatus status) {
        Team team = withId(new Team(), teamId);
        team.setName("Team Alpha");
        team.setStatus(status);
        return team;
    }

    private TeamMember member(UUID id, UUID userId, TeamMember.Role role, TeamMember.Status status) {
        TeamMember member = withId(new TeamMember(), id);
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus(status);
        return member;
    }

    private void givenActiveTeamWithCaptain() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.ACTIVE)));
        when(teamRepository.findForInviteById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.ACTIVE)));
        when(teamMemberRepository.findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.of(member(UUID.randomUUID(), captainId, TeamMember.Role.CAPTAIN,
                        TeamMember.Status.ACTIVE)));
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void inviteMemberGuardsInOrder() {
        InviteMemberRequest request = new InviteMemberRequest(inviteeId);
        when(teamRepository.findForInviteById(teamId)).thenReturn(Optional.empty());
        assertError(() -> service.inviteMember(teamId, captainId, request), ErrorCode.TEAM_001);

        when(teamRepository.findForInviteById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.DISBANDED)));
        assertError(() -> service.inviteMember(teamId, captainId, request), ErrorCode.TEAM_016);

        when(teamRepository.findForInviteById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.INACTIVE)));
        assertError(() -> service.inviteMember(teamId, captainId, request), ErrorCode.TEAM_015);

        givenActiveTeamWithCaptain();
        assertError(() -> service.inviteMember(teamId, inviteeId, request), ErrorCode.TEAM_002);

        when(userRepository.findByIdAndDeletedAtIsNull(inviteeId)).thenReturn(Optional.empty());
        assertError(() -> service.inviteMember(teamId, captainId, request), ErrorCode.USER_001);

        when(userRepository.findByIdAndDeletedAtIsNull(inviteeId)).thenReturn(Optional.of(new User()));
        when(teamMemberRepository.existsByTeamIdAndUserIdAndStatusIn(eq(teamId), eq(inviteeId), anyCollection()))
                .thenReturn(true);
        assertError(() -> service.inviteMember(teamId, captainId, request), ErrorCode.TEAM_003);
        verify(teamMemberRepository, never()).saveAndFlush(any());
    }

    @Test
    void inviteMemberCreatesPendingInvitationAndMapsUniqueRaceToTeam003() {
        givenActiveTeamWithCaptain();
        when(userRepository.findByIdAndDeletedAtIsNull(inviteeId)).thenReturn(Optional.of(new User()));
        when(teamMemberRepository.saveAndFlush(any(TeamMember.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), invitationId));

        InvitationResponse response = service.inviteMember(teamId, captainId, new InviteMemberRequest(inviteeId));

        ArgumentCaptor<TeamMember> saved = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(TeamMember.Role.MEMBER);
        assertThat(saved.getValue().getStatus()).isEqualTo(TeamMember.Status.PENDING);
        assertThat(response.invitationId()).isEqualTo(invitationId);
        assertThat(response.teamName()).isEqualTo("Team Alpha");
        assertThat(response.status()).isEqualTo("PENDING");

        when(teamMemberRepository.saveAndFlush(any(TeamMember.class)))
                .thenThrow(new DataIntegrityViolationException("idx_team_member_pending_or_active"));
        assertError(() -> service.inviteMember(teamId, captainId, new InviteMemberRequest(inviteeId)),
                ErrorCode.TEAM_003);
    }

    @Test
    void acceptAndRejectGuardInOrder() {
        when(teamMemberRepository.findById(invitationId)).thenReturn(Optional.empty());
        assertError(() -> service.acceptInvitation(invitationId, inviteeId), ErrorCode.TEAM_004);
        assertError(() -> service.rejectInvitation(invitationId, inviteeId), ErrorCode.TEAM_004);

        when(teamMemberRepository.findById(invitationId)).thenReturn(Optional.of(
                member(invitationId, inviteeId, TeamMember.Role.MEMBER, TeamMember.Status.PENDING)));
        assertError(() -> service.acceptInvitation(invitationId, captainId), ErrorCode.TEAM_005);
        assertError(() -> service.rejectInvitation(invitationId, captainId), ErrorCode.TEAM_005);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.INACTIVE)));
        assertError(() -> service.acceptInvitation(invitationId, inviteeId), ErrorCode.TEAM_015);
        assertError(() -> service.rejectInvitation(invitationId, inviteeId), ErrorCode.TEAM_015);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.ACTIVE)));
        when(teamMemberRepository.findById(invitationId)).thenReturn(Optional.of(
                member(invitationId, inviteeId, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE)));
        assertError(() -> service.acceptInvitation(invitationId, inviteeId), ErrorCode.TEAM_006);
        assertError(() -> service.rejectInvitation(invitationId, inviteeId), ErrorCode.TEAM_006);
    }

    @Test
    void acceptActivatesWithJoinedAtAndReturnsMemberView() {
        TeamMember invitation = member(invitationId, inviteeId, TeamMember.Role.MEMBER, TeamMember.Status.PENDING);
        when(teamMemberRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.ACTIVE)));
        when(teamMemberRepository.findMemberViewById(invitationId)).thenReturn(Optional.of(
                projections.createProjection(MemberView.class, Map.of("memberId", invitationId,
                        "displayName", "Invitee", "userDeleted", false, "status", "ACTIVE"))));

        MemberResponse response = service.acceptInvitation(invitationId, inviteeId);

        assertThat(invitation.getStatus()).isEqualTo(TeamMember.Status.ACTIVE);
        assertThat(invitation.getJoinedAt()).isNotNull();
        verify(teamMemberRepository).saveAndFlush(invitation);
        assertThat(response.displayName()).isEqualTo("Invitee");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectMarksRejected() {
        TeamMember invitation = member(invitationId, inviteeId, TeamMember.Role.MEMBER, TeamMember.Status.PENDING);
        when(teamMemberRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.ACTIVE)));

        service.rejectInvitation(invitationId, inviteeId);

        assertThat(invitation.getStatus()).isEqualTo(TeamMember.Status.REJECTED);
        verify(teamMemberRepository).saveAndFlush(invitation);
    }

    @Test
    void listMembersRequiresTeam() {
        when(teamRepository.existsById(teamId)).thenReturn(false);
        assertError(() -> service.listMembers(teamId, 1, 20), ErrorCode.TEAM_001);

        when(teamRepository.existsById(teamId)).thenReturn(true);
        when(teamMemberRepository.findActiveMembers(eq(teamId), any())).thenReturn(new PageImpl<>(List.of(
                projections.createProjection(MemberView.class, Map.of("displayName", "A", "userDeleted", false))),
                PageRequest.of(0, 20), 1));

        PageResponse<MemberResponse> page = service.listMembers(teamId, 1, 20);

        assertThat(page.data()).extracting(MemberResponse::displayName).containsExactly("A");
        assertThat(page.pagination().totalItems()).isEqualTo(1);
    }

    @Test
    void leaveTeamGuardsAndMarksLeft() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());
        assertError(() -> service.leaveTeam(teamId, inviteeId), ErrorCode.TEAM_001);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.DISBANDED)));
        assertError(() -> service.leaveTeam(teamId, inviteeId), ErrorCode.TEAM_016);

        // Đội INACTIVE vẫn rời được: tạm ngưng hoạt động không giữ chân thành viên.
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.INACTIVE)));
        when(teamMemberRepository.findByTeamIdAndUserIdAndStatus(teamId, inviteeId, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.empty());
        assertError(() -> service.leaveTeam(teamId, inviteeId), ErrorCode.TEAM_010);

        when(teamMemberRepository.findByTeamIdAndUserIdAndStatus(teamId, captainId, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.of(member(UUID.randomUUID(), captainId, TeamMember.Role.CAPTAIN,
                        TeamMember.Status.ACTIVE)));
        assertError(() -> service.leaveTeam(teamId, captainId), ErrorCode.TEAM_009);

        TeamMember active = member(UUID.randomUUID(), inviteeId, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        when(teamMemberRepository.findByTeamIdAndUserIdAndStatus(teamId, inviteeId, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.of(active));

        service.leaveTeam(teamId, inviteeId);

        assertThat(active.getStatus()).isEqualTo(TeamMember.Status.LEFT);
        assertThat(active.getLeftAt()).isNotNull();
        verify(teamMemberRepository).saveAndFlush(active);
    }

    @Test
    void inviteMemberRunsInsideRetrierAndReadsTeamWithForceIncrementLock() {
        givenActiveTeamWithCaptain();
        when(userRepository.findByIdAndDeletedAtIsNull(inviteeId)).thenReturn(Optional.of(new User()));
        when(teamMemberRepository.saveAndFlush(any(TeamMember.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), invitationId));

        service.inviteMember(teamId, captainId, new InviteMemberRequest(inviteeId));

        verify(retrier).execute(any());
        verify(teamRepository).findForInviteById(teamId);
        verify(teamRepository, never()).findById(any());
    }

    @Test
    void acceptAndRejectRejectDisbandedTeamWithTeam016() {
        when(teamMemberRepository.findById(invitationId)).thenReturn(Optional.of(
                member(invitationId, inviteeId, TeamMember.Role.MEMBER, TeamMember.Status.PENDING)));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.DISBANDED)));

        assertError(() -> service.acceptInvitation(invitationId, inviteeId), ErrorCode.TEAM_016);
        assertError(() -> service.rejectInvitation(invitationId, inviteeId), ErrorCode.TEAM_016);
        verify(teamMemberRepository, never()).saveAndFlush(any());
    }

    @Test
    void leaveTeamReportsTeam016WhenMembershipClosedBecauseTeamWasDisbandedConcurrently() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(Team.TeamStatus.ACTIVE)));
        when(teamMemberRepository.findByTeamIdAndUserIdAndStatus(teamId, inviteeId, TeamMember.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(teamRepository.findCurrentStatusById(teamId)).thenReturn(Optional.of("DISBANDED"));

        assertError(() -> service.leaveTeam(teamId, inviteeId), ErrorCode.TEAM_016);
    }
}
