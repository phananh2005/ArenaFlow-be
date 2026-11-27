package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Một tỉnh/thành của danh mục khu vực. */
public interface ProvinceView {
    UUID getProvinceId();
    String getProvinceName();
}
