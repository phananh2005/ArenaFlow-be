package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.support.mercenary.MercenaryRules;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateRecruitmentRequest(@NotNull @Min(1) @Max(MercenaryRules.MAX_QUANTITY) Integer quantityNeeded) {
}
