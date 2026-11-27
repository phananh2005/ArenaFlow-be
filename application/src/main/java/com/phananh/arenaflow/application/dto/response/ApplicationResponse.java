package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đơn ứng tuyển; sân và tên đội trưởng chỉ có với đội trưởng đội tuyển hoặc đơn đã duyệt. */
public record ApplicationResponse(UUID applicationId, UUID recruitmentId, String status, UUID conversationId,
                                  ApplicantBriefResponse applicant, Recruitment recruitment, LocalDateTime appliedAt,
                                  LocalDateTime respondedAt, LocalDateTime withdrawnAt) {

    public record Recruitment(String position, String status, MercenaryTeamBriefResponse team, Match match,
                              String captainName) {
    }

    public record Match(String source, UUID matchId, UUID externalMatchId, String opponentName, LocalDateTime startsAt,
                        LocalDateTime endsAt, String status, AreaResponse area, VenueRevealResponse venue) {
    }
}
