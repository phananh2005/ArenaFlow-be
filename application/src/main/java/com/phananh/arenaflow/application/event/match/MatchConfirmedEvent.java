package com.phananh.arenaflow.application.event.match;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kèo vừa được ghép. {@code acceptedRequestId} chỉ có ở luồng A, {@code pairingId} chỉ có ở luồng B. Phát trong
 * transaction ghi; listener xử lý sau khi commit.
 */
public record MatchConfirmedEvent(UUID matchId, UUID acceptedRequestId, UUID pairingId, UUID authorTeamId,
                                  UUID opponentTeamId, LocalDateTime occurredAt) {
}
