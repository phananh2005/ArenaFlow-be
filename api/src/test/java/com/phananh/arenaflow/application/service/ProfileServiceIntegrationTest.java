package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Xoá tài khoản trên DB dev (.env.dev); rollback sau test. Captain phải chuyển quyền/giải tán trước. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class ProfileServiceIntegrationTest {

    @Autowired private ProfileService profileService;
    @Autowired private TeamService teamService;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void deleteAccountRejectsSoleCaptainOfInactiveTeamWithoutSideEffects() {
        User captain = saveUser("Solo Captain");
        User owner = saveUser("Other Owner");
        Team solo = saveTeam("Solo", captain);
        Team other = saveTeam("Other", owner);
        TeamMember captainMembership = saveMember(solo, captain, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        saveMember(other, owner, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        TeamMember invitation = saveMember(other, captain, TeamMember.Role.MEMBER, TeamMember.Status.PENDING);
        solo.setStatus(Team.TeamStatus.INACTIVE);
        solo.setInactiveReason("Admin khoá");
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> profileService.deleteAccount(captain.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_003));

        entityManager.clear();
        assertThat(teamRepository.findById(solo.getId()).orElseThrow().getStatus()).isEqualTo(Team.TeamStatus.INACTIVE);
        assertThat(teamMemberRepository.findById(captainMembership.getId()).orElseThrow().getStatus())
                .isEqualTo(TeamMember.Status.ACTIVE);
        assertThat(teamMemberRepository.findById(invitation.getId()).orElseThrow().getStatus())
                .isEqualTo(TeamMember.Status.PENDING);
        assertThat(userRepository.findByIdAndDeletedAtIsNull(captain.getId())).isPresent();
    }

    @Test
    void deleteAccountAfterDisbandLeavesRejectsSoftDeletesInThreeQueriesAndIsIdempotent() {
        User captain = saveUser("Disbanding Captain");
        User owner = saveUser("Other Owner");
        Team own = saveTeam("Own", captain);
        Team joined = saveTeam("Joined", owner);
        Team inviting = saveTeam("Inviting", owner);
        saveMember(own, captain, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        saveMember(joined, owner, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        saveMember(inviting, owner, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        TeamMember membership = saveMember(joined, captain, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        TeamMember invitation = saveMember(inviting, captain, TeamMember.Role.MEMBER, TeamMember.Status.PENDING);
        entityManager.flush();
        entityManager.clear();
        teamService.disbandTeam(own.getId(), captain.getId(), null);
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        profileService.deleteAccount(captain.getId());

        // Ngưỡng ghi ≤ 6: S khoá membership mở + U bulk rời đội/từ chối lời mời + U soft delete = 3.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        entityManager.clear();
        assertThat(teamRepository.findById(own.getId()).orElseThrow().getStatus()).isEqualTo(Team.TeamStatus.DISBANDED);
        assertThat(teamMemberRepository.findById(membership.getId()).orElseThrow().getStatus())
                .isEqualTo(TeamMember.Status.LEFT);
        assertThat(teamMemberRepository.findById(invitation.getId()).orElseThrow().getStatus())
                .isEqualTo(TeamMember.Status.REJECTED);
        assertThat(userRepository.findByIdAndDeletedAtIsNull(captain.getId())).isEmpty();
        assertThat(teamRepository.findById(joined.getId()).orElseThrow().getStatus()).isEqualTo(Team.TeamStatus.ACTIVE);

        profileService.deleteAccount(captain.getId()); // xoá lần 2 → không lỗi (204)
    }

    private User saveUser(String name) {
        User user = User.builder()
                .displayName(name)
                .build();
        return userRepository.save(user);
    }

    private Team saveTeam(String name, User creator) {
        Team team = Team.builder()
                .name(name)
                .createdBy(creator.getId())
                .build();
        return teamRepository.save(team);
    }

    private TeamMember saveMember(Team team, User user, TeamMember.Role role, TeamMember.Status status) {
        TeamMember member = TeamMember.builder()
                .teamId(team.getId())
                .userId(user.getId())
                .role(role)
                .status(status)
                .build();
        return teamMemberRepository.save(member);
    }
}
