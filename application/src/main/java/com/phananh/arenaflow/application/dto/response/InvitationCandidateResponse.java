package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

/** Người có thể mời; không có số điện thoại. {@code membershipStatus}: ACTIVE/PENDING với đội đang mời, null nếu chưa. */
public record InvitationCandidateResponse(UUID userId, String displayName, String avatarUrl, String membershipStatus) {
}
