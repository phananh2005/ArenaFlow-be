package com.phananh.arenaflow.domain.entity;

import jakarta.persistence.Column;

import java.lang.reflect.Field;
import java.util.Arrays;

/** Đọc {@link Column} của một field entity để kiểm cờ insertable / updatable bằng reflection. */
public final class ColumnMapping {

    private ColumnMapping() {
    }

    /** Trả annotation {@link Column} của field; fail test nếu thiếu field hoặc thiếu {@code @Column}. */
    public static Column column(Class<?> entity, String field) {
        try {
            Column column = entity.getDeclaredField(field).getAnnotation(Column.class);
            if (column == null) {
                throw new AssertionError(entity.getSimpleName() + "." + field + " thiếu @Column");
            }
            return column;
        } catch (NoSuchFieldException exception) {
            throw new AssertionError(entity.getSimpleName() + " không có field " + field, exception);
        }
    }

    /** Kiểm tra entity có khai báo field với tên cho trước hay không. */
    public static boolean hasField(Class<?> entity, String field) {
        return Arrays.stream(entity.getDeclaredFields()).map(Field::getName).anyMatch(field::equals);
    }
}
