package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.support.match.MatchRules;
import com.phananh.arenaflow.application.support.match.PairingRules;
import com.phananh.arenaflow.application.validation.CostAgreementFields;
import com.phananh.arenaflow.application.validation.OneOf;
import com.phananh.arenaflow.application.validation.ValidCostAgreement;
import com.phananh.arenaflow.application.validation.ValidDeposit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Thay toàn bộ thoả thuận chi phí + tiền cọc của thương lượng ở {@code revision} FE đang hiển thị. Vắng
 * {@code customItems} = danh sách rỗng.
 */
@ValidCostAgreement
@ValidDeposit
public record UpdatePairingCostAgreementRequest(
        @NotNull UUID teamId,
        @NotNull @PositiveOrZero Integer revision,
        @OneOf({"VENUE_ONLY", "DRINKS_ONLY", "BOTH"}) String costType,
        @OneOf({"FIFTY_FIFTY", "SEVENTY_THIRTY_HOME", "SEVENTY_THIRTY_LOSER", "LOSER_PAYS_DRINKS"}) String costSplit,
        @Min(1) @Max(MatchRules.MAX_AMOUNT) Long venueAmount,
        @Min(1) @Max(MatchRules.MAX_AMOUNT) Long drinksAmount,
        @Min(1) @Max(MatchRules.MAX_AMOUNT) Long depositAmount,
        @Size(max = PairingRules.MAX_DEPOSIT_HOLDER) String depositHolder,
        @Size(max = MatchRules.MAX_CUSTOM_ITEMS) List<@Valid CostCustomItemRequest> customItems)
        implements CostAgreementFields {

    public UpdatePairingCostAgreementRequest {
        customItems = customItems == null ? List.of() : customItems;
    }
}
