# Component & Class Design — Ngày giờ theo giờ Việt Nam, không kèm múi giờ

| Mục | Giá trị |
|---|---|
| Mã chức năng | `SYSTEM-002` |
| Phạm vi tầng | Backend (Spring Boot REST API). Frontend: ngoài phạm vi repo này |
| Repo | ArenaFlow-be — Spring Boot 4.0.9, Jackson 3 (`tools.jackson`), Hibernate 7.2, PostgreSQL 18 (Neon), Flyway |
| Quy ước áp dụng | `convention-backend.md` (module, entity, `@PrePersist`), `api-convention.md` — **cả hai đang ghi `OffsetDateTime` / `timestamptz` / ví dụ `Z`; phải sửa theo §2 trước khi code** (§9 (a)); [ADR-0017](../../decisions/0017-vietnam-local-datetime.md) (Proposed) |
| Spec nghiệp vụ | `docs/agent/business-spec/SYSTEM-001/` (v1.13) — BC-M05 |
| Ràng buộc dữ liệu | Schema V1–V5 (V5 = VENUE-001, PR #75 — đã merge `61aa100`) · migration mới `V6__vietnam_local_datetime.sql` (§7) |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-21 · v1.0 (baseline) |

## 1. Tổng quan

Đổi mọi thời điểm của hệ thống từ "thời điểm tuyệt đối, trả UTC" sang **giờ Việt Nam không kèm múi giờ** (BC-M05):

| Tầng | Trước | Sau |
|---|---|---|
| DB | `TIMESTAMPTZ`, `DEFAULT NOW()` | `TIMESTAMP` (= `timestamp without time zone`), `DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')`; `DATE` giữ nguyên |
| Entity / tham số repository | `OffsetDateTime` | `LocalDateTime`; `LocalDate` giữ nguyên |
| Projection native | `Instant` | `LocalDateTime` |
| Mapper | `MapperSupport.toUtc(Instant)` → `OffsetDateTime` UTC | Gán thẳng `LocalDateTime` (bỏ `toUtc`) |
| DTO / event / `ErrorResponse` | `OffsetDateTime` | `LocalDateTime` |
| JSON | `"…Z"` hoặc `"…+07:00"` | `"2026-11-20T19:00:00"` — mặc định Jackson cho `LocalDateTime` (không cấu hình định dạng riêng) |
| Nguồn "bây giờ" | `OffsetDateTime.now()` (theo JVM), `Clock.systemUTC()` | `Clock` bean zone `Asia/Ho_Chi_Minh` (service) / `AppTime.now()` (nơi không inject được) — §2.2 |

Không endpoint mới, không mã lỗi mới, không đổi số truy vấn (`Processing_Flow.md` §2). Hợp đồng FE: `API_Spec.md`.

### 1.1. Điều kiện tiên quyết và thứ tự migration

- **V6 thuộc SYSTEM-002** (user chốt). Các thiết kế chưa code đã giữ chỗ V6–V11 → **dời +1** (đã sửa trong tài liệu các mã, §9; số thực chốt lại khi bắt đầu code từng mã):

| Mã | Số cũ | Số mới |
|---|---|---|
| MATCH-001 `…match_flow_a_lifecycle.sql` | V6 | **V7** |
| MATCH-002 `…match_flow_b_pairing.sql` | V7 | **V8** |
| REPUTATION-001 `…reputation_rating_report_dispute.sql` | V8 | **V9** |
| MERCENARY-001 `…mercenary_recruitment_chat_reputation.sql` | V9 | **V10** |
| NOTIFICATION-001 `…notification.sql` | V10 | **V11** |
| USER-003 `…user_name_search.sql` | V11 | **V12** |

- **DB dev dùng chung:** áp V6 lên Neon dev làm mọi nhánh còn entity `OffsetDateTime` (code trước SYSTEM-002) **không khởi động được** trên DB dev (`ddl-auto=validate` báo sai kiểu cột) cho tới khi rebase lên `main` có SYSTEM-002. Vì vậy: code + integration test SYSTEM-002 chạy trên **Neon branch riêng** (xin phép user mỗi lần tạo/xoá branch); DB dev chung chỉ nhận V6 **sau khi PR SYSTEM-002 merge**, có báo trước các nhánh đang mở — `Processing_Flow.md` §4.1.

## 2. Quy ước thời gian (nguồn cho BE; chuyển vào `convention-backend.md` — §9)

### 2.1. Kiểu dữ liệu

| Loại giá trị | Java | DB | JSON |
|---|---|---|---|
| Thời điểm nghiệp vụ / audit (`…At`, giờ trận, hạn) | `LocalDateTime` (giờ Việt Nam) | `TIMESTAMP` | `"2026-11-20T19:00:00[.ffffff]"` |
| Ngày lịch (ngày sinh) | `LocalDate` | `DATE` | `"1995-05-15"` |
| Khoảng thời gian (TTL, `expiresIn`, ngưỡng cấu hình) | `Duration` / số giây | — | integer |
| Đồng hồ đơn điệu (rate limit trong bộ nhớ) | `System.nanoTime()` | — | — |

**NEVER** dùng `OffsetDateTime`, `ZonedDateTime`, `Instant`, `java.util.Date`, `java.sql.Timestamp` cho giá trị lưu DB hoặc trả JSON. **Ngoại lệ epoch** — giữ `Instant`/epoch nội bộ, không lưu DB, không trả JSON dạng datetime:

| Chỗ | Giữ | Lý do |
|---|---|---|
| `JwtTokenService` (`issueToken`, `parseRefreshToken`) | `Instant` → `Date` cho claim `iat`/`exp` | Chuẩn JWT là giây Unix; thư viện Nimbus nhận `Date` |
| `CloudinaryImageUploadSigner` (`timestamp` = epoch giây, `signedAt` trong `public_id`), `SignedPublicId.signedAt`, `VenueImageServiceImpl.isWithinConfirmationWindow`, `VenueImageCleanupServiceImpl.expiredCandidates` | `Instant` từ `clock.instant()` | Tham số ký của Cloudinary là epoch giây; so tuổi asset là phép trừ thời điểm tuyệt đối, không chạm DB |
| `AssetAdminRateLimitedException.resetAt` | `String` (header Cloudinary nguyên văn) | Chỉ ghi log |
| `UuidGenerator(VERSION_7)` / `uuidv7()` | epoch ms bên trong UUID (ADR-0008) | Không phải giá trị thời gian của nghiệp vụ |

### 2.2. Nguồn "bây giờ"

- **`common/time/AppTime`** (mới, `final`, constructor private — chạy `reuse-scan` trước khi tạo):

```java
public final class AppTime {
    public static final String ZONE_ID = "Asia/Ho_Chi_Minh";   // hằng compile-time: dùng được trong @Scheduled(zone = …)
    public static final ZoneId ZONE = ZoneId.of(ZONE_ID);
    public static LocalDateTime now()           // LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.MICROS)
    public static LocalDate today()             // LocalDate.now(ZONE)
}
```

- **`api/config/ClockConfig`** (mới — chuyển lên từ MATCH-001 §8; MATCH-001 dùng lại, không tạo lần hai):

```java
@Bean Clock clock() { return Clock.tick(Clock.system(AppTime.ZONE), Duration.ofNanos(1_000)); } // độ phân giải micro giây
```

| Nơi | Lấy "bây giờ" bằng |
|---|---|
| Service, job, component có DI | Inject `Clock` → `LocalDateTime.now(clock)` (thời điểm) · `clock.instant()` (chỉ cho ngoại lệ §2.1) |
| Entity `@PrePersist`/`@PreUpdate`, record factory (`ErrorResponse.from`), `GlobalExceptionHandler`, `ConstraintValidator` | `AppTime.now()` / `AppTime.today()` |
| Test | `Clock.fixed(instant, AppTime.ZONE)` |

Lý do: (1) zone tường minh → code không phụ thuộc `-Duser.timezone` (IDE, runner, container khác); JVM vẫn chạy `-Duser.timezone=Asia/Ho_Chi_Minh` (hub `CLAUDE.md` §8; Dockerfile của nhánh deploy `chore/docker-render-deploy` — Render dev — đã đặt `TZ` + `-Duser.timezone`) cho log và phiên JDBC. (2) `Clock` cố định được → test biên mốc (24 giờ, 180 ngày…) không phụ thuộc đồng hồ thật — tiền lệ `VenueAlertServiceImpl`, `VenueImageCleanupServiceImpl`. (3) Cắt về micro giây = độ chính xác của `TIMESTAMP` PostgreSQL → giá trị trong response sau khi ghi trùng giá trị đọc lại từ DB.

**Bẫy phải test:** `LocalDateTime.now(clock)` dùng **zone của `clock`**. Mọi `Clock.systemUTC()` (constructor mặc định của `VenueAlertServiceImpl`, `VenueImageCleanupServiceImpl`, `VenueImageServiceImpl`, `CloudinaryImageUploadSigner`) phải đổi sang `Clock` bean / `Clock.system(AppTime.ZONE)` — để `systemUTC` thì mốc lệch 7 giờ. **NEVER** `LocalDateTime.now()` / `LocalDate.now()` không tham số trong `src/main`.

### 2.3. SQL

- **NEVER** dùng `now()`, `CURRENT_TIMESTAMP`, `LOCALTIMESTAMP`, `CURRENT_DATE` trần trong native query / migration để ghi hoặc so sánh cột thời gian: với cột `TIMESTAMP`, PostgreSQL đổi `timestamptz` → `timestamp` theo **TimeZone của phiên** (Neon dev mặc định `GMT` — user kiểm 2026-11-19) → lệch 7 giờ.
  - Native query: Java truyền `:now` (đã làm ở mọi câu hiện có — §6.3; không câu native nào trong code `main` hoặc nhánh VENUE-001 dùng `now()`, grep 2026-11-21).
  - DEFAULT / migration cần "bây giờ" ở DB: `(now() AT TIME ZONE 'Asia/Ho_Chi_Minh')` — không phụ thuộc TimeZone phiên.
- [Inference — theo mã nguồn pgjdbc, chưa kiểm trên Neon pooler] pgjdbc gửi tham số khởi động `TimeZone` = timezone JVM, nên phiên của ứng dụng chạy `Asia/Ho_Chi_Minh`; phiên khác (console Neon, MCP, `psql`) vẫn `GMT`. Thiết kế **không dựa** vào điều này (quy tắc trên); test §8 #4 ghi nhận giá trị thực tế.
- Không đặt `ALTER DATABASE … SET timezone` và không đặt `hibernate.jdbc.time_zone`: `LocalDateTime` ↔ `TIMESTAMP` bind không đổi múi giờ.

### 2.4. Lịch chạy job

- Mọi `@Scheduled(cron = …)` khai `zone = AppTime.ZONE_ID` (VENUE-001 đang ghi chuỗi `"Asia/Ho_Chi_Minh"` → đổi sang hằng). `fixedDelay`/`fixedRate` không cần zone.
- Mốc nghiệp vụ = phép cộng/trừ trên `LocalDateTime.now(clock)` (`minusDays(180)`, `plusHours(2)`, `minusDays(90)`, `minusDays(30)`…). Việt Nam không có giờ mùa hè → cộng/trừ giờ địa phương bằng cộng/trừ thời lượng thật (theo dõi ở ADR-0017).

### 2.5. JSON

- Response: không cấu hình — Jackson 3 ghi `LocalDateTime` theo ISO-8601 không offset, `LocalDate` `YYYY-MM-DD` [Inference — theo mặc định Spring Boot / Jackson 3; test §8 #5 khẳng định]. Phần lẻ giây: giây luôn ghi; phần lẻ 1–6 chữ số, bỏ số 0 cuối, không có khi giây tròn [Inference — theo `DateTimeFormatter.ISO_LOCAL_DATE_TIME`; `API_Spec.md` §1.1 hứa với FE như vậy, test §8 #5 khẳng định].
- Request body: chuỗi kèm `Z`/offset → `400 VALIDATION_ERROR` (`API_Spec.md` §1.1). [Unverified] Jackson 2.x ở chế độ lenient **bỏ `Z` rồi đọc như giờ địa phương** (lệch 7 giờ âm thầm), offset `+07:00` thì lỗi parse. Developer viết test trước (§8 #6); nếu Jackson 3 mặc định vẫn nhận `Z` → thêm `api/config/JsonTimeConfig` (một `JsonMapperBuilderCustomizer` đặt `withConfigOverride(LocalDateTime.class, …JsonFormat.Value.forLeniency(false))`) — chỉ thay đổi độ chặt, không đổi định dạng ghi.
- Query param `LocalDateTime` (MATCH-001 `startsAtFrom`/`startsAtTo` — chưa code): khai `@DateTimeFormat(iso = ISO.DATE_TIME)` **và** từ chối chuỗi có offset (vd constraint/converter dùng `DateTimeFormatter.ISO_LOCAL_DATE_TIME`) — [Unverified] `ISO.DATE_TIME` của Spring parse `ISO_DATE_TIME` (nhận offset tuỳ chọn rồi bỏ). Áp khi code MATCH-001; test như §8 #6.
- Lỗi parse `datetime` ở **query** → `MethodArgumentTypeMismatchException` → handler hiện có trả `field` = tên tham số (không đổi). Ở **body** → `HttpMessageNotReadableException`: handler hiện trả `field = "body"` cho mọi lỗi đọc JSON → **sửa** để trả đường dẫn field (§6.1), áp cho mọi lỗi đọc JSON body, không riêng thời gian.

## 3. Package / file thay đổi

```
common/  time/AppTime.java                                   (mới)
         exception/ErrorResponse.java                        (sửa — §6.1)
domain/  entity/**  (22 entity — §5.1) · projection/** (§5.2) · repository/** (§5.3)
application/ mapper/MapperSupport.java, TeamMapper, MembershipMapper, ProfileMapper, VenueMapper (§6.2)
         dto/response/** (§6.4) · event/venue/VenueAdminAlertEvent (§6.5)
         service/impl/** , support/storage/**, validation/MinAgeValidator (§6.3)
api/     config/ClockConfig.java (mới) · config/JsonTimeConfig.java (mới, có điều kiện — §2.5)
         config/GlobalExceptionHandler.java (sửa — §6.1: AppTime.now(), field của lỗi đọc JSON body)
         scheduler/Venue*Scheduler (zone = AppTime.ZONE_ID)
         resources/db/migration/V6__vietnam_local_datetime.sql (mới — §7)
```

## 4. Controller

Không đổi. Controller không nhận tham số thời gian ở code hiện có (request DTO chỉ có `LocalDate dateOfBirth` — giữ nguyên).

## 5. Domain

### 5.1. Entity — mọi field thời điểm `OffsetDateTime` → `LocalDateTime`; callback dùng `AppTime.now()`

| Entity | Field | Callback |
|---|---|---|
| `user/User` | `createdAt`, `updatedAt`, `deletedAt` | `@PrePersist` (`createdAt`, `updatedAt`), `@PreUpdate` (`updatedAt`) |
| `user/OAuthAccount`, `user/AdministrativeUnit` | `createdAt` | `@PrePersist` |
| `team/Team` | `createdAt`, `updatedAt`, `disbandedAt` | `@PrePersist`, `@PreUpdate` |
| `team/TeamMember` | `invitedAt`, `joinedAt`, `leftAt` | `@PrePersist` (`invitedAt` nếu null) |
| `team/TeamCaptainHistory` | `transferredAt` | `@PrePersist` (nếu null) |
| `match/Sport`, `match/MatchVenueOption`, `match/CostCustomItem` | `createdAt` | `@PrePersist` |
| `match/Match` | `matchStartTime`, `matchEndTime`, `expiresAt`, `confirmedMatchTime`, `cancelledAt`, `createdAt`, `updatedAt` | `@PrePersist`, `@PreUpdate` |
| `match/MatchTimeOption` | `proposedStartTime`, `proposedEndTime`, `createdAt` | `@PrePersist` |
| `match/MatchRequest` | `requestedAt`, `respondedAt`, `withdrawnAt` | `@PrePersist` (`requestedAt` nếu null) |
| `match/Pairing` | `authorConfirmedAt`, `interestedConfirmedAt`, `finalConfirmedAt`, `createdAt` | `@PrePersist` |
| `venue/Venue` (bản V5) | `lastSelectedAt`, `adminVerifiedInactiveAt`, `staleAlertedAt`, `closureAlertedAt`, `createdAt`, `updatedAt` | `@PrePersist`, `@PreUpdate` |
| `venue/VenueImage` | `createdAt` | `@PrePersist` |
| `venue/VenueReport` (bản V5) | `handledAt`, `createdAt` | `@PrePersist` |
| `rating/Rating` | `createdAt` | `@PrePersist` |
| `rating/Report` | `adminReviewedAt`, `createdAt` | `@PrePersist` |
| `mercenary/MercenaryRecruitment` | `createdAt`, `updatedAt` | `@PrePersist`, `@PreUpdate` |
| `mercenary/MercenaryApplication` | `appliedAt`, `respondedAt`, `withdrawnAt` | `@PrePersist` (`appliedAt` nếu null) |
| `chat/ChatMessage`, `chat/Notification` | `createdAt` | `@PrePersist` |

Không đổi `@Column` (Hibernate suy kiểu `TIMESTAMP` từ `LocalDateTime`; `ddl-auto=validate` bắt lệch). Entity của bảng mà MATCH-001/NOTIFICATION-001… sẽ bỏ/thay (vd `ChatMessage`) vẫn đổi để `validate` qua.

### 5.2. Projection — getter `Instant` → `LocalDateTime`

`CaptainHistoryView.getTransferredAt`, `MemberHistoryView.getLeftAt`, `MemberView.getJoinedAt`, `ProfileView.getCreatedAt/getUpdatedAt`, `TeamDetailView.getCreatedAt/getUpdatedAt/getDisbandedAt`, `TeamSummaryView.getCreatedAt`; VENUE-001: `AdminVenueReportView.getHandledAt/getCreatedAt`, `VenueDetailView.getLastSelectedAt/getAdminVerifiedInactiveAt/getCreatedAt/getUpdatedAt`, `VenueSummaryView.getLastSelectedAt`. Quy tắc: **mọi** getter thời điểm của projection là `LocalDateTime`.

[Unverified] Hibernate 7.2 trả cột `TIMESTAMP` của native query dạng `LocalDateTime` (hoặc `java.sql.Timestamp` được Spring Data chuyển sang `LocalDateTime`) — điểm nội bộ, developer kiểm bằng test §8 #4; lệch → đổi getter theo kiểu thực tế và chuyển trong mapper.

### 5.3. Repository — tham số `OffsetDateTime` → `LocalDateTime` (câu SQL giữ nguyên)

| Repository | Method |
|---|---|
| `UserRepository` | `softDelete(id, now)` |
| `TeamMemberRepository` | `disbandRemaining(teamId, now)`, `leaveAndRejectAllOpen(userId, now)` |
| `VenueRepository` (V5) | `markClosureAlerted(venueId, now)`, `markSelected(ids, selectedAt)`, `markStaleAlerted(threshold, now, batchLimit)` |
| `VenueReportRepository` (V5) | `resolveIfPending(…, now)`, `verifyPendingClosureReports(…, now)` |

## 6. Application / API

### 6.1. Lỗi

- `common/exception/ErrorResponse.timestamp`: `LocalDateTime`; `from(…)` dùng `AppTime.now()`.
- `api/config/GlobalExceptionHandler.validationResponse`: `OffsetDateTime.now()` → `AppTime.now()`. `SecurityErrorWriter` đi qua `ErrorResponse.from` — không sửa riêng.
- `GlobalExceptionHandler.handleMessageNotReadable` (`HttpMessageNotReadableException`) — **sửa** (hiện trả `field = "body"` cho mọi lỗi đọc JSON): lấy đường dẫn field từ cause Jackson 3 rồi mới fallback.

```java
// Đường dẫn field từ lỗi Jackson; không xác định được (JSON sai cú pháp, thiếu body) → "body".
private static String bodyField(HttpMessageNotReadableException exception)
// Duyệt chuỗi cause từ exception.getCause() (cause, cause.getCause(), …); lấy tools.jackson.databind.exc.MismatchedInputException
// ĐẦU TIÊN gặp (m). KHÔNG dùng getMostSpecificCause(): cause sâu nhất của lỗi ngày giờ / UUID là DateTimeParseException /
// IllegalArgumentException, không mang path → sẽ rơi về "body".
//   → m.getPath(): tên field nối bằng ".", phần tử mảng dạng "[i]" — vd "startsAt", "timeOptions[0].startsAt", "dateOfBirth"
//   → không có MismatchedInputException trong chuỗi / path rỗng (vd lỗi cú pháp) → "body"
```

  - [Unverified] tên lớp / method Jackson 3 (`MismatchedInputException#getPath`, gồm `InvalidFormatException` khi sai định dạng ngày giờ) **và** vị trí của nó trong chuỗi cause: [Inference — theo Jackson 2.x `JSR310DeserializerBase._handleDateTimeException` / `FromStringDeserializer`; Jackson 3 chưa kiểm] lỗi parse ngày giờ / UUID bọc `DateTimeParseException` bên dưới `InvalidFormatException`, nên phải lấy `MismatchedInputException` đầu tiên chứ không lấy cause sâu nhất. Bằng chứng: test §8 #6 (a) (`startsAt`) và (b) (`dateOfBirth`) — viết trước; lệch → dùng lớp cha đúng của Jackson 3 có `getPath()`.
  - Áp cho **mọi** lỗi đọc JSON body (sai kiểu số/enum/UUID/ngày giờ, ngày giờ kèm offset), không riêng thời gian. `message` giữ `"Nội dung request không hợp lệ"`; không lộ chi tiết parser.
  - FE thấy được: `validationErrors[0].field` đổi từ `"body"` sang tên field (vd USER-001 2.6 `dateOfBirth` sai định dạng → `"dateOfBirth"`) — ghi ở CHANGELOG USER-001 v5.0 và `USER-001/API_Spec.md` §1.2.

### 6.2. Mapper

- `MapperSupport.toUtc(Instant)`: **xoá** (không còn nguồn `Instant`). MapStruct gán `LocalDateTime` → `LocalDateTime` trực tiếp.
- `VenueMapper`: bỏ mọi `expression = "java(MapperSupport.toUtc(…))"` (8 mapping) — để MapStruct tự gán hoặc `source = …`.
- `TeamMapper`, `MembershipMapper`, `ProfileMapper`: không còn dùng `toUtc` ngầm (theo kiểu) — không đổi annotation khác.

### 6.3. Service / support — thay `OffsetDateTime.now()` và `Clock.systemUTC()`

| Lớp | Hiện tại | Sau |
|---|---|---|
| `ProfileServiceImpl` (`deleteAccount`), `TeamServiceImpl` (3 chỗ), `TeamMembershipServiceImpl` (2 chỗ) | `OffsetDateTime.now()` | Inject `Clock`; `LocalDateTime.now(clock)` |
| `AdminVenueServiceImpl` (2), `VenueServiceImpl` (1) | `OffsetDateTime.now()` | như trên |
| `VenueAlertServiceImpl` | `OffsetDateTime.ofInstant(clock.instant(), UTC)`; mặc định `Clock.systemUTC()` | `LocalDateTime.now(clock)`; `threshold = now.minusDays(staleDays)`; `Clock` bean |
| `VenueSelectionService.markSelected(ids, selectedAt)` | `OffsetDateTime` | `LocalDateTime` (caller MATCH-001/MERCENARY-001 truyền `now` của lần thử) |
| `CloudinaryImageUploadSigner.signUpload` | `expiresAt = OffsetDateTime.ofInstant(now + TTL, UTC)` | `LocalDateTime.ofInstant(now.plusSeconds(TTL), AppTime.ZONE)`; `timestamp` epoch giữ nguyên; `Clock` bean |
| `UploadSignature.expiresAt`, `VenueImageUploadSignatureResponse.expiresAt` | `OffsetDateTime` | `LocalDateTime` |
| `VenueImageServiceImpl`, `VenueImageCleanupServiceImpl` | `clock.instant()`; mặc định `Clock.systemUTC()` | Giữ `Instant` (ngoại lệ §2.1); đổi mặc định sang `Clock` bean |
| `MinAgeValidator` | `LocalDate.now()` | `AppTime.today()` |
| `JwtTokenService` | `Instant.now()` | Giữ (ngoại lệ §2.1) — có thể đổi sang `clock.instant()` cho test, không bắt buộc |

### 6.4. Response DTO — `OffsetDateTime` → `LocalDateTime`

USER-001/002: `AdminTeamResponse`, `AdminTeamSummaryResponse`, `CaptainHistoryResponse`, `CaptainTransferResponse`, `InvitationResponse`, `MemberHistoryResponse`, `MemberResponse`, `ProfileResponse`, `TeamResponse`, `TeamSummaryResponse`. VENUE-001: `AdminVenueReportResponse`, `AdminVenueResponse`, `VenueImageResponse`, `VenueImageUploadSignatureResponse`, `VenueReportResponse`, `VenueResponse`, `VenueSummaryResponse`. Request DTO: không đổi (chỉ `LocalDate`).

### 6.5. Domain event

- `VenueAdminAlertEvent.triggeredAt` (PR #75 đã chuyển event sang `application/event/venue/`): `LocalDateTime` (giờ Việt Nam).
- Quy ước ADR-0011 quy tắc 2 (`occurredAt`, UTC) → `occurredAt` là `LocalDateTime` giờ Việt Nam — sửa đổi đề xuất ở ADR-0017; thiết kế MATCH-001… đã đổi kiểu (§9).

### 6.6. Cấu hình

`application.properties`: không đổi. Không thêm Maven dependency.

## 7. Schema — migration `V6__vietnam_local_datetime.sql`

**Quy tắc viết:**
1. Mỗi bảng **một** câu `ALTER TABLE` gồm mọi cột của bảng đó: CHECK so hai cột (`match_end_time > match_start_time`, `expires_at <= match_start_time`, `joined_at >= invited_at`, `left_at >= …`) được kiểm **sau** khi mọi cột cùng đổi kiểu. Tách câu → giữa chừng so `timestamp` với `timestamptz` theo TimeZone phiên → CHECK có thể sai.
2. Cột có DEFAULT: `DROP DEFAULT` → `TYPE … USING` → `SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')` trong cùng câu (mẫu của tài liệu PostgreSQL `ALTER TABLE`).
3. `USING col AT TIME ZONE 'Asia/Ho_Chi_Minh'`: `timestamptz AT TIME ZONE zone` → giờ địa phương của zone, **không** phụ thuộc TimeZone phiên → giữ đúng thời điểm (12:00 UTC → 19:00).
4. Không đổi V1–V5 (checksum Flyway). Index trên cột thời gian (`idx_match_expiry`, `idx_pairing_race`, `idx_venue_last_selected`, `idx_notification_retention`…) được PostgreSQL dựng lại tự động.

Mẫu (hai bảng; các bảng khác cùng mẫu theo bảng dưới):

```sql
-- Mọi thời điểm chuyển sang giờ Việt Nam không kèm múi giờ; giữ đúng thời điểm đã lưu.
ALTER TABLE "user"
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN deleted_at TYPE TIMESTAMP USING deleted_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE match
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN match_start_time TYPE TIMESTAMP USING match_start_time AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN match_end_time TYPE TIMESTAMP USING match_end_time AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN expires_at TYPE TIMESTAMP USING expires_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN confirmed_match_time TYPE TIMESTAMP USING confirmed_match_time AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN cancelled_at TYPE TIMESTAMP USING cancelled_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');
```

**Đủ 51 cột / 22 bảng** (`team_tag` bị V2 xoá — không có). Nguồn: grep `TIMESTAMPTZ` V1 (48) + V4 (1) + V5 (3) − `team_tag.created_at`.

| # | Bảng | Cột → `TIMESTAMP` (nguồn) | Cột có DEFAULT (đổi) |
|---|---|---|---|
| 1 | `administrative_unit` | `created_at` (V1) | `created_at` |
| 2 | `"user"` | `created_at`, `updated_at`, `deleted_at` (V1) | `created_at`, `updated_at` |
| 3 | `oauth_account` | `created_at` (V1) | `created_at` |
| 4 | `team` | `created_at`, `updated_at` (V1), `disbanded_at` (V4) | `created_at`, `updated_at` |
| 5 | `team_member` | `invited_at`, `joined_at`, `left_at` (V1) | `invited_at` |
| 6 | `team_captain_history` | `transferred_at` (V1) | `transferred_at` |
| 7 | `sport` | `created_at` (V1) | `created_at` |
| 8 | `venue` | `last_selected_at`, `admin_verified_inactive_at`, `created_at`, `updated_at` (V1), `stale_alerted_at`, `closure_alerted_at` (V5) | `created_at`, `updated_at` |
| 9 | `venue_image` | `created_at` (V1) | `created_at` |
| 10 | `venue_report` | `created_at` (V1), `handled_at` (V5) | `created_at` |
| 11 | `match` | `match_start_time`, `match_end_time`, `expires_at`, `confirmed_match_time`, `cancelled_at`, `created_at`, `updated_at` (V1) | `created_at`, `updated_at` |
| 12 | `match_venue_option` | `created_at` (V1) | `created_at` |
| 13 | `match_time_option` | `proposed_start_time`, `proposed_end_time`, `created_at` (V1) | `created_at` |
| 14 | `cost_custom_item` | `created_at` (V1) | `created_at` |
| 15 | `match_request` | `requested_at`, `responded_at`, `withdrawn_at` (V1) | `requested_at` |
| 16 | `pairing` | `author_confirmed_at`, `interested_confirmed_at`, `final_confirmed_at`, `created_at` (V1) | `created_at` |
| 17 | `rating` | `created_at` (V1) | `created_at` |
| 18 | `report` | `admin_reviewed_at`, `created_at` (V1) | `created_at` |
| 19 | `mercenary_recruitment` | `created_at`, `updated_at` (V1) | `created_at`, `updated_at` |
| 20 | `mercenary_application` | `applied_at`, `responded_at`, `withdrawn_at` (V1) | `applied_at` |
| 21 | `chat_message` | `created_at` (V1) | `created_at` |
| 22 | `notification` | `created_at` (V1) | `created_at` |

Tổng: 51 cột, 27 DEFAULT (V1 có 28 `DEFAULT NOW()`, trừ `team_tag.created_at` — đối chiếu file trên `main` 2026-11-21). `"user".date_of_birth` (`DATE`) không đổi.

**Migration sau V6 (mọi mã):** cột thời điểm mới khai `TIMESTAMP`, DEFAULT (nếu cần) `(now() AT TIME ZONE 'Asia/Ho_Chi_Minh')`; cast mảng `CAST(:x AS timestamp[])`. Thiết kế MATCH-001… đã sửa theo (§9).

## 8. Test (TDD — viết trước, Red đúng lý do)

**Môi trường integration test:** Neon branch riêng cho SYSTEM-002, tạo từ branch của DB dev sau khi PR #75 merge (DB đã ở V5) — xin phép user mỗi lần tạo/xoá. Chạy test bằng cách đặt `DATABASE_URL`/`DATABASE_USERNAME`/`DATABASE_PASSWORD` của branch đó **trong phiên shell** (không sửa `.env.dev`, không ghi connection string vào repo/log); unit test thuần chạy như thường. **NEVER** chạy integration test SYSTEM-002 vào DB dev chung trước khi PR merge.

| # | Loại | Kiểm |
|---|---|---|
| 1 | Unit `AppTimeTest`, `ClockConfigTest` | `AppTime.now()` cắt micro giây, zone `Asia/Ho_Chi_Minh`; bean `Clock` có zone đó và tick 1 µs |
| 2 | Integration migration `VietnamLocalDatetimeMigrationTest` (`@SpringBootTest`, `ddl-auto=none`, Neon branch riêng) | (a) `information_schema.columns WHERE table_schema = current_schema()`: 0 cột `data_type = 'timestamp with time zone'`; 51 cột liệt kê ở §7 là `timestamp without time zone`. (b) 27 `column_default` chứa `AT TIME ZONE 'Asia/Ho_Chi_Minh'`. (c) Trong transaction, `SET LOCAL TimeZone = 'GMT'`, INSERT `"user"` bỏ `created_at` → `created_at` cách `now() AT TIME ZONE 'Asia/Ho_Chi_Minh'` < 5 giây (DEFAULT đúng giờ VN dù phiên GMT) |
| 3 | Integration giữ thời điểm khi chuyển (schema tạm) | Flyway lập trình vào schema tạm `sys002_<ngẫu nhiên>`: `target=5` → INSERT `match` với `'2026-11-20 12:00:00+00'`, `team_member` với `invited_at < joined_at` → `target=6` → đọc ra `2026-11-20 19:00:00`, CHECK vẫn đúng → `DROP SCHEMA … CASCADE` trong `finally`. Điểm nội bộ, developer kiểm: role có quyền `CREATE SCHEMA` trên Neon branch [Unverified]; không có → tạo branch thứ hai từ trạng thái V5 (xin phép) hoặc bỏ test này, giữ #2 |
| 4 | Integration repository | Projection native trả `LocalDateTime` đúng giá trị đã ghi (vd `TeamRepository.findDetailById`, `VenueRepository` V5) — quyết định kiểu getter §5.2; ghi nhận `SELECT current_setting('TimeZone')` của phiên ứng dụng (log, không assert) |
| 5 | Unit JSON (`ResponseDtoJsonTest`, `SecurityErrorHandlersTest` — sửa) | `LocalDateTime.of(2026,11,20,19,0)` → `"2026-11-20T19:00:00"`; có micro giây → `"…19:00:00.123456"`; 500 ms → `"…19:00:00.5"`; 120 ms → `"…19:00:00.12"`; `ErrorResponse.timestamp` không có `Z`/offset |
| 6 | MVC test request (viết trước khi sửa handler — §6.1) | (a) Body `datetime` kèm `Z` / `+07:00` → `400 VALIDATION_ERROR`, `validationErrors[0].field` = tên field (vd `"startsAt"`, phần tử mảng `"timeOptions[0].startsAt"`); dạng không offset → nhận. Nếu Jackson 3 mặc định vẫn nhận `Z` (test đỏ) → thêm `JsonTimeConfig` (§2.5). Dùng DTO test tạm nếu chưa có request có `LocalDateTime` (code hiện tại chưa có). (b) USER-001 2.6 `dateOfBirth: "15/05/1995"` → `field = "dateOfBirth"`; field số/enum sai kiểu → tên field. (c) JSON sai cú pháp / body rỗng → `field = "body"`. Hiện không có test nào assert `"body"` (grep `main` `2787ecc`) |
| 7 | Unit service có mốc | `VenueAlertServiceImpl` với `Clock.fixed(…, AppTime.ZONE)`: ngưỡng 180 ngày tính theo giờ VN (venue chọn lúc 19:00 VN đúng 180 ngày trước → biên); `CloudinaryImageUploadSigner.expiresAt` = giờ VN của `timestamp + TTL` |
| 8 | Hồi quy | Toàn bộ test hiện có đổi `OffsetDateTime`/`Instant` fixture sang `LocalDateTime` (trừ ngoại lệ epoch §2.1); `NfsWriteQueryCountTest`, `VenueNfsQueryCountTest` — số câu SQL không đổi. **SQL trong test dùng `now()` trần** ghi cột thời gian → đổi sang `now() AT TIME ZONE 'Asia/Ho_Chi_Minh'` hoặc tham số Java (§2.3) — trên `main` `2787ecc`: `VenueControllerTest.java:239, 283, 310`, `AdminVenueControllerTest.java:31, 91`, `VenueSelectionIntegrationTest.java:59, 69`, `VenueStaleAlertRaceTest.java:72` (`now()` thường), `UserTeamMigrationTest.java:51` |

## 9. Tài liệu cần đồng bộ

Thứ tự bắt buộc: (b) PR #75 merge, rebase, sửa §9.1 — **đã xong 2026-11-21**; còn lại: thiết kế này được duyệt → (a) nhánh framework → mới bắt đầu code SYSTEM-002.

| Tài liệu | Việc | Khi nào / trạng thái (2026-11-21) |
|---|---|---|
| `docs/agent/decisions/0017-vietnam-local-datetime.md` | ADR mới `Proposed` | Đã tạo |
| ADR-0016 (Proposed) | V11 → V12, V5–V10 → V5–V11 | Đã sửa |
| USER-002, USER-003, MATCH-001, MATCH-002, REPUTATION-001, MERCENARY-001, NOTIFICATION-001 (API_Spec, CCD, PF, CHANGELOG) | Kiểu/ví dụ thời gian + số migration | Đã sửa (entry CHANGELOG MAJOR mỗi mã) |
| (a) `docs/agent/convention-backend.md` (trên `main` `2787ecc`: l.286–303 entity mẫu + quy tắc Timestamp, 367–368 DTO mẫu, 410, 431 `ErrorResponse` mẫu, **l.479 `@SQLDelete(sql = "UPDATE arenas SET deleted_at = NOW() …")` phải đổi** sang `now() AT TIME ZONE 'Asia/Ho_Chi_Minh'`), `api-convention.md` (l.121, 395 + quy tắc định dạng thời gian ở §10), `security-backend.md` (l.377), `docs/agent/README.md` (l.132), `docs/agent/CLAUDE.md` §2 (thêm dòng trỏ `docs/agent/design/SYSTEM-002/`) | Thay quy ước `OffsetDateTime`/`timestamptz`/ví dụ `Z` bằng §2 | Nhánh riêng `chore/agent-framework-vietnam-local-datetime`, **sau khi thiết kế được duyệt, trước khi code** |
| (b) USER-001, VENUE-001, `docs/database/{schema.dbml,schema-design.md,data-model.md}` | Như các mã trên + V6 vào tài liệu DB | **Đã sửa 2026-11-21** sau khi PR #75 merge (`61aa100`) và rebase — chi tiết §9.1 |
| ADR-0011, ADR-0013, ADR-0014 (Accepted) | Mục `Sửa đổi` theo ADR-0017 §Sửa đổi đề xuất | Áp **khi ADR-0017 `Accepted`** (PR #75 đã merge) — chưa áp |

### 9.1. Đã sửa sau khi PR #75 merge (2026-11-21, đối chiếu bản trên `main` `61aa100`)

- **USER-001 v4.4 → v5.0** (CHANGELOG MAJOR): `API_Spec.md` §1.1 dòng "Thời gian" → giờ VN không offset, trỏ SYSTEM-002 §1.1; §4.2 kiểu `datetime`; mọi ví dụ `…Z` (cộng 7 giờ) và `…+07:00` (bỏ offset), kể cả `error.timestamp` §1.2. `Component_Class_Design.md`: tham số repository `LocalDateTime`; bảng projection `Instant` → `LocalDateTime` gán thẳng; mục "Thời gian trả ra" viết lại; xoá `toUtc` khỏi `MapperSupport`; đóng tech-debt "chuẩn hoá thời gian về UTC"; số migration MERCENARY-001 V9 → V10 (CCD, PF). Lượt review 1: `API_Spec.md` §1.2 dòng `VALIDATION_ERROR` + CCD bảng exception — `field` của lỗi đọc body là tên field (§6.1); CHANGELOG v5.0 thêm dòng tương ứng.
- **VENUE-001 v4.3 → v5.0** (CHANGELOG MAJOR): `API_Spec.md` §1 dòng "Thời gian", §4.5 `expiresAt` (giờ VN), 27 ví dụ `…Z`; `timestamp` (epoch giây) giữ nguyên. `Component_Class_Design.md`: mọi `OffsetDateTime` → `LocalDateTime` (signature `UploadSignature`, `VenueSelectionService`, `VenueAdminAlertEvent`, repository); `SignedPublicId.signedAt` giữ `Instant` (ngoại lệ §2.1); `@Scheduled zone = AppTime.ZONE_ID`; projection gán thẳng (bỏ `toUtc`); `expiresAt` = giờ VN của `timestamp` + 1h; DDL V5 giữ nguyên chữ `TIMESTAMPTZ` (file migration bất biến) kèm ghi chú V6; tham chiếu `conversation_message` V6 → V7 (MATCH-001). `Processing_Flow.md` §3.14: `now = LocalDateTime.now(clock)` giờ VN.
- **`docs/database/`**: `schema-design.md` (Conventions + 54 chỗ `TIMESTAMPTZ` → `TIMESTAMP`, ghi chú cập nhật V6), `schema.dbml` (52 `timestamptz` → `timestamp`, 28 default `now()` → `now() AT TIME ZONE 'Asia/Ho_Chi_Minh'` — gồm `team_tag` vì file vẫn sinh từ V1), `data-model.md` (quy tắc thời gian, ghi chú Pairing).
- **Đối chiếu §7 với `main`:** `V5__venue_management.sql` trên `main` trùng nội dung bản đã đọc từ nhánh #75 (chỉ khác ký tự xuống dòng); 51 cột không đổi. **Sửa lệch:** số DEFAULT là **27**, không phải 28 (V1 có 28 `DEFAULT NOW()` gồm `team_tag`). Lớp Java dùng kiểu thời gian trên `main` khớp §5–§6.
- Thiết kế đã có trên nhánh này (USER-002 CCD) giữ nguyên chữ DDL của V4 (`TIMESTAMPTZ`) kèm ghi chú V6 — không viết lại migration đã chạy.

## 10. Danh sách [cần xác nhận]

Không còn điểm chờ user. Đã chốt 2026-11-21 (user): ngoại lệ epoch giữ `Instant` nội bộ (§2.1); request kèm `Z`/offset → `400` (§2.5); chưa có production — Render dev chạy trên DB dev chung, deploy lại ngay sau khi V6 áp lên DB dev; FE phát hành trước / BE sau và điểm khôi phục Neon áp khi có production (`Processing_Flow.md` §4.1); hoãn USER-001/VENUE-001/`docs/database/` tới sau PR #75 (§9); integration test trên Neon branch riêng, DB dev chung nhận V6 khi PR merge (§1.1, §8); nhánh framework riêng (§9); Sửa đổi ADR áp khi ADR-0017 Accepted và PR #75 merge (§9); dời số migration V6–V11 → V7–V12 (§1.1).

Điểm nội bộ, developer kiểm bằng test (không chặn): quyền `CREATE SCHEMA` trên Neon branch (§8 #3); kiểu Java Hibernate 7.2 trả cho cột `TIMESTAMP` trong native query (§5.2, §8 #4); Jackson 3 mặc định có nhận `Z` không (§2.5, §8 #6); lớp / method Jackson 3 lấy đường dẫn field (`MismatchedInputException#getPath`) (§6.1, §8 #6).
