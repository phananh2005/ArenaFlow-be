package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamCaptainHistory;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.projection.CaptainHistoryView;
import com.phananh.arenaflow.domain.projection.MemberHistoryView;
import com.phananh.arenaflow.domain.projection.MemberView;
import com.phananh.arenaflow.domain.projection.TeamDetailView;
import com.phananh.arenaflow.domain.projection.TeamSummaryView;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.phananh.arenaflow.domain.repository.UserRepositoryTest.newUser;
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
    void membershipLookups() {
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

        // data + count; Spring Data bỏ COUNT khi trang đầu chưa đầy → 1 hoặc 2 câu (≤ ngưỡng danh sách).
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
    void leaveAndRejectAllOpenMovesMemberAndPendingButNeverCaptainOrOtherRows() {
        User owner = userRepository.save(newUser("Owner", null));
        User user = userRepository.save(newUser("Leaving User", null));
        User other = userRepository.save(newUser("Other User", null));
        Team a = saveTeam("A", owner, List.of());
        Team b = saveTeam("B", owner, List.of());
        Team captained = saveTeam("Captained", owner, List.of());
        Team former = saveTeam("Former", owner, List.of());
        TeamMember active = saveMember(a, user, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        // Accept commit sau thời điểm :now của xoá tài khoản → left_at phải lấy joined_at (CHECK left_at ≥ joined_at).
        active.setJoinedAt(OffsetDateTime.now().plusHours(1));
        TeamMember pending = saveMember(b, user, TeamMember.Role.MEMBER, TeamMember.Status.PENDING);
        TeamMember captainRow = saveMember(captained, user, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        TeamMember leftRow = saveMember(former, user, TeamMember.Role.MEMBER, TeamMember.Status.LEFT);
        TeamMember othersRow = saveMember(a, other, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        flushAndClear();
        // setJoinedAt sau save → fixture đã UPDATE (version 0 → 1) trước bulk; so với giá trị ngay trước bulk.
        long activeVersionBefore = teamMemberRepository.findById(active.getId()).orElseThrow().getVersion();
        flushAndClear();

        assertThat(teamMemberRepository.leaveAndRejectAllOpen(user.getId(), OffsetDateTime.now())).isEqualTo(2);
        flushAndClear();

        TeamMember left = teamMemberRepository.findById(active.getId()).orElseThrow();
        assertThat(left.getStatus()).isEqualTo(TeamMember.Status.LEFT);
        assertThat(left.getLeftAt()).isAtSameInstantAs(left.getJoinedAt());
        assertThat(left.getVersion()).isEqualTo(activeVersionBefore + 1);
        TeamMember rejected = teamMemberRepository.findById(pending.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(TeamMember.Status.REJECTED);
        assertThat(rejected.getLeftAt()).isNull();
        assertThat(rejected.getVersion()).isEqualTo(1L);
        TeamMember stillCaptain = teamMemberRepository.findById(captainRow.getId()).orElseThrow();
        assertThat(stillCaptain.getStatus()).isEqualTo(TeamMember.Status.ACTIVE);
        assertThat(stillCaptain.getVersion()).isZero();
        assertThat(teamMemberRepository.findById(leftRow.getId()).orElseThrow().getVersion()).isZero();
        TeamMember untouched = teamMemberRepository.findById(othersRow.getId()).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(TeamMember.Status.ACTIVE);
        assertThat(untouched.getVersion()).isZero();
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

    // ---------- Giải tán đội, xoá tài khoản, lịch sử thành viên ----------

    @Test
    void findsDisbandedTeamDetailWithDisbanderAndDeletedFlagInOneQuery() {
        User captain = newUser("Disbander", null);
        captain.setDeletedAt(OffsetDateTime.now());
        userRepository.save(captain);
        Team team = saveDisbandedTeam("Disbanded Detail", captain, "Hết người chơi");
        team.setInactiveReason("Admin khoá trước đó");
        saveMember(team, captain, TeamMember.Role.CAPTAIN, TeamMember.Status.LEFT);
        flushAndClear();
        Statistics statistics = resetStatistics();

        TeamDetailView detail = teamRepository.findDetailById(team.getId()).orElseThrow();

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(detail.getStatus()).isEqualTo("DISBANDED");
        assertThat(detail.getCaptainId()).isNull();
        assertThat(detail.getMemberCount()).isZero();
        assertThat(detail.getDisbandReason()).isEqualTo("Hết người chơi");
        assertThat(detail.getDisbandedAt()).isNotNull();
        assertThat(detail.getDisbandedById()).isEqualTo(captain.getId());
        assertThat(detail.getDisbandedByName()).isEqualTo("Disbander");
        assertThat(detail.getDisbandedByDeleted()).isTrue();
        assertThat(detail.getInactiveReason()).isEqualTo("Admin khoá trước đó");
    }

    @Test
    void activeTeamDetailHasNoDisbandMetadata() {
        User captain = userRepository.save(newUser("Active Captain", null));
        Team team = saveTeam("Not Disbanded", captain, List.of());
        flushAndClear();

        TeamDetailView detail = teamRepository.findDetailById(team.getId()).orElseThrow();

        assertThat(detail.getDisbandReason()).isNull();
        assertThat(detail.getDisbandedAt()).isNull();
        assertThat(detail.getDisbandedById()).isNull();
        assertThat(detail.getDisbandedByName()).isNull();
        assertThat(detail.getDisbandedByDeleted()).isFalse();
    }

    @Test
    void filtersSummariesByDisbandedWithDisbanderName() {
        User captain = userRepository.save(newUser("Summary Disbander", null));
        Team disbanded = saveDisbandedTeam("Disbanded Summary", captain, "Giải tán");
        Team active = saveTeam("Active Summary", captain, List.of());
        flushAndClear();
        setCreatedAt(disbanded, "2100-02-01T00:00:00Z");
        setCreatedAt(active, "2100-02-02T00:00:00Z");
        Statistics statistics = resetStatistics();

        Page<TeamSummaryView> page = teamRepository.findSummaries("DISBANDED", PageRequest.of(0, 1));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(page.getContent()).singleElement().satisfies(view -> {
            assertThat(view.getTeamId()).isEqualTo(disbanded.getId());
            assertThat(view.getStatus()).isEqualTo("DISBANDED");
            assertThat(view.getDisbandedByName()).isEqualTo("Summary Disbander");
            assertThat(view.getDisbandedByDeleted()).isFalse();
        });
    }

    @Test
    void disbandRemainingLeavesActiveRejectsPendingKeepsOthersAndBumpsVersion() {
        User captain = userRepository.save(newUser("Disband Captain", null));
        User early = userRepository.save(newUser("Early Member", null));
        User late = userRepository.save(newUser("Late Accepter", null));
        User invitee = userRepository.save(newUser("Invitee", null));
        User leaver = userRepository.save(newUser("Old Leaver", null));
        Team team = saveTeam("Disband Remaining", captain, List.of());
        Team other = saveTeam("Other Team", captain, List.of());
        TeamMember captainRow = saveMember(team, captain, TeamMember.Role.CAPTAIN, TeamMember.Status.LEFT);
        TeamMember earlyRow = saveMember(team, early, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        TeamMember lateRow = saveMember(team, late, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        TeamMember pendingRow = saveMember(team, invitee, TeamMember.Role.MEMBER, TeamMember.Status.PENDING);
        TeamMember leftRow = saveMember(team, leaver, TeamMember.Role.MEMBER, TeamMember.Status.LEFT);
        TeamMember otherRow = saveMember(other, early, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        flushAndClear();
        jdbcTemplate.update("UPDATE team_member SET joined_at = invited_at WHERE id IN (?, ?)", earlyRow.getId(), otherRow.getId());
        // Accept commit sau thời điểm :now của disband: joined_at ở tương lai -> left_at phải = joined_at.
        jdbcTemplate.update("UPDATE team_member SET joined_at = invited_at + interval '1 hour' WHERE id = ?", lateRow.getId());
        OffsetDateTime now = OffsetDateTime.now();
        Statistics statistics = resetStatistics();

        int updated = teamMemberRepository.disbandRemaining(team.getId(), now);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(updated).isEqualTo(3);
        flushAndClear();
        TeamMember earlyLeft = teamMemberRepository.findById(earlyRow.getId()).orElseThrow();
        assertThat(earlyLeft.getStatus()).isEqualTo(TeamMember.Status.LEFT);
        assertThat(earlyLeft.getLeftAt()).isAfterOrEqualTo(earlyLeft.getJoinedAt());
        assertThat(earlyLeft.getVersion()).isEqualTo(1L);
        TeamMember lateLeft = teamMemberRepository.findById(lateRow.getId()).orElseThrow();
        assertThat(lateLeft.getStatus()).isEqualTo(TeamMember.Status.LEFT);
        assertThat(lateLeft.getLeftAt().toInstant()).isEqualTo(lateLeft.getJoinedAt().toInstant());
        TeamMember rejected = teamMemberRepository.findById(pendingRow.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(TeamMember.Status.REJECTED);
        assertThat(rejected.getLeftAt()).isNull();
        assertThat(rejected.getVersion()).isEqualTo(1L);
        assertThat(teamMemberRepository.findById(leftRow.getId()).orElseThrow().getVersion()).isZero();
        assertThat(teamMemberRepository.findById(captainRow.getId()).orElseThrow().getVersion()).isZero();
        TeamMember untouched = teamMemberRepository.findById(otherRow.getId()).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(TeamMember.Status.ACTIVE);
        assertThat(untouched.getVersion()).isZero();
    }

    @Test
    void findsOpenMembershipsOfUserOrderedByIdInOneQuery() {
        User owner = userRepository.save(newUser("Lock Owner", null));
        User user = userRepository.save(newUser("Lock User", null));
        User other = userRepository.save(newUser("Lock Other", null));
        Team a = saveTeam("Lock A", owner, List.of());
        Team b = saveTeam("Lock B", owner, List.of());
        Team c = saveTeam("Lock C", owner, List.of());
        Team d = saveTeam("Lock D", owner, List.of());
        saveMember(a, user, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        saveMember(b, user, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        saveMember(c, user, TeamMember.Role.MEMBER, TeamMember.Status.PENDING);
        saveMember(d, user, TeamMember.Role.MEMBER, TeamMember.Status.LEFT);
        saveMember(a, other, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        flushAndClear();
        // Thứ tự khoá phải là thứ tự uuid của PostgreSQL (so byte) — lấy từ DB, không dùng UUID.compareTo của Java.
        List<UUID> expectedOrder = jdbcTemplate.queryForList("SELECT id FROM team_member WHERE user_id = ? "
                + "AND status IN ('ACTIVE', 'PENDING') ORDER BY id", UUID.class, user.getId());
        Statistics statistics = resetStatistics();

        List<TeamMember> rows = teamMemberRepository.findByUserIdAndStatusInOrderByIdAsc(user.getId(),
                List.of(TeamMember.Status.ACTIVE, TeamMember.Status.PENDING));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(rows).extracting(TeamMember::getId).containsExactlyElementsOf(expectedOrder).hasSize(3);
    }

    @Test
    void findsMemberHistoryActiveAndLeftSortedByJoinedAtWithMaskingFlagAndLeftAt() {
        User captain = userRepository.save(newUser("History Captain", null));
        User leaver = userRepository.save(newUser("History Leaver", null));
        User deleted = newUser("History Gone", null);
        deleted.setDeletedAt(OffsetDateTime.now());
        userRepository.save(deleted);
        User invitee = userRepository.save(newUser("History Invitee", null));
        User refuser = userRepository.save(newUser("History Refuser", null));
        Team team = saveTeam("Member History", captain, List.of());
        TeamMember captainRow = saveMember(team, captain, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        TeamMember leaverRow = saveMember(team, leaver, TeamMember.Role.MEMBER, TeamMember.Status.LEFT);
        TeamMember deletedRow = saveMember(team, deleted, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        saveMember(team, invitee, TeamMember.Role.MEMBER, TeamMember.Status.PENDING);
        saveMember(team, refuser, TeamMember.Role.MEMBER, TeamMember.Status.REJECTED);
        flushAndClear();
        jdbcTemplate.update("UPDATE team_member SET joined_at = invited_at + interval '1 minute', "
                + "left_at = invited_at + interval '10 minute' WHERE id = ?", leaverRow.getId());
        jdbcTemplate.update("UPDATE team_member SET joined_at = invited_at + interval '2 minute' WHERE id = ?", deletedRow.getId());
        jdbcTemplate.update("UPDATE team_member SET joined_at = invited_at + interval '3 minute' WHERE id = ?", captainRow.getId());
        Statistics statistics = resetStatistics();

        Page<MemberHistoryView> all = teamMemberRepository.findMemberHistory(
                team.getId(), List.of("ACTIVE", "LEFT"), PageRequest.of(0, 10));

        assertThat(statistics.getPrepareStatementCount()).isBetween(1L, 2L);
        assertThat(all.getTotalElements()).isEqualTo(3);
        assertThat(all.getContent()).extracting(MemberHistoryView::getUserId)
                .containsExactly(leaver.getId(), deleted.getId(), captain.getId());
        MemberHistoryView left = all.getContent().getFirst();
        assertThat(left.getStatus()).isEqualTo("LEFT");
        assertThat(left.getLeftAt()).isNotNull();
        assertThat(left.getRole()).isEqualTo("MEMBER");
        assertThat(all.getContent().get(1).getUserDeleted()).isTrue();
        assertThat(all.getContent().get(2).getLeftAt()).isNull();
        assertThat(all.getContent().get(2).getRole()).isEqualTo("CAPTAIN");

        Page<MemberHistoryView> leftOnly = teamMemberRepository.findMemberHistory(
                team.getId(), List.of("LEFT"), PageRequest.of(0, 10));
        assertThat(leftOnly.getContent()).extracting(MemberHistoryView::getUserId).containsExactly(leaver.getId());

        Page<MemberHistoryView> paged = teamMemberRepository.findMemberHistory(
                team.getId(), List.of("ACTIVE", "LEFT"), PageRequest.of(1, 2));
        assertThat(paged.getTotalElements()).isEqualTo(3);
        assertThat(paged.getContent()).extracting(MemberHistoryView::getUserId).containsExactly(captain.getId());
    }

    private Team saveDisbandedTeam(String name, User disbander, String reason) {
        Team team = new Team();
        team.setName(name);
        team.setCreatedBy(disbander.getId());
        team.setSkillLevels(List.of());
        team.setStatus(Team.TeamStatus.DISBANDED);
        team.setDisbandReason(reason);
        team.setDisbandedAt(OffsetDateTime.now());
        team.setDisbandedBy(disbander.getId());
        return teamRepository.save(team);
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
