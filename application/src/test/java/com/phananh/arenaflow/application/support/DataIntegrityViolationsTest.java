package com.phananh.arenaflow.application.support;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class DataIntegrityViolationsTest {

    @Test
    void readsConstraintNameFromHibernateCause() {
        DataIntegrityViolationException exception = DataIntegrityViolationFixtures.violation("uq_venue_report_pending");

        assertThat(DataIntegrityViolations.constraintName(exception)).isEqualTo("uq_venue_report_pending");
        assertThat(DataIntegrityViolations.violates(exception, "uq_venue_report_pending")).isTrue();
        assertThat(DataIntegrityViolations.violates(exception, "uq_venue_image_public_id")).isFalse();
    }

    @Test
    void unknownConstraintWhenCauseIsNotHibernateViolation() {
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("uq_venue_report_pending", new SQLException("boom"));

        assertThat(DataIntegrityViolations.constraintName(exception)).isNull();
        assertThat(DataIntegrityViolations.violates(exception, "uq_venue_report_pending")).isFalse();
    }
}
