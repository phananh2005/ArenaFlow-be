package com.phananh.arenaflow.api.scheduler;

import com.phananh.arenaflow.application.service.RetentionJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Chỉ kích hoạt job xoá hạn lưu; cron "-" là tắt job. Lỗi một tác vụ không chặn tác vụ sau. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionScheduler {

    private final RetentionJobService retentionJobService;

    @Scheduled(cron = "${arenaflow.notification.retention.cron}")
    public void purgeExpired() {
        runSafely("conversations", retentionJobService::purgeExpiredConversations);
        runSafely("notifications", retentionJobService::purgeExpiredNotifications);
        runSafely("reminders", retentionJobService::purgeOldReminders);
    }

    private void runSafely(String task, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.error("Retention task {} failed", task, exception);
        }
    }
}
