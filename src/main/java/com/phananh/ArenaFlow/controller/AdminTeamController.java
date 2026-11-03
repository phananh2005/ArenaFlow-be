package com.phananh.ArenaFlow.controller;

import com.phananh.ArenaFlow.dto.request.MarkInactiveRequest;
import com.phananh.ArenaFlow.dto.response.AdminTeamSummaryResponse;
import com.phananh.ArenaFlow.dto.response.ApiResponse;
import com.phananh.ArenaFlow.dto.response.PageResponse;
import com.phananh.ArenaFlow.dto.response.TeamResponse;
import com.phananh.ArenaFlow.service.AdminTeamService;
import com.phananh.ArenaFlow.service.TeamStatusFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
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
    public ApiResponse<TeamResponse> markInactive(@PathVariable UUID teamId,
                                                  @Valid @RequestBody MarkInactiveRequest request) {
        return ApiResponse.of(adminTeamService.markInactive(teamId, request));
    }

    @PatchMapping("/{teamId}/reactivate")
    public ApiResponse<TeamResponse> reactivate(@PathVariable UUID teamId) {
        return ApiResponse.of(adminTeamService.reactivate(teamId));
    }

    @GetMapping
    public PageResponse<AdminTeamSummaryResponse> listTeams(
            @RequestParam(defaultValue = "ALL") TeamStatusFilter status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return adminTeamService.listTeams(status, page, pageSize);
    }
}
