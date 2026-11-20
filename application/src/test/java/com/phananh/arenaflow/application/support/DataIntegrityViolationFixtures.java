package com.phananh.arenaflow.application.support;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

/** Fixture test cho vi phạm constraint database đúng dạng {@code saveAndFlush} ném ra. */
public final class DataIntegrityViolationFixtures {

    private DataIntegrityViolationFixtures() {
    }

    /** Vi phạm Hibernate được Spring bọc, mang tên {@code constraint}. */
    public static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", new SQLException("violation"), constraint));
    }
}
