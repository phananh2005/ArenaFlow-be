package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.CreateChatReportRequest;
import com.phananh.arenaflow.application.dto.request.CreateReportRequest;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.PaginationMeta;
import com.phananh.arenaflow.application.dto.response.ReportResponse;
import com.phananh.arenaflow.application.dto.response.TeamBriefResponse;
import com.phananh.arenaflow.application.event.reputation.TeamReportedEvent;
import com.phananh.arenaflow.application.mapper.ReputationMapper;
import com.phananh.arenaflow.application.service.ReportDirection;
import com.phananh.arenaflow.application.service.ReportService;
import com.phananh.arenaflow.application.service.ReputationService;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.DataIntegrityViolations;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.application.support.chat.ConversationAccess;
import com.phananh.arenaflow.application.support.chat.ConversationAccessPolicy;
import com.phananh.arenaflow.application.support.reputation.MatchSides;
import com.phananh.arenaflow.application.support.reputation.TeamVerificationRefresher;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.projection.ChatReportContextView;
import com.phananh.arenaflow.domain.projection.ReportContextView;
import com.phananh.arenaflow.domain.projection.TeamReportView;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import com.phananh.arenaflow.domain.repository.ReportRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    private static final String REPORT_UNIQUE_INDEX = "idx_report_unique";
    private static final Set<String> REPORTABLE_MATCH_STATUSES = Set.of(Match.Status.ARCHIVED.name(),
            Match.Status.COMPLETED.name(), Match.Status.CANCELLED.name());

    private final MatchRepository matchRepository;
    private final ReportRepository reportRepository;
    private final TeamGuard teamGuard;
    private final ReputationService reputationService;
    private final TeamVerificationRefresher verificationRefresher;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final ReputationMapper reputationMapper;
    private final PageRequestFactory pageRequestFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, ConversationAccessPolicy> accessPolicies;
    private final Clock clock;

    public ReportServiceImpl(MatchRepository matchRepository, ReportRepository reportRepository, TeamGuard teamGuard,
                             ReputationService reputationService, TeamVerificationRefresher verificationRefresher,
                             ConcurrencyConflictRetrier concurrencyConflictRetrier, ReputationMapper reputationMapper,
                             PageRequestFactory pageRequestFactory, ApplicationEventPublisher eventPublisher,
                             List<ConversationAccessPolicy> accessPolicies, Clock clock) {
        this.matchRepository = matchRepository;
        this.reportRepository = reportRepository;
        this.teamGuard = teamGuard;
        this.reputationService = reputationService;
        this.verificationRefresher = verificationRefresher;
        this.concurrencyConflictRetrier = concurrencyConflictRetrier;
        this.reputationMapper = reputationMapper;
        this.pageRequestFactory = pageRequestFactory;
        this.eventPublisher = eventPublisher;
        this.accessPolicies = accessPolicies.stream()
                .collect(Collectors.toUnmodifiableMap(policy -> policy.contextType().name(), Function.identity()));
        this.clock = clock;
    }

    /** Q1 ngữ cảnh, Q2 INSERT report, Q3 trừ điểm, Q4 UPDATE điểm thực trừ lúc commit, sau commit Q5 refresh huy hiệu. */
    @Override
    public ReportResponse createReport(UUID matchId, UUID callerId, CreateReportRequest request) {
        Report.ReportType type = Report.ReportType.valueOf(request.reportType());
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            ReportContextView ctx = matchRepository.findReportContext(matchId, callerId, type.name())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_001));
            MatchSides sides = guardMatchReport(ctx, type);
            LocalDateTime now = LocalDateTime.now(clock);
            Report report = writeReport(matchId, sides.ownTeamId(), sides.otherTeamId(), type, request.reason(), callerId,
                    now);
            TeamBriefResponse counterpart = sides.ownIsAuthor()
                    ? new TeamBriefResponse(ctx.getOpponentTeamId(), ctx.getOpponentTeamName(), ctx.getOpponentTeamAvatarUrl())
                    : new TeamBriefResponse(ctx.getAuthorTeamId(), ctx.getAuthorTeamName(), ctx.getAuthorTeamAvatarUrl());
            return reputationMapper.toSentReport(report, ctx.getMatchStartTime(), counterpart, now);
        });
    }

    /** Q1 ngữ cảnh cuộc trò chuyện, Q2 INSERT report, Q3 trừ điểm, Q4 UPDATE điểm thực trừ, sau commit Q5 refresh huy hiệu. */
    @Override
    public ReportResponse createChatReport(UUID conversationId, UUID callerId, CreateChatReportRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            ChatReportContextView ctx = reportRepository.findChatReportContext(conversationId, callerId, now)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_002));
            if (Boolean.TRUE.equals(ctx.getCallerDeleted())) {
                throw new BusinessException(ErrorCode.USER_001);
            }
            Conversation.Party viewer = guardChatReport(ctx, callerId, now);
            boolean viewerIsA = viewer == Conversation.Party.A;
            UUID reporter = viewerIsA ? ctx.getPartyAId() : ctx.getPartyBId();
            UUID reported = viewerIsA ? ctx.getPartyBId() : ctx.getPartyAId();
            Report report = writeReport(ctx.getMatchId(), reporter, reported, Report.ReportType.BAD_COMMUNICATION,
                    request.reason(), callerId, now);
            TeamBriefResponse counterpart = viewerIsA
                    ? new TeamBriefResponse(reported, ctx.getPartyBName(), ctx.getPartyBAvatarUrl())
                    : new TeamBriefResponse(reported, ctx.getPartyAName(), ctx.getPartyAAvatarUrl());
            return reputationMapper.toSentReport(report, ctx.getMatchStartTime(), counterpart, now);
        });
    }

    /** Q1 quyền đội trưởng, Q2 trang, Q3 đếm. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> listTeamReports(UUID teamId, UUID callerId, ReportDirection direction, int page,
                                                        int pageSize) {
        teamGuard.requireActiveCaptain(teamId, callerId);
        Pageable pageable = pageRequestFactory.create(page, pageSize);
        boolean received = direction == ReportDirection.RECEIVED;
        List<TeamReportView> rows = received
                ? reportRepository.findReceivedReports(teamId, pageable.getPageSize(), pageable.getOffset())
                : reportRepository.findSentReports(teamId, pageable.getPageSize(), pageable.getOffset());
        long total = received ? reportRepository.countReceivedReports(teamId) : reportRepository.countSentReports(teamId);
        LocalDateTime now = LocalDateTime.now(clock);
        return new PageResponse<>(rows.stream().map(row -> reputationMapper.toReport(row, direction, now)).toList(),
                new PaginationMeta(page, pageSize, total));
    }

    /** Thứ tự kiểm: TEAM_002 → TEAM_015 → REPORT_005 → REPORT_002 → REPORT_003 → REPORT_004. */
    private static MatchSides guardMatchReport(ReportContextView ctx, Report.ReportType type) {
        if (!MatchSides.isCaptainOfAny(ctx)) {
            throw new BusinessException(ErrorCode.TEAM_002);
        }
        if (MatchSides.isCallerTeamInactive(ctx)) {
            throw new BusinessException(ErrorCode.TEAM_015);
        }
        if (MatchSides.isCaptainOfBoth(ctx)) {
            throw new BusinessException(ErrorCode.REPORT_005);
        }
        if (!REPORTABLE_MATCH_STATUSES.contains(ctx.getStatus()) || ctx.getOpponentTeamId() == null) {
            throw new BusinessException(ErrorCode.REPORT_002);
        }
        if (Match.Status.CANCELLED.name().equals(ctx.getStatus()) && type != Report.ReportType.BAD_COMMUNICATION) {
            throw new BusinessException(ErrorCode.REPORT_003);
        }
        MatchSides sides = MatchSides.of(ctx);
        Boolean alreadyReported = sides.ownIsAuthor() ? ctx.getAuthorReportedType() : ctx.getOpponentReportedType();
        if (Boolean.TRUE.equals(alreadyReported)) {
            throw new BusinessException(ErrorCode.REPORT_004);
        }
        return sides;
    }

    /**
     * Thứ tự kiểm sau USER_001: CHAT_003 → REPORT_011 → TEAM_016 → TEAM_015 → REPORT_005 → REPORT_010 → REPORT_004.
     * Bên của người gọi do policy của loại ngữ cảnh quyết định (quy tắc "ai là bên nào" dùng chung với chat). Loại ngữ
     * cảnh thiếu policy là lỗi lập trình (mỗi loại phải có đúng một policy); loại khác chat hai đội → REPORT_011.
     */
    private Conversation.Party guardChatReport(ChatReportContextView ctx, UUID callerId, LocalDateTime now) {
        boolean teamMatch = Conversation.ContextType.TEAM_MATCH.name().equals(ctx.getContextType());
        ConversationAccessPolicy policy = accessPolicies.get(ctx.getContextType());
        if (policy == null) {
            throw new IllegalStateException("Thiếu policy cho loại cuộc trò chuyện " + ctx.getContextType());
        }
        ConversationAccess access = policy.resolve(ctx, callerId, now);
        if (access.viewerParty() == null) {
            throw new BusinessException(ErrorCode.CHAT_003);
        }
        if (!teamMatch) {
            throw new BusinessException(ErrorCode.REPORT_011);
        }
        // Bên chỉ xem = người đã giải tán đội của mình: vẫn đọc chat nhưng đội đã giải tán không report được.
        if (access.viewerReadOnly()) {
            throw new BusinessException(ErrorCode.TEAM_016);
        }
        boolean viewerIsA = access.viewerParty() == Conversation.Party.A;
        String viewerTeamStatus = viewerIsA ? ctx.getPartyATeamStatus() : ctx.getPartyBTeamStatus();
        if (Team.TeamStatus.INACTIVE.name().equals(viewerTeamStatus)) {
            throw new BusinessException(ErrorCode.TEAM_015);
        }
        if (Boolean.TRUE.equals(ctx.getCallerPartyACaptain()) && Boolean.TRUE.equals(ctx.getCallerPartyBCaptain())) {
            throw new BusinessException(ErrorCode.REPORT_005);
        }
        if (!Boolean.TRUE.equals(ctx.getHasMessages())) {
            throw new BusinessException(ErrorCode.REPORT_010);
        }
        Boolean alreadyReported = viewerIsA ? ctx.getAReportedB() : ctx.getBReportedA();
        if (Boolean.TRUE.equals(alreadyReported)) {
            throw new BusinessException(ErrorCode.REPORT_004);
        }
        return access.viewerParty();
    }

    /**
     * Đường ghi chung của report sau trận và qua chat: INSERT (flush để bắt index chống trùng → REPORT_004) → trừ điểm
     * đội bị report → ghi điểm thực trừ bằng dirty checking (UPDATE lúc commit) → event → refresh huy hiệu sau commit.
     */
    private Report writeReport(UUID matchId, UUID reporterTeamId, UUID reportedTeamId, Report.ReportType type,
                               String reason, UUID callerId, LocalDateTime now) {
        Report report;
        try {
            // Flush ngay để bắt vi phạm index chống trùng (gửi lặp đồng thời, kể cả qua chat và sau trận) → REPORT_004.
            report = reportRepository.saveAndFlush(Report.builder()
                    .matchId(matchId)
                    .reporterType(Report.ReporterType.TEAM).reporterId(reporterTeamId)
                    .reportedType(Report.ReportedType.TEAM).reportedId(reportedTeamId)
                    .reportType(type)
                    .reason(reason)
                    .createdByUserId(callerId)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            if (DataIntegrityViolations.violates(exception, REPORT_UNIQUE_INDEX)) {
                throw new BusinessException(ErrorCode.REPORT_004);
            }
            throw exception;
        }
        int points = reputationService.applyReportPenalty(reportedTeamId, type, report.getId());
        report.setReliabilityPenalty(points);
        eventPublisher.publishEvent(new TeamReportedEvent(report.getId(), matchId, reporterTeamId, reportedTeamId, type,
                now));
        verificationRefresher.refreshAfterCommit(List.of(reportedTeamId));
        return report;
    }
}
