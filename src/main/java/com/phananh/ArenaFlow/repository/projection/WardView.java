package com.phananh.ArenaFlow.repository.projection;

import java.util.UUID;

/** Phường/xã kèm tỉnh/thành cha. */
public interface WardView {
    UUID getWardId();
    String getWardName();
    UUID getProvinceId();
    String getProvinceName();
}
