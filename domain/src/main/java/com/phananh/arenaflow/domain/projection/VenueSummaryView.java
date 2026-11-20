package com.phananh.arenaflow.domain.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Một dòng kết quả tìm kiếm địa điểm; distance là null khi không có truy vấn toạ độ. */
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
