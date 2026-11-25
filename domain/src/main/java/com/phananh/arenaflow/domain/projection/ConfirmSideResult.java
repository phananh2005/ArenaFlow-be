package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;

/** Hai mốc xác nhận của pairing sau câu ghi xác nhận một phía ({@code RETURNING}). */
public interface ConfirmSideResult {
    LocalDateTime getAuthorConfirmedAt();
    LocalDateTime getInterestedConfirmedAt();
}
