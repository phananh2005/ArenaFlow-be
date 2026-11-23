package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một thành viên đội; displayName/avatarUrl đã che khi user đã xoá.
 *
 * @param memberId    id membership (bằng id lời mời đã chấp nhận)
 * @param teamId      id đội
 * @param userId      id user
 * @param displayName tên hiển thị, tên mặc định khi user đã xoá tài khoản
 * @param avatarUrl   ảnh đại diện; có thể null
 * @param role        {@code CAPTAIN} hoặc {@code MEMBER}
 * @param status      trạng thái membership
 * @param joinedAt    thời điểm vào đội, giờ Việt Nam không kèm múi giờ
 */
public record MemberResponse(UUID memberId, UUID teamId, UUID userId, String displayName, String avatarUrl, String role,
                             String status, LocalDateTime joinedAt) {
}
