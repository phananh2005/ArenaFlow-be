package com.phananh.arenaflow.application.event.mercenary;

import java.time.LocalDateTime;
import java.util.UUID;

/** Có report mới theo đơn; {@code direction} = {@code TEAM_TO_MERCENARY} | {@code MERCENARY_TO_TEAM}. */
public record MercenaryReportCreatedEvent(UUID reportId, UUID applicationId, String direction, String reportType,
                                          LocalDateTime occurredAt) {
}
