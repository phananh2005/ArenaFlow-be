package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.application.event.match.MatchExpiredEvent;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import com.phananh.arenaflow.domain.repository.MatchRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Chuyển một kèo OPEN quá hạn sang EXPIRED khi thao tác ghi gặp nó (cùng câu bulk với job hết hạn). */
@Component
@RequiredArgsConstructor
public class MatchExpiry {

    private final MatchRepository matchRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Bắt buộc trong transaction của caller (lần thử phải COMMIT việc chuyển trạng thái). Kèo được chuyển → yêu cầu
     * PENDING của kèo luồng A → EXPIRED, phát đúng một {@link MatchExpiredEvent}; trả true nếu chính lời gọi này
     * chuyển. 0 dòng → job / request khác đã chuyển, không làm gì thêm.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean expireIfDue(UUID matchId, UUID authorTeamId, Match.Type type, LocalDateTime now) {
        if (matchRepository.expireIfDue(matchId, now) == 0) {
            return false;
        }
        if (type == Match.Type.HAS_VENUE) {
            matchRequestRepository.expirePendingByMatchIds(List.of(matchId));
        }
        eventPublisher.publishEvent(new MatchExpiredEvent(matchId, authorTeamId, now));
        return true;
    }
}
