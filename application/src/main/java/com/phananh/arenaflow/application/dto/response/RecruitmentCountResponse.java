package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

/** Trạng thái và số lượng của tin sau khi duyệt / rút. */
public record RecruitmentCountResponse(UUID recruitmentId, String status, int quantityNeeded, int quantityApproved) {
}
