package com.phananh.arenaflow.domain.projection;

import java.time.Instant;
import java.util.UUID;

/** Dòng danh sách đội (user và admin); inactiveReason chỉ admin dùng. */
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
    Instant getCreatedAt();
    String getDisbandedByName();
    Boolean getDisbandedByDeleted();
}
