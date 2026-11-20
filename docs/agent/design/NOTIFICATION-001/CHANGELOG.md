# CHANGELOG — NOTIFICATION-001 Thông báo in-app

## [v2.0] — 2026-11-21

**Type:** MAJOR (đổi định dạng mọi giá trị thời gian: bỏ `Z`/offset, giá trị theo giờ Việt Nam) · **Tóm tắt:** Thời gian theo giờ Việt Nam, không kèm múi giờ (SYSTEM-002).

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | `API_Spec.md` §1 (quy ước chung), `createdAt` của thông báo (REST và frame realtime) | Đổi | Chuỗi ISO-8601 kèm múi giờ, response trả UTC (vd `"2026-11-12T11:00:00Z"`) → giờ Việt Nam, **không** kèm múi giờ, ở cả request và response (vd `"2026-11-12T18:00:00"`); request kèm `Z`/offset → `400 VALIDATION_ERROR` | SRS SYSTEM-001 v1.13 BC-M05 (user chốt 2026-11-21); SYSTEM-002 `API_Spec.md` §1.1 | Đọc và gửi thời gian theo SYSTEM-002 `API_Spec.md` §1.1: hiển thị nguyên giá trị, không đổi theo múi giờ máy; khi so với giờ hiện tại coi là UTC+7 |
| 2 | Ví dụ JSON | Sửa | Giá trị `…Z` đổi sang cùng thời điểm theo giờ Việt Nam (cộng 7 giờ, bỏ `Z`) | Như #1 | Không |
| 3 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Như #1 | Không |
| 4 | Header 3 tài liệu thiết kế NOTIFICATION-001 | Ghi nhận | v1.1 → v2.0 | Đồng bộ phiên bản | Không |

### Ảnh hưởng
- Không đổi endpoint, field, mã lỗi; chỉ đổi định dạng giá trị thời gian.
- Cùng đợt: SYSTEM-002 `API_Spec.md` v1.0 (quy ước thời gian toàn hệ thống, thứ tự phát hành BE/FE).

## [v1.1] — 2026-11-18

**Type:** MINOR (không đổi endpoint, field, mã thành công, mã lỗi, message) · **Tóm tắt:** Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Dọn tài liệu làm việc 2026-11-18 | Không |
| 2 | Header 3 tài liệu thiết kế NOTIFICATION-001 | Ghi nhận | v1.0 → v1.1 | Đồng bộ phiên bản | Không |

### Ảnh hưởng
- API contract NOTIFICATION-001: không đổi.
