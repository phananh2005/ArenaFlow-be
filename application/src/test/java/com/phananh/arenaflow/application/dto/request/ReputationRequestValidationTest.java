package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ReputationRequestValidationTest {

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
    void ratingDefaultsToNotNoShowAndTrimsEmptyCommentToNull() {
        CreateRatingRequest request = new CreateRatingRequest(null, 5, "  ", 3, 2);

        assertThat(request.isNoShow()).isFalse();
        assertThat(request.comment()).isNull();
        assertThat(invalidFields(request)).isEmpty();
        assertThat(new CreateRatingRequest(false, 4, "  Đá đẹp ", null, null).comment()).isEqualTo("Đá đẹp");
    }

    @Test
    void starRatingRequiresScoreOneToFive() {
        assertThat(invalidFields(new CreateRatingRequest(false, null, null, null, null))).containsExactly("score");
        assertThat(invalidFields(new CreateRatingRequest(false, 0, null, null, null))).containsExactly("score");
        assertThat(invalidFields(new CreateRatingRequest(false, 6, null, null, null))).containsExactly("score");
    }

    @Test
    void noShowRatingRejectsScoreAndTeamScores() {
        assertThat(invalidFields(new CreateRatingRequest(true, null, "Không đến", null, null))).isEmpty();
        assertThat(invalidFields(new CreateRatingRequest(true, 5, null, null, null))).containsExactly("score");
        assertThat(invalidFields(new CreateRatingRequest(true, null, null, 1, 0)))
                .containsExactlyInAnyOrder("raterTeamScore", "rateeTeamScore");
    }

    @Test
    void reportRequiresKnownTypeAndTrimmedReasonUpTo1000() {
        assertThat(invalidFields(new CreateReportRequest("NOSHOW", "  Không đến  "))).isEmpty();
        assertThat(new CreateReportRequest("NOSHOW", "  Không đến  ").reason()).isEqualTo("Không đến");
        assertThat(invalidFields(new CreateReportRequest("LATE", "Trễ"))).containsExactly("reportType");
        assertThat(invalidFields(new CreateReportRequest(null, "   "))).containsExactlyInAnyOrder("reportType", "reason");
        assertThat(invalidFields(new CreateReportRequest("BAD_PLAY", "x".repeat(1001)))).containsExactly("reason");
        assertThat(invalidFields(new CreateChatReportRequest("  "))).containsExactly("reason");
        assertThat(new CreateChatReportRequest(" Chửi bới ").reason()).isEqualTo("Chửi bới");
    }

    @Test
    void disputeAndResolutionRequireTrimmedTextWithinLimits() {
        assertThat(new CreateDisputeRequest("  Có mặt  ").evidence()).isEqualTo("Có mặt");
        assertThat(invalidFields(new CreateDisputeRequest(" "))).containsExactly("evidence");
        assertThat(invalidFields(new CreateDisputeRequest("x".repeat(1001)))).containsExactly("evidence");
        assertThat(invalidFields(new ResolveDisputeRequest("REJECTED", " Hợp lệ "))).isEmpty();
        assertThat(new ResolveDisputeRequest("REJECTED", " Hợp lệ ").note()).isEqualTo("Hợp lệ");
        assertThat(invalidFields(new ResolveDisputeRequest("APPROVED", "x".repeat(501))))
                .containsExactlyInAnyOrder("decision", "note");
        assertThat(invalidFields(new ResolveDisputeRequest(null, null))).containsExactlyInAnyOrder("decision", "note");
    }

    @Test
    void teamScoresComeInPairsWithinRangeAndCommentLimit() {
        assertThat(invalidFields(new CreateRatingRequest(false, 3, null, 1, null))).containsExactly("rateeTeamScore");
        assertThat(invalidFields(new CreateRatingRequest(false, 3, null, null, 2))).containsExactly("raterTeamScore");
        assertThat(invalidFields(new CreateRatingRequest(false, 3, null, 1000, -1)))
                .containsExactlyInAnyOrder("raterTeamScore", "rateeTeamScore");
        assertThat(invalidFields(new CreateRatingRequest(false, 3, "x".repeat(501), null, null))).containsExactly("comment");
    }
}
