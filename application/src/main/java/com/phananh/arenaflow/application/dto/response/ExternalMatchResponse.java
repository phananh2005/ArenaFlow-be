package com.phananh.arenaflow.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Trận ngoài hệ thống; {@code recruitments} chỉ có ở response khai báo. */
public record ExternalMatchResponse(UUID externalMatchId, UUID teamId, String status, LocalDateTime startsAt,
                                    LocalDateTime endsAt, List<String> skillLevels, String opponentName,
                                    VenueRevealResponse venue, AreaResponse area, boolean hasApprovedMercenary,
                                    LocalDateTime cancelledAt, LocalDateTime createdAt, LocalDateTime updatedAt,
                                    @JsonInclude(JsonInclude.Include.NON_NULL) List<RecruitmentResponse> recruitments) {
}
