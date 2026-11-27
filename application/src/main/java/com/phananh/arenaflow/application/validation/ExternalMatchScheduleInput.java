package com.phananh.arenaflow.application.validation;

import java.time.LocalDateTime;

/** Cặp giờ của trận ngoài hệ thống — đích của {@link ValidExternalMatchSchedule}. */
public interface ExternalMatchScheduleInput {

    LocalDateTime startsAt();

    LocalDateTime endsAt();
}
