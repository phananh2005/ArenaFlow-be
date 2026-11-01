# CLAUDE.md — Hub điều phối của ArenaFlow-be

> Trang điều phối trung tâm. **Chỉ trỏ** đến kiến thức, agent, skill, playbook — không chứa nội dung chi tiết.
>
> Quy tắc **MUST / NEVER** ở §8 có độ ưu tiên CAO NHẤT.

---

## §1 — Kiến thức nền

### Nạp mọi phiên (`@`) — chỉ file nhỏ, ổn định

@docs/agent/CLAUDE.md

### Tham chiếu — đọc khi cần (KHÔNG `@`)

| File | Đọc khi nào |
|---|---|
| `docs/agent/non-functional-standards.md` | Khi viết API trả danh sách, truy vấn DB, tác vụ dài |
| `docs/agent/adaptation-rules.md` | Khi cần điều chỉnh cách làm việc của agent |
| `docs/agent/external-knowledge.md` | Khi tra cứu Neon project/schema hoặc cân nhắc thay đổi database |

---

## §2 — Kiến thức dự án · GHI ĐÈ §1

> Ưu tiên: Dự án > Tổ chức. Dự án không đề cập → áp dụng Tổ chức.

@docs/agent/CLAUDE.md

---

## §3 — Routing

> Repo một tầng (`backend`) → không cần xác định tầng. Mọi thay đổi code đều thuộc tầng `backend`.

@.claude/AGENTS.md

| Request | MUST thực thi | NEVER |
|---|---|---|
| Phân tích yêu cầu nghiệp vụ | procedure `business-analyst` → duyệt → spec nghiệp vụ | đoán yêu cầu, bỏ qua phân tích |
| Thiết kế chức năng mới | kiểm tra spec → chưa có → `business-analyst`; đã có spec → `solution-architect` → duyệt → `architect-reviewer` | bỏ qua thiết kế, code thẳng |
| Implement chức năng mới | kiểm tra thiết kế → chưa có → `solution-architect`; đã có → `developer` | code chức năng mới khi chưa có thiết kế |
| Implement sửa nhỏ | procedure `developer` | viết code tự do không qua procedure |
| Debug / lỗi | skill `debug` | đoán nguyên nhân khi chưa reproduce |
| Review thiết kế | procedure `architect-reviewer` | tự sửa thiết kế |
| Review code / chuẩn bị MR | procedure `code-reviewer` | commit khi chưa review |
| Agent lệch quy ước repo (lặp lại) | skill `workflow-adapt` | tự nới guardrail |
| Hỏi đáp / giải thích / tìm kiếm | xử lý trực tiếp | — |

> Decision tree + cách thực thi: `.claude/AGENTS.md`.
> Coordinator **tự đọc procedure và thực thi inline** — không yêu cầu user chuyển agent thủ công.

---

## §4 — Skills

| Skill | Dùng khi |
|---|---|
| `task-state-init` | Bắt đầu implement tính năng mới |
| `progress-tracker` | Sau mỗi bước implement hoàn thành |
| `reuse-scan` | Trước khi tạo thành phần dùng chung mới |
| `code-review` | Review nhanh trước commit |
| `debug` | Debug có hệ thống (reproduce → isolate → diagnose → fix) |
| `workflow-adapt` | Điều chỉnh cách làm việc khi agent lệch quy ước repo |
| `decisions-relevance` | Rà kho ADR khi có quyết định kiến trúc |

---

## §5 — Playbooks

> **TDD luôn bật và bắt buộc** cho mọi thay đổi hành vi code; không có cờ bật/tắt. Thực hiện Red → Green → Refactor theo `.claude/agents/developer.md`. Chỉ thay đổi thuần tài liệu được miễn.

---

## §6 — Hook an toàn

> Hook chạy ở tầng harness. Chỉ liệt kê hook **đã cấu hình và kiểm chứng** — không mô tả chỉ dẫn markdown như enforcement.

- **PreToolUse** (`Bash`) → `docs/agent/hooks/agent-hooks.cjs guard` — chặn lệnh phá huỷ đã định nghĩa.
- **PostToolUse** (`Bash|Write|Edit`) → `docs/agent/hooks/agent-hooks.cjs logger` — chỉ log thời gian và tên tool; không lưu nội dung/lệnh.
- **SessionStart** (`startup|resume|clear`) → nhắc khi Git pre-commit chưa cài.
- **Git pre-commit** (`.githooks/pre-commit`) → quét secret trong staged files bằng module chung `docs/agent/hooks/agent-hooks.cjs`.
- **Điều kiện hook Claude Code chạy:** mở harness tại gốc repo · Node.js có trên PATH · workspace đã được trust.
- **Lưu ý:** Git `core.hooksPath` là cấu hình local, không tự đi theo clone; xem `docs/agent/README.md`.

---

## §7 — State & bàn giao phiên

- Thư mục state: `docs/agent/state/local-dev/` (copy từ `docs/agent/state/_template/`). **Một giá trị duy nhất** ở mọi file.
- State cá nhân được gitignore bằng `docs/agent/state/*/`; template `_template/` được giữ trong Git.
- `/af-handoff` lưu phiên · `/af-resume` đọc lại và **hỏi trước khi code tiếp**.

---

## §8 — MUST / NEVER (ưu tiên cao nhất)

### MUST
- **Thiết kế trước khi code:** chức năng mới (endpoint mới, entity mới, luồng nghiệp vụ mới) → **MUST** chạy `solution-architect` sinh tài liệu thiết kế → duyệt → `architect-reviewer` → duyệt → mới cho `developer` code. Sửa nhỏ (bugfix, refactor không đổi hợp đồng) → thẳng `developer`.
- TDD **luôn bật và bắt buộc** cho mọi thay đổi làm đổi hành vi code: viết test trước (Red), xác nhận fail đúng lý do, viết code tối thiểu (Green), rồi refactor và chạy lại test. Chỉ thay đổi thuần tài liệu mới không cần TDD.
- Nhận yêu cầu implement → **MUST** chạy `task-state-init` trước khi viết code (sau khi thiết kế đã duyệt nếu là chức năng mới).
- **MUST** ghi kế hoạch chi tiết vào `current-task.md` và chờ user duyệt — một cổng duyệt duy nhất.
- Viết endpoint / truy vấn DB → **MUST** đọc `docs/agent/non-functional-standards.md` **trước** khi lập kế hoạch; không đạt ngưỡng → dừng, chờ xác nhận.
- Sau khi viết/sửa code → **MUST** chạy `./mvnw test` liên quan, dán output, xác nhận PASS. Test cần PostgreSQL hoạt động với datasource được cấu hình; thiếu DB → báo blocker, không khẳng định PASS.
- Sau mỗi bước implement → **MUST** chạy `progress-tracker`.
- Trước khi commit → **MUST** chạy `code-review`.
- Debug → **MUST** reproduce trước khi nêu nguyên nhân.

### NEVER
- **NEVER** code chức năng mới khi chưa có thiết kế được duyệt.
- **NEVER** báo xong / tick DoD khi test chưa chạy hoặc còn fail.
- **NEVER** tạo thành phần dùng chung mới khi chưa có Reuse Report.
- **NEVER** suy đoán hợp đồng service/repo khác khi chưa tra nguồn — không tra được → `[cần xác nhận]`.
- **NEVER** tự commit / push / tạo MR khi chưa được yêu cầu.
- **NEVER** tự sửa tài liệu thiết kế khi đang review (`architect-reviewer`); chỉ ghi findings.
- **NEVER** `@` mọi tài liệu — chỉ `@` file cần ở hầu hết mọi phiên.
- **NEVER** sửa file do generator quản lý (`.mvn/`, `mvnw`, `mvnw.cmd`, `HELP.md`).

---

## §9 — Tự điều chỉnh cách làm việc

- Hạ tầng agent phải khớp repo. Khi agent lệch quy ước repo lặp lại, lệnh trong hub hết đúng, hoặc quy tắc mâu thuẫn code → skill `workflow-adapt`.
- Điều chỉnh đi vào **kênh tuỳ biến** (`docs/agent/`), có bằng chứng, được duyệt, ghi dấu vết. Không nới guardrail ở §8.
