package com.phananh.arenaflow.application.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Lịch sử thành viên (Admin): field của {@link MemberResponse} + leftAt (null khi còn ACTIVE); tên user đã xoá đã che. */
public record MemberHistoryResponse(UUID memberId, UUID teamId, UUID userId, String displayName, String avatarUrl,
                                    String role, String status, OffsetDateTime joinedAt, OffsetDateTime leftAt) {
}
