package com.phananh.arenaflow.application.event.reputation;

import com.phananh.arenaflow.application.service.ReliabilityReason;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Điểm uy tín vừa được cộng/trừ (chỉ mang {@code delta}, không đọc lại điểm mới). Chỉ dùng cho thông báo / dữ liệu dẫn
 * xuất tính lại được, không dùng để ghi điểm.
 */
public record ReliabilityScoreChangedEvent(ReputationSubjectType subjectType, UUID subjectId, int delta,
                                           ReliabilityReason reason, UUID referenceId, LocalDateTime occurredAt) {
}
