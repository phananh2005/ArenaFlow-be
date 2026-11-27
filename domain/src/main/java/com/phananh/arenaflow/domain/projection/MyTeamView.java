package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đội người gọi đang là thành viên ACTIVE, kèm vai trò và thời điểm tham gia của người gọi. */
public interface MyTeamView {
    UUID getTeamId();
    String getTeamName();
    String getAvatarUrl();
    String getSkillLevels();
    String getStatus();
    String getCaptainName();
    Boolean getCaptainDeleted();
    Long getMemberCount();
    String getRole();
    LocalDateTime getJoinedAt();
}
