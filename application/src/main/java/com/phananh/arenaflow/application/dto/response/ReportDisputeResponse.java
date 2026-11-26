package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Tranh chấp kèm report; {@code evidence} null ở góc nhìn đội đã report. */
public record ReportDisputeResponse(UUID disputeId, String status, String evidence, String decision,
                                    String resolutionNote, LocalDateTime createdAt, LocalDateTime resolvedAt) {
}
