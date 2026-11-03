package com.phananh.ArenaFlow.service;

import com.phananh.ArenaFlow.dto.request.InviteMemberRequest;
import com.phananh.ArenaFlow.dto.response.InvitationResponse;
import com.phananh.ArenaFlow.dto.response.MemberResponse;
import com.phananh.ArenaFlow.dto.response.PageResponse;

import java.util.UUID;

public interface TeamMembershipService {

    InvitationResponse inviteMember(UUID teamId, UUID captainUserId, InviteMemberRequest request);

    MemberResponse acceptInvitation(UUID invitationId, UUID userId);

    InvitationResponse rejectInvitation(UUID invitationId, UUID userId);

    PageResponse<MemberResponse> listMembers(UUID teamId, int page, int pageSize);

    void leaveTeam(UUID teamId, UUID userId);
}
