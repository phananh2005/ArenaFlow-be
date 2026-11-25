package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kết quả xác nhận: {@code status} NEGOTIATING (chờ bên kia) | CONFIRMED (đã ghép); {@code matchStatus} OPEN |
 * CONFIRMED.
 */
public record PairingConfirmationResponse(UUID pairingId, String status, int revision, LocalDateTime authorConfirmedAt,
                                          LocalDateTime interestedConfirmedAt, LocalDateTime confirmedAt, UUID matchId,
                                          String matchStatus) {
}
