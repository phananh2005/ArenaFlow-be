package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SendOtpRequest(@NotNull @Pattern(regexp = "^0\\d{9}$") String phone) {
}
