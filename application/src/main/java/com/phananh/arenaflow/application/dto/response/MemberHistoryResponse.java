package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lịch sử thành viên (Admin): field của {@link MemberResponse} + trạng thái user đã xoá và leftAt.
 *
 * @param memberId      id membership
 * @param teamId        id đội
 * @param userId        id user
 * @param displayName   tên thật của user, kể cả khi đã xoá tài khoản
 * @param avatarUrl     ảnh đại diện thật; có thể null
 * @param isUserDeleted true khi user đã xoá tài khoản
 * @param role          {@code CAPTAIN} hoặc {@code MEMBER}
 * @param status        trạng thái membership
 * @param joinedAt      thời điểm vào đội, giờ Việt Nam không kèm múi giờ
 * @param leftAt        thời điểm rời đội, giờ Việt Nam không kèm múi giờ; null khi còn ACTIVE
 */
public record MemberHistoryResponse(UUID memberId, UUID teamId, UUID userId, String displayName, String avatarUrl,
                                    boolean isUserDeleted, String role, String status, LocalDateTime joinedAt,
                                    LocalDateTime leftAt) {
}
