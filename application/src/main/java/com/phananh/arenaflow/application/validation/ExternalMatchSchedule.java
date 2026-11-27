package com.phananh.arenaflow.application.validation;

import java.time.LocalDateTime;

/** Cặp giờ trận ngoài sau khi áp thay đổi — service kiểm bằng Bean Validation trước khi ghi. */
@ValidExternalMatchSchedule
public record ExternalMatchSchedule(LocalDateTime startsAt, LocalDateTime endsAt) implements ExternalMatchScheduleInput {
}
