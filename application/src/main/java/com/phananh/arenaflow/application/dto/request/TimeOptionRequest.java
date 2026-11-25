package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Khung giờ đề xuất của kèo luồng B (chỉ kiểm khung ở đợt luồng B). */
public record TimeOptionRequest(@NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt) {
}
