# CHANGELOG — REPUTATION-001 Đánh giá, report, tranh chấp & uy tín đội

## [v2.1] — 2026-11-27

**Type:** MINOR (không đổi endpoint, field, mã thành công, mã lỗi, message) · **Tóm tắt:** Cập nhật tài liệu nội bộ BE cho khớp code sau khi triển khai; không ảnh hưởng FE.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE theo code đã triển khai (tên và vị trí thành phần, chi tiết truy vấn, cấu hình job huy hiệu), không ảnh hưởng FE | Đối chiếu code với thiết kế sau khi triển khai | Không |
| 2 | Hành vi 2.1 / 2.3 / 2.10 (điểm uy tín) | Sửa | Chuỗi trận sạch khi report gửi gần như cùng lúc với đánh giá hoàn tất trận: trước đây chấp nhận trường hợp hiếm đội bị report vẫn được tính trận sạch → report đã gửi xong trước khi trận chuyển `COMPLETED` → trận không tính sạch; report gửi đồng thời mà lần hoàn tất xử lý trước → tính như report sau `COMPLETED` (`API_Spec.md` §1.3); trừ điểm khi hai report cùng loại đến cùng lúc luôn theo bậc liên tiếp | Sửa lỗi xử lý đồng thời để kết quả khớp quy tắc đã công bố (§1.3); user chọn sửa 2026-11-26 | Không — điểm uy tín ở 2.9 theo đúng quy tắc §1.3 sẵn có |
| 3 | Header 3 tài liệu thiết kế REPUTATION-001 | Ghi nhận | v2.0 → v2.1 | Đồng bộ phiên bản | Không |

### Ảnh hưởng
- Không đổi endpoint, field, mã thành công, mã lỗi, message, hình dạng response.
- Nghiệp vụ / SRS: không đổi — thay đổi #2 làm hệ thống khớp quy tắc chuỗi trận sạch hiện có.

## [v2.0] — 2026-11-21

**Type:** MAJOR (đổi định dạng mọi giá trị thời gian: bỏ `Z`/offset, giá trị theo giờ Việt Nam) · **Tóm tắt:** Thời gian theo giờ Việt Nam, không kèm múi giờ (SYSTEM-002).

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | `API_Spec.md` §1 (quy ước chung), mọi field thời gian của response | Đổi | Chuỗi ISO-8601 kèm múi giờ, response trả UTC (vd `"2026-11-12T11:00:00Z"`) → giờ Việt Nam, **không** kèm múi giờ, ở cả request và response (vd `"2026-11-12T18:00:00"`); request kèm `Z`/offset → `400 VALIDATION_ERROR` | SRS SYSTEM-001 v1.13 BC-M05 (user chốt 2026-11-21); SYSTEM-002 `API_Spec.md` §1.1 | Đọc và gửi thời gian theo SYSTEM-002 `API_Spec.md` §1.1: hiển thị nguyên giá trị, không đổi theo múi giờ máy; khi so với giờ hiện tại coi là UTC+7 |
| 2 | Ví dụ JSON | Sửa | Giá trị `…Z` đổi sang cùng thời điểm theo giờ Việt Nam (cộng 7 giờ, bỏ `Z`) | Như #1 | Không |
| 3 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Như #1 | Không |
| 4 | Header 3 tài liệu thiết kế REPUTATION-001 | Ghi nhận | v1.3 → v2.0 | Đồng bộ phiên bản | Không |

### Ảnh hưởng
- Không đổi endpoint, field, mã lỗi; chỉ đổi định dạng giá trị thời gian.
- Cùng đợt: SYSTEM-002 `API_Spec.md` v1.0 (quy ước thời gian toàn hệ thống, thứ tự phát hành BE/FE).

## [v1.3] — 2026-11-16

**Type:** MINOR (không đổi endpoint, field, mã thành công, mã lỗi, message) · **Tóm tắt:** Cập nhật tài liệu nội bộ BE: chốt quy tắc thông báo "nhận đánh giá" (chỉ khi người nhận đã đánh giá trước) và nhắc đánh giá lúc kèo kết thúc; không ảnh hưởng FE.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE (đóng điểm mở về thông báo "nhận đánh giá" mâu thuẫn quy tắc không tiết lộ đánh giá; người nghe sự kiện tranh chấp), không ảnh hưởng FE | User chốt 2026-11-16 (NOTIFICATION-001 Q3, Q8) | Không — thông báo: NOTIFICATION-001 `API_Spec.md` §4.2 `RATING_RECEIVED`, `REPORT_DISPUTED`, `DISPUTE_CREATED` |
| 2 | Header 3 tài liệu | Ghi nhận | v1.2 → v1.3 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract REPUTATION-001: không đổi.
- Nghiệp vụ / SRS: "rating nhận" (Overview §4, Business_Rules §8.6) cần ghi điều kiện — business-analyst.

## [v1.2] — 2026-11-15

**Type:** MINOR (thêm endpoint 2.10, thêm mã lỗi `REPORT_010`, `REPORT_011` (+ `TEAM_016` dùng lại ở 2.10); ghi rõ quy tắc chuỗi trận sạch; không đổi endpoint, field, mã thành công, mã lỗi đã có) · **Tóm tắt:** Report "giao tiếp tệ" qua chat hai đội: cuộc trò chuyện của một kèo đã có tin nhắn → hai đội trưởng report được nhau bất kể trạng thái yêu cầu / thương lượng / kèo (SRS `SYSTEM-001` v1.10 — BR-R07 trường hợp 3, AC-R08).

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | 2.10 (mới) | Thêm | — → `POST /conversations/{conversationId}/reports` body `{reason}` → `201` Report (§4.4, `reportType = BAD_COMMUNICATION`, `direction = SENT`) | BR-R07 trường hợp 3: report "giao tiếp tệ" qua chat, kể cả đội không được ghép, kể cả kèo `CONFIRMED` trước trận | Nút "Report" trong phòng chat hai đội gọi 2.10 với `conversationId` |
| 2 | §5 | Thêm mã | — → `422 REPORT_010` (cuộc trò chuyện chưa có tin nhắn), `422 REPORT_011` (cuộc trò chuyện không phải giữa hai đội — dùng MERCENARY-001 2.20) | Điều kiện của 2.10 | Xử lý mã mới |
| 3 | §1.3, §1.7, §3.1 | Ghi rõ | — → report qua chat trừ điểm / cắt chuỗi như report sau trận; "mỗi loại một lần mỗi kèo" tính chung 2.3 và 2.10 (`REPORT_004`) | BR-R07 | Ẩn loại "giao tiếp tệ" đã gửi (2.4 `SENT`) ở cả màn hình chat và màn hình report sau trận |
| 4 | 2.3 `REPORT_002` | Ghi rõ | — → FE gợi ý report từ phòng chat (2.10) khi trận chưa kết thúc mà hai đội đã nhắn tin | BR-R07 | Không bắt buộc |
| 5 | 2.4 | Ghi rõ | — → gồm report qua chat; `counterpartTeam` có thể là đội không được ghép của kèo | Report gắn kèo của cuộc trò chuyện | Không |
| 6 | §1.3 chuỗi trận sạch | Ghi rõ | "đội không có report nào trong trận → +1" → chỉ tính report của **đội đối phương của trận** và **mercenary đá thuê trong trận**; report qua chat của đội không được ghép chỉ đưa chuỗi về 0 lúc gửi (user chốt 2026-11-15) | Report qua chat của đội không đá trận không phải là hành vi trong trận | Không (điểm / chuỗi hiển thị ở 2.9 đã theo quy tắc mới) |
| 6a | 2.10 lỗi | Thêm mã | — → `422 TEAM_016` khi người gọi là người đã giải tán đội của mình (vẫn đọc được chat — MATCH-001 v2.4 §1.6) | BR-R10, BR-N05 (3) | Ẩn nút "Report" trong phòng chat của đội đã giải tán |
| 6b | 2.10 lỗi | Thêm mã / thứ tự | — → `404 USER_001` (tài khoản đã xoá, token còn hạn); thứ tự kiểm `CHAT_002` → `USER_001` → `CHAT_003` → `REPORT_011` → `TEAM_016` → `TEAM_015` → … (người không tham gia cuộc trò chuyện mercenary nhận `CHAT_003`, không phải `REPORT_011`) | Review 2026-11-15 | Xử lý `USER_001` như các API khác |
| 6c | §1.3 chuỗi trận sạch | Ghi rõ | "mercenary đá thuê trong trận" → chỉ mercenary **đã được duyệt** đá thuê trong trận, khi hệ thống có chức năng tuyển mercenary (MERCENARY-001); report qua chat của ứng viên bị từ chối / đã rút không làm trận mất "sạch" | Review 2026-11-15 (cách tính phía mercenary do MERCENARY-001 thêm) | Không |
| 7 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE ngoài các mục trên | — | — |
| 8 | Header 3 tài liệu | Ghi nhận | v1.1 → v1.2 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract: thêm endpoint + mã lỗi (tương thích ngược). Cuộc trò chuyện, `CHAT_002`/`CHAT_003`: MATCH-001 v2.4.
- Nghiệp vụ / SRS: theo SRS `SYSTEM-001` v1.10 (BR-R07, UC-R02, AC-R02, AC-R08).

## [v1.1] — 2026-11-13 (cập nhật 2026-11-14)

**Type:** MINOR (thêm mã lỗi `REPORT_009` cho 2.5, ghi rõ phạm vi 2.4 / 2.6–2.8 / 2.9; không đổi endpoint, field, mã thành công, mã lỗi đã có) · **Tóm tắt:** Mở rộng cho MERCENARY-001: tranh chấp report bùng nhắm vào mercenary dùng chung 2.5; report / tranh chấp / đánh giá giữa đội và mercenary nằm ở API của MERCENARY-001, không lẫn vào danh sách giữa hai đội.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | 2.5, §5 | Thêm | Chỉ đội trưởng đội bị report tranh chấp → thêm report nhắm vào **cá nhân** (đội trưởng report mercenary): chính người bị report tranh chấp; người khác → `403 REPORT_009` "Chỉ người bị report được tranh chấp" | Mercenary tranh chấp report bùng nhắm vào mình (Business_Rules §5.1, BR-ME14) | Màn hình report cá nhân (MERCENARY-001 2.22) gọi 2.5 khi `canDispute = true`; xử lý mã mới |
| 2 | 2.4 | Ghi rõ | — → chỉ gồm report giữa hai đội; report giữa đội và mercenary ở MERCENARY-001 2.21 | Giữ hình dạng response (`counterpartTeam`) | Thêm tab mercenary dùng MERCENARY-001 2.21 nếu cần |
| 3 | 2.6–2.8 | Ghi rõ | — → chỉ tranh chấp report giữa hai đội; tranh chấp liên quan mercenary ở MERCENARY-001 2.25–2.27 (2.7 / 2.8 với id đó → `404 DISPUTE_001`) | Giữ hình dạng response (`reporterTeam`, `reportedTeam`) | Admin panel thêm hàng chờ mercenary |
| 3a | §1.3, §1.4, §1.7 | Ghi rõ | — → mercenary đá thuê cho đội report được đội (MERCENARY-001 2.20): trừ điểm đội theo cùng bảng (chung bộ đếm số lần), cắt chuỗi trận sạch, report bùng loại kèo khỏi tử số tỉ lệ hoàn thành; đội tranh chấp bằng 2.5 | BR-ME14 (user chốt 2026-11-14) | Không đổi cách gọi; hiển thị điểm / tỉ lệ đã gồm tác động này |
| 4 | 2.9 `rating.averageRating` | Ghi rõ | — → chỉ tính đánh giá của các đội đối phương; đánh giá của mercenary dành cho đội không tính | BR-ME13 | Không |
| 5 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE (điểm cá nhân, huy hiệu cá nhân, kiểm tồn tại tranh chấp theo hàng chờ ở 2.8), không ảnh hưởng FE ngoài các mục trên | MERCENARY-001 | — |
| 6 | Header 3 tài liệu | Ghi nhận | v1.0 → v1.1 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract: thêm mã lỗi `REPORT_009` cho 2.5 (tương thích ngược); phạm vi dữ liệu 2.4, 2.6–2.9 ghi rõ.
- Nghiệp vụ / SRS: theo SRS `SYSTEM-001` v1.9 (BR-ME13, BR-ME14) — không sửa SRS trong thay đổi này.
