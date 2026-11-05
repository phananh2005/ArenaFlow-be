package com.phananh.arenaflow.api.team;

import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.MemberResponse;
import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.service.TeamMembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ApiResponse<MemberResponse>> accept(@PathVariable UUID invitationId, @CurrentUser UUID currentUserId) {
        return ResponseEntity.ok(ApiResponse.of(membershipService.acceptInvitation(invitationId, currentUserId)));
    }

    @PostMapping("/{invitationId}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID invitationId, @CurrentUser UUID currentUserId) {
        membershipService.rejectInvitation(invitationId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
