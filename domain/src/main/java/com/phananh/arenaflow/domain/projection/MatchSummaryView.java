package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kèo rút gọn cho danh sách. {@code venueOptionCount}, {@code timeOptionCount} null ở đợt luồng A;
 * {@code distanceKm} chỉ có khi tìm theo toạ độ.
 */
public interface MatchSummaryView {
    UUID getMatchId();
    String getType();
    String getEffectiveStatus();
    UUID getSportId();
    String getSportName();
    String getFormat();
    String getSkillLevels();
    LocalDateTime getMatchStartTime();
    LocalDateTime getMatchEndTime();
    LocalDateTime getExpiresAt();
    UUID getAuthorTeamId();
    String getAuthorTeamName();
    Integer getAuthorTeamReliabilityScore();
    UUID getOpponentTeamId();
    String getOpponentTeamName();
    UUID getVenueId();
    String getVenueName();
    String getVenueAddress();
    Integer getVenueOptionCount();
    Integer getTimeOptionCount();
    Double getDistanceKm();
    LocalDateTime getCreatedAt();
}
