package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.service.impl.NotificationJobServiceImpl;
import com.phananh.arenaflow.application.support.notification.NotificationDraft;
import com.phananh.arenaflow.application.support.notification.NotificationProperties;
import com.phananh.arenaflow.application.support.notification.NotificationTexts;
import com.phananh.arenaflow.application.support.notification.NotificationWriter;
import com.phananh.arenaflow.domain.entity.chat.Notification.ReferenceType;
import com.phananh.arenaflow.domain.entity.chat.Notification.Type;
import com.phananh.arenaflow.domain.projection.GameAudienceRowView;
import com.phananh.arenaflow.domain.projection.ReminderGameView;
import com.phananh.arenaflow.domain.repository.MatchReminderRepository;
import com.phananh.arenaflow.domain.repository.NotificationAudienceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationJobServiceImplTest {

    /** 10:00 giờ Việt Nam. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);
    private static final LocalDateTime START = NOW.plusHours(2);

    private final MatchReminderRepository reminderRepository = mock(MatchReminderRepository.class);
    private final NotificationAudienceRepository audienceRepository = mock(NotificationAudienceRepository.class);
    private final NotificationWriter writer = mock(NotificationWriter.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final NotificationJobService service = new NotificationJobServiceImpl(reminderRepository, audienceRepository,
            writer, new NotificationTexts(), transactionTemplate,
            new NotificationProperties(new NotificationProperties.Reminder(2, 3, Duration.ofHours(24)),
                    new NotificationProperties.Retention(200, 1000, 100), new NotificationProperties.Executor(2, 4, 1000)),
            Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneId.systemDefault()));

    @BeforeEach
    void runCallbacksInline() {
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
    }

    @Test
    void startRemindersClaimWithTwoHourWindowAndNotifyCaptainsAndMercenaries() {
        UUID match = UUID.randomUUID();
        UUID external = UUID.randomUUID();
        UUID captain = UUID.randomUUID();
        UUID mercenary = UUID.randomUUID();
        UUID application = UUID.randomUUID();
        when(reminderRepository.claimDueStart(NOW, NOW.plusHours(2), 7200L, 2))
                .thenReturn(List.of(game(match, null), game(null, external)), List.of());
        when(audienceRepository.findGameAudience(any(), any(), any())).thenReturn(List.of(
                row("CAPTAIN", captain, match, null, null, "FC A", "FC B", null),
                row("MERCENARY", mercenary, null, external, application, "FC C", null, "Thủ môn")));

        int reminded = service.sendDueStartReminders();

        assertThat(reminded).isEqualTo(2);
        ArgumentCaptor<UUID[]> matchIds = ArgumentCaptor.forClass(UUID[].class);
        ArgumentCaptor<String[]> statuses = ArgumentCaptor.forClass(String[].class);
        ArgumentCaptor<UUID[]> externalIds = ArgumentCaptor.forClass(UUID[].class);
        verify(audienceRepository).findGameAudience(matchIds.capture(), statuses.capture(), externalIds.capture());
        assertThat(matchIds.getValue()).containsExactly(match);
        assertThat(statuses.getValue()).containsExactly("CONFIRMED");
        assertThat(externalIds.getValue()).containsExactly(external);
        assertThat(drafts(1)).extracting(NotificationDraft::recipientUserId, NotificationDraft::type,
                NotificationDraft::referenceType, NotificationDraft::referenceId)
                .containsExactly(tuple(captain, Type.REMINDER_BEFORE_MATCH, ReferenceType.MATCH, match),
                        tuple(mercenary, Type.REMINDER_BEFORE_MATCH, ReferenceType.MERCENARY_APPLICATION, application));
    }

    @Test
    void stopsWhenClaimIsShorterThanBatchAndCapsBatchesPerRun() {
        when(reminderRepository.claimDueStart(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(game(UUID.randomUUID(), null), game(UUID.randomUUID(), null)));
        when(audienceRepository.findGameAudience(any(), any(), any())).thenReturn(List.of());

        assertThat(service.sendDueStartReminders()).isEqualTo(6);
        verify(reminderRepository, times(3)).claimDueStart(any(), any(), anyLong(), anyInt());
        verify(writer, never()).write(any(), any());
    }

    @Test
    void emptyClaimStopsWithoutReadingAudience() {
        when(reminderRepository.claimDueStart(any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        assertThat(service.sendDueStartReminders()).isZero();
        verify(audienceRepository, never()).findGameAudience(any(), any(), any());
    }

    @Test
    void failedBatchStopsTheRun() {
        when(reminderRepository.claimDueStart(any(), any(), anyLong(), anyInt()))
                .thenThrow(new QueryTimeoutException("timeout"));

        assertThat(service.sendDueStartReminders()).isZero();
        verify(reminderRepository, times(1)).claimDueStart(any(), any(), anyLong(), anyInt());
    }

    @Test
    void ratingRemindersUseLookbackWindowAndOnlyExternalMatches() {
        UUID external = UUID.randomUUID();
        UUID mercenary = UUID.randomUUID();
        UUID application = UUID.randomUUID();
        when(reminderRepository.claimDueRating(NOW, NOW.minusHours(24), 2)).thenReturn(List.of(external));
        when(audienceRepository.findGameAudience(any(), any(), any())).thenReturn(List.of(
                row("MERCENARY", mercenary, null, external, application, "FC C", null, "Tiền đạo")));

        assertThat(service.sendDueRatingReminders()).isEqualTo(1);

        ArgumentCaptor<UUID[]> matchIds = ArgumentCaptor.forClass(UUID[].class);
        ArgumentCaptor<String[]> statuses = ArgumentCaptor.forClass(String[].class);
        verify(audienceRepository).findGameAudience(matchIds.capture(), statuses.capture(), eq(new UUID[]{external}));
        assertThat(matchIds.getValue()).isEmpty();
        assertThat(statuses.getValue()).isEmpty();
        assertThat(drafts(1)).extracting(NotificationDraft::recipientUserId, NotificationDraft::type,
                NotificationDraft::referenceType, NotificationDraft::referenceId)
                .containsExactly(tuple(mercenary, Type.MERCENARY_RATING_REMINDER, ReferenceType.MERCENARY_APPLICATION,
                        application));
    }

    @SuppressWarnings("unchecked")
    private List<NotificationDraft> drafts(int calls) {
        ArgumentCaptor<List<NotificationDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(writer, times(calls)).write(captor.capture(), eq(NOW));
        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }

    private ReminderGameView game(UUID match, UUID external) {
        Map<String, Object> values = new HashMap<>();
        values.put("matchId", match);
        values.put("externalMatchId", external);
        return projections.createProjection(ReminderGameView.class, values);
    }

    private GameAudienceRowView row(String kind, UUID recipient, UUID match, UUID external, UUID application,
                                    String team, String opponent, String position) {
        Map<String, Object> values = new HashMap<>();
        values.put("kind", kind);
        values.put("recipientUserId", recipient);
        values.put("matchId", match);
        values.put("externalMatchId", external);
        values.put("applicationId", application);
        values.put("startTime", START);
        values.put("venueName", "Sân X");
        values.put("teamName", team);
        values.put("opponentTeamName", opponent);
        values.put("position", position);
        return projections.createProjection(GameAudienceRowView.class, values);
    }
}
