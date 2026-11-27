package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.ResolveDisputeRequest;
import com.phananh.arenaflow.application.dto.response.AdminMercenaryDisputeDetailResponse;
import com.phananh.arenaflow.application.dto.response.AdminMercenaryDisputeResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.PaginationMeta;
import com.phananh.arenaflow.application.event.reputation.DisputeResolvedEvent;
import com.phananh.arenaflow.application.mapper.MercenaryReputationMapper;
import com.phananh.arenaflow.application.service.AdminMercenaryDisputeService;
import com.phananh.arenaflow.application.service.DisputeStatusFilter;
import com.phananh.arenaflow.application.service.ReputationService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminMercenaryDisputeServiceImpl implements AdminMercenaryDisputeService {

    private static final String RESOLVE_DISPUTE = "RESOLVE_DISPUTE";
    private static final String TARGET_DISPUTE = "DISPUTE";

    private final DisputeRepository disputeRepository;
    private final ReportRepository reportRepository;
    private final RatingRepository ratingRepository;
    private final ReputationService reputationService;
    private final VerificationRefresher verificationRefresher;
    private final AdminAuditLog adminAuditLog;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final MercenaryReputationMapper reputationMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** Hàng chờ (2 truy vấn): PENDING cũ nhất trước, trạng thái khác mới nhất trước; tên thật + cờ đã xoá. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminMercenaryDisputeResponse> listDisputes(DisputeStatusFilter status, int page,
                                                                    int pageSize) {
        String value = status.toQueryValue();
        var rows = disputeRepository.findAdminMercenaryDisputes(value, status == DisputeStatusFilter.PENDING,
                pageSize, (long) (page - 1) * pageSize);
        long total = disputeRepository.countAdminMercenaryDisputes(value);
        return new PageResponse<>(rows.stream().map(reputationMapper::toAdminDispute).toList(),
                new PaginationMeta(page, pageSize, total));
    }

    /** Chi tiết (2 truy vấn): không có / không thuộc hàng chờ mercenary → DISPUTE_001. */
    @Override
    @Transactional(readOnly = true)
    public AdminMercenaryDisputeDetailResponse getDispute(UUID disputeId) {
        return detail(disputeId);
    }

    /**
     * Chốt tranh chấp (REJECTED: 6 truy vấn kể cả refresh huy hiệu sau commit; CONFIRMED: 4). CAS rỗng → có tranh chấp
     * thuộc hàng chờ mercenary → DISPUTE_002, không → DISPUTE_001. Bác report → hoàn đúng điểm đã trừ cho bên bị report
     * (cá nhân hoặc đội). Audit sau commit.
     */
    @Override
    public AdminMercenaryDisputeDetailResponse resolveDispute(UUID disputeId, UUID adminId,
                                                              ResolveDisputeRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            UUID reportId = disputeRepository.resolveMercenaryIfPending(disputeId, request.decision(), request.note(),
                    adminId, now).orElseThrow(() -> new BusinessException(disputeRepository
                    .existsMercenaryDispute(disputeId) ? ErrorCode.DISPUTE_002 : ErrorCode.DISPUTE_001));
            ReportDecisionView decision = reportRepository.applyDecision(reportId, request.decision(), now)
                    .orElseThrow(() -> new IllegalStateException("Disputed report not in DISPUTED state"));
            if ("REJECTED".equals(request.decision())) {
                refund(decision, reportId);
            }
            eventPublisher.publishEvent(new DisputeResolvedEvent(disputeId, reportId, decision.getReporterType(),
                    decision.getReporterId(), decision.getReportedType(), decision.getReportedId(),
                    request.decision(), now));
            adminAuditLog.afterCommit(RESOLVE_DISPUTE, adminId, TARGET_DISPUTE, disputeId);
            // Câu đọc chi tiết chạy sau các câu UPDATE native trong cùng transaction nên thấy kết quả vừa ghi.
            return detail(disputeId);
        });
    }

    /** Hoàn điểm đã thực trừ cho bên bị report và refresh huy hiệu bên đó sau commit. */
    private void refund(ReportDecisionView decision, UUID reportId) {
        Report.ReportType type = Report.ReportType.valueOf(decision.getReportType());
        int points = decision.getReliabilityPenalty() == null ? 0 : decision.getReliabilityPenalty();
        if ("INDIVIDUAL".equals(decision.getReportedType())) {
            reputationService.refundUserReportPenalty(decision.getReportedId(), type, points, reportId);
            verificationRefresher.refreshUsersAfterCommit(Set.of(decision.getReportedId()));
        } else {
            reputationService.refundReportPenalty(decision.getReportedId(), type, points, reportId);
            verificationRefresher.refreshTeamsAfterCommit(Set.of(decision.getReportedId()));
        }
    }

    private AdminMercenaryDisputeDetailResponse detail(UUID disputeId) {
        AdminMercenaryDisputeDetailView view = disputeRepository.findAdminMercenaryDisputeDetail(disputeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DISPUTE_001));
        return reputationMapper.toAdminDisputeDetail(view,
                ratingRepository.findByApplicationIdOrderByCreatedAtAsc(view.getApplicationId()));
    }
}
