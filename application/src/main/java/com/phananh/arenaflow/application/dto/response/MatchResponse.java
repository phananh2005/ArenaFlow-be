package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Kèo chi tiết; {@code status} là trạng thái hiển thị. */
public record MatchResponse(UUID matchId, String type, String status, UUID sportId, String sportName, String format,
        List<String> skillLevels, String ageNote, String matchNature, LocalDateTime startsAt, LocalDateTime endsAt,
        LocalDateTime expiresAt, MatchTeamResponse authorTeam, MatchTeamResponse opponentTeam, MatchVenueResponse venue,
        List<MatchVenueResponse> venueOptions, List<MatchTimeOptionResponse> timeOptions, UUID confirmedPairingId,
        MatchCostAgreementResponse costAgreement, UUID cancelledByTeamId, LocalDateTime cancelledAt,
        String cancelReason, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
