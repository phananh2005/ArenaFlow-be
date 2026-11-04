package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Tài khoản OAuth + user để đăng nhập: cần userId, role và trạng thái đã xoá (AUTH_003). */
public interface UserAuthView {
    UUID getUserId();
    String getRole();
    Boolean getDeleted();
}
