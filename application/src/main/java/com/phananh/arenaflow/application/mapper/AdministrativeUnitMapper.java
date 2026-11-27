package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.ProvinceResponse;
import com.phananh.arenaflow.application.dto.response.WardSummaryResponse;
import com.phananh.arenaflow.domain.projection.ProvinceView;
import com.phananh.arenaflow.domain.projection.ProvinceWardView;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AdministrativeUnitMapper {

    ProvinceResponse toProvince(ProvinceView view);

    /** Chỉ gọi cho dòng có phường/xã ({@code wardId} khác null). */
    WardSummaryResponse toWardSummary(ProvinceWardView view);
}
