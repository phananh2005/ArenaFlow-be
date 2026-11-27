package com.phananh.arenaflow.application.event.mercenary;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đơn ứng tuyển bị từ chối — bởi đội trưởng, hoặc tự động vì tin đã đóng trước khi đơn kịp ghi nhận. */
public record MercenaryApplicationRejectedEvent(UUID applicationId, UUID recruitmentId, UUID matchId,
                                                UUID externalMatchId, UUID teamId, UUID applicantId, Cause cause,
                                                LocalDateTime occurredAt) {

    public enum Cause {
        CAPTAIN, RECRUITMENT_CLOSED
    }
}
