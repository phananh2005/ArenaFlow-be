package com.phananh.arenaflow.application.validation;

import java.math.BigDecimal;

/** Request tìm kiếm có toạ độ tuỳ chọn — kiểm bằng {@link ValidGeoQuery}. */
public interface GeoQueryRequest {

    BigDecimal lat();

    BigDecimal lng();

    BigDecimal radiusKm();
}
