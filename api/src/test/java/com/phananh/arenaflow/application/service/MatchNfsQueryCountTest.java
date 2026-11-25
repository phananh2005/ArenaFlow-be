package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CancelMatchRequest;
import com.phananh.arenaflow.application.dto.request.ChatHistoryQuery;
import com.phananh.arenaflow.application.dto.request.CostCustomItemRequest;
import com.phananh.arenaflow.application.dto.request.CreateMatchRequest;
import com.phananh.arenaflow.application.dto.request.CreateTeamRequest;
import com.phananh.arenaflow.application.dto.request.SendConversationMessageRequest;
import com.phananh.arenaflow.application.dto.request.SearchMatchRequest;
import com.phananh.arenaflow.application.dto.request.SendMatchRequestRequest;
import com.phananh.arenaflow.application.dto.request.TimeOptionRequest;
import com.phananh.arenaflow.application.dto.request.UpdateCostAgreementRequest;
import com.phananh.arenaflow.application.dto.response.MatchRequestResponse;
import com.phananh.arenaflow.common.time.AppTime;
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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đếm số câu SQL thật (Hibernate Statistics) của kèo luồng A, luồng B và chat so với ngưỡng 3 (chi tiết) / 4 (danh sách) / 6
 * (ghi). Test chạy trong một transaction rollback: câu chạy lúc commit (tăng version kèo khi gửi yêu cầu, INSERT tin)
 * không có trong số đếm — ghi chú ở từng assert; số đầy đủ đo ở test commit thật.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class MatchNfsQueryCountTest {

    private static final int DETAIL_LIMIT = 3;
    private static final int LIST_LIMIT = 4;
    private static final int WRITE_LIMIT = 6;

    @Autowired private MatchService matchService;
    @Autowired private MatchRequestService matchRequestService;
    @Autowired private ConversationService conversationService;
    @Autowired private TeamService teamService;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Statistics statistics;
    private User alphaCaptain;
    private User betaCaptain;
    private UUID alphaTeam;
    private UUID betaTeam;
    private UUID sportId;
    private UUID venueId;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        alphaCaptain = userRepository.save(User.builder().displayName("NFS Alpha").build());
        betaCaptain = userRepository.save(User.builder().displayName("NFS Beta").build());
        alphaTeam = teamService.createTeam(alphaCaptain.getId(), new CreateTeamRequest("NFS Alpha " + UUID.randomUUID(),
                null, null, null)).teamId();
        betaTeam = teamService.createTeam(betaCaptain.getId(), new CreateTeamRequest("NFS Beta " + UUID.randomUUID(),
                null, null, null)).teamId();
        sportId = jdbcTemplate.queryForObject("SELECT id FROM sport WHERE is_active ORDER BY name LIMIT 1", UUID.class);
        venueId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO venue (id, name, address, latitude, longitude, created_by_type, created_by_user_id) "
                + "VALUES (?, 'Sân NFS', 'Địa chỉ', 10.7, 106.7, 'USER', ?)", venueId, alphaCaptain.getId());
        entityManager.flush();
        entityManager.clear();
    }

    private <T> long count(Supplier<T> call) {
        statistics.clear();
        call.get();
        entityManager.flush();
        long statements = statistics.getPrepareStatementCount();
        entityManager.clear();
        return statements;
    }

    private UUID createMatch() {
        LocalDateTime start = AppTime.now().plusDays(2).truncatedTo(ChronoUnit.HOURS);
        return matchService.createMatch(alphaCaptain.getId(), new CreateMatchRequest(null, alphaTeam, sportId, venueId,
                null, "SEVEN_A_SIDE", start, start.plusHours(2), null, start.minusHours(5), null, null, null)).matchId();
    }

    @Test
    void matchPathsStayWithinBudgets() {
        LocalDateTime start = AppTime.now().plusDays(2).truncatedTo(ChronoUnit.HOURS);
        long create = count(() -> matchService.createMatch(alphaCaptain.getId(), new CreateMatchRequest(null, alphaTeam,
                sportId, venueId, null, "SEVEN_A_SIDE", start, start.plusHours(2), null, start.minusHours(5), null,
                null, null)));
        assertThat(create).as("2.1").isEqualTo(6);

        UUID matchId = createMatch();
        assertThat(count(() -> matchService.getMatch(matchId))).as("2.3").isLessThanOrEqualTo(DETAIL_LIMIT).isEqualTo(2);

        long cost = count(() -> matchService.updateCostAgreement(matchId, alphaCaptain.getId(),
                new UpdateCostAgreementRequest("BOTH", "FIFTY_FIFTY", 1L, 1L, List.of(
                        new CostCustomItemRequest("Trọng tài", 2L), new CostCustomItemRequest("Nước", 3L)))));
        assertThat(cost).as("2.4 có khoản tự nhập (một câu INSERT cho mọi khoản)").isEqualTo(6);

        long send = count(() -> matchRequestService.sendRequest(matchId, betaCaptain.getId(),
                new SendMatchRequestRequest(betaTeam)));
        // Tăng version kèo chạy lúc commit → không đếm ở đây (đầy đủ: 6).
        assertThat(send).as("2.6 lần đầu").isLessThanOrEqualTo(WRITE_LIMIT).isEqualTo(5);

        assertThat(count(() -> matchRequestService.listMatchRequests(matchId, alphaCaptain.getId(),
                MatchRequestStatusFilter.ALL, 1, 20))).as("2.7").isLessThanOrEqualTo(LIST_LIMIT);
        assertThat(count(() -> matchService.listTeamMatches(alphaTeam, MatchStatusFilter.ALL, MatchRoleFilter.ALL, 1, 20)))
                .as("2.11").isLessThanOrEqualTo(LIST_LIMIT);
    }

    private UUID createNeedVenueMatch(UUID secondVenueId) {
        LocalDateTime start = AppTime.now().plusDays(2).truncatedTo(ChronoUnit.HOURS);
        return matchService.createMatch(alphaCaptain.getId(), new CreateMatchRequest("NEED_VENUE", alphaTeam, sportId,
                null, List.of(venueId, secondVenueId), "SEVEN_A_SIDE", null, null,
                List.of(new TimeOptionRequest(start.plusHours(3), start.plusHours(5)),
                        new TimeOptionRequest(start, start.plusHours(2))),
                start.minusHours(5), null, null, null)).matchId();
    }

    private UUID secondVenue() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO venue (id, name, address, latitude, longitude, created_by_type, created_by_user_id) "
                + "VALUES (?, 'Sân NFS 2', 'Địa chỉ 2', 10.71, 106.71, 'USER', ?)", id, alphaCaptain.getId());
        return id;
    }

    @Test
    void needVenuePathsStayWithinBudgets() {
        UUID secondVenueId = secondVenue();
        long create = count(() -> createNeedVenueMatch(secondVenueId));
        assertThat(create).as("2.1 kèo B: đội, bộ môn, địa điểm, INSERT kèo, INSERT lựa chọn địa điểm, INSERT khung giờ")
                .isLessThanOrEqualTo(WRITE_LIMIT).isEqualTo(6);

        UUID matchId = createNeedVenueMatch(secondVenueId);
        entityManager.flush();
        entityManager.clear();
        assertThat(count(() -> matchService.getMatch(matchId))).as("2.3 kèo B chưa ghép: chi tiết + lựa chọn")
                .isLessThanOrEqualTo(DETAIL_LIMIT).isEqualTo(2);
        assertThat(count(() -> matchService.searchMatches(new SearchMatchRequest("NEED_VENUE", sportId, null, null,
                AppTime.now(), null, new BigDecimal("10.71"), new BigDecimal("106.71"), null, null, null))))
                // Trang đầu chưa đầy → Spring Data bỏ câu count (1); trang đầy: trang + count (2).
                .as("2.2 kèo B theo toạ độ + khoảng giờ").isLessThanOrEqualTo(LIST_LIMIT).isBetween(1L, 2L);
        assertThat(count(() -> matchService.listTeamMatches(alphaTeam, MatchStatusFilter.ALL, MatchRoleFilter.ALL, 1, 20)))
                .as("2.11 kèo B: đội tồn tại + trang (+ count khi trang đầy)").isLessThanOrEqualTo(LIST_LIMIT)
                .isBetween(2L, 3L);

        jdbcTemplate.update("INSERT INTO pairing (id, match_id, interested_team_id, status) VALUES (?, ?, ?, 'NEGOTIATING')",
                UUID.randomUUID(), matchId, betaTeam);
        assertThat(count(() -> matchService.cancelMatch(matchId, alphaCaptain.getId(),
                new CancelMatchRequest(alphaTeam, "OPEN", null))))
                .as("2.5 OPEN kèo B: kèo, đội, CAS kèo, thương lượng → SUPERSEDED").isLessThanOrEqualTo(WRITE_LIMIT)
                .isEqualTo(4);
    }

    @Test
    void acceptAndChatPathsStayWithinBudgets() {
        UUID matchId = createMatch();
        MatchRequestResponse request = matchRequestService.sendRequest(matchId, betaCaptain.getId(),
                new SendMatchRequestRequest(betaTeam));
        entityManager.flush();
        entityManager.clear();
        UUID conversationId = request.conversationId();

        assertThat(count(() -> conversationService.send(conversationId, betaCaptain.getId(),
                new SendConversationMessageRequest("Xin chào")))).as("2.16").isEqualTo(2);
        assertThat(count(() -> conversationService.history(conversationId, alphaCaptain.getId(),
                new ChatHistoryQuery(null, null, 50)))).as("2.17").isEqualTo(2);
        assertThat(count(() -> {
            conversationService.markRead(conversationId, alphaCaptain.getId());
            return null;
        })).as("2.18").isEqualTo(2);
        assertThat(count(() -> conversationService.getConversation(conversationId, alphaCaptain.getId()))).as("2.19")
                .isEqualTo(1);
        assertThat(count(() -> conversationService.listMyConversations(alphaCaptain.getId(), 1, 20))).as("2.20")
                .isEqualTo(2);

        assertThat(count(() -> matchRequestService.acceptRequest(request.requestId(), alphaCaptain.getId())))
                .as("2.8").isEqualTo(6);
    }
}
