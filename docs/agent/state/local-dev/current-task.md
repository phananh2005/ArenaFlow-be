---
task: VENUE-001 — quản lý địa điểm sân (V5)
created: 2026-11-18
updated: 2026-11-21
percent: 98
status: in_progress
---

## Mô tả
Triển khai VENUE-001 theo thiết kế đã duyệt, bắt đầu với migration Flyway V5 và các thành phần backend cho quản lý địa điểm sân.

## Thiết kế liên quan
- `docs/agent/business-spec/SYSTEM-001/` — UC-V01..V06, BR-V01..V11, BC-V01..V07, AC-V01..V05.
- `docs/agent/design/VENUE-001/` — `API_Spec.md`, `Component_Class_Design.md`, `Processing_Flow.md` (v4.2).
- `docs/agent/non-functional-standards.md` — N_DETAIL=3, N_LIST=4, N_WRITE=6; không N+1; không giữ transaction khi chờ I/O ngoài.

## Definition of Done
- [x] Migration Flyway V5 áp dụng được trên database dev (Flyway schema version 5; `VenueManagementMigrationTest` 6/6 PASS, 2026-11-18).
- [x] Các use case/API VENUE-001 trong phạm vi thiết kế được triển khai kèm kiểm thử.
- [x] Cloudinary, phân quyền, validation và giới hạn phi chức năng tuân thủ thiết kế.
- [x] Test liên quan PASS với `-Duser.timezone=Asia/Ho_Chi_Minh` (full suite 374/374, 2026-11-21).
- [x] Lệch thiết kế (nếu có) được cập nhật một lần sau khi code/test PASS và được `architect-reviewer` review (ĐẠT 2026-11-21).
- [x] Code-reviewer không còn CRITICAL/MAJOR trước khi push tạo PR (ĐẠT 2026-11-21, phần sửa cuối gồm dịch comment).

## Các bước thực hiện (tổng quan)
- [x] 1. Đối chiếu spec, design, tiêu chuẩn non-functional và kiến trúc hiện có; rà các mục `[cần xác nhận]`.
- [x] 2. Quét tái sử dụng và lập kế hoạch chi tiết — trình user duyệt.
- [x] 3. Triển khai theo TDD (Red → Green → Refactor), gồm migration V5 và từng phase VENUE-001.
- [x] 4. Chạy test liên quan và xác nhận PASS (dán output).
- [x] 5. Kiểm tra ảnh hưởng, cập nhật design/SRS nếu cần; `architect-reviewer` review phần design sửa.
- [ ] 6. Review & chuẩn bị PR khi user yêu cầu. (Đã commit 2026-11-21: code `7af06aa` + `docs(VENUE-001)`; chưa push/PR.)

## Lệch thiết kế
_Ghi ngay khi code khác design; KHÔNG sửa design giữa chừng — cập nhật một lần ở bước cuối (`CLAUDE.md` §8). Trống = code khớp design._

| # | Phase | Design | Code thực tế | Lý do | Sửa ở |
|---|---|---|---|---|---|
| 1 | 5 | `Component_Class_Design.md` §4.13: audit "`RESOLVE_REPORT` (+ trạng thái chốt)" nhưng chữ ký `afterCommit(action, adminId, venueId, reportId)` không có tham số trạng thái | Log `RESOLVE_REPORT` kèm `venueId`, `reportId`, không kèm `VERIFIED`/`REJECTED` | Bám chữ ký đã duyệt; trạng thái chốt tra được qua `reportId` | `Component_Class_Design.md` §4.13 (chốt: thêm trạng thái vào action/tham số hay bỏ cụm "+ trạng thái chốt") |
| 2 | 3 | `Component_Class_Design.md` §4.1, §4.10: `VenueService#searchVenues` | Method tên `listVenues` (controller/test dùng tên này từ Phase 3) | Tên nội bộ, không đổi API | `Component_Class_Design.md` §4.1, §4.10 → `listVenues` |
| 3 | 6 | §4.12: "fail-fast khi khởi động" (không nêu vị trí) | `VenueImageCleanupProperties#requireSafeToEnable(CloudinaryProperties)`, gọi trong constructor `VenueImageCleanupScheduler` khi cron ≠ `-` | Kiểm được bằng unit test; app không khởi động khi bật sai | `Component_Class_Design.md` §4.12 (ghi vị trí) |
| 4 | 6 | §8 cấu hình: cron `-` = tắt "dùng trong test" (không nêu cách đặt) | `api/pom.xml` surefire `systemPropertyVariables` đặt `arenaflow.venue.stale-alert.cron=-`; cleanup mặc định `-` sẵn | Không có `src/test/resources/application.properties` (sẽ che file main) | `Component_Class_Design.md` §8 (ghi cách tắt trong test) |
| 5 | 7b | `Processing_Flow.md` §3.14 (dòng 176): job 180 ngày "dừng khi < `batch-size`" | `VenueAlertServiceImpl#alertStaleVenues` dừng khi lô **rỗng** (vẫn chặn bởi `max-batches-per-run`, lỗi lô) | Sau m2, dòng bị sửa đồng thời bị loại khỏi lô → lô ngắn không có nghĩa là hết; code-reviewer 7b n1, user chọn sửa 2026-11-21 | `Processing_Flow.md` §3.14 |

## Tái sử dụng
**Nguồn và cách quét:** đã quét text/tên symbol trên toàn bộ `common`, `domain`, `application`, `api` trong `src/main/java`; đọc sâu entity venue, repository/service/controller hiện có, mapper, validation, phân trang, retrier, resolver và test patterns. Từ khoá: `venue`, `cloudinary`, `image upload`, `storage`, `mapper`, `validator`, `paging`, `retry`, `CurrentUser`, `audit`, `scheduler`. Không có vùng mù trong bốn module source; Cloudinary chưa có implementation trong repo.

- **DÙNG LẠI:**
  - `application/.../support/PageRequestFactory#create` và `dto/response/PageResponse#from` cho hai endpoint danh sách, đã chặn `pageSize` 1..100.
  - `application/.../mapper/MapperSupport#toUtc`, `#maskName`; `ProfileMapper`/`TeamMapper` làm mẫu MapStruct `componentModel = spring`, projection và masking theo ADR-0002.
  - `application/.../validation/OneOf` + `OneOfValidator` cho các enum request dạng chuỗi; annotation validation và `GlobalExceptionHandler` hiện có cho envelope lỗi.
  - `application/.../support/ConcurrencyConflictRetrier` cho retry transaction của 2.9–2.12; chỉ mở rộng overload nhận `ErrorCode` để giữ tương thích TEAM hiện có.
  - `api/.../security/CurrentUser`, `CurrentUserArgumentResolver`, `WebMvcConfig` cho inject actor từ JWT; chỉ mở rộng resolver để hỗ trợ `CurrentActor`.
  - `domain/.../entity/venue/{Venue,VenueImage,VenueReport}` để mở rộng theo V5 thay vì tạo entity song song.
  - `domain/.../repository/AdministrativeUnitRepository#findWardWithProvince`, `UserRepository#findByIdAndDeletedAtIsNull` cho kiểm phường/xã và user còn hiệu lực.
  - `application/src/test/.../MapperTest`, `*ServiceImplTest`, `api/*/*ControllerTest`, `NfsQueryCountTest`, `*IntegrationTest` và race test hiện có làm khuôn TDD/unit/integration/query count/concurrency.
- **MỞ RỘNG:** `ErrorCode` (+ `VENUE_001..VENUE_016`), `ConcurrencyConflictRetrier` (overload exhausted code), `CurrentUserArgumentResolver` (trả `CurrentActor` khi parameter yêu cầu), `application.properties`/`.env.example` (Cloudinary + scheduler configuration), `WebMvcConfig`/scheduling config; entity venue hiện có theo delta V5.
- **VIẾT MỚI:** repository/projection venue; DTO request/response; `VenueMapper`; `VenueGuard`; service/interface/implementation cho venue, ảnh, admin và selection port; `CurrentActor`; `ValidGeoQuery`; storage port `ImageUploadSigner`/`ImageAssetAdmin` và implementation Cloudinary; event, audit log, alert/cleanup service, scheduler. Lý do: repository chỉ có entity venue, chưa có API/service/repository/projection/storage/job tương đương; không tạo helper song song với thành phần đã liệt kê ở DÙNG LẠI.

### Reuse Report 2026-11-20 — 7b m4 (helper tên constraint DB)
**Nguồn và cách quét:** grep `src/main/java` của `common`, `domain`, `application`, `api`; từ khoá `constraintName`, `getConstraintName`, `getMostSpecificCause`, `SQLState`, `DataIntegrity`, `IntegrityViolation`, `PSQLException`, `ServerErrorMessage`, `uq_`, `unique index`; liệt kê lớp tên `*Util*`/`*Helper*`/`*Support*`/`*Exception*`. Không vùng mù.
- **DÙNG LẠI:** không có helper public tương đương.
- **MỞ RỘNG:** `api/.../config/GlobalExceptionHandler#constraintName` (private static, chỉ dùng trong `api`, `application` không gọi được) → chuyển logic sang `application/.../support/DataIntegrityViolations#constraintName`, handler gọi lại helper (không còn bản song song). `GlobalExceptionHandler` nhánh `TEAM_003` (so `getMostSpecificCause().getMessage()` chứa `idx_team_member_pending_or_active`) giữ nguyên — ngoài phạm vi m4. `ConcurrencyConflictRetrier#isDeadlock` (SQLState deadlock) khác mục đích — không gộp.
- **VIẾT MỚI:** `DataIntegrityViolations#violates(exception, constraint)` — chưa có hàm so tên constraint; dùng ở `VenueServiceImpl#reportVenue`, `VenueImageServiceImpl#addImage`.

## Kế hoạch chi tiết
### 0 — Cổng kỹ thuật trước implementation bị chặn
1. **Cloudinary upload spike — ✅ PASS 2026-11-18:** cloud-dev signed upload PNG trả 200; `public_id` phân cấp trong `arenaflow-dev/venues/spike-*` với `-`/`_` được giữ nguyên; response signature SHA-1 (`public_id`, `version`, `api_secret`) xác minh đúng; GIF ngoài `allowed_formats` bị từ chối 400; upload lặp cùng `public_id` với `overwrite=false` trả lại chính asset/version/etag cũ (200, không overwrite). Không ghi secret/signature/URL. Được viết signer và test integration 2.4–2.5. Asset spike để job cleanup tương lai xử lý.
2. **Cloudinary Admin API spike — bắt buộc trước job cleanup:** kiểm list prefix/cursor, delete batch ≤100, map rate-limit 420/429 và response `deleted`/`partial`. Chỉ sau đó mới viết `CloudinaryImageAssetAdmin` + cleanup job.
3. **Spike migration/data + `UPDATE … RETURNING` — trước job stale alert:** kiểm bằng integration test với DB dev rằng V5 áp dụng được và Spring Data native query trả projection như thiết kế. Nếu không, dùng đúng phương án dự phòng trong `Processing_Flow.md` §3.14 và ghi lệch thiết kế nếu contract nội bộ thay đổi.
4. Trước chạy migration, kiểm dữ liệu dev chỉ-đọc: report legacy có trùng `(reporter_id, venue_id, 'OTHER')` không. Nếu có, dừng báo vì unique index V5 sẽ không áp dụng được; không tự xoá/sửa dữ liệu.

### 1 — Database/domain/error nền tảng (TDD Red → Green → Refactor)
1. **Red:** ✅ thêm `VenueManagementMigrationTest` mô tả V5: schema/index, CHECK enum/trạng thái, `ward_id`, `public_id`, unique public ID/report PENDING; fail đúng do V5 chưa tồn tại. Preflight aggregate trong test: 0 nhóm report legacy trùng.
2. **Green:** ✅ tạo `api/src/main/resources/db/migration/V5__venue_management.sql`; Flyway áp V5 vào DB dev. Entity/error sẽ được mở rộng trong phase read-model tiếp theo để giữ code compile theo từng lát TDD.
3. **Refactor/verify:** ✅ tách assertion constraint thành transaction riêng (PostgreSQL abort transaction sau constraint failure); chạy `VenueManagementMigrationTest` với `.env.dev`: 6 tests PASS, Flyway schema version 5.

### 2 — Query/read model, DTO/mapper/validation (TDD)
1. **Red:** mapper/validator/repository tests cho masking response thường vs Admin, ward, UTC, làm tròn distance, không lộ `publicId`, pagination defaults, geo query pair/radius và request trim/empty-string semantics.
2. **Green:** tạo projection, `VenueRepository`, `VenueImageRepository`, `VenueReportRepository`; request/response record; `VenueMapper`; `ValidGeoQuery`; filter enum; `VenueGuard`; tái dùng `PageRequestFactory`, `OneOf`, `MapperSupport`.
3. **Refactor:** thêm native query/count query và index-aware search path bốn nhánh; không thêm query trong loop. Query budget: 2.2=2/4, 2.3=2/3, 2.8=2/4.

### 3 — Venue core endpoints 2.1–2.3 và 2.12 (TDD)
1. **Red:** service/controller tests cho create, list/search/nearby/detail/update; guard order, USER_001/USER_002/VENUE_001/002/013/016, owner-vs-admin, inactive, ward legacy, user deleted masking.
2. **Green:** `VenueService`/`VenueServiceImpl`, `VenueController`; extend `CurrentUserArgumentResolver` + `CurrentActor`; extend retrier for `VENUE_011`; config/controller wiring.
3. **Refactor/verify:** ✅ `VenueNfsQueryCountTest` đo Hibernate Statistics trên DB dev (2026-11-18): create=3/6, list=2/4 (fixture ép COUNT), detail=2/3, update trong 3–5/6; PASS 1/1, không N+1 cho core paths. Optimistic retry sẽ kiểm trong phase concurrency/report liên quan.

### 4 — Image lifecycle endpoints 2.4–2.6, 2.13 (TDD; sau blocker #9)
1. **Red:** unit test signature vectors/HMAC/constant-time comparison/public-ID parse/delivery URL; service/controller tests permission, max-five, duplicate, primary replacement/promotion and no disclosure of Cloudinary secret. Integration race tests: two image writes, fifth-image race, delete-vs-write, exactly one primary.
2. **Green:** storage port/records/Cloudinary signer; `VenueImageService`/implementation; `VenueImageController`; image repository locking/native updates; configuration properties and `.env.example` placeholders. Không gọi Cloudinary trong DB transaction; 2.5 xác minh cục bộ trước `TransactionTemplate`.
3. **Refactor/verify:** query-count integration 2.5=5, 2.6=5, 2.13=6 (xoá ảnh chính); fail paths `VENUE_003..006`, `VENUE_012`; scan log để bảo đảm không log secret/signature. `progress-tracker` sau phase PASS.
4. ✅ **2026-11-18 (máy 2):** 2.6 `setPrimaryImage` + 2.13 `deleteImage` (service + controller), `VenueImageRepository#setPrimary/findByIdAndVenueId/promoteFirstAsPrimary`, `AdminAuditLog` (§4.13, kéo từ Phase 5 vì 2.13 cần). `VenueImageServiceImplTest` 20/20, `AdminAuditLogTest` 3/3, `VenueControllerTest` 2 test mới PASS, `VenueNfsQueryCountTest` 2/2 (2.5=5, 2.6=5, 2.13=6), `VenueImageWriteRaceTest` 7/7 (ảnh thứ 5, hai ảnh chính, thêm chính vs xoá chính, set-primary vs xoá, xoá lặp, rollback set-primary thật, audit chỉ ghi sau commit). Còn lại Phase 4: scan log secret/signature.
5. ✅ **2026-11-19:** `.env.dev` máy 2 có `CLOUDINARY_*` → `VenueControllerTest` 8/8 PASS (gồm E2E upload thật, 0 skip). Scan log: chạy `VenueControllerTest` + `VenueImageWriteRaceTest` ở `logging.level.root=DEBUG` (15/15 PASS), quét 20 472 dòng output → 0 lần xuất hiện `api_secret`, 0 chuỗi `signature` SHA-1. Code VENUE/storage chỉ có 1 lệnh log (`AdminAuditLog`, chỉ id). **Phase 4 hoàn tất.**

### 5 — Report/Admin endpoints 2.7–2.11 (TDD)
1. **Red:** tests cho report/non-admin, pending uniqueness, resolve CAS/deadlock retry, mark inactive/reactivate, closed-report behavior, Admin response giữ tên thật + cờ deleted, audit only after commit.
2. **Green:** `AdminVenueService`, `AdminVenueController`, `AdminVenueReportController`, report repository native operations, `AdminAuditLog`, `VenueAdminAlertEvent`/`VenueAlertType` theo ADR-0011. Không tự thêm listener notification trong VENUE-001.
3. **Refactor/verify:** integration test double-submit report, double-resolve, resolve-vs-inactive, two CLOSED reports one event; query budgets 2.7=2–3/6, 2.9=3–4/6, 2.10=5/6, 2.11=4/6. `progress-tracker` sau phase PASS.
4. ✅ **2026-11-19:** 2.7 `VenueService#reportVenue` (+ cổng `markClosureAlerted`, `VenueAdminAlertEvent`/`VenueAlertType` trong `application/event`), 2.8–2.11 `AdminVenueService(Impl)` + `AdminVenueController` + `AdminVenueReportController`, `VenueReportRepository` (`findAdminReports`, `findAdminViewById`, `resolveIfPending`, `verifyPendingClosureReports`), `VenueRepository#reopenClosureAlertIfNoPending`, `AdminVenueReportView`, `AdminVenueReportResponse`, `VenueReportStatusFilter`, mapper `toReport`/`toAdminReport`; bổ sung audit `ADMIN_UPDATE_VENUE` cho 2.12. Test: `VenueServiceImplTest` 15/15, `AdminVenueServiceImplTest` 8/8, `VenueControllerTest` 9/9, `AdminVenueControllerTest` 2/2, `VenueNfsQueryCountTest` 3/3 (2.7=3 khi mở cổng/2, 2.8=2, 2.9=3/4, 2.10=5, 2.11=4), `VenueReportAdminRaceTest` 6/6. Full suite 329/329 PASS. Hardening: `CloudinaryProperties#toString` che `apiSecret` (`CloudinaryPropertiesTest` 2/2). **Phase 5 hoàn tất.**

### 6 — Port cho MATCH-001 và scheduled jobs (TDD; hai job theo blocker tương ứng)
1. **Red:** `VenueSelectionService` tests for empty/missing/inactive/order and mandatory transaction; stale alert batch tests; cleanup job tests with fake `ImageAssetAdmin` (age, DB-reference, malformed/future ID, rate limit/budget).
2. **Green:** selection port; alert service/scheduler after #11; `ImageAssetAdmin`, cleanup service/scheduler after #12; scheduling config and properties. Admin API strictly outside transaction; default cleanup cron disabled and fail-fast config validation.
3. **Refactor/verify:** integration query counts/job transactions; no external HTTP in request or open transaction. `progress-tracker` after phase PASS.
4. ✅ **2026-11-19 — 6a port:** `VenueSelectionService(Impl)` (`requireSelectable` readOnly 1 query, giữ thứ tự id, `VENUE_001` trước `VENUE_002`; `markSelected` `MANDATORY` 1 bulk `GREATEST`, reset `stale_alerted_at`, `version + 1`), `SelectableVenueView`, `VenueRepository#findSelectableByIdIn/markSelected`. `VenueSelectionServiceImplTest` 5/5, `VenueSelectionIntegrationTest` 3/3.
5. ✅ **2026-11-19 — 6b job 180 ngày, blocker #11 GỠ:** native `UPDATE … RETURNING` không `@Modifying` chạy được qua Spring Data, trả `StaleVenueView` (`VenueStaleAlertRepositoryTest` 2/2) → không cần phương án dự phòng §3.14. `VenueAlertService(Impl)` (mỗi lô một `TransactionTemplate`, dừng khi lô ngắn / đủ `max-batches-per-run` / lỗi lô), `VenueAlertProperties`, `VenueStaleAlertScheduler`, `SchedulingConfig` (`@EnableScheduling`), `application.properties` (cron `${VENUE_STALE_ALERT_CRON:0 0 3 * * *}`, 180/500/20), surefire `api` đặt cron `-`. `VenueAlertServiceImplTest` 3/3, `VenueStaleAlertSchedulerTest` 2/2.
6. ✅ **2026-11-19 — 6c job dọn asset, blocker #12 GỠ:** spike live (user đồng ý) trên cloud-dev: list `{resources[], next_cursor}` (không có `next_cursor` ở trang cuối), header `X-FeatureRateLimit-Limit/Remaining/Reset`, delete `{deleted:{id:"deleted"|"not_found"}, deleted_counts, partial}`; chỉ xoá 2 asset spike tự tạo. Code: `ImageAssetAdmin`, `AssetPage`, `AssetAdminRateLimitedException`, `CloudinaryImageAssetAdmin` (`RestClient`, Basic Auth, 420/429 → rate-limited, timeout 5s/30s), `VenueImageRepository#findExistingPublicIds`, `VenueImageCleanupService(Impl)`, `VenueImageCleanupProperties` (+ pageSize, maxAdminCallsPerRun, timeouts, `requireSafeToEnable`), `VenueImageCleanupScheduler` (mặc định tắt `${VENUE_IMAGE_CLEANUP_CRON:-}`, fail-fast khi bật), `application.properties`. Test: `CloudinaryImageAssetAdminTest` 5/5, `CloudinaryImageAssetAdminLiveTest` 1/1 (live, gated env), `VenueImageCleanupServiceImplTest` 6/6, `VenueImageCleanupPropertiesTest` 3/3, `VenueImageCleanupSchedulerTest` 2/2, `VenueImageCleanupIntegrationTest` 1/1. Full suite 362/362 PASS. **Phase 6 hoàn tất.**

### 7 — Final verification/document synchronization
1. Run all relevant unit tests, then integration/migration/race/query-count tests with `.env.dev` and `-Duser.timezone=Asia/Ho_Chi_Minh`; paste output and do not mark PASS until green.
   - ✅ 2026-11-19 đối chiếu code ↔ design: phát hiện `listVenues` **không escape** `%`/`_`/`\` của `q` (design §4.1 yêu cầu) → bugfix TDD: `VenueServiceImplTest#listVenuesEscapesLikeWildcardsAndBackslashInKeyword` Red → Green; `VenueControllerTest#searchKeywordMatchesLikeWildcardsLiterally` PASS trên DB thật. Mã lỗi `VENUE_001..016` khớp HTTP status API_Spec §5. Lệch thiết kế #2–#4 ghi bảng.
2. Compare actual API/DB/integration to approved design; every non-contract implementation variance goes into the lệch-design table immediately, then update design once after all tests pass. Any API/DB/business-contract deviation stops and asks user first.
3. Update `docs/database/{schema.dbml,schema-design.md,data-model.md}` and relevant USER-001 design delta; run `architect-reviewer` only for final design changes. Do not touch framework documents from this feature branch; route framework-only updates separately.
4. Only if user asks to prepare/push a PR: rebase `origin/main`, test again if main moved, run `code-reviewer` on complete branch diff, resolve CRITICAL/MAJOR, then commit/push/PR per request.

### 7b — Xử lý finding `code-reviewer` 2026-11-20 (user chốt từng mục 2026-11-20) — ĐÃ DUYỆT 2026-11-20
Không đổi API contract / DB schema / nghiệp vụ. TDD Red → Green → Refactor cho mục đổi hành vi; mục thuần refactor/comment chạy lại test hiện có.

| # | Quyết định | Việc làm | Test |
|---|---|---|---|
| M1 | Sửa | Live test (gated env, cloud-dev) xoá 1 lô 100 `public_ids[]` **không tồn tại** qua `CloudinaryImageAssetAdmin#deleteAssets` (1 request, không đụng asset thật). Cloudinary nhận → giữ `DELETE_BATCH = 100`; từ chối → giảm `DELETE_BATCH` về mức được nhận + test unit chia lô | `CloudinaryImageAssetAdminLiveTest` (+1 ca); `VenueImageCleanupServiceImplTest` nếu đổi lô |
| m1 | Chuyển việc | Không sửa trong VENUE-001; thuộc việc chuẩn hoá `LocalDateTime`/`TIMESTAMP` giờ VN (nhánh riêng sau khi VENUE-001 vào main) | — |
| m2 | Sửa | `VenueRepository#markStaleAlerted`: WHERE ngoài thêm `AND status = 'ACTIVE' AND COALESCE(last_selected_at, created_at) < :threshold` | Red: integration race commit thật — tx A khoá + `markSelected` sân X (và ca mark-inactive), job chạy luồng khác bị chặn, A commit → X không có trong kết quả |
| m3 | Tech-debt | Ghi `tech-debt.md`, gắn yêu cầu mới "quản lý report chung, bỏ thông báo" | — |
| m4 | Sửa 2 chỗ VENUE | `reuse-scan` → helper `application/support/DataIntegrityViolations` (`constraintName`, `violates`); `GlobalExceptionHandler#constraintName` gọi helper. `VenueServiceImpl:169` chỉ `uq_venue_report_pending` → `VENUE_014`; `VenueImageServiceImpl:165` chỉ `uq_venue_image_public_id` → `VENUE_006`; khác → ném lại. `TeamMembershipServiceImpl:66` ghi tech-debt | Red: `DataIntegrityViolationsTest`; `VenueServiceImplTest`/`VenueImageServiceImplTest` ca constraint khác → ném lại; chạy lại `VenueReportAdminRaceTest`, `VenueImageWriteRaceTest` (tên constraint thật) |
| m5 | Sửa | `CloudinaryImageUploadSigner`: `private static final Pattern` cho regex `parsePublicId` | Chạy lại `CloudinaryImageUploadSignerTest`, test cleanup |
| m6 | Sửa | `VenueImageCleanupServiceImpl`: tách `RestClientResponseException` (log mã HTTP), `ResourceAccessException` (log loại), `RuntimeException` còn lại → `log.error` kèm stack trace; không log body/URL | Red: `VenueImageCleanupServiceImplTest` ca 503, timeout, lỗi bất ngờ — job dừng, không xoá thêm, log đúng mức/nội dung |
| m7 | Sửa | `ConcurrencyConflictRetrier:44` comment → "hết lượt → exhaustedCode sau vòng lặp" | — |
| m8 | Sửa | `VenueServiceImpl:101` dùng `import VenueSummaryView` thay FQN | Chạy lại `VenueServiceImplTest` |
| m9 | Tech-debt | Ghi `tech-debt.md`: giới hạn tần suất 2.4, dựng bộ giới hạn dùng chung khi code USER-003 (§4.5) | — |
| m10 | A | Không sửa V5. Ghi `tech-debt.md` + mô tả PR: trước khi áp V5 ở môi trường khác chạy preflight chỉ-đọc `SELECT COUNT(*) FROM (SELECT reporter_id, venue_id FROM venue_report GROUP BY 1,2 HAVING COUNT(*) > 1) d;` ≠ 0 → dừng | — |

**Kết quả 7b (2026-11-21):** M1 live PASS 1/1 — cloud-dev nhận 1 DELETE 100 `public_ids[]` độ dài thật → giữ `DELETE_BATCH = 100`, không sửa code. m2 Red (2/2 fail: sân vừa chọn / vừa INACTIVE vẫn bị claim — tái hiện thật) → Green `VenueStaleAlertRaceTest` 2/2, `VenueStaleAlertRepositoryTest` 2/2. m4 Red (FK bị map `VENUE_014`/`VENUE_006`) → Green `DataIntegrityViolationsTest` 2/2, `VenueServiceImplTest` 17/17, `VenueImageServiceImplTest` 21/21. m5/m7/m8 áp dụng (không đổi hành vi). m6 Red (3/3 log chỉ tên lớp) → Green `VenueImageCleanupServiceImplTest` 9/9. m3/m9/m10 + `TeamMembershipServiceImpl` → `tech-debt.md` #8–#11.
**Review 7b (`code-reviewer` 2026-11-21): ĐẠT, 0 CRITICAL/MAJOR, 4 MINOR.** n1 sửa (Red: dừng sau lô ngắn → Green `VenueAlertServiceImplTest` 3/3; lệch thiết kế #5). n3 sửa (log nguyên nhân gốc, Red → Green). n4 sửa (`DataIntegrityViolationFixtures`). n2 sửa (poll `pg_stat_activity` theo `pg_backend_pid()` của chính giao dịch claim; `VenueStaleAlertRaceTest` 2/2; kiểm ngược: bỏ sửa m2 → 2/2 fail).
**Review n1–n4 (`code-reviewer` 2026-11-21): ĐẠT, 0 CRITICAL/MAJOR, 1 MINOR** (comment `VenueAlertServiceImpl:68` nói quá) — user chọn sửa câu chữ, không đổi logic. Full suite trước đó 374/374 PASS.
**Đồng bộ design 2026-11-21 (developer đối chiếu):** lệch #1–#4 đã nằm trong design v4.3 (phiên trước). Cập nhật thêm: #5 → `Processing_Flow.md` §3.14 (dừng khi lô rỗng); m2 → `Component_Class_Design.md` câu `markStaleAlerted`; m4 → CCD `reportVenue`/`addImage` + mô tả `DataIntegrityViolations` (code nay khớp `Processing_Flow.md` "vi phạm khác → 500"); m6/n3 → `Processing_Flow.md` §3.16 mục Lỗi. Không đổi API/DB/nghiệp vụ → không sửa SRS, `docs/database/*`. Entry CHANGELOG v4.3 xử lý cùng finding `architect-reviewer` C1 (quy tắc CHANGELOG publish chỉ ghi thay đổi FE thấy được).
**architect-reviewer 2026-11-21 (toàn bộ design chưa commit): CHƯA ĐẠT — 1 CRITICAL, 3 MAJOR, 8 MINOR; user chọn sửa tất cả theo đề xuất.** Đã sửa: C1 (CHANGELOG VENUE-001 v4.3 / USER-001 v4.4 chỉ còn thay đổi FE thấy được + 1 dòng nội bộ BE; `publish-spec` chạy qua), M-new1 (API_Spec ghi kết quả spike; §6 #9 chỉ còn dung lượng file, Không chặn), M1 (bỏ nhãn `[cần xác nhận #9/#11/#12]` đã đóng), M2 + M3 (bảng `[cần xác nhận]` CCD §12, PF §10 tách "Còn mở" / "Đã đóng"; phần dư #9 → #19), M4 (`PATCH` → `MINOR`), N1 (ngày 2026-11-21), N2 (§4.14 `DataIntegrityViolations`), N3 (thứ tự nhánh lỗi PF §3.16), N4 (bỏ `[Inference]` spring-web), N6 (`data-model.md` tách 2 cổng cảnh báo). Re-review lượt 2: CHƯA ĐẠT 1 MAJOR (A1 đánh số #9/#19) + 5 MINOR → user chọn sửa hết; lượt 3: ĐẠT + 1 MINOR tuỳ chọn (409 hoặc lưu ảnh cũ) → user chọn sửa; xác nhận ĐẠT 2026-11-21. `publish-spec` chạy qua. **Design VENUE-001 v4.3 / USER-001 v4.4 / data-model: ĐẠT.**
**Việc sau commit VENUE-001 (user chốt 2026-11-20/21):** nhánh `chore/agent-framework-*` từ `origin/main` — (a) `convention-backend.md` §2.5 Import (không FQN trong thân code `src/main` + `src/test`, trừ trùng tên ngắn; không wildcard) + dòng checklist; (b) §2.4 mở rộng: **mọi** comment (Javadoc + `//`, main + test) tiếng Việt; (c) N5: §1 cây package thêm `application/support/storage/`, `application/support/security/`, `application/event/`, `api/scheduler/`, `@CurrentUser CurrentActor`. Gợi ý (không chặn): ADR-0012 thêm mục "Sửa đổi" qua MR — #11 đã kiểm chứng + WHERE ngoài phải lặp đủ điều kiện.
**Dịch comment tiếng Anh → tiếng Việt (user chốt 2026-11-21, làm trên nhánh này):** Xong: 88 comment / 50 file (kiểm byte sau khi bỏ comment: 0 file đổi ngoài comment), `test-compile` PASS. Full suite 374/374 PASS; `code-reviewer` ĐẠT + 5 MINOR câu chữ (user chọn sửa hết) → xác nhận ĐẠT 2026-11-21.

Sau đó: full suite (`.env.dev`, `-Duser.timezone=Asia/Ho_Chi_Minh`) → `code-reviewer` phần sửa → đối chiếu lệch design (#1–#4) + cập nhật design một lần → xử lý finding `architect-reviewer` 2026-11-19 (user chọn từng mục) → `architect-reviewer` phần sửa → commit khi user yêu cầu.

## Blockers / Ghi chú
- **Cloudinary upload blocker #9 — đã gỡ 2026-11-18:** cấu hình cloud-dev riêng và upload spike PASS; chi tiết ở bước 0.1.
- **#12 Cloudinary Admin API — đã gỡ 2026-11-19** (spike live + `CloudinaryImageAssetAdminLiveTest`).
- **#11 native `UPDATE … RETURNING` — đã gỡ 2026-11-19** (`VenueStaleAlertRepositoryTest`).
- **Migration preflight:** inspect only-aggregated legacy report duplicates before applying V5; any duplicate blocks migration and requires user decision.
- Other `[cần xác nhận]` entries (#2–8, #13–18) are explicitly non-blocking under the approved design; no API/DB/business contract may be changed without asking.
