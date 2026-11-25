package com.phananh.arenaflow.application.event.match;

import java.time.LocalDateTime;
import java.util.UUID;

/** Kèo OPEN quá hạn chuyển EXPIRED (job hoặc thao tác ghi gặp kèo quá hạn). */
public record MatchExpiredEvent(UUID matchId, UUID authorTeamId, LocalDateTime occurredAt) {}
