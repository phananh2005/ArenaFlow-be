package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đánh giá mercenary dành cho đội. */
public record TeamMercenaryRatingResponse(UUID ratingId, String source, UUID matchId, UUID externalMatchId,
                                          LocalDateTime matchStartsAt, MercenaryUserBriefResponse mercenary,
                                          Integer score, String comment, LocalDateTime createdAt) {
}
