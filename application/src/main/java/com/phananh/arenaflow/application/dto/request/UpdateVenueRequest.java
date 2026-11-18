package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.OneOf;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.util.UUID;

/** PATCH venue: fields are optional; ward validity is conditional in the service. */
public record UpdateVenueRequest(
        @Size(max = 200) @Pattern(regexp = "(?s).*\\S.*") String venueName,
        @Size(max = 500) @Pattern(regexp = "(?s).*\\S.*") String address,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 2, fraction = 7) BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
        UUID wardId,
        @OneOf({"FIVE_A_SIDE", "SEVEN_A_SIDE", "ELEVEN_A_SIDE", ""}) String venueType,
        @Pattern(regexp = "^$|^0[0-9]{9,10}$") String phone,
        @URL(regexp = "^$|^https?://.*") @Size(max = 500) String website) {
}
