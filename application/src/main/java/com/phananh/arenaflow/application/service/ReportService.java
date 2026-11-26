package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateChatReportRequest;
import com.phananh.arenaflow.application.dto.request.CreateReportRequest;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.ReportResponse;

import java.util.UUID;

public interface ReportService {

    /**
     * Đội của người gọi report đội kia sau trận. Thứ tự lỗi: MATCH_001 → TEAM_002 → TEAM_015 → REPORT_005 →
     * REPORT_002 → REPORT_003 → REPORT_004. Trừ điểm đội bị report trong cùng transaction; thử lại xung đột →
     * MATCH_014. 5 truy vấn kể cả refresh huy hiệu.
     */
    ReportResponse createReport(UUID matchId, UUID callerId, CreateReportRequest request);

    /**
     * Report "giao tiếp tệ" qua chat hai đội của một kèo, mọi trạng thái kèo. Thứ tự lỗi: CHAT_002 → USER_001 →
     * CHAT_003 → REPORT_011 → TEAM_016 → TEAM_015 → REPORT_005 → REPORT_010 → REPORT_004. Cùng đường ghi với report sau
     * trận; mỗi chiều tối đa một report giao tiếp tệ mỗi kèo. 5 truy vấn kể cả refresh huy hiệu.
     */
    ReportResponse createChatReport(UUID conversationId, UUID callerId, CreateChatReportRequest request);

    /** Report đội nhận / đã gửi; quyền đội trưởng kiểm trước (TEAM_001 → TEAM_016 → TEAM_015 → TEAM_002). 3 truy vấn. */
    PageResponse<ReportResponse> listTeamReports(UUID teamId, UUID callerId, ReportDirection direction, int page,
                                                 int pageSize);
}
