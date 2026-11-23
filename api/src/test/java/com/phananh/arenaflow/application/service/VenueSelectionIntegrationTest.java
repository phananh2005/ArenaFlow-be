package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateVenueRequest;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.common.time.AppTime;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.projection.SelectableVenueView;
import com.phananh.arenaflow.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Chọn địa điểm trên DB thật: đọc dữ liệu hiển thị trong một câu, ghi thời điểm được chọn không lùi và tăng version. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class VenueSelectionIntegrationTest {

    @Autowired private VenueSelectionService venueSelectionService;
    @Autowired private VenueService venueService;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void requireSelectableReadsDisplayDataInOneQueryAndKeepsRequestedOrder() {
        UUID first = venue("Selectable First");
        UUID second = venue("Selectable Second");
        jdbcTemplate.update("INSERT INTO venue_image (id, venue_id, image_url, public_id, is_primary, display_order) "
                + "VALUES (?, ?, 'https://img.test/primary', ?, TRUE, 1)", UUID.randomUUID(), second, "sel/" + UUID.randomUUID());
        Statistics statistics = statistics();

        List<SelectableVenueView> views = venueSelectionService.requireSelectable(List.of(second, first));

        assertThat(statistics.getPrepareStatementCount()).as("requireSelectable").isEqualTo(1);
        assertThat(views).extracting(SelectableVenueView::getId).containsExactly(second, first);
        assertThat(views.get(0).getPrimaryImageUrl()).isEqualTo("https://img.test/primary");
        assertThat(views.get(1).getPrimaryImageUrl()).isNull();
        assertThat(views.get(0).getWardName()).isEqualTo("Selection Ward");
        assertThat(views.get(0).getProvinceName()).isEqualTo("Selection Province");
        assertThat(views.get(0).getStatus()).isEqualTo("ACTIVE");

        jdbcTemplate.update("UPDATE venue SET status = 'INACTIVE', admin_verified_inactive_at = (now() AT TIME ZONE 'Asia/Ho_Chi_Minh') WHERE id = ?", first);
        assertError(() -> venueSelectionService.requireSelectable(List.of(second, first)), ErrorCode.VENUE_002);
        assertError(() -> venueSelectionService.requireSelectable(List.of(second, UUID.randomUUID())), ErrorCode.VENUE_001);
    }

    @Test
    void markSelectedKeepsLatestTimeResetsStaleGateAndBumpsVersionInOneQuery() {
        UUID venueId = venue("Selected Venue");
        LocalDateTime later = LocalDateTime.of(2026, 11, 19, 17, 0);
        LocalDateTime earlier = LocalDateTime.of(2026, 11, 1, 17, 0);
        jdbcTemplate.update("UPDATE venue SET stale_alerted_at = (now() AT TIME ZONE 'Asia/Ho_Chi_Minh') WHERE id = ?", venueId);
        Long versionBefore = jdbcTemplate.queryForObject("SELECT version FROM venue WHERE id = ?", Long.class, venueId);
        Statistics statistics = statistics();

        venueSelectionService.markSelected(List.of(venueId), later);
        assertThat(statistics.getPrepareStatementCount()).as("markSelected").isEqualTo(1);
        venueSelectionService.markSelected(List.of(venueId), earlier);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT last_selected_at, stale_alerted_at, version FROM venue WHERE id = ?", venueId);
        assertThat(((Timestamp) row.get("last_selected_at")).toLocalDateTime()).isEqualTo(later);
        assertThat(row.get("stale_alerted_at")).isNull();
        assertThat((Long) row.get("version")).isEqualTo(versionBefore + 2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void markSelectedRefusesToRunOutsideCallerTransaction() {
        assertThatThrownBy(() -> venueSelectionService.markSelected(List.of(UUID.randomUUID()), AppTime.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private Statistics statistics() {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }

    private UUID venue(String name) {
        User creator = userRepository.save(User.builder().displayName(name + " Creator").build());
        UUID provinceId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level) VALUES (?, 'Selection Province', 'province')",
                provinceId);
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level, parent_id) VALUES (?, 'Selection Ward', 'ward', ?)",
                wardId, provinceId);
        UUID venueId = venueService.createVenue(creator.getId(), new CreateVenueRequest(name, "Address",
                new BigDecimal("10.1"), new BigDecimal("106.1"), wardId, null, null, null)).venueId();
        entityManager.flush();
        return venueId;
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
