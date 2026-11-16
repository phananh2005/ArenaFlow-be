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
> User gõ lệnh này = **đã yêu cầu push** các ref `refs/wip/*` → không hỏi lại trước khi push (trừ các điểm dừng bên dưới).

## Ảnh chụp là gì
- Commit tạo bằng index tạm (`commit-tree`), cha = HEAD của nhánh, chứa file đang sửa + file mới chưa track
  (trừ file gitignore như `.env.dev`). Nhánh thật, staging, working tree **không đổi**.
- **Staging không được giữ**: phía máy kia mọi thay đổi về trạng thái chưa stage.
- Lưu ở `refs/wip/<nhánh>` — **không phải nhánh**: không hiện trong danh sách nhánh GitHub/IDE, không tạo PR được.
- Message có dấu nhận diện `wip(handoff): snapshot <nhánh> — ảnh chụp tạm, KHÔNG merge, KHÔNG tạo PR` và trailer
  `WIP-Handoff: true`, `WIP-Branch`, `WIP-Base`, `WIP-Dirty`, `WIP-Current`, `WIP-Saved`, `WIP-Machine`.
- Nhánh "chưa đồng bộ" = có thay đổi chưa commit trong worktree đang checkout nó, **hoặc** có commit chưa có trên `origin`.
  Nhánh có upstream đã bị xoá trên remote (`[gone]`) và không có file dở → bỏ qua kèm cảnh báo.
- Mỗi máy có mã riêng (`git config --local wip-handoff.machine`, tự tạo lần đầu).

## Quy trình (tuần tự — không bỏ bước)

### Bước 1 — Lưu phiên
- Đọc `.claude/commands/af-handoff.md` và thực thi **Bước 1–4** (ghi đè `docs/agent/state/local-dev/session-handoff.md`).
- Không có task đang theo dõi → vẫn ghi handoff tối thiểu (nhánh hiện tại, file đang sửa, việc tiếp theo nếu biết) — **không** dừng.
- `session-handoff.md` nằm trong working tree nên sẽ đi theo ảnh chụp của nhánh đang làm.

### Bước 2 — Xem trước
```bash
node docs/agent/scripts/wip-handoff.cjs push --dry-run
```
- In từng ảnh chụp kèm **commit chưa push** và **danh sách file** (`A`/`M`/`D`) sẽ lên GitHub. Trình bảng này cho user;
  có file lạ/không muốn đẩy (vd file nháp chưa track) → nêu ra. Không hỏi lại nếu không có gì bất thường.
- Exit 1 → **DỪNG**, báo nguyên văn:
  - **secret** (trong file dở hoặc trong commit chưa push) → báo `file:dòng`, không push. Sửa bằng biến môi trường/placeholder;
    dữ liệu giả có chủ đích → `allow-secret` trên dòng đó (hỏi user trước). Secret đã nằm trong commit chưa push → hỏi user cách xử lý.
  - **máy khác đã đẩy ảnh chụp mới hơn** → hướng dẫn chạy `/af-resume-pull` trước. Áp bị đụng độ **không** có nghĩa ảnh chụp
    lỗi thời → hỏi user. Chỉ khi user **xác nhận** nó đã lỗi thời → `apply <nhánh> --skip` (giữ bản dự phòng local
    `refs/wip-skipped/<nhánh>/<sha12>`, mỗi lần bỏ qua một ref riêng, không xoá gì) rồi push lại. Chỉ dùng `--force-overwrite` khi user yêu cầu rõ
    (ghi đè mất ảnh chụp của máy kia, không có bản dự phòng). Ảnh chụp của máy khác mà nội dung đã có trên `origin/<nhánh>` (đã cũ) thì không chặn.
  - **ref không phải ảnh chụp hợp lệ** ở `refs/wip/<nhánh>` → báo user; bỏ qua bằng `apply <nhánh> --skip` khi user xác nhận.
  - **worktree HEAD tách rời có file dở** (vd đang rebase) → việc dở đó không đẩy được; báo user xử lý.
- "Không có nhánh nào chưa đồng bộ" → báo và kết thúc (handoff đã lưu ở Bước 1 nhưng **chưa** lên GitHub — nói rõ).
- Dòng "Gợi ý: … ảnh chụp thừa" → chuyển nguyên cho user; **không** tự xoá.

### Bước 3 — Đẩy
```bash
node docs/agent/scripts/wip-handoff.cjs push
```
- Một lần `git push --atomic --force-with-lease` cho mọi `refs/wip/<nhánh>`; ghi đè ảnh chụp cũ của chính máy này
  hoặc ảnh chụp máy này đã lấy về.
- Lỗi push (mạng/quyền/lease) → báo nguyên văn lỗi, **không** khẳng định đã đẩy.

### Bước 4 — Báo
- Bảng: nhánh · ảnh chụp (7 ký tự) · có file dở / số commit chưa push · nhánh đang làm.
- Nhắc: máy này **vẫn giữ** file đang sửa; `.env.dev` không đi theo (tự chép nếu máy kia chưa có);
  máy kia gõ `/af-resume-pull`. Quay lại máy này sau khi đã làm tiếp ở máy kia → cũng dùng `/af-resume-pull`
  (nó đề nghị stash bản cũ) **trước khi** `/af-handoff-push` lần nữa.

## Ràng buộc
- **NEVER** commit/push lên nhánh thật, **NEVER** tạo PR, **NEVER** `--no-verify` / bỏ secret scan.
- **NEVER** `--force-overwrite` khi user chưa yêu cầu rõ; **NEVER** `apply --skip` khi user chưa xác nhận ảnh chụp đó đã lỗi thời.
- **NEVER** tự xoá `refs/wip/*` trên remote; chỉ xoá khi user yêu cầu rõ ref nào: `git push origin --delete refs/wip/<nhánh>`.
- Quy tắc đồng bộ `main` (rebase trước commit/push) và cổng `code-reviewer` (`CLAUDE.md` §8) **không áp dụng** cho ảnh chụp:
  không commit lên nhánh, không push nhánh, không tạo PR. Hai quy tắc đó vẫn áp dụng khi commit/push thật.
