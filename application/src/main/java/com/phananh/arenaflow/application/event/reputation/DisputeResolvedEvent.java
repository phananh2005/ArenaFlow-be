package com.phananh.arenaflow.application.event.reputation;

import com.phananh.arenaflow.domain.entity.rating.Report;
import java.time.LocalDateTime;
import java.util.UUID;

/** Admin quyết định tranh chấp (đội và mercenary). Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record DisputeResolvedEvent(UUID disputeId, UUID reportId, Report.ReporterType reporterType, UUID reporterId,
                                   Report.ReportedType reportedType, UUID reportedId, Decision decision,
                                   LocalDateTime occurredAt) {

    public enum Decision {
        CONFIRMED, REJECTED
    }
}
