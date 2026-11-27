package com.phananh.arenaflow.application.event.reputation;

import java.time.LocalDateTime;
import java.util.UUID;

/** Người dùng được cấp / bị thu hồi huy hiệu xác minh. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record UserVerificationChangedEvent(UUID userId, boolean verified, LocalDateTime occurredAt) {
}
