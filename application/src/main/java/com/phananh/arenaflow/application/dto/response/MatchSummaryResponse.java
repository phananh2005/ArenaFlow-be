package com.phananh.arenaflow.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record MatchSummaryResponse(UUID matchId, String type, String status, UUID sportId, String sportName, String format,
        List<String> skillLevels, LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime expiresAt,
        UUID authorTeamId, String authorTeamName, Integer authorTeamReliabilityScore, UUID opponentTeamId,
        String opponentTeamName, UUID venueId, String venueName, String venueAddress, Integer venueOptionCount,
        Integer timeOptionCount, BigDecimal distanceKm, LocalDateTime createdAt) {
}
