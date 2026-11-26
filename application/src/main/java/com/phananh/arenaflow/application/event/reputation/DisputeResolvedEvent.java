package com.phananh.arenaflow.application.event.reputation;

import com.phananh.arenaflow.domain.entity.rating.Dispute;

import java.time.LocalDateTime;
import java.util.UUID;

/** Admin vừa quyết định một tranh chấp. */
public record DisputeResolvedEvent(UUID disputeId, UUID reportId, UUID reporterTeamId, UUID reportedTeamId,
                                   Dispute.Decision decision, LocalDateTime occurredAt) {
}
