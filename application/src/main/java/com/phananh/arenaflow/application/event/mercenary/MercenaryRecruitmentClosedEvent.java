package com.phananh.arenaflow.application.event.mercenary;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tin tuyển vừa đóng / hết hạn; {@code rejectedApplicationIds} là đơn chờ vừa bị từ chối theo (danh sách nhỏ — để người
 * nghe không truy vấn lại). Không dùng để báo mercenary đã duyệt về việc trận bị huỷ.
 */
public record MercenaryRecruitmentClosedEvent(UUID recruitmentId, UUID matchId, UUID externalMatchId, UUID teamId,
                                              Cause cause, List<UUID> rejectedApplicationIds,
                                              LocalDateTime occurredAt) {

    public enum Cause {
        CAPTAIN, MATCH_CANCELLED, EXTERNAL_MATCH_CANCELLED, EXPIRED
    }
}
