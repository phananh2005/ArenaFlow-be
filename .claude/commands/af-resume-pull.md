---
description: >
  Lấy việc làm dở từ máy khác (ảnh chụp refs/wip/* do /af-handoff-push đẩy lên), hỏi rồi áp vào nhánh local
  + working tree, sau đó chạy /af-resume (tóm tắt và HỎI trước khi code). Chỉ chạy khi user gõ lệnh này.
disable-model-invocation: true
argument-hint: "(không cần tham số)"
---

# /af-resume-pull — Lấy việc dở từ máy khác + tiếp tục phiên

> Lệnh **chỉ user gọi**. Đi cặp với `/af-handoff-push`. Khác `/af-resume` (chỉ đọc): lệnh này **có ghi**
> vào nhánh local / working tree, nên **MUST hỏi** trước khi áp.

## Quy trình (tuần tự — không bỏ bước)

### Bước 1 — Liệt kê ảnh chụp
```bash
node docs/agent/scripts/wip-handoff.cjs list
```
- Fetch `origin` + `refs/wip/*` rồi in mỗi ảnh chụp: nhánh · thời điểm lưu · có file dở / chỉ commit chưa push ·
  nhánh đang làm lúc handoff · trạng thái nhánh local (`chưa có` / `= base` / `đi sau` / `đi TRƯỚC` / `LỆCH`).
- Không có ảnh chụp → báo, chuyển thẳng **Bước 4**.

### Bước 2 — Hỏi
- Trình danh sách; đề xuất áp **tất cả** ảnh chụp áp được, ảnh chụp "nhánh đang làm lúc handoff" áp **sau cùng**
  (nó quyết định nhánh được checkout).
- `đi TRƯỚC` (local mới hơn ảnh chụp) / `LỆCH` → **không áp**; nêu lý do, hỏi user xử lý tay.
- Working tree hiện tại có thay đổi chưa commit và ảnh chụp cần áp có file dở → hỏi: **stash** (`--stash`, cất vào
  `git stash` kèm message `wip-handoff: …`, lấy lại được) hay dừng. **NEVER** tự bỏ thay đổi.

### Bước 3 — Áp (chỉ ảnh chụp user đã chọn)
```bash
node docs/agent/scripts/wip-handoff.cjs apply <nhánh> [--stash]
```
- Nhánh local chưa có → tạo tại base; đi sau → fast-forward tới base; rồi (nếu có file dở) checkout nhánh và
  `cherry-pick --no-commit` + `reset` → file về trạng thái **chưa commit**, nhánh **không** chứa commit tạm.
- Exit 1 → báo nguyên văn, **dừng** các ảnh chụp còn lại, hỏi user.

### Bước 4 — Tiếp tục phiên
- Đọc `.claude/commands/af-resume.md` và thực thi **Bước 2–4** (đọc `session-handoff.md` vừa áp, tóm tắt, **hỏi trước khi code**).

## Ràng buộc
- **NEVER** áp khi user chưa đồng ý; **NEVER** `reset --hard` / `clean` / xoá thay đổi local.
- **NEVER** tự xoá `refs/wip/*` trên remote (chỉ khi user yêu cầu rõ: `git push origin --delete refs/wip/<nhánh>`).
- Sau khi áp, commit/push thật theo quy tắc thường lệ (`CLAUDE.md` §8: rebase, review gate, chỉ push khi user yêu cầu).
