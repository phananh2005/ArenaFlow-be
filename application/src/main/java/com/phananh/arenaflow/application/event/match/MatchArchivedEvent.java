package com.phananh.arenaflow.application.event.match;

import java.time.LocalDateTime;
import java.util.UUID;

/** Kèo đã ghép qua giờ kết thúc chuyển ARCHIVED (job). */
public record MatchArchivedEvent(UUID matchId, UUID authorTeamId, UUID opponentTeamId, LocalDateTime matchEndTime,
                                 LocalDateTime occurredAt) {}
