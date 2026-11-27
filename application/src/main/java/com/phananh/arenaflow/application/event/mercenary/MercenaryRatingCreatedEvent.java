package com.phananh.arenaflow.application.event.mercenary;

import com.phananh.arenaflow.application.support.mercenary.MercenarySide;
import java.time.LocalDateTime;
import java.util.UUID;

/** Một bên của đơn đánh giá bên kia; {@code direction} là bên chấm. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record MercenaryRatingCreatedEvent(UUID ratingId, UUID applicationId, MercenarySide direction, LocalDateTime occurredAt) {
}
