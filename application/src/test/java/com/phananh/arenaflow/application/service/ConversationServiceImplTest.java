package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.ChatHistoryQuery;
import com.phananh.arenaflow.application.dto.request.SendConversationMessageRequest;
import com.phananh.arenaflow.application.dto.response.ChatHistoryResponse;
import com.phananh.arenaflow.application.dto.response.ConversationMessageResponse;
import com.phananh.arenaflow.application.dto.response.ConversationResponse;
import com.phananh.arenaflow.application.dto.response.ConversationSummaryResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.mapper.ConversationMapper;
import com.phananh.arenaflow.application.service.impl.ConversationServiceImpl;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.MatchExpiry;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.chat.ChatRealtimeGateway;
import com.phananh.arenaflow.application.support.chat.ConversationAccessPolicy;
import com.phananh.arenaflow.application.support.chat.RealtimeFrame;
import com.phananh.arenaflow.application.support.chat.TeamMatchConversationPolicy;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.entity.chat.ConversationMessage;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.projection.ChatCursorView;
import com.phananh.arenaflow.domain.projection.ConversationMessageView;
import com.phananh.arenaflow.domain.projection.MineCountView;
import com.phananh.arenaflow.domain.repository.ConversationMessageRepository;
import com.phananh.arenaflow.domain.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static com.phananh.arenaflow.application.support.chat.ConversationFixtures.NOW;
import static com.phananh.arenaflow.application.support.chat.ConversationFixtures.context;
import static com.phananh.arenaflow.application.support.chat.ConversationFixtures.listRow;
import static com.phananh.arenaflow.application.support.chat.ConversationFixtures.teamMatchRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationServiceImplTest {

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ConversationMessageRepository messageRepository = mock(ConversationMessageRepository.class);
    private final MatchExpiry matchExpiry = mock(MatchExpiry.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final ChatRealtimeGateway gateway = mock(ChatRealtimeGateway.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneId.systemDefault());
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID teamA = UUID.randomUUID();
    private final UUID teamB = UUID.randomUUID();
    private ConversationServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new ConversationServiceImpl(conversationRepository, messageRepository,
                List.of(new TeamMatchConversationPolicy()), matchExpiry, retrier,
                Mappers.getMapper(ConversationMapper.class), gateway, new PageRequestFactory(), clock);
        when(retrier.execute(eq(ErrorCode.MATCH_014), any()))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(1)).get());
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    private Map<String, Object> row() {
        return teamMatchRow(conversationId, teamA, teamB);
    }

    private void givenContext(Map<String, Object> row) {
        when(conversationRepository.findContext(conversationId, callerId, NOW)).thenReturn(Optional.of(context(row)));
    }

    @Test
    void missingPolicyForAContextTypeFailsAtStartup() {
        assertThatThrownBy(() -> new ConversationServiceImpl(conversationRepository, messageRepository,
                List.<ConversationAccessPolicy>of(), matchExpiry, retrier, Mappers.getMapper(ConversationMapper.class),
                gateway, new PageRequestFactory(), clock)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sendSavesTrimmedMessageAsCallerPartyAndPublishesAfterCommit() {
        givenContext(row());
        when(messageRepository.save(any(ConversationMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConversationMessageResponse response = service.send(conversationId, callerId,
                new SendConversationMessageRequest("  18:00 sân Y được không?  "));

        ArgumentCaptor<ConversationMessage> saved = ArgumentCaptor.forClass(ConversationMessage.class);
        verify(messageRepository).save(saved.capture());
        assertThat(saved.getValue().getMessage()).isEqualTo("18:00 sân Y được không?");
        assertThat(saved.getValue().getSenderParty()).isEqualTo(Conversation.Party.A);
        assertThat(saved.getValue().getSenderUserId()).isEqualTo(callerId);
        assertThat(response.senderPartyId()).isEqualTo(teamA);
        assertThat(response.senderName()).isEqualTo("Minh");
        verify(gateway).publishAfterCommit(conversationId, new RealtimeFrame.MessageCreated(response));
    }

    @Test
    void sendChecksExistenceDeletedCallerParticipationThenReadOnly() {
        when(conversationRepository.findContext(conversationId, callerId, NOW)).thenReturn(Optional.empty());
        assertError(() -> service.send(conversationId, callerId, new SendConversationMessageRequest("hi")),
                ErrorCode.CHAT_002);

        Map<String, Object> deleted = row();
        deleted.put("callerDeleted", true);
        givenContext(deleted);
        assertError(() -> service.send(conversationId, callerId, new SendConversationMessageRequest("hi")),
                ErrorCode.USER_001);

        Map<String, Object> outsider = row();
        outsider.put("callerPartyACaptain", false);
        outsider.put("expiresAt", NOW.minusDays(1));
        givenContext(outsider);
        assertError(() -> service.send(conversationId, callerId, new SendConversationMessageRequest("hi")),
                ErrorCode.CHAT_003);
        verifyNoInteractions(matchExpiry);

        Map<String, Object> rejected = row();
        rejected.put("requestStatus", "REJECTED");
        givenContext(rejected);
        assertError(() -> service.send(conversationId, callerId, new SendConversationMessageRequest("hi")),
                ErrorCode.CHAT_004);
        verify(messageRepository, never()).save(any());
        verifyNoInteractions(gateway);
    }

    @Test
    void sendOnPendingRequestOfExpiredMatchExpiresItAndReturnsMatch004() {
        Map<String, Object> row = row();
        row.put("expiresAt", NOW);
        givenContext(row);

        assertError(() -> service.send(conversationId, callerId, new SendConversationMessageRequest("hi")),
                ErrorCode.MATCH_004);
        verify(matchExpiry).expireIfDue(any(UUID.class), eq(teamA), eq(Match.Type.HAS_VENUE), eq(NOW));
        verify(messageRepository, never()).save(any());
    }

    private ConversationMessageView messageView(String party) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", UUID.randomUUID());
        values.put("conversationId", conversationId);
        values.put("senderParty", party);
        values.put("senderUserId", UUID.randomUUID());
        values.put("senderName", "Nam");
        values.put("senderDeleted", true);
        values.put("message", "OK");
        values.put("isRead", false);
        values.put("createdAt", NOW);
        return projections.createProjection(ConversationMessageView.class, values);
    }

    @Test
    void historyFetchesLimitPlusOneAndReportsHasMore() {
        givenContext(row());
        when(messageRepository.findLatest(conversationId, 3)).thenReturn(new ArrayList<>(List.of(
                messageView("B"), messageView("A"), messageView("B"))));

        ChatHistoryResponse response = service.history(conversationId, callerId, new ChatHistoryQuery(null, null, 2));

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.items().getFirst().senderPartyId()).isEqualTo(teamB);
        assertThat(response.items().getFirst().senderName()).isEqualTo("Người dùng đã xóa");
        assertThat(response.items().getFirst().senderAvatarUrl()).isNull();
    }

    @Test
    void historyWithForeignCursorIsChat001() {
        givenContext(row());
        UUID before = UUID.randomUUID();
        when(messageRepository.findCursor(conversationId, before)).thenReturn(Optional.empty());

        assertError(() -> service.history(conversationId, callerId, new ChatHistoryQuery(before, null, 50)),
                ErrorCode.CHAT_001);
    }

    @Test
    void historyAfterCursorReadsOlderFirstPage() {
        givenContext(row());
        UUID after = UUID.randomUUID();
        ChatCursorView cursor = projections.createProjection(ChatCursorView.class, Map.of("createdAt", NOW, "id", after));
        when(messageRepository.findCursor(conversationId, after)).thenReturn(Optional.of(cursor));
        when(messageRepository.findAfter(conversationId, NOW, after, 51)).thenReturn(List.of(messageView("A")));

        ChatHistoryResponse response = service.history(conversationId, callerId, new ChatHistoryQuery(null, after, null));

        assertThat(response.items()).hasSize(1);
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void readOnlyViewerMayReadAndMarkReadButCannotSend() {
        Map<String, Object> row = row();
        row.put("callerPartyACaptain", false);
        row.put("partyATeamStatus", "DISBANDED");
        row.put("callerPartyADisbandedBy", true);
        givenContext(row);

        service.markRead(conversationId, callerId);
        verify(messageRepository).markOtherPartyRead(conversationId, "A");

        ConversationResponse header = service.getConversation(conversationId, callerId);
        assertThat(header.canSend()).isFalse();
        assertThat(header.viewerPartyId()).isEqualTo(teamA);
        service.requireSubscribable(conversationId, callerId);

        assertError(() -> service.send(conversationId, callerId, new SendConversationMessageRequest("hi")),
                ErrorCode.CHAT_004);
    }

    @Test
    void subscribeByOutsiderIsChat003() {
        Map<String, Object> row = row();
        row.put("callerPartyACaptain", false);
        givenContext(row);

        assertError(() -> service.requireSubscribable(conversationId, callerId), ErrorCode.CHAT_003);
    }

    @Test
    void myConversationsSkipsPageQueryWhenCountIsZeroAndRejectsDeletedCaller() {
        when(conversationRepository.countMine(callerId)).thenReturn(projections.createProjection(MineCountView.class,
                Map.of("total", 0L, "callerDeleted", false)));
        PageResponse<ConversationSummaryResponse> empty = service.listMyConversations(callerId, 1, 20);
        assertThat(empty.data()).isEmpty();
        verify(conversationRepository, never()).findMine(any(), any(), anyInt(), anyLong());

        when(conversationRepository.countMine(callerId)).thenReturn(projections.createProjection(MineCountView.class,
                Map.of("total", 0L, "callerDeleted", true)));
        assertError(() -> service.listMyConversations(callerId, 1, 20), ErrorCode.USER_001);
    }

    @Test
    void myConversationsResolvesPolicyPerRowAndUnreadFromOtherSide() {
        when(conversationRepository.countMine(callerId)).thenReturn(projections.createProjection(MineCountView.class,
                Map.of("total", 2L, "callerDeleted", false)));
        Map<String, Object> mine = row();
        mine.put("lastActivityAt", NOW);
        mine.put("unreadFromA", true);
        mine.put("unreadFromB", true);
        mine.put("startsAt", NOW.plusDays(1));
        Map<String, Object> notMine = row();
        notMine.put("id", UUID.randomUUID());
        notMine.put("callerPartyACaptain", false);
        notMine.put("lastActivityAt", NOW);
        when(conversationRepository.findMine(callerId, NOW, 20, 20L)).thenReturn(List.of(listRow(mine), listRow(notMine)));

        PageResponse<ConversationSummaryResponse> page = service.listMyConversations(callerId, 2, 20);

        assertThat(page.data()).singleElement().satisfies(summary -> {
            assertThat(summary.viewerParty().partyId()).isEqualTo(teamA);
            assertThat(summary.otherParty().name()).isEqualTo("Team Beta");
            assertThat(summary.hasUnread()).isTrue();
            assertThat(summary.canSend()).isTrue();
            assertThat(summary.lastMessage()).isNull();
        });
        assertThat(page.pagination().totalItems()).isEqualTo(2);
    }
}
