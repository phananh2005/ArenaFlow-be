package com.phananh.arenaflow.application.support.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Kích thước lô của job nhắc / xoá hạn lưu và executor của listener; biểu thức cron do scheduler đọc trực tiếp. */
@ConfigurationProperties(prefix = "arenaflow.notification")
public record NotificationProperties(Reminder reminder, Retention retention, Executor executor) {

    public record Reminder(int batchSize, int maxBatchesPerRun, Duration ratingLookback) {
    }

    public record Retention(int conversationBatchSize, int notificationBatchSize, int maxBatchesPerRun) {
    }

    public record Executor(int coreSize, int maxSize, int queueCapacity) {
    }
}
