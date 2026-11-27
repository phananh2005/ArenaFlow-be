package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.WithdrawApplicationRequest;
import com.phananh.arenaflow.application.dto.response.ApplicationResponse;
import com.phananh.arenaflow.application.dto.response.ApprovalResponse;
import com.phananh.arenaflow.application.dto.response.WithdrawalResponse;
import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationApprovedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationRejectedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationSubmittedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationWithdrawnEvent;
import com.phananh.arenaflow.application.mapper.MercenaryMapper;
import com.phananh.arenaflow.application.service.impl.MercenaryApplicationServiceImpl;
import com.phananh.arenaflow.application.support.ConcurrencyConflictException;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.DataIntegrityViolationFixtures;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.chat.ChatRealtimeGateway;
import com.phananh.arenaflow.application.support.chat.RealtimeFrame;
import com.phananh.arenaflow.application.support.mercenary.MercenaryGuard;
import com.phananh.arenaflow.application.support.reputation.VerificationRefresher;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.entity.mercenary.MercenaryApplication;
import com.phananh.arenaflow.domain.entity.mercenary.MercenaryRecruitment;
import com.phananh.arenaflow.domain.projection.ApplicationContextView;
import com.phananh.arenaflow.domain.projection.ApplyContextView;
import com.phananh.arenaflow.domain.repository.ConversationRepository;
import com.phananh.arenaflow.domain.repository.MercenaryApplicationRepository;
import com.phananh.arenaflow.domain.repository.MercenaryRecruitmentRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MercenaryApplicationServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    private final MercenaryApplicationRepository applicationRepository = mock(MercenaryApplicationRepository.class);
    private final MercenaryRecruitmentRepository recruitmentRepository = mock(MercenaryRecruitmentRepository.class);
    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ReputationService reputationService = mock(ReputationService.class);
    private final VerificationRefresher verificationRefresher = mock(VerificationRefresher.class);
    private final ChatRealtimeGateway realtimeGateway = mock(ChatRealtimeGateway.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID callerId = UUID.randomUUID();
    private final UUID recruitmentId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final UUID matchId = UUID.randomUUID();

    private MercenaryApplicationServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        service = new MercenaryApplicationServiceImpl(applicationRepository, recruitmentRepository,
                conversationRepository, new MercenaryGuard(), reputationService, verificationRefresher,
                realtimeGateway, retrier, Mappers.getMapper(MercenaryMapper.class), new PageRequestFactory(),
                eventPublisher, clock);
        when(retrier.execute(eq(ErrorCode.MATCH_014), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(1)).get());
        when(applicationRepository.saveAndFlush(any(MercenaryApplication.class))).thenAnswer(invocation -> {
            MercenaryApplication application = invocation.getArgument(0);
            ReflectionTestUtils.setField(application, "id", applicationId);
            return application;
        });
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation conversation = invocation.getArgument(0);
            ReflectionTestUtils.setField(conversation, "id", conversationId);
            return conversation;
        });
    }

    @Test
    void applyGuardOrder() {
        assertApplyError(Map.of("callerDeleted", true, "recruitmentId", ""), ErrorCode.USER_001);
        assertApplyError(Map.of("recruitmentId", ""), ErrorCode.MERCENARY_001);
        assertApplyError(Map.of("effectiveStatus", "CLOSED", "gameStart", NOW), ErrorCode.MERCENARY_007);
        assertApplyError(Map.of("effectiveStatus", "FILLED", "gameStart", NOW), ErrorCode.MERCENARY_013);
        assertApplyError(Map.of("effectiveStatus", "FILLED", "teamStatus", "INACTIVE"), ErrorCode.MERCENARY_006);
        assertApplyError(Map.of("teamStatus", "INACTIVE", "callerMember", true), ErrorCode.MERCENARY_005);
        assertApplyError(Map.of("callerMember", true, "callerHasActive", true), ErrorCode.MERCENARY_010);
        assertApplyError(Map.of("callerHasActive", true, "callerOverlap", true), ErrorCode.MERCENARY_009);
        assertApplyError(Map.of("callerOverlap", true), ErrorCode.MERCENARY_011);
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void applyMapsDuplicateRaceToMercenary009() {
        when(applicationRepository.findApplyContext(recruitmentId, callerId, NOW))
                .thenReturn(Optional.of(applyContext(Map.of())));
        when(applicationRepository.saveAndFlush(any(MercenaryApplication.class)))
                .thenThrow(DataIntegrityViolationFixtures.violation("idx_mercenary_application_active"));

        assertError(() -> service.apply(recruitmentId, callerId), ErrorCode.MERCENARY_009);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void applyCreatesApplicationAndConversationBetweenTeamAndApplicant() {
        when(applicationRepository.findApplyContext(recruitmentId, callerId, NOW))
                .thenReturn(Optional.of(applyContext(Map.of())));

        ApplicationResponse response = service.apply(recruitmentId, callerId);

        ArgumentCaptor<Conversation> conversation = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(conversation.capture());
        Conversation saved = conversation.getValue();
        assertThat(saved.getContextType()).isEqualTo(Conversation.ContextType.MERCENARY_APPLICATION);
        assertThat(saved.getApplicationId()).isEqualTo(applicationId);
        assertThat(saved.getMatchId()).isEqualTo(matchId);
        assertThat(saved.getExternalMatchId()).isNull();
        assertThat(saved.getPartyAType()).isEqualTo(Conversation.PartyType.TEAM);
        assertThat(saved.getPartyAId()).isEqualTo(teamId);
        assertThat(saved.getPartyBType()).isEqualTo(Conversation.PartyType.USER);
        assertThat(saved.getPartyBId()).isEqualTo(callerId);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.recruitment().match().venue()).isNull();
        assertThat(response.recruitment().captainName()).isNull();
        verify(eventPublisher).publishEvent(any(MercenaryApplicationSubmittedEvent.class));
    }

    @Test
    void approveGuardOrder() {
        assertApproveError(Map.of("teamStatus", "INACTIVE", "callerCaptain", false), ErrorCode.TEAM_015);
        assertApproveError(Map.of("callerCaptain", false), ErrorCode.TEAM_002);
        assertApproveError(Map.of("recruitmentEffectiveStatus", "EXPIRED", "effectiveStatus", "REJECTED"),
                ErrorCode.MERCENARY_007);
        assertApproveError(Map.of("effectiveStatus", "WITHDRAWN", "gameStart", NOW), ErrorCode.MERCENARY_012);
        assertApproveError(Map.of("gameStart", NOW, "recruitmentEffectiveStatus", "FILLED"), ErrorCode.MERCENARY_013);
        assertApproveError(Map.of("recruitmentEffectiveStatus", "FILLED", "applicantMember", true),
                ErrorCode.MERCENARY_006);
        assertApproveError(Map.of("applicantMember", true, "applicantOverlap", true), ErrorCode.MERCENARY_010);
        assertApproveError(Map.of("applicantOverlap", true), ErrorCode.MERCENARY_011);
        verify(recruitmentRepository, never()).incrementApproved(any(), anyInt(), anyInt(), any());
    }

    @Test
    void approveLocksRecruitmentThenApplicationAndPushesFrame() {
        when(applicationRepository.findContext(applicationId, callerId, NOW))
                .thenReturn(Optional.of(context(Map.of("quantityNeeded", 2, "quantityApproved", 1))));
        when(recruitmentRepository.incrementApproved(recruitmentId, 1, 2, NOW)).thenReturn(1);
        when(applicationRepository.approveIfPending(applicationId, NOW)).thenReturn(1);

        ApprovalResponse response = service.approve(applicationId, callerId);

        var order = inOrder(recruitmentRepository, applicationRepository);
        order.verify(recruitmentRepository).incrementApproved(recruitmentId, 1, 2, NOW);
        order.verify(applicationRepository).approveIfPending(applicationId, NOW);
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.recruitment().status()).isEqualTo("FILLED");
        assertThat(response.recruitment().quantityApproved()).isEqualTo(2);
        verify(realtimeGateway).publishAfterCommit(conversationId, new RealtimeFrame.ApplicationUpdated(applicationId,
                MercenaryApplication.Status.APPROVED, recruitmentId, MercenaryRecruitment.Status.FILLED, 2));
        verify(eventPublisher).publishEvent(any(MercenaryApplicationApprovedEvent.class));
    }

    @Test
    void approveCasMissOnEitherRowSignalsRetry() {
        when(applicationRepository.findContext(applicationId, callerId, NOW)).thenReturn(Optional.of(context(Map.of())));
        when(recruitmentRepository.incrementApproved(any(), anyInt(), anyInt(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.approve(applicationId, callerId)).isInstanceOf(ConcurrencyConflictException.class);

        when(recruitmentRepository.incrementApproved(any(), anyInt(), anyInt(), any())).thenReturn(1);
        when(applicationRepository.approveIfPending(applicationId, NOW)).thenReturn(0);
        assertThatThrownBy(() -> service.approve(applicationId, callerId)).isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void rejectAllowsInactiveTeamAndPublishesCaptainCause() {
        when(applicationRepository.findContext(applicationId, callerId, NOW))
                .thenReturn(Optional.of(context(Map.of("teamStatus", "INACTIVE"))));
        when(applicationRepository.rejectIfPending(applicationId, NOW)).thenReturn(1);

        service.reject(applicationId, callerId);

        ArgumentCaptor<MercenaryApplicationRejectedEvent> event =
                ArgumentCaptor.forClass(MercenaryApplicationRejectedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().cause()).isEqualTo(MercenaryApplicationRejectedEvent.Cause.CAPTAIN);
        verify(realtimeGateway).publishAfterCommit(eq(conversationId), any(RealtimeFrame.ApplicationUpdated.class));
    }

    @Test
    void withdrawRequiresApplicantAndExpectedStatus() {
        when(applicationRepository.findContext(applicationId, callerId, NOW))
                .thenReturn(Optional.of(context(Map.of("callerApplicant", false))));
        assertError(() -> service.withdraw(applicationId, callerId, new WithdrawApplicationRequest("PENDING")),
                ErrorCode.MERCENARY_014);

        when(applicationRepository.findContext(applicationId, callerId, NOW))
                .thenReturn(Optional.of(context(Map.of("callerApplicant", true, "effectiveStatus", "APPROVED"))));
        assertError(() -> service.withdraw(applicationId, callerId, new WithdrawApplicationRequest("PENDING")),
                ErrorCode.MERCENARY_012);
    }

    @Test
    void withdrawPendingHasNoPenalty() {
        when(applicationRepository.findContext(applicationId, callerId, NOW))
                .thenReturn(Optional.of(context(Map.of("callerApplicant", true))));
        when(applicationRepository.withdrawPending(applicationId, NOW)).thenReturn(1);

        WithdrawalResponse response = service.withdraw(applicationId, callerId, new WithdrawApplicationRequest("PENDING"));

        assertThat(response.reliabilityPenalty()).isZero();
        assertThat(response.previousStatus()).isEqualTo("PENDING");
        verify(reputationService, never()).adjustUserReliability(any(), any());
        verify(recruitmentRepository, never()).decrementApproved(any(), any());
    }

    @Test
    void withdrawApprovedAfterStartOrCancelledIsRejected() {
        when(applicationRepository.findContext(applicationId, callerId, NOW)).thenReturn(Optional.of(context(Map.of(
                "callerApplicant", true, "effectiveStatus", "APPROVED", "gameCancelled", true))));
        assertError(() -> service.withdraw(applicationId, callerId, new WithdrawApplicationRequest("APPROVED")),
                ErrorCode.MERCENARY_004);

        when(applicationRepository.findContext(applicationId, callerId, NOW)).thenReturn(Optional.of(context(Map.of(
                "callerApplicant", true, "effectiveStatus", "APPROVED", "gameStart", NOW))));
        assertError(() -> service.withdraw(applicationId, callerId, new WithdrawApplicationRequest("APPROVED")),
                ErrorCode.MERCENARY_013);
    }

    @Test
    void withdrawApprovedPenalisesByTimeBandAndReopensFilledRecruitment() {
        when(applicationRepository.findContext(applicationId, callerId, NOW)).thenReturn(Optional.of(context(Map.of(
                "callerApplicant", true, "effectiveStatus", "APPROVED", "gameStart", NOW.plusHours(4),
                "recruitmentEffectiveStatus", "FILLED", "quantityNeeded", 2, "quantityApproved", 2))));
        when(recruitmentRepository.decrementApproved(recruitmentId, NOW)).thenReturn(1);
        when(applicationRepository.withdrawApproved(applicationId, NOW)).thenReturn(1);

        WithdrawalResponse response = service.withdraw(applicationId, callerId,
                new WithdrawApplicationRequest("APPROVED"));

        assertThat(response.reliabilityPenalty()).isEqualTo(3);
        assertThat(response.recruitment().status()).isEqualTo("OPEN");
        assertThat(response.recruitment().quantityApproved()).isEqualTo(1);
        verify(reputationService).adjustUserReliability(callerId, new ReliabilityChange(-3,
                ReliabilityReason.MERCENARY_WITHDRAWN_AFTER_APPROVED, applicationId, true));
        verify(verificationRefresher).refreshUsersAfterCommit(Set.of(callerId));
        ArgumentCaptor<MercenaryApplicationWithdrawnEvent> event =
                ArgumentCaptor.forClass(MercenaryApplicationWithdrawnEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().previousStatus()).isEqualTo(MercenaryApplication.Status.APPROVED);
        assertThat(event.getValue().cause()).isEqualTo(MercenaryApplicationWithdrawnEvent.Cause.APPLICANT);
    }

    @Test
    void withdrawApprovedCasMissRollsBackRecruitmentDecrement() {
        when(applicationRepository.findContext(applicationId, callerId, NOW)).thenReturn(Optional.of(context(Map.of(
                "callerApplicant", true, "effectiveStatus", "APPROVED"))));
        when(recruitmentRepository.decrementApproved(recruitmentId, NOW)).thenReturn(1);
        when(applicationRepository.withdrawApproved(applicationId, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.withdraw(applicationId, callerId, new WithdrawApplicationRequest("APPROVED")))
                .isInstanceOf(ConcurrencyConflictException.class);
        verify(reputationService, never()).adjustUserReliability(any(), any());
    }

    @Test
    void getApplicationRequiresParticipant() {
        when(applicationRepository.findContext(applicationId, callerId, NOW)).thenReturn(Optional.empty());
        assertError(() -> service.getApplication(applicationId, callerId), ErrorCode.MERCENARY_002);

        when(applicationRepository.findContext(applicationId, callerId, NOW))
                .thenReturn(Optional.of(context(Map.of("callerCaptain", false))));
        assertError(() -> service.getApplication(applicationId, callerId), ErrorCode.MERCENARY_014);

        when(applicationRepository.findContext(applicationId, callerId, NOW))
                .thenReturn(Optional.of(context(Map.of())));
        assertThat(service.getApplication(applicationId, callerId).recruitment().match().venue()).isNotNull();
    }

    private void assertApplyError(Map<String, Object> overrides, ErrorCode code) {
        when(applicationRepository.findApplyContext(recruitmentId, callerId, NOW))
                .thenReturn(Optional.of(applyContext(overrides)));
        assertError(() -> service.apply(recruitmentId, callerId), code);
    }

    private void assertApproveError(Map<String, Object> overrides, ErrorCode code) {
        when(applicationRepository.findContext(applicationId, callerId, NOW))
                .thenReturn(Optional.of(context(overrides)));
        assertError(() -> service.approve(applicationId, callerId), code);
    }

    private ApplyContextView applyContext(Map<String, Object> overrides) {
        Map<String, Object> values = new HashMap<>();
        values.put("callerDeleted", false);
        values.put("callerName", "Mercenary");
        values.put("recruitmentId", recruitmentId);
        values.put("status", "OPEN");
        values.put("effectiveStatus", "OPEN");
        values.put("teamId", teamId);
        values.put("matchId", matchId);
        values.put("position", "Tiền vệ");
        values.put("teamStatus", "ACTIVE");
        values.put("teamName", "FC A");
        values.put("source", "SYSTEM");
        values.put("gameStatus", "CONFIRMED");
        values.put("gameStart", NOW.plusDays(1));
        values.put("gameEnd", NOW.plusDays(1).plusHours(2));
        values.put("callerMember", false);
        values.put("callerHasActive", false);
        values.put("callerOverlap", false);
        values.putAll(overrides);
        if ("".equals(values.get("recruitmentId"))) {
            values.put("recruitmentId", null);
        }
        return projections.createProjection(ApplyContextView.class, values);
    }

    private ApplicationContextView context(Map<String, Object> overrides) {
        Map<String, Object> values = new HashMap<>();
        values.put("applicationId", applicationId);
        values.put("status", "PENDING");
        values.put("effectiveStatus", "PENDING");
        values.put("applicantId", callerId);
        values.put("applicantName", "Mercenary");
        values.put("appliedAt", NOW.minusDays(1));
        values.put("recruitmentId", recruitmentId);
        values.put("position", "Tiền vệ");
        values.put("recruitmentStatus", "OPEN");
        values.put("recruitmentEffectiveStatus", "OPEN");
        values.put("quantityNeeded", 3);
        values.put("quantityApproved", 1);
        values.put("matchId", matchId);
        values.put("source", "SYSTEM");
        values.put("gameStatus", "CONFIRMED");
        values.put("gameStart", NOW.plusDays(1));
        values.put("gameEnd", NOW.plusDays(1).plusHours(2));
        values.put("teamId", teamId);
        values.put("teamName", "FC A");
        values.put("teamStatus", "ACTIVE");
        values.put("captainName", "Captain");
        values.put("venueId", UUID.randomUUID());
        values.put("venueName", "Sân A");
        values.put("conversationId", conversationId);
        values.put("callerCaptain", true);
        values.put("callerApplicant", false);
        values.put("applicantMember", false);
        values.put("applicantOverlap", false);
        values.put("gameCancelled", false);
        values.put("gameEnded", false);
        values.putAll(overrides);
        return projections.createProjection(ApplicationContextView.class, values);
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
