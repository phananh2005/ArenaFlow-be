package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Chi tiết đội + captain ACTIVE (nullable) + số thành viên ACTIVE (subquery COUNT) + người giải tán — 1 truy vấn.
 * Cột thời điểm ({@code createdAt}, {@code updatedAt}, {@code disbandedAt}) là {@code TIMESTAMP} giờ Việt Nam, đọc
 * thẳng thành {@code LocalDateTime}.
 */
public interface TeamDetailView {
    UUID getTeamId();
    String getTeamName();
    String getAvatarUrl();
    String getDescription();
    /** JSON thô của cột skill_levels; mapper chuyển sang List bằng SkillLevelsConverter. */
    String getSkillLevels();
    String getStatus();
    String getInactiveReason();
    UUID getCaptainId();
    String getCaptainName();
    /** true khi captain đã xoá tài khoản; endpoint thường che tên (DeletedUserMasking), Admin nhận cờ. */
    Boolean getCaptainDeleted();
    Long getMemberCount();
    UUID getCreatedBy();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
    String getDisbandReason();
    LocalDateTime getDisbandedAt();
    UUID getDisbandedById();
    String getDisbandedByName();
    /** true khi người giải tán đã xoá tài khoản; endpoint thường che tên (DeletedUserMasking), Admin nhận cờ. */
    Boolean getDisbandedByDeleted();
}
