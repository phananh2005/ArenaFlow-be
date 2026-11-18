package com.phananh.arenaflow.api.venue;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.CreateVenueRequest;
import com.phananh.arenaflow.application.dto.request.SearchVenueRequest;
import com.phananh.arenaflow.application.dto.request.UpdateVenueRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.VenueResponse;
import com.phananh.arenaflow.application.dto.response.VenueSummaryResponse;
import com.phananh.arenaflow.application.service.VenueService;
import com.phananh.arenaflow.application.support.security.CurrentActor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
@Validated
public class VenueController {

    private final VenueService venueService;

    @PostMapping
    public ResponseEntity<ApiResponse<VenueResponse>> createVenue(@CurrentUser UUID currentUserId,
                                                                    @Valid @RequestBody CreateVenueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(venueService.createVenue(currentUserId, request)));
    }

    @GetMapping
    public ResponseEntity<PageResponse<VenueSummaryResponse>> listVenues(
            @Valid @ModelAttribute SearchVenueRequest request) {
        return ResponseEntity.ok(venueService.listVenues(request));
    }

    @PatchMapping("/{venueId}")
    public ResponseEntity<ApiResponse<VenueResponse>> updateVenue(@PathVariable UUID venueId,
            @CurrentUser CurrentActor actor, @Valid @RequestBody UpdateVenueRequest request) {
        return ResponseEntity.ok(ApiResponse.of(venueService.updateVenue(venueId, actor, request)));
    }

    @GetMapping("/{venueId}")
    public ResponseEntity<ApiResponse<VenueResponse>> getVenue(@PathVariable UUID venueId) {
        return ResponseEntity.ok(ApiResponse.of(venueService.getVenue(venueId)));
    }
}
