package com.phananh.arenaflow.application.event.reputation;

import com.phananh.arenaflow.domain.entity.rating.Report;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một đội vừa report đội khác (sau trận hoặc qua chat). */
public record TeamReportedEvent(UUID reportId, UUID matchId, UUID reporterTeamId, UUID reportedTeamId,
                                Report.ReportType reportType, LocalDateTime occurredAt) {
}
