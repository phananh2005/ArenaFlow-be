package com.phananh.ArenaFlow.dto.response;

/** §2.2 — OTP mock, expiresIn = 300 giây. */
public record OtpSentResponse(String phone, int expiresIn) {
}
