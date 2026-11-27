package com.phananh.arenaflow.domain.repository;

/**
 * Hằng SQL thống kê uy tín đội (tỉ lệ hoàn thành, sao trung bình) — nguồn duy nhất cho đọc và refresh huy hiệu. Tham
 * số: {@code :cancelWindowHours}; đội lấy từ {@code t2.id} của câu ngoài.
 */
public final class TeamReputationSql {

    /** Thống kê một đội ({@code t2.id}) dạng subquery cho {@code CROSS JOIN LATERAL}: completed_matches, avg_rating. */
    public static final String STATS_LATERAL = """
            SELECT (
                WITH m_t AS (
                  SELECT m.id, m.status, m.match_start_time, m.cancelled_at, m.cancelled_by_team_id,
                         m.confirmed_opponent_team_id FROM match m WHERE m.author_team_id = t2.id
                  UNION ALL
                  SELECT m.id, m.status, m.match_start_time, m.cancelled_at, m.cancelled_by_team_id,
                         m.confirmed_opponent_team_id FROM match m WHERE m.confirmed_opponent_team_id = t2.id),
                f AS (
                  SELECT m_t.*, EXISTS (SELECT 1 FROM report r WHERE r.match_id = m_t.id AND r.reported_type = 'TEAM'
                                        AND r.reported_id = t2.id AND r.report_type = 'NOSHOW'
                                        AND r.status <> 'REJECTED') AS noshow
                  FROM m_t)
                SELECT COUNT(*) FILTER (WHERE status = 'COMPLETED' AND NOT noshow) FROM f
            ) AS completed_matches,
            (SELECT AVG(rt.score) FROM rating rt WHERE rt.ratee_type = 'TEAM' AND rt.ratee_id = t2.id
                AND rt.score IS NOT NULL AND rt.application_id IS NULL) AS avg_rating
            """;

    private TeamReputationSql() {
    }
}
