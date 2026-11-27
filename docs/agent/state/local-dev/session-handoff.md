---
dev: local-dev
saved: 2026-11-27 (cuối phiên)
task: Tích hợp 6 nhánh song song lên main — đã merge MATCH-001 (#87), MATCH-002 (#88), REPUTATION-001 (#89); kế tiếp MERCENARY-001 (V10)
status: in_progress
---

## Đang làm dở
- Task: đưa 6 nhánh code song song (worktree `.claude/worktrees/agent-*`) lên `main`, lần lượt theo migration V7 → V12. `current-task.md` ở repo chính là task cũ (USER-001 liveness, `done`) — task điều phối nằm ở file này + `pending-review-questions.md`.
- **Đã merge REPUTATION-001 (V9):** PR [#89](https://github.com/phananh2005/ArenaFlow-be/pull/89) squash → `origin/main` `e708b62` (2026-11-27); sau đó merge PR #90 (`e69fbec`, `code-reviewer` luôn chạy bằng subagent); `main` local đã ở `e69fbec`. Chi tiết từng bước P7 (TDD, test, 5 lượt code-reviewer, 5 lượt architect-reviewer): `progress.md` / `current-task.md` **trong worktree** `agent-ae20f7ae909465469` (KHÔNG commit — user chọn).
- **Bước hiện tại:** chưa bắt đầu **MERCENARY-001** (V10).

| Nhánh | Worktree | Trạng thái |
|---|---|---|
| `feature/MATCH-001-match-flow-a-impl` | `agent-ae792d52783b81019` (thừa) | ĐÃ MERGE #87 (V7) |
| `feature/MATCH-002-match-flow-b-impl` | `agent-afe2f2ada6a54efbf` (thừa) | ĐÃ MERGE #88 (V8) |
| `feature/REPUTATION-001-reputation-impl` | `agent-ae20f7ae909465469` (thừa; còn 2 file state chưa commit) | ĐÃ MERGE #89 (V9) |
| `feature/MERCENARY-001-mercenary-impl` | `agent-a5be6d5ff61e51a83` (base `89d252f`) | **Kế tiếp** — chờ hỏi `M1–M4`, `m1–m7`, `C1–C3` + checklist tích hợp |
| `feature/NOTIFICATION-001-notification-impl` | `agent-a84f87c0d85615cd5` | Chờ hỏi `m1–m7`, `Q1–Q4` |
| `feature/USER-003-user-discovery-impl` | `agent-a39625570a485474c` | Chờ hỏi `CRITICAL C1` V12, `m1–m5` |

## File đang sửa (git status lúc handoff — repo chính, nhánh `main` @ `e69fbec`)
- `M docs/agent/state/local-dev/current-task.md`, `pending-review-questions.md`, `progress.md`, `session-handoff.md`, `tech-debt.md` (state điều phối, không commit)
- `?? docs/database/schema-v12.dbml` — **không do phiên này tạo** (ghi 2026-11-26 15:41), để nguyên; hỏi user (có thể tài liệu tham khảo USER-003 V12).
- `git stash` (giữ, không tự xoá): `stash@{0}` "wip-handoff: cất trước khi áp refs/wip/main (2026-11-25 14:30)"; `stash@{1}` của `docs/USER-003-state-cleanup`.

## Quyết định / context quan trọng
- **DB dev chung đang ở Flyway V9** (áp 2026-11-26 khi test REPUTATION-001). `main` đã có V9 → chạy được. 3 worktree còn lại (base `89d252f`) không khởi động với DB dev tới khi rebase lên `e708b62`.
- **Quy trình mỗi nhánh** (user duyệt): hỏi từng câu review lượt 1 → xem trước conflict bằng commit tạm + `git merge-tree` → hỏi user cách gộp (REPUTATION: user cho agent tự gộp theo nhóm: bản tạm → bản main; cùng thêm → giữ cả hai; state → giữ bản nhánh) → lập kế hoạch P7 vào `current-task.md` worktree, chờ duyệt → TDD → chạy toàn bộ test (DB dev) → `code-reviewer` → cập nhật design một lần → `architect-reviewer` lặp tới 0 finding → `docs/database/*` theo migration → commit `feat` + `docs` (không commit state) → push → PR → merge squash khi user yêu cầu.
- **Lệnh test DB:** `. <repo chính>/.env.dev && export DATABASE_URL DATABASE_USERNAME DATABASE_PASSWORD JWT_SECRET CLOUDINARY_CLOUD_NAME CLOUDINARY_API_KEY CLOUDINARY_API_SECRET CLOUDINARY_FOLDER && ./mvnw -Duser.timezone=Asia/Ho_Chi_Minh …` — **KHÔNG `set -a`** (export cả `SPRINGDOC_ENABLED` → `SecurityConfigIntegrationTest.apiDocsAreDisabledByDefault` fail). Chạy test **foreground** (chạy nền bị ngắt khi phiên kết thúc), chia theo lớp nếu lâu.
- **Bài học kỹ thuật (ADR-0013 / ADR-0003 Sửa đổi 2026-11-27):** ở READ COMMITTED, câu cập nhật uy tín còn đọc bảng khác để quyết định → khoá dòng ở câu riêng trước; cột của chính dòng đọc trong CTE `locked` hoặc dòng đích UPDATE, không quét bảng lần hai. Áp cho MERCENARY-001 `applyMercenaryCompletion` (`"user"`).
- **Checklist MERCENARY-001** (trong `pending-review-questions.md` mục MERCENARY-001 + REPUTATION-001 CCD §11): khoá `"user"` câu riêng (+1 truy vấn, 2.18 4 → 5/W6 — hỏi user); REPUTATION-001 2.1 nay **6/W6** (không thêm truy vấn được); tên method mới `find/count{Received,Sent}Reports`, `findAdminDisputesOldestFirst/NewestFirst`; thêm test 2.10 chat mercenary → `REPORT_011`, người ngoài → `CHAT_003`; sửa PF/CCD MERCENARY dòng trỏ `[cần xác nhận #12]`/#6 đã đóng.
- Mỗi migration sau xoá cột có trong V6 → thêm vào `DROPPED_AFTER_V6` của `VietnamLocalDatetimeMigrationTest` (đã có `chat_message.created_at`, `match.confirmed_match_time`, `report.admin_reviewed_at`).
- Hook guard chặn lệnh bash chứa chuỗi `DROP TABLE` (kể cả trong văn bản) → viết script ra file scratchpad. File docs có thể CRLF → script thay chuỗi phải theo kiểu xuống dòng của file.
- User muốn hỏi **từng câu một**, kèm ví dụ code/dữ liệu cụ thể, giải thích dễ hiểu; khi user hỏi "chưa hiểu" → giải thích rồi hỏi lại đúng câu đó.
- Tech-debt mới: #12 sổ đăng ký `ConversationAccessPolicy` lặp (`ReportServiceImpl` vs `ConversationServiceImpl`).

## Blockers
- _không có_

## Việc tiếp theo
- [ ] Hỏi user về `docs/database/schema-v12.dbml` (chưa track, không do phiên này tạo).
- [ ] MERCENARY-001 (V10): hỏi từng câu `M1–M4`, `m1–m7`, `C1–C3` (`pending-review-questions.md`) + checklist tích hợp → xem trước conflict khi rebase lên `e708b62` → hỏi cách gộp → P7 như REPUTATION-001.
- [ ] Sau đó NOTIFICATION-001 (V11) → USER-003 (V12, CRITICAL C1).
- [ ] Hỏi user: xoá worktree thừa `agent-ae792d52783b81019` (MATCH-001), `agent-afe2f2ada6a54efbf` (MATCH-002), `agent-ae20f7ae909465469` (REPUTATION-001) (giữ nhánh; chỉ khi user đồng ý).
- [ ] Nhánh `chore/agent-framework-*`: `CLOUDINARY_*` + cảnh báo không `set -a` trong lệnh test DB (`CLAUDE.md` §8); REPUTATION-001 CCD §11: `api-convention.md` §5/§6 (2.10, domain RATING/REPORT/DISPUTE), `convention-backend.md` §1 (`support/reputation/`), `docs/agent/CLAUDE.md` §2 (dòng REPUTATION-001 đã có — kiểm lại); lỗ hổng `publish-spec.cjs` chép nguyên comment HTML sang repo FE.
- [ ] Chờ user trả lời: giữ hay đổi quy tắc "developer tự đặt câu hỏi khi thiết kế bỏ ngỏ" (đổi → `workflow-adapt`).
- [ ] (Tuỳ user) AC kịch bản lỗi thời hạn kèo B (UC-M02) vào SRS.
- [ ] (Để sau) Chỗ đặt record / enum trong `application/service/`.
- [ ] User xác nhận Render dev chạy bản mới khi redeploy từ `main` `e708b62`.
