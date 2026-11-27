package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.response.ProvinceResponse;
import com.phananh.arenaflow.application.dto.response.WardSummaryResponse;
import com.phananh.arenaflow.application.mapper.AdministrativeUnitMapper;
import com.phananh.arenaflow.application.service.AdministrativeUnitService;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.projection.ProvinceWardView;
import com.phananh.arenaflow.domain.repository.AdministrativeUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdministrativeUnitServiceImpl implements AdministrativeUnitService {

    private final AdministrativeUnitRepository administrativeUnitRepository;
    private final AdministrativeUnitMapper administrativeUnitMapper;

    /** 1 truy vấn. */
    @Override
    public List<ProvinceResponse> listProvinces() {
        return administrativeUnitRepository.findProvinces().stream().map(administrativeUnitMapper::toProvince).toList();
    }

    /**
     * 1 truy vấn (tỉnh LEFT JOIN phường): 0 dòng → USER_004; 1 dòng không có phường → danh sách rỗng; còn lại → map.
     */
    @Override
    public List<WardSummaryResponse> listWards(UUID provinceId) {
        List<ProvinceWardView> rows = administrativeUnitRepository.findWardsOfProvince(provinceId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.USER_004);
        }
        if (rows.size() == 1 && rows.getFirst().getWardId() == null) {
            return List.of();
        }
        return rows.stream().map(administrativeUnitMapper::toWardSummary).toList();
    }
}
