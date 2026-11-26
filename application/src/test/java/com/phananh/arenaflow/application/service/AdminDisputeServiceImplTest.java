package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.ResolveDisputeRequest;
import com.phananh.arenaflow.application.dto.response.AdminDisputeDetailResponse;
import com.phananh.arenaflow.application.dto.response.AdminDisputeResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.event.reputation.DisputeResolvedEvent;
import com.phananh.arenaflow.application.mapper.ReputationMapper;
import com.phananh.arenaflow.application.service.impl.AdminDisputeServiceImpl;
import com.phananh.arenaflow.application.support.AdminAuditLog;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.reputation.TeamVerificationRefresher;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.rating.Dispute;
import com.phananh.arenaflow.domain.entity.rating.Rating;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.projection.AdminDisputeDetailView;
import com.phananh.arenaflow.domain.projection.AdminDisputeView;
import com.phananh.arenaflow.domain.projection.ReportDecisionView;
import com.phananh.arenaflow.domain.repository.DisputeRepository;
import com.phananh.arenaflow.domain.repository.RatingRepository;
import com.phananh.arenaflow.domain.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminDisputeServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T16:00:00Z"), ZoneId.systemDefault());
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 23, 0);

    private final DisputeRepository disputeRepository = mock(DisputeRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final RatingRepository ratingRepository = mock(RatingRepository.class);
    private final ReputationService reputationService = mock(ReputationService.class);
    private final TeamVerificationRefresher refresher = mock(TeamVerificationRefresher.class);
    private final AdminAuditLog auditLog = mock(AdminAuditLog.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID disputeId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID matchId = UUID.randomUUID();
    private final UUID reporter = UUID.randomUUID();
    private final UUID reported = UUID.randomUUID();

    private AdminDisputeService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new AdminDisputeServiceImpl(disputeRepository, reportRepository, ratingRepository, reputationService,
                refresher, auditLog, retrier, Mappers.getMapper(ReputationMapper.class), new PageRequestFactory(),
                publisher, CLOCK);
        when(retrier.execute(eq(ErrorCode.MATCH_014), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(1)).get());
        when(disputeRepository.findAdminDisputeDetail(disputeId)).thenReturn(Optional.of(detail()));
        when(ratingRepository.findByMatchIdAndRaterTypeOrderByCreatedAtAsc(matchId, Rating.RaterType.TEAM))
                .thenReturn(List.of());
    }

    @Test
    void pendingQueueIsOldestFirstOthersNewestFirst() {
        when(disputeRepository.findAdminDisputesOldestFirst("PENDING", 20, 0)).thenReturn(List.of(detail()));
        when(disputeRepository.countAdminDisputes("PENDING")).thenReturn(1L);

        PageResponse<AdminDisputeResponse> page = service.listDisputes(DisputeStatusFilter.PENDING, 1, 20);

        assertThat(page.pagination().totalItems()).isEqualTo(1);
        AdminDisputeResponse dispute = page.data().get(0);
        assertThat(dispute.disputedByName()).isEqualTo("Trần Văn B");
        assertThat(dispute.isDisputedByDeleted()).isTrue();
        assertThat(dispute.isResolvedByDeleted()).isFalse();
        assertThat(dispute.reporterTeam().status()).isEqualTo("ACTIVE");

        service.listDisputes(DisputeStatusFilter.ALL, 2, 10);
        verify(disputeRepository).findAdminDisputesNewestFirst(null, 10, 10);
    }

    @Test
    void getDisputeMissingIsDispute001() {
        when(disputeRepository.findAdminDisputeDetail(disputeId)).thenReturn(Optional.empty());

        assertCode(() -> service.getDispute(disputeId), ErrorCode.DISPUTE_001);
    }

    @Test
    void getDisputeIncludesMatchRatingsOldestFirst() {
        AdminDisputeDetailResponse response = service.getDispute(disputeId);

        assertThat(response.match().sportName()).isEqualTo("Bóng đá");
        assertThat(response.evidence()).isEqualTo("Có mặt");
        assertThat(response.matchRatings()).isEmpty();
        verify(ratingRepository).findByMatchIdAndRaterTypeOrderByCreatedAtAsc(matchId, Rating.RaterType.TEAM);
    }

    @Test
    void conditionalUpdateMissDistinguishesMissingFromResolved() {
        when(disputeRepository.resolveIfPending(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(disputeRepository.existsById(disputeId)).thenReturn(false);
        assertCode(() -> service.resolveDispute(disputeId, adminId, reject()), ErrorCode.DISPUTE_001);

        when(disputeRepository.existsById(disputeId)).thenReturn(true);
        assertCode(() -> service.resolveDispute(disputeId, adminId, reject()), ErrorCode.DISPUTE_002);
        verifyNoInteractions(reputationService);
    }

    @Test
    void missingDisputedReportIsProgrammingError() {
        when(disputeRepository.resolveIfPending(disputeId, "REJECTED", "Có mặt đúng giờ", adminId, NOW))
                .thenReturn(Optional.of(reportId));
        when(reportRepository.applyDecision(reportId, "REJECTED", NOW)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveDispute(disputeId, adminId, reject()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectRefundsActualPenaltyRefreshesBadgeAuditsAndReturnsDetail() {
        when(disputeRepository.resolveIfPending(disputeId, "REJECTED", "Có mặt đúng giờ", adminId, NOW))
                .thenReturn(Optional.of(reportId));
        when(reportRepository.applyDecision(reportId, "REJECTED", NOW)).thenReturn(Optional.of(decision(3)));

        AdminDisputeDetailResponse response = service.resolveDispute(disputeId, adminId, reject());

        InOrder order = inOrder(disputeRepository, reportRepository, reputationService, refresher, publisher, auditLog);
        order.verify(disputeRepository).resolveIfPending(disputeId, "REJECTED", "Có mặt đúng giờ", adminId, NOW);
        order.verify(reportRepository).applyDecision(reportId, "REJECTED", NOW);
        order.verify(reputationService).refundReportPenalty(reported, Report.ReportType.NOSHOW, 3, reportId);
        order.verify(refresher).refreshAfterCommit(List.of(reported));
        order.verify(publisher).publishEvent(new DisputeResolvedEvent(disputeId, reportId, reporter, reported,
                Dispute.Decision.REJECTED, NOW));
        order.verify(auditLog).afterCommit("RESOLVE_DISPUTE", adminId, "DISPUTE", disputeId);
        assertThat(response.disputeId()).isEqualTo(disputeId);
    }

    @Test
    void confirmKeepsPenalty() {
        when(disputeRepository.resolveIfPending(disputeId, "CONFIRMED", "Đúng", adminId, NOW))
                .thenReturn(Optional.of(reportId));
        when(reportRepository.applyDecision(reportId, "CONFIRMED", NOW)).thenReturn(Optional.of(decision(5)));

        service.resolveDispute(disputeId, adminId, new ResolveDisputeRequest("CONFIRMED", "Đúng"));

        verify(reputationService, never()).refundReportPenalty(any(), any(), anyInt(), any());
        verify(refresher, never()).refreshAfterCommit(any());
        verify(auditLog).afterCommit("RESOLVE_DISPUTE", adminId, "DISPUTE", disputeId);
        verify(disputeRepository, never()).findAdminDisputesNewestFirst(any(), anyInt(), anyLong());
    }

    private ResolveDisputeRequest reject() {
        return new ResolveDisputeRequest("REJECTED", "Có mặt đúng giờ");
    }

    private ReportDecisionView decision(int penalty) {
        return projections.createProjection(ReportDecisionView.class, Map.of("reportedTeamId", reported,
                "reporterTeamId", reporter, "reportType", "NOSHOW", "reliabilityPenalty", penalty));
    }

    private AdminDisputeDetailView detail() {
        Map<String, Object> values = new HashMap<>();
        values.put("disputeId", disputeId);
        values.put("reportId", reportId);
        values.put("matchId", matchId);
        values.put("matchStartTime", NOW.minusHours(5));
        values.put("reporterTeamId", reporter);
        values.put("reporterTeamName", "FC Hoa Lư");
        values.put("reporterTeamStatus", "ACTIVE");
        values.put("reportedTeamId", reported);
        values.put("reportedTeamName", "FC Sấm Sét");
        values.put("reportedTeamStatus", "ACTIVE");
        values.put("reportedById", UUID.randomUUID());
        values.put("reportedByName", "Nguyễn Văn A");
        values.put("reportedByDeleted", false);
        values.put("disputedById", UUID.randomUUID());
        values.put("disputedByName", "Trần Văn B");
        values.put("disputedByDeleted", true);
        values.put("status", "PENDING");
        values.put("resolvedByDeleted", false);
        values.put("reportCreatedAt", NOW.minusHours(2));
        values.put("createdAt", NOW.minusHours(1));
        values.put("sportName", "Bóng đá");
        values.put("matchStatus", "COMPLETED");
        values.put("matchEndTime", NOW.minusHours(4));
        values.put("reportReason", "Không đến");
        values.put("reliabilityPenalty", 5);
        values.put("evidence", "Có mặt");
        values.put("reporterTeamReliabilityScore", 84);
        values.put("reportedTeamReliabilityScore", 75);
        return projections.createProjection(AdminDisputeDetailView.class, values);
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(code);
    }
}
