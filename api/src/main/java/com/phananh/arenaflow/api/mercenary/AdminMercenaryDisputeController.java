package com.phananh.arenaflow.api.mercenary;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.ResolveDisputeRequest;
import com.phananh.arenaflow.application.dto.response.AdminMercenaryDisputeDetailResponse;
import com.phananh.arenaflow.application.dto.response.AdminMercenaryDisputeResponse;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.service.AdminMercenaryDisputeService;
import com.phananh.arenaflow.application.service.DisputeStatusFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Chỉ ADMIN (SecurityConfig cho {@code /api/v1/admin/**} → 403 AUTH_004). */
@RestController
@RequestMapping("/api/v1/admin/mercenary-disputes")
@RequiredArgsConstructor
@Validated
public class AdminMercenaryDisputeController {

    private final AdminMercenaryDisputeService disputeService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminMercenaryDisputeResponse>> listDisputes(
            @RequestParam(defaultValue = "PENDING") DisputeStatusFilter status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(disputeService.listDisputes(status, page, pageSize));
    }

    @GetMapping("/{disputeId}")
    public ResponseEntity<ApiResponse<AdminMercenaryDisputeDetailResponse>> getDispute(@PathVariable UUID disputeId) {
        return ResponseEntity.ok(ApiResponse.of(disputeService.getDispute(disputeId)));
    }

    @PatchMapping("/{disputeId}/resolve")
    public ResponseEntity<ApiResponse<AdminMercenaryDisputeDetailResponse>> resolveDispute(
            @PathVariable UUID disputeId, @CurrentUser UUID currentUserId,
            @Valid @RequestBody ResolveDisputeRequest request) {
        return ResponseEntity.ok(ApiResponse.of(disputeService.resolveDispute(disputeId, currentUserId, request)));
    }
}
