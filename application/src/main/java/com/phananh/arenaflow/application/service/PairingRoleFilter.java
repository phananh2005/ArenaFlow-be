package com.phananh.arenaflow.application.service;

/** Vai trò của đội trong thương lượng: AUTHOR = kèo của đội, INTERESTED = đội nhận kèo, ALL = cả hai. */
public enum PairingRoleFilter {
    AUTHOR, INTERESTED, ALL;

    /** Giá trị truyền xuống native query: null = cả hai vai trò. */
    public String toQueryValue() {
        return this == ALL ? null : name();
    }
}
