package com.phananh.arenaflow.api.mercenary;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.CreateMercenaryRatingRequest;
import com.phananh.arenaflow.application.dto.request.CreateReportRequest;
import com.phananh.arenaflow.application.dto.request.WithdrawApplicationRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.ApplicationResponse;
import com.phananh.arenaflow.application.dto.response.ApprovalResponse;
import com.phananh.arenaflow.application.dto.response.MercenaryRatingResponse;
import com.phananh.arenaflow.application.dto.response.MercenaryRatingsResponse;
import com.phananh.arenaflow.application.dto.response.MercenaryReportResponse;
import com.phananh.arenaflow.application.dto.response.WithdrawalResponse;
import com.phananh.arenaflow.application.service.MercenaryApplicationService;
import com.phananh.arenaflow.application.service.MercenaryRatingService;
import com.phananh.arenaflow.application.service.MercenaryReportService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mercenary-applications/{applicationId}")
@RequiredArgsConstructor
@Validated
public class ApplicationController {

    private final MercenaryApplicationService applicationService;
    private final MercenaryRatingService ratingService;
    private final MercenaryReportService reportService;

    @GetMapping
    public ResponseEntity<ApiResponse<ApplicationResponse>> getApplication(@PathVariable UUID applicationId,
                                                                           @CurrentUser UUID currentUserId) {
        return ResponseEntity.ok(ApiResponse.of(applicationService.getApplication(applicationId, currentUserId)));
    }

    @PostMapping("/approve")
    public ResponseEntity<ApiResponse<ApprovalResponse>> approve(@PathVariable UUID applicationId,
                                                                 @CurrentUser UUID currentUserId) {
        return ResponseEntity.ok(ApiResponse.of(applicationService.approve(applicationId, currentUserId)));
    }

    @PostMapping("/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID applicationId, @CurrentUser UUID currentUserId) {
        applicationService.reject(applicationId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> withdraw(@PathVariable UUID applicationId,
                                                                    @CurrentUser UUID currentUserId,
                                                                    @Valid @RequestBody WithdrawApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.of(applicationService.withdraw(applicationId, currentUserId, request)));
    }

    @PostMapping("/ratings")
    public ResponseEntity<ApiResponse<MercenaryRatingResponse>> createRating(
            @PathVariable UUID applicationId, @CurrentUser UUID currentUserId,
            @Valid @RequestBody CreateMercenaryRatingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(ratingService.createRating(applicationId, currentUserId, request)));
    }

    @GetMapping("/ratings")
    public ResponseEntity<ApiResponse<MercenaryRatingsResponse>> getRatings(@PathVariable UUID applicationId,
                                                                            @CurrentUser UUID currentUserId) {
        return ResponseEntity.ok(ApiResponse.of(ratingService.getRatings(applicationId, currentUserId)));
    }

    @PostMapping("/reports")
    public ResponseEntity<ApiResponse<MercenaryReportResponse>> createReport(
            @PathVariable UUID applicationId, @CurrentUser UUID currentUserId,
            @Valid @RequestBody CreateReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(reportService.createReport(applicationId, currentUserId, request)));
    }
}
