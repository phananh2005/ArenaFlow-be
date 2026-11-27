package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Context đánh giá theo đơn (một câu, xuất phát từ người gọi): đơn (null khi không có), trận, đội tuyển, cờ người gọi,
 * đánh giá phía đội và phía mercenary đã có (nullable).
 */
public interface MercenaryRatingContextView {
    Boolean getCallerDeleted();
    UUID getApplicationId();
    String getApplicationStatus();
    UUID getApplicantId();
    UUID getMatchId();
    UUID getExternalMatchId();
    Boolean getGameEnded();
    UUID getTeamId();
    String getTeamStatus();
    Boolean getCallerCaptain();
    Boolean getCallerApplicant();
    UUID getTeamRatingId();
    Boolean getTeamRatingNoShow();
    Integer getTeamRatingScore();
    String getTeamRatingComment();
    LocalDateTime getTeamRatingCreatedAt();
    UUID getMercenaryRatingId();
    Integer getMercenaryRatingScore();
    String getMercenaryRatingComment();
    LocalDateTime getMercenaryRatingCreatedAt();
}
