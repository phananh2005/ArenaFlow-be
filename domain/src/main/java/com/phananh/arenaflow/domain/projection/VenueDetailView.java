package com.phananh.arenaflow.domain.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Toàn bộ dòng venue kèm tên người tạo và tên phường, từ một native query. */
public interface VenueDetailView {
    UUID getVenueId();
    String getVenueName();
    String getAddress();
    BigDecimal getLatitude();
    BigDecimal getLongitude();
    UUID getWardId();
    String getWardName();
    UUID getProvinceId();
    String getProvinceName();
    String getVenueType();
    String getPhone();
    String getWebsite();
    String getStatus();
    String getCreatedByType();
    UUID getCreatedById();
    String getCreatedByName();
    Boolean getCreatedByDeleted();
    Instant getLastSelectedAt();
    Instant getAdminVerifiedInactiveAt();
    Instant getCreatedAt();
    Instant getUpdatedAt();
}
