package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.response.InvitationCandidatesResponse;
import com.phananh.arenaflow.application.support.InvitationSearchRateLimiter;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.testsupport.UserDiscoveryFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Đếm số câu SQL THẬT (Hibernate Statistics) của tra cứu người dùng. Danh sách đo với trang đầy (pageSize = 1, ≥ 2
 * dòng) để Spring Data không bỏ câu COUNT. Tìm theo tên đo cả khi rất nhiều người khớp: số câu không đổi.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class UserDiscoveryNfsQueryCountTest {

    private static final UUID HCM_PROVINCE = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID BEN_NGHE_WARD = UUID.fromString("10000000-0000-7000-8000-000000000001");

    @Autowired private AdministrativeUnitService administrativeUnitService;
    @Autowired private TeamMembershipService membershipService;
    @Autowired private TeamService teamService;
    @Autowired private InvitationSearchRateLimiter rateLimiter;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private UserDiscoveryFixtures fixtures;
    private String word;
    private UUID captain;
    private UUID member;
    private UUID team;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        fixtures = new UserDiscoveryFixtures(jdbcTemplate);
        word = UserDiscoveryFixtures.uniqueWord();
        captain = fixtures.user("Captain " + word);
        member = fixtures.user("Member " + word);
        team = fixtures.team("Team " + word, captain);
        fixtures.activeMember(team, member, UserDiscoveryFixtures.DEFAULT_TIME);
        fixtures.user(word + " Một", UserDiscoveryFixtures.uniquePhone(), "USER");
        fixtures.user(word + " Hai");
    }

    private long measure(Runnable call) {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        try {
            call.run();
        } catch (BusinessException ignored) {
            // Đo cả nhánh lỗi nghiệp vụ.
        }
        return statistics.getPrepareStatementCount();
    }

    @Test
    void catalogEndpointsUseOneQuery() {
        assertThat(measure(administrativeUnitService::listProvinces)).as("GET /provinces").isEqualTo(1);
        assertThat(measure(() -> administrativeUnitService.listWards(HCM_PROVINCE)))
                .as("GET /provinces/{id}/wards").isEqualTo(1);
        assertThat(measure(() -> administrativeUnitService.listWards(BEN_NGHE_WARD)))
                .as("GET /provinces/{id}/wards → USER_004").isEqualTo(1);
    }

    @Test
    void invitationSearchStaysWithinListLimit() {
        String phone = jdbcTemplate.queryForObject(
                "SELECT phone_number FROM \"user\" WHERE display_name = ?", String.class, word + " Một");

        assertThat(measure(() -> membershipService.searchInvitationCandidates(team, captain, phone)))
                .as("2.3 số điện thoại").isEqualTo(3);
        assertThat(measure(() -> membershipService.searchInvitationCandidates(team, captain, word)))
                .as("2.3 tên").isEqualTo(4);
        assertThat(measure(() -> membershipService.searchInvitationCandidates(team, captain, "+84 12345")))
                .as("2.3 USER_005").isZero();
        assertThat(measure(() -> membershipService.searchInvitationCandidates(team, captain, "a!")))
                .as("2.3 USER_006").isEqualTo(1);
        assertThat(measure(() -> membershipService.searchInvitationCandidates(team, member, word)))
                .as("2.3 TEAM_002 (tên)").isEqualTo(4);
        assertThat(measure(() -> rateLimiter.acquire(UUID.randomUUID())))
                .as("giới hạn số lần gọi không truy vấn DB").isZero();
    }

    @Test
    void rateLimitRejectionUsesNoQuery() {
        UUID caller = UUID.randomUUID();
        IntStream.range(0, 30).forEach(i -> rateLimiter.acquire(caller));

        assertThat(measure(() -> assertThatThrownBy(() -> rateLimiter.acquire(caller))
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.USER_007)))
                .as("2.3 USER_007").isZero();
    }

    @Test
    void nameSearchQueryCountDoesNotGrowWithMatches() {
        List<Object[]> rows = IntStream.range(0, 3_000)
                .mapToObj(i -> new Object[]{UUID.randomUUID(), "Nguyễn " + (i % 4 == 0 ? "Văn " : "") + word + " " + i})
                .toList();
        jdbcTemplate.batchUpdate("INSERT INTO \"user\" (id, display_name) VALUES (?, ?)", rows);

        assertThat(measure(() -> membershipService.searchInvitationCandidates(team, captain, word)))
                .as("2.3 tên, 3.000 người khớp").isEqualTo(4);
        InvitationCandidatesResponse result = membershipService.searchInvitationCandidates(team, captain,
                "nguyen " + word);
        assertThat(result.items()).hasSize(20);
        assertThat(result.hasMore()).isTrue();
        // Tên bắt đầu bằng "nguyen zq…" (đã bỏ dấu) xếp trước mọi tên "Nguyễn Văn …".
        assertThat(result.items()).allSatisfy(item -> assertThat(item.displayName()).startsWith("Nguyễn " + word));
    }

    @Test
    void myListsStayWithinListLimitOnFullPage() {
        UUID me = fixtures.user("NFS Me " + word);
        fixtures.invite(team, me, "2026-11-10 08:00:00");
        UUID other = fixtures.team("Other " + word, fixtures.user("Other Captain " + word));
        fixtures.invite(other, me, "2026-11-11 08:00:00");
        fixtures.team("Mine " + word, me);
        UUID third = fixtures.team("Third " + word, fixtures.user("Third Captain " + word));
        fixtures.activeMember(third, me, UserDiscoveryFixtures.DEFAULT_TIME);

        assertThat(measure(() -> membershipService.listMyInvitations(me, 1, 1)))
                .as("GET /profile/invitations trang đầy").isEqualTo(3);
        assertThat(measure(() -> membershipService.listMyInvitations(me, 1, 20)))
                .as("GET /profile/invitations trang chưa đầy").isEqualTo(2);
        assertThat(measure(() -> teamService.listMyTeams(me, null, 1, 1)))
                .as("GET /profile/teams trang đầy").isEqualTo(3);
        assertThat(measure(() -> teamService.listMyTeams(me, MyTeamRoleFilter.CAPTAIN, 1, 20)))
                .as("GET /profile/teams?role=CAPTAIN trang chưa đầy").isEqualTo(2);
    }
}
