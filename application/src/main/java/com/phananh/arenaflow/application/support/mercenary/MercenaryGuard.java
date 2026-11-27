package com.phananh.arenaflow.application.support.mercenary;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.projection.RecruitContextView;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Một nguồn cho các kiểm tra của luồng tuyển mercenary trên dữ liệu đã nạp bằng câu context — không truy vấn thêm.
 * Service gọi theo đúng thứ tự guard của từng endpoint.
 */
@Component
public class MercenaryGuard {

    private static final Set<String> NOT_MATCHED = Set.of("OPEN", "EXPIRED");
    private static final Set<String> FINISHED = Set.of("CANCELLED", "ARCHIVED", "COMPLETED");
    private static final Set<String> TERMINAL_RECRUITMENT = Set.of("CLOSED", "EXPIRED");

    /** Người gọi đã xoá tài khoản (token còn hạn) không được tạo dữ liệu mới → USER_001. */
    public void requireNotDeleted(boolean callerDeleted) {
        if (callerDeleted) {
            throw new BusinessException(ErrorCode.USER_001);
        }
    }

    /**
     * Đội DISBANDED → TEAM_016; INACTIVE khi thao tác không cho phép → TEAM_015; người gọi không phải đội trưởng ACTIVE
     * → TEAM_002.
     */
    public void requireCaptain(String teamStatus, boolean callerCaptain, boolean allowInactive) {
        if ("DISBANDED".equals(teamStatus)) {
            throw new BusinessException(ErrorCode.TEAM_016);
        }
        if ("INACTIVE".equals(teamStatus) && !allowInactive) {
            throw new BusinessException(ErrorCode.TEAM_015);
        }
        if (!callerCaptain) {
            throw new BusinessException(ErrorCode.TEAM_002);
        }
    }

    /**
     * Trận nhận tin tuyển: đội không thuộc trận → MATCH_009; kèo chưa ghép (OPEN / EXPIRED) → MERCENARY_003; trận đã huỷ /
     * kết thúc hoặc đã qua giờ kết thúc → MERCENARY_004; đã tới giờ bắt đầu → MERCENARY_013. Trận ngoài hệ thống có đội
     * khai báo ở {@code authorTeamId}.
     */
    public void requireRecruitableMatch(RecruitContextView ctx, LocalDateTime now) {
        if (!ctx.getTeamId().equals(ctx.getAuthorTeamId()) && !ctx.getTeamId().equals(ctx.getOpponentTeamId())) {
            throw new BusinessException(ErrorCode.MATCH_009);
        }
        if (NOT_MATCHED.contains(ctx.getMatchStatus())) {
            throw new BusinessException(ErrorCode.MERCENARY_003);
        }
        if (FINISHED.contains(ctx.getMatchStatus()) || !now.isBefore(ctx.getGameEnd())) {
            throw new BusinessException(ErrorCode.MERCENARY_004);
        }
        requireNotStarted(ctx.getGameStart(), now);
    }

    /** Tin (trạng thái hiệu lực) đã đóng / hết hạn → MERCENARY_007. */
    public void requireActive(String effectiveRecruitmentStatus) {
        if (TERMINAL_RECRUITMENT.contains(effectiveRecruitmentStatus)) {
            throw new BusinessException(ErrorCode.MERCENARY_007);
        }
    }

    /** Như {@link #requireActive(String)}, thêm tin đã đủ người → MERCENARY_006. */
    public void requireOpen(String effectiveRecruitmentStatus) {
        requireActive(effectiveRecruitmentStatus);
        if ("FILLED".equals(effectiveRecruitmentStatus)) {
            throw new BusinessException(ErrorCode.MERCENARY_006);
        }
    }

    /** Từ đúng giờ bắt đầu trận → MERCENARY_013. */
    public void requireNotStarted(LocalDateTime gameStart, LocalDateTime now) {
        if (!now.isBefore(gameStart)) {
            throw new BusinessException(ErrorCode.MERCENARY_013);
        }
    }

    /** Thành viên ACTIVE của đội trong trận → MERCENARY_010; đã có đơn APPROVED trùng giờ (gồm chính trận) → MERCENARY_011. */
    public void requireEligibleApplicant(boolean memberOfGameTeams, boolean overlappingApproval) {
        if (memberOfGameTeams) {
            throw new BusinessException(ErrorCode.MERCENARY_010);
        }
        if (overlappingApproval) {
            throw new BusinessException(ErrorCode.MERCENARY_011);
        }
    }

    /** Vừa là ứng viên vừa là đội trưởng → MERCENARY_019; không thuộc bên nào → MERCENARY_014. */
    public MercenarySide resolveSide(boolean callerApplicant, boolean callerCaptain) {
        if (callerApplicant && callerCaptain) {
            throw new BusinessException(ErrorCode.MERCENARY_019);
        }
        if (callerApplicant) {
            return MercenarySide.APPLICANT;
        }
        if (callerCaptain) {
            return MercenarySide.CAPTAIN;
        }
        throw new BusinessException(ErrorCode.MERCENARY_014);
    }
}
