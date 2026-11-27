package com.phananh.arenaflow.application.event.user;

import java.time.LocalDateTime;
import java.util.UUID;

/** Tài khoản mới vừa được tạo. */
public record UserRegisteredEvent(UUID userId, LocalDateTime occurredAt) {
}
