package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.projection.TeamDetailView;
import com.phananh.arenaflow.domain.projection.VenueDetailView;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Projection native đọc cột TIMESTAMP thành LocalDateTime đúng giá trị đã ghi (giờ Việt Nam, micro giây). */
@Slf4j
class LocalDatetimeProjectionRepositoryTest extends RepositoryIntegrationTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 20, 19, 0, 0, 123_456_000);
    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 11, 20, 23, 30, 5);
    private static final LocalDateTime SELECTED = LocalDateTime.of(2026, 11, 21, 0, 46, 40);

    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void teamDetailProjectionReturnsStoredLocalDateTime() {
        // Ghi nhận TimeZone phiên của ứng dụng (không assert): thiết kế không dựa vào giá trị này.
        log.info("Application session TimeZone = {}",
                jdbcTemplate.queryForObject("SELECT current_setting('TimeZone')", String.class));
        User creator = userRepository.save(User.builder().displayName("Projection Creator").build());
        Team team = teamRepository.save(Team.builder().name("Projection Team").createdBy(creator.getId()).build());
        flushAndClear();
        jdbcTemplate.update("UPDATE team SET created_at = ?, updated_at = ? WHERE id = ?", CREATED, UPDATED, team.getId());

        TeamDetailView detail = teamRepository.findDetailById(team.getId()).orElseThrow();

        assertThat(detail.getCreatedAt()).isEqualTo(CREATED);
        assertThat(detail.getUpdatedAt()).isEqualTo(UPDATED);
        assertThat(detail.getDisbandedAt()).isNull();
    }

    @Test
    void venueDetailProjectionReturnsStoredLocalDateTime() {
        UUID creator = userRepository.save(User.builder().displayName("Projection Venue Creator").build()).getId();
        flushAndClear();
        UUID venueId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO venue (id, name, address, latitude, longitude, status, created_by_type, "
                        + "created_by_user_id, created_at, updated_at, last_selected_at) "
                        + "VALUES (?, 'Projection Venue', 'Address', 10.1, 106.1, 'ACTIVE', 'USER', ?, ?, ?, ?)",
                venueId, creator, CREATED, UPDATED, SELECTED);

        VenueDetailView detail = venueRepository.findDetailById(venueId).orElseThrow();

        assertThat(detail.getCreatedAt()).isEqualTo(CREATED);
        assertThat(detail.getUpdatedAt()).isEqualTo(UPDATED);
        assertThat(detail.getLastSelectedAt()).isEqualTo(SELECTED);
    }
}
