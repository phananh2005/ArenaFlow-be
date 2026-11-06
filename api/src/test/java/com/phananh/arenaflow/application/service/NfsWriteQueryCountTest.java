package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.InviteMemberRequest;
import com.phananh.arenaflow.domain.entity.user.User;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Số câu SQL của luồng ghi (giải tán, xoá tài khoản, mời), đo trên transaction commit thật (không
 * {@code @Transactional}) để tính cả câu UPDATE version lúc commit của OPTIMISTIC_FORCE_INCREMENT. Ngưỡng ghi ≤ 6.
 */
class NfsWriteQueryCountTest extends CommittedDataIntegrationTest {

    @Autowired private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUpStatistics() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private long measure(Runnable call) {
        statistics.clear();
        call.run();
        return statistics.getPrepareStatementCount();
    }

    @Test
    void disbandTeamUsesSixStatements() {
        User captain = user("NFS Disband Captain");
        User member = user("NFS Disband Member");
        User invitee = user("NFS Disband Invitee");
        UUID teamId = team(captain);
        join(teamId, captain, member);
        invite(teamId, captain, invitee);

        // S team, S captain, U captain LEFT, U team DISBANDED, U còn lại (bulk), S detail.
        assertThat(measure(() -> teamService.disbandTeam(teamId, captain.getId(), null))).isEqualTo(6);
    }

    @Test
    void deleteAccountUsesThreeStatements() {
        User captain = user("NFS Delete Captain");
        User member = user("NFS Delete Member");
        UUID joined = team(captain);
        UUID inviting = team(user("NFS Delete Other Captain"));
        join(joined, captain, member);
        jdbcTemplate.update("INSERT INTO team_member (id, team_id, user_id, role, status) "
                + "VALUES (gen_random_uuid(), ?, ?, 'MEMBER', 'PENDING')", inviting, member.getId());

        // S khoá membership mở (PESSIMISTIC_WRITE), U rời đội + từ chối lời mời (1 bulk), U soft delete.
        assertThat(measure(() -> profileService.deleteAccount(member.getId()))).isEqualTo(3);
    }

    @Test
    void inviteMemberUsesSixStatementsIncludingVersionBumpAtCommit() {
        User captain = user("NFS Invite Captain");
        User invitee = user("NFS Invite Invitee");
        UUID teamId = team(captain);
        long versionBefore = jdbcTemplate.queryForObject("SELECT version FROM team WHERE id = ?", Long.class, teamId);

        // S team (force increment), S captain, S invitee, S tồn tại, I lời mời, U team.version lúc commit.
        assertThat(measure(() -> membershipService.inviteMember(teamId, captain.getId(),
                new InviteMemberRequest(invitee.getId())))).isEqualTo(6);
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM team WHERE id = ?", Long.class, teamId))
                .isEqualTo(versionBefore + 1);
    }
}
