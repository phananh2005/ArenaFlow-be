package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Lời mời đang chờ của người gọi kèm đội mời và đội trưởng hiện tại. */
public interface MyInvitationView {
    UUID getInvitationId();
    LocalDateTime getInvitedAt();
    UUID getTeamId();
    String getTeamName();
    String getAvatarUrl();
    String getSkillLevels();
    String getStatus();
    String getCaptainName();
    Boolean getCaptainDeleted();
    Long getMemberCount();
}
