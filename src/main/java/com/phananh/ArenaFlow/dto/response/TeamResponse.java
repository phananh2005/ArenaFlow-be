package com.phananh.ArenaFlow.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** §3.12 — captainId/captainName null khi đội không còn captain ACTIVE; tên user đã xoá đã được che. */
public record TeamResponse(UUID teamId, String teamName, String avatarUrl, String description, List<String> skillLevels,
                           String status, String inactiveReason, UUID captainId, String captainName, long memberCount,
                           UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
