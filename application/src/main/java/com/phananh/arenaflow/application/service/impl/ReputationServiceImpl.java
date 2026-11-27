package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.event.reputation.ReliabilityScoreChangedEvent;
import com.phananh.arenaflow.application.event.reputation.ReputationSubjectType;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReputationServiceImpl implements ReputationService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** 1 truy vấn (bỏ qua khi không đổi gì); 0 dòng → IllegalStateException (user phải tồn tại). */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void adjustUserReliability(UUID userId, ReliabilityChange change) {
        if (change.delta() == 0 && !change.breaksCleanStreak()) {
            return;
        }
        if (userRepository.adjustReliability(userId, change.delta(), change.breaksCleanStreak()) == 0) {
            throw new IllegalStateException("User not found for reliability change");
        }
        if (change.delta() != 0) {
            publish(ReputationSubjectType.USER, userId, change.delta(), change.reason(), change.referenceId());
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int applyReportPenalty(UUID teamId, Report.ReportType type, UUID reportId) {
        ScoreChangeView change = teamRepository.applyReportPenalty(teamId, type.name(), ReportPenaltyPolicy.tiers(type))
                .orElseThrow(() -> new IllegalStateException("Team not found for report penalty"));
        return publishPenalty(ReputationSubjectType.TEAM, teamId, change, reportId);
    }

    /** Luôn chạy (giảm bộ đếm cả khi không hoàn điểm); không khôi phục chuỗi trận sạch. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void refundReportPenalty(UUID teamId, Report.ReportType type, int points, UUID reportId) {
        if (teamRepository.refundReportPenalty(teamId, type.name(), points) == 0) {
            throw new IllegalStateException("Team not found for report refund");
        }
        if (points > 0) {
            publish(ReputationSubjectType.TEAM, teamId, points, ReliabilityReason.REPORT_REJECTED_REFUND, reportId);
        }
    }

    /**
     * Một câu khoá dòng user, cộng điểm hoàn thành + thưởng sao, cập nhật chuỗi trận sạch và thưởng mốc; 0 dòng →
     * IllegalStateException. Phát event với thay đổi thực (sau chặn [0, 100]).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyMercenaryCompletion(UUID userId, UUID matchId, UUID externalMatchId, int ratingBonus,
                                         UUID ratingId) {
        ScoreChangeView change = userRepository.applyMercenaryCompletion(userId, matchId, externalMatchId, ratingBonus,
                        ReputationRules.COMPLETION_POINTS, ReputationRules.STREAK_MILESTONE,
                        ReputationRules.STREAK_BONUS_5, ReputationRules.STREAK_BONUS_10)
                .orElseThrow(() -> new IllegalStateException("User not found for mercenary completion"));
        int delta = change.getNewScore() - change.getOldScore();
        if (delta != 0) {
            publish(ReputationSubjectType.USER, userId, delta, ReliabilityReason.MERCENARY_COMPLETED, ratingId);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int applyUserReportPenalty(UUID userId, Report.ReportType type, UUID reportId) {
        ScoreChangeView change = userRepository.applyReportPenalty(userId, type.name(), ReportPenaltyPolicy.tiers(type))
                .orElseThrow(() -> new IllegalStateException("User not found for report penalty"));
        return publishPenalty(ReputationSubjectType.USER, userId, change, reportId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void refundUserReportPenalty(UUID userId, Report.ReportType type, int points, UUID reportId) {
        if (userRepository.refundReportPenalty(userId, type.name(), points) == 0) {
            throw new IllegalStateException("User not found for report refund");
        }
        if (points > 0) {
            publish(ReputationSubjectType.USER, userId, points, ReliabilityReason.REPORT_REJECTED_REFUND, reportId);
        }
    }

    private int publishPenalty(ReputationSubjectType type, UUID id, ScoreChangeView change, UUID reportId) {
        int points = change.getOldScore() - change.getNewScore();
        if (points > 0) {
            publish(type, id, -points, ReliabilityReason.REPORT_RECEIVED, reportId);
        }
        return points;
    }

    private void publish(ReputationSubjectType type, UUID id, int delta, ReliabilityReason reason, UUID referenceId) {
        eventPublisher.publishEvent(new ReliabilityScoreChangedEvent(type, id, delta, reason, referenceId,
                LocalDateTime.now(clock)));
    }
}
