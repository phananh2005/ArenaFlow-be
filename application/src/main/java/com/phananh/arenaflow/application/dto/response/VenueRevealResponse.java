package com.phananh.arenaflow.application.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Sân của trận — chỉ trả cho đội trưởng đội tuyển và người có đơn được duyệt. */
public record VenueRevealResponse(UUID venueId, String name, String address, BigDecimal latitude, BigDecimal longitude) {
}
