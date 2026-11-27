package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Đội người gọi đang tham gia; {@code role}, {@code joinedAt} là của người gọi. */
public record MyTeamResponse(UUID teamId, String teamName, String avatarUrl, List<String> skillLevels, String status,
                             String captainName, long memberCount, String role, LocalDateTime joinedAt) {
}
