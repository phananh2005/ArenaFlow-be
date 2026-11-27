package com.phananh.arenaflow.application.support.mercenary;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.projection.RecruitContextView;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MercenaryGuardTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);
    private final MercenaryGuard guard = new MercenaryGuard();
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID author = UUID.randomUUID();
    private final UUID opponent = UUID.randomUUID();

    @Test
    void deletedCallerIsRejectedAsUserNotFound() {
        assertError(() -> guard.requireNotDeleted(true), ErrorCode.USER_001);
        assertThatCode(() -> guard.requireNotDeleted(false)).doesNotThrowAnyException();
    }

    @Test
    void captainCheckOrderIsDisbandedThenInactiveThenCaptain() {
        assertError(() -> guard.requireCaptain("DISBANDED", true, true), ErrorCode.TEAM_016);
        assertError(() -> guard.requireCaptain("INACTIVE", false, false), ErrorCode.TEAM_015);
        assertError(() -> guard.requireCaptain("ACTIVE", false, false), ErrorCode.TEAM_002);
        assertError(() -> guard.requireCaptain("INACTIVE", false, true), ErrorCode.TEAM_002);
        assertThatCode(() -> guard.requireCaptain("INACTIVE", true, true)).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireCaptain("ACTIVE", true, false)).doesNotThrowAnyException();
    }

    @Test
    void recruitableMatchChecksTeamThenMatchedThenEndedThenStarted() {
        UUID stranger = UUID.randomUUID();
        assertError(() -> guard.requireRecruitableMatch(ctx(stranger, "OPEN", NOW.plusHours(3)), NOW), ErrorCode.MATCH_009);
        assertError(() -> guard.requireRecruitableMatch(ctx(author, "OPEN", NOW.plusHours(3)), NOW),
                ErrorCode.MERCENARY_003);
        assertError(() -> guard.requireRecruitableMatch(ctx(author, "EXPIRED", NOW.plusHours(3)), NOW),
                ErrorCode.MERCENARY_003);
        for (String ended : new String[] {"CANCELLED", "ARCHIVED", "COMPLETED"}) {
            assertError(() -> guard.requireRecruitableMatch(ctx(opponent, ended, NOW.plusHours(3)), NOW),
                    ErrorCode.MERCENARY_004);
        }
        assertError(() -> guard.requireRecruitableMatch(ctx(author, "CONFIRMED", NOW.minusHours(2)), NOW),
                ErrorCode.MERCENARY_004);
        assertError(() -> guard.requireRecruitableMatch(ctx(author, "CONFIRMED", NOW), NOW), ErrorCode.MERCENARY_013);
        assertThatCode(() -> guard.requireRecruitableMatch(ctx(opponent, "CONFIRMED", NOW.plusMinutes(1)), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void externalMatchUsesSameRulesWithDeclaringTeam() {
        assertError(() -> guard.requireRecruitableMatch(ctx(author, "CANCELLED", NOW.plusHours(3)), NOW),
                ErrorCode.MERCENARY_004);
        assertThatCode(() -> guard.requireRecruitableMatch(ctx(author, "SCHEDULED", NOW.plusHours(3)), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void recruitmentStatusGuards() {
        assertError(() -> guard.requireActive("CLOSED"), ErrorCode.MERCENARY_007);
        assertError(() -> guard.requireActive("EXPIRED"), ErrorCode.MERCENARY_007);
        assertThatCode(() -> guard.requireActive("FILLED")).doesNotThrowAnyException();
        assertError(() -> guard.requireOpen("EXPIRED"), ErrorCode.MERCENARY_007);
        assertError(() -> guard.requireOpen("FILLED"), ErrorCode.MERCENARY_006);
        assertThatCode(() -> guard.requireOpen("OPEN")).doesNotThrowAnyException();
    }

    @Test
    void notStartedRejectsFromExactStartTime() {
        assertError(() -> guard.requireNotStarted(NOW, NOW), ErrorCode.MERCENARY_013);
        assertError(() -> guard.requireNotStarted(NOW.minusMinutes(1), NOW), ErrorCode.MERCENARY_013);
        assertThatCode(() -> guard.requireNotStarted(NOW.plusSeconds(1), NOW)).doesNotThrowAnyException();
    }

    @Test
    void eligibleApplicantChecksMembershipBeforeOverlap() {
        assertError(() -> guard.requireEligibleApplicant(true, true), ErrorCode.MERCENARY_010);
        assertError(() -> guard.requireEligibleApplicant(false, true), ErrorCode.MERCENARY_011);
        assertThatCode(() -> guard.requireEligibleApplicant(false, false)).doesNotThrowAnyException();
    }

    @Test
    void sideIsResolvedFromCaller() {
        assertThat(guard.resolveSide(true, false)).isEqualTo(MercenarySide.APPLICANT);
        assertThat(guard.resolveSide(false, true)).isEqualTo(MercenarySide.CAPTAIN);
        assertError(() -> guard.resolveSide(true, true), ErrorCode.MERCENARY_019);
        assertError(() -> guard.resolveSide(false, false), ErrorCode.MERCENARY_014);
    }

    private RecruitContextView ctx(UUID teamId, String status, LocalDateTime start) {
        Map<String, Object> values = new HashMap<>();
        values.put("teamId", teamId);
        values.put("authorTeamId", author);
        values.put("opponentTeamId", "SCHEDULED".equals(status) ? null : opponent);
        values.put("matchStatus", status);
        values.put("gameStart", start);
        values.put("gameEnd", start.plusMinutes(90));
        return projections.createProjection(RecruitContextView.class, values);
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
