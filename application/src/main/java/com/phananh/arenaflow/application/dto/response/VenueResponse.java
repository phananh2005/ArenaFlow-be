package com.phananh.arenaflow.application.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record VenueResponse(UUID venueId, String venueName, String address, BigDecimal latitude, BigDecimal longitude,
                            WardResponse ward, String venueType, String phone, String website, String status,
                            String createdByType, UUID createdById, String createdByName, OffsetDateTime lastSelectedAt,
                            OffsetDateTime adminVerifiedInactiveAt, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                            List<VenueImageResponse> images) {
}
