package com.phananh.arenaflow.application.dto.response;

import java.util.List;

/** Thoả thuận chi phí lồng trong kèo chi tiết (7 field — khác response lưu thoả thuận). */
public record MatchCostAgreementResponse(String costType, String costSplit, Long venueAmount, Long drinksAmount,
                                         Long depositAmount, String depositHolder,
                                         List<CostCustomItemResponse> customItems) {
}
