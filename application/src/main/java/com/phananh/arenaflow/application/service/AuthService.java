package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.OAuthLoginRequest;
import com.phananh.arenaflow.application.dto.request.RefreshTokenRequest;
import com.phananh.arenaflow.application.dto.request.SendOtpRequest;
import com.phananh.arenaflow.application.dto.request.VerifyOtpRequest;
import com.phananh.arenaflow.application.dto.response.AuthResponse;
import com.phananh.arenaflow.application.dto.response.OtpSentResponse;

public interface AuthService {

    AuthResult loginWithOAuth(OAuthLoginRequest request);

    OtpSentResponse sendOtp(SendOtpRequest request);

    AuthResult verifyOtp(VerifyOtpRequest request);

    AuthResponse refresh(RefreshTokenRequest request);
}
