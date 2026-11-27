package com.phananh.arenaflow.application.event.match;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đội tạo kèo từ chối yêu cầu ghép kèo. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record MatchRequestRejectedEvent(UUID requestId, UUID matchId, UUID requesterTeamId, LocalDateTime occurredAt) {
}
