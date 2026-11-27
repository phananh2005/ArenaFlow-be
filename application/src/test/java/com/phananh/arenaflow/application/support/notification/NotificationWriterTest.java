package com.phananh.arenaflow.application.support.notification;

import com.phananh.arenaflow.application.dto.response.NotificationResponse;
import com.phananh.arenaflow.application.mapper.NotificationMapper;
import com.phananh.arenaflow.application.support.realtime.UserFrame;
import com.phananh.arenaflow.application.support.realtime.UserFrameDelivery;
import com.phananh.arenaflow.application.support.realtime.UserRealtimeGateway;
import com.phananh.arenaflow.domain.entity.chat.Notification;
import com.phananh.arenaflow.domain.projection.InsertedNotificationView;
import com.phananh.arenaflow.domain.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationWriterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);
    private static final UUID MATCH = UUID.randomUUID();

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final UserRealtimeGateway gateway = mock(UserRealtimeGateway.class);
    private final PushSender pushSender = mock(PushSender.class);
    private final NotificationMapper mapper = Mappers.getMapper(NotificationMapper.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final NotificationWriter writer = new NotificationWriter(repository, mapper, gateway, pushSender);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void requiresActiveTransaction() {
        assertThatThrownBy(() -> writer.write(List.of(draft(UUID.randomUUID())), NOW))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void emptyOrNullOnlyDraftsDoNotQuery() {
        TransactionSynchronizationManager.initSynchronization();

        assertThat(writer.write(List.of(), NOW)).isZero();
        assertThat(writer.write(List.of(draft(null)), NOW)).isZero();

        verifyNoInteractions(repository, gateway, pushSender);
    }

    @Test
    void deduplicatesByRecipientTypeAndReferenceThenWritesOneStatementAndPublishesFrames() {
        TransactionSynchronizationManager.initSynchronization();
        UUID captain = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(repository.insertAllReturning(any(), any(), any(), any(), any(), any(), eq(NOW)))
                .thenReturn(List.of(inserted(captain), inserted(other)));

        int written = writer.write(List.of(draft(captain), draft(captain), draft(other), draft(null)), NOW);

        assertThat(written).isEqualTo(2);
        ArgumentCaptor<UUID[]> recipients = ArgumentCaptor.forClass(UUID[].class);
        ArgumentCaptor<String[]> types = ArgumentCaptor.forClass(String[].class);
        ArgumentCaptor<String[]> referenceTypes = ArgumentCaptor.forClass(String[].class);
        verify(repository, times(1)).insertAllReturning(recipients.capture(), types.capture(), any(), any(),
                referenceTypes.capture(), any(), eq(NOW));
        assertThat(recipients.getValue()).containsExactly(captain, other);
        assertThat(types.getValue()).containsExactly("MATCH_CONFIRMED", "MATCH_CONFIRMED");
        assertThat(referenceTypes.getValue()).containsExactly("MATCH", "MATCH");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UserFrameDelivery>> deliveries = ArgumentCaptor.forClass(Collection.class);
        verify(gateway).publishAfterCommit(deliveries.capture());
        assertThat(deliveries.getValue()).extracting(UserFrameDelivery::userId).containsExactly(captain, other);
        NotificationResponse frame = ((UserFrame.NotificationCreated) deliveries.getValue().iterator().next().frame())
                .notification();
        assertThat(frame.isRead()).isFalse();
        assertThat(frame.type()).isEqualTo(Notification.Type.MATCH_CONFIRMED);
    }

    @Test
    void differentReferenceIsNotADuplicate() {
        TransactionSynchronizationManager.initSynchronization();
        UUID captain = UUID.randomUUID();
        when(repository.insertAllReturning(any(), any(), any(), any(), any(), any(), eq(NOW))).thenReturn(List.of());

        writer.write(List.of(draft(captain),
                new NotificationDraft(captain, Notification.Type.MATCH_CONFIRMED, "t", "b",
                        Notification.ReferenceType.MATCH, UUID.randomUUID())), NOW);

        ArgumentCaptor<UUID[]> recipients = ArgumentCaptor.forClass(UUID[].class);
        verify(repository).insertAllReturning(recipients.capture(), any(), any(), any(), any(), any(), eq(NOW));
        assertThat(recipients.getValue()).hasSize(2);
        verify(gateway, never()).publishAfterCommit(any());
    }

    @Test
    void pushIsSentOnlyAfterCommitAndPushFailureIsSwallowed() {
        TransactionSynchronizationManager.initSynchronization();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(repository.insertAllReturning(any(), any(), any(), any(), any(), any(), eq(NOW)))
                .thenReturn(List.of(inserted(first), inserted(second)));
        doThrow(new IllegalStateException("push down")).when(pushSender).send(eq(first), any());

        writer.write(List.of(draft(first), draft(second)), NOW);

        verifyNoInteractions(pushSender);
        List<TransactionSynchronization> synchronizations = new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        verify(pushSender).send(eq(first), any(PushMessage.class));
        verify(pushSender).send(eq(second), any(PushMessage.class));
    }

    @Test
    void rollbackDoesNotPush() {
        TransactionSynchronizationManager.initSynchronization();
        UUID first = UUID.randomUUID();
        when(repository.insertAllReturning(any(), any(), any(), any(), any(), any(), eq(NOW)))
                .thenReturn(List.of(inserted(first)));

        writer.write(List.of(draft(first)), NOW);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(pushSender);
    }

    private static NotificationDraft draft(UUID recipient) {
        return new NotificationDraft(recipient, Notification.Type.MATCH_CONFIRMED, "Kèo đã chốt", "Nội dung",
                Notification.ReferenceType.MATCH, MATCH);
    }

    private InsertedNotificationView inserted(UUID recipient) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", UUID.randomUUID());
        values.put("recipientUserId", recipient);
        values.put("type", "MATCH_CONFIRMED");
        values.put("title", "Kèo đã chốt");
        values.put("body", "Nội dung");
        values.put("referenceType", "MATCH");
        values.put("referenceId", MATCH);
        values.put("createdAt", NOW);
        return projections.createProjection(InsertedNotificationView.class, values);
    }
}
