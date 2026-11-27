package com.phananh.arenaflow.application.event.mercenary;

import java.time.LocalDateTime;
import java.util.UUID;

/** Có người ứng tuyển tin tuyển của đội. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record MercenaryApplicationSubmittedEvent(UUID applicationId, UUID recruitmentId, UUID matchId, UUID externalMatchId, UUID teamId,
        UUID applicantId, LocalDateTime occurredAt) {
}
