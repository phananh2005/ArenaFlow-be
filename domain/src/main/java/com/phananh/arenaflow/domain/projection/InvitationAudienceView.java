package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Trạng thái lời mời, người được mời và tên đội. */
public interface InvitationAudienceView {
    String getInvitationStatus();
    UUID getInviteeUserId();
    String getTeamName();
}
