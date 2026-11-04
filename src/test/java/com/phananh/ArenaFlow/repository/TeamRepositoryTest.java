package com.phananh.ArenaFlow.repository;

import com.phananh.ArenaFlow.entity.team.Team;
import com.phananh.ArenaFlow.entity.team.TeamCaptainHistory;
import com.phananh.ArenaFlow.entity.team.TeamMember;
import com.phananh.ArenaFlow.entity.user.User;
import com.phananh.ArenaFlow.repository.projection.CaptainHistoryView;
import com.phananh.ArenaFlow.repository.projection.CaptainTeamView;
import com.phananh.ArenaFlow.repository.projection.MemberView;
import com.phananh.ArenaFlow.repository.projection.TeamDetailView;
import com.phananh.ArenaFlow.repository.projection.TeamSummaryView;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.phananh.ArenaFlow.repository.UserRepositoryTest.newUser;
import static org.assertj.core.api.Assertions.assertThat;

class TeamRepositoryTest extends RepositoryIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private TeamCaptainHistoryRepository historyRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void findsTeamDetailWithCaptainAndActiveMemberCountInOneQuery() {
        User captain = userRepository.save(newUser("Captain A", null));
        User member = userRepository.save(newUser("Member B", null));
        User pending = userRepository.save(newUser("Pending C", null));
        Team team = saveTeam("Team Detail", captain, List.of("Nhiệt tình", "Rèn luyện"));
        saveMember(team, captain, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        saveMember(team, member, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        saveMember(team, pending, TeamMember.Role.MEMBER, TeamMember.Status.PENDING);
        flushAndClear();
        Statistics statistics = resetStatistics();

        TeamDetailView detail = teamRepository.findDetailById(team.getId()).orElseThrow();

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(detail.getTeamName()).isEqualTo("Team Detail");
        assertThat(detail.getSkillLevels()).isEqualTo("[\"Nhiệt tình\",\"Rèn luyện\"]");
        assertThat(detail.getStatus()).isEqualTo("ACTIVE");
        assertThat(detail.getCaptainId()).isEqualTo(captain.getId());
        assertThat(detail.getCaptainName()).isEqualTo("Captain A");
        assertThat(detail.getCaptainDeleted()).isFalse();
        assertThat(detail.getMemberCount()).isEqualTo(2L);
        assertThat(detail.getCreatedBy()).isEqualTo(captain.getId());
        assertThat(teamRepository.findDetailById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void teamDetailHasNullCaptainWhenNoActiveCaptain() {
        User creator = userRepository.save(newUser("Creator", null));
        Team team = saveTeam("No Captain", creator, List.of());
        flushAndClear();

        TeamDetailView detail = teamRepository.findDetailById(team.getId()).orElseThrow();

        assertThat(detail.getCaptainId()).isNull();
        assertThat(detail.getMemberCount()).isZero();
    }

    @Test
    void findsTeamSummariesFilteredSortedAndPagedWithCountInTwoQueries() {
        User captain = userRepository.save(newUser("Captain S", null));
        Team older = saveTeam("Older", captain, List.of());
        Team newer = saveTeam("Newer", captain, List.of());
        Team inactive = saveTeam("Inactive", captain, List.of());
        flushAndClear();
        // Đặt created_at ở tương lai để 3 đội test đứng đầu thứ tự created_at DESC dù DB có dữ liệu khác.
        setCreatedAt(older, "2100-01-01T00:00:00Z");
        setCreatedAt(newer, "2100-01-02T00:00:00Z");
        setCreatedAt(inactive, "2100-01-03T00:00:00Z");
        jdbcTemplate.update("UPDATE team SET status = 'INACTIVE', inactive_reason = 'test' WHERE id = ?", inactive.getId());
        Statistics statistics = resetStatistics();

        Page<TeamSummaryView> active = teamRepository.findSummaries("ACTIVE", PageRequest.of(0, 2));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(active.getContent()).extracting(TeamSummaryView::getTeamId)
                .containsExactly(newer.getId(), older.getId());
        assertThat(active.getTotalElements()).isGreaterThanOrEqualTo(2);

        Page<TeamSummaryView> all = teamRepository.findSummaries(null, PageRequest.of(0, 1));
        assertThat(all.getContent()).extracting(TeamSummaryView::getTeamId).containsExactly(inactive.getId());
        assertThat(all.getContent().getFirst().getInactiveReason()).isEqualTo("test");
    }

    @Test
    void marksInactiveOnlyTeamsWithExactlyOneActiveMemberAndBumpsVersion() {
        User captain = userRepository.save(newUser("Solo Captain", null));
        User other = userRepository.save(newUser("Other", null));
        Team solo = saveTeam("Solo", captain, List.of());
        Team crowded = saveTeam("Crowded", captain, List.of());
        saveMember(solo, captain, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        saveMember(crowded, captain, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        saveMember(crowded, other, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        flushAndClear();

        int updated = teamRepository.markInactiveByIds(
                Set.of(solo.getId(), crowded.getId()), "Đội trưởng duy nhất đã xoá tài khoản", OffsetDateTime.now());

        assertThat(updated).isEqualTo(1);
        flushAndClear();
        Team reloaded = teamRepository.findById(solo.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Team.TeamStatus.INACTIVE);
        assertThat(reloaded.getInactiveReason()).isEqualTo("Đội trưởng duy nhất đã xoá tài khoản");
        assertThat(reloaded.getVersion()).isEqualTo(1L);
        assertThat(teamRepository.findById(crowded.getId()).orElseThrow().getStatus())
                .isEqualTo(Team.TeamStatus.ACTIVE);
    }

    @Test
    void membershipLookupsAndCaptainTeamsWithActiveCount() {
        User captain = userRepository.save(newUser("Cap", null));
        User member = userRepository.save(newUser("Mem", null));
        Team team = saveTeam("Lookup", captain, List.of());
        saveMember(team, captain, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        saveMember(team, member, TeamMember.Role.MEMBER, TeamMember.Status.PENDING);
        flushAndClear();

        assertThat(teamMemberRepository.findByTeamIdAndRoleAndStatus(
                team.getId(), TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE))
                .hasValueSatisfying(m -> assertThat(m.getUserId()).isEqualTo(captain.getId()));
        assertThat(teamMemberRepository.findByTeamIdAndUserIdAndStatus(
                team.getId(), member.getId(), TeamMember.Status.PENDING)).isPresent();
        assertThat(teamMemberRepository.existsByTeamIdAndUserIdAndStatusIn(
                team.getId(), member.getId(), List.of(TeamMember.Status.PENDING, TeamMember.Status.ACTIVE))).isTrue();

        Statistics statistics = resetStatistics();
        List<CaptainTeamView> captainTeams = teamMemberRepository.findCaptainTeamsWithActiveCount(captain.getId());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(captainTeams).singleElement().satisfies(view -> {
            assertThat(view.getTeamId()).isEqualTo(team.getId());
            assertThat(view.getActiveCount()).isEqualTo(1L);
        });
    }

    @Test
    void findsActiveMembersCaptainFirstWithMaskingFlagInTwoQueries() {
        User captain = userRepository.save(newUser("Captain M", null));
        User early = userRepository.save(newUser("Early", null));
        User deleted = newUser("Gone", null);
        deleted.setDeletedAt(OffsetDateTime.now());
        userRepository.save(deleted);
        User leaver = userRepository.save(newUser("Leaver", null));
        Team team = saveTeam("Members", captain, List.of());
        TeamMember earlyMember = saveMember(team, early, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        TeamMember deletedMember = saveMember(team, deleted, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        TeamMember captainMember = saveMember(team, captain, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        saveMember(team, leaver, TeamMember.Role.MEMBER, TeamMember.Status.LEFT);
        flushAndClear();
        jdbcTemplate.update("UPDATE team_member SET joined_at = invited_at + interval '1 minute' WHERE id = ?", earlyMember.getId());
        jdbcTemplate.update("UPDATE team_member SET joined_at = invited_at + interval '2 minute' WHERE id = ?", deletedMember.getId());
        jdbcTemplate.update("UPDATE team_member SET joined_at = invited_at + interval '3 minute' WHERE id = ?", captainMember.getId());
        Statistics statistics = resetStatistics();

        Page<MemberView> page = teamMemberRepository.findActiveMembers(team.getId(), PageRequest.of(0, 10));

        // data + count; Spring Data bỏ COUNT khi trang đầu chưa đầy → 1 hoặc 2 câu (NFS: ≤ ngưỡng).
        assertThat(statistics.getPrepareStatementCount()).isBetween(1L, 2L);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(MemberView::getUserId)
                .containsExactly(captain.getId(), early.getId(), deleted.getId());
        MemberView gone = page.getContent().get(2);
        assertThat(gone.getUserDeleted()).isTrue();
        assertThat(gone.getRole()).isEqualTo("MEMBER");
        assertThat(gone.getJoinedAt()).isNotNull();
    }

    @Test
    void bulkLeaveAndRejectForDeletedUserBumpVersion() {
        User owner = userRepository.save(newUser("Owner", null));
        User user = userRepository.save(newUser("Leaving User", null));
        Team a = saveTeam("A", owner, List.of());
        Team b = saveTeam("B", owner, List.of());
        TeamMember active = saveMember(a, user, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        TeamMember pending = saveMember(b, user, TeamMember.Role.MEMBER, TeamMember.Status.PENDING);
        flushAndClear();

        assertThat(teamMemberRepository.leaveAllActive(user.getId(), OffsetDateTime.now())).isEqualTo(1);
        assertThat(teamMemberRepository.rejectAllPending(user.getId())).isEqualTo(1);
        flushAndClear();

        TeamMember left = teamMemberRepository.findById(active.getId()).orElseThrow();
        assertThat(left.getStatus()).isEqualTo(TeamMember.Status.LEFT);
        assertThat(left.getLeftAt()).isNotNull();
        assertThat(left.getVersion()).isEqualTo(1L);
        TeamMember rejected = teamMemberRepository.findById(pending.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(TeamMember.Status.REJECTED);
        assertThat(rejected.getVersion()).isEqualTo(1L);
    }

    @Test
    void findsCaptainHistoryNewestFirstWithUserNamesInTwoQueries() {
        User first = userRepository.save(newUser("First Captain", null));
        User second = newUser("Second Captain", null);
        second.setDeletedAt(OffsetDateTime.now());
        userRepository.save(second);
        Team team = saveTeam("History", first, List.of());
        TeamCaptainHistory created = saveHistory(team, null, first, null);
        TeamCaptainHistory transferred = saveHistory(team, first, second, "Bận công tác");
        flushAndClear();
        jdbcTemplate.update("UPDATE team_captain_history SET transferred_at = transferred_at - interval '1 day' WHERE id = ?",
                created.getId());
        Statistics statistics = resetStatistics();

        Page<CaptainHistoryView> page = historyRepository.findHistory(team.getId(), PageRequest.of(0, 10));

        assertThat(statistics.getPrepareStatementCount()).isBetween(1L, 2L);
        assertThat(page.getContent()).extracting(CaptainHistoryView::getHistoryId)
                .containsExactly(transferred.getId(), created.getId());
        CaptainHistoryView latest = page.getContent().getFirst();
        assertThat(latest.getFromUserName()).isEqualTo("First Captain");
        assertThat(latest.getToUserDeleted()).isTrue();
        assertThat(latest.getReason()).isEqualTo("Bận công tác");
        assertThat(page.getContent().get(1).getFromUserId()).isNull();
    }

    private Team saveTeam(String name, User creator, List<String> skillLevels) {
        Team team = new Team();
        team.setName(name);
        team.setCreatedBy(creator.getId());
        team.setSkillLevels(skillLevels);
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

    private TeamCaptainHistory saveHistory(Team team, User from, User to, String reason) {
        TeamCaptainHistory history = new TeamCaptainHistory();
        history.setTeamId(team.getId());
        history.setFromUserId(from == null ? null : from.getId());
        history.setToUserId(to.getId());
        history.setReason(reason);
        return historyRepository.save(history);
    }

    private void setCreatedAt(Team team, String isoInstant) {
        jdbcTemplate.update("UPDATE team SET created_at = ?::timestamptz WHERE id = ?", isoInstant, team.getId());
    }
}
