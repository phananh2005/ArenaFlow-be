package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.ResolveDisputeRequest;
import com.phananh.arenaflow.application.dto.response.AdminDisputeDetailResponse;
import com.phananh.arenaflow.application.dto.response.AdminDisputeResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;

import java.util.UUID;

public interface AdminDisputeService {

    /** Hàng chờ PENDING cũ nhất trước; RESOLVED / ALL mới nhất trước. 2 truy vấn. */
    PageResponse<AdminDisputeResponse> listDisputes(DisputeStatusFilter status, int page, int pageSize);

    /** Chi tiết + đánh giá hai đội của trận. DISPUTE_001 khi không có. 2 truy vấn. */
    AdminDisputeDetailResponse getDispute(UUID disputeId);

    /**
     * Quyết định có điều kiện PENDING → RESOLVED (DISPUTE_001 / DISPUTE_002 khi 0 dòng), áp quyết định lên report;
     * bác report → hoàn đúng điểm đã thực trừ + refresh huy hiệu sau commit; ghi audit sau commit; trả chi tiết sau khi
     * quyết định. Thử lại xung đột → MATCH_014. Tối đa 6 truy vấn (bác report, kể cả refresh huy hiệu).
     */
    AdminDisputeDetailResponse resolveDispute(UUID disputeId, UUID adminId, ResolveDisputeRequest request);
}
