package com.phananh.arenaflow.domain.projection;

/** Ngữ cảnh đánh dấu đã đọc một thông báo của người gọi. */
public interface NotificationReadContextView {
    Boolean getCallerDeleted();
    Boolean getFound();
    Boolean getIsRead();
}
