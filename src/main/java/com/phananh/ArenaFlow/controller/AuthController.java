package com.phananh.ArenaFlow.controller;

import com.phananh.ArenaFlow.dto.request.OAuthLoginRequest;
import com.phananh.ArenaFlow.dto.request.RefreshTokenRequest;
import com.phananh.ArenaFlow.dto.request.SendOtpRequest;
import com.phananh.ArenaFlow.dto.request.VerifyOtpRequest;
import com.phananh.ArenaFlow.dto.response.ApiResponse;
import com.phananh.ArenaFlow.dto.response.AuthResponse;
import com.phananh.ArenaFlow.dto.response.OtpSentResponse;
import com.phananh.ArenaFlow.service.AuthResult;
import com.phananh.ArenaFlow.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Public (SecurityConfig permitAll `/api/v1/auth/**`). */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private static final URI PROFILE_LOCATION = URI.create("/api/v1/profile");

    private final AuthService authService;

    @PostMapping("/oauth")
    public ResponseEntity<ApiResponse<AuthResponse>> oauth(@Valid @RequestBody OAuthLoginRequest request) {
        return toResponse(authService.loginWithOAuth(request));
    }

    @PostMapping("/send-otp")
    public ApiResponse<OtpSentResponse> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        return ApiResponse.of(authService.sendOtp(request));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return toResponse(authService.verifyOtp(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.of(authService.refresh(request));
    }

    /** Tạo mới → 201 + Location hồ sơ; đã có → 200. */
    private static ResponseEntity<ApiResponse<AuthResponse>> toResponse(AuthResult result) {
        ApiResponse<AuthResponse> body = ApiResponse.of(result.body());
        return result.isNewUser() ? ResponseEntity.created(PROFILE_LOCATION).body(body) : ResponseEntity.ok(body);
    }
}
