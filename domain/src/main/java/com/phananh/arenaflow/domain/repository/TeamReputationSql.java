package com.phananh.arenaflow.domain.repository;

/**
 * Nguồn duy nhất của công thức thống kê đội (tỉ lệ hoàn thành + sao trung bình), dùng chung cho câu đọc uy tín và câu
 * refresh huy hiệu xác minh.
 */
public final class TeamReputationSql {

    /**
     * Subquery một dòng cho đội {@code tx.id} (bảng {@code team} ngoài phải đặt alias {@code tx}; dùng trong
     * {@code CROSS JOIN LATERAL}), tham số {@code :cancelWindowHours}. Trả {@code completed_matches},
     * {@code counted_matches}, {@code avg_rating} (null khi chưa có sao), {@code rating_count}.
     * <ul>
     *   <li>Mọi trận của đội: hai nhánh UNION ALL theo đội tạo kèo / đội đã ghép để dùng hai index; một đội không thể
     *   vừa là đội tạo kèo vừa là đội đã ghép của cùng trận.</li>
     *   <li>{@code noshow}: đội bị report bùng (chưa bị bác) trong trận.</li>
     *   <li>Hoàn thành (tử số): trận COMPLETED mà không bị report bùng — đánh giá "không đến" không loại trận.</li>
     *   <li>Được tính (mẫu số): COMPLETED; CANCELLED sau khi ghép do chính đội huỷ trong cửa sổ trước giờ bắt đầu;
     *   ARCHIVED (chưa ai đánh giá) mà đội bị report bùng.</li>
     *   <li>Sao: chỉ đánh giá của đội đối phương, bỏ đánh giá "không đến".</li>
     * </ul>
     */
    public static final String STATS = """
            SELECT st.completed_matches, st.counted_matches, rt.avg_rating, rt.rating_count
            FROM (
                SELECT COUNT(*) FILTER (WHERE f.status = 'COMPLETED' AND NOT f.noshow) AS completed_matches,
                       COUNT(*) FILTER (WHERE f.status = 'COMPLETED'
                                        OR (f.status = 'CANCELLED' AND f.confirmed_opponent_team_id IS NOT NULL
                                            AND f.cancelled_by_team_id = tx.id
                                            AND f.cancelled_at > f.match_start_time - make_interval(hours => :cancelWindowHours))
                                        OR (f.status = 'ARCHIVED' AND f.noshow)) AS counted_matches
                FROM (
                    SELECT m_t.status, m_t.match_start_time, m_t.cancelled_at, m_t.cancelled_by_team_id,
                           m_t.confirmed_opponent_team_id,
                           EXISTS (SELECT 1 FROM report r
                                   WHERE r.match_id = m_t.id AND r.reported_type = 'TEAM' AND r.reported_id = tx.id
                                     AND r.report_type = 'NOSHOW' AND r.status <> 'REJECTED') AS noshow
                    FROM (
                        SELECT m.id, m.status, m.match_start_time, m.cancelled_at, m.cancelled_by_team_id,
                               m.confirmed_opponent_team_id
                        FROM match m WHERE m.author_team_id = tx.id
                        UNION ALL
                        SELECT m.id, m.status, m.match_start_time, m.cancelled_at, m.cancelled_by_team_id,
                               m.confirmed_opponent_team_id
                        FROM match m WHERE m.confirmed_opponent_team_id = tx.id
                    ) m_t
                ) f
            ) st
            CROSS JOIN (
                SELECT AVG(r.score) AS avg_rating, COUNT(r.score) AS rating_count
                FROM rating r
                WHERE r.ratee_type = 'TEAM' AND r.ratee_id = tx.id AND r.rater_type = 'TEAM' AND r.score IS NOT NULL
            ) rt
            """;

    private TeamReputationSql() {
    }
}
