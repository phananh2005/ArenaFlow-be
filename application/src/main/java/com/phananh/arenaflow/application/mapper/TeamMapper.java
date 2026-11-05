package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.AdminTeamSummaryResponse;
import com.phananh.arenaflow.application.dto.response.CaptainHistoryResponse;
import com.phananh.arenaflow.application.dto.response.TeamResponse;
import com.phananh.arenaflow.application.dto.response.TeamSummaryResponse;
import com.phananh.arenaflow.domain.projection.CaptainHistoryView;
import com.phananh.arenaflow.domain.projection.TeamDetailView;
import com.phananh.arenaflow.domain.projection.TeamSummaryView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = MapperSupport.class, imports = MapperSupport.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TeamMapper {

    @Mapping(target = "captainName",
            expression = "java(MapperSupport.maskName(view.getCaptainName(), view.getCaptainDeleted()))")
    @Mapping(target = "disbandedByName",
            expression = "java(MapperSupport.maskName(view.getDisbandedByName(), view.getDisbandedByDeleted()))")
    TeamResponse toResponse(TeamDetailView view);

    @Mapping(target = "captainName",
            expression = "java(MapperSupport.maskName(view.getCaptainName(), view.getCaptainDeleted()))")
    @Mapping(target = "disbandedByName",
            expression = "java(MapperSupport.maskName(view.getDisbandedByName(), view.getDisbandedByDeleted()))")
    TeamSummaryResponse toSummary(TeamSummaryView view);

    @Mapping(target = "captainName",
            expression = "java(MapperSupport.maskName(view.getCaptainName(), view.getCaptainDeleted()))")
    @Mapping(target = "disbandedByName",
            expression = "java(MapperSupport.maskName(view.getDisbandedByName(), view.getDisbandedByDeleted()))")
    AdminTeamSummaryResponse toAdminSummary(TeamSummaryView view);

    @Mapping(target = "fromUserName",
            expression = "java(MapperSupport.maskName(view.getFromUserName(), view.getFromUserDeleted()))")
    @Mapping(target = "toUserName",
            expression = "java(MapperSupport.maskName(view.getToUserName(), view.getToUserDeleted()))")
    CaptainHistoryResponse toResponse(CaptainHistoryView view);
}
