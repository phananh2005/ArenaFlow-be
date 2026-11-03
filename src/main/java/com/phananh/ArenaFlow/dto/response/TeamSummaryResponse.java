package com.phananh.ArenaFlow.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** §3.13. */
public record TeamSummaryResponse(UUID teamId, String teamName, String avatarUrl, List<String> skillLevels, String status,
                                  String captainName, long memberCount, OffsetDateTime createdAt) {
}
