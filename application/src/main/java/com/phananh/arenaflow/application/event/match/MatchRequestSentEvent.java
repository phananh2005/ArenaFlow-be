package com.phananh.arenaflow.application.event.match;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đội gửi yêu cầu ghép kèo luồng A; phát trong transaction, listener xử lý sau commit. */
public record MatchRequestSentEvent(UUID requestId, UUID matchId, UUID requesterTeamId, UUID authorTeamId, LocalDateTime occurredAt) {}
