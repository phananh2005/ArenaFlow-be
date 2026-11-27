package com.phananh.arenaflow.application.event.match;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đội tạo kèo từ chối yêu cầu. */
public record MatchRequestRejectedEvent(UUID requestId, UUID matchId, UUID requesterTeamId, LocalDateTime occurredAt) {}
