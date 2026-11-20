package com.phananh.arenaflow.application.service;

/** Bộ lọc trạng thái danh sách report của Admin; ALL = không lọc. */
public enum VenueReportStatusFilter {
    PENDING, VERIFIED, REJECTED, ALL;

    /** Giá trị truyền xuống native query: null = mọi trạng thái. */
    public String toQueryValue() {
        return this == ALL ? null : name();
    }
}
