package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CancelMatchRequest;
import com.phananh.arenaflow.application.dto.request.CreateMatchRequest;
import com.phananh.arenaflow.application.dto.request.SearchMatchRequest;
import com.phananh.arenaflow.application.dto.request.UpdateCostAgreementRequest;
import com.phananh.arenaflow.application.dto.response.CostAgreementResponse;
import com.phananh.arenaflow.application.dto.response.MatchCancellationResponse;
import com.phananh.arenaflow.application.dto.response.MatchResponse;
import com.phananh.arenaflow.application.dto.response.MatchSummaryResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;

import java.util.UUID;

public interface MatchService {

    /**
     * Tạo kèo: TEAM_001 → TEAM_016 → TEAM_015 → TEAM_002 → SPORT_001 → SPORT_002 → VENUE_001 → VENUE_002 (kèo luồng
     * B: trên mọi lựa chọn địa điểm). Luồng A: 6 truy vấn (gồm đọc lại chi tiết để dựng response). Luồng B: 6 truy vấn
     * (ghi kèo, lựa chọn địa điểm, khung giờ; response dựng từ dữ liệu đã nạp, không đọc lại).
     */
    MatchResponse createMatch(UUID callerId, CreateMatchRequest request);

    /**
     * Kèo đang tìm đối (OPEN chưa quá hạn, đội tạo kèo ACTIVE, bộ môn bật), cả hai luồng; kèo luồng B lọc giờ theo
     * khung giờ đề xuất và đo khoảng cách tới lựa chọn địa điểm gần nhất. 2 truy vấn (trang + đếm).
     */
    PageResponse<MatchSummaryResponse> searchMatches(SearchMatchRequest request);

    /**
     * Chi tiết kèo ở mọi trạng thái (trạng thái hiển thị, không ghi); không có → MATCH_001. Luồng A 2 truy vấn; luồng B
     * + lựa chọn địa điểm / khung giờ (chưa ghép 2, đã ghép 3).
     */
    MatchResponse getMatch(UUID matchId);

    /**
     * Thay toàn bộ thoả thuận chi phí (đội tạo kèo, kèo OPEN luồng A): MATCH_001 → TEAM_016 → TEAM_015 → TEAM_002 →
     * MATCH_016 → MATCH_004 → MATCH_003 → MATCH_005. Luôn UPDATE kèo (CAS version) để tuần tự hoá với chấp nhận;
     * thử lại khi xung đột, hết lượt → MATCH_014. Kèo quá hạn: commit chuyển EXPIRED rồi MATCH_004. ≤ 6 truy vấn.
     */
    CostAgreementResponse updateCostAgreement(UUID matchId, UUID callerId, UpdateCostAgreementRequest request);

    /**
     * Huỷ kèo: MATCH_001 → TEAM_001 → TEAM_016 → TEAM_002 → MATCH_009 → MATCH_004 → MATCH_005 → MATCH_015 (khác
     * {@code expectedStatus}, kiểm ở mọi lần thử) → OPEN: MATCH_010 (chỉ kèo luồng A; kèo luồng B: thương lượng đang
     * trao đổi → SUPERSEDED) · CONFIRMED: MATCH_011 → MATCH_012. Huỷ CONFIRMED trừ điểm đội huỷ theo khung giờ. Thử lại
     * khi xung đột, hết lượt → MATCH_014.
     */
    MatchCancellationResponse cancelMatch(UUID matchId, UUID callerId, CancelMatchRequest request);

    /** Kèo đội đã tạo / đã ghép; đội không tồn tại → TEAM_001. 3 truy vấn. */
    PageResponse<MatchSummaryResponse> listTeamMatches(UUID teamId, MatchStatusFilter status, MatchRoleFilter role,
                                                       int page, int pageSize);
}
