package com.phananh.arenaflow.application.event.match;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đội khác nhận kèo luồng B, mở thương lượng. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record PairingCreatedEvent(UUID pairingId, UUID matchId, UUID interestedTeamId, UUID authorTeamId,
                                  LocalDateTime occurredAt) {
}
