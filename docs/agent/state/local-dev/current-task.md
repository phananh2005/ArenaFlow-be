---
task: SYSTEM-002 — chuẩn hoá ngày giờ LocalDateTime / TIMESTAMP giờ Việt Nam (V6)
created: 2026-11-21
updated: 2026-11-23
percent: 88
status: in_progress
---

## Mô tả
Đổi mọi thời điểm của hệ thống từ `OffsetDateTime` / `TIMESTAMPTZ` (trả UTC) sang `LocalDateTime` / `TIMESTAMP` giờ Việt Nam không kèm múi giờ (BC-M05, ADR-0017): migration V6 (51 cột / 22 bảng, 27 DEFAULT), entity / projection / repository / DTO / event / `ErrorResponse`, nguồn "bây giờ" qua bean `Clock` (zone `Asia/Ho_Chi_Minh`, tick 1 µs) và `AppTime`, cron `zone = AppTime.ZONE_ID`, lỗi đọc JSON body trả `field` = đường dẫn field. Không endpoint mới, không mã lỗi mới, không đổi số truy vấn.

Bối cảnh (user chốt 2026-11-21): 7 mã còn lại implement song song, mỗi mã một nhánh từ `origin/main`. SYSTEM-002 là mã gốc — sở hữu `common/time/AppTime` và `api/config/ClockConfig`; các mã khác tạo bản tạm cùng chữ ký, khi tích hợp lấy bản của nhánh này. Test cần DB viết ở giai đoạn 2 nhưng **chỉ chạy ở giai đoạn tích hợp**.

## Thiết kế liên quan
- `docs/agent/design/SYSTEM-002/` v1.1 — `Component_Class_Design.md` (CCD), `Processing_Flow.md` (PF), `API_Spec.md`, `CHANGELOG.md`.
- `docs/agent/decisions/0017-vietnam-local-datetime.md` (Accepted).
- `docs/agent/convention-backend.md` §6.2 (quy ước thời gian), §12 (test).
- `docs/agent/non-functional-standards.md` — N_DETAIL=3, N_LIST=4, N_WRITE=6. SYSTEM-002 không thêm/bớt câu SQL (PF §2) → đạt ngưỡng, giữ nguyên số của USER-001/USER-002/VENUE-001.

## Definition of Done
- [x] `AppTime`, `ClockConfig` đúng chữ ký CCD §2.2; `JsonTimeConfig` chỉ tạo nếu test #6 chứng minh Jackson 3 mặc định nhận `Z`.
- [x] `src/main` không còn `OffsetDateTime`, `ZonedDateTime`, `Clock.systemUTC()`, `LocalDateTime.now()` / `LocalDate.now()` không tham số, `MapperSupport.toUtc`; `Instant` chỉ còn ở ngoại lệ epoch CCD §2.1 (grep, dán output).
- [x] `V6__vietnam_local_datetime.sql`: 22 `ALTER TABLE`, 51 cột, 27 DEFAULT `(now() AT TIME ZONE 'Asia/Ho_Chi_Minh')`; V1–V5 không đổi.
- [x] `@Scheduled(cron)` dùng `zone = AppTime.ZONE_ID`.
- [x] `GlobalExceptionHandler`: `AppTime.now()`; lỗi đọc JSON body trả đường dẫn field, JSON sai cú pháp / body rỗng → `"body"`.
- [x] Unit test thuần (common, domain, application, api không DB) PASS — dán output.
- [x] Test cần DB đã viết/sửa và **biên dịch được** (`test-compile`), chưa chạy — chạy ở giai đoạn tích hợp (mục "Test để giai đoạn tích hợp").
- [x] `code-reviewer` không còn CRITICAL/MAJOR; đối chiếu design + bảng "Lệch thiết kế" → cập nhật design (nếu có) → `architect-reviewer`.

## Các bước thực hiện (tổng quan)
- [x] 1. Đối chiếu & phân tích ảnh hưởng (design v1.1, convention §6.2, ADR-0017, NFS)
- [x] 2. Lập kế hoạch chi tiết — file cần sửa (chờ user duyệt)
- [x] 3. Triển khai code + test theo phase P1–P5 (TDD); P6 phần unit + grep xong
- [x] 4. Chạy unit test thuần & xác nhận PASS (dán output); `test-compile` toàn repo PASS
- [x] 5. `code-reviewer` trên phần chưa review → sửa CRITICAL/MAJOR, test lại (lượt 1–7, lượt 7: 0 finding)
- [x] 6. Đối chiếu code với design → cập nhật design theo bảng "Lệch thiết kế" → `architect-reviewer` review phần sửa (lượt 1–5, lượt 5: 0 finding; ADR-0018 mới; không ảnh hưởng SRS)
- [ ] 7. Bước 6 có sửa code → test lại + `code-reviewer` phần sửa → commit (khi user yêu cầu)
- [ ] 8. (Giai đoạn tích hợp) chạy test cần DB trên Neon branch đã chốt — không tick PASS trước khi chạy thật

## Lệch thiết kế
_Ghi ngay khi code khác design; KHÔNG sửa design giữa chừng — cập nhật một lần ở bước cuối (`CLAUDE.md` §8). Trống = code khớp design._

| # | Phase | Design | Code thực tế | Lý do | Sửa ở |
|---|---|---|---|---|---|
| 1 | P4 | CCD §2.5, §10: `JsonTimeConfig` có điều kiện; "[Unverified] Jackson 3 mặc định có nhận `Z` không" | Đã kiểm (Jackson 3.1.7): mặc định **nhận** `"…Z"` (200, bỏ `Z`) → tạo `api/config/JsonTimeConfig` (bean `strictLocalDateTimeCustomizer`, `withConfigOverride(LocalDateTime, forLeniency(false))`); `+07:00` vốn lỗi parse | Nhánh có điều kiện của design được kích hoạt; bỏ nhãn [Unverified] | CCD §2.5, §3, §10 |
| 2 | P4 | CCD §6.1: [Unverified] `MismatchedInputException#getPath` của Jackson 3 | Đã kiểm: `getPath()` định nghĩa ở `JacksonException` (trả `List<JacksonException.Reference>`), lấy `MismatchedInputException` đầu tiên trong chuỗi cause cho đủ ca ngày giờ / số / enum / UUID / mảng; JSON cụt và body rỗng → `"body"` | Bỏ nhãn [Unverified]; ghi đúng lớp khai báo `getPath` | CCD §6.1 |
| 3 | P4 | CCD §8 #6: "MVC test request" (sửa `GlobalExceptionHandlerTest`) | Lớp test mới `RequestBodyReadErrorTest` + `JsonTestSupport` (MockMvc standalone dựng `JsonMapper` qua customizer của `JsonTimeConfig`) | Tách ca đọc body cho gọn; repo không có `@WebMvcTest` | CCD §8 #6 (tên lớp test) |
| 4 | P2, P4 | CCD §8 không có | Thêm unit test `EntityTimeTypesTest` (reflection 22 entity + 9 projection + callback) và `VietnamLocalDatetimeMigrationScriptTest` (kiểm tĩnh V6, không DB) | Bù cho test cần DB bị hoãn tới giai đoạn tích hợp | CCD §8 (bổ sung dòng) |
| 5 | Sửa review | CCD §8 #3 (test schema tạm `target=5 → 6`, giữ thời điểm khi chuyển) | **Bỏ** `VietnamLocalDatetimeConversionTest`; giữ #2 (`VietnamLocalDatetimeMigrationTest`) | V1 dòng 152 `INSERT INTO sport (name, is_active)` không có `id` trong khi `sport.id UUID PRIMARY KEY` không DEFAULT → V1 không chạy được trên schema trống (code-reviewer MAJOR 2, user chọn bỏ 2026-11-21) | CCD §8 #3, §10 |
| 6 | Sửa sau review (user chốt 2026-11-21) | ADR-0017 quyết định 3, CCD §2.2, §2.3 (cron), convention §6.2: "bây giờ" có zone tường minh `Asia/Ho_Chi_Minh`, cấm `LocalDateTime.now()` không tham số | Java dựa vào timezone JVM (giả định JVM luôn chạy `Asia/Ho_Chi_Minh`: Dockerfile Render `TZ` + `-Duser.timezone`, lệnh `./mvnw`): `ClockConfig` = `Clock.tick(Clock.systemDefaultZone(), 1 µs)`; `AppTime` bỏ `ZONE`/`ZONE_ID`, `now()` = `LocalDateTime.now().truncatedTo(MICROS)`, `today()` = `LocalDate.now()`; `CloudinaryImageUploadSigner` dùng `clock.getZone()`; 2 scheduler bỏ `zone =`; test bỏ phần ép JVM về UTC (m1). SQL giữ `now() AT TIME ZONE 'Asia/Ho_Chi_Minh'` (TimeZone phiên Neon = GMT, không theo JVM) | User chọn bỏ cả 5 chỗ zone tường minh | ADR-0017 (quyết định 3 + phương án bị loại), CCD §2.2, §2.3, §8 #1, convention §6.2 (nhánh `chore/`) |
| 7 | Sửa sau review (user chốt 2026-11-21) | CCD §2.5 chỉ có `JsonTimeConfig` (đọc `LocalDateTime` chặt); không nói về số nguyên | Thêm `api/config/JsonNumberConfig` (bean `strictIntegerCustomizer`, CoercionConfig `LogicalType.Integer`: `Float` → Fail, `String` → Fail) → `4.5`, `4.0`, `"4"` gửi vào field số nguyên (`int`/`Integer`/`Long`…) → `400 VALIDATION_ERROR`, `field` = đường dẫn field; `JsonTestSupport` áp cả 2 customizer; `JsonTimeConfigIntegrationTest` → `JsonMapperIntegrationTest` (thêm ca số nguyên) | Lỗi chéo mã: Jackson ép `4.5 → 4` (REPUTATION M2 đã kiểm, MERCENARY, MATCH-001 m5; có sẵn trên `main`: `AddVenueImageRequest.version`); user chọn sửa toàn ứng dụng trên SYSTEM-002, chặt, chặn cả chuỗi | CCD §2.5, §3, §6.1, §8 #6 |
| 8 | Sửa sau review lượt 3–4 (m6, m8, m12, user chốt 2026-11-23) | CCD §2.2 / ADR-0017: JVM chạy `-Duser.timezone=Asia/Ho_Chi_Minh` (chỉ nêu cờ lệnh / Dockerfile) | Root `pom.xml` thêm `maven-surefire-plugin` `<argLine>-Duser.timezone=Asia/Ho_Chi_Minh</argLine>` (kế thừa xuống 4 module); `api/pom.xml` `spring-boot-maven-plugin` `<systemPropertyVariables><user.timezone>Asia/Ho_Chi_Minh</user.timezone>` cho JVM của `spring-boot:run` (m8, m12: không dùng `jvmArguments` để vẫn truyền được `-Dspring-boot.run.jvmArguments` từ dòng lệnh); test `AppTimeTest.jvmRunningTestsUsesVietnamTimeZone` (Red khi không cờ: `Asia/Saigon`; Green sau argLine) | JVM test luôn giờ VN bất kể máy / CI | CCD SYSTEM-002 §2.2 (nguồn giờ JVM: test ghim bằng surefire) |
| 9 | Sửa sau review lượt 3 (m9, user chốt 2026-11-23) | CCD §8 không có test chặn zone tường minh | Thêm unit test `NoExplicitTimeZoneTest` (`api` test, quét tĩnh `*/src/main/java` của 4 module) | Giữ quyết định "chỉ dùng giờ JVM" (Lệch #6) không bị phá về sau | CCD SYSTEM-002 §8 (thêm test) |

## Tái sử dụng
Reuse Report (skill `reuse-scan`, 2026-11-21, HEAD `6fe6285`).

**Nguồn đã quét:** `common/src/main`, `domain/src/main`, `application/src/main`, `api/src/main` (`*.java`) bằng grep (ripgrep/grep -rn). **Vùng mù:** `.claude/worktrees/agent-*` (bản sao cũ của repo, không phải source — loại khỏi kết luận); thư viện ngoài (Spring/Jackson) không quét ngoài phạm vi API công khai.

**Từ khoá:** `class \w*(Time|Clock|Date)\w*`, `ZoneId`, `Clock.system`, `systemUTC`, `@Bean … Clock`, `Asia/Ho_Chi_Minh`, `TimeUtil`, `DateUtil`, `JsonMapperBuilderCustomizer`, `Jackson2ObjectMapperBuilder`, `JsonFormat`, `Leniency`, `MismatchedInputException`, `InvalidFormatException`, `getPath()`, `toUtc`.

| Thành phần | Nhóm | Nguồn / lý do |
|---|---|---|
| `common/time/AppTime` (`ZONE_ID`, `ZONE`, `now()`, `today()`) | **VIẾT MỚI** | Không có lớp tiện ích thời gian / `ZoneId` nào trong 4 module (chỉ chuỗi `"Asia/Ho_Chi_Minh"` literal ở 2 scheduler — sẽ thay bằng `AppTime.ZONE_ID`). Chữ ký theo CCD §2.2 — các mã song song dùng lại chữ ký này |
| `api/config/ClockConfig` (bean `Clock`) | **VIẾT MỚI** | Không có `@Bean Clock`. Hiện 4 lớp tự tạo `Clock.systemUTC()` trong constructor mặc định (`VenueAlertServiceImpl:33`, `VenueImageCleanupServiceImpl:48`, `VenueImageServiceImpl:53`, `CloudinaryImageUploadSigner:40`) → bỏ constructor mặc định, inject bean (không tạo nguồn Clock song song) |
| `api/config/JsonTimeConfig` (có điều kiện) | **VIẾT MỚI** (chỉ khi test #6 đỏ vì Jackson nhận `Z`) | Không có `JsonMapperBuilderCustomizer` / cấu hình Jackson nào trong `src/main`; `application*.properties` không có `spring.jackson.*` |
| `GlobalExceptionHandler#bodyField` (private) | **MỞ RỘNG** | Sửa `handleMessageNotReadable` (`GlobalExceptionHandler.java:90-94`) — hiện trả cứng `"body"`; không có helper đọc path Jackson nào trong repo |
| `MapperSupport.toUtc` | **Xoá** | `application/mapper/MapperSupport.java:22` — không còn nguồn `Instant`; 11 chỗ dùng ở `VenueMapper` |
| `ErrorResponse.from`, `GlobalExceptionHandler.validationResponse` | **DÙNG LẠI** (đổi kiểu) | Giữ nguyên luồng, chỉ đổi `OffsetDateTime.now()` → `AppTime.now()` |

## Kế hoạch chi tiết

Thứ tự phase theo phụ thuộc module (`common` → `domain` → `application` → `api`). Mỗi phase: Red (viết/sửa test, chạy, fail đúng lý do) → Green → Refactor → `progress-tracker`. Lệnh unit test: `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -pl <module> -am -Dtest=<Test> -Dsurefire.failIfNoSpecifiedTests=false test` — không nạp `.env.dev`.

**Lưu ý biên dịch:** module `api` chứa mọi test cần DB → chạy bất kỳ unit test nào của `api` cũng phải biên dịch toàn bộ test của `api`. Vì vậy P4 gồm cả việc đổi kiểu fixture trong test tích hợp của `api` (chỉ biên dịch, không chạy).

### P1 — `common`: `AppTime` + `ErrorResponse` (CCD §2.2, §6.1)
- Test viết trước:
  - `common/.../time/AppTimeTest` (mới, #1): `now()` không có phần dưới micro giây; `ZONE.getId()` = `Asia/Ho_Chi_Minh`; `now()` cách `LocalDateTime.now(ZONE)` < 1 s; `today()` = `LocalDate.now(ZONE)`; `ZONE_ID` là hằng (dùng được trong annotation).
  - `ErrorResponseTest` (sửa, #5): `timestamp` là `LocalDateTime`; JSON không có `Z`/offset.
- File: tạo `common/src/main/java/com/phananh/arenaflow/common/time/AppTime.java`; sửa `common/exception/ErrorResponse.java`.

### P2 — `domain`: 22 entity, 9 projection, 4 repository (CCD §5)
- Test viết trước:
  - `domain/.../entity/EntityTimeTypesTest` (mới, bổ sung — không có trong CCD §8): duyệt reflection 22 entity + 9 projection → không field/getter nào kiểu `OffsetDateTime`/`Instant`/`ZonedDateTime`; mọi field thời điểm CCD §5.1 là `LocalDateTime`.
  - `TeamEntityTest`, `VenueManagementEntityTest`, `UserEntityTest` (sửa): `@PrePersist`/`@PreUpdate` gán `LocalDateTime` giờ VN, cắt micro giây, giữ giá trị đã gán (`invitedAt`, `transferredAt`…).
- File sửa (35): entity `user/{User,OAuthAccount,AdministrativeUnit}`, `team/{Team,TeamMember,TeamCaptainHistory}`, `match/{Sport,MatchVenueOption,CostCustomItem,Match,MatchTimeOption,MatchRequest,Pairing}`, `venue/{Venue,VenueImage,VenueReport}`, `rating/{Rating,Report}`, `mercenary/{MercenaryRecruitment,MercenaryApplication}`, `chat/{ChatMessage,Notification}`; projection `{AdminVenueReportView,CaptainHistoryView,MemberHistoryView,MemberView,ProfileView,TeamDetailView,TeamSummaryView,VenueDetailView,VenueSummaryView}`; repository `{UserRepository,TeamMemberRepository,VenueRepository,VenueReportRepository}` (chỉ kiểu tham số, SQL giữ nguyên).

### P3 — `application`: mapper, DTO, event, service, signer, validator (CCD §6.2–§6.5)
- Test viết trước:
  - `ResponseDtoJsonTest` (sửa, #5): `LocalDateTime.of(2026,11,20,19,0)` → `"2026-11-20T19:00:00"`; micro giây → `"…19:00:00.123456"`; 500 ms → `"…19:00:00.5"`; 120 ms → `"…19:00:00.12"`.
  - `MapperTest`, `VenueMapperTest` (sửa): projection `LocalDateTime` → DTO gán thẳng, cùng giá trị.
  - `VenueAlertServiceImplTest` (sửa, #7): `Clock.fixed(…, AppTime.ZONE)`; biên 180 ngày theo giờ VN; `threshold`/`now` truyền vào `markStaleAlerted` và `VenueAdminAlertEvent.triggeredAt` là giờ VN.
  - `CloudinaryImageUploadSignerTest` (sửa, #7): `expiresAt` = `LocalDateTime.ofInstant(timestamp + TTL, AppTime.ZONE)`; `timestamp` epoch không đổi.
  - `ProfileServiceImplTest`, `TeamServiceImplTest`, `TeamMembershipServiceImplTest`, `AdminVenueServiceImplTest`, `VenueServiceImplTest`, `VenueSelectionServiceImplTest` (sửa): truyền `Clock.fixed(…, AppTime.ZONE)` vào constructor; assert `now` (giờ VN) đi vào `softDelete`/`disbandRemaining`/`leaveAndRejectAllOpen`/`setJoinedAt`/`setLeftAt`/`resolveIfPending`/`markClosureAlerted`/`markSelected`.
  - `VenueImageServiceImplTest`, `VenueImageCleanupServiceImplTest` (sửa): `Clock.fixed(…, AppTime.ZONE)`; logic `Instant` giữ nguyên (ngoại lệ epoch).
  - `CustomValidatorsTest` (sửa): `MinAgeValidator` biên tuổi tính theo `AppTime.today()`.
- File sửa (≈34): `mapper/{MapperSupport,VenueMapper}`; 17 response DTO (CCD §6.4); `event/venue/VenueAdminAlertEvent`; `support/storage/{UploadSignature,CloudinaryImageUploadSigner}`; `service/VenueSelectionService` + `impl/{VenueSelectionServiceImpl,ProfileServiceImpl,TeamServiceImpl,TeamMembershipServiceImpl,AdminVenueServiceImpl,VenueServiceImpl,VenueAlertServiceImpl,VenueImageServiceImpl,VenueImageCleanupServiceImpl}` (inject `Clock`; bỏ constructor mặc định `Clock.systemUTC()`); `validation/MinAgeValidator`. `JwtTokenService` giữ `Instant.now()` (CCD §6.3 cho phép, không đổi hành vi).

### P4 — `api` main + unit test của `api` (CCD §2.2, §2.4, §2.5, §6.1)
- Test viết trước:
  - `api/.../config/ClockConfigTest` (mới, #1): zone `Asia/Ho_Chi_Minh`; hai lần `instant()` có phần nano chia hết 1 000 (tick 1 µs).
  - `GlobalExceptionHandlerTest` (sửa, #6): controller + DTO test tạm có `LocalDateTime startsAt`, `List<…> timeOptions[].startsAt`, `LocalDate dateOfBirth`, `int`, enum, `UUID`. (a) `startsAt` kèm `Z` / `+07:00` → 400, `field` = `startsAt` / `timeOptions[0].startsAt`; không offset → 200. (b) `dateOfBirth: "15/05/1995"` → `dateOfBirth`; số/enum/UUID sai kiểu → tên field. (c) JSON sai cú pháp / body rỗng → `"body"`. `error.timestamp` không offset. MockMvc standalone dựng converter từ `JsonMapper` giống Boot (+ customizer của `JsonTimeConfig` nếu phải tạo).
  - `SecurityErrorHandlersTest` (sửa, #5): `timestamp` không `Z`/offset.
  - `VietnamLocalDatetimeMigrationScriptTest` (mới, bổ sung — unit thuần, đọc file V6 từ classpath, không DB): đủ 22 `ALTER TABLE`, 51 cột `TYPE TIMESTAMP USING … AT TIME ZONE 'Asia/Ho_Chi_Minh'`, 27 `SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')`, không `now()` trần. (Viết ở P4, Green ở P5.)
- File: tạo `api/config/ClockConfig.java`, `JsonTimeConfig.java` (chỉ khi #6(a) đỏ vì nhận `Z`); sửa `api/config/GlobalExceptionHandler.java`, `api/scheduler/{VenueStaleAlertScheduler,VenueImageCleanupScheduler}.java`.
- Đổi kiểu fixture (`OffsetDateTime`/`Instant` → `LocalDateTime`, `Clock` zone VN) trong mọi test của `api/src/test` để biên dịch (≈25 file: controller test, `application/service/*IntegrationTest|*RaceTest|*QueryCountTest`, `domain/repository/*Test`, `ApiIntegrationTest`…) — chỉ `test-compile`, không chạy.

### P5 — Migration V6 + test cần DB (viết, không chạy) (CCD §7, §8 #2–#4, #8)
- File: tạo `api/src/main/resources/db/migration/V6__vietnam_local_datetime.sql` theo bảng CCD §7 (mỗi bảng một `ALTER TABLE`, DROP DEFAULT → TYPE … USING → SET DEFAULT).
- Green cho `VietnamLocalDatetimeMigrationScriptTest` (chạy ngay).
- Viết test cần DB (mục dưới) + sửa SQL `now()` trần trong test → `now() AT TIME ZONE 'Asia/Ho_Chi_Minh'`: `VenueControllerTest:239,283,310`, `AdminVenueControllerTest:31,91`, `VenueSelectionIntegrationTest:59,69`, `VenueStaleAlertRaceTest:72`, `UserTeamMigrationTest:51`.
- Kiểm: `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh test-compile` toàn repo PASS.

### P6 — Kiểm chứng
- Chạy toàn bộ unit test thuần (danh sách lớp không cần DB ở cả 4 module) → dán output.
- Grep guard `src/main`: `OffsetDateTime|ZonedDateTime|Clock.systemUTC|LocalDateTime.now()|LocalDate.now()|toUtc` = 0; `Instant` chỉ ở ngoại lệ CCD §2.1 → dán output.
- `code-reviewer` → đối chiếu design → `architect-reviewer` (nếu có lệch).

**Ước lượng:** tạo 4–5 file main (`AppTime`, `ClockConfig`, V6, [`JsonTimeConfig`]) + ≈7 file test mới; sửa ≈73 file main + ≈45 file test → tổng ≈125–130 file.

## Test để giai đoạn tích hợp
Viết/sửa ở giai đoạn 2 (phải `test-compile` PASS), **CHỈ CHẠY ở giai đoạn tích hợp**, trên DB đã chốt (CCD §8: Neon branch riêng, NEVER DB dev chung trước khi PR SYSTEM-002 merge):

| Test | Loại | Kiểm |
|---|---|---|
| `migration/VietnamLocalDatetimeMigrationTest` (mới, #2) | `@SpringBootTest`, `ddl-auto=none` | 0 cột `timestamp with time zone`; 51 cột `timestamp without time zone`; 27 DEFAULT có `AT TIME ZONE 'Asia/Ho_Chi_Minh'`; `SET LOCAL TimeZone='GMT'` + INSERT `"user"` → `created_at` lệch < 5 s so với giờ VN |
| `domain/repository/LocalDatetimeProjectionRepositoryTest` (mới, #4) | `RepositoryIntegrationTest` | Projection native (`TeamRepository.findDetailById`, `VenueRepository` V5) trả `LocalDateTime` đúng giá trị đã ghi; log `current_setting('TimeZone')` |
| `NfsQueryCountTest`, `NfsWriteQueryCountTest`, `VenueNfsQueryCountTest` (sửa fixture, #8) | `@SpringBootTest` | Số câu SQL không đổi |
| Mọi `ApiIntegrationTest` con (controller test), `application/service/*IntegrationTest`, `*RaceTest`, `domain/repository/*Test`, `migration/{UserTeamMigrationTest,VenueManagementMigrationTest}`, `api/scheduler/*SchedulerTest`, `ArenaFlowApplicationTests` (sửa fixture, #8) | Cần DB | Hồi quy; Hibernate `validate` `LocalDateTime` ↔ `TIMESTAMP`; response không `Z` |

## Blockers / Ghi chú
- **Cho MATCH-001 (và mã có request `LocalDateTime`)** [Inference — theo hành vi `LocalDateTimeDeserializer` khi tắt leniency, chưa kiểm]: `JsonTimeConfig` tắt leniency cho `LocalDateTime` → chuỗi rỗng `""` ở field `LocalDateTime` có thể thành `400 VALIDATION_ERROR` thay vì `null`. Viết test cho ca này khi code request có thời điểm.
- Test #6 trên `JsonMapper` thật của Spring: `FrameworkErrorStatusTest.applicationJsonMapperRejectsLocalDateTimeWithOffset` (cần context — chạy ở giai đoạn tích hợp).
- **[cần xác nhận] DB cho test tích hợp:** CCD §8 / PF §4.1 bắt chạy trên Neon branch riêng (xin phép tạo/xoá), NEVER DB dev chung trước khi PR merge — khác mặc định hub §8 (tự nạp `.env.dev`). Giai đoạn tích hợp dùng một Neon branch chung cho cả chuỗi V6–V12 hay riêng từng mã? Ai tạo?
- Test #3 đã bỏ (bảng "Lệch thiết kế" #5).
- **Rủi ro [Inference]:** kiểu Java Hibernate 7.2 trả cho cột `TIMESTAMP` trong native projection (CCD §5.2) chỉ kiểm được ở #4 → có thể phải đổi getter / mapper khi tích hợp (ghi "Lệch thiết kế" lúc đó).
- **Rủi ro:** repo không có `spring-boot-webmvc-test` (`@WebMvcTest`) → test #6 chạy MockMvc standalone với `JsonMapper` tự dựng; việc Boot áp customizer chỉ được chứng minh ở test tích hợp (controller test). Không thêm dependency.
- **Rủi ro song song:** các nhánh MATCH-001… sửa cùng entity (`Match`, `MatchRequest`, `ChatMessage`, `Notification`…), `GlobalExceptionHandler`, và tạo bản tạm `AppTime`/`ClockConfig` → xung đột add/add khi tích hợp; entity mới của họ (`LocalDateTime`) chỉ `validate` được sau V6.
