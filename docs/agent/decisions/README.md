# Architecture Decision Records (ADR)

> Repo đã có thư mục ADR → **dùng cái đã có**, không tạo thư mục thứ hai.

## Cách dùng
1. Copy `_template.md` → `NNNN-tieu-de-kebab-case.md`.
2. `NNNN` = 4 chữ số, tăng dần, **không tái sử dụng** kể cả khi ADR bị bỏ (khoảng trống số là bình thường).
3. Vòng đời: `Proposed` → `Accepted` (khi chốt qua MR) → `Deprecated` / `Superseded by ADR-MMMM`.
   **Không viết lại** Bối cảnh / Quyết định / Hệ quả của ADR đã `Accepted`. Thay đổi sau đó:

   | Loại thay đổi | Cách làm |
   |---|---|
   | Đảo ngược / thay thế quyết định | ADR mới + ADR cũ `Superseded by …`, hai bên trỏ nhau |
   | **Bổ sung** không đảo ngược | Thêm hàng `Sửa đổi` vào metadata + mục `## Sửa đổi YYYY-MM-DD` cuối file |
   | Bảo trì metadata | Sửa trực tiếp hàng metadata |

4. ADR hiệu lực được trỏ từ tài liệu kiến trúc.

## Ngưỡng
- **ADR** = quyết định có đánh đổi, ảnh hưởng lâu dài hoặc xuyên chức năng.
- Quyết định nhẹ trong phiên → `docs/agent/state/<DEV_ID>/decisions-log.md`, **không** lạm ADR.

## Ai làm gì
- Tạo ADR `Proposed`: developer khi chốt quyết định lớn.
- Chuyển `Accepted`: qua MR, người duyệt quyết định — agent chỉ khuyến nghị.
