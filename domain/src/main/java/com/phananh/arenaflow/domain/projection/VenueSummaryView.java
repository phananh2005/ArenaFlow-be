package com.phananh.arenaflow.domain.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row for venue search; distance is null without a geo query. */
public interface VenueSummaryView {
    UUID getVenueId();
    String getVenueName();
    String getAddress();
    BigDecimal getLatitude();
    BigDecimal getLongitude();
    String getVenueType();
    String getStatus();
    String getPrimaryImageUrl();
    Double getDistanceKm();
    Instant getLastSelectedAt();
}
