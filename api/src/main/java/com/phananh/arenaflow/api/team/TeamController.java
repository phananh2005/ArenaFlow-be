package com.phananh.arenaflow.api.team;

import com.phananh.arenaflow.application.dto.request.CreateTeamRequest;
import com.phananh.arenaflow.application.dto.request.InviteMemberRequest;
import com.phananh.arenaflow.application.dto.request.TransferCaptainRequest;
import com.phananh.arenaflow.application.dto.request.UpdateTeamRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.CaptainHistoryResponse;
import com.phananh.arenaflow.application.dto.response.CaptainTransferResponse;
import com.phananh.arenaflow.application.dto.response.InvitationResponse;
import com.phananh.arenaflow.application.dto.response.MemberResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.TeamResponse;
import com.phananh.arenaflow.application.dto.response.TeamSummaryResponse;
import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.service.TeamMembershipService;
import com.phananh.arenaflow.application.service.TeamService;
import com.phananh.arenaflow.application.service.TeamStatusFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/** Quyền captain/thành viên kiểm trong service (thứ tự 404 → 403). */
@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
@Validated
public class TeamController {

    private final TeamService teamService;
    private final TeamMembershipService membershipService;

    @PostMapping
    public ResponseEntity<ApiResponse<TeamResponse>> createTeam(@CurrentUser UUID currentUserId,
                                                                @Valid @RequestBody CreateTeamRequest request) {
        TeamResponse team = teamService.createTeam(currentUserId, request);
        return ResponseEntity.created(URI.create("/api/v1/teams/" + team.teamId())).body(ApiResponse.of(team));
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<ApiResponse<TeamResponse>> getTeam(@PathVariable UUID teamId) {
        return ResponseEntity.ok(ApiResponse.of(teamService.getTeam(teamId)));
    }

    @GetMapping
    public ResponseEntity<PageResponse<TeamSummaryResponse>> listTeams(
            @RequestParam(defaultValue = "ACTIVE") TeamStatusFilter status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(teamService.listTeams(status, page, pageSize));
    }

    @PatchMapping("/{teamId}")
    public ResponseEntity<ApiResponse<TeamResponse>> updateTeam(@PathVariable UUID teamId, @CurrentUser UUID currentUserId,
                                                @Valid @RequestBody UpdateTeamRequest request) {
        return ResponseEntity.ok(ApiResponse.of(teamService.updateTeam(teamId, currentUserId, request)));
    }

    @PostMapping("/{teamId}/invitations")
    public ResponseEntity<ApiResponse<InvitationResponse>> inviteMember(@PathVariable UUID teamId,
                                                                        @CurrentUser UUID currentUserId,
                                                                        @Valid @RequestBody InviteMemberRequest request) {
        InvitationResponse invitation = membershipService.inviteMember(teamId, currentUserId, request);
        return ResponseEntity.created(URI.create("/api/v1/invitations/" + invitation.invitationId()))
                .body(ApiResponse.of(invitation));
    }

    @PostMapping("/{teamId}/captain/transfer")
    public ResponseEntity<ApiResponse<CaptainTransferResponse>> transferCaptain(@PathVariable UUID teamId,
                                                                @CurrentUser UUID currentUserId,
                                                                @Valid @RequestBody TransferCaptainRequest request) {
        return ResponseEntity.ok(ApiResponse.of(teamService.transferCaptain(teamId, currentUserId, request)));
    }

    @GetMapping("/{teamId}/captain/history")
    public ResponseEntity<PageResponse<CaptainHistoryResponse>> listCaptainHistory(
            @PathVariable UUID teamId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(teamService.listCaptainHistory(teamId, page, pageSize));
    }

    @GetMapping("/{teamId}/members")
    public ResponseEntity<PageResponse<MemberResponse>> listMembers(
            @PathVariable UUID teamId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(membershipService.listMembers(teamId, page, pageSize));
    }

    @PostMapping("/{teamId}/members/leave")
    public ResponseEntity<Void> leaveTeam(@PathVariable UUID teamId, @CurrentUser UUID currentUserId) {
        membershipService.leaveTeam(teamId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
