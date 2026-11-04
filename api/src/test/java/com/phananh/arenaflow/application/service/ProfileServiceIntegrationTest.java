package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** Xoá tài khoản trên DB dev (.env.dev); rollback sau test. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class ProfileServiceIntegrationTest {

    @Autowired private ProfileService profileService;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void deleteAccountInactivatesSoloTeamLeavesRejectsSoftDeletesAndIsIdempotent() {
        User captain = saveUser("Solo Captain");
        User owner = saveUser("Other Owner");
        Team solo = saveTeam("Solo", captain);
        Team other = saveTeam("Other", owner);
        TeamMember captainMembership = saveMember(solo, captain, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        saveMember(other, owner, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        TeamMember invitation = saveMember(other, captain, TeamMember.Role.MEMBER, TeamMember.Status.PENDING);
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        profileService.deleteAccount(captain.getId());

        // NFS W6: S captain teams + U team + U leave + U reject + U soft delete = 5.
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6);
        entityManager.clear();
        Team reloaded = teamRepository.findById(solo.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Team.TeamStatus.INACTIVE);
        assertThat(reloaded.getInactiveReason()).isEqualTo("Đội trưởng duy nhất đã xoá tài khoản");
        assertThat(teamMemberRepository.findById(captainMembership.getId()).orElseThrow().getStatus())
                .isEqualTo(TeamMember.Status.LEFT);
        assertThat(teamMemberRepository.findById(invitation.getId()).orElseThrow().getStatus())
                .isEqualTo(TeamMember.Status.REJECTED);
        assertThat(userRepository.findByIdAndDeletedAtIsNull(captain.getId())).isEmpty();
        assertThat(teamRepository.findById(other.getId()).orElseThrow().getStatus()).isEqualTo(Team.TeamStatus.ACTIVE);

        profileService.deleteAccount(captain.getId()); // xoá lần 2 → không lỗi (204)
    }

    private User saveUser(String name) {
        User user = new User();
        user.setDisplayName(name);
        return userRepository.save(user);
    }

    private Team saveTeam(String name, User creator) {
        Team team = new Team();
        team.setName(name);
        team.setCreatedBy(creator.getId());
        return teamRepository.save(team);
    }

    private TeamMember saveMember(Team team, User user, TeamMember.Role role, TeamMember.Status status) {
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(user.getId());
        member.setRole(role);
        member.setStatus(status);
        return teamMemberRepository.save(member);
    }
}
