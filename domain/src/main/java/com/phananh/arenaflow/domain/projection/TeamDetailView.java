package com.phananh.arenaflow.domain.projection;

import java.time.Instant;
import java.util.UUID;

/** Chi tiết đội + captain ACTIVE (nullable) + số thành viên ACTIVE (subquery COUNT) — 1 truy vấn. */
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
    /** true khi captain đã xoá tài khoản → mapper che tên (DeletedUserMasking). */
    Boolean getCaptainDeleted();
    Long getMemberCount();
    UUID getCreatedBy();
    Instant getCreatedAt();
    Instant getUpdatedAt();
}
