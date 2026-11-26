package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

/** {@code received} luôn null khi {@code given} null — không tiết lộ đối phương đã đánh giá hay chưa. */
public record MatchRatingsResponse(UUID matchId, UUID teamId, RatingResponse given, RatingResponse received) {
}
