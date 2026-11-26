package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Mọi field của tranh chấp Admin + trận, lý do report, bằng chứng, quyết định, điểm hiện tại hai đội, đánh giá trận. */
public record AdminDisputeDetailResponse(UUID disputeId, UUID reportId, UUID matchId, LocalDateTime matchStartsAt,
                                         AdminTeamBriefResponse reporterTeam, AdminTeamBriefResponse reportedTeam,
                                         UUID reportedById, String reportedByName, boolean isReportedByDeleted,
                                         UUID disputedById, String disputedByName, boolean isDisputedByDeleted,
                                         String status, String decision,
                                         UUID resolvedById, String resolvedByName, boolean isResolvedByDeleted,
                                         LocalDateTime resolvedAt, LocalDateTime reportCreatedAt,
                                         LocalDateTime createdAt, DisputeMatchResponse match, String reportReason,
                                         Integer reliabilityPenalty, String evidence, String resolutionNote,
                                         Integer reporterTeamReliabilityScore, Integer reportedTeamReliabilityScore,
                                         List<RatingResponse> matchRatings) {
}
