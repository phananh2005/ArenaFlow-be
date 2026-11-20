# CHANGELOG — SYSTEM-002 Ngày giờ theo giờ Việt Nam, không kèm múi giờ

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
