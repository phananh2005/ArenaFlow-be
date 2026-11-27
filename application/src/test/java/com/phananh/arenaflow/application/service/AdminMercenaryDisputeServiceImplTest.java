package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.ResolveDisputeRequest;
import com.phananh.arenaflow.application.dto.response.AdminMercenaryDisputeDetailResponse;
import com.phananh.arenaflow.application.event.reputation.DisputeResolvedEvent;
import com.phananh.arenaflow.application.mapper.MercenaryReputationMapper;
import com.phananh.arenaflow.application.service.impl.AdminMercenaryDisputeServiceImpl;
import com.phananh.arenaflow.application.support.AdminAuditLog;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.reputation.VerificationRefresher;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.projection.AdminMercenaryDisputeDetailView;
import com.phananh.arenaflow.domain.projection.ReportDecisionView;
import com.phananh.arenaflow.domain.repository.DisputeRepository;
import com.phananh.arenaflow.domain.repository.RatingRepository;
import com.phananh.arenaflow.domain.repository.ReportRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminMercenaryDisputeServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    private final DisputeRepository disputeRepository = mock(DisputeRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final RatingRepository ratingRepository = mock(RatingRepository.class);
    private final ReputationService reputationService = mock(ReputationService.class);
    private final VerificationRefresher verificationRefresher = mock(VerificationRefresher.class);
    private final AdminAuditLog auditLog = mock(AdminAuditLog.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID disputeId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID reportedId = UUID.randomUUID();
    private AdminMercenaryDisputeServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new AdminMercenaryDisputeServiceImpl(disputeRepository, reportRepository, ratingRepository,
                reputationService, verificationRefresher, auditLog, retrier,
                Mappers.getMapper(MercenaryReputationMapper.class), eventPublisher,
                Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
        when(retrier.execute(eq(ErrorCode.MATCH_014), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(1)).get());
        when(disputeRepository.findAdminMercenaryDisputeDetail(disputeId)).thenReturn(Optional.of(detail()));
        when(ratingRepository.findByApplicationIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
    }

    @Test
    void casMissDistinguishesMissingFromAlreadyResolved() {
        when(disputeRepository.resolveMercenaryIfPending(eq(disputeId), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(disputeRepository.existsMercenaryDispute(disputeId)).thenReturn(false);
        assertError(() -> service.resolveDispute(disputeId, adminId, new ResolveDisputeRequest("REJECTED", "x")),
                ErrorCode.DISPUTE_001);

        when(disputeRepository.existsMercenaryDispute(disputeId)).thenReturn(true);
        assertError(() -> service.resolveDispute(disputeId, adminId, new ResolveDisputeRequest("REJECTED", "x")),
                ErrorCode.DISPUTE_002);
        verify(reportRepository, never()).applyDecision(any(), any(), any());
    }

    @Test
    void rejectedIndividualReportRefundsMercenary() {
        when(disputeRepository.resolveMercenaryIfPending(disputeId, "REJECTED", "Sai", adminId, NOW))
                .thenReturn(Optional.of(reportId));
        when(reportRepository.applyDecision(reportId, "REJECTED", NOW)).thenReturn(Optional.of(decision("INDIVIDUAL")));

        AdminMercenaryDisputeDetailResponse response = service.resolveDispute(disputeId, adminId,
                new ResolveDisputeRequest("REJECTED", "Sai"));

        verify(reputationService).refundUserReportPenalty(reportedId, Report.ReportType.NOSHOW, 5, reportId);
        verify(verificationRefresher).refreshUsersAfterCommit(Set.of(reportedId));
        verify(auditLog).afterCommit("RESOLVE_DISPUTE", adminId, "DISPUTE", disputeId);
        verify(eventPublisher).publishEvent(any(DisputeResolvedEvent.class));
        assertThat(response.disputeId()).isEqualTo(disputeId);
    }

    @Test
    void rejectedTeamReportRefundsTeam() {
        when(disputeRepository.resolveMercenaryIfPending(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(reportId));
        when(reportRepository.applyDecision(reportId, "REJECTED", NOW)).thenReturn(Optional.of(decision("TEAM")));

        service.resolveDispute(disputeId, adminId, new ResolveDisputeRequest("REJECTED", "Sai"));

        verify(reputationService).refundReportPenalty(reportedId, Report.ReportType.NOSHOW, 5, reportId);
        verify(verificationRefresher).refreshTeamsAfterCommit(Set.of(reportedId));
    }

    @Test
    void confirmedKeepsPenalty() {
        when(disputeRepository.resolveMercenaryIfPending(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(reportId));
        when(reportRepository.applyDecision(reportId, "CONFIRMED", NOW)).thenReturn(Optional.of(decision("TEAM")));

        service.resolveDispute(disputeId, adminId, new ResolveDisputeRequest("CONFIRMED", "Đúng"));

        verify(reputationService, never()).refundReportPenalty(any(), any(), anyInt(), any());
        verify(reputationService, never()).refundUserReportPenalty(any(), any(), anyInt(), any());
    }

    @Test
    void detailOutsideMercenaryQueueIsNotFound() {
        UUID other = UUID.randomUUID();
        when(disputeRepository.findAdminMercenaryDisputeDetail(other)).thenReturn(Optional.empty());

        assertError(() -> service.getDispute(other), ErrorCode.DISPUTE_001);
    }

    private ReportDecisionView decision(String reportedType) {
        return projections.createProjection(ReportDecisionView.class, Map.of("reportedType", reportedType,
                "reportedId", reportedId, "reporterType", "TEAM".equals(reportedType) ? "INDIVIDUAL" : "TEAM",
                "reporterId", UUID.randomUUID(), "reportType", "NOSHOW", "reliabilityPenalty", 5));
    }

    private AdminMercenaryDisputeDetailView detail() {
        Map<String, Object> values = new HashMap<>();
        values.put("disputeId", disputeId);
        values.put("reportId", reportId);
        values.put("applicationId", UUID.randomUUID());
        values.put("externalMatchId", UUID.randomUUID());
        values.put("reporterType", "TEAM");
        values.put("status", "RESOLVED");
        values.put("source", "EXTERNAL");
        values.put("reliabilityPenalty", 5);
        values.put("mercenaryDeleted", true);
        values.put("mercenaryName", "Tên thật");
        return projections.createProjection(AdminMercenaryDisputeDetailView.class, values);
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
