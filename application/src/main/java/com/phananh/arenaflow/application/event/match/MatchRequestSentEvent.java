package com.phananh.arenaflow.application.event.match;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đội gửi yêu cầu ghép kèo luồng A. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record MatchRequestSentEvent(UUID requestId, UUID matchId, UUID requesterTeamId, UUID authorTeamId, LocalDateTime occurredAt) {
}
