package com.phananh.arenaflow.application.service;

/** Lọc tin của đội theo trạng thái hiệu lực; ACTIVE = OPEN + FILLED; ALL = không lọc. */
public enum RecruitmentStatusFilter {
    ACTIVE, OPEN, FILLED, CLOSED, EXPIRED, ALL;

    /** Giá trị truyền xuống native query: null = mọi trạng thái. */
    public String toQueryValue() {
        return this == ALL ? null : name();
    }
}
