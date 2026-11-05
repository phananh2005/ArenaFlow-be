package com.phananh.arenaflow.application.service;

import java.util.List;

/** Bộ lọc lịch sử thành viên (Admin); ALL = ACTIVE + LEFT, không gồm lời mời PENDING/REJECTED. */
public enum MemberHistoryStatusFilter {
    ACTIVE, LEFT, ALL;

    /** Giá trị truyền xuống native query {@code status IN (:statuses)}. */
    public List<String> toQueryValues() {
        return this == ALL ? List.of(ACTIVE.name(), LEFT.name()) : List.of(name());
    }
}
