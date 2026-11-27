package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Chi tiết tranh chấp liên quan mercenary cho Admin. */
public record AdminMercenaryDisputeDetailResponse(UUID disputeId, UUID reportId, UUID applicationId, String source,
                                                  UUID matchId, UUID externalMatchId, LocalDateTime matchStartsAt,
                                                  String reporterSide, AdminMercenaryDisputeResponse.Team team,
                                                  UUID mercenaryId, String mercenaryName, boolean isMercenaryDeleted,
                                                  UUID reportedById, String reportedByName,
                                                  boolean isReportedByDeleted, UUID disputedById,
                                                  String disputedByName, boolean isDisputedByDeleted, String status,
                                                  String decision, UUID resolvedById, String resolvedByName,
                                                  boolean isResolvedByDeleted, LocalDateTime resolvedAt,
                                                  LocalDateTime reportCreatedAt, LocalDateTime createdAt, Match match,
                                                  String position, String reportReason, int reliabilityPenalty,
                                                  String evidence, String resolutionNote, int teamReliabilityScore,
                                                  int mercenaryReliabilityScore,
                                                  List<MercenaryRatingResponse> ratings) {

    public record Match(String source, String sportName, String status, LocalDateTime startsAt, LocalDateTime endsAt,
                        String venueName, String opponentName) {
    }
}
