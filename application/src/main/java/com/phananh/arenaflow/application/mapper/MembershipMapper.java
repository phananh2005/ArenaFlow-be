package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.InvitationResponse;
import com.phananh.arenaflow.application.dto.response.MemberHistoryResponse;
import com.phananh.arenaflow.application.dto.response.MemberResponse;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.projection.MemberHistoryView;
import com.phananh.arenaflow.domain.projection.MemberView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = MapperSupport.class, imports = MapperSupport.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MembershipMapper {

    @Mapping(target = "invitationId", source = "member.id")
    @Mapping(target = "teamId", source = "member.teamId")
    @Mapping(target = "userId", source = "member.userId")
    @Mapping(target = "status", source = "member.status")
    @Mapping(target = "invitedAt", source = "member.invitedAt")
    @Mapping(target = "teamName", source = "teamName")
    InvitationResponse toInvitation(TeamMember member, String teamName);

    /** Thành viên đã xoá tài khoản: tên → "Người dùng đã xóa", avatar → null; userId giữ nguyên. */
    @Mapping(target = "displayName",
            expression = "java(MapperSupport.maskName(view.getDisplayName(), view.getUserDeleted()))")
    @Mapping(target = "avatarUrl",
            expression = "java(MapperSupport.maskAvatar(view.getAvatarUrl(), view.getUserDeleted()))")
    MemberResponse toMember(MemberView view);

    /** Admin nhận tên và avatar thật kể cả khi user đã xoá tài khoản, kèm cờ xoá. */
    @Mapping(target = "isUserDeleted", source = "userDeleted")
    MemberHistoryResponse toMemberHistory(MemberHistoryView view);
}
