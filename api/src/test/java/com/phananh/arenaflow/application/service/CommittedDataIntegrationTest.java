package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateTeamRequest;
import com.phananh.arenaflow.application.dto.request.InviteMemberRequest;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Integration test KHÔNG {@code @Transactional}: mỗi service call commit thật trên DB dev (.env.dev) — cần cho race
 * (2 transaction song song) và đếm câu SQL lúc commit (OPTIMISTIC_FORCE_INCREMENT). Mọi đội/user tạo qua fixture
 * được ghi lại và xoá trong {@link #deleteCommittedFixtures()} (team → cascade team_member, team_captain_history).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
abstract class CommittedDataIntegrationTest {

    @Autowired protected TeamService teamService;
    @Autowired protected TeamMembershipService membershipService;
    @Autowired protected AdminTeamService adminTeamService;
    @Autowired protected ProfileService profileService;
    @Autowired protected UserRepository userRepository;
    @Autowired protected JdbcTemplate jdbcTemplate;

    private final List<UUID> createdUsers = new ArrayList<>();
    private final List<UUID> createdTeams = new ArrayList<>();

    @AfterEach
    void deleteCommittedFixtures() {
        createdTeams.forEach(teamId -> jdbcTemplate.update("DELETE FROM team WHERE id = ?", teamId));
        createdUsers.forEach(userId -> jdbcTemplate.update("DELETE FROM \"user\" WHERE id = ?", userId));
    }

    protected User user(String displayName) {
        User user = new User();
        user.setDisplayName(displayName);
        User saved = userRepository.save(user);
        createdUsers.add(saved.getId());
        return saved;
    }

    /** Đội ACTIVE với {@code captain} là captain ACTIVE (qua service thật). */
    protected UUID team(User captain) {
        UUID teamId = teamService.createTeam(captain.getId(),
                new CreateTeamRequest("Committed " + captain.getDisplayName(), null, null, null)).teamId();
        createdTeams.add(teamId);
        return teamId;
    }

    protected UUID invite(UUID teamId, User captain, User invitee) {
        return membershipService.inviteMember(teamId, captain.getId(), new InviteMemberRequest(invitee.getId()))
                .invitationId();
    }

    protected void join(UUID teamId, User captain, User member) {
        membershipService.acceptInvitation(invite(teamId, captain, member), member.getId());
    }

    protected String teamStatus(UUID teamId) {
        return jdbcTemplate.queryForObject("SELECT status FROM team WHERE id = ?", String.class, teamId);
    }

    /** Số membership ACTIVE/PENDING của đội — phải = 0 khi đội DISBANDED. */
    protected int openMemberships(UUID teamId) {
        return count("SELECT COUNT(*) FROM team_member WHERE team_id = ? AND status IN ('ACTIVE', 'PENDING')", teamId);
    }

    protected int activeCaptains(UUID teamId) {
        return count("SELECT COUNT(*) FROM team_member WHERE team_id = ? AND role = 'CAPTAIN' AND status = 'ACTIVE'",
                teamId);
    }

    protected int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
