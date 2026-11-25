package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record PairingCostUpdateResponse(UUID pairingId, int revision, PairingCostAgreementResponse costAgreement,
                                        LocalDateTime authorConfirmedAt, LocalDateTime interestedConfirmedAt,
                                        LocalDateTime updatedAt) {
}
