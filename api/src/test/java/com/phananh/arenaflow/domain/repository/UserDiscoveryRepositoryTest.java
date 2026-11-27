package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.projection.InvitationCandidateView;
import com.phananh.arenaflow.testsupport.UserDiscoveryFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tìm người để mời theo tên / số điện thoại. Mỗi test dùng một từ ngẫu nhiên trong tên để không lẫn dữ liệu sẵn có
 * của DB.
 */
class UserDiscoveryRepositoryTest extends RepositoryIntegrationTest {

    private static final int LIMIT = 21;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserDiscoveryFixtures fixtures;
    private String word;
    private UUID captain;
    private UUID team;

    @BeforeEach
    void setUp() {
        fixtures = new UserDiscoveryFixtures(jdbcTemplate);
        word = UserDiscoveryFixtures.uniqueWord();
        captain = fixtures.user("Captain " + word);
        team = fixtures.team("Team " + word, captain);
    }

    private List<InvitationCandidateView> searchByName(UUID callerId, String folded) {
        String[] tokens = folded.split(" ");
        String tsQuery = String.join(" & ", Arrays.stream(tokens).map(token -> token + ":*").toList());
        return userRepository.searchCandidatesByName(team, callerId, tsQuery, folded, LIMIT);
    }

    private List<String> names(List<InvitationCandidateView> rows) {
        return rows.stream().map(InvitationCandidateView::getDisplayName).toList();
    }

    @Test
    void foldNameUsesDatabaseFunction() {
        assertThat(userRepository.foldName("  ĐẶNG  Thị-Ổi!! ")).isEqualTo("dang thi oi");
        assertThat(userRepository.foldName("ng.an")).isEqualTo("ng an");
    }

    @Test
    void matchesWordPrefixesIgnoringAccentsAndOrdersStartsWithFirstThenVietnameseCollation() {
        fixtures.user(word + " Văn An");
        fixtures.user("Lan " + word);
        fixtures.user("Ánh " + word);
        fixtures.user("X" + word + " Khác");

        List<String> result = names(searchByName(captain, word));

        // Tên bắt đầu bằng chuỗi tìm xếp trước dù ICU xếp "Lan…"/"Ánh…" trước "zq…".
        assertThat(result).containsExactly(word + " Văn An", "Ánh " + word, "Lan " + word);
    }

    @Test
    void everyInputWordMustMatchTheStartOfSomeWordInName() {
        fixtures.user("An Phạm " + word);
        fixtures.user("Nguyễn Văn An " + word);
        fixtures.user("Trần An " + word);
        fixtures.user("Lan Nguyễn " + word);

        assertThat(names(searchByName(captain, "an " + word)))
                .containsExactly("An Phạm " + word, "Nguyễn Văn An " + word, "Trần An " + word);
        assertThat(names(searchByName(captain, "nguyen an " + word))).containsExactly("Nguyễn Văn An " + word);
        assertThat(names(searchByName(captain, "ng an " + word))).containsExactly("Nguyễn Văn An " + word);
    }

    @Test
    void excludesDeletedUsersCallerAndAdmins() {
        UUID deleted = fixtures.user("Deleted " + word);
        fixtures.softDelete(deleted);
        fixtures.user("Admin " + word, null, "ADMIN");
        fixtures.user("Visible " + word);

        assertThat(names(searchByName(captain, word))).containsExactly("Visible " + word);
    }

    @Test
    void returnsAtMostLimitRowsSoServiceCanDetectMore() {
        IntStream.rangeClosed(1, 25).forEach(i -> fixtures.user(word + " Người " + String.format("%02d", i)));

        List<InvitationCandidateView> rows = searchByName(captain, word);

        assertThat(rows).hasSize(LIMIT);
        assertThat(rows.getFirst().getDisplayName()).isEqualTo(word + " Người 01");
    }

    @Test
    void labelsMembershipOfTheSearchedTeamOnly() {
        UUID active = fixtures.user("Active " + word);
        UUID pending = fixtures.user("Pending " + word);
        UUID otherTeamMember = fixtures.user("Other " + word);
        fixtures.activeMember(team, active, UserDiscoveryFixtures.DEFAULT_TIME);
        fixtures.invite(team, pending, UserDiscoveryFixtures.DEFAULT_TIME);
        UUID otherCaptain = fixtures.user("Other Captain " + word);
        UUID otherTeam = fixtures.team("Other Team " + word, otherCaptain);
        fixtures.activeMember(otherTeam, otherTeamMember, UserDiscoveryFixtures.DEFAULT_TIME);

        List<InvitationCandidateView> rows = searchByName(captain, word);

        assertThat(rows).filteredOn(row -> row.getUserId().equals(active))
                .singleElement().extracting(InvitationCandidateView::getMembershipStatus).isEqualTo("ACTIVE");
        assertThat(rows).filteredOn(row -> row.getUserId().equals(pending))
                .singleElement().extracting(InvitationCandidateView::getMembershipStatus).isEqualTo("PENDING");
        assertThat(rows).filteredOn(row -> row.getUserId().equals(otherTeamMember))
                .singleElement().extracting(InvitationCandidateView::getMembershipStatus).isNull();
        assertThat(rows).filteredOn(row -> row.getUserId().equals(otherCaptain))
                .singleElement().extracting(InvitationCandidateView::getMembershipStatus).isNull();
    }

    @Test
    void findsByExactPhoneExcludingDeletedCallerAndAdmin() {
        String phone = UserDiscoveryFixtures.uniquePhone();
        UUID target = fixtures.user("Phone " + word, phone, "USER");

        assertThat(userRepository.searchCandidatesByPhone(team, captain, phone, LIMIT))
                .singleElement().extracting(InvitationCandidateView::getUserId).isEqualTo(target);

        fixtures.softDelete(target);
        assertThat(userRepository.searchCandidatesByPhone(team, captain, phone, LIMIT)).isEmpty();

        String adminPhone = UserDiscoveryFixtures.uniquePhone();
        fixtures.user("Admin Phone " + word, adminPhone, "ADMIN");
        assertThat(userRepository.searchCandidatesByPhone(team, captain, adminPhone, LIMIT)).isEmpty();

        String selfPhone = UserDiscoveryFixtures.uniquePhone();
        jdbcTemplate.update("UPDATE \"user\" SET phone_number = ? WHERE id = ?", selfPhone, captain);
        assertThat(userRepository.searchCandidatesByPhone(team, captain, selfPhone, LIMIT)).isEmpty();
    }

    @Test
    void userWithoutPhoneIsFoundByName() {
        fixtures.user("Nophone " + word);

        assertThat(names(searchByName(captain, "nophone " + word))).containsExactly("Nophone " + word);
    }

    @Test
    void existsOnlyForNotDeletedUser() {
        UUID active = fixtures.user("Exists " + word);
        UUID deleted = fixtures.user("Gone " + word);
        fixtures.softDelete(deleted);

        assertThat(userRepository.existsByIdAndDeletedAtIsNull(active)).isTrue();
        assertThat(userRepository.existsByIdAndDeletedAtIsNull(deleted)).isFalse();
        assertThat(userRepository.existsByIdAndDeletedAtIsNull(UUID.randomUUID())).isFalse();
    }

    @Test
    void namePlanWithBindParametersUsesGinIndex() {
        // Kế hoạch generic (tham số bind) phải dùng idx_user_name_search khi bảng đủ lớn để không quét tuần tự.
        jdbcTemplate.execute("SET LOCAL plan_cache_mode = force_generic_plan");
        jdbcTemplate.execute("""
                PREPARE user_name_search_probe(uuid, uuid, text, text, int) AS
                SELECT u.id FROM "user" u
                LEFT JOIN team_member m
                       ON m.team_id = $1 AND m.user_id = u.id AND m.status IN ('PENDING', 'ACTIVE')
                WHERE user_name_search_vector(u.display_name) @@ to_tsquery('simple', $3)
                  AND u.deleted_at IS NULL AND u.role <> 'ADMIN' AND u.id <> $2
                ORDER BY starts_with(user_name_fold(u.display_name), $4) DESC,
                         u.display_name COLLATE "vi-VN-x-icu", u.id
                LIMIT $5
                """);
        try {
            jdbcTemplate.execute("SET LOCAL enable_seqscan = off");
            List<String> plan = jdbcTemplate.queryForList(
                    "EXPLAIN EXECUTE user_name_search_probe('" + team + "', '" + captain + "', 'an:*', 'an', 21)",
                    String.class);
            assertThat(String.join("\n", plan)).contains("idx_user_name_search");
        } finally {
            jdbcTemplate.execute("DEALLOCATE user_name_search_probe");
        }
    }
}
