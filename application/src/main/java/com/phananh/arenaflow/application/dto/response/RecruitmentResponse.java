package com.phananh.arenaflow.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.UUID;

/** Tin tuyển mercenary; {@code rejectedApplicationCount} chỉ có ở response đóng tin. */
public record RecruitmentResponse(UUID recruitmentId, UUID matchId, MercenaryTeamBriefResponse team, String position,
                                  int quantityNeeded, int quantityApproved, String status,
                                  RecruitmentMatchResponse match, String captainName, String viewerRole,
                                  MyApplicationResponse myApplication, Integer pendingApplicationCount,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) Integer rejectedApplicationCount,
                                  LocalDateTime closedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {

    /** Đơn PENDING/APPROVED của người xem ở tin. */
    public record MyApplicationResponse(UUID applicationId, String status, UUID conversationId) {
    }
}
