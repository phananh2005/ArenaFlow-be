package com.phananh.arenaflow.application.event.mercenary;

import java.time.LocalDateTime;
import java.util.UUID;

/** Có đánh giá mới theo đơn; {@code direction} = {@code TEAM_TO_MERCENARY} | {@code MERCENARY_TO_TEAM}. */
public record MercenaryRatingCreatedEvent(UUID ratingId, UUID applicationId, String direction,
                                          LocalDateTime occurredAt) {
}
