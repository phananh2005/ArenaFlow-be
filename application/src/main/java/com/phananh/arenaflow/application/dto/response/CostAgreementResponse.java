package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Response lưu thoả thuận chi phí: {@code updatedAt} = thời điểm cập nhật kèo. */
public record CostAgreementResponse(UUID matchId, String costType, String costSplit, Long venueAmount,
                                    Long drinksAmount, List<CostCustomItemResponse> customItems,
                                    LocalDateTime updatedAt) {
}
