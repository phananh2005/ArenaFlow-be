package com.phananh.arenaflow.application.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Chi tiết đội cho Admin: field như {@link TeamResponse} nhưng tên captain/người giải tán đã xoá tài khoản giữ nguyên,
 * kèm cờ để giao diện gắn nhãn. captainId/captainName null khi đội không còn captain ACTIVE.
 */
public record AdminTeamResponse(UUID teamId, String teamName, String avatarUrl, String description,
                                List<String> skillLevels, String status, String inactiveReason, UUID captainId,
                                String captainName, boolean isCaptainDeleted, long memberCount, UUID createdBy,
                                OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime disbandedAt,
                                UUID disbandedById, String disbandedByName, boolean isDisbandedByDeleted,
                                String disbandReason) {
}
