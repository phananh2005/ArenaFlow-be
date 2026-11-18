package com.phananh.arenaflow.application.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VenueImageResponse(UUID imageId, UUID venueId, String imageUrl, boolean isPrimary, int displayOrder,
                                 UUID uploadedById, OffsetDateTime createdAt) {
}
