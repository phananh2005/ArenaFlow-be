# API Specification — Tuyển mercenary, ứng tuyển, chat theo đơn, uy tín cá nhân

<!-- File này được publish cho FE (ADR-0010). Viết theo design-standards.md §11:
     không tên class/bảng/cột/migration/transaction/khoá/số truy vấn; không trỏ Component_Class_Design.md,
     Processing_Flow.md, docs/database/. Chi tiết nội bộ BE ghi ở hai file đó. -->

| Mục | Giá trị |
|---|---|
| Mã chức năng | `MERCENARY-001` |
| Phạm vi tầng | Backend (Spring Boot REST API + kênh STOMP/WebSocket của MATCH-001). Frontend: ngoài phạm vi repo này |
| Repo | ArenaFlow-be |
| Quy ước áp dụng | `api-convention.md` (envelope, phân trang offset / con trỏ, định dạng lỗi, kiểu dữ liệu); quy ước chung USER-001 `API_Spec.md` §1.1, §1.2 và MATCH-001 `API_Spec.md` §1.1; kèo, trạng thái kèo, mã `MATCH_*`: MATCH-001 `API_Spec.md`; cuộc trò chuyện (2.14–2.17 = MATCH-001 2.19, 2.16, 2.17, 2.18), kênh realtime, mã `CHAT_*`: MATCH-001 `API_Spec.md` §1.7, §4.4, §4.5, §5; report / tranh chấp / uy tín đội, mã `RATING_*`/`REPORT_*`/`DISPUTE_*`: REPUTATION-001 `API_Spec.md`; mã `TEAM_*`, `USER_*`, `AUTH_*`: USER-001/USER-002 `API_Spec.md`; hiển thị user đã xoá: ADR-0002 (Sửa đổi 2026-11-12) |
| Spec nghiệp vụ | `docs/agent/business-spec/SYSTEM-001/` (v1.10; mercenary theo v1.9, cập nhật 2026-11-14) — UC-ME01..ME09 (gồm UC-ME01 A1 trận ngoài hệ thống), UC-U03; BR-ME01..BR-ME19, BR-V11, BC-ME01..ME05; Business_Rules §2.4, §2.5, §3.1–§3.3, §5.1, §5.2, §5.5, §6, §8.2; AC-ME01..ME11; BR-R01..R10, BR-N01, BR-N03; Scenario 7 |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-13 · v1.0 (baseline) · hiện tại **v2.0** (solution-architect · 2026-11-15) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan

Sau khi kèo đã ghép (`CONFIRMED`), đội trưởng của **đội tạo kèo** hoặc **đội đã ghép** đăng **tin tuyển mercenary** (người chơi lẻ đá thuê): mỗi vị trí một tin, kèm số lượng. Đội cũng tuyển được cho **trận ngoài hệ thống** (không do ArenaFlow ghép — BR-ME19): đội trưởng khai báo sân, giờ, trình độ, tên đối thủ rồi đăng tin (2.28–2.33). Người dùng **tìm kèo mercenary** theo GPS (bán kính 1–20 km, mặc định 3 km) hoặc theo khu vực hồ sơ, **ứng tuyển**; đội trưởng **duyệt / từ chối**; mercenary đã duyệt có thể **rút lui** trước giờ bắt đầu (bị trừ điểm uy tín cá nhân). Mỗi đơn ứng tuyển có một **cuộc trò chuyện 1–1** giữa đội trưởng và ứng viên. Sau trận, đội trưởng và mercenary **đánh giá**, **report** nhau; mercenary có **uy tín cá nhân** (điểm uy tín, tỉ lệ hoàn thành, sao trung bình, huy hiệu xác minh).

**Ngoài phạm vi:** gửi thông báo đẩy (NOTIFICATION-001) · xoá tin nhắn hết hạn lưu (NOTIFICATION-001) · nhập phường/xã cho địa điểm sân (VENUE-001 `API_Spec.md` 2.1, 2.12 — v4.0) · sửa / xoá đánh giá, report, tin nhắn · đổi vị trí của tin (đóng tin và tạo tin mới) · đội trưởng huỷ duyệt mercenary đã duyệt (không hỗ trợ — BR-ME10) · header `Idempotency-Key` (FE chặn bấm lặp).

### 1.1. Quy ước chung

- Như MATCH-001 `API_Spec.md` §1.1: Bearer token (`401 AUTH_001` → refresh), `/api/v1/admin/**` cần quyền Admin (`403 AUTH_004`), envelope `data` / `error` / `pagination`, `201` không kèm header `Location`, thời gian ISO-8601 trả UTC, chuỗi được bỏ khoảng trắng đầu/cuối (`""` ở field tuỳ chọn = không nhập), enum UPPERCASE, `400 VALIDATION_ERROR` kèm `validationErrors[]`.
- **Vị trí** (`position`) dùng **nhãn tiếng Việt** như `preferredPosition` của hồ sơ (USER-001): `Thủ môn` | `Hậu vệ` | `Tiền vệ` | `Tiền đạo`.
- Danh sách phân trang `page` (≥ 1, mặc định 1) / `pageSize` (1..100, mặc định 20); thứ tự cố định ghi ở từng endpoint. Lịch sử chat (2.16) phân trang bằng **con trỏ** (`before` / `after`, `limit`) như MATCH-001 2.17.
- Mọi thao tác ghi có thể trả `409 MATCH_014` "Dữ liệu vừa được cập nhật bởi thao tác khác, vui lòng thử lại" khi nhiều thao tác đụng nhau liên tục → FE tải lại rồi cho người dùng bấm lại.
- Người dùng đã xoá tài khoản hiện `"Người dùng đã xóa"`, ảnh `null` ở mọi endpoint không phải Admin; API Admin (2.25–2.27) trả tên thật kèm cờ `is…Deleted` (ADR-0002).

### 1.2. Ai thao tác được

| Vai trò | Xác định thế nào | Làm được |
|---|---|---|
| **Đội trưởng đội tuyển** | Đội trưởng đang hoạt động **hiện tại** của đội đăng tin (đội tạo kèo hoặc đội đã ghép của trận) | Tạo / sửa số lượng / đóng tin; xem ứng viên; duyệt / từ chối; chat; đánh giá / report mercenary của đơn |
| **Ứng viên / mercenary** | Người đã gửi đơn ứng tuyển | Rút đơn; chat; đánh giá / report đội tuyển của đơn; tranh chấp report bùng nhắm vào mình (REPUTATION-001 2.5) |
| Người dùng đã đăng nhập | — | Tìm kèo, xem chi tiết tin, ứng tuyển, xem uy tín cá nhân của bất kỳ ai, xem đánh giá mercenary dành cho đội |
| Admin | Quyền Admin | Xử lý tranh chấp report bùng liên quan mercenary (2.25–2.27) |

- Tạo tin (2.1) gửi `teamId` = đội của mình trong trận (một người làm đội trưởng cả hai đội — BR-M18 — chọn phía).
- Thao tác trên tin / đơn không gửi `teamId`: hệ thống lấy đội đăng tin. Đội trưởng mới sau khi chuyển quyền tiếp quản mọi tin, đơn, cuộc trò chuyện của đội.
- Đánh giá / report / xem đánh giá theo đơn (2.18–2.20): hệ thống tự xác định phía từ người gọi (ứng viên → phía mercenary; đội trưởng đội tuyển → phía đội).

**Trạng thái đội tuyển ảnh hưởng thao tác** (BR-ME05):

| Thao tác | Đội tuyển `INACTIVE` (tạm ngưng) | Đội tuyển `DISBANDED` (giải tán) |
|---|---|---|
| Tạo tin (2.1, 2.33), sửa số lượng (2.5), duyệt (2.11), khai báo / sửa trận ngoài (2.28, 2.31) | `422 TEAM_015` | `422 TEAM_016` |
| Huỷ trận ngoài (2.32), xem trận ngoài của đội (2.29, 2.30) | Được | `422 TEAM_016` |
| Đóng tin (2.6), từ chối (2.12), xem tin / ứng viên của đội (2.2, 2.8) | Được | `422 TEAM_016` |
| Tin hiện khi tìm kiếm (2.3); nhận ứng tuyển (2.7) | Không hiện; `422 MERCENARY_005` | Không hiện; `422 MERCENARY_005` |
| Chat (2.14–2.17) | Được | Người đã giải tán đội tuyển (đội trưởng cuối) vẫn mở cuộc trò chuyện, đọc lịch sử, đánh dấu đã đọc, đăng ký realtime nhưng **không gửi** (`canSend = false`, 2.15 → `409 CHAT_004`); thành viên khác của đội → `403 CHAT_003`; ứng viên đọc / gửi như cũ (user chốt 2026-11-15, như chat hai đội — MATCH-001 §1.6) |
| Đánh giá, report, xem đánh giá theo đơn — phía đội (2.18–2.20); danh sách report của đội (2.21) | `422 TEAM_015` (BR-R10) | Không còn đội trưởng → `403 MERCENARY_014` (2.18, 2.19; 2.20 với người khác) / `422 TEAM_016` (2.21; 2.20 khi người gọi là người đã giải tán đội tuyển — BR-R10) |
| Phía mercenary đánh giá / report đội | Được (trạng thái đội bị đánh giá / report không ảnh hưởng — BR-R10) | Được |

### 1.3. Trạng thái tin tuyển và đơn ứng tuyển

**Tin tuyển** (`recruitment.status`):

| Trạng thái | Ý nghĩa |
|---|---|
| `OPEN` | Đang tuyển, nhận ứng tuyển và duyệt |
| `FILLED` | Đã đủ số lượng; đơn `PENDING` giữ nguyên nhưng không duyệt được tới khi tin về `OPEN` (rút lui hoặc tăng số lượng) |
| `CLOSED` | Đội trưởng đóng, hoặc kèo bị huỷ (BR-ME09) — trạng thái cuối |
| `EXPIRED` | Đã tới giờ kết thúc dự kiến của trận (BR-ME02) — trạng thái cuối |

**Đơn ứng tuyển** (`application.status`):

| Trạng thái | Ý nghĩa |
|---|---|
| `PENDING` | Chờ duyệt |
| `APPROVED` | Được duyệt — mercenary của trận |
| `REJECTED` | Bị đội trưởng từ chối, hoặc tin bị đóng / hết hạn khi đơn đang chờ — trạng thái cuối |
| `WITHDRAWN` | Ứng viên rút — trạng thái cuối |

- **Trạng thái hiển thị là trạng thái hiệu lực:** kèo bị huỷ → mọi tin `OPEN`/`FILLED` của kèo hiện `CLOSED` ngay; tới giờ kết thúc trận → hiện `EXPIRED` ngay; đơn `PENDING` của tin `CLOSED`/`EXPIRED` hiện `REJECTED` ngay. Thông báo cho ứng viên về các thay đổi tự động này có thể trễ tối đa khoảng 5 phút (BR-ME07). Thông báo kèo bị huỷ đến mercenary đã duyệt có thể trễ tối đa ~5 phút; trận ngoài báo ngay khi đội trưởng huỷ (2.32) (BR-ME09).
- **Từ giờ bắt đầu trận** (BR-ME07, BR-ME08): tin không còn hiện khi tìm kèo, không ứng tuyển, không duyệt, không tạo tin mới và không **tăng** số lượng được nữa (`422 MERCENARY_013`); vẫn **giảm** số lượng và đóng tin được; trạng thái tin vẫn `OPEN`/`FILLED` tới giờ kết thúc rồi `EXPIRED`, đơn `PENDING` còn lại thành `REJECTED` lúc đó. Đội trưởng vẫn đóng tin, từ chối đơn; ứng viên rút đơn chờ được.
- **Mỗi (kèo, đội, vị trí) tối đa một tin `OPEN`/`FILLED`** (BR-ME05): muốn thêm người thì tăng số lượng (2.5); vị trí đã có tin đang mở → `409 MERCENARY_023`.
- **Ứng viên xoá tài khoản** (BR-ME18, USER-001 `API_Spec.md` 2.7): mọi đơn `PENDING`/`APPROVED` của trận chưa bắt đầu (kèo đã ghép hoặc trận ngoài chưa huỷ) tự thành `WITHDRAWN`, không trừ điểm; tin `FILLED` về `OPEN`; đội trưởng nhận thông báo. Không có frame realtime cho thay đổi này.
- Kèo bị huỷ: đơn `APPROVED` **giữ nguyên** `APPROVED` (lịch sử); mercenary không bị trừ điểm, trận không tính tỉ lệ hoàn thành cá nhân, hai bên không đánh giá nhau (BR-ME09).
- `quantityApproved` = số đơn đang `APPROVED`; `quantityNeeded` ≥ `quantityApproved`.
- **Trận ngoài hệ thống** (BR-ME19): trạng thái `SCHEDULED` (đã khai báo) hoặc `CANCELLED` (đội trưởng huỷ — 2.32). Tin của trận ngoài theo đúng các quy tắc trên, dùng giờ bắt đầu / kết thúc đã khai báo: huỷ trận ngoài (chỉ trước giờ bắt đầu, như huỷ kèo) = kèo bị huỷ (tin `CLOSED`, đơn chờ `REJECTED`, đơn đã duyệt giữ nguyên, không ai bị trừ điểm); tới giờ kết thúc = tin `EXPIRED`, trận được coi là đã kết thúc để đánh giá / report.

### 1.4. Bảo vệ vị trí sân (BR-ME04, BC-ME03)

| Người xem | Thấy |
|---|---|
| Người chưa được duyệt (chưa ứng tuyển, đơn `PENDING`/`REJECTED`/`WITHDRAWN`) | `distanceKm` (chỉ khi gửi toạ độ) và `area` "phường, tỉnh" của sân. `venue = null`, `captainName = null` |
| Người có đơn `APPROVED` ở tin | Thêm `venue` (tên, địa chỉ, toạ độ) và `captainName` (tên đội trưởng hiện tại) trên tin / đơn |
| Đội trưởng đội tuyển | Như người có đơn `APPROVED` |

- `area` = phường/xã và tỉnh/thành của **sân**; `null` khi sân tạo trước khi bắt buộc nhập phường và chưa được bổ sung (BR-V11) — sân đó vẫn ra khi tìm bằng GPS, **không** ra khi tìm không GPS. FE hiển thị `"<wardName>, <provinceName>"` (vd "Phường Sài Gòn, TP. Hồ Chí Minh").
- **Chat là ngoại lệ cho tên đội trưởng** (BR-ME04, user chốt 2026-11-14): trong cuộc trò chuyện của đơn (2.14–2.16, frame `MESSAGE_CREATED`) tên đội trưởng hiện tại và tên người gửi luôn hiện, kể cả khi đơn `PENDING`. Ẩn trước khi duyệt chỉ áp cho **tên sân, địa chỉ, toạ độ**.
- `distanceKm` làm tròn **tới 0,5 km** (vd `2.5`) để không suy ra được vị trí chính xác của sân; khoảng cách dưới 0,5 km hiện `0.5`.
- Toạ độ người dùng gửi lên (2.3, 2.4) chỉ dùng để tính khoảng cách trong request, không được lưu.

### 1.5. Uy tín cá nhân — điểm uy tín (Reliability Score)

Điểm cá nhân trong [0, 100], khởi đầu 80, độc lập với điểm đội; đội bùng không làm giảm điểm cá nhân thành viên. Mức hiển thị `reliabilityLevel` như REPUTATION-001 `API_Spec.md` §4.1. Chỉ trận tham gia với vai trò **mercenary** (kèo trên hệ thống hoặc trận ngoài hệ thống) mới tác động (BR-ME15).

| Sự kiện | Mercenary | Thay đổi |
|---|---|---|
| Đội trưởng đánh giá mercenary **bằng sao** (2.18) — tính "hoàn thành" trận | Mercenary được đánh giá | +2 |
| Lúc được đánh giá bằng sao, chuỗi trận sạch cá nhân chạm bội số của 5 (5, 15… → +5; 10, 20… → +10) | Mercenary đạt mốc | +5 / +10 (cộng thêm vào +2) |
| Đánh giá đó là 5 sao | Mercenary được đánh giá | +1 (cộng thêm) |
| Rút lui sau khi được duyệt (2.13) | Người rút | −1 (từ đủ 24 giờ trước giờ bắt đầu) · −3 (từ đủ 4 giờ đến dưới 24 giờ) · −5 (dưới 4 giờ); chuỗi trận sạch về 0 |
| Bị đội trưởng report (2.20) | Mercenary bị report | Theo bảng mức trừ REPUTATION-001 `API_Spec.md` §1.3, theo số lần bị report loại đó **của cá nhân** |
| Admin bác report bùng (2.27 `REJECTED`) | Mercenary bị report | Hoàn đúng số điểm đã thực trừ |

- Đánh giá "Không đến", hoặc đội trưởng **chưa** đánh giá → không +2, không tính hoàn thành, không đổi chuỗi (user chốt 2026-11-14). Việc hai đội đánh giá nhau / trận chuyển `COMPLETED` **không** cộng điểm cho mercenary; đánh giá giữa đội trưởng và mercenary **không** làm trận `COMPLETED` (BR-ME13).
- **Chuỗi trận sạch cá nhân:** lúc được đánh giá bằng sao, mercenary không bị report nào (chưa bị Admin bác) trong trận → +1, có → về 0; bị report ở bất kỳ trận nào → về 0; rút lui sau khi duyệt → về 0. Chuỗi chạm bội số của 5 → số lần bị report mọi loại của cá nhân về 0.
- **Mercenary report đội (2.20) tác động lên đội như report giữa hai đội** (BR-ME14, user chốt 2026-11-14): trừ điểm **đội** theo REPUTATION-001 `API_Spec.md` §1.3; cắt chuỗi trận sạch của đội; report **bùng** loại kèo đó khỏi tử số tỉ lệ hoàn thành của đội (REPUTATION-001 §1.4) — với kèo trên hệ thống; đội tranh chấp report bùng trong 24 giờ (REPUTATION-001 2.5). Trận ngoài hệ thống không tính vào tỉ lệ hoàn thành của đội.
- Đánh giá của mercenary dành cho đội **không** cộng điểm đội và không vào sao trung bình đội (BR-ME13). Trận ngoài hệ thống không cộng / trừ điểm đội, trừ report của mercenary (BR-ME19).

### 1.6. Tỉ lệ hoàn thành, sao trung bình, huy hiệu cá nhân

Tính tại thời điểm đọc (2.8, 2.23):
- **Trận được tính (mẫu số):** trận mà mercenary được đội trưởng đánh giá **bằng sao**; trận đã kết thúc mà mercenary còn `APPROVED` và bị report bùng (chưa bị bác); trận mà mercenary **rút lui sau khi được duyệt dưới 24 giờ** trước giờ bắt đầu (trừ khi trận sau đó bị huỷ).
- **Trận hoàn thành (tử số):** trận mà mercenary được đánh giá bằng sao và **không** bị report bùng (chưa bị bác).
- Không tính: trận bị huỷ (BR-ME09), rút từ đủ 24 giờ trước trận, đơn chưa từng được duyệt, trận chưa được đội trưởng đánh giá hoặc bị đánh giá "Không đến" mà không bị report bùng.
- Trận được tính < 5 → `completionRate = null`, `isNewcomer = true` (FE hiện "Mới tham gia X/5 trận"); ≥ 5 → phần trăm làm tròn tới số nguyên.
- **Sao trung bình cá nhân:** trung bình sao đội trưởng đã chấm cho người đó (không tính "không đến"), 1 chữ số thập phân.
- **Huy hiệu xác minh cá nhân** (BR-R04): đủ cả 4 điều kiện — tài khoản có số điện thoại; số trận hoàn thành **> 5**; sao trung bình **> 3.0**; điểm uy tín **≥ 70**. Cấp / thu hồi ngay sau mỗi đánh giá, report, quyết định tranh chấp liên quan cá nhân và khi rút lui sau duyệt; thêm / bỏ số điện thoại cập nhật ở lần đối soát hằng ngày (trễ tối đa ~1 ngày) — Business_Rules §8.2.

### 1.7. Cuộc trò chuyện theo đơn và kênh realtime

- Mỗi đơn ứng tuyển có đúng **một** cuộc trò chuyện (`conversationId` có trong đơn — 2.7, 2.8, 2.9, 2.10, và trong danh sách "Tin nhắn của tôi" MATCH-001 2.20), mở ngay khi ứng tuyển. Hai bên: **đội tuyển** (người nhắn là đội trưởng hiện tại) và **ứng viên** (BR-ME16). `contextType = "MERCENARY_APPLICATION"`.
- **Endpoint, kiểu dữ liệu, kênh realtime dùng chung** với chat giữa hai đội trong kèo (MATCH-001 `API_Spec.md` §1.6, §1.7, 2.16–2.20, §4.7, §4.8): 2.14–2.17 ở đây **là** MATCH-001 2.19, 2.16, 2.17, 2.18 (cùng path, cùng body / response / lỗi). Mục này chỉ ghi quy tắc riêng của cuộc trò chuyện theo đơn.
- **Gửi tin được** (`canSend`) khi đơn (trạng thái hiệu lực) là `PENDING` hoặc `APPROVED` — đơn `APPROVED` nhắn được cả trước, trong, sau trận và khi kèo bị huỷ, tới khi tin nhắn hết hạn lưu. Đơn `REJECTED`/`WITHDRAWN` (kể cả đơn chờ bị từ chối tự động khi tin đóng / hết hạn) → **chỉ xem** (`409 CHAT_004`). Đọc lịch sử, đánh dấu đã đọc: mọi trạng thái. Không có lỗi "kèo hết hạn" (`MATCH_004`) ở cuộc trò chuyện theo đơn.
- **Đội tuyển đã giải tán** (user chốt 2026-11-15 — như chat hai đội, MATCH-001 §1.6): người đã bấm giải tán (đội trưởng cuối) vẫn là bên đội tuyển của cuộc trò chuyện ở chế độ **chỉ xem** — 2.14 (`canSend = false`), 2.16, 2.17, `SUBSCRIBE` được; 2.15 → `409 CHAT_004`. Thành viên khác của đội → `403 CHAT_003`. Ứng viên đọc / gửi theo trạng thái đơn như trên. Ví dụ: FC Hoa Lư đã duyệt Trần Văn C, đội trưởng A giải tán đội → A vẫn đọc chat với C nhưng ô chat bị khoá; C vẫn nhắn được (đơn `APPROVED`).
- Tên đội trưởng hiện tại (2.14) và tên người gửi luôn hiện trong chat, kể cả khi đơn `PENDING` (BR-ME04 ngoại lệ — §1.4); đội đã giải tán → `captainName = null`.
- Tin nhắn 1..1000 ký tự; mỗi tin mang **người thực sự đã gửi** (tên, ảnh); tin cũ giữ người gửi lúc đó dù đội đã chuyển quyền đội trưởng (như BR-N03). Tin được lưu 90 ngày sau khi trận kết thúc (BR-N01).
- **Kênh realtime:** MATCH-001 `API_Spec.md` §1.7 — `SUBSCRIBE /topic/conversations/{conversationId}`; thứ tự bù tin sau khi kết nối lại dùng 2.16 (`after`) và 2.14. Lỗi đăng ký: `CHAT_002`, `CHAT_003`, `AUTH_001`.

**Frame server gửi tới topic cuộc trò chuyện theo đơn** (body JSON):

| `type` | Khi nào | Nội dung |
|---|---|---|
| `MESSAGE_CREATED` | Có tin mới (kể cả tin của chính mình) | `message`: object Tin nhắn (MATCH-001 §4.8) |
| `APPLICATION_UPDATED` | Đội trưởng duyệt / từ chối, ứng viên rút đơn | `applicationId`, `status`, `recruitmentId`, `recruitmentStatus`, `quantityApproved` |

```json
{ "type": "APPLICATION_UPDATED", "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01", "status": "APPROVED",
  "recruitmentId": "01937090-4a5b-7c6d-8e7f-0a1b2c3d4e01", "recruitmentStatus": "FILLED", "quantityApproved": 2 }
```

- **Không** có frame khi hệ thống đóng tin / hết hạn / từ chối đơn chờ tự động: FE biết qua `CHAT_004` ở lần gửi kế tiếp hoặc khi tải lại 2.10 / 2.14.

## 2. Endpoints

| # | Method + Path | Dùng khi | Ai gọi được | Thành công |
|---|---|---|---|---|
| 2.1 | `POST /api/v1/matches/{matchId}/mercenary-recruitments` | Đăng tin tuyển (một hoặc nhiều vị trí) | Đội trưởng đội tạo kèo / đội đã ghép | 201 |
| 2.2 | `GET /api/v1/teams/{teamId}/mercenary-recruitments` | Quản lý tin của đội | Đội trưởng của đội | 200 |
| 2.3 | `GET /api/v1/mercenary-recruitments` | Tìm kèo mercenary | Người dùng đã đăng nhập | 200 |
| 2.4 | `GET /api/v1/mercenary-recruitments/{recruitmentId}` | Xem chi tiết tin | Người dùng đã đăng nhập | 200 |
| 2.5 | `PATCH /api/v1/mercenary-recruitments/{recruitmentId}` | Sửa số lượng | Đội trưởng đội tuyển | 200 |
| 2.6 | `POST /api/v1/mercenary-recruitments/{recruitmentId}/close` | Đóng tin sớm | Đội trưởng đội tuyển | 200 |
| 2.7 | `POST /api/v1/mercenary-recruitments/{recruitmentId}/applications` | Ứng tuyển | Người dùng đã đăng nhập | 201 |
| 2.8 | `GET /api/v1/mercenary-recruitments/{recruitmentId}/applications` | Danh sách ứng viên của tin | Đội trưởng đội tuyển | 200 |
| 2.9 | `GET /api/v1/profile/mercenary-applications` | Đơn ứng tuyển của tôi | Người dùng đã đăng nhập | 200 |
| 2.10 | `GET /api/v1/mercenary-applications/{applicationId}` | Chi tiết đơn | Ứng viên, đội trưởng đội tuyển | 200 |
| 2.11 | `POST /api/v1/mercenary-applications/{applicationId}/approve` | Duyệt ứng viên | Đội trưởng đội tuyển | 200 |
| 2.12 | `POST /api/v1/mercenary-applications/{applicationId}/reject` | Từ chối ứng viên | Đội trưởng đội tuyển | 204 |
| 2.13 | `POST /api/v1/mercenary-applications/{applicationId}/withdraw` | Rút đơn / rút lui | Ứng viên | 200 |
| 2.14 | `GET /api/v1/conversations/{conversationId}` | Đầu phòng chat (= MATCH-001 2.19) | Hai bên của cuộc trò chuyện | 200 |
| 2.15 | `POST /api/v1/conversations/{conversationId}/messages` | Gửi tin nhắn (= MATCH-001 2.16) | Hai bên | 201 |
| 2.16 | `GET /api/v1/conversations/{conversationId}/messages` | Lịch sử chat (= MATCH-001 2.17) | Hai bên | 200 |
| 2.17 | `POST /api/v1/conversations/{conversationId}/messages/read` | Đánh dấu đã đọc (= MATCH-001 2.18) | Hai bên | 204 |
| 2.18 | `POST /api/v1/mercenary-applications/{applicationId}/ratings` | Đánh giá sau trận | Đội trưởng đội tuyển, mercenary | 201 |
| 2.19 | `GET /api/v1/mercenary-applications/{applicationId}/ratings` | Xem đánh giá của đơn | Đội trưởng đội tuyển, mercenary | 200 |
| 2.20 | `POST /api/v1/mercenary-applications/{applicationId}/reports` | Report | Đội trưởng đội tuyển, ứng viên | 201 |
| 2.21 | `GET /api/v1/teams/{teamId}/mercenary-reports` | Report đội nhận từ / gửi cho mercenary | Đội trưởng của đội | 200 |
| 2.22 | `GET /api/v1/profile/mercenary-reports` | Report tôi nhận / gửi với vai trò mercenary | Người dùng đã đăng nhập | 200 |
| 2.23 | `GET /api/v1/users/{userId}/reputation` | Xem uy tín cá nhân | Người dùng đã đăng nhập | 200 |
| 2.24 | `GET /api/v1/teams/{teamId}/mercenary-ratings` | Đánh giá mercenary dành cho đội (hồ sơ đội) | Người dùng đã đăng nhập | 200 |
| 2.25 | `GET /api/v1/admin/mercenary-disputes` | Admin: hàng chờ tranh chấp mercenary | Admin | 200 |
| 2.26 | `GET /api/v1/admin/mercenary-disputes/{disputeId}` | Admin: chi tiết tranh chấp | Admin | 200 |
| 2.27 | `PATCH /api/v1/admin/mercenary-disputes/{disputeId}/resolve` | Admin: quyết định tranh chấp | Admin | 200 |
| 2.28 | `POST /api/v1/teams/{teamId}/external-matches` | Khai báo trận ngoài hệ thống + đăng tin | Đội trưởng của đội | 201 |
| 2.29 | `GET /api/v1/teams/{teamId}/external-matches` | Trận ngoài của đội | Đội trưởng của đội | 200 |
| 2.30 | `GET /api/v1/external-matches/{externalMatchId}` | Chi tiết trận ngoài (màn sửa) | Đội trưởng của đội | 200 |
| 2.31 | `PATCH /api/v1/external-matches/{externalMatchId}` | Sửa trận ngoài | Đội trưởng của đội | 200 |
| 2.32 | `POST /api/v1/external-matches/{externalMatchId}/cancel` | Huỷ trận ngoài | Đội trưởng của đội | 200 |
| 2.33 | `POST /api/v1/external-matches/{externalMatchId}/mercenary-recruitments` | Thêm vị trí cho trận ngoài | Đội trưởng của đội | 201 |

**Tranh chấp report bùng** (mercenary bị đội trưởng report bùng, hoặc đội bị mercenary report bùng): dùng REPUTATION-001 `API_Spec.md` 2.5 `POST /api/v1/reports/{reportId}/dispute` — từ v1.1 của REPUTATION-001, report nhắm vào **cá nhân** do chính người bị report tranh chấp (`403 REPORT_009` nếu người khác gọi); report nhắm vào đội do đội trưởng đội đó tranh chấp như cũ. `reportId` lấy từ 2.21 / 2.22 (`canDispute = true`).

---

### 2.1. Đăng tin tuyển — `POST /api/v1/matches/{matchId}/mercenary-recruitments`

**Dùng khi:** màn hình chi tiết kèo đã ghép, đội trưởng bấm "Tuyển mercenary", chọn một hoặc nhiều vị trí, mỗi vị trí một số lượng (UC-ME01). Mỗi vị trí thành một tin riêng (BR-ME05).
**Ai gọi được:** đội trưởng của `teamId`; `teamId` là đội tạo kèo hoặc đội đã ghép của trận; trận `CONFIRMED` và chưa tới giờ kết thúc.

**Path params:** `matchId` (UUID, bắt buộc).

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` | string (UUID) | Có | Đội của mình trong trận | `"01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c"` |
| `positions` | array | Có | 1..4 phần tử, không trùng `position` | — |
| `positions[].position` | string | Có | `Thủ môn` \| `Hậu vệ` \| `Tiền vệ` \| `Tiền đạo` | `"Tiền vệ"` |
| `positions[].quantity` | integer | Có | 1..10 (BR-ME05) | `2` |

```json
{
  "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c",
  "positions": [
    { "position": "Tiền vệ", "quantity": 2 },
    { "position": "Thủ môn", "quantity": 1 }
  ]
}
```

**Response 201** — mảng Tin tuyển (§4.2), cùng thứ tự `positions` trong request. Người gọi là đội trưởng → có `venue`, `captainName`.

```json
{
  "data": [
    {
      "recruitmentId": "01937090-4a5b-7c6d-8e7f-0a1b2c3d4e01",
      "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
      "team": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null },
      "position": "Tiền vệ",
      "quantityNeeded": 2,
      "quantityApproved": 0,
      "status": "OPEN",
      "match": {
        "startsAt": "2026-11-14T11:00:00Z",
        "endsAt": "2026-11-14T12:30:00Z",
        "format": "SEVEN_A_SIDE",
        "skillLevels": ["Trung bình"],
        "area": { "wardName": "Phường Sài Gòn", "provinceName": "TP. Hồ Chí Minh" },
        "distanceKm": null,
        "venue": { "venueId": "01935d00-1111-7222-8333-444455556666", "name": "Sân bóng Hoa Lư", "address": "2 Đinh Tiên Hoàng, Phường Sài Gòn, TP.HCM", "latitude": 10.7883, "longitude": 106.7018 }
      },
      "captainName": "Nguyễn Văn A",
      "viewerRole": "CAPTAIN",
      "myApplication": null,
      "pendingApplicationCount": 0,
      "closedAt": null,
      "createdAt": "2026-11-13T08:00:00Z",
      "updatedAt": "2026-11-13T08:00:00Z"
    },
    {
      "recruitmentId": "01937090-4a5b-7c6d-8e7f-0a1b2c3d4e02",
      "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
      "team": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null },
      "position": "Thủ môn",
      "quantityNeeded": 1,
      "quantityApproved": 0,
      "status": "OPEN",
      "match": { "startsAt": "2026-11-14T11:00:00Z", "endsAt": "2026-11-14T12:30:00Z", "format": "SEVEN_A_SIDE", "skillLevels": ["Trung bình"],
                 "area": { "wardName": "Phường Sài Gòn", "provinceName": "TP. Hồ Chí Minh" }, "distanceKm": null,
                 "venue": { "venueId": "01935d00-1111-7222-8333-444455556666", "name": "Sân bóng Hoa Lư", "address": "2 Đinh Tiên Hoàng, Phường Sài Gòn, TP.HCM", "latitude": 10.7883, "longitude": 106.7018 } },
      "captainName": "Nguyễn Văn A",
      "viewerRole": "CAPTAIN",
      "myApplication": null,
      "pendingApplicationCount": 0,
      "closedAt": null,
      "createdAt": "2026-11-13T08:00:00Z",
      "updatedAt": "2026-11-13T08:00:00Z"
    }
  ]
}
```

Đội đã có tin `OPEN`/`FILLED` cho một vị trí trong request → `409 MERCENARY_023`, không tạo tin nào; muốn thêm người thì tăng số lượng tin đó (2.5).

**Lỗi** (thứ tự kiểm từ trên xuống)

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu `teamId` / `positions`; `positions` rỗng hoặc > 4; vị trí ngoài danh sách hoặc trùng; `quantity` ngoài 1..10 | Hiện lỗi cạnh field |
| 404 | `MATCH_001` | Không có kèo | Quay lại |
| 404 | `TEAM_001` | Không có đội `teamId` | Kiểm lại `teamId` |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 422 | `TEAM_015` | Đội đang tạm ngưng | Hiện message |
| 403 | `TEAM_002` | Không phải đội trưởng của `teamId` | Ẩn nút "Tuyển mercenary" |
| 403 | `MATCH_009` | `teamId` không phải đội tạo kèo / đội đã ghép của trận | Gửi đúng `teamId` |
| 422 | `MERCENARY_003` | Kèo chưa ghép (`OPEN`, `EXPIRED`) | Hiện "Chỉ có thể tuyển mercenary sau khi kèo đã ghép" |
| 422 | `MERCENARY_004` | Kèo đã huỷ, đã kết thúc hoặc đã tới giờ kết thúc | Hiện message; ẩn nút |
| 422 | `MERCENARY_013` | Trận đã bắt đầu (BR-ME08) | Ẩn nút "Tuyển mercenary" |
| 409 | `MERCENARY_023` | Đội đã có tin đang mở cho vị trí này của trận | Tải lại 2.2; gợi ý tăng số lượng tin đó (2.5) |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.2. Tin tuyển của đội — `GET /api/v1/teams/{teamId}/mercenary-recruitments`

**Dùng khi:** màn hình "Tuyển mercenary" của đội — mọi tin của đội, hoặc tin của một trận (`matchId`).
**Ai gọi được:** đội trưởng của `teamId` (đội `INACTIVE` vẫn xem được).

**Path / Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` | UUID | Có | — | `01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c` |
| `matchId` | UUID | Không | Chỉ tin của trận này | `01936b20-7d8e-7f90-a1b2-c3d4e5f60708` |
| `status` | string | Không | `ACTIVE` (mặc định: `OPEN` + `FILLED`) \| `OPEN` \| `FILLED` \| `CLOSED` \| `EXPIRED` \| `ALL` | `ACTIVE` |
| `page`, `pageSize` | integer | Không | §1.1 | `1`, `20` |

**Thứ tự sắp xếp:** giờ bắt đầu trận tăng dần; cùng trận → `createdAt` tăng dần, rồi `recruitmentId`.

**Response 200** — mảng Tin tuyển (§4.2, góc nhìn đội trưởng: có `venue`, `captainName`, `pendingApplicationCount`) + `pagination`.

```json
{
  "data": [
    {
      "recruitmentId": "01937090-4a5b-7c6d-8e7f-0a1b2c3d4e01",
      "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
      "team": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null },
      "position": "Tiền vệ",
      "quantityNeeded": 2,
      "quantityApproved": 1,
      "status": "OPEN",
      "match": { "startsAt": "2026-11-14T11:00:00Z", "endsAt": "2026-11-14T12:30:00Z", "format": "SEVEN_A_SIDE", "skillLevels": ["Trung bình"],
                 "area": { "wardName": "Phường Sài Gòn", "provinceName": "TP. Hồ Chí Minh" }, "distanceKm": null,
                 "venue": { "venueId": "01935d00-1111-7222-8333-444455556666", "name": "Sân bóng Hoa Lư", "address": "2 Đinh Tiên Hoàng, Phường Sài Gòn, TP.HCM", "latitude": 10.7883, "longitude": 106.7018 } },
      "captainName": "Nguyễn Văn A",
      "viewerRole": "CAPTAIN",
      "myApplication": null,
      "pendingApplicationCount": 3,
      "closedAt": null,
      "createdAt": "2026-11-13T08:00:00Z",
      "updatedAt": "2026-11-13T09:10:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `status` ngoài enum; `matchId` sai định dạng; `page`/`pageSize` ngoài miền | Sửa tham số |
| 404 | `TEAM_001` | Không có đội | Quay lại |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 403 | `TEAM_002` | Không phải đội trưởng | Ẩn màn hình |

---

### 2.3. Tìm kèo mercenary — `GET /api/v1/mercenary-recruitments`

**Dùng khi:** màn hình "Tìm kèo mercenary" (UC-ME02). App xin quyền GPS; có toạ độ → gửi `lat`/`lng` (BR-ME03); không có → không gửi, hiển thị "Bật GPS để tìm kèo gần bạn hơn".
**Ai gọi được:** người dùng đã đăng nhập.

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `lat` | number | Không | −90..90; gửi cùng `lng` | `10.7769` |
| `lng` | number | Không | −180..180; gửi cùng `lat` | `106.7009` |
| `radiusKm` | number | Không | 1..20, mặc định 3; chỉ khi có `lat`/`lng` | `3` |
| `position` | string | Không | Vị trí (§1.1) | `"Tiền vệ"` |
| `page`, `pageSize` | integer | Không | §1.1 | `1`, `20` |

| Chế độ | Kết quả | Thứ tự | `distanceKm` |
|---|---|---|---|
| Có `lat`/`lng` | Tin có sân trong bán kính `radiusKm` | Khoảng cách tăng dần → giờ bắt đầu → `recruitmentId` | Có (km, làm tròn 0,5 km — §1.4) |
| Không | Tin có sân **cùng tỉnh** với phường trong hồ sơ người gọi | Sân **cùng phường** lên đầu → giờ bắt đầu tăng dần → `recruitmentId` | `null` |

Chỉ trả tin `OPEN` của kèo `CONFIRMED` hoặc trận ngoài hệ thống `SCHEDULED` **chưa tới giờ bắt đầu**, của đội tuyển đang hoạt động (BR-ME05, BR-ME07, BR-ME19). Tin của đội mình / tin đã ứng tuyển vẫn hiện — FE dùng `myApplication` và lỗi 2.7 để ẩn / khoá nút "Ứng tuyển". Sân chưa có phường (BR-V11) vẫn ra khi tìm bằng GPS (`area = null`), không ra khi tìm không GPS.

**Response 200** — mảng Tin tuyển (§4.2, góc nhìn người tìm: `venue = null`, `captainName = null` trừ khi đơn của mình `APPROVED`) + `pagination`.

```json
{
  "data": [
    {
      "recruitmentId": "01937090-4a5b-7c6d-8e7f-0a1b2c3d4e01",
      "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
      "team": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null },
      "position": "Tiền vệ",
      "quantityNeeded": 2,
      "quantityApproved": 1,
      "status": "OPEN",
      "match": { "startsAt": "2026-11-14T11:00:00Z", "endsAt": "2026-11-14T12:30:00Z", "format": "SEVEN_A_SIDE", "skillLevels": ["Trung bình"],
                 "area": { "wardName": "Phường Sài Gòn", "provinceName": "TP. Hồ Chí Minh" }, "distanceKm": 2.5, "venue": null },
      "captainName": null,
      "viewerRole": null,
      "myApplication": null,
      "pendingApplicationCount": null,
      "closedAt": null,
      "createdAt": "2026-11-13T08:00:00Z",
      "updatedAt": "2026-11-13T09:10:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Chỉ gửi một trong `lat`/`lng`; `radiusKm` ngoài 1..20 hoặc gửi khi không có toạ độ; `position` ngoài danh sách | Sửa tham số |
| 404 | `USER_001` | Không gửi toạ độ và tài khoản đã xoá (token còn hạn) | Xoá token, về đăng nhập |
| 422 | `MERCENARY_018` | Không gửi toạ độ và hồ sơ chưa chọn khu vực (phường/xã) (BR-ME17) | Hiện "Bật GPS hoặc cập nhật khu vực trong hồ sơ để tìm kèo" |

---

### 2.4. Chi tiết tin tuyển — `GET /api/v1/mercenary-recruitments/{recruitmentId}`

**Dùng khi:** mở một tin từ kết quả tìm kiếm, thông báo, danh sách của đội.
**Ai gọi được:** người dùng đã đăng nhập; mọi trạng thái tin.

**Path / Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `recruitmentId` | UUID | Có | — | `01937090-4a5b-7c6d-8e7f-0a1b2c3d4e01` |
| `lat`, `lng` | number | Không | Cặp; để trả `distanceKm` | `10.7769`, `106.7009` |

**Response 200** — Tin tuyển (§4.2). Ẩn / hiện sân theo §1.4. Ví dụ người xem có đơn `APPROVED`:

```json
{
  "data": {
    "recruitmentId": "01937090-4a5b-7c6d-8e7f-0a1b2c3d4e01",
    "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
    "team": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null },
    "position": "Tiền vệ",
    "quantityNeeded": 2,
    "quantityApproved": 2,
    "status": "FILLED",
    "match": { "startsAt": "2026-11-14T11:00:00Z", "endsAt": "2026-11-14T12:30:00Z", "format": "SEVEN_A_SIDE", "skillLevels": ["Trung bình"],
               "area": { "wardName": "Phường Sài Gòn", "provinceName": "TP. Hồ Chí Minh" }, "distanceKm": 2.5,
               "venue": { "venueId": "01935d00-1111-7222-8333-444455556666", "name": "Sân bóng Hoa Lư", "address": "2 Đinh Tiên Hoàng, Phường Sài Gòn, TP.HCM", "latitude": 10.7883, "longitude": 106.7018 } },
    "captainName": "Nguyễn Văn A",
    "viewerRole": "APPLICANT",
    "myApplication": { "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01", "status": "APPROVED", "conversationId": "019370a2-6c7d-7e8f-a001-2b3c4d5e6f01" },
    "pendingApplicationCount": null,
    "closedAt": null,
    "createdAt": "2026-11-13T08:00:00Z",
    "updatedAt": "2026-11-13T10:00:00Z"
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Chỉ gửi một trong `lat`/`lng`; id sai định dạng | Sửa tham số |
| 404 | `MERCENARY_001` | Không có tin | Quay lại |

---

### 2.5. Sửa số lượng — `PATCH /api/v1/mercenary-recruitments/{recruitmentId}`

**Dùng khi:** đội trưởng đổi số người cần tuyển của tin (UC-ME07). Không đổi được vị trí.
**Ai gọi được:** đội trưởng đội tuyển; tin `OPEN` hoặc `FILLED`.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `quantityNeeded` | integer | Có | 1..10, ≥ `quantityApproved` | `4` |

```json
{ "quantityNeeded": 4 }
```

Kết quả (BR-ME08): bằng `quantityApproved` → `FILLED`; lớn hơn → `OPEN`. Từ giờ bắt đầu trận chỉ **giảm** được (tăng → `422 MERCENARY_013`). Ví dụ đã duyệt 2: gửi 1 → `422 MERCENARY_008`; gửi 2 → `FILLED`; gửi 4 → `OPEN`.

**Response 200** — Tin tuyển (§4.2) sau khi sửa.

```json
{
  "data": {
    "recruitmentId": "01937090-4a5b-7c6d-8e7f-0a1b2c3d4e01",
    "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
    "team": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null },
    "position": "Tiền vệ",
    "quantityNeeded": 4,
    "quantityApproved": 2,
    "status": "OPEN",
    "match": { "startsAt": "2026-11-14T11:00:00Z", "endsAt": "2026-11-14T12:30:00Z", "format": "SEVEN_A_SIDE", "skillLevels": ["Trung bình"],
               "area": { "wardName": "Phường Sài Gòn", "provinceName": "TP. Hồ Chí Minh" }, "distanceKm": null,
               "venue": { "venueId": "01935d00-1111-7222-8333-444455556666", "name": "Sân bóng Hoa Lư", "address": "2 Đinh Tiên Hoàng, Phường Sài Gòn, TP.HCM", "latitude": 10.7883, "longitude": 106.7018 } },
    "captainName": "Nguyễn Văn A",
    "viewerRole": "CAPTAIN",
    "myApplication": null,
    "pendingApplicationCount": 1,
    "closedAt": null,
    "createdAt": "2026-11-13T08:00:00Z",
    "updatedAt": "2026-11-13T11:00:00Z"
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu `quantityNeeded`; ngoài 1..10 | Hiện lỗi cạnh field |
| 404 | `MERCENARY_001` | Không có tin | Quay lại |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 422 | `TEAM_015` | Đội đang tạm ngưng | Hiện message |
| 403 | `TEAM_002` | Không phải đội trưởng đội tuyển | Ẩn nút |
| 409 | `MERCENARY_007` | Tin đã đóng / hết hạn (kể cả do kèo bị huỷ, tới giờ kết thúc) | Tải lại 2.4 |
| 422 | `MERCENARY_008` | Số mới thấp hơn số đã duyệt | Hiện "Số lượng không được thấp hơn số người đã duyệt" |
| 422 | `MERCENARY_013` | Tăng số lượng khi trận đã bắt đầu | Chỉ cho giảm số lượng / đóng tin |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.6. Đóng tin — `POST /api/v1/mercenary-recruitments/{recruitmentId}/close`

**Dùng khi:** đội trưởng bấm "Đóng tin" (UC-ME07). Đơn `PENDING` của tin bị từ chối tự động, ứng viên nhận thông báo (BR-ME07); đơn `APPROVED` giữ nguyên.
**Ai gọi được:** đội trưởng đội tuyển (đội `INACTIVE` vẫn được); tin `OPEN` hoặc `FILLED`. Không body.

**Response 200** — Tin tuyển (§4.2) với `status = CLOSED`, `closedAt`, thêm `rejectedApplicationCount` (số đơn chờ vừa bị từ chối).

```json
{
  "data": {
    "recruitmentId": "01937090-4a5b-7c6d-8e7f-0a1b2c3d4e01",
    "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
    "team": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null },
    "position": "Tiền vệ",
    "quantityNeeded": 4,
    "quantityApproved": 2,
    "status": "CLOSED",
    "match": { "startsAt": "2026-11-14T11:00:00Z", "endsAt": "2026-11-14T12:30:00Z", "format": "SEVEN_A_SIDE", "skillLevels": ["Trung bình"],
               "area": { "wardName": "Phường Sài Gòn", "provinceName": "TP. Hồ Chí Minh" }, "distanceKm": null,
               "venue": { "venueId": "01935d00-1111-7222-8333-444455556666", "name": "Sân bóng Hoa Lư", "address": "2 Đinh Tiên Hoàng, Phường Sài Gòn, TP.HCM", "latitude": 10.7883, "longitude": 106.7018 } },
    "captainName": "Nguyễn Văn A",
    "viewerRole": "CAPTAIN",
    "myApplication": null,
    "pendingApplicationCount": 0,
    "rejectedApplicationCount": 1,
    "closedAt": "2026-11-13T12:00:00Z",
    "createdAt": "2026-11-13T08:00:00Z",
    "updatedAt": "2026-11-13T12:00:00Z"
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `MERCENARY_001` | Không có tin | Quay lại |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 403 | `TEAM_002` | Không phải đội trưởng đội tuyển | Ẩn nút |
| 409 | `MERCENARY_007` | Tin đã đóng / hết hạn | Tải lại 2.4 |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.7. Ứng tuyển — `POST /api/v1/mercenary-recruitments/{recruitmentId}/applications`

**Dùng khi:** người dùng bấm "Ứng tuyển" ở chi tiết tin (UC-ME03). Đội trưởng nhận thông báo; cuộc trò chuyện của đơn mở ngay.
**Ai gọi được:** người dùng đã đăng nhập; tin `OPEN`; trước giờ bắt đầu trận. Không body.

Không ứng tuyển được khi (BR-ME06): là thành viên đang hoạt động của **một trong hai đội** của trận (trận ngoài hệ thống: đội khai báo); đã có đơn `PENDING`/`APPROVED` ở tin này; đã có đơn `APPROVED` ở trận trùng thời gian (khoảng giờ bắt đầu – giờ kết thúc giao nhau; tính cả tin khác của chính trận này — mỗi người tối đa một đơn `APPROVED` trong một trận; kèo đã huỷ không tính).

**Response 201** — Đơn ứng tuyển (§4.3).

```json
{
  "data": {
    "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01",
    "recruitmentId": "01937090-4a5b-7c6d-8e7f-0a1b2c3d4e01",
    "status": "PENDING",
    "conversationId": "019370a2-6c7d-7e8f-a001-2b3c4d5e6f01",
    "applicant": { "userId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10", "displayName": "Trần Văn C", "avatarUrl": null, "preferredPosition": "Tiền vệ" },
    "recruitment": {
      "position": "Tiền vệ",
      "status": "OPEN",
      "team": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null },
      "match": { "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708", "startsAt": "2026-11-14T11:00:00Z", "endsAt": "2026-11-14T12:30:00Z", "status": "CONFIRMED",
                 "area": { "wardName": "Phường Sài Gòn", "provinceName": "TP. Hồ Chí Minh" }, "venue": null },
      "captainName": null
    },
    "appliedAt": "2026-11-13T09:00:00Z",
    "respondedAt": null,
    "withdrawnAt": null
  }
}
```

**Lỗi** (thứ tự kiểm từ trên xuống)

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `USER_001` | Tài khoản đã xoá (token còn hạn) | Xoá token, về đăng nhập |
| 404 | `MERCENARY_001` | Không có tin | Quay lại |
| 409 | `MERCENARY_007` | Tin đã đóng / hết hạn | Tải lại 2.4 |
| 422 | `MERCENARY_013` | Trận đã bắt đầu | Hiện "Trận đã bắt đầu"; ẩn nút |
| 409 | `MERCENARY_006` | Tin đã đủ người | Hiện "Tin tuyển đã đủ người" |
| 422 | `MERCENARY_005` | Đội tuyển đang tạm ngưng / đã giải tán | Hiện message |
| 422 | `MERCENARY_010` | Bạn là thành viên của một trong hai đội trong trận | Ẩn nút "Ứng tuyển" cho tin này |
| 409 | `MERCENARY_009` | Đã có đơn chờ duyệt / đã duyệt ở tin này | Tải lại 2.4 (`myApplication`) |
| 422 | `MERCENARY_011` | Đã được duyệt ở trận trùng giờ | Hiện message |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.8. Ứng viên của tin — `GET /api/v1/mercenary-recruitments/{recruitmentId}/applications`

**Dùng khi:** đội trưởng xem danh sách ứng viên để duyệt (UC-ME04) — hồ sơ, đánh giá, điểm uy tín.
**Ai gọi được:** đội trưởng đội tuyển (đội `INACTIVE` vẫn xem được).

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `status` | string | Không | `PENDING` (mặc định) \| `APPROVED` \| `REJECTED` \| `WITHDRAWN` \| `ALL` | `PENDING` |
| `page`, `pageSize` | integer | Không | §1.1 | `1`, `20` |

**Thứ tự sắp xếp:** `appliedAt` cũ nhất trước (đến trước xếp trước), rồi `applicationId`.

**Response 200** — mảng Ứng viên (§4.4) + `pagination`.

```json
{
  "data": [
    {
      "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01",
      "status": "PENDING",
      "conversationId": "019370a2-6c7d-7e8f-a001-2b3c4d5e6f01",
      "applicant": { "userId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10", "displayName": "Trần Văn C", "avatarUrl": null, "preferredPosition": "Tiền vệ" },
      "reputation": {
        "reliabilityScore": 82,
        "reliabilityLevel": "NORMAL",
        "completion": { "completedMatches": 3, "countedMatches": 3, "completionRate": null, "isNewcomer": true },
        "rating": { "averageRating": 4.7, "ratingCount": 3 },
        "isVerified": false
      },
      "appliedAt": "2026-11-13T09:00:00Z",
      "respondedAt": null,
      "withdrawnAt": null
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `status` ngoài enum; `page`/`pageSize` ngoài miền | Sửa tham số |
| 404 | `MERCENARY_001` | Không có tin | Quay lại |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 403 | `TEAM_002` | Không phải đội trưởng đội tuyển | Ẩn màn hình |

---

### 2.9. Đơn ứng tuyển của tôi — `GET /api/v1/profile/mercenary-applications`

**Dùng khi:** màn hình "Kèo mercenary của tôi" — đơn đang chờ, đã duyệt (thấy sân), lịch sử.
**Ai gọi được:** người dùng đã đăng nhập (chỉ đơn của mình).

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `status` | string | Không | `ACTIVE` (mặc định: `PENDING` + `APPROVED`) \| `PENDING` \| `APPROVED` \| `REJECTED` \| `WITHDRAWN` \| `ALL` | `ACTIVE` |
| `page`, `pageSize` | integer | Không | §1.1 | `1`, `20` |

**Thứ tự sắp xếp:** `appliedAt` mới nhất trước, rồi `applicationId` giảm dần.

**Response 200** — mảng Đơn ứng tuyển (§4.3) + `pagination`. Đơn `APPROVED` có `recruitment.match.venue` và `recruitment.captainName`.

```json
{
  "data": [
    {
      "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01",
      "recruitmentId": "01937090-4a5b-7c6d-8e7f-0a1b2c3d4e01",
      "status": "APPROVED",
      "conversationId": "019370a2-6c7d-7e8f-a001-2b3c4d5e6f01",
      "applicant": { "userId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10", "displayName": "Trần Văn C", "avatarUrl": null, "preferredPosition": "Tiền vệ" },
      "recruitment": {
        "position": "Tiền vệ",
        "status": "FILLED",
        "team": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null },
        "match": { "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708", "startsAt": "2026-11-14T11:00:00Z", "endsAt": "2026-11-14T12:30:00Z", "status": "CONFIRMED",
                   "area": { "wardName": "Phường Sài Gòn", "provinceName": "TP. Hồ Chí Minh" },
                   "venue": { "venueId": "01935d00-1111-7222-8333-444455556666", "name": "Sân bóng Hoa Lư", "address": "2 Đinh Tiên Hoàng, Phường Sài Gòn, TP.HCM", "latitude": 10.7883, "longitude": 106.7018 } },
        "captainName": "Nguyễn Văn A"
      },
      "appliedAt": "2026-11-13T09:00:00Z",
      "respondedAt": "2026-11-13T10:00:00Z",
      "withdrawnAt": null
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `status` ngoài enum; `page`/`pageSize` ngoài miền | Sửa tham số |

---

### 2.10. Chi tiết đơn — `GET /api/v1/mercenary-applications/{applicationId}`

**Dùng khi:** mở đơn từ thông báo, từ phòng chat (lấy trạng thái đơn để hiện nút), sau khi nhận frame `APPLICATION_UPDATED`.
**Ai gọi được:** ứng viên của đơn; đội trưởng hiện tại của đội tuyển.

**Response 200** — Đơn ứng tuyển (§4.3); với ứng viên thêm theo §1.4, với đội trưởng luôn có `venue`, `captainName`. Ví dụ như phần tử của 2.9.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `MERCENARY_002` | Không có đơn | Quay lại |
| 403 | `MERCENARY_014` | Không phải ứng viên, không phải đội trưởng đội tuyển | Quay lại |

---

### 2.11. Duyệt ứng viên — `POST /api/v1/mercenary-applications/{applicationId}/approve`

**Dùng khi:** đội trưởng bấm "Duyệt" rồi xác nhận (UC-ME04). Đủ số lượng → tin `FILLED`; đơn chờ còn lại giữ nguyên. Không huỷ duyệt được sau đó (BR-ME10).
**Ai gọi được:** đội trưởng đội tuyển; tin `OPEN`; đơn `PENDING`; trước giờ bắt đầu trận. Không body.

**Response 200**

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `applicationId` | UUID | — |
| `status` | string | Luôn `APPROVED` |
| `respondedAt` | string | Thời điểm duyệt |
| `recruitment` | object | `recruitmentId`, `status` (`OPEN` \| `FILLED`), `quantityNeeded`, `quantityApproved` |

```json
{
  "data": {
    "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01",
    "status": "APPROVED",
    "respondedAt": "2026-11-13T10:00:00Z",
    "recruitment": { "recruitmentId": "01937090-4a5b-7c6d-8e7f-0a1b2c3d4e01", "status": "FILLED", "quantityNeeded": 2, "quantityApproved": 2 }
  }
}
```

**Lỗi** (thứ tự kiểm từ trên xuống)

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `MERCENARY_002` | Không có đơn | Tải lại 2.8 |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 422 | `TEAM_015` | Đội đang tạm ngưng | Hiện message |
| 403 | `TEAM_002` | Không phải đội trưởng đội tuyển | Ẩn nút |
| 409 | `MERCENARY_007` | Tin đã đóng / hết hạn | Tải lại 2.4 |
| 409 | `MERCENARY_012` | Đơn không còn chờ duyệt (ứng viên đã rút, đã xử lý) | Tải lại 2.8 |
| 422 | `MERCENARY_013` | Trận đã bắt đầu | Hiện "Trận đã bắt đầu"; ẩn nút "Duyệt" |
| 409 | `MERCENARY_006` | Tin đã đủ người | Hiện "Tin tuyển đã đủ người"; tăng số lượng (2.5) nếu muốn duyệt thêm |
| 422 | `MERCENARY_010` | Ứng viên vừa trở thành thành viên một trong hai đội | Hiện message; đơn giữ chờ duyệt |
| 422 | `MERCENARY_011` | Ứng viên vừa được duyệt ở trận trùng giờ | Hiện message; đơn giữ chờ duyệt |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.12. Từ chối ứng viên — `POST /api/v1/mercenary-applications/{applicationId}/reject`

**Dùng khi:** đội trưởng bấm "Từ chối" rồi xác nhận (UC-ME04). Không ảnh hưởng uy tín ứng viên.
**Ai gọi được:** đội trưởng đội tuyển (đội `INACTIVE` vẫn được); đơn `PENDING`; tin chưa đóng / hết hạn. Không body.

**Response 204** — không body.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `MERCENARY_002` | Không có đơn | Tải lại 2.8 |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 403 | `TEAM_002` | Không phải đội trưởng đội tuyển | Ẩn nút |
| 409 | `MERCENARY_007` | Tin đã đóng / hết hạn (đơn chờ đã bị từ chối tự động) | Tải lại 2.8 |
| 409 | `MERCENARY_012` | Đơn không còn chờ duyệt | Tải lại 2.8 |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.13. Rút đơn / rút lui — `POST /api/v1/mercenary-applications/{applicationId}/withdraw`

**Dùng khi:** ứng viên rút đơn đang chờ (UC-ME03 A1 — không ảnh hưởng uy tín), hoặc mercenary đã duyệt bấm "Rút lui" rồi xác nhận (UC-ME05 — bị trừ điểm, §1.5). FE hiện cảnh báo mức trừ trước khi xác nhận (tính theo `startsAt`).
**Ai gọi được:** ứng viên của đơn. Rút lui sau duyệt chỉ **trước giờ bắt đầu** trận và khi kèo chưa bị huỷ / kết thúc (BR-ME11).

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `expectedStatus` | string | Có | `PENDING` \| `APPROVED` — trạng thái đơn FE **đang hiển thị** | `"APPROVED"` |

```json
{ "expectedStatus": "APPROVED" }
```

Trạng thái hiện tại khác `expectedStatus` (vd FE hiện "chờ duyệt" nhưng đơn vừa được duyệt) → `409 MERCENARY_012`, không rút, không trừ điểm — FE tải lại và cho người dùng quyết định lại.

**Response 200**

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `applicationId` | UUID | — |
| `status` | string | Luôn `WITHDRAWN` |
| `previousStatus` | string | `PENDING` \| `APPROVED` |
| `withdrawnAt` | string | — |
| `reliabilityPenalty` | integer | Số điểm bị trừ, **luôn ≥ 0** (§4.1): `0` khi rút đơn chờ; `1` / `3` / `5` khi rút lui sau duyệt (mức theo bảng, trước khi chặn [0,100]) |
| `recruitment` | object | `recruitmentId`, `status` (tin `FILLED` → `OPEN` khi rút lui), `quantityNeeded`, `quantityApproved` |

```json
{
  "data": {
    "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01",
    "status": "WITHDRAWN",
    "previousStatus": "APPROVED",
    "withdrawnAt": "2026-11-14T07:00:00Z",
    "reliabilityPenalty": 3,
    "recruitment": { "recruitmentId": "01937090-4a5b-7c6d-8e7f-0a1b2c3d4e01", "status": "OPEN", "quantityNeeded": 2, "quantityApproved": 1 }
  }
}
```

Ví dụ Scenario 7: trận 18:00 ngày mai, rút lúc 14:00 (4 giờ trước) → bị trừ 3 điểm (`reliabilityPenalty = 3`), tin `FILLED` → `OPEN`.

**Lỗi** (thứ tự kiểm từ trên xuống)

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu / sai `expectedStatus` | Sửa request |
| 404 | `MERCENARY_002` | Không có đơn | Quay lại |
| 403 | `MERCENARY_014` | Không phải ứng viên của đơn | Ẩn nút |
| 409 | `MERCENARY_012` | Trạng thái đơn khác `expectedStatus` (kể cả đơn đã bị từ chối / đã rút) | Tải lại 2.10 |
| 422 | `MERCENARY_004` | Rút lui khi kèo đã huỷ / kết thúc | Hiện message; ẩn nút |
| 422 | `MERCENARY_013` | Rút lui khi trận đã bắt đầu | Hiện "Trận đã bắt đầu" — không rút lui được |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.14. Đầu phòng chat — `GET /api/v1/conversations/{conversationId}`

**Cùng endpoint MATCH-001 `API_Spec.md` 2.19** (hợp đồng, lỗi `CHAT_002` / `CHAT_003` ở đó). **Dùng khi:** mở phòng chat của một đơn — hiện hai bên (đội tuyển + đội trưởng hiện tại, ứng viên), bên của mình, có gửi được tin không (§1.7).
**Ai gọi được:** đội trưởng hiện tại của đội tuyển; ứng viên. Mọi trạng thái đơn.

**Response 200** — Cuộc trò chuyện (MATCH-001 §4.7). Ví dụ cuộc trò chuyện theo đơn, người gọi là ứng viên:

```json
{
  "data": {
    "conversationId": "019370a2-6c7d-7e8f-a001-2b3c4d5e6f01",
    "contextType": "MERCENARY_APPLICATION",
    "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
    "matchType": null,
    "requestId": null,
    "requestStatus": null,
    "pairingId": null,
    "pairingStatus": null,
    "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01",
    "applicationStatus": "APPROVED",
    "source": "SYSTEM",
    "externalMatchId": null,
    "parties": [
      { "partyType": "TEAM", "partyId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null, "captainName": "Nguyễn Văn A" },
      { "partyType": "USER", "partyId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10", "name": "Trần Văn C", "avatarUrl": null, "captainName": null }
    ],
    "viewerPartyId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10",
    "canSend": true
  }
}
```

Cuộc trò chuyện theo đơn: `parties[0]` = đội tuyển (`TEAM`), `parties[1]` = ứng viên (`USER`, `captainName = null`); `matchType`, `requestId`, `requestStatus`, `pairingId`, `pairingStatus` luôn `null`.

---

### 2.15. Gửi tin nhắn — `POST /api/v1/conversations/{conversationId}/messages`

**Cùng endpoint MATCH-001 `API_Spec.md` 2.16** (body `{ "content": "…" }` 1..1000 ký tự, response Tin nhắn MATCH-001 §4.8, bảng lỗi ở đó). **Dùng khi:** ô chat của phòng chat đơn ứng tuyển (UC-ME09, AC-ME10).
**Ai gọi được:** hai bên của cuộc trò chuyện; đơn đang `PENDING` hoặc `APPROVED` (§1.7). Người gửi luôn là người gọi API.

Dùng chung endpoint chat của MATCH-001 (2.16–2.19 của mã đó); 2.15–2.17 ở đây chỉ ghi phần riêng của cuộc trò chuyện theo đơn. Ví dụ ứng viên gửi tin:

```json
{ "content": "Mình đá tiền vệ trái được, mấy giờ có mặt?" }
```

```json
{ "data": { "messageId": "019370b0-7d8e-7f90-b102-3c4d5e6f7a01", "conversationId": "019370a2-6c7d-7e8f-a001-2b3c4d5e6f01",
            "senderPartyType": "USER", "senderPartyId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10",
            "senderUserId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10", "senderName": "Trần Văn C", "senderAvatarUrl": null,
            "content": "Mình đá tiền vệ trái được, mấy giờ có mặt?", "isRead": false, "createdAt": "2026-11-13T09:05:00Z" } }
```

Ứng viên gửi: `senderPartyType = "USER"`, `senderPartyId` = `senderUserId` = id ứng viên; tin của đội trưởng: `senderPartyType = "TEAM"`, `senderPartyId` = id đội tuyển. Hiển thị tin của bên đội: `<tên đội> · <senderName>` (vd "FC Hoa Lư · Nguyễn Văn A").

Lỗi riêng của cuộc trò chuyện theo đơn: `409 CHAT_004` khi đơn đã bị từ chối / đã rút (chỉ xem) → khoá ô chat, vẫn cho đọc. Không có `MATCH_004`.

---

### 2.16. Lịch sử chat — `GET /api/v1/conversations/{conversationId}/messages`

**Cùng endpoint MATCH-001 `API_Spec.md` 2.17** (query `before` / `after` / `limit`, response `items` + `hasMore`, lỗi `CHAT_001` / `CHAT_002` / `CHAT_003`). **Dùng khi:** mở phòng chat (trang mới nhất), cuộn lên xem tin cũ, bù tin sau khi kết nối realtime lại.
**Ai gọi được:** hai bên; mọi trạng thái đơn.

---

### 2.17. Đánh dấu đã đọc — `POST /api/v1/conversations/{conversationId}/messages/read`

**Cùng endpoint MATCH-001 `API_Spec.md` 2.18** (không body, `204`, lỗi `CHAT_002` / `CHAT_003`). **Dùng khi:** người dùng mở / đang xem phòng chat. **Ai gọi được:** hai bên; mọi trạng thái đơn.

---

### 2.18. Đánh giá sau trận — `POST /api/v1/mercenary-applications/{applicationId}/ratings`

**Dùng khi:** sau trận, đội trưởng đánh giá **từng** mercenary đã tham gia (1–5 sao hoặc "Không đến"); mercenary đánh giá đội thuê mình (1–5 sao) (UC-ME06). Phía được xác định từ người gọi (§1.2).
**Ai gọi được:** đội trưởng đội tuyển (đội `ACTIVE` — BR-R10) hoặc mercenary của đơn. Trận đã kết thúc (kèo `ARCHIVED`/`COMPLETED`; trận ngoài hệ thống chưa huỷ và đã qua giờ kết thúc) **và** đơn còn `APPROVED` lúc trận kết thúc (BR-ME12).

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `isNoShow` | boolean | Không | Mặc định `false`; `true` = "Không đến" — **chỉ đội trưởng** | `false` |
| `score` | integer | Có khi `isNoShow = false` | Số nguyên 1..5; không gửi khi `isNoShow = true` | `5` |
| `comment` | string | Không | ≤ 500 ký tự | `"Chạy nhiều, đúng giờ"` |

```json
{ "isNoShow": false, "score": 5, "comment": "Chạy nhiều, đúng giờ" }
```

Chọn "Không đến" → sau khi gửi thành công FE gợi ý "Bạn có muốn report bùng không?" và mở 2.20 với `reportType = NOSHOW` (hai thao tác tách nhau). Đánh giá này **không** làm trận chuyển `COMPLETED`. Điểm uy tín theo §1.5: đội trưởng đánh giá **bằng sao** → mercenary được tính hoàn thành (+2, chuỗi, mốc thưởng; 5 sao thêm +1); "Không đến" → không cộng; đánh giá của mercenary cho đội không đổi điểm đội.

**Response 201** — Đánh giá mercenary (§4.5).

```json
{
  "data": {
    "ratingId": "019370c0-8e9f-7a01-c203-4d5e6f7a8b01",
    "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01",
    "source": "SYSTEM", "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708", "externalMatchId": null,
    "direction": "TEAM_TO_MERCENARY",
    "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c",
    "mercenaryUserId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10",
    "isNoShow": false,
    "score": 5,
    "comment": "Chạy nhiều, đúng giờ",
    "createdAt": "2026-11-14T13:00:00Z"
  }
}
```

**Lỗi** (thứ tự kiểm từ trên xuống)

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu `score` khi `isNoShow = false`; có `score` khi `isNoShow = true`; `score` ngoài 1..5; `comment` > 500 | Hiện lỗi cạnh field |
| 404 | `USER_001` | Tài khoản đã xoá (token còn hạn) | Xoá token, về đăng nhập |
| 404 | `MERCENARY_002` | Không có đơn | Quay lại |
| 422 | `MERCENARY_019` | Người gọi vừa là ứng viên vừa là đội trưởng đội tuyển | Hiện message |
| 403 | `MERCENARY_014` | Không phải ứng viên, không phải đội trưởng đội tuyển | Ẩn nút |
| 422 | `TEAM_015` | Phía đội: đội đang tạm ngưng | Hiện message |
| 422 | `MERCENARY_015` | Trận chưa kết thúc / bị huỷ, hoặc mercenary không còn được duyệt lúc trận kết thúc | Hiện message; trận vừa kết thúc → thử lại sau ít phút |
| 422 | `MERCENARY_016` | Mercenary chọn "Không đến" | Ẩn lựa chọn "Không đến" ở phía mercenary |
| 409 | `MERCENARY_017` | Phía mình đã đánh giá đơn này | Hiện "Bạn đã đánh giá người chơi này"; tải lại 2.19 |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.19. Xem đánh giá của đơn — `GET /api/v1/mercenary-applications/{applicationId}/ratings`

**Dùng khi:** màn hình sau trận của đơn — quyết định hiện nút "Đánh giá" (`given = null`) hay kết quả.
**Ai gọi được:** đội trưởng đội tuyển (đội `ACTIVE`) hoặc mercenary của đơn.

**Response 200**

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `applicationId` | UUID | — |
| `viewerSide` | string | `TEAM` \| `MERCENARY` — phía của người gọi |
| `given` | Đánh giá mercenary (§4.5) \| null | Đánh giá phía mình đã gửi |
| `received` | Đánh giá mercenary (§4.5) \| null | Đánh giá phía kia gửi cho mình; luôn `null` khi `given = null` (BC-R03/R04) |

```json
{
  "data": {
    "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01",
    "viewerSide": "MERCENARY",
    "given": null,
    "received": null
  }
}
```

Đơn chưa đủ điều kiện đánh giá → `200` với `given = null`, `received = null`.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `MERCENARY_002` | Không có đơn | Quay lại |
| 422 | `MERCENARY_019` | Người gọi vừa là ứng viên vừa là đội trưởng | Hiện message |
| 403 | `MERCENARY_014` | Không tham gia đơn | Ẩn mục đánh giá |
| 422 | `TEAM_015` | Phía đội: đội đang tạm ngưng | Hiện message |

---

### 2.20. Report — `POST /api/v1/mercenary-applications/{applicationId}/reports`

**Dùng khi:** đội trưởng report mercenary, hoặc mercenary / ứng viên report đội tuyển (UC-ME08); hoặc sau khi đánh giá "Không đến" và đồng ý report bùng.
**Ai gọi được:** đội trưởng đội tuyển (đội `ACTIVE` — BR-R10) hoặc ứng viên của đơn.

| Ngữ cảnh | Loại được report |
|---|---|
| Trận đã kết thúc (như 2.18) **và** đơn `APPROVED` (đã tham gia — BR-ME12) | Cả 4 loại |
| Ngoài ra, cuộc trò chuyện của đơn **đã có tin nhắn** (mọi trạng thái đơn / trận, kể cả kèo bị huỷ) | Chỉ `BAD_COMMUNICATION` |
| Còn lại | Không |

**Request body** — như REPUTATION-001 `API_Spec.md` 2.3:

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `reportType` | string | Có | `NOSHOW` \| `BAD_PLAY` \| `BAD_COMMUNICATION` \| `SKILL_MISMATCH` | `"NOSHOW"` |
| `reason` | string | Có | 1..1000 ký tự sau khi bỏ khoảng trắng đầu/cuối | `"Duyệt rồi nhưng không đến, không báo"` |

```json
{ "reportType": "NOSHOW", "reason": "Duyệt rồi nhưng không đến, không báo" }
```

Report được chấp nhận ngay và trừ điểm bên bị report (§1.5; đội: REPUTATION-001 §1.3). Mỗi phía report bên kia **tối đa một lần cho mỗi loại** trong một trận. Report bùng: bên bị report có 24 giờ để tranh chấp (REPUTATION-001 2.5).

**Response 201** — Report mercenary (§4.6) theo góc nhìn người gửi (`direction = "SENT"`).

```json
{
  "data": {
    "reportId": "019370d0-9fa0-7b12-d304-5e6f7a8b9c01",
    "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01",
    "source": "SYSTEM", "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708", "externalMatchId": null,
    "matchStartsAt": "2026-11-14T11:00:00Z",
    "direction": "SENT",
    "reporterSide": "TEAM",
    "team": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null },
    "mercenary": { "userId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10", "displayName": "Trần Văn C", "avatarUrl": null },
    "reportType": "NOSHOW",
    "reason": "Duyệt rồi nhưng không đến, không báo",
    "status": "AUTO_ACCEPTED",
    "disputeDeadlineAt": "2026-11-15T13:10:00Z",
    "canDispute": false,
    "reliabilityPenalty": null,
    "dispute": null,
    "createdAt": "2026-11-14T13:10:00Z"
  }
}
```

**Lỗi** (thứ tự kiểm từ trên xuống)

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `reportType` ngoài enum; thiếu `reason` / rỗng sau trim / > 1000 | Hiện lỗi cạnh field |
| 404 | `USER_001` | Tài khoản đã xoá (token còn hạn) | Xoá token, về đăng nhập |
| 404 | `MERCENARY_002` | Không có đơn | Quay lại |
| 422 | `MERCENARY_019` | Người gọi vừa là ứng viên vừa là đội trưởng | Hiện message |
| 403 | `MERCENARY_014` | Không tham gia đơn | Ẩn nút "Report" |
| 422 | `TEAM_016` | Người gọi là người đã giải tán đội tuyển (vẫn đọc được chat — §1.7 — nhưng đội đã giải tán không report được, BR-R10) | Ẩn nút "Report" |
| 422 | `TEAM_015` | Phía đội: đội đang tạm ngưng | Hiện message |
| 422 | `MERCENARY_020` | Chưa tham gia trận và cuộc trò chuyện chưa có tin nhắn | Ẩn nút "Report" |
| 422 | `MERCENARY_021` | Chỉ có ngữ cảnh tin nhắn mà chọn loại khác "Giao tiếp tệ" | Chỉ cho chọn "Giao tiếp tệ" |
| 409 | `MERCENARY_022` | Phía mình đã report loại này trong trận | Ẩn loại đã report (2.21 / 2.22 `SENT`) |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.21. Report liên quan mercenary của đội — `GET /api/v1/teams/{teamId}/mercenary-reports`

**Dùng khi:** màn hình "Report" của đội, tab mercenary — report đội **nhận từ** mercenary (`RECEIVED`: lý do, điểm đội bị trừ, nút "Tranh chấp" cho report bùng) và đội **đã gửi** cho mercenary (`SENT`). Report giữa hai đội vẫn ở REPUTATION-001 2.4.
**Ai gọi được:** đội trưởng của đội `ACTIVE` (BR-R10).

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `direction` | string | Không | `RECEIVED` (mặc định) \| `SENT` | `RECEIVED` |
| `page`, `pageSize` | integer | Không | §1.1 | `1`, `20` |

**Thứ tự sắp xếp:** `createdAt` mới nhất trước, rồi `reportId` giảm dần.

**Response 200** — mảng Report mercenary (§4.6) + `pagination`.

```json
{
  "data": [
    {
      "reportId": "019370d0-9fa0-7b12-d304-5e6f7a8b9c02",
      "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01",
      "source": "SYSTEM", "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708", "externalMatchId": null,
      "matchStartsAt": "2026-11-14T11:00:00Z",
      "direction": "RECEIVED",
      "reporterSide": "MERCENARY",
      "team": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null },
      "mercenary": { "userId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10", "displayName": "Trần Văn C", "avatarUrl": null },
      "reportType": "BAD_COMMUNICATION",
      "reason": "Đội trưởng nhắn tin thiếu tôn trọng",
      "status": "AUTO_ACCEPTED",
      "disputeDeadlineAt": null,
      "canDispute": false,
      "reliabilityPenalty": 2,
      "dispute": null,
      "createdAt": "2026-11-14T13:30:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `direction` ngoài enum; `page`/`pageSize` ngoài miền | Sửa tham số |
| 404 | `TEAM_001` | Không có đội | Quay lại |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 422 | `TEAM_015` | Đội đang tạm ngưng | Hiện message |
| 403 | `TEAM_002` | Không phải đội trưởng | Ẩn màn hình |

---

### 2.22. Report của tôi với vai trò mercenary — `GET /api/v1/profile/mercenary-reports`

**Dùng khi:** màn hình "Report" cá nhân — report tôi **bị** đội trưởng gửi (`RECEIVED`: lý do, điểm bị trừ, nút "Tranh chấp" cho report bùng) và tôi **đã gửi** cho đội (`SENT`).
**Ai gọi được:** người dùng đã đăng nhập (chỉ của mình).

**Query params:** như 2.21. **Thứ tự:** như 2.21.

**Response 200** — mảng Report mercenary (§4.6) + `pagination`. Ví dụ tab `RECEIVED`, report bùng còn trong hạn tranh chấp:

```json
{
  "data": [
    {
      "reportId": "019370d0-9fa0-7b12-d304-5e6f7a8b9c01",
      "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01",
      "source": "SYSTEM", "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708", "externalMatchId": null,
      "matchStartsAt": "2026-11-14T11:00:00Z",
      "direction": "RECEIVED",
      "reporterSide": "TEAM",
      "team": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null },
      "mercenary": { "userId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10", "displayName": "Trần Văn C", "avatarUrl": null },
      "reportType": "NOSHOW",
      "reason": "Duyệt rồi nhưng không đến, không báo",
      "status": "AUTO_ACCEPTED",
      "disputeDeadlineAt": "2026-11-15T13:10:00Z",
      "canDispute": true,
      "reliabilityPenalty": 5,
      "dispute": null,
      "createdAt": "2026-11-14T13:10:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

Nút "Tranh chấp" (`canDispute = true`) gọi REPUTATION-001 2.5 với `reportId`.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `direction` ngoài enum; `page`/`pageSize` ngoài miền | Sửa tham số |

---

### 2.23. Uy tín cá nhân — `GET /api/v1/users/{userId}/reputation`

**Dùng khi:** hồ sơ người chơi, thẻ ứng viên (đội trưởng xem trước khi duyệt), hồ sơ của chính mình.
**Ai gọi được:** người dùng đã đăng nhập.

**Response 200** — Uy tín cá nhân (§4.9).

```json
{
  "data": {
    "userId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10",
    "reliabilityScore": 82,
    "reliabilityLevel": "NORMAL",
    "completion": { "completedMatches": 3, "countedMatches": 3, "completionRate": null, "isNewcomer": true },
    "rating": { "averageRating": 4.7, "ratingCount": 3 },
    "verification": { "isVerified": false, "verifiedAt": null }
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `userId` sai định dạng | — |
| 404 | `USER_001` | Không có người dùng hoặc tài khoản đã xoá | Hiện "Không tìm thấy người dùng" |

---

### 2.24. Đánh giá mercenary dành cho đội — `GET /api/v1/teams/{teamId}/mercenary-ratings`

**Dùng khi:** hồ sơ đội, mục riêng "Mercenary đánh giá" (BR-ME13 — không gộp vào sao trung bình đội của REPUTATION-001 2.9).
**Ai gọi được:** người dùng đã đăng nhập; đội ở mọi trạng thái.

Chỉ liệt kê đánh giá mà đội **đã đánh giá lại** mercenary đó trong cùng đơn (giữ quy tắc "chỉ thấy đánh giá đối phương sau khi mình đã đánh giá" — BC-R03, BR-ME13).

**Query params:** `page`, `pageSize` (§1.1). **Thứ tự:** `createdAt` mới nhất trước, rồi `ratingId` giảm dần.

**Response 200** — mảng phần tử + `pagination`.

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `ratingId` | UUID | — |
| `source` | string | `SYSTEM` \| `EXTERNAL` (§4.1) |
| `matchId` | UUID \| null | Kèo trên hệ thống; `null` khi trận ngoài |
| `externalMatchId` | UUID \| null | Trận ngoài hệ thống; `null` khi kèo |
| `matchStartsAt` | string | — |
| `mercenary` | Người dùng rút gọn (§4.1) | Người đánh giá |
| `score` | integer | 1..5 |
| `comment` | string \| null | — |
| `createdAt` | string | — |

```json
{
  "data": [
    {
      "ratingId": "019370c0-8e9f-7a01-c203-4d5e6f7a8b02",
      "source": "SYSTEM", "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708", "externalMatchId": null,
      "matchStartsAt": "2026-11-14T11:00:00Z",
      "mercenary": { "userId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10", "displayName": "Trần Văn C", "avatarUrl": null },
      "score": 4,
      "comment": "Đội chơi vui, thanh toán đúng hẹn",
      "createdAt": "2026-11-14T13:20:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `teamId` sai định dạng; `page`/`pageSize` ngoài miền | Sửa tham số |
| 404 | `TEAM_001` | Không có đội | Quay lại |

---

### 2.25. Admin: hàng chờ tranh chấp mercenary — `GET /api/v1/admin/mercenary-disputes`

**Dùng khi:** admin panel, tranh chấp report bùng giữa đội trưởng và mercenary (UC-ME08 bước 4, UC-R04). Hàng chờ riêng (BR-ME14); tranh chấp giữa hai đội vẫn ở REPUTATION-001 2.6.
**Ai gọi được:** Admin.

**Query params:** như REPUTATION-001 2.6 (`status` `PENDING` mặc định \| `RESOLVED` \| `ALL`; `page`, `pageSize`). **Thứ tự:** như REPUTATION-001 2.6.

**Response 200** — mảng Tranh chấp mercenary — Admin (§4.10) + `pagination`.

```json
{
  "data": [
    {
      "disputeId": "019370e0-a0b1-7c23-e405-6f7a8b9c0d01",
      "reportId": "019370d0-9fa0-7b12-d304-5e6f7a8b9c01",
      "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01",
      "source": "SYSTEM", "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708", "externalMatchId": null,
      "matchStartsAt": "2026-11-14T11:00:00Z",
      "reporterSide": "TEAM",
      "team": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null, "status": "ACTIVE" },
      "mercenaryId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10",
      "mercenaryName": "Trần Văn C",
      "isMercenaryDeleted": false,
      "reportedById": "01935f77-3c2d-7e1f-8a9b-0c1d2e3f4a5b",
      "reportedByName": "Nguyễn Văn A",
      "isReportedByDeleted": false,
      "disputedById": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10",
      "disputedByName": "Trần Văn C",
      "isDisputedByDeleted": false,
      "status": "PENDING",
      "decision": null,
      "resolvedById": null,
      "resolvedByName": null,
      "isResolvedByDeleted": false,
      "resolvedAt": null,
      "reportCreatedAt": "2026-11-14T13:10:00Z",
      "createdAt": "2026-11-14T14:00:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 403 | `AUTH_004` | Không phải Admin | Ẩn menu quản trị |
| 400 | `VALIDATION_ERROR` | `status` ngoài enum; `page`/`pageSize` ngoài miền | Sửa bộ lọc |

---

### 2.26. Admin: chi tiết tranh chấp mercenary — `GET /api/v1/admin/mercenary-disputes/{disputeId}`

**Dùng khi:** Admin xem lý do report, bằng chứng tranh chấp, đánh giá hai bên đã gửi cho đơn, tin nhắn không có ở đây (Admin không đọc chat).
**Ai gọi được:** Admin.

**Response 200** — Tranh chấp mercenary chi tiết — Admin (§4.11).

```json
{
  "data": {
    "disputeId": "019370e0-a0b1-7c23-e405-6f7a8b9c0d01",
    "reportId": "019370d0-9fa0-7b12-d304-5e6f7a8b9c01",
    "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01",
    "source": "SYSTEM", "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708", "externalMatchId": null,
    "matchStartsAt": "2026-11-14T11:00:00Z",
    "reporterSide": "TEAM",
    "team": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null, "status": "ACTIVE" },
    "mercenaryId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10",
    "mercenaryName": "Trần Văn C",
    "isMercenaryDeleted": false,
    "reportedById": "01935f77-3c2d-7e1f-8a9b-0c1d2e3f4a5b",
    "reportedByName": "Nguyễn Văn A",
    "isReportedByDeleted": false,
    "disputedById": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10",
    "disputedByName": "Trần Văn C",
    "isDisputedByDeleted": false,
    "status": "PENDING",
    "decision": null,
    "resolvedById": null,
    "resolvedByName": null,
    "isResolvedByDeleted": false,
    "resolvedAt": null,
    "reportCreatedAt": "2026-11-14T13:10:00Z",
    "createdAt": "2026-11-14T14:00:00Z",
    "match": { "source": "SYSTEM", "sportName": "Bóng đá", "status": "COMPLETED", "startsAt": "2026-11-14T11:00:00Z", "endsAt": "2026-11-14T12:30:00Z", "venueName": "Sân bóng Hoa Lư", "opponentName": null },
    "position": "Tiền vệ",
    "reportReason": "Duyệt rồi nhưng không đến, không báo",
    "reliabilityPenalty": 5,
    "evidence": "Tôi có mặt 17:50, đã nhắn tin cho đội trưởng lúc 17:55",
    "resolutionNote": null,
    "teamReliabilityScore": 84,
    "mercenaryReliabilityScore": 77,
    "ratings": [
      { "ratingId": "019370c0-8e9f-7a01-c203-4d5e6f7a8b03", "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01",
        "source": "SYSTEM", "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708", "externalMatchId": null, "direction": "TEAM_TO_MERCENARY",
        "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "mercenaryUserId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10",
        "isNoShow": true, "score": null, "comment": "Không thấy đến", "createdAt": "2026-11-14T13:05:00Z" }
    ]
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 403 | `AUTH_004` | Không phải Admin | Ẩn menu quản trị |
| 404 | `DISPUTE_001` | Không có tranh chấp (hoặc là tranh chấp giữa hai đội — xem REPUTATION-001 2.7) | Quay lại danh sách |

---

### 2.27. Admin: quyết định tranh chấp mercenary — `PATCH /api/v1/admin/mercenary-disputes/{disputeId}/resolve`

**Dùng khi:** Admin chốt: report đúng (`CONFIRMED`) hoặc bác report (`REJECTED`). Quyết định là cuối cùng; cả hai bên nhận thông báo.
**Ai gọi được:** Admin; tranh chấp `PENDING`.

**Request body** — như REPUTATION-001 2.8 (`decision` `CONFIRMED` \| `REJECTED`; `note` 1..500 ký tự).

```json
{ "decision": "REJECTED", "note": "Tin nhắn lúc 17:55 cho thấy mercenary có mặt" }
```

| `decision` | Kết quả |
|---|---|
| `CONFIRMED` | Report → `CONFIRMED`; giữ nguyên điểm đã trừ, không trừ thêm |
| `REJECTED` | Report → `REJECTED`; bên bị report (mercenary hoặc đội) được hoàn `reliabilityPenalty` điểm, số lần bị report bùng giảm 1; chuỗi trận sạch không khôi phục |

**Response 200** — Tranh chấp mercenary chi tiết — Admin (§4.11) sau khi quyết định (`status = RESOLVED`, có `decision`, `resolutionNote`, người xử lý, điểm hiện tại sau hoàn).

```json
{
  "data": {
    "disputeId": "019370e0-a0b1-7c23-e405-6f7a8b9c0d01",
    "status": "RESOLVED",
    "decision": "REJECTED",
    "resolvedById": "01935a02-6e7f-7a1b-9c2d-5e6f7a8b9c0d",
    "resolvedByName": "Admin ArenaFlow",
    "isResolvedByDeleted": false,
    "resolvedAt": "2026-11-14T16:00:00Z",
    "resolutionNote": "Tin nhắn lúc 17:55 cho thấy mercenary có mặt",
    "mercenaryReliabilityScore": 82
  }
}
```

(Response thật có đủ mọi field của §4.11; ví dụ chỉ liệt kê field đổi.)

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 403 | `AUTH_004` | Không phải Admin | Ẩn menu quản trị |
| 400 | `VALIDATION_ERROR` | `decision` ngoài enum; thiếu `note` / rỗng sau trim / > 500 | Hiện lỗi cạnh field |
| 404 | `DISPUTE_001` | Không có tranh chấp mercenary | Quay lại danh sách |
| 409 | `DISPUTE_002` | Tranh chấp đã được quyết định | Tải lại 2.26 |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.28. Khai báo trận ngoài hệ thống — `POST /api/v1/teams/{teamId}/external-matches`

**Dùng khi:** đội trưởng chọn "Tuyển mercenary" → "Trận ngoài hệ thống" (UC-ME01 A1, AC-ME11): chọn sân từ danh sách địa điểm (chưa có thì thêm địa điểm trước — VENUE-001 2.1), nhập giờ, trình độ, tên đối thủ (tuỳ chọn), rồi vị trí + số lượng. Một lần gửi tạo trận ngoài và mỗi vị trí một tin `OPEN`. Sân của trận ngoài được tính là "được chọn" cho cảnh báo địa điểm lâu không dùng (BC-V05 — `lastSelectedAt` của địa điểm cập nhật, VENUE-001 §4.2).
**Ai gọi được:** đội trưởng của `teamId`; đội `ACTIVE`.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `venueId` | string (UUID) | Có | Địa điểm đang hoạt động (VENUE-001) | `"01935d00-1111-7222-8333-444455556666"` |
| `startsAt` | string (thời gian) | Có | ≥ hiện tại + 1 giờ | `"2026-11-20T11:00:00Z"` |
| `endsAt` | string (thời gian) | Có | Sau `startsAt`, không quá 12 giờ | `"2026-11-20T12:30:00Z"` |
| `skillLevels` | string[] | Không | Tối đa 3 trong 9 nhãn trình độ (như kèo — MATCH-001 2.1); rỗng / vắng = mọi trình độ | `["Trung bình"]` |
| `opponentName` | string | Không | ≤ 100 ký tự; `""` = không nhập | `"FC Phường 5"` |
| `positions` | array | Có | Như 2.1: 1..4 phần tử, không trùng vị trí; `quantity` 1..10 | — |

```json
{
  "venueId": "01935d00-1111-7222-8333-444455556666",
  "startsAt": "2026-11-20T11:00:00Z",
  "endsAt": "2026-11-20T12:30:00Z",
  "skillLevels": ["Trung bình"],
  "opponentName": "FC Phường 5",
  "positions": [ { "position": "Thủ môn", "quantity": 1 } ]
}
```

**Response 201** — Trận ngoài (§4.12) kèm `recruitments`: mảng Tin tuyển (§4.2, góc nhìn đội trưởng; `match.source = "EXTERNAL"`).

```json
{
  "data": {
    "externalMatchId": "019371f0-1a2b-7c3d-8e4f-5a6b7c8d9e01",
    "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c",
    "status": "SCHEDULED",
    "startsAt": "2026-11-20T11:00:00Z",
    "endsAt": "2026-11-20T12:30:00Z",
    "skillLevels": ["Trung bình"],
    "opponentName": "FC Phường 5",
    "venue": { "venueId": "01935d00-1111-7222-8333-444455556666", "name": "Sân bóng Hoa Lư", "address": "2 Đinh Tiên Hoàng, Phường Sài Gòn, TP.HCM", "latitude": 10.7883, "longitude": 106.7018 },
    "area": { "wardName": "Phường Sài Gòn", "provinceName": "TP. Hồ Chí Minh" },
    "hasApprovedMercenary": false,
    "cancelledAt": null,
    "createdAt": "2026-11-14T08:00:00Z",
    "updatedAt": "2026-11-14T08:00:00Z",
    "recruitments": [
      {
        "recruitmentId": "01937200-2b3c-7d4e-9f50-6a7b8c9d0e01",
        "matchId": null,
        "team": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null },
        "position": "Thủ môn",
        "quantityNeeded": 1,
        "quantityApproved": 0,
        "status": "OPEN",
        "match": { "source": "EXTERNAL", "externalMatchId": "019371f0-1a2b-7c3d-8e4f-5a6b7c8d9e01", "opponentName": "FC Phường 5",
                   "startsAt": "2026-11-20T11:00:00Z", "endsAt": "2026-11-20T12:30:00Z", "format": null, "skillLevels": ["Trung bình"],
                   "area": { "wardName": "Phường Sài Gòn", "provinceName": "TP. Hồ Chí Minh" }, "distanceKm": null,
                   "venue": { "venueId": "01935d00-1111-7222-8333-444455556666", "name": "Sân bóng Hoa Lư", "address": "2 Đinh Tiên Hoàng, Phường Sài Gòn, TP.HCM", "latitude": 10.7883, "longitude": 106.7018 } },
        "captainName": "Nguyễn Văn A",
        "viewerRole": "CAPTAIN",
        "myApplication": null,
        "pendingApplicationCount": 0,
        "closedAt": null,
        "createdAt": "2026-11-14T08:00:00Z",
        "updatedAt": "2026-11-14T08:00:00Z"
      }
    ]
  }
}
```

**Lỗi** (thứ tự kiểm từ trên xuống)

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu field bắt buộc; giờ sai ràng buộc; `skillLevels` > 3 hoặc ngoài danh sách; `positions` sai như 2.1 | Hiện lỗi cạnh field |
| 404 | `TEAM_001` | Không có đội | Quay lại |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 422 | `TEAM_015` | Đội đang tạm ngưng | Hiện message |
| 403 | `TEAM_002` | Không phải đội trưởng | Ẩn chức năng |
| 404 | `VENUE_001` | Không có địa điểm | Chọn lại sân |
| 422 | `VENUE_002` | Địa điểm đã ngừng hoạt động | Chọn sân khác |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.29. Trận ngoài của đội — `GET /api/v1/teams/{teamId}/external-matches`

**Dùng khi:** màn "Tuyển mercenary" của đội, mục trận ngoài hệ thống.
**Ai gọi được:** đội trưởng của `teamId` (đội `INACTIVE` vẫn xem được).

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `status` | string | Không | `UPCOMING` (mặc định: `SCHEDULED` chưa tới giờ kết thúc) \| `ENDED` (`SCHEDULED` đã qua giờ kết thúc) \| `CANCELLED` \| `ALL` | `UPCOMING` |
| `page`, `pageSize` | integer | Không | §1.1 | `1`, `20` |

**Thứ tự sắp xếp:** `UPCOMING` → `startsAt` tăng dần; còn lại → `startsAt` giảm dần; rồi `externalMatchId`.

**Response 200** — mảng Trận ngoài (§4.12, không có `recruitments`) + `pagination`.

```json
{
  "data": [
    {
      "externalMatchId": "019371f0-1a2b-7c3d-8e4f-5a6b7c8d9e01",
      "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c",
      "status": "SCHEDULED",
      "startsAt": "2026-11-20T11:00:00Z",
      "endsAt": "2026-11-20T12:30:00Z",
      "skillLevels": ["Trung bình"],
      "opponentName": "FC Phường 5",
      "venue": { "venueId": "01935d00-1111-7222-8333-444455556666", "name": "Sân bóng Hoa Lư", "address": "2 Đinh Tiên Hoàng, Phường Sài Gòn, TP.HCM", "latitude": 10.7883, "longitude": 106.7018 },
      "area": { "wardName": "Phường Sài Gòn", "provinceName": "TP. Hồ Chí Minh" },
      "hasApprovedMercenary": true,
      "cancelledAt": null,
      "createdAt": "2026-11-14T08:00:00Z",
      "updatedAt": "2026-11-14T08:00:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `status` ngoài enum; `page`/`pageSize` ngoài miền | Sửa tham số |
| 404 | `TEAM_001` | Không có đội | Quay lại |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 403 | `TEAM_002` | Không phải đội trưởng | Ẩn mục |

Tin tuyển của trận ngoài: 2.2 (lọc theo đội; phần tử có `match.externalMatchId`).

---

### 2.30. Chi tiết trận ngoài — `GET /api/v1/external-matches/{externalMatchId}`

**Dùng khi:** mở màn sửa / huỷ trận ngoài. `hasApprovedMercenary = true` → khoá ô sân, giờ, trình độ.
**Ai gọi được:** đội trưởng hiện tại của đội khai báo (đội `INACTIVE` vẫn xem được). Người khác xem thông tin trận qua tin tuyển (2.4).

**Response 200** — Trận ngoài (§4.12, không có `recruitments`). Ví dụ như phần tử của 2.29.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `MERCENARY_024` | Không có trận ngoài | Quay lại |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 403 | `TEAM_002` | Không phải đội trưởng của đội khai báo | Quay lại |

---

### 2.31. Sửa trận ngoài — `PATCH /api/v1/external-matches/{externalMatchId}`

**Dùng khi:** đội trưởng sửa thông tin trận ngoài (BR-ME19). Sân, giờ, trình độ chỉ sửa được khi **chưa** có mercenary nào được duyệt (`hasApprovedMercenary = false`) và trận chưa bắt đầu; tên đối thủ sửa được tới khi trận kết thúc.
**Ai gọi được:** đội trưởng của đội khai báo; đội `ACTIVE`; trận `SCHEDULED`, chưa tới giờ kết thúc.

**Request body** — cập nhật một phần; field vắng / `null` = giữ nguyên; ràng buộc như 2.28.

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `venueId` | string (UUID) | Không | Như 2.28 | `"01935d00-1111-7222-8333-444455556667"` |
| `startsAt`, `endsAt` | string (thời gian) | Không | Như 2.28 (kiểm trên cặp giờ sau khi áp thay đổi) | `"2026-11-20T12:00:00Z"` |
| `skillLevels` | string[] | Không | Như 2.28; `[]` = mọi trình độ | `[]` |
| `opponentName` | string | Không | ≤ 100; `""` = xoá | `"FC Phường 6"` |

```json
{ "startsAt": "2026-11-20T12:00:00Z", "endsAt": "2026-11-20T13:30:00Z" }
```

Gửi trùng giá trị hiện tại không tính là thay đổi. Đổi sân → sân mới được tính là "được chọn" (BC-V05); sân cũ giữ nguyên `lastSelectedAt`. Tin tuyển của trận hiển thị giờ / sân mới ngay.

**Response 200** — Trận ngoài (§4.12) sau khi sửa.

**Lỗi** (thứ tự kiểm từ trên xuống)

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Field sai ràng buộc | Hiện lỗi cạnh field |
| 404 | `MERCENARY_024` | Không có trận ngoài | Quay lại |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 422 | `TEAM_015` | Đội đang tạm ngưng | Hiện message |
| 403 | `TEAM_002` | Không phải đội trưởng | Ẩn nút |
| 422 | `MERCENARY_004` | Trận ngoài đã huỷ hoặc đã kết thúc | Tải lại 2.30 |
| 422 | `MERCENARY_013` | Đổi sân / giờ / trình độ khi trận đã bắt đầu | Chỉ cho sửa tên đối thủ |
| 409 | `MERCENARY_025` | Đổi sân / giờ / trình độ khi đã có mercenary được duyệt | Tải lại 2.30; khoá các ô đó |
| 404 | `VENUE_001` | Không có địa điểm mới | Chọn lại |
| 422 | `VENUE_002` | Địa điểm mới đã ngừng hoạt động | Chọn sân khác |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.32. Huỷ trận ngoài — `POST /api/v1/external-matches/{externalMatchId}/cancel`

**Dùng khi:** đội trưởng bấm "Huỷ trận ngoài" rồi xác nhận (BR-ME19, BR-ME09). Mọi tin `OPEN`/`FILLED` của trận → `CLOSED`; đơn chờ → `REJECTED`; đơn đã duyệt giữ nguyên `APPROVED` (lịch sử); không ai bị trừ điểm, đội không bị trừ điểm; ứng viên và mercenary nhận thông báo. Sau khi huỷ không đánh giá được nhau; chat của đơn đã duyệt vẫn dùng được (§1.7).
**Ai gọi được:** đội trưởng của đội khai báo (đội `INACTIVE` vẫn được); trận `SCHEDULED`, **trước giờ bắt đầu** (như huỷ kèo — BR-M11; BR-ME19). Không body.

**Response 200**

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `externalMatchId` | UUID | — |
| `status` | string | Luôn `CANCELLED` |
| `cancelledAt` | string | — |
| `closedRecruitmentCount` | integer | Số tin vừa đóng |
| `rejectedApplicationCount` | integer | Số đơn chờ vừa bị từ chối |

```json
{ "data": { "externalMatchId": "019371f0-1a2b-7c3d-8e4f-5a6b7c8d9e01", "status": "CANCELLED", "cancelledAt": "2026-11-15T09:00:00Z", "closedRecruitmentCount": 1, "rejectedApplicationCount": 2 } }
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `MERCENARY_024` | Không có trận ngoài | Quay lại |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 403 | `TEAM_002` | Không phải đội trưởng | Ẩn nút |
| 422 | `MERCENARY_004` | Trận ngoài đã huỷ hoặc đã kết thúc | Tải lại 2.30 |
| 422 | `MERCENARY_013` | Trận đã bắt đầu — không huỷ được | Hiện "Trận đã bắt đầu"; ẩn nút "Huỷ" |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.33. Thêm vị trí cho trận ngoài — `POST /api/v1/external-matches/{externalMatchId}/mercenary-recruitments`

**Dùng khi:** đội trưởng tuyển thêm vị trí khác cho trận ngoài đã khai báo (như 2.1 với kèo trên hệ thống).
**Ai gọi được:** đội trưởng của đội khai báo; đội `ACTIVE`; trận `SCHEDULED`, chưa tới giờ bắt đầu.

**Request body:** `positions` như 2.1 (không có `teamId` — đội là đội khai báo).

```json
{ "positions": [ { "position": "Hậu vệ", "quantity": 2 } ] }
```

**Response 201** — mảng Tin tuyển (§4.2), cùng thứ tự `positions`; ví dụ như phần tử `recruitments` của 2.28.

**Lỗi** (thứ tự kiểm từ trên xuống)

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `positions` sai như 2.1 | Hiện lỗi cạnh field |
| 404 | `MERCENARY_024` | Không có trận ngoài | Quay lại |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 422 | `TEAM_015` | Đội đang tạm ngưng | Hiện message |
| 403 | `TEAM_002` | Không phải đội trưởng | Ẩn nút |
| 422 | `MERCENARY_004` | Trận ngoài đã huỷ hoặc đã kết thúc | Tải lại 2.30 |
| 422 | `MERCENARY_013` | Trận đã bắt đầu | Ẩn nút |
| 409 | `MERCENARY_023` | Đã có tin đang mở cho vị trí này | Tăng số lượng tin đó (2.5) |
| 409 | `MATCH_014` | §1.1 | Thử lại |

## 3. Hành động theo trạng thái

Trạng thái là trạng thái hiển thị (§1.3). Đội tuyển tạm ngưng / giải tán: §1.2.

### 3.1. Tin tuyển

| Tin `status` | Đội trưởng đội tuyển | Người dùng khác |
|---|---|---|
| `OPEN` — trước giờ bắt đầu trận | Xem (2.4), sửa số lượng (2.5), đóng (2.6), xem ứng viên (2.8), duyệt / từ chối (2.11, 2.12) | Xem (2.4), ứng tuyển (2.7) nếu đủ điều kiện (BR-ME06) |
| `OPEN`, `FILLED` — từ giờ bắt đầu trận (chưa tới giờ kết thúc) | Xem, sửa số lượng, đóng, xem ứng viên, từ chối (không duyệt — `MERCENARY_013`) | Xem; không ứng tuyển (`MERCENARY_013`); tin không hiện ở 2.3 |
| `FILLED` — trước giờ bắt đầu trận | Xem, sửa số lượng (tăng → `OPEN`), đóng, xem ứng viên, **từ chối** đơn chờ (không duyệt được — `MERCENARY_006`) | Xem; không ứng tuyển (`MERCENARY_006`) |
| `CLOSED`, `EXPIRED` | Xem, xem ứng viên | Xem |

Kèo của tin: chỉ tạo tin khi kèo `CONFIRMED` và chưa tới giờ kết thúc (2.1); mỗi (kèo, đội, vị trí) tối đa một tin `OPEN`/`FILLED`.

### 3.2. Đơn ứng tuyển

| Đơn `status` | Ứng viên | Đội trưởng đội tuyển |
|---|---|---|
| `PENDING` | Xem (2.10), rút (2.13 `expectedStatus = PENDING`, không trừ điểm), chat (2.14–2.17), report "Giao tiếp tệ" khi đã có tin nhắn (2.20) | Xem, duyệt (khi tin `OPEN`), từ chối, chat, report "Giao tiếp tệ" khi đã có tin nhắn |
| `APPROVED` — trước giờ bắt đầu, kèo chưa huỷ | Xem (thấy sân, đội trưởng), **rút lui** (2.13 `expectedStatus = APPROVED`, trừ điểm), chat, report "Giao tiếp tệ" khi đã có tin nhắn | Xem, chat, report "Giao tiếp tệ" khi đã có tin nhắn (không huỷ duyệt — BR-ME10) |
| `APPROVED` — trận đang diễn ra / `CONFIRMED` sau giờ bắt đầu | Xem, chat (không rút — `MERCENARY_013`) | Xem, chat |
| `APPROVED` — trận `ARCHIVED`/`COMPLETED` | Xem, chat, đánh giá đội (2.18), report đội mọi loại (2.20) | Xem, chat, đánh giá mercenary (sao hoặc "Không đến"), report mercenary mọi loại |
| `APPROVED` — kèo `CANCELLED` | Xem, chat, report "Giao tiếp tệ" khi đã có tin nhắn (không đánh giá, không rút) | Như ứng viên |
| `REJECTED`, `WITHDRAWN` | Xem, đọc chat (chỉ xem), report "Giao tiếp tệ" khi đã có tin nhắn | Như ứng viên |

**Gợi ý hiển thị nút:** khoá ô chat khi `canSend = false` (2.14); ẩn "Đánh giá" khi `given ≠ null` (2.19); ẩn loại report đã gửi (2.21 / 2.22 `SENT`); hiện "Rút lui" kèm mức trừ dự kiến khi đơn `APPROVED` và giờ hiện tại < `startsAt`.

### 3.3. Report và tranh chấp

| Report `status` | Bên bị report | Bên report | Admin |
|---|---|---|---|
| `AUTO_ACCEPTED` (`NOSHOW`, trước `disputeDeadlineAt`) | Tranh chấp (REPUTATION-001 2.5) | Xem (2.21 / 2.22) | — |
| `AUTO_ACCEPTED` (loại khác — trạng thái cuối) | Xem | Xem | — |
| `DISPUTED` | Xem | Xem | Xem (2.25, 2.26), quyết định (2.27) |
| `CONFIRMED`, `REJECTED` | Xem | Xem | Xem |

### 3.4. Trận ngoài hệ thống

| Trận ngoài | Đội trưởng của đội khai báo | Người khác |
|---|---|---|
| `SCHEDULED`, trước giờ bắt đầu | Xem (2.30), sửa (2.31 — sân / giờ / trình độ chỉ khi chưa có mercenary được duyệt), huỷ (2.32), thêm vị trí (2.33); thao tác tin như §3.1 | Xem qua tin (2.4), ứng tuyển (2.7) |
| `SCHEDULED`, từ giờ bắt đầu tới giờ kết thúc | Xem, sửa tên đối thủ (không huỷ — `MERCENARY_013`); tin như §3.1 dòng "từ giờ bắt đầu" | Xem qua tin |
| `SCHEDULED`, đã qua giờ kết thúc | Xem; đánh giá / report mercenary của đơn (2.18, 2.20) | Mercenary: đánh giá / report đội |
| `CANCELLED` | Xem | Xem; chat đơn đã duyệt (§1.7) |

## 4. Kiểu dữ liệu dùng chung

### 4.1. Enum và kiểu rút gọn

| Kiểu / enum | Giá trị | Ý nghĩa |
|---|---|---|
| `position` | `Thủ môn` \| `Hậu vệ` \| `Tiền vệ` \| `Tiền đạo` | Vị trí (nhãn tiếng Việt) |
| Tin `status` | `OPEN` \| `FILLED` \| `CLOSED` \| `EXPIRED` | §1.3 |
| Đơn `status` | `PENDING` \| `APPROVED` \| `REJECTED` \| `WITHDRAWN` | §1.3 |
| `viewerRole` | `CAPTAIN` \| `APPLICANT` \| `null` | Vai trò người xem với tin: đội trưởng đội tuyển / có đơn đang hoạt động ở tin / khác |
| `direction` (đánh giá) | `TEAM_TO_MERCENARY` \| `MERCENARY_TO_TEAM` | Chiều đánh giá |
| `viewerSide`, `reporterSide` | `TEAM` \| `MERCENARY` | Phía đội tuyển / phía mercenary |
| `direction` (report) | `RECEIVED` \| `SENT` | Theo góc nhìn đội (2.21) / người gọi (2.22) |
| `reportType`, report `status`, tranh chấp `status`, `decision`, `reliabilityLevel` | Như REPUTATION-001 `API_Spec.md` §4.1 | — |
| `contextType` | `MERCENARY_APPLICATION` \| `TEAM_MATCH` | Loại cuộc trò chuyện — `TEAM_MATCH`: chat giữa hai đội trong kèo (MATCH-001 §1.6). Có thể thêm giá trị ở bản sau — FE bỏ qua giá trị lạ |
| `partyType`, `senderPartyType` | `TEAM` \| `USER` | Bên của cuộc trò chuyện |
| `format`, `match.status` | Như MATCH-001 `API_Spec.md` §4.1 | — |
| Đội rút gọn | `teamId`, `name`, `avatarUrl` (+ `status` chỉ ở API Admin) | — |
| Người dùng rút gọn | `userId`, `displayName` (`"Người dùng đã xóa"` khi đã xoá), `avatarUrl` (`null` khi đã xoá) | — |
| `area` | `wardName`, `provinceName` | Phường/xã, tỉnh/thành của sân; cả object `null` khi sân chưa có phường |
| `venue` | `venueId`, `name`, `address`, `latitude`, `longitude` | Chỉ trả theo §1.4 |
| `reliabilityPenalty` (mọi response) | integer ≥ 0 | **Số điểm bị trừ** (không mang dấu âm); `0` = không trừ |
| `match.source` | `SYSTEM` \| `EXTERNAL` | Kèo trên hệ thống / trận ngoài hệ thống |
| Trận ngoài `status` | `SCHEDULED` \| `CANCELLED` | Đã khai báo / đội trưởng đã huỷ; kết thúc tính theo `endsAt` |

### 4.2. Tin tuyển — response 2.1–2.6

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `recruitmentId` | UUID | — |
| `matchId` | UUID \| null | Kèo trên hệ thống; `null` khi tin thuộc trận ngoài (`match.externalMatchId`) |
| `team` | Đội rút gọn | Đội tuyển |
| `position` | string | §4.1 |
| `quantityNeeded`, `quantityApproved` | integer | Số cần / số đang được duyệt |
| `status` | string | Trạng thái hiển thị (§1.3) |
| `match.source` | string | `SYSTEM` (kèo trên hệ thống) \| `EXTERNAL` (trận ngoài hệ thống — BR-ME19) |
| `match.externalMatchId` | UUID \| null | Trận ngoài (`EXTERNAL`); `matchId` của tin là `null` khi `EXTERNAL` |
| `match.opponentName` | string \| null | Tên đối thủ khai báo của trận ngoài; `null` với `SYSTEM` |
| `match.startsAt`, `match.endsAt` | string | Giờ bắt đầu / kết thúc dự kiến |
| `match.format` | string \| null | Định dạng trận |
| `match.skillLevels` | string[] | Trình độ mong muốn của kèo (rỗng = mọi trình độ) |
| `match.area` | object \| null | §4.1 |
| `match.distanceKm` | number \| null | Khoảng cách từ toạ độ đã gửi (2.3 có GPS, 2.4 có `lat`/`lng`); làm tròn 0,5 km (§1.4) |
| `match.venue` | object \| null | §1.4 |
| `captainName` | string \| null | Đội trưởng hiện tại — §1.4 (chat luôn hiện — 2.14) |
| `viewerRole` | string \| null | §4.1 |
| `myApplication` | object \| null | Đơn `PENDING`/`APPROVED` của người xem ở tin: `applicationId`, `status`, `conversationId` |
| `pendingApplicationCount` | integer \| null | Số đơn chờ — chỉ khi người xem là đội trưởng |
| `rejectedApplicationCount` | integer | **Chỉ ở 2.6** — số đơn chờ vừa bị từ chối |
| `closedAt` | string \| null | Thời điểm đóng (`CLOSED`) |
| `createdAt`, `updatedAt` | string | — |

### 4.3. Đơn ứng tuyển — response 2.7, 2.9, 2.10

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `applicationId`, `recruitmentId` | UUID | — |
| `status` | string | Trạng thái hiển thị (§1.3) |
| `conversationId` | UUID | Cuộc trò chuyện của đơn (§1.7) |
| `applicant` | Người dùng rút gọn + `preferredPosition` (string \| null) | Ứng viên |
| `recruitment.position`, `recruitment.status` | string | Tin của đơn |
| `recruitment.team` | Đội rút gọn | — |
| `recruitment.match` | object | `source`, `matchId` (null khi `EXTERNAL`), `externalMatchId`, `opponentName`, `startsAt`, `endsAt`, `status` (trạng thái kèo, hoặc `SCHEDULED`/`CANCELLED` của trận ngoài), `area`, `venue` (§1.4) |
| `recruitment.captainName` | string \| null | §1.4 |
| `appliedAt` | string | — |
| `respondedAt` | string \| null | Thời điểm duyệt / từ chối |
| `withdrawnAt` | string \| null | Thời điểm rút |

### 4.4. Ứng viên — phần tử 2.8

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `applicationId` | UUID | — |
| `status` | string | Trạng thái hiển thị |
| `conversationId` | UUID | — |
| `applicant` | Người dùng rút gọn + `preferredPosition` | — |
| `reputation.reliabilityScore`, `reputation.reliabilityLevel` | integer, string | §1.5 |
| `reputation.completion` | object | Như §4.9 `completion` |
| `reputation.rating` | object | Như §4.9 `rating` |
| `reputation.isVerified` | boolean | Huy hiệu cá nhân |
| `appliedAt`, `respondedAt`, `withdrawnAt` | string \| null | — |

### 4.5. Đánh giá mercenary — response 2.18, 2.19, 2.26

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `ratingId`, `applicationId` | UUID | — |
| `source` | string | `SYSTEM` \| `EXTERNAL` (§4.1) |
| `matchId` | UUID \| null | Kèo trên hệ thống; `null` khi trận ngoài |
| `externalMatchId` | UUID \| null | Trận ngoài hệ thống; `null` khi kèo |
| `direction` | string | `TEAM_TO_MERCENARY` \| `MERCENARY_TO_TEAM` |
| `teamId` | UUID | Đội tuyển |
| `mercenaryUserId` | UUID | Mercenary |
| `isNoShow` | boolean | `true` = "Không đến" (chỉ `TEAM_TO_MERCENARY`) |
| `score` | integer \| null | 1..5; `null` khi `isNoShow = true` |
| `comment` | string \| null | — |
| `createdAt` | string | — |

### 4.6. Report mercenary — response 2.20–2.22

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `reportId`, `applicationId` | UUID | — |
| `source` | string | `SYSTEM` \| `EXTERNAL` (§4.1) |
| `matchId` | UUID \| null | Kèo trên hệ thống; `null` khi trận ngoài |
| `externalMatchId` | UUID \| null | Trận ngoài hệ thống; `null` khi kèo |
| `matchStartsAt` | string | — |
| `direction` | string | `RECEIVED` \| `SENT` theo góc nhìn người xem |
| `reporterSide` | string | `TEAM` (đội trưởng report mercenary) \| `MERCENARY` (mercenary report đội) |
| `team` | Đội rút gọn | Đội tuyển |
| `mercenary` | Người dùng rút gọn | Mercenary / ứng viên |
| `reportType`, `reason`, `status` | string | Như REPUTATION-001 §4.4 |
| `disputeDeadlineAt` | string \| null | Lúc report + 24 giờ; `null` nếu không phải `NOSHOW` |
| `canDispute` | boolean | `true` khi `RECEIVED`, `NOSHOW`, `AUTO_ACCEPTED`, trước hạn → hiện nút "Tranh chấp" (REPUTATION-001 2.5) |
| `reliabilityPenalty` | integer \| null | `RECEIVED`: số điểm thực bị trừ; `SENT`: `null` |
| `dispute` | object \| null | Như REPUTATION-001 §4.4 (`evidence` = `null` ở `SENT`) |
| `createdAt` | string | — |

### 4.7. Cuộc trò chuyện — response 2.14

MATCH-001 `API_Spec.md` §4.7 (v2.4 — shape chung mọi cuộc trò chuyện; cuộc trò chuyện theo đơn dùng `applicationId`, `applicationStatus`, `source`, `matchId` / `externalMatchId`; field của chat hai đội = `null` — ví dụ 2.14).

### 4.8. Tin nhắn — response 2.15, 2.16, frame `MESSAGE_CREATED`

MATCH-001 `API_Spec.md` §4.8. `senderPartyType`: `TEAM` (đội trưởng gửi) \| `USER` (ứng viên gửi). Hiển thị tin của bên đội: `<tên đội> · <senderName>` (vd "FC Hoa Lư · Nguyễn Văn A").

### 4.9. Uy tín cá nhân — response 2.23

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `userId` | UUID | — |
| `reliabilityScore` | integer | 0..100 |
| `reliabilityLevel` | string | Như REPUTATION-001 §4.1 |
| `completion.completedMatches` | integer | Tử số §1.6 |
| `completion.countedMatches` | integer | Mẫu số §1.6 |
| `completion.completionRate` | integer \| null | Phần trăm làm tròn; `null` khi `countedMatches < 5` |
| `completion.isNewcomer` | boolean | `countedMatches < 5` |
| `rating.averageRating` | number \| null | 1 chữ số thập phân; `null` khi chưa có sao |
| `rating.ratingCount` | integer | Số đánh giá có sao |
| `verification.isVerified` | boolean | Huy hiệu cá nhân (§1.6) |
| `verification.verifiedAt` | string \| null | Thời điểm cấp |

### 4.10. Tranh chấp mercenary — Admin (2.25)

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `disputeId`, `reportId`, `applicationId` | UUID | — |
| `source` | string | `SYSTEM` \| `EXTERNAL` (§4.1) |
| `matchId` | UUID \| null | Kèo trên hệ thống; `null` khi trận ngoài |
| `externalMatchId` | UUID \| null | Trận ngoài hệ thống; `null` khi kèo |
| `matchStartsAt` | string | — |
| `reporterSide` | string | `TEAM` (đội report mercenary — mercenary tranh chấp) \| `MERCENARY` (mercenary report đội — đội trưởng tranh chấp) |
| `team` | Đội rút gọn kèm `status` | Đội tuyển |
| `mercenaryId`, `mercenaryName`, `isMercenaryDeleted` | UUID, string, boolean | Mercenary (tên thật — ADR-0002) |
| `reportedById`, `reportedByName`, `isReportedByDeleted` | UUID, string, boolean | Người đã gửi report |
| `disputedById`, `disputedByName`, `isDisputedByDeleted` | UUID, string, boolean | Người đã gửi tranh chấp |
| `status`, `decision`, `resolvedById`, `resolvedByName`, `isResolvedByDeleted`, `resolvedAt`, `reportCreatedAt`, `createdAt` | — | Như REPUTATION-001 §4.6 |

### 4.11. Tranh chấp mercenary chi tiết — Admin (2.26, 2.27)

Mọi field §4.10, thêm:

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `match` | object | `source`, `sportName` (`null` với trận ngoài), `status`, `startsAt`, `endsAt`, `venueName`, `opponentName` (trận ngoài) |
| `position` | string | Vị trí của tin |
| `reportReason` | string | Lý do report |
| `reliabilityPenalty` | integer | Điểm bên bị report đã thực bị trừ cho report này |
| `evidence` | string | Bằng chứng tranh chấp |
| `resolutionNote` | string \| null | Lý do quyết định |
| `teamReliabilityScore`, `mercenaryReliabilityScore` | integer | Điểm uy tín **hiện tại** của đội và mercenary |
| `ratings` | mảng Đánh giá mercenary (§4.5) | Đánh giá hai phía đã gửi cho đơn (0–2), cũ nhất trước |

### 4.12. Trận ngoài hệ thống — response 2.28–2.32

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `externalMatchId`, `teamId` | UUID | Trận / đội khai báo |
| `status` | string | `SCHEDULED` \| `CANCELLED` |
| `startsAt`, `endsAt` | string | Giờ khai báo |
| `skillLevels` | string[] | Trình độ (rỗng = mọi trình độ) |
| `opponentName` | string \| null | Tên đối thủ |
| `venue` | object | `venueId`, `name`, `address`, `latitude`, `longitude` (chỉ đội trưởng gọi được các endpoint này) |
| `area` | object \| null | §4.1 |
| `hasApprovedMercenary` | boolean | Có tin của trận đang có mercenary được duyệt → khoá sửa sân / giờ / trình độ |
| `cancelledAt` | string \| null | — |
| `createdAt`, `updatedAt` | string | — |
| `recruitments` | mảng Tin tuyển (§4.2) | **Chỉ ở 2.28** |

## 5. Mã lỗi

Mã mới của MERCENARY-001:

| Mã | HTTP | Message | Endpoint |
|---|---|---|---|
| `MERCENARY_001` | 404 | "Không tìm thấy tin tuyển" | 2.4–2.8 |
| `MERCENARY_002` | 404 | "Không tìm thấy đơn ứng tuyển" | 2.10–2.13, 2.18–2.20 |
| `MERCENARY_003` | 422 | "Chỉ có thể tuyển mercenary sau khi kèo đã ghép" | 2.1 |
| `MERCENARY_004` | 422 | "Kèo đã kết thúc hoặc đã bị huỷ" | 2.1, 2.13, 2.31–2.33 |
| `MERCENARY_005` | 422 | "Đội tuyển hiện không hoạt động" | 2.7 |
| `MERCENARY_006` | 409 | "Tin tuyển đã đủ người" | 2.7, 2.11 |
| `MERCENARY_007` | 409 | "Tin tuyển đã đóng hoặc hết hạn" | 2.5–2.7, 2.11, 2.12 |
| `MERCENARY_008` | 422 | "Số lượng không được thấp hơn số người đã duyệt" | 2.5 |
| `MERCENARY_009` | 409 | "Bạn đã ứng tuyển tin này" | 2.7 |
| `MERCENARY_010` | 422 | "Thành viên của đội trong trận không thể làm mercenary cho trận này" | 2.7, 2.11 |
| `MERCENARY_011` | 422 | "Đã có đơn được duyệt ở trận trùng giờ" | 2.7, 2.11 |
| `MERCENARY_012` | 409 | "Trạng thái đơn đã thay đổi, vui lòng tải lại" | 2.11–2.13 |
| `MERCENARY_013` | 422 | "Trận đã bắt đầu" | 2.1, 2.5 (tăng số lượng), 2.7, 2.11, 2.13, 2.31–2.33 |
| `MERCENARY_014` | 403 | "Bạn không tham gia đơn ứng tuyển này" | 2.10, 2.13, 2.18–2.20 |
| `MERCENARY_015` | 422 | "Chỉ đánh giá được sau trận mà mercenary đã tham gia" | 2.18 |
| `MERCENARY_016` | 422 | "Chỉ đội trưởng được chọn 'Không đến'" | 2.18 |
| `MERCENARY_017` | 409 | "Bạn đã đánh giá người chơi này" | 2.18 |
| `MERCENARY_018` | 422 | "Bật GPS hoặc cập nhật khu vực trong hồ sơ để tìm kèo" | 2.3 |
| `MERCENARY_019` | 422 | "Bạn vừa là ứng viên vừa là đội trưởng của đơn này, không thể thực hiện" | 2.18–2.20 |
| `MERCENARY_020` | 422 | "Chỉ report được sau trận đã tham gia, hoặc khi đã nhắn tin" | 2.20 |
| `MERCENARY_021` | 422 | "Đơn này chỉ report được giao tiếp tệ" | 2.20 |
| `MERCENARY_022` | 409 | "Bạn đã report loại này trong trận" | 2.20 |
| `MERCENARY_023` | 409 | "Đội đã có tin đang mở cho vị trí này, hãy tăng số lượng" | 2.1, 2.33 |
| `MERCENARY_024` | 404 | "Không tìm thấy trận ngoài hệ thống" | 2.30–2.33 |
| `MERCENARY_025` | 409 | "Trận đã có mercenary được duyệt, không sửa được sân, giờ hoặc trình độ" | 2.31 |

Mã dùng lại (message theo `API_Spec.md` của mã gốc):

| Mã | HTTP | Nguồn | Endpoint |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | `api-convention.md` §11 | Mọi endpoint có tham số / body |
| `AUTH_001` | 401 | USER-001 | Mọi endpoint, kênh realtime |
| `AUTH_004` | 403 | USER-001 | 2.25–2.27 |
| `USER_001` | 404 | USER-001 | 2.3, 2.7, 2.15, 2.18, 2.20, 2.23 |
| `TEAM_001` | 404 | USER-001 | 2.1, 2.2, 2.21, 2.24, 2.28, 2.29 |
| `TEAM_002` | 403 | USER-001 | 2.1, 2.2, 2.5, 2.6, 2.8, 2.11, 2.12, 2.21, 2.28–2.33 |
| `TEAM_015` | 422 | USER-001 | 2.1, 2.5, 2.11, 2.18–2.21, 2.28, 2.31, 2.33 |
| `TEAM_016` | 422 | USER-002 | 2.1, 2.2, 2.5, 2.6, 2.8, 2.11, 2.12, 2.20 (người đã giải tán đội tuyển — v2.0), 2.21, 2.28–2.33 |
| `MATCH_001` | 404 | MATCH-001 | 2.1 |
| `MATCH_009` | 403 | MATCH-001 | 2.1 |
| `MATCH_014` | 409 | MATCH-001 | Mọi endpoint ghi trừ 2.17 (2.15: hiếm — MATCH-001 2.16) |
| `VENUE_001`, `VENUE_002` | 404, 422 | VENUE-001 | 2.28, 2.31 |
| `CHAT_001` | 422 | MATCH-001 | 2.16 |
| `CHAT_002`, `CHAT_003` | 404, 403 | MATCH-001 (v2.4; v1.0 ghi là mã mới của mã này — mã và message không đổi) | 2.14–2.17, kênh realtime |
| `CHAT_004` | 409 | MATCH-001 (như trên) | 2.15 |
| `DISPUTE_001`, `DISPUTE_002` | 404, 409 | REPUTATION-001 | 2.26, 2.27 |
| `REPORT_009` | 403 | REPUTATION-001 (v1.1) | REPUTATION-001 2.5 khi tranh chấp report nhắm vào mercenary |

Request vi phạm nhiều điều kiện nhận lỗi đầu tiên theo thứ tự trong bảng lỗi của từng endpoint.

## 6. Danh sách [cần xác nhận]

Không còn điểm mở ảnh hưởng hợp đồng hay hành vi API. Đã chốt (user, 2026-11-14):

1. Ứng viên xoá tài khoản: đơn `APPROVED` của trận đã huỷ giữ nguyên (BR-ME18).
2. Trận ngoài hệ thống (2.28, 2.31): giờ bắt đầu ≥ hiện tại + 1 giờ, thời lượng ≤ 12 giờ, `opponentName` ≤ 100 ký tự; sửa sân / giờ / trình độ chỉ trước giờ bắt đầu và khi chưa có mercenary được duyệt (BR-ME19).
3. Khai báo trận ngoài ở một sân (2.28) hoặc đổi sang sân đó (2.31) tính là sân "được chọn" cho cảnh báo địa điểm 180 ngày (BC-V05).

Đã chốt (v2.0):

16. ~~Đội tuyển đã giải tán — chat theo đơn có áp quy tắc "người đã giải tán chỉ xem" không~~ — **user chốt 2026-11-15:** có (§1.2, §1.7; report 2.20 → `TEAM_016`).

17. ~~Người đã giải tán đội tuyển lấy `conversationId` của chat theo đơn từ đâu~~ — **user chốt 2026-11-15:** danh sách "Tin nhắn của tôi" MATCH-001 `API_Spec.md` 2.20 (gồm chat theo đơn của đội trưởng đội tuyển, người đã giải tán đội tuyển, ứng viên).
