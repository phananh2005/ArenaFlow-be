package com.phananh.arenaflow.application.dto.response;

/** OTP mock, expiresIn = 300 giây. */
public record OtpSentResponse(String phone, int expiresIn) {
}
