package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Dòng hàng chờ tranh chấp liên quan mercenary (API Admin — tên thật + cờ đã xoá). */
public record AdminMercenaryDisputeResponse(UUID disputeId, UUID reportId, UUID applicationId, String source,
                                            UUID matchId, UUID externalMatchId, LocalDateTime matchStartsAt,
                                            String reporterSide, Team team, UUID mercenaryId, String mercenaryName,
                                            boolean isMercenaryDeleted, UUID reportedById, String reportedByName,
                                            boolean isReportedByDeleted, UUID disputedById, String disputedByName,
                                            boolean isDisputedByDeleted, String status, String decision,
                                            UUID resolvedById, String resolvedByName, boolean isResolvedByDeleted,
                                            LocalDateTime resolvedAt, LocalDateTime reportCreatedAt,
                                            LocalDateTime createdAt) {

    /** Đội tuyển kèm trạng thái. */
    public record Team(UUID teamId, String name, String avatarUrl, String status) {
    }
}
