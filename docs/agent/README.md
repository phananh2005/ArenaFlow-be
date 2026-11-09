# Hạ tầng Agent — ArenaFlow-be

> Hướng dẫn sử dụng, ownership và kiểm chứng hạ tầng AI agent.

## Cấu trúc

```text
ArenaFlow-be/
├── CLAUDE.md
├── .claude/
│   ├── settings.json                   # Hook Claude Code
│   ├── AGENTS.md                       # Routing
│   ├── agents/
│   │   ├── business-analyst.md
│   │   ├── solution-architect.md
│   │   ├── architect-reviewer.md
│   │   ├── developer.md
│   │   └── code-reviewer.md
│   ├── skills/
│   │   ├── task-state-init/SKILL.md
│   │   ├── progress-tracker/SKILL.md
│   │   ├── reuse-scan/SKILL.md
│   │   ├── code-review/SKILL.md
│   │   ├── debug/SKILL.md
│   │   ├── workflow-adapt/SKILL.md
│   │   └── decisions-relevance/SKILL.md
│   └── commands/
│       ├── af-handoff.md
│       └── af-resume.md
├── .githooks/pre-commit                # Secret scan staged
├── .github/workflows/publish-spec.yml  # Publish SRS + API_Spec sang ArenaFlow-spec cho FE
├── docs/agent/
│   ├── CLAUDE.md                       # Kiến thức dự án
│   ├── design-standards.md             # Chuẩn viết thiết kế (API, Class, Flow)
│   ├── external-knowledge.md           # Quy tắc Neon MCP và bảo vệ credentials
│   ├── README.md
│   ├── non-functional-standards.md
│   ├── adaptation-rules.md
│   ├── business-spec/
│   │   ├── README.md
│   │   └── _template/
│   │       ├── Overview.md
│   │       ├── Use_Cases.md
│   │       ├── Business_Rules.md
│   │       ├── Data_Model_Conceptual.md
│   │       └── Acceptance_Criteria.md
│   ├── design/
│   │   ├── README.md
│   │   └── _template/
│   │       ├── API_Spec.md
│   │       ├── Component_Class_Design.md
│   │       └── Processing_Flow.md
│   ├── decisions/
│   │   ├── README.md
│   │   └── _template.md
│   ├── state/_template/
│   │   ├── current-task.md
│   │   ├── progress.md
│   │   ├── session-handoff.md
│   │   ├── decisions-log.md
│   │   └── tech-debt.md
│   ├── hooks/
│   │   ├── agent-hooks.cjs
│   │   └── fixture-tests.cjs
│   └── scripts/
│       ├── publish-spec.cjs            # Chép tài liệu FE cần sang thư mục đích
│       └── publish-spec-tests.cjs
└── .agent-bootstrap/project-profile.md # Hồ sơ dự án
```

## Cách dùng

1. Mở Claude Code tại gốc repo để nạp `CLAUDE.md` và project hooks.
2. **Yêu cầu nghiệp vụ mơ hồ:** `business-analyst` (phân tích) → duyệt → spec nghiệp vụ.
3. **Chức năng mới:** `solution-architect` (thiết kế) → duyệt → `architect-reviewer` → duyệt → `developer` (code).
4. **Sửa nhỏ:** thẳng `developer`.
5. Khi implement, đọc skill `task-state-init`, procedure `developer`, rồi cập nhật `progress-tracker`.
6. Kết thúc phiên: `/af-handoff`; tiếp tục phiên: `/af-resume`.
7. Trước commit: diff có code (`*/src/**`, POM, hook, `docs/agent/scripts/*.cjs`, `.github/workflows/*`) → agent `code-reviewer`; không có code → không review (`CLAUDE.md` §8). Git pre-commit quét secret trong staged content.
8. Rà ADR khi cần bằng skill `decisions-relevance`.

## Hooks

Claude Code hooks được cấu hình trong `.claude/settings.json`:

- `PreToolUse` cho `Bash`: chặn force-push vào `main`/`master`, xóa đệ quy target nguy hiểm, `TRUNCATE TABLE`, và `DROP` không có `IF EXISTS`.
- `PostToolUse` cho `Bash|Write|Edit`: chỉ ghi thời gian và tên tool vào `.claude/logs/tool-use.jsonl`; không lưu lệnh hoặc nội dung file; log được làm rỗng khi vượt 1 MiB.
- `SessionStart`: nhắc nếu clone chưa cấu hình Git hook.

Git pre-commit dùng cùng module secret-scan. Cấu hình local cho clone hiện tại:

```sh
git config --local core.hooksPath .githooks
```

Gỡ cấu hình local:

```sh
git config --local --unset core.hooksPath
```

`core.hooksPath` không đi theo clone. Cần Node.js trên PATH; thiếu Node thì pre-commit cảnh báo và cho commit. Hooks Claude Code yêu cầu mở phiên ở gốc repo và workspace đã trust. Dùng `/hooks` để xem, chỉnh hoặc tắt.

## Publish tài liệu cho FE

- SRS và API_Spec ở BE là **nguồn duy nhất**. Khi merge vào `main` thay đổi ở `business-spec/<MÃ>/`, `design/<MÃ>/API_Spec.md`, `design/<MÃ>/CHANGELOG.md`, `api-convention.md` hoặc `decisions/`, workflow `.github/workflows/publish-spec.yml` tự chép sang repo chỉ-đọc `phananh2005/ArenaFlow-spec` (FE gắn làm git submodule). Có thể chạy tay bằng `workflow_dispatch` trên `main`.
- Đích: `business-spec/<MÃ>/`, `api/<MÃ>/API_Spec.md` + `CHANGELOG.md`, `api/api-convention.md`, `decisions/`, `README.md` (mục lục, sinh tự động), `SOURCE` (SHA commit BE). Không publish `Component_Class_Design.md`, `Processing_Flow.md`, `_template`, `docs/database/`, state, hooks, mã nguồn.
- FE chỉ thấy thay đổi **sau khi merge vào `main`**. **NEVER** sửa trực tiếp ArenaFlow-spec — bị ghi đè ở lần publish sau.
- Entry **MAJOR** trong `CHANGELOG.md` của API_Spec là tín hiệu FE phải xem lại phần gọi API.
- **Chặn tự động** (trước khi xoá/chép gì ở đích): `API_Spec.md` và entry mới nhất của `CHANGELOG.md` (bỏ qua comment HTML) không được nhắc tài liệu không publish (`Component_Class_Design`, `Processing_Flow`, `docs/database`, `design-standards`, convention BE, `.java`), annotation BE, tên class `…Repository`/`…ServiceImpl`/`…Retrier`, tên migration `V<n>__…`; link tương đối phải trỏ tới tài liệu được publish và còn đúng ở đích (vd `../../api-convention.md` gãy vì đích là `api/api-convention.md`). Vi phạm → publish dừng, liệt kê `file:dòng`; sửa tài liệu theo `design-standards.md` §11 rồi merge lại. CHANGELOG kiểm từ đầu file tới hết entry mới nhất (không có heading `## [` → kiểm cả file). Giới hạn đã biết: link percent-encoded (`%20`…) bị chặn nhầm — đặt tên file ASCII; nội dung trong code fence vẫn bị kiểm như văn bản thường (comment HTML trong code fence bị bỏ qua).
- Thư mục `<MÃ>` phải khớp `^[A-Z][A-Z0-9]*-\d+$`. Gom theo domain/cụm (`<DOMAIN>/<MÃ>/`) → publish báo lỗi và dừng; sửa script trước.
- Token ghi sang ArenaFlow-spec là secret `SPEC_REPO_TOKEN` của repo BE; không ghi token vào file.
- Điều kiện chạy: repo `ArenaFlow-spec` đã tồn tại và nhánh `main` có ít nhất một commit (tạo repo kèm README) — repo rỗng thì bước checkout lỗi. README này bị thay bằng README sinh tự động ở lần publish đầu.

## Kiểm chứng

```sh
node docs/agent/hooks/fixture-tests.cjs
node docs/agent/hooks/agent-hooks.cjs test
node docs/agent/scripts/publish-spec-tests.cjs
# Chạy thử publish vào thư mục tạm (không đụng ArenaFlow-spec):
node docs/agent/scripts/publish-spec.cjs . <thư-mục-tạm>
```

Project Neon `ArenaFlow-be` (`misty-dew-90700890`) ở Singapore có database mặc định `neondb` và branch `main`. Unit test thuần chạy độc lập, không cần `.env.dev`. Integration test cần DB: agent tự nạp `.env.dev` (DB dev; `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`) bằng `. ./.env.dev && export DATABASE_URL DATABASE_USERNAME DATABASE_PASSWORD JWT_SECRET && ./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -Dtest=<Test> test`. Chưa cấu hình Testcontainers hay DB test riêng; integration test ghi vào DB dev. Không báo test PASS nếu test chưa chạy đạt.

**Timezone khi build/test/chạy:** mọi lệnh `./mvnw` (build, test) kèm `-Duser.timezone=Asia/Ho_Chi_Minh`, vd `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh test`. Tham số này tới được JVM con chạy test của surefire (đã kiểm 2026-11-08: chạy với `-Duser.timezone=UTC` → log của JVM test có offset `Z`). Chạy jar: `java -Duser.timezone=Asia/Ho_Chi_Minh -jar …`. Cột thời gian là `timestamptz` nên PostgreSQL vẫn lưu theo UTC; timezone JVM quyết định offset của `OffsetDateTime.now()` và giờ trong log.

## Ownership

| File/thư mục | Chủ sở hữu | Quy tắc |
|---|---|---|
| `CLAUDE.md`, `.claude/` | Dự án | Sửa theo quy trình review; không ghi secret vào settings |
| `docs/agent/CLAUDE.md` | Dự án (kênh tuỳ biến) | Kiến thức dự án, ưu tiên hơn kiến thức tổ chức |
| `docs/agent/non-functional-standards.md` | Dự án | Ngưỡng truy vấn 3/4/6 do người dùng chốt |
| `docs/agent/adaptation-rules.md` | Dự án | Điều chỉnh sau khi có bằng chứng và được duyệt |
| `.mvn/`, `mvnw`, `mvnw.cmd`, `HELP.md` | Spring Initializr | Không sửa trực tiếp |
| `.githooks/` | Dự án | Git hooks version-controlled; cần cài `core.hooksPath` riêng mỗi clone |
| `.github/workflows/publish-spec.yml`, `docs/agent/scripts/` | Dự án | Pin action theo commit SHA; secret chỉ qua `secrets.*`; sửa → qua `code-reviewer` |
| Repo `ArenaFlow-spec` | Sinh tự động từ BE | Chỉ đọc; không sửa trực tiếp |

## Điều kiện sử dụng

- Claude Code mở tại gốc repo, workspace đã trust.
- Node.js có trên PATH để chạy Claude hooks và secret scan.
- Mỗi clone cần cấu hình `core.hooksPath` riêng.
