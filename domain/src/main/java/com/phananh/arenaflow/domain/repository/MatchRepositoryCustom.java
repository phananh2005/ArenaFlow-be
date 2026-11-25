package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.projection.MatchAuthorAccessView;
import com.phananh.arenaflow.domain.projection.MatchAvailabilityView;

import java.util.Optional;
import java.util.UUID;

/** Fragment của {@link MatchRepository}: thao tác cần {@code EntityManager}. */
public interface MatchRepositoryCustom {

    /** Tăng {@code version} của kèo lúc commit (khoá lạc quan) để tuần tự hoá với thao tác khác trên cùng kèo. */
    void forceIncrementVersion(Match match);

    /** Trạng thái đội tác giả, bộ môn, đội trưởng hiện tại và cuộc trò chuyện đã có của (kèo, đội) — một câu. */
    MatchAvailabilityView findAvailability(UUID matchId, UUID teamId);

    /** Đội tác giả, trạng thái đội, người gọi có là đội trưởng ACTIVE, loại kèo — một câu. */
    Optional<MatchAuthorAccessView> findAuthorAccess(UUID matchId, UUID userId);
}
