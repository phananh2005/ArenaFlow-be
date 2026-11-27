package com.phananh.arenaflow.application.event.match;

import java.time.LocalDateTime;
import java.util.UUID;

/** Kèo hết hạn mà chưa ghép được. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record MatchExpiredEvent(UUID matchId, UUID authorTeamId, LocalDateTime occurredAt) {
}
