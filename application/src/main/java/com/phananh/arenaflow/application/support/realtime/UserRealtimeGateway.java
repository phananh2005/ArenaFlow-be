package com.phananh.arenaflow.application.support.realtime;

import java.util.Collection;

/** Kênh realtime theo người dùng; không phụ thuộc spring-websocket. */
public interface UserRealtimeGateway {

    /**
     * Phải gọi trong transaction đang chạy: đăng ký một lần đẩy sau commit cho cả lô; transaction rollback thì không
     * đẩy. Lỗi gửi sau commit không ném ra ngoài.
     */
    void publishAfterCommit(Collection<UserFrameDelivery> deliveries);
}
