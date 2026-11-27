package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.support.mercenary.MercenaryRules;
import com.phananh.arenaflow.application.validation.ExternalMatchScheduleInput;
import com.phananh.arenaflow.application.validation.UniquePositions;
import com.phananh.arenaflow.application.validation.ValidExternalMatchSchedule;
import com.phananh.arenaflow.application.validation.ValidSkillLevels;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@ValidExternalMatchSchedule
public record CreateExternalMatchRequest(
        @NotNull UUID venueId,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt,
        @ValidSkillLevels List<String> skillLevels,
        @Size(max = MercenaryRules.MAX_OPPONENT_NAME) String opponentName,
        @NotEmpty @Size(max = MercenaryRules.MAX_POSITIONS) @UniquePositions
        List<@Valid @NotNull RecruitmentPositionRequest> positions) implements ExternalMatchScheduleInput {

    public CreateExternalMatchRequest {
        opponentName = opponentName == null || opponentName.isBlank() ? null : opponentName.trim();
        skillLevels = skillLevels == null ? List.of() : skillLevels;
    }
}
