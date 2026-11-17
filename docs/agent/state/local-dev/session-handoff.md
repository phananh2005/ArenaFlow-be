---
dev: local-dev
saved: 2026-11-18
task: Thiết kế SYSTEM-001 + USER-003 hoàn tất (đã merge); chưa bắt đầu implement V5–V11. current-task.md / progress.md vẫn là task cũ USER-002 (done)
status: done
---

## Đang làm dở
- Không có việc dở. Thiết kế đã merge: VENUE-001, MATCH-001, MATCH-002, REPUTATION-001, MERCENARY-001, NOTIFICATION-001 (#68), USER-003 (#71 SRS + design v1.0, ADR-0016 `Proposed`), convention USER-003 (#72).
- Nhánh dọn state `docs/USER-003-state-cleanup` (2026-11-18): sửa USER-003 v1.1 (phương án dự phòng timeout CCD §9 #9), NOTIFICATION-001 v1.1 (bỏ trỏ tệp scope), xoá 5 tệp `*-scope.md` (quyết định đã nằm trong SRS / thiết kế / decisions-log).

## File đang sửa (git status lúc handoff)
- Untracked ngoài phạm vi: `ketqua.md`, `ketqua-render.md` — không đụng, không commit.

## Quyết định / context quan trọng
- Phiên bản: SRS SYSTEM-001 v1.12; USER-003 SRS v1.0, design v1.1; USER-001 v4.3; USER-002 v3.1; NOTIFICATION-001 v1.1.
- Migration giữ chỗ: VENUE-001 V5, MATCH-001 V6, MATCH-002 V7, REPUTATION-001 V8, MERCENARY-001 V9, NOTIFICATION-001 V10, USER-003 V11. Flyway không out-of-order → code USER-003 sau V5–V10 (user chốt, không tách đợt).
- Cách làm việc: hỏi bằng ví dụ cụ thể / mock màn hình, user hiểu context thì gom câu hỏi một lượt; trình finding review cho user chọn trước khi sửa; user ngắt câu hỏi rồi nói "tiếp" → hỏi lại.

## Blockers
- `[cần xác nhận]` chặn code: xem mục "[cần xác nhận]" của từng thiết kế trong `docs/agent/design/<MÃ>/`.
- `git stash list` còn 2 bản handoff cũ (2026-11-16, 2026-11-17) — chỉ `drop` khi user đồng ý.

## Việc tiếp theo
- [ ] Implement theo thứ tự V5 → V11 (VENUE-001 → MATCH-001 đợt 1 → MATCH-002 + luồng B MATCH-001 → REPUTATION-001 → MERCENARY-001 → NOTIFICATION-001 → USER-003); mỗi mã `task-state-init` trước khi code.
- [ ] USER-003 trước merge: integration test #2 (EXPLAIN dùng GIN trên bảng `"user"` thật), #9 (timeout qua Neon); cập nhật `docs/database/*` cho V11.
- [ ] Lần sửa kế tiếp VENUE-001 / NOTIFICATION-001: trỏ danh mục khu vực và "Lời mời của tôi" sang USER-003.
- [ ] Không commit/push/PR khi chưa được yêu cầu.
