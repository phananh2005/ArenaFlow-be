package com.phananh.arenaflow.application.support.reputation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kích thước lô của job đối soát huy hiệu xác minh đội; biểu thức cron do scheduler đọc trực tiếp. */
@ConfigurationProperties(prefix = "arenaflow.reputation.verification")
public record TeamVerificationProperties(int batchSize) {
}
