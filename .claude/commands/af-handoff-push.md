---
description: >
  Lưu phiên (/af-handoff) RỒI đẩy mọi việc làm dở lên GitHub để làm tiếp trên máy khác: tạo ảnh chụp
  cho mọi nhánh local chưa đồng bộ (file đang sửa / commit chưa push) và push vào ref ẩn refs/wip/<nhánh>.
  Không đổi nhánh thật, không commit lên nhánh, không tạo PR. Chỉ chạy khi user gõ lệnh này.
disable-model-invocation: true
argument-hint: "(không cần tham số)"
---

# /af-handoff-push — Lưu phiên + đẩy việc dở lên GitHub

> Lệnh **chỉ user gọi**. Đi cặp với `/af-resume-pull` trên máy kia.
> User gõ lệnh này = **đã yêu cầu push** các ref `refs/wip/*` → không hỏi lại trước khi push.

## Ảnh chụp là gì
- Commit tạo bằng index tạm (`commit-tree`), cha = HEAD của nhánh, chứa file đang sửa + file mới chưa track
  (trừ file gitignore như `.env.dev`). Nhánh thật, staging, working tree **không đổi**.
- Lưu ở `refs/wip/<nhánh>` — **không phải nhánh**: không hiện trong danh sách nhánh GitHub/IDE, không tạo PR được.
- Message có dấu nhận diện `wip(handoff): snapshot <nhánh> — ảnh chụp tạm, KHÔNG merge, KHÔNG tạo PR` và trailer
  `WIP-Handoff: true`, `WIP-Branch`, `WIP-Base`, `WIP-Dirty`, `WIP-Current`, `WIP-Saved`.
- Nhánh "chưa đồng bộ" = đang checkout và có thay đổi chưa commit, **hoặc** có commit chưa có trên `origin`.

## Quy trình (tuần tự — không bỏ bước)

### Bước 1 — Lưu phiên
- Đọc `.claude/commands/af-handoff.md` và thực thi **Bước 1–4** (ghi đè `docs/agent/state/local-dev/session-handoff.md`).
- Không có task đang theo dõi → vẫn ghi handoff tối thiểu (nhánh hiện tại, file đang sửa, việc tiếp theo nếu biết) — **không** dừng.
- `session-handoff.md` nằm trong working tree nên sẽ đi theo ảnh chụp của nhánh đang làm.

### Bước 2 — Xem trước
```bash
node docs/agent/scripts/wip-handoff.cjs push --dry-run
```
- Exit 1 vì **secret** → **DỪNG**, báo `file:dòng`, không push. Sửa bằng biến môi trường/placeholder; dữ liệu giả có chủ đích → thêm `allow-secret` trên dòng đó (hỏi user trước).
- "Không có nhánh nào chưa đồng bộ" → báo và kết thúc (handoff vẫn đã lưu ở Bước 1, nhưng **chưa** lên GitHub — nói rõ).

### Bước 3 — Đẩy
```bash
node docs/agent/scripts/wip-handoff.cjs push
```
- Một lần `git push --atomic --force-with-lease` cho mọi `refs/wip/<nhánh>`; ghi đè ảnh chụp cũ của cùng nhánh.
- Lỗi push (mạng/quyền/lease) → báo nguyên văn lỗi, **không** khẳng định đã đẩy.

### Bước 4 — Báo
- Bảng: nhánh · ảnh chụp (7 ký tự) · có file dở / chỉ commit chưa push · nhánh đang làm.
- Nhắc: máy này **vẫn giữ** file đang sửa; `.env.dev` không đi theo (tự chép nếu máy kia chưa có);
  máy kia gõ `/af-resume-pull`. Quay lại máy này sau khi đã làm tiếp ở máy kia → cũng dùng `/af-resume-pull` (nó đề nghị stash bản cũ).

## Ràng buộc
- **NEVER** commit/push lên nhánh thật, **NEVER** tạo PR, **NEVER** `--no-verify` / bỏ secret scan.
- **NEVER** tự xoá `refs/wip/*` trên remote; chỉ xoá khi user yêu cầu rõ ref nào: `git push origin --delete refs/wip/<nhánh>`.
- Quy tắc đồng bộ `main` (rebase trước commit/push, `CLAUDE.md` §8) **không áp dụng** cho ảnh chụp: không commit lên nhánh, không push nhánh. Rebase vẫn làm như cũ khi commit/push thật.
- Worktree HEAD tách rời có thay đổi → script cảnh báo và bỏ qua; nêu lại trong báo cáo.
