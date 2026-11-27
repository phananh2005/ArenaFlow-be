package com.phananh.arenaflow.domain.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Đơn ứng tuyển + ứng viên + tin + trận + đội + đội trưởng hiện tại + sân + khu vực + cuộc trò chuyện. */
public interface ApplicationView {
    UUID getApplicationId();
    String getStatus();
    String getEffectiveStatus();
    UUID getApplicantId();
    String getApplicantName();
    String getApplicantAvatarUrl();
    Boolean getApplicantDeleted();
    String getApplicantPreferredPosition();
    LocalDateTime getAppliedAt();
    LocalDateTime getRespondedAt();
    LocalDateTime getWithdrawnAt();
    UUID getRecruitmentId();
    String getPosition();
    String getRecruitmentStatus();
    String getRecruitmentEffectiveStatus();
    Integer getQuantityNeeded();
    Integer getQuantityApproved();
    UUID getMatchId();
    UUID getExternalMatchId();
    String getSource();
    String getOpponentName();
    String getGameStatus();
    LocalDateTime getGameStart();
    LocalDateTime getGameEnd();
    UUID getTeamId();
    String getTeamName();
    String getTeamAvatarUrl();
    String getTeamStatus();
    String getCaptainName();
    Boolean getCaptainDeleted();
    UUID getVenueId();
    String getVenueName();
    String getVenueAddress();
    BigDecimal getVenueLatitude();
    BigDecimal getVenueLongitude();
    String getWardName();
    String getProvinceName();
    UUID getConversationId();
}
