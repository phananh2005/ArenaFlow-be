package com.phananh.arenaflow.application.event.reputation;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đội được cấp / bị thu hồi huy hiệu xác minh. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record TeamVerificationChangedEvent(UUID teamId, boolean verified, LocalDateTime occurredAt) {
}
