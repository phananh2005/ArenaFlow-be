package com.phananh.arenaflow.application.listener;

import com.phananh.arenaflow.application.event.match.MatchExpiredEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryGameCancelledEvent;
import com.phananh.arenaflow.application.event.reputation.TeamVerificationChangedEvent;
import com.phananh.arenaflow.application.event.user.UserRegisteredEvent;
import com.phananh.arenaflow.application.service.NotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationListenersTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);
    private static final List<Class<?>> LISTENERS = List.of(MatchNotificationListener.class,
            MercenaryNotificationListener.class, ReputationNotificationListener.class, AccountNotificationListener.class);

    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);

    @Test
    void everyHandlerIsAsyncAfterCommitWithoutTransaction() {
        List<Method> handlers = LISTENERS.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> Modifier.isPublic(method.getModifiers())).toList();

        assertThat(handlers).hasSize(28);
        assertThat(handlers).allSatisfy(method -> {
            assertThat(method.getAnnotation(Async.class).value()).isEqualTo("notificationExecutor");
            assertThat(method.getAnnotation(TransactionalEventListener.class).phase())
                    .isEqualTo(TransactionPhase.AFTER_COMMIT);
            assertThat(method.getAnnotation(Transactional.class)).isNull();
        });
        assertThat(handlers).extracting(method -> method.getParameterTypes()[0]).doesNotHaveDuplicates();
    }

    @Test
    void delegatesToDispatcher() {
        MatchExpiredEvent expired = new MatchExpiredEvent(UUID.randomUUID(), UUID.randomUUID(), NOW);
        MercenaryGameCancelledEvent cancelled = new MercenaryGameCancelledEvent(UUID.randomUUID(), null, List.of(), NOW);
        TeamVerificationChangedEvent verification = new TeamVerificationChangedEvent(UUID.randomUUID(), true, NOW);
        UserRegisteredEvent registered = new UserRegisteredEvent(UUID.randomUUID(), NOW);

        new MatchNotificationListener(dispatcher).on(expired);
        new MercenaryNotificationListener(dispatcher).on(cancelled);
        new ReputationNotificationListener(dispatcher).on(verification);
        new AccountNotificationListener(dispatcher).on(registered);

        verify(dispatcher).matchExpired(expired);
        verify(dispatcher).mercenaryGameCancelled(cancelled);
        verify(dispatcher).teamVerificationChanged(verification);
        verify(dispatcher).userRegistered(registered);
    }

    @Test
    void dispatcherFailureIsSwallowed() {
        doThrow(new IllegalStateException("db down")).when(dispatcher).userRegistered(any());

        assertThatCode(() -> new AccountNotificationListener(dispatcher)
                .on(new UserRegisteredEvent(UUID.randomUUID(), NOW))).doesNotThrowAnyException();
    }
}
