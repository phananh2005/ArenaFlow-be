package com.phananh.arenaflow.api.reputation;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.ReportResponse;
import com.phananh.arenaflow.application.dto.response.TeamReputationResponse;
import com.phananh.arenaflow.application.service.ReportDirection;
import com.phananh.arenaflow.application.service.ReportService;
import com.phananh.arenaflow.application.service.TeamReputationService;
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
public class TeamReputationController {

    private final ReportService reportService;
    private final TeamReputationService teamReputationService;

    @GetMapping("/reputation")
    public ResponseEntity<ApiResponse<TeamReputationResponse>> getTeamReputation(@PathVariable UUID teamId) {
        return ResponseEntity.ok(ApiResponse.of(teamReputationService.getTeamReputation(teamId)));
    }

    @GetMapping("/reports")
    public ResponseEntity<PageResponse<ReportResponse>> listTeamReports(@PathVariable UUID teamId,
            @CurrentUser UUID currentUserId,
            @RequestParam(defaultValue = "RECEIVED") ReportDirection direction,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(reportService.listTeamReports(teamId, currentUserId, direction, page, pageSize));
    }
}
