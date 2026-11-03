# Hồ sơ dự án — ArenaFlow-be

> **Cách dùng:** đây là **nguồn duy nhất** cho mọi `<PLACEHOLDER>` khi dùng mẫu trong `samples/`.
>
> - Mỗi giá trị **MUST** có cột "Bằng chứng" (path/section trong repo, hoặc "người dùng xác nhận").
> - Không xác định được → ghi `[cần xác nhận]`, KHÔNG đoán.
> - KHÔNG ghi secret, email, URL nội bộ, giá trị `.env`.

## 1. Dự án

| Mục | Giá trị | Bằng chứng |
|---|---|---|
| Tên | `ArenaFlow-be` | `pom.xml` → `<artifactId>ArenaFlow</artifactId>`, tên thư mục repo |
| Mô tả | Nền tảng Đặt lịch Sân thể thao & Ghép kèo Thời gian thực (backend) | Người dùng xác nhận |
| Loại | `service` (Spring Boot backend REST API) | `pom.xml` → `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security` |
| Quy mô đội | 1 người | Người dùng xác nhận |
| Kho lưu mã nguồn | GitHub: `phananh2005/ArenaFlow-be` | Repo được tạo bằng `gh repo create` và đã push branch `master` |
| Ngôn ngữ tài liệu sinh ra | `vi` | Quy tắc ngôn ngữ của prompt |

## 2. Harness (AI agent đang dùng)

| Mục | Giá trị | Bằng chứng |
|---|---|---|
| Harness chính | Claude Code | Phiên hiện tại đang chạy Claude Code |
| Harness phụ | không có | |
| File hub chỉ dẫn | `CLAUDE.md` | `adapters/harness-matrix.md` |
| Hỗ trợ agent/sub-agent | có | `.claude/agents/*.md` |
| Hỗ trợ skill | có | `.claude/skills/<tên>/SKILL.md` |
| Hỗ trợ command do user gọi | có | `.claude/commands/*.md` |
| Hỗ trợ hook | có | `.claude/settings.json → hooks` |
| Điều kiện nạp cấu hình | Mở tại gốc repo + workspace đã trust | `adapters/harness-matrix.md` |

## 3. Mức áp dụng

| Mục | Giá trị | Lý do |
|---|---|---|
| Mức | `full` | Người dùng chỉ định tường minh |

## 4. Tầng (layer) — mỗi tầng một dòng

| `id` | Thư mục | Ngôn ngữ | Lệnh build | Lệnh test | Lệnh lint | Tài liệu convention | Bằng chứng |
|---|---|---|---|---|---|---|---|
| `backend` | `src/main/java/` | Java 21 | `./mvnw compile` | `./mvnw test` (unit thuần: độc lập; integration: tự nạp `.env.dev`) | không có | không có | `pom.xml` → `<java.version>21</java.version>`, Maven Wrapper; `./mvnw compile` đã BUILD SUCCESS |

> Unit test thuần chạy không cần `.env.dev`. Integration test dùng DB dev qua `.env.dev`: `. ./.env.dev && export DATABASE_URL DATABASE_USERNAME DATABASE_PASSWORD JWT_SECRET && ./mvnw -Dtest=<Test> test`.

## 5. Đường dẫn

| Placeholder | Giá trị | Bằng chứng |
|---|---|---|
| `<AGENT_DIR>` | `.claude` | Harness Claude Code, mặc định |
| `<KNOWLEDGE_DIR>` | `docs/agent` | Tạo mới — repo chưa có thư mục tương đương |
| `<STATE_DIR>` | `docs/agent/state` | Tạo mới |
| `<HOOKS_DIR>` | `docs/agent/hooks` | Tạo mới |

## 6. Quyền sở hữu & đồng bộ

| Mục | Giá trị | Bằng chứng |
|---|---|---|
| File do generator/sync quản lý | `.mvn/wrapper/`, `mvnw`, `mvnw.cmd`, `HELP.md` (Spring Initializr) | Nội dung và cấu trúc là output của Spring Initializr |
| Kênh tuỳ biến của dự án | `docs/agent/CLAUDE.md` | Tạo mới — hub gốc `CLAUDE.md` trỏ tới |
| Submodule | không phát hiện | `git submodule status` — không có submodule được khai báo |

## 7. State & bàn giao phiên

| Mục | Giá trị | Bằng chứng |
|---|---|---|
| Dùng state task | có | Mức full |
| Cách đặt `<DEV_ID>` | `local-dev` (đội 1 người) | Người dùng xác nhận |
| State được commit hay gitignore | gitignore | Phiên bản đề xuất: state là dữ liệu tạm, không nên commit |
| Tên command lưu phiên | `af-handoff` | Tránh trùng `/handoff` built-in; `af` = ArenaFlow |
| Tên command mở lại phiên | `af-resume` | Tránh trùng `/resume` built-in |

## 8. Git & review

| Mục | Giá trị | Bằng chứng |
|---|---|---|
| Nhánh mặc định | `main` | `gh api repos/phananh2005/ArenaFlow-be --jq .default_branch` → `main` (2026-11-03); `master` không còn tồn tại |
| Nhánh được bảo vệ | `main`: PR bắt buộc (0 approval), áp dụng cả admin (`enforce_admins`), cấm force-push/xóa, không yêu cầu status checks; thêm Claude guard chặn force-push `main`/`master` | `gh api -X PUT .../branches/main/protection` rồi GET xác minh (2026-11-03) |
| Đồng bộ nhánh | Mọi nhánh khác `main`: agent tự `git fetch origin && git rebase --autostash origin/main` trước khi bắt đầu chức năng, trước commit, trước push | Người dùng yêu cầu 2026-11-03; `CLAUDE.md` §8 |
| Quy ước tên nhánh | `feature/<MÃ>-<mô-tả>` (code + SRS/Design của chức năng) · `docs/<MÃ>-<chủ-đề>` (chỉ tài liệu) · `chore/agent-framework-<chủ-đề>` (Claude-framework); tạo từ `origin/main`; chi tiết `CLAUDE.md` §8 | Người dùng chốt 2026-11-03 (dự án 1 người) |
| Quy ước commit | không có | |
| Cách merge vào `main` | Chỉ qua Pull Request (`gh pr create --base main`); agent không merge/push trực tiếp lên `main`; user tự merge PR hoặc yêu cầu agent `gh pr merge <số> --squash`. GitHub branch protection enforce (PR bắt buộc, cả admin) | Người dùng yêu cầu 2026-11-03; `CLAUDE.md` §8 |
| Nền tảng review | GitHub | Repo được tạo tại `https://github.com/phananh2005/ArenaFlow-be` |

## 9. Playbook & hook

| Mục | Giá trị | Bằng chứng |
|---|---|---|
| Playbook đang bật + mức enforcement | TDD bắt buộc theo hướng dẫn (Red → Green → Refactor); không có runtime hook enforcement riêng | Người dùng yêu cầu luôn bật TDD; `CLAUDE.md` §5, `.claude/agents/developer.md` |
| Runtime cho hook | `node` v24.18.0 | `node --version` trên máy hiện tại |

## 10. CI

| Mục | Giá trị | Bằng chứng |
|---|---|---|
| Có CI trong repo | không thấy trong repo | Không có `.github/workflows/`, `.gitlab-ci.yml`, `Jenkinsfile` hay tương đương |
| File CI | không có | |

## 11. Thiết kế, ADR, phi chức năng, tra cứu ngoài

| Mục | Giá trị | Bằng chứng |
|---|---|---|
| Có quy trình thiết kế trước khi code | có | Người dùng yêu cầu 2026-11-01 |
| Thư mục spec nghiệp vụ / thiết kế | `docs/agent/design/` | Tạo mới 2026-11-01 |
| `<design-standards>` | `docs/agent/design-standards.md` | Tạo mới 2026-11-01 |
| `<thư mục ADR>` | `docs/agent/decisions` | Tạo mới — mức full |
| Tầng có DB/ORM | có — PostgreSQL | `pom.xml` → `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `flyway-database-postgresql` |
| `<non-functional-standards>` + ngưỡng | `docs/agent/non-functional-standards.md` · `N_DETAIL=3`, `N_LIST=4`, `N_WRITE=6` (đã chốt) | Người dùng xác nhận ngưỡng 3/4/6 |
| Nguồn tra cứu ngoài repo | Neon MCP từ plugin `neon-postgres@neon` (`plugin:neon:neon`), không pin URL; chỉ được thao tác project `misty-dew-90700890` | `.claude/settings.json` → `extraKnownMarketplaces.neon` + `enabledPlugins`; OAuth, không lưu API key trong repo |
| `<external-knowledge>` | `docs/agent/external-knowledge.md` — quy tắc bắt buộc truyền `projectId` | Lần dùng đầu cần OAuth trong Claude Code |

## 12. Quyết định thích ứng

> Mỗi lần mẫu được điều chỉnh/bỏ cho khớp repo — ghi 1 dòng.
> Bảng tín hiệu: `adapters/adaptation-rules.md`.

| Ngày | Tín hiệu | Bằng chứng | Điều chỉnh | Lý do | Đảo ngược bằng |
|---|---|---|---|---|---|
| 2026-10-31 | Một tầng duy nhất (backend) | `src/main/java/` chỉ có package backend; `pom.xml` chỉ có dependency backend | Bỏ hậu tố tầng (dùng `developer`, `code-reviewer`, `debug`, `code-review` thay vì `developer-backend`…); bỏ bước "xác định tầng" trong routing | Repo một tầng → routing theo tầng là thừa | Thêm lại hậu tố khi repo mở rộng thêm tầng |
| 2026-10-31 | Chưa có quy trình thiết kế trước code nhưng người dùng chọn giữ ADR | Không có thư mục spec/design/ADR trước bootstrap; người dùng xác nhận giữ ADR | Không tạo `solution-architect`/`architect-reviewer`; giữ `docs/agent/decisions/` + template và thêm skill `decisions-relevance` | ADR là nơi ghi quyết định kiến trúc dài hạn, không đòi hỏi quy trình thiết kế đầy đủ | Bỏ ADR/skill nếu người dùng quyết định không dùng |
| 2026-11-01 | Người dùng yêu cầu thêm tầng thiết kế cho BE và FE; repo hiện tại là backend | Yêu cầu tường minh trong phiên 2026-11-01 | Tạo `solution-architect`, `architect-reviewer`, `docs/agent/design-standards.md`, `docs/agent/design/_template/` (3 tài liệu: API, Class, Flow); cập nhật routing: chức năng mới → thiết kế → duyệt → code | Tầng thiết kế bảo đảm có hợp đồng API + thiết kế class rõ ràng trước khi code; giảm rủi ro thay đổi lớn sau | Xoá tầng thiết kế nếu quy trình không phù hợp |
| 2026-11-01 | Người dùng yêu cầu thêm agent BA để phân tích yêu cầu trước thiết kế | Yêu cầu tường minh trong phiên 2026-11-01 | Tạo `business-analyst`, `docs/agent/business-spec/_template/` (5 tài liệu: Overview, Use Cases, Business Rules, Data Model Conceptual, Acceptance Criteria); cập nhật routing: yêu cầu mơ hồ → BA → spec nghiệp vụ → thiết kế | Agent BA làm rõ yêu cầu và formal hóa spec trước khi thiết kế kỹ thuật; tách rõ tầng phân tích ↔ thiết kế | Xoá agent BA nếu spec inline đủ dùng |
| 2026-10-31 | Hook được người dùng duyệt bật đầy đủ; Node có sẵn | Người dùng chọn "Cài và bật tất cả"; Node v24.18.0 | Tạo Claude hooks + secret scan Git pre-commit; cấu hình `.claude/settings.json`; cài `core.hooksPath=.githooks` local | Hook đã có fixture và kiểm tra thực tế; cấu hình Git local không đồng bộ qua clone | Xóa hook config; gỡ `core.hooksPath` local |
| 2026-10-31 | Người dùng chốt ngưỡng truy vấn | Xác nhận trong hội thoại | Áp dụng 3/4/6 cho detail/list/write | Đã được người dùng chốt | Điều chỉnh qua quyết định mới |
| 2026-10-31 | Có DB (PostgreSQL + JPA + Flyway) | `pom.xml` → `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `flyway-database-postgresql`; người dùng xác nhận chuyển toàn bộ ứng dụng sang PostgreSQL | Bật chuẩn phi chức năng: phân trang, ngưỡng số truy vấn, index | Tầng có DB cần kiểm soát hiệu năng truy vấn | Bỏ khi repo không còn dùng DB |
| 2026-10-31 | PostgreSQL là DB ứng dụng; test DB chưa cấu hình riêng | Người dùng chọn PostgreSQL toàn ứng dụng và trả lời "để lại" cho cấu hình DB test | Không thêm H2/Testcontainers; test cần PostgreSQL chạy để PASS | Không thêm dependency test hoặc Docker requirement | Cấu hình DB test riêng khi người dùng yêu cầu |
| 2026-10-31 | GitHub branch protection | API xác nhận protection cho `master` | PR bắt buộc, force-push/xóa bị cấm; không yêu cầu status checks/approval | Repo chưa có CI; chủ dự án làm một mình | Chỉnh qua GitHub branch protection settings |
| 2026-11-03 | Nhánh mặc định đổi sang `main`, chưa protected (404) | `gh api` GET protection `main` → 404 | Người dùng yêu cầu bật qua `gh api`: PUT protection `main` — PR bắt buộc, 0 approval, `enforce_admins=true`, cấm force-push/xóa, không status checks; GET xác minh khớp | Enforce quy tắc "merge vào `main` chỉ qua PR" ở phía GitHub, kể cả với owner | `gh api -X DELETE repos/phananh2005/ArenaFlow-be/branches/main/protection` hoặc Settings → Branches |
| 2026-10-31 | Neon project đã tạo và liên kết | Project `misty-dew-90700890`, org `org-purple-hall-06771419`, region `aws-ap-southeast-1`; default branch `main`, DB `neondb` | `.mcp.json` pin Neon MCP vào project, OAuth; không lấy/in connection string | Bảo đảm agent chỉ truy cập đúng project | Gỡ `.mcp.json` hoặc unlink bằng Neon CLI |
| 2026-11-04 | Neon MCP bị trùng: `Neon` (`.mcp.json`, pin `projectId`) và `plugin:neon:neon` (plugin `neon-postgres@neon`) | `claude mcp list` liệt kê cả 2 server cùng `mcp.neon.tech`; người dùng yêu cầu chỉ giữ server của plugin | Gỡ `Neon` khỏi `.mcp.json`, xóa `.mcp.json.tmp` (bản sao sót); cài plugin project scope + `extraKnownMarketplaces.neon` trong `.claude/settings.json`; thay pin URL bằng quy tắc bắt buộc truyền `projectId` trong `external-knowledge.md` | Một server duy nhất, kèm 7 skill `neon:*`, cả team nhận qua `.claude/settings.json` | Thêm lại `Neon` với `?projectId=misty-dew-90700890` vào `.mcp.json` và tắt `plugin:neon:neon` qua `/mcp` |
| 2026-10-31 | Không thấy CI trong repo | Không có file CI trong checkout | Có Claude guard + Git pre-commit secret scan; chưa xác minh CI ngoài repo | Không tạo hook lặp chức năng CI chưa biết | Cập nhật khi xác nhận CI ngoài repo |
| 2026-10-31 | Dự án một người | Người dùng xác nhận dự án cá nhân | State dùng namespace `local-dev`; giữ procedure `code-reviewer` và skill `code-review` riêng | Solo workflow vẫn cần review toàn diện và review nhanh | Tách namespace nếu có contributor mới |
| 2026-10-31 | Maven test đã chạy trước khi cấu hình PostgreSQL | `./mvnw test` khi còn MySQL config compile thành công, Spring context fail do thiếu datasource | Ghi rõ test chưa PASS; sau khi có PostgreSQL project, cần cấu hình env và chạy lại | Kết quả cũ không xác nhận test pass với cấu hình hiện tại | Chạy test sau khi datasource sẵn sàng |
| 2026-10-31 | Maven compile đã xác nhận sau khi chuyển PostgreSQL | `./mvnw compile` trả `BUILD SUCCESS` sau khi dependency PostgreSQL được cập nhật | Lệnh compile được xác nhận | Compile không thay thế integration test | Chạy lại sau thay đổi code liên quan |
| 2026-10-31 | Node không được khai báo trong manifest Java | Không có `package.json`; nhưng `node --version` trả `v24.18.0` trên máy hiện tại | Dùng Node cho hook Claude Code và Git pre-commit trên máy này; ghi rõ yêu cầu khi clone khác | Runtime đã xác nhận cục bộ, không bảo đảm trên mọi máy | Clone thiếu Node thì cài Node hoặc tắt hooks có liên quan |
| 2026-11-03 | Quy tắc "mọi test cần PostgreSQL, thiếu DB → blocker" lệch thực tế repo | 15/17 test class không dùng `@SpringBootTest`/`@DataJpaTest`; `progress.md` ghi unit test thuần chạy không cần `.env.dev`; người dùng yêu cầu và chọn "Agent tự nạp .env.dev" vì đây là DB dev | Unit test thuần chạy độc lập; integration test → agent tự nạp `.env.dev` và chạy, không hỏi; chỉ blocker khi thiếu `.env.dev` hoặc DB dev không kết nối được. Sửa `CLAUDE.md` §8, `.claude/AGENTS.md` §4, `.claude/agents/developer.md`, `docs/agent/README.md`, mục 4 hồ sơ | Bỏ blocker thừa cho unit test; DB dev chấp nhận ghi từ test. Cổng test (phải PASS mới báo xong) giữ nguyên | `git revert`; đổi lại khi `.env.dev` trỏ DB dùng chung/production hoặc khi thêm Testcontainers/H2 |
| 2026-11-03 | Chưa có quy tắc đồng bộ nhánh với `main`; hồ sơ ghi nhánh mặc định `master` lệch thực tế | Grep `rebase` trong hub/procedure/skill: không có; `git branch -a` chỉ có `main`; GitHub default branch `main`, chưa protected | Thêm MUST tự rebase `origin/main` (autostash) cho mọi nhánh khác `main` trước khi bắt đầu chức năng/commit/push; conflict → abort + báo; push sau rebase dùng `--force-with-lease`. Sửa `CLAUDE.md` §8, `.claude/agents/developer.md`, `.claude/skills/task-state-init`, hồ sơ §8 (`master` → `main`) | Người dùng yêu cầu, chọn rebase cho mọi nhánh và agent tự rebase | `git revert`; bỏ dòng MUST trong `CLAUDE.md` §8 |
| 2026-11-03 | Chưa có quy ước tên nhánh; thay đổi framework đang trộn với code trên nhánh feature | Hồ sơ §8 ghi "không có"; working tree nhánh `feature/USER-001-...` chứa cả `CLAUDE.md`/`.claude/` lẫn `src/` | Thêm quy ước 3 nhóm nhánh `feature/` · `docs/` · `chore/agent-framework-` vào `CLAUDE.md` §8; NEVER commit lẫn nhóm; `task-state-init` tự tạo nhánh `feature/` khi đang ở `main`; hồ sơ §8 | Người dùng (làm 1 mình) chọn tách riêng framework, giữ SRS/Design chung nhánh feature | `git revert`; bỏ mục quy ước trong `CLAUDE.md` §8 |
| 2026-11-03 | Chưa có quy tắc cách merge vào `main` | Grep `merge`/`gh pr` trong hub/procedure: chỉ có "review trước merge"; `main` chưa protected trên GitHub (404) | Thêm MUST: merge vào `main` chỉ qua PR (`gh pr create --base main` → `code-reviewer` → user merge); NEVER merge/commit/push trực tiếp lên `main` | Người dùng yêu cầu | `git revert`; bỏ dòng trong `CLAUDE.md` §8 |
| 2026-11-03 | User muốn agent merge PR thay mình | User chọn "đổi quy tắc", merge squash PR #1, #2 | Cho phép agent `gh pr merge <số> --squash` khi user yêu cầu rõ PR; vẫn NEVER merge/push trực tiếp `main` | Làm 1 mình, giảm thao tác tay trên GitHub | Bỏ vế "hoặc yêu cầu agent merge" trong `CLAUDE.md` §8 |
| 2026-11-03 | Ngoại lệ 1 lần: viết lại lịch sử `main` để README là root commit | User chọn rõ "Commit gốc thật (root)" sau khi được nêu rủi ro (tắt protection, force-push `main`) | Agent chuẩn bị lịch sử mới local; **user tự chạy** lệnh tắt protection → force-push `main` → bật lại protection (guard hook chặn agent force-push `main`, không vượt hook); rebase lại nhánh chore/docs | Yêu cầu tường minh của user; không phải thay đổi quy tắc — NEVER force-push `main` vẫn giữ | Không đảo được sau khi force-push (hash cũ chỉ còn trong reflog local) |
| 2026-11-03 | Chỉ tự tách nhánh khi implement và đang ở `main`; SRS/Design không có quy tắc nhánh | Grep `nhánh/branch` trong `business-analyst.md`, `solution-architect.md`: không có; `task-state-init` chỉ xử lý trường hợp đang ở `main` | MUST tự chọn nhánh đích trước mọi việc mới (SRS, Design, code, framework): đúng nhánh → rebase; khác → checkout/tạo từ `origin/main`; thay đổi chưa commit của việc khác → dừng, hỏi. Sửa `CLAUDE.md` §8, `business-analyst`, `solution-architect` (Bước 0), `developer`, `task-state-init` | Người dùng duyệt đề xuất; tránh trộn 2 chức năng trên cùng nhánh | `git revert`; bỏ mục "Tự chọn nhánh" trong `CLAUDE.md` §8 |
| 2026-11-03 | Code USER-001 lệch design ở nhiều điểm nhỏ (class phụ, kiểu thời gian projection…); chưa có quy tắc khi nào cập nhật design cho chức năng mới | Phase 3–4 USER-001: 8 điểm lệch; quy tắc cũ chỉ nói cập nhật design cho "sửa chức năng đã có" | Chức năng mới: ghi lệch vào bảng "Lệch thiết kế" trong `current-task.md`, NEVER sửa design giữa chừng; cập nhật design một lần sau khi mọi phase code xong + test PASS → `architect-reviewer`. Lệch đổi API/DB/nghiệp vụ → dừng hỏi trước. Sửa `CLAUDE.md` §8, `developer.md`, `progress-tracker`, template `current-task.md` | Người dùng yêu cầu: cập nhật design sau cùng | `git revert`; bỏ mục "Lệch thiết kế khi đang code" trong `CLAUDE.md` §8 |

## 13. Còn chờ xác nhận

- [ ] Neon project đã provision database `neondb`; ứng dụng local chưa được cấu hình kết nối qua `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`. Không lưu connection string/secret trong hồ sơ.
- [ ] Chưa chạy `./mvnw test` sau khi chuyển PostgreSQL; cần cấu hình datasource và xác nhận PostgreSQL reachable.
- [x] Convention API backend (`api-convention.md`), convention tầng backend (`convention-backend.md`), security backend (`security-backend.md`) — đã tạo 2026-11-01.
- [x] `./mvnw compile` trả `BUILD SUCCESS` sau khi đổi dependency PostgreSQL.
- [x] TDD bắt buộc theo Red → Green → Refactor; chưa có hook runtime ép thứ tự test/code.
- [x] GitHub protection trên `master`: PR bắt buộc, force-push và xóa nhánh bị cấm; không bắt buộc checks/approval.
- [x] Node.js v24.18.0 có trên máy hiện tại; mỗi clone khác cần runtime Node để hooks hoạt động.
- [x] Ngưỡng truy vấn 3/4/6 do người dùng chốt.
- [x] Tầng thiết kế đã tạo với 2 agents + design-standards + templates.
- [ ] Chưa xác minh CI bên ngoài repo.
