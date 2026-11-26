package com.phananh.arenaflow.application.event.reputation;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đội vừa được cấp ({@code verified = true}) hoặc bị thu hồi huy hiệu xác minh. */
public record TeamVerificationChangedEvent(UUID teamId, boolean verified, LocalDateTime occurredAt) {
}
