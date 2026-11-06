package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.OAuthLoginRequest;
import com.phananh.arenaflow.application.dto.request.RefreshTokenRequest;
import com.phananh.arenaflow.application.dto.request.SendOtpRequest;
import com.phananh.arenaflow.application.dto.request.VerifyOtpRequest;
import com.phananh.arenaflow.application.dto.response.AuthResponse;
import com.phananh.arenaflow.application.dto.response.OtpSentResponse;

public interface AuthService {

    /**
     * Mock: không xác minh {@code oauthToken}. Email (trim + lowercase) cùng provider là khoá nhận diện: chưa có → đăng
     * ký ({@code isNewUser} = true), đã có → đăng nhập; user đã xoá → AUTH_003.
     */
    AuthResult loginWithOAuth(OAuthLoginRequest request);

    /** Mock: không gửi OTP thật, chỉ trả số điện thoại và thời hạn 300 giây. */
    OtpSentResponse sendOtp(SendOtpRequest request);

    /**
     * Mock: KHÔNG kiểm {@code otp} — mọi mã 6 chữ số đều qua. Số điện thoại là khoá nhận diện; đăng ký-hoặc-đăng nhập
     * như {@link #loginWithOAuth}.
     */
    AuthResult verifyOtp(VerifyOtpRequest request);

    /**
     * Cấp cặp token mới, role đọc lại từ DB. Refresh token sai/hết hạn → AUTH_002; user không tồn tại hoặc đã xoá
     * → AUTH_003.
     */
    AuthResponse refresh(RefreshTokenRequest request);
}
