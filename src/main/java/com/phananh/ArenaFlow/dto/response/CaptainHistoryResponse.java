package com.phananh.ArenaFlow.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** §3.17 — fromUserId null = lần tạo đội; tên user đã xoá đã được che. */
public record CaptainHistoryResponse(UUID historyId, UUID teamId, UUID fromUserId, String fromUserName, UUID toUserId,
                                     String toUserName, String reason, OffsetDateTime transferredAt) {
}
