# NOTIFICATION-001 — phạm vi & câu cần chốt

Nhánh: `feature/NOTIFICATION-001-in-app-notification` (từ `origin/main` `8432d5c`, 2026-11-16).
SRS: SYSTEM-001 v1.10 — UC-N02, UC-N03, BR-N01, BR-N02, BC-N01..N03, Overview §4 (11 loại), §9.6, Business_Rules §8.6, AC "NOTIFICATION DOMAIN", AC-U01 (thông báo chào mừng), AC-M08 (push sau trận).
Đã chốt 2026-11-09 (decisions-log): in-app (bảng `notification` + API) + interface `PushSender` bản no-op; FCM/APNs để sau. Miền phát `ApplicationEvent`, NOTIFICATION-001 nghe (ADR-0011, `@TransactionalEventListener(AFTER_COMMIT)`).
Schema có sẵn (V1): `notification` (`recipient_user_id`, `type` CHECK 11 giá trị, `title` 200, `body`, `reference_type`, `reference_id`, `is_read`, `created_at`; index `(recipient_user_id, is_read, created_at DESC)`, `(created_at)`). Chưa có bảng device token.

## Event đã có trong các design (người nghe = NOTIFICATION-001)
- VENUE-001: `VenueReportedEvent`, `VenueAdminAlertEvent` (cảnh báo admin; VENUE #1 gộp hay không).
- MATCH-001: `MatchRequestSentEvent`, `MatchConfirmedEvent`, `MatchRequestRejectedEvent`, `MatchRequestWithdrawnEvent` (tuỳ chọn), `MatchCancelledEvent`, `MatchExpiredEvent` (AC-M07), `MatchArchivedEvent` (AC-M08).
- MATCH-002: `PairingCreatedEvent`; pairing `SUPERSEDED`/`EXPIRED` tự truy vấn (AC-M05).
- REPUTATION-001: `MatchRatedEvent` (#11 "rating nhận" vs BC-R04), `TeamReportedEvent`, `ReportDisputedEvent` (tuỳ chọn), `DisputeResolvedEvent`, `TeamVerificationChangedEvent`.
- MERCENARY-001: 9 event §4.11 (ứng tuyển, duyệt, từ chối, rút, đóng tin, huỷ trận, rating, report, huy hiệu cá nhân).
- USER-001/USER-002 (đã code): chưa có event — lời mời vào đội (1 trong 11 loại), chào mừng (AC-U01).
- Chưa có nguồn: "reminder trước trận" (cần job).
- Job xoá chat 90 ngày: điều kiện ở MATCH-001 CCD §11 dòng 745, MERCENARY-001 CCD §11 dòng 715.

## Câu cần chốt (hỏi từng câu)
| # | Chủ đề | Trạng thái |
|---|---|---|
| Q1 | Loại thông báo: đúng 11 loại SRS hay thêm các loại design đã hứa | chốt |
| Q2 | Người nhận khi sự kiện nhắm vào đội (đội trưởng / mọi thành viên); chuyển quyền đội trưởng | chốt |
| Q3 | "Rating nhận" mâu thuẫn BC-R04 (REPUTATION-001 #11) | chốt |
| Q4 | Reminder trước trận: lúc nào, gửi ai | chốt |
| Q5 | Nội dung lưu sẵn (tiêu đề/nội dung tiếng Việt lúc tạo) hay lưu loại + tham số | chốt |
| Q6 | Thông báo mới đẩy realtime qua kênh STOMP đã có | chốt |
| Q7 | Thao tác người dùng: danh sách, đếm chưa đọc, đọc 1 / tất cả, xoá | chốt |
| Q8 | Thông báo cho Admin (cảnh báo địa điểm, tranh chấp mới) | chốt |
| Q9 | Thông báo tin nhắn chat mới | chốt |
| Q10 | Đăng ký device token (FCM sau) — làm bây giờ hay để sau | chốt |

## Đã chốt
- Q1 (2026-11-16): gửi 11 loại SRS + mọi loại design đã ghi người nghe NOTIFICATION-001, **kể cả tuỳ chọn** (rút yêu cầu `PENDING` → báo đội tạo kèo; mở tranh chấp → báo đội đã report). Gồm: hết hạn kèo, yêu cầu/pairing bị thay thế/hết hạn, quyết định tranh chấp, huy hiệu đội + cá nhân, mercenary duyệt/từ chối/rút/đóng tin/huỷ trận/rating/report, chào mừng, cảnh báo địa điểm. → V-mới đổi CHECK `notification.type`; SRS cập nhật danh sách loại.
- Q2 (2026-11-16): sự kiện nhắm vào đội → chỉ **đội trưởng tại lúc tạo thông báo** nhận (listener `@Async` chạy, có thể vài giây sau sự kiện — không phải đúng lúc sự kiện; chuyển quyền trong khoảng đó thì đội trưởng mới nhận; event nguồn không đổi — làm rõ sau review, user chọn M1(a) 2026-11-16, đóng Processing_Flow `[cần xác nhận #11]`); chuyển quyền đội trưởng không chuyển thông báo cũ (thông báo ở lại người cũ, đội trưởng mới nhận từ lúc nhận quyền).
- Q3 (2026-11-16): không lộ BC-R04. "Trận đã xong (nhắc đánh giá)" = loại `MATCH_COMPLETED` gửi **lúc kèo ARCHIVED** (`MatchArchivedEvent`) cho cả hai đội trưởng; chuyển ARCHIVED → COMPLETED không gửi gì. "Nhận đánh giá" chỉ gửi khi người nhận **đã đánh giá trước** (bên kia đánh giá sau); người chưa đánh giá không nhận. Áp tương tự đánh giá đội ↔ mercenary (BC-R03/BR-ME13). Đóng REPUTATION-001 `[cần xác nhận #11]`.
- Q4 (2026-11-16): nhắc trước trận **một lần, 2 giờ trước giờ bắt đầu** (job 5 phút → trễ tối đa ~5 phút); người nhận: đội trưởng hiện tại hai đội của kèo `CONFIRMED` + mọi mercenary `APPROVED` của trận (kể cả trận ngoài hệ thống `SCHEDULED`). Kèo ghép khi đã còn < 2 giờ → không nhắc. Cần đánh dấu đã nhắc để không trùng.
- Q5 (2026-11-16): server soạn sẵn `title`/`body` tiếng Việt lúc tạo (ảnh chụp tại thời điểm — đổi tên đội sau không cập nhật); nội dung chỉ có tên đội / sân / giờ / vị trí, **không tên cá nhân** → không cần che user đã xoá. Dùng `reference_type` + `reference_id` để FE mở màn hình. Giữ schema V1 (ngoài CHECK `type` — Q1).
- Q6 (2026-11-16): app đang mở nhận thông báo mới ngay qua kênh STOMP đã có — thêm kênh riêng mỗi người (vd `/user/queue/notifications`, frame `NOTIFICATION_CREATED`); kết nối lại → FE gọi API đếm chưa đọc để bù. Push điện thoại khi app đóng vẫn để sau (`PushSender` no-op — đã giải thích cho user).
- Q7 (2026-11-16): 4 API — danh sách của tôi (mới nhất trước, lọc "chỉ chưa đọc"), số chưa đọc, đánh dấu 1 đã đọc, đánh dấu tất cả đã đọc. Không xoá (tự xoá sau 30 ngày). Kiểu phân trang: solution-architect chọn theo `api-convention.md` §7.
- Q8 (2026-11-16): Admin nhận (mỗi admin đang hoạt động 1 dòng, đọc riêng): cảnh báo địa điểm `VenueAdminAlertEvent` (1 lần — BC-V05) + mỗi tranh chấp mới (đội `ReportDisputedEvent` + mercenary). **Không** báo từng `VenueReportedEvent` (đóng VENUE-001 Processing_Flow #1: không gộp, chỉ báo alert). Tranh chấp mới: vừa báo admin vừa báo đội đã report (Q1).
- Q9 (2026-11-16): tin nhắn chat **không** tạo dòng `notification`; mỗi tin mới đẩy frame nhẹ (vd `CONVERSATION_ACTIVITY` + `conversationId`) qua kênh riêng mỗi người (Q6) tới những người xem được cuộc trò chuyện (trừ người gửi) để chấm đỏ "Tin nhắn" cập nhật. Push điện thoại cho tin nhắn: quyết khi làm FCM. → ảnh hưởng MATCH-001 §1.7 (thêm frame trên kênh người dùng).
- Q10 (2026-11-16): không làm bảng / API thiết bị (device token) ở NOTIFICATION-001; thiết kế trọn gói cùng lúc tích hợp FCM.
- Q11 (2026-11-16, sau thiết kế v1.0 — `[cần xác nhận #2]`): thêm cả 4 loại — (a) lời mời vào đội được chấp nhận / từ chối → đội trưởng (UC-T03); (b) chuyển quyền đội trưởng → chỉ đội trưởng mới (Q13) (UC-T04, AC-T03); (c) Admin tạm ngưng / kích hoạt lại đội → đội trưởng (UC-T05); (d) nhắc mercenary APPROVED đánh giá sau trận (kèo: `MatchArchivedEvent`; trận ngoài: nhánh job 5 phút). Thêm vào V10.
- Q12 (2026-11-16, `[cần xác nhận #3]`): trận ngoài hệ thống giữ như thiết kế — chỉ nhắc mercenary `APPROVED` (không nhắc đội trưởng đội khai báo); trận khai báo khi còn < 2 giờ → không nhắc; mercenary duyệt sau lần nhắc → không nhắc bù (1 lần / trận, như kèo).
- Q13 (2026-11-16): chuyển quyền đội trưởng → chỉ đội trưởng mới nhận; đội trưởng cũ (người thao tác) không nhận.
