package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.OneOf;
import com.phananh.arenaflow.application.validation.ValidCostAgreement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Thay toàn bộ thoả thuận chi phí của kèo luồng A; {@code customItems} vắng = rỗng (giữ thứ tự gửi). */
@ValidCostAgreement
public record UpdateCostAgreementRequest(
        @OneOf({"VENUE_ONLY", "DRINKS_ONLY", "BOTH"}) String costType,
        @OneOf({"FIFTY_FIFTY", "SEVENTY_THIRTY_HOME", "SEVENTY_THIRTY_LOSER", "LOSER_PAYS_DRINKS"}) String costSplit,
        @Min(1) @Max(1_000_000_000L) Long venueAmount,
        @Min(1) @Max(1_000_000_000L) Long drinksAmount,
        @Size(max = 10) List<@Valid CostCustomItemRequest> customItems) {

    public UpdateCostAgreementRequest {
        customItems = customItems == null ? List.of() : customItems;
    }
}
