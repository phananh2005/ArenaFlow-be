package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.service.VenueStatusFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiểm giá trị mặc định và ràng buộc của bộ lọc danh sách địa điểm dành cho Admin. */
class AdminSearchVenueRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void defaultsToEveryStatusOnFirstPageAndTrimsKeyword() {
        AdminSearchVenueRequest defaults = new AdminSearchVenueRequest(null, null, null, null);
        AdminSearchVenueRequest keyword = new AdminSearchVenueRequest("  Hoa Lư  ", VenueStatusFilter.INACTIVE, 3, 50);

        assertThat(validator.validate(defaults)).isEmpty();
        assertThat(defaults.status()).isEqualTo(VenueStatusFilter.ALL);
        assertThat(defaults.page()).isEqualTo(1);
        assertThat(defaults.pageSize()).isEqualTo(20);
        assertThat(validator.validate(keyword)).isEmpty();
        assertThat(keyword.q()).isEqualTo("Hoa Lư");
        assertThat(keyword.status()).isEqualTo(VenueStatusFilter.INACTIVE);
    }

    @Test
    void rejectsKeywordAndPagingOutOfRange() {
        AdminSearchVenueRequest invalid = new AdminSearchVenueRequest("x".repeat(101), null, 0, 101);
        AdminSearchVenueRequest zeroPageSize = new AdminSearchVenueRequest(null, null, null, 0);

        assertThat(validator.validate(invalid)).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("q", "page", "pageSize");
        assertThat(validator.validate(zeroPageSize).stream().map(ConstraintViolation::getPropertyPath)
                .map(Object::toString).collect(Collectors.toSet())).containsExactly("pageSize");
    }
}
