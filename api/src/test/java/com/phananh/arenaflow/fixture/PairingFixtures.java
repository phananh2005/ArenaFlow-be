package com.phananh.arenaflow.fixture;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dữ liệu mẫu cho test tích hợp luồng B (kèo NEED_VENUE, lựa chọn, pairing, cuộc trò chuyện) ghi thẳng bằng SQL.
 * Thời gian là giờ Việt Nam ({@code LocalDateTime} ↔ cột {@code TIMESTAMP}).
 */
public final class PairingFixtures {

    private final JdbcTemplate jdbc;
    private final List<UUID> users = new ArrayList<>();
    private final List<UUID> teams = new ArrayList<>();
    private final List<UUID> venues = new ArrayList<>();
    private final List<UUID> matches = new ArrayList<>();

    public PairingFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Xoá mọi dòng fixture đã tạo (cho test commit thật, không {@code @Transactional}): cuộc trò chuyện → kèo (cascade
     * pairing, lựa chọn, khoản tự nhập) → địa điểm → đội → người dùng.
     */
    public void deleteAll() {
        matches.forEach(id -> jdbc.update("DELETE FROM conversation WHERE match_id = ?", id));
        matches.forEach(id -> jdbc.update("DELETE FROM match WHERE id = ?", id));
        venues.forEach(id -> jdbc.update("DELETE FROM venue WHERE id = ?", id));
        teams.forEach(id -> jdbc.update("DELETE FROM team_member WHERE team_id = ?", id));
        teams.forEach(id -> jdbc.update("DELETE FROM team WHERE id = ?", id));
        users.forEach(id -> jdbc.update("DELETE FROM \"user\" WHERE id = ?", id));
    }

    public UUID user(String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO \"user\" (id, display_name) VALUES (?, ?)", id, displayName);
        users.add(id);
        return id;
    }

    /** Đội {@code status} với đội trưởng ACTIVE {@code captainId}. */
    public UUID team(String name, UUID captainId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO team (id, name, created_by, status) VALUES (?, ?, ?, ?)", id, name, captainId, status);
        teams.add(id);
        jdbc.update("INSERT INTO team_member (id, team_id, user_id, role, status) VALUES (?, ?, ?, 'CAPTAIN', 'ACTIVE')",
                UUID.randomUUID(), id, captainId);
        return id;
    }

    public UUID activeTeam(String name, UUID captainId) {
        return team(name, captainId, "ACTIVE");
    }

    public UUID sportId() {
        return jdbc.queryForObject("SELECT id FROM sport ORDER BY created_at, id LIMIT 1", UUID.class);
    }

    public UUID venue(UUID creatorId, String name, String status, LocalDateTime createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO venue (id, name, address, latitude, longitude, status, created_by_type, "
                        + "created_by_user_id, created_at, updated_at, admin_verified_inactive_at) "
                        + "VALUES (?, ?, 'Địa chỉ', 10.1, 106.1, ?, 'USER', ?, ?, ?, ?)",
                id, name, status, creatorId, createdAt, createdAt, "INACTIVE".equals(status) ? createdAt : null);
        venues.add(id);
        return id;
    }

    /** Kèo NEED_VENUE {@code OPEN}; giờ trận = khung sớm nhất {@code start}..{@code end}. */
    public UUID needVenueMatch(UUID authorTeamId, LocalDateTime start, LocalDateTime end, LocalDateTime expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO match (id, type, author_team_id, status, sport_id, match_start_time, match_end_time, "
                        + "expires_at) VALUES (?, 'NEED_VENUE', ?, 'OPEN', ?, ?, ?, ?)",
                id, authorTeamId, sportId(), start, end, expiresAt);
        matches.add(id);
        return id;
    }

    public void venueOption(UUID matchId, UUID venueId, int displayOrder) {
        jdbc.update("INSERT INTO match_venue_option (id, match_id, venue_id, display_order) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), matchId, venueId, displayOrder);
    }

    public UUID timeOption(UUID matchId, LocalDateTime start, LocalDateTime end) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO match_time_option (id, match_id, proposed_start_time, proposed_end_time) "
                + "VALUES (?, ?, ?, ?)", id, matchId, start, end);
        return id;
    }

    public UUID pairing(UUID matchId, UUID interestedTeamId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pairing (id, match_id, interested_team_id, status) VALUES (?, ?, ?, 'NEGOTIATING')",
                id, matchId, interestedTeamId);
        return id;
    }

    public void proposal(UUID pairingId, UUID venueId, UUID timeOptionId) {
        jdbc.update("UPDATE pairing SET proposed_venue_id = ?, proposed_time_option_id = ? WHERE id = ?",
                venueId, timeOptionId, pairingId);
    }

    public UUID conversation(UUID matchId, UUID authorTeamId, UUID interestedTeamId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO conversation (id, context_type, match_id, party_a_type, party_a_id, party_b_type, "
                + "party_b_id) VALUES (?, 'TEAM_MATCH', ?, 'TEAM', ?, 'TEAM', ?)", id, matchId, authorTeamId,
                interestedTeamId);
        return id;
    }

    /** Kèo B đầy đủ: hai đội, một địa điểm ACTIVE, một khung giờ, một pairing NEGOTIATING đã chốt phương án. */
    public Scenario scenario(LocalDateTime now) {
        UUID authorCaptain = user("Đội trưởng A");
        UUID interestedCaptain = user("Đội trưởng B");
        UUID authorTeam = activeTeam("Alpha", authorCaptain);
        UUID interestedTeam = activeTeam("Beta", interestedCaptain);
        LocalDateTime start = now.plusDays(3);
        UUID matchId = needVenueMatch(authorTeam, start, start.plusHours(2), now.plusDays(2));
        UUID venueId = venue(authorCaptain, "Sân X", "ACTIVE", now.minusDays(10));
        venueOption(matchId, venueId, 1);
        UUID timeOptionId = timeOption(matchId, start, start.plusHours(2));
        UUID pairingId = pairing(matchId, interestedTeam);
        proposal(pairingId, venueId, timeOptionId);
        UUID conversationId = conversation(matchId, authorTeam, interestedTeam);
        return new Scenario(authorCaptain, interestedCaptain, authorTeam, interestedTeam, matchId, venueId,
                timeOptionId, pairingId, conversationId, start);
    }

    public record Scenario(UUID authorCaptain, UUID interestedCaptain, UUID authorTeam, UUID interestedTeam,
                           UUID matchId, UUID venueId, UUID timeOptionId, UUID pairingId, UUID conversationId,
                           LocalDateTime start) {
    }
}
