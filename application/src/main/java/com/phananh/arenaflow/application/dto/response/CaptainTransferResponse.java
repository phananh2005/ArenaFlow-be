package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kết quả chuyển quyền đội trưởng.
 *
 * @param teamId        id đội
 * @param fromUserId    đội trưởng cũ
 * @param toUserId      đội trưởng mới
 * @param reason        lý do chuyển quyền; có thể null
 * @param transferredAt thời điểm chuyển quyền, giờ Việt Nam không kèm múi giờ
 */
public record CaptainTransferResponse(UUID teamId, UUID fromUserId, UUID toUserId, String reason,
                                      LocalDateTime transferredAt) {
}
