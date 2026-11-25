package com.phananh.arenaflow.domain.entity.chat;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.user.User;
import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiểm tra entity cuộc trò chuyện, tin nhắn, ánh xạ kiểu cột sender_party và cờ khoá cột điểm. */
class ConversationEntityTest {

    @Test
    void enumsMatchDatabaseValues() {
        assertThat(Conversation.ContextType.values()).extracting(Enum::name).containsExactly("TEAM_MATCH");
        assertThat(Conversation.PartyType.values()).extracting(Enum::name).containsExactly("TEAM", "USER");
        assertThat(Conversation.Party.values()).extracting(Enum::name).containsExactly("A", "B");
    }

    @Test
    void builderKeepsContextAndParties() {
        UUID matchId = UUID.randomUUID();
        UUID teamA = UUID.randomUUID();
        UUID teamB = UUID.randomUUID();

        Conversation conversation = Conversation.builder()
                .contextType(Conversation.ContextType.TEAM_MATCH)
                .matchId(matchId)
                .partyAType(Conversation.PartyType.TEAM).partyAId(teamA)
                .partyBType(Conversation.PartyType.TEAM).partyBId(teamB)
                .build();

        assertThat(conversation.getMatchId()).isEqualTo(matchId);
        assertThat(conversation.getPartyAId()).isEqualTo(teamA);
        assertThat(conversation.getPartyBId()).isEqualTo(teamB);
    }

    @Test
    void messageDefaultsToUnread() {
        ConversationMessage message = ConversationMessage.builder()
                .senderParty(Conversation.Party.A)
                .message("Xin chào")
                .build();

        assertThat(message.isRead()).isFalse();
        assertThat(message.getSenderParty()).isEqualTo(Conversation.Party.A);
    }

    @Test
    void reliabilityScoreIsNotUpdatableThroughEntities() throws NoSuchFieldException {
        assertThat(Team.class.getDeclaredField("reliabilityScore").getAnnotation(Column.class).updatable()).isFalse();
        assertThat(User.class.getDeclaredField("reliabilityScore").getAnnotation(Column.class).updatable()).isFalse();
    }

    @Test
    void senderPartyUsesVarcharJdbcTypeToMatchV7Column() throws NoSuchFieldException {
        JdbcTypeCode jdbcType = ConversationMessage.class.getDeclaredField("senderParty")
                .getAnnotation(JdbcTypeCode.class);

        assertThat(jdbcType).isNotNull();
        assertThat(jdbcType.value()).isEqualTo(SqlTypes.VARCHAR);
    }
}
