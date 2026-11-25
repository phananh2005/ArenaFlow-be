package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.ConfirmPairingRequest;
import com.phananh.arenaflow.application.dto.request.CostCustomItemRequest;
import com.phananh.arenaflow.application.dto.request.CreatePairingRequest;
import com.phananh.arenaflow.application.dto.request.UpdatePairingCostAgreementRequest;
import com.phananh.arenaflow.application.dto.request.UpdateProposalRequest;
import com.phananh.arenaflow.common.time.AppTime;
import com.phananh.arenaflow.fixture.PairingFixtures;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Số câu SQL thật mỗi endpoint thương lượng (một lần thử), đo trên commit thật để tính cả câu tăng version lúc commit.
 * Ngưỡng: đọc chi tiết ≤ 3, danh sách ≤ 4, ghi ≤ 6.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PairingNfsQueryCountTest {

    @Autowired private PairingService pairingService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManagerFactory entityManagerFactory;
    private Statistics statistics;
    private PairingFixtures fixtures;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        fixtures = new PairingFixtures(jdbcTemplate);
        now = AppTime.now();
    }

    @AfterEach
    void cleanUp() {
        fixtures.deleteAll();
    }

    private long measure(Runnable call) {
        statistics.clear();
        call.run();
        return statistics.getPrepareStatementCount();
    }

    @Test
    void createPairingUsesSixStatementsAndFiveWhenConversationExists() {
        PairingFixtures.Scenario s = fixtures.scenario(now);
        UUID gammaCaptain = fixtures.user("NFS Gamma");
        UUID gamma = fixtures.activeTeam("NFS Gamma Team", gammaCaptain);
        UUID deltaCaptain = fixtures.user("NFS Delta");
        UUID delta = fixtures.activeTeam("NFS Delta Team", deltaCaptain);
        fixtures.conversation(s.matchId(), s.authorTeam(), delta);

        // S kèo, S ngữ cảnh đội, S availability, I pairing, I cuộc trò chuyện, U version kèo lúc commit.
        assertThat(measure(() -> pairingService.createPairing(s.matchId(), gammaCaptain,
                new CreatePairingRequest(gamma)))).isEqualTo(6);
        assertThat(measure(() -> pairingService.createPairing(s.matchId(), deltaCaptain,
                new CreatePairingRequest(delta)))).isEqualTo(5);
    }

    @Test
    void readsStayWithinDetailAndListThresholds() {
        PairingFixtures.Scenario s = fixtures.scenario(now);

        assertThat(measure(() -> pairingService.getPairing(s.pairingId(), s.authorCaptain()))).isEqualTo(2);
        assertThat(measure(() -> pairingService.listMatchPairings(s.matchId(), s.authorCaptain(),
                PairingStatusFilter.ALL, 1, 20))).isEqualTo(3);
        assertThat(measure(() -> pairingService.listTeamPairings(s.interestedTeam(), s.interestedCaptain(),
                PairingRoleFilter.ALL, PairingStatusFilter.ALL, 1, 20))).isEqualTo(3);
    }

    @Test
    void proposalUsesThreeStatementsAndTwoWhenUnchanged() {
        PairingFixtures.Scenario s = fixtures.scenario(now);
        UUID otherTime = fixtures.timeOption(s.matchId(), s.start().plusDays(1), s.start().plusDays(1).plusHours(2));

        assertThat(measure(() -> pairingService.updateProposal(s.pairingId(), s.authorCaptain(),
                new UpdateProposalRequest(s.authorTeam(), 0, s.venueId(), s.timeOptionId())))).isEqualTo(2);
        assertThat(measure(() -> pairingService.updateProposal(s.pairingId(), s.authorCaptain(),
                new UpdateProposalRequest(s.authorTeam(), 0, s.venueId(), otherTime)))).isEqualTo(3);
    }

    @Test
    void costAgreementUsesFiveStatementsAndThreeWhenEmpty() {
        PairingFixtures.Scenario s = fixtures.scenario(now);

        assertThat(measure(() -> pairingService.updateCostAgreement(s.pairingId(), s.authorCaptain(),
                new UpdatePairingCostAgreementRequest(s.authorTeam(), 0, null, null, null, null, null, null,
                        List.of(new CostCustomItemRequest("Trọng tài", 1L)))))).isEqualTo(5);
        assertThat(measure(() -> pairingService.updateCostAgreement(s.pairingId(), s.authorCaptain(),
                new UpdatePairingCostAgreementRequest(s.authorTeam(), 1, null, null, null, null, null, null,
                        List.of())))).isEqualTo(3);
    }

    @Test
    void confirmationUsesTwoStatementsForOneSideAndFiveForCompletion() {
        PairingFixtures.Scenario s = fixtures.scenario(now);

        assertThat(measure(() -> pairingService.confirm(s.pairingId(), s.interestedCaptain(),
                new ConfirmPairingRequest(s.interestedTeam(), 0)))).isEqualTo(2);
        assertThat(measure(() -> pairingService.confirm(s.pairingId(), s.authorCaptain(),
                new ConfirmPairingRequest(s.authorTeam(), 0)))).isEqualTo(5);
    }
}
