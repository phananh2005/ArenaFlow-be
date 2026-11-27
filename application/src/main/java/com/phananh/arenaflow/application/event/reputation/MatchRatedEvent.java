package com.phananh.arenaflow.application.event.reputation;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một đội đánh giá đội kia sau trận. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record MatchRatedEvent(UUID ratingId, UUID matchId, UUID raterTeamId, UUID rateeTeamId, boolean isNoShow,
                              boolean matchCompleted, LocalDateTime occurredAt) {
}
