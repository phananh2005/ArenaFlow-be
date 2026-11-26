package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Report vừa được Admin quyết định: hai đội, loại, điểm đã thực trừ (để hoàn khi bác). */
public interface ReportDecisionView {

    UUID getReportedTeamId();

    UUID getReporterTeamId();

    String getReportType();

    Integer getReliabilityPenalty();
}
