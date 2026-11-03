package com.phananh.ArenaFlow.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** §2.21 — TeamSummaryResponse + inactiveReason. */
public record AdminTeamSummaryResponse(UUID teamId, String teamName, String avatarUrl, List<String> skillLevels,
                                       String status, String inactiveReason, String captainName, long memberCount,
                                       OffsetDateTime createdAt) {
}
