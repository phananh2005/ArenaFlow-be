package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Thương lượng (chi tiết) — response nhận kèo và xem chi tiết. {@code status} là trạng thái hiển thị. */
public record PairingResponse(UUID pairingId, UUID matchId, UUID conversationId, String status, int revision,
                              LocalDateTime matchExpiresAt, PairingTeamResponse authorTeam,
                              PairingTeamResponse interestedTeam, ProposalResponse proposal,
                              LocalDateTime authorConfirmedAt, LocalDateTime interestedConfirmedAt,
                              LocalDateTime confirmedAt, PairingCostAgreementResponse costAgreement,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
}
