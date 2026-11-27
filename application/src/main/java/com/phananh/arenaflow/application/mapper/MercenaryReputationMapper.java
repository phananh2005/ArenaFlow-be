package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.AdminMercenaryDisputeDetailResponse;
import com.phananh.arenaflow.application.dto.response.AdminMercenaryDisputeResponse;
import com.phananh.arenaflow.application.dto.response.MercenaryRatingResponse;
import com.phananh.arenaflow.application.dto.response.MercenaryReportResponse;
import com.phananh.arenaflow.application.dto.response.MercenaryTeamBriefResponse;
import com.phananh.arenaflow.application.dto.response.MercenaryUserBriefResponse;
import com.phananh.arenaflow.application.dto.response.ReputationSummaryResponse;
import com.phananh.arenaflow.application.dto.response.TeamMercenaryRatingResponse;
import com.phananh.arenaflow.application.dto.response.UserReputationResponse;
import com.phananh.arenaflow.application.support.reputation.ReportStatusResolver;
import com.phananh.arenaflow.application.support.reputation.ReputationRules;
import com.phananh.arenaflow.domain.entity.rating.Rating;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.projection.AdminMercenaryDisputeDetailView;
import com.phananh.arenaflow.domain.projection.AdminMercenaryDisputeView;
import com.phananh.arenaflow.domain.projection.MercenaryReportContextView;
import com.phananh.arenaflow.domain.projection.MercenaryReportView;
import com.phananh.arenaflow.domain.projection.TeamMercenaryRatingView;
import com.phananh.arenaflow.domain.projection.UserReputationView;
import org.mapstruct.Mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dựng response uy tín cá nhân: mức điểm, tỉ lệ hoàn thành (null khi chưa đủ số trận được tính), sao trung bình 1 chữ số
 * thập phân.
 */
@Mapper(componentModel = "spring")
public interface MercenaryReputationMapper {

    String TEAM_TO_MERCENARY = "TEAM_TO_MERCENARY";
    String MERCENARY_TO_TEAM = "MERCENARY_TO_TEAM";
    String RECEIVED = "RECEIVED";
    String SENT = "SENT";

    default UserReputationResponse toReputation(UserReputationView v) {
        int score = v.getReliabilityScore() == null ? 0 : v.getReliabilityScore();
        return new UserReputationResponse(v.getUserId(), score, reliabilityLevel(score),
                completion(v.getCompletedMatches(), v.getCountedMatches()),
                rating(v.getAverageRating(), v.getRatingCount()),
                new UserReputationResponse.Verification(v.getVerifiedAt() != null, v.getVerifiedAt()));
    }

    /** Đánh giá theo đơn từ entity (chiều suy ra từ bên đánh giá). */
    default MercenaryRatingResponse toRating(Rating rating) {
        boolean teamToMercenary = rating.getRaterType() == Rating.RaterType.TEAM;
        return toRating(rating.getId(), rating.getApplicationId(), rating.getMatchId(), rating.getExternalMatchId(),
                teamToMercenary, teamToMercenary ? rating.getRaterId() : rating.getRateeId(),
                teamToMercenary ? rating.getRateeId() : rating.getRaterId(), rating.isNoShow(), rating.getScore(),
                rating.getComment(), rating.getCreatedAt());
    }

    default MercenaryRatingResponse toRating(UUID ratingId, UUID applicationId, UUID matchId, UUID externalMatchId,
                                             boolean teamToMercenary, UUID teamId, UUID mercenaryUserId,
                                             boolean noShow, Integer score, String comment, LocalDateTime createdAt) {
        return new MercenaryRatingResponse(ratingId, applicationId, source(matchId), matchId, externalMatchId,
                teamToMercenary ? TEAM_TO_MERCENARY : MERCENARY_TO_TEAM, teamId, mercenaryUserId, noShow, score,
                comment, createdAt);
    }

    /**
     * Report theo góc nhìn người xem: trạng thái hiệu lực (report bùng hết hạn tranh chấp hiện CONFIRMED), nút tranh
     * chấp, điểm bị trừ chỉ ở chiều nhận, bằng chứng tranh chấp chỉ ở chiều nhận; tên mercenary đã xoá được che.
     */
    default MercenaryReportResponse toReport(MercenaryReportView v, String direction, LocalDateTime now) {
        boolean received = RECEIVED.equals(direction);
        MercenaryReportResponse.Dispute dispute = v.getDisputeId() == null ? null : new MercenaryReportResponse.Dispute(
                v.getDisputeId(), v.getDisputeStatus(), received ? v.getDisputeEvidence() : null,
                v.getDisputeDecision(), v.getDisputeResolutionNote(), v.getDisputeCreatedAt(), v.getDisputeResolvedAt());
        return new MercenaryReportResponse(v.getReportId(), v.getApplicationId(), source(v.getMatchId()),
                v.getMatchId(), v.getExternalMatchId(), v.getGameStart(), direction, reporterSide(v.getReporterType()),
                new MercenaryTeamBriefResponse(v.getTeamId(), v.getTeamName(), v.getTeamAvatarUrl()),
                new MercenaryUserBriefResponse(v.getMercenaryId(),
                        MapperSupport.maskName(v.getMercenaryName(), v.getMercenaryDeleted()),
                        MapperSupport.maskAvatar(v.getMercenaryAvatarUrl(), v.getMercenaryDeleted())),
                v.getReportType(), v.getReason(),
                ReportStatusResolver.effectiveStatus(v.getStatus(), v.getReportType(), v.getCreatedAt(), now),
                ReportStatusResolver.disputeDeadline(v.getReportType(), v.getCreatedAt()),
                ReportStatusResolver.canDispute(received, v.getStatus(), v.getReportType(), v.getCreatedAt(), now),
                received ? v.getReliabilityPenalty() : null, dispute, v.getCreatedAt());
    }

    /** Report vừa gửi (góc nhìn người gửi — chiều SENT). */
    default MercenaryReportResponse toCreatedReport(Report report, MercenaryReportContextView ctx) {
        return new MercenaryReportResponse(report.getId(), report.getApplicationId(), source(report.getMatchId()),
                report.getMatchId(), report.getExternalMatchId(), ctx.getGameStart(), SENT,
                reporterSide(report.getReporterType().name()),
                new MercenaryTeamBriefResponse(ctx.getTeamId(), ctx.getTeamName(), ctx.getTeamAvatarUrl()),
                new MercenaryUserBriefResponse(ctx.getApplicantId(),
                        MapperSupport.maskName(ctx.getApplicantName(), ctx.getApplicantDeleted()),
                        MapperSupport.maskAvatar(ctx.getApplicantAvatarUrl(), ctx.getApplicantDeleted())),
                report.getReportType().name(), report.getReason(), report.getStatus().name(),
                ReportStatusResolver.disputeDeadline(report.getReportType().name(), report.getCreatedAt()), false, null,
                null, report.getCreatedAt());
    }

    default TeamMercenaryRatingResponse toTeamRating(TeamMercenaryRatingView v) {
        return new TeamMercenaryRatingResponse(v.getRatingId(), source(v.getMatchId()), v.getMatchId(),
                v.getExternalMatchId(), v.getGameStart(), new MercenaryUserBriefResponse(v.getUserId(),
                MapperSupport.maskName(v.getDisplayName(), v.getDeleted()),
                MapperSupport.maskAvatar(v.getAvatarUrl(), v.getDeleted())), v.getScore(), v.getComment(),
                v.getCreatedAt());
    }

    /** Dòng hàng chờ Admin: tên thật + cờ đã xoá (không che). */
    default AdminMercenaryDisputeResponse toAdminDispute(AdminMercenaryDisputeView v) {
        return new AdminMercenaryDisputeResponse(v.getDisputeId(), v.getReportId(), v.getApplicationId(),
                source(v.getMatchId()), v.getMatchId(), v.getExternalMatchId(), v.getGameStart(),
                reporterSide(v.getReporterType()), adminTeam(v), v.getMercenaryId(), v.getMercenaryName(),
                Boolean.TRUE.equals(v.getMercenaryDeleted()), v.getReportedById(), v.getReportedByName(),
                Boolean.TRUE.equals(v.getReportedByDeleted()), v.getDisputedById(), v.getDisputedByName(),
                Boolean.TRUE.equals(v.getDisputedByDeleted()), v.getStatus(), v.getDecision(), v.getResolvedById(),
                v.getResolvedByName(), Boolean.TRUE.equals(v.getResolvedByDeleted()), v.getResolvedAt(),
                v.getReportCreatedAt(), v.getCreatedAt());
    }

    /** Chi tiết Admin + đánh giá hai phía của đơn (cũ nhất trước). */
    default AdminMercenaryDisputeDetailResponse toAdminDisputeDetail(AdminMercenaryDisputeDetailView v,
                                                                     List<Rating> ratings) {
        AdminMercenaryDisputeDetailResponse.Match match = new AdminMercenaryDisputeDetailResponse.Match(v.getSource(),
                v.getSportName(), v.getGameStatus(), v.getGameStart(), v.getGameEnd(), v.getVenueName(),
                v.getOpponentName());
        return new AdminMercenaryDisputeDetailResponse(v.getDisputeId(), v.getReportId(), v.getApplicationId(),
                source(v.getMatchId()), v.getMatchId(), v.getExternalMatchId(), v.getGameStart(),
                reporterSide(v.getReporterType()), adminTeam(v), v.getMercenaryId(), v.getMercenaryName(),
                Boolean.TRUE.equals(v.getMercenaryDeleted()), v.getReportedById(), v.getReportedByName(),
                Boolean.TRUE.equals(v.getReportedByDeleted()), v.getDisputedById(), v.getDisputedByName(),
                Boolean.TRUE.equals(v.getDisputedByDeleted()), v.getStatus(), v.getDecision(), v.getResolvedById(),
                v.getResolvedByName(), Boolean.TRUE.equals(v.getResolvedByDeleted()), v.getResolvedAt(),
                v.getReportCreatedAt(), v.getCreatedAt(), match, v.getPosition(), v.getReportReason(),
                v.getReliabilityPenalty() == null ? 0 : v.getReliabilityPenalty(), v.getEvidence(),
                v.getResolutionNote(), v.getTeamReliabilityScore() == null ? 0 : v.getTeamReliabilityScore(),
                v.getMercenaryReliabilityScore() == null ? 0 : v.getMercenaryReliabilityScore(),
                ratings.stream().map(this::toRating).toList());
    }

    private static AdminMercenaryDisputeResponse.Team adminTeam(AdminMercenaryDisputeView v) {
        return new AdminMercenaryDisputeResponse.Team(v.getTeamId(), v.getTeamName(), v.getTeamAvatarUrl(),
                v.getTeamStatus());
    }

    /** Kèo trên hệ thống khi có {@code matchId}, ngược lại trận ngoài hệ thống. */
    static String source(UUID matchId) {
        return matchId != null ? "SYSTEM" : "EXTERNAL";
    }

    /** Bên report: đội trưởng (TEAM) hoặc mercenary (MERCENARY). */
    static String reporterSide(String reporterType) {
        return "TEAM".equals(reporterType) ? "TEAM" : "MERCENARY";
    }

    /** Tóm tắt uy tín cho thẻ ứng viên. */
    static ReputationSummaryResponse summary(Integer reliabilityScore, Long completedMatches, Long countedMatches,
                                             BigDecimal averageRating, Long ratingCount, boolean verified) {
        int score = reliabilityScore == null ? 0 : reliabilityScore;
        return new ReputationSummaryResponse(score, reliabilityLevel(score), completion(completedMatches, countedMatches),
                rating(averageRating, ratingCount), verified);
    }

    /** EXCELLENT / NORMAL / WARNING / CRITICAL theo ngưỡng điểm (chỉ để hiển thị). */
    static String reliabilityLevel(int score) {
        if (score >= ReputationRules.LEVEL_EXCELLENT_MIN) {
            return "EXCELLENT";
        }
        if (score >= ReputationRules.LEVEL_NORMAL_MIN) {
            return "NORMAL";
        }
        return score >= ReputationRules.LEVEL_WARNING_MIN ? "WARNING" : "CRITICAL";
    }

    /** Tỉ lệ hoàn thành làm tròn tới số nguyên; chưa đủ số trận được tính → null và "mới tham gia". */
    static UserReputationResponse.Completion completion(Long completedMatches, Long countedMatches) {
        int completed = completedMatches == null ? 0 : completedMatches.intValue();
        int counted = countedMatches == null ? 0 : countedMatches.intValue();
        boolean newcomer = counted < ReputationRules.NEWCOMER_THRESHOLD;
        Integer rate = newcomer ? null : BigDecimal.valueOf(completed * 100L)
                .divide(BigDecimal.valueOf(counted), 0, RoundingMode.HALF_UP).intValue();
        return new UserReputationResponse.Completion(completed, counted, rate, newcomer);
    }

    static UserReputationResponse.Rating rating(BigDecimal averageRating, Long ratingCount) {
        return new UserReputationResponse.Rating(
                averageRating == null ? null : averageRating.setScale(1, RoundingMode.HALF_UP),
                ratingCount == null ? 0 : ratingCount.intValue());
    }
}
