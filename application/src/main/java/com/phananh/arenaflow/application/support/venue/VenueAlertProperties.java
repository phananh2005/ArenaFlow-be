package com.phananh.arenaflow.application.support.venue;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kích thước job cảnh báo địa điểm lâu không được chọn; biểu thức cron do scheduler đọc trực tiếp. */
@ConfigurationProperties(prefix = "arenaflow.venue.stale-alert")
public record VenueAlertProperties(int staleDays, int batchSize, int maxBatchesPerRun) {
}
