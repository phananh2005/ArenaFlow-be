package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.GeoQueryRequest;
import com.phananh.arenaflow.application.validation.OneOf;
import com.phananh.arenaflow.application.validation.ValidGeoQuery;
import com.phananh.arenaflow.application.validation.ValidMatchSearch;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Bộ lọc tìm kèo; mặc định gán trước Bean Validation ({@code radiusKm} = 10 chỉ khi có đủ toạ độ). */
@ValidGeoQuery
@ValidMatchSearch
public record SearchMatchRequest(
        @OneOf({"HAS_VENUE", "NEED_VENUE"}) String type,
        UUID sportId,
        @OneOf({"FIVE_A_SIDE", "SEVEN_A_SIDE", "ELEVEN_A_SIDE"}) String format,
        String skillLevel,
        LocalDateTime startsAtFrom,
        LocalDateTime startsAtTo,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 2, fraction = 7) BigDecimal lat,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal lng,
        @DecimalMin("0.1") @DecimalMax("50") BigDecimal radiusKm,
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer pageSize) implements GeoQueryRequest {

    private static final BigDecimal DEFAULT_RADIUS_KM = BigDecimal.TEN;

    public SearchMatchRequest {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        radiusKm = radiusKm == null && lat != null && lng != null ? DEFAULT_RADIUS_KM : radiusKm;
    }
}
