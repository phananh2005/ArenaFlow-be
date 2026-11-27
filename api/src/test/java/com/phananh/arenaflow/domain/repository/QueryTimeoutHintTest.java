package com.phananh.arenaflow.domain.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Query hint timeout 2 giây trên native query được driver áp thật trên kết nối DB dev: câu bị huỷ sau khoảng 2 giây
 * và Spring dịch thành {@link QueryTimeoutException} (handler chung trả 500 SYS_005). Không đạt → không dùng được
 * giới hạn thời gian cho câu tìm theo tên, phải chọn phương án dự phòng trước khi merge.
 */
class QueryTimeoutHintTest extends RepositoryIntegrationTest {

    @Autowired
    private QueryTimeoutProbeRepository probeRepository;

    @Test
    void nativeQueryWithTimeoutHintIsCancelledAfterAboutTwoSeconds() {
        long start = System.nanoTime();

        assertThatThrownBy(() -> probeRepository.sleepThreeSeconds()).isInstanceOf(QueryTimeoutException.class);

        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(1_500)).isLessThan(Duration.ofMillis(2_900));
    }
}
