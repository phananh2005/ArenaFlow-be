package com.phananh.arenaflow.application.support.reputation;

import com.phananh.arenaflow.domain.projection.MatchSidesView;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchSidesTest {

    private final UUID author = UUID.randomUUID();
    private final UUID opponent = UUID.randomUUID();

    @Test
    void callerCaptainOfAuthorActsForAuthorAgainstOpponent() {
        MatchSides sides = MatchSides.of(view(true, false));

        assertThat(sides.ownTeamId()).isEqualTo(author);
        assertThat(sides.otherTeamId()).isEqualTo(opponent);
        assertThat(sides.ownIsAuthor()).isTrue();
    }

    @Test
    void callerCaptainOfOpponentActsForOpponentAgainstAuthor() {
        MatchSides sides = MatchSides.of(view(false, true));

        assertThat(sides.ownTeamId()).isEqualTo(opponent);
        assertThat(sides.otherTeamId()).isEqualTo(author);
        assertThat(sides.ownIsAuthor()).isFalse();
    }

    @Test
    void callerTeamInactiveOnlyChecksSidesCallerLeads() {
        assertThat(MatchSides.isCallerTeamInactive(view(true, false, "INACTIVE", "ACTIVE"))).isTrue();
        assertThat(MatchSides.isCallerTeamInactive(view(true, false, "ACTIVE", "INACTIVE"))).isFalse();
        assertThat(MatchSides.isCallerTeamInactive(view(true, true, "ACTIVE", "INACTIVE"))).isTrue();
    }

    @Test
    void sideFlagsReportNoneOrBoth() {
        assertThat(MatchSides.isCaptainOfAny(view(false, false))).isFalse();
        assertThat(MatchSides.isCaptainOfBoth(view(true, true))).isTrue();
        assertThat(MatchSides.isCaptainOfBoth(view(true, false))).isFalse();
        assertThatThrownBy(() -> MatchSides.of(view(true, true))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MatchSides.of(view(false, false))).isInstanceOf(IllegalStateException.class);
    }

    private MatchSidesView view(boolean authorCaptain, boolean opponentCaptain) {
        return view(authorCaptain, opponentCaptain, "ACTIVE", "ACTIVE");
    }

    private MatchSidesView view(boolean authorCaptain, boolean opponentCaptain, String authorStatus,
                                String opponentStatus) {
        return new MatchSidesView() {
            @Override
            public String getAuthorTeamStatus() {
                return authorStatus;
            }

            @Override
            public String getOpponentTeamStatus() {
                return opponentStatus;
            }

            @Override
            public UUID getAuthorTeamId() {
                return author;
            }

            @Override
            public UUID getOpponentTeamId() {
                return opponent;
            }

            @Override
            public Boolean getCallerAuthorCaptain() {
                return authorCaptain;
            }

            @Override
            public Boolean getCallerOpponentCaptain() {
                return opponentCaptain;
            }
        };
    }
}
