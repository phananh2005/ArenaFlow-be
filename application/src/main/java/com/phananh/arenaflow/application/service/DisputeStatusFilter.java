package com.phananh.arenaflow.application.service;

/** Bộ lọc hàng chờ tranh chấp; ALL = không lọc. */
public enum DisputeStatusFilter {
    PENDING, RESOLVED, ALL;

    /** Giá trị truyền xuống native query: null = mọi trạng thái. */
    public String toQueryValue() {
        return this == ALL ? null : name();
    }
}
