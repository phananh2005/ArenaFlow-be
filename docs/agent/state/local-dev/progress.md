---
task: VENUE-001 — quản lý địa điểm sân (V5)
created: 2026-11-18
status: in_progress
---

## Mô tả
Triển khai VENUE-001 theo thiết kế đã duyệt, bắt đầu với migration Flyway V5 và các thành phần backend cho quản lý địa điểm sân.

## Thiết kế liên quan
- `docs/agent/business-spec/SYSTEM-001/` — UC-V01..V06, BR-V01..V11, BC-V01..V07, AC-V01..V05.
- `docs/agent/design/VENUE-001/` — `API_Spec.md`, `Component_Class_Design.md`, `Processing_Flow.md` (v4.2).
- `docs/agent/non-functional-standards.md` — N_DETAIL=3, N_LIST=4, N_WRITE=6; không N+1; không giữ transaction khi chờ I/O ngoài.

## Đã xong
- Phase 1 migration V5: test Red/Green/Refactor; `VenueManagementMigrationTest` PASS 6/6 trên `.env.dev`, Flyway schema version 5, preflight aggregate 0 nhóm report legacy trùng.
- Phase 2: entity V5/error codes, request DTO validation, response/projection mapper, guard/current actor/repository baseline; các test unit liên quan PASS.
- Phase 3 core endpoints hoàn tất: `POST /api/v1/venues`, `GET /api/v1/venues/{venueId}`, `GET /api/v1/venues`, `PATCH /api/v1/venues/{venueId}`. PATCH controller integration `VenueControllerTest` PASS 3/3 (2026-11-18).
- Query-count core: `VenueNfsQueryCountTest` PASS 1/1, create=3/6, list=2/4, detail=2/3, update=3–5/6, không N+1.
- Cloudinary upload spike: PNG signed upload 200; response signature SHA-1 đúng; nested public ID `-`/`_` hoạt động; GIF ngoài format bị từ chối 400; duplicate `overwrite=false` không overwrite. Blocker #9 đã gỡ.
- Phase 4 signing + 2.4: `CloudinaryImageUploadSignerTest` PASS 3/3; service 2.4 test PASS; controller 2.4 integration không trả `apiSecret`.
- Phase 4 2.5: local verify public ID/scope/uploader/response signature/clock-skew/confirm-window trước transaction; row lock, stats, primary replacement and duplicate map `VENUE_006`; service tests PASS 8/8. Controller accepts forged result as `VENUE_005`. Cloudinary-dev E2E `2.4 → direct PNG upload → 2.5` PASS trong `VenueControllerTest` 6/6 (BUILD SUCCESS 59.079s). Asset test mồ côi có thể còn do rollback; job cleanup tương lai xử lý.
- Phase 4 2.6 + 2.13 (2026-11-18, máy 2): `PATCH …/images/{imageId}/set-primary` và `DELETE …/images/{imageId}` (service + controller), repository `setPrimary`/`findByIdAndVenueId`/`promoteFirstAsPrimary`, `AdminAuditLog` (afterCommit, chỉ id). `VenueImageServiceImplTest` 20/20, `AdminAuditLogTest` 3/3, `VenueControllerTest` 2 test mới PASS.
- Query-count ghi ảnh: `VenueNfsQueryCountTest` 2/2 — 2.5=5, 2.6=5, 2.13=6 (xoá ảnh chính), đúng ngân sách W6.
- Race ghi ảnh (commit thật): `VenueImageWriteRaceTest` 7/7 — ảnh thứ 5 `{OK|VENUE_003}`, hai ảnh chính, thêm chính vs xoá chính, set-primary vs xoá, xoá lặp `{OK|VENUE_004}`; rollback set-primary khôi phục ảnh chính; audit admin chỉ ghi sau commit.
- Full suite 2026-11-18: 301 test, 2 fail, 1 skip — 2 fail/1 skip do `.env.dev` chưa có `CLOUDINARY_*`. 2026-11-19 đã bổ sung: `VenueControllerTest` 8/8 PASS, 0 skip (gồm E2E upload thật).
- Scan log secret (2026-11-19): DEBUG toàn bộ, 20 472 dòng output, 0 `api_secret`, 0 chữ ký SHA-1. Phase 4 hoàn tất.
- Phase 5 (2026-11-19): 2.7 report + cổng cảnh báo CLOSED (1 event/đợt), 2.8 danh sách report Admin, 2.9 chốt report có điều kiện, 2.10 mark-inactive (verify report CLOSED PENDING), 2.11 reactivate (xoá cả hai cổng cảnh báo), audit Admin sau commit (2.9–2.13). Query-count khớp design: 2.7=3/2, 2.8=2, 2.9=3/4, 2.10=5, 2.11=4.
- Race Phase 5 (commit thật, `VenueReportAdminRaceTest` 6/6): double-submit report `{OK|VENUE_014}`, hai report CLOSED → đúng 1 event, double resolve `{OK|VENUE_010}`, resolve CLOSED vs mark-inactive không 500, ghi ảnh vs mark-inactive `{OK|OK}`/`{VENUE_002|OK}` + audit đúng 1 lần, 2.12 vs ghi ảnh không mất thay đổi.
- `CloudinaryProperties#toString` che `apiSecret`.
- Full suite 2026-11-19 (có `CLOUDINARY_*`): 329 test PASS, 0 fail, 0 skip.
- Phase 6a (2026-11-19): port `VenueSelectionService` cho MATCH-001/MERCENARY-001 — unit 5/5, integration 3/3 (1 query mỗi thao tác, `MANDATORY` từ chối ngoài transaction).
- Phase 6b (2026-11-19): blocker #11 gỡ (`UPDATE … RETURNING` qua Spring Data, 2/2); job cảnh báo 180 ngày + scheduler (cron tắt trong test) — 3/3, 2/2.
- Phase 6c (2026-11-19): blocker #12 gỡ bằng spike live; job dọn asset Cloudinary (mặc định tắt, fail-fast khi bật với thư mục dùng chung / chưa cấu hình / grace quá ngắn) — unit 5+6+3, scheduler 2/2, integration 1/1, live 1/1.
- Full suite 2026-11-19 sau Phase 6: 362 test PASS, 0 fail, 0 skip.

## Đang làm
- VENUE-001 98% — đã commit 2026-11-21 (user yêu cầu, kèm state): code `7af06aa` (sau khi thêm marker `allow-secret` cho 2 dòng secret scan báo nhầm, `code-reviewer` ĐẠT) + commit `docs(VENUE-001)`. Chưa push / chưa PR.
  - Phase 7b: finding `code-reviewer` 2026-11-20 xử lý xong (M1 live, m2/m4/m5/m6/m7/m8 sửa; m3/m9/m10/Team → tech-debt #8–#11; m1 → chuẩn hoá LocalDateTime).
  - Review phần sửa: n1–n4 sửa (n1 = lệch thiết kế #5) → ĐẠT; comment `VenueAlertServiceImpl:68` sửa.
  - Design: lệch #1–#5 đồng bộ; `architect-reviewer` 3 lượt (C1, M-new1, M1–M4, N1–N6, A1, N-a–N-e, MINOR 409) → ĐẠT; `publish-spec` chạy qua.
  - Comment tiếng Anh → tiếng Việt: 88 comment / 50 file + 5 sửa câu chữ → `code-reviewer` ĐẠT.
- Full suite 2026-11-21 (sau dịch comment): 374 PASS, 0 fail, 0 skip.

## Còn lại
- Push + tạo PR khi user yêu cầu (`git fetch origin && git rebase --autostash origin/main` trước push; mô tả PR kèm preflight V5 — tech-debt #11).
- Sau khi VENUE-001 vào main: nhánh `chore/agent-framework-*` (§2.4 comment tiếng Việt mọi loại, §2.5 Import, §1 cây package; gợi ý ADR-0012); chuẩn hoá `LocalDateTime` giờ VN; chức năng quản lý report/cảnh báo chung (bỏ thông báo Admin, badge số mục mới).
