package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Report vừa được Admin quyết định — đủ để hoàn điểm đúng bên bị report (đội hoặc cá nhân). */
public interface ReportDecisionView {
    String getReportedType();
    UUID getReportedId();
    String getReporterType();
    UUID getReporterId();
    String getReportType();
    Integer getReliabilityPenalty();
}
