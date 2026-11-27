package com.phananh.arenaflow.application.validation;

/** Request tìm kiếm có toạ độ tuỳ chọn — đích của {@link ValidGeoQuery}. */
public interface GeoQuery {

    Number lat();

    Number lng();

    Number radiusKm();
}
