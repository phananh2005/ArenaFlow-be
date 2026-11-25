package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Khung giờ của kèo luồng B (id do DB sinh, đọc qua {@code RETURNING}). */
public interface TimeOptionView {
    UUID getId();
    LocalDateTime getProposedStartTime();
    LocalDateTime getProposedEndTime();
}
