package com.phananh.arenaflow.application.event.reputation;

import com.phananh.arenaflow.application.service.ReliabilityReason;

import java.time.LocalDateTime;
import java.util.UUID;

/** Điểm uy tín của đội / cá nhân vừa đổi; chỉ dùng cho thông báo và dữ liệu dẫn xuất. */
public record ReliabilityScoreChangedEvent(ReputationSubjectType subjectType, UUID subjectId, int delta,
                                           ReliabilityReason reason, UUID referenceId, LocalDateTime occurredAt) {
}
