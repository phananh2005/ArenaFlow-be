package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RequestDtoValidationTest {

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
    void oauthLoginRequest() {
        assertThat(invalidFields(new OAuthLoginRequest("GOOGLE", "token", "a@example.com"))).isEmpty();
        assertThat(invalidFields(new OAuthLoginRequest("PHONE", " ", "not-an-email")))
                .containsExactlyInAnyOrder("provider", "oauthToken", "email");
        assertThat(invalidFields(new OAuthLoginRequest(null, null, null)))
                .containsExactlyInAnyOrder("provider", "oauthToken", "email");
    }

    @Test
    void otpRequests() {
        assertThat(invalidFields(new SendOtpRequest("0987654321"))).isEmpty();
        assertThat(invalidFields(new SendOtpRequest("987654321"))).containsExactly("phone");
        assertThat(invalidFields(new VerifyOtpRequest("0987654321", "123456"))).isEmpty();
        assertThat(invalidFields(new VerifyOtpRequest("09876543210", "12345")))
                .containsExactlyInAnyOrder("phone", "otp");
    }

    @Test
    void refreshTokenRequest() {
        assertThat(invalidFields(new RefreshTokenRequest("jwt"))).isEmpty();
        assertThat(invalidFields(new RefreshTokenRequest(""))).containsExactly("refreshToken");
    }

    @Test
    void profileUpdateRequestAllowsAllNullButValidatesPresentFields() {
        assertThat(invalidFields(new ProfileUpdateRequest(null, null, null, null, null, null, null))).isEmpty();
        assertThat(invalidFields(new ProfileUpdateRequest("Nguyễn Văn A", "https://cdn.example.com/a.png", "bio",
                "Tiền vệ", LocalDate.now().minusYears(20), "MALE", UUID.randomUUID()))).isEmpty();
        assertThat(invalidFields(new ProfileUpdateRequest("   ", "not a url", "x".repeat(1001),
                "Thủ thành", LocalDate.now().minusYears(10), "male", null)))
                .containsExactlyInAnyOrder("displayName", "avatarUrl", "bio", "preferredPosition",
                        "dateOfBirth", "gender");
        assertThat(invalidFields(new ProfileUpdateRequest("x".repeat(101), null, null, null,
                LocalDate.now().plusDays(1), null, null)))
                .containsExactlyInAnyOrder("displayName", "dateOfBirth");
    }

    @Test
    void createTeamRequest() {
        assertThat(invalidFields(new CreateTeamRequest("Team Alpha", null, null, List.of("Chill")))).isEmpty();
        assertThat(invalidFields(new CreateTeamRequest(" ", "bad", "x".repeat(1001), List.of("Pro"))))
                .containsExactlyInAnyOrder("teamName", "avatarUrl", "description", "skillLevels");
        assertThat(invalidFields(new CreateTeamRequest(null, null, null, null))).containsExactly("teamName");
    }

    @Test
    void updateTeamRequestAllOptionalButTeamNameNotBlankIfPresent() {
        assertThat(invalidFields(new UpdateTeamRequest(null, null, null, null))).isEmpty();
        assertThat(invalidFields(new UpdateTeamRequest("  ", null, null, List.of("Chill", "Chill"))))
                .containsExactlyInAnyOrder("teamName", "skillLevels");
    }

    @Test
    void avatarUrlAcceptsOnlyHttpAndHttps() {
        for (String allowed : List.of("http://cdn.example.com/a.png", "https://cdn.example.com/a.png")) {
            assertThat(invalidFields(new ProfileUpdateRequest(null, allowed, null, null, null, null, null)))
                    .as(allowed).isEmpty();
            assertThat(invalidFields(new CreateTeamRequest("Team", allowed, null, null))).as(allowed).isEmpty();
            assertThat(invalidFields(new UpdateTeamRequest(null, allowed, null, null))).as(allowed).isEmpty();
        }
        for (String rejected : List.of("file:///etc/passwd", "ftp://cdn.example.com/a.png", "javascript:alert(1)")) {
            assertThat(invalidFields(new ProfileUpdateRequest(null, rejected, null, null, null, null, null)))
                    .as(rejected).containsExactly("avatarUrl");
            assertThat(invalidFields(new CreateTeamRequest("Team", rejected, null, null)))
                    .as(rejected).containsExactly("avatarUrl");
            assertThat(invalidFields(new UpdateTeamRequest(null, rejected, null, null)))
                    .as(rejected).containsExactly("avatarUrl");
        }
    }

    @Test
    void memberAndCaptainAndAdminRequests() {
        assertThat(invalidFields(new InviteMemberRequest(UUID.randomUUID()))).isEmpty();
        assertThat(invalidFields(new InviteMemberRequest(null))).containsExactly("userId");

        assertThat(invalidFields(new TransferCaptainRequest(UUID.randomUUID(), null))).isEmpty();
        assertThat(invalidFields(new TransferCaptainRequest(null, "x".repeat(501))))
                .containsExactlyInAnyOrder("newCaptainId", "reason");

        assertThat(invalidFields(new MarkInactiveRequest("Vi phạm"))).isEmpty();
        assertThat(invalidFields(new MarkInactiveRequest(" "))).containsExactly("reason");
        assertThat(invalidFields(new MarkInactiveRequest("x".repeat(501)))).containsExactly("reason");
    }

    @Test
    void disbandTeamRequestReasonOptionalUpTo100Chars() {
        assertThat(invalidFields(new DisbandTeamRequest(null))).isEmpty();
        assertThat(invalidFields(new DisbandTeamRequest("   "))).isEmpty();
        assertThat(invalidFields(new DisbandTeamRequest("x".repeat(100)))).isEmpty();
        assertThat(invalidFields(new DisbandTeamRequest("x".repeat(101)))).containsExactly("reason");
    }
}
