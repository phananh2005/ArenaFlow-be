package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.SportResponse;
import com.phananh.arenaflow.domain.entity.match.Sport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SportMapper {

    @Mapping(target = "sportId", source = "id")
    @Mapping(target = "sportName", source = "name")
    @Mapping(target = "isActive", source = "isActive")
    SportResponse toResponse(Sport sport);
}
