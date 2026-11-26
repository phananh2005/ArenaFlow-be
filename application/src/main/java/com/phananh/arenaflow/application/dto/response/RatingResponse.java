package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record RatingResponse(UUID ratingId, UUID matchId, UUID raterTeamId, UUID rateeTeamId, boolean isNoShow,
                             Integer score, String comment, Integer raterTeamScore, Integer rateeTeamScore,
                             LocalDateTime createdAt) {
}
