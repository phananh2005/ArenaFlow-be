package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.service.impl.RetentionJobServiceImpl;
import com.phananh.arenaflow.application.support.notification.NotificationProperties;
import com.phananh.arenaflow.domain.repository.RetentionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetentionJobServiceImplTest {

    /** 03:30 giờ Việt Nam. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 3, 30);

    private final RetentionRepository repository = mock(RetentionRepository.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final RetentionJobService service = new RetentionJobServiceImpl(repository, transactionTemplate,
            new NotificationProperties(new NotificationProperties.Reminder(200, 50, Duration.ofHours(24)),
                    new NotificationProperties.Retention(2, 3, 4), new NotificationProperties.Executor(2, 4, 1000)),
            Clock.fixed(Instant.parse("2026-11-20T20:30:00Z"), ZoneId.systemDefault()));

    @BeforeEach
    void runCallbacksInline() {
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
    }

    @Test
    void conversationsUseNinetyDayCutoffAndStopOnShortBatch() {
        when(repository.deleteExpiredConversations(NOW.minusDays(90), 2)).thenReturn(2, 2, 1);

        assertThat(service.purgeExpiredConversations()).isEqualTo(5);
        verify(repository, times(3)).deleteExpiredConversations(NOW.minusDays(90), 2);
    }

    @Test
    void notificationsUseThirtyDayCutoffAndCapBatchesPerRun() {
        when(repository.deleteExpiredNotifications(NOW.minusDays(30), 3)).thenReturn(3);

        assertThat(service.purgeExpiredNotifications()).isEqualTo(12);
        verify(repository, times(4)).deleteExpiredNotifications(NOW.minusDays(30), 3);
    }

    @Test
    void failedBatchStopsTaskAndKeepsCount() {
        when(repository.deleteExpiredNotifications(any(), anyInt())).thenReturn(3)
                .thenThrow(new QueryTimeoutException("timeout"));

        assertThat(service.purgeExpiredNotifications()).isEqualTo(3);
    }

    @Test
    void remindersOlderThanThirtyDaysAreDeletedInOneStatement() {
        when(repository.deleteRemindersSentBefore(eq(NOW.minusDays(30)))).thenReturn(7);

        assertThat(service.purgeOldReminders()).isEqualTo(7);
    }
}
