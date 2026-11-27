package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Tỉnh/thành LEFT JOIN phường/xã: {@code wardId} null = tỉnh/thành chưa có phường/xã nào. */
public interface ProvinceWardView {
    UUID getProvinceId();
    UUID getWardId();
    String getWardName();
}
