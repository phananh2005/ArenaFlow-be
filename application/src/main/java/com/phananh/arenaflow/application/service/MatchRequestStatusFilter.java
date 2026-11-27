package com.phananh.arenaflow.application.service;

/** Lọc yêu cầu ghép kèo theo trạng thái hiển thị; ALL = không lọc. */
public enum MatchRequestStatusFilter {
    PENDING, ACCEPTED, REJECTED, WITHDRAWN, SUPERSEDED, EXPIRED, ALL;

    /** Giá trị truyền xuống native query: null = tất cả. */
    public String toQueryValue() {
        return this == ALL ? null : name();
    }
}
