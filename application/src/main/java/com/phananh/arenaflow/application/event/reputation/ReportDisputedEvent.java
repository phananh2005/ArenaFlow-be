package com.phananh.arenaflow.application.event.reputation;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đội bị report bùng vừa gửi tranh chấp. */
public record ReportDisputedEvent(UUID disputeId, UUID reportId, UUID reporterTeamId, UUID reportedTeamId,
                                  LocalDateTime occurredAt) {
}
