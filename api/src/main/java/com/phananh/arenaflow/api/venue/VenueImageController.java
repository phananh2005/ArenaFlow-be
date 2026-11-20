package com.phananh.arenaflow.api.venue;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.AddVenueImageRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.VenueImageResponse;
import com.phananh.arenaflow.application.dto.response.VenueImageUploadSignatureResponse;
import com.phananh.arenaflow.application.service.VenueImageService;
import com.phananh.arenaflow.application.support.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/venues/{venueId}")
@RequiredArgsConstructor
@Validated
public class VenueImageController {

    private final VenueImageService venueImageService;

    @PostMapping("/images")
    public ResponseEntity<ApiResponse<VenueImageResponse>> addImage(@PathVariable UUID venueId,
            @CurrentUser CurrentActor actor, @Valid @RequestBody AddVenueImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(venueImageService.addImage(venueId, actor, request)));
    }

    @PostMapping("/image-upload-signatures")
    public ResponseEntity<ApiResponse<VenueImageUploadSignatureResponse>> issueUploadSignature(
            @PathVariable UUID venueId, @CurrentUser CurrentActor actor) {
        return ResponseEntity.ok(ApiResponse.of(venueImageService.issueUploadSignature(venueId, actor)));
    }

    @PatchMapping("/images/{imageId}/set-primary")
    public ResponseEntity<ApiResponse<List<VenueImageResponse>>> setPrimaryImage(@PathVariable UUID venueId,
            @PathVariable UUID imageId, @CurrentUser CurrentActor actor) {
        return ResponseEntity.ok(ApiResponse.of(venueImageService.setPrimaryImage(venueId, imageId, actor)));
    }

    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<ApiResponse<List<VenueImageResponse>>> deleteImage(@PathVariable UUID venueId,
            @PathVariable UUID imageId, @CurrentUser CurrentActor actor) {
        return ResponseEntity.ok(ApiResponse.of(venueImageService.deleteImage(venueId, imageId, actor)));
    }
}
