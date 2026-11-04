package com.phananh.ArenaFlow.dto.response;

/** OTP mock, expiresIn = 300 giây. */
public record OtpSentResponse(String phone, int expiresIn) {
}
