package com.phananh.arenaflow.domain.projection;

/** Tổng số cuộc trò chuyện của người gọi + cờ người gọi đã xoá tài khoản, đọc trong cùng câu đếm. */
public interface MineCountView {
    Long getTotal();
    Boolean getCallerDeleted();
}
