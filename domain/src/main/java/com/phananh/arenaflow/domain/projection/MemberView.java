package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thành viên đội + thông tin user; userDeleted → mapper che tên/ảnh.
 * Cột thời điểm ({@code joinedAt}) là {@code TIMESTAMP} giờ Việt Nam, đọc thẳng thành {@code LocalDateTime}.
 */
public interface MemberView {
    UUID getMemberId();
    UUID getTeamId();
    UUID getUserId();
    String getDisplayName();
    String getAvatarUrl();
    Boolean getUserDeleted();
    String getRole();
    String getStatus();
    LocalDateTime getJoinedAt();
}
