package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.application.event.match.MatchCancelledEvent;
import com.phananh.arenaflow.application.service.ReliabilityChange;
import com.phananh.arenaflow.application.service.ReliabilityReason;
import com.phananh.arenaflow.application.service.ReputationService;
import com.phananh.arenaflow.application.support.reputation.TimeBandPenaltyPolicy;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.repository.MatchRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Một đường huỷ kèo đã ghép, dùng chung cho "huỷ kèo CONFIRMED" và "rút yêu cầu ACCEPTED" — cùng kết quả, trừ điểm
 * một lần. Caller kiểm quyền, trạng thái, lý do, giờ trận TRƯỚC khi gọi.
 */
@Component
@RequiredArgsConstructor
public class MatchCancellation {

    private final MatchRequestRepository matchRequestRepository;
    private final ReputationService reputationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Bắt buộc trong transaction của caller. Đổi entity sang CANCELLED (CAS lúc flush); đội huỷ là đội đã ghép của kèo
     * luồng A → yêu cầu ACCEPTED → WITHDRAWN (phải đúng 1 dòng, Hibernate tự flush CAS kèo trước câu này); trừ điểm và
     * cắt chuỗi trận sạch của đội huỷ; phát một {@link MatchCancelledEvent}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CancellationResult cancelConfirmed(Match match, UUID cancellingTeamId, String reason,
                                              MatchCancelledEvent.Cause cause, LocalDateTime now) {
        match.setStatus(Match.Status.CANCELLED);
        match.setCancelledByTeamId(cancellingTeamId);
        match.setCancelledAt(now);
        match.setCancelReason(reason);

        boolean byOpponent = cancellingTeamId.equals(match.getConfirmedOpponentTeamId());
        UUID withdrawnRequestId = null;
        if (byOpponent && match.getType() == Match.Type.HAS_VENUE) {
            List<UUID> withdrawn = matchRequestRepository.withdrawAccepted(match.getId(), cancellingTeamId, reason, now);
            if (withdrawn.size() != 1) {
                throw new IllegalStateException("Confirmed match must have exactly one accepted request: " + match.getId());
            }
            withdrawnRequestId = withdrawn.getFirst();
        }

        int penalty = TimeBandPenaltyPolicy.MATCH_CANCELLATION.penaltyFor(match.getMatchStartTime(), now);
        ReliabilityReason reliabilityReason = cause == MatchCancelledEvent.Cause.REQUEST_WITHDRAWN
                ? ReliabilityReason.MATCH_REQUEST_WITHDRAWN_AFTER_ACCEPTED
                : ReliabilityReason.MATCH_CANCELLED_AFTER_CONFIRMED;
        reputationService.adjustTeamReliability(cancellingTeamId,
                new ReliabilityChange(penalty, reliabilityReason, match.getId(), true));

        UUID otherTeamId = byOpponent ? match.getAuthorTeamId() : match.getConfirmedOpponentTeamId();
        eventPublisher.publishEvent(new MatchCancelledEvent(match.getId(), cancellingTeamId, otherTeamId,
                Match.Status.CONFIRMED, cause, withdrawnRequestId, now));
        return new CancellationResult(penalty);
    }
}
