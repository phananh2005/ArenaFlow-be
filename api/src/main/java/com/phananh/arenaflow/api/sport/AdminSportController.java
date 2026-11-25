package com.phananh.arenaflow.api.sport;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.UpdateSportRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.SportResponse;
import com.phananh.arenaflow.application.service.SportService;
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

/** Chỉ ADMIN (SecurityConfig `hasRole('ADMIN')` → 403 AUTH_004). */
@RestController
@RequestMapping("/api/v1/admin/sports")
@RequiredArgsConstructor
@Validated
public class AdminSportController {

    private final SportService sportService;

    @GetMapping
    public ResponseEntity<PageResponse<SportResponse>> listSports(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(sportService.listSports(isActive, page, pageSize));
    }

    @PatchMapping("/{sportId}")
    public ResponseEntity<ApiResponse<SportResponse>> updateSport(@PathVariable UUID sportId,
            @CurrentUser UUID currentUserId, @Valid @RequestBody UpdateSportRequest request) {
        return ResponseEntity.ok(ApiResponse.of(sportService.updateSport(sportId, currentUserId, request)));
    }
}
