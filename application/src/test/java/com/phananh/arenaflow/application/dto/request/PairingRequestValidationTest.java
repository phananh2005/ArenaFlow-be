package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PairingRequestValidationTest {

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
    void createPairingRequiresTeam() {
        assertThat(invalidFields(new CreatePairingRequest(UUID.randomUUID()))).isEmpty();
        assertThat(invalidFields(new CreatePairingRequest(null))).containsExactly("teamId");
    }

    @Test
    void proposalRequiresTeamNonNegativeRevisionVenueAndTimeOption() {
        assertThat(invalidFields(new UpdateProposalRequest(UUID.randomUUID(), 0, UUID.randomUUID(), UUID.randomUUID())))
                .isEmpty();
        assertThat(invalidFields(new UpdateProposalRequest(null, -1, null, null)))
                .containsExactlyInAnyOrder("teamId", "revision", "venueId", "timeOptionId");
        assertThat(invalidFields(new UpdateProposalRequest(UUID.randomUUID(), null, UUID.randomUUID(),
                UUID.randomUUID()))).containsExactly("revision");
    }

    @Test
    void confirmRequiresTeamAndNonNegativeRevision() {
        assertThat(invalidFields(new ConfirmPairingRequest(UUID.randomUUID(), 3))).isEmpty();
        assertThat(invalidFields(new ConfirmPairingRequest(null, -1))).containsExactlyInAnyOrder("teamId", "revision");
    }

    @Test
    void costAgreementAcceptsDepositWithHolderAndEmptyAgreement() {
        assertThat(invalidFields(cost("VENUE_ONLY", "FIFTY_FIFTY", 600_000L, null, 200_000L, "Đội chủ nhà",
                List.of(new CostCustomItemRequest("Trọng tài", 200_000L))))).isEmpty();
        assertThat(invalidFields(cost(null, null, null, null, null, null, null))).isEmpty();
    }

    @Test
    void absentCustomItemsBecomeEmptyList() {
        assertThat(cost(null, null, null, null, null, null, null).customItems()).isEmpty();
    }

    @Test
    void depositAmountRangeAndHolderLength() {
        assertThat(invalidFields(cost(null, null, null, null, 0L, null, List.of()))).containsExactly("depositAmount");
        assertThat(invalidFields(cost(null, null, null, null, 1_000_000_001L, null, List.of())))
                .containsExactly("depositAmount");
        assertThat(invalidFields(cost(null, null, null, null, 1L, "x".repeat(101), List.of())))
                .containsExactly("depositHolder");
        assertThat(invalidFields(cost(null, null, null, null, 1_000_000_000L, "x".repeat(100), List.of()))).isEmpty();
    }

    @Test
    void depositHolderWithoutAmountIsReportedOnHolder() {
        assertThat(invalidFields(cost(null, null, null, null, null, "Đội chủ nhà", List.of())))
                .containsExactly("depositHolder");
    }

    @Test
    void blankDepositHolderMeansNotEnteredSoNeedsNoAmount() {
        assertThat(invalidFields(cost(null, null, null, null, null, "  ", List.of()))).isEmpty();
    }

    @Test
    void costAgreementRejectsTeamRevisionEnumsAmountsAndTooManyItems() {
        List<CostCustomItemRequest> eleven = Collections.nCopies(11, new CostCustomItemRequest("Khoản", 1L));
        UpdatePairingCostAgreementRequest invalid = new UpdatePairingCostAgreementRequest(null, -1, "FREE", "HALF",
                0L, 0L, null, null, eleven);

        assertThat(invalidFields(invalid)).containsExactlyInAnyOrder("teamId", "revision", "costType", "costSplit",
                "venueAmount", "drinksAmount", "customItems");
        assertThat(invalidFields(cost(null, null, null, null, null, null,
                List.of(new CostCustomItemRequest(" ", 0L))))).containsExactlyInAnyOrder("customItems[0].itemName",
                "customItems[0].amount");
    }

    private static UpdatePairingCostAgreementRequest cost(String costType, String costSplit, Long venueAmount,
                                                          Long drinksAmount, Long depositAmount, String depositHolder,
                                                          List<CostCustomItemRequest> items) {
        return new UpdatePairingCostAgreementRequest(UUID.randomUUID(), 2, costType, costSplit, venueAmount,
                drinksAmount, depositAmount, depositHolder, items);
    }
}
