package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Context ứng tuyển (một câu, xuất phát từ người gọi): cờ người gọi đã xoá, tin (null khi không có), trận, đội tuyển,
 * khu vực, điều kiện ứng viên, thông tin hiển thị của người gọi.
 */
public interface ApplyContextView {
    Boolean getCallerDeleted();
    String getCallerName();
    String getCallerAvatarUrl();
    String getCallerPreferredPosition();
    UUID getRecruitmentId();
    String getStatus();
    String getEffectiveStatus();
    UUID getTeamId();
    UUID getMatchId();
    UUID getExternalMatchId();
    String getPosition();
    Integer getQuantityNeeded();
    Integer getQuantityApproved();
    String getTeamStatus();
    String getTeamName();
    String getTeamAvatarUrl();
    String getSource();
    String getOpponentName();
    String getGameStatus();
    LocalDateTime getGameStart();
    LocalDateTime getGameEnd();
    String getWardName();
    String getProvinceName();
    Boolean getCallerMember();
    Boolean getCallerHasActive();
    Boolean getCallerOverlap();
}
