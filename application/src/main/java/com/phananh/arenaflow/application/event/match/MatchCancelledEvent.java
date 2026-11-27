package com.phananh.arenaflow.application.event.match;

import com.phananh.arenaflow.domain.entity.match.Match;
import java.time.LocalDateTime;
import java.util.UUID;

/** Kèo bị huỷ (kể cả đội đã ghép rút yêu cầu); {@code otherTeamId} null khi huỷ kèo OPEN. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record MatchCancelledEvent(UUID matchId, UUID cancelledByTeamId, UUID otherTeamId, Match.Status previousStatus,
                                  Cause cause, UUID withdrawnRequestId, LocalDateTime occurredAt) {

    public enum Cause {
        CANCEL, REQUEST_WITHDRAWN
    }
}
