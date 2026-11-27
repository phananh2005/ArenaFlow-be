package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Report giữa đội tuyển và mercenary theo góc nhìn người xem. */
public record MercenaryReportResponse(UUID reportId, UUID applicationId, String source, UUID matchId,
                                      UUID externalMatchId, LocalDateTime matchStartsAt, String direction,
                                      String reporterSide, MercenaryTeamBriefResponse team,
                                      MercenaryUserBriefResponse mercenary, String reportType, String reason,
                                      String status, LocalDateTime disputeDeadlineAt, boolean canDispute,
                                      Integer reliabilityPenalty, Dispute dispute, LocalDateTime createdAt) {

    /** Tranh chấp; {@code evidence} null khi người xem là bên đã report. */
    public record Dispute(UUID disputeId, String status, String evidence, String decision, String resolutionNote,
                          LocalDateTime createdAt, LocalDateTime resolvedAt) {
    }
}
