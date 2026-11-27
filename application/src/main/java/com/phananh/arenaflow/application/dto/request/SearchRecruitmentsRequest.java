package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.support.mercenary.MercenaryRules;
import com.phananh.arenaflow.application.validation.GeoQuery;
import com.phananh.arenaflow.application.validation.OneOf;
import com.phananh.arenaflow.application.validation.ValidGeoQuery;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Tìm kèo mercenary: có toạ độ → theo bán kính (mặc định 3 km); không → theo tỉnh trong hồ sơ. */
@ValidGeoQuery
public record SearchRecruitmentsRequest(
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lng,
        @DecimalMin("1") @DecimalMax("20") Double radiusKm,
        @OneOf({"Thủ môn", "Hậu vệ", "Tiền vệ", "Tiền đạo"}) String position,
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer pageSize) implements GeoQuery {

    public SearchRecruitmentsRequest {
        position = position == null || position.isBlank() ? null : position.trim();
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        radiusKm = radiusKm == null && lat != null && lng != null ? Double.valueOf(MercenaryRules.DEFAULT_RADIUS_KM)
                : radiusKm;
    }
}
