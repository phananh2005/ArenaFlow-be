package com.phananh.arenaflow.api.scheduler;

import com.phananh.arenaflow.application.service.RetentionJobService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kiểm thuần (không Spring context): thứ tự tác vụ và cô lập lỗi; đăng ký cron kiểm ở test tích hợp. */
class RetentionSchedulerTest {

    private final RetentionJobService service = mock(RetentionJobService.class);
    private final RetentionScheduler scheduler = new RetentionScheduler(service);

    @Test
    void runsAllThreeTasksEvenWhenOneFails() {
        when(service.purgeExpiredConversations()).thenThrow(new IllegalStateException("db down"));

        assertThatCode(scheduler::purgeExpired).doesNotThrowAnyException();

        verify(service).purgeExpiredConversations();
        verify(service).purgeExpiredNotifications();
        verify(service).purgeOldReminders();
    }

    @Test
    void usesConfigurableCronWithoutExplicitZone() throws NoSuchMethodException {
        Scheduled scheduled = RetentionScheduler.class.getMethod("purgeExpired").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${arenaflow.notification.retention.cron}");
        assertThat(scheduled.zone()).isEmpty();
    }
}
