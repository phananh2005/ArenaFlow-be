package com.phananh.arenaflow.application.support.reputation;

import com.phananh.arenaflow.application.event.reputation.TeamVerificationChangedEvent;
import com.phananh.arenaflow.domain.projection.VerificationChangeView;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamVerificationRefresherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T02:00:00Z"), ZoneId.systemDefault());
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 9, 0);

    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final TeamVerificationRefresher refresher =
            new TeamVerificationRefresher(teamRepository, transactionManager, publisher, CLOCK);
    private final UUID teamA = UUID.randomUUID();
    private final UUID teamB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(teamRepository.refreshVerification(any(), any(), anyInt(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of());
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        if (TransactionSynchronizationManager.hasResource(refresher)) {
            TransactionSynchronizationManager.unbindResource(refresher);
        }
    }

    @Test
    void rolledBackAttemptDoesNotRefreshAndNextAttemptRefreshesExactlyOnce() {
        TransactionSynchronizationManager.initSynchronization();
        refresher.refreshAfterCommit(List.of(teamA));
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        TransactionSynchronizationManager.initSynchronization();
        refresher.refreshAfterCommit(List.of(teamB));
        commit();

        ArgumentCaptor<UUID[]> ids = ArgumentCaptor.forClass(UUID[].class);
        verify(teamRepository, times(1)).refreshVerification(ids.capture(), eq(NOW), anyInt(), anyDouble(), anyInt(),
                anyInt());
        assertThat(ids.getValue()).containsExactly(teamB);
    }

    @Test
    void consecutiveRequestsOnSameThreadEachRefreshTheirOwnTeamsOnce() {
        TransactionSynchronizationManager.initSynchronization();
        refresher.refreshAfterCommit(List.of(teamA));
        refresher.refreshAfterCommit(List.of(teamA, teamB));
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        commit();

        TransactionSynchronizationManager.initSynchronization();
        refresher.refreshAfterCommit(List.of(teamB));
        commit();

        ArgumentCaptor<UUID[]> ids = ArgumentCaptor.forClass(UUID[].class);
        verify(teamRepository, times(2)).refreshVerification(ids.capture(), any(), anyInt(), anyDouble(), anyInt(),
                anyInt());
        assertThat(ids.getAllValues().get(0)).containsExactlyInAnyOrder(teamA, teamB);
        assertThat(ids.getAllValues().get(1)).containsExactly(teamB);
    }

    @Test
    void refreshNowRunsInNewTransactionAndPublishesEventPerChangedTeam() {
        when(teamRepository.refreshVerification(any(), any(), anyInt(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of(changed(teamA, true)));

        refresher.refreshNow(List.of(teamA, teamB));

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(teamRepository).refreshVerification(any(), eq(NOW), eq(ReputationRules.BADGE_MIN_COMPLETED_EXCLUSIVE),
                eq(ReputationRules.BADGE_MIN_AVG_EXCLUSIVE), eq(ReputationRules.BADGE_MIN_SCORE),
                eq(ReputationRules.LATE_CANCEL_WINDOW_HOURS));
        verify(publisher).publishEvent(new TeamVerificationChangedEvent(teamA, true, NOW));
    }

    @Test
    void refreshFailureIsLoggedNotThrown() {
        when(teamRepository.refreshVerification(any(), any(), anyInt(), anyDouble(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> refresher.refreshNow(List.of(teamA))).doesNotThrowAnyException();
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void emptyIdsDoNotQuery() {
        refresher.refreshNow(List.of());

        verify(teamRepository, never()).refreshVerification(any(), any(), anyInt(), anyDouble(), anyInt(), anyInt());
    }

    @Test
    void requiresActiveTransaction() {
        assertThatThrownBy(() -> refresher.refreshAfterCommit(List.of(teamA))).isInstanceOf(IllegalStateException.class);
    }

    private void commit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void complete(int status) {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(sync -> sync.afterCompletion(status));
        TransactionSynchronizationManager.clearSynchronization();
    }

    private VerificationChangeView changed(UUID teamId, boolean verified) {
        return new SpelAwareProxyProjectionFactory().createProjection(VerificationChangeView.class,
                Map.of("teamId", teamId, "verified", verified));
    }
}
