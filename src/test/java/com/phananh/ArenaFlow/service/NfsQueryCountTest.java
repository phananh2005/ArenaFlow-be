package com.phananh.ArenaFlow.service;

import com.phananh.ArenaFlow.dto.request.CreateTeamRequest;
import com.phananh.ArenaFlow.dto.request.InviteMemberRequest;
import com.phananh.ArenaFlow.dto.request.MarkInactiveRequest;
import com.phananh.ArenaFlow.dto.request.ProfileUpdateRequest;
import com.phananh.ArenaFlow.dto.request.TransferCaptainRequest;
import com.phananh.ArenaFlow.dto.request.UpdateTeamRequest;
import com.phananh.ArenaFlow.entity.user.User;
import com.phananh.ArenaFlow.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NFS §3: đếm số câu SQL THẬT (Hibernate Statistics) cho các endpoint chưa đo ở test khác.
 * Ngưỡng: D (chi tiết) ≤ 3, L (danh sách) ≤ 4, W (ghi) ≤ 6. Danh sách đo với trang đầy (pageSize = 1, ≥ 2 dòng)
 * để Spring Data không bỏ câu COUNT — đo trường hợp xấu nhất.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class NfsQueryCountTest {

    private static final int D = 3;
    private static final int L = 4;
    private static final int W = 6;
    private static final UUID BEN_NGHE_WARD = UUID.fromString("10000000-0000-7000-8000-000000000001");

    @Autowired private ProfileService profileService;
    @Autowired private TeamService teamService;
    @Autowired private TeamMembershipService membershipService;
    @Autowired private AdminTeamService adminTeamService;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private User captain;
    private User member;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        captain = saveUser("NFS Captain");
        member = saveUser("NFS Member");
        teamId = teamService.createTeam(captain.getId(), new CreateTeamRequest("NFS Team", null, null, null)).teamId();
        UUID invitation = membershipService.inviteMember(teamId, captain.getId(),
                new InviteMemberRequest(member.getId())).invitationId();
        membershipService.acceptInvitation(invitation, member.getId());
        teamService.transferCaptain(teamId, captain.getId(), new TransferCaptainRequest(member.getId(), null));
        teamService.transferCaptain(teamId, member.getId(), new TransferCaptainRequest(captain.getId(), null));
        // history: 3 dòng; thành viên ACTIVE: 2; captain hiện tại: captain
    }

    private long measure(Runnable call) {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        call.run();
        entityManager.flush();
        return statistics.getPrepareStatementCount();
    }

    @Test
    void readEndpointsStayWithinDetailAndListLimits() {
        assertThat(measure(() -> profileService.getProfile(captain.getId()))).as("2.5").isLessThanOrEqualTo(D);
        assertThat(measure(() -> teamService.listCaptainHistory(teamId, 1, 1))).as("2.16").isLessThanOrEqualTo(L);
        assertThat(measure(() -> membershipService.listMembers(teamId, 1, 1))).as("2.17").isLessThanOrEqualTo(L);
        assertThat(measure(() -> adminTeamService.listTeams(TeamStatusFilter.ALL, 1, 1))).as("2.21")
                .isLessThanOrEqualTo(L);
    }

    @Test
    void writeEndpointsStayWithinWriteLimit() {
        assertThat(measure(() -> profileService.updateProfile(captain.getId(), new ProfileUpdateRequest(
                "Renamed", null, null, null, null, null, BEN_NGHE_WARD)))).as("2.6").isLessThanOrEqualTo(W);
        assertThat(measure(() -> teamService.updateTeam(teamId, captain.getId(),
                new UpdateTeamRequest("NFS Renamed", null, null, List.of("Chill"))))).as("2.11").isEqualTo(4);

        User invitee = saveUser("NFS Invitee");
        UUID invitation = membershipService.inviteMember(teamId, captain.getId(),
                new InviteMemberRequest(invitee.getId())).invitationId();
        assertThat(measure(() -> membershipService.rejectInvitation(invitation, invitee.getId()))).as("2.14")
                .isEqualTo(3);

        assertThat(measure(() -> adminTeamService.markInactive(teamId, new MarkInactiveRequest("NFS"))))
                .as("2.19").isLessThanOrEqualTo(W);
        assertThat(measure(() -> adminTeamService.reactivate(teamId))).as("2.20").isLessThanOrEqualTo(W);
    }

    private User saveUser(String name) {
        User user = new User();
        user.setDisplayName(name);
        return userRepository.save(user);
    }
}
