package com.phananh.arenaflow.application.event.reputation;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một đội vừa đánh giá đội kia; {@code matchCompleted} = đánh giá này chuyển trận sang COMPLETED. */
public record MatchRatedEvent(UUID ratingId, UUID matchId, UUID raterTeamId, UUID rateeTeamId, boolean isNoShow,
                              boolean matchCompleted, LocalDateTime occurredAt) {
}
