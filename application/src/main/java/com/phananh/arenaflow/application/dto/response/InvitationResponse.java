package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lời mời vào đội (PENDING / ACTIVE / REJECTED).
 *
 * @param invitationId id lời mời (cũng là id membership khi được chấp nhận)
 * @param teamId       id đội
 * @param teamName     tên đội
 * @param userId       người được mời
 * @param status       trạng thái lời mời; {@code PENDING} khi vừa tạo
 * @param invitedAt    thời điểm mời, giờ Việt Nam không kèm múi giờ
 */
public record InvitationResponse(UUID invitationId, UUID teamId, String teamName, UUID userId, String status,
                                 LocalDateTime invitedAt) {
}
