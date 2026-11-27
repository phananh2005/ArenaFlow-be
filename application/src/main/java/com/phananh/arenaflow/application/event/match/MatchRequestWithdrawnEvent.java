package com.phananh.arenaflow.application.event.match;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đội gửi rút yêu cầu khi còn PENDING (rút sau khi được chấp nhận chỉ phát {@link MatchCancelledEvent}). */
public record MatchRequestWithdrawnEvent(UUID requestId, UUID matchId, UUID requesterTeamId, UUID authorTeamId,
                                         LocalDateTime occurredAt) {}
