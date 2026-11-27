package com.phananh.arenaflow.application.event.mercenary;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Trận bị huỷ — báo mọi mercenary APPROVED vừa được đánh dấu đã báo ({@code applicationIds}); đúng một trong
 * {@code matchId} / {@code externalMatchId} khác null.
 */
public record MercenaryGameCancelledEvent(UUID matchId, UUID externalMatchId, List<UUID> applicationIds,
                                          LocalDateTime occurredAt) {
}
