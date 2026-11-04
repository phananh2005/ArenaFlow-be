package com.phananh.ArenaFlow.service;

/** Bộ lọc trạng thái danh sách đội; ALL = không lọc. */
public enum TeamStatusFilter {
    ACTIVE, INACTIVE, ALL;

    /** Giá trị truyền xuống native query: null = tất cả. */
    public String toQueryValue() {
        return this == ALL ? null : name();
    }
}
