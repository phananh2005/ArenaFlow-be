package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.event.reputation.ReliabilityScoreChangedEvent;
import com.phananh.arenaflow.application.event.reputation.ReputationSubjectType;
import com.phananh.arenaflow.application.service.impl.ReputationServiceImpl;
import com.phananh.arenaflow.application.support.reputation.ReputationRules;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.projection.ScoreChangeView;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReputationServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID userId = UUID.randomUUID();
    private final UUID referenceId = UUID.randomUUID();
    private ReputationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReputationServiceImpl(userRepository, teamRepository, eventPublisher,
                Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

    @Test
    void mercenaryCompletionPassesRulesAndPublishesRealDelta() {
        UUID matchId = UUID.randomUUID();
        when(userRepository.applyMercenaryCompletion(userId, matchId, null, 1, ReputationRules.COMPLETION_POINTS,
                ReputationRules.STREAK_MILESTONE, ReputationRules.STREAK_BONUS_5, ReputationRules.STREAK_BONUS_10))
                .thenReturn(Optional.of(change(80, 83)));

        service.applyMercenaryCompletion(userId, matchId, null, 1, referenceId);

        verify(eventPublisher).publishEvent(new ReliabilityScoreChangedEvent(ReputationSubjectType.USER, userId, 3,
                ReliabilityReason.MERCENARY_COMPLETED, referenceId, NOW));
    }

    @Test
    void mercenaryCompletionAtCapDoesNotPublish() {
        when(userRepository.applyMercenaryCompletion(any(), any(), any(), anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt())).thenReturn(Optional.of(change(100, 100)));

        service.applyMercenaryCompletion(userId, UUID.randomUUID(), null, 0, referenceId);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void mercenaryCompletionWithoutUserRowIsProgrammingError() {
        when(userRepository.applyMercenaryCompletion(any(), any(), any(), anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyMercenaryCompletion(userId, null, UUID.randomUUID(), 0, referenceId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void userReportPenaltyReturnsPointsActuallyDeducted() {
        when(userRepository.applyReportPenalty(eq(userId), eq("NOSHOW"), aryEq(new int[] {5, 8, 12, 15})))
                .thenReturn(Optional.of(change(3, 0)));

        int points = service.applyUserReportPenalty(userId, Report.ReportType.NOSHOW, referenceId);

        assertThat(points).isEqualTo(3);
        verify(eventPublisher).publishEvent(new ReliabilityScoreChangedEvent(ReputationSubjectType.USER, userId, -3,
                ReliabilityReason.REPORT_RECEIVED, referenceId, NOW));
    }

    @Test
    void teamReportPenaltyUsesTeamTable() {
        UUID teamId = UUID.randomUUID();
        when(teamRepository.applyReportPenalty(eq(teamId), eq("BAD_COMMUNICATION"), any()))
                .thenReturn(Optional.of(change(80, 78)));

        assertThat(service.applyReportPenalty(teamId, Report.ReportType.BAD_COMMUNICATION, referenceId)).isEqualTo(2);
        verify(userRepository, never()).applyReportPenalty(any(), any(), any());
    }

    @Test
    void refundRunsEvenForZeroPointsButPublishesOnlyWhenPositive() {
        when(userRepository.refundReportPenalty(userId, "NOSHOW", 0)).thenReturn(1);

        service.refundUserReportPenalty(userId, Report.ReportType.NOSHOW, 0, referenceId);

        verify(userRepository).refundReportPenalty(userId, "NOSHOW", 0);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void adjustUserReliabilitySkipsNoOpChange() {
        service.adjustUserReliability(userId, new ReliabilityChange(0, ReliabilityReason.MATCH_COMPLETED, referenceId,
                false));

        verify(userRepository, never()).adjustReliability(any(), anyInt(), anyBoolean());
    }

    @Test
    void adjustUserReliabilityBreaksStreakAndPublishes() {
        when(userRepository.adjustReliability(userId, -3, true)).thenReturn(1);

        service.adjustUserReliability(userId, new ReliabilityChange(-3,
                ReliabilityReason.MERCENARY_WITHDRAWN_AFTER_APPROVED, referenceId, true));

        verify(eventPublisher).publishEvent(new ReliabilityScoreChangedEvent(ReputationSubjectType.USER, userId, -3,
                ReliabilityReason.MERCENARY_WITHDRAWN_AFTER_APPROVED, referenceId, NOW));
    }

    private ScoreChangeView change(int oldScore, int newScore) {
        return projections.createProjection(ScoreChangeView.class, Map.of("subjectType", "USER", "subjectId", userId,
                "oldScore", oldScore, "newScore", newScore));
    }
}
