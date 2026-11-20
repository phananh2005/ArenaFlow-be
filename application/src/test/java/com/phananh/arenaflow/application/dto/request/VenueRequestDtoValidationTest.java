package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class VenueRequestDtoValidationTest {

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

    private static Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void createVenueRequiresValidCoreFieldsAndAllowsEmptyOptionalValues() {
        CreateVenueRequest valid = new CreateVenueRequest("Sân Hoa Lư", "2 Đinh Tiên Hoàng",
                new BigDecimal("10.7881234"), new BigDecimal("106.6991234"), UUID.randomUUID(), "",
                "", "");
        CreateVenueRequest invalid = new CreateVenueRequest(" ", " ", new BigDecimal("90.00000001"),
                new BigDecimal("180.00000001"), null, "NINE_A_SIDE", "123", "ftp://venue.test");

        assertThat(invalidFields(valid)).isEmpty();
        assertThat(invalidFields(invalid)).containsExactlyInAnyOrder(
                "venueName", "address", "latitude", "longitude", "wardId", "venueType", "phone", "website");
    }

    @Test
    void updateVenueAllowsAllNullAndRejectsInvalidProvidedValues() {
        UpdateVenueRequest empty = new UpdateVenueRequest(null, null, null, null, null, null, null, null);
        UpdateVenueRequest invalid = new UpdateVenueRequest(" ", " ", new BigDecimal("-90.00000001"),
                new BigDecimal("180.00000001"), null, "NINE_A_SIDE", "123", "not-a-url");

        assertThat(invalidFields(empty)).isEmpty();
        assertThat(invalidFields(invalid)).containsExactlyInAnyOrder(
                "venueName", "address", "latitude", "longitude", "venueType", "phone", "website");
    }

    @Test
    void addVenueImageValidatesCloudinaryConfirmationFields() {
        AddVenueImageRequest valid = new AddVenueImageRequest("folder/venues/image", 1L, "webp", "signature", true);
        AddVenueImageRequest invalid = new AddVenueImageRequest(" ", 0L, "JPG", " ", null);

        assertThat(invalidFields(valid)).isEmpty();
        assertThat(invalidFields(invalid)).containsExactlyInAnyOrder("publicId", "version", "format", "signature");
    }

    @Test
    void reportAndResolveRequestsAcceptOnlyTheirDocumentedStatuses() {
        assertThat(invalidFields(new ReportVenueRequest("CLOSED", null))).isEmpty();
        assertThat(invalidFields(new ReportVenueRequest("INVALID", "x".repeat(1001))))
                .containsExactlyInAnyOrder("reason", "detail");
        assertThat(invalidFields(new ResolveVenueReportRequest("VERIFIED"))).isEmpty();
        assertThat(invalidFields(new ResolveVenueReportRequest("PENDING"))).containsExactly("status");
    }
}
