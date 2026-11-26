package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.event.reputation.ReliabilityScoreChangedEvent;
import com.phananh.arenaflow.application.event.reputation.ReputationSubjectType;
import com.phananh.arenaflow.application.service.CompletionEntry;
import com.phananh.arenaflow.application.service.ReliabilityChange;
import com.phananh.arenaflow.application.service.ReliabilityReason;
import com.phananh.arenaflow.application.service.ReputationService;
import com.phananh.arenaflow.application.support.reputation.ReportPenaltyPolicy;
import com.phananh.arenaflow.application.support.reputation.ReputationRules;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.projection.ScoreChangeView;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReputationServiceImpl implements ReputationService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void adjustTeamReliability(UUID teamId, ReliabilityChange change) {
        if (isNoOp(change)) {
            return;
        }
        int updated = teamRepository.adjustReliability(teamId, change.delta(), change.breaksCleanStreak());
        publish(ReputationSubjectType.TEAM, teamId, change, updated);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void adjustUserReliability(UUID userId, ReliabilityChange change) {
        if (isNoOp(change)) {
            return;
        }
        int updated = userRepository.adjustReliability(userId, change.delta(), change.breaksCleanStreak());
        publish(ReputationSubjectType.USER, userId, change, updated);
    }

    /**
     * Khoá các đội trước, rồi một câu UPDATE … RETURNING cho mọi đội (câu này mới thấy report đã commit trong lúc chờ
     * khoá); event chỉ cho đội có điểm đổi (đã chặn [0, 100]).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyMatchCompletion(UUID matchId, List<CompletionEntry> entries) {
        UUID[] ids = new UUID[entries.size()];
        boolean[] completes = new boolean[entries.size()];
        int[] bonuses = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            ids[i] = entries.get(i).teamId();
            completes[i] = entries.get(i).completes();
            bonuses[i] = entries.get(i).ratingBonus();
        }
        teamRepository.lockForReputation(ids);
        List<ScoreChangeView> changes = teamRepository.applyCompletion(matchId, ids, completes, bonuses,
                ReputationRules.COMPLETION_POINTS, ReputationRules.STREAK_MILESTONE, ReputationRules.STREAK_BONUS_5,
                ReputationRules.STREAK_BONUS_10);
        if (changes.size() != entries.size()) {
            throw new IllegalStateException("Cập nhật uy tín hoàn tất trận thiếu đội: " + changes.size() + "/" + entries.size());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        for (ScoreChangeView change : changes) {
            publishIfChanged(change.getTeamId(), change.getNewScore() - change.getOldScore(),
                    ReliabilityReason.MATCH_COMPLETED, matchId, now);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int applyReportPenalty(UUID teamId, Report.ReportType type, UUID reportId) {
        ScoreChangeView change = teamRepository.applyReportPenalty(teamId, type.name(), ReportPenaltyPolicy.tiers(type))
                .orElseThrow(() -> new IllegalStateException("Không có đội để trừ điểm report: " + teamId));
        int points = change.getOldScore() - change.getNewScore();
        publishIfChanged(teamId, -points, ReliabilityReason.REPORT_RECEIVED, reportId, LocalDateTime.now(clock));
        return points;
    }

    /** Luôn chạy câu cập nhật (giảm bộ đếm cả khi {@code points = 0}); event chỉ khi có điểm hoàn. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void refundReportPenalty(UUID teamId, Report.ReportType type, int points, UUID reportId) {
        if (teamRepository.refundReportPenalty(teamId, type.name(), points) == 0) {
            throw new IllegalStateException("Không có đội để hoàn điểm report: " + teamId);
        }
        publishIfChanged(teamId, points, ReliabilityReason.REPORT_REJECTED_REFUND, reportId, LocalDateTime.now(clock));
    }

    private static boolean isNoOp(ReliabilityChange change) {
        return change.delta() == 0 && !change.breaksCleanStreak();
    }

    /** 0 dòng = chủ thể không tồn tại: caller đã kiểm trước nên đây là bất biến bị phá. */
    private void publish(ReputationSubjectType type, UUID subjectId, ReliabilityChange change, int updated) {
        if (updated != 1) {
            throw new IllegalStateException("Reliability subject not found: " + type + " " + subjectId);
        }
        eventPublisher.publishEvent(new ReliabilityScoreChangedEvent(type, subjectId, change.delta(), change.reason(),
                change.referenceId(), LocalDateTime.now(clock)));
    }

    private void publishIfChanged(UUID teamId, int delta, ReliabilityReason reason, UUID referenceId, LocalDateTime now) {
        if (delta != 0) {
            eventPublisher.publishEvent(new ReliabilityScoreChangedEvent(ReputationSubjectType.TEAM, teamId, delta,
                    reason, referenceId, now));
        }
    }
}
