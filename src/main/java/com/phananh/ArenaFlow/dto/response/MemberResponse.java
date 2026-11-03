package com.phananh.ArenaFlow.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** §3.16 — displayName/avatarUrl đã che khi user đã xoá. */
public record MemberResponse(UUID memberId, UUID teamId, UUID userId, String displayName, String avatarUrl, String role,
                             String status, OffsetDateTime joinedAt) {
}
