package com.phananh.arenaflow.api.area;

import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.ProvinceResponse;
import com.phananh.arenaflow.application.dto.response.WardSummaryResponse;
import com.phananh.arenaflow.application.service.AdministrativeUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Danh mục tỉnh/thành → phường/xã cho người đã đăng nhập; trả toàn bộ, không phân trang. */
@RestController
@RequestMapping("/api/v1/provinces")
@RequiredArgsConstructor
@Validated
public class AdministrativeUnitController {

    private final AdministrativeUnitService administrativeUnitService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProvinceResponse>>> listProvinces() {
        return ResponseEntity.ok(ApiResponse.of(administrativeUnitService.listProvinces()));
    }

    @GetMapping("/{provinceId}/wards")
    public ResponseEntity<ApiResponse<List<WardSummaryResponse>>> listWards(@PathVariable UUID provinceId) {
        return ResponseEntity.ok(ApiResponse.of(administrativeUnitService.listWards(provinceId)));
    }
}
