package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một người nhận thông báo của report / tranh chấp (bên report, bên bị report, Admin). */
public interface ReportAudienceRowView {
    String getAudience();
    UUID getRecipientUserId();
    UUID getReportId();
    String getReportType();
    String getReporterType();
    String getReportedType();
    String getReporterTeamName();
    String getReportedTeamName();
    String getPosition();
    UUID getApplicationId();
    UUID getDisputeId();
    String getDecision();
    LocalDateTime getGameStartTime();
}
