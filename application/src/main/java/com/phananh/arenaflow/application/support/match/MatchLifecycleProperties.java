package com.phananh.arenaflow.application.support.match;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kích thước lô của job vòng đời kèo; biểu thức cron do scheduler đọc trực tiếp. */
@ConfigurationProperties(prefix = "arenaflow.match.lifecycle")
public record MatchLifecycleProperties(int batchSize, int maxBatchesPerRun) {
}
