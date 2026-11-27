---
task: SYSTEM-002 — chuẩn hoá ngày giờ LocalDateTime / TIMESTAMP giờ Việt Nam (V6)
created: 2026-11-21
status: in_progress
---

## Đã xong
- P1 `common` (2026-11-21): `AppTime` mới; `ErrorResponse.timestamp` → `LocalDateTime`, `from` dùng `AppTime.now()`. Red: biên dịch lỗi đúng kiểu. Green: `./mvnw -pl common test` 13/13 PASS.
- P2 `domain` (2026-11-21): 22 entity, 9 projection, 4 repository → `LocalDateTime`; callback `AppTime.now()`. `EntityTimeTypesTest` (mới, 53 ca). Green: `./mvnw -pl domain -am test` domain 65/65 PASS.
- P3 `application` (2026-11-21): 17 DTO + event + `UploadSignature` + `VenueSelectionService(+Impl)` → `LocalDateTime`; xoá `MapperSupport.toUtc`, `VenueMapper` gán `source`; 5 service inject `Clock` (`LocalDateTime.now(clock)`); bỏ constructor mặc định `Clock.systemUTC()` ở `VenueAlertServiceImpl`, `VenueImageServiceImpl`, `VenueImageCleanupServiceImpl`, `CloudinaryImageUploadSigner`; `MinAgeValidator` → `AppTime.today()`. Red: MapStruct không map `LocalDateTime` → `OffsetDateTime` + test biên dịch lỗi. Green: `./mvnw -pl application -am test` application 196 (2 skip live Cloudinary có sẵn), 0 fail.
- P4 `api` (2026-11-21): `ClockConfig` (mới), `JsonTimeConfig` (mới — Jackson 3 mặc định nhận `Z`), `GlobalExceptionHandler` (`AppTime.now()`, `bodyField` theo `MismatchedInputException#getPath`), 2 scheduler `zone = AppTime.ZONE_ID`; đổi fixture 15 test cần DB của `api` (chỉ biên dịch) + sửa `NOW()` trần trong SQL test. Red: `RequestBodyReadErrorTest` 5 fail (`field`=`body`, `Z` → 200). Green: 8 lớp unit của api 32/32 PASS.
- P5 (2026-11-21): `V6__vietnam_local_datetime.sql` (22 bảng, 51 cột, 27 DEFAULT); `VietnamLocalDatetimeMigrationScriptTest` 3/3 PASS. Viết (KHÔNG chạy) `VietnamLocalDatetimeMigrationTest`, `VietnamLocalDatetimeConversionTest`, `LocalDatetimeProjectionRepositoryTest`. `./mvnw test-compile` toàn repo BUILD SUCCESS.
- P6 phần unit: common 13, domain 65, application 196 (2 skip live Cloudinary), api 32 — 0 fail. Grep chặn `src/main`: 0 `OffsetDateTime|ZonedDateTime|Clock.systemUTC|LocalDateTime.now()|LocalDate.now()|toUtc|ZoneOffset`; `Instant` chỉ ở ngoại lệ epoch (JWT, Cloudinary).

- Sửa review lượt 1 (2026-11-21): 5 finding (M1, M2 bỏ test #3, m1, m2, m3); `code-reviewer` lượt 2 0C/0M, còn m4, m5 chờ duyệt.
- Bỏ zone tường minh (2026-11-21, Lệch thiết kế #6, user chốt bỏ cả 5 chỗ): `ClockConfig` → `Clock.systemDefaultZone()`; `AppTime` bỏ `ZONE`/`ZONE_ID`, `now()`/`today()` theo JVM; `CloudinaryImageUploadSigner` → `clock.getZone()`; 2 scheduler bỏ `zone =`; test bỏ phần ép JVM về UTC, fixture `Clock.fixed(…, ZoneId.systemDefault())`. Không đổi hành vi khi JVM chạy giờ VN → không có bước Red (refactor dưới test xanh). Unit: common 12/12, domain 65/65, application 196 (2 skip), api unit 32/32; `test-compile` toàn repo PASS. Scheduler test là `@SpringBootTest` → chưa chạy.
- m4 (A) + m5 (tách) (2026-11-21): `migration/V6TimestampColumns` dùng chung; test DB V6 assert `containsAll` cặp bảng.cột; `JsonMapperIntegrationTest` (tách từ `FrameworkErrorStatusTest`). `VietnamLocalDatetimeMigrationScriptTest` 3/3 PASS.
- Số nguyên chặt toàn ứng dụng (2026-11-21, Lệch thiết kế #7, user chốt A trên SYSTEM-002, chặt, chặn chuỗi): `api/config/JsonNumberConfig`. Red: `RequestBodyReadErrorTest` 3 fail (4.5 / 4.0 / "4" → 200). Green: 11/11. api unit 36/36; `test-compile` api PASS. `JsonMapperIntegrationTest` (DB) chưa chạy.

- 2026-11-23: quyết lượt 3 — M1 A (sửa 5 worktree khi tích hợp), m6 `argLine` root `pom.xml` + `AppTimeTest.jvmRunningTestsUsesVietnamTimeZone` (Red `Asia/Saigon`), m7 đổi tên test `ErrorResponseTest`, m8/m12 `api/pom.xml` `spring-boot-maven-plugin` `systemPropertyVariables user.timezone`, m9 `NoExplicitTimeZoneTest` (+ m11 `Clock.systemUTC()`), m10 Javadoc 3 method bean. Lượt 4: 0C/0M/3 MINOR (đã sửa).
- 2026-11-23: merge PR #81 (VENUE-001 sửa) + #82 (quy tắc Javadoc / comment cấu hình) → rebase SYSTEM-002 lên `origin/main` 5e17d63 (state giữ bản nhánh, code tự gộp). Unit: common 14/14, domain 65/65, application 208 (2 skip), api unit 39/39; api test-compile PASS.

- 2026-11-23: `code-reviewer` lượt 5 (0C / 1 MAJOR / 2 MINOR) — user chọn sửa cả 3, đã sửa:
  - M1 → A: Javadoc theo convention §2.4 cho phần code SYSTEM-002 (quét diff bằng script, 0 chỗ còn thiếu): 27 callback `@PrePersist`/`@PreUpdate` ở 22 entity; Javadoc class 3 repository + `verifyPendingClosureReports`; 7 service impl (class, 2 constructor cleanup, `markSelected`); `CloudinaryImageUploadSigner` (constructor, `signUpload`); `MinAgeValidator`; `VenueMapper` (class + 4 method); `GlobalExceptionHandler.path`/`validationResponse`; 2 method scheduler; `AppTime()`; 9 record (`ErrorResponse`, `UploadSignature`, 7 response venue) có `@param`; Javadoc class 25 class test. Getter projection **được miễn** (user chốt) → Javadoc interface của 9 projection thêm câu mô tả cột thời điểm. Enum lồng không đổi trong diff không thêm. Chỉ đổi comment → không có bước Red.
  - m13: `AdminVenueControllerTest:91` → `(now() AT TIME ZONE 'Asia/Ho_Chi_Minh')`.
  - m14: bảng "Lệch thiết kế" #8 → `systemPropertyVariables` `user.timezone`.
  - Test: common 14/14, domain 65/65, application 208 (2 skip live Cloudinary), 0 fail; api unit 9 lớp 39/39 + biên dịch toàn bộ test `api` — BUILD SUCCESS.

- 2026-11-23: `code-reviewer` lượt 6: 0C / 0M / 1 MINOR (m15 — 11 record đổi kiểu thiếu `@param`); user chọn A → đã thêm `@param`; application 208 (2 skip), 0 fail.

- 2026-11-23: `code-reviewer` lượt 7 (phần sửa m15): 0C / 0M / 0 MINOR — cổng review code đạt.

- 2026-11-23: bước 6 — cập nhật design một lần theo bảng "Lệch thiết kế" (9 dòng): ADR-0017 mục `Sửa đổi 2026-11-23` (quyết định 3, 6 → giờ JVM); SYSTEM-002 v1.2 (CCD §1, §2.2, §2.3, §2.4, §2.5 + `JsonNumberConfig`, §3, §6.1, §6.3, §6.6, §8 #1/#1b/#1c/#2/#2b/#3 bỏ/#6/#6b/#7, §9 thêm 4 dòng, §10; PF §3.2, §3.4, §3.5; API_Spec §2 field `integer` chặt + §6 bước 2; CHANGELOG v1.2 MINOR); VENUE-001 v6.1 (CCD bỏ `zone = AppTime.ZONE_ID`, `clock.getZone()`; CHANGELOG MINOR nội bộ). Design MATCH-001/NOTIFICATION-001/REPUTATION-001 sửa trên nhánh của từng mã khi rebase (theo M1 A).

- 2026-11-23: `architect-reviewer` lượt 1: 0C / 2 MAJOR / 6 MINOR, không ảnh hưởng SRS. User chọn: M1 (a) ADR-0018 mới; M2 sửa đủ 4 chỗ; m2–m6 sửa; m4 giữ `""` = không gửi. Đã sửa:
  - ADR-0017 trả về bản HEAD, chỉ đổi hàng `Trạng thái` trỏ ADR-0018; tạo `0018-jvm-time-zone-as-single-clock-source.md` (Proposed, `Accepted` qua PR SYSTEM-002).
  - SYSTEM-002 CCD (trỏ ADR-0018, bảng JVM thêm IDE / `java -jar`, §2.5 phạm vi kiểu + `""`, §8 #6 ca `int`/`""`, §9 ADR-0018 + `api-convention.md` + dòng JSON convention), API_Spec (§1 câu số nguyên, §2 ví dụ field thật + `""` + endpoint bị ảnh hưởng VENUE-001 2.5 `version`, §6), CHANGELOG v1.2 (Type ghi vế không tương thích).
  - VENUE-001 API_Spec 2.5 `version` + CHANGELOG v6.1 (dòng FE thấy được); USER-001 v5.1 (API_Spec §1.2 + CHANGELOG MINOR + header 3 tài liệu).
  - Test `RequestBodyReadErrorTest` (14/14 PASS): ca `int` nguyên thuỷ (DTO riêng — Jackson 3 từ chối thiếu giá trị nguyên thuỷ), `""` → `null` với `Integer`/`Long`, `400` với `int` (hành vi sẵn có, không đổi code main).

- 2026-11-23: `code-reviewer` lượt 8: 0C/0M/3 MINOR (m16–m18 comment) → user sửa cả 3, đã sửa, test PASS. `architect-reviewer` lượt 2: 0C/1 MAJOR/3 MINOR → user: M3 theo tiền lệ ADR-0002/0005, m19 sửa cả hai, m20, m21 sửa → đã sửa.
- **Việc lúc merge PR SYSTEM-002:** ADR-0018 → `Accepted`; ADR-0017 theo CCD SYSTEM-002 §9 dòng ADR-0017 (bỏ "đề xuất"/"(Proposed)" ở `Liên quan`; thêm hàng + mục `Sửa đổi` "Quyết định 3, 6 thay bởi ADR-0018" kèm 3 điểm Hệ quả/Cần theo dõi/phương án loại).

- 2026-11-23: `code-reviewer` lượt 9: 0 finding. `architect-reviewer` lượt 3: 0C/0M/3 MINOR (m22–m24) → user sửa cả 3 → đã sửa.

- 2026-11-23: `code-reviewer` lượt 10 (m24): 0 finding. `architect-reviewer` lượt 4: 1 MINOR (m25) → sửa → lượt 5: 0 finding. **Vòng lặp design xong; không ảnh hưởng SRS** (5 lượt architect-reviewer đều kết luận) → bỏ bước cập nhật SRS.
- 2026-11-23: rule mới (user chốt): `code-reviewer` không review comment / Javadoc; diff chỉ đổi comment / Javadoc không cần `code-reviewer` trước commit. Áp ngay trong phiên; file framework sửa trong worktree `.claude/worktrees/chore-code-review-skip-comments` (nhánh `chore/agent-framework-code-review-skip-comments` từ `origin/main` 5e17d63): `.claude/agents/code-reviewer.md`, `CLAUDE.md` §8, `.agent-bootstrap/project-profile.md` §12 — chưa commit.

- 2026-11-23: commit SYSTEM-002 trên `feature/SYSTEM-002-local-datetime` (base `origin/main` 5e17d63, rebase không có commit mới): `fef540d` feat (code + state, 127 file), `92d116e` docs(SYSTEM-002) (design + ADR, 14 file). Chưa push.

- 2026-11-23: **test cần DB SYSTEM-002 chạy trên DB dev (đã V6)** — `-pl api -am test` từ code trùng `main` `865a855`: common 14, domain 65, application 208, api 189 → 1 fail `SecurityConfigIntegrationTest.apiDocsAreDisabledByDefault` do lệnh của agent dùng `set -a` (export cả `SPRINGDOC_ENABLED` — ADR-0004 đã nêu); chạy lại lớp đó bằng lệnh chuẩn hub (chỉ export 4 biến) → 10/10 PASS. `set -a` cũng làm `CloudinaryImageAssetAdminLiveTest` (2) chạy thật trên cloud-dev: upload + xoá đúng 1 ảnh spike, PASS, không để lại asset. Gồm cả 3 test DB của PR #81 (VENUE-001).

- 2026-11-23: task USER-001 liveness (nhánh `feature/USER-001-liveness-probe`): Spring Boot 4 bật probe sẵn → không đổi code main; thêm test liveness / readiness / `groups`; USER-001 v5.2 + ADR-0004 `Sửa đổi`; code-reviewer + architect-reviewer xong; commit. Render dev liveness 200 (curl). UptimeRobot 401 do monitor gửi `HEAD` → user đổi monitor sang GET / Keyword.

- 2026-11-23: PR #85 (liveness) merge `1afedd3`; không đổi code nên nhánh deploy Render không cần rebase / deploy lại.

- 2026-11-23: nhánh `chore/agent-framework-local-datetime-jvm-clock` — đã sửa 5 file quy ước + 1 dòng `project-profile.md` §12 (convention §2.4/§6.2/checklist, `api-convention.md` §10, README timezone JVM, `security-backend.md` health liveness/readiness). Chỉ tài liệu → không `code-reviewer`. PR #86 merge (squash) `89d252f`.

- 2026-11-23: rebase 6 nhánh song song (worktree) lên `origin/main` `89d252f` — user cho phép agent tự gộp conflict lần này. Cách làm: sao lưu `refs/backup/<MÃ>-2026-11-23` + stash "pre-rebase <MÃ>", fast-forward lên main, áp lại 3-way, bỏ staging (code vẫn chưa commit). Theo ADR-0018: bỏ `zone = AppTime.ZONE_ID` (3 scheduler), hằng zone `NotificationRules`, `AppTime.ZONE` → `ZoneId.systemDefault()` trong test; `AppTime`/`ClockConfig` lấy bản main. Unit PASS cả 6 (USER-003, MATCH-002, REPUTATION-001, NOTIFICATION-001, MATCH-001, MERCENARY-001); test cần DB chưa chạy. Còn: CCD MATCH-001/NOTIFICATION-001/REPUTATION-001 theo ADR-0018; mã lỗi / entity bản tạm trùng giữa các nhánh xử lý khi tích hợp.

## Đang làm
- Còn lại: user xác nhận Render dev deploy lại + response không còn `Z` (bước 9); rebase 6 nhánh song song theo quy tắc ADR-0018 (bước 10); nhánh `chore/` convention §6.2/§2.4/`api-convention.md`/README l.132 (CCD §9 (c)); Neon branch riêng của bước 4 không tạo nên không cần xoá.
