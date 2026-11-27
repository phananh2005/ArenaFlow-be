package com.phananh.arenaflow.application.event.mercenary;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đơn ứng tuyển bị từ chối (đội trưởng hoặc tin đã đóng). Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record MercenaryApplicationRejectedEvent(UUID applicationId, UUID recruitmentId, UUID matchId, UUID externalMatchId, UUID teamId,
        UUID applicantId, Cause cause, LocalDateTime occurredAt) {

    public enum Cause {
        CAPTAIN, RECRUITMENT_CLOSED
    }
}
