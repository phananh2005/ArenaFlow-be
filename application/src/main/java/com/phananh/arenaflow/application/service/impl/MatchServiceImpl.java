package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.CancelMatchRequest;
import com.phananh.arenaflow.application.dto.request.CostCustomItemRequest;
import com.phananh.arenaflow.application.dto.request.CreateMatchRequest;
import com.phananh.arenaflow.application.dto.request.SearchMatchRequest;
import com.phananh.arenaflow.application.dto.request.UpdateCostAgreementRequest;
import com.phananh.arenaflow.application.dto.response.CostAgreementResponse;
import com.phananh.arenaflow.application.dto.response.MatchCancellationResponse;
import com.phananh.arenaflow.application.dto.response.MatchResponse;
import com.phananh.arenaflow.application.dto.response.MatchSummaryResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.event.match.MatchCancelledEvent;
import com.phananh.arenaflow.application.mapper.MatchMapper;
import com.phananh.arenaflow.application.service.MatchRoleFilter;
import com.phananh.arenaflow.application.service.MatchService;
import com.phananh.arenaflow.application.service.MatchStatusFilter;
import com.phananh.arenaflow.application.service.VenueSelectionService;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.GeoBoundingBox;
import com.phananh.arenaflow.application.support.MatchCancellation;
import com.phananh.arenaflow.application.support.MatchExpiry;
import com.phananh.arenaflow.application.support.MatchGuard;
import com.phananh.arenaflow.application.support.MatchWriteOutcome;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.match.CostCustomItem;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.match.MatchRequest;
import com.phananh.arenaflow.domain.entity.match.Sport;
import com.phananh.arenaflow.domain.projection.MatchDetailView;
import com.phananh.arenaflow.domain.projection.MatchSummaryView;
import com.phananh.arenaflow.domain.repository.CostCustomItemRepository;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import com.phananh.arenaflow.domain.repository.MatchRequestRepository;
import com.phananh.arenaflow.domain.repository.SportRepository;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Không có {@code @Transactional} mức lớp: method đọc readOnly, method ghi tự mở transaction hoặc qua retrier. */
@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchRepository matchRepository;
    private final CostCustomItemRepository costCustomItemRepository;
    private final SportRepository sportRepository;
    private final TeamRepository teamRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final MatchGuard matchGuard;
    private final TeamGuard teamGuard;
    private final MatchExpiry matchExpiry;
    private final MatchCancellation matchCancellation;
    private final VenueSelectionService venueSelectionService;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final MatchMapper matchMapper;
    private final PageRequestFactory pageRequestFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    @Transactional
    public MatchResponse createMatch(UUID callerId, CreateMatchRequest request) {
        if (CreateMatchRequest.NEED_VENUE.equals(request.type())) {
            throw new BusinessException(ErrorCode.MATCH_016);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        teamGuard.requireActiveCaptain(request.teamId(), callerId);
        requireActiveSport(request.sportId());
        List<UUID> venueIds = List.of(request.venueId());
        venueSelectionService.requireSelectable(venueIds);

        Match match = matchRepository.save(Match.builder()
                .type(Match.Type.HAS_VENUE)
                .status(Match.Status.OPEN)
                .authorTeamId(request.teamId())
                .sportId(request.sportId())
                .format(Match.Format.valueOf(request.format()))
                .skillLevels(request.skillLevels() == null ? List.of() : request.skillLevels())
                .ageNote(trimToNull(request.ageNote()))
                .matchNature(trimToNull(request.matchNature()))
                .venueId(request.venueId())
                .matchStartTime(request.startsAt())
                .matchEndTime(request.endsAt())
                .expiresAt(request.expiresAt())
                .build());
        // Câu bulk native: Hibernate tự flush INSERT kèo trước khi chạy.
        venueSelectionService.markSelected(venueIds, now);
        MatchDetailView view = matchRepository.findDetailById(match.getId(), now)
                .orElseThrow(() -> new IllegalStateException("Match just created is missing: " + match.getId()));
        return matchMapper.toResponse(view, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MatchSummaryResponse> searchMatches(SearchMatchRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        Pageable pageable = pageRequestFactory.create(request.page(), request.pageSize());
        Page<MatchSummaryView> matches;
        if (request.lat() == null) {
            matches = matchRepository.searchOpen(request.type(), request.sportId(), request.format(),
                    request.skillLevel(), request.startsAtFrom(), request.startsAtTo(), now, pageable);
        } else {
            double lat = request.lat().doubleValue();
            double lng = request.lng().doubleValue();
            double radiusKm = request.radiusKm().doubleValue();
            GeoBoundingBox box = GeoBoundingBox.around(lat, lng, radiusKm);
            matches = matchRepository.searchOpenNearby(request.type(), request.sportId(), request.format(),
                    request.skillLevel(), request.startsAtFrom(), request.startsAtTo(), now, lat, lng, radiusKm,
                    box.minLat(), box.maxLat(), box.minLng(), box.maxLng(), pageable);
        }
        return PageResponse.from(matches, matchMapper::toSummary, request.page(), request.pageSize());
    }

    @Override
    @Transactional(readOnly = true)
    public MatchResponse getMatch(UUID matchId) {
        MatchDetailView view = matchRepository.findDetailById(matchId, LocalDateTime.now(clock))
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_001));
        return matchMapper.toResponse(view, loadCustomItems(view));
    }

    @Override
    public CostAgreementResponse updateCostAgreement(UUID matchId, UUID callerId, UpdateCostAgreementRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            Match match = matchGuard.findMatch(matchId);
            teamGuard.requireActiveCaptain(match.getAuthorTeamId(), callerId);
            if (match.getType() == Match.Type.NEED_VENUE) {
                throw new BusinessException(ErrorCode.MATCH_016);
            }
            if (matchGuard.isExpiryDue(match.getStatus(), match.getExpiresAt(), now)) {
                matchExpiry.expireIfDue(matchId, match.getAuthorTeamId(), match.getType(), now);
                return MatchWriteOutcome.<CostAgreementResponse>expired();
            }
            matchGuard.requireOpen(match);

            match.setCostType(request.costType() == null ? null : Match.CostType.valueOf(request.costType()));
            match.setCostSplit(request.costSplit() == null ? null : Match.CostSplit.valueOf(request.costSplit()));
            match.setCostVenueAmount(request.venueAmount());
            match.setCostDrinksAmount(request.drinksAmount());
            // Gán để entity luôn dirty: UPDATE kèo (CAS version) chạy cả khi chỉ đổi khoản tự nhập.
            match.setUpdatedAt(now);
            // Câu native: Hibernate tự flush UPDATE kèo (CAS) trước khi xoá khoản cũ.
            costCustomItemRepository.deleteByMatchIdBulk(matchId);
            List<CostCustomItem> items = List.of();
            if (!request.customItems().isEmpty()) {
                String[] names = request.customItems().stream().map(item -> item.itemName().trim()).toArray(String[]::new);
                Long[] amounts = request.customItems().stream().map(CostCustomItemRequest::amount).toArray(Long[]::new);
                costCustomItemRepository.insertAll(matchId, names, amounts, now);
                items = costCustomItemRepository.findByMatchIdOrderByDisplayOrderAsc(matchId);
            }
            return MatchWriteOutcome.done(matchMapper.toCostAgreement(match, items));
        }).valueOrThrowExpired();
    }

    @Override
    public MatchCancellationResponse cancelMatch(UUID matchId, UUID callerId, CancelMatchRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            Match match = matchGuard.findMatch(matchId);
            teamGuard.requireCaptainAllowInactive(request.teamId(), callerId);
            boolean byAuthor = request.teamId().equals(match.getAuthorTeamId());
            if (!byAuthor && !request.teamId().equals(match.getConfirmedOpponentTeamId())) {
                throw new BusinessException(ErrorCode.MATCH_009);
            }
            if (matchGuard.isExpiryDue(match.getStatus(), match.getExpiresAt(), now)) {
                matchExpiry.expireIfDue(matchId, match.getAuthorTeamId(), match.getType(), now);
                return MatchWriteOutcome.<MatchCancellationResponse>expired();
            }
            matchGuard.requireNotExpired(match.getStatus());
            if (match.getStatus() != Match.Status.OPEN && match.getStatus() != Match.Status.CONFIRMED) {
                throw new BusinessException(ErrorCode.MATCH_005);
            }
            // So với trạng thái client đang hiển thị (không với lần đọc đầu): màn hình có thể cũ trước khi request tới.
            if (!match.getStatus().name().equals(request.expectedStatus())) {
                throw new BusinessException(ErrorCode.MATCH_015);
            }
            String reason = trimToNull(request.reason());
            if (match.getStatus() == Match.Status.OPEN) {
                return MatchWriteOutcome.done(cancelOpen(match, request.teamId(), reason, now));
            }
            if (reason == null) {
                throw new BusinessException(ErrorCode.MATCH_011);
            }
            if (!now.isBefore(match.getMatchStartTime())) {
                throw new BusinessException(ErrorCode.MATCH_012);
            }
            int penalty = matchCancellation.cancelConfirmed(match, request.teamId(), reason,
                    MatchCancelledEvent.Cause.CANCEL, now).reliabilityPenalty();
            return MatchWriteOutcome.done(matchMapper.toCancellation(match, penalty));
        }).valueOrThrowExpired();
    }

    /** Huỷ kèo OPEN (chỉ đội tạo kèo): kèo luồng A còn yêu cầu PENDING → MATCH_010; không trừ điểm; CAS lúc commit. */
    private MatchCancellationResponse cancelOpen(Match match, UUID teamId, String reason, LocalDateTime now) {
        if (match.getType() == Match.Type.HAS_VENUE
                && matchRequestRepository.existsByMatchIdAndStatus(match.getId(), MatchRequest.Status.PENDING)) {
            throw new BusinessException(ErrorCode.MATCH_010);
        }
        match.setStatus(Match.Status.CANCELLED);
        match.setCancelledByTeamId(teamId);
        match.setCancelledAt(now);
        match.setCancelReason(reason);
        eventPublisher.publishEvent(new MatchCancelledEvent(match.getId(), teamId, null, Match.Status.OPEN,
                MatchCancelledEvent.Cause.CANCEL, null, now));
        return matchMapper.toCancellation(match, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MatchSummaryResponse> listTeamMatches(UUID teamId, MatchStatusFilter status,
                                                              MatchRoleFilter role, int page, int pageSize) {
        if (!teamRepository.existsById(teamId)) {
            throw new BusinessException(ErrorCode.TEAM_001);
        }
        Page<MatchSummaryView> matches = matchRepository.findTeamMatches(teamId, role.toQueryValue(),
                status.toQueryValue(), LocalDateTime.now(clock), pageRequestFactory.create(page, pageSize));
        return PageResponse.from(matches, matchMapper::toSummary, page, pageSize);
    }

    /** Khoản tự nhập có ở kèo luồng A, và kèo luồng B đã ghép (được chép khi ghép). */
    private List<CostCustomItem> loadCustomItems(MatchDetailView view) {
        boolean hasItems = Match.Type.HAS_VENUE.name().equals(view.getType()) || view.getConfirmedPairingId() != null;
        return hasItems ? costCustomItemRepository.findByMatchIdOrderByDisplayOrderAsc(view.getMatchId()) : List.of();
    }

    private void requireActiveSport(UUID sportId) {
        Sport sport = sportRepository.findById(sportId).orElseThrow(() -> new BusinessException(ErrorCode.SPORT_001));
        if (!Boolean.TRUE.equals(sport.getIsActive())) {
            throw new BusinessException(ErrorCode.SPORT_002);
        }
    }

    /** Chuỗi tuỳ chọn: bỏ khoảng trắng đầu/cuối, rỗng → null. */
    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
