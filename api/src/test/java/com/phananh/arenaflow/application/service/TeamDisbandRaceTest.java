package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.InviteMemberRequest;
import com.phananh.arenaflow.application.dto.request.MarkInactiveRequest;
import com.phananh.arenaflow.application.dto.request.TransferCaptainRequest;
import com.phananh.arenaflow.application.support.security.JwtTokenService;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.domain.entity.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Race giữa các thao tác ghi trên đội: 2 luồng xuất phát cùng lúc qua {@link CyclicBarrier},
 * lặp {@value #ROUNDS} vòng, mỗi vòng fixture mới. Không tất định → assert BẤT BIẾN dữ liệu sau mỗi vòng và tập
 * kết quả cho phép; không bao giờ có lỗi ngoài {@link BusinessException} (≈ 500). Thống kê kết quả in ra stdout.
 * R1/R2 quét độ trễ luồng thứ hai (0 → thời gian 1 lần transfer) để chạm cả hai nhánh thắng; độ nhạy đã kiểm bằng
 * mutation (bỏ force-increment của invite → M1 fail; bỏ kiểm lại captain trong deleteAccount → R1 fail), 2026-11-05.
 * 2026-11-06: deleteAccount đổi sang khoá bi quan, bước kiểm lại đã bỏ. "Delete vs accept own invitation" Red
 * thật trên luồng cũ (user đã xoá còn ACTIVE); "delete vs transfer away" nhạy qua mutation bỏ kiểm captain đầu tiên của
 * luồng cũ (user đã xoá còn MEMBER ACTIVE).
 */
@ExtendWith(OutputCaptureExtension.class)
class TeamDisbandRaceTest extends CommittedDataIntegrationTest {

    private static final int ROUNDS = 10;
    private static final String OK = "OK";

    @Autowired private WebApplicationContext context;
    @Autowired private JwtTokenService jwtTokenService;

    private ExecutorService executor;

    @BeforeEach
    void startExecutor() {
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void doubleSubmitDisbandLetsExactlyOneWinAndTheOtherGetsTeam016() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            User captain = user("Race Double Captain " + round);
            User member = user("Race Double Member " + round);
            UUID teamId = team(captain);
            join(teamId, captain, member);

            List<String> outcomes = race(() -> teamService.disbandTeam(teamId, captain.getId(), null),
                    () -> teamService.disbandTeam(teamId, captain.getId(), null));

            record(stats, outcomes);
            assertThat(outcomes).as("round %d", round).containsExactlyInAnyOrder(OK, "TEAM_016");
            assertDisbandedAndClosed(teamId);
        }
        report("double-submit disband", stats);
    }

    @Test
    void disbandVersusTransferCaptainR2(CapturedOutput output) throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        long transferMillis = measureTransferMillis();
        for (int round = 0; round < ROUNDS; round++) {
            long disbandDelay = transferMillis * round / ROUNDS;
            User captain = user("Race R2 Captain " + round);
            User member = user("Race R2 Member " + round);
            UUID teamId = team(captain);
            join(teamId, captain, member);

            List<String> outcomes = race(() -> teamService.transferCaptain(teamId, captain.getId(),
                            new TransferCaptainRequest(member.getId(), null)),
                    () -> teamService.disbandTeam(teamId, captain.getId(), null), disbandDelay);

            record(stats, outcomes);
            String transfer = outcomes.get(0);
            String disband = outcomes.get(1);
            // Disband thắng → transfer luôn TEAM_016 (kể cả khi member đã bị chuyển LEFT trước bước tra người nhận).
            assertThat(disband + "/" + transfer).as("round %d", round).isIn("OK/TEAM_016", "TEAM_002/OK");
            if (OK.equals(disband)) {
                assertDisbandedAndClosed(teamId);
            } else {
                assertThat(teamStatus(teamId)).isEqualTo("ACTIVE");
                assertThat(activeCaptains(teamId)).isEqualTo(1);
            }
        }
        report("R2 disband vs transfer", stats);
        // Transfer và disband phải khoá row captain cũ trước row member. Đảo thứ tự → deadlock; retrier thử lại nên
        // kết quả vẫn hợp lệ, dấu vết duy nhất là log 40P01 → coi là lỗi.
        assertThat(output.getAll()).as("deadlock trong R2: thứ tự khoá transfer/disband bị đảo").doesNotContain("40P01");
    }

    @Test
    void inviteVersusDisbandM1NeverLeavesPendingInvitationOnDisbandedTeam() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            User captain = user("Race M1 Captain " + round);
            User invitee = user("Race M1 Invitee " + round);
            UUID teamId = team(captain);

            List<String> outcomes = race(() -> membershipService.inviteMember(teamId, captain.getId(),
                            new InviteMemberRequest(invitee.getId())),
                    () -> teamService.disbandTeam(teamId, captain.getId(), null));

            record(stats, outcomes);
            assertThat(outcomes.get(0)).as("invite, round %d", round).isIn(OK, "TEAM_016");
            assertThat(outcomes.get(1)).as("disband, round %d", round).isEqualTo(OK);
            assertDisbandedAndClosed(teamId);
        }
        report("M1 invite vs disband", stats);
    }

    @Test
    void acceptVersusDisbandNeverLeavesActiveMemberAndKeepsLeftAtAfterJoinedAt() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            User captain = user("Race Accept Captain " + round);
            User invitee = user("Race Accept Invitee " + round);
            UUID teamId = team(captain);
            UUID invitationId = invite(teamId, captain, invitee);

            List<String> outcomes = race(() -> membershipService.acceptInvitation(invitationId, invitee.getId()),
                    () -> teamService.disbandTeam(teamId, captain.getId(), null));

            record(stats, outcomes);
            assertThat(outcomes.get(0)).as("accept, round %d", round).isIn(OK, "TEAM_016");
            assertThat(outcomes.get(1)).as("disband, round %d", round).isEqualTo(OK);
            assertDisbandedAndClosed(teamId);
            assertThat(count("SELECT COUNT(*) FROM team_member WHERE team_id = ? AND status = 'LEFT' "
                    + "AND left_at < joined_at", teamId)).as("left_at >= joined_at, round %d", round).isZero();
        }
        report("disband vs accept", stats);
    }

    @Test
    void disbandVersusAdminLockAlwaysEndsDisbandedAndKeepsReasonWhenLockWins() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            User captain = user("Race Lock Captain " + round);
            UUID teamId = team(captain);

            List<String> outcomes = race(() -> teamService.disbandTeam(teamId, captain.getId(), null),
                    () -> adminTeamService.markInactive(teamId, new MarkInactiveRequest("Race lock")));

            record(stats, outcomes);
            assertThat(outcomes.get(0)).as("disband, round %d", round).isEqualTo(OK);
            assertThat(outcomes.get(1)).as("lock, round %d", round).isIn(OK, "TEAM_016");
            assertDisbandedAndClosed(teamId);
            String inactiveReason = jdbcTemplate.queryForObject(
                    "SELECT inactive_reason FROM team WHERE id = ?", String.class, teamId);
            assertThat(inactiveReason).as("round %d", round).isEqualTo(OK.equals(outcomes.get(1)) ? "Race lock" : null);
        }
        report("disband vs admin lock", stats);
    }

    @Test
    void deleteAccountVersusTransferToSameUserR1NeverLosesCaptain() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        long transferMillis = measureTransferMillis();
        for (int round = 0; round < ROUNDS; round++) {
            long deleteDelay = transferMillis * round / ROUNDS;
            User captain = user("Race R1 Captain " + round);
            User member = user("Race R1 Member " + round);
            UUID teamId = team(captain);
            join(teamId, captain, member);

            List<String> outcomes = race(() -> teamService.transferCaptain(teamId, captain.getId(),
                            new TransferCaptainRequest(member.getId(), null)),
                    () -> {
                        profileService.deleteAccount(member.getId());
                        return null;
                    }, deleteDelay);

            record(stats, outcomes);
            // transfer/delete: delete thắng → transfer TEAM_007; transfer thắng → delete USER_003.
            assertThat(outcomes.get(0) + "/" + outcomes.get(1)).as("round %d", round).isIn("TEAM_007/OK", "OK/USER_003");
            assertThat(activeCaptains(teamId)).as("round %d", round).isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM team_member m JOIN \"user\" u ON u.id = m.user_id "
                    + "WHERE m.team_id = ? AND m.status = 'ACTIVE' AND u.deleted_at IS NOT NULL", teamId))
                    .as("deleted user still ACTIVE, round %d", round).isZero();
        }
        report("R1 delete account vs transfer", stats);
    }

    /**
     * User xoá tài khoản trong lúc chính user đó accept lời mời (thiết bị khác). Không được để user đã
     * xoá còn ACTIVE/PENDING trong đội, không vi phạm CHECK left_at ≥ joined_at (≈ 500).
     */
    @Test
    void deleteAccountVersusAcceptOwnInvitationNeverLeavesDeletedUserInTeam() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        long acceptMillis = measureAcceptMillis();
        for (int round = 0; round < ROUNDS; round++) {
            long deleteDelay = acceptMillis * round / ROUNDS;
            User captain = user("Race DA Captain " + round);
            User invitee = user("Race DA Invitee " + round);
            UUID teamId = team(captain);
            UUID invitationId = invite(teamId, captain, invitee);

            List<String> outcomes = race(() -> membershipService.acceptInvitation(invitationId, invitee.getId()),
                    () -> {
                        profileService.deleteAccount(invitee.getId());
                        return null;
                    }, deleteDelay);

            record(stats, outcomes);
            // accept/delete: accept thắng → delete chuyển member sang LEFT; delete thắng → lời mời REJECTED → TEAM_006.
            assertThat(outcomes.get(0) + "/" + outcomes.get(1)).as("round %d", round).isIn("OK/OK", "TEAM_006/OK");
            assertThat(isSoftDeleted(invitee)).as("delete OK → soft deleted, round %d", round).isTrue();
            assertThat(openMembershipsOfDeletedUsers(teamId)).as("deleted user still ACTIVE/PENDING, round %d", round)
                    .isZero();
            assertThat(count("SELECT COUNT(*) FROM team_member WHERE team_id = ? AND status = 'LEFT' "
                    + "AND left_at < joined_at", teamId)).as("left_at >= joined_at, round %d", round).isZero();
        }
        report("delete account vs accept own invitation", stats);
    }

    /**
     * Race ngược chiều R1: captain U chuyển quyền cho X trong lúc U xoá tài khoản. Đội luôn đúng 1 captain ACTIVE và user
     * đã xoá không còn là thành viên ACTIVE.
     */
    @Test
    void deleteAccountVersusTransferAwayNeverLeavesDeletedUserInTeam() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        long transferMillis = measureTransferMillis();
        for (int round = 0; round < ROUNDS; round++) {
            long deleteDelay = transferMillis * round / ROUNDS;
            User captain = user("Race TA Captain " + round);
            User successor = user("Race TA Successor " + round);
            UUID teamId = team(captain);
            join(teamId, captain, successor);

            List<String> outcomes = race(() -> teamService.transferCaptain(teamId, captain.getId(),
                            new TransferCaptainRequest(successor.getId(), null)),
                    () -> {
                        profileService.deleteAccount(captain.getId());
                        return null;
                    }, deleteDelay);

            record(stats, outcomes);
            // transfer/delete: transfer commit trước → U là MEMBER → xoá được (LEFT); U còn là captain → USER_003.
            assertThat(outcomes.get(0) + "/" + outcomes.get(1)).as("round %d", round).isIn("OK/OK", "OK/USER_003");
            assertThat(activeCaptains(teamId)).as("round %d", round).isEqualTo(1);
            assertThat(openMembershipsOfDeletedUsers(teamId)).as("deleted user still ACTIVE/PENDING, round %d", round)
                    .isZero();
        }
        report("delete account vs transfer away", stats);
    }

    /** Đường HTTP thật (open-in-view: các lần thử của retrier dùng chung EntityManager của request). */
    @Test
    void doubleSubmitDisbandOverHttpReturns200And422Team016() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            User captain = user("Race Http Captain " + round);
            User member = user("Race Http Member " + round);
            UUID teamId = team(captain);
            join(teamId, captain, member);
            String token = bearer(captain);
            Callable<String> call = () -> http(mockMvc,
                    post("/api/v1/teams/" + teamId + "/disband").header(HttpHeaders.AUTHORIZATION, token));

            List<String> outcomes = race(call, call);

            List<String> statuses = outcomes.stream().map(outcome -> outcome.substring(0, 3)).toList();
            record(stats, statuses);
            assertThat(statuses).as("round %d: %s", round, outcomes).containsExactlyInAnyOrder("200", "422");
            assertThat(outcomes).as("round %d", round).anySatisfy(outcome -> assertThat(outcome).contains("TEAM_016"));
            assertDisbandedAndClosed(teamId);
        }
        report("HTTP double-submit disband", stats);
    }

    /**
     * M1 qua HTTP: xung đột của invite xảy ra LÚC COMMIT (OPTIMISTIC_FORCE_INCREMENT), và với open-in-view mọi lần thử
     * của retrier dùng chung EntityManager của request → lần retry phải đọc lại đội (không lấy bản cache cũ), nên invite
     * chỉ được 201 hoặc 422 TEAM_016, KHÔNG BAO GIỜ 409 TEAM_014. Disband trễ dần (0 → thời gian 1 invite) để rơi vào
     * khe giữa Q2 và commit của invite.
     */
    @Test
    void inviteVersusDisbandOverHttpNeverReturns409() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Map<String, Integer> stats = new TreeMap<>();
        long inviteMillis = measureHttpInviteMillis(mockMvc);
        for (int round = 0; round < ROUNDS; round++) {
            long disbandDelay = inviteMillis * round / ROUNDS;
            User captain = user("Race Http M1 Captain " + round);
            User invitee = user("Race Http M1 Invitee " + round);
            UUID teamId = team(captain);
            String token = bearer(captain);

            List<String> outcomes = race(() -> httpInvite(mockMvc, token, teamId, invitee),
                    () -> http(mockMvc, post("/api/v1/teams/" + teamId + "/disband").header(HttpHeaders.AUTHORIZATION, token)),
                    disbandDelay);

            String invite = outcomes.get(0);
            String disband = outcomes.get(1);
            record(stats, List.of(invite.substring(0, 3), disband.substring(0, 3)));
            assertThat(disband).as("disband, round %d", round).startsWith("200");
            assertThat(invite).as("invite, round %d", round).satisfiesAnyOf(
                    outcome -> assertThat(outcome).startsWith("201"),
                    outcome -> assertThat(outcome).startsWith("422").contains("TEAM_016"));
            assertDisbandedAndClosed(teamId);
        }
        report("HTTP M1 invite vs disband", stats);
    }

    private long measureHttpInviteMillis(MockMvc mockMvc) throws Exception {
        User captain = user("Race Http Timing Captain");
        UUID teamId = team(captain);
        String token = bearer(captain);
        httpInvite(mockMvc, token, teamId, user("Race Http Timing Warmup"));
        long start = System.nanoTime();
        assertThat(httpInvite(mockMvc, token, teamId, user("Race Http Timing Invitee"))).startsWith("201");
        return Math.max(1, (System.nanoTime() - start) / 1_000_000);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenService.issueAccessToken(user.getId(), user.getRole());
    }

    private static String httpInvite(MockMvc mockMvc, String token, UUID teamId, User invitee) throws Exception {
        return http(mockMvc, post("/api/v1/teams/" + teamId + "/invitations")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + invitee.getId() + "\"}"));
    }

    /** "status body" của một request MockMvc (chạy trong luồng của race). */
    private static String http(MockMvc mockMvc, MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        return result.getResponse().getStatus() + " " + result.getResponse().getContentAsString();
    }

    private void assertDisbandedAndClosed(UUID teamId) {
        assertThat(teamStatus(teamId)).isEqualTo("DISBANDED");
        assertThat(openMemberships(teamId)).as("ACTIVE/PENDING on disbanded team").isZero();
    }

    /** Chạy 2 thao tác cùng lúc; trả mã kết quả theo thứ tự tham số: "OK", mã BusinessException, hoặc chuỗi trả về. */
    private List<String> race(Callable<?> first, Callable<?> second) throws Exception {
        return race(first, second, 0);
    }

    /**
     * Như {@link #race(Callable, Callable)} nhưng luồng thứ hai chờ {@code secondDelayMillis} sau barrier. Quét độ trễ
     * qua các vòng để rơi vào khe hẹp giữa 2 câu SQL của luồng kia (vd. R1: giữa Q1 và Q2 của deleteAccount).
     */
    private List<String> race(Callable<?> first, Callable<?> second, long secondDelayMillis) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Future<String> a = executor.submit(() -> attempt(barrier, first, 0));
        Future<String> b = executor.submit(() -> attempt(barrier, second, secondDelayMillis));
        return List.of(a.get(60, SECONDS), b.get(60, SECONDS));
    }

    /** Thời gian một lần transferCaptain trên DB dev (khởi động + đo) — làm biên quét độ trễ. */
    private long measureTransferMillis() {
        User captain = user("Race Timing Captain");
        User member = user("Race Timing Member");
        UUID teamId = team(captain);
        join(teamId, captain, member);
        teamService.transferCaptain(teamId, captain.getId(), new TransferCaptainRequest(member.getId(), null));
        long start = System.nanoTime();
        teamService.transferCaptain(teamId, member.getId(), new TransferCaptainRequest(captain.getId(), null));
        return Math.max(1, (System.nanoTime() - start) / 1_000_000);
    }

    /** Thời gian một lần acceptInvitation trên DB dev (khởi động + đo) — làm biên quét độ trễ. */
    private long measureAcceptMillis() {
        User captain = user("Race Timing Accept Captain");
        User warmup = user("Race Timing Accept Warmup");
        User invitee = user("Race Timing Accept Invitee");
        UUID teamId = team(captain);
        join(teamId, captain, warmup);
        UUID invitationId = invite(teamId, captain, invitee);
        long start = System.nanoTime();
        membershipService.acceptInvitation(invitationId, invitee.getId());
        return Math.max(1, (System.nanoTime() - start) / 1_000_000);
    }

    /** User đã bị soft delete — chặn hồi quy "delete trả OK mà không set deleted_at" làm bất biến bên dưới pass vô nghĩa. */
    private boolean isSoftDeleted(User user) {
        return count("SELECT COUNT(*) FROM \"user\" WHERE id = ? AND deleted_at IS NOT NULL", user.getId()) == 1;
    }

    /** Membership ACTIVE/PENDING của user đã soft delete trong đội — phải luôn = 0 sau xoá tài khoản. */
    private int openMembershipsOfDeletedUsers(UUID teamId) {
        return count("SELECT COUNT(*) FROM team_member m JOIN \"user\" u ON u.id = m.user_id WHERE m.team_id = ? "
                + "AND m.status IN ('ACTIVE', 'PENDING') AND u.deleted_at IS NOT NULL", teamId);
    }

    private static String attempt(CyclicBarrier barrier, Callable<?> call, long delayMillis) throws Exception {
        barrier.await(30, SECONDS);
        if (delayMillis > 0) {
            Thread.sleep(delayMillis);
        }
        try {
            Object result = call.call();
            return result instanceof String text ? text : OK;
        } catch (BusinessException exception) {
            return exception.getErrorCode().name();
        }
        // Exception khác (≈ 500) để lọt ra → Future.get ném ExecutionException → test fail.
    }

    private static void record(Map<String, Integer> stats, List<String> outcomes) {
        stats.merge(String.join(" | ", outcomes), 1, Integer::sum);
    }

    private static void report(String scenario, Map<String, Integer> stats) {
        System.out.println("[RACE] " + scenario + " → " + stats);
    }
}
