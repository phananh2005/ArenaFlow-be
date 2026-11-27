package com.phananh.arenaflow.application.dto.response;

/** Uy tín công khai của ứng viên trên thẻ ứng viên. */
public record ReputationSummaryResponse(int reliabilityScore, String reliabilityLevel,
                                        UserReputationResponse.Completion completion,
                                        UserReputationResponse.Rating rating, boolean isVerified) {
}
