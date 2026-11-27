package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đánh giá theo đơn; {@code direction} = TEAM_TO_MERCENARY | MERCENARY_TO_TEAM. */
public record MercenaryRatingResponse(UUID ratingId, UUID applicationId, String source, UUID matchId,
                                      UUID externalMatchId, String direction, UUID teamId, UUID mercenaryUserId,
                                      boolean isNoShow, Integer score, String comment, LocalDateTime createdAt) {
}
