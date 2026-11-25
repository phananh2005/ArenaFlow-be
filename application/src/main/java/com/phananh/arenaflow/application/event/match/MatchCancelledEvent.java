package com.phananh.arenaflow.application.event.match;

import com.phananh.arenaflow.domain.entity.match.Match;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kèo bị huỷ — một event duy nhất cho mỗi thao tác huỷ / rút sau ghép. {@code otherTeamId} null khi huỷ kèo OPEN;
 * {@code withdrawnRequestId} khác null khi đội đã ghép của kèo luồng A huỷ / rút.
 */
public record MatchCancelledEvent(UUID matchId, UUID cancelledByTeamId, UUID otherTeamId, Match.Status previousStatus,
                                  Cause cause, UUID withdrawnRequestId, LocalDateTime occurredAt) {

    /** Nguồn thao tác: huỷ kèo, hay rút yêu cầu đã được chấp nhận. */
    public enum Cause {
        CANCEL, REQUEST_WITHDRAWN
    }
}
