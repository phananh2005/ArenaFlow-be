package com.phananh.arenaflow.application.event.mercenary;

import com.phananh.arenaflow.application.support.mercenary.MercenarySide;
import com.phananh.arenaflow.domain.entity.rating.Report;
import java.time.LocalDateTime;
import java.util.UUID;

/** Một bên của đơn report bên kia; {@code direction} là bên report. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record MercenaryReportCreatedEvent(UUID reportId, UUID applicationId, MercenarySide direction, Report.ReportType reportType,
                                          LocalDateTime occurredAt) {
}
