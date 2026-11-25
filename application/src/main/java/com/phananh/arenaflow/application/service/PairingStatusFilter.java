package com.phananh.arenaflow.application.service;

/** Bộ lọc trạng thái hiển thị của danh sách thương lượng; ALL = không lọc. */
public enum PairingStatusFilter {
    NEGOTIATING, CONFIRMED, SUPERSEDED, EXPIRED, ALL;

    /** Giá trị truyền xuống native query: null = mọi trạng thái. */
    public String toQueryValue() {
        return this == ALL ? null : name();
    }
}
