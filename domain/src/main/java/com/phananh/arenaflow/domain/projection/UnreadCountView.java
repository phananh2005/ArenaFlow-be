package com.phananh.arenaflow.domain.projection;

/** Cờ người gọi đã xoá và số thông báo chưa đọc trong hạn hiển thị. */
public interface UnreadCountView {
    Boolean getCallerDeleted();
    Long getUnreadCount();
}
