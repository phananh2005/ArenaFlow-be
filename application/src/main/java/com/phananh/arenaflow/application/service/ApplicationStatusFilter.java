package com.phananh.arenaflow.application.service;

/** Lọc đơn ứng tuyển theo trạng thái hiệu lực; ACTIVE = PENDING + APPROVED; ALL = không lọc. */
public enum ApplicationStatusFilter {
    ACTIVE, PENDING, APPROVED, REJECTED, WITHDRAWN, ALL;

    /** Giá trị truyền xuống native query: null = mọi trạng thái. */
    public String toQueryValue() {
        return this == ALL ? null : name();
    }
}
