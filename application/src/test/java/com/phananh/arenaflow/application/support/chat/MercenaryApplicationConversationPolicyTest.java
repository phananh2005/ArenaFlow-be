package com.phananh.arenaflow.application.support.chat;

import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.projection.ConversationContextView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MercenaryApplicationConversationPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);
    private final MercenaryApplicationConversationPolicy policy = new MercenaryApplicationConversationPolicy();
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID callerId = UUID.randomUUID();

    @Test
    void servesMercenaryApplicationContext() {
        assertThat(policy.contextType()).isEqualTo(Conversation.ContextType.MERCENARY_APPLICATION);
    }

    @ParameterizedTest
    @CsvSource({"PENDING, true", "APPROVED, true", "REJECTED, false", "WITHDRAWN, false"})
    void captainIsPartyAAndCanSendOnlyWhileApplicationIsActive(String status, boolean canSend) {
        ConversationAccess access = policy.resolve(ctx(Map.of("callerMercenaryCaptain", true,
                "applicationEffectiveStatus", status)), callerId, NOW);

        assertThat(access.viewerParty()).isEqualTo(Conversation.Party.A);
        assertThat(access.canSend()).isEqualTo(canSend);
        assertThat(access.viewerReadOnly()).isFalse();
        assertThat(access.expiryDue()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"PENDING, true", "APPROVED, true", "REJECTED, false"})
    void applicantIsPartyB(String status, boolean canSend) {
        ConversationAccess access = policy.resolve(ctx(Map.of("callerApplicant", true,
                "applicationEffectiveStatus", status)), callerId, NOW);

        assertThat(access.viewerParty()).isEqualTo(Conversation.Party.B);
        assertThat(access.canSend()).isEqualTo(canSend);
    }

    @Test
    void personWhoDisbandedRecruitingTeamOnlyViews() {
        ConversationAccess access = policy.resolve(ctx(Map.of("mercenaryTeamStatus", "DISBANDED",
                "callerMercenaryDisbandedBy", true, "applicationEffectiveStatus", "APPROVED")), callerId, NOW);

        assertThat(access.viewerParty()).isEqualTo(Conversation.Party.A);
        assertThat(access.canSend()).isFalse();
        assertThat(access.viewerReadOnly()).isTrue();
    }

    @Test
    void disbandedByFlagWithoutDisbandedTeamGrantsNothing() {
        ConversationAccess access = policy.resolve(ctx(Map.of("callerMercenaryDisbandedBy", true)), callerId, NOW);

        assertThat(access.viewerParty()).isNull();
        assertThat(access.canSend()).isFalse();
    }

    @Test
    void otherMemberIsNotAParty() {
        ConversationAccess access = policy.resolve(ctx(Map.of()), callerId, NOW);

        assertThat(access.viewerParty()).isNull();
        assertThat(access.canSend()).isFalse();
        assertThat(access.viewerReadOnly()).isFalse();
    }

    private ConversationContextView ctx(Map<String, Object> overrides) {
        Map<String, Object> values = new HashMap<>();
        values.put("contextType", "MERCENARY_APPLICATION");
        values.put("applicationEffectiveStatus", "PENDING");
        values.put("mercenaryTeamStatus", "ACTIVE");
        values.put("callerMercenaryCaptain", false);
        values.put("callerMercenaryDisbandedBy", false);
        values.put("callerApplicant", false);
        values.putAll(overrides);
        return projections.createProjection(ConversationContextView.class, values);
    }
}
