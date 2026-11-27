package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Dòng hàng chờ tranh chấp liên quan mercenary — tên thật + cờ đã xoá (API Admin). */
public interface AdminMercenaryDisputeView {
    UUID getDisputeId();
    UUID getReportId();
    UUID getApplicationId();
    UUID getMatchId();
    UUID getExternalMatchId();
    LocalDateTime getGameStart();
    String getReporterType();
    UUID getTeamId();
    String getTeamName();
    String getTeamAvatarUrl();
    String getTeamStatus();
    UUID getMercenaryId();
    String getMercenaryName();
    Boolean getMercenaryDeleted();
    UUID getReportedById();
    String getReportedByName();
    Boolean getReportedByDeleted();
    UUID getDisputedById();
    String getDisputedByName();
    Boolean getDisputedByDeleted();
    String getStatus();
    String getDecision();
    UUID getResolvedById();
    String getResolvedByName();
    Boolean getResolvedByDeleted();
    LocalDateTime getResolvedAt();
    LocalDateTime getReportCreatedAt();
    LocalDateTime getCreatedAt();
}
