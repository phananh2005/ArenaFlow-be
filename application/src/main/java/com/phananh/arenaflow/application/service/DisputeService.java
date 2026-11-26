package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateDisputeRequest;
import com.phananh.arenaflow.application.dto.response.DisputeCreatedResponse;

import java.util.UUID;

public interface DisputeService {

    /**
     * Đội trưởng đội bị report bùng tranh chấp trong hạn. Thứ tự lỗi: REPORT_001 → TEAM_016 → TEAM_015 → TEAM_002 →
     * REPORT_006 → REPORT_008 → REPORT_007. Chuyển report sang DISPUTED bằng câu có điều kiện (hạn kiểm trong câu);
     * 0 dòng → thử lại, lần sau guard trả REPORT_008 / REPORT_007; hết lượt → MATCH_014. 3 truy vấn.
     */
    DisputeCreatedResponse createDispute(UUID reportId, UUID callerId, CreateDisputeRequest request);
}
