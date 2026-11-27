package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

/** Một phường/xã trong danh sách của một tỉnh/thành (khác {@link WardResponse} của hồ sơ, không kèm tỉnh). */
public record WardSummaryResponse(UUID wardId, String wardName) {
}
