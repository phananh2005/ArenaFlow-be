package com.phananh.arenaflow.application.support.notification;

import com.phananh.arenaflow.application.support.notification.NotificationTexts.Text;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTextsTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 20, 18, 0);
    private final NotificationTexts texts = new NotificationTexts();

    @Test
    void matchRequestReceivedFormatsTimeAndVenue() {
        Text text = texts.matchRequestReceived("FC Sấm Sét", START, "Sân bóng Phú Thọ");

        assertThat(text.title()).isEqualTo("Yêu cầu ghép kèo mới");
        assertThat(text.body()).isEqualTo("Đội FC Sấm Sét muốn ghép kèo 18:00 20/11 tại Sân bóng Phú Thọ.");
    }

    @Test
    void omitsVenueWhenMissingAndBothTimeAndVenueWhenTimeMissing() {
        assertThat(texts.requestSuperseded("FC A", START, null).body())
                .isEqualTo("Kèo 18:00 20/11 của đội FC A đã ghép với đội khác.");
        assertThat(texts.requestSuperseded("FC A", null, "Sân X").body())
                .isEqualTo("Kèo của đội FC A đã ghép với đội khác.");
    }

    @Test
    void welcomeHasFixedContent() {
        Text text = texts.welcome();

        assertThat(text.title()).isEqualTo("Chào mừng bạn đến với ArenaFlow");
        assertThat(text.body()).isEqualTo("Hoàn thiện hồ sơ để bắt đầu tìm đội và tìm kèo.");
    }

    @Test
    void teamTexts() {
        assertThat(texts.teamInvitation("FC A").body()).isEqualTo("Đội FC A mời bạn tham gia đội.");
        assertThat(texts.teamInvitationAccepted("FC A").body())
                .isEqualTo("Có thành viên mới vừa chấp nhận lời mời vào đội FC A.");
        assertThat(texts.teamInvitationRejected("FC A").body()).isEqualTo("Một lời mời vào đội FC A đã bị từ chối.");
        assertThat(texts.captainTransferred("FC A").body()).isEqualTo("Bạn đã trở thành đội trưởng đội FC A.");
        assertThat(texts.teamMarkedInactive("FC A").body())
                .isEqualTo("Admin đã tạm ngưng hoạt động đội FC A. Xem chi tiết đội để biết lý do.");
        assertThat(texts.teamReactivated("FC A").body()).isEqualTo("Admin đã kích hoạt lại đội FC A.");
    }

    @Test
    void matchLifecycleTexts() {
        assertThat(texts.matchConfirmed("FC A", "FC B", START, "Sân X").body())
                .isEqualTo("Kèo giữa đội FC A và đội FC B đã chốt: 18:00 20/11 tại Sân X.");
        assertThat(texts.matchCancelledConfirmed("FC B", START, null).body())
                .isEqualTo("Đội FC B đã huỷ kèo 18:00 20/11.");
        assertThat(texts.matchCancelledNegotiating("FC A").body())
                .isEqualTo("Đội FC A đã huỷ kèo mà đội bạn đang thương lượng.");
        assertThat(texts.matchExpired(START, "Sân X").body())
                .isEqualTo("Kèo 18:00 20/11 tại Sân X của đội bạn đã hết hạn mà chưa ghép được đội.");
        assertThat(texts.pairingNew("FC B").body())
                .isEqualTo("Đội FC B đã nhận kèo của đội bạn — vào thương lượng để chốt sân, giờ và chi phí.");
        assertThat(texts.pairingExpired("FC A").title()).isEqualTo("Thương lượng đã hết hạn");
    }

    @Test
    void reminderTextsForCaptainAndMercenary() {
        assertThat(texts.reminderForCaptain("FC B", START, "Sân X").body())
                .isEqualTo("Trận với đội FC B lúc 18:00 20/11 tại Sân X bắt đầu sau khoảng 2 giờ.");
        assertThat(texts.reminderForMercenary("FC A", "Thủ môn", START, null).body())
                .isEqualTo("Trận của đội FC A (vị trí Thủ môn) lúc 18:00 20/11 bắt đầu sau khoảng 2 giờ.");
        assertThat(texts.matchCompleted("FC B", START).body())
                .isEqualTo("Hãy nhập tỷ số và đánh giá đội FC B (trận 18:00 20/11).");
        assertThat(texts.mercenaryRatingReminder("FC A", START, "Tiền đạo").body())
                .isEqualTo("Hãy đánh giá đội FC A (trận 18:00 20/11, vị trí Tiền đạo).");
    }

    @Test
    void reportReceivedAddsDisputeDeadlineOnlyForNoShow() {
        assertThat(texts.reportReceivedFromTeam("FC A", true, "NOSHOW").body())
                .isEqualTo("Đội FC A đã report đội bạn: bùng kèo. Bạn có 24 giờ để tranh chấp.");
        assertThat(texts.reportReceivedFromTeam("FC A", false, "BAD_COMMUNICATION").body())
                .isEqualTo("Đội FC A đã report bạn: giao tiếp tệ.");
        assertThat(texts.reportReceivedFromMercenary("Hậu vệ", "BAD_PLAY").body())
                .isEqualTo("Mercenary vị trí Hậu vệ đã report đội bạn: chơi xấu.");
        assertThat(texts.reportReceivedFromTeam("FC A", true, "SKILL_MISMATCH").body())
                .isEqualTo("Đội FC A đã report đội bạn: không đúng trình độ.");
    }

    @Test
    void disputeTexts() {
        assertThat(texts.reportDisputedByTeam("FC B").body())
                .isEqualTo("Đội FC B đã tranh chấp report bùng kèo của bạn; Admin sẽ xem xét.");
        assertThat(texts.reportDisputedByMercenary("Tiền vệ").body())
                .isEqualTo("Mercenary vị trí Tiền vệ đã tranh chấp report bùng kèo của bạn; Admin sẽ xem xét.");
        assertThat(texts.disputeConfirmed(START).body()).isEqualTo("Admin giữ nguyên report bùng kèo (trận 18:00 20/11).");
        assertThat(texts.disputeRejectedForReported(START).body())
                .isEqualTo("Admin bác report bùng kèo (trận 18:00 20/11); điểm uy tín đã bị trừ được hoàn lại.");
        assertThat(texts.disputeRejectedForReporter(START).body())
                .isEqualTo("Admin bác report bùng kèo của bạn (trận 18:00 20/11).");
        assertThat(texts.disputeCreated(START).body())
                .isEqualTo("Có tranh chấp report bùng kèo mới (trận 18:00 20/11) đang chờ Admin xem xét.");
    }

    @Test
    void verificationTexts() {
        assertThat(texts.verificationGrantedTeam("FC A").body()).isEqualTo("Đội FC A đã được cấp huy hiệu xác minh.");
        assertThat(texts.verificationGrantedUser().body()).isEqualTo("Bạn đã được cấp huy hiệu xác minh.");
        assertThat(texts.verificationRevokedTeam("FC A").body())
                .isEqualTo("Đội FC A không còn đủ điều kiện nên huy hiệu xác minh đã bị thu hồi.");
        assertThat(texts.verificationRevokedUser().body())
                .isEqualTo("Bạn không còn đủ điều kiện nên huy hiệu xác minh đã bị thu hồi.");
    }

    @Test
    void mercenaryTextsNeverRevealVenueBeforeApproval() {
        assertThat(texts.mercenaryApplication("Thủ môn", START, "FC A").body())
                .isEqualTo("Có người ứng tuyển vị trí Thủ môn cho trận 18:00 20/11 của đội FC A.");
        assertThat(texts.mercenaryApproved("FC A", "Thủ môn", START, "Sân X").body())
                .isEqualTo("Đội FC A đã duyệt bạn vị trí Thủ môn cho trận 18:00 20/11 tại Sân X.");
        assertThat(texts.mercenaryRejectedByCaptain("FC A", "Thủ môn", START).body())
                .isEqualTo("Đội FC A đã từ chối đơn vị trí Thủ môn (trận 18:00 20/11).")
                .doesNotContain("Sân");
        assertThat(texts.mercenaryRejectedClosed("Thủ môn", "FC A", START).body())
                .isEqualTo("Tin tuyển vị trí Thủ môn của đội FC A (trận 18:00 20/11) đã đóng nên đơn của bạn không được duyệt.");
        assertThat(texts.mercenaryWithdrawnByApplicant("Thủ môn", START).body())
                .isEqualTo("Mercenary vị trí Thủ môn đã rút lui khỏi trận 18:00 20/11.");
        assertThat(texts.mercenaryWithdrawnAccountDeleted("Thủ môn", START).body())
                .isEqualTo("Một ứng viên vị trí Thủ môn (trận 18:00 20/11) đã xoá tài khoản nên đơn bị rút.");
        assertThat(texts.mercenaryGameCancelled(START, "FC A", "Thủ môn").body())
                .isEqualTo("Trận 18:00 20/11 của đội FC A (vị trí Thủ môn) đã bị huỷ.");
    }

    @Test
    void ratingReceivedVariants() {
        assertThat(texts.ratingReceivedFromTeam("FC A", START).body())
                .isEqualTo("Đội FC A đã đánh giá đội bạn sau trận 18:00 20/11.");
        assertThat(texts.ratingReceivedByMercenaryFromTeam("FC A", START).body())
                .isEqualTo("Đội FC A đã đánh giá bạn sau trận 18:00 20/11.");
        assertThat(texts.ratingReceivedByTeamFromMercenary("Tiền đạo", START).body())
                .isEqualTo("Mercenary vị trí Tiền đạo đã đánh giá đội bạn sau trận 18:00 20/11.");
    }

    @Test
    void venueAdminAlerts() {
        assertThat(texts.venueClosureReported("Sân X").body())
                .isEqualTo("Địa điểm Sân X bị report đã đóng cửa — cần xác minh.");
        assertThat(texts.venueStale("Sân X").body())
                .isEqualTo("Địa điểm Sân X không được chọn cho kèo nào trong 180 ngày — cần kiểm tra.");
    }

    @Test
    void everyTitleFitsColumnLimit() {
        assertThat(texts.mercenaryApplication("Thủ môn", START, "FC A").title().length()).isLessThanOrEqualTo(200);
        assertThat(texts.requestWithdrawn("FC A", START, null).title()).isEqualTo("Yêu cầu ghép kèo đã bị rút");
        assertThat(texts.requestAccepted("FC A", START, null).title()).isEqualTo("Yêu cầu ghép kèo được chấp nhận");
        assertThat(texts.requestRejected("FC A", START, null).title()).isEqualTo("Yêu cầu ghép kèo bị từ chối");
        assertThat(texts.requestExpired("FC A", START, null).title()).isEqualTo("Yêu cầu ghép kèo đã hết hạn");
        assertThat(texts.pairingSuperseded("FC A").title()).isEqualTo("Kèo đã ghép với đội khác");
    }
}
