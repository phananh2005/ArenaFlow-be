package com.phananh.arenaflow.application.service;

/** Bộ lọc trạng thái danh sách địa điểm; ALL = không lọc. */
public enum VenueStatusFilter {
    ACTIVE, INACTIVE, ALL;

    /** Giá trị truyền xuống native query: null = mọi trạng thái. */
    public String toQueryValue() {
        return this == ALL ? null : name();
    }
}
