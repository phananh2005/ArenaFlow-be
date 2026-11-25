package com.phananh.arenaflow.api.sport;

import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.SportResponse;
import com.phananh.arenaflow.application.service.SportService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sports")
@RequiredArgsConstructor
@Validated
public class SportController {

    private final SportService sportService;

    @GetMapping
    public ResponseEntity<PageResponse<SportResponse>> listActiveSports(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(sportService.listActiveSports(page, pageSize));
    }
}
