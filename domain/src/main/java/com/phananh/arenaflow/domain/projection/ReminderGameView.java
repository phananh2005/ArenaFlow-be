package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Trận vừa được claim để nhắc — đúng một trong hai id khác null. */
public interface ReminderGameView {
    UUID getMatchId();
    UUID getExternalMatchId();
}
