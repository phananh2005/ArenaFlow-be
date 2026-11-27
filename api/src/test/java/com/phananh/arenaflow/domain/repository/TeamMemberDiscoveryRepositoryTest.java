package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.projection.MyInvitationView;
import com.phananh.arenaflow.domain.projection.MyTeamView;
import com.phananh.arenaflow.testsupport.UserDiscoveryFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Lời mời của tôi / đội của tôi (chỉ dữ liệu của người gọi). */
class TeamMemberDiscoveryRepositoryTest extends RepositoryIntegrationTest {

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserDiscoveryFixtures fixtures;
    private UUID me;

    @BeforeEach
    void setUp() {
        fixtures = new UserDiscoveryFixtures(jdbcTemplate);
        me = fixtures.user("Me");
    }

    @Test
    void myInvitationsListsOnlyMyPendingInvitationsNewestFirstWithCurrentCaptain() {
        UUID lan = fixtures.user("Lan");
        UUID hung = fixtures.user("Hùng");
        UUID alpha = fixtures.team("Alpha", lan, "2026-10-01 10:00:00");
        fixtures.activeMember(alpha, hung, "2026-10-02 10:00:00");
        UUID older = fixtures.invite(alpha, me, "2026-11-10 08:00:00");
        fixtures.transferCaptain(alpha, lan, hung);

        UUID beta = fixtures.team("Beta", fixtures.user("Beta Captain"));
        UUID newer = fixtures.invite(beta, me, "2026-11-17 19:30:00");
        fixtures.deactivateTeam(beta);

        UUID gamma = fixtures.team("Gamma", fixtures.user("Gamma Captain"));
        fixtures.invite(gamma, me, "2026-11-12 08:00:00");
        fixtures.disbandTeam(gamma, fixtures.user("Admin"));

        UUID delta = fixtures.team("Delta", fixtures.user("Delta Captain"));
        fixtures.activeMember(delta, me, "2026-11-01 08:00:00");
        fixtures.invite(alpha, fixtures.user("Someone Else"), "2026-11-18 08:00:00");

        Page<MyInvitationView> page = teamMemberRepository.findMyPendingInvitations(me, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(MyInvitationView::getInvitationId).containsExactly(newer, older);
        MyInvitationView first = page.getContent().getFirst();
        assertThat(first.getStatus()).isEqualTo("INACTIVE");
        assertThat(first.getInvitedAt()).isEqualTo(LocalDateTime.of(2026, 11, 17, 19, 30));
        MyInvitationView second = page.getContent().get(1);
        assertThat(second.getTeamName()).isEqualTo("Alpha");
        assertThat(second.getCaptainName()).isEqualTo("Hùng");
        assertThat(second.getCaptainDeleted()).isFalse();
        assertThat(second.getMemberCount()).isEqualTo(2L);
        assertThat(second.getSkillLevels()).isEqualTo("[\"Thi đấu\"]");
    }

    @Test
    void myInvitationsFlagsDeletedCaptainAndCountsAcrossPages() {
        UUID captain = fixtures.user("Old Captain");
        UUID team = fixtures.team("Epsilon", captain);
        fixtures.invite(team, me, "2026-11-01 09:00:00");
        fixtures.softDelete(captain);
        UUID other = fixtures.team("Zeta", fixtures.user("Zeta Captain"));
        fixtures.invite(other, me, "2026-11-02 09:00:00");

        Page<MyInvitationView> firstPage = teamMemberRepository.findMyPendingInvitations(me, PageRequest.of(0, 1));

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent()).singleElement().extracting(MyInvitationView::getTeamName).isEqualTo("Zeta");
        Page<MyInvitationView> secondPage = teamMemberRepository.findMyPendingInvitations(me, PageRequest.of(1, 1));
        assertThat(secondPage.getContent()).singleElement().satisfies(view -> {
            assertThat(view.getTeamName()).isEqualTo("Epsilon");
            assertThat(view.getCaptainDeleted()).isTrue();
        });
    }

    @Test
    void myTeamsListsActiveMembershipsCaptainFirstThenNewestJoined() {
        UUID r = fixtures.team("R", me, "2026-10-01 10:00:00");
        UUID q = fixtures.team("Q", fixtures.user("Q Captain"), "2026-09-01 10:00:00");
        fixtures.activeMember(q, me, "2026-11-10 19:00:00");
        UUID p = fixtures.team("P", fixtures.user("P Captain"), "2026-09-01 10:00:00");
        fixtures.activeMember(p, me, "2026-11-01 19:00:00");
        fixtures.deactivateTeam(p);

        UUID left = fixtures.team("Left", fixtures.user("Left Captain"));
        fixtures.member(left, me, "MEMBER", "LEFT", "2026-10-05 10:00:00", "2026-10-05 10:00:00");
        UUID pendingOnly = fixtures.team("Pending Only", fixtures.user("Pending Captain"));
        fixtures.invite(pendingOnly, me, "2026-11-11 10:00:00");
        UUID disbanded = fixtures.team("Disbanded", fixtures.user("Disbanded Captain"));
        fixtures.activeMember(disbanded, me, "2026-11-12 10:00:00");
        fixtures.disbandTeam(disbanded, me);

        Page<MyTeamView> page = teamMemberRepository.findMyTeams(me, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(MyTeamView::getTeamId).containsExactly(r, q, p);
        MyTeamView captainRow = page.getContent().getFirst();
        assertThat(captainRow.getRole()).isEqualTo("CAPTAIN");
        assertThat(captainRow.getCaptainName()).isEqualTo("Me");
        assertThat(captainRow.getJoinedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 0));
        assertThat(page.getContent().get(2).getStatus()).isEqualTo("INACTIVE");
        assertThat(page.getContent().get(1).getMemberCount()).isEqualTo(2L);
    }

    @Test
    void myTeamsFilteredByCaptainMatchesTeamsBlockingAccountDeletion() {
        UUID mine = fixtures.team("Mine", me);
        UUID other = fixtures.team("Other", fixtures.user("Other Captain"));
        fixtures.activeMember(other, me, UserDiscoveryFixtures.DEFAULT_TIME);

        Page<MyTeamView> captainOnly = teamMemberRepository.findMyTeams(me, "CAPTAIN", PageRequest.of(0, 20));
        assertThat(captainOnly.getTotalElements()).isEqualTo(1);
        assertThat(captainOnly.getContent()).extracting(MyTeamView::getTeamId).containsExactly(mine);

        UUID successor = fixtures.user("Successor");
        fixtures.activeMember(mine, successor, UserDiscoveryFixtures.DEFAULT_TIME);
        fixtures.transferCaptain(mine, me, successor);

        assertThat(teamMemberRepository.findMyTeams(me, "CAPTAIN", PageRequest.of(0, 20)).getTotalElements())
                .isZero();
        assertThat(teamMemberRepository.findMyTeams(me, null, PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(2);
    }
}
