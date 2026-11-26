package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.ResolveDisputeRequest;
import com.phananh.arenaflow.application.dto.response.AdminDisputeDetailResponse;
import com.phananh.arenaflow.application.dto.response.AdminDisputeResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.PaginationMeta;
import com.phananh.arenaflow.application.event.reputation.DisputeResolvedEvent;
import com.phananh.arenaflow.application.mapper.ReputationMapper;
import com.phananh.arenaflow.application.service.AdminDisputeService;
import com.phananh.arenaflow.application.service.DisputeStatusFilter;
import com.phananh.arenaflow.application.service.ReputationService;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AdminDisputeServiceImpl implements AdminDisputeService {

    private static final String AUDIT_ACTION_RESOLVE = "RESOLVE_DISPUTE";
    private static final String AUDIT_TARGET_DISPUTE = "DISPUTE";

    private final DisputeRepository disputeRepository;
    private final ReportRepository reportRepository;
    private final RatingRepository ratingRepository;
    private final ReputationService reputationService;
    private final TeamVerificationRefresher verificationRefresher;
    private final AdminAuditLog adminAuditLog;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final ReputationMapper reputationMapper;
    private final PageRequestFactory pageRequestFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AdminDisputeServiceImpl(DisputeRepository disputeRepository, ReportRepository reportRepository,
                                   RatingRepository ratingRepository, ReputationService reputationService,
                                   TeamVerificationRefresher verificationRefresher, AdminAuditLog adminAuditLog,
                                   ConcurrencyConflictRetrier concurrencyConflictRetrier, ReputationMapper reputationMapper,
                                   PageRequestFactory pageRequestFactory, ApplicationEventPublisher eventPublisher,
                                   Clock clock) {
        this.disputeRepository = disputeRepository;
        this.reportRepository = reportRepository;
        this.ratingRepository = ratingRepository;
        this.reputationService = reputationService;
        this.verificationRefresher = verificationRefresher;
        this.adminAuditLog = adminAuditLog;
        this.concurrencyConflictRetrier = concurrencyConflictRetrier;
        this.reputationMapper = reputationMapper;
        this.pageRequestFactory = pageRequestFactory;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** Q1 trang, Q2 đếm. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminDisputeResponse> listDisputes(DisputeStatusFilter status, int page, int pageSize) {
        Pageable pageable = pageRequestFactory.create(page, pageSize);
        String statusValue = status.toQueryValue();
        List<AdminDisputeView> rows = status.oldestFirst()
                ? disputeRepository.findAdminDisputesOldestFirst(statusValue, pageable.getPageSize(), pageable.getOffset())
                : disputeRepository.findAdminDisputesNewestFirst(statusValue, pageable.getPageSize(), pageable.getOffset());
        long total = disputeRepository.countAdminDisputes(statusValue);
        return new PageResponse<>(rows.stream().map(reputationMapper::toAdminDispute).toList(),
                new PaginationMeta(page, pageSize, total));
    }

    /** Q1 chi tiết, Q2 đánh giá của trận. */
    @Override
    @Transactional(readOnly = true)
    public AdminDisputeDetailResponse getDispute(UUID disputeId) {
        return detail(disputeId);
    }

    /**
     * Q1 tranh chấp PENDING → RESOLVED có điều kiện (0 dòng: Q1b phân biệt không có / đã xử lý), Q2 report DISPUTED →
     * quyết định, (Q3 hoàn điểm khi bác), Q4–Q5 chi tiết sau quyết định; sau commit refresh huy hiệu + audit.
     */
    @Override
    public AdminDisputeDetailResponse resolveDispute(UUID disputeId, UUID adminId, ResolveDisputeRequest request) {
        Dispute.Decision decision = Dispute.Decision.valueOf(request.decision());
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            UUID reportId = disputeRepository.resolveIfPending(disputeId, decision.name(), request.note(), adminId, now)
                    .orElseThrow(() -> new BusinessException(disputeRepository.existsById(disputeId)
                            ? ErrorCode.DISPUTE_002 : ErrorCode.DISPUTE_001));
            // Tranh chấp PENDING luôn đi với report DISPUTED → rỗng là lỗi dữ liệu / lập trình.
            ReportDecisionView report = reportRepository.applyDecision(reportId, decision.name(), now)
                    .orElseThrow(() -> new IllegalStateException("Report của tranh chấp không ở trạng thái DISPUTED"));
            if (decision == Dispute.Decision.REJECTED) {
                reputationService.refundReportPenalty(report.getReportedTeamId(),
                        Report.ReportType.valueOf(report.getReportType()), report.getReliabilityPenalty(), reportId);
                verificationRefresher.refreshAfterCommit(List.of(report.getReportedTeamId()));
            }
            eventPublisher.publishEvent(new DisputeResolvedEvent(disputeId, reportId, report.getReporterTeamId(),
                    report.getReportedTeamId(), decision, now));
            adminAuditLog.afterCommit(AUDIT_ACTION_RESOLVE, adminId, AUDIT_TARGET_DISPUTE, disputeId);
            return detail(disputeId);
        });
    }

    private AdminDisputeDetailResponse detail(UUID disputeId) {
        AdminDisputeDetailView view = disputeRepository.findAdminDisputeDetail(disputeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DISPUTE_001));
        List<Rating> ratings = ratingRepository.findByMatchIdAndRaterTypeOrderByCreatedAtAsc(view.getMatchId(),
                Rating.RaterType.TEAM);
        return reputationMapper.toAdminDetail(view, ratings);
    }
}
