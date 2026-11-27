package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Phường / tỉnh trong hồ sơ người dùng + cờ đã xoá. */
public interface ProfileAreaView {
    UUID getWardId();
    UUID getProvinceId();
    Boolean getDeleted();
}
