package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.WithdrawApplicationRequest;
import com.phananh.arenaflow.application.dto.response.ApplicantResponse;
import com.phananh.arenaflow.application.dto.response.ApplicationResponse;
import com.phananh.arenaflow.application.dto.response.ApprovalResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.WithdrawalResponse;

import java.util.UUID;

/** Đơn ứng tuyển mercenary: ứng tuyển, xem, duyệt, từ chối, rút. */
public interface MercenaryApplicationService {

    ApplicationResponse apply(UUID recruitmentId, UUID callerId);

    PageResponse<ApplicantResponse> listApplicants(UUID recruitmentId, UUID callerId, ApplicationStatusFilter status,
                                                   int page, int pageSize);

    PageResponse<ApplicationResponse> listMyApplications(UUID callerId, ApplicationStatusFilter status, int page,
                                                         int pageSize);

    ApplicationResponse getApplication(UUID applicationId, UUID callerId);

    ApprovalResponse approve(UUID applicationId, UUID callerId);

    void reject(UUID applicationId, UUID callerId);

    WithdrawalResponse withdraw(UUID applicationId, UUID callerId, WithdrawApplicationRequest request);
}
