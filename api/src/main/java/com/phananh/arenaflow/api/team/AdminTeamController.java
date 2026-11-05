package com.phananh.arenaflow.api.team;

import com.phananh.arenaflow.application.dto.request.MarkInactiveRequest;
import com.phananh.arenaflow.application.dto.response.AdminTeamSummaryResponse;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.MemberHistoryResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.TeamResponse;
import com.phananh.arenaflow.application.service.AdminTeamService;
import com.phananh.arenaflow.application.service.MemberHistoryStatusFilter;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Chỉ ADMIN (SecurityConfig `hasRole('ADMIN')` → 403 AUTH_004). */
@RestController
@RequestMapping("/api/v1/admin/teams")
@RequiredArgsConstructor
@Validated
public class AdminTeamController {

    private final AdminTeamService adminTeamService;

    @PatchMapping("/{teamId}/mark-inactive")
    public ResponseEntity<ApiResponse<TeamResponse>> markInactive(@PathVariable UUID teamId,
                                                  @Valid @RequestBody MarkInactiveRequest request) {
        return ResponseEntity.ok(ApiResponse.of(adminTeamService.markInactive(teamId, request)));
    }

    @PatchMapping("/{teamId}/reactivate")
    public ResponseEntity<ApiResponse<TeamResponse>> reactivate(@PathVariable UUID teamId) {
        return ResponseEntity.ok(ApiResponse.of(adminTeamService.reactivate(teamId)));
    }

    @GetMapping
    public ResponseEntity<PageResponse<AdminTeamSummaryResponse>> listTeams(
            @RequestParam(defaultValue = "ALL") TeamStatusFilter status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(adminTeamService.listTeams(status, page, pageSize));
    }

    /** Lịch sử thành viên ACTIVE/LEFT của đội ở mọi trạng thái; sort cố định joined_at, id (USER-002 API_Spec §2.4). */
    @GetMapping("/{teamId}/members")
    public ResponseEntity<PageResponse<MemberHistoryResponse>> listMemberHistory(
            @PathVariable UUID teamId,
            @RequestParam(defaultValue = "ALL") MemberHistoryStatusFilter status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(adminTeamService.listMemberHistory(teamId, status, page, pageSize));
    }
}
