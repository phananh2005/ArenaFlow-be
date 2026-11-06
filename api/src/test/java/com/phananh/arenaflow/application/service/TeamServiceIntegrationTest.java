package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateTeamRequest;
import com.phananh.arenaflow.application.dto.request.TransferCaptainRequest;
import com.phananh.arenaflow.application.dto.response.CaptainHistoryResponse;
import com.phananh.arenaflow.application.dto.response.TeamResponse;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tạo đội + chuyển quyền trên DB dev (.env.dev); rollback sau test. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class TeamServiceIntegrationTest {

    @Autowired private TeamService teamService;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void createsTeamThenTransfersCaptainWithinQueryLimits() {
        User captain = saveUser("Captain IT");
        User member = saveUser("Member IT");
        entityManager.flush();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        TeamResponse created = teamService.createTeam(captain.getId(),
                new CreateTeamRequest("Team IT", null, null, List.of("Chill", "Thi đấu")));
        entityManager.flush();

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6); // S, I, I, I
        assertThat(created.captainName()).isEqualTo("Captain IT");
        assertThat(created.memberCount()).isEqualTo(1);

        TeamMember joined = new TeamMember();
        joined.setTeamId(created.teamId());
        joined.setUserId(member.getId());
        joined.setRole(TeamMember.Role.MEMBER);
        joined.setStatus(TeamMember.Status.ACTIVE);
        joined.setJoinedAt(OffsetDateTime.now().plusSeconds(1));
        teamMemberRepository.save(joined);
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        teamService.transferCaptain(created.teamId(), captain.getId(), new TransferCaptainRequest(member.getId(), "Bận"));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6); // S, S, S, U, U, I
        entityManager.flush();
        entityManager.clear();
        TeamResponse after = teamService.getTeam(created.teamId());
        assertThat(after.captainId()).isEqualTo(member.getId());
        assertThat(after.skillLevels()).containsExactly("Chill", "Thi đấu");
        List<CaptainHistoryResponse> history = teamService.listCaptainHistory(created.teamId(), 1, 20).data();
        assertThat(history).extracting(CaptainHistoryResponse::toUserId).containsExactly(member.getId(), captain.getId());
        assertThat(history.getFirst().reason()).isEqualTo("Bận");
    }

    private User saveUser(String name) {
        User user = new User();
        user.setDisplayName(name);
        return userRepository.save(user);
    }
}
