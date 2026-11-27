package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.support.mercenary.MercenaryRules;
import com.phananh.arenaflow.application.validation.UniquePositions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateRecruitmentsRequest(
        @NotNull UUID teamId,
        @NotEmpty @Size(max = MercenaryRules.MAX_POSITIONS) @UniquePositions
        List<@Valid @NotNull RecruitmentPositionRequest> positions) {
}
