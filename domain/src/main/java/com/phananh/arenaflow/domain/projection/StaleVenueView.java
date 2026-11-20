package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Địa điểm được job cảnh báo 180 ngày không được chọn claim. */
public interface StaleVenueView {
    UUID getId();
    String getName();
}
