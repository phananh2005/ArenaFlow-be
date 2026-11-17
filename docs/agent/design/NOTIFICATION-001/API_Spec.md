# API Specification — Thông báo in-app & tín hiệu realtime cá nhân

<!-- File này được publish cho FE (ADR-0010). Viết theo design-standards.md §11:
     không tên class/bảng/cột/migration/transaction/khoá/số truy vấn; không trỏ Component_Class_Design.md,
     Processing_Flow.md, docs/database/. Chi tiết nội bộ BE ghi ở hai file đó. -->

| Mục | Giá trị |
|---|---|
| Mã chức năng | `NOTIFICATION-001` |
| Phạm vi tầng | Backend (Spring Boot REST API + kênh STOMP/WebSocket của MATCH-001). Frontend: ngoài phạm vi repo này |
| Repo | ArenaFlow-be |
| Quy ước áp dụng | `api-convention.md` (envelope, phân trang con trỏ §7.2, định dạng lỗi, kiểu dữ liệu); quy ước chung MATCH-001 `API_Spec.md` §1.1; kết nối realtime (`/ws`, `CONNECT`, heartbeat, lỗi kết nối): MATCH-001 `API_Spec.md` §1.7; mã `USER_*`, `AUTH_*`: USER-001 `API_Spec.md`; [ADR-0014](../../decisions/0014-realtime-chat-stomp-websocket.md) (kênh realtime), [ADR-0015](../../decisions/0015-notification-async-listener-and-user-channel.md) |
| Spec nghiệp vụ | `docs/agent/business-spec/SYSTEM-001/` (v1.10) — UC-N02, UC-N03; BR-N01, BR-N02; BC-N01..BC-N03; Overview §4 (loại thông báo); Business_Rules §8.6; AC "NOTIFICATION DOMAIN", AC-N02, AC-U01, AC-T02, AC-T03, UC-T03, UC-T04, UC-T05, AC-M03..AC-M09, AC-R02, AC-R06, AC-ME03, AC-ME05, AC-ME06, AC-ME11, AC-V03 · phạm vi user chốt 2026-11-16 (Q1–Q13) — SRS SYSTEM-001 sẽ cập nhật theo phạm vi này |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-16 · v1.0 (baseline) · hiện tại **v1.1** (solution-architect · 2026-11-18) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan

Hệ thống tự tạo **thông báo in-app** khi có sự kiện liên quan tới người dùng (yêu cầu ghép kèo, kèo được chốt / bị huỷ / hết hạn, nhắc trước trận, nhắc đánh giá, report, tranh chấp, huy hiệu, ứng tuyển mercenary, lời mời vào đội, chào mừng, cảnh báo cho Admin…). Người dùng xem danh sách thông báo của mình (mới nhất trước), xem số chưa đọc (chấm đỏ / badge), đánh dấu một hoặc tất cả là đã đọc. Thông báo mới và tín hiệu "có tin nhắn mới" được đẩy ngay qua **kênh realtime cá nhân** khi app đang mở (§1.4).

**Ngoài phạm vi:** push điện thoại khi app đóng (FCM/APNs) và đăng ký thiết bị — làm sau (§1.5) · xoá thông báo thủ công (thông báo tự xoá sau 30 ngày) · cài đặt tắt / bật từng loại thông báo · thông báo cho tin nhắn chat (không tạo thông báo — chỉ có tín hiệu `CONVERSATION_ACTIVITY`, §1.4) · nội dung thông báo theo ngôn ngữ khác tiếng Việt.

### 1.1. Quy ước chung

- Như MATCH-001 `API_Spec.md` §1.1: Bearer token (`401 AUTH_001` → refresh token rồi gửi lại), envelope `data` / `error`, thời gian ISO-8601 trả UTC, enum UPPERCASE, `400 VALIDATION_ERROR` kèm `validationErrors[]`.
- Mọi endpoint chỉ thao tác trên **thông báo của chính người gọi**; không có tham số chọn người dùng khác. Thông báo của người khác (hoặc id không tồn tại) → `404 NOTIFICATION_001`.
- Tài khoản đã xoá (token còn hạn) → `404 USER_001` ở mọi endpoint.
- **Hạn lưu 30 ngày** (BR-N02): thông báo tạo quá 30 ngày không còn xuất hiện ở mọi endpoint (kể cả khi chưa đọc) và bị xoá hẳn hằng ngày.
- **Nội dung soạn sẵn** (`title`, `body` tiếng Việt) tại lúc tạo — là ảnh chụp tại thời điểm đó: đội đổi tên, kèo đổi giờ sau này **không** cập nhật thông báo cũ. FE hiển thị nguyên văn, **không** tự ghép câu theo `type`. Nội dung chỉ có tên đội, tên sân, giờ, vị trí, loại report — **không** có tên cá nhân.
- **Mở màn hình liên quan:** dùng `referenceType` + `referenceId` (§4.3). `type` dùng để chọn biểu tượng / màu.
- **Loại thông báo có thể tăng:** FE MUST hiển thị được thông báo có `type` / `referenceType` chưa biết (biểu tượng mặc định, không mở màn hình nào) — thêm loại mới là thay đổi không phá vỡ.

### 1.2. Ai nhận

| Sự kiện nhắm vào | Người nhận |
|---|---|
| Một **đội** (vd kèo của đội bị huỷ) | **Đội trưởng của đội tại lúc hệ thống tạo thông báo** (có thể vài giây sau sự kiện) — **không** phải đội trưởng đúng lúc sự kiện xảy ra: đội chuyển quyền đội trưởng trong khoảng đó thì đội trưởng mới nhận. Thành viên thường không nhận. Đội đã giải tán (không còn đội trưởng) → không ai nhận |
| Một **người dùng** (ứng viên mercenary, người được mời, người bị report cá nhân…) | Chính người đó |
| **Admin** (§4.2 `VENUE_ADMIN_ALERT`, `DISPUTE_CREATED`) | **Mỗi** Admin đang hoạt động nhận một thông báo riêng, đọc riêng |

- Chuyển quyền đội trưởng: thông báo đã tạo **ở lại** người nhận cũ; đội trưởng mới chỉ nhận thông báo tạo từ lúc nhận quyền (user chốt 2026-11-16). Thông báo về chính việc chuyển quyền (`CAPTAIN_TRANSFERRED`) chỉ gửi **đội trưởng mới**.
- Người dùng đã xoá tài khoản không nhận thông báo mới.
- Một người là đội trưởng của cả hai đội trong cùng sự kiện (vd tự ghép hai đội của mình) chỉ nhận **một** thông báo cho mỗi loại.

### 1.3. Lúc nào thông báo được tạo

| Nguồn | Độ trễ |
|---|---|
| Thao tác của người dùng (gửi / chấp nhận / từ chối yêu cầu, huỷ kèo, ứng tuyển, duyệt, đánh giá, report, tranh chấp, mời vào đội, chấp nhận / từ chối lời mời, chuyển quyền đội trưởng, Admin tạm ngưng / kích hoạt lại đội, đăng ký…) | Vài giây sau khi thao tác thành công. Thao tác thất bại không tạo thông báo |
| Hệ thống tự chuyển trạng thái (kèo hết hạn, kèo kết thúc, tin tuyển tự đóng, báo mercenary khi kèo bị huỷ, cảnh báo địa điểm, huy hiệu đối soát hằng ngày) | Theo chu kỳ của tác vụ đó (phần lớn ≤ ~5 phút; huy hiệu, cảnh báo 180 ngày: hằng ngày) |
| Nhắc trước trận (`REMINDER_BEFORE_MATCH`) | Một lần, **khoảng 2 giờ** trước giờ bắt đầu (trễ tối đa ~5 phút). Kèo được chốt / trận ngoài hệ thống được khai báo khi còn < 2 giờ → không nhắc. Mercenary được duyệt sau lần nhắc → không nhắc bù |
| Nhắc mercenary đánh giá (`MERCENARY_RATING_REMINDER`) | Kèo: cùng lúc kèo chuyển "đã kết thúc" (≤ ~5 phút sau giờ kết thúc). Trận ngoài hệ thống: ≤ ~5 phút sau giờ kết thúc; trận đã kết thúc quá 24 giờ mà chưa được nhắc (hệ thống gián đoạn) → không nhắc |

Không bảo đảm tuyệt đối mọi thông báo được tạo (sự cố máy chủ đúng lúc vừa thao tác có thể làm mất thông báo của thao tác đó); trạng thái thật luôn đọc từ API của chức năng tương ứng.

### 1.4. Kênh realtime cá nhân

Dùng chung kết nối STOMP của MATCH-001 `API_Spec.md` §1.7 (`/ws`, `CONNECT` kèm Bearer token, heartbeat, lỗi `AUTH_001`). Sau `CONNECT`, FE `SUBSCRIBE` **đúng** destination:

```
/user/queue/notifications
```

- Destination cố định cho mọi người dùng — server tự giao frame của **người đang kết nối**; không chứa id người dùng. Destination khác mẫu (vd `/queue/notifications`, `/user/queue/**`, `/user/{id}/queue/notifications`) → frame `ERROR` rồi đóng kết nối.
- Đăng ký một lần cho cả phiên (không phụ thuộc màn hình). Vẫn `SUBSCRIBE` thêm `/topic/conversations/{id}` khi mở phòng chat (MATCH-001 §1.7).
- Không bảo đảm giao frame. **Sau mỗi lần kết nối lại:** `SUBSCRIBE /user/queue/notifications` trước, rồi gọi 2.2 (số chưa đọc) và — nếu đang ở màn hình thông báo — tải lại 2.1 trang đầu; màn hình "Tin nhắn" tải lại MATCH-001 2.20.
- Frame chỉ đến người đang kết nối với **tài khoản nhận**; nhiều thiết bị cùng tài khoản đều nhận. Đánh dấu đã đọc (2.3, 2.4) **không** đẩy frame sang thiết bị khác — thiết bị khác cập nhật khi gọi lại 2.2 (vd khi app trở lại foreground).

**Frame server gửi** (body JSON, phân biệt bằng `type`):

| `type` | Khi nào | Nội dung | FE nên làm gì |
|---|---|---|---|
| `NOTIFICATION_CREATED` | Có thông báo mới cho người nhận | `notification`: object Thông báo (§4.1), `isRead = false` | Tăng badge chưa đọc 1; màn hình thông báo đang mở → chèn lên đầu (bỏ trùng theo `notificationId`) |
| `CONVERSATION_ACTIVITY` | Có tin nhắn mới trong một cuộc trò chuyện người nhận **xem được** (chat hai đội, chat theo đơn ứng tuyển; gồm cuộc trò chuyện chỉ xem), **trừ** người gửi tin | `conversationId`, `messageId`, `createdAt` (thời điểm gửi tin) | Bật chấm đỏ "Tin nhắn"; màn hình "Tin nhắn" đang mở → tải lại MATCH-001 2.20 hoặc đưa cuộc trò chuyện lên đầu. Không chứa nội dung tin — nội dung lấy qua MATCH-001 2.17 hoặc frame `MESSAGE_CREATED` của topic cuộc trò chuyện |

```json
{ "type": "NOTIFICATION_CREATED",
  "notification": { "notificationId": "019340a1-7b2c-7d3e-8f40-5a6b7c8d9e01", "type": "MATCH_REQUEST_RECEIVED",
                    "title": "Yêu cầu ghép kèo mới",
                    "body": "Đội FC Sấm Sét muốn ghép kèo 18:00 20/11 tại Sân bóng Phú Thọ.",
                    "referenceType": "MATCH", "referenceId": "01933f10-2a3b-7c4d-8e5f-6a7b8c9d0e11",
                    "isRead": false, "createdAt": "2026-11-16T08:00:03Z" } }
```
```json
{ "type": "CONVERSATION_ACTIVITY", "conversationId": "01933d41-1a2b-7c3d-8e4f-5a6b7c8d9e31",
  "messageId": "01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e01", "createdAt": "2026-11-16T08:05:00Z" }
```

- Tin nhắn chat **không** tạo thông báo (không có trong 2.1, không tăng số chưa đọc ở 2.2). Trạng thái "chưa đọc" của chat: `hasUnread` ở MATCH-001 2.20.
- Thông báo đẩy điện thoại khi app đóng: chưa có (§1.5).

### 1.5. Push điện thoại

Chưa hỗ trợ ở bản này: chưa có API đăng ký thiết bị, app đóng không nhận thông báo. Khi tích hợp FCM/APNs sẽ có thay đổi bổ sung (endpoint đăng ký thiết bị, phạm vi loại được push) công bố qua `CHANGELOG.md` của mã này.

## 2. Endpoints

| # | Method + Path | Dùng khi | Ai gọi được |
|---|---|---|---|
| 2.1 | `GET /api/v1/profile/notifications` | Màn hình "Thông báo" (cuộn xuống tải thêm; lọc "Chưa đọc") | Người dùng đã đăng nhập (thông báo của mình) |
| 2.2 | `GET /api/v1/profile/notifications/unread-count` | Badge chuông thông báo: mở app, kết nối realtime lại, app trở lại foreground | Như trên |
| 2.3 | `POST /api/v1/profile/notifications/{notificationId}/read` | Người dùng bấm vào một thông báo | Như trên |
| 2.4 | `POST /api/v1/profile/notifications/read` | Nút "Đánh dấu tất cả đã đọc" | Như trên |

**Mã thành công & tiêu chí `api-convention.md` §5:** 2.1, 2.2 → `200` (B1); 2.3, 2.4 → `204` (N1 — kết quả suy ra từ việc request thành công: thông báo đã đọc, số chưa đọc giảm / về 0).

### 2.1. Danh sách thông báo của tôi — `GET /api/v1/profile/notifications`

**Dùng khi:** mở màn hình "Thông báo"; cuộn xuống để tải thông báo cũ hơn; bật bộ lọc "Chỉ chưa đọc".
**Ai gọi được:** người dùng đã đăng nhập; chỉ thấy thông báo của mình trong 30 ngày gần nhất.

Phân trang **con trỏ** (`api-convention.md` §7.2): danh sách liên tục có phần tử mới ở đầu nên phân trang theo trang (`page`) bị trôi (lặp / sót khi có thông báo mới giữa hai lần tải).

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `unreadOnly` | boolean | Không | Mặc định `false`. `true` → chỉ thông báo chưa đọc | `true` |
| `before` | UUID | Không | `notificationId` mốc — lấy thông báo **cũ hơn** mốc. Không gửi cùng `after` | `"019340a1-7b2c-7d3e-8f40-5a6b7c8d9e01"` |
| `after` | UUID | Không | `notificationId` mốc — lấy thông báo **mới hơn** mốc. Không gửi cùng `before` | `"019340a1-…"` |
| `limit` | integer | Không | 1..100, mặc định 50 | `20` |

| Gửi | Trả | Thứ tự `items` | `hasMore = true` khi |
|---|---|---|---|
| Không con trỏ | `limit` thông báo mới nhất | Mới nhất trước | Còn thông báo cũ hơn |
| `before` | `limit` thông báo ngay trước mốc | Mới nhất trước | Còn thông báo cũ hơn |
| `after` | `limit` thông báo ngay sau mốc | **Cũ nhất trước** | Còn thông báo mới hơn |

- Thứ tự theo `createdAt` rồi `notificationId`. Thông báo mốc không có trong kết quả. Mốc phải là thông báo **của người gọi** còn trong 30 ngày (mốc đã đọc vẫn dùng được khi `unreadOnly = true`).
- Cuộn tải thêm: `before` = `notificationId` cuối cùng đang hiển thị. Không cần `after` khi đã nhận frame `NOTIFICATION_CREATED`; dùng `after` để bù khi nghi lỡ frame (hoặc tải lại trang đầu).
- Không có `pagination` / tổng số; số chưa đọc lấy ở 2.2.

**Response 200**

```json
{ "data": {
    "items": [
      { "notificationId": "019340a1-7b2c-7d3e-8f40-5a6b7c8d9e01", "type": "REQUEST_ACCEPTED",
        "title": "Yêu cầu ghép kèo được chấp nhận",
        "body": "Đội FC Sấm Sét đã chấp nhận yêu cầu ghép kèo 18:00 20/11 tại Sân bóng Phú Thọ.",
        "referenceType": "MATCH", "referenceId": "01933f10-2a3b-7c4d-8e5f-6a7b8c9d0e11",
        "isRead": false, "createdAt": "2026-11-16T08:00:03Z" },
      { "notificationId": "01933fe0-1a2b-7c3d-8e4f-5a6b7c8d9e02", "type": "WELCOME",
        "title": "Chào mừng bạn đến với ArenaFlow",
        "body": "Hoàn thiện hồ sơ để bắt đầu tìm đội và tìm kèo.",
        "referenceType": "USER", "referenceId": "01932e00-5a6b-7c7d-8e8f-9a0b1c2d3e12",
        "isRead": true, "createdAt": "2026-11-10T02:00:00Z" }
    ],
    "hasMore": false } }
```

**Lỗi** (ngoài `401 AUTH_001`)

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Gửi cả `before` và `after`; `limit` ngoài 1..100; id / boolean sai định dạng | Lỗi lập trình — sửa tham số |
| 404 | `USER_001` | Tài khoản đã bị xoá (token còn hạn) | Đăng xuất |
| 422 | `NOTIFICATION_002` | Mốc `before` / `after` không phải thông báo của người gọi hoặc đã quá 30 ngày | Tải lại trang đầu (không con trỏ) |

### 2.2. Số thông báo chưa đọc — `GET /api/v1/profile/notifications/unread-count`

**Dùng khi:** hiển thị badge chuông: lúc mở app, sau khi kết nối realtime lại, khi app trở lại foreground, sau khi đánh dấu đọc trên thiết bị khác.
**Ai gọi được:** người dùng đã đăng nhập.

**Response 200** — `unreadCount` = số thông báo chưa đọc của người gọi trong 30 ngày gần nhất (số chính xác, không giới hạn; FE tự hiện "99+" nếu muốn). Không tính tin nhắn chat.

```json
{ "data": { "unreadCount": 3 } }
```

**Lỗi:** `404 USER_001` (như 2.1).

### 2.3. Đánh dấu một thông báo đã đọc — `POST /api/v1/profile/notifications/{notificationId}/read`

**Dùng khi:** người dùng bấm vào một thông báo (trước hoặc cùng lúc mở màn hình theo `referenceType`).
**Ai gọi được:** người nhận thông báo.

**Path params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `notificationId` | UUID | Có | Thông báo của người gọi | `"019340a1-7b2c-7d3e-8f40-5a6b7c8d9e01"` |

Không có body. **Idempotent:** thông báo đã đọc → vẫn `204`, không đổi gì.

**Response 204** — không có body. FE đặt `isRead = true` và giảm badge 1 (nếu trước đó chưa đọc).

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `notificationId` không đúng định dạng UUID | Lỗi lập trình — dùng đúng `notificationId` từ 2.1 / frame `NOTIFICATION_CREATED` |
| 404 | `NOTIFICATION_001` | Không có thông báo này trong thông báo của người gọi (id sai, của người khác, hoặc đã quá 30 ngày / đã bị xoá) | Bỏ thông báo khỏi danh sách đang hiển thị; vẫn mở màn hình liên quan nếu có `referenceId` |
| 404 | `USER_001` | Tài khoản đã bị xoá | Đăng xuất |

### 2.4. Đánh dấu tất cả đã đọc — `POST /api/v1/profile/notifications/read`

**Dùng khi:** người dùng bấm "Đánh dấu tất cả đã đọc".
**Ai gọi được:** người dùng đã đăng nhập.

Không có body. Mọi thông báo chưa đọc của người gọi **tồn tại tại lúc server xử lý** thành đã đọc — kể cả thông báo vừa tạo mà màn hình chưa kịp hiển thị (frame `NOTIFICATION_CREATED` đến sau request này mô tả thông báo **mới hơn**, vẫn chưa đọc). Không có thông báo chưa đọc → vẫn `204`.

**Response 204** — không có body. FE đặt mọi thông báo đang hiển thị `isRead = true`, badge = 0.

**Lỗi:** `404 USER_001` (như 2.1).

---

## 3. Hành động theo trạng thái

| `isRead` | Người nhận |
|---|---|
| `false` | Mở màn hình liên quan (§4.3) + đánh dấu đã đọc (2.3); "Đánh dấu tất cả đã đọc" (2.4) |
| `true` | Mở màn hình liên quan (§4.3). Không có thao tác "đánh dấu chưa đọc" / xoá |

## 4. Kiểu dữ liệu dùng chung

### 4.1. Thông báo — phần tử 2.1, frame `NOTIFICATION_CREATED`

| Field | Kiểu | Null | Ý nghĩa |
|---|---|---|---|
| `notificationId` | UUID | Không | Id thông báo |
| `type` | enum (§4.2) | Không | Loại thông báo — chọn biểu tượng |
| `title` | string ≤ 200 | Không | Tiêu đề soạn sẵn (tiếng Việt) |
| `body` | string | Không | Nội dung soạn sẵn (tiếng Việt), hiển thị nguyên văn |
| `referenceType` | enum (§4.3) | Có | Loại màn hình liên quan; `null` = không mở màn hình nào |
| `referenceId` | UUID | Có | Id đối tượng; `null` khi và chỉ khi `referenceType = null` |
| `isRead` | boolean | Không | Đã đọc |
| `createdAt` | string (ISO-8601, UTC) | Không | Thời điểm tạo thông báo |

### 4.2. Loại thông báo (`type`)

"Đội trưởng" = đội trưởng hiện tại của đội tại lúc tạo thông báo (§1.2).

| `type` | Khi nào | Người nhận | `referenceType` |
|---|---|---|---|
| `WELCOME` | Đăng ký tài khoản mới (AC-U01) | Người vừa đăng ký | `USER` (chính mình) |
| `TEAM_INVITATION` | Đội trưởng mời vào đội (USER-001 2.12) | Người được mời | `TEAM_INVITATION` |
| `TEAM_INVITATION_ACCEPTED` | Người được mời chấp nhận lời mời (USER-001 2.13) | Đội trưởng đội | `TEAM` |
| `TEAM_INVITATION_REJECTED` | Người được mời từ chối lời mời (USER-001 2.14) | Đội trưởng đội | `TEAM` |
| `CAPTAIN_TRANSFERRED` | Đội trưởng chuyển quyền (USER-001 2.15) | **Chỉ** đội trưởng mới (đội trưởng cũ — người thao tác — không nhận) | `TEAM` |
| `TEAM_MARKED_INACTIVE` | Admin tạm ngưng đội (USER-001 2.19) | Đội trưởng đội | `TEAM` |
| `TEAM_REACTIVATED` | Admin kích hoạt lại đội (USER-001 2.20) | Đội trưởng đội | `TEAM` |
| `MATCH_REQUEST_RECEIVED` | Đội khác gửi yêu cầu ghép kèo luồng A (MATCH-001 2.6) | Đội trưởng đội tạo kèo | `MATCH` |
| `REQUEST_WITHDRAWN` | Đội gửi rút yêu cầu **đang chờ** (MATCH-001 2.10 `PENDING`) | Đội trưởng đội tạo kèo | `MATCH` |
| `REQUEST_ACCEPTED` | Yêu cầu được chấp nhận — kèo luồng A đã ghép (MATCH-001 2.8) | Đội trưởng đội được chấp nhận | `MATCH` |
| `REQUEST_REJECTED` | Yêu cầu bị từ chối (MATCH-001 2.9) | Đội trưởng đội gửi | `MATCH` |
| `REQUEST_SUPERSEDED` | Kèo luồng A ghép với đội khác trong khi yêu cầu của đội còn chờ | Đội trưởng mỗi đội có yêu cầu bị thay thế | `MATCH` |
| `REQUEST_EXPIRED` | Kèo luồng A hết hạn khi yêu cầu của đội còn chờ (AC-M07) | Đội trưởng mỗi đội có yêu cầu hết hạn | `MATCH` |
| `PAIRING_NEW` | Đội khác nhận kèo luồng B — mở thương lượng (MATCH-002 2.1) | Đội trưởng đội tạo kèo | `PAIRING` |
| `PAIRING_SUPERSEDED` | Kèo luồng B ghép với đội khác trong khi thương lượng của đội còn mở (AC-M05) | Đội trưởng mỗi đội có thương lượng bị thay thế | `MATCH` |
| `PAIRING_EXPIRED` | Kèo luồng B hết hạn khi thương lượng của đội còn mở (AC-M07) | Đội trưởng mỗi đội có thương lượng hết hạn | `MATCH` |
| `MATCH_CONFIRMED` | Thương lượng luồng B hoàn tất — kèo đã ghép (MATCH-002 2.7) | Đội trưởng **cả hai** đội | `MATCH` |
| `MATCH_CANCELLED` | Kèo đã ghép bị huỷ / đội đã ghép rút yêu cầu (MATCH-001 2.5, 2.10) → đội còn lại; kèo luồng B **chưa ghép** bị huỷ → các đội đang thương lượng | Đội trưởng đội còn lại / đội trưởng mỗi đội đang thương lượng | `MATCH` |
| `MATCH_EXPIRED` | Kèo hết hạn mà chưa ghép được (AC-M07) | Đội trưởng đội tạo kèo | `MATCH` |
| `REMINDER_BEFORE_MATCH` | Khoảng 2 giờ trước giờ bắt đầu kèo đã ghép / trận ngoài hệ thống (§1.3) | Đội trưởng hai đội của kèo; mọi mercenary đã được duyệt của trận (kèo hoặc trận ngoài hệ thống) | `MATCH` (đội trưởng) · `MERCENARY_APPLICATION` (mercenary) |
| `MATCH_COMPLETED` | Kèo đã ghép tới giờ kết thúc — nhắc nhập tỷ số và đánh giá (AC-M08) | Đội trưởng hai đội | `MATCH` |
| `MERCENARY_RATING_REMINDER` | Trận (kèo hoặc trận ngoài hệ thống) của đơn đã duyệt kết thúc — nhắc mercenary đánh giá đội thuê (§1.3) | Mercenary đã được duyệt | `MERCENARY_APPLICATION` |
| `RATING_RECEIVED` | Bên kia gửi đánh giá **sau khi** người nhận đã đánh giá họ (giữa hai đội — REPUTATION-001 2.1; giữa đội và mercenary — MERCENARY-001 2.18). Người nhận **chưa** đánh giá → không có thông báo (BC-R04) | Đội trưởng đội được đánh giá / mercenary được đánh giá | `MATCH` (giữa hai đội) · `MERCENARY_APPLICATION` (đội ↔ mercenary) |
| `REPORT_RECEIVED` | Bị report (REPUTATION-001 2.3, 2.10; MERCENARY-001 2.20). Report bùng: nội dung nhắc hạn 24 giờ tranh chấp | Đội trưởng đội bị report / người bị report | `REPORT` |
| `REPORT_DISPUTED` | Bên bị report bùng gửi tranh chấp (REPUTATION-001 2.5) | Đội trưởng đội đã report / người đã report | `REPORT` |
| `DISPUTE_RESOLVED` | Admin quyết định tranh chấp (REPUTATION-001 2.8; MERCENARY-001 2.27) | Hai bên: bên report và bên bị report | `REPORT` |
| `VERIFICATION_BADGE_GRANTED` | Được cấp huy hiệu xác minh (AC-R06) | Đội trưởng đội / người dùng được cấp | `TEAM` · `USER` |
| `VERIFICATION_BADGE_REVOKED` | Bị thu hồi huy hiệu xác minh (AC-R06) | Đội trưởng đội / người dùng bị thu hồi | `TEAM` · `USER` |
| `MERCENARY_APPLICATION` | Có người ứng tuyển tin tuyển của đội (MERCENARY-001 2.7) | Đội trưởng đội tuyển | `MERCENARY_APPLICATION` |
| `MERCENARY_APPLICATION_APPROVED` | Đơn được duyệt (MERCENARY-001 2.11) | Ứng viên | `MERCENARY_APPLICATION` |
| `MERCENARY_APPLICATION_REJECTED` | Đơn bị từ chối: đội trưởng từ chối (2.12), hoặc tin tuyển đóng / hết hạn / trận bị huỷ khi đơn còn chờ (BR-ME07, BR-ME09) | Ứng viên | `MERCENARY_APPLICATION` |
| `MERCENARY_WITHDRAWN` | Mercenary **đã được duyệt** rút lui (MERCENARY-001 2.13); ứng viên xoá tài khoản (mọi đơn đang chờ / đã duyệt bị rút — BR-ME18) | Đội trưởng đội tuyển | `MERCENARY_APPLICATION` |
| `MERCENARY_GAME_CANCELLED` | Trận của đơn đã duyệt bị huỷ (kèo bị huỷ, hoặc trận ngoài hệ thống bị huỷ — BR-ME09) | Mercenary đã được duyệt | `MERCENARY_APPLICATION` |
| `VENUE_ADMIN_ALERT` | Địa điểm cần Admin kiểm tra: bị report "đã đóng cửa", hoặc 180 ngày không được chọn (BC-V05) — một lần mỗi đợt cảnh báo, **không** báo từng report | Mỗi Admin | `VENUE` |
| `DISPUTE_CREATED` | Có tranh chấp report bùng mới cần xử lý (giữa hai đội hoặc liên quan mercenary) | Mỗi Admin | `DISPUTE` (giữa hai đội) · `MERCENARY_DISPUTE` (liên quan mercenary) |

Không có thông báo cho: đội trưởng cũ khi chuyển quyền; lý do Admin tạm ngưng đội (không đưa vào nội dung — xem chi tiết đội); ứng viên tự rút đơn **đang chờ**; kèo chuyển từ "đã kết thúc" sang "đã hoàn tất" (sau đánh giá); từng report địa điểm; tin nhắn chat (§1.4).

### 4.3. Màn hình liên quan (`referenceType` → `referenceId`)

| `referenceType` | `referenceId` là | Màn hình FE mở (API dữ liệu) |
|---|---|---|
| `MATCH` | Id kèo | Chi tiết kèo — MATCH-001 2.3 (đội tạo kèo: thêm yêu cầu 2.7 / thương lượng MATCH-002 2.2; sau trận: đánh giá REPUTATION-001 2.2) |
| `PAIRING` | Id thương lượng | Chi tiết thương lượng — MATCH-002 2.4 |
| `TEAM` | Id đội | Chi tiết đội / uy tín đội — USER-001 2.9, REPUTATION-001 2.9 |
| `TEAM_INVITATION` | Id lời mời | Lời mời vào đội — chấp nhận / từ chối USER-001 2.13, 2.14 |
| `USER` | Id người dùng (chính người nhận) | Hồ sơ của tôi / uy tín cá nhân — USER-001 2.5, MERCENARY-001 2.23 |
| `VENUE` | Id địa điểm | Chi tiết địa điểm (Admin) — VENUE-001 2.3, report 2.8 |
| `REPORT` | Id report | Danh sách report — đội: REPUTATION-001 2.4 (report giữa hai đội) hoặc MERCENARY-001 2.21; cá nhân: MERCENARY-001 2.22. Tranh chấp: REPUTATION-001 2.5 |
| `DISPUTE` | Id tranh chấp | Admin: chi tiết tranh chấp — REPUTATION-001 2.7 |
| `MERCENARY_DISPUTE` | Id tranh chấp | Admin: chi tiết tranh chấp mercenary — MERCENARY-001 2.26 |
| `MERCENARY_APPLICATION` | Id đơn ứng tuyển | Chi tiết đơn — MERCENARY-001 2.10 (đội trưởng: thêm ứng viên của tin 2.8) |

Đối tượng có thể đã đổi trạng thái hoặc người dùng không còn quyền xem (vd đã chuyển quyền đội trưởng) — FE xử lý lỗi của API đích như bình thường.

## 5. Mã lỗi

| Mã | HTTP | Message | Endpoint |
|---|---|---|---|
| `NOTIFICATION_001` | 404 | Thông báo không tồn tại | 2.3 |
| `NOTIFICATION_002` | 422 | Thông báo mốc không hợp lệ, vui lòng tải lại | 2.1 |
| `USER_001` | 404 | Người dùng không tồn tại (USER-001) | 2.1–2.4 |
| `VALIDATION_ERROR` | 400 | Dữ liệu không hợp lệ | 2.1, 2.3 |

## 6. Danh sách [cần xác nhận]

Không có — các điểm mở về loại thông báo và nhắc trận ngoài hệ thống đã được user chốt 2026-11-16 (đã phản ánh ở §1.3, §4.2).
