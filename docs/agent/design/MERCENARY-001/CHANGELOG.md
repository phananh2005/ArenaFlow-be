# CHANGELOG — MERCENARY-001 Tuyển mercenary, ứng tuyển, chat theo đơn, uy tín cá nhân

## [v3.0] — 2026-11-21

**Type:** MAJOR (đổi định dạng mọi giá trị thời gian: bỏ `Z`/offset, giá trị theo giờ Việt Nam) · **Tóm tắt:** Thời gian theo giờ Việt Nam, không kèm múi giờ (SYSTEM-002).

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | `API_Spec.md` §1 (quy ước chung), mọi field thời gian của request (`startsAt`, `endsAt` của trận ngoài hệ thống…) và response | Đổi | Chuỗi ISO-8601 kèm múi giờ, response trả UTC (vd `"2026-11-12T11:00:00Z"`) → giờ Việt Nam, **không** kèm múi giờ, ở cả request và response (vd `"2026-11-12T18:00:00"`); request kèm `Z`/offset → `400 VALIDATION_ERROR` | SRS SYSTEM-001 v1.13 BC-M05 (user chốt 2026-11-21); SYSTEM-002 `API_Spec.md` §1.1 | Đọc và gửi thời gian theo SYSTEM-002 `API_Spec.md` §1.1: hiển thị nguyên giá trị, không đổi theo múi giờ máy; khi so với giờ hiện tại coi là UTC+7 |
| 2 | Ví dụ JSON | Sửa | Giá trị `…Z` đổi sang cùng thời điểm theo giờ Việt Nam (cộng 7 giờ, bỏ `Z`) | Như #1 | Không |
| 3 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Như #1 | Không |
| 4 | Header 3 tài liệu thiết kế MERCENARY-001 | Ghi nhận | v2.1 → v3.0 | Đồng bộ phiên bản | Không |

### Ảnh hưởng
- Không đổi endpoint, field, mã lỗi; chỉ đổi định dạng giá trị thời gian.
- Cùng đợt: SYSTEM-002 `API_Spec.md` v1.0 (quy ước thời gian toàn hệ thống, thứ tự phát hành BE/FE).

## [v2.1] — 2026-11-16

**Type:** MINOR (không đổi endpoint, field, mã thành công, mã lỗi, message) · **Tóm tắt:** Cập nhật tài liệu nội bộ BE: người nghe sự kiện và job xoá tin nhắn hết hạn lưu đã thiết kế ở NOTIFICATION-001; không ảnh hưởng FE.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE (loại thông báo cho từng sự kiện tuyển mercenary; "nhận đánh giá" chỉ khi người nhận đã đánh giá lại; đóng điểm mở job xoá tin nhắn), không ảnh hưởng FE | NOTIFICATION-001 (user chốt 2026-11-16 — Q1, Q3) | Không — thông báo: NOTIFICATION-001 `API_Spec.md` §4.2 |
| 2 | Header 3 tài liệu | Ghi nhận | v2.0 → v2.1 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract MERCENARY-001: không đổi.

## [v2.0] — 2026-11-15

**Type:** MAJOR (2.20 đổi mã lỗi cho người đã giải tán đội tuyển `403 MERCENARY_014` → `422 TEAM_016`; người đó nay xem được chat 2.14, 2.16, 2.17 và kênh realtime (trước: `403 CHAT_003`); thêm field `null` vào response 2.14, thêm giá trị enum `contextType`, thêm `MATCH_014` hiếm ở 2.15) · **Tóm tắt:** Chat theo đơn dùng chung bộ endpoint / kiểu dữ liệu / kênh realtime với chat giữa hai đội trong kèo (MATCH-001 v2.4); người đã giải tán đội tuyển được xem chat (không gửi) như chat hai đội.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | 2.14–2.17, §1.7, §4.7, §4.8 | Ghi rõ | Hợp đồng chat định nghĩa ở mã này → 2.14–2.17 **là** MATCH-001 2.19, 2.16, 2.17, 2.18 (cùng path, body, response, lỗi); mã này giữ quy tắc riêng (hai bên, `canSend` theo trạng thái đơn, frame `APPLICATION_UPDATED`) | Một bộ endpoint / topic cho mọi chat (user chọn 2026-11-15) | Dùng chung client chat với chat hai đội; không đổi path |
| 2 | 2.14 response (MATCH-001 §4.7) | Thêm field | — → `matchType`, `requestId`, `requestStatus`, `pairingId`, `pairingStatus` — luôn `null` với cuộc trò chuyện theo đơn | Shape chung có thêm field của chat hai đội | Bỏ qua các field này với `contextType = MERCENARY_APPLICATION` |
| 3 | §4.1 `contextType` | Thêm giá trị | `MERCENARY_APPLICATION` → thêm `TEAM_MATCH` (chat hai đội) | Shape chung | Phân nhánh hiển thị theo `contextType` |
| 4 | §5 | Ghi rõ | `CHAT_002`, `CHAT_003`, `CHAT_004` từ "mã mới của MERCENARY-001" → "mã dùng lại của MATCH-001" (mã, HTTP, message không đổi) | Chủ sở hữu bộ endpoint chat | Không |
| 5 | 2.15, §5 | Thêm mã | — → `409 MATCH_014` hiếm (thao tác đồng thời) | Gửi tin chạy chung đường ghi của MATCH-001 2.16 | Cho gửi lại |
| 6 | §1.2, §1.7, 2.14, 2.16, 2.17, kênh realtime | **Đổi hành vi** | Đội tuyển giải tán → không ai của đội xem chat (`403 CHAT_003`) → người đã giải tán đội tuyển (đội trưởng cuối) **chỉ xem**: 2.14 (`canSend = false`), 2.16, 2.17, `SUBSCRIBE` được; 2.15 → `409 CHAT_004`; thành viên khác vẫn `403 CHAT_003` | User chốt 2026-11-15 — thống nhất với chat hai đội (MATCH-001 §1.6) | Không ẩn phòng chat với người đã giải tán đội tuyển; khoá ô chat theo `canSend` |
| 7 | 2.20 lỗi | **Đổi mã** | Người đã giải tán đội tuyển: `403 MERCENARY_014` → `422 TEAM_016` (BR-R10); người khác không thuộc đơn giữ `403 MERCENARY_014` | Người đó là một bên (chỉ xem) của cuộc trò chuyện theo đơn | Xử lý `TEAM_016` ở nút "Report" (ẩn nút) |
| 8 | §1.7, §6 | Ghi rõ | — → chat theo đơn có trong danh sách "Tin nhắn của tôi" (MATCH-001 2.20) cho đội trưởng đội tuyển, người đã giải tán đội tuyển (chỉ xem) và ứng viên (user chốt 2026-11-15) | Đường vào chat cho người đã giải tán đội tuyển | Màn hình "Tin nhắn" dùng MATCH-001 2.20 |
| 9 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE ngoài các mục trên | — | — |
| 10 | Header 3 tài liệu | Ghi nhận | v1.0 → v2.0 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract: **phá vỡ** ở #6 (người đã giải tán đội tuyển: `CHAT_003` → xem được) và #7 (2.20: `MERCENARY_014` → `TEAM_016`). Endpoint, path, field đã có giữ nguyên.
- Nghiệp vụ / SRS: BR-ME16, AC-ME10 (người đã giải tán đội tuyển chỉ xem — SRS `SYSTEM-001` v1.10); chat hai đội ở MATCH-001 v2.4.
