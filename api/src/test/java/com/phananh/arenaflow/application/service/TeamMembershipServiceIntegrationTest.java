package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateTeamRequest;
import com.phananh.arenaflow.application.dto.request.InviteMemberRequest;
import com.phananh.arenaflow.application.dto.response.InvitationResponse;
import com.phananh.arenaflow.application.dto.response.MemberResponse;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mời → chấp nhận → rời đội trên DB dev (.env.dev); rollback sau test. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class TeamMembershipServiceIntegrationTest {

    @Autowired private TeamService teamService;
    @Autowired private TeamMembershipService membershipService;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void inviteAcceptListLeaveWithinQueryLimits() {
        User captain = saveUser("Captain M");
        User invitee = saveUser("Invitee M");
        UUID teamId = teamService.createTeam(captain.getId(), new CreateTeamRequest("Team M", null, null, null)).teamId();
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        InvitationResponse invitation = membershipService.inviteMember(teamId, captain.getId(),
                new InviteMemberRequest(invitee.getId()));
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6); // S, S, S, S, I
        assertThat(invitation.status()).isEqualTo("PENDING");
        assertThatThrownBy(() -> membershipService.inviteMember(teamId, captain.getId(),
                new InviteMemberRequest(invitee.getId())))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TEAM_003));
        entityManager.clear();

        statistics.clear();
        MemberResponse accepted = membershipService.acceptInvitation(invitation.invitationId(), invitee.getId());
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6); // S, S, U, S
        assertThat(accepted.status()).isEqualTo("ACTIVE");
        assertThat(accepted.displayName()).isEqualTo("Invitee M");
        entityManager.clear();

        assertThat(membershipService.listMembers(teamId, 1, 20).data())
                .extracting(MemberResponse::userId).containsExactly(captain.getId(), invitee.getId());

        statistics.clear();
        membershipService.leaveTeam(teamId, invitee.getId());
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6); // S, S, U
        entityManager.flush();
        entityManager.clear();
        assertThat(membershipService.listMembers(teamId, 1, 20).data())
                .extracting(MemberResponse::userId).containsExactly(captain.getId());
    }

    private User saveUser(String name) {
        User user = User.builder()
                .displayName(name)
                .build();
        return userRepository.save(user);
    }
}
