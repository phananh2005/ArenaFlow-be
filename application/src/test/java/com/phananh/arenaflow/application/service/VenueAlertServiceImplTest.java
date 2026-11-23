package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.event.venue.VenueAdminAlertEvent;
import com.phananh.arenaflow.application.event.venue.VenueAlertType;
import com.phananh.arenaflow.application.service.impl.VenueAlertServiceImpl;
import com.phananh.arenaflow.application.support.venue.VenueAlertProperties;
import com.phananh.arenaflow.domain.projection.StaleVenueView;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cảnh báo địa điểm lâu không được chọn: claim theo batch, ngưỡng 180 ngày theo giờ hiện tại, dừng khi lỗi. */
class VenueAlertServiceImplTest {

    /** 20:00 UTC ngày 19 = 03:00 giờ Việt Nam ngày 20: lệch ngày nếu tính theo UTC. */
    private static final Instant NOW = Instant.parse("2026-11-19T20:00:00Z");
    private static final LocalDateTime NOW_VN = LocalDateTime.of(2026, 11, 20, 3, 0);

    private final VenueRepository venueRepository = mock(VenueRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private VenueAlertService service;

    @BeforeEach
    void setUp() {
        service = new VenueAlertServiceImpl(venueRepository, eventPublisher, transactionTemplate,
                new VenueAlertProperties(180, 2, 3), Clock.fixed(NOW, ZoneId.systemDefault()));
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
    }

    @Test
    void keepsClaimingAfterShortBatchUntilEmptyBatchAndPublishesOneStaleAlertPerVenue() {
        LocalDateTime now = NOW_VN;
        // Batch thiếu có thể do dòng bị bỏ qua vì ghi đồng thời, không có nghĩa là đã hết dòng.
        when(venueRepository.markStaleAlerted(eq(now.minusDays(180)), eq(now), eq(2)))
                .thenReturn(views(1), views(2), views(0));

        int alerted = service.alertStaleVenues();

        assertThat(alerted).isEqualTo(3);
        verify(transactionTemplate, times(3)).execute(any(TransactionCallback.class));
        ArgumentCaptor<VenueAdminAlertEvent> events = ArgumentCaptor.forClass(VenueAdminAlertEvent.class);
        verify(eventPublisher, times(3)).publishEvent(events.capture());
        assertThat(events.getAllValues()).allSatisfy(event -> {
            assertThat(event.alertType()).isEqualTo(VenueAlertType.STALE_180_DAYS);
            assertThat(event.triggeredAt()).isEqualTo(now);
            assertThat(event.venueName()).startsWith("Stale ");
        });
    }

    @Test
    void staleThresholdIs180DaysBeforeVietnamLocalNow() {
        when(venueRepository.markStaleAlerted(any(), any(), eq(2))).thenReturn(views(0));

        service.alertStaleVenues();

        verify(venueRepository).markStaleAlerted(LocalDateTime.of(2026, 5, 24, 3, 0), NOW_VN, 2);
    }

    @Test
    void stopsAtMaxBatchesPerRun() {
        when(venueRepository.markStaleAlerted(any(), any(), eq(2))).thenReturn(views(2));

        assertThat(service.alertStaleVenues()).isEqualTo(6);
        verify(venueRepository, times(3)).markStaleAlerted(any(), any(), eq(2));
    }

    @Test
    void stopsOnBatchFailureKeepingEarlierCommittedBatches() {
        when(venueRepository.markStaleAlerted(any(), any(), eq(2)))
                .thenReturn(views(2))
                .thenThrow(new QueryTimeoutException("timeout"));

        assertThat(service.alertStaleVenues()).isEqualTo(2);
        verify(venueRepository, times(2)).markStaleAlerted(any(), any(), eq(2));
    }

    private List<StaleVenueView> views(int count) {
        return IntStream.range(0, count).mapToObj(i -> projections.createProjection(StaleVenueView.class,
                Map.of("id", UUID.randomUUID(), "name", "Stale " + i))).toList();
    }
}
