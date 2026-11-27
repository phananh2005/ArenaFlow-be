package com.phananh.arenaflow.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hợp đồng hồi quy cho Flyway V7 (kèo luồng A + chat dùng chung). Chạy trên database dev sau V6.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class MatchFlowAMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesMatchFlowASchema() {
        assertThat(columnExists("match", "version")).isTrue();
        assertThat(columnExists("match", "skill_levels")).isTrue();
        assertThat(columnExists("match", "cancel_reason")).isTrue();
        assertThat(columnExists("match_request", "reason")).isTrue();
        assertThat(columnExists("cost_custom_item", "display_order")).isTrue();
        assertThat(tableExists("chat_message")).isFalse();
        assertThat(tableExists("conversation")).isTrue();
        assertThat(tableExists("conversation_message")).isTrue();
        assertThat(indexExists("idx_match_open_start")).isTrue();
        assertThat(indexExists("idx_match_opponent_status")).isTrue();
        assertThat(indexExists("idx_match_request_accepted")).isTrue();
        assertThat(indexExists("idx_cost_custom_item_order")).isTrue();
        assertThat(indexExists("idx_cost_custom_item_match")).isFalse();
        assertThat(indexExists("idx_conversation_team_match")).isTrue();
        assertThat(indexExists("idx_conversation_party_a")).isTrue();
        assertThat(indexExists("idx_conversation_party_b")).isTrue();
        assertThat(indexExists("idx_team_disbanded_by")).isTrue();
        assertThat(indexExists("idx_conversation_message_time")).isTrue();
        assertThat(indexExists("idx_conversation_message_unread")).isTrue();
        assertThat(dataType("conversation", "created_at")).isEqualTo("timestamp without time zone");
        assertThat(dataType("conversation_message", "created_at")).isEqualTo("timestamp without time zone");
    }

    @Test
    @Transactional
    void enforcesMatchFormatAndCostChecks() {
        UUID matchId = insertMatch();

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE match SET format = 'SIX' WHERE id = ?", matchId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("match_format_check");
    }

    @Test
    @Transactional
    void rejectsSecondAcceptedRequestOfSameMatch() {
        UUID matchId = insertMatch();
        jdbcTemplate.update("INSERT INTO match_request (id, match_id, requester_team_id, status, responded_at) "
                + "VALUES (?, ?, ?, 'ACCEPTED', now() AT TIME ZONE 'Asia/Ho_Chi_Minh')",
                UUID.randomUUID(), matchId, insertTeam(insertUser()));

        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO match_request (id, match_id, requester_team_id, "
                        + "status, responded_at) VALUES (?, ?, ?, 'ACCEPTED', now() AT TIME ZONE 'Asia/Ho_Chi_Minh')",
                UUID.randomUUID(), matchId, insertTeam(insertUser())))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("idx_match_request_accepted");
    }

    @Test
    @Transactional
    void conversationIsUniquePerMatchAndJoiningTeamAndMessageKeepsSender() {
        UUID userId = insertUser();
        UUID matchId = insertMatch();
        UUID teamB = insertTeam(userId);
        UUID authorTeam = jdbcTemplate.queryForObject("SELECT author_team_id FROM match WHERE id = ?", UUID.class, matchId);
        UUID conversationId = insertConversation(matchId, authorTeam, teamB);

        assertThatThrownBy(() -> insertConversation(matchId, authorTeam, teamB))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("idx_conversation_team_match");

        jdbcTemplate.update("INSERT INTO conversation_message (id, conversation_id, sender_party, sender_user_id, message) "
                + "VALUES (?, ?, 'B', ?, 'Xin chào')", UUID.randomUUID(), conversationId, userId);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM \"user\" WHERE id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void rejectsEmptyAndTooLongMessages() {
        UUID userId = insertUser();
        UUID matchId = insertMatch();
        UUID authorTeam = jdbcTemplate.queryForObject("SELECT author_team_id FROM match WHERE id = ?", UUID.class, matchId);
        UUID conversationId = insertConversation(matchId, authorTeam, insertTeam(userId));

        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO conversation_message (id, conversation_id, sender_party, "
                + "sender_user_id, message) VALUES (?, ?, 'A', ?, ?)", UUID.randomUUID(), conversationId, userId,
                "x".repeat(1001))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertConversation(UUID matchId, UUID teamA, UUID teamB) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO conversation (id, context_type, match_id, party_a_type, party_a_id, party_b_type, "
                + "party_b_id) VALUES (?, 'TEAM_MATCH', ?, 'TEAM', ?, 'TEAM', ?)", id, matchId, teamA, teamB);
        return id;
    }

    private UUID insertMatch() {
        UUID authorTeam = insertTeam(insertUser());
        UUID sportId = jdbcTemplate.queryForObject("SELECT id FROM sport ORDER BY name LIMIT 1", UUID.class);
        UUID venueId = insertVenue(insertUser());
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO match (id, type, author_team_id, status, sport_id, format, venue_id, "
                + "match_start_time, match_end_time, expires_at) VALUES (?, 'HAS_VENUE', ?, 'OPEN', ?, 'SEVEN_A_SIDE', ?, "
                + "TIMESTAMP '2030-01-02 18:00', TIMESTAMP '2030-01-02 20:00', TIMESTAMP '2030-01-02 12:00')",
                id, authorTeam, sportId, venueId);
        return id;
    }

    private UUID insertVenue(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO venue (id, name, address, latitude, longitude, created_by_type, created_by_user_id) "
                + "VALUES (?, 'Sân test', 'Địa chỉ', 10.7, 106.7, 'USER', ?)", id, userId);
        return id;
    }

    private UUID insertTeam(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO team (id, name, created_by) VALUES (?, ?, ?)", id, "Team " + id, userId);
        return id;
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO \"user\" (id, display_name) VALUES (?, 'Người test')", id);
        return id;
    }

    private boolean columnExists(String table, String column) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?)", Boolean.class, table, column));
    }

    private boolean tableExists(String table) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema = current_schema() AND table_name = ?)", Boolean.class, table));
    }

    private boolean indexExists(String index) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = current_schema() AND indexname = ?)",
                Boolean.class, index));
    }

    private String dataType(String table, String column) {
        return jdbcTemplate.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema = "
                + "current_schema() AND table_name = ? AND column_name = ?", String.class, table, column);
    }
}
