package com.phananh.arenaflow.application.service;

/** Lọc kèo theo trạng thái hiển thị; ALL = không lọc. */
public enum MatchStatusFilter {
    OPEN, CONFIRMED, ARCHIVED, COMPLETED, EXPIRED, CANCELLED, ALL;

    /** Giá trị truyền xuống native query: null = tất cả. */
    public String toQueryValue() {
        return this == ALL ? null : name();
    }
}
