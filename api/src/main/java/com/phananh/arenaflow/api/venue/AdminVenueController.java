package com.phananh.arenaflow.api.venue;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.response.AdminVenueResponse;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.service.AdminVenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Chỉ ADMIN (SecurityConfig `hasRole('ADMIN')` → 403 AUTH_004). */
@RestController
@RequestMapping("/api/v1/admin/venues")
@RequiredArgsConstructor
@Validated
public class AdminVenueController {

    private final AdminVenueService adminVenueService;

    @PatchMapping("/{venueId}/mark-inactive")
    public ResponseEntity<ApiResponse<AdminVenueResponse>> markInactive(@PathVariable UUID venueId,
                                                                        @CurrentUser UUID adminId) {
        return ResponseEntity.ok(ApiResponse.of(adminVenueService.markInactive(venueId, adminId)));
    }

    @PatchMapping("/{venueId}/reactivate")
    public ResponseEntity<ApiResponse<AdminVenueResponse>> reactivate(@PathVariable UUID venueId,
                                                                      @CurrentUser UUID adminId) {
        return ResponseEntity.ok(ApiResponse.of(adminVenueService.reactivate(venueId, adminId)));
    }
}
