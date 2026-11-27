package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.AddRecruitmentsRequest;
import com.phananh.arenaflow.application.dto.request.CreateRecruitmentsRequest;
import com.phananh.arenaflow.application.dto.request.SearchRecruitmentsRequest;
import com.phananh.arenaflow.application.dto.request.UpdateRecruitmentRequest;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.RecruitmentResponse;

import java.util.List;
import java.util.UUID;

/** Tin tuyển mercenary: tạo, tìm, xem, sửa số lượng, đóng. */
public interface MercenaryRecruitmentService {

    List<RecruitmentResponse> createRecruitments(UUID matchId, UUID callerId, CreateRecruitmentsRequest request);

    List<RecruitmentResponse> addExternalRecruitments(UUID externalMatchId, UUID callerId,
                                                      AddRecruitmentsRequest request);

    PageResponse<RecruitmentResponse> listTeamRecruitments(UUID teamId, UUID callerId, UUID matchId,
                                                           RecruitmentStatusFilter status, int page, int pageSize);

    PageResponse<RecruitmentResponse> search(UUID callerId, SearchRecruitmentsRequest request);

    RecruitmentResponse getRecruitment(UUID recruitmentId, UUID callerId, Double lat, Double lng);

    RecruitmentResponse updateQuantity(UUID recruitmentId, UUID callerId, UpdateRecruitmentRequest request);

    RecruitmentResponse close(UUID recruitmentId, UUID callerId);
}
