package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Ứng viên của tin (góc nhìn đội trưởng) kèm uy tín công khai. */
public record ApplicantResponse(UUID applicationId, String status, UUID conversationId, ApplicantBriefResponse applicant,
                               ReputationSummaryResponse reputation, LocalDateTime appliedAt,
                               LocalDateTime respondedAt, LocalDateTime withdrawnAt) {
}
