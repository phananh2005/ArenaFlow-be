package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Ngữ cảnh tranh chấp một report: report, trạng thái đội bị report, quyền đội trưởng của người gọi, đã có tranh chấp. */
public interface DisputeContextView {

    UUID getReportId();

    String getReportType();

    String getStatus();

    LocalDateTime getCreatedAt();

    UUID getReporterTeamId();

    UUID getReportedTeamId();

    String getReportedTeamStatus();

    Boolean getCallerReportedCaptain();

    Boolean getDisputeExists();
}
