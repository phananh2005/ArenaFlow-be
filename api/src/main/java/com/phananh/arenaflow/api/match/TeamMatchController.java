package com.phananh.arenaflow.api.match;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.response.MatchRequestResponse;
import com.phananh.arenaflow.application.dto.response.MatchSummaryResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.service.MatchRequestService;
import com.phananh.arenaflow.application.service.MatchRequestStatusFilter;
import com.phananh.arenaflow.application.service.MatchRoleFilter;
import com.phananh.arenaflow.application.service.MatchService;
import com.phananh.arenaflow.application.service.MatchStatusFilter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams/{teamId}")
@RequiredArgsConstructor
@Validated
public class TeamMatchController {

    private final MatchService matchService;
    private final MatchRequestService matchRequestService;

    @GetMapping("/matches")
    public ResponseEntity<PageResponse<MatchSummaryResponse>> listTeamMatches(
            @PathVariable UUID teamId,
            @RequestParam(defaultValue = "ALL") MatchStatusFilter status,
            @RequestParam(defaultValue = "ALL") MatchRoleFilter role,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(matchService.listTeamMatches(teamId, status, role, page, pageSize));
    }

    @GetMapping("/match-requests")
    public ResponseEntity<PageResponse<MatchRequestResponse>> listTeamRequests(
            @PathVariable UUID teamId,
            @CurrentUser UUID currentUserId,
            @RequestParam(defaultValue = "ALL") MatchRequestStatusFilter status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(matchRequestService.listTeamRequests(teamId, currentUserId, status, page, pageSize));
    }
}
