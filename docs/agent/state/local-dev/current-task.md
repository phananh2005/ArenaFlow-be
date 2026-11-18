---
task: VENUE-001 — quản lý địa điểm sân (V5)
created: 2026-11-18
updated: 2026-11-18
percent: 68
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
- [ ] Các use case/API VENUE-001 trong phạm vi thiết kế được triển khai kèm kiểm thử.
- [ ] Cloudinary, phân quyền, validation và giới hạn phi chức năng tuân thủ thiết kế.
- [ ] Test liên quan PASS với `-Duser.timezone=Asia/Ho_Chi_Minh`.
- [ ] Lệch thiết kế (nếu có) được cập nhật một lần sau khi code/test PASS và được `architect-reviewer` review.
- [ ] Code-reviewer không còn CRITICAL/MAJOR trước khi push tạo PR.

## Các bước thực hiện (tổng quan)
- [x] 1. Đối chiếu spec, design, tiêu chuẩn non-functional và kiến trúc hiện có; rà các mục `[cần xác nhận]`.
- [x] 2. Quét tái sử dụng và lập kế hoạch chi tiết — trình user duyệt.
- [ ] 3. Triển khai theo TDD (Red → Green → Refactor), gồm migration V5 và từng phase VENUE-001.
- [ ] 4. Chạy test liên quan và xác nhận PASS (dán output).
- [ ] 5. Kiểm tra ảnh hưởng, cập nhật design/SRS nếu cần; `architect-reviewer` review phần design sửa.
- [ ] 6. Review & chuẩn bị PR khi user yêu cầu.

## Lệch thiết kế
_Ghi ngay khi code khác design; KHÔNG sửa design giữa chừng — cập nhật một lần ở bước cuối (`CLAUDE.md` §8). Trống = code khớp design._

| # | Phase | Design | Code thực tế | Lý do | Sửa ở |
|---|---|---|---|---|---|

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

### 5 — Report/Admin endpoints 2.7–2.11 (TDD)
1. **Red:** tests cho report/non-admin, pending uniqueness, resolve CAS/deadlock retry, mark inactive/reactivate, closed-report behavior, Admin response giữ tên thật + cờ deleted, audit only after commit.
2. **Green:** `AdminVenueService`, `AdminVenueController`, `AdminVenueReportController`, report repository native operations, `AdminAuditLog`, `VenueAdminAlertEvent`/`VenueAlertType` theo ADR-0011. Không tự thêm listener notification trong VENUE-001.
3. **Refactor/verify:** integration test double-submit report, double-resolve, resolve-vs-inactive, two CLOSED reports one event; query budgets 2.7=2–3/6, 2.9=3–4/6, 2.10=5/6, 2.11=4/6. `progress-tracker` sau phase PASS.

### 6 — Port cho MATCH-001 và scheduled jobs (TDD; hai job theo blocker tương ứng)
1. **Red:** `VenueSelectionService` tests for empty/missing/inactive/order and mandatory transaction; stale alert batch tests; cleanup job tests with fake `ImageAssetAdmin` (age, DB-reference, malformed/future ID, rate limit/budget).
2. **Green:** selection port; alert service/scheduler after #11; `ImageAssetAdmin`, cleanup service/scheduler after #12; scheduling config and properties. Admin API strictly outside transaction; default cleanup cron disabled and fail-fast config validation.
3. **Refactor/verify:** integration query counts/job transactions; no external HTTP in request or open transaction. `progress-tracker` after phase PASS.

### 7 — Final verification/document synchronization
1. Run all relevant unit tests, then integration/migration/race/query-count tests with `.env.dev` and `-Duser.timezone=Asia/Ho_Chi_Minh`; paste output and do not mark PASS until green.
2. Compare actual API/DB/integration to approved design; every non-contract implementation variance goes into the lệch-design table immediately, then update design once after all tests pass. Any API/DB/business-contract deviation stops and asks user first.
3. Update `docs/database/{schema.dbml,schema-design.md,data-model.md}` and relevant USER-001 design delta; run `architect-reviewer` only for final design changes. Do not touch framework documents from this feature branch; route framework-only updates separately.
4. Only if user asks to prepare/push a PR: rebase `origin/main`, test again if main moved, run `code-reviewer` on complete branch diff, resolve CRITICAL/MAJOR, then commit/push/PR per request.

## Blockers / Ghi chú
- **Cloudinary upload blocker #9 — đã gỡ 2026-11-18:** cấu hình cloud-dev riêng và upload spike PASS; chi tiết ở bước 0.1.
- **BLOCKER #12 — Cloudinary Admin API:** requires separate live cloud-dev spike before cleanup job.
- **BLOCKER #11 — native `UPDATE … RETURNING`:** must prove via first integration test before alert job; fallback is specified in `Processing_Flow.md` §3.14.
- **Migration preflight:** inspect only-aggregated legacy report duplicates before applying V5; any duplicate blocks migration and requires user decision.
- Other `[cần xác nhận]` entries (#2–8, #13–18) are explicitly non-blocking under the approved design; no API/DB/business contract may be changed without asking.
