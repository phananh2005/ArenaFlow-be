package com.phananh.arenaflow.domain.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Trận ngoài hệ thống + sân + khu vực + có mercenary được duyệt + trạng thái đội + cờ người gọi là đội trưởng. */
public interface ExternalMatchView {
    UUID getExternalMatchId();
    UUID getTeamId();
    String getTeamStatus();
    Boolean getCallerCaptain();
    String getStatus();
    LocalDateTime getStartsAt();
    LocalDateTime getEndsAt();
    String getSkillLevels();
    String getOpponentName();
    UUID getVenueId();
    String getVenueName();
    String getVenueAddress();
    BigDecimal getVenueLatitude();
    BigDecimal getVenueLongitude();
    String getWardName();
    String getProvinceName();
    Boolean getHasApprovedMercenary();
    LocalDateTime getCancelledAt();
    Long getVersion();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
}
