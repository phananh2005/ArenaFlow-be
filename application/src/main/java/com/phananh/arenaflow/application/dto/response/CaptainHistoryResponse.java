package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một lần chuyển quyền đội trưởng: fromUserId null = lần tạo đội; tên user đã xoá đã được che.
 *
 * @param historyId     id bản ghi lịch sử
 * @param teamId        id đội
 * @param fromUserId    đội trưởng cũ; null khi là lần tạo đội
 * @param fromUserName  tên đội trưởng cũ; null cùng {@code fromUserId}
 * @param toUserId      người nhận quyền
 * @param toUserName    tên người nhận quyền
 * @param reason        lý do chuyển quyền; có thể null
 * @param transferredAt thời điểm chuyển quyền, giờ Việt Nam không kèm múi giờ
 */
public record CaptainHistoryResponse(UUID historyId, UUID teamId, UUID fromUserId, String fromUserName, UUID toUserId,
                                     String toUserName, String reason, LocalDateTime transferredAt) {
}
