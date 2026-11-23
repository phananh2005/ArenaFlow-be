package com.phananh.arenaflow.domain.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Toàn bộ dòng venue kèm tên người tạo và tên phường, từ một native query.
 * Cột thời điểm ({@code lastSelectedAt}, {@code adminVerifiedInactiveAt}, {@code createdAt}, {@code updatedAt}) là
 * {@code TIMESTAMP} giờ Việt Nam, đọc thẳng thành {@code LocalDateTime}.
 */
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
    LocalDateTime getLastSelectedAt();
    LocalDateTime getAdminVerifiedInactiveAt();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
}
