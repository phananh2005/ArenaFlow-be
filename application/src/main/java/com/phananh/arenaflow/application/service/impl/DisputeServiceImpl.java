package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.CreateDisputeRequest;
import com.phananh.arenaflow.application.dto.response.DisputeCreatedResponse;
import com.phananh.arenaflow.application.event.reputation.ReportDisputedEvent;
import com.phananh.arenaflow.application.mapper.ReputationMapper;
import com.phananh.arenaflow.application.service.DisputeService;
import com.phananh.arenaflow.application.support.ConcurrencyConflictException;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.reputation.ReputationRules;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.rating.Dispute;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.projection.DisputeContextView;
import com.phananh.arenaflow.domain.repository.DisputeRepository;
import com.phananh.arenaflow.domain.repository.ReportRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DisputeServiceImpl implements DisputeService {

    private final ReportRepository reportRepository;
    private final DisputeRepository disputeRepository;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final ReputationMapper reputationMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public DisputeServiceImpl(ReportRepository reportRepository, DisputeRepository disputeRepository,
                              ConcurrencyConflictRetrier concurrencyConflictRetrier, ReputationMapper reputationMapper,
                              ApplicationEventPublisher eventPublisher, Clock clock) {
        this.reportRepository = reportRepository;
        this.disputeRepository = disputeRepository;
        this.concurrencyConflictRetrier = concurrencyConflictRetrier;
        this.reputationMapper = reputationMapper;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** Q1 ngữ cảnh, Q2 AUTO_ACCEPTED → DISPUTED có điều kiện, Q3 INSERT tranh chấp (flush lúc commit). */
    @Override
    public DisputeCreatedResponse createDispute(UUID reportId, UUID callerId, CreateDisputeRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            DisputeContextView ctx = reportRepository.findDisputeContext(reportId, callerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_001));
            LocalDateTime now = LocalDateTime.now(clock);
            guard(ctx, now);
            if (reportRepository.markDisputed(reportId, now, now.minus(ReputationRules.DISPUTE_WINDOW)) == 0) {
                // Report vừa đổi trạng thái / vừa quá hạn giữa Q1 và Q2: lần thử sau, guard trả mã lỗi đúng.
                throw new ConcurrencyConflictException("Report không còn ở trạng thái tranh chấp được");
            }
            // Unique theo report_id đã được câu cập nhật có điều kiện bảo vệ → không cần flush sớm.
            Dispute dispute = disputeRepository.save(Dispute.builder()
                    .reportId(reportId)
                    .evidence(request.evidence())
                    .createdByUserId(callerId)
                    .build());
            eventPublisher.publishEvent(new ReportDisputedEvent(dispute.getId(), reportId, ctx.getReporterTeamId(),
                    ctx.getReportedTeamId(), now));
            return reputationMapper.toDisputeCreated(dispute);
        });
    }

    /**
     * Kiểm đội bị report trên dữ liệu đã đọc (không gọi guard đội để giữ một câu): TEAM_016 → TEAM_015 → TEAM_002 →
     * REPORT_006 → REPORT_008 → REPORT_007.
     */
    private static void guard(DisputeContextView ctx, LocalDateTime now) {
        if (Team.TeamStatus.DISBANDED.name().equals(ctx.getReportedTeamStatus())) {
            throw new BusinessException(ErrorCode.TEAM_016);
        }
        if (Team.TeamStatus.INACTIVE.name().equals(ctx.getReportedTeamStatus())) {
            throw new BusinessException(ErrorCode.TEAM_015);
        }
        if (!Boolean.TRUE.equals(ctx.getCallerReportedCaptain())) {
            throw new BusinessException(ErrorCode.TEAM_002);
        }
        if (!Report.ReportType.NOSHOW.name().equals(ctx.getReportType())) {
            throw new BusinessException(ErrorCode.REPORT_006);
        }
        if (!Report.Status.AUTO_ACCEPTED.name().equals(ctx.getStatus()) || Boolean.TRUE.equals(ctx.getDisputeExists())) {
            throw new BusinessException(ErrorCode.REPORT_008);
        }
        if (!now.isBefore(ctx.getCreatedAt().plus(ReputationRules.DISPUTE_WINDOW))) {
            throw new BusinessException(ErrorCode.REPORT_007);
        }
    }
}
