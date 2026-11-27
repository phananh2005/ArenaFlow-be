---
task: MERCENARY-001 — tuyển mercenary (V10)
updated: 2026-11-21
percent: 70
---

## Đang làm
- Bước hiện tại: dừng trước `code-reviewer` (theo chỉ đạo coordinator).
- Tóm tắt: P0–P8 + phần unit của P9 xong; unit test PASS; test-compile toàn repo PASS.

## Đã xong (phiên này)
- P0 phụ thuộc tạm; P1 V10 + entity + converter + mã lỗi; P2 guard/validator; P3 SQL/repository/projection; P4 tin tuyển + trận ngoài; P5 đơn ứng tuyển + policy chat + `VerificationRefresher`; P6 đánh giá/report/uy tín; P7 Admin tranh chấp; P8 job, xoá tài khoản, cấu hình, 8 controller.

## Còn lại
- `code-reviewer`; giai đoạn tích hợp (áp MỞ RỘNG, gỡ phụ thuộc tạm, viết nốt + chạy test tích hợp/NFS/race); đối chiếu design theo bảng "Lệch thiết kế" → `architect-reviewer`.

## Kiểm chứng đã chạy
- `./mvnw -o -Duser.timezone=Asia/Ho_Chi_Minh -pl application -am -Dtest=<37 lớp unit> -Dsurefire.failIfNoSpecifiedTests=false test` → application: Tests run: 209, Failures: 0, Errors: 0 — BUILD SUCCESS.
- `./mvnw -o -q -Duser.timezone=Asia/Ho_Chi_Minh test-compile` (toàn repo) → exit 0.
- Test tích hợp (`MercenaryMigrationTest`, `MercenaryRepositoryIntegrationTest`, `MercenaryNfsQueryCountTest`, sửa `NfsWriteQueryCountTest`, `ProfileServiceIntegrationTest`): CHƯA CHẠY — không có `.env.dev`, V10 cần V6–V9.

## Blockers
- Không có `.env.dev`; V10 cần V6–V9 → test DB để giai đoạn tích hợp.
