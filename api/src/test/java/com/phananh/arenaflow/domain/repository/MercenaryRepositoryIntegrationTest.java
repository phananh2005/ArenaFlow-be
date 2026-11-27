package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.common.time.AppTime;
import com.phananh.arenaflow.domain.entity.mercenary.MercenaryRecruitment;
import com.phananh.arenaflow.domain.projection.GameCancelNoticeView;
import com.phananh.arenaflow.domain.projection.RecruitContextView;
import com.phananh.arenaflow.domain.projection.RecruitmentRowView;
import com.phananh.arenaflow.domain.projection.RecruitmentView;
import com.phananh.arenaflow.domain.projection.ScoreChangeView;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Câu native của tuyển mercenary trên database dev (sau V10) — trận ngoài hệ thống để không phụ thuộc fixture kèo.
 * Gồm bảng kiểm điểm hoàn thành của mercenary khi đội trưởng chấm sao.
 */
@SpringBootTest
@Transactional
class MercenaryRepositoryIntegrationTest {

    @Autowired private MercenaryRecruitmentRepository recruitmentRepository;
    @Autowired private MercenaryApplicationRepository applicationRepository;
    @Autowired private ExternalMatchRepository externalMatchRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final LocalDateTime now = AppTime.now().truncatedTo(ChronoUnit.SECONDS);
    private UUID captainId;
    private UUID teamId;
    private UUID externalMatchId;

    @BeforeEach
    void setUp() {
        captainId = user("Captain");
        teamId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO team (id, name, created_by) VALUES (?, 'Mercenary Repo Team', ?)", teamId,
                captainId);
        jdbcTemplate.update("INSERT INTO team_member (id, team_id, user_id, role, status) "
                + "VALUES (gen_random_uuid(), ?, ?, 'CAPTAIN', 'ACTIVE')", teamId, captainId);
        externalMatchId = externalMatch(now.plusDays(1), "SCHEDULED");
    }

    @Test
    void insertAllWritesVietnameseLabelsAndContextListsActivePositions() {
        List<RecruitmentRowView> rows = recruitmentRepository.insertAll(null, externalMatchId, teamId, captainId,
                new String[] {"Thủ môn", "Tiền vệ"}, new int[] {1, 2}, now);

        assertThat(rows).extracting(RecruitmentRowView::getPosition).containsExactlyInAnyOrder("Thủ môn", "Tiền vệ");
        entityManager.clear();
        MercenaryRecruitment entity = recruitmentRepository.findById(rows.get(0).getId()).orElseThrow();
        assertThat(entity.getPosition().label()).isEqualTo(rows.get(0).getPosition());
        RecruitContextView ctx = externalMatchRepository.findRecruitContext(externalMatchId, captainId).orElseThrow();
        assertThat(ctx.getActivePositions().split("\\|")).containsExactlyInAnyOrder("Thủ môn", "Tiền vệ");
        assertThat(ctx.getCallerCaptain()).isTrue();
        assertThatThrownBy(() -> recruitmentRepository.insertAll(null, externalMatchId, teamId, captainId,
                new String[] {"Tiền vệ"}, new int[] {1}, now)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void recruitmentOfEndedExternalMatchIsEffectivelyExpired() {
        UUID ended = externalMatch(now.minusHours(3), "SCHEDULED");
        UUID recruitmentId = recruitmentRepository.insertAll(null, ended, teamId, captainId, new String[] {"Hậu vệ"},
                new int[] {1}, now.minusDays(1)).get(0).getId();

        RecruitmentView view = recruitmentRepository.findView(recruitmentId, captainId, now, null, null).orElseThrow();

        assertThat(view.getStatus()).isEqualTo("OPEN");
        assertThat(view.getEffectiveStatus()).isEqualTo("EXPIRED");
        assertThat(view.getViewerIsCaptain()).isTrue();
        assertThat(view.getDistanceKm()).isNull();
    }

    @Test
    void cancelNoticeMarksApprovedApplicationsOnce() {
        UUID recruitmentId = recruitmentRepository.insertAll(null, externalMatchId, teamId, captainId,
                new String[] {"Tiền đạo"}, new int[] {1}, now).get(0).getId();
        UUID applicationId = application(recruitmentId, user("Mercenary"), "APPROVED");

        List<GameCancelNoticeView> first = applicationRepository.markGameCancelNotifiedByExternalMatch(externalMatchId,
                now);
        List<GameCancelNoticeView> second = applicationRepository.markGameCancelNotifiedByExternalMatch(externalMatchId,
                now);

        assertThat(first).extracting(GameCancelNoticeView::getId).containsExactly(applicationId);
        assertThat(second).isEmpty();
    }

    /** Bảng kiểm điểm hoàn thành mercenary: (điểm, chuỗi, bộ đếm bùng) trước, có report chưa bác, sao → sau. */
    @ParameterizedTest
    @CsvSource({
            "80, 0, 0, false, 0, 82, 1, 0",
            "80, 4, 1, false, 0, 87, 5, 0",
            "80, 9, 0, false, 1, 93, 10, 0",
            "80, 3, 1, true, 1, 83, 0, 1",
            "99, 4, 0, false, 0, 100, 5, 0"})
    void mercenaryCompletionTable(int score, int streak, int noshow, boolean reported, int bonus, int expectedScore,
                                  int expectedStreak, int expectedNoshow) {
        UUID mercenaryId = user("Completion");
        jdbcTemplate.update("UPDATE \"user\" SET reliability_score = ?, clean_streak = ?, report_count_noshow = ? "
                + "WHERE id = ?", score, streak, noshow, mercenaryId);
        UUID recruitmentId = recruitmentRepository.insertAll(null, externalMatchId, teamId, captainId,
                new String[] {"Thủ môn"}, new int[] {1}, now).get(0).getId();
        UUID applicationId = application(recruitmentId, mercenaryId, "APPROVED");
        if (reported) {
            jdbcTemplate.update("""
                    INSERT INTO report (id, external_match_id, application_id, reporter_type, reporter_id, reported_type,
                                        reported_id, report_type, reason, created_by_user_id)
                    VALUES (gen_random_uuid(), ?, ?, 'TEAM', ?, 'INDIVIDUAL', ?, 'BAD_PLAY', 'Lý do', ?)
                    """, externalMatchId, applicationId, teamId, mercenaryId, captainId);
        }

        ScoreChangeView change = userRepository.applyMercenaryCompletion(mercenaryId, null, externalMatchId, bonus, 2,
                5, 5, 10).orElseThrow();

        assertThat(change.getOldScore()).isEqualTo(score);
        assertThat(change.getNewScore()).isEqualTo(expectedScore);
        assertThat(jdbcTemplate.queryForObject("SELECT clean_streak FROM \"user\" WHERE id = ?", Integer.class,
                mercenaryId)).isEqualTo(expectedStreak);
        assertThat(jdbcTemplate.queryForObject("SELECT report_count_noshow FROM \"user\" WHERE id = ?", Integer.class,
                mercenaryId)).isEqualTo(expectedNoshow);
    }

    private UUID user(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO \"user\" (id, display_name) VALUES (?, ?)", id, name);
        return id;
    }

    private UUID externalMatch(LocalDateTime start, String status) {
        UUID venueId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO venue (id, name, address, latitude, longitude, created_by_type, created_by_user_id)
                VALUES (?, 'Mercenary Repo Venue', 'Address', 10.0, 106.0, 'USER', ?)
                """, venueId, captainId);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO external_match (id, team_id, venue_id, match_start_time, match_end_time, status,
                                            created_by_user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, teamId, venueId, start, start.plusMinutes(90), status, captainId);
        return id;
    }

    private UUID application(UUID recruitmentId, UUID applicantId, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO mercenary_application (id, recruitment_id, applicant_id, status, applied_at, responded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, recruitmentId, applicantId, status, now, "PENDING".equals(status) ? null : now);
        return id;
    }
}
