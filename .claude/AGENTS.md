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
├── Implement chức năng mới                   → kiểm tra thiết kế:
│   ├── Chưa có thiết kế                      → solution-architect trước → duyệt → developer
│   └── Đã có thiết kế                        → task-state-init → developer
├── Sửa chức năng đã triển khai               → developer → sau khi code & test PASS:
│   ├── Kiểm tra ảnh hưởng design (API/DB/sequence/integration)
│   │   └── CÓ ảnh hưởng                      → cập nhật design → architect-reviewer (chỉ review phần sửa)
│   └── Kiểm tra ảnh hưởng nghiệp vụ (use case/business rule/actor)
│       └── CÓ ảnh hưởng                      → cập nhật SRS
├── Debug / lỗi / không chạy                  → skill debug
├── Review thiết kế                            → procedure architect-reviewer
├── Review code / chuẩn bị MR                 → procedure code-reviewer
├── Rà ADR / quyết định kiến trúc              → skill decisions-relevance
├── Agent làm lệch quy ước repo (lặp lại)     → skill workflow-adapt
└── Hỏi đáp / giải thích / tìm kiếm           → xử lý trực tiếp
```

### Phân biệt "chức năng mới" vs "sửa chức năng đã có"

| Loại | Đặc điểm | Ví dụ |
|---|---|---|
| **Chức năng mới** | Endpoint mới, entity mới, use case mới | `POST /matches`, thêm bảng `Tournament` |
| **Sửa chức năng đã có** | Thay đổi logic/API/DB của chức năng hiện có | Thêm field `avatar_url` vào `GET /users/:id`, đổi validation rule |

### Tiêu chí "ảnh hưởng design"

**CÓ ảnh hưởng** (cần cập nhật design document):
- API contract: endpoint path, HTTP method, request/response structure, status code mới
- Database schema: thêm/xóa/đổi tên table/column, index/constraint
- Class diagram: thêm/xóa class/interface, thay đổi method signature public
- Sequence diagram: thay đổi luồng gọi giữa component
- Integration: thay đổi cách gọi external service/database/queue

**KHÔNG ảnh hưởng** (không cần cập nhật):
- Bugfix logic bên trong method (không đổi signature)
- Refactor code (rename private, extract helper)
- Thay đổi implementation (đổi thuật toán, optimize query)
- Thêm log/comment/validation không đổi hành vi bên ngoài

### Tiêu chí "ảnh hưởng nghiệp vụ"

**CÓ ảnh hưởng** (cần cập nhật SRS):
- Use case: thêm/xóa/sửa acceptance criteria
- Business rule: thay đổi điều kiện/công thức/validation rule
- Actor/Permission: thay đổi quyền truy cập
- Workflow: thay đổi trạng thái/bước trong quy trình
- Data model nghiệp vụ: thêm/xóa entity/relationship

**KHÔNG ảnh hưởng**:
- Bugfix làm code khớp đúng spec hiện tại
- Cải thiện performance/security không đổi hành vi
- Thay đổi thuần kỹ thuật (đổi lib, cache, optimize)

## §2 — Bảng procedure

| Procedure / skill | Trigger | Output | Không làm |
|---|---|---|---|
| `business-analyst` | "phân tích yêu cầu [X]", "làm spec nghiệp vụ", "analyze requirements" | Spec nghiệp vụ trong `docs/agent/business-spec/<MÃ>/` | Thiết kế kỹ thuật (API/class/DB) |
| `solution-architect` | "thiết kế [X]", "design API/luồng/class", "làm đặc tả kỹ thuật" | Tài liệu thiết kế trong `docs/agent/design/<MÃ>/` | Viết code production |
| `architect-reviewer` | "review thiết kế", "soi design" | CRITICAL/MAJOR/MINOR cho tài liệu thiết kế | Tự sửa thiết kế |
| `developer` | "implement/viết/tạo/build/thêm/sửa [X]" | Code + test PASS + cập nhật tài liệu (nếu có ảnh hưởng) + state cập nhật | Code khi chưa duyệt kế hoạch; code chức năng mới khi chưa có thiết kế; bỏ qua cập nhật tài liệu khi có ảnh hưởng |
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
4. Mọi thay đổi hành vi code MUST theo TDD **Red → Green → Refactor**; chỉ thay đổi thuần tài liệu được miễn. Unit test thuần chạy độc lập, không cần `.env.dev`; integration test → tự nạp `.env.dev` (DB dev, xem `CLAUDE.md` §8). Chỉ khi không có `.env.dev` hoặc DB dev không kết nối được mới dừng trước khi viết production code và báo blocker.
5. Sau mỗi bước implement → `progress-tracker`.

**NEVER** yêu cầu người dùng chuyển agent trong UI; **NEVER** bỏ bước vì "nhanh hơn".
