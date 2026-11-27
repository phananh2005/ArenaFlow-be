package com.phananh.arenaflow.api.mercenary;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.CreateRecruitmentsRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.RecruitmentResponse;
import com.phananh.arenaflow.application.service.MercenaryRecruitmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matches/{matchId}/mercenary-recruitments")
@RequiredArgsConstructor
@Validated
public class MatchRecruitmentController {

    private final MercenaryRecruitmentService recruitmentService;

    @PostMapping
    public ResponseEntity<ApiResponse<List<RecruitmentResponse>>> createRecruitments(
            @PathVariable UUID matchId, @CurrentUser UUID currentUserId,
            @Valid @RequestBody CreateRecruitmentsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(recruitmentService.createRecruitments(matchId, currentUserId, request)));
    }
}
