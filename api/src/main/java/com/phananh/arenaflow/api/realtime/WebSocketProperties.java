package com.phananh.arenaflow.api.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Kênh realtime. {@code allowedOriginPatterns} rỗng = chỉ same-origin (fail-closed; client native không gửi Origin);
 * NEVER "*". Heartbeat hai chiều {@code heartbeatMs} (mặc định 10 giây) để phát hiện kết nối chết.
 */
@ConfigurationProperties(prefix = "arenaflow.websocket")
public record WebSocketProperties(List<String> allowedOriginPatterns, Long heartbeatMs) {

    private static final long DEFAULT_HEARTBEAT_MS = 10_000L;

    public WebSocketProperties {
        allowedOriginPatterns = allowedOriginPatterns == null ? List.of()
                : allowedOriginPatterns.stream().map(String::trim).filter(pattern -> !pattern.isEmpty()).toList();
        if (allowedOriginPatterns.contains("*")) {
            throw new IllegalArgumentException("arenaflow.websocket.allowed-origin-patterns must not allow every origin");
        }
        heartbeatMs = heartbeatMs == null ? DEFAULT_HEARTBEAT_MS : heartbeatMs;
    }
}
