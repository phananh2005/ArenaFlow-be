package com.phananh.arenaflow.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Dựng dữ liệu cho test tra cứu người dùng bằng SQL thuần: thời điểm truyền dạng chuỗi giờ Việt Nam không offset
 * ({@code "2026-11-17 19:30:00"}) và ép kiểu trong SQL, nên không phụ thuộc kiểu thời gian của entity. Gọi trong test
 * có {@code @Transactional} để dữ liệu được rollback.
 */
public final class UserDiscoveryFixtures {

    public static final String DEFAULT_TIME = "2026-11-01 10:00:00";

    private final JdbcTemplate jdbc;

    public UserDiscoveryFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID user(String displayName) {
        return user(displayName, null, "USER");
    }

    public UUID user(String displayName, String phoneNumber, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO \"user\" (id, display_name, phone_number, role) VALUES (?, ?, ?, ?)",
                id, displayName, phoneNumber, role);
        return id;
    }

    /** Xoá mềm: deleted_at lấy bằng created_at để không phụ thuộc giờ phiên. */
    public void softDelete(UUID userId) {
        jdbc.update("UPDATE \"user\" SET deleted_at = created_at WHERE id = ?", userId);
    }

    /** Đội ACTIVE có đội trưởng ACTIVE tham gia lúc {@code captainJoinedAt}. */
    public UUID team(String name, UUID captainId, String captainJoinedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO team (id, name, created_by, skill_levels) VALUES (?, ?, ?, ?)",
                id, name, captainId, "[\"Thi đấu\"]");
        member(id, captainId, "CAPTAIN", "ACTIVE", captainJoinedAt, captainJoinedAt);
        return id;
    }

    public UUID team(String name, UUID captainId) {
        return team(name, captainId, DEFAULT_TIME);
    }

    /** {@code joinedAt} null với lời mời PENDING. */
    public UUID member(UUID teamId, UUID userId, String role, String status, String invitedAt, String joinedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO team_member (id, team_id, user_id, role, status, invited_at, joined_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS timestamp), CAST(? AS timestamp))
                """, id, teamId, userId, role, status, invitedAt, joinedAt);
        return id;
    }

    public UUID invite(UUID teamId, UUID userId, String invitedAt) {
        return member(teamId, userId, "MEMBER", "PENDING", invitedAt, null);
    }

    public UUID activeMember(UUID teamId, UUID userId, String joinedAt) {
        return member(teamId, userId, "MEMBER", "ACTIVE", joinedAt, joinedAt);
    }

    public void deactivateTeam(UUID teamId) {
        jdbc.update("UPDATE team SET status = 'INACTIVE', inactive_reason = 'Kiểm thử' WHERE id = ?", teamId);
    }

    /** Giải tán như luồng thật: PENDING → REJECTED, ACTIVE → LEFT, đội DISBANDED. */
    public void disbandTeam(UUID teamId, UUID byUserId) {
        jdbc.update("UPDATE team_member SET status = 'REJECTED' WHERE team_id = ? AND status = 'PENDING'", teamId);
        jdbc.update("""
                UPDATE team_member SET status = 'LEFT', left_at = COALESCE(joined_at, invited_at)
                WHERE team_id = ? AND status = 'ACTIVE'
                """, teamId);
        jdbc.update("""
                UPDATE team SET status = 'DISBANDED', disband_reason = 'Kiểm thử', disbanded_at = created_at,
                                disbanded_by = ?
                WHERE id = ?
                """, byUserId, teamId);
    }

    /** Chuyển quyền đội trưởng trực tiếp trong DB (đội trưởng cũ thành MEMBER). */
    public void transferCaptain(UUID teamId, UUID fromUserId, UUID toUserId) {
        jdbc.update("UPDATE team_member SET role = 'MEMBER' WHERE team_id = ? AND user_id = ? AND status = 'ACTIVE'",
                teamId, fromUserId);
        jdbc.update("UPDATE team_member SET role = 'CAPTAIN' WHERE team_id = ? AND user_id = ? AND status = 'ACTIVE'",
                teamId, toUserId);
    }

    /** Từ ngẫu nhiên chỉ gồm chữ thường, bắt đầu bằng {@code z}, để tách dữ liệu test khỏi dữ liệu sẵn có của DB. */
    public static String uniqueWord() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        StringBuilder word = new StringBuilder("zq");
        for (int i = 0; i < 8; i++) {
            word.append((char) ('a' + Character.digit(hex.charAt(i), 16) % 26));
        }
        return word.toString();
    }

    /** Số điện thoại ngẫu nhiên dạng 0 + 9 chữ số. */
    public static String uniquePhone() {
        long value = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000L);
        return "09" + String.format("%08d", value % 100_000_000L);
    }
}
