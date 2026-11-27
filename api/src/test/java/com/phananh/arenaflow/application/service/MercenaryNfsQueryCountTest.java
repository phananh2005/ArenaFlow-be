package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.AddRecruitmentsRequest;
import com.phananh.arenaflow.application.dto.request.CreateExternalMatchRequest;
import com.phananh.arenaflow.application.dto.request.RecruitmentPositionRequest;
import com.phananh.arenaflow.application.dto.request.UpdateExternalMatchRequest;
import com.phananh.arenaflow.application.dto.request.WithdrawApplicationRequest;
import com.phananh.arenaflow.application.dto.response.ExternalMatchResponse;
import com.phananh.arenaflow.common.time.AppTime;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đo số câu SQL thật (Hibernate Statistics) cho luồng tuyển mercenary trên trận ngoài hệ thống. Test chạy trong một
 * transaction nên phần refresh huy hiệu sau commit không được đếm ở đây (ghi chú ở từng ca).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class MercenaryNfsQueryCountTest {

    private static final int DETAIL_LIMIT = 3;
    private static final int LIST_LIMIT = 4;
    private static final int WRITE_LIMIT = 6;

    @Autowired private ExternalMatchService externalMatchService;
    @Autowired private MercenaryRecruitmentService recruitmentService;
    @Autowired private MercenaryApplicationService applicationService;
    @Autowired private UserReputationService userReputationService;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Statistics statistics;
    private UUID captainId;
    private UUID mercenaryId;
    private UUID teamId;
    private UUID venueId;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        captainId = user("NFS Mercenary Captain");
        mercenaryId = user("NFS Mercenary");
        teamId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO team (id, name, created_by) VALUES (?, 'NFS Mercenary Team', ?)", teamId,
                captainId);
        jdbcTemplate.update("INSERT INTO team_member (id, team_id, user_id, role, status) "
                + "VALUES (gen_random_uuid(), ?, ?, 'CAPTAIN', 'ACTIVE')", teamId, captainId);
        UUID provinceId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level) VALUES (?, 'NFS Province', 'province')",
                provinceId);
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level, parent_id) "
                + "VALUES (?, 'NFS Ward', 'ward', ?)", wardId, provinceId);
        venueId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO venue (id, name, address, latitude, longitude, created_by_type, created_by_user_id, ward_id)
                VALUES (?, 'NFS Mercenary Venue', 'Address', 10.7, 106.7, 'USER', ?, ?)
                """, venueId, captainId, wardId);
    }

    @Test
    void externalMatchAndApplicationPathsStayWithinBudgets() {
        var start = AppTime.now().plusDays(2);
        CreateExternalMatchRequest create = new CreateExternalMatchRequest(venueId, start, start.plusHours(2),
                List.of(), "FC NFS", List.of(new RecruitmentPositionRequest("Tiền vệ", 2)));

        ExternalMatchResponse[] created = new ExternalMatchResponse[1];
        assertThat(measure(() -> created[0] = externalMatchService.createExternalMatch(teamId, captainId, create)))
                .as("POST /teams/{id}/external-matches").isEqualTo(5).isLessThanOrEqualTo(WRITE_LIMIT);
        UUID externalMatchId = created[0].externalMatchId();
        UUID recruitmentId = created[0].recruitments().get(0).recruitmentId();

        assertThat(measure(() -> recruitmentService.addExternalRecruitments(externalMatchId, captainId,
                new AddRecruitmentsRequest(List.of(new RecruitmentPositionRequest("Thủ môn", 1))))))
                .as("POST /external-matches/{id}/mercenary-recruitments").isEqualTo(2);
        assertThat(measure(() -> externalMatchService.getExternalMatch(externalMatchId, captainId)))
                .as("GET /external-matches/{id}").isLessThanOrEqualTo(DETAIL_LIMIT);
        assertThat(measure(() -> externalMatchService.listTeamExternalMatches(teamId, captainId,
                ExternalMatchStatusFilter.ALL, 1, 20))).as("GET /teams/{id}/external-matches")
                .isLessThanOrEqualTo(LIST_LIMIT);
        assertThat(measure(() -> externalMatchService.updateExternalMatch(externalMatchId, captainId,
                new UpdateExternalMatchRequest(null, null, null, null, "FC NFS 2"))))
                .as("PATCH /external-matches/{id} (chỉ tên đối thủ)").isEqualTo(2);
        assertThat(measure(() -> recruitmentService.getRecruitment(recruitmentId, mercenaryId, null, null)))
                .as("GET /mercenary-recruitments/{id}").isEqualTo(1);

        UUID[] applicationId = new UUID[1];
        assertThat(measure(() -> applicationId[0] = applicationService.apply(recruitmentId, mercenaryId)
                .applicationId())).as("POST /mercenary-recruitments/{id}/applications").isEqualTo(3);
        assertThat(measure(() -> applicationService.listApplicants(recruitmentId, captainId,
                ApplicationStatusFilter.ALL, 1, 20))).as("GET /mercenary-recruitments/{id}/applications")
                .isLessThanOrEqualTo(LIST_LIMIT);
        assertThat(measure(() -> applicationService.getApplication(applicationId[0], mercenaryId)))
                .as("GET /mercenary-applications/{id}").isEqualTo(1);
        assertThat(measure(() -> applicationService.approve(applicationId[0], captainId)))
                .as("POST /mercenary-applications/{id}/approve").isEqualTo(3);
        // Q1 context, U tin, U đơn, U điểm; refresh huy hiệu chạy sau commit (không đếm trong transaction test).
        assertThat(measure(() -> applicationService.withdraw(applicationId[0], mercenaryId,
                new WithdrawApplicationRequest("APPROVED")))).as("POST /mercenary-applications/{id}/withdraw")
                .isEqualTo(4);
        assertThat(measure(() -> userReputationService.getUserReputation(mercenaryId)))
                .as("GET /users/{id}/reputation").isEqualTo(1);
        assertThat(measure(() -> externalMatchService.cancelExternalMatch(externalMatchId, captainId)))
                .as("POST /external-matches/{id}/cancel").isEqualTo(5);
    }

    private UUID user(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO \"user\" (id, display_name) VALUES (?, ?)", id, name);
        return id;
    }

    private long measure(Supplier<?> operation) {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        operation.get();
        entityManager.flush();
        return statistics.getPrepareStatementCount();
    }
}
