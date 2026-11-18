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

## Đang làm
- Phase 4 — hoàn thiện 2.5 NFS query-count/integration race, rồi endpoint 2.6 set-primary và 2.13 delete image.

## Còn lại
- Image lifecycle 2.6, 2.13; report/Admin; MATCH-001 selection port; stale alert proof/job; cleanup job sau Cloudinary Admin API spike.
- Final test/query-count/race tests, tài liệu DB/design nếu lệch, architect review và code review khi chuẩn bị PR.
