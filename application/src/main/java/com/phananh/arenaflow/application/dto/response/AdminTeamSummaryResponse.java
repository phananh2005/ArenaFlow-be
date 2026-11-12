package com.phananh.arenaflow.application.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * TeamSummaryResponse + inactiveReason; tên captain/người giải tán đã xoá tài khoản giữ nguyên, kèm cờ để giao diện
 * Admin gắn nhãn.
 */
public record AdminTeamSummaryResponse(UUID teamId, String teamName, String avatarUrl, List<String> skillLevels,
                                       String status, String inactiveReason, String captainName,
                                       boolean isCaptainDeleted, long memberCount, OffsetDateTime createdAt,
                                       String disbandedByName, boolean isDisbandedByDeleted) {
}
