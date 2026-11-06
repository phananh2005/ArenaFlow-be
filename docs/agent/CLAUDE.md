# Kiến thức dự án — ArenaFlow-be

> Cổng vào ngữ cảnh **đặc thù** của dự án; hub gốc trỏ tới file này. **Khi mâu thuẫn với kiến thức tổ chức, nội dung ở đây ghi đè**.

> [!IMPORTANT]
> `@path` = nạp vào context **mọi phiên** (tốn token, dễ loãng). **Chỉ `@` file cần ở hầu hết mọi phiên**.
> Tài liệu theo tình huống → liệt kê ở §2, KHÔNG `@`. Nghi ngờ → cho vào §2.

## 1. Auto-load (`@`)

_(Chưa có file kiến trúc/thuật ngữ — thêm khi dự án phát triển)_

---

## 2. Tham chiếu — đọc khi cần (KHÔNG `@`)

> Mục lục điều hướng. Mỗi dòng chỉ ghi **đường dẫn + khi nào đọc**; chi tiết nằm trong file được trỏ tới.

| Tình huống | Đọc |
|---|---|
| Phân tích yêu cầu nghiệp vụ | `docs/agent/business-spec/_template/`, spec nghiệp vụ đã có trong `docs/agent/business-spec/` |
| Thiết kế chức năng mới | `docs/agent/design-standards.md`, `docs/agent/design/_template/`, spec nghiệp vụ tại `docs/agent/business-spec/<MÃ>/` |
| Review tài liệu thiết kế | `docs/agent/design-standards.md`, thiết kế đã có trong `docs/agent/design/` |
| Viết endpoint / truy vấn DB | `docs/agent/non-functional-standards.md` |
| Thiết kế mô hình dữ liệu / thay đổi schema | `docs/database/data-model.md` |
| Điều chỉnh cách làm việc agent | `docs/agent/adaptation-rules.md` |
| Rà quyết định kiến trúc | `docs/agent/decisions/` |
| Tra cứu Neon project/schema | `docs/agent/external-knowledge.md` |
| Làm việc với user/hồ sơ/đội/lời mời/xác thực (USER-001) | `docs/agent/design/USER-001/` |
| Làm việc với giải tán đội / quy tắc xoá tài khoản đội trưởng / lịch sử thành viên (Admin) (USER-002) | `docs/agent/design/USER-002/` |

---

## 3. Bảo trì file này

- Chức năng mới có spec/thiết kế → thêm đường dẫn ở §2. **KHÔNG `@`**.
- Chỉ thêm `@` ở §1 khi file thực sự cần ở hầu hết mọi phiên.
