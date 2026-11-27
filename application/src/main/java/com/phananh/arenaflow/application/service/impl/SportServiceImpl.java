package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.UpdateSportRequest;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.SportResponse;
import com.phananh.arenaflow.application.mapper.SportMapper;
import com.phananh.arenaflow.application.service.SportService;
import com.phananh.arenaflow.application.support.AdminAuditLog;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.match.Sport;
import com.phananh.arenaflow.domain.repository.SportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SportServiceImpl implements SportService {

    private static final String TARGET_SPORT = "SPORT";

    private final SportRepository sportRepository;
    private final SportMapper sportMapper;
    private final PageRequestFactory pageRequestFactory;
    private final AdminAuditLog adminAuditLog;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SportResponse> listActiveSports(int page, int pageSize) {
        Page<Sport> sports = sportRepository.findByIsActiveOrderByNameAscIdAsc(true,
                pageRequestFactory.create(page, pageSize));
        return PageResponse.from(sports, sportMapper::toResponse, page, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SportResponse> listSports(Boolean isActive, int page, int pageSize) {
        Pageable pageable = pageRequestFactory.create(page, pageSize);
        Page<Sport> sports = isActive == null
                ? sportRepository.findAllByOrderByNameAscIdAsc(pageable)
                : sportRepository.findByIsActiveOrderByNameAscIdAsc(isActive, pageable);
        return PageResponse.from(sports, sportMapper::toResponse, page, pageSize);
    }

    @Override
    @Transactional
    public SportResponse updateSport(UUID sportId, UUID adminId, UpdateSportRequest request) {
        Sport sport = sportRepository.findById(sportId).orElseThrow(() -> new BusinessException(ErrorCode.SPORT_001));
        if (!request.isActive().equals(sport.getIsActive())) {
            sport.setIsActive(request.isActive());
            adminAuditLog.afterCommit(AdminAuditLog.SET_SPORT_ACTIVE, adminId, TARGET_SPORT, sportId);
        }
        return sportMapper.toResponse(sport);
    }
}
