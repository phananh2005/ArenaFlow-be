package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.MarkInactiveRequest;
import com.phananh.arenaflow.application.dto.response.AdminTeamResponse;
import com.phananh.arenaflow.application.dto.response.AdminTeamSummaryResponse;
import com.phananh.arenaflow.application.dto.response.MemberHistoryResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;

import java.util.UUID;

/** Quyền ADMIN chặn ở SecurityConfig (`/api/v1/admin/**`). Response Admin không che user đã xoá tài khoản, kèm cờ. */
public interface AdminTeamService {

    /**
     * Chuyển đội ACTIVE → INACTIVE, lưu lý do đã trim. Thứ tự lỗi: TEAM_001 → TEAM_016 (đã giải tán) → TEAM_011
     * (đã INACTIVE). Chạy qua retrier: xung đột version hoặc deadlock hết lượt thử → 409 TEAM_014.
     */
    AdminTeamResponse markInactive(UUID teamId, MarkInactiveRequest request);

    /**
     * Mở lại đội INACTIVE và xoá inactiveReason. Thứ tự lỗi: TEAM_001 → TEAM_016 → TEAM_012 (đang ACTIVE) → TEAM_013
     * (không còn captain ACTIVE; nếu đội vừa bị giải tán song song thì trả TEAM_016). Retrier → TEAM_014.
     */
    AdminTeamResponse reactivate(UUID teamId);

    /** Danh sách đội kèm inactiveReason; {@code status} ALL = mọi trạng thái. Sort created_at DESC, id DESC. */
    PageResponse<AdminTeamSummaryResponse> listTeams(TeamStatusFilter status, int page, int pageSize);

    /**
     * Lịch sử thành viên (ACTIVE/LEFT) của đội ở mọi trạng thái, kể cả DISBANDED; không gồm lời mời PENDING/REJECTED.
     * Đội không tồn tại → TEAM_001. Sort cố định joined_at, id.
     */
    PageResponse<MemberHistoryResponse> listMemberHistory(UUID teamId, MemberHistoryStatusFilter status,
                                                          int page, int pageSize);
}
