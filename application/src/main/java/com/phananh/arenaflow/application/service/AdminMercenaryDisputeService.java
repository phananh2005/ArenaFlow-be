package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.ResolveDisputeRequest;
import com.phananh.arenaflow.application.dto.response.AdminMercenaryDisputeDetailResponse;
import com.phananh.arenaflow.application.dto.response.AdminMercenaryDisputeResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;

import java.util.UUID;

/** Admin xử lý tranh chấp report bùng giữa đội tuyển và mercenary (hàng chờ riêng). */
public interface AdminMercenaryDisputeService {

    PageResponse<AdminMercenaryDisputeResponse> listDisputes(DisputeStatusFilter status, int page, int pageSize);

    AdminMercenaryDisputeDetailResponse getDispute(UUID disputeId);

    AdminMercenaryDisputeDetailResponse resolveDispute(UUID disputeId, UUID adminId, ResolveDisputeRequest request);
}
