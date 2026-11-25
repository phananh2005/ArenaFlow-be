package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProposalUpdateResponse(UUID pairingId, int revision, ProposalResponse proposal,
                                     LocalDateTime authorConfirmedAt, LocalDateTime interestedConfirmedAt,
                                     LocalDateTime updatedAt) {
}
