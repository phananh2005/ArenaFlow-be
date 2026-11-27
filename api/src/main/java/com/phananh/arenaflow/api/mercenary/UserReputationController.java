package com.phananh.arenaflow.api.mercenary;

import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.UserReputationResponse;
import com.phananh.arenaflow.application.service.UserReputationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/reputation")
@RequiredArgsConstructor
public class UserReputationController {

    private final UserReputationService userReputationService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserReputationResponse>> getReputation(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.of(userReputationService.getUserReputation(userId)));
    }
}
