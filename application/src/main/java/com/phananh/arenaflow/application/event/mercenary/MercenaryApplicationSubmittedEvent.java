package com.phananh.arenaflow.application.event.mercenary;

import java.time.LocalDateTime;
import java.util.UUID;

/** Có đơn ứng tuyển mới. */
public record MercenaryApplicationSubmittedEvent(UUID applicationId, UUID recruitmentId, UUID matchId,
                                                 UUID externalMatchId, UUID teamId, UUID applicantId,
                                                 LocalDateTime occurredAt) {
}
