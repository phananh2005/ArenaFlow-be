package com.phananh.arenaflow.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Trận của tin tuyển (kèo trên hệ thống hoặc trận ngoài hệ thống). */
public record RecruitmentMatchResponse(String source, UUID externalMatchId, String opponentName, LocalDateTime startsAt,
                                       LocalDateTime endsAt, String format, List<String> skillLevels, AreaResponse area,
                                       BigDecimal distanceKm, VenueRevealResponse venue) {
}
