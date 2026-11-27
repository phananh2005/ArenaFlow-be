package com.phananh.arenaflow.application.support.mercenary;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kích thước lô của job vòng đời tin tuyển (chạy trong job vòng đời kèo); {@code cancelNoticeLookbackDays} giới hạn
 * phạm vi quét trận bị huỷ cần báo mercenary.
 */
@ConfigurationProperties(prefix = "arenaflow.mercenary.lifecycle")
public record MercenaryLifecycleProperties(int batchSize, int maxBatchesPerRun, int cancelNoticeLookbackDays) {
}
