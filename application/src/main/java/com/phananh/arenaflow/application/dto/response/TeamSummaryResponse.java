package com.phananh.arenaflow.application.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Tóm tắt đội trong danh sách. */
public record TeamSummaryResponse(UUID teamId, String teamName, String avatarUrl, List<String> skillLevels, String status,
                                  String captainName, long memberCount, OffsetDateTime createdAt,
                                  String disbandedByName) {
}
