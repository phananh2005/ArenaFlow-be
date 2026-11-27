package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.common.time.AppTime;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Bean Validation của request tạo kèo (luồng A) và tìm kèo. */
class MatchRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    private final LocalDateTime start = AppTime.now().plusDays(2).truncatedTo(ChronoUnit.HOURS);

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private CreateMatchRequest flowA(UUID venueId, LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime expiresAt) {
        return new CreateMatchRequest(null, UUID.randomUUID(), UUID.randomUUID(), venueId, null, "SEVEN_A_SIDE",
                startsAt, endsAt, null, expiresAt, List.of("Nhiệt tình"), "U23", "Giao hữu");
    }

    private static <T> Set<String> invalidFields(Set<ConstraintViolation<T>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void typeDefaultsToHasVenueAndValidFlowAPasses() {
        CreateMatchRequest request = flowA(UUID.randomUUID(), start, start.plusHours(2), start.minusHours(6));

        assertThat(request.type()).isEqualTo("HAS_VENUE");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void flowARequiresVenueStartAndEnd() {
        assertThat(invalidFields(validator.validate(flowA(null, null, null, start))))
                .contains("venueId", "startsAt", "endsAt");
    }

    @Test
    void flowARejectsFieldsOfFlowB() {
        CreateMatchRequest request = new CreateMatchRequest("HAS_VENUE", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of(UUID.randomUUID()), "SEVEN_A_SIDE", start, start.plusHours(2),
                List.of(new TimeOptionRequest(start, start.plusHours(1))), start.minusHours(1), null, null, null);

        assertThat(invalidFields(validator.validate(request))).contains("venueOptionIds", "timeOptions");
    }

    @Test
    void startMustBeAtLeastOneHourAhead() {
        LocalDateTime soon = AppTime.now().plusMinutes(30);
        assertThat(invalidFields(validator.validate(flowA(UUID.randomUUID(), soon, soon.plusHours(1), soon))))
                .contains("startsAt");
    }

    @Test
    void endMustBeAfterStartAndWithinTwelveHours() {
        assertThat(invalidFields(validator.validate(flowA(UUID.randomUUID(), start, start, start.minusHours(1)))))
                .contains("endsAt");
        assertThat(invalidFields(validator.validate(flowA(UUID.randomUUID(), start, start.plusHours(12),
                start.minusHours(1))))).doesNotContain("endsAt");
        assertThat(invalidFields(validator.validate(flowA(UUID.randomUUID(), start, start.plusHours(12).plusSeconds(1),
                start.minusHours(1))))).contains("endsAt");
    }

    @Test
    void expiresAtWithinSevenDaysBeforeStartAndInFuture() {
        assertThat(invalidFields(validator.validate(flowA(UUID.randomUUID(), start, start.plusHours(2), start))))
                .doesNotContain("expiresAt");
        assertThat(invalidFields(validator.validate(flowA(UUID.randomUUID(), start, start.plusHours(2),
                start.plusSeconds(1))))).contains("expiresAt");
        LocalDateTime far = AppTime.now().plusDays(20);
        assertThat(invalidFields(validator.validate(flowA(UUID.randomUUID(), far, far.plusHours(2),
                far.minusDays(7).minusSeconds(1))))).contains("expiresAt");
        assertThat(invalidFields(validator.validate(flowA(UUID.randomUUID(), start, start.plusHours(2),
                AppTime.now().minusMinutes(1))))).contains("expiresAt");
    }

    @Test
    void needVenueSkipsFlowAScheduleChecksInFirstWave() {
        CreateMatchRequest request = new CreateMatchRequest("NEED_VENUE", UUID.randomUUID(), UUID.randomUUID(), null,
                List.of(UUID.randomUUID()), "SEVEN_A_SIDE", null, null,
                List.of(new TimeOptionRequest(start, start.plusHours(2))), start.minusHours(1), null, null, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void enumsLengthsAndSkillLevelsAreChecked() {
        CreateMatchRequest request = new CreateMatchRequest("OTHER", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, "SIX", start, start.plusHours(2), null, start.minusHours(1),
                List.of("Không có"), "x".repeat(101), "y".repeat(201));

        assertThat(invalidFields(validator.validate(request)))
                .contains("type", "format", "skillLevels", "ageNote", "matchNature");
    }

    private UpdateCostAgreementRequest cost(String type, String split, Long venue, Long drinks) {
        return new UpdateCostAgreementRequest(type, split, venue, drinks, null);
    }

    @Test
    void costAgreementRelationsBetweenTypeAmountsAndSplit() {
        assertThat(validator.validate(cost(null, null, null, null))).isEmpty();
        assertThat(validator.validate(cost("BOTH", "LOSER_PAYS_DRINKS", 1L, 1L))).isEmpty();
        assertThat(validator.validate(cost("VENUE_ONLY", "FIFTY_FIFTY", 600_000L, null))).isEmpty();
        assertThat(cost(null, null, null, null).customItems()).isEmpty();

        assertThat(invalidFields(validator.validate(cost(null, "FIFTY_FIFTY", 1L, 1L))))
                .contains("costSplit", "venueAmount", "drinksAmount");
        assertThat(invalidFields(validator.validate(cost("BOTH", null, null, null))))
                .contains("costSplit", "venueAmount", "drinksAmount");
        assertThat(invalidFields(validator.validate(cost("VENUE_ONLY", "LOSER_PAYS_DRINKS", 1L, 1L))))
                .contains("costSplit", "drinksAmount");
        assertThat(invalidFields(validator.validate(cost("DRINKS_ONLY", "FIFTY_FIFTY", 1L, 1L)))).contains("venueAmount");
        assertThat(invalidFields(validator.validate(cost("CUSTOM", "X", 0L, 1_000_000_001L))))
                .contains("costType", "costSplit", "venueAmount", "drinksAmount");
    }

    @Test
    void costAgreementLimitsCustomItems() {
        List<CostCustomItemRequest> eleven = Collections.nCopies(11, new CostCustomItemRequest("Nước", 1L));
        assertThat(invalidFields(validator.validate(new UpdateCostAgreementRequest(null, null, null, null, eleven))))
                .contains("customItems");
        assertThat(invalidFields(validator.validate(new UpdateCostAgreementRequest(null, null, null, null,
                List.of(new CostCustomItemRequest(" ", 0L), new CostCustomItemRequest("x".repeat(101), null))))))
                .contains("customItems[0].itemName", "customItems[0].amount", "customItems[1].itemName",
                        "customItems[1].amount");
    }

    @Test
    void cancelAndWithdrawRequireExpectedStatus() {
        assertThat(invalidFields(validator.validate(new CancelMatchRequest(null, null, "x".repeat(501)))))
                .contains("teamId", "expectedStatus", "reason");
        assertThat(invalidFields(validator.validate(new CancelMatchRequest(UUID.randomUUID(), "PENDING", null))))
                .contains("expectedStatus");
        assertThat(validator.validate(new CancelMatchRequest(UUID.randomUUID(), "CONFIRMED", null))).isEmpty();
    }

    @Test
    void searchDefaultsAndGeoAndTimeRangeRules() {
        SearchMatchRequest defaults = new SearchMatchRequest(null, null, null, null, null, null, null, null, null, null, null);
        assertThat(defaults.page()).isEqualTo(1);
        assertThat(defaults.pageSize()).isEqualTo(20);
        assertThat(defaults.radiusKm()).isNull();

        SearchMatchRequest nearby = new SearchMatchRequest(null, null, null, null, null, null,
                new BigDecimal("10.78"), new BigDecimal("106.7"), null, null, null);
        assertThat(nearby.radiusKm()).isEqualByComparingTo("10");
        assertThat(validator.validate(nearby)).isEmpty();

        assertThat(invalidFields(validator.validate(new SearchMatchRequest(null, null, null, null, null, null,
                BigDecimal.ONE, null, null, null, null)))).contains("lat");
        assertThat(invalidFields(validator.validate(new SearchMatchRequest(null, null, null, null, null, null,
                null, null, BigDecimal.ONE, null, null)))).contains("radiusKm");
        assertThat(invalidFields(validator.validate(new SearchMatchRequest(null, null, null, null, start,
                start.minusSeconds(1), null, null, null, null, null)))).contains("startsAtTo");
        assertThat(invalidFields(validator.validate(new SearchMatchRequest("X", null, "SIX", "Không có", null, null,
                null, null, null, 0, 101)))).contains("type", "format", "skillLevel", "page", "pageSize");
        assertThat(validator.validate(new SearchMatchRequest("HAS_VENUE", null, "FIVE_A_SIDE", "Nhiệt tình", start,
                start, null, null, null, null, null))).isEmpty();
    }
}
