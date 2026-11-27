package com.phananh.arenaflow.application.event.mercenary;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Trận của các đơn đã duyệt bị huỷ; đúng một trong {@code matchId}, {@code externalMatchId} khác null. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record MercenaryGameCancelledEvent(UUID matchId, UUID externalMatchId, List<UUID> applicationIds, LocalDateTime occurredAt) {
}
