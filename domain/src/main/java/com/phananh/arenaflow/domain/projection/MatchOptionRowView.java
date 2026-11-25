package com.phananh.arenaflow.domain.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một dòng lựa chọn của kèo luồng B: {@code kind = VENUE} (cột địa điểm) hoặc {@code TIME} (cột khung giờ); cột của
 * loại kia là {@code null}. Lựa chọn địa điểm và khung giờ đọc chung một câu {@code UNION ALL}.
 */
public interface MatchOptionRowView {
    String getKind();
    Integer getDisplayOrder();
    UUID getVenueId();
    String getVenueName();
    String getAddress();
    BigDecimal getLatitude();
    BigDecimal getLongitude();
    String getVenueStatus();
    String getPrimaryImageUrl();
    UUID getTimeOptionId();
    LocalDateTime getStartsAt();
    LocalDateTime getEndsAt();
}
