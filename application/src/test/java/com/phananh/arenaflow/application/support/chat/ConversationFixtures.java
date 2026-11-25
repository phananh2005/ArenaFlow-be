package com.phananh.arenaflow.application.support.chat;

import com.phananh.arenaflow.domain.projection.ConversationContextView;
import com.phananh.arenaflow.domain.projection.ConversationListRowView;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Dựng dòng ngữ cảnh cuộc trò chuyện luồng A cho unit test (đội A tạo kèo, đội B gửi yêu cầu). */
public final class ConversationFixtures {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);

    private static final SpelAwareProxyProjectionFactory PROJECTIONS = new SpelAwareProxyProjectionFactory();

    private ConversationFixtures() {
    }

    /** Ngữ cảnh mặc định: người gọi là đội trưởng đội A, yêu cầu gần nhất PENDING, kèo OPEN còn hạn. */
    public static Map<String, Object> teamMatchRow(UUID conversationId, UUID teamA, UUID teamB) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", conversationId);
        row.put("contextType", "TEAM_MATCH");
        row.put("matchId", UUID.randomUUID());
        row.put("partyAType", "TEAM");
        row.put("partyAId", teamA);
        row.put("partyBType", "TEAM");
        row.put("partyBId", teamB);
        row.put("callerName", "Minh");
        row.put("callerDeleted", false);
        row.put("matchType", "HAS_VENUE");
        row.put("matchStatus", "OPEN");
        row.put("expiresAt", NOW.plusHours(3));
        row.put("authorTeamId", teamA);
        row.put("partyAName", "Team Alpha");
        row.put("partyACaptainName", "Minh");
        row.put("partyACaptainDeleted", false);
        row.put("callerPartyACaptain", true);
        row.put("partyATeamStatus", "ACTIVE");
        row.put("callerPartyADisbandedBy", false);
        row.put("partyBName", "Team Beta");
        row.put("partyBCaptainName", "Nam");
        row.put("partyBCaptainDeleted", false);
        row.put("callerPartyBCaptain", false);
        row.put("partyBTeamStatus", "ACTIVE");
        row.put("callerPartyBDisbandedBy", false);
        row.put("requestId", UUID.randomUUID());
        row.put("requestStatus", "PENDING");
        row.put("requestEffectiveStatus", "PENDING");
        return row;
    }

    public static ConversationContextView context(Map<String, Object> row) {
        return PROJECTIONS.createProjection(ConversationContextView.class, row);
    }

    public static ConversationListRowView listRow(Map<String, Object> row) {
        return PROJECTIONS.createProjection(ConversationListRowView.class, row);
    }
}
