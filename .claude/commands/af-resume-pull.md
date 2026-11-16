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
  nhánh đang làm lúc handoff · trạng thái:
  - `chưa có nhánh local` / `local = base` / `local đi sau` → áp đúng như máy cũ.
  - `file dở dựa trên commit cũ hơn đầu nhánh` → áp phần dở **lên đầu nhánh** (local, hoặc origin nếu chưa có local); có thể đụng độ.
  - `nhánh local đã chứa ảnh chụp` → không cần áp.
  - `CŨ HƠN nhánh trên origin` → nội dung đã có trên origin; không áp.
  - `LỆCH lịch sử` → không áp.
- Dòng "Bỏ qua: …" (ref không hợp lệ) → báo user.
- Không có ảnh chụp → báo, chuyển thẳng **Bước 4**.

### Bước 2 — Hỏi
- Ảnh chụp **chỉ có commit chưa push** → đề xuất áp **tất cả** (chỉ tạo/fast-forward nhánh local, không đụng working tree).
- Ảnh chụp **có file dở** → mỗi lượt chỉ áp **một** (một working tree chỉ chứa được một bộ file dở). Đề xuất ảnh chụp
  "nhánh đang làm lúc handoff"; nếu có nhiều → hỏi user chọn cái nào. Các ảnh chụp có file dở còn lại để nguyên trên remote
  (áp sau khi đã commit/stash việc hiện tại).
- `CŨ HƠN nhánh trên origin` → **không áp**; đề xuất pull nhánh thật (`git switch <nhánh>` + `git pull --ff-only`), ảnh chụp đó là thừa.
- `file dở dựa trên commit cũ hơn đầu nhánh` → nói rõ với user là sẽ áp lên đầu nhánh và có thể đụng độ.
- `LỆCH lịch sử` → **không áp**; nêu lý do, hỏi user xử lý tay.
- Working tree hiện tại có thay đổi chưa commit và cần áp ảnh chụp có file dở → hỏi: **stash** (`--stash`, cất vào
  `git stash` kèm message `wip-handoff: …`, lấy lại được) hay dừng. **NEVER** tự bỏ thay đổi.

### Bước 3 — Áp (chỉ ảnh chụp user đã chọn; các ảnh chụp chỉ-commit trước, ảnh chụp có file dở sau cùng)
```bash
node docs/agent/scripts/wip-handoff.cjs apply <nhánh> [--stash | --skip]
```
- Nhánh local chưa có → tạo tại base, hoặc tại `origin/<nhánh>` khi phải áp lên đầu nhánh (upstream `origin/<nhánh>` nếu có);
  đi sau → fast-forward tới base; đi trước → giữ nguyên. Ảnh chụp có file dở → checkout nhánh, `cherry-pick --no-commit` + `reset`
  → file về trạng thái **chưa commit** (staging không được giữ), nhánh **không** chứa commit tạm.
- Áp lên đầu nhánh: script **thử trước trong bộ nhớ** (`git merge-tree`). Sẽ **đụng độ** → từ chối khi **chưa** stash / chuyển /
  tạo nhánh, in file đụng độ. **Hỏi user** chọn:
  - áp tay (lệnh script in sẵn) — **NEVER** tự sửa đụng độ khi user chưa yêu cầu;
  - ảnh chụp đã lỗi thời → `apply <nhánh> --skip` (chỉ ghi nhận đã xem; không đổi working tree, không xoá ref) → `/af-handoff-push` được ghi đè nó;
  - để nguyên, xử lý sau.
- Máy này ghi nhận đã lấy ảnh chụp (`refs/wip-seen/<nhánh>`) → `/af-handoff-push` sau đó được phép ghi đè nó.
- Exit 1 → báo nguyên văn, **dừng** các ảnh chụp còn lại, hỏi user.

### Bước 4 — Tiếp tục phiên
- Đọc `.claude/commands/af-resume.md` và thực thi **Bước 2–4** (đọc `session-handoff.md` vừa áp, tóm tắt, **hỏi trước khi code**).

## Ràng buộc
- **NEVER** áp khi user chưa đồng ý; **NEVER** `reset --hard` / `clean` / xoá thay đổi local.
- **NEVER** tự xoá `refs/wip/*` trên remote (chỉ khi user yêu cầu rõ: `git push origin --delete refs/wip/<nhánh>`).
- Sau khi áp, commit/push thật theo quy tắc thường lệ (`CLAUDE.md` §8: rebase, review gate, chỉ push khi user yêu cầu).
