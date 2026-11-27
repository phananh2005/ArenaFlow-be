package com.phananh.arenaflow.domain.repository;

/**
 * Đoạn SQL dùng chung về trận của tin tuyển (alias tin {@code r}, kèo {@code m}, trận ngoài {@code x}).
 * Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. Chỉ gồm các hằng NOTIFICATION-001 cần.
 */
public final class MercenarySql {

    public static final String GAME_JOIN =
            " LEFT JOIN match m ON m.id = r.match_id LEFT JOIN external_match x ON x.id = r.external_match_id ";
    public static final String GAME_START = "COALESCE(m.match_start_time, x.match_start_time)";
    public static final String GAME_VENUE = "COALESCE(m.venue_id, x.venue_id)";

    private MercenarySql() {
    }
}
