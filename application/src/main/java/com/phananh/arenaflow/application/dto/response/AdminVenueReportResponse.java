package com.phananh.arenaflow.application.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminVenueReportResponse(UUID reportId, UUID venueId, String venueName, String venueStatus,
                                       UUID reporterId, String reporterName, boolean isReporterDeleted,
                                       String reason, String detail, String status,
                                       UUID handledById, String handledByName, boolean isHandledByDeleted,
                                       OffsetDateTime handledAt, OffsetDateTime createdAt) {
}
