package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.InvitationCandidateResponse;
import com.phananh.arenaflow.application.dto.response.InvitationResponse;
import com.phananh.arenaflow.application.dto.response.MemberHistoryResponse;
import com.phananh.arenaflow.application.dto.response.MemberResponse;
import com.phananh.arenaflow.application.dto.response.MyInvitationResponse;
import com.phananh.arenaflow.application.dto.response.MyInvitationTeamResponse;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.projection.InvitationCandidateView;
import com.phananh.arenaflow.domain.projection.MemberHistoryView;
import com.phananh.arenaflow.domain.projection.MemberView;
import com.phananh.arenaflow.domain.projection.MyInvitationView;
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

    /** Người có thể mời: không che tên vì người đã xoá tài khoản không có trong kết quả tìm. */
    InvitationCandidateResponse toCandidate(InvitationCandidateView view);

    /** Lời mời của tôi: đội lồng trong {@code team}; thời điểm mời gán thẳng (giờ Việt Nam). */
    @Mapping(target = "team", expression = "java(toMyInvitationTeam(view))")
    MyInvitationResponse toMyInvitation(MyInvitationView view);

    /** Che tên đội trưởng hiện tại nếu đã xoá tài khoản; đội không còn đội trưởng ACTIVE → captainName null. */
    @Mapping(target = "captainName",
            expression = "java(MapperSupport.maskName(view.getCaptainName(), view.getCaptainDeleted()))")
    MyInvitationTeamResponse toMyInvitationTeam(MyInvitationView view);
}
