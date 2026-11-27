package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Đội và đội trưởng ACTIVE hiện tại (null khi đội không còn đội trưởng). */
public interface TeamAudienceRowView {
    UUID getTeamId();
    String getTeamName();
    UUID getCaptainUserId();
}
