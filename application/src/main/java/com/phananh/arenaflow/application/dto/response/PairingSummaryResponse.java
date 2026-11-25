package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Thương lượng (rút gọn) — phần tử danh sách của kèo / của đội. */
public record PairingSummaryResponse(UUID pairingId, UUID matchId, UUID conversationId, String status, int revision,
                                     UUID authorTeamId, String authorTeamName, UUID interestedTeamId,
                                     String interestedTeamName, Integer interestedTeamReliabilityScore,
                                     UUID proposalVenueId, UUID proposalTimeOptionId, LocalDateTime authorConfirmedAt,
                                     LocalDateTime interestedConfirmedAt, LocalDateTime confirmedAt,
                                     LocalDateTime matchStartsAt, LocalDateTime matchExpiresAt,
                                     LocalDateTime createdAt, LocalDateTime updatedAt) {
}
