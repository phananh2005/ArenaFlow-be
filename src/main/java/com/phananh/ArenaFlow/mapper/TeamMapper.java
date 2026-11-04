package com.phananh.ArenaFlow.mapper;

import com.phananh.ArenaFlow.dto.response.AdminTeamSummaryResponse;
import com.phananh.ArenaFlow.dto.response.CaptainHistoryResponse;
import com.phananh.ArenaFlow.dto.response.TeamResponse;
import com.phananh.ArenaFlow.dto.response.TeamSummaryResponse;
import com.phananh.ArenaFlow.repository.projection.CaptainHistoryView;
import com.phananh.ArenaFlow.repository.projection.TeamDetailView;
import com.phananh.ArenaFlow.repository.projection.TeamSummaryView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = MapperSupport.class, imports = MapperSupport.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TeamMapper {

    @Mapping(target = "captainName",
            expression = "java(MapperSupport.maskName(view.getCaptainName(), view.getCaptainDeleted()))")
    TeamResponse toResponse(TeamDetailView view);

    @Mapping(target = "captainName",
            expression = "java(MapperSupport.maskName(view.getCaptainName(), view.getCaptainDeleted()))")
    TeamSummaryResponse toSummary(TeamSummaryView view);

    @Mapping(target = "captainName",
            expression = "java(MapperSupport.maskName(view.getCaptainName(), view.getCaptainDeleted()))")
    AdminTeamSummaryResponse toAdminSummary(TeamSummaryView view);

    @Mapping(target = "fromUserName",
            expression = "java(MapperSupport.maskName(view.getFromUserName(), view.getFromUserDeleted()))")
    @Mapping(target = "toUserName",
            expression = "java(MapperSupport.maskName(view.getToUserName(), view.getToUserDeleted()))")
    CaptainHistoryResponse toResponse(CaptainHistoryView view);
}
