package com.phananh.arenaflow.domain.projection;

import java.math.BigDecimal;
import java.util.UUID;

/** Dữ liệu hiển thị địa điểm cho luồng kèo/trận ngoài hệ thống, gồm ảnh chính và tên phường/tỉnh. */
public interface SelectableVenueView {
    UUID getId();
    String getName();
    String getAddress();
    BigDecimal getLatitude();
    BigDecimal getLongitude();
    String getStatus();
    String getPrimaryImageUrl();
    String getWardName();
    String getProvinceName();
}
