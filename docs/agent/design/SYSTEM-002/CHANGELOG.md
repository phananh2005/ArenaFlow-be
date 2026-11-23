# CHANGELOG — SYSTEM-002 Ngày giờ theo giờ Việt Nam, không kèm múi giờ

## [v1.2] — 2026-11-23

**Type:** MINOR (không đổi endpoint, shape response, mã lỗi, message; tương thích ngược, trừ: field `integer` trong body gửi số thập phân / chuỗi số trước được nhận nay `400`) · **Tóm tắt:** Đồng bộ thiết kế với code đã viết: field `integer` trong body chỉ nhận số nguyên JSON (user chốt 2026-11-21), cập nhật tài liệu nội bộ BE.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | `API_Spec.md` §1, §2 "Field số nguyên trong body", bảng lỗi `400 VALIDATION_ERROR` | Thêm ràng buộc | Field `integer` nhận cả `4.5` (cắt thành `4`), `4.0`, `"4"` → chỉ nhận số nguyên JSON; còn lại `400 VALIDATION_ERROR`, `field` = đường dẫn field; `""` đọc như `null`. Field `number` không đổi. Endpoint đang chạy bị ảnh hưởng: VENUE-001 2.5 `version` | Giá trị bị cắt âm thầm làm sai dữ liệu người dùng nhập | Gửi số nguyên JSON cho mọi field `integer`; chặn nhập phần thập phân hoặc làm tròn trước khi gửi; không gửi số dạng chuỗi |
| 2 | `API_Spec.md` §6 bước 2 | Sửa | Thêm: request gửi số thập phân / chuỗi số vào field `integer` bị từ chối từ lúc BE triển khai | Như #1 | Kiểm VENUE-001 2.5 `version` trước khi BE triển khai |
| 3 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Đồng bộ với code | Không |
| 4 | Header 3 tài liệu | Ghi nhận | v1.1 → v1.2 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract SYSTEM-002: không thêm endpoint, field, mã lỗi; request trước đây được nhận nhờ cắt số nay bị từ chối (#1) — VENUE-001 v6.1, USER-001 (lỗi chung) ghi cùng quy tắc.
- Nghiệp vụ / SRS: không đổi.

## [v1.1] — 2026-11-21

**Type:** MINOR (không đổi endpoint, field, mã thành công, mã lỗi, message) · **Tóm tắt:** ADR-0017 đã `Accepted` (2026-11-21) — bỏ nhãn "Proposed" ở header API_Spec; cập nhật tài liệu nội bộ BE, không ảnh hưởng FE.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | `API_Spec.md` header "Quy ước áp dụng" | Sửa | "ADR-0017 (Proposed)" → "ADR-0017" | ADR-0017 `Accepted` 2026-11-21 | Không |
| 2 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Như #1 | Không |
| 3 | Header 3 tài liệu | Ghi nhận | v1.0 → v1.1 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract SYSTEM-002: không đổi.
- Nghiệp vụ / SRS: không đổi.
