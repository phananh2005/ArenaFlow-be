package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.match.CostCustomItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CostCustomItemRepository extends JpaRepository<CostCustomItem, UUID> {

    List<CostCustomItem> findByMatchIdOrderByDisplayOrderAsc(UUID matchId);

    @Modifying
    @Query(value = "DELETE FROM cost_custom_item WHERE match_id = :matchId", nativeQuery = true)
    int deleteByMatchIdBulk(@Param("matchId") UUID matchId);

    /**
     * Thêm mọi khoản trong một câu (unnest), giữ thứ tự mảng làm {@code display_order} 1..n. Id do {@code uuidv7()}
     * của PostgreSQL (≥ 18) sinh, cùng định dạng UUID v7 với entity — caller đọc lại để lấy id.
     */
    @Modifying
    @Query(value = """
            INSERT INTO cost_custom_item (id, match_id, item_name, amount, display_order, created_at)
            SELECT uuidv7(), :matchId, t.name, t.amount, t.ord, :now
            FROM unnest(CAST(:names AS text[]), CAST(:amounts AS bigint[])) WITH ORDINALITY AS t(name, amount, ord)
            """, nativeQuery = true)
    int insertAll(@Param("matchId") UUID matchId, @Param("names") String[] names, @Param("amounts") Long[] amounts,
                  @Param("now") LocalDateTime now);
}
