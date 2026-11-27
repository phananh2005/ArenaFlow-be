package com.phananh.arenaflow.api.config;

import com.phananh.arenaflow.application.support.notification.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AsyncConfigTest {

    private static final NotificationProperties PROPERTIES = new NotificationProperties(
            new NotificationProperties.Reminder(200, 50, Duration.ofHours(24)),
            new NotificationProperties.Retention(200, 1000, 100),
            new NotificationProperties.Executor(2, 4, 1000));

    @Test
    void notificationExecutorIsBoundedAndRunsOnCallerWhenFull() {
        ThreadPoolTaskExecutor executor = new AsyncConfig().notificationExecutor(PROPERTIES);
        executor.initialize();
        try {
            ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
            assertThat(pool.getCorePoolSize()).isEqualTo(2);
            assertThat(pool.getMaximumPoolSize()).isEqualTo(4);
            assertThat(executor.getQueueCapacity()).isEqualTo(1000);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("notification-");
            assertThat(pool.getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void uncaughtExceptionHandlerOnlyLogs() throws NoSuchMethodException {
        assertThatCode(() -> new AsyncConfig().getAsyncUncaughtExceptionHandler().handleUncaughtException(
                new IllegalStateException("boom"), Object.class.getMethod("toString"))).doesNotThrowAnyException();
    }
}
