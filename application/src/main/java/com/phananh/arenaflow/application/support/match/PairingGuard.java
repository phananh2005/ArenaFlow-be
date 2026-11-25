package com.phananh.arenaflow.application.support.match;

import com.phananh.arenaflow.application.service.PairingSide;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.match.Pairing;
import com.phananh.arenaflow.domain.projection.PairingContextView;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Một nguồn cho các bước kiểm trên ngữ cảnh thương lượng đã đọc (không truy vấn thêm). */
@Component
public class PairingGuard {

    private static final String DISBANDED = "DISBANDED";
    private static final String INACTIVE = "INACTIVE";

    /** {@code teamId} không phải một trong hai đội → PAIRING_002. */
    public PairingSide requireSide(PairingContextView ctx, UUID teamId) {
        if (teamId.equals(ctx.getAuthorTeamId())) {
            return PairingSide.AUTHOR;
        }
        if (teamId.equals(ctx.getInterestedTeamId())) {
            return PairingSide.INTERESTED;
        }
        throw new BusinessException(ErrorCode.PAIRING_002);
    }

    /**
     * Đội phía {@code side}: DISBANDED → TEAM_016; INACTIVE (khi không cho phép) → TEAM_015; người gọi không phải đội
     * trưởng ACTIVE của đội đó → TEAM_002.
     */
    public void requireCaptain(PairingContextView ctx, PairingSide side, boolean allowInactive) {
        boolean author = side == PairingSide.AUTHOR;
        String teamStatus = author ? ctx.getAuthorTeamStatus() : ctx.getInterestedTeamStatus();
        if (DISBANDED.equals(teamStatus)) {
            throw new BusinessException(ErrorCode.TEAM_016);
        }
        if (INACTIVE.equals(teamStatus) && !allowInactive) {
            throw new BusinessException(ErrorCode.TEAM_015);
        }
        Boolean captain = author ? ctx.getCallerAuthorCaptain() : ctx.getCallerInterestedCaptain();
        if (!Boolean.TRUE.equals(captain)) {
            throw new BusinessException(ErrorCode.TEAM_002);
        }
    }

    /** Thương lượng đã kết thúc → PAIRING_005. */
    public void requireNegotiating(PairingContextView ctx) {
        if (!Pairing.Status.NEGOTIATING.name().equals(ctx.getStatus())) {
            throw new BusinessException(ErrorCode.PAIRING_005);
        }
    }

    /** Gói FE đang hiển thị đã cũ → PAIRING_006. */
    public void requireRevision(PairingContextView ctx, int expected) {
        if (ctx.getRevision() != expected) {
            throw new BusinessException(ErrorCode.PAIRING_006);
        }
    }

    /** Địa điểm đã ngừng hoạt động → VENUE_002. */
    public void requireVenueActive(String venueStatus) {
        if (INACTIVE.equals(venueStatus)) {
            throw new BusinessException(ErrorCode.VENUE_002);
        }
    }
}
