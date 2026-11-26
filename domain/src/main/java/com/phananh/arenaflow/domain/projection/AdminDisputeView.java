package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Tranh chấp trong hàng chờ Admin: tên thật người dùng + cờ đã xoá (API Admin không che tên). */
public interface AdminDisputeView {

    UUID getDisputeId();

    UUID getReportId();

    UUID getMatchId();

    LocalDateTime getMatchStartTime();

    UUID getReporterTeamId();

    String getReporterTeamName();

    String getReporterTeamAvatarUrl();

    String getReporterTeamStatus();

    UUID getReportedTeamId();

    String getReportedTeamName();

    String getReportedTeamAvatarUrl();

    String getReportedTeamStatus();

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
