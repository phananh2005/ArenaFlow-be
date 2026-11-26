package com.phananh.arenaflow.api.reputation;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.CreateRatingRequest;
import com.phananh.arenaflow.application.dto.request.CreateReportRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.MatchRatingsResponse;
import com.phananh.arenaflow.application.dto.response.RatingCreatedResponse;
import com.phananh.arenaflow.application.dto.response.ReportResponse;
import com.phananh.arenaflow.application.service.RatingService;
import com.phananh.arenaflow.application.service.ReportService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/matches/{matchId}")
@RequiredArgsConstructor
@Validated
public class MatchReputationController {

    private final RatingService ratingService;
    private final ReportService reportService;

    @PostMapping("/ratings")
    public ResponseEntity<ApiResponse<RatingCreatedResponse>> createRating(@PathVariable UUID matchId,
            @CurrentUser UUID currentUserId, @Valid @RequestBody CreateRatingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(ratingService.createRating(matchId, currentUserId, request)));
    }

    @GetMapping("/ratings")
    public ResponseEntity<ApiResponse<MatchRatingsResponse>> getMatchRatings(@PathVariable UUID matchId,
            @RequestParam UUID teamId, @CurrentUser UUID currentUserId) {
        return ResponseEntity.ok(ApiResponse.of(ratingService.getMatchRatings(matchId, teamId, currentUserId)));
    }

    @PostMapping("/reports")
    public ResponseEntity<ApiResponse<ReportResponse>> createReport(@PathVariable UUID matchId,
            @CurrentUser UUID currentUserId, @Valid @RequestBody CreateReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(reportService.createReport(matchId, currentUserId, request)));
    }
}
