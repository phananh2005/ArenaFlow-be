package com.phananh.ArenaFlow.controller;

import com.phananh.ArenaFlow.dto.response.ApiResponse;
import com.phananh.ArenaFlow.dto.response.InvitationResponse;
import com.phananh.ArenaFlow.dto.response.MemberResponse;
import com.phananh.ArenaFlow.security.CurrentUser;
import com.phananh.ArenaFlow.service.TeamMembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Chỉ người được mời xử lý lời mời (kiểm trong service). */
@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
@Validated
public class InvitationController {

    private final TeamMembershipService membershipService;

    @PostMapping("/{invitationId}/accept")
    public ApiResponse<MemberResponse> accept(@PathVariable UUID invitationId, @CurrentUser UUID currentUserId) {
        return ApiResponse.of(membershipService.acceptInvitation(invitationId, currentUserId));
    }

    @PostMapping("/{invitationId}/reject")
    public ApiResponse<InvitationResponse> reject(@PathVariable UUID invitationId, @CurrentUser UUID currentUserId) {
        return ApiResponse.of(membershipService.rejectInvitation(invitationId, currentUserId));
    }
}
