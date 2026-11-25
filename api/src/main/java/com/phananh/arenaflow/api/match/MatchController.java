package com.phananh.arenaflow.api.match;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.CancelMatchRequest;
import com.phananh.arenaflow.application.dto.request.CreateMatchRequest;
import com.phananh.arenaflow.application.dto.request.SearchMatchRequest;
import com.phananh.arenaflow.application.dto.request.SendMatchRequestRequest;
import com.phananh.arenaflow.application.dto.request.UpdateCostAgreementRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.CostAgreementResponse;
import com.phananh.arenaflow.application.dto.response.MatchCancellationResponse;
import com.phananh.arenaflow.application.dto.response.MatchRequestResponse;
import com.phananh.arenaflow.application.dto.response.MatchResponse;
import com.phananh.arenaflow.application.dto.response.MatchSummaryResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.service.MatchRequestService;
import com.phananh.arenaflow.application.service.MatchRequestStatusFilter;
import com.phananh.arenaflow.application.service.MatchService;
import com.phananh.arenaflow.application.validation.OneOf;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
@Validated
public class MatchController {

    private final MatchService matchService;
    private final MatchRequestService matchRequestService;

    @PostMapping
    public ResponseEntity<ApiResponse<MatchResponse>> createMatch(@CurrentUser UUID currentUserId,
                                                                  @Valid @RequestBody CreateMatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(matchService.createMatch(currentUserId, request)));
    }

    @GetMapping
    public ResponseEntity<PageResponse<MatchSummaryResponse>> searchMatches(
            @Valid @ModelAttribute SearchMatchRequest request) {
        return ResponseEntity.ok(matchService.searchMatches(request));
    }

    @GetMapping("/{matchId}")
    public ResponseEntity<ApiResponse<MatchResponse>> getMatch(@PathVariable UUID matchId) {
        return ResponseEntity.ok(ApiResponse.of(matchService.getMatch(matchId)));
    }

    @PutMapping("/{matchId}/cost-agreement")
    public ResponseEntity<ApiResponse<CostAgreementResponse>> updateCostAgreement(@PathVariable UUID matchId,
            @CurrentUser UUID currentUserId, @Valid @RequestBody UpdateCostAgreementRequest request) {
        return ResponseEntity.ok(ApiResponse.of(matchService.updateCostAgreement(matchId, currentUserId, request)));
    }

    @PostMapping("/{matchId}/requests")
    public ResponseEntity<ApiResponse<MatchRequestResponse>> sendRequest(@PathVariable UUID matchId,
            @CurrentUser UUID currentUserId, @Valid @RequestBody SendMatchRequestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(matchRequestService.sendRequest(matchId, currentUserId, request)));
    }

    /** {@code status}: chỉ PENDING (mặc định) hoặc ALL. */
    @GetMapping("/{matchId}/requests")
    public ResponseEntity<PageResponse<MatchRequestResponse>> listMatchRequests(@PathVariable UUID matchId,
            @CurrentUser UUID currentUserId,
            @RequestParam(defaultValue = "PENDING") @OneOf({"PENDING", "ALL"}) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(matchRequestService.listMatchRequests(matchId, currentUserId,
                MatchRequestStatusFilter.valueOf(status), page, pageSize));
    }

    @PostMapping("/{matchId}/cancel")
    public ResponseEntity<ApiResponse<MatchCancellationResponse>> cancelMatch(@PathVariable UUID matchId,
            @CurrentUser UUID currentUserId, @Valid @RequestBody CancelMatchRequest request) {
        return ResponseEntity.ok(ApiResponse.of(matchService.cancelMatch(matchId, currentUserId, request)));
    }
}
