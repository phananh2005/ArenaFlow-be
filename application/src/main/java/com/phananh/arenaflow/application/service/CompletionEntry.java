package com.phananh.arenaflow.application.service;

import java.util.UUID;

/**
 * Một đội trong lần trận chuyển COMPLETED: {@code completes} = cộng điểm hoàn thành và xét chuỗi trận sạch;
 * {@code ratingBonus} = điểm thưởng đánh giá 5 sao cộng cùng câu.
 */
public record CompletionEntry(UUID teamId, boolean completes, int ratingBonus) {
}
