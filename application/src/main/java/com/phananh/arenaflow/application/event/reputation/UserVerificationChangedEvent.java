package com.phananh.arenaflow.application.event.reputation;

import java.time.LocalDateTime;
import java.util.UUID;

/** Huy hiệu xác minh cá nhân vừa được cấp / thu hồi. */
public record UserVerificationChangedEvent(UUID userId, boolean verified, LocalDateTime occurredAt) {
}
