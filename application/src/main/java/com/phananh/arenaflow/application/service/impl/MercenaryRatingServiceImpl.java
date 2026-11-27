package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.CreateMercenaryRatingRequest;
import com.phananh.arenaflow.application.dto.response.MercenaryRatingResponse;
import com.phananh.arenaflow.application.dto.response.MercenaryRatingsResponse;
import com.phananh.arenaflow.application.event.mercenary.MercenaryRatingCreatedEvent;
import com.phananh.arenaflow.application.mapper.MercenaryReputationMapper;
import com.phananh.arenaflow.application.service.MercenaryRatingService;
import com.phananh.arenaflow.application.service.ReputationService;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.DataIntegrityViolations;
import com.phananh.arenaflow.application.support.mercenary.MercenaryGuard;
import com.phananh.arenaflow.application.support.mercenary.MercenarySide;
import com.phananh.arenaflow.application.support.reputation.ReputationRules;
import com.phananh.arenaflow.application.support.reputation.VerificationRefresher;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.rating.Rating;
import com.phananh.arenaflow.domain.projection.MercenaryRatingContextView;
import com.phananh.arenaflow.domain.repository.RatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MercenaryRatingServiceImpl implements MercenaryRatingService {

    private static final String RATING_INDEX = "idx_rating_mercenary_unique";
    private static final int FIVE_STARS = 5;

    private final RatingRepository ratingRepository;
    private final MercenaryGuard mercenaryGuard;
    private final ReputationService reputationService;
    private final VerificationRefresher verificationRefresher;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final MercenaryReputationMapper reputationMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Đánh giá theo đơn. Thứ tự kiểm: USER_001 → MERCENARY_002 → MERCENARY_019 → MERCENARY_014 → (phía đội trưởng)
     * TEAM_015 → MERCENARY_015 (trận chưa kết thúc / đơn không APPROVED) → MERCENARY_016 ("Không đến" ở phía mercenary)
     * → MERCENARY_017 (đã đánh giá; gửi đồng thời → unique index). Đội trưởng chấm sao = mercenary hoàn thành trận (một
     * câu ghi điểm) + refresh huy hiệu sau commit (4 truy vấn); "Không đến" / phía mercenary: 2 truy vấn, không ghi điểm.
     * Không chuyển trạng thái trận.
     */
    @Override
    public MercenaryRatingResponse createRating(UUID applicationId, UUID callerId,
                                                CreateMercenaryRatingRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            MercenaryRatingContextView ctx = context(applicationId, callerId, now);
            mercenaryGuard.requireNotDeleted(Boolean.TRUE.equals(ctx.getCallerDeleted()));
            MercenarySide side = resolveSide(ctx);
            if (!Boolean.TRUE.equals(ctx.getGameEnded()) || !"APPROVED".equals(ctx.getApplicationStatus())) {
                throw new BusinessException(ErrorCode.MERCENARY_015);
            }
            boolean noShow = Boolean.TRUE.equals(request.isNoShow());
            if (side == MercenarySide.APPLICANT && noShow) {
                throw new BusinessException(ErrorCode.MERCENARY_016);
            }
            boolean captain = side == MercenarySide.CAPTAIN;
            if ((captain ? ctx.getTeamRatingId() : ctx.getMercenaryRatingId()) != null) {
                throw new BusinessException(ErrorCode.MERCENARY_017);
            }
            Rating rating = save(Rating.builder()
                    .matchId(ctx.getMatchId())
                    .externalMatchId(ctx.getExternalMatchId())
                    .applicationId(applicationId)
                    .raterType(captain ? Rating.RaterType.TEAM : Rating.RaterType.INDIVIDUAL)
                    .raterId(captain ? ctx.getTeamId() : ctx.getApplicantId())
                    .rateeType(captain ? Rating.RateeType.INDIVIDUAL : Rating.RateeType.TEAM)
                    .rateeId(captain ? ctx.getApplicantId() : ctx.getTeamId())
                    .isNoShow(noShow)
                    .score(request.score())
                    .comment(request.comment())
                    .createdByUserId(callerId)
                    .build());
            if (captain && !noShow) {
                int bonus = request.score() == FIVE_STARS ? ReputationRules.FIVE_STAR_BONUS : 0;
                reputationService.applyMercenaryCompletion(ctx.getApplicantId(), ctx.getMatchId(),
                        ctx.getExternalMatchId(), bonus, rating.getId());
                verificationRefresher.refreshUsersAfterCommit(Set.of(ctx.getApplicantId()));
            }
            MercenaryRatingResponse response = reputationMapper.toRating(rating);
            eventPublisher.publishEvent(new MercenaryRatingCreatedEvent(rating.getId(), applicationId,
                    response.direction(), now));
            return response;
        });
    }

    /**
     * Đánh giá của đơn theo góc nhìn người gọi (1 truy vấn): MERCENARY_002 → MERCENARY_019 → MERCENARY_014 → (phía đội
     * trưởng) TEAM_015. Đánh giá phía kia chỉ hiện sau khi người gọi đã đánh giá.
     */
    @Override
    @Transactional(readOnly = true)
    public MercenaryRatingsResponse getRatings(UUID applicationId, UUID callerId) {
        MercenaryRatingContextView ctx = context(applicationId, callerId, LocalDateTime.now(clock));
        MercenarySide side = resolveSide(ctx);
        MercenaryRatingResponse teamRating = ctx.getTeamRatingId() == null ? null : reputationMapper.toRating(
                ctx.getTeamRatingId(), applicationId, ctx.getMatchId(), ctx.getExternalMatchId(), true, ctx.getTeamId(),
                ctx.getApplicantId(), Boolean.TRUE.equals(ctx.getTeamRatingNoShow()), ctx.getTeamRatingScore(),
                ctx.getTeamRatingComment(), ctx.getTeamRatingCreatedAt());
        MercenaryRatingResponse mercenaryRating = ctx.getMercenaryRatingId() == null ? null : reputationMapper.toRating(
                ctx.getMercenaryRatingId(), applicationId, ctx.getMatchId(), ctx.getExternalMatchId(), false,
                ctx.getTeamId(), ctx.getApplicantId(), false, ctx.getMercenaryRatingScore(),
                ctx.getMercenaryRatingComment(), ctx.getMercenaryRatingCreatedAt());
        boolean captain = side == MercenarySide.CAPTAIN;
        MercenaryRatingResponse given = captain ? teamRating : mercenaryRating;
        MercenaryRatingResponse received = given == null ? null : captain ? mercenaryRating : teamRating;
        return new MercenaryRatingsResponse(applicationId, captain ? "TEAM" : "MERCENARY", given, received);
    }

    private MercenaryRatingContextView context(UUID applicationId, UUID callerId, LocalDateTime now) {
        return ratingRepository.findMercenaryRatingContext(applicationId, callerId, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
    }

    /** Đơn không có → MERCENARY_002; phía người gọi; đội trưởng của đội tạm ngưng → TEAM_015. */
    private MercenarySide resolveSide(MercenaryRatingContextView ctx) {
        if (ctx.getApplicationId() == null) {
            throw new BusinessException(ErrorCode.MERCENARY_002);
        }
        MercenarySide side = mercenaryGuard.resolveSide(Boolean.TRUE.equals(ctx.getCallerApplicant()),
                Boolean.TRUE.equals(ctx.getCallerCaptain()));
        if (side == MercenarySide.CAPTAIN && "INACTIVE".equals(ctx.getTeamStatus())) {
            throw new BusinessException(ErrorCode.TEAM_015);
        }
        return side;
    }

    /** Flush ngay để đổi vi phạm unique (đã đánh giá) thành MERCENARY_017 trước khi ghi điểm. */
    private Rating save(Rating rating) {
        try {
            return ratingRepository.saveAndFlush(rating);
        } catch (DataIntegrityViolationException exception) {
            if (DataIntegrityViolations.violates(exception, RATING_INDEX)) {
                throw new BusinessException(ErrorCode.MERCENARY_017);
            }
            throw exception;
        }
    }
}
