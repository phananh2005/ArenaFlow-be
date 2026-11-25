package com.phananh.arenaflow.application.service;

/** Vai trò của đội trong kèo: đội tạo kèo / đội đã ghép; ALL = cả hai. */
public enum MatchRoleFilter {
    AUTHOR, OPPONENT, ALL;

    /** Giá trị truyền xuống native query: null = cả hai. */
    public String toQueryValue() {
        return this == ALL ? null : name();
    }
}
