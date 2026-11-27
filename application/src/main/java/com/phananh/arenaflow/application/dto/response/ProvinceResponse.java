package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

/** Một tỉnh/thành trong danh mục khu vực. */
public record ProvinceResponse(UUID provinceId, String provinceName) {
}
