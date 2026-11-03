package com.phananh.ArenaFlow.repository.projection;

import java.util.UUID;

/** Đội mà user đang là captain ACTIVE + số thành viên ACTIVE (dùng khi xoá tài khoản). */
public interface CaptainTeamView {
    UUID getTeamId();
    Long getActiveCount();
}
