package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.support.mercenary.MercenaryRules;
import com.phananh.arenaflow.application.validation.OneOf;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RecruitmentPositionRequest(
        @NotNull @OneOf({"Thủ môn", "Hậu vệ", "Tiền vệ", "Tiền đạo"}) String position,
        @NotNull @Min(1) @Max(MercenaryRules.MAX_QUANTITY) Integer quantity) {

    public RecruitmentPositionRequest {
        position = position == null ? null : position.trim();
    }
}
