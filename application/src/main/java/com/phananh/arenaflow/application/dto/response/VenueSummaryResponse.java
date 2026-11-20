package com.phananh.arenaflow.application.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VenueSummaryResponse(UUID venueId, String venueName, String address, BigDecimal latitude,
                                   BigDecimal longitude, String venueType, String status, String primaryImageUrl,
                                   BigDecimal distanceKm, OffsetDateTime lastSelectedAt) {
}
