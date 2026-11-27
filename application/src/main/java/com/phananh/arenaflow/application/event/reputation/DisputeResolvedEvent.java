package com.phananh.arenaflow.application.event.reputation;

import java.time.LocalDateTime;
import java.util.UUID;

/** Admin vừa quyết định tranh chấp; bên report / bị report có thể là đội hoặc cá nhân. */
public record DisputeResolvedEvent(UUID disputeId, UUID reportId, String reporterType, UUID reporterId,
                                   String reportedType, UUID reportedId, String decision, LocalDateTime occurredAt) {
}
