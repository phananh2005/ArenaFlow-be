package com.phananh.arenaflow.application.service;

/** Lọc trận ngoài của đội: UPCOMING (chưa tới giờ kết thúc), ENDED, CANCELLED, ALL = không lọc. */
public enum ExternalMatchStatusFilter {
    UPCOMING, ENDED, CANCELLED, ALL;

    /** Giá trị truyền xuống native query: null = mọi trạng thái. */
    public String toQueryValue() {
        return this == ALL ? null : name();
    }
}
