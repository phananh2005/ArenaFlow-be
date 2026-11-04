package com.phananh.ArenaFlow.service;

import com.phananh.ArenaFlow.dto.request.OAuthLoginRequest;
import com.phananh.ArenaFlow.dto.request.RefreshTokenRequest;
import com.phananh.ArenaFlow.dto.request.SendOtpRequest;
import com.phananh.ArenaFlow.dto.request.VerifyOtpRequest;
import com.phananh.ArenaFlow.dto.response.AuthResponse;
import com.phananh.ArenaFlow.dto.response.OtpSentResponse;

public interface AuthService {

    AuthResult loginWithOAuth(OAuthLoginRequest request);

    OtpSentResponse sendOtp(SendOtpRequest request);

    AuthResult verifyOtp(VerifyOtpRequest request);

    AuthResponse refresh(RefreshTokenRequest request);
}
