package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Tranh chấp trong hàng chờ Admin: tên thật người dùng + cờ đã xoá ngay sau mỗi tên. */
public record AdminDisputeResponse(UUID disputeId, UUID reportId, UUID matchId, LocalDateTime matchStartsAt,
                                   AdminTeamBriefResponse reporterTeam, AdminTeamBriefResponse reportedTeam,
                                   UUID reportedById, String reportedByName, boolean isReportedByDeleted,
                                   UUID disputedById, String disputedByName, boolean isDisputedByDeleted,
                                   String status, String decision,
                                   UUID resolvedById, String resolvedByName, boolean isResolvedByDeleted,
                                   LocalDateTime resolvedAt, LocalDateTime reportCreatedAt, LocalDateTime createdAt) {
}
