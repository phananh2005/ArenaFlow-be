package com.phananh.arenaflow.application.event.mercenary;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đơn ứng tuyển được duyệt. */
public record MercenaryApplicationApprovedEvent(UUID applicationId, UUID recruitmentId, UUID matchId,
                                                UUID externalMatchId, UUID teamId, UUID applicantId,
                                                LocalDateTime occurredAt) {
}
