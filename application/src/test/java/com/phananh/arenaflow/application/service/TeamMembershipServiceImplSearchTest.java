package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.response.InvitationCandidatesResponse;
import com.phananh.arenaflow.application.dto.response.MyInvitationResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.mapper.MembershipMapper;
import com.phananh.arenaflow.application.service.impl.TeamMembershipServiceImpl;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.projection.InvitationCandidateView;
import com.phananh.arenaflow.domain.projection.MyInvitationView;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Tìm người để mời và lời mời của tôi (service, không DB). */
@ExtendWith(MockitoExtension.class)
class TeamMembershipServiceImplSearchTest {

    private static final int LIMIT = 21;

    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private TeamGuard teamGuard;
    @Spy
    private MembershipMapper membershipMapper = Mappers.getMapper(MembershipMapper.class);
    @Spy
    private PageRequestFactory pageRequestFactory = new PageRequestFactory();
    @InjectMocks
    private TeamMembershipServiceImpl service;

    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID teamId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    private InvitationCandidateView candidate(int index) {
        Map<String, Object> values = new HashMap<>();
        values.put("userId", UUID.randomUUID());
        values.put("displayName", "Người " + index);
        values.put("avatarUrl", null);
        values.put("membershipStatus", null);
        return projections.createProjection(InvitationCandidateView.class, values);
    }

    private List<InvitationCandidateView> candidates(int count) {
        return IntStream.range(0, count).mapToObj(this::candidate).toList();
    }

    private static ErrorCode errorCodeOf(Throwable error) {
        return ((BusinessException) error).getErrorCode();
    }

    @Test
    void phoneSearchChecksTeamThenCaptainThenSearchesNormalizedPhone() {
        when(userRepository.searchCandidatesByPhone(teamId, callerId, "0987654321", LIMIT)).thenReturn(candidates(1));

        InvitationCandidatesResponse response = service.searchInvitationCandidates(teamId, callerId, "+84 987 654 321");

        assertThat(response.items()).hasSize(1);
        assertThat(response.hasMore()).isFalse();
        InOrder order = inOrder(teamGuard, userRepository);
        order.verify(teamGuard).activeTeam(teamId);
        order.verify(teamGuard).requireCaptain(teamId, callerId);
        order.verify(userRepository).searchCandidatesByPhone(teamId, callerId, "0987654321", LIMIT);
    }

    @Test
    void nameSearchFoldsInDatabaseBeforeGuardsAndUsesPrefixTsQuery() {
        when(userRepository.foldName("Nguyễn An")).thenReturn("nguyen an");
        when(userRepository.searchCandidatesByName(teamId, callerId, "nguyen:* & an:*", "nguyen an", LIMIT))
                .thenReturn(candidates(3));

        InvitationCandidatesResponse response = service.searchInvitationCandidates(teamId, callerId, " Nguyễn An ");

        assertThat(response.items()).extracting("displayName").containsExactly("Người 0", "Người 1", "Người 2");
        assertThat(response.hasMore()).isFalse();
        InOrder order = inOrder(userRepository, teamGuard);
        order.verify(userRepository).foldName("Nguyễn An");
        order.verify(teamGuard).activeTeam(teamId);
        order.verify(teamGuard).requireCaptain(teamId, callerId);
        order.verify(userRepository).searchCandidatesByName(teamId, callerId, "nguyen:* & an:*", "nguyen an", LIMIT);
    }

    @Test
    void twentyOneRowsReturnTwentyItemsWithHasMore() {
        when(userRepository.foldName("an")).thenReturn("an");
        List<InvitationCandidateView> rows = candidates(21);
        when(userRepository.searchCandidatesByName(teamId, callerId, "an:*", "an", LIMIT)).thenReturn(rows);

        InvitationCandidatesResponse response = service.searchInvitationCandidates(teamId, callerId, "an");

        assertThat(response.items()).hasSize(20);
        assertThat(response.items().getLast().displayName()).isEqualTo("Người 19");
        assertThat(response.hasMore()).isTrue();
    }

    @Test
    void exactlyTwentyRowsHaveNoMore() {
        when(userRepository.foldName("an")).thenReturn("an");
        when(userRepository.searchCandidatesByName(teamId, callerId, "an:*", "an", LIMIT)).thenReturn(candidates(20));

        InvitationCandidatesResponse response = service.searchInvitationCandidates(teamId, callerId, "an");

        assertThat(response.items()).hasSize(20);
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void invalidPhoneFailsWithUser005WithoutAnyQuery() {
        assertThatThrownBy(() -> service.searchInvitationCandidates(teamId, callerId, "+84 12345"))
                .isInstanceOf(BusinessException.class)
                .extracting(TeamMembershipServiceImplSearchTest::errorCodeOf).isEqualTo(ErrorCode.USER_005);

        verifyNoInteractions(userRepository, teamGuard, teamMemberRepository);
    }

    @Test
    void shortFoldedNameFailsWithUser006AfterOnlyTheFoldQuery() {
        when(userRepository.foldName("Ă!")).thenReturn("a");

        assertThatThrownBy(() -> service.searchInvitationCandidates(teamId, callerId, "Ă!"))
                .isInstanceOf(BusinessException.class)
                .extracting(TeamMembershipServiceImplSearchTest::errorCodeOf).isEqualTo(ErrorCode.USER_006);

        verify(userRepository).foldName("Ă!");
        verifyNoInteractions(teamGuard, teamMemberRepository);
    }

    @Test
    void guardFailureStopsBeforeSearch() {
        when(userRepository.foldName("an")).thenReturn("an");
        when(teamGuard.requireCaptain(teamId, callerId)).thenThrow(new BusinessException(ErrorCode.TEAM_002));

        assertThatThrownBy(() -> service.searchInvitationCandidates(teamId, callerId, "an"))
                .extracting(TeamMembershipServiceImplSearchTest::errorCodeOf).isEqualTo(ErrorCode.TEAM_002);

        verify(userRepository).foldName("an");
        verify(teamGuard).activeTeam(teamId);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void myInvitationsRequiresActiveCallerAndMapsPage() {
        Map<String, Object> values = new HashMap<>();
        values.put("invitationId", UUID.randomUUID());
        values.put("invitedAt", LocalDateTime.of(2026, 11, 17, 19, 30));
        values.put("teamId", UUID.randomUUID());
        values.put("teamName", "Team Alpha");
        values.put("avatarUrl", null);
        values.put("skillLevels", "[]");
        values.put("status", "ACTIVE");
        values.put("captainName", "Hùng");
        values.put("captainDeleted", false);
        values.put("memberCount", 5L);
        MyInvitationView view = projections.createProjection(MyInvitationView.class, values);
        when(userRepository.existsByIdAndDeletedAtIsNull(callerId)).thenReturn(true);
        when(teamMemberRepository.findMyPendingInvitations(callerId, PageRequest.of(1, 10)))
                .thenReturn(new PageImpl<>(List.of(view), PageRequest.of(1, 10), 11));

        PageResponse<MyInvitationResponse> page = service.listMyInvitations(callerId, 2, 10);

        assertThat(page.data()).singleElement().satisfies(invitation -> {
            assertThat(invitation.team().teamName()).isEqualTo("Team Alpha");
            assertThat(invitation.invitedAt()).isEqualTo(LocalDateTime.of(2026, 11, 17, 19, 30));
        });
        assertThat(page.pagination().totalItems()).isEqualTo(11);
        assertThat(page.pagination().page()).isEqualTo(2);
    }

    @Test
    void myInvitationsOfDeletedCallerFailsWithUser001() {
        when(userRepository.existsByIdAndDeletedAtIsNull(callerId)).thenReturn(false);

        assertThatThrownBy(() -> service.listMyInvitations(callerId, 1, 20))
                .extracting(TeamMembershipServiceImplSearchTest::errorCodeOf).isEqualTo(ErrorCode.USER_001);

        verify(teamMemberRepository, never()).findMyPendingInvitations(any(), any());
    }
}
