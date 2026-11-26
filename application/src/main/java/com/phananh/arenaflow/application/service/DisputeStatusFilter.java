package com.phananh.arenaflow.application.service;

/** Bộ lọc trạng thái danh sách tranh chấp của Admin; ALL = không lọc. */
public enum DisputeStatusFilter {
    PENDING, RESOLVED, ALL;

    /** Giá trị truyền xuống native query: null = mọi trạng thái. */
    public String toQueryValue() {
        return this == ALL ? null : name();
    }

    /** Hàng chờ PENDING xử lý theo thứ tự đến (cũ nhất trước); còn lại mới nhất trước. */
    public boolean oldestFirst() {
        return this == PENDING;
    }
}
