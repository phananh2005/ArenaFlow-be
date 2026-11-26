package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.event.reputation.ReliabilityScoreChangedEvent;
import com.phananh.arenaflow.application.event.reputation.ReputationSubjectType;
import com.phananh.arenaflow.application.service.impl.ReputationServiceImpl;
import com.phananh.arenaflow.application.support.reputation.ReputationRules;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.projection.ScoreChangeView;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReputationServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);

    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneId.systemDefault());
    private final ReputationServiceImpl service = new ReputationServiceImpl(teamRepository, userRepository, publisher, clock);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID subjectId = UUID.randomUUID();
    private final UUID referenceId = UUID.randomUUID();
    private final UUID matchId = UUID.randomUUID();
    private final UUID teamA = UUID.randomUUID();
    private final UUID teamB = UUID.randomUUID();

    @Test
    void zeroDeltaWithoutStreakBreakRunsNoQuery() {
        service.adjustTeamReliability(subjectId, new ReliabilityChange(0, ReliabilityReason.MATCH_CANCELLED_AFTER_CONFIRMED,
                referenceId, false));

        verifyNoInteractions(teamRepository, publisher);
    }

    @Test
    void adjustsTeamInOneAtomicUpdateAndPublishesEvent() {
        when(teamRepository.adjustReliability(subjectId, -5, true)).thenReturn(1);

        service.adjustTeamReliability(subjectId, new ReliabilityChange(-5,
                ReliabilityReason.MATCH_CANCELLED_AFTER_CONFIRMED, referenceId, true));

        verify(teamRepository).adjustReliability(subjectId, -5, true);
        ArgumentCaptor<ReliabilityScoreChangedEvent> event = ArgumentCaptor.forClass(ReliabilityScoreChangedEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new ReliabilityScoreChangedEvent(ReputationSubjectType.TEAM, subjectId, -5,
                ReliabilityReason.MATCH_CANCELLED_AFTER_CONFIRMED, referenceId, NOW));
    }

    @Test
    void missingTeamIsProgrammingError() {
        when(teamRepository.adjustReliability(subjectId, -2, true)).thenReturn(0);

        assertThatThrownBy(() -> service.adjustTeamReliability(subjectId, new ReliabilityChange(-2,
                ReliabilityReason.MATCH_REQUEST_WITHDRAWN_AFTER_ACCEPTED, referenceId, true)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(publisher);
    }

    @Test
    void adjustsUserWithSubjectTypeUser() {
        when(userRepository.adjustReliability(subjectId, -3, false)).thenReturn(1);

        service.adjustUserReliability(subjectId, new ReliabilityChange(-3,
                ReliabilityReason.MATCH_CANCELLED_AFTER_CONFIRMED, referenceId, false));

        ArgumentCaptor<ReliabilityScoreChangedEvent> event = ArgumentCaptor.forClass(ReliabilityScoreChangedEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().subjectType()).isEqualTo(ReputationSubjectType.USER);
    }

    @Test
    void applyMatchCompletionSendsEntriesInOneStatementAndPublishesRealDeltaPerChangedTeam() {
        when(teamRepository.applyCompletion(eq(matchId), any(), any(), any(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(change(teamA, 80, 87), change(teamB, 100, 100)));

        service.applyMatchCompletion(matchId, List.of(new CompletionEntry(teamA, true, 0),
                new CompletionEntry(teamB, true, 1)));

        InOrder order = inOrder(teamRepository);
        order.verify(teamRepository).lockForReputation(new UUID[]{teamA, teamB});
        order.verify(teamRepository).applyCompletion(eq(matchId), eq(new UUID[]{teamA, teamB}), eq(new boolean[]{true, true}),
                eq(new int[]{0, 1}), eq(ReputationRules.COMPLETION_POINTS), eq(ReputationRules.STREAK_MILESTONE),
                eq(ReputationRules.STREAK_BONUS_5), eq(ReputationRules.STREAK_BONUS_10));
        ArgumentCaptor<ReliabilityScoreChangedEvent> events = ArgumentCaptor.forClass(ReliabilityScoreChangedEvent.class);
        verify(publisher, times(1)).publishEvent(events.capture());
        assertThat(events.getValue()).isEqualTo(new ReliabilityScoreChangedEvent(ReputationSubjectType.TEAM, teamA, 7,
                ReliabilityReason.MATCH_COMPLETED, matchId, NOW));
    }

    @Test
    void applyMatchCompletionFailsWhenATeamRowIsMissing() {
        when(teamRepository.applyCompletion(eq(matchId), any(), any(), any(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(change(teamA, 80, 82)));

        assertThatThrownBy(() -> service.applyMatchCompletion(matchId, List.of(new CompletionEntry(teamA, true, 0),
                new CompletionEntry(teamB, true, 0)))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void applyReportPenaltyReturnsActualDeductionAndPublishesNegativeDelta() {
        UUID reportId = UUID.randomUUID();
        when(teamRepository.applyReportPenalty(eq(teamA), eq("NOSHOW"), eq(new int[]{5, 8, 12, 15})))
                .thenReturn(Optional.of(change(teamA, 3, 0)));

        int points = service.applyReportPenalty(teamA, Report.ReportType.NOSHOW, reportId);

        assertThat(points).isEqualTo(3);
        verify(publisher).publishEvent(new ReliabilityScoreChangedEvent(ReputationSubjectType.TEAM, teamA, -3,
                ReliabilityReason.REPORT_RECEIVED, reportId, NOW));
    }

    @Test
    void applyReportPenaltyAtZeroScorePublishesNothing() {
        when(teamRepository.applyReportPenalty(eq(teamA), eq("BAD_PLAY"), any())).thenReturn(Optional.of(change(teamA, 0, 0)));

        assertThat(service.applyReportPenalty(teamA, Report.ReportType.BAD_PLAY, UUID.randomUUID())).isZero();
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void applyReportPenaltyFailsWhenTeamMissing() {
        when(teamRepository.applyReportPenalty(eq(teamA), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyReportPenalty(teamA, Report.ReportType.NOSHOW, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refundAlwaysDecrementsCounterAndPublishesOnlyWhenPointsPositive() {
        UUID reportId = UUID.randomUUID();
        when(teamRepository.refundReportPenalty(teamA, "NOSHOW", 5)).thenReturn(1);
        when(teamRepository.refundReportPenalty(teamA, "NOSHOW", 0)).thenReturn(1);

        service.refundReportPenalty(teamA, Report.ReportType.NOSHOW, 5, reportId);
        service.refundReportPenalty(teamA, Report.ReportType.NOSHOW, 0, reportId);

        verify(teamRepository).refundReportPenalty(teamA, "NOSHOW", 0);
        verify(publisher, times(1)).publishEvent(new ReliabilityScoreChangedEvent(ReputationSubjectType.TEAM, teamA, 5,
                ReliabilityReason.REPORT_REJECTED_REFUND, reportId, NOW));
    }

    @Test
    void refundFailsWhenTeamMissing() {
        when(teamRepository.refundReportPenalty(teamA, "NOSHOW", 5)).thenReturn(0);

        assertThatThrownBy(() -> service.refundReportPenalty(teamA, Report.ReportType.NOSHOW, 5, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    private ScoreChangeView change(UUID teamId, int oldScore, int newScore) {
        return projections.createProjection(ScoreChangeView.class,
                Map.of("teamId", teamId, "oldScore", oldScore, "newScore", newScore));
    }
}
