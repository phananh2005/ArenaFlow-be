package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.response.ProvinceResponse;
import com.phananh.arenaflow.application.dto.response.WardSummaryResponse;

import java.util.List;
import java.util.UUID;

/** Danh mục khu vực chỉ đọc: trả toàn bộ, không phân trang (số tỉnh/thành và phường/xã có giới hạn tự nhiên). */
public interface AdministrativeUnitService {

    List<ProvinceResponse> listProvinces();

    /** {@code provinceId} không phải id tỉnh/thành (không tồn tại hoặc là phường/xã) → USER_004. */
    List<WardSummaryResponse> listWards(UUID provinceId);
}
