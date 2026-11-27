package com.phananh.arenaflow.application.event.match;

import java.time.LocalDateTime;
import java.util.UUID;

/** Kèo đã ghép: luồng A có {@code acceptedRequestId}, luồng B có {@code pairingId}. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record MatchConfirmedEvent(UUID matchId, UUID acceptedRequestId, UUID pairingId, UUID authorTeamId, UUID opponentTeamId,
                                  LocalDateTime occurredAt) {
}
