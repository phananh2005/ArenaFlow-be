package com.phananh.arenaflow.api.auth;

import com.phananh.arenaflow.application.dto.request.OAuthLoginRequest;
import com.phananh.arenaflow.application.dto.request.RefreshTokenRequest;
import com.phananh.arenaflow.application.dto.request.SendOtpRequest;
import com.phananh.arenaflow.application.dto.request.VerifyOtpRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.AuthResponse;
import com.phananh.arenaflow.application.dto.response.OtpSentResponse;
import com.phananh.arenaflow.application.service.AuthResult;
import com.phananh.arenaflow.application.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public (SecurityConfig permitAll `/api/v1/auth/**`). */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;

    @PostMapping("/oauth")
    public ResponseEntity<ApiResponse<AuthResponse>> oauth(@Valid @RequestBody OAuthLoginRequest request) {
        return toResponse(authService.loginWithOAuth(request));
    }

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<OtpSentResponse>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.of(authService.sendOtp(request)));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return toResponse(authService.verifyOtp(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.of(authService.refresh(request)));
    }

    /** Tạo mới → 201 (không header {@code Location}); đã có → 200. */
    private static ResponseEntity<ApiResponse<AuthResponse>> toResponse(AuthResult result) {
        ApiResponse<AuthResponse> body = ApiResponse.of(result.body());
        return result.isNewUser() ? ResponseEntity.status(HttpStatus.CREATED).body(body) : ResponseEntity.ok(body);
    }
}
