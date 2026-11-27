package com.phananh.arenaflow.api.profile;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.response.MyInvitationResponse;
import com.phananh.arenaflow.application.dto.response.MyTeamResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.service.MyTeamRoleFilter;
import com.phananh.arenaflow.application.service.TeamMembershipService;
import com.phananh.arenaflow.application.service.TeamService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Lời mời và đội của chính người gọi; không có tham số xem thay người khác, kể cả Admin. */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Validated
public class ProfileTeamController {

    private final TeamMembershipService membershipService;
    private final TeamService teamService;

    @GetMapping("/invitations")
    public ResponseEntity<PageResponse<MyInvitationResponse>> listMyInvitations(
            @CurrentUser UUID currentUserId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(membershipService.listMyInvitations(currentUserId, page, pageSize));
    }

    /** {@code role} bỏ trống = mọi đội; giá trị khác CAPTAIN → 400 VALIDATION_ERROR. */
    @GetMapping("/teams")
    public ResponseEntity<PageResponse<MyTeamResponse>> listMyTeams(
            @CurrentUser UUID currentUserId,
            @RequestParam(required = false) MyTeamRoleFilter role,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(teamService.listMyTeams(currentUserId, role, page, pageSize));
    }
}
