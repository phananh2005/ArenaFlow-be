# API Specification — [TÊN CHỨC NĂNG]

<!-- File này được publish cho FE (ADR-0010). Viết theo design-standards.md §11:
     không tên class/bảng/cột/migration/transaction/khoá/số truy vấn; không trỏ Component_Class_Design.md,
     Processing_Flow.md, docs/database/. Chi tiết nội bộ BE ghi ở hai file đó. -->

| Mục | Giá trị |
|---|---|
| Mã chức năng | `[MÃ]` |
| Phạm vi tầng | Backend (Spring Boot REST API) |
| Repo | ArenaFlow-be |
| Quy ước áp dụng | `api-convention.md` (envelope, phân trang, định dạng lỗi, kiểu dữ liệu) |
| Spec nghiệp vụ | `docs/agent/business-spec/[MÃ]/` ([UC/BR liên quan]) |
| Tác giả / Ngày / Phiên bản | [Agent/User] · [YYYY-MM-DD] · v1.0 (baseline) |

## 1. Tổng quan

[Chức năng làm gì, cho ai — 2–3 câu theo góc nhìn người dùng app.]

**Quy ước chung cho mọi endpoint của mã này:** [xác thực, enum UPPERCASE, quy tắc nghiệp vụ dùng chung… — chỉ những gì FE cần.]

## 2. Endpoints

| # | Method + Path | Dùng khi | Ai gọi được |
|---|---|---|---|
| 2.1 | `POST /api/v1/[path]` | [Màn hình / hành động] | [Vai trò] |

### 2.1. [Tên hành động] — `POST /api/v1/[path]`

**Dùng khi:** [Người dùng làm gì trên màn hình nào.]
**Ai gọi được:** [Vai trò + điều kiện, vd "đội trưởng đội tạo kèo"].

**Path / Query params** (nếu có)

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `[id]` | UUID | Có | — | `"0190…"` |

**Request body** (nếu có)

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `[field]` | string | Có | [≤ 100 ký tự; enum `A` \| `B`] | `"…"` |

```json
{ "[field]": "…" }
```

**Response [200|201|204]**

```json
{ "data": { "[field]": "…" } }
```

**Lỗi** (ngoài lỗi chung `400 VALIDATION_ERROR`, `401`, `403` của `api-convention.md`)

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 409 | `[MÃ]_001` | [Tình huống người dùng gặp] | [Tải lại / ẩn nút / hiện thông báo …] |

---

## 3. Hành động theo trạng thái

<!-- Bắt buộc khi đối tượng có trạng thái; bỏ mục nếu không có. -->

| Trạng thái | [Vai trò 1] | [Vai trò 2] | [Người khác] |
|---|---|---|---|
| `[STATUS_A]` | [Hành động được phép → endpoint] | — | — |

## 4. Kiểu dữ liệu dùng chung

| Kiểu / enum | Giá trị | Ý nghĩa |
|---|---|---|
| `[status]` | `A` \| `B` | [Ý nghĩa từng giá trị] |

## 5. Mã lỗi

| Mã | HTTP | Message | Endpoint |
|---|---|---|---|
| `[MÃ]_001` | 409 | [message] | 2.1 |

## 6. Danh sách [cần xác nhận]

1. [Điểm ảnh hưởng hợp đồng API chưa chốt — điểm nội bộ BE ghi ở Component_Class_Design.md / Processing_Flow.md]
