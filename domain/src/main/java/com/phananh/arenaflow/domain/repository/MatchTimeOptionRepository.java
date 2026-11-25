package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.match.MatchTimeOption;
import com.phananh.arenaflow.domain.projection.TimeOptionView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MatchTimeOptionRepository extends JpaRepository<MatchTimeOption, UUID> {

    /**
     * Ghi mọi khung giờ của kèo trong một câu và trả id do PostgreSQL sinh ({@code RETURNING}) để response có
     * {@code timeOptionId} mà không đọc lại. Khai báo không {@code @Modifying}: câu trả dòng nên chạy như query.
     */
    @Query(value = """
            INSERT INTO match_time_option (id, match_id, proposed_start_time, proposed_end_time, created_at)
            SELECT uuidv7(), :matchId, t.s, t.e, :now
            FROM unnest(CAST(:starts AS timestamp[]), CAST(:ends AS timestamp[])) AS t(s, e)
            RETURNING id AS id, proposed_start_time AS proposedStartTime, proposed_end_time AS proposedEndTime
            """, nativeQuery = true)
    List<TimeOptionView> insertAllReturning(@Param("matchId") UUID matchId, @Param("starts") LocalDateTime[] starts,
                                            @Param("ends") LocalDateTime[] ends, @Param("now") LocalDateTime now);
}
