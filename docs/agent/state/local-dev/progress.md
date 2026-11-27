---
task: NOTIFICATION-001 — thông báo in-app (V11)
updated: 2026-11-21
percent: 60
---

## Đang làm
- Bước hiện tại: dừng trước `code-reviewer` (P0–P6 xong) — chờ coordinator.
- Tóm tắt: P0–P6 code xong theo TDD, unit test PASS; test tích hợp viết một phần, chưa chạy; P7 + delta MATCH-001 dời sang tích hợp.

## Đã xong (phiên này)
- P0 phụ thuộc tạm; P1 V11 + entity + 12 projection + 4 repository; P2 texts/writer/mapper/port realtime + no-op; P3 REST 2.1–2.4;
  P4 dispatcher (28 event) + 4 listener + AsyncConfig; P5 5 event USER-001 phát từ 4 service; P6 job nhắc / dọn + RetentionScheduler + properties.

## Còn lại
- `code-reviewer`; đối chiếu design; giai đoạn tích hợp (P7, delta MATCH-001, test tích hợp cần V7–V10, chạy test tích hợp).

## Kiểm chứng đã chạy
- Unit test từng phase: Red (thiếu class / sai constructor) → Green (output surefire trong phiên).
- `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh test-compile` toàn repo → không lỗi.
- Test tích hợp (migration, repository, controller, NFS, listener): **chưa chạy** — cần DB có V6–V10 + `.env.dev`.

## Blockers
_không có_ (câu hỏi không chặn: mục "Câu hỏi chờ duyệt" trong current-task.md)
