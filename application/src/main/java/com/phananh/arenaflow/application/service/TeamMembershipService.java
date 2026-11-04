package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.InviteMemberRequest;
import com.phananh.arenaflow.application.dto.response.InvitationResponse;
import com.phananh.arenaflow.application.dto.response.MemberResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;

import java.util.UUID;

public interface TeamMembershipService {

    InvitationResponse inviteMember(UUID teamId, UUID captainUserId, InviteMemberRequest request);

    MemberResponse acceptInvitation(UUID invitationId, UUID userId);

    InvitationResponse rejectInvitation(UUID invitationId, UUID userId);

    PageResponse<MemberResponse> listMembers(UUID teamId, int page, int pageSize);

    void leaveTeam(UUID teamId, UUID userId);
}
