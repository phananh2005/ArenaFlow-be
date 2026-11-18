---
dev: local-dev
saved: 2026-11-18 21:25
task: VENUE-001 — quản lý địa điểm sân (V5)
status: in_progress
---

## Đang làm dở
- Task: triển khai VENUE-001 theo TDD, hiện khoảng 68%.
- Bước hiện tại: Phase 4 — đã Green 2.4 (issue upload signature) và core path 2.5 (add image) gồm service unit tests (8/8) và Cloudinary dev E2E (`VenueControllerTest` 6/6 PASS). Sắp bắt đầu 2.6 set-primary và 2.13 delete image theo TDD.
- Nhánh: `feature/VENUE-001-venue-management` (đã `git fetch origin && git rebase --autostash origin/main` thành công trước handoff).

## File đang sửa (git status lúc handoff)
- Modified (14):
  - `.claude/settings.json` (cho phép narrow Cloudinary Maven env chain — kiểm lại: có thể thuộc nhánh `chore/agent-framework-*`)
  - `.env.example` (thêm placeholder Cloudinary)
  - `api/src/main/java/.../api/security/CurrentUserArgumentResolver.java`
  - `api/src/main/resources/application.properties`
  - `api/src/test/java/.../api/security/CurrentUserArgumentResolverTest.java`
  - `application/src/main/java/.../application/support/ConcurrencyConflictRetrier.java`
  - `application/src/test/java/.../application/support/ConcurrencyConflictRetrierTest.java`
  - `common/src/main/java/.../common/exception/ErrorCode.java`
  - `docs/agent/state/local-dev/{current-task,progress,session-handoff}.md`
  - `domain/src/main/java/.../domain/entity/venue/{Venue,VenueImage,VenueReport}.java`
  - `application/src/main/java/.../application/support/VenueGuard.java`
- Untracked (41): migration V5; package `api/.../venue/` (controller + test); DTO request/response/mapper/service/guard/repository/projection Venue và storage signer; tests migration/entity/DTO/mapper/service/guard/controller và `VenueNfsQueryCountTest`; `VenueImageCleanupProperties`, `VenueImageServiceImpl` + test, `VenueImageUploadSignatureResponse`. Xem `git status --short` để có danh sách chính xác.

## Quyết định / context quan trọng
- Nguồn thiết kế: `docs/agent/design/VENUE-001/` v4.2; SRS `docs/agent/business-spec/SYSTEM-001/`; NFS: N_DETAIL=3, N_LIST=4, N_WRITE=6.
- Hoàn thành và đã kiểm chứng:
  - V5 migration: `VenueManagementMigrationTest` PASS 6/6; schema Flyway v5.
  - Core create/detail/list/update và `PATCH /api/v1/venues/{venueId}`: `VenueControllerTest` integration PASS; `VenueNfsQueryCountTest` create=3/6, list=2/4, detail=2/3, update=3–5/6, không N+1.
  - `CurrentUserArgumentResolver` hỗ trợ `@CurrentUser CurrentActor` (trả `Object`, authority `ROLE_ADMIN`); `CurrentUserArgumentResolverTest` PASS 3/3.
  - `ConcurrencyConflictRetrier#execute(ErrorCode, Supplier)` backward compatible; `VENUE_011` cho venue exhaustion.
  - Cloudinary upload spike đã gỡ blocker #9: PNG signed upload 200, response signature SHA-1 đúng, nested public ID `-`/`_` hoạt động, GIF bị từ chối 400, `overwrite=false` không overwrite.
  - 2.4 service + controller: `CloudinaryImageUploadSignerTest` PASS 3/3; service 2.4 PASS; endpoint 2.4 integration không lộ `apiSecret`.
  - 2.5 service + controller: local verify public ID/scope/uploader/response signature/clock-skew (5m)/confirm-window (6h) trước transaction; row lock, stats, primary replacement, duplicate `VENUE_006`; `VenueImageServiceImplTest` PASS 8/8; E2E Cloudinary dev `2.4 → upload PNG → 2.5` PASS trong `VenueControllerTest` 6/6.
- Không có dòng lệch thiết kế; code bám contract/API/DB đã duyệt.
- `.env.dev` đã có cấu hình Cloudinary cloud-dev (`CLOUDINARY_FOLDER=arenaflow-dev`); KHÔNG commit file này. `.env.example` có placeholder an toàn.
- `.claude/settings.local.json` đã có allowlist hẹp cho exact Cloudinary Maven env chain; `.claude/settings.json` cũng bị sửa trong working tree — cần tách sang `chore/agent-framework-*` nếu không thuộc feature.

## Blockers
- **#12 Cloudinary Admin API:** cần live cloud-dev spike trước cleanup job.
- **#11 native `UPDATE … RETURNING`:** cần integration proof trước stale-alert job.
- Không có blocker cho 2.6/2.13.

## Việc tiếp theo
- [ ] TDD endpoint 2.6 `PATCH /api/v1/venues/{venueId}/images/{imageId}/set-primary` (service + controller + integration).
- [ ] TDD endpoint 2.13 `DELETE /api/v1/venues/{venueId}/images/{imageId}` (service + controller + integration; hard-delete ảnh, promote ảnh chính mới).
- [ ] NFS query-count cho 2.5 (4–5/6), 2.6 (5/6), 2.13 (6/6); race tests image writes.
- [ ] Sau Phase 4: Phase 5 report/Admin, Phase 6 port MATCH-001 + jobs (theo blocker #11/#12), Phase 7 final verify/docs.
- [ ] Không commit/push/PR khi user chưa yêu cầu.
