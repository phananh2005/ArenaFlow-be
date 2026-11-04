package com.phananh.ArenaFlow.repository;

import com.phananh.ArenaFlow.entity.team.Team;
import com.phananh.ArenaFlow.entity.team.TeamMember;
import com.phananh.ArenaFlow.entity.user.User;
import com.phananh.ArenaFlow.repository.projection.MemberView;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.UUID;

import static com.phananh.ArenaFlow.repository.UserRepositoryTest.newUser;
import static org.assertj.core.api.Assertions.assertThat;

class MemberViewByIdRepositoryTest extends RepositoryIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;

    @Test
    void findsMemberViewByIdInOneQuery() {
        User user = userRepository.save(newUser("Joined", null));
        Team team = new Team();
        team.setName("View");
        team.setCreatedBy(user.getId());
        teamRepository.save(team);
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(user.getId());
        member.setRole(TeamMember.Role.MEMBER);
        member.setStatus(TeamMember.Status.ACTIVE);
        member.setInvitedAt(OffsetDateTime.now());
        member.setJoinedAt(member.getInvitedAt());
        teamMemberRepository.save(member);
        flushAndClear();
        Statistics statistics = resetStatistics();

        MemberView view = teamMemberRepository.findMemberViewById(member.getId()).orElseThrow();

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(view.getMemberId()).isEqualTo(member.getId());
        assertThat(view.getDisplayName()).isEqualTo("Joined");
        assertThat(view.getUserDeleted()).isFalse();
        assertThat(view.getStatus()).isEqualTo("ACTIVE");
        assertThat(view.getJoinedAt()).isNotNull();
        assertThat(teamMemberRepository.findMemberViewById(UUID.randomUUID())).isEmpty();
    }
}
