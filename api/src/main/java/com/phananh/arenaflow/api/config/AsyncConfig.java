package com.phananh.arenaflow.api.config;

import com.phananh.arenaflow.application.support.notification.NotificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executor riêng cho listener thông báo: giới hạn số luồng (giới hạn số kết nối DB thông báo chiếm), hàng đợi có
 * chặn trên; đầy thì chạy trên luồng phát event (chậm thay vì mất thông báo). Khi tắt máy chờ hàng đợi tối đa 30 giây.
 * Chỉ method mang {@code @Async("notificationExecutor")} chạy bất đồng bộ.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean
    public ThreadPoolTaskExecutor notificationExecutor(NotificationProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.executor().coreSize());
        executor.setMaxPoolSize(properties.executor().maxSize());
        executor.setQueueCapacity(properties.executor().queueCapacity());
        executor.setThreadNamePrefix("notification-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /** Listener tự bắt lỗi; đây là lưới cuối — chỉ log tên method, không log tham số (có thể chứa dữ liệu người dùng). */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (exception, method, params) -> log.error("Async task failed: method={}", method.getName(), exception);
    }
}
