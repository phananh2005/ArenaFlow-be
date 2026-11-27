package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Tin OPEN/FILLED tới hạn đóng; {@code cause} = {@code MATCH_CANCELLED} | {@code EXPIRED}. */
public interface DueRecruitmentView {
    UUID getId();
    UUID getMatchId();
    UUID getExternalMatchId();
    UUID getTeamId();
    String getCause();
}
