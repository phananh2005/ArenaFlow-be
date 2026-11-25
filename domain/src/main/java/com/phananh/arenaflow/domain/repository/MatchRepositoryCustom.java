package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.match.Match;

/** Phần cài đặt tay của {@link MatchRepository} — để service không cầm {@code EntityManager}. */
public interface MatchRepositoryCustom {

    /**
     * Tăng {@code version} của kèo lúc commit (OPTIMISTIC_FORCE_INCREMENT) dù entity không đổi, để thao tác khác đang
     * giữ bản cũ (chấp nhận, huỷ) xung đột và thử lại.
     */
    void forceIncrementVersion(Match match);
}
