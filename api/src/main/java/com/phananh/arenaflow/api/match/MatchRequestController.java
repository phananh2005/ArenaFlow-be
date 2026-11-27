package com.phananh.arenaflow.api.match;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.RejectMatchRequestRequest;
import com.phananh.arenaflow.application.dto.request.WithdrawMatchRequestRequest;
import com.phananh.arenaflow.application.dto.response.AcceptMatchRequestResponse;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.MatchRequestWithdrawalResponse;
import com.phananh.arenaflow.application.service.MatchRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/match-requests/{requestId}")
@RequiredArgsConstructor
@Validated
public class MatchRequestController {

    private final MatchRequestService matchRequestService;

    @PostMapping("/accept")
    public ResponseEntity<ApiResponse<AcceptMatchRequestResponse>> acceptRequest(@PathVariable UUID requestId,
                                                                                 @CurrentUser UUID currentUserId) {
        return ResponseEntity.ok(ApiResponse.of(matchRequestService.acceptRequest(requestId, currentUserId)));
    }

    /** Body tuỳ chọn (chỉ lý do). */
    @PostMapping("/reject")
    public ResponseEntity<Void> rejectRequest(@PathVariable UUID requestId, @CurrentUser UUID currentUserId,
                                              @Valid @RequestBody(required = false) RejectMatchRequestRequest request) {
        matchRequestService.rejectRequest(requestId, currentUserId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<MatchRequestWithdrawalResponse>> withdrawRequest(@PathVariable UUID requestId,
            @CurrentUser UUID currentUserId, @Valid @RequestBody WithdrawMatchRequestRequest request) {
        return ResponseEntity.ok(ApiResponse.of(matchRequestService.withdrawRequest(requestId, currentUserId, request)));
    }
}
