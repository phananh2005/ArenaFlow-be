package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateRatingRequest;
import com.phananh.arenaflow.application.dto.response.MatchRatingsResponse;
import com.phananh.arenaflow.application.dto.response.RatingCreatedResponse;
import com.phananh.arenaflow.application.event.reputation.MatchRatedEvent;
import com.phananh.arenaflow.application.mapper.ReputationMapper;
import com.phananh.arenaflow.application.service.impl.RatingServiceImpl;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.application.support.reputation.TeamVerificationRefresher;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.rating.Rating;
import com.phananh.arenaflow.domain.projection.MatchRatingRowView;
import com.phananh.arenaflow.domain.projection.RatingContextView;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import com.phananh.arenaflow.domain.repository.RatingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static com.phananh.arenaflow.application.support.DataIntegrityViolationFixtures.violation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RatingServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T14:05:00Z"), ZoneId.systemDefault());
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 21, 5);

    private final MatchRepository matchRepository = mock(MatchRepository.class);
    private final RatingRepository ratingRepository = mock(RatingRepository.class);
    private final TeamGuard teamGuard = mock(TeamGuard.class);
    private final ReputationService reputationService = mock(ReputationService.class);
    private final TeamVerificationRefresher refresher = mock(TeamVerificationRefresher.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID matchId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID author = UUID.randomUUID();
    private final UUID opponent = UUID.randomUUID();
    private final UUID ratingId = UUID.randomUUID();

    private RatingService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new RatingServiceImpl(matchRepository, ratingRepository, teamGuard, reputationService, refresher,
                retrier, Mappers.getMapper(ReputationMapper.class), publisher, CLOCK);
        when(retrier.execute(eq(ErrorCode.MATCH_014), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(1)).get());
        when(ratingRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Rating rating = invocation.getArgument(0);
            ReflectionTestUtils.setField(rating, "id", ratingId);
            ReflectionTestUtils.setField(rating, "createdAt", NOW);
            return rating;
        });
    }

    @Test
    void missingMatchIsMatch001() {
        when(matchRepository.findRatingContext(matchId, callerId)).thenReturn(Optional.empty());

        assertCode(() -> service.createRating(matchId, callerId, stars(5)), ErrorCode.MATCH_001);
    }

    @Test
    void guardOrderTeam002ThenTeam015ThenRating003ThenRating001ThenRating002() {
        stub(context("ARCHIVED", false, false, "ACTIVE", false, false));
        assertCode(() -> service.createRating(matchId, callerId, stars(5)), ErrorCode.TEAM_002);

        stub(context("OPEN", true, true, "INACTIVE", false, false));
        assertCode(() -> service.createRating(matchId, callerId, stars(5)), ErrorCode.TEAM_015);

        stub(context("OPEN", true, true, "ACTIVE", false, false));
        assertCode(() -> service.createRating(matchId, callerId, stars(5)), ErrorCode.RATING_003);

        stub(context("CONFIRMED", true, false, "ACTIVE", false, false));
        assertCode(() -> service.createRating(matchId, callerId, stars(5)), ErrorCode.RATING_001);

        stub(context("COMPLETED", true, false, "ACTIVE", true, false));
        assertCode(() -> service.createRating(matchId, callerId, stars(5)), ErrorCode.RATING_002);
        verify(ratingRepository, never()).saveAndFlush(any());
    }

    @Test
    void uniqueIndexRaceIsRating002() {
        stub(context("COMPLETED", true, false, "ACTIVE", false, false));
        doThrow(violation("idx_rating_unique")).when(ratingRepository).saveAndFlush(any());

        assertCode(() -> service.createRating(matchId, callerId, stars(5)), ErrorCode.RATING_002);
    }

    @Test
    void firstRatingOfArchivedMatchCompletesItForBothTeamsWithFiveStarBonus() {
        stub(context("ARCHIVED", false, true, "ACTIVE", false, false));
        when(matchRepository.completeIfArchived(matchId, NOW)).thenReturn(1);

        RatingCreatedResponse response = service.createRating(matchId, callerId, stars(5));

        verify(reputationService).applyMatchCompletion(matchId, List.of(new CompletionEntry(opponent, true, 0),
                new CompletionEntry(author, true, 1)));
        verify(reputationService, never()).adjustTeamReliability(any(), any());
        verify(publisher).publishEvent(new MatchRatedEvent(ratingId, matchId, opponent, author, false, true, NOW));
        assertThat(refreshedTeams()).containsExactlyInAnyOrder(author, opponent);
        assertThat(response.matchStatus()).isEqualTo("COMPLETED");
        assertThat(response.raterTeamId()).isEqualTo(opponent);
        assertThat(response.rateeTeamId()).isEqualTo(author);
        assertThat(response.score()).isEqualTo(5);
    }

    @Test
    void noShowFirstRatingCompletesOnlyForRater() {
        stub(context("ARCHIVED", true, false, "ACTIVE", false, false));
        when(matchRepository.completeIfArchived(matchId, NOW)).thenReturn(1);

        service.createRating(matchId, callerId, new CreateRatingRequest(true, null, "Không đến", null, null));

        verify(reputationService).applyMatchCompletion(matchId, List.of(new CompletionEntry(author, true, 0)));
        verify(publisher).publishEvent(new MatchRatedEvent(ratingId, matchId, author, opponent, true, true, NOW));
    }

    @Test
    void lostCompletionRaceOrCompletedMatchOnlyAddsFiveStarBonus() {
        stub(context("ARCHIVED", true, false, "ACTIVE", false, false));
        when(matchRepository.completeIfArchived(matchId, NOW)).thenReturn(0);

        service.createRating(matchId, callerId, stars(5));

        verify(reputationService, never()).applyMatchCompletion(any(), anyList());
        verify(reputationService).adjustTeamReliability(opponent,
                new ReliabilityChange(1, ReliabilityReason.FIVE_STAR_RATING, ratingId, false));
        verify(publisher).publishEvent(new MatchRatedEvent(ratingId, matchId, author, opponent, false, false, NOW));
        assertThat(refreshedTeams()).containsExactly(opponent);
    }

    @Test
    void completedMatchWithFourStarsChangesNoScore() {
        stub(context("COMPLETED", true, false, "ACTIVE", false, false));

        service.createRating(matchId, callerId, stars(4));

        verify(matchRepository, never()).completeIfArchived(any(), any());
        verifyNoInteractions(reputationService);
        assertThat(refreshedTeams()).containsExactly(opponent);
    }

    @Test
    void savedRatingCarriesSidesAndCreator() {
        stub(context("COMPLETED", false, true, "ACTIVE", false, false));

        service.createRating(matchId, callerId, new CreateRatingRequest(false, 4, "Tốt", 3, 2));

        ArgumentCaptor<Rating> saved = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRaterId()).isEqualTo(opponent);
        assertThat(saved.getValue().getRateeId()).isEqualTo(author);
        assertThat(saved.getValue().getRaterType()).isEqualTo(Rating.RaterType.TEAM);
        assertThat(saved.getValue().getCreatedByUserId()).isEqualTo(callerId);
        assertThat(saved.getValue().getRaterTeamScore()).isEqualTo(3);
    }

    @Test
    void getMatchRatingsChecksMatchCaptainAndMembership() {
        when(ratingRepository.findMatchRatingRows(matchId)).thenReturn(List.of());
        assertCode(() -> service.getMatchRatings(matchId, author, callerId), ErrorCode.MATCH_001);

        when(ratingRepository.findMatchRatingRows(matchId)).thenReturn(List.of(row(null, null, null)));
        UUID stranger = UUID.randomUUID();
        assertCode(() -> service.getMatchRatings(matchId, stranger, callerId), ErrorCode.MATCH_009);
        verify(teamGuard).requireActiveCaptain(stranger, callerId);
    }

    @Test
    void receivedIsHiddenUntilOwnTeamHasRated() {
        UUID received = UUID.randomUUID();
        when(ratingRepository.findMatchRatingRows(matchId)).thenReturn(List.of(row(received, opponent, author)));

        MatchRatingsResponse hidden = service.getMatchRatings(matchId, author, callerId);
        assertThat(hidden.given()).isNull();
        assertThat(hidden.received()).isNull();

        UUID given = UUID.randomUUID();
        when(ratingRepository.findMatchRatingRows(matchId))
                .thenReturn(List.of(row(received, opponent, author), row(given, author, opponent)));
        MatchRatingsResponse visible = service.getMatchRatings(matchId, author, callerId);
        assertThat(visible.given().ratingId()).isEqualTo(given);
        assertThat(visible.received().ratingId()).isEqualTo(received);
        assertThat(visible.teamId()).isEqualTo(author);
    }

    private CreateRatingRequest stars(int score) {
        return new CreateRatingRequest(false, score, null, null, null);
    }

    private void stub(RatingContextView view) {
        when(matchRepository.findRatingContext(matchId, callerId)).thenReturn(Optional.of(view));
    }

    private RatingContextView context(String status, boolean authorCaptain, boolean opponentCaptain, String callerTeamStatus,
                                      boolean authorRated, boolean opponentRated) {
        Map<String, Object> values = new HashMap<>();
        values.put("matchId", matchId);
        values.put("status", status);
        values.put("authorTeamId", author);
        values.put("opponentTeamId", opponent);
        values.put("authorTeamStatus", authorCaptain ? callerTeamStatus : "ACTIVE");
        values.put("opponentTeamStatus", opponentCaptain ? callerTeamStatus : "ACTIVE");
        values.put("callerAuthorCaptain", authorCaptain);
        values.put("callerOpponentCaptain", opponentCaptain);
        values.put("authorRated", authorRated);
        values.put("opponentRated", opponentRated);
        return projections.createProjection(RatingContextView.class, values);
    }

    private MatchRatingRowView row(UUID id, UUID rater, UUID ratee) {
        Map<String, Object> values = new HashMap<>();
        values.put("authorTeamId", author);
        values.put("opponentTeamId", opponent);
        values.put("ratingId", id);
        values.put("raterTeamId", rater);
        values.put("rateeTeamId", ratee);
        values.put("noShow", id == null ? null : false);
        values.put("score", id == null ? null : 4);
        values.put("createdAt", id == null ? null : NOW);
        return projections.createProjection(MatchRatingRowView.class, values);
    }

    @SuppressWarnings("unchecked")
    private Collection<UUID> refreshedTeams() {
        ArgumentCaptor<Collection<UUID>> teams = ArgumentCaptor.forClass(Collection.class);
        verify(refresher).refreshAfterCommit(teams.capture());
        return teams.getValue();
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(code);
    }
}
