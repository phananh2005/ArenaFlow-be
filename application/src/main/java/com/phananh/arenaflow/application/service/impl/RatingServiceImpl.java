package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.CreateRatingRequest;
import com.phananh.arenaflow.application.dto.response.MatchRatingsResponse;
import com.phananh.arenaflow.application.dto.response.RatingCreatedResponse;
import com.phananh.arenaflow.application.dto.response.RatingResponse;
import com.phananh.arenaflow.application.event.reputation.MatchRatedEvent;
import com.phananh.arenaflow.application.mapper.ReputationMapper;
import com.phananh.arenaflow.application.service.CompletionEntry;
import com.phananh.arenaflow.application.service.RatingService;
import com.phananh.arenaflow.application.service.ReliabilityChange;
import com.phananh.arenaflow.application.service.ReliabilityReason;
import com.phananh.arenaflow.application.service.ReputationService;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.DataIntegrityViolations;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.application.support.reputation.MatchSides;
import com.phananh.arenaflow.application.support.reputation.ReputationRules;
import com.phananh.arenaflow.application.support.reputation.TeamVerificationRefresher;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.rating.Rating;
import com.phananh.arenaflow.domain.projection.MatchRatingRowView;
import com.phananh.arenaflow.domain.projection.RatingContextView;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import com.phananh.arenaflow.domain.repository.RatingRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RatingServiceImpl implements RatingService {

    private static final String RATING_UNIQUE_INDEX = "idx_rating_unique";
    private static final int FIVE_STARS = 5;

    private final MatchRepository matchRepository;
    private final RatingRepository ratingRepository;
    private final TeamGuard teamGuard;
    private final ReputationService reputationService;
    private final TeamVerificationRefresher verificationRefresher;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final ReputationMapper reputationMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RatingServiceImpl(MatchRepository matchRepository, RatingRepository ratingRepository, TeamGuard teamGuard,
                             ReputationService reputationService, TeamVerificationRefresher verificationRefresher,
                             ConcurrencyConflictRetrier concurrencyConflictRetrier, ReputationMapper reputationMapper,
                             ApplicationEventPublisher eventPublisher, Clock clock) {
        this.matchRepository = matchRepository;
        this.ratingRepository = ratingRepository;
        this.teamGuard = teamGuard;
        this.reputationService = reputationService;
        this.verificationRefresher = verificationRefresher;
        this.concurrencyConflictRetrier = concurrencyConflictRetrier;
        this.reputationMapper = reputationMapper;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Q1 ngữ cảnh, Q2 INSERT đánh giá, (Q3 ARCHIVED → COMPLETED), (Q4 điểm hai đội trong một câu hoặc +1 khi 5 sao),
     * sau commit Q5 refresh huy hiệu.
     */
    @Override
    public RatingCreatedResponse createRating(UUID matchId, UUID callerId, CreateRatingRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            RatingContextView ctx = matchRepository.findRatingContext(matchId, callerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_001));
            MatchSides sides = guardRating(ctx);
            LocalDateTime now = LocalDateTime.now(clock);
            boolean noShow = Boolean.TRUE.equals(request.isNoShow());
            Rating rating = saveRating(Rating.builder()
                    .matchId(matchId)
                    .raterType(Rating.RaterType.TEAM).raterId(sides.ownTeamId())
                    .rateeType(Rating.RateeType.TEAM).rateeId(sides.otherTeamId())
                    .noShow(noShow)
                    .score(request.score())
                    .comment(request.comment())
                    .raterTeamScore(request.raterTeamScore())
                    .rateeTeamScore(request.rateeTeamScore())
                    .createdByUserId(callerId)
                    .build());
            boolean fiveStars = !noShow && request.score() == FIVE_STARS;
            // 0 dòng = đội kia vừa hoàn tất trận trước → chỉ còn thưởng 5 sao như trận đã COMPLETED.
            boolean completed = Match.Status.ARCHIVED.name().equals(ctx.getStatus())
                    && matchRepository.completeIfArchived(matchId, now) == 1;
            if (completed) {
                List<CompletionEntry> entries = new ArrayList<>();
                entries.add(new CompletionEntry(sides.ownTeamId(), true, 0));
                // Đội bị đánh giá "không đến": điểm và chuỗi trận sạch giữ nguyên.
                if (!noShow) {
                    entries.add(new CompletionEntry(sides.otherTeamId(), true,
                            fiveStars ? ReputationRules.FIVE_STAR_BONUS : 0));
                }
                reputationService.applyMatchCompletion(matchId, entries);
            } else if (fiveStars) {
                reputationService.adjustTeamReliability(sides.otherTeamId(), new ReliabilityChange(
                        ReputationRules.FIVE_STAR_BONUS, ReliabilityReason.FIVE_STAR_RATING, rating.getId(), false));
            }
            eventPublisher.publishEvent(new MatchRatedEvent(rating.getId(), matchId, sides.ownTeamId(),
                    sides.otherTeamId(), noShow, completed, now));
            verificationRefresher.refreshAfterCommit(completed
                    ? List.of(sides.otherTeamId(), sides.ownTeamId())
                    : List.of(sides.otherTeamId()));
            return reputationMapper.toCreated(rating, Match.Status.COMPLETED.name());
        });
    }

    /** Q1 trận + đánh giá, Q2 quyền đội trưởng. */
    @Override
    @Transactional(readOnly = true)
    public MatchRatingsResponse getMatchRatings(UUID matchId, UUID teamId, UUID callerId) {
        List<MatchRatingRowView> rows = ratingRepository.findMatchRatingRows(matchId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.MATCH_001);
        }
        teamGuard.requireActiveCaptain(teamId, callerId);
        MatchRatingRowView match = rows.get(0);
        if (!teamId.equals(match.getAuthorTeamId()) && !teamId.equals(match.getOpponentTeamId())) {
            throw new BusinessException(ErrorCode.MATCH_009);
        }
        RatingResponse given = find(rows, matchId, teamId, true);
        // Chỉ lộ đánh giá đối phương sau khi đội mình đã đánh giá.
        RatingResponse received = given == null ? null : find(rows, matchId, teamId, false);
        return new MatchRatingsResponse(matchId, teamId, given, received);
    }

    /** Thứ tự kiểm: TEAM_002 → TEAM_015 (đội phía người gọi tạm ngưng) → RATING_003 → RATING_001 → RATING_002. */
    private static MatchSides guardRating(RatingContextView ctx) {
        if (!MatchSides.isCaptainOfAny(ctx)) {
            throw new BusinessException(ErrorCode.TEAM_002);
        }
        if (MatchSides.isCallerTeamInactive(ctx)) {
            throw new BusinessException(ErrorCode.TEAM_015);
        }
        if (MatchSides.isCaptainOfBoth(ctx)) {
            throw new BusinessException(ErrorCode.RATING_003);
        }
        if (!Match.Status.ARCHIVED.name().equals(ctx.getStatus()) && !Match.Status.COMPLETED.name().equals(ctx.getStatus())) {
            throw new BusinessException(ErrorCode.RATING_001);
        }
        MatchSides sides = MatchSides.of(ctx);
        Boolean alreadyRated = sides.ownIsAuthor() ? ctx.getAuthorRated() : ctx.getOpponentRated();
        if (Boolean.TRUE.equals(alreadyRated)) {
            throw new BusinessException(ErrorCode.RATING_002);
        }
        return sides;
    }

    private Rating saveRating(Rating rating) {
        try {
            // Flush ngay để bắt vi phạm index chống trùng (hai lần gửi đồng thời) thành RATING_002.
            return ratingRepository.saveAndFlush(rating);
        } catch (DataIntegrityViolationException exception) {
            if (DataIntegrityViolations.violates(exception, RATING_UNIQUE_INDEX)) {
                throw new BusinessException(ErrorCode.RATING_002);
            }
            throw exception;
        }
    }

    private RatingResponse find(List<MatchRatingRowView> rows, UUID matchId, UUID teamId, boolean given) {
        return rows.stream()
                .filter(row -> row.getRatingId() != null)
                .filter(row -> teamId.equals(given ? row.getRaterTeamId() : row.getRateeTeamId()))
                .findFirst()
                .map(row -> reputationMapper.toRating(row, matchId))
                .orElse(null);
    }
}
