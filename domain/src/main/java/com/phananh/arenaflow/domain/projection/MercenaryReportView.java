package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Report giữa đội tuyển và mercenary + đội + mercenary + tranh chấp (nullable). */
public interface MercenaryReportView {
    UUID getReportId();
    UUID getApplicationId();
    UUID getMatchId();
    UUID getExternalMatchId();
    LocalDateTime getGameStart();
    String getReporterType();
    String getReportType();
    String getReason();
    String getStatus();
    Integer getReliabilityPenalty();
    LocalDateTime getCreatedAt();
    UUID getTeamId();
    String getTeamName();
    String getTeamAvatarUrl();
    UUID getMercenaryId();
    String getMercenaryName();
    String getMercenaryAvatarUrl();
    Boolean getMercenaryDeleted();
    UUID getDisputeId();
    String getDisputeStatus();
    String getDisputeEvidence();
    String getDisputeDecision();
    String getDisputeResolutionNote();
    LocalDateTime getDisputeCreatedAt();
    LocalDateTime getDisputeResolvedAt();
}
