package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.ConversationResponse;
import com.phananh.arenaflow.application.dto.response.ConversationSummaryResponse;
import com.phananh.arenaflow.application.support.chat.ConversationAccess;
import com.phananh.arenaflow.domain.entity.chat.Conversation;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Map;
import java.util.UUID;

import static com.phananh.arenaflow.application.support.chat.ConversationFixtures.NOW;
import static com.phananh.arenaflow.application.support.chat.ConversationFixtures.context;
import static com.phananh.arenaflow.application.support.chat.ConversationFixtures.listRow;
import static com.phananh.arenaflow.application.support.chat.ConversationFixtures.needVenueRow;
import static com.phananh.arenaflow.application.support.chat.ConversationFixtures.teamMatchRow;
import static org.assertj.core.api.Assertions.assertThat;

/** Đầu phòng chat, tin nhắn và dòng "Tin nhắn của tôi" dựng từ ngữ cảnh đã nạp (luồng A, luồng B). */
class ConversationMapperTest {

    private final ConversationMapper mapper = Mappers.getMapper(ConversationMapper.class);
    private final UUID teamA = UUID.randomUUID();
    private final UUID teamB = UUID.randomUUID();

    @Test
    void headerListsAuthorTeamFirstMasksDeletedCaptainAndUsesEffectiveRequestStatus() {
        Map<String, Object> row = teamMatchRow(UUID.randomUUID(), teamA, teamB);
        row.put("partyBCaptainDeleted", true);
        row.put("requestEffectiveStatus", "EXPIRED");
        row.put("partyATeamStatus", "DISBANDED");
        row.put("partyACaptainName", null);

        ConversationResponse response = mapper.toResponse(context(row),
                new ConversationAccess(Conversation.Party.B, false, false, false));

        assertThat(response.parties()).extracting(party -> party.partyId()).containsExactly(teamA, teamB);
        assertThat(response.parties().get(0).captainName()).isNull();
        assertThat(response.parties().get(1).captainName()).isEqualTo("Người dùng đã xóa");
        assertThat(response.viewerPartyId()).isEqualTo(teamB);
        assertThat(response.requestStatus()).isEqualTo("EXPIRED");
        assertThat(response.matchType()).isEqualTo("HAS_VENUE");
        assertThat(response.source()).isEqualTo("SYSTEM");
        assertThat(response.pairingId()).isNull();
        assertThat(response.applicationId()).isNull();
    }

    @Test
    void needVenueHeaderAndSummaryCarryPairingWithEffectiveStatus() {
        Map<String, Object> row = needVenueRow(UUID.randomUUID(), teamA, teamB);
        row.put("pairingEffectiveStatus", "EXPIRED");
        row.put("lastActivityAt", NOW);
        row.put("startsAt", NOW.plusDays(1));
        ConversationAccess access = new ConversationAccess(Conversation.Party.A, false, false, false);

        ConversationResponse response = mapper.toResponse(context(row), access);
        ConversationSummaryResponse summary = mapper.toSummary(listRow(row), access);

        assertThat(response.matchType()).isEqualTo("NEED_VENUE");
        assertThat(response.pairingId()).isEqualTo(row.get("pairingId"));
        assertThat(response.pairingStatus()).isEqualTo("EXPIRED");
        assertThat(response.requestId()).isNull();
        assertThat(response.requestStatus()).isNull();
        assertThat(summary.pairingId()).isEqualTo(row.get("pairingId"));
        assertThat(summary.requestId()).isNull();
    }

    @Test
    void summaryShowsOtherPartyLastMessagePreviewAndUnreadOfOtherSide() {
        Map<String, Object> row = teamMatchRow(UUID.randomUUID(), teamA, teamB);
        UUID messageId = UUID.randomUUID();
        row.put("lastActivityAt", NOW);
        row.put("lastMessageId", messageId);
        row.put("lastContentPreview", "Sân X có chỗ để xe không?");
        row.put("lastSenderParty", "B");
        row.put("lastMessageCreatedAt", NOW);
        row.put("lastSenderName", "Nam");
        row.put("lastSenderDeleted", false);
        row.put("unreadFromA", false);
        row.put("unreadFromB", true);
        row.put("startsAt", NOW.plusDays(1));

        ConversationSummaryResponse summary = mapper.toSummary(listRow(row),
                new ConversationAccess(Conversation.Party.A, true, false, false));

        assertThat(summary.viewerParty().name()).isEqualTo("Team Alpha");
        assertThat(summary.otherParty().partyId()).isEqualTo(teamB);
        assertThat(summary.lastMessage().messageId()).isEqualTo(messageId);
        assertThat(summary.lastMessage().senderPartyId()).isEqualTo(teamB);
        assertThat(summary.hasUnread()).isTrue();
        assertThat(summary.startsAt()).isEqualTo(NOW.plusDays(1));
        assertThat(summary.requestId()).isEqualTo(row.get("requestId"));
    }
}
