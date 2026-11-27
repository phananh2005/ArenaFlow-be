package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Kèo được job claim (đã khoá dòng) để chuyển EXPIRED / ARCHIVED. */
public interface DueMatchView {
    UUID getId();
    UUID getAuthorTeamId();
    UUID getOpponentTeamId();
    LocalDateTime getMatchEndTime();
}
