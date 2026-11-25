package com.phananh.arenaflow.application.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record MatchVenueResponse(UUID venueId, String venueName, String address, BigDecimal latitude, BigDecimal longitude,
                                 String venueStatus, String primaryImageUrl) {
}
