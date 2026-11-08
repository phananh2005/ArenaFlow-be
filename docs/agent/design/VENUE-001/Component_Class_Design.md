# Component & Class Design — Quản lý địa điểm sân

| Mục | Giá trị |
|---|---|
| Mã chức năng | `VENUE-001` |
| Phạm vi tầng | Backend (Spring Boot REST API). Frontend: ngoài phạm vi repo này |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · Spring Data JPA · Flyway · Cloudinary (ký/xác minh cục bộ trong request; Admin API chỉ trong job dọn asset) |
| Quy ước áp dụng | `docs/agent/convention-backend.md`, `docs/agent/security-backend.md`, `docs/agent/non-functional-standards.md` · kế thừa USER-001 `Component_Class_Design.md` §4.6, §10 · ADR-0002, ADR-0003 (+ Sửa đổi 2026-11-09), ADR-0006, ADR-0008, [ADR-0009](../../decisions/0009-cloudinary-signed-direct-upload.md) (`Proposed`) |
| Ràng buộc dữ liệu | V1 + `V5__venue_management.sql` (§5.1); entity hiện có `domain/.../entity/venue/{Venue,VenueImage,VenueReport}` |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-09 · v1.0 (baseline) |

## 1. Tổng quan kiến trúc

Layered theo 4 module Maven (ADR-0006) như USER-001. Hợp đồng: `API_Spec.md`. Luồng, transaction, số truy vấn: `Processing_Flow.md`. Điểm mới:
- **Cloudinary** sau 2 interface trung tính: `ImageUploadSigner` (ký/xác minh bằng JDK, trong request, §4.5) và `ImageAssetAdmin` (Admin API, chỉ job dọn, §4.12).
- **Domain event** `ApplicationEvent` cho NOTIFICATION-001 (§4.7).
- **2 scheduled job** đầu tiên của repo: cảnh báo 180 ngày (§4.8), dọn asset (§4.12).
- **Port cho MATCH-001** `VenueSelectionService` (§4.6).
- **Quyền chủ sở hữu + role** qua `@CurrentUser CurrentActor` (§4.11).
- **Khoá dòng `venue` cho ghi ảnh** thay cho kiểm `@Version` + retry (§4.4, ADR-0003 Sửa đổi 2026-11-09).

## 2. Package Structure (delta)

```text
common/        com.phananh.arenaflow.common
└── exception/       ErrorCode (+ VENUE_001..VENUE_015)

domain/        com.phananh.arenaflow.domain
├── entity/venue/    Venue, VenueImage, VenueReport (sửa — §6)
├── repository/      VenueRepository, VenueImageRepository, VenueReportRepository (mới)
└── projection/      VenueDetailView, VenueSummaryView, VenueImageStatsView, VenueStatusView,
                     StaleVenueView, AdminVenueReportView (mới)

application/   com.phananh.arenaflow.application
├── service/         VenueService, VenueImageService, AdminVenueService, VenueSelectionService,
│   │                VenueAlertService, VenueImageCleanupService, VenueStatusFilter, VenueReportStatusFilter
│   └── impl/        *ServiceImpl
├── event/           VenueReportedEvent, VenueAdminAlertEvent, VenueAlertType (package mới)
├── support/         VenueGuard, AdminAuditLog (mới), ConcurrencyConflictRetrier (sửa — §4.9)
│   ├── security/    CurrentActor (mới)
│   ├── storage/     ImageUploadSigner, UploadSignature, SignedPublicId, CloudinaryImageUploadSigner,
│   │                ImageAssetAdmin, AssetPage, AssetAdminRateLimitedException,
│   │                CloudinaryImageAssetAdmin, CloudinaryProperties (package mới)
│   └── venue/       VenueAlertProperties, VenueImageCleanupProperties (mới)
├── validation/      ValidGeoQuery + ValidGeoQueryValidator (mới); OneOf (tái dùng)
├── dto/request/     CreateVenueRequest, UpdateVenueRequest, SearchVenueRequest, AddVenueImageRequest,
│                    ReportVenueRequest, ResolveVenueReportRequest
├── dto/response/    VenueResponse, VenueSummaryResponse, VenueImageResponse,
│                    VenueImageUploadSignatureResponse, VenueReportResponse, AdminVenueReportResponse
└── mapper/          VenueMapper (MapStruct); MapperSupport (tái dùng)

api/           com.phananh.arenaflow.api
├── venue/           VenueController, VenueImageController, AdminVenueController, AdminVenueReportController
├── scheduler/       VenueStaleAlertScheduler, VenueImageCleanupScheduler (package mới)
├── security/        CurrentUserArgumentResolver (sửa — §4.11)
└── config/          SchedulingConfig (@EnableScheduling)
```

Thành phần dùng chung mới/sửa (`VenueGuard`, `ImageUploadSigner`, `ImageAssetAdmin`, `event/`, `ConcurrencyConflictRetrier`, `ValidGeoQuery`, `CurrentActor`, `CurrentUserArgumentResolver`) → developer MUST chạy `reuse-scan` trước khi tạo.

## 3. Controller Layer

Quy ước như USER-001 §3 (`@Validated`, `ResponseEntity<ApiResponse<…>>`, 201 không `Location`). Endpoint cần role nhận `@CurrentUser CurrentActor actor` (2.4–2.7, 2.12, 2.13); còn lại `@CurrentUser UUID`.

| Controller | `@RequestMapping` | Endpoint | Service |
|---|---|---|---|
| `VenueController` | `/api/v1/venues` | 2.1, 2.2, 2.3, 2.7, 2.12 | `VenueService` |
| `VenueImageController` | `/api/v1/venues/{venueId}` (`/image-upload-signatures`, `/images`, `/images/{imageId}/set-primary`, `DELETE /images/{imageId}`) | 2.4, 2.5, 2.6, 2.13 | `VenueImageService` |
| `AdminVenueController` | `/api/v1/admin/venues` | 2.10, 2.11 | `AdminVenueService` |
| `AdminVenueReportController` | `/api/v1/admin/venue-reports` | 2.8, 2.9 | `AdminVenueService` |

- 2.2: `@Valid @ModelAttribute SearchVenueRequest` (§7). 2.8: `@RequestParam` như `AdminTeamController`.
- Không đổi `SecurityConfig`.

## 4. Service Layer

### 4.1. VenueService
```java
VenueResponse createVenue(UUID callerId, CreateVenueRequest request);                     // @Transactional
PageResponse<VenueSummaryResponse> searchVenues(SearchVenueRequest request);             // readOnly
VenueResponse getVenue(UUID venueId);                                                     // readOnly; dùng lại ở 2.10–2.12
VenueReportResponse reportVenue(UUID venueId, CurrentActor actor, ReportVenueRequest r); // @Transactional
VenueResponse updateVenue(UUID venueId, CurrentActor actor, UpdateVenueRequest r);       // retrier(VENUE_011)
```
Deps: `VenueRepository`, `VenueReportRepository`, `UserRepository` (`findByIdAndDeletedAtIsNull`), `VenueImageRepository`, `VenueGuard`, `VenueMapper`, `PageRequestFactory`, `ApplicationEventPublisher`, `AdminAuditLog`.
- `createVenue`: chuẩn hoá (trim; `""` → `null`) → `Venue.builder()` → `save`; response từ entity + `displayName`, không đọc lại.
- `searchVenues`: escape `q` (`%`, `_`, `\`) rồi bọc `%…%`; chọn 1 trong 4 truy vấn theo (có toạ độ?, `status`) — §5.2.
- `reportVenue`: `actor.admin()` → `VENUE_015` → `activeVenue` → `saveAndFlush`; constraint `uq_venue_report_pending` → `VENUE_014`.
- `updateVenue`: `findVenue` → `requireOwnerOrAdmin` → `requireWritable` → setter → `save` → `getVenue`.
- Luồng chi tiết: `Processing_Flow.md` §3.1–§3.3, §3.7, §3.12.

### 4.2. VenueImageService
```java
VenueImageUploadSignatureResponse issueUploadSignature(UUID venueId, CurrentActor actor);          // readOnly
VenueImageResponse addImage(UUID venueId, CurrentActor actor, AddVenueImageRequest r);              // kiểm không DB rồi TransactionTemplate
List<VenueImageResponse> setPrimaryImage(UUID venueId, UUID imageId, CurrentActor actor);           // @Transactional
List<VenueImageResponse> deleteImage(UUID venueId, UUID imageId, CurrentActor actor);               // @Transactional
```
Deps: `VenueGuard`, `VenueImageRepository`, `ImageUploadSigner`, `VenueMapper`, `VenueImageCleanupProperties`, `TransactionTemplate`, `AdminAuditLog`.
- Hằng `MAX_IMAGES_PER_VENUE = 5` (BR-V02); `scopePath = "venues/" + venueId`.
- Ghi ảnh (2.5, 2.6, 2.13) mở bằng `venueGuard.lockForImageWrite` — không retrier (§4.4).
- `addImage`: không annotate; kiểm `publicId` + chữ ký response (không DB) **trước**, rồi phần DB trong `transactionTemplate.execute` để không giữ kết nối khi tính toán; `saveAndFlush` bắt `uq_venue_image_public_id` → `VENUE_006` (NFS §4.1 ngoại lệ 1).
- Luồng: `Processing_Flow.md` §3.4–§3.6, §3.13.

### 4.3. AdminVenueService
```java
VenueResponse markInactive(UUID venueId, UUID adminId);                                    // retrier(VENUE_011)
VenueResponse reactivate(UUID venueId, UUID adminId);                                      // retrier(VENUE_011)
PageResponse<AdminVenueReportResponse> listReports(VenueReportStatusFilter s, UUID venueId, int page, int pageSize); // readOnly
AdminVenueReportResponse resolveReport(UUID reportId, UUID adminId, ResolveVenueReportRequest r); // retrier(VENUE_011)
```
Deps: `VenueGuard`, `VenueRepository`, `VenueReportRepository`, `VenueService` (`getVenue`), `ConcurrencyConflictRetrier`, `VenueMapper`, `PageRequestFactory`, `AdminAuditLog` (§4.13).

### 4.4. VenueGuard (`application/support`, `@Component`)
```java
Venue findVenue(UUID venueId);                          // findById; không có → VENUE_001
Venue activeVenue(UUID venueId);                        // findVenue + INACTIVE → VENUE_002 (mọi người) — 2.7
void requireOwnerOrAdmin(Venue v, CurrentActor a);      // a.admin() || a.userId().equals(v.getCreatedByUserId()); sai → VENUE_013
void requireWritable(Venue v, CurrentActor a);          // INACTIVE && !a.admin() → VENUE_002 (BR-V06, BR-V07)
Venue lockForImageWrite(UUID venueId);                  // Q1 lockRow (0 dòng → VENUE_001) → Q2 findById
```
- Nguồn duy nhất cho mã `VENUE_001/002/013` theo thứ tự `API_Spec.md` §2.0 (service gọi đúng thứ tự cho từng endpoint).
- `lockForImageWrite`: `UPDATE venue SET version = version + 1 WHERE id = :id` **không** so `version` → chờ khoá dòng thay vì thất bại, nên mọi ghi ảnh của cùng địa điểm chạy tuần tự **trước** khi đọc trạng thái/số ảnh, không cần retry. Đọc entity **sau** khi khoá (Q2) → luôn thấy dữ liệu đã commit mới nhất. Tăng `version` để luồng CAS khác (2.10–2.12) đang giữ bản cũ phát hiện xung đột. Lý do bỏ CAS + retry: `Processing_Flow.md` §5; ghi nhận ở ADR-0003 Sửa đổi 2026-11-09.
- Hệ quả chấp nhận: ở 2.13, caller không có quyền vẫn giữ khoá dòng trong thời gian ngắn trước khi bị từ chối (đổi lại không thêm truy vấn).

### 4.5. ImageUploadSigner (`application/support/storage`) — trung tính, tái dùng được cho ảnh khác

```java
public interface ImageUploadSigner {
    boolean isConfigured();                                        // cloudName, apiKey, apiSecret, folder đều có
    UploadSignature signUpload(String scopePath, UUID uploaderId); // scopePath vd "venues/<venueId>"
    Optional<SignedPublicId> parsePublicId(String publicId);       // sai mẫu → empty
    boolean isUploadedBy(SignedPublicId id, UUID userId);           // so tag HMAC, thời gian hằng
    boolean verifyUploadResponse(String publicId, long version, String signature);
    String deliveryUrl(String publicId, long version, String format);
    String rootPrefix(String scopeRoot);                           // "<folder>/<scopeRoot>/" — job dọn dùng
}
public record UploadSignature(String uploadUrl, String cloudName, String apiKey, String publicId, long timestamp,
                              String allowedFormats, boolean overwrite, String signature, OffsetDateTime expiresAt) {}
public record SignedPublicId(String scopePath, Instant signedAt, String nonce, String tag) {}
```

**Định dạng `publicId`:** `<folder>/<scopePath>/<signedAt>-<nonce>-<tag>`
- `signedAt`: Unix giây, = `timestamp` đã ký → tuổi asset tính được không cần dữ liệu Cloudinary.
- `nonce`: 16 byte `SecureRandom`, base64url không padding (22 ký tự).
- `tag`: 22 ký tự đầu base64url của `HMAC-SHA256(tagKey, scopePath + "|" + signedAt + "|" + nonce + "|" + uploaderId)`; `tagKey = HMAC-SHA256(apiSecret, "arenaflow-upload-tag")` (tách miền với chữ ký Cloudinary). **Vì sao:** ràng buộc người xin chữ ký vào chính `public_id` đã ký → Cloudinary chặn đổi `public_id`, backend kiểm lại ở 2.5 bằng caller hiện tại; URL công khai chỉ chứa tag một chiều (có nonce riêng mỗi upload nên không liên kết được các ảnh cùng người, không suy ra user id); không thêm field request và không thêm secret phải quản lý. Phương án loại: tham số `context` của Cloudinary (backend không đọc lại được ở 2.5 nếu không gọi Admin API, chữ ký response không phủ `context`); user id trong `public_id` (lộ trên URL).
- Mẫu kiểm: `^<folder>/<scopePath>/\d{10}-[A-Za-z0-9_-]{22}-[A-Za-z0-9_-]{22}$`; không `..`. `folder` và `scopePath` ghép vào regex qua `Pattern.quote(...)` (thư mục có thể chứa ký tự đặc biệt của regex, vd `.`).

**Ký request** ([Authentication signatures](https://cloudinary.com/documentation/authentication_signatures)): mọi tham số gửi kèm trừ `file`, `cloud_name`, `resource_type`, `api_key`; sắp theo tên, nối `k=v` bằng `&`, nối liền `api_secret`, SHA-1 hex. Tham số ký: `allowed_formats`, `overwrite=false`, `public_id`, `timestamp`. Hợp lệ 1 giờ. Test vector bắt buộc: `timestamp=1315060510` + secret `abcd` → `a21ad0f63beb4de2e5575204b79ab90bffb02c10`. `overwrite=false`: chặn dùng lại chữ ký trong 1 giờ để ghi đè asset đã có `[cần xác nhận #9]`.

**Xác minh response** ([Response signatures](https://cloudinary.com/documentation/response_signatures)): digest SHA trên `public_id`, `version`, `api_secret`; thiết kế: SHA-1 hex của `"public_id=" + publicId + "&version=" + version + apiSecret`, so bằng `MessageDigest.isEqual`. Cách nối chuỗi chưa kiểm trên response thật `[cần xác nhận #9]`.

`deliveryUrl` = `https://res.cloudinary.com/<cloudName>/image/upload/v<version>/<publicId>.<format>`. **NEVER** log `apiSecret`, `tagKey`, chuỗi cần ký, `signature`.

`CloudinaryImageUploadSigner` (`@Component`, deps `CloudinaryProperties`). `CloudinaryProperties` (`arenaflow.cloudinary.*`, quét bởi `@ConfigurationPropertiesScan` như `JwtProperties`): `cloudName`, `apiKey`, `apiSecret`, `folder` — **không** có giá trị mặc định (§8). Thiếu → `isConfigured() == false` → 2.4/2.5 trả `503 VENUE_012`, log `WARN` một lần lúc khởi động.

### 4.6. VenueSelectionService — port cho MATCH-001
```java
void requireSelectable(Collection<UUID> venueIds);                    // readOnly; thiếu id → VENUE_001; có INACTIVE → VENUE_002; 1 truy vấn
void markSelected(Collection<UUID> venueIds, OffsetDateTime selectedAt); // MANDATORY; 1 truy vấn bulk, reset cổng 180 ngày
```
`MANDATORY` để `last_selected_at` rollback cùng kèo. Thời điểm "được chọn" do MATCH-001 quyết định.

### 4.7. Domain event (`application/event`)
```java
public record VenueReportedEvent(UUID reportId, UUID venueId, String venueName, UUID reporterId,
                                 VenueReport.Reason reason, OffsetDateTime reportedAt) {}
public record VenueAdminAlertEvent(UUID venueId, String venueName, VenueAlertType alertType, OffsetDateTime triggeredAt) {}
public enum VenueAlertType { STALE_180_DAYS, CLOSURE_REPORTED }
```
Phát trong transaction ghi; hợp đồng listener (NOTIFICATION-001): `@TransactionalEventListener(AFTER_COMMIT)` (ADR-0003 §Cần theo dõi). VENUE-001 không có listener. Payload không chứa PII ngoài id. Không có event xoá ảnh (job dọn tự đối chiếu DB). Quy ước event: `[cần xác nhận #10]`.

### 4.8. VenueAlertService + scheduler (cảnh báo 180 ngày, BC-V05)
`int alertStaleVenues()` — mỗi lô một `TransactionTemplate`. `VenueStaleAlertScheduler`: `@Scheduled(cron = "${arenaflow.venue.stale-alert.cron}", zone = "Asia/Ho_Chi_Minh")`, không logic. `SchedulingConfig`: `@EnableScheduling`. Cron `-` = tắt (dùng trong test). Tham số: §8. Luồng: `Processing_Flow.md` §3.14.

### 4.9. ConcurrencyConflictRetrier (sửa — dùng chung)
```java
public <T> T execute(Supplier<T> operation);                           // giữ nguyên: hết lượt → TEAM_014
public <T> T execute(ErrorCode exhaustedCode, Supplier<T> operation);  // mới
```
Bản cũ uỷ quyền bản mới với `TEAM_014` → USER-001/002 không đổi. VENUE-001 dùng `VENUE_011` cho 2.9–2.12. Ghi nhận ở ADR-0003 Sửa đổi 2026-11-09.

### 4.10. Transaction trong ServiceImpl

Theo USER-001 §4.6: không `@Transactional` mức class.

| Method | Ranh giới |
|---|---|
| `searchVenues`, `getVenue`, `issueUploadSignature`, `listReports`, `requireSelectable` | `@Transactional(readOnly = true)` |
| `createVenue`, `reportVenue` | `@Transactional` |
| `setPrimaryImage`, `deleteImage` | `@Transactional`; tuần tự hoá bằng `lockForImageWrite`, không retrier |
| `addImage` | Không annotate; kiểm không DB trước, phần DB trong `TransactionTemplate` (khoá như trên), không retrier |
| `updateVenue`, `markInactive`, `reactivate`, `resolveReport` | Không annotate; `concurrencyConflictRetrier.execute(VENUE_011, …)` (CAS `@Version` hoặc bulk có điều kiện) |
| `markSelected` | `@Transactional(propagation = MANDATORY)` |
| `alertStaleVenues` | Mỗi lô một `TransactionTemplate` |
| `cleanupOrphanAssets` | Mỗi trang một `TransactionTemplate` read-only ngắn cho truy vấn DB; Admin API gọi **ngoài** transaction (NFS §5) |

### 4.11. CurrentActor (BR-V06, BR-V07, UC-V03 actor)
```java
public record CurrentActor(UUID userId, boolean admin) {}   // application/support/security
```
`CurrentUserArgumentResolver` (sửa) hỗ trợ thêm `@CurrentUser CurrentActor`: `userId` từ `Jwt.getSubject()` (lỗi → `AUTH_001`), `admin` = authorities chứa `ROLE_ADMIN`. Không truy vấn DB `[cần xác nhận #15]`. Không dùng `@PreAuthorize` vì quyền phụ thuộc `created_by_user_id`, trạng thái địa điểm và thứ tự guard.

### 4.12. Job dọn asset Cloudinary

```java
int cleanupOrphanAssets();   // VenueImageCleanupService; chỉ scheduler gọi
public interface ImageAssetAdmin {                                     // application/support/storage, trung tính
    AssetPage listAssets(String prefix, String cursor, int maxResults);  // GET /resources/image/upload?prefix=&max_results=&next_cursor=
    Set<String> deleteAssets(List<String> publicIds);                    // DELETE /resources/image/upload, public_ids[] ≤100; trả id "deleted"
}
public record AssetPage(List<String> publicIds, String nextCursor) {}
```
- `CloudinaryImageAssetAdmin`: Spring `RestClient` (spring-web, có sẵn qua `common` — [Inference] kiểm scope khi implement), Basic Auth `apiKey:apiSecret`, base `https://api.cloudinary.com/v1_1/<cloudName>`, timeout §8. HTTP 420/429 → `AssetAdminRateLimitedException` (kèm `X-FeatureRateLimit-Reset`); lỗi khác ném nguyên. Nguồn: [Admin API overview](https://cloudinary.com/documentation/admin_api_overview.md), [Get resources](https://cloudinary.com/documentation/admin_api_resources_get_resources.md), [Delete resources](https://cloudinary.com/documentation/admin_api_resources_delete_resources_by_public_id.md) `[cần xác nhận #12]`.
- `VenueImageCleanupScheduler`: `@Scheduled(cron = "${arenaflow.venue.image-cleanup.cron}", zone = "Asia/Ho_Chi_Minh")`.
- **Mặc định TẮT** (cron `-`); chỉ bật tường minh. **Fail-fast khi khởi động** nếu job bật và: Cloudinary chưa cấu hình, `folder` trống hoặc bằng giá trị mặc định cũ `arenaflow`, hoặc `confirm-window + 1h ≥ grace`. Lý do: nhiều môi trường dùng chung một cloud mà chung thư mục thì job môi trường này sẽ xoá asset môi trường khác (asset không có trong DB của nó).
- Luồng, lý do tham số, lỗi: `Processing_Flow.md` §3.16.

### 4.13. Audit log thao tác Admin (`security-backend.md` §11)
`AdminAuditLog` (`application/support`, `@Component`): `void afterCommit(String action, UUID adminId, UUID venueId, UUID reportId)` — đăng ký `TransactionSynchronizationManager.registerSynchronization(…)` và chỉ ghi `log.info("Admin action: action={}, adminId={}, venueId={}, reportId={}", …)` trong `afterCommit()`. Gọi **bên trong** transaction của thao tác (trong lambda retrier hoặc method `@Transactional`): lần thử bị rollback không ghi, lần thử commit ghi đúng một lần. Một cơ chế cho mọi audit log admin của VENUE-001 (không ghi ở tầng gọi ngoài transaction — `@Transactional` method như 2.13 không có tầng như vậy). Thiếu transaction đang hoạt động → `IllegalStateException` (lỗi lập trình). Áp cho: `MARK_INACTIVE`, `REACTIVATE`, `RESOLVE_REPORT` (+ trạng thái chốt), và 2.12/2.13 khi `actor.admin()` và không phải người tạo (`ADMIN_UPDATE_VENUE`, `ADMIN_DELETE_IMAGE`). Chỉ id, không tên/PII/body.

## 5. Repository & Data Model

### 5.1. Migration `V5__venue_management.sql`

```sql
-- venue: khoá lạc quan, cổng cảnh báo 1 lần, loại sân, nhất quán INACTIVE
ALTER TABLE venue ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE venue ADD COLUMN stale_alerted_at TIMESTAMPTZ;
ALTER TABLE venue ADD COLUMN closure_alerted_at TIMESTAMPTZ;
UPDATE venue SET venue_type = NULL WHERE venue_type IS NOT NULL AND venue_type NOT IN ('FIVE_A_SIDE', 'SEVEN_A_SIDE', 'ELEVEN_A_SIDE');
ALTER TABLE venue ADD CONSTRAINT venue_type_check CHECK (venue_type IS NULL OR venue_type IN ('FIVE_A_SIDE', 'SEVEN_A_SIDE', 'ELEVEN_A_SIDE'));
UPDATE venue SET admin_verified_inactive_at = COALESCE(admin_verified_inactive_at, updated_at) WHERE status = 'INACTIVE';
UPDATE venue SET admin_verified_inactive_at = NULL WHERE status = 'ACTIVE';
ALTER TABLE venue ADD CONSTRAINT venue_inactive_consistency_check CHECK (
    (status = 'INACTIVE' AND admin_verified_inactive_at IS NOT NULL)
    OR (status = 'ACTIVE' AND admin_verified_inactive_at IS NULL));
CREATE INDEX idx_venue_status_name ON venue(status, name, id);

-- venue_image: định danh Cloudinary
ALTER TABLE venue_image ADD COLUMN public_id VARCHAR(255);
UPDATE venue_image SET public_id = 'legacy/' || id::text WHERE public_id IS NULL;
ALTER TABLE venue_image ALTER COLUMN public_id SET NOT NULL;
CREATE UNIQUE INDEX uq_venue_image_public_id ON venue_image(public_id);

-- venue_report: lý do dạng mã + chi tiết, trạng thái xử lý, chống trùng
ALTER TABLE venue_report ADD COLUMN detail TEXT;
UPDATE venue_report SET detail = reason, reason = 'OTHER';
ALTER TABLE venue_report ALTER COLUMN reason TYPE VARCHAR(20);
ALTER TABLE venue_report ADD CONSTRAINT venue_report_reason_check CHECK (reason IN ('CLOSED', 'WRONG_INFO', 'OTHER'));
ALTER TABLE venue_report ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE venue_report ADD CONSTRAINT venue_report_status_check CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED'));
ALTER TABLE venue_report ADD COLUMN handled_by UUID REFERENCES "user"(id) ON DELETE RESTRICT;
ALTER TABLE venue_report ADD COLUMN handled_at TIMESTAMPTZ;
ALTER TABLE venue_report ADD CONSTRAINT venue_report_handled_consistency_check CHECK (
    (status = 'PENDING' AND handled_by IS NULL AND handled_at IS NULL)
    OR (status <> 'PENDING' AND handled_by IS NOT NULL AND handled_at IS NOT NULL));
CREATE INDEX idx_venue_report_status_created ON venue_report(status, created_at DESC);
CREATE UNIQUE INDEX uq_venue_report_pending ON venue_report(reporter_id, venue_id, reason) WHERE status = 'PENDING';
```
- Chỉ delta spec/thiết kế cần: khoá + cổng cảnh báo (BC-V05), CHECK loại sân (BR-V05; giữ kiểu cột V1), `public_id` (ADR-0009), trạng thái report (Data Model §2.4), chống trùng report (BR-V09).
- Dữ liệu cũ (chưa kiểm số dòng Neon dev — developer kiểm khi lập kế hoạch): `UPDATE` giữ migration chạy được; `venue_type` lạ → `NULL`; report trùng `(reporter_id, venue_id, 'OTHER')` sẽ làm `uq_venue_report_pending` lỗi → dừng, báo, không tự xoá.
- `handled_by ON DELETE RESTRICT` nhất quán `team.disbanded_by`. Không ràng buộc ≤5 ảnh / unique `display_order` ở DB (tuần tự hoá bằng khoá dòng `venue`). Không index cho job 180 ngày (1 lần/ngày). Giữ bất nhất V1 `reporter_id NOT NULL` + `ON DELETE SET NULL` (user chỉ soft-delete).
- **Xoá ảnh = hard-delete** dòng `venue_image` (lệch có chủ đích, §10).

### 5.2. Repository

Native + interface projection như USER-001 §5; `Page` native có `countQuery`; bulk `@Modifying` trên `venue` MUST `version + 1`.

```java
public interface VenueRepository extends JpaRepository<Venue, UUID> {
    @Modifying int lockRow(UUID id);                                       // UPDATE venue SET version = version + 1 WHERE id = :id
    Optional<VenueDetailView> findDetailById(UUID id);                     // LEFT JOIN "user" người tạo
    Page<VenueSummaryView> searchByStatus(String status, String qPattern, Pageable p);   // status ∈ ACTIVE|INACTIVE
    Page<VenueSummaryView> searchAll(String qPattern, Pageable p);                        // status = ALL
    Page<VenueSummaryView> searchNearbyActive(String qPattern, double lat, double lng, double radiusKm,
            double minLat, double maxLat, double minLng, double maxLng, Pageable p);      // literal 'ACTIVE'
    Page<VenueSummaryView> searchNearbyAny(String status /* null = ALL */, String qPattern, double lat, double lng,
            double radiusKm, double minLat, double maxLat, double minLng, double maxLng, Pageable p);
    List<VenueStatusView> findStatusesByIdIn(Collection<UUID> ids);
    @Modifying int markClosureAlerted(UUID id, OffsetDateTime now);        // WHERE status='ACTIVE' AND closure_alerted_at IS NULL
    @Modifying int reopenClosureAlertIfNoPending(UUID id);                 // WHERE closure_alerted_at IS NOT NULL AND NOT EXISTS report CLOSED PENDING
    @Modifying int markSelected(Collection<UUID> ids, OffsetDateTime selectedAt);
    List<StaleVenueView> markStaleAlerted(OffsetDateTime threshold, OffsetDateTime now, int limit); // UPDATE … RETURNING
}
public interface VenueImageRepository extends JpaRepository<VenueImage, UUID> {
    List<VenueImage> findByVenueIdOrderByDisplayOrderAsc(UUID venueId);
    long countByVenueId(UUID venueId);
    VenueImageStatsView findStatsByVenueId(UUID venueId);                  // COUNT(*), COALESCE(MAX(display_order), 0)
    @Modifying int clearPrimary(UUID venueId);
    @Modifying int setPrimary(UUID venueId, UUID imageId);                 // 0 nếu ảnh không thuộc venue
    Optional<VenueImage> findByIdAndVenueId(UUID id, UUID venueId);
    @Modifying int promoteFirstAsPrimary(UUID venueId);
    @Query("SELECT i.publicId FROM VenueImage i WHERE i.publicId IN :publicIds")
    List<String> findExistingPublicIds(Collection<String> publicIds);      // job dọn, ≤ page-size tham số
}
public interface VenueReportRepository extends JpaRepository<VenueReport, UUID> {
    Page<AdminVenueReportView> findAdminReports(String status /* null = ALL */, UUID venueId /* null = mọi */, Pageable p);
    Optional<AdminVenueReportView> findAdminViewById(UUID id);
    @Modifying int resolveIfPending(UUID id, String status, UUID adminId, OffsetDateTime now);
    @Modifying int verifyPendingClosureReports(UUID venueId, UUID adminId, OffsetDateTime now);
}
```

Ghi chú SQL:
- **Tìm kiếm tách 4 truy vấn tường minh** để planner dùng được index: `searchByStatus` (`v.status = :status`, sort `name, id` → `idx_venue_status_name`); `searchAll` (không lọc status); `searchNearbyActive` dùng literal `v.status = 'ACTIVE'` để khớp partial index `idx_venue_location … WHERE status = 'ACTIVE'` (tham số bind không chứng minh được vị từ partial index ở generic plan); `searchNearbyAny` cho `INACTIVE|ALL` (hiếm, chủ yếu Admin, không bảo đảm index). Chung: `(:qPattern IS NULL OR v.name ILIKE :qPattern OR v.address ILIKE :qPattern)` `[cần xác nhận #8]`; `LEFT JOIN venue_image vi ON vi.venue_id = v.id AND vi.is_primary`. Nearby: hộp giới hạn (`Δlat = r/111.32`, `Δlng = r/(111.32·cos lat)`, tính ở service) + lọc `distance_km <= :radiusKm` qua subquery; Haversine R = 6371 km.
- `lockRow`: Venue chưa nằm trong persistence context lúc gọi → `findById` sau đó nạp bản mới; entity không dirty nên không bị ghi lại lúc commit. Lock mode PostgreSQL của `UPDATE` không đổi cột khoá là `FOR NO KEY UPDATE` — tương thích với `FOR KEY SHARE` mà INSERT `venue_image`/`venue_report` lấy qua FK.
- `promoteFirstAsPrimary`: `UPDATE venue_image SET is_primary = TRUE WHERE id = (SELECT id FROM venue_image WHERE venue_id = :venueId ORDER BY display_order, id LIMIT 1)`; Hibernate tự flush `DELETE` trước câu này (comment một dòng — NFS §4.1).
- `markStaleAlerted`: `UPDATE venue SET stale_alerted_at = :now, version = version + 1 WHERE id IN (SELECT id FROM venue WHERE status = 'ACTIVE' AND stale_alerted_at IS NULL AND COALESCE(last_selected_at, created_at) < :threshold ORDER BY id LIMIT :limit) AND stale_alerted_at IS NULL RETURNING id, name` — khai báo không `@Modifying` `[cần xác nhận #11]`; dự phòng: `Processing_Flow.md` §3.14.
- `markSelected`: `SET last_selected_at = GREATEST(COALESCE(last_selected_at, :t), :t), stale_alerted_at = NULL, version = version + 1 WHERE id IN (:ids)`.

### 5.3. Projection

| Projection | Cột |
|---|---|
| `VenueDetailView` | cột `venue` cho `VenueResponse` + `createdByName`, `createdByDeleted` |
| `VenueSummaryView` | cột `VenueSummaryResponse` + `primaryImageUrl`, `distanceKm` (`Double`) |
| `VenueImageStatsView` | `imageCount`, `maxDisplayOrder` |
| `VenueStatusView` | `id`, `status` |
| `StaleVenueView` | `id`, `name` |
| `AdminVenueReportView` | cột report + `venueName`, `venueStatus`, `reporterName`, `reporterDeleted`, `handledByName`, `handledByDeleted` |

Thời gian trong projection là `Instant`, mapper chuyển UTC (USER-001 §5).

## 6. Entity (`domain/entity/venue`)

| Entity | Thay đổi |
|---|---|
| `Venue` | + `@Version Long version` · + `staleAlertedAt`, `closureAlertedAt` · `venueType`: `String` → `enum VenueType { FIVE_A_SIDE, SEVEN_A_SIDE, ELEVEN_A_SIDE }` (`@Enumerated(STRING)`, `length = 50`) |
| `VenueImage` | + `publicId` (`nullable = false`, `length = 255`) |
| `VenueReport` | `reason`: hiện là `String` với `@Column(name = "reason", nullable = false, columnDefinition = "TEXT")` → **bỏ `columnDefinition`**, đổi thành `enum Reason { CLOSED, WRONG_INFO, OTHER }` với `@Enumerated(EnumType.STRING) @Column(name = "reason", nullable = false, length = 20)` (khớp `VARCHAR(20)` sau V5) · + `detail`: `@Column(name = "detail", columnDefinition = "TEXT")` · + `status`: `enum Status { PENDING, VERIFIED, REJECTED }`, `@Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20)`, `@Builder.Default PENDING` · + `handledBy` (`UUID`, `handled_by`), `handledAt` (`OffsetDateTime`, `handled_at`); sửa Javadoc lớp |

Giữ Lombok/builder/`@PrePersist` (`convention-backend.md` §6, §6.1); `src/main` không gán `id`, `version`, `createdAt`, `updatedAt` qua builder. Kiểm bằng migration integration test (`ddl-auto=validate` sau V5): `reason`/`status` là `VARCHAR(20)`, `venue_type` `VARCHAR(50)`, `public_id` `VARCHAR(255)`.

## 7. DTO & Mapper

- DTO là `record`; enum dạng chuỗi dùng `@OneOf` (phân biệt hoa thường).
- **Field tuỳ chọn chấp nhận `""`** (cả tạo và sửa — `API_Spec.md` §3): `venueType` `@OneOf({"FIVE_A_SIDE","SEVEN_A_SIDE","ELEVEN_A_SIDE",""})`; `phone` `@Pattern(regexp = "^$|^0[0-9]{9,10}$")`; `website` `@URL(regexp = "^$|^https?://.*")` + `@Size(max = 500)` ([Inference] `@URL` của Hibernate Validator coi `""` hợp lệ — có test). Service đổi `""` → `null` (tạo) hoặc xoá giá trị (sửa). `UpdateVenueRequest.venueName/address`: `@Size` + `@Pattern(regexp = "(?s).*\\S.*")` (null hợp lệ, chỉ khoảng trắng → 400).
- **`SearchVenueRequest`** (record, bind `@ModelAttribute`): `page`, `pageSize` kiểu `Integer`, `status` kiểu `VenueStatusFilter`; compact constructor gán mặc định khi `null` (`page = 1`, `pageSize = 20`, `status = ACTIVE`, `radiusKm = 5` nếu có toạ độ) **trước** Bean Validation (`@Min/@Max` chạy trên giá trị sau mặc định). `@ValidGeoQuery` mức lớp gắn lỗi vào property node (`lat` / `radiusKm`) vì `GlobalExceptionHandler` chỉ đọc `getFieldErrors()`. Test bắt buộc: không gửi `page`/`pageSize`/`status` → 1/20/`ACTIVE`; `pageSize=101` → 400.
- `VenueMapper` (`componentModel = "spring"`, `uses`/`imports` `MapperSupport`, `unmappedTargetPolicy = ERROR`): `toResponse(VenueDetailView, List<VenueImage>)`, `toCreatedResponse(Venue, String creatorName)`, `toSummary(VenueSummaryView)` (`distanceKm` HALF_UP 2 chữ số), `toImage(VenueImage)` (không map `publicId`), `toReport(VenueReport)`, `toAdminReport(AdminVenueReportView)` (che tên), `toSignatureResponse(UploadSignature)`.

## 8. Cấu hình (nguồn duy nhất cho giá trị) & Dependencies

```properties
arenaflow.cloudinary.cloud-name=${CLOUDINARY_CLOUD_NAME:}
arenaflow.cloudinary.api-key=${CLOUDINARY_API_KEY:}
arenaflow.cloudinary.api-secret=${CLOUDINARY_API_SECRET:}
arenaflow.cloudinary.folder=${CLOUDINARY_FOLDER:}
arenaflow.venue.stale-alert.cron=${VENUE_STALE_ALERT_CRON:0 0 3 * * *}
arenaflow.venue.stale-alert.stale-days=180
arenaflow.venue.stale-alert.batch-size=500
arenaflow.venue.stale-alert.max-batches-per-run=20
arenaflow.venue.image-cleanup.cron=${VENUE_IMAGE_CLEANUP_CRON:-}
arenaflow.venue.image-cleanup.grace=24h
arenaflow.venue.image-cleanup.confirm-window=6h
arenaflow.venue.image-cleanup.clock-skew=5m
arenaflow.venue.image-cleanup.page-size=500
arenaflow.venue.image-cleanup.max-admin-calls-per-run=100
arenaflow.venue.image-cleanup.connect-timeout=5s
arenaflow.venue.image-cleanup.read-timeout=30s
```

| Tham số | Lý do giá trị |
|---|---|
| `folder` | **Bắt buộc, riêng mỗi môi trường** (vd `arenaflow-dev`, `arenaflow-prod`); không mặc định để không vô tình dùng chung thư mục giữa các môi trường cùng cloud |
| `stale-alert.cron` 03:00 | Giờ thấp điểm |
| `image-cleanup.cron` mặc định `-` (tắt) | Chỉ bật tường minh ở môi trường có thư mục riêng (fail-fast §4.12); gợi ý khi bật: `0 30 3 * * *` (thấp điểm, sau job 180 ngày) |
| `confirm-window` 6h | Đủ cho client retry 2.5 sau mạng chập chờn; chữ ký upload đã hết hạn sau 1h |
| `grace` 24h | > `confirm-window` + 1h (hạn chữ ký), dư lớn cho lệch đồng hồ → asset bị xoá chắc chắn không còn đường xác nhận |
| `clock-skew` 5m | `signedAt` ở tương lai quá mức này → `VENUE_005`; job coi là sai mẫu |
| `page-size` 500, lô xoá 100 | Tối đa theo tài liệu Admin API → ít request nhất |
| `max-admin-calls-per-run` 100 | Gói free 500 request/giờ; chừa ≥400 cho việc khác |

`.env.example`: thêm `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `CLOUDINARY_FOLDER` (placeholder, ghi chú "khác nhau mỗi môi trường"). **NEVER** commit giá trị thật.

Không thêm Maven dependency: `MessageDigest`/`Mac`/`HexFormat`/`Base64` của JDK; scheduling trong `spring-context`; `RestClient` của spring-web. Không dùng SDK `cloudinary-*` (chỉ cần ký + 2 lời gọi Admin API).

## 9. Security

- Không endpoint public mới; không đổi `SecurityConfig`.
- Chỉ lưu URL tự dựng từ `publicId` đã xác minh (chữ ký response + mẫu + người xin chữ ký + hạn).
- `apiSecret`/`tagKey` chỉ ở `CloudinaryImageUploadSigner`/`CloudinaryImageAssetAdmin`; response 2.4 chỉ có `apiKey`; không log header Basic Auth.
- Quyền: thêm ảnh/đổi ảnh chính mọi user trên địa điểm `ACTIVE` (BR-V07); sửa địa điểm/xoá ảnh người tạo hoặc Admin (BR-V06); địa điểm `INACTIVE` chỉ Admin ghi; Admin không report (UC-V03). Audit log §4.13.
- Ảnh đã xoá còn truy cập được qua URL tới khi job dọn chạy và cache CDN hết hạn (`Processing_Flow.md` §9).

## 10. Lệch convention / chuẩn có chủ đích

| Quy ước | Thiết kế | Lý do | Trạng thái |
|---|---|---|---|
| `api-convention.md` §12, `convention-backend.md` §10: soft delete | Hard-delete `venue_image` (2.13) | Spec không có khôi phục ảnh; không bảng nào tham chiếu; job dọn xoá asset nên khôi phục dòng DB cũng không lấy lại ảnh; soft-delete buộc mọi truy vấn ảnh + 2 unique index thêm `deleted_at IS NULL` | Đã được user duyệt 2026-11-09 |
| `non-functional-standards.md` §3: không đọc lại dữ liệu đã nạp | 2.10–2.12 nạp entity `Venue` rồi đọc `VenueDetailView` cho response | Response cần tên người tạo (join `"user"`) và dữ liệu sau flush; dựng từ entity vẫn cần 1 SELECT user → cùng số truy vấn; dùng chung mapper với 2.3 nên response nhất quán (tiền lệ USER-001 `PATCH /profile`) | Ghi nhận trong thiết kế |
| ADR-0003: optimistic lock + retry, không khoá trước | Ghi ảnh (2.5, 2.6, 2.13) khoá dòng `venue` bằng `UPDATE` không so version, không retrier | Kiểm `@Version` lúc commit để hai request cùng đặt ảnh chính đụng unique index → 500 (`Processing_Flow.md` §5) | Ghi vào ADR-0003 Sửa đổi 2026-11-09 |
| `convention-backend.md` §9: `@Transactional(readOnly)` mức class | Transaction theo method | Kế thừa USER-001 §4.6 | Đã có tiền lệ |

## 11. Tài liệu cần đồng bộ khi implement

Sau khi mọi phase code xong + test PASS (CLAUDE.md §8):

| Tài liệu | Nội dung | Nhánh |
|---|---|---|
| `docs/database/schema.dbml`, `schema-design.md` §5 | Cột/index/CHECK V5 | `feature/VENUE-001-*` |
| `docs/database/data-model.md` §3.5 | Loại sân enum, quyền sửa/xoá ảnh, trạng thái + chống trùng report, hard-delete ảnh, `public_id` | `feature/VENUE-001-*` |
| `docs/agent/api-convention.md` §5 · §6 | 13 endpoint (gồm `DELETE` trả 200 — B3) + domain `VENUE` | `chore/agent-framework-*` (qua `workflow-adapt`) |
| `docs/agent/convention-backend.md` §1 | Package `application/event/`, `application/support/storage/`, `api/scheduler/`; `@CurrentUser CurrentActor` | `chore/agent-framework-*` |
| `docs/agent/design/USER-001/Component_Class_Design.md` §9.4, `Processing_Flow.md` §5 | Resolver hỗ trợ `CurrentActor`; retrier có overload mã lỗi | `feature/VENUE-001-*` (+ `USER-001/CHANGELOG.md`) |
| `docs/agent/decisions/0003-optimistic-locking-strategy.md` | Mục Sửa đổi 2026-11-09 (đã viết sẵn trong thay đổi thiết kế này) — kiểm lại khớp code | Cùng nhánh thiết kế |
| `docs/agent/decisions/0009-*.md` | `Proposed` → `Accepted` khi user duyệt qua PR | Theo PR |

## 12. Danh sách [cần xác nhận]

Đánh số toàn thiết kế: 1–7 ở `API_Spec.md` §6, 8–15 ở đây, 16–18 ở `Processing_Flow.md` §10.

| # | Nội dung | Loại |
|---|---|---|
| 8 | Tìm `q` bằng `ILIKE '%…%'` không có index phù hợp; nhanh hơn cần `pg_trgm` + GIN (kiểm extension Neon) | Không chặn |
| 9 | **Cloudinary upload chưa chạy thử:** cách nối chuỗi chữ ký response; `allowed_formats` và `overwrite=false` có được ký và có tác dụng chặn không; ký tự `-`/`_` trong `public_id`; giới hạn kích thước file (chữ ký không chặn được); hành vi khi vượt 25 credit/tháng; chế độ "dynamic folders" với `/` trong `public_id` | **Chặn code** — spike trên cloud dev trước khi viết `CloudinaryImageUploadSigner` |
| 10 | ADR riêng cho quy ước domain event (VENUE-001 đặt tiền lệ) trước MATCH-001 / NOTIFICATION-001 | Không chặn |
| 11 | Spring Data chạy native `UPDATE … RETURNING` không `@Modifying` (§5.2) | **Chặn code** job 180 ngày — kiểm bằng integration test đầu tiên; có phương án dự phòng |
| 12 | **Admin API Cloudinary** (đã tra tài liệu, chưa chạy thử): list `prefix` ≤500 + `next_cursor`; xoá ≤100 `public_ids[]` (định dạng tham số mảng khi gọi HTTP thuần), map `deleted` + `partial`; gói free 500 request/giờ, vượt → 420, header `X-FeatureRateLimit-*`; `invalidate` CDN mặc định tắt; `prefix` với "dynamic folders" | **Chặn code** job dọn — spike trên cloud dev |
| 13 | Grace period user đã xoá: 2.4–2.7, 2.12, 2.13 chỉ dựa JWT → tối đa 1h sau xoá tài khoản vẫn ghi được (mức USER-001 `Processing_Flow.md` §9) | Không chặn |
| 14 | Hai scheduler giả định một instance (chưa ShedLock/Redis): job 180 ngày không phát trùng event nhưng truy vấn thừa; job dọn tốn gấp đôi hạn mức Admin API (xoá lặp vô hại) | Không chặn (chốt khi triển khai nhiều instance) |
| 15 | Role admin lấy từ JWT cho 2.4–2.7, 2.12, 2.13: đổi role trong DB có hiệu lực sau tối đa 1h | Không chặn |
