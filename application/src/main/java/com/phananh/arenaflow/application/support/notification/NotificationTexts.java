package com.phananh.arenaflow.application.support.notification;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Nguồn duy nhất của câu chữ thông báo (tiếng Việt, soạn sẵn lúc tạo). Chỉ dùng tên đội, tên sân, giờ trận, vị trí,
 * loại report — không tên cá nhân. Giờ trận dạng {@code HH:mm dd/MM}; thiếu giờ (kèo chưa chốt) thì bỏ cả giờ lẫn
 * sân; thiếu sân thì bỏ phần "tại sân".
 */
@Component
public class NotificationTexts {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM");
    private static final Map<String, String> REPORT_LABELS = Map.of(
            "NOSHOW", "bùng kèo",
            "BAD_PLAY", "chơi xấu",
            "BAD_COMMUNICATION", "giao tiếp tệ",
            "SKILL_MISMATCH", "không đúng trình độ");
    private static final String RATING_TITLE = "Trận đấu hôm nay thế nào?";
    private static final String SUPERSEDED_TITLE = "Kèo đã ghép với đội khác";

    public record Text(String title, String body) {
    }

    public Text welcome() {
        return new Text("Chào mừng bạn đến với ArenaFlow", "Hoàn thiện hồ sơ để bắt đầu tìm đội và tìm kèo.");
    }

    public Text teamInvitation(String teamName) {
        return new Text("Lời mời vào đội", "Đội " + teamName + " mời bạn tham gia đội.");
    }

    public Text teamInvitationAccepted(String teamName) {
        return new Text("Lời mời vào đội được chấp nhận",
                "Có thành viên mới vừa chấp nhận lời mời vào đội " + teamName + ".");
    }

    public Text teamInvitationRejected(String teamName) {
        return new Text("Lời mời vào đội bị từ chối", "Một lời mời vào đội " + teamName + " đã bị từ chối.");
    }

    public Text captainTransferred(String teamName) {
        return new Text("Bạn là đội trưởng mới", "Bạn đã trở thành đội trưởng đội " + teamName + ".");
    }

    public Text teamMarkedInactive(String teamName) {
        return new Text("Đội bị tạm ngưng",
                "Admin đã tạm ngưng hoạt động đội " + teamName + ". Xem chi tiết đội để biết lý do.");
    }

    public Text teamReactivated(String teamName) {
        return new Text("Đội được kích hoạt lại", "Admin đã kích hoạt lại đội " + teamName + ".");
    }

    public Text matchRequestReceived(String requesterTeam, LocalDateTime start, String venue) {
        return new Text("Yêu cầu ghép kèo mới", "Đội " + requesterTeam + " muốn ghép kèo" + game(start, venue) + ".");
    }

    public Text requestWithdrawn(String requesterTeam, LocalDateTime start, String venue) {
        return new Text("Yêu cầu ghép kèo đã bị rút",
                "Đội " + requesterTeam + " đã rút yêu cầu ghép kèo" + game(start, venue) + ".");
    }

    public Text requestAccepted(String authorTeam, LocalDateTime start, String venue) {
        return new Text("Yêu cầu ghép kèo được chấp nhận",
                "Đội " + authorTeam + " đã chấp nhận yêu cầu ghép kèo" + game(start, venue) + ".");
    }

    public Text requestRejected(String authorTeam, LocalDateTime start, String venue) {
        return new Text("Yêu cầu ghép kèo bị từ chối",
                "Đội " + authorTeam + " đã từ chối yêu cầu ghép kèo" + game(start, venue) + ".");
    }

    public Text requestSuperseded(String authorTeam, LocalDateTime start, String venue) {
        return new Text(SUPERSEDED_TITLE,
                "Kèo" + game(start, venue) + " của đội " + authorTeam + " đã ghép với đội khác.");
    }

    public Text requestExpired(String authorTeam, LocalDateTime start, String venue) {
        return new Text("Yêu cầu ghép kèo đã hết hạn",
                "Kèo" + game(start, venue) + " của đội " + authorTeam + " đã hết hạn.");
    }

    public Text pairingNew(String interestedTeam) {
        return new Text("Có đội nhận kèo", "Đội " + interestedTeam
                + " đã nhận kèo của đội bạn — vào thương lượng để chốt sân, giờ và chi phí.");
    }

    public Text pairingSuperseded(String authorTeam) {
        return new Text(SUPERSEDED_TITLE,
                "Kèo của đội " + authorTeam + " mà đội bạn đang thương lượng đã ghép với đội khác.");
    }

    public Text pairingExpired(String authorTeam) {
        return new Text("Thương lượng đã hết hạn",
                "Kèo của đội " + authorTeam + " mà đội bạn đang thương lượng đã hết hạn.");
    }

    public Text matchConfirmed(String authorTeam, String opponentTeam, LocalDateTime start, String venue) {
        return new Text("Kèo đã chốt", "Kèo giữa đội " + authorTeam + " và đội " + opponentTeam + " đã chốt:"
                + game(start, venue) + ".");
    }

    public Text matchCancelledConfirmed(String cancellingTeam, LocalDateTime start, String venue) {
        return new Text("Kèo bị huỷ", "Đội " + cancellingTeam + " đã huỷ kèo" + game(start, venue) + ".");
    }

    public Text matchCancelledNegotiating(String authorTeam) {
        return new Text("Kèo bị huỷ", "Đội " + authorTeam + " đã huỷ kèo mà đội bạn đang thương lượng.");
    }

    public Text matchExpired(LocalDateTime start, String venue) {
        return new Text("Kèo đã hết hạn",
                "Kèo" + game(start, venue) + " của đội bạn đã hết hạn mà chưa ghép được đội.");
    }

    public Text reminderForCaptain(String opponentTeam, LocalDateTime start, String venue) {
        return new Text("Sắp đến giờ đá", "Trận với đội " + opponentTeam + " lúc" + game(start, venue)
                + " bắt đầu sau khoảng 2 giờ.");
    }

    public Text reminderForMercenary(String teamName, String position, LocalDateTime start, String venue) {
        return new Text("Sắp đến giờ đá", "Trận của đội " + teamName + " (vị trí " + position + ") lúc"
                + game(start, venue) + " bắt đầu sau khoảng 2 giờ.");
    }

    public Text matchCompleted(String opponentTeam, LocalDateTime start) {
        return new Text(RATING_TITLE, "Hãy nhập tỷ số và đánh giá đội " + opponentTeam + " (trận" + time(start) + ").");
    }

    public Text mercenaryRatingReminder(String teamName, LocalDateTime start, String position) {
        return new Text(RATING_TITLE, "Hãy đánh giá đội " + teamName + " (trận" + time(start) + ", vị trí "
                + position + ").");
    }

    public Text ratingReceivedFromTeam(String raterTeam, LocalDateTime start) {
        return new Text("Bạn nhận được đánh giá",
                "Đội " + raterTeam + " đã đánh giá đội bạn sau trận" + time(start) + ".");
    }

    public Text ratingReceivedByMercenaryFromTeam(String teamName, LocalDateTime start) {
        return new Text("Bạn nhận được đánh giá", "Đội " + teamName + " đã đánh giá bạn sau trận" + time(start) + ".");
    }

    public Text ratingReceivedByTeamFromMercenary(String position, LocalDateTime start) {
        return new Text("Bạn nhận được đánh giá",
                "Mercenary vị trí " + position + " đã đánh giá đội bạn sau trận" + time(start) + ".");
    }

    /** {@code reportedIsTeam}: bên bị report là đội ("đội bạn") hay cá nhân ("bạn"). */
    public Text reportReceivedFromTeam(String reporterTeam, boolean reportedIsTeam, String reportType) {
        return new Text("Bạn bị report", "Đội " + reporterTeam + " đã report " + (reportedIsTeam ? "đội bạn" : "bạn")
                + ": " + reportLabel(reportType) + "." + noShowDeadline(reportType));
    }

    public Text reportReceivedFromMercenary(String position, String reportType) {
        return new Text("Bạn bị report", "Mercenary vị trí " + position + " đã report đội bạn: " + reportLabel(reportType)
                + "." + noShowDeadline(reportType));
    }

    public Text reportDisputedByTeam(String teamName) {
        return new Text("Report của bạn bị tranh chấp",
                "Đội " + teamName + " đã tranh chấp report bùng kèo của bạn; Admin sẽ xem xét.");
    }

    public Text reportDisputedByMercenary(String position) {
        return new Text("Report của bạn bị tranh chấp",
                "Mercenary vị trí " + position + " đã tranh chấp report bùng kèo của bạn; Admin sẽ xem xét.");
    }

    public Text disputeConfirmed(LocalDateTime start) {
        return new Text("Tranh chấp đã có kết quả", "Admin giữ nguyên report bùng kèo (trận" + time(start) + ").");
    }

    public Text disputeRejectedForReported(LocalDateTime start) {
        return new Text("Tranh chấp đã có kết quả", "Admin bác report bùng kèo (trận" + time(start)
                + "); điểm uy tín đã bị trừ được hoàn lại.");
    }

    public Text disputeRejectedForReporter(LocalDateTime start) {
        return new Text("Tranh chấp đã có kết quả", "Admin bác report bùng kèo của bạn (trận" + time(start) + ").");
    }

    public Text disputeCreated(LocalDateTime start) {
        return new Text("Tranh chấp mới cần xử lý", "Có tranh chấp report bùng kèo mới (trận" + time(start)
                + ") đang chờ Admin xem xét.");
    }

    public Text verificationGrantedTeam(String teamName) {
        return new Text("Đã nhận huy hiệu xác minh", "Đội " + teamName + " đã được cấp huy hiệu xác minh.");
    }

    public Text verificationGrantedUser() {
        return new Text("Đã nhận huy hiệu xác minh", "Bạn đã được cấp huy hiệu xác minh.");
    }

    public Text verificationRevokedTeam(String teamName) {
        return new Text("Huy hiệu xác minh bị thu hồi",
                "Đội " + teamName + " không còn đủ điều kiện nên huy hiệu xác minh đã bị thu hồi.");
    }

    public Text verificationRevokedUser() {
        return new Text("Huy hiệu xác minh bị thu hồi", "Bạn không còn đủ điều kiện nên huy hiệu xác minh đã bị thu hồi.");
    }

    public Text mercenaryApplication(String position, LocalDateTime start, String teamName) {
        return new Text("Có người ứng tuyển", "Có người ứng tuyển vị trí " + position + " cho trận" + time(start)
                + " của đội " + teamName + ".");
    }

    public Text mercenaryApproved(String teamName, String position, LocalDateTime start, String venue) {
        return new Text("Bạn đã được duyệt", "Đội " + teamName + " đã duyệt bạn vị trí " + position + " cho trận"
                + game(start, venue) + ".");
    }

    public Text mercenaryRejectedByCaptain(String teamName, String position, LocalDateTime start) {
        return new Text("Đơn ứng tuyển không được duyệt", "Đội " + teamName + " đã từ chối đơn vị trí " + position
                + " (trận" + time(start) + ").");
    }

    public Text mercenaryRejectedClosed(String position, String teamName, LocalDateTime start) {
        return new Text("Đơn ứng tuyển không được duyệt", "Tin tuyển vị trí " + position + " của đội " + teamName
                + " (trận" + time(start) + ") đã đóng nên đơn của bạn không được duyệt.");
    }

    public Text mercenaryWithdrawnByApplicant(String position, LocalDateTime start) {
        return new Text("Mercenary rút lui", "Mercenary vị trí " + position + " đã rút lui khỏi trận" + time(start) + ".");
    }

    public Text mercenaryWithdrawnAccountDeleted(String position, LocalDateTime start) {
        return new Text("Mercenary rút lui", "Một ứng viên vị trí " + position + " (trận" + time(start)
                + ") đã xoá tài khoản nên đơn bị rút.");
    }

    public Text mercenaryGameCancelled(LocalDateTime start, String teamName, String position) {
        return new Text("Trận đấu bị huỷ", "Trận" + time(start) + " của đội " + teamName + " (vị trí " + position
                + ") đã bị huỷ.");
    }

    public Text venueClosureReported(String venueName) {
        return new Text("Cảnh báo địa điểm sân", "Địa điểm " + venueName + " bị report đã đóng cửa — cần xác minh.");
    }

    public Text venueStale(String venueName) {
        return new Text("Cảnh báo địa điểm sân",
                "Địa điểm " + venueName + " không được chọn cho kèo nào trong 180 ngày — cần kiểm tra.");
    }

    /** " HH:mm dd/MM[ tại sân]" — rỗng khi chưa có giờ trận (khi đó cũng bỏ sân). */
    private static String game(LocalDateTime start, String venue) {
        if (start == null) {
            return "";
        }
        return time(start) + (venue == null || venue.isBlank() ? "" : " tại " + venue);
    }

    private static String time(LocalDateTime start) {
        return start == null ? "" : " " + TIME.format(start);
    }

    private static String reportLabel(String reportType) {
        return REPORT_LABELS.getOrDefault(reportType, reportType);
    }

    private static String noShowDeadline(String reportType) {
        return "NOSHOW".equals(reportType) ? " Bạn có 24 giờ để tranh chấp." : "";
    }
}
