package com.phananh.arenaflow.application.service;

import java.util.UUID;

/** Một lần cộng/trừ điểm uy tín; {@code breaksCleanStreak} đưa chuỗi trận sạch về 0 trong cùng câu UPDATE. */
public record ReliabilityChange(int delta, ReliabilityReason reason, UUID referenceId, boolean breaksCleanStreak) {
}
