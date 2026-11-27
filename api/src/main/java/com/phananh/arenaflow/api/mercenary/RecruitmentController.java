package com.phananh.arenaflow.api.mercenary;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.SearchRecruitmentsRequest;
import com.phananh.arenaflow.application.dto.request.UpdateRecruitmentRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.ApplicantResponse;
import com.phananh.arenaflow.application.dto.response.ApplicationResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.RecruitmentResponse;
import com.phananh.arenaflow.application.service.ApplicationStatusFilter;
import com.phananh.arenaflow.application.service.MercenaryApplicationService;
import com.phananh.arenaflow.application.service.MercenaryRecruitmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mercenary-recruitments")
@RequiredArgsConstructor
@Validated
public class RecruitmentController {

    private final MercenaryRecruitmentService recruitmentService;
    private final MercenaryApplicationService applicationService;

    @GetMapping
    public ResponseEntity<PageResponse<RecruitmentResponse>> search(@CurrentUser UUID currentUserId,
                                                                    @Valid @ModelAttribute SearchRecruitmentsRequest request) {
        return ResponseEntity.ok(recruitmentService.search(currentUserId, request));
    }

    @GetMapping("/{recruitmentId}")
    public ResponseEntity<ApiResponse<RecruitmentResponse>> getRecruitment(
            @PathVariable UUID recruitmentId, @CurrentUser UUID currentUserId,
            @RequestParam(required = false) @DecimalMin("-90") @DecimalMax("90") Double lat,
            @RequestParam(required = false) @DecimalMin("-180") @DecimalMax("180") Double lng)
            throws MissingServletRequestParameterException {
        // Toạ độ là một cặp: thiếu một nửa → 400 VALIDATION_ERROR gắn tham số còn thiếu.
        if ((lat == null) != (lng == null)) {
            throw new MissingServletRequestParameterException(lat == null ? "lat" : "lng", "Double");
        }
        return ResponseEntity.ok(ApiResponse.of(recruitmentService.getRecruitment(recruitmentId, currentUserId, lat,
                lng)));
    }

    @PatchMapping("/{recruitmentId}")
    public ResponseEntity<ApiResponse<RecruitmentResponse>> updateQuantity(
            @PathVariable UUID recruitmentId, @CurrentUser UUID currentUserId,
            @Valid @RequestBody UpdateRecruitmentRequest request) {
        return ResponseEntity.ok(ApiResponse.of(recruitmentService.updateQuantity(recruitmentId, currentUserId, request)));
    }

    @PostMapping("/{recruitmentId}/close")
    public ResponseEntity<ApiResponse<RecruitmentResponse>> close(@PathVariable UUID recruitmentId,
                                                                  @CurrentUser UUID currentUserId) {
        return ResponseEntity.ok(ApiResponse.of(recruitmentService.close(recruitmentId, currentUserId)));
    }

    @PostMapping("/{recruitmentId}/applications")
    public ResponseEntity<ApiResponse<ApplicationResponse>> apply(@PathVariable UUID recruitmentId,
                                                                  @CurrentUser UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(applicationService.apply(recruitmentId, currentUserId)));
    }

    @GetMapping("/{recruitmentId}/applications")
    public ResponseEntity<PageResponse<ApplicantResponse>> listApplicants(
            @PathVariable UUID recruitmentId, @CurrentUser UUID currentUserId,
            @RequestParam(defaultValue = "PENDING") ApplicationStatusFilter status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(applicationService.listApplicants(recruitmentId, currentUserId, status, page,
                pageSize));
    }
}
