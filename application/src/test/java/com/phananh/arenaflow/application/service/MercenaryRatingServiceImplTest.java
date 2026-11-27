package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateMercenaryRatingRequest;
import com.phananh.arenaflow.application.dto.response.MercenaryRatingResponse;
import com.phananh.arenaflow.application.dto.response.MercenaryRatingsResponse;
import com.phananh.arenaflow.application.event.mercenary.MercenaryRatingCreatedEvent;
import com.phananh.arenaflow.application.mapper.MercenaryReputationMapper;
import com.phananh.arenaflow.application.service.impl.MercenaryRatingServiceImpl;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.DataIntegrityViolationFixtures;
import com.phananh.arenaflow.application.support.mercenary.MercenaryGuard;
import com.phananh.arenaflow.application.support.reputation.VerificationRefresher;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.rating.Rating;
import com.phananh.arenaflow.domain.projection.MercenaryRatingContextView;
import com.phananh.arenaflow.domain.repository.RatingRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MercenaryRatingServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    private final RatingRepository ratingRepository = mock(RatingRepository.class);
    private final ReputationService reputationService = mock(ReputationService.class);
    private final VerificationRefresher verificationRefresher = mock(VerificationRefresher.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID callerId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final UUID applicantId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final UUID matchId = UUID.randomUUID();
    private final UUID ratingId = UUID.randomUUID();
    private MercenaryRatingServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new MercenaryRatingServiceImpl(ratingRepository, new MercenaryGuard(), reputationService,
                verificationRefresher, retrier, Mappers.getMapper(MercenaryReputationMapper.class), eventPublisher,
                Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
        when(retrier.execute(eq(ErrorCode.MATCH_014), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(1)).get());
        when(ratingRepository.saveAndFlush(any(Rating.class))).thenAnswer(invocation -> {
            Rating rating = invocation.getArgument(0);
            ReflectionTestUtils.setField(rating, "id", ratingId);
            ReflectionTestUtils.setField(rating, "createdAt", NOW);
            return rating;
        });
    }

    @Test
    void guardOrder() {
        assertCreateError(Map.of("callerDeleted", true), ErrorCode.USER_001);
        assertCreateError(Map.of("applicationId", ""), ErrorCode.MERCENARY_002);
        assertCreateError(Map.of("callerApplicant", true), ErrorCode.MERCENARY_019);
        assertCreateError(Map.of("callerCaptain", false), ErrorCode.MERCENARY_014);
        assertCreateError(Map.of("teamStatus", "INACTIVE", "gameEnded", false), ErrorCode.TEAM_015);
        assertCreateError(Map.of("gameEnded", false), ErrorCode.MERCENARY_015);
        assertCreateError(Map.of("applicationStatus", "WITHDRAWN"), ErrorCode.MERCENARY_015);
        assertCreateError(Map.of("teamRatingId", UUID.randomUUID()), ErrorCode.MERCENARY_017);
        verify(ratingRepository, never()).saveAndFlush(any());
    }

    @Test
    void mercenarySideCannotChooseNoShow() {
        when(ratingRepository.findMercenaryRatingContext(applicationId, callerId, NOW)).thenReturn(Optional.of(
                ctx(Map.of("callerCaptain", false, "callerApplicant", true))));

        assertError(() -> service.createRating(applicationId, callerId, new CreateMercenaryRatingRequest(true, null,
                null)), ErrorCode.MERCENARY_016);
    }

    @Test
    void duplicateRaceMapsToMercenary017() {
        when(ratingRepository.findMercenaryRatingContext(applicationId, callerId, NOW)).thenReturn(Optional.of(
                ctx(Map.of())));
        when(ratingRepository.saveAndFlush(any(Rating.class)))
                .thenThrow(DataIntegrityViolationFixtures.violation("idx_rating_mercenary_unique"));

        assertError(() -> service.createRating(applicationId, callerId, new CreateMercenaryRatingRequest(null, 4,
                null)), ErrorCode.MERCENARY_017);
    }

    @Test
    void captainStarRatingCompletesMercenaryWithFiveStarBonus() {
        when(ratingRepository.findMercenaryRatingContext(applicationId, callerId, NOW)).thenReturn(Optional.of(
                ctx(Map.of())));

        MercenaryRatingResponse response = service.createRating(applicationId, callerId,
                new CreateMercenaryRatingRequest(null, 5, "Tốt"));

        ArgumentCaptor<Rating> saved = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRaterType()).isEqualTo(Rating.RaterType.TEAM);
        assertThat(saved.getValue().getRaterId()).isEqualTo(teamId);
        assertThat(saved.getValue().getRateeType()).isEqualTo(Rating.RateeType.INDIVIDUAL);
        assertThat(saved.getValue().getRateeId()).isEqualTo(applicantId);
        assertThat(saved.getValue().getApplicationId()).isEqualTo(applicationId);
        assertThat(saved.getValue().getCreatedByUserId()).isEqualTo(callerId);
        verify(reputationService).applyMercenaryCompletion(applicantId, matchId, null, 1, ratingId);
        verify(verificationRefresher).refreshUsersAfterCommit(Set.of(applicantId));
        assertThat(response.direction()).isEqualTo("TEAM_TO_MERCENARY");
        assertThat(response.source()).isEqualTo("SYSTEM");
        verify(eventPublisher).publishEvent(any(MercenaryRatingCreatedEvent.class));
    }

    @Test
    void captainNoShowAndMercenaryRatingDoNotTouchReputation() {
        when(ratingRepository.findMercenaryRatingContext(applicationId, callerId, NOW)).thenReturn(Optional.of(
                ctx(Map.of())));
        service.createRating(applicationId, callerId, new CreateMercenaryRatingRequest(true, null, null));

        when(ratingRepository.findMercenaryRatingContext(applicationId, callerId, NOW)).thenReturn(Optional.of(
                ctx(Map.of("callerCaptain", false, "callerApplicant", true))));
        MercenaryRatingResponse response = service.createRating(applicationId, callerId,
                new CreateMercenaryRatingRequest(null, 3, null));

        assertThat(response.direction()).isEqualTo("MERCENARY_TO_TEAM");
        verify(reputationService, never()).applyMercenaryCompletion(any(), any(), any(), anyInt(), any());
        verifyNoInteractions(verificationRefresher);
    }

    @Test
    void receivedRatingHiddenUntilViewerRated() {
        when(ratingRepository.findMercenaryRatingContext(applicationId, callerId, NOW)).thenReturn(Optional.of(
                ctx(Map.of("mercenaryRatingId", UUID.randomUUID(), "mercenaryRatingScore", 4))));
        MercenaryRatingsResponse hidden = service.getRatings(applicationId, callerId);
        assertThat(hidden.viewerSide()).isEqualTo("TEAM");
        assertThat(hidden.given()).isNull();
        assertThat(hidden.received()).isNull();

        when(ratingRepository.findMercenaryRatingContext(applicationId, callerId, NOW)).thenReturn(Optional.of(
                ctx(Map.of("mercenaryRatingId", UUID.randomUUID(), "mercenaryRatingScore", 4,
                        "teamRatingId", UUID.randomUUID(), "teamRatingScore", 5, "teamRatingNoShow", false))));
        MercenaryRatingsResponse visible = service.getRatings(applicationId, callerId);
        assertThat(visible.given().score()).isEqualTo(5);
        assertThat(visible.received().score()).isEqualTo(4);
    }

    private void assertCreateError(Map<String, Object> overrides, ErrorCode code) {
        when(ratingRepository.findMercenaryRatingContext(applicationId, callerId, NOW))
                .thenReturn(Optional.of(ctx(overrides)));
        assertError(() -> service.createRating(applicationId, callerId, new CreateMercenaryRatingRequest(null, 4,
                null)), code);
    }

    private MercenaryRatingContextView ctx(Map<String, Object> overrides) {
        Map<String, Object> values = new HashMap<>();
        values.put("callerDeleted", false);
        values.put("applicationId", applicationId);
        values.put("applicationStatus", "APPROVED");
        values.put("applicantId", applicantId);
        values.put("matchId", matchId);
        values.put("gameEnded", true);
        values.put("teamId", teamId);
        values.put("teamStatus", "ACTIVE");
        values.put("callerCaptain", true);
        values.put("callerApplicant", false);
        values.putAll(overrides);
        if ("".equals(values.get("applicationId"))) {
            values.put("applicationId", null);
        }
        return projections.createProjection(MercenaryRatingContextView.class, values);
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
