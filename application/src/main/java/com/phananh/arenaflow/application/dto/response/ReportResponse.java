package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Report theo góc nhìn một đội. {@code status} là trạng thái hiệu lực; {@code reliabilityPenalty} null ở góc nhìn
 * {@code SENT}.
 */
public record ReportResponse(UUID reportId, UUID matchId, LocalDateTime matchStartsAt, String direction,
                             TeamBriefResponse counterpartTeam, String reportType, String reason, String status,
                             LocalDateTime disputeDeadlineAt, boolean canDispute, Integer reliabilityPenalty,
                             ReportDisputeResponse dispute, LocalDateTime createdAt) {
}
