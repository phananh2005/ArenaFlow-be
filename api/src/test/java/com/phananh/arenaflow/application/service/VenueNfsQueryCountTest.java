package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateVenueRequest;
import com.phananh.arenaflow.application.dto.request.SearchVenueRequest;
import com.phananh.arenaflow.application.dto.request.UpdateVenueRequest;
import com.phananh.arenaflow.application.support.security.CurrentActor;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.UserRepository;
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

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures real SQL through Hibernate Statistics for VENUE core API service paths.
 * The list fixture has more rows than its page size, forcing Spring Data to execute the count query.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class VenueNfsQueryCountTest {

    private static final int DETAIL_LIMIT = 3;
    private static final int LIST_LIMIT = 4;
    private static final int WRITE_LIMIT = 6;

    @Autowired private VenueService venueService;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Statistics statistics;
    private User creator;
    private UUID wardId;
    private UUID venueId;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        creator = userRepository.save(User.builder().displayName("Venue NFS Creator").build());
        wardId = insertWard();
        entityManager.flush();
        entityManager.clear();

        venueId = venueService.createVenue(creator.getId(), createRequest("Venue NFS One")).venueId();
        venueService.createVenue(creator.getId(), createRequest("Venue NFS Two"));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void coreVenuePathsStayWithinApprovedQueryBudgetsWithoutNPlusOne() {
        assertThat(measure(() -> venueService.createVenue(creator.getId(), createRequest("Venue NFS Three"))))
                .as("POST /api/v1/venues")
                .isEqualTo(3)
                .isLessThanOrEqualTo(WRITE_LIMIT);

        assertThat(measure(() -> venueService.listVenues(new SearchVenueRequest(
                null, null, null, null, null, 1, 2))))
                .as("GET /api/v1/venues")
                .isEqualTo(2)
                .isLessThanOrEqualTo(LIST_LIMIT);

        assertThat(measure(() -> venueService.getVenue(venueId)))
                .as("GET /api/v1/venues/{venueId}")
                .isEqualTo(2)
                .isLessThanOrEqualTo(DETAIL_LIMIT);

        assertThat(measure(() -> venueService.updateVenue(venueId, new CurrentActor(creator.getId(), false),
                new UpdateVenueRequest("Venue NFS Updated", null, null, null, null, null, null, null))))
                .as("PATCH /api/v1/venues/{venueId}")
                .isBetween(3L, 5L)
                .isLessThanOrEqualTo(WRITE_LIMIT);
    }

    private long measure(Supplier<?> operation) {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        operation.get();
        entityManager.flush();
        return statistics.getPrepareStatementCount();
    }

    private CreateVenueRequest createRequest(String name) {
        return new CreateVenueRequest(name, "NFS Address", new BigDecimal("10.7881234"),
                new BigDecimal("106.6991234"), wardId, null, null, null);
    }

    private UUID insertWard() {
        UUID provinceId = UUID.randomUUID();
        UUID newWardId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level) VALUES (?, 'NFS Province', 'province')",
                provinceId);
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level, parent_id) VALUES (?, 'NFS Ward', 'ward', ?)",
                newWardId, provinceId);
        return newWardId;
    }
}
