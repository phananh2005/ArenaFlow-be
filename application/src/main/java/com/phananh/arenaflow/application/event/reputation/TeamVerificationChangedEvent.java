package com.phananh.arenaflow.application.event.reputation;

import java.time.LocalDateTime;
import java.util.UUID;

/** Huy hiệu xác minh của đội vừa được cấp / thu hồi. */
public record TeamVerificationChangedEvent(UUID teamId, boolean verified, LocalDateTime occurredAt) {
}
