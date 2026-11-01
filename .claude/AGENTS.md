# AGENTS.md — Routing & cách coordinator thực thi procedure

> Phiên mặc định = **coordinator**: nhận request → chọn procedure → **tự đọc và thực thi inline**,
> không bắt người dùng chuyển agent thủ công.

## §1 — Decision tree

> Repo một tầng (`backend`) → bỏ bước xác định tầng, bỏ hậu tố tầng.

```text
Nhận request
├── Phân tích yêu cầu / làm spec nghiệp vụ    → procedure business-analyst
├── Thiết kế / design / làm đặc tả            → kiểm tra spec:
│   ├── Chưa có spec nghiệp vụ                → business-analyst trước → duyệt → solution-architect
│   └── Đã có spec nghiệp vụ                  → solution-architect → architect-reviewer
├── Implement / viết / tạo / sửa code         → kiểm tra thiết kế:
│   ├── Chưa có thiết kế cho chức năng mới    → solution-architect trước → duyệt → developer
│   └── Đã có thiết kế hoặc sửa nhỏ          → task-state-init → developer
├── Debug / lỗi / không chạy                  → skill debug
├── Review thiết kế                            → procedure architect-reviewer
├── Review code / chuẩn bị MR                 → procedure code-reviewer
├── Rà ADR / quyết định kiến trúc              → skill decisions-relevance
├── Agent làm lệch quy ước repo (lặp lại)     → skill workflow-adapt
└── Hỏi đáp / giải thích / tìm kiếm           → xử lý trực tiếp
```

## §2 — Bảng procedure

| Procedure / skill | Trigger | Output | Không làm |
|---|---|---|---|
| `business-analyst` | "phân tích yêu cầu [X]", "làm spec nghiệp vụ", "analyze requirements" | Spec nghiệp vụ trong `docs/agent/business-spec/<MÃ>/` | Thiết kế kỹ thuật (API/class/DB) |
| `solution-architect` | "thiết kế [X]", "design API/luồng/class", "làm đặc tả kỹ thuật" | Tài liệu thiết kế trong `docs/agent/design/<MÃ>/` | Viết code production |
| `architect-reviewer` | "review thiết kế", "soi design" | CRITICAL/MAJOR/MINOR cho tài liệu thiết kế | Tự sửa thiết kế |
| `developer` | "implement/viết/tạo/build/thêm [X]" | Code + test PASS + state cập nhật | Code khi chưa duyệt kế hoạch; code chức năng mới khi chưa có thiết kế |
| `code-reviewer` | "review code", "tạo MR" | CRITICAL/MAJOR/MINOR có `file:line` | Tự sửa / commit |
| `debug` | "lỗi", "bug", "exception" | Root cause + fix + test | Đoán khi chưa reproduce |
| `workflow-adapt` | Người dùng sửa cùng hành vi ≥ 2 lần; lệnh/quy tắc lệch repo | Đề xuất điều chỉnh có bằng chứng | Nới guardrail, sửa file org-owned |

## §3 — Khi nào xử lý trực tiếp

| Loại request | Trực tiếp? |
|---|---|
| Giải thích code, tra cứu, tóm tắt, so sánh | ✅ |
| Đọc log / error message | ✅ — nhưng sửa lỗi phải qua `debug` |
| Phân tích yêu cầu nghiệp vụ | ✗ → `business-analyst` |
| Thiết kế kỹ thuật chức năng mới | ✗ → `solution-architect` |
| Viết / sửa code production | ✗ → `developer` (chức năng mới phải có thiết kế trước) |
| Review thiết kế | ✗ → `architect-reviewer` |
| Review code trước merge | ✗ → `code-reviewer` |

## §4 — Cách thực thi một procedure

1. Báo ngắn: "Bắt đầu `<procedure>` — Bước 1: đọc context…".
2. Đọc file procedure **khi cần** (không nạp sẵn mọi procedure vào context).
3. Thực thi tuần tự, không bỏ bước; gặp tín hiệu dừng → dừng và hỏi.
4. Mọi thay đổi hành vi code MUST theo TDD **Red → Green → Refactor**; chỉ thay đổi thuần tài liệu được miễn. Nếu test không thể chạy do môi trường (ví dụ thiếu PostgreSQL), dừng trước khi viết production code và báo blocker.
5. Sau mỗi bước implement → `progress-tracker`.

**NEVER** yêu cầu người dùng chuyển agent trong UI; **NEVER** bỏ bước vì "nhanh hơn".
