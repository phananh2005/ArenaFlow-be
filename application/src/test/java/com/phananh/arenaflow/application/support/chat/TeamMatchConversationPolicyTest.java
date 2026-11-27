package com.phananh.arenaflow.application.support.chat;

import com.phananh.arenaflow.domain.entity.chat.Conversation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.UUID;

import static com.phananh.arenaflow.application.support.chat.ConversationFixtures.NOW;
import static com.phananh.arenaflow.application.support.chat.ConversationFixtures.context;
import static com.phananh.arenaflow.application.support.chat.ConversationFixtures.teamMatchRow;
import static org.assertj.core.api.Assertions.assertThat;

/** Bảng nhắn được / chỉ xem của chat hai đội luồng A. */
class TeamMatchConversationPolicyTest {

    private final TeamMatchConversationPolicy policy = new TeamMatchConversationPolicy();
    private final UUID callerId = UUID.randomUUID();
    private final UUID teamA = UUID.randomUUID();
    private final UUID teamB = UUID.randomUUID();

    private Map<String, Object> row() {
        return teamMatchRow(UUID.randomUUID(), teamA, teamB);
    }

    @Test
    void handlesTeamMatchContext() {
        assertThat(policy.contextType()).isEqualTo(Conversation.ContextType.TEAM_MATCH);
    }

    @ParameterizedTest(name = "yêu cầu {0}, đã ghép {1} → gửi được {2}")
    @CsvSource({
            "PENDING, false, true",
            "ACCEPTED, true, true",
            "WITHDRAWN, true, true",
            "REJECTED, false, false",
            "SUPERSEDED, false, false",
            "EXPIRED, false, false",
            "WITHDRAWN, false, false"
    })
    void canSendFollowsLatestRequestAndMatchedTeam(String requestStatus, boolean matched, boolean canSend) {
        Map<String, Object> row = row();
        row.put("requestStatus", requestStatus);
        if (matched) {
            row.put("matchOpponentTeamId", teamB);
            row.put("matchStatus", "CANCELLED");
        }

        ConversationAccess access = policy.resolve(context(row), callerId, NOW);

        assertThat(access.viewerParty()).isEqualTo(Conversation.Party.A);
        assertThat(access.canSend()).isEqualTo(canSend);
        assertThat(access.expiryDue()).isFalse();
    }

    @Test
    void pendingOnExpiredOpenMatchIsExpiryDueAndCannotSend() {
        Map<String, Object> row = row();
        row.put("expiresAt", NOW);

        ConversationAccess access = policy.resolve(context(row), callerId, NOW);

        assertThat(access.expiryDue()).isTrue();
        assertThat(access.canSend()).isFalse();
    }

    @Test
    void captainOfBothTeamsActsAsAuthorTeam() {
        Map<String, Object> row = row();
        row.put("callerPartyBCaptain", true);

        assertThat(policy.resolve(context(row), callerId, NOW).viewerParty()).isEqualTo(Conversation.Party.A);
    }

    @Test
    void joiningTeamCaptainIsPartyB() {
        Map<String, Object> row = row();
        row.put("callerPartyACaptain", false);
        row.put("callerPartyBCaptain", true);

        ConversationAccess access = policy.resolve(context(row), callerId, NOW);

        assertThat(access.viewerParty()).isEqualTo(Conversation.Party.B);
        assertThat(access.canSend()).isTrue();
    }

    @Test
    void whoDisbandedOwnTeamIsReadOnlyAndNeverTriggersExpiry() {
        Map<String, Object> row = row();
        row.put("callerPartyACaptain", false);
        row.put("partyBTeamStatus", "DISBANDED");
        row.put("callerPartyBDisbandedBy", true);
        row.put("expiresAt", NOW.minusMinutes(1));

        ConversationAccess access = policy.resolve(context(row), callerId, NOW);

        assertThat(access.viewerParty()).isEqualTo(Conversation.Party.B);
        assertThat(access.viewerReadOnly()).isTrue();
        assertThat(access.canSend()).isFalse();
        assertThat(access.expiryDue()).isFalse();
    }

    @Test
    void outsiderHasNoParty() {
        Map<String, Object> row = row();
        row.put("callerPartyACaptain", false);
        row.put("partyBTeamStatus", "DISBANDED");

        ConversationAccess access = policy.resolve(context(row), callerId, NOW);

        assertThat(access.viewerParty()).isNull();
        assertThat(access.canSend()).isFalse();
    }
}
