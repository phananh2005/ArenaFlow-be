package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Đội tác giả của kèo, trạng thái đội, người gọi có là đội trưởng ACTIVE, loại kèo — một câu. */
public interface MatchAuthorAccessView {
    UUID getAuthorTeamId();
    String getAuthorTeamStatus();
    Boolean getCallerIsCaptain();
    String getType();
}
