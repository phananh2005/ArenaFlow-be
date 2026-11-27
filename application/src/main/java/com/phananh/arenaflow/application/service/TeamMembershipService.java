package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.InviteMemberRequest;
import com.phananh.arenaflow.application.dto.response.InvitationCandidatesResponse;
import com.phananh.arenaflow.application.dto.response.InvitationResponse;
import com.phananh.arenaflow.application.dto.response.MemberResponse;
import com.phananh.arenaflow.application.dto.response.MyInvitationResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;

import java.util.UUID;

public interface TeamMembershipService {

    InvitationResponse inviteMember(UUID teamId, UUID captainUserId, InviteMemberRequest request);

    MemberResponse acceptInvitation(UUID invitationId, UUID userId);

    void rejectInvitation(UUID invitationId, UUID userId);

    PageResponse<MemberResponse> listMembers(UUID teamId, int page, int pageSize);

    void leaveTeam(UUID teamId, UUID userId);

    /**
     * Đội trưởng tìm người để mời theo số điện thoại hoặc tên (tối đa 20 gợi ý + hasMore). Giới hạn số lần gọi đã
     * kiểm ở tầng HTTP trước khi gọi method này.
     */
    InvitationCandidatesResponse searchInvitationCandidates(UUID teamId, UUID callerId, String q);

    /** Lời mời đang chờ gửi tới chính người gọi; người gọi đã xoá tài khoản → USER_001. */
    PageResponse<MyInvitationResponse> listMyInvitations(UUID userId, int page, int pageSize);
}
