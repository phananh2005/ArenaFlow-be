package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Context report theo đơn (một câu, xuất phát từ người gọi): đơn, trận, đội tuyển, cờ người gọi (gồm người đã giải tán
 * đội), đã có tin nhắn chưa, đã report loại này theo từng phía chưa.
 */
public interface MercenaryReportContextView {
    Boolean getCallerDeleted();
    UUID getApplicationId();
    String getApplicationStatus();
    UUID getApplicantId();
    String getApplicantName();
    String getApplicantAvatarUrl();
    Boolean getApplicantDeleted();
    UUID getMatchId();
    UUID getExternalMatchId();
    Boolean getGameEnded();
    LocalDateTime getGameStart();
    UUID getTeamId();
    String getTeamStatus();
    String getTeamName();
    String getTeamAvatarUrl();
    Boolean getCallerCaptain();
    Boolean getCallerApplicant();
    Boolean getCallerDisbandedBy();
    Boolean getHasMessages();
    Boolean getTeamReported();
    Boolean getApplicantReported();
}
