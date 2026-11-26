package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Hai đội của trận, trạng thái từng đội và cờ người gọi là đội trưởng ACTIVE của từng đội (đọc cùng câu ngữ cảnh). */
public interface MatchSidesView {

    UUID getAuthorTeamId();

    UUID getOpponentTeamId();

    String getAuthorTeamStatus();

    String getOpponentTeamStatus();

    Boolean getCallerAuthorCaptain();

    Boolean getCallerOpponentCaptain();
}
