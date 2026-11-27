package com.phananh.arenaflow.api.mercenary;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.AddRecruitmentsRequest;
import com.phananh.arenaflow.application.dto.request.UpdateExternalMatchRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.ExternalMatchCancellationResponse;
import com.phananh.arenaflow.application.dto.response.ExternalMatchResponse;
import com.phananh.arenaflow.application.dto.response.RecruitmentResponse;
import com.phananh.arenaflow.application.service.ExternalMatchService;
import com.phananh.arenaflow.application.service.MercenaryRecruitmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/external-matches/{externalMatchId}")
@RequiredArgsConstructor
@Validated
public class ExternalMatchController {

    private final ExternalMatchService externalMatchService;
    private final MercenaryRecruitmentService recruitmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<ExternalMatchResponse>> getExternalMatch(@PathVariable UUID externalMatchId,
                                                                               @CurrentUser UUID currentUserId) {
        return ResponseEntity.ok(ApiResponse.of(externalMatchService.getExternalMatch(externalMatchId, currentUserId)));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<ExternalMatchResponse>> updateExternalMatch(
            @PathVariable UUID externalMatchId, @CurrentUser UUID currentUserId,
            @Valid @RequestBody UpdateExternalMatchRequest request) {
        return ResponseEntity.ok(ApiResponse.of(externalMatchService.updateExternalMatch(externalMatchId,
                currentUserId, request)));
    }

    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<ExternalMatchCancellationResponse>> cancelExternalMatch(
            @PathVariable UUID externalMatchId, @CurrentUser UUID currentUserId) {
        return ResponseEntity.ok(ApiResponse.of(externalMatchService.cancelExternalMatch(externalMatchId,
                currentUserId)));
    }

    @PostMapping("/mercenary-recruitments")
    public ResponseEntity<ApiResponse<List<RecruitmentResponse>>> addRecruitments(
            @PathVariable UUID externalMatchId, @CurrentUser UUID currentUserId,
            @Valid @RequestBody AddRecruitmentsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                recruitmentService.addExternalRecruitments(externalMatchId, currentUserId, request)));
    }
}
