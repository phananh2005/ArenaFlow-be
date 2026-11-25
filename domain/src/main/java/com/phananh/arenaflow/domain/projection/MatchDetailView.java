package com.phananh.arenaflow.domain.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Chi tiết kèo trong một câu: bộ môn, đội tạo kèo, đội đã ghép, địa điểm (thông tin mới nhất + ảnh chính).
 * {@code effectiveStatus}: kèo OPEN đã tới hạn trả EXPIRED dù DB chưa đổi. {@code skillLevels} là JSON thô.
 * {@code costDepositAmount}, {@code confirmedPairingId} chỉ có ở kèo luồng B đã ghép (null ở kèo luồng A).
 */
public interface MatchDetailView {
    UUID getMatchId();
    String getType();
    String getEffectiveStatus();
    UUID getSportId();
    String getSportName();
    String getFormat();
    String getSkillLevels();
    String getAgeNote();
    String getMatchNature();
    LocalDateTime getMatchStartTime();
    LocalDateTime getMatchEndTime();
    LocalDateTime getExpiresAt();
    UUID getAuthorTeamId();
    String getAuthorTeamName();
    String getAuthorTeamAvatarUrl();
    Integer getAuthorTeamReliabilityScore();
    UUID getOpponentTeamId();
    String getOpponentTeamName();
    String getOpponentTeamAvatarUrl();
    Integer getOpponentTeamReliabilityScore();
    UUID getVenueId();
    String getVenueName();
    String getVenueAddress();
    BigDecimal getVenueLatitude();
    BigDecimal getVenueLongitude();
    String getVenueStatus();
    String getVenuePrimaryImageUrl();
    String getCostType();
    String getCostSplit();
    Long getCostVenueAmount();
    Long getCostDrinksAmount();
    Long getCostDepositAmount();
    String getCostDepositHolder();
    UUID getConfirmedPairingId();
    UUID getCancelledByTeamId();
    LocalDateTime getCancelledAt();
    String getCancelReason();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
}
