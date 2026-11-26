package com.phananh.arenaflow.api.scheduler;

import com.phananh.arenaflow.application.service.TeamVerificationJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Chỉ kích hoạt job đối soát huy hiệu xác minh đội; cron "-" là tắt job. */
@Component
@RequiredArgsConstructor
public class TeamVerificationScheduler {

    private final TeamVerificationJobService teamVerificationJobService;

    /** Chạy đối soát huy hiệu xác minh theo cron cấu hình; lịch theo timezone JVM. */
    @Scheduled(cron = "${arenaflow.reputation.verification.cron}")
    public void reconcileVerificationBadges() {
        teamVerificationJobService.reconcileAll();
    }
}
