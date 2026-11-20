package com.phananh.arenaflow.application.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VenueReportResponse(UUID reportId, UUID venueId, String reason, String detail, String status,
                                  OffsetDateTime createdAt) {
}
