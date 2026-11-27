package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Kết quả rút đơn; {@code reliabilityPenalty} là số điểm bị trừ (≥ 0). */
public record WithdrawalResponse(UUID applicationId, String status, String previousStatus, LocalDateTime withdrawnAt,
                                 int reliabilityPenalty, RecruitmentCountResponse recruitment) {
}
