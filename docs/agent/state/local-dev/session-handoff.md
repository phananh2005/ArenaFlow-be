---
dev: local-dev
saved: 2026-11-15 17:11
task: Thiết kế SYSTEM-001 còn lại — chat hai đội đã merge (PR #63), framework MERCENARY-001 đã merge (PR #62); current-task.md vẫn là task cũ USER-002 (done)
status: done
---

## Đang làm dở
- Task: rollout thiết kế SYSTEM-001 (memory `system-001-design-rollout`). `current-task.md` / `progress.md` vẫn ghi task USER-002 đã xong; thiết kế không dùng `task-state-init` (chưa implement).
- Bước hiện tại: đã squash-merge 2026-11-15 — PR #62 `6c346d7` (framework MERCENARY-001: api-convention 2xx/mã lỗi/cursor, convention-backend §1, CLAUDE.md §2) và PR #63 `6de649f` (chat hai đội: SRS v1.10 + thiết kế). Workflow `publish-spec` cho 2 commit lúc handoff đang chạy / chờ — chưa thấy kết quả. Chưa bắt đầu việc kế tiếp; đã hỏi user chọn việc nào trước (chưa trả lời).
- Nhánh hiện tại: `chore/agent-framework-mercenary-001-conventions` (đã merge, giữ, không xoá). Nhánh `docs/MATCH-002-team-chat-anytime` cũng đã merge, giữ. Việc mới tạo nhánh từ `origin/main`.

## File đang sửa (git status lúc handoff)
- Không có thay đổi tracked.
- Untracked ngoài phạm vi: `ketqua.md`, `ketqua-render.md` — không đụng, không commit.

## Quyết định / context quan trọng
- Chat hai đội (decisions-log 2026-11-15, `team-chat-anytime-scope.md`): mọi chat (luồng A, B, mercenary) gộp về `conversation` + `conversation_message`, lõi chat + kênh realtime STOMP đặt ở **MATCH-001** (V6, endpoint 2.16–2.20 gồm `GET /profile/conversations`); MATCH-001 code **2 đợt** (đợt 1 luồng A + chat; nhánh luồng B làm cùng đợt MATCH-002, trong lúc chờ 2.1 `NEED_VENUE` → `422 MATCH_016`, ngoại lệ MINOR user duyệt; khi bỏ chặn thêm entry CHANGELOG MATCH-001); gửi lại request dùng tiếp chat cũ; người đã giải tán đội chỉ xem (cả mercenary); report "giao tiếp tệ" khi chat có tin (REPUTATION-001 2.10 `POST /conversations/{id}/reports`); trận sạch chỉ xét đối thủ thật (+ mercenary APPROVED do MERCENARY-001 thêm ở V9); không frame khi request luồng A đổi trạng thái.
- Phiên bản: SRS SYSTEM-001 v1.10; MATCH-001 v2.4, MATCH-002 v2.0 (MAJOR), REPUTATION-001 v1.2, MERCENARY-001 v2.0 (MAJOR), VENUE-001 v4.1; ADR-0014 Sửa đổi 2026-11-15.
- Cách làm việc: hỏi user từng câu một, ví dụ cụ thể, không viết tắt nội bộ; trình finding review cho user chọn trước khi sửa; user ngắt câu hỏi rồi nói "tiếp" → hỏi lại. Hook guard chặn `rm -rf` (dùng thư mục mới thay vì xoá).

## Blockers
- Implement theo thứ tự: VENUE-001 (V5) → MATCH-001 đợt 1 (V6) → MATCH-002 + nhánh luồng B MATCH-001 (V7) → REPUTATION-001 (V8) → MERCENARY-001 (V9).
- ADR 0009–0014 còn `Proposed` — chốt qua MR.
- `[cần xác nhận]` chặn code: MATCH-001 #5 (VENUE trước), #12 (SockJS), #16 (đếm dòng `chat_message` trước V6); MATCH-002 #4, #5; REPUTATION-001 #8; MERCENARY-001 #1.

## Việc tiếp theo
- [ ] Hỏi lại user chọn thứ tự 2 việc dưới (đã hỏi, chưa trả lời).
- [ ] PR framework (nhánh `chore/agent-framework-<chủ-đề>` từ `origin/main`): `api-convention.md` — các dòng chat `/pairings/{id}/messages` → MATCH-001 `/conversations/*`, thêm `/profile/conversations`, `/conversations/{id}/reports`, `REPORT_010/011`, `MATCH_016` ở 2.1; `docs/agent/CLAUDE.md` §2 dòng MATCH-001 (+ chat dùng chung / realtime / Tin nhắn của tôi), MATCH-002 (bỏ "chat realtime"); cân nhắc thêm dòng REPUTATION-001 (đang thiếu).
- [ ] NOTIFICATION-001 (mã thiết kế cuối): in-app + `PushSender` no-op; job xoá chat 90 ngày cho mọi `conversation` (điều kiện ở MATCH-001 / MERCENARY-001 "Tài liệu cần đồng bộ").
- [ ] Kiểm kết quả workflow `publish-spec` của `6c346d7`, `6de649f` (`gh run list`).
- [ ] USER-003 SRS (tạm dừng 2026-11-10): nhánh `feature/USER-003-user-discovery` có commit WIP `a638fc7` **chỉ ở local, chưa push** (upstream đang trỏ nhầm `origin/main`). Nội dung: 5 file `docs/agent/business-spec/USER-003/` + sửa 3 file SRS SYSTEM-001 (Overview, Use_Cases, Acceptance_Criteria). Còn Q-01..Q-16 chưa chốt. Khi làm tiếp: `git fetch origin && git rebase --autostash origin/main` → dự kiến conflict 3 file SYSTEM-001 (WIP đánh v1.4, `main` đã v1.10 → đánh lại v1.11) → conflict thì abort, báo user; push bằng `git push -u origin feature/USER-003-user-discovery` khi user yêu cầu.
- [ ] Không commit/push/PR khi chưa được yêu cầu.
