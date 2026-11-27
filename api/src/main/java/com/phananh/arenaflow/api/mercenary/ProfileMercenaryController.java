package com.phananh.arenaflow.api.mercenary;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.response.ApplicationResponse;
import com.phananh.arenaflow.application.dto.response.MercenaryReportResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.service.ApplicationStatusFilter;
import com.phananh.arenaflow.application.service.MercenaryApplicationService;
import com.phananh.arenaflow.application.service.MercenaryReportDirection;
import com.phananh.arenaflow.application.service.MercenaryReportService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Validated
public class ProfileMercenaryController {

    private final MercenaryApplicationService applicationService;
    private final MercenaryReportService reportService;

    @GetMapping("/mercenary-applications")
    public ResponseEntity<PageResponse<ApplicationResponse>> listMyApplications(
            @CurrentUser UUID currentUserId,
            @RequestParam(defaultValue = "ACTIVE") ApplicationStatusFilter status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(applicationService.listMyApplications(currentUserId, status, page, pageSize));
    }

    @GetMapping("/mercenary-reports")
    public ResponseEntity<PageResponse<MercenaryReportResponse>> listMyReports(
            @CurrentUser UUID currentUserId,
            @RequestParam(defaultValue = "RECEIVED") MercenaryReportDirection direction,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(reportService.listMyReports(currentUserId, direction, page, pageSize));
    }
}
