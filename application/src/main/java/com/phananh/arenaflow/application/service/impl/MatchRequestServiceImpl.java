package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.RejectMatchRequestRequest;
import com.phananh.arenaflow.application.dto.request.SendMatchRequestRequest;
import com.phananh.arenaflow.application.dto.request.WithdrawMatchRequestRequest;
import com.phananh.arenaflow.application.dto.response.AcceptMatchRequestResponse;
import com.phananh.arenaflow.application.dto.response.MatchRequestResponse;
import com.phananh.arenaflow.application.dto.response.MatchRequestWithdrawalResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.event.match.MatchCancelledEvent;
import com.phananh.arenaflow.application.event.match.MatchConfirmedEvent;
import com.phananh.arenaflow.application.event.match.MatchRequestRejectedEvent;
import com.phananh.arenaflow.application.event.match.MatchRequestSentEvent;
import com.phananh.arenaflow.application.event.match.MatchRequestWithdrawnEvent;
import com.phananh.arenaflow.application.mapper.MatchMapper;
import com.phananh.arenaflow.application.service.MatchRequestService;
import com.phananh.arenaflow.application.service.MatchRequestStatusFilter;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.DataIntegrityViolations;
import com.phananh.arenaflow.application.support.MatchCancellation;
import com.phananh.arenaflow.application.support.MatchExpiry;
import com.phananh.arenaflow.application.support.MatchGuard;
import com.phananh.arenaflow.application.support.MatchWriteOutcome;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.match.MatchRequest;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.projection.MatchAuthorAccessView;
import com.phananh.arenaflow.domain.projection.MatchAvailabilityView;
import com.phananh.arenaflow.domain.projection.MatchRequestContextView;
import com.phananh.arenaflow.domain.projection.TeamCaptainContextView;
import com.phananh.arenaflow.domain.repository.ConversationRepository;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import com.phananh.arenaflow.domain.repository.MatchRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchRequestServiceImpl implements MatchRequestService {

    private static final String ACTIVE_REQUEST_INDEX = "idx_match_request_active";
    private static final String ACCEPTED_REQUEST_INDEX = "idx_match_request_accepted";

    private final MatchRepository matchRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final ConversationRepository conversationRepository;
    private final MatchGuard matchGuard;
    private final TeamGuard teamGuard;
    private final MatchExpiry matchExpiry;
    private final MatchCancellation matchCancellation;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final MatchMapper matchMapper;
    private final PageRequestFactory pageRequestFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    public MatchRequestResponse sendRequest(UUID matchId, UUID callerId, SendMatchRequestRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            Match match = matchGuard.findMatch(matchId);
            TeamCaptainContextView requester = teamGuard.requireActiveCaptain(request.teamId(), callerId);
            if (request.teamId().equals(match.getAuthorTeamId())) {
                throw new BusinessException(ErrorCode.MATCH_007);
            }
            if (match.getType() == Match.Type.NEED_VENUE) {
                throw new BusinessException(ErrorCode.MATCH_016);
            }
            if (matchGuard.isExpiryDue(match.getStatus(), match.getExpiresAt(), now)) {
                matchExpiry.expireIfDue(matchId, match.getAuthorTeamId(), match.getType(), now);
                return MatchWriteOutcome.<MatchRequestResponse>expired();
            }
            matchGuard.requireOpen(match);
            MatchAvailabilityView availability = matchRepository.findAvailability(matchId, request.teamId());
            if (!Team.TeamStatus.ACTIVE.name().equals(availability.getAuthorTeamStatus())) {
                throw new BusinessException(ErrorCode.MATCH_013);
            }
            if (!Boolean.TRUE.equals(availability.getSportActive())) {
                throw new BusinessException(ErrorCode.SPORT_002);
            }

            MatchRequest matchRequest = MatchRequest.builder()
                    .matchId(matchId)
                    .requesterTeamId(request.teamId())
                    .status(MatchRequest.Status.PENDING)
                    .requestedAt(now)
                    .build();
            try {
                // Flush tường minh để đổi vi phạm unique "một yêu cầu đang hiệu lực mỗi đội" thành MATCH_006.
                matchRequestRepository.saveAndFlush(matchRequest);
            } catch (DataIntegrityViolationException exception) {
                if (DataIntegrityViolations.violates(exception, ACTIVE_REQUEST_INDEX)) {
                    throw new BusinessException(ErrorCode.MATCH_006);
                }
                throw exception;
            }
            UUID conversationId = availability.getExistingConversationId();
            if (conversationId == null) {
                conversationId = conversationRepository.save(Conversation.builder()
                        .contextType(Conversation.ContextType.TEAM_MATCH)
                        .matchId(matchId)
                        .partyAType(Conversation.PartyType.TEAM).partyAId(match.getAuthorTeamId())
                        .partyBType(Conversation.PartyType.TEAM).partyBId(request.teamId())
                        .build()).getId();
            }
            matchRepository.forceIncrementVersion(match);
            eventPublisher.publishEvent(new MatchRequestSentEvent(matchRequest.getId(), matchId, request.teamId(),
                    match.getAuthorTeamId(), now));
            return MatchWriteOutcome.done(matchMapper.toCreatedRequest(matchRequest, requester, conversationId));
        }).valueOrThrowExpired();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MatchRequestResponse> listMatchRequests(UUID matchId, UUID callerId,
                                                                MatchRequestStatusFilter status, int page, int pageSize) {
        MatchAuthorAccessView access = matchRepository.findAuthorAccess(matchId, callerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_001));
        if (!Boolean.TRUE.equals(access.getCallerIsCaptain())) {
            boolean disbanded = Team.TeamStatus.DISBANDED.name().equals(access.getAuthorTeamStatus());
            throw new BusinessException(disbanded ? ErrorCode.TEAM_016 : ErrorCode.TEAM_002);
        }
        if (Match.Type.NEED_VENUE.name().equals(access.getType())) {
            throw new BusinessException(ErrorCode.MATCH_016);
        }
        return PageResponse.from(matchRequestRepository.findByMatch(matchId, status == MatchRequestStatusFilter.PENDING,
                LocalDateTime.now(clock), pageRequestFactory.create(page, pageSize)), matchMapper::toRequest, page, pageSize);
    }

    @Override
    public AcceptMatchRequestResponse acceptRequest(UUID requestId, UUID callerId) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            MatchRequestContextView context = findContext(requestId);
            Match match = matchGuard.findMatch(context.getMatchId());
            teamGuard.requireActiveCaptain(match.getAuthorTeamId(), callerId);
            if (matchGuard.isExpiryDue(match.getStatus(), match.getExpiresAt(), now)) {
                matchExpiry.expireIfDue(match.getId(), match.getAuthorTeamId(), match.getType(), now);
                return MatchWriteOutcome.<AcceptMatchRequestResponse>expired();
            }
            matchGuard.requireOpen(match);
            if (!MatchRequest.Status.PENDING.name().equals(context.getStatus())) {
                throw new BusinessException(ErrorCode.MATCH_008);
            }
            if (!Team.TeamStatus.ACTIVE.name().equals(context.getRequesterTeamStatus())) {
                throw new BusinessException(ErrorCode.MATCH_013);
            }
            if (!Boolean.TRUE.equals(context.getSportActive())) {
                throw new BusinessException(ErrorCode.SPORT_002);
            }

            match.setStatus(Match.Status.CONFIRMED);
            match.setConfirmedOpponentTeamId(context.getRequesterTeamId());
            try {
                // Câu native: Hibernate tự flush UPDATE kèo (CAS version) trước; 0 dòng → exception rollback cả CAS.
                if (matchRequestRepository.acceptIfPending(requestId, now) == 0) {
                    throw new BusinessException(ErrorCode.MATCH_008);
                }
            } catch (DataIntegrityViolationException exception) {
                if (DataIntegrityViolations.violates(exception, ACCEPTED_REQUEST_INDEX)) {
                    throw new BusinessException(ErrorCode.MATCH_003);
                }
                throw exception;
            }
            matchRequestRepository.supersedePending(match.getId(), now);
            eventPublisher.publishEvent(new MatchConfirmedEvent(match.getId(), requestId, null, match.getAuthorTeamId(),
                    context.getRequesterTeamId(), now));
            return MatchWriteOutcome.done(new AcceptMatchRequestResponse(match.getId(), Match.Status.CONFIRMED.name(),
                    requestId, MatchRequest.Status.ACCEPTED.name(), context.getRequesterTeamId(),
                    context.getRequesterTeamName(), now));
        }).valueOrThrowExpired();
    }

    @Override
    public void rejectRequest(UUID requestId, UUID callerId, RejectMatchRequestRequest request) {
        String reason = request == null ? null : MatchServiceImpl.trimToNull(request.reason());
        concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            MatchRequestContextView context = findContext(requestId);
            teamGuard.requireCaptainAllowInactive(context.getAuthorTeamId(), callerId);
            if (expireIfDue(context, now)) {
                return MatchWriteOutcome.<Void>expired();
            }
            // Kèo đã EXPIRED → MATCH_004 (không để rơi xuống MATCH_008 vì yêu cầu cũng đã EXPIRED).
            matchGuard.requireNotExpired(Match.Status.valueOf(context.getMatchStatus()));
            if (matchRequestRepository.rejectIfPending(requestId, reason, now) == 0) {
                throw new BusinessException(ErrorCode.MATCH_008);
            }
            eventPublisher.publishEvent(new MatchRequestRejectedEvent(requestId, context.getMatchId(),
                    context.getRequesterTeamId(), now));
            return MatchWriteOutcome.<Void>done(null);
        }).valueOrThrowExpired();
    }

    @Override
    public MatchRequestWithdrawalResponse withdrawRequest(UUID requestId, UUID callerId,
                                                          WithdrawMatchRequestRequest request) {
        String reason = MatchServiceImpl.trimToNull(request.reason());
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            MatchRequestContextView context = findContext(requestId);
            teamGuard.requireCaptainAllowInactive(context.getRequesterTeamId(), callerId);
            if (expireIfDue(context, now)) {
                return MatchWriteOutcome.<MatchRequestWithdrawalResponse>expired();
            }
            matchGuard.requireNotExpired(Match.Status.valueOf(context.getMatchStatus()));
            boolean pending = MatchRequest.Status.PENDING.name().equals(context.getStatus());
            if (!pending && !MatchRequest.Status.ACCEPTED.name().equals(context.getStatus())) {
                throw new BusinessException(ErrorCode.MATCH_008);
            }
            // So với trạng thái client đang hiển thị: màn hình còn PENDING mà yêu cầu vừa được chấp nhận → không rút.
            if (!context.getStatus().equals(request.expectedStatus())) {
                throw new BusinessException(ErrorCode.MATCH_015);
            }
            return MatchWriteOutcome.done(pending
                    ? withdrawPending(context, reason, now)
                    : withdrawAccepted(context, reason, now));
        }).valueOrThrowExpired();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MatchRequestResponse> listTeamRequests(UUID teamId, UUID callerId,
                                                               MatchRequestStatusFilter status, int page, int pageSize) {
        teamGuard.requireCaptainAllowInactive(teamId, callerId);
        return PageResponse.from(matchRequestRepository.findByRequesterTeam(teamId, status.toQueryValue(),
                LocalDateTime.now(clock), pageRequestFactory.create(page, pageSize)), matchMapper::toRequest, page, pageSize);
    }

    /** Rút yêu cầu còn chờ: 0 dòng = yêu cầu vừa đổi trạng thái (vd được chấp nhận) → MATCH_015; không trừ điểm. */
    private MatchRequestWithdrawalResponse withdrawPending(MatchRequestContextView context, String reason,
                                                          LocalDateTime now) {
        if (matchRequestRepository.withdrawIfPending(context.getRequestId(), reason, now) == 0) {
            throw new BusinessException(ErrorCode.MATCH_015);
        }
        eventPublisher.publishEvent(new MatchRequestWithdrawnEvent(context.getRequestId(), context.getMatchId(),
                context.getRequesterTeamId(), context.getAuthorTeamId(), now));
        return new MatchRequestWithdrawalResponse(context.getRequestId(), context.getMatchId(),
                MatchRequest.Status.WITHDRAWN.name(), now, context.getMatchStatus(), 0);
    }

    /**
     * Rút yêu cầu đã chấp nhận = huỷ kèo đã ghép (cùng đường với huỷ kèo, một event): kèo không còn CONFIRMED →
     * MATCH_005 → thiếu lý do MATCH_011 → trận đã bắt đầu MATCH_012.
     */
    private MatchRequestWithdrawalResponse withdrawAccepted(MatchRequestContextView context, String reason,
                                                            LocalDateTime now) {
        Match match = matchGuard.findMatch(context.getMatchId());
        if (match.getStatus() != Match.Status.CONFIRMED) {
            throw new BusinessException(ErrorCode.MATCH_005);
        }
        if (reason == null) {
            throw new BusinessException(ErrorCode.MATCH_011);
        }
        if (!now.isBefore(match.getMatchStartTime())) {
            throw new BusinessException(ErrorCode.MATCH_012);
        }
        int penalty = matchCancellation.cancelConfirmed(match, context.getRequesterTeamId(), reason,
                MatchCancelledEvent.Cause.REQUEST_WITHDRAWN, now).reliabilityPenalty();
        return new MatchRequestWithdrawalResponse(context.getRequestId(), context.getMatchId(),
                MatchRequest.Status.WITHDRAWN.name(), now, Match.Status.CANCELLED.name(), penalty);
    }

    private MatchRequestContextView findContext(UUID requestId) {
        return matchRequestRepository.findContextById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_002));
    }

    /** Nhánh chuyển EXPIRED khi kèo OPEN đã tới hạn; true → caller trả {@code Expired} để commit rồi MATCH_004. */
    private boolean expireIfDue(MatchRequestContextView context, LocalDateTime now) {
        if (!matchGuard.isExpiryDue(Match.Status.valueOf(context.getMatchStatus()), context.getExpiresAt(), now)) {
            return false;
        }
        matchExpiry.expireIfDue(context.getMatchId(), context.getAuthorTeamId(),
                Match.Type.valueOf(context.getMatchType()), now);
        return true;
    }
}
