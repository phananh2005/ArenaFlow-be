package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.ConfirmPairingRequest;
import com.phananh.arenaflow.application.dto.request.CostCustomItemRequest;
import com.phananh.arenaflow.application.dto.request.CreatePairingRequest;
import com.phananh.arenaflow.application.dto.request.UpdatePairingCostAgreementRequest;
import com.phananh.arenaflow.application.dto.request.UpdateProposalRequest;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.PairingConfirmationResponse;
import com.phananh.arenaflow.application.dto.response.PairingCostUpdateResponse;
import com.phananh.arenaflow.application.dto.response.PairingResponse;
import com.phananh.arenaflow.application.dto.response.PairingSummaryResponse;
import com.phananh.arenaflow.application.dto.response.ProposalUpdateResponse;
import com.phananh.arenaflow.application.event.match.MatchConfirmedEvent;
import com.phananh.arenaflow.application.event.match.PairingCreatedEvent;
import com.phananh.arenaflow.application.mapper.PairingMapper;
import com.phananh.arenaflow.application.service.PairingRoleFilter;
import com.phananh.arenaflow.application.service.PairingService;
import com.phananh.arenaflow.application.service.PairingSide;
import com.phananh.arenaflow.application.service.PairingStatusFilter;
import com.phananh.arenaflow.application.service.VenueSelectionService;
import com.phananh.arenaflow.application.support.ConcurrencyConflictException;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.DataIntegrityViolations;
import com.phananh.arenaflow.application.support.MatchExpiry;
import com.phananh.arenaflow.application.support.MatchGuard;
import com.phananh.arenaflow.application.support.MatchWriteOutcome;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.application.support.chat.ChatRealtimeGateway;
import com.phananh.arenaflow.application.support.chat.PairingChange;
import com.phananh.arenaflow.application.support.chat.RealtimeFrame;
import com.phananh.arenaflow.application.support.match.PairingGuard;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.entity.match.CostCustomItem;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.match.Pairing;
import com.phananh.arenaflow.domain.projection.ConfirmSideResult;
import com.phananh.arenaflow.domain.projection.MatchAuthorAccessView;
import com.phananh.arenaflow.domain.projection.MatchAvailabilityView;
import com.phananh.arenaflow.domain.projection.PairingContextView;
import com.phananh.arenaflow.domain.projection.PairingDetailView;
import com.phananh.arenaflow.domain.projection.ProposalCandidateView;
import com.phananh.arenaflow.domain.projection.TeamCaptainContextView;
import com.phananh.arenaflow.domain.repository.ConversationRepository;
import com.phananh.arenaflow.domain.repository.CostCustomItemRepository;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import com.phananh.arenaflow.domain.repository.PairingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Thương lượng luồng B. Mỗi thao tác ghi là một lần thử của retrier (một transaction): đọc ngữ cảnh một câu → guard →
 * câu ghi CAS; điều kiện CAS không khớp → {@link ConcurrencyConflictException} → thử lại, lần sau guard trả mã đúng.
 * Kèo quá hạn → commit việc chuyển EXPIRED rồi mới báo MATCH_004 (ngoài retrier). Event và frame chỉ đi ra sau commit.
 */
@Service
@RequiredArgsConstructor
public class PairingServiceImpl implements PairingService {

    private static final String PAIRING_ACTIVE_INDEX = "idx_pairing_active";
    private static final String ACTIVE = "ACTIVE";
    private static final String DISBANDED = "DISBANDED";

    private final PairingRepository pairingRepository;
    private final MatchRepository matchRepository;
    private final CostCustomItemRepository costCustomItemRepository;
    private final ConversationRepository conversationRepository;
    private final MatchGuard matchGuard;
    private final TeamGuard teamGuard;
    private final PairingGuard pairingGuard;
    private final MatchExpiry matchExpiry;
    private final VenueSelectionService venueSelectionService;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final PairingMapper pairingMapper;
    private final PageRequestFactory pageRequestFactory;
    private final ChatRealtimeGateway realtimeGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    public PairingResponse createPairing(UUID matchId, UUID callerId, CreatePairingRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014,
                () -> createPairingAttempt(matchId, callerId, request.teamId())).valueOrThrowExpired();
    }

    private MatchWriteOutcome<PairingResponse> createPairingAttempt(UUID matchId, UUID callerId, UUID teamId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Match match = matchGuard.findMatch(matchId);
        TeamCaptainContextView team = teamGuard.requireActiveCaptain(teamId, callerId);
        if (teamId.equals(match.getAuthorTeamId())) {
            throw new BusinessException(ErrorCode.PAIRING_004);
        }
        if (match.getType() != Match.Type.NEED_VENUE) {
            throw new BusinessException(ErrorCode.MATCH_016);
        }
        if (matchGuard.isExpiryDue(match.getStatus(), match.getExpiresAt(), now)) {
            matchExpiry.expireIfDue(match.getId(), match.getAuthorTeamId(), match.getType(), now);
            return new MatchWriteOutcome.Expired<>();
        }
        matchGuard.requireOpen(match);
        MatchAvailabilityView availability = matchRepository.findAvailability(matchId, teamId);
        if (!ACTIVE.equals(availability.getAuthorTeamStatus())) {
            throw new BusinessException(ErrorCode.MATCH_013);
        }
        if (!Boolean.TRUE.equals(availability.getSportActive())) {
            throw new BusinessException(ErrorCode.SPORT_002);
        }
        Pairing pairing = Pairing.builder().matchId(matchId).interestedTeamId(teamId)
                .status(Pairing.Status.NEGOTIATING).build();
        try {
            // Flush tường minh để đổi vi phạm unique "mỗi đội một thương lượng đang hiệu lực" thành PAIRING_003.
            pairingRepository.saveAndFlush(pairing);
        } catch (DataIntegrityViolationException exception) {
            if (DataIntegrityViolations.violates(exception, PAIRING_ACTIVE_INDEX)) {
                throw new BusinessException(ErrorCode.PAIRING_003);
            }
            throw exception;
        }
        UUID conversationId = availability.getExistingConversationId();
        if (conversationId == null) {
            conversationId = conversationRepository.save(Conversation.builder()
                    .contextType(Conversation.ContextType.TEAM_MATCH).matchId(matchId)
                    .partyAType(Conversation.PartyType.TEAM).partyAId(match.getAuthorTeamId())
                    .partyBType(Conversation.PartyType.TEAM).partyBId(teamId)
                    .build()).getId();
        }
        matchRepository.forceIncrementVersion(match);
        eventPublisher.publishEvent(new PairingCreatedEvent(pairing.getId(), matchId, teamId, match.getAuthorTeamId(),
                now));
        return new MatchWriteOutcome.Done<>(pairingMapper.toCreatedResponse(pairing, match, team, availability,
                conversationId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PairingSummaryResponse> listMatchPairings(UUID matchId, UUID callerId,
                                                                  PairingStatusFilter status, int page, int pageSize) {
        MatchAuthorAccessView access = matchRepository.findAuthorAccess(matchId, callerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_001));
        if (!Boolean.TRUE.equals(access.getCallerIsCaptain())) {
            throw new BusinessException(DISBANDED.equals(access.getAuthorTeamStatus())
                    ? ErrorCode.TEAM_016 : ErrorCode.TEAM_002);
        }
        if (!Match.Type.NEED_VENUE.name().equals(access.getType())) {
            throw new BusinessException(ErrorCode.MATCH_016);
        }
        return PageResponse.from(pairingRepository.findByMatch(matchId, status.toQueryValue(), LocalDateTime.now(clock),
                pageRequestFactory.create(page, pageSize)), pairingMapper::toSummary, page, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PairingSummaryResponse> listTeamPairings(UUID teamId, UUID callerId, PairingRoleFilter role,
                                                                 PairingStatusFilter status, int page, int pageSize) {
        teamGuard.requireCaptainAllowInactive(teamId, callerId);
        return PageResponse.from(pairingRepository.findByTeam(teamId, role.toQueryValue(), status.toQueryValue(),
                LocalDateTime.now(clock), pageRequestFactory.create(page, pageSize)), pairingMapper::toSummary, page,
                pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PairingResponse getPairing(UUID pairingId, UUID callerId) {
        PairingDetailView view = pairingRepository.findDetailById(pairingId, callerId, LocalDateTime.now(clock))
                .orElseThrow(() -> new BusinessException(ErrorCode.PAIRING_001));
        if (!Boolean.TRUE.equals(view.getCallerAuthorCaptain())
                && !Boolean.TRUE.equals(view.getCallerInterestedCaptain())) {
            throw new BusinessException(ErrorCode.PAIRING_002);
        }
        return pairingMapper.toResponse(view, costCustomItemRepository.findByPairingIdOrderByDisplayOrderAsc(pairingId));
    }

    @Override
    public ProposalUpdateResponse updateProposal(UUID pairingId, UUID callerId, UpdateProposalRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014,
                () -> updateProposalAttempt(pairingId, callerId, request)).valueOrThrowExpired();
    }

    private MatchWriteOutcome<ProposalUpdateResponse> updateProposalAttempt(UUID pairingId, UUID callerId,
                                                                            UpdateProposalRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        PairingContextView ctx = findContext(pairingId, callerId);
        if (guardWrite(ctx, request.teamId(), request.revision(), now) == null) {
            return new MatchWriteOutcome.Expired<>();
        }
        ProposalCandidateView candidate = pairingRepository
                .findProposalCandidate(ctx.getMatchId(), request.venueId(), request.timeOptionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAIRING_008));
        pairingGuard.requireVenueActive(candidate.getVenueStatus());
        if (request.venueId().equals(ctx.getProposedVenueId())
                && request.timeOptionId().equals(ctx.getProposedTimeOptionId())) {
            return new MatchWriteOutcome.Done<>(pairingMapper.toProposalUpdate(ctx.getId(), ctx.getRevision(), candidate,
                    ctx.getAuthorConfirmedAt(), ctx.getInterestedConfirmedAt(), ctx.getUpdatedAt()));
        }
        UUID conversationId = requireConversation(ctx);
        if (pairingRepository.updateProposal(ctx.getId(), request.venueId(), request.timeOptionId(), request.revision(),
                now) == 0) {
            throw new ConcurrencyConflictException("pairing proposal CAS missed");
        }
        int revision = request.revision() + 1;
        publish(conversationId, ctx.getId(), Pairing.Status.NEGOTIATING, revision, null, null, null,
                PairingChange.PROPOSAL);
        return new MatchWriteOutcome.Done<>(pairingMapper.toProposalUpdate(ctx.getId(), revision, candidate, null, null,
                now));
    }

    @Override
    public PairingCostUpdateResponse updateCostAgreement(UUID pairingId, UUID callerId,
                                                         UpdatePairingCostAgreementRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014,
                () -> updateCostAttempt(pairingId, callerId, request)).valueOrThrowExpired();
    }

    private MatchWriteOutcome<PairingCostUpdateResponse> updateCostAttempt(UUID pairingId, UUID callerId,
                                                                           UpdatePairingCostAgreementRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        PairingContextView ctx = findContext(pairingId, callerId);
        if (guardWrite(ctx, request.teamId(), request.revision(), now) == null) {
            return new MatchWriteOutcome.Expired<>();
        }
        UUID conversationId = requireConversation(ctx);
        String depositHolder = MatchServiceImpl.trimToNull(request.depositHolder());
        if (pairingRepository.updateCost(ctx.getId(), request.costType(), request.costSplit(), request.venueAmount(),
                request.drinksAmount(), request.depositAmount(), depositHolder, request.revision(), now) == 0) {
            throw new ConcurrencyConflictException("pairing cost CAS missed");
        }
        costCustomItemRepository.deleteByPairingIdBulk(ctx.getId());
        List<CostCustomItem> items = List.of();
        if (!request.customItems().isEmpty()) {
            String[] names = request.customItems().stream().map(item -> item.itemName().trim()).toArray(String[]::new);
            Long[] amounts = request.customItems().stream().map(CostCustomItemRequest::amount).toArray(Long[]::new);
            costCustomItemRepository.insertAllForPairing(ctx.getId(), names, amounts, now);
            // Đọc lại để lấy itemId do DB sinh; Hibernate tự flush trước truy vấn (không có thay đổi chờ ở đây).
            items = costCustomItemRepository.findByPairingIdOrderByDisplayOrderAsc(ctx.getId());
        }
        int revision = request.revision() + 1;
        publish(conversationId, ctx.getId(), Pairing.Status.NEGOTIATING, revision, null, null, null, PairingChange.COST);
        return new MatchWriteOutcome.Done<>(pairingMapper.toCostUpdate(ctx.getId(), revision, request.costType(),
                request.costSplit(), request.venueAmount(), request.drinksAmount(), request.depositAmount(),
                depositHolder, items, now));
    }

    @Override
    public PairingConfirmationResponse confirm(UUID pairingId, UUID callerId, ConfirmPairingRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014,
                () -> confirmAttempt(pairingId, callerId, request)).valueOrThrowExpired();
    }

    private MatchWriteOutcome<PairingConfirmationResponse> confirmAttempt(UUID pairingId, UUID callerId,
                                                                          ConfirmPairingRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        PairingContextView ctx = findContext(pairingId, callerId);
        PairingSide side = guardWrite(ctx, request.teamId(), request.revision(), now);
        if (side == null) {
            return new MatchWriteOutcome.Expired<>();
        }
        if (ctx.getProposedVenueId() == null) {
            throw new BusinessException(ErrorCode.PAIRING_007);
        }
        pairingGuard.requireVenueActive(ctx.getProposedVenueStatus());
        boolean author = side == PairingSide.AUTHOR;
        if (!ACTIVE.equals(author ? ctx.getInterestedTeamStatus() : ctx.getAuthorTeamStatus())) {
            throw new BusinessException(ErrorCode.MATCH_013);
        }
        if (!Boolean.TRUE.equals(ctx.getSportActive())) {
            throw new BusinessException(ErrorCode.SPORT_002);
        }
        LocalDateTime mine = author ? ctx.getAuthorConfirmedAt() : ctx.getInterestedConfirmedAt();
        LocalDateTime other = author ? ctx.getInterestedConfirmedAt() : ctx.getAuthorConfirmedAt();
        if (mine != null) {
            return new MatchWriteOutcome.Done<>(pairingMapper.toConfirmation(ctx.getId(), Pairing.Status.NEGOTIATING,
                    ctx.getRevision(), ctx.getAuthorConfirmedAt(), ctx.getInterestedConfirmedAt(), null,
                    ctx.getMatchId(), Match.Status.valueOf(ctx.getMatchStatus())));
        }
        UUID conversationId = requireConversation(ctx);
        if (other != null) {
            return new MatchWriteOutcome.Done<>(complete(ctx, conversationId, now));
        }
        ConfirmSideResult result = pairingRepository.confirmSide(ctx.getId(), side.name(), ctx.getRevision(), now)
                .orElseThrow(() -> new ConcurrencyConflictException("pairing confirmation CAS missed"));
        if ((author ? result.getInterestedConfirmedAt() : result.getAuthorConfirmedAt()) != null) {
            // Bên kia vừa xác nhận và commit trước: không hoàn tất khi đang giữ khoá pairing (khoá kèo sau sẽ ngược
            // thứ tự kèo → pairing) — rollback để lần thử sau đi nhánh hoàn tất.
            throw new ConcurrencyConflictException("other side confirmed concurrently");
        }
        publish(conversationId, ctx.getId(), Pairing.Status.NEGOTIATING, ctx.getRevision(),
                result.getAuthorConfirmedAt(), result.getInterestedConfirmedAt(), null, PairingChange.CONFIRMATION);
        return new MatchWriteOutcome.Done<>(pairingMapper.toConfirmation(ctx.getId(), Pairing.Status.NEGOTIATING,
                ctx.getRevision(), result.getAuthorConfirmedAt(), result.getInterestedConfirmedAt(), null,
                ctx.getMatchId(), Match.Status.OPEN));
    }

    /** Ghép kèo: CAS kèo trước → pairing (xác nhận + thay thế) → chép khoản tự nhập → đánh dấu địa điểm được chọn. */
    private PairingConfirmationResponse complete(PairingContextView ctx, UUID conversationId, LocalDateTime now) {
        if (matchRepository.confirmPairing(ctx.getMatchId(), ctx.getMatchVersion(), ctx.getInterestedTeamId(),
                ctx.getProposedVenueId(), ctx.getProposedStartTime(), ctx.getProposedEndTime(), ctx.getCostType(),
                ctx.getCostSplit(), ctx.getCostVenueAmount(), ctx.getCostDrinksAmount(), ctx.getCostDepositAmount(),
                ctx.getCostDepositHolder(), now) == 0) {
            throw new ConcurrencyConflictException("match CAS missed");
        }
        if (!pairingRepository.finalizeConfirmation(ctx.getMatchId(), ctx.getId(), ctx.getRevision(), now)
                .contains(ctx.getId())) {
            throw new ConcurrencyConflictException("pairing package changed before completion");
        }
        costCustomItemRepository.copyPairingItemsToMatch(ctx.getId(), ctx.getMatchId(), now);
        venueSelectionService.markSelected(List.of(ctx.getProposedVenueId()), now);
        eventPublisher.publishEvent(new MatchConfirmedEvent(ctx.getMatchId(), null, ctx.getId(), ctx.getAuthorTeamId(),
                ctx.getInterestedTeamId(), now));
        LocalDateTime authorAt = ctx.getAuthorConfirmedAt() != null ? ctx.getAuthorConfirmedAt() : now;
        LocalDateTime interestedAt = ctx.getInterestedConfirmedAt() != null ? ctx.getInterestedConfirmedAt() : now;
        publish(conversationId, ctx.getId(), Pairing.Status.CONFIRMED, ctx.getRevision(), authorAt, interestedAt, now,
                PairingChange.CONFIRMATION);
        return pairingMapper.toConfirmation(ctx.getId(), Pairing.Status.CONFIRMED, ctx.getRevision(), authorAt,
                interestedAt, now, ctx.getMatchId(), Match.Status.CONFIRMED);
    }

    private PairingContextView findContext(UUID pairingId, UUID callerId) {
        return pairingRepository.findContext(pairingId, callerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAIRING_001));
    }

    /**
     * Thứ tự guard chung của thao tác ghi: phía → đội (trạng thái rồi quyền) → hết hạn → trạng thái thương lượng →
     * revision. Trả {@code null} khi chính lần thử này vừa chuyển kèo sang EXPIRED (caller trả {@code Expired}).
     */
    private PairingSide guardWrite(PairingContextView ctx, UUID teamId, int revision, LocalDateTime now) {
        PairingSide side = pairingGuard.requireSide(ctx, teamId);
        pairingGuard.requireCaptain(ctx, side, false);
        if (matchGuard.isExpiryDue(Match.Status.valueOf(ctx.getMatchStatus()), ctx.getExpiresAt(), now)) {
            matchExpiry.expireIfDue(ctx.getMatchId(), ctx.getAuthorTeamId(), Match.Type.valueOf(ctx.getMatchType()),
                    now);
            return null;
        }
        pairingGuard.requireNegotiating(ctx);
        pairingGuard.requireRevision(ctx, revision);
        return side;
    }

    /** Cuộc trò chuyện được tạo cùng transaction với pairing → luôn có; thiếu = bất biến bị phá. */
    private static UUID requireConversation(PairingContextView ctx) {
        if (ctx.getConversationId() == null) {
            throw new IllegalStateException("Pairing " + ctx.getId() + " has no TEAM_MATCH conversation");
        }
        return ctx.getConversationId();
    }

    private void publish(UUID conversationId, UUID pairingId, Pairing.Status status, int revision,
                         LocalDateTime authorConfirmedAt, LocalDateTime interestedConfirmedAt,
                         LocalDateTime confirmedAt, PairingChange change) {
        realtimeGateway.publishAfterCommit(conversationId, new RealtimeFrame.PairingUpdated(pairingId, status, revision,
                authorConfirmedAt, interestedConfirmedAt, confirmedAt, change));
    }
}
