package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.service.VenueStatusFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SearchVenueRequestValidationTest {

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
    void defaultsSearchToFirstActivePageAndNearbyRadiusWhenCoordinatesArePresent() {
        SearchVenueRequest defaults = new SearchVenueRequest(null, null, null, null, null, null, null);
        SearchVenueRequest nearby = new SearchVenueRequest("  Hoa Lư  ", null,
                new BigDecimal("10.78"), new BigDecimal("106.70"), null, null, null);

        assertThat(validator.validate(defaults)).isEmpty();
        assertThat(defaults.status()).isEqualTo(VenueStatusFilter.ACTIVE);
        assertThat(defaults.page()).isEqualTo(1);
        assertThat(defaults.pageSize()).isEqualTo(20);
        assertThat(defaults.radiusKm()).isNull();

        assertThat(validator.validate(nearby)).isEmpty();
        assertThat(nearby.q()).isEqualTo("Hoa Lư");
        assertThat(nearby.radiusKm()).isEqualByComparingTo("5");
    }

    @Test
    void rejectsInvalidGeoPairsAndValuesOnDocumentedFields() {
        SearchVenueRequest missingLongitude = new SearchVenueRequest(null, null, BigDecimal.ONE, null, null, null, null);
        SearchVenueRequest radiusWithoutCoordinates = new SearchVenueRequest(null, null, null, null,
                BigDecimal.ONE, null, null);
        SearchVenueRequest invalidValues = new SearchVenueRequest("x".repeat(101), VenueStatusFilter.ALL,
                new BigDecimal("90.00000001"), new BigDecimal("180.00000001"), new BigDecimal("50.1"), 0, 101);

        assertThat(invalidFields(missingLongitude)).containsExactly("lat");
        assertThat(invalidFields(radiusWithoutCoordinates)).containsExactly("radiusKm");
        assertThat(invalidFields(invalidValues)).containsExactlyInAnyOrder("q", "lat", "lng", "radiusKm", "page", "pageSize");
    }

    private static Set<String> invalidFields(SearchVenueRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
