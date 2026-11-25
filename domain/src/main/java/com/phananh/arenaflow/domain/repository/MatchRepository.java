package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.match.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID>, MatchRepositoryCustom {

    /**
     * Ghép kèo luồng B (CAS trên {@code status = OPEN} + {@code version} đọc cùng lần thử): ghi đội đã ghép, địa
     * điểm, khung giờ, chi phí + cọc đã chốt; {@code version + 1}. 0 dòng = kèo vừa đổi trạng thái hoặc phiên bản.
     * Không nạp entity {@code Match} nên không có bản managed nào bị ghi đè lúc flush.
     */
    @Modifying
    @Query(value = """
            UPDATE match SET status = 'CONFIRMED', confirmed_opponent_team_id = :opponentTeamId, venue_id = :venueId,
                   match_start_time = :startTime, match_end_time = :endTime,
                   cost_type = :costType, cost_split = :costSplit, cost_venue_amount = :venueAmount,
                   cost_drinks_amount = :drinksAmount, cost_deposit_amount = :depositAmount,
                   cost_deposit_holder = :depositHolder, version = version + 1, updated_at = :now
            WHERE id = :matchId AND status = 'OPEN' AND version = :expectedVersion
            """, nativeQuery = true)
    int confirmPairing(@Param("matchId") UUID matchId, @Param("expectedVersion") long expectedVersion,
                       @Param("opponentTeamId") UUID opponentTeamId, @Param("venueId") UUID venueId,
                       @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
                       @Param("costType") String costType, @Param("costSplit") String costSplit,
                       @Param("venueAmount") Long venueAmount, @Param("drinksAmount") Long drinksAmount,
                       @Param("depositAmount") Long depositAmount, @Param("depositHolder") String depositHolder,
                       @Param("now") LocalDateTime now);
}
