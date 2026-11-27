package com.phananh.arenaflow.application.event.match;

import java.time.LocalDateTime;
import java.util.UUID;

/** Kèo đã ghép tới giờ kết thúc. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record MatchArchivedEvent(UUID matchId, UUID authorTeamId, UUID opponentTeamId, LocalDateTime matchEndTime,
                                 LocalDateTime occurredAt) {
}
