package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;

/** Chi tiết tranh chấp mercenary cho Admin. */
public interface AdminMercenaryDisputeDetailView extends AdminMercenaryDisputeView {
    String getSource();
    String getSportName();
    String getGameStatus();
    LocalDateTime getGameEnd();
    String getVenueName();
    String getOpponentName();
    String getPosition();
    String getReportReason();
    Integer getReliabilityPenalty();
    String getEvidence();
    String getResolutionNote();
    Integer getTeamReliabilityScore();
    Integer getMercenaryReliabilityScore();
}
