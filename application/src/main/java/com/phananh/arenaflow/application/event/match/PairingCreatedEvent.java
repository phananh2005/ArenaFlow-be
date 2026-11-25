package com.phananh.arenaflow.application.event.match;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một đội vừa nhận kèo luồng B. Phát trong transaction ghi; listener xử lý sau khi commit. */
public record PairingCreatedEvent(UUID pairingId, UUID matchId, UUID interestedTeamId, UUID authorTeamId,
                                  LocalDateTime occurredAt) {
}
