package com.phananh.arenaflow.application.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Lịch sử thành viên (Admin): field của {@link MemberResponse} + trạng thái user đã xoá và leftAt. */
public record MemberHistoryResponse(UUID memberId, UUID teamId, UUID userId, String displayName, String avatarUrl,
                                    boolean isUserDeleted, String role, String status, OffsetDateTime joinedAt,
                                    OffsetDateTime leftAt) {
}
