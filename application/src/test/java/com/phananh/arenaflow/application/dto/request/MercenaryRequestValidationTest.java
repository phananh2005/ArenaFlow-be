package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.common.time.AppTime;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MercenaryRequestValidationTest {

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
    void searchDefaultsPageAndRadiusOnlyWithCoordinates() {
        SearchRecruitmentsRequest withoutGps = new SearchRecruitmentsRequest(null, null, null, " ", null, null);
        SearchRecruitmentsRequest withGps = new SearchRecruitmentsRequest(10.7, 106.7, null, "Tiền vệ", null, null);

        assertThat(withoutGps.page()).isEqualTo(1);
        assertThat(withoutGps.pageSize()).isEqualTo(20);
        assertThat(withoutGps.radiusKm()).isNull();
        assertThat(withoutGps.position()).isNull();
        assertThat(withGps.radiusKm()).isEqualTo(3.0);
        assertThat(fields(withGps)).isEmpty();
    }

    @Test
    void searchRejectsHalfCoordinatesRadiusWithoutCoordinatesAndOutOfRangeRadius() {
        assertThat(fields(new SearchRecruitmentsRequest(10.7, null, null, null, null, null))).contains("lat");
        assertThat(fields(new SearchRecruitmentsRequest(null, null, 5.0, null, null, null))).contains("radiusKm");
        assertThat(fields(new SearchRecruitmentsRequest(10.7, 106.7, 25.0, null, null, null))).contains("radiusKm");
        assertThat(fields(new SearchRecruitmentsRequest(10.7, 106.7, 0.5, null, null, null))).contains("radiusKm");
        assertThat(fields(new SearchRecruitmentsRequest(null, null, null, "Libero", 0, 101)))
                .contains("position", "page", "pageSize");
    }

    @Test
    void createRecruitmentsLimitsPositionsAndQuantity() {
        RecruitmentPositionRequest midfield = new RecruitmentPositionRequest("Tiền vệ", 1);
        assertThat(fields(new CreateRecruitmentsRequest(UUID.randomUUID(), List.of(midfield)))).isEmpty();
        assertThat(fields(new CreateRecruitmentsRequest(null, List.of()))).contains("teamId", "positions");
        assertThat(fields(new CreateRecruitmentsRequest(UUID.randomUUID(), List.of(
                new RecruitmentPositionRequest("Thủ môn", 11), new RecruitmentPositionRequest("Hậu vệ", 0)))))
                .contains("positions[0].quantity", "positions[1].quantity");
        assertThat(fields(new CreateRecruitmentsRequest(UUID.randomUUID(), List.of(
                new RecruitmentPositionRequest("Thủ môn", 1), new RecruitmentPositionRequest("Hậu vệ", 1),
                new RecruitmentPositionRequest("Tiền vệ", 1), new RecruitmentPositionRequest("Tiền đạo", 1),
                new RecruitmentPositionRequest("Tiền đạo", 1))))).contains("positions");
    }

    @Test
    void externalMatchTrimsOpponentAndChecksSchedule() {
        LocalDateTime start = AppTime.now().plusHours(2);
        CreateExternalMatchRequest request = new CreateExternalMatchRequest(UUID.randomUUID(), start,
                start.plusHours(2), null, "  ", List.of(new RecruitmentPositionRequest("Tiền vệ", 1)));

        assertThat(request.opponentName()).isNull();
        assertThat(request.skillLevels()).isEmpty();
        assertThat(fields(request)).isEmpty();
        assertThat(fields(new CreateExternalMatchRequest(UUID.randomUUID(), start, start.plusHours(13), List.of(),
                "x".repeat(101), List.of(new RecruitmentPositionRequest("Tiền vệ", 1)))))
                .contains("endsAt", "opponentName");
    }

    @Test
    void withdrawAndRatingRequests() {
        assertThat(fields(new WithdrawApplicationRequest("APPROVED"))).isEmpty();
        assertThat(fields(new WithdrawApplicationRequest("REJECTED"))).contains("expectedStatus");
        assertThat(fields(new WithdrawApplicationRequest(null))).contains("expectedStatus");
        assertThat(fields(new CreateMercenaryRatingRequest(null, 4, " Tốt "))).isEmpty();
        assertThat(new CreateMercenaryRatingRequest(null, 4, " Tốt ").comment()).isEqualTo("Tốt");
        assertThat(new CreateMercenaryRatingRequest(null, 4, "  ").comment()).isNull();
        assertThat(fields(new CreateMercenaryRatingRequest(false, null, null))).contains("score");
        assertThat(fields(new CreateMercenaryRatingRequest(null, 3, "x".repeat(501)))).contains("comment");
    }

    private static Set<String> fields(Object request) {
        return validator.validate(request).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(Collectors.toSet());
    }
}
