package com.phananh.arenaflow.api.mercenary;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.CreateExternalMatchRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.ExternalMatchResponse;
import com.phananh.arenaflow.application.dto.response.MercenaryReportResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.RecruitmentResponse;
import com.phananh.arenaflow.application.dto.response.TeamMercenaryRatingResponse;
import com.phananh.arenaflow.application.service.ExternalMatchService;
import com.phananh.arenaflow.application.service.ExternalMatchStatusFilter;
import com.phananh.arenaflow.application.service.MercenaryRecruitmentService;
import com.phananh.arenaflow.application.service.MercenaryReportDirection;
import com.phananh.arenaflow.application.service.MercenaryReportService;
import com.phananh.arenaflow.application.service.RecruitmentStatusFilter;
import com.phananh.arenaflow.application.service.UserReputationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams/{teamId}")
@RequiredArgsConstructor
@Validated
public class TeamMercenaryController {

    private final MercenaryRecruitmentService recruitmentService;
    private final MercenaryReportService reportService;
    private final UserReputationService userReputationService;
    private final ExternalMatchService externalMatchService;

    @GetMapping("/mercenary-recruitments")
    public ResponseEntity<PageResponse<RecruitmentResponse>> listRecruitments(
            @PathVariable UUID teamId, @CurrentUser UUID currentUserId,
            @RequestParam(required = false) UUID matchId,
            @RequestParam(defaultValue = "ACTIVE") RecruitmentStatusFilter status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(recruitmentService.listTeamRecruitments(teamId, currentUserId, matchId, status, page,
                pageSize));
    }

    @GetMapping("/mercenary-reports")
    public ResponseEntity<PageResponse<MercenaryReportResponse>> listReports(
            @PathVariable UUID teamId, @CurrentUser UUID currentUserId,
            @RequestParam(defaultValue = "RECEIVED") MercenaryReportDirection direction,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(reportService.listTeamReports(teamId, currentUserId, direction, page, pageSize));
    }

    @GetMapping("/mercenary-ratings")
    public ResponseEntity<PageResponse<TeamMercenaryRatingResponse>> listMercenaryRatings(
            @PathVariable UUID teamId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(userReputationService.listTeamMercenaryRatings(teamId, page, pageSize));
    }

    @PostMapping("/external-matches")
    public ResponseEntity<ApiResponse<ExternalMatchResponse>> createExternalMatch(
            @PathVariable UUID teamId, @CurrentUser UUID currentUserId,
            @Valid @RequestBody CreateExternalMatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(externalMatchService.createExternalMatch(teamId, currentUserId, request)));
    }

    @GetMapping("/external-matches")
    public ResponseEntity<PageResponse<ExternalMatchResponse>> listExternalMatches(
            @PathVariable UUID teamId, @CurrentUser UUID currentUserId,
            @RequestParam(defaultValue = "UPCOMING") ExternalMatchStatusFilter status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(externalMatchService.listTeamExternalMatches(teamId, currentUserId, status, page,
                pageSize));
    }
}
