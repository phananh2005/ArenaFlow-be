package com.phananh.arenaflow.domain.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ngữ cảnh tạo tin tuyển (một câu): trận (kèo hoặc trận ngoài — {@code source}), đội tuyển + cờ người gọi là đội trưởng
 * ACTIVE, sân + khu vực, vị trí đã có tin OPEN/FILLED của đội cho trận (nối bằng {@code |}).
 */
public interface RecruitContextView {
    String getSource();
    UUID getMatchId();
    UUID getExternalMatchId();
    String getMatchStatus();
    LocalDateTime getGameStart();
    LocalDateTime getGameEnd();
    UUID getAuthorTeamId();
    UUID getOpponentTeamId();
    String getFormat();
    String getSkillLevels();
    String getOpponentName();
    UUID getTeamId();
    String getTeamStatus();
    String getTeamName();
    String getTeamAvatarUrl();
    Boolean getCallerCaptain();
    String getCallerName();
    Boolean getCallerDeleted();
    UUID getVenueId();
    String getVenueName();
    String getVenueAddress();
    BigDecimal getVenueLatitude();
    BigDecimal getVenueLongitude();
    String getWardName();
    String getProvinceName();
    String getActivePositions();
}
