package com.phananh.arenaflow.application.event.reputation;

import com.phananh.arenaflow.domain.entity.rating.Report;
import java.time.LocalDateTime;
import java.util.UUID;

/** Report giữa hai bên của một trận. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record TeamReportedEvent(UUID reportId, UUID matchId, Report.ReporterType reporterType, UUID reporterId,
                                Report.ReportedType reportedType, UUID reportedId, Report.ReportType reportType,
                                LocalDateTime occurredAt) {
}
