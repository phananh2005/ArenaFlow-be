package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.NotificationListQuery;
import com.phananh.arenaflow.application.dto.response.NotificationHistoryResponse;
import com.phananh.arenaflow.application.mapper.NotificationMapper;
import com.phananh.arenaflow.application.service.impl.NotificationServiceImpl;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.projection.NotificationCallerView;
import com.phananh.arenaflow.domain.projection.NotificationReadContextView;
import com.phananh.arenaflow.domain.projection.NotificationView;
import com.phananh.arenaflow.domain.projection.UnreadCountView;
import com.phananh.arenaflow.domain.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-11-21T03:00:00Z");
    /** 10:00 giờ Việt Nam. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);
    private static final LocalDateTime VISIBLE_FROM = NOW.minusDays(30);
    private static final UUID CALLER = UUID.randomUUID();

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final NotificationService service = new NotificationServiceImpl(repository,
            Mappers.getMapper(NotificationMapper.class), Clock.fixed(NOW_INSTANT, ZoneId.systemDefault()));

    @Test
    void listWithoutCursorReturnsLatestAndDetectsMore() {
        when(repository.findCaller(CALLER, null, VISIBLE_FROM)).thenReturn(Optional.of(caller(false, null, null)));
        when(repository.findLatest(CALLER, VISIBLE_FROM, 3)).thenReturn(rows(3));

        NotificationHistoryResponse response = service.listMine(CALLER, new NotificationListQuery(false, null, null, 2));

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasMore()).isTrue();
    }

    @Test
    void listUnreadOnlyUsesUnreadStatement() {
        when(repository.findCaller(CALLER, null, VISIBLE_FROM)).thenReturn(Optional.of(caller(false, null, null)));
        when(repository.findLatestUnread(CALLER, VISIBLE_FROM, 51)).thenReturn(rows(1));

        NotificationHistoryResponse response = service.listMine(CALLER, new NotificationListQuery(true, null, null, null));

        assertThat(response.items()).hasSize(1);
        assertThat(response.hasMore()).isFalse();
        verify(repository, never()).findLatest(any(), any(), anyInt());
    }

    @Test
    void listBeforeAndAfterUseAnchorTime() {
        UUID anchor = UUID.randomUUID();
        LocalDateTime anchorAt = NOW.minusDays(1);
        when(repository.findCaller(CALLER, anchor, VISIBLE_FROM)).thenReturn(Optional.of(caller(false, anchorAt, anchor)));
        when(repository.findBefore(CALLER, VISIBLE_FROM, anchorAt, anchor, 11)).thenReturn(rows(0));
        when(repository.findAfterUnread(CALLER, VISIBLE_FROM, anchorAt, anchor, 11)).thenReturn(rows(0));

        service.listMine(CALLER, new NotificationListQuery(false, anchor, null, 10));
        service.listMine(CALLER, new NotificationListQuery(true, null, anchor, 10));

        verify(repository).findBefore(CALLER, VISIBLE_FROM, anchorAt, anchor, 11);
        verify(repository).findAfterUnread(CALLER, VISIBLE_FROM, anchorAt, anchor, 11);
    }

    @Test
    void listRejectsMissingOrDeletedCallerBeforeCursor() {
        UUID anchor = UUID.randomUUID();
        when(repository.findCaller(CALLER, anchor, VISIBLE_FROM)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listMine(CALLER, new NotificationListQuery(false, anchor, null, 10)))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.USER_001);

        when(repository.findCaller(CALLER, anchor, VISIBLE_FROM)).thenReturn(Optional.of(caller(true, null, null)));
        assertThatThrownBy(() -> service.listMine(CALLER, new NotificationListQuery(false, anchor, null, 10)))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.USER_001);
    }

    @Test
    void listRejectsForeignOrExpiredAnchor() {
        UUID anchor = UUID.randomUUID();
        when(repository.findCaller(CALLER, anchor, VISIBLE_FROM)).thenReturn(Optional.of(caller(false, null, null)));

        assertThatThrownBy(() -> service.listMine(CALLER, new NotificationListQuery(false, null, anchor, 10)))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.NOTIFICATION_002);
    }

    @Test
    void countUnread() {
        when(repository.countUnread(CALLER, VISIBLE_FROM)).thenReturn(Optional.of(projection(UnreadCountView.class,
                Map.of("callerDeleted", false, "unreadCount", 3L))));

        assertThat(service.countUnread(CALLER).unreadCount()).isEqualTo(3);
    }

    @Test
    void countUnreadRejectsDeletedCaller() {
        when(repository.countUnread(CALLER, VISIBLE_FROM)).thenReturn(Optional.of(projection(UnreadCountView.class,
                Map.of("callerDeleted", true, "unreadCount", 0L))));

        assertThatThrownBy(() -> service.countUnread(CALLER))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.USER_001);
    }

    @Test
    void markReadOrderOfChecks() {
        UUID id = UUID.randomUUID();
        when(repository.findReadContext(CALLER, id, VISIBLE_FROM)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markRead(CALLER, id))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.USER_001);

        when(repository.findReadContext(CALLER, id, VISIBLE_FROM)).thenReturn(Optional.of(readContext(false, false, false)));
        assertThatThrownBy(() -> service.markRead(CALLER, id))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.NOTIFICATION_001);
        verify(repository, never()).markRead(any(), any());
    }

    @Test
    void markReadAlreadyReadDoesNotWrite() {
        UUID id = UUID.randomUUID();
        when(repository.findReadContext(CALLER, id, VISIBLE_FROM)).thenReturn(Optional.of(readContext(false, true, true)));

        service.markRead(CALLER, id);

        verify(repository).findReadContext(CALLER, id, VISIBLE_FROM);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void markReadUnread() {
        UUID id = UUID.randomUUID();
        when(repository.findReadContext(CALLER, id, VISIBLE_FROM)).thenReturn(Optional.of(readContext(false, true, false)));

        service.markRead(CALLER, id);

        verify(repository).markRead(CALLER, id);
    }

    @Test
    void markAllRead() {
        when(repository.findCaller(CALLER, null, VISIBLE_FROM)).thenReturn(Optional.of(caller(false, null, null)));

        service.markAllRead(CALLER);

        verify(repository).markAllRead(eq(CALLER), eq(VISIBLE_FROM));
    }

    @Test
    void markAllReadRejectsDeletedCaller() {
        when(repository.findCaller(CALLER, null, VISIBLE_FROM)).thenReturn(Optional.of(caller(true, null, null)));

        assertThatThrownBy(() -> service.markAllRead(CALLER))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.USER_001);
        verify(repository, never()).markAllRead(any(), any());
    }

    private NotificationCallerView caller(boolean deleted, LocalDateTime anchorAt, UUID anchorId) {
        Map<String, Object> values = new HashMap<>();
        values.put("callerDeleted", deleted);
        values.put("anchorCreatedAt", anchorAt);
        values.put("anchorId", anchorId);
        return projection(NotificationCallerView.class, values);
    }

    private NotificationReadContextView readContext(boolean deleted, boolean found, boolean isRead) {
        return projection(NotificationReadContextView.class,
                Map.of("callerDeleted", deleted, "found", found, "isRead", isRead));
    }

    private List<NotificationView> rows(int count) {
        return IntStream.range(0, count).mapToObj(i -> {
            Map<String, Object> values = new HashMap<>();
            values.put("id", UUID.randomUUID());
            values.put("type", "WELCOME");
            values.put("title", "t");
            values.put("body", "b");
            values.put("referenceType", "USER");
            values.put("referenceId", CALLER);
            values.put("isRead", false);
            values.put("createdAt", NOW.minusMinutes(i));
            return projection(NotificationView.class, values);
        }).toList();
    }

    private <T> T projection(Class<T> type, Map<String, Object> values) {
        return projections.createProjection(type, values);
    }
}
