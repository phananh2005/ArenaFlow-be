package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.CreateReportRequest;
import com.phananh.arenaflow.application.dto.response.MercenaryReportResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.PaginationMeta;
import com.phananh.arenaflow.application.event.mercenary.MercenaryReportCreatedEvent;
import com.phananh.arenaflow.application.mapper.MercenaryReputationMapper;
import com.phananh.arenaflow.application.service.MercenaryReportDirection;
import com.phananh.arenaflow.application.service.MercenaryReportService;
import com.phananh.arenaflow.application.service.ReputationService;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.DataIntegrityViolations;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.application.support.mercenary.MercenaryGuard;
import com.phananh.arenaflow.application.support.mercenary.MercenarySide;
import com.phananh.arenaflow.application.support.reputation.VerificationRefresher;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.projection.MercenaryReportContextView;
import com.phananh.arenaflow.domain.projection.MercenaryReportView;
import com.phananh.arenaflow.domain.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MercenaryReportServiceImpl implements MercenaryReportService {

    private static final Set<String> REPORT_INDEXES = Set.of("idx_report_unique", "idx_report_external_unique");
    private static final String SUBJECT_TEAM = "TEAM";
    private static final String SUBJECT_INDIVIDUAL = "INDIVIDUAL";

    private final ReportRepository reportRepository;
    private final TeamGuard teamGuard;
    private final MercenaryGuard mercenaryGuard;
    private final ReputationService reputationService;
    private final VerificationRefresher verificationRefresher;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final MercenaryReputationMapper reputationMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Report theo đơn (5 truy vấn kể cả refresh huy hiệu sau commit). Thứ tự kiểm: USER_001 → MERCENARY_002 →
     * MERCENARY_019 → TEAM_016 (người đã giải tán đội tuyển) → MERCENARY_014 → (phía đội trưởng) TEAM_015 →
     * MERCENARY_020 (không tham gia trận và chưa có tin nhắn) → MERCENARY_021 (chỉ được report giao tiếp tệ) →
     * MERCENARY_022 (đã report loại này; gửi đồng thời → unique index). Đội trưởng report → trừ điểm cá nhân mercenary;
     * mercenary report → trừ điểm đội như report giữa hai đội.
     */
    @Override
    public MercenaryReportResponse createReport(UUID applicationId, UUID callerId, CreateReportRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            MercenaryReportContextView ctx = reportRepository.findMercenaryReportContext(applicationId, callerId,
                    request.reportType(), now).orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
            mercenaryGuard.requireNotDeleted(Boolean.TRUE.equals(ctx.getCallerDeleted()));
            if (ctx.getApplicationId() == null) {
                throw new BusinessException(ErrorCode.MERCENARY_002);
            }
            MercenarySide side = resolveSide(ctx);
            Report.ReportType type = Report.ReportType.valueOf(request.reportType());
            boolean participated = Boolean.TRUE.equals(ctx.getGameEnded())
                    && "APPROVED".equals(ctx.getApplicationStatus());
            if (!participated && !Boolean.TRUE.equals(ctx.getHasMessages())) {
                throw new BusinessException(ErrorCode.MERCENARY_020);
            }
            if (!participated && type != Report.ReportType.BAD_COMMUNICATION) {
                throw new BusinessException(ErrorCode.MERCENARY_021);
            }
            boolean captain = side == MercenarySide.CAPTAIN;
            if (Boolean.TRUE.equals(captain ? ctx.getTeamReported() : ctx.getApplicantReported())) {
                throw new BusinessException(ErrorCode.MERCENARY_022);
            }
            Report report = save(Report.builder()
                    .matchId(ctx.getMatchId())
                    .externalMatchId(ctx.getExternalMatchId())
                    .applicationId(applicationId)
                    .reporterType(captain ? Report.ReporterType.TEAM : Report.ReporterType.INDIVIDUAL)
                    .reporterId(captain ? ctx.getTeamId() : ctx.getApplicantId())
                    .reportedType(captain ? Report.ReportedType.INDIVIDUAL : Report.ReportedType.TEAM)
                    .reportedId(captain ? ctx.getApplicantId() : ctx.getTeamId())
                    .reportType(type)
                    .reason(request.reason())
                    .createdByUserId(callerId)
                    .build());
            int points;
            if (captain) {
                points = reputationService.applyUserReportPenalty(ctx.getApplicantId(), type, report.getId());
                verificationRefresher.refreshUsersAfterCommit(Set.of(ctx.getApplicantId()));
            } else {
                points = reputationService.applyReportPenalty(ctx.getTeamId(), type, report.getId());
                verificationRefresher.refreshTeamsAfterCommit(Set.of(ctx.getTeamId()));
            }
            // Ghi điểm thực trừ bằng dirty checking, flush lúc commit.
            report.setReliabilityPenalty(points);
            eventPublisher.publishEvent(new MercenaryReportCreatedEvent(report.getId(), applicationId,
                    captain ? MercenaryReputationMapper.TEAM_TO_MERCENARY : MercenaryReputationMapper.MERCENARY_TO_TEAM,
                    type.name(), now));
            return reputationMapper.toCreatedReport(report, ctx);
        });
    }

    /** Report liên quan mercenary của đội (đội trưởng đội ACTIVE): 3 truy vấn. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<MercenaryReportResponse> listTeamReports(UUID teamId, UUID callerId,
                                                                 MercenaryReportDirection direction, int page,
                                                                 int pageSize) {
        teamGuard.requireActiveCaptain(teamId, callerId);
        return page(SUBJECT_TEAM, teamId, direction, page, pageSize);
    }

    /** Report của chính người gọi với vai trò mercenary: 2 truy vấn. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<MercenaryReportResponse> listMyReports(UUID callerId, MercenaryReportDirection direction,
                                                               int page, int pageSize) {
        return page(SUBJECT_INDIVIDUAL, callerId, direction, page, pageSize);
    }

    private PageResponse<MercenaryReportResponse> page(String subjectType, UUID subjectId,
                                                       MercenaryReportDirection direction, int page, int pageSize) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<MercenaryReportView> rows = reportRepository.findMercenaryReports(subjectType, subjectId,
                direction.name(), pageSize, (long) (page - 1) * pageSize);
        long total = reportRepository.countMercenaryReports(subjectType, subjectId, direction.name());
        return new PageResponse<>(rows.stream().map(row -> reputationMapper.toReport(row, direction.name(), now))
                .toList(), new PaginationMeta(page, pageSize, total));
    }

    /**
     * Phía người gọi: vừa ứng viên vừa đội trưởng → MERCENARY_019; không thuộc bên nào nhưng là người đã giải tán đội
     * tuyển → TEAM_016, người khác → MERCENARY_014; đội trưởng của đội tạm ngưng → TEAM_015.
     */
    private MercenarySide resolveSide(MercenaryReportContextView ctx) {
        boolean applicant = Boolean.TRUE.equals(ctx.getCallerApplicant());
        boolean captain = Boolean.TRUE.equals(ctx.getCallerCaptain());
        if (!applicant && !captain && Boolean.TRUE.equals(ctx.getCallerDisbandedBy())) {
            throw new BusinessException(ErrorCode.TEAM_016);
        }
        MercenarySide side = mercenaryGuard.resolveSide(applicant, captain);
        if (side == MercenarySide.CAPTAIN && "INACTIVE".equals(ctx.getTeamStatus())) {
            throw new BusinessException(ErrorCode.TEAM_015);
        }
        return side;
    }

    /** Flush ngay để đổi vi phạm unique (đã report loại này) thành MERCENARY_022 trước khi trừ điểm. */
    private Report save(Report report) {
        try {
            return reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException exception) {
            if (REPORT_INDEXES.contains(DataIntegrityViolations.constraintName(exception))) {
                throw new BusinessException(ErrorCode.MERCENARY_022);
            }
            throw exception;
        }
    }
}
