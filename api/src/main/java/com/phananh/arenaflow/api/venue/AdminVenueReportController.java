package com.phananh.arenaflow.api.venue;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.ResolveVenueReportRequest;
import com.phananh.arenaflow.application.dto.response.AdminVenueReportResponse;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.service.AdminVenueService;
import com.phananh.arenaflow.application.service.VenueReportStatusFilter;
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
@RequestMapping("/api/v1/admin/venue-reports")
@RequiredArgsConstructor
@Validated
public class AdminVenueReportController {

    private final AdminVenueService adminVenueService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminVenueReportResponse>> listReports(
            @RequestParam(defaultValue = "PENDING") VenueReportStatusFilter status,
            @RequestParam(required = false) UUID venueId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(adminVenueService.listReports(status, venueId, page, pageSize));
    }

    @PatchMapping("/{reportId}/resolve")
    public ResponseEntity<ApiResponse<AdminVenueReportResponse>> resolveReport(@PathVariable UUID reportId,
            @CurrentUser UUID adminId, @Valid @RequestBody ResolveVenueReportRequest request) {
        return ResponseEntity.ok(ApiResponse.of(adminVenueService.resolveReport(reportId, adminId, request)));
    }
}
