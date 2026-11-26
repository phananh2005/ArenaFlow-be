package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.common.time.AppTime;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dữ liệu mẫu bằng JDBC cho test tích hợp đánh giá / report / tranh chấp / uy tín (schema sau V9).
 * Mọi thời điểm truyền vào là {@link LocalDateTime} giờ Việt Nam.
 */
public final class ReputationDbFixtures {

    private final JdbcTemplate jdbc;

    public ReputationDbFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID user(String name, String phoneNumber) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO \"user\" (id, display_name, phone_number) VALUES (?, ?, ?)", id, name, phoneNumber);
        return id;
    }

    public void deleteUser(UUID userId, LocalDateTime at) {
        jdbc.update("UPDATE \"user\" SET deleted_at = ? WHERE id = ?", at, userId);
    }

    /** Đội kèm đội trưởng ACTIVE {@code captainId}. */
    public UUID team(String name, UUID captainId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO team (id, name, created_by) VALUES (?, ?, ?)", id, name, captainId);
        LocalDateTime joinedAt = AppTime.now();
        jdbc.update("""
                INSERT INTO team_member (id, team_id, user_id, role, status, invited_at, joined_at)
                VALUES (?, ?, ?, 'CAPTAIN', 'ACTIVE', ?, ?)
                """, UUID.randomUUID(), id, captainId, joinedAt, joinedAt);
        return id;
    }

    public void teamState(UUID teamId, int score, int cleanStreak, int noShowCount) {
        jdbc.update("UPDATE team SET reliability_score = ?, clean_streak = ?, report_count_noshow = ? WHERE id = ?",
                score, cleanStreak, noShowCount, teamId);
    }

    public void teamStatus(UUID teamId, String status) {
        jdbc.update("UPDATE team SET status = ? WHERE id = ?", status, teamId);
    }

    public UUID venue(UUID creatorId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO venue (id, name, address, latitude, longitude, created_by_type, created_by_user_id)
                VALUES (?, 'Sân uy tín', 'Địa chỉ', 10.0, 106.0, 'USER', ?)
                """, id, creatorId);
        return id;
    }

    /** Kèo luồng A đã ghép (hoặc chưa nếu {@code opponentTeamId = null}) ở trạng thái {@code status}. */
    public UUID match(UUID authorTeamId, UUID opponentTeamId, String status, LocalDateTime startsAt, UUID venueId) {
        UUID id = UUID.randomUUID();
        UUID sportId = jdbc.queryForObject("SELECT id FROM sport ORDER BY name LIMIT 1", UUID.class);
        jdbc.update("""
                INSERT INTO match (id, type, author_team_id, status, sport_id, format, venue_id, match_start_time,
                                   match_end_time, expires_at, confirmed_opponent_team_id, created_at, updated_at)
                VALUES (?, 'HAS_VENUE', ?, ?, ?, 'FIVE_A_SIDE', ?, ?, ?, ?, ?, ?, ?)
                """, id, authorTeamId, status, sportId, venueId, startsAt, startsAt.plusHours(1).plusMinutes(30),
                startsAt.minusHours(2), opponentTeamId, startsAt.minusDays(2), startsAt.minusDays(2));
        return id;
    }

    public void cancelMatch(UUID matchId, UUID cancelledByTeamId, LocalDateTime cancelledAt) {
        jdbc.update("UPDATE match SET status = 'CANCELLED', cancelled_by_team_id = ?, cancelled_at = ? WHERE id = ?",
                cancelledByTeamId, cancelledAt, matchId);
    }

    public UUID rating(UUID matchId, UUID raterTeamId, UUID rateeTeamId, Integer score, boolean noShow, UUID byUserId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO rating (id, match_id, rater_type, rater_id, ratee_type, ratee_id, score, is_no_show,
                                    created_by_user_id)
                VALUES (?, ?, 'TEAM', ?, 'TEAM', ?, ?, ?, ?)
                """, id, matchId, raterTeamId, rateeTeamId, score, noShow, byUserId);
        return id;
    }

    public UUID report(UUID matchId, UUID reporterTeamId, UUID reportedTeamId, String type, String status, int penalty,
                       UUID byUserId, LocalDateTime createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO report (id, match_id, reporter_type, reporter_id, reported_type, reported_id, report_type,
                                    reason, status, reliability_penalty, created_by_user_id, created_at, updated_at)
                VALUES (?, ?, 'TEAM', ?, 'TEAM', ?, ?, 'Lý do report', ?, ?, ?, ?, ?)
                """, id, matchId, reporterTeamId, reportedTeamId, type, status, penalty, byUserId, createdAt, createdAt);
        return id;
    }

    public UUID dispute(UUID reportId, UUID byUserId, LocalDateTime createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dispute (id, report_id, evidence, created_by_user_id, created_at, updated_at)
                VALUES (?, ?, 'Bằng chứng', ?, ?, ?)
                """, id, reportId, byUserId, createdAt, createdAt);
        return id;
    }
}
