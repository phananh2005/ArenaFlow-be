package com.phananh.arenaflow.api.pairing;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.ConfirmPairingRequest;
import com.phananh.arenaflow.application.dto.request.UpdatePairingCostAgreementRequest;
import com.phananh.arenaflow.application.dto.request.UpdateProposalRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.PairingConfirmationResponse;
import com.phananh.arenaflow.application.dto.response.PairingCostUpdateResponse;
import com.phananh.arenaflow.application.dto.response.PairingResponse;
import com.phananh.arenaflow.application.dto.response.ProposalUpdateResponse;
import com.phananh.arenaflow.application.service.PairingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Thao tác trên một thương lượng: xem, chốt phương án, lưu chi phí + cọc, xác nhận. */
@RestController
@RequestMapping("/api/v1/pairings/{pairingId}")
@RequiredArgsConstructor
@Validated
public class PairingController {

    private final PairingService pairingService;

    @GetMapping
    public ResponseEntity<ApiResponse<PairingResponse>> getPairing(@PathVariable UUID pairingId,
                                                                   @CurrentUser UUID currentUserId) {
        return ResponseEntity.ok(ApiResponse.of(pairingService.getPairing(pairingId, currentUserId)));
    }

    @PutMapping("/proposal")
    public ResponseEntity<ApiResponse<ProposalUpdateResponse>> updateProposal(@PathVariable UUID pairingId,
            @CurrentUser UUID currentUserId, @Valid @RequestBody UpdateProposalRequest request) {
        return ResponseEntity.ok(ApiResponse.of(pairingService.updateProposal(pairingId, currentUserId, request)));
    }

    @PutMapping("/cost-agreement")
    public ResponseEntity<ApiResponse<PairingCostUpdateResponse>> updateCostAgreement(@PathVariable UUID pairingId,
            @CurrentUser UUID currentUserId, @Valid @RequestBody UpdatePairingCostAgreementRequest request) {
        return ResponseEntity.ok(ApiResponse.of(pairingService.updateCostAgreement(pairingId, currentUserId, request)));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<PairingConfirmationResponse>> confirm(@PathVariable UUID pairingId,
            @CurrentUser UUID currentUserId, @Valid @RequestBody ConfirmPairingRequest request) {
        return ResponseEntity.ok(ApiResponse.of(pairingService.confirm(pairingId, currentUserId, request)));
    }
}
