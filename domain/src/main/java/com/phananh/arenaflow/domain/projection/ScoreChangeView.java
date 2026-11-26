package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Điểm uy tín của một đội trước và sau câu cập nhật nguyên tử. */
public interface ScoreChangeView {

    UUID getTeamId();

    Integer getOldScore();

    Integer getNewScore();
}
