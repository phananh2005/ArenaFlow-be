package com.phananh.arenaflow.application.support.reputation;

import com.phananh.arenaflow.application.event.reputation.TeamVerificationChangedEvent;
import com.phananh.arenaflow.application.event.reputation.UserVerificationChangedEvent;
import com.phananh.arenaflow.domain.projection.VerificationChangeView;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

class VerificationRefresherTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final NoOpTransactionManager transactionManager = new NoOpTransactionManager();
    private final TransactionTemplate outer = new TransactionTemplate(transactionManager);
    private VerificationRefresher refresher;

    @BeforeEach
    void setUp() {
        refresher = new VerificationRefresher(teamRepository, userRepository, transactionManager, eventPublisher,
                Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
        when(userRepository.refreshVerification(any(), any(), anyInt(), anyInt(), anyDouble(), anyInt()))
                .thenReturn(List.of());
        when(teamRepository.refreshVerification(any(), any(), anyInt(), anyDouble(), anyInt())).thenReturn(List.of());
    }

    @Test
    void requiresActiveTransaction() {
        assertThatThrownBy(() -> refresher.refreshUsersAfterCommit(Set.of(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rolledBackAttemptDoesNotRefreshAndNextAttemptRefreshesExactlyOnce() {
        UUID user = UUID.randomUUID();
        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            refresher.refreshUsersAfterCommit(Set.of(user));
            throw new IllegalArgumentException("rollback");
        })).isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).refreshVerification(any(), any(), anyInt(), anyInt(), anyDouble(), anyInt());

        outer.executeWithoutResult(status -> {
            refresher.refreshUsersAfterCommit(Set.of(user));
            refresher.refreshUsersAfterCommit(Set.of(user));
        });

        ArgumentCaptor<UUID[]> ids = ArgumentCaptor.forClass(UUID[].class);
        verify(userRepository, times(1)).refreshVerification(ids.capture(), eq(NOW), anyInt(), anyInt(), anyDouble(),
                anyInt());
        assertThat(ids.getValue()).containsExactly(user);
        verify(teamRepository, never()).refreshVerification(any(), any(), anyInt(), anyDouble(), anyInt());
    }

    @Test
    void consecutiveTransactionsOnSameThreadRefreshOwnIdsOnly() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID team = UUID.randomUUID();

        outer.executeWithoutResult(status -> refresher.refreshUsersAfterCommit(Set.of(first)));
        outer.executeWithoutResult(status -> {
            refresher.refreshUsersAfterCommit(Set.of(second));
            refresher.refreshTeamsAfterCommit(Set.of(team));
        });

        ArgumentCaptor<UUID[]> ids = ArgumentCaptor.forClass(UUID[].class);
        verify(userRepository, times(2)).refreshVerification(ids.capture(), any(), anyInt(), anyInt(), anyDouble(),
                anyInt());
        assertThat(ids.getAllValues().get(0)).containsExactly(first);
        assertThat(ids.getAllValues().get(1)).containsExactly(second);
        verify(teamRepository, times(1)).refreshVerification(any(), any(), anyInt(), anyDouble(), anyInt());
    }

    @Test
    void publishesEventPerChangedSubject() {
        SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
        UUID user = UUID.randomUUID();
        UUID team = UUID.randomUUID();
        when(userRepository.refreshVerification(any(), any(), anyInt(), anyInt(), anyDouble(), anyInt()))
                .thenReturn(List.of(projections.createProjection(VerificationChangeView.class,
                        Map.of("subjectId", user, "verified", true))));
        when(teamRepository.refreshVerification(any(), any(), anyInt(), anyDouble(), anyInt()))
                .thenReturn(List.of(projections.createProjection(VerificationChangeView.class,
                        Map.of("subjectId", team, "verified", false))));

        refresher.refreshNow(Set.of(team), Set.of(user));

        verify(eventPublisher).publishEvent(new UserVerificationChangedEvent(user, true, NOW));
        verify(eventPublisher).publishEvent(new TeamVerificationChangedEvent(team, false, NOW));
    }

    @Test
    void refreshFailureIsSwallowed() {
        when(userRepository.refreshVerification(any(), any(), anyInt(), anyInt(), anyDouble(), anyInt()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> refresher.refreshNow(Set.of(), Set.of(UUID.randomUUID()))).doesNotThrowAnyException();
    }

    /** Transaction manager giả: không tài nguyên, chỉ để kích hoạt synchronization như transaction thật. */
    static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
