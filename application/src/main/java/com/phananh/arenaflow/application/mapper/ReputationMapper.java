package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.AdminDisputeDetailResponse;
import com.phananh.arenaflow.application.dto.response.AdminDisputeResponse;
import com.phananh.arenaflow.application.dto.response.AdminTeamBriefResponse;
import com.phananh.arenaflow.application.dto.response.DisputeCreatedResponse;
import com.phananh.arenaflow.application.dto.response.DisputeMatchResponse;
import com.phananh.arenaflow.application.dto.response.RatingCreatedResponse;
import com.phananh.arenaflow.application.dto.response.RatingResponse;
import com.phananh.arenaflow.application.dto.response.ReportDisputeResponse;
import com.phananh.arenaflow.application.dto.response.ReportResponse;
import com.phananh.arenaflow.application.dto.response.TeamBriefResponse;
import com.phananh.arenaflow.application.dto.response.TeamReputationResponse;
import com.phananh.arenaflow.application.service.ReportDirection;
import com.phananh.arenaflow.application.support.reputation.ReportStatusResolver;
import com.phananh.arenaflow.application.support.reputation.ReputationRules;
import com.phananh.arenaflow.domain.entity.rating.Dispute;
import com.phananh.arenaflow.domain.entity.rating.Rating;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.projection.AdminDisputeDetailView;
import com.phananh.arenaflow.domain.projection.AdminDisputeView;
import com.phananh.arenaflow.domain.projection.MatchRatingRowView;
import com.phananh.arenaflow.domain.projection.TeamReportView;
import com.phananh.arenaflow.domain.projection.TeamReputationView;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Ánh xạ đánh giá / report / tranh chấp / uy tín. Viết tay bằng method default: nhiều response cần tính khi đọc
 * (trạng thái report hiệu lực, quyền tranh chấp, làm tròn), tên field API khác tên cột.
 */
@Mapper(componentModel = "spring", uses = MapperSupport.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ReputationMapper {

    default RatingResponse toRating(Rating rating) {
        return new RatingResponse(rating.getId(), rating.getMatchId(), rating.getRaterId(), rating.getRateeId(),
                rating.isNoShow(), rating.getScore(), rating.getComment(), rating.getRaterTeamScore(),
                rating.getRateeTeamScore(), rating.getCreatedAt());
    }

    default RatingResponse toRating(MatchRatingRowView row, UUID matchId) {
        return new RatingResponse(row.getRatingId(), matchId, row.getRaterTeamId(), row.getRateeTeamId(),
                Boolean.TRUE.equals(row.getNoShow()), row.getScore(), row.getComment(), row.getRaterTeamScore(),
                row.getRateeTeamScore(), row.getCreatedAt());
    }

    default RatingCreatedResponse toCreated(Rating rating, String matchStatus) {
        return new RatingCreatedResponse(rating.getId(), rating.getMatchId(), rating.getRaterId(), rating.getRateeId(),
                rating.isNoShow(), rating.getScore(), rating.getComment(), rating.getRaterTeamScore(),
                rating.getRateeTeamScore(), rating.getCreatedAt(), matchStatus);
    }

    /**
     * Report trong danh sách của đội theo góc nhìn {@code direction}: trạng thái hiệu lực + quyền tranh chấp tính theo
     * {@code now}; góc nhìn SENT ẩn điểm bị trừ và bằng chứng tranh chấp của đội kia.
     */
    default ReportResponse toReport(TeamReportView view, ReportDirection direction, LocalDateTime now) {
        Report.ReportType type = Report.ReportType.valueOf(view.getReportType());
        Report.Status stored = Report.Status.valueOf(view.getStatus());
        boolean sent = direction == ReportDirection.SENT;
        ReportDisputeResponse dispute = view.getDisputeId() == null ? null
                : new ReportDisputeResponse(view.getDisputeId(), view.getDisputeStatus(),
                sent ? null : view.getDisputeEvidence(), view.getDisputeDecision(), view.getDisputeResolutionNote(),
                view.getDisputeCreatedAt(), view.getDisputeResolvedAt());
        return new ReportResponse(view.getReportId(), view.getMatchId(), view.getMatchStartTime(), direction.name(),
                new TeamBriefResponse(view.getCounterpartTeamId(), view.getCounterpartTeamName(),
                        view.getCounterpartTeamAvatarUrl()),
                type.name(), view.getReason(),
                ReportStatusResolver.effectiveStatus(stored, type, view.getCreatedAt(), now).name(),
                ReportStatusResolver.disputeDeadline(type, view.getCreatedAt()),
                ReportStatusResolver.canDispute(direction, type, stored, view.getCreatedAt(), now),
                sent ? null : view.getReliabilityPenalty(), dispute, view.getCreatedAt());
    }

    default DisputeCreatedResponse toDisputeCreated(Dispute dispute) {
        return new DisputeCreatedResponse(dispute.getId(), dispute.getReportId(), dispute.getStatus().name(),
                dispute.getEvidence(), Report.Status.DISPUTED.name(), dispute.getCreatedAt());
    }

    /** Tên người dùng trả nguyên (API Admin không che tên người đã xoá), cờ đã xoá false khi không có người. */
    default AdminDisputeResponse toAdminDispute(AdminDisputeView view) {
        return new AdminDisputeResponse(view.getDisputeId(), view.getReportId(), view.getMatchId(),
                view.getMatchStartTime(), reporterTeam(view), reportedTeam(view),
                view.getReportedById(), view.getReportedByName(), Boolean.TRUE.equals(view.getReportedByDeleted()),
                view.getDisputedById(), view.getDisputedByName(), Boolean.TRUE.equals(view.getDisputedByDeleted()),
                view.getStatus(), view.getDecision(),
                view.getResolvedById(), view.getResolvedByName(), Boolean.TRUE.equals(view.getResolvedByDeleted()),
                view.getResolvedAt(), view.getReportCreatedAt(), view.getCreatedAt());
    }

    default AdminDisputeDetailResponse toAdminDetail(AdminDisputeDetailView view, List<Rating> ratings) {
        return new AdminDisputeDetailResponse(view.getDisputeId(), view.getReportId(), view.getMatchId(),
                view.getMatchStartTime(), reporterTeam(view), reportedTeam(view),
                view.getReportedById(), view.getReportedByName(), Boolean.TRUE.equals(view.getReportedByDeleted()),
                view.getDisputedById(), view.getDisputedByName(), Boolean.TRUE.equals(view.getDisputedByDeleted()),
                view.getStatus(), view.getDecision(),
                view.getResolvedById(), view.getResolvedByName(), Boolean.TRUE.equals(view.getResolvedByDeleted()),
                view.getResolvedAt(), view.getReportCreatedAt(), view.getCreatedAt(),
                new DisputeMatchResponse(view.getSportName(), view.getMatchStatus(), view.getMatchStartTime(),
                        view.getMatchEndTime(), view.getVenueName()),
                view.getReportReason(), view.getReliabilityPenalty(), view.getEvidence(), view.getResolutionNote(),
                view.getReporterTeamReliabilityScore(), view.getReportedTeamReliabilityScore(),
                ratings.stream().map(this::toRating).toList());
    }

    /**
     * Tỉ lệ hoàn thành = phần trăm làm tròn HALF_UP tới số nguyên, null khi số trận được tính dưới ngưỡng "mới tham
     * gia"; sao trung bình HALF_UP 1 chữ số thập phân.
     */
    default TeamReputationResponse toReputation(TeamReputationView view) {
        int completed = view.getCompletedMatches() == null ? 0 : view.getCompletedMatches().intValue();
        int counted = view.getCountedMatches() == null ? 0 : view.getCountedMatches().intValue();
        boolean newcomer = counted < ReputationRules.NEWCOMER_THRESHOLD;
        Integer completionRate = newcomer ? null
                : BigDecimal.valueOf(100L * completed).divide(BigDecimal.valueOf(counted), 0, RoundingMode.HALF_UP)
                .intValue();
        BigDecimal average = view.getAverageRating() == null ? null
                : view.getAverageRating().setScale(1, RoundingMode.HALF_UP);
        int score = view.getReliabilityScore();
        return new TeamReputationResponse(view.getTeamId(), score, ReputationRules.reliabilityLevel(score).name(),
                new TeamReputationResponse.CompletionResponse(completed, counted, completionRate, newcomer),
                new TeamReputationResponse.RatingSummaryResponse(average,
                        view.getRatingCount() == null ? 0 : view.getRatingCount().intValue()),
                new TeamReputationResponse.VerificationResponse(view.getVerifiedAt() != null, view.getVerifiedAt()));
    }

    private static AdminTeamBriefResponse reporterTeam(AdminDisputeView view) {
        return new AdminTeamBriefResponse(view.getReporterTeamId(), view.getReporterTeamName(),
                view.getReporterTeamAvatarUrl(), view.getReporterTeamStatus());
    }

    private static AdminTeamBriefResponse reportedTeam(AdminDisputeView view) {
        return new AdminTeamBriefResponse(view.getReportedTeamId(), view.getReportedTeamName(),
                view.getReportedTeamAvatarUrl(), view.getReportedTeamStatus());
    }

    /** Report vừa gửi, góc nhìn đội gửi (SENT): chưa có tranh chấp, không trả điểm bị trừ. */
    default ReportResponse toSentReport(Report report, LocalDateTime matchStartsAt, TeamBriefResponse counterpart,
                                        LocalDateTime now) {
        return new ReportResponse(report.getId(), report.getMatchId(), matchStartsAt, ReportDirection.SENT.name(),
                counterpart, report.getReportType().name(), report.getReason(),
                ReportStatusResolver.effectiveStatus(report.getStatus(), report.getReportType(), report.getCreatedAt(), now)
                        .name(),
                ReportStatusResolver.disputeDeadline(report.getReportType(), report.getCreatedAt()), false, null, null,
                report.getCreatedAt());
    }
}
