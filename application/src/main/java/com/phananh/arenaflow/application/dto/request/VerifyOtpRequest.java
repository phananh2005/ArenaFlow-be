package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotNull @Pattern(regexp = "^0\\d{9}$") String phone,
        @NotNull @Pattern(regexp = "^\\d{6}$") String otp) {
}
