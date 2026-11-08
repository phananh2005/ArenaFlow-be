package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.projection.MemberView;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.UUID;

import static com.phananh.arenaflow.domain.repository.UserRepositoryTest.newUser;
import static org.assertj.core.api.Assertions.assertThat;

class MemberViewByIdRepositoryTest extends RepositoryIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;

    @Test
    void findsMemberViewByIdInOneQuery() {
        User user = userRepository.save(newUser("Joined", null));
        Team team = Team.builder()
                .name("View")
                .createdBy(user.getId())
                .build();
        teamRepository.save(team);
        OffsetDateTime invitedAt = OffsetDateTime.now();
        TeamMember member = TeamMember.builder()
                .teamId(team.getId())
                .userId(user.getId())
                .role(TeamMember.Role.MEMBER)
                .status(TeamMember.Status.ACTIVE)
                .invitedAt(invitedAt)
                .joinedAt(invitedAt)
                .build();
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
