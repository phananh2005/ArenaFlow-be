package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Đội tạo kèo + trạng thái đội + cờ người gọi là đội trưởng ACTIVE của đội đó + loại kèo, trong một câu. */
public interface MatchAuthorAccessView {
    UUID getAuthorTeamId();
    String getAuthorTeamStatus();
    Boolean getCallerIsCaptain();
    String getType();
}
