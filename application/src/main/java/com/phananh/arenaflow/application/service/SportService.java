package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.UpdateSportRequest;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.SportResponse;

import java.util.UUID;

public interface SportService {

    /** Bộ môn đang bật, sắp theo tên rồi id; 2 truy vấn (trang + đếm). */
    PageResponse<SportResponse> listActiveSports(int page, int pageSize);

    /** Admin: mọi bộ môn, {@code isActive} null = không lọc. */
    PageResponse<SportResponse> listSports(Boolean isActive, int page, int pageSize);

    /**
     * Admin bật/tắt: không có → SPORT_001; cùng giá trị → không ghi, không audit; khác → dirty checking + audit sau
     * commit. Không version: hai admin cùng bật/tắt thì ghi sau thắng, kết quả vẫn là một giá trị hợp lệ.
     */
    SportResponse updateSport(UUID sportId, UUID adminId, UpdateSportRequest request);
}
