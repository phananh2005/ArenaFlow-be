package com.phananh.arenaflow.application.support.reputation;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.projection.MatchSidesView;

import java.util.UUID;

/**
 * Đội của người gọi ({@code own}) và đội còn lại ({@code other}) trong một trận — dùng chung cho đánh giá và report:
 * hệ thống không nhận {@code teamId}, đội hành động là phía mà người gọi đang làm đội trưởng ACTIVE.
 */
public record MatchSides(UUID ownTeamId, UUID otherTeamId, boolean ownIsAuthor) {

    /**
     * Chỉ gọi sau khi đã chặn hai trường hợp "không phía nào" và "cả hai phía" (mỗi API có mã lỗi riêng) → vi phạm là
     * lỗi lập trình.
     */
    public static MatchSides of(MatchSidesView view) {
        boolean author = Boolean.TRUE.equals(view.getCallerAuthorCaptain());
        boolean opponent = Boolean.TRUE.equals(view.getCallerOpponentCaptain());
        if (author == opponent) {
            throw new IllegalStateException("Người gọi phải là đội trưởng của đúng một đội trong trận");
        }
        return author
                ? new MatchSides(view.getAuthorTeamId(), view.getOpponentTeamId(), true)
                : new MatchSides(view.getOpponentTeamId(), view.getAuthorTeamId(), false);
    }

    public static boolean isCaptainOfAny(MatchSidesView view) {
        return Boolean.TRUE.equals(view.getCallerAuthorCaptain()) || Boolean.TRUE.equals(view.getCallerOpponentCaptain());
    }

    public static boolean isCaptainOfBoth(MatchSidesView view) {
        return Boolean.TRUE.equals(view.getCallerAuthorCaptain()) && Boolean.TRUE.equals(view.getCallerOpponentCaptain());
    }

    /** Đội mà người gọi làm đội trưởng đang tạm ngưng (INACTIVE); trạng thái đội còn lại không ảnh hưởng. */
    public static boolean isCallerTeamInactive(MatchSidesView view) {
        return inactive(view.getCallerAuthorCaptain(), view.getAuthorTeamStatus())
                || inactive(view.getCallerOpponentCaptain(), view.getOpponentTeamStatus());
    }

    private static boolean inactive(Boolean callerIsCaptain, String teamStatus) {
        return Boolean.TRUE.equals(callerIsCaptain) && Team.TeamStatus.INACTIVE.name().equals(teamStatus);
    }
}
