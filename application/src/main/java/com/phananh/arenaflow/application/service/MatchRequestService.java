package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.RejectMatchRequestRequest;
import com.phananh.arenaflow.application.dto.request.SendMatchRequestRequest;
import com.phananh.arenaflow.application.dto.request.WithdrawMatchRequestRequest;
import com.phananh.arenaflow.application.dto.response.AcceptMatchRequestResponse;
import com.phananh.arenaflow.application.dto.response.MatchRequestResponse;
import com.phananh.arenaflow.application.dto.response.MatchRequestWithdrawalResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;

import java.util.UUID;

/**
 * Yêu cầu ghép kèo luồng A. Thao tác ghi chạy qua retrier (mỗi lần thử một transaction, hết lượt → MATCH_014); gặp
 * kèo OPEN quá hạn → commit chuyển EXPIRED rồi MATCH_004.
 */
public interface MatchRequestService {

    /**
     * MATCH_001 → TEAM_001 → TEAM_016 → TEAM_015 → TEAM_002 → MATCH_007 → MATCH_016 → MATCH_004 → MATCH_003 →
     * MATCH_005 → MATCH_013 → SPORT_002 → MATCH_006. Mở cuộc trò chuyện với đội tạo kèo ở lần gửi đầu; gửi lại dùng
     * tiếp cuộc trò chuyện cũ. Tăng version kèo lúc commit để tuần tự hoá với chấp nhận / huỷ. ≤ 6 truy vấn.
     */
    MatchRequestResponse sendRequest(UUID matchId, UUID callerId, SendMatchRequestRequest request);

    /** Đội tạo kèo xem yêu cầu: MATCH_001 → TEAM_016 / TEAM_002 → MATCH_016; đội INACTIVE vẫn xem. 3 truy vấn. */
    PageResponse<MatchRequestResponse> listMatchRequests(UUID matchId, UUID callerId, MatchRequestStatusFilter status,
                                                         int page, int pageSize);

    /**
     * MATCH_002 → TEAM_016 → TEAM_015 → TEAM_002 → MATCH_004 → MATCH_003 → MATCH_005 → MATCH_008 → MATCH_013 →
     * SPORT_002. Kèo → CONFIRMED (CAS), yêu cầu → ACCEPTED, yêu cầu PENDING khác → SUPERSEDED. 6 truy vấn.
     */
    AcceptMatchRequestResponse acceptRequest(UUID requestId, UUID callerId);

    /** MATCH_002 → TEAM_016 → TEAM_002 → MATCH_004 (cả kèo đã EXPIRED) → MATCH_008. Không chạm kèo. */
    void rejectRequest(UUID requestId, UUID callerId, RejectMatchRequestRequest request);

    /**
     * MATCH_002 → TEAM_016 → TEAM_002 → MATCH_004 → MATCH_008 → MATCH_015 (khác {@code expectedStatus}) → PENDING:
     * rút (đổi trạng thái giữa chừng → MATCH_015) · ACCEPTED: MATCH_005 → MATCH_011 → MATCH_012 → huỷ kèo, trừ điểm
     * đội gửi.
     */
    MatchRequestWithdrawalResponse withdrawRequest(UUID requestId, UUID callerId, WithdrawMatchRequestRequest request);

    /** Yêu cầu đội đã gửi: TEAM_001 → TEAM_016 → TEAM_002 (đội INACTIVE vẫn xem). 3 truy vấn. */
    PageResponse<MatchRequestResponse> listTeamRequests(UUID teamId, UUID callerId, MatchRequestStatusFilter status,
                                                        int page, int pageSize);
}
