package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Người có thể mời vào đội; {@code membershipStatus} là PENDING/ACTIVE với chính đội đang mời, null nếu chưa có. */
public interface InvitationCandidateView {
    UUID getUserId();
    String getDisplayName();
    String getAvatarUrl();
    String getMembershipStatus();
}
