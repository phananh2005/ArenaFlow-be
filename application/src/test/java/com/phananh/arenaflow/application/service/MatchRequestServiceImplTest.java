package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.RejectMatchRequestRequest;
import com.phananh.arenaflow.application.dto.request.SendMatchRequestRequest;
import com.phananh.arenaflow.application.dto.request.WithdrawMatchRequestRequest;
import com.phananh.arenaflow.application.dto.response.AcceptMatchRequestResponse;
import com.phananh.arenaflow.application.dto.response.MatchRequestResponse;
import com.phananh.arenaflow.application.dto.response.MatchRequestWithdrawalResponse;
import com.phananh.arenaflow.application.event.match.MatchCancelledEvent;
import com.phananh.arenaflow.application.event.match.MatchConfirmedEvent;
import com.phananh.arenaflow.application.event.match.MatchRequestRejectedEvent;
import com.phananh.arenaflow.application.event.match.MatchRequestSentEvent;
import com.phananh.arenaflow.application.event.match.MatchRequestWithdrawnEvent;
import com.phananh.arenaflow.application.mapper.MatchMapper;
import com.phananh.arenaflow.application.service.impl.MatchRequestServiceImpl;
import com.phananh.arenaflow.application.support.CancellationResult;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.MatchCancellation;
import com.phananh.arenaflow.application.support.MatchExpiry;
import com.phananh.arenaflow.application.support.MatchGuard;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.match.MatchRequest;
import com.phananh.arenaflow.domain.projection.MatchAuthorAccessView;
import com.phananh.arenaflow.domain.projection.MatchAvailabilityView;
import com.phananh.arenaflow.domain.projection.MatchRequestContextView;
import com.phananh.arenaflow.domain.projection.TeamCaptainContextView;
import com.phananh.arenaflow.domain.repository.ConversationRepository;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import com.phananh.arenaflow.domain.repository.MatchRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static com.phananh.arenaflow.application.support.DataIntegrityViolationFixtures.violation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MatchRequestServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);

    private final MatchRepository matchRepository = mock(MatchRepository.class);
    private final MatchRequestRepository requestRepository = mock(MatchRequestRepository.class);
    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final TeamGuard teamGuard = mock(TeamGuard.class);
    private final MatchExpiry matchExpiry = mock(MatchExpiry.class);
    private final MatchCancellation matchCancellation = mock(MatchCancellation.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneId.systemDefault());
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID callerId = UUID.randomUUID();
    private final UUID authorTeamId = UUID.randomUUID();
    private final UUID requesterTeamId = UUID.randomUUID();
    private final UUID matchId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private MatchRequestServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new MatchRequestServiceImpl(matchRepository, requestRepository, conversationRepository,
                new MatchGuard(matchRepository), teamGuard, matchExpiry, matchCancellation, retrier,
                Mappers.getMapper(MatchMapper.class), new PageRequestFactory(), publisher, clock);
        when(retrier.execute(eq(ErrorCode.MATCH_014), any()))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(1)).get());
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    private Match openMatch() {
        return Match.builder().id(matchId).type(Match.Type.HAS_VENUE).status(Match.Status.OPEN)
                .authorTeamId(authorTeamId).matchStartTime(NOW.plusDays(1)).expiresAt(NOW.plusHours(3)).build();
    }

    private <T> T projection(Class<T> type, Map<String, Object> values) {
        return projections.createProjection(type, new HashMap<>(values));
    }

    private TeamCaptainContextView requesterContext() {
        return projection(TeamCaptainContextView.class, Map.of("status", "ACTIVE", "captain", true,
                "name", "Team Beta", "reliabilityScore", 75));
    }

    private MatchAvailabilityView availability(String authorStatus, boolean sportActive, UUID conversationId) {
        Map<String, Object> row = new HashMap<>(Map.of("authorTeamStatus", authorStatus, "sportActive", sportActive));
        row.put("existingConversationId", conversationId);
        return projection(MatchAvailabilityView.class, row);
    }

    private MatchRequestContextView context(String status, String matchStatus, LocalDateTime expiresAt) {
        return projection(MatchRequestContextView.class, Map.ofEntries(Map.entry("requestId", requestId),
                Map.entry("matchId", matchId), Map.entry("requesterTeamId", requesterTeamId),
                Map.entry("status", status), Map.entry("authorTeamId", authorTeamId),
                Map.entry("matchStatus", matchStatus), Map.entry("matchType", "HAS_VENUE"),
                Map.entry("expiresAt", expiresAt), Map.entry("matchStartTime", NOW.plusDays(1)),
                Map.entry("requesterTeamName", "Team Beta"), Map.entry("requesterTeamStatus", "ACTIVE"),
                Map.entry("requesterTeamReliabilityScore", 75), Map.entry("sportActive", true)));
    }

    // ---- Gửi yêu cầu (2.6) ----

    @Test
    void firstSendCreatesPendingRequestAndConversationThenBumpsMatchVersion() {
        Match match = openMatch();
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(teamGuard.requireActiveCaptain(requesterTeamId, callerId)).thenReturn(requesterContext());
        when(matchRepository.findAvailability(matchId, requesterTeamId)).thenReturn(availability("ACTIVE", true, null));
        when(requestRepository.saveAndFlush(any(MatchRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation conversation = invocation.getArgument(0);
            conversation.setId(conversationId);
            return conversation;
        });

        MatchRequestResponse response = service.sendRequest(matchId, callerId, new SendMatchRequestRequest(requesterTeamId));

        ArgumentCaptor<MatchRequest> request = ArgumentCaptor.forClass(MatchRequest.class);
        ArgumentCaptor<Conversation> conversation = ArgumentCaptor.forClass(Conversation.class);
        var order = inOrder(requestRepository, conversationRepository, matchRepository);
        order.verify(requestRepository).saveAndFlush(request.capture());
        order.verify(conversationRepository).save(conversation.capture());
        order.verify(matchRepository).forceIncrementVersion(match);
        assertThat(request.getValue().getStatus()).isEqualTo(MatchRequest.Status.PENDING);
        assertThat(request.getValue().getRequestedAt()).isEqualTo(NOW);
        assertThat(conversation.getValue().getContextType()).isEqualTo(Conversation.ContextType.TEAM_MATCH);
        assertThat(conversation.getValue().getPartyAId()).isEqualTo(authorTeamId);
        assertThat(conversation.getValue().getPartyBId()).isEqualTo(requesterTeamId);
        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.requesterTeamName()).isEqualTo("Team Beta");
        assertThat(response.startsAt()).isNull();
        verify(publisher).publishEvent(any(MatchRequestSentEvent.class));
    }

    @Test
    void resendReusesExistingConversation() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(openMatch()));
        when(teamGuard.requireActiveCaptain(requesterTeamId, callerId)).thenReturn(requesterContext());
        UUID existing = UUID.randomUUID();
        when(matchRepository.findAvailability(matchId, requesterTeamId)).thenReturn(availability("ACTIVE", true, existing));
        when(requestRepository.saveAndFlush(any(MatchRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchRequestResponse response = service.sendRequest(matchId, callerId, new SendMatchRequestRequest(requesterTeamId));

        assertThat(response.conversationId()).isEqualTo(existing);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void sendGuardsOwnTeamAuthorTeamSportAndDuplicate() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(openMatch()));
        assertError(() -> service.sendRequest(matchId, callerId, new SendMatchRequestRequest(authorTeamId)),
                ErrorCode.MATCH_007);

        when(teamGuard.requireActiveCaptain(requesterTeamId, callerId)).thenReturn(requesterContext());
        when(matchRepository.findAvailability(matchId, requesterTeamId)).thenReturn(availability("INACTIVE", true, null));
        assertError(() -> service.sendRequest(matchId, callerId, new SendMatchRequestRequest(requesterTeamId)),
                ErrorCode.MATCH_013);

        when(matchRepository.findAvailability(matchId, requesterTeamId)).thenReturn(availability("ACTIVE", false, null));
        assertError(() -> service.sendRequest(matchId, callerId, new SendMatchRequestRequest(requesterTeamId)),
                ErrorCode.SPORT_002);

        when(matchRepository.findAvailability(matchId, requesterTeamId)).thenReturn(availability("ACTIVE", true, null));
        when(requestRepository.saveAndFlush(any(MatchRequest.class))).thenThrow(violation("idx_match_request_active"));
        assertError(() -> service.sendRequest(matchId, callerId, new SendMatchRequestRequest(requesterTeamId)),
                ErrorCode.MATCH_006);
        verify(conversationRepository, never()).save(any());
        verify(matchRepository, never()).forceIncrementVersion(any());
    }

    @Test
    void sendOnExpiredOpenMatchExpiresIt() {
        Match match = openMatch();
        match.setExpiresAt(NOW);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(teamGuard.requireActiveCaptain(requesterTeamId, callerId)).thenReturn(requesterContext());

        assertError(() -> service.sendRequest(matchId, callerId, new SendMatchRequestRequest(requesterTeamId)),
                ErrorCode.MATCH_004);
        verify(matchExpiry).expireIfDue(matchId, authorTeamId, Match.Type.HAS_VENUE, NOW);
        verify(requestRepository, never()).saveAndFlush(any());
    }

    // ---- Xem yêu cầu của kèo (2.7) ----

    @Test
    void listMatchRequestsRequiresAuthorCaptain() {
        when(matchRepository.findAuthorAccess(matchId, callerId)).thenReturn(Optional.empty());
        assertError(() -> service.listMatchRequests(matchId, callerId, MatchRequestStatusFilter.PENDING, 1, 20),
                ErrorCode.MATCH_001);

        when(matchRepository.findAuthorAccess(matchId, callerId)).thenReturn(Optional.of(projection(
                MatchAuthorAccessView.class, Map.of("authorTeamId", authorTeamId, "authorTeamStatus", "DISBANDED",
                        "callerIsCaptain", false, "type", "HAS_VENUE"))));
        assertError(() -> service.listMatchRequests(matchId, callerId, MatchRequestStatusFilter.PENDING, 1, 20),
                ErrorCode.TEAM_016);

        when(matchRepository.findAuthorAccess(matchId, callerId)).thenReturn(Optional.of(projection(
                MatchAuthorAccessView.class, Map.of("authorTeamId", authorTeamId, "authorTeamStatus", "INACTIVE",
                        "callerIsCaptain", true, "type", "HAS_VENUE"))));
        when(requestRepository.findByMatch(matchId, true, NOW, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        assertThat(service.listMatchRequests(matchId, callerId, MatchRequestStatusFilter.PENDING, 1, 20).data()).isEmpty();
    }

    // ---- Chấp nhận (2.8) ----

    @Test
    void acceptConfirmsMatchAcceptsRequestAndSupersedesOthers() {
        Match match = openMatch();
        when(requestRepository.findContextById(requestId)).thenReturn(Optional.of(context("PENDING", "OPEN", NOW.plusHours(3))));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(requestRepository.acceptIfPending(requestId, NOW)).thenReturn(1);

        AcceptMatchRequestResponse response = service.acceptRequest(requestId, callerId);

        verify(teamGuard).requireActiveCaptain(authorTeamId, callerId);
        assertThat(match.getStatus()).isEqualTo(Match.Status.CONFIRMED);
        assertThat(match.getConfirmedOpponentTeamId()).isEqualTo(requesterTeamId);
        var order = inOrder(requestRepository);
        order.verify(requestRepository).acceptIfPending(requestId, NOW);
        order.verify(requestRepository).supersedePending(matchId, NOW);
        verify(publisher).publishEvent(new MatchConfirmedEvent(matchId, requestId, null, authorTeamId, requesterTeamId, NOW));
        assertThat(response).isEqualTo(new AcceptMatchRequestResponse(matchId, "CONFIRMED", requestId, "ACCEPTED",
                requesterTeamId, "Team Beta", NOW));
    }

    @Test
    void acceptLosingRaceOnRequestIsMatch008AndUniqueAcceptedIsMatch003() {
        when(requestRepository.findContextById(requestId)).thenReturn(Optional.of(context("PENDING", "OPEN", NOW.plusHours(3))));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(openMatch()));
        when(requestRepository.acceptIfPending(requestId, NOW)).thenReturn(0);
        assertError(() -> service.acceptRequest(requestId, callerId), ErrorCode.MATCH_008);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(openMatch()));
        when(requestRepository.acceptIfPending(requestId, NOW)).thenThrow(violation("idx_match_request_accepted"));
        assertError(() -> service.acceptRequest(requestId, callerId), ErrorCode.MATCH_003);
        verify(requestRepository, never()).supersedePending(any(), any());
    }

    @Test
    void acceptGuardsRequestStatusRequesterTeamAndSport() {
        when(requestRepository.findContextById(requestId)).thenReturn(Optional.empty());
        assertError(() -> service.acceptRequest(requestId, callerId), ErrorCode.MATCH_002);

        when(requestRepository.findContextById(requestId)).thenReturn(Optional.of(context("REJECTED", "OPEN", NOW.plusHours(3))));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(openMatch()));
        assertError(() -> service.acceptRequest(requestId, callerId), ErrorCode.MATCH_008);

        Match confirmed = openMatch();
        confirmed.setStatus(Match.Status.CONFIRMED);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(confirmed));
        assertError(() -> service.acceptRequest(requestId, callerId), ErrorCode.MATCH_003);
    }

    // ---- Từ chối (2.9) ----

    @Test
    void rejectPendingRequest() {
        when(requestRepository.findContextById(requestId)).thenReturn(Optional.of(context("PENDING", "OPEN", NOW.plusHours(3))));
        when(requestRepository.rejectIfPending(requestId, "Đã có lịch", NOW)).thenReturn(1);

        service.rejectRequest(requestId, callerId, new RejectMatchRequestRequest(" Đã có lịch "));

        verify(teamGuard).requireCaptainAllowInactive(authorTeamId, callerId);
        verify(publisher).publishEvent(new MatchRequestRejectedEvent(requestId, matchId, requesterTeamId, NOW));
    }

    @Test
    void rejectOnExpiredMatchIsMatch004NotMatch008() {
        when(requestRepository.findContextById(requestId)).thenReturn(Optional.of(context("EXPIRED", "EXPIRED", NOW.minusDays(1))));
        assertError(() -> service.rejectRequest(requestId, callerId, null), ErrorCode.MATCH_004);

        when(requestRepository.findContextById(requestId)).thenReturn(Optional.of(context("PENDING", "OPEN", NOW)));
        assertError(() -> service.rejectRequest(requestId, callerId, null), ErrorCode.MATCH_004);
        verify(matchExpiry).expireIfDue(matchId, authorTeamId, Match.Type.HAS_VENUE, NOW);
        verify(requestRepository, never()).rejectIfPending(any(), any(), any());
    }

    @Test
    void rejectAlreadyHandledIsMatch008() {
        when(requestRepository.findContextById(requestId)).thenReturn(Optional.of(context("PENDING", "OPEN", NOW.plusHours(3))));
        when(requestRepository.rejectIfPending(requestId, null, NOW)).thenReturn(0);

        assertError(() -> service.rejectRequest(requestId, callerId, null), ErrorCode.MATCH_008);
        verifyNoInteractions(publisher);
    }

    // ---- Rút (2.10) ----

    @Test
    void withdrawPendingKeepsMatchAndPublishesWithdrawnEvent() {
        when(requestRepository.findContextById(requestId)).thenReturn(Optional.of(context("PENDING", "OPEN", NOW.plusHours(3))));
        when(requestRepository.withdrawIfPending(requestId, null, NOW)).thenReturn(1);

        MatchRequestWithdrawalResponse response = service.withdrawRequest(requestId, callerId,
                new WithdrawMatchRequestRequest("PENDING", null));

        verify(teamGuard).requireCaptainAllowInactive(requesterTeamId, callerId);
        assertThat(response).isEqualTo(new MatchRequestWithdrawalResponse(requestId, matchId, "WITHDRAWN", NOW, "OPEN", 0));
        verify(publisher).publishEvent(new MatchRequestWithdrawnEvent(requestId, matchId, requesterTeamId, authorTeamId, NOW));
    }

    @Test
    void withdrawPendingThatChangedMeanwhileIsMatch015() {
        when(requestRepository.findContextById(requestId)).thenReturn(Optional.of(context("PENDING", "OPEN", NOW.plusHours(3))));
        when(requestRepository.withdrawIfPending(requestId, null, NOW)).thenReturn(0);

        assertError(() -> service.withdrawRequest(requestId, callerId, new WithdrawMatchRequestRequest("PENDING", null)),
                ErrorCode.MATCH_015);
    }

    @Test
    void withdrawComparesWithExpectedStatusAndRejectsHandledRequests() {
        when(requestRepository.findContextById(requestId)).thenReturn(Optional.of(context("ACCEPTED", "CONFIRMED", NOW.plusHours(3))));
        assertError(() -> service.withdrawRequest(requestId, callerId, new WithdrawMatchRequestRequest("PENDING", null)),
                ErrorCode.MATCH_015);

        when(requestRepository.findContextById(requestId)).thenReturn(Optional.of(context("REJECTED", "OPEN", NOW.plusHours(3))));
        assertError(() -> service.withdrawRequest(requestId, callerId, new WithdrawMatchRequestRequest("PENDING", null)),
                ErrorCode.MATCH_008);
        verifyNoInteractions(matchCancellation);
    }

    @Test
    void withdrawAcceptedCancelsMatchThroughSharedCancellation() {
        Match match = openMatch();
        match.setStatus(Match.Status.CONFIRMED);
        match.setConfirmedOpponentTeamId(requesterTeamId);
        when(requestRepository.findContextById(requestId)).thenReturn(Optional.of(context("ACCEPTED", "CONFIRMED", NOW.plusHours(3))));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(matchCancellation.cancelConfirmed(match, requesterTeamId, "Không đủ người",
                MatchCancelledEvent.Cause.REQUEST_WITHDRAWN, NOW)).thenReturn(new CancellationResult(-5));

        MatchRequestWithdrawalResponse response = service.withdrawRequest(requestId, callerId,
                new WithdrawMatchRequestRequest("ACCEPTED", " Không đủ người "));

        assertThat(response).isEqualTo(new MatchRequestWithdrawalResponse(requestId, matchId, "WITHDRAWN", NOW,
                "CANCELLED", -5));
        verify(publisher, never()).publishEvent(any(MatchRequestWithdrawnEvent.class));
    }

    @Test
    void withdrawAcceptedGuardsMatchStatusReasonAndStart() {
        Match match = openMatch();
        match.setStatus(Match.Status.CANCELLED);
        when(requestRepository.findContextById(requestId)).thenReturn(Optional.of(context("ACCEPTED", "CANCELLED", NOW.plusHours(3))));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        assertError(() -> service.withdrawRequest(requestId, callerId, new WithdrawMatchRequestRequest("ACCEPTED", "x")),
                ErrorCode.MATCH_005);

        match.setStatus(Match.Status.CONFIRMED);
        assertError(() -> service.withdrawRequest(requestId, callerId, new WithdrawMatchRequestRequest("ACCEPTED", " ")),
                ErrorCode.MATCH_011);

        match.setMatchStartTime(NOW);
        assertError(() -> service.withdrawRequest(requestId, callerId, new WithdrawMatchRequestRequest("ACCEPTED", "x")),
                ErrorCode.MATCH_012);
        verifyNoInteractions(matchCancellation);
    }

    // ---- Yêu cầu của đội (2.12) ----

    @Test
    void listTeamRequestsRequiresCaptainAllowingInactive() {
        when(requestRepository.findByRequesterTeam(requesterTeamId, null, NOW, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(service.listTeamRequests(requesterTeamId, callerId, MatchRequestStatusFilter.ALL, 1, 20).data()).isEmpty();
        verify(teamGuard).requireCaptainAllowInactive(requesterTeamId, callerId);
    }
}
