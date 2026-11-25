package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.support.match.MatchRules;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Một khoản phí tự nhập của thoả thuận chi phí. */
public record CostCustomItemRequest(
        @NotBlank @Size(max = 100) String itemName,
        @NotNull @Min(1) @Max(MatchRules.MAX_AMOUNT) Long amount) {
}
