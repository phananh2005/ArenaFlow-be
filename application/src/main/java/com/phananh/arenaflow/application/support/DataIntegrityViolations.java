package com.phananh.arenaflow.application.support;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/** Xác định constraint database mà thao tác ghi vi phạm, không đọc message SQL (có thể chứa giá trị dòng). */
public final class DataIntegrityViolations {

    private DataIntegrityViolations() {
    }

    /** Tên constraint bị vi phạm, hoặc {@code null} khi nguyên nhân không mang tên constraint. */
    public static String constraintName(DataIntegrityViolationException exception) {
        return exception.getCause() instanceof ConstraintViolationException violation
                ? violation.getConstraintName()
                : null;
    }

    public static boolean violates(DataIntegrityViolationException exception, String constraint) {
        return constraint.equals(constraintName(exception));
    }
}
