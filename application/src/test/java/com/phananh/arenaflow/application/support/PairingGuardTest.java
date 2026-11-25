package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.application.service.PairingSide;
import com.phananh.arenaflow.application.support.match.PairingGuard;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.projection.PairingContextView;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PairingGuardTest {

    private static final UUID AUTHOR_TEAM = UUID.randomUUID();
    private static final UUID INTERESTED_TEAM = UUID.randomUUID();

    private final PairingGuard guard = new PairingGuard();

    @Test
    void requireSideResolvesTeamToItsSideOrRejectsOutsider() {
        PairingContextView ctx = ctx("ACTIVE", "ACTIVE", true, true);

        assertThat(guard.requireSide(ctx, AUTHOR_TEAM)).isEqualTo(PairingSide.AUTHOR);
        assertThat(guard.requireSide(ctx, INTERESTED_TEAM)).isEqualTo(PairingSide.INTERESTED);
        assertError(() -> guard.requireSide(ctx, UUID.randomUUID()), ErrorCode.PAIRING_002);
    }

    @Test
    void requireCaptainChecksDisbandedThenInactiveThenCaptainOfThatSide() {
        assertError(() -> guard.requireCaptain(ctx("DISBANDED", "ACTIVE", false, true), PairingSide.AUTHOR, false),
                ErrorCode.TEAM_016);
        assertError(() -> guard.requireCaptain(ctx("INACTIVE", "ACTIVE", true, true), PairingSide.AUTHOR, false),
                ErrorCode.TEAM_015);
        assertError(() -> guard.requireCaptain(ctx("ACTIVE", "ACTIVE", false, true), PairingSide.AUTHOR, false),
                ErrorCode.TEAM_002);
        assertError(() -> guard.requireCaptain(ctx("ACTIVE", "ACTIVE", true, false), PairingSide.INTERESTED, false),
                ErrorCode.TEAM_002);
        assertThatCode(() -> guard.requireCaptain(ctx("INACTIVE", "ACTIVE", true, false), PairingSide.AUTHOR, true))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.requireCaptain(ctx("ACTIVE", "ACTIVE", false, true), PairingSide.INTERESTED, false))
                .doesNotThrowAnyException();
    }

    @Test
    void requireNegotiatingRejectsFinishedNegotiation() {
        PairingContextView ctx = ctx("ACTIVE", "ACTIVE", true, true);
        when(ctx.getStatus()).thenReturn("SUPERSEDED");

        assertError(() -> guard.requireNegotiating(ctx), ErrorCode.PAIRING_005);
    }

    @Test
    void requireRevisionRejectsStalePackage() {
        PairingContextView ctx = ctx("ACTIVE", "ACTIVE", true, true);
        when(ctx.getRevision()).thenReturn(3);

        assertError(() -> guard.requireRevision(ctx, 2), ErrorCode.PAIRING_006);
        assertThatCode(() -> guard.requireRevision(ctx, 3)).doesNotThrowAnyException();
    }

    @Test
    void requireVenueActiveRejectsInactiveVenue() {
        assertError(() -> guard.requireVenueActive("INACTIVE"), ErrorCode.VENUE_002);
        assertThatCode(() -> guard.requireVenueActive("ACTIVE")).doesNotThrowAnyException();
    }

    private static PairingContextView ctx(String authorStatus, String interestedStatus, boolean callerAuthor,
                                          boolean callerInterested) {
        PairingContextView ctx = mock(PairingContextView.class);
        when(ctx.getAuthorTeamId()).thenReturn(AUTHOR_TEAM);
        when(ctx.getInterestedTeamId()).thenReturn(INTERESTED_TEAM);
        when(ctx.getAuthorTeamStatus()).thenReturn(authorStatus);
        when(ctx.getInterestedTeamStatus()).thenReturn(interestedStatus);
        when(ctx.getCallerAuthorCaptain()).thenReturn(callerAuthor);
        when(ctx.getCallerInterestedCaptain()).thenReturn(callerInterested);
        when(ctx.getStatus()).thenReturn("NEGOTIATING");
        when(ctx.getRevision()).thenReturn(0);
        return ctx;
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
