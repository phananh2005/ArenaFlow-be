package com.phananh.arenaflow.application.support.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;

/**
 * Bản tạm khi hạ tầng STOMP chưa có: giữ hợp đồng "phải có transaction đang chạy" nhưng không đẩy frame nào.
 * Bị thay bằng adapter STOMP khi kênh realtime có trên nhánh chính.
 */
@Slf4j
@Component
public class NoOpUserRealtimeGateway implements UserRealtimeGateway {

    @Override
    public void publishAfterCommit(Collection<UserFrameDelivery> deliveries) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("UserRealtimeGateway requires an active transaction");
        }
        log.debug("Realtime channel not configured; {} user frames skipped", deliveries.size());
    }
}
