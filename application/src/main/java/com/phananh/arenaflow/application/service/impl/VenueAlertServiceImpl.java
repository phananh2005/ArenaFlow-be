package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.event.venue.VenueAdminAlertEvent;
import com.phananh.arenaflow.application.event.venue.VenueAlertType;
import com.phananh.arenaflow.application.service.VenueAlertService;
import com.phananh.arenaflow.application.support.venue.VenueAlertProperties;
import com.phananh.arenaflow.domain.projection.StaleVenueView;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cảnh báo Admin về địa điểm lâu không được chọn (quá số ngày cấu hình tính từ "bây giờ" của bean {@code Clock}),
 * xử lý theo batch.
 */
@Slf4j
@Service
public class VenueAlertServiceImpl implements VenueAlertService {

    private final VenueRepository venueRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final VenueAlertProperties properties;
    private final Clock clock;

    public VenueAlertServiceImpl(VenueRepository venueRepository, ApplicationEventPublisher eventPublisher,
                                 TransactionTemplate transactionTemplate, VenueAlertProperties properties, Clock clock) {
        this.venueRepository = venueRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Mỗi batch một transaction: claim bằng UPDATE … RETURNING, phát một event cho mỗi địa điểm đã claim, commit.
     * Batch lỗi thì rollback và kết thúc lượt chạy; điều kiện chặn stale_alerted_at giúp lượt sau chạy tiếp an toàn.
     */
    @Override
    public int alertStaleVenues() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime threshold = now.minusDays(properties.staleDays());
        int alerted = 0;
        for (int batch = 0; batch < properties.maxBatchesPerRun(); batch++) {
            List<StaleVenueView> claimed;
            try {
                claimed = transactionTemplate.execute(status -> {
                    List<StaleVenueView> venues = venueRepository.markStaleAlerted(threshold, now, properties.batchSize());
                    venues.forEach(venue -> eventPublisher.publishEvent(new VenueAdminAlertEvent(venue.getId(),
                            venue.getName(), VenueAlertType.STALE_180_DAYS, now)));
                    return venues;
                });
            } catch (RuntimeException exception) {
                log.error("Stale venue alert batch {} failed; stopping this run", batch + 1, exception);
                break;
            }
            alerted += claimed.size();
            // Batch thiếu chưa chắc đã hết dòng: dòng bị sửa đồng thời bị bỏ qua; chỉ dừng khi batch rỗng.
            if (claimed.isEmpty()) {
                break;
            }
        }
        log.info("Stale venue alert run finished: {} venues alerted", alerted);
        return alerted;
    }
}
