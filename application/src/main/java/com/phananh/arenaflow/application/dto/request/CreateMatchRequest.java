package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.OneOf;
import com.phananh.arenaflow.application.validation.ValidMatchSchedule;
import com.phananh.arenaflow.application.validation.ValidSkillLevels;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tạo kèo. Field bắt buộc theo luồng (A: {@code venueId}, {@code startsAt}, {@code endsAt}; B: {@code venueOptionIds},
 * {@code timeOptions}) kiểm ở {@link ValidMatchSchedule}. {@code type} vắng = HAS_VENUE.
 */
@ValidMatchSchedule
public record CreateMatchRequest(
        @OneOf({"HAS_VENUE", "NEED_VENUE"}) String type,
        @NotNull UUID teamId,
        @NotNull UUID sportId,
        UUID venueId,
        List<UUID> venueOptionIds,
        @NotNull @OneOf({"FIVE_A_SIDE", "SEVEN_A_SIDE", "ELEVEN_A_SIDE"}) String format,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        List<@Valid TimeOptionRequest> timeOptions,
        @NotNull LocalDateTime expiresAt,
        @ValidSkillLevels List<String> skillLevels,
        @Size(max = 100) String ageNote,
        @Size(max = 200) String matchNature) {

    public static final String HAS_VENUE = "HAS_VENUE";
    public static final String NEED_VENUE = "NEED_VENUE";

    public CreateMatchRequest {
        type = type == null ? HAS_VENUE : type;
    }
}
