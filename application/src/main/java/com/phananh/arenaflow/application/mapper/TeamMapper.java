package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.AdminTeamResponse;
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

    /**
     * Che tên captain và người giải tán nếu họ đã xoá tài khoản (id giữ nguyên). Đội không còn captain ACTIVE
     * (vd DISBANDED) → captainId/captainName null.
     */
    @Mapping(target = "captainName",
            expression = "java(MapperSupport.maskName(view.getCaptainName(), view.getCaptainDeleted()))")
    @Mapping(target = "disbandedByName",
            expression = "java(MapperSupport.maskName(view.getDisbandedByName(), view.getDisbandedByDeleted()))")
    TeamResponse toResponse(TeamDetailView view);

    /** Như {@link #toResponse(TeamDetailView)}: che tên captain/người giải tán đã xoá tài khoản. */
    @Mapping(target = "captainName",
            expression = "java(MapperSupport.maskName(view.getCaptainName(), view.getCaptainDeleted()))")
    @Mapping(target = "disbandedByName",
            expression = "java(MapperSupport.maskName(view.getDisbandedByName(), view.getDisbandedByDeleted()))")
    TeamSummaryResponse toSummary(TeamSummaryView view);

    /** Danh sách đội của Admin: kèm inactiveReason; không che tên user đã xoá, trả cờ đã xoá. */
    @Mapping(target = "isCaptainDeleted", source = "captainDeleted")
    @Mapping(target = "isDisbandedByDeleted", source = "disbandedByDeleted")
    AdminTeamSummaryResponse toAdminSummary(TeamSummaryView view);

    /** Chi tiết đội của Admin: như {@link #toResponse(TeamDetailView)} nhưng không che tên, trả cờ đã xoá. */
    @Mapping(target = "isCaptainDeleted", source = "captainDeleted")
    @Mapping(target = "isDisbandedByDeleted", source = "disbandedByDeleted")
    AdminTeamResponse toAdminResponse(TeamDetailView view);

    /** Che tên người chuyển/nhận quyền đã xoá tài khoản; fromUserId null = lần tạo đội. */
    @Mapping(target = "fromUserName",
            expression = "java(MapperSupport.maskName(view.getFromUserName(), view.getFromUserDeleted()))")
    @Mapping(target = "toUserName",
            expression = "java(MapperSupport.maskName(view.getToUserName(), view.getToUserDeleted()))")
    CaptainHistoryResponse toResponse(CaptainHistoryView view);
}
