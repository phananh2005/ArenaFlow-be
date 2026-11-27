package com.phananh.arenaflow.api.scheduler;

import com.phananh.arenaflow.application.service.MatchLifecycleJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Chỉ kích hoạt job; cron "-" là tắt. Tác vụ này lỗi thì tác vụ kia vẫn chạy. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchLifecycleScheduler {

    private final MatchLifecycleJobService matchLifecycleJobService;

    @Scheduled(cron = "${arenaflow.match.lifecycle.cron}")
    public void runLifecycle() {
        try {
            matchLifecycleJobService.expireDueMatches();
        } catch (RuntimeException exception) {
            log.error("Match expiry task failed", exception);
        }
        try {
            matchLifecycleJobService.archiveFinishedMatches();
        } catch (RuntimeException exception) {
            log.error("Match archive task failed", exception);
        }
    }
}
