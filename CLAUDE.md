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
| Sửa chức năng đã triển khai | `developer` → code & test → kiểm tra ảnh hưởng → cập nhật design (nếu có) → cập nhật SRS (nếu có) → `architect-reviewer` (nếu sửa design) → `code-reviewer` (nếu có code) | bỏ qua cập nhật tài liệu khi có ảnh hưởng |
| Debug / lỗi | skill `debug` | đoán nguyên nhân khi chưa reproduce |
| Review thiết kế | procedure `architect-reviewer` | tự sửa thiết kế |
| Review code / chuẩn bị commit | procedure `code-reviewer` (chỉ khi diff có code) | commit code khi chưa qua `code-reviewer` |
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
| `code-review` | Review nhanh 5 chiều **khi user yêu cầu** — không phải bước bắt buộc trước commit |
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
- **Thiết kế trước khi code:** 
  - **Chức năng mới** (endpoint mới, entity mới, use case mới) → **MUST** chạy `solution-architect` sinh tài liệu thiết kế → duyệt → `architect-reviewer` → duyệt → mới cho `developer` code.
    - **Lệch thiết kế khi đang code:** code khác design → **NEVER** sửa design giữa chừng; **MUST** ghi ngay vào bảng "Lệch thiết kế" trong `current-task.md` (phase · design · code thực tế · lý do · file design cần sửa). Sau khi **mọi phase đã code xong + test PASS** → cập nhật design **một lần** theo bảng → `architect-reviewer` review phần sửa. Thay đổi làm đổi API contract / DB schema / nghiệp vụ so với design đã duyệt → **dừng, hỏi user** trước khi code (không chỉ ghi lệch).
  - **Sửa chức năng đã có** → `developer` code trước → sau khi test PASS → kiểm tra ảnh hưởng:
    - Có ảnh hưởng design (API/DB/sequence/integration) → **MUST** cập nhật design document → `architect-reviewer` review phần sửa
    - Có ảnh hưởng nghiệp vụ (use case/business rule/actor) → **MUST** cập nhật SRS
    - Không ảnh hưởng → bỏ qua cập nhật tài liệu
- TDD **luôn bật và bắt buộc** cho mọi thay đổi làm đổi hành vi code: viết test trước (Red), xác nhận fail đúng lý do, viết code tối thiểu (Green), rồi refactor và chạy lại test. Chỉ thay đổi thuần tài liệu mới không cần TDD.
- Nhận yêu cầu implement **chức năng mới** → **MUST** chạy `task-state-init` trước khi viết code (sau khi thiết kế đã duyệt).
- **MUST** ghi kế hoạch chi tiết vào `current-task.md` và chờ user duyệt — một cổng duyệt duy nhất.
- Viết endpoint / truy vấn DB → **MUST** đọc `docs/agent/non-functional-standards.md` **trước** khi lập kế hoạch; không đạt ngưỡng → dừng, chờ xác nhận.
- Sau khi viết/sửa code → **MUST** chạy `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh test` liên quan, dán output, xác nhận PASS. Mọi lệnh `./mvnw` (build/test) **MUST** kèm `-Duser.timezone=Asia/Ho_Chi_Minh`.
  - **Unit test thuần** (không dựng Spring context/DB) chạy độc lập, **không** cần `.env.dev`.
  - **Integration test** (`@SpringBootTest`, `@DataJpaTest`, migration test) cần DB → agent **tự nạp `.env.dev`** (DB dev) rồi chạy, không cần hỏi: `. ./.env.dev && export DATABASE_URL DATABASE_USERNAME DATABASE_PASSWORD JWT_SECRET && ./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -Dtest=<Test> test`. **NEVER** in giá trị biến môi trường.
  - Chỉ báo blocker khi không có `.env.dev` hoặc DB dev không kết nối được; khi đó không khẳng định PASS.
- **Sau khi sửa chức năng đã triển khai và test PASS → MUST tự kiểm tra:**
  1. Có thay đổi API contract / database schema / luồng component / integration? → cập nhật design
  2. Có thay đổi use case / business rule / actor / workflow? → cập nhật SRS
  3. Đã cập nhật design → gọi `architect-reviewer` review phần sửa
- Sau mỗi bước implement → **MUST** chạy `progress-tracker`.
- Trước khi commit, **nếu diff có code** (`*/src/**` (Java, migration SQL, `application*.properties`), `pom.xml` / `*/pom.xml`, hook `.cjs`, `.githooks/*`) → **MUST** chạy agent `code-reviewer` trên diff chưa commit so với `origin/main` (`git diff origin/main`, gồm cả file mới chưa track); còn CRITICAL/MAJOR → sửa, chạy lại `code-reviewer` cho phần sửa rồi mới commit. Đây là cổng review **duy nhất**: không chạy thêm skill `code-review`, không review lại sau push / sau khi tạo PR. **Diff không có code** (tài liệu `*.md`, `.claude/settings.json`, `.gitignore`, `.gitattributes`, `.env.example`…) → không chạy `code-reviewer` lẫn `code-review`. Git pre-commit vẫn quét secret.
- **Đồng bộ `main`:** trên **mọi nhánh khác `main`**, agent **MUST tự** chạy `git fetch origin && git rebase --autostash origin/main` tại 3 thời điểm: (1) trước khi bắt đầu triển khai một chức năng, (2) trước mỗi commit, (3) trước mỗi push.
  - Conflict khi rebase → `git rebase --abort`, **dừng**, báo danh sách file conflict; **NEVER** tự resolve. Conflict khi áp lại autostash → dừng, báo (stash vẫn còn trong `git stash list`).
  - `fetch` lỗi (mạng/quyền) → báo, **không** khẳng định đã đồng bộ.
  - Rebase kéo về commit mới → chạy lại test liên quan trước khi commit/push.
  - Nhánh đã push trước đó → push sau rebase dùng `git push --force-with-lease` (vẫn chỉ push khi user yêu cầu); **NEVER** `--force`; **NEVER** force-push `main`.
- **Quy ước tên nhánh:** mọi nhánh tạo từ `origin/main` (sau `git fetch origin`); `<MÃ>` viết hoa như mã spec/design, phần mô tả kebab-case chữ thường.

  | Nhánh | Chứa | Ví dụ |
  |---|---|---|
  | `feature/<MÃ>-<mô-tả>` | Code chức năng mới / sửa chức năng + SRS/Design của chính chức năng đó (commit riêng `docs(<MÃ>): ...`) | `feature/USER-001-user-team-management` |
  | `docs/<MÃ>-<chủ-đề>` | Chỉ tài liệu không kèm code: `docs/agent/business-spec/`, `docs/agent/design/<MÃ>/`, `docs/agent/decisions/`, `docs/database/` | `docs/USER-001-update-api-spec` |
  | `refactor/<chủ-đề>` | Tái cấu trúc code **không đổi hành vi** (API/DB/nghiệp vụ giữ nguyên) + design/ADR bị ảnh hưởng (commit riêng `docs(REFACTOR): ...`); framework bị ảnh hưởng vẫn tách sang `chore/agent-framework-<chủ-đề>` | `refactor/multi-module-structure` |
  | `chore/agent-framework-<chủ-đề>` | Claude-framework: `CLAUDE.md`, `.claude/`, `.agent-bootstrap/`, `.githooks/`, `docs/agent/hooks/`, `docs/agent/*.md`, các `_template/` | `chore/agent-framework-test-env-rebase` |

  - **Tự chọn nhánh trước khi bắt đầu việc mới** (SRS, Design, code, framework) — **MUST**:
    1. Xác định nhánh đích: SRS/Design của chức năng sẽ code → `feature/<MÃ>-<mô-tả>` (code sau làm tiếp trên chính nhánh này); tài liệu không gắn code → `docs/<MÃ>-<chủ-đề>`; framework → `chore/agent-framework-<chủ-đề>`; tái cấu trúc code không đổi hành vi → `refactor/<chủ-đề>`. Chưa rõ `<MÃ>`/nhóm → hỏi user.
    2. Working tree còn thay đổi chưa commit **của việc khác** → **dừng**, hỏi user (commit hay stash); **NEVER** mang sang nhánh mới.
    3. `git fetch origin` → nhánh hiện tại **là** nhánh đích → `git rebase --autostash origin/main`; nhánh đích đã tồn tại → checkout rồi rebase; chưa có → `git switch -c <nhánh-đích> origin/main`.
  - Trước commit: thay đổi thuộc nhóm khác nhánh hiện tại (vd framework trên nhánh `feature/`) → **NEVER** commit lẫn; tách sang nhánh đúng nhóm (tạo từ `origin/main`), không rõ nhóm → hỏi user.
- **Merge vào `main` chỉ qua merge request (GitHub Pull Request):** commit (diff có code thì đã qua `code-reviewer`, xem quy tắc commit ở trên) → push nhánh → `gh pr create --base main` → user tự merge trên GitHub **hoặc** yêu cầu agent merge bằng `gh pr merge <số> --squash` (chỉ khi user yêu cầu rõ PR nào; mặc định squash, **không** kèm `--delete-branch`). **NEVER** `git merge` / `git rebase` / `git commit` / `git push` trực tiếp lên `main` local hay `origin/main`. Tạo PR vẫn chỉ khi user yêu cầu.
- Debug → **MUST** reproduce trước khi nêu nguyên nhân.
- **Subagent chạy trên repo thật** (mặc định, không `isolation: "worktree"`): agent đọc/review (`architect-reviewer`, `code-reviewer`, `Explore`) phải thấy diff chưa commit; agent viết code chạy tuần tự với phiên chính. Chỉ dùng worktree khi user yêu cầu nhiều agent ghi code song song trên phần độc lập — worktree tạo từ commit (không có thay đổi chưa commit) và không có `.env.dev` (test cần DB không chạy được nếu chưa copy). Xong việc → gộp kết quả về rồi `git worktree remove` (không xoá nhánh).

### NEVER
- **NEVER** code chức năng mới khi chưa có thiết kế được duyệt.
- **NEVER** báo xong / tick DoD khi test chưa chạy hoặc còn fail.
- **NEVER** tạo thành phần dùng chung mới khi chưa có Reuse Report.
- **NEVER** suy đoán hợp đồng service/repo khác khi chưa tra nguồn — không tra được → `[cần xác nhận]`.
- **NEVER** tự commit / push / tạo MR khi chưa được yêu cầu.
- **NEVER** xoá nhánh local hay remote (`git branch -d/-D`, `git push --delete`, `gh pr merge --delete-branch`, xoá worktree kèm nhánh) khi user chưa yêu cầu rõ nhánh nào — kể cả nhánh đã merge.
- **NEVER** tự sửa tài liệu thiết kế khi đang review (`architect-reviewer`); chỉ ghi findings.
- **NEVER** `@` mọi tài liệu — chỉ `@` file cần ở hầu hết mọi phiên.
- **NEVER** sửa file do generator quản lý (`.mvn/`, `mvnw`, `mvnw.cmd`, `HELP.md`).

---

## §9 — Tự điều chỉnh cách làm việc

- Hạ tầng agent phải khớp repo. Khi agent lệch quy ước repo lặp lại, lệnh trong hub hết đúng, hoặc quy tắc mâu thuẫn code → skill `workflow-adapt`.
- Điều chỉnh đi vào **kênh tuỳ biến** (`docs/agent/`), có bằng chứng, được duyệt, ghi dấu vết. Không nới guardrail ở §8.
