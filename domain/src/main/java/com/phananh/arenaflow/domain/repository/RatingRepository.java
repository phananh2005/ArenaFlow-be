package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.rating.Rating;
import com.phananh.arenaflow.domain.projection.MatchRatingRowView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    /**
     * Trận LEFT JOIN đánh giá đội ↔ đội của trận trong một câu (tiền tố {@code match_id} của index chống trùng): rỗng =
     * không có trận; một dòng với cột đánh giá null = trận chưa có đánh giá; tối đa hai dòng.
     */
    @Query(value = """
            SELECT m.author_team_id AS authorTeamId, m.confirmed_opponent_team_id AS opponentTeamId,
                   r.id AS ratingId, r.rater_id AS raterTeamId, r.ratee_id AS rateeTeamId, r.is_no_show AS noShow,
                   CAST(r.score AS INTEGER) AS score, r.comment AS comment,
                   r.rater_team_score AS raterTeamScore, r.ratee_team_score AS rateeTeamScore, r.created_at AS createdAt
            FROM match m
            LEFT JOIN rating r ON r.match_id = m.id AND r.rater_type = 'TEAM' AND r.ratee_type = 'TEAM'
            WHERE m.id = :matchId
            ORDER BY r.created_at, r.id
            """, nativeQuery = true)
    List<MatchRatingRowView> findMatchRatingRows(@Param("matchId") UUID matchId);

    List<Rating> findByMatchIdAndRaterTypeOrderByCreatedAtAsc(UUID matchId, Rating.RaterType raterType);
}
