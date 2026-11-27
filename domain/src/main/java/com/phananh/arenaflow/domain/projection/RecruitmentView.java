package com.phananh.arenaflow.domain.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tin tuyển + trận + đội + sân + khu vực + đội trưởng hiện tại + cờ của người xem (đội trưởng / đơn đang hoạt động).
 * Sân và tên đội trưởng có trong dòng; mapper quyết định ẩn / hiện theo người xem.
 */
public interface RecruitmentView {
    UUID getRecruitmentId();
    UUID getMatchId();
    UUID getExternalMatchId();
    UUID getTeamId();
    String getTeamName();
    String getTeamAvatarUrl();
    String getTeamStatus();
    String getPosition();
    Integer getQuantityNeeded();
    Integer getQuantityApproved();
    String getStatus();
    String getEffectiveStatus();
    String getSource();
    String getOpponentName();
    String getGameStatus();
    LocalDateTime getGameStart();
    LocalDateTime getGameEnd();
    String getFormat();
    String getSkillLevels();
    UUID getVenueId();
    String getVenueName();
    String getVenueAddress();
    BigDecimal getVenueLatitude();
    BigDecimal getVenueLongitude();
    String getWardName();
    String getProvinceName();
    String getCaptainName();
    Boolean getCaptainDeleted();
    Boolean getViewerIsCaptain();
    UUID getMyApplicationId();
    String getMyApplicationStatus();
    UUID getMyConversationId();
    Long getPendingCount();
    LocalDateTime getClosedAt();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
    Double getDistanceKm();
}
