package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một nguồn cho mã lỗi trạng thái kèo. Mọi thao tác ghi kiểm hết hạn TRƯỚC các trạng thái khác: quá hạn → chuyển
 * EXPIRED rồi MATCH_004; đã EXPIRED → MATCH_004 (không phải MATCH_005 / MATCH_008).
 */
@Component
@RequiredArgsConstructor
public class MatchGuard {

    private final MatchRepository matchRepository;

    /** Kèo theo id (không khoá); không có → MATCH_001. */
    public Match findMatch(UUID matchId) {
        return matchRepository.findById(matchId).orElseThrow(() -> new BusinessException(ErrorCode.MATCH_001));
    }

    /** Kèo OPEN đã tới {@code expiresAt} → thao tác ghi phải đi nhánh chuyển EXPIRED. */
    public boolean isExpiryDue(Match.Status status, LocalDateTime expiresAt, LocalDateTime now) {
        return status == Match.Status.OPEN && !expiresAt.isAfter(now);
    }

    public void requireNotExpired(Match.Status status) {
        if (status == Match.Status.EXPIRED) {
            throw new BusinessException(ErrorCode.MATCH_004);
        }
    }

    /** EXPIRED → MATCH_004; CONFIRMED → MATCH_003; trạng thái khác OPEN → MATCH_005. */
    public void requireOpen(Match match) {
        requireNotExpired(match.getStatus());
        if (match.getStatus() == Match.Status.CONFIRMED) {
            throw new BusinessException(ErrorCode.MATCH_003);
        }
        if (match.getStatus() != Match.Status.OPEN) {
            throw new BusinessException(ErrorCode.MATCH_005);
        }
    }
}
