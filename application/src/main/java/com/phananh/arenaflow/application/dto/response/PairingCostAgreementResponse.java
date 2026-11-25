package com.phananh.arenaflow.application.dto.response;

import java.util.List;

/** Thoả thuận chi phí + tiền cọc của thương lượng; chưa nhập → mọi field null, {@code customItems} rỗng. */
public record PairingCostAgreementResponse(String costType, String costSplit, Long venueAmount, Long drinksAmount,
                                           Long depositAmount, String depositHolder,
                                           List<CostCustomItemResponse> customItems) {
}
