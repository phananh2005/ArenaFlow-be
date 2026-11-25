package com.phananh.arenaflow.api.pairing;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.CreatePairingRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.PairingResponse;
import com.phananh.arenaflow.application.dto.response.PairingSummaryResponse;
import com.phananh.arenaflow.application.service.PairingService;
import com.phananh.arenaflow.application.service.PairingStatusFilter;
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

/** Nhận kèo luồng B và danh sách thương lượng của một kèo (đội trưởng đội tạo kèo). */
@RestController
@RequestMapping("/api/v1/matches/{matchId}/pairings")
@RequiredArgsConstructor
@Validated
public class MatchPairingController {

    private final PairingService pairingService;

    @PostMapping
    public ResponseEntity<ApiResponse<PairingResponse>> createPairing(@PathVariable UUID matchId,
            @CurrentUser UUID currentUserId, @Valid @RequestBody CreatePairingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(pairingService.createPairing(matchId, currentUserId, request)));
    }

    @GetMapping
    public ResponseEntity<PageResponse<PairingSummaryResponse>> listMatchPairings(@PathVariable UUID matchId,
            @CurrentUser UUID currentUserId,
            @RequestParam(defaultValue = "ALL") PairingStatusFilter status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(pairingService.listMatchPairings(matchId, currentUserId, status, page, pageSize));
    }
}
