package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.service.VenueStatusFilter;
import com.phananh.arenaflow.application.validation.ValidGeoQuery;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@ValidGeoQuery
public record SearchVenueRequest(
        @Size(max = 100) String q,
        VenueStatusFilter status,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 2, fraction = 7) BigDecimal lat,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal lng,
        @DecimalMin("0.1") @DecimalMax("50") BigDecimal radiusKm,
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer pageSize) {

    public SearchVenueRequest {
        q = q == null ? null : q.trim();
        status = status == null ? VenueStatusFilter.ACTIVE : status;
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        radiusKm = radiusKm == null && lat != null && lng != null ? BigDecimal.valueOf(5) : radiusKm;
    }
}
