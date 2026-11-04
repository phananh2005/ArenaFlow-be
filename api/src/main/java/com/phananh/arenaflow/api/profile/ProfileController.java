package com.phananh.arenaflow.api.profile;

import com.phananh.arenaflow.application.dto.request.ProfileUpdateRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.ProfileResponse;
import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Hồ sơ của user hiện tại. */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Validated
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(@CurrentUser UUID currentUserId) {
        return ResponseEntity.ok(ApiResponse.of(profileService.getProfile(currentUserId)));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(@CurrentUser UUID currentUserId,
                                                      @Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(profileService.updateProfile(currentUserId, request)));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@CurrentUser UUID currentUserId) {
        profileService.deleteAccount(currentUserId);
        return ResponseEntity.noContent().build();
    }
}
