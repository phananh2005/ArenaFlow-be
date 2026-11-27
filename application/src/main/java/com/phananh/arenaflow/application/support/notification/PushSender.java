package com.phananh.arenaflow.application.support.notification;

import java.util.UUID;

/** Đẩy thông báo ra thiết bị; gọi sau commit, ngoài transaction. */
public interface PushSender {

    void send(UUID recipientUserId, PushMessage message);
}
