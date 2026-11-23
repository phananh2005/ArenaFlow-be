package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dòng danh sách đội (user và admin); inactiveReason chỉ admin dùng.
 * Cột thời điểm ({@code createdAt}) là {@code TIMESTAMP} giờ Việt Nam, đọc thẳng thành {@code LocalDateTime}.
 */
public interface TeamSummaryView {
    UUID getTeamId();
    String getTeamName();
    String getAvatarUrl();
    String getSkillLevels();
    String getStatus();
    String getInactiveReason();
    String getCaptainName();
    Boolean getCaptainDeleted();
    Long getMemberCount();
    LocalDateTime getCreatedAt();
    String getDisbandedByName();
    Boolean getDisbandedByDeleted();
}
