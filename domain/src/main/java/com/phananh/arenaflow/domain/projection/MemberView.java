package com.phananh.arenaflow.domain.projection;

import java.time.Instant;
import java.util.UUID;

/** Thành viên đội + thông tin user; userDeleted → mapper che tên/ảnh. */
public interface MemberView {
    UUID getMemberId();
    UUID getTeamId();
    UUID getUserId();
    String getDisplayName();
    String getAvatarUrl();
    Boolean getUserDeleted();
    String getRole();
    String getStatus();
    Instant getJoinedAt();
}
