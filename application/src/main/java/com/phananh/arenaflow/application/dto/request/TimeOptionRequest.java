package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Khung giờ đề xuất của kèo luồng B; quy tắc giờ kiểm ở {@code @ValidMatchSchedule} của request tạo kèo. */
public record TimeOptionRequest(@NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt) {
}
