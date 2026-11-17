---
dev: local-dev
saved: 2026-11-18 00:53
task: Mọi màn Admin hiện tên thật + ảnh thật + cờ đã xoá của user đã xoá tài khoản (task code cuối — đã xong; sau đó chỉ làm thiết kế/tài liệu). Sắp bắt đầu implement V5 → V11
status: done
---

## Đang làm dở
- Task: chưa có task code đang làm. `current-task.md` / `progress.md` vẫn là task USER-002 (2026-11-12, 100%, PR #59 đã merge).
- Bước hiện tại: user yêu cầu "code các migration V5 → V11"; **đang chờ user chọn phạm vi** (đã hỏi, chưa trả lời):
  - **A (khuyến nghị):** code đủ từng chức năng, mỗi lần một mã — VENUE-001 (V5) trước: nhánh `feature/VENUE-001-…` từ `origin/main` → `task-state-init` + kế hoạch `current-task.md` → **chờ duyệt** → TDD → `code-reviewer` → PR → merge rồi mới sang mã sau.
  - **B:** chỉ viết SQL migration V5 → V11 + test migration (chưa có Java/API) — lệch quy ước nhánh theo một mã.
- Phiên này đã merge: #71 (USER-003 SRS + design v1.0 + ADR-0016 `Proposed`), #72 (convention USER-003), #73 (USER-003 v1.1, NOTIFICATION-001 v1.1, xoá 5 tệp `*-scope.md`). Đã drop 2 stash handoff cũ, xoá 2 nhánh local `worktree-agent-*`.
- Nhánh hiện tại: `docs/USER-003-state-cleanup` (đã merge #73, giữ). `main` local chậm so với `origin/main` → `git pull` khi về `main`.

## File đang sửa (git status lúc handoff)
- `docs/agent/state/local-dev/session-handoff.md` (tệp này).
- Untracked ngoài phạm vi: `ketqua.md`, `ketqua-render.md` — không đụng, không commit.

## Quyết định / context quan trọng
- Phiên bản: SRS SYSTEM-001 v1.12; USER-003 SRS v1.0, design v1.1; USER-001 v4.3; USER-002 v3.1; NOTIFICATION-001 v1.1. Chi tiết: `decisions-log.md` (2026-11-17, 2026-11-18).
- Migration giữ chỗ: VENUE-001 V5, MATCH-001 V6, MATCH-002 V7, REPUTATION-001 V8, MERCENARY-001 V9, NOTIFICATION-001 V10, USER-003 V11. Flyway không out-of-order → code theo đúng thứ tự; USER-003 sau V5–V10 (user chốt, không tách đợt).
- `DATABASE_URL` trỏ host `-pooler` của Neon (PgBouncer chế độ transaction): `SET` theo phiên không tin cậy, dùng `SET LOCAL` trong transaction.
- Nhánh `chore/docker-render-deploy` chỉ để deploy Render dev — không tạo PR, không merge `main` (user chốt 2026-11-18).
- Cách làm việc: giải thích bằng ví dụ cụ thể / mock màn hình; user hiểu context thì gom câu hỏi một lượt; trình finding review cho user chọn trước khi sửa; user ngắt câu hỏi rồi nói "tiếp" → hỏi lại; chỉ commit/push/PR/merge/xoá nhánh khi user bảo.

## Blockers
- Chờ user chọn A/B ở trên.
- `[cần xác nhận]` chặn code: xem mục "[cần xác nhận]" của từng thiết kế `docs/agent/design/<MÃ>/` — rà khi bắt đầu mỗi mã. USER-003 trước merge code: #2 (EXPLAIN dùng GIN trên bảng `"user"` thật), #9 (timeout qua Neon pooler).
- ADR-0016 còn `Proposed` — chốt qua MR.

## Việc tiếp theo
- [ ] Hỏi lại user: phạm vi A hay B cho "code các migration V5 → V11".
- [ ] (Nếu A) VENUE-001: đọc thiết kế `docs/agent/design/VENUE-001/` + `non-functional-standards.md`, rà `[cần xác nhận]`, `task-state-init`, trình kế hoạch chờ duyệt.
- [ ] Sau code USER-003 + test PASS: cập nhật `docs/database/*` cho V11.
- [ ] Lần sửa kế tiếp VENUE-001 / NOTIFICATION-001: trỏ danh mục khu vực và "Lời mời của tôi" sang USER-003.
- [ ] Không commit/push/PR khi chưa được yêu cầu.
