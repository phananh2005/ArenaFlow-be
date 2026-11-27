package com.phananh.arenaflow.application.support.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Chưa tích hợp push thiết bị: không gửi gì, chỉ log loại + người nhận (không log nội dung). */
@Slf4j
@Component
public class NoOpPushSender implements PushSender {

    @Override
    public void send(UUID recipientUserId, PushMessage message) {
        log.debug("Push skipped: type={}, recipient={}", message.type(), recipientUserId);
    }
}
