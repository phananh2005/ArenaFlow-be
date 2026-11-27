package com.phananh.arenaflow.application.event.mercenary;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Tin tuyển đóng; {@code rejectedApplicationIds} là đơn chờ vừa bị từ chối tự động. Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record MercenaryRecruitmentClosedEvent(UUID recruitmentId, UUID matchId, UUID externalMatchId, UUID teamId, Cause cause,
                                              List<UUID> rejectedApplicationIds, LocalDateTime occurredAt) {

    public enum Cause {
        CAPTAIN, MATCH_CANCELLED, EXTERNAL_MATCH_CANCELLED, EXPIRED
    }
}
