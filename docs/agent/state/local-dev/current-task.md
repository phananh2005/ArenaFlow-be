---
task: MERCENARY-001 — tuyển mercenary, tin tuyển & đơn ứng tuyển, chat theo đơn, uy tín cá nhân, trận ngoài hệ thống (V10)
created: 2026-11-21
status: in_progress
---

## Mô tả
Triển khai MERCENARY-001 theo thiết kế đã duyệt (v3.0) trên nhánh `feature/MERCENARY-001-mercenary-impl` (tạo từ `origin/main` `6fe6285`, worktree riêng). Implement song song với SYSTEM-002 (V6), MATCH-001 đợt 1 (V7), MATCH-002 (V8), REPUTATION-001 (V9), NOTIFICATION-001 (V11), USER-003 (V12) — user chốt 2026-11-21. Giai đoạn 1 (hiện tại): chỉ lập kế hoạch. Giai đoạn 2: code + unit test thuần. Test cần DB / Spring context / migration / đếm truy vấn: viết ở giai đoạn 2, **chỉ chạy** ở giai đoạn tích hợp (worktree không có `.env.dev`; V10 cần V6–V9 chưa có trên `main`).

Phạm vi: migration `V10__mercenary_recruitment_chat_reputation.sql`; 29 endpoint riêng (2.1–2.13, 2.18–2.33; 2.14–2.17 là endpoint chat của MATCH-001); job đóng tin / đơn mồ côi / báo huỷ trận; uy tín cá nhân (hoàn thành, rút lui, report, hoàn điểm, huy hiệu); xoá tài khoản ứng viên (USER-001 2.7); phần sửa của REPUTATION-001 (§4.12 CCD) và lõi chat MATCH-001 (khối `MERCENARY_APPLICATION`).

## Thiết kế liên quan
- `docs/agent/design/MERCENARY-001/` — `API_Spec.md`, `Component_Class_Design.md` (CCD), `Processing_Flow.md` (PF), `CHANGELOG.md` (v3.0).
- `docs/agent/business-spec/SYSTEM-001/` — UC-ME01..ME09, BR-ME01..BR-ME19, BC-ME01..ME05, AC-ME01..ME11.
- Mã đứng trước (chưa có trên `main`): SYSTEM-002 CCD §2, §5.1 (`AppTime`, `ClockConfig`, entity → `LocalDateTime`); MATCH-001 CCD §1.1, §4.5, §4.8, §4.14, §4.15, §5.1–§5.3, §6, §7; MATCH-002 CCD §12 #4, #5 (`ConcurrencyConflictException`, `RETURNING` không `@Modifying`); REPUTATION-001 CCD §4.6–§4.10, §5.2, §6, §14.
- `docs/agent/non-functional-standards.md` (đọc trước kế hoạch): N_DETAIL=3, N_LIST=4, N_WRITE=6; §4.1 flush. `docs/agent/convention-backend.md` §2.4 (comment tiếng Việt, không trỏ tài liệu), §2.5, §6.2 (giờ VN, `LocalDateTime`, `Clock`/`AppTime`).
- ADR-0002, 0003 (+ Sửa đổi 2026-11-14), 0007 (+ Sửa đổi 2026-11-14), 0008, 0011, 0012, 0013, 0014, 0017.

## Definition of Done
- [ ] V10 áp được trên DB dev sau V6–V9 (`MercenaryMigrationTest` PASS — giai đoạn tích hợp).
- [ ] 29 endpoint + job + uy tín cá nhân + xoá tài khoản theo thiết kế; thứ tự guard đúng PF §1.1.
- [ ] Unit test thuần PASS ở giai đoạn 2 (dán output); test tích hợp / NFS / race PASS ở giai đoạn tích hợp.
- [ ] Số truy vấn mỗi endpoint đo bằng log SQL ≤ bảng PF §3 (2.27 `REJECTED` = 6/W6).
- [ ] Phần MỞ RỘNG vào class của mã khác đã áp khi tích hợp; phụ thuộc tạm đã gỡ (bản mã sở hữu thắng).
- [ ] `code-reviewer` không còn CRITICAL/MAJOR; đối chiếu design + bảng "Lệch thiết kế" → cập nhật design một lần → `architect-reviewer`.
- [ ] Đồng bộ tài liệu CCD §11 (schema.dbml, schema-design.md, data-model.md; api-convention/convention-backend/CLAUDE.md §2 qua nhánh `chore/agent-framework-*`).

## Các bước thực hiện (tổng quan)
- [x] 1. Đối chiếu & phân tích ảnh hưởng (spec / convention / kiến trúc / NFS)
- [x] 2. Lập kế hoạch chi tiết — file cần sửa (chờ user duyệt)
- [x] 3. Triển khai code + unit test thuần theo TDD (P0–P9)
- [x] 4. Chạy unit test & xác nhận PASS (dán output) — test tích hợp chưa chạy
- [ ] 5. `code-reviewer` trên phần chưa review → sửa CRITICAL/MAJOR, test lại
- [ ] 6. Giai đoạn tích hợp: áp MỞ RỘNG, gỡ phụ thuộc tạm, chạy test tích hợp / NFS / race
- [ ] 7. Đối chiếu code với design → cập nhật design theo bảng "Lệch thiết kế" → `architect-reviewer`
- [ ] 8. Có sửa code ở bước 6–7 → test lại + `code-reviewer` phần sửa → commit (khi user yêu cầu)

## Lệch thiết kế
_Ghi ngay khi code khác design; KHÔNG sửa design giữa chừng — cập nhật một lần ở bước cuối (`CLAUDE.md` §8). Trống = code khớp design._

| # | Phase | Design | Code thực tế | Lý do | Sửa ở |
|---|---|---|---|---|---|
| 1 | P2 | `@ValidMercenarySearch` + validator mới | Dùng `@ValidGeoQuery` tổng quát qua `GeoQuery` (bản tạm MATCH-001 sở hữu); `GeoBoundingBox` (MATCH-001) | Quy tắc trùng hoàn toàn; user chốt Q2/Q3 | CCD §2, §7 |
| 2 | P3 | Chữ ký repository §5.2 | Bỏ `now` khi câu không dùng (`findRecruitContext` kèo/trận ngoài, `ExternalMatchRepository.findContext/findDetail`); thêm `callerId` (`findByTeam` tin + trận ngoài), `cancelWindowHours` (`findApplicants`, `findReputation`, `UserRepository.refreshVerification`) | Spring Data từ chối tham số đặt tên không có trong câu; thống kê cá nhân cần tham số cửa sổ | CCD §5.2 |
| 3 | P3 | `findMercenaryReports`/`count…` một method | 4 câu cố định + method `default` chọn câu theo (đối tượng, chiều) | Đúng ý "bốn câu cố định, không nối chuỗi" | CCD §5.2 |
| 4 | P3 | `ScoreChangeView` (`teamId` + `subjectType`), `VerificationChangeView` (`teamId`) | `getSubjectType`/`getSubjectId`, `getSubjectId`/`getVerified` | Dùng chung đội + cá nhân | CCD §4.12, §5.5; REPUTATION-001 §5.3 |
| 5 | P3 | `RecruitContextView.activePositions` mảng; `RecruitmentWriteContextView` shape riêng | Chuỗi nối `|`; `RecruitmentWriteContextView extends RecruitmentView` (thêm `teamStatus`, sân, tên người gọi vào context) | Tránh rủi ro map mảng của projection; response 2.5/2.6 dựng từ context không đọc lại | CCD §5.5 |
| 6 | P3 | `GAME_CANCELLED/CONFIRMED/ENDED` dạng `m… OR x…` | `COALESCE(m.status, x.status)` / `COALESCE(…, false)` | An toàn NULL (một trong hai join luôn NULL); cùng ngữ nghĩa | CCD §5.3 |
| 7 | P8 | `rejectOrphanPending` RETURNING `id, recruitment_id` | RETURNING thêm `applicant_id`, `match_id`, `external_match_id`, `team_id` (projection `OrphanRejectedApplicationView`) | Đủ payload `MercenaryApplicationRejectedEvent`; vẫn 1 câu | PF §3.12 bước 2, CCD §4.9 |
| 8 | P4 | Tên DTO phụ (`AreaResponse`, `VenueRevealResponse`…) | + `MercenaryTeamBriefResponse`, `MercenaryUserBriefResponse`, `ApplicantBriefResponse`, `RecruitmentCountResponse`; record lồng `UserReputationResponse.Completion/Rating/Verification`, `ApplicationResponse.Recruitment/Match` | Tránh trùng tên DTO REPUTATION-001 (`TeamBriefResponse`, `CompletionResponse`…) khi tích hợp | CCD §2, §7 |
| 9 | P4 | 2.31: `requireSelectable` trước CAS, kiểm 025 trên tin đã khoá | Thêm kiểm sớm `hasApprovedMercenary` của context → `MERCENARY_025` trước khi đọc sân (vẫn kiểm lại sau khi khoá tin) | Bảng thứ tự guard đặt 025 trước VENUE_001/002 | PF §3.13 |
| 10 | P4 | 2.31 cặp giờ "kiểm ở service" | `Validator` kiểm record `ExternalMatchSchedule` (cùng `@ValidExternalMatchSchedule`) → `ConstraintViolationException` → 400 `VALIDATION_ERROR` gắn `startsAt`/`endsAt` | Một nguồn quy tắc giờ | CCD §7 |
| 11 | P4 | 2.32 luôn phát `MercenaryGameCancelledEvent` | Chỉ phát khi có đơn APPROVED vừa đánh dấu | Không có người nhận | CCD §4.15 |
| 12 | P0 | `ReputationRules` (REPUTATION-001) | Bản tạm thêm `CANCEL_WINDOW_HOURS = 24`, `LEVEL_*_MIN` | Thống kê cá nhân / mức điểm cần hằng một nơi | REPUTATION-001 §4.6 |

## Câu hỏi chờ duyệt
_Quy tắc user 2026-11-21: câu hỏi CHẶN → ghi tại đây rồi dừng, báo về; không chặn → ghi và làm tiếp._

| # | Vị trí | Vấn đề | Phương án | Chặn? |
|---|---|---|---|---|
| C1 | `api/.../mercenary/RecruitmentController.java` (`listApplicants`, `ApplicationStatusFilter`) | 2.8 nhận thêm `status=ACTIVE` (hợp đồng chỉ PENDING/APPROVED/REJECTED/WITHDRAWN/ALL) vì dùng chung enum với 2.9 | (a) giữ (lỏng hơn hợp đồng); (b) tách enum riêng cho 2.8 → 400 | Không — đang làm (a) |
| C2 | Test tích hợp | Chỉ viết `MercenaryMigrationTest`, `MercenaryRepositoryIntegrationTest` (luồng trận ngoài + bảng kiểm điểm hoàn thành), `MercenaryNfsQueryCountTest` (luồng trận ngoài); sửa `NfsWriteQueryCountTest`, `ProfileServiceIntegrationTest` (xoá tài khoản 3 → 5 câu). Luồng kèo, race, controller, khối chat, trận sạch đội cần fixture/code V7–V9 | Viết nốt ở giai đoạn tích hợp khi có code MATCH-001/REPUTATION-001 | Không |
| C3 | `domain/.../MercenarySql.java` `CONVERSATION_MERCENARY_*` | Khối cột / join mercenary cho `findContext`/`findMine` chỉ là hằng, chưa cắm vào `ConversationSql` (MATCH-001) | Cắm khi tích hợp | Không |

## Tái sử dụng
_Reuse Report (skill `reuse-scan`, 2026-11-21). Nguồn quét: `common`, `domain`, `application`, `api` `*/src/main/java` trên `origin/main` `6fe6285` bằng `grep -rln` + đọc file ứng viên. Từ khoá: Converter, AttributeConverter, Position, Haversine, ValidGeoQuery, SkillLevels, afterCommit/TransactionSynchronization, Concurrency, Rules, Penalty, Verification/verified, Sql, maskName/maskAvatar, Guard, Unique, ceil/round, radiusKm, PageRequestFactory, ApplicationEventPublisher._
_**Vùng mù:** class của 6 mã đang code song song (SYSTEM-002, MATCH-001, MATCH-002, REPUTATION-001, NOTIFICATION-001, USER-003) — chưa có trên `main`, chỉ đối chiếu được qua thiết kế của chúng._

| Thành phần cần | Phân loại | Nguồn / thay đổi dự kiến |
|---|---|---|
| Khung retry + transaction mỗi lần thử | DÙNG LẠI | `application/support/ConcurrencyConflictRetrier#execute(ErrorCode, Supplier)` với `MATCH_014` |
| Bắt `ConcurrencyConflictException` trong retrier | MỞ RỘNG (thuộc MATCH-002 #5) | Phụ thuộc tạm — xem mục dưới |
| Map tên constraint → mã lỗi | DÙNG LẠI | `application/support/DataIntegrityViolations#violates(e, "idx_…")` (`MERCENARY_009/017/022/023`) |
| Phân trang | DÙNG LẠI | `application/support/PageRequestFactory#create(page, pageSize)`, `PageResponse.from(...)` |
| Che tên / ảnh user đã xoá | DÙNG LẠI | `application/mapper/MapperSupport#maskName`, `#maskAvatar` (`uses = MapperSupport.class`) |
| `skill_levels` JSON ↔ `List<String>` | DÙNG LẠI | `domain/entity/converter/SkillLevelsConverter` (entity `ExternalMatch`), `MapperSupport#skillLevels`, `@ValidSkillLevels` |
| `@OneOf` (vị trí, `expectedStatus`, report type) | DÙNG LẠI | `application/validation/OneOf` |
| Kiểm cặp `lat`/`lng`, `radiusKm` cần toạ độ (2.3) | MỞ RỘNG | `application/validation/ValidGeoQuery(+Validator)` đang gắn cứng `SearchVenueRequest` → tổng quát hoá: interface `GeoQuery { Number lat(); Number lng(); Number radiusKm(); }` (`SearchVenueRequest` + `SearchRecruitmentsRequest` implement); không tạo `@ValidMercenarySearch` (Lệch #1, chờ Q2) |
| Hộp giới hạn toạ độ từ bán kính | MỞ RỘNG | Logic inline trong `VenueServiceImpl#listVenues` (111.32 km/độ) → tách `application/support/GeoBoundingBox` (record `of(lat, lng, radiusKm)`), `VenueServiceImpl` gọi lại; chờ Q3 (MATCH-001 có thể tách song song) |
| Biểu thức Haversine SQL | VIẾT MỚI (hằng) | Chỉ có inline trong `VenueRepository` (native, không tham chiếu được từ `@Query` khác) → `MercenarySql.DISTANCE_KM` (hằng `static final String` ghép compile-time); không sửa `VenueRepository` |
| Làm tròn khoảng cách 0,5 km | VIẾT MỚI | `VenueMapper#roundDistance` làm tròn 2 chữ số HALF_UP — khác quy tắc (`ceil(d/0.5)*0.5`, tối thiểu 0.5) → method default trong `MercenaryMapper` |
| `PositionConverter` (enum ↔ nhãn VN) | VIẾT MỚI | Chỉ có `SkillLevelsConverter` (JSON list) — không khớp; `preferredPosition` của `User` là `String` + `@OneOf` → giữ nguyên |
| `@UniquePositions` | VIẾT MỚI | Không có validator "phần tử duy nhất theo field"; `@UniqueElements` (Hibernate Validator) so cả record (`position`+`quantity`) → không dùng được |
| `@ValidExternalMatchSchedule` | VIẾT MỚI (so sánh `@ValidMatchSchedule` MATCH-001 — vùng mù) | Dùng hằng `MatchRules.MIN_LEAD_TIME`, `MAX_DURATION` (MATCH-001) — không định nghĩa lại giá trị |
| `MercenarySql` (GAME_*, trạng thái hiệu lực, `OVERLAPPING_APPROVAL`, `MEMBER_OF_GAME_TEAMS`, `USER_STATS`) | VIẾT MỚI | Không có hằng SQL dùng chung nào trên `main`; mẫu `ConversationSql` / `TeamReputationSql` thuộc mã khác (vùng mù) |
| `MercenaryGuard`, `MercenarySide`, `MercenaryRules` | VIẾT MỚI | `TeamGuard` / `VenueGuard` là guard theo miền khác; quyền đội dùng lại `TeamGuard` (method MATCH-001 thêm) |
| `TimeBandPenaltyPolicy.MERCENARY_WITHDRAWAL` | MỞ RỘNG (class MATCH-001) | Thêm hằng `(-1, -3, -5)` vào record MATCH-001 (phụ thuộc tạm cho record) |
| `VerificationRefresher` (đội + cá nhân) | MỞ RỘNG (class REPUTATION-001 `TeamVerificationRefresher`) | Mẫu `afterCommit` có ở `AdminAuditLog`; đổi tên + thêm tập `users` — xem Q5 |
| Audit admin sau commit | DÙNG LẠI | `AdminAuditLog#afterCommit(String, UUID, String, UUID)` (overload MATCH-001 — phụ thuộc tạm) |
| `ReportStatusResolver`, `ReportPenaltyPolicy`, `ReputationRules` | DÙNG LẠI (REPUTATION-001) | Phụ thuộc tạm — Q6 |
| `MercenaryApplicationConversationPolicy` | VIẾT MỚI (cài `ConversationAccessPolicy` MATCH-001) | Interface thuộc MATCH-001 — phụ thuộc tạm |
| `MercenaryAccountCleanup` | VIẾT MỚI | Gọi từ `ProfileServiceImpl#deleteAccount` (có trên `main`) |
| `MercenaryMapper`, `MercenaryReputationMapper` | VIẾT MỚI (MapStruct, `uses MapperSupport`) | Làm tròn / mức điểm dùng chung `ReputationMapper` (REPUTATION-001 — vùng mù; gom khi tích hợp) |
| `AdminMercenaryDisputeService` | VIẾT MỚI + gom phần chung với `AdminDisputeServiceImpl` (REPUTATION-001) khi tích hợp | CCD §4.6 |
| `ApplicationEventPublisher` cho domain event | DÙNG LẠI | Mẫu `VenueAlertServiceImpl`, `VenueServiceImpl` |

## Kế hoạch chi tiết

### NFS gate (đọc `non-functional-standards.md` trước khi lập)
Số truy vấn mỗi API lấy theo PF §3 "Bảng số truy vấn" — 33/33 trong ngưỡng; chạm ngưỡng: **2.27 `REJECTED` = 6/W6**; USER-001 2.7 tăng 3 → 5/W6; 2.13 `APPROVED` 5/W6; 2.20 5/W6; 2.28 5/W6; 2.32 5/W6. Kế hoạch không thêm truy vấn nào ngoài bảng. Không có endpoint vượt → không dừng ở cổng NFS. Đo bằng log SQL ở giai đoạn tích hợp (`MercenaryNfsQueryCountTest`). Retry nhân tối đa 3 (ADR-0003).

### Tiến độ phase (giai đoạn 2 — user duyệt 2026-11-21)
- [x] P0 — phụ thuộc tạm (unit PASS: AppTimeTest 2, ErrorCodeTest 5, ConcurrencyConflictRetrierTest 12, SearchVenueRequestValidationTest 2)
- [x] P1 — V10, entity, `PositionConverter`, `MERCENARY_*` (unit PASS: PositionConverterTest 4, MercenaryEntityTest 7, EntityIdGenerationTest 1, UserEntityTest 2)
- [x] P2 — `MercenaryGuard`/`Rules`/`Side`, validator (unit PASS: MercenaryGuardTest 8, TimeBandPenaltyPolicyTest 6, MercenaryValidatorsTest 3, GeoBoundingBoxTest 1, CustomValidatorsTest 3, TeamGuardTest 6)
- [x] P3 — `MercenarySql`, 3 repository mới + Rating/Report/Dispute/User/Team repository, 24 projection (unit PASS: MercenarySqlTest 6; SQL chạy ở tích hợp)
- [x] P4 (service, DTO, mapper, event; controller gộp làm ở P8b) — unit PASS: MercenaryMapperTest 12, MercenaryRecruitmentServiceImplTest 15, ExternalMatchServiceImplTest 11, MercenaryRequestValidationTest 5
- [x] P5 (service, policy chat, `VerificationRefresher`) — unit PASS: MercenaryApplicationServiceImplTest 13, MercenaryApplicationConversationPolicyTest 11, VerificationRefresherTest 5
- [x] P6 — unit PASS: ReputationServiceImplTest 8, MercenaryRatingServiceImplTest 6, MercenaryReportServiceImplTest 13, UserReputationServiceImplTest 3
- [x] P7 — unit PASS: AdminMercenaryDisputeServiceImplTest 5 (phần sửa REPUTATION-001 §4.12: áp khi tích hợp)
- [x] P8 — job, `MercenaryAccountCleanup`, `ProfileServiceImpl`, cấu hình, 8 controller — unit PASS: MercenaryLifecycleJobServiceImplTest 5, MercenaryAccountCleanupTest 2, ProfileServiceImplTest 5, MercenaryLifecyclePropertiesTest 1
- [x] P9 (unit) — `./mvnw -o -Duser.timezone=Asia/Ho_Chi_Minh -pl application -am -Dtest=<37 lớp unit> -Dsurefire.failIfNoSpecifiedTests=false test` → application `Tests run: 209, Failures: 0, Errors: 0` BUILD SUCCESS; `./mvnw -o -q -Duser.timezone=Asia/Ho_Chi_Minh test-compile` toàn repo → exit 0. Dừng trước `code-reviewer`. · [ ] P4 · [ ] P5 · [ ] P6 · [ ] P7 · [ ] P8 · [ ] P9 (unit)

### Quy ước chung cho mọi phase
- TDD: mỗi phase viết test trước (Red, fail đúng lý do) → code tối thiểu (Green) → refactor. Lệnh unit: `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -pl <module> -am -Dtest=<Test> -Dsurefire.failIfNoSpecifiedTests=false test`.
- Mọi thời điểm `LocalDateTime` giờ VN; `now = LocalDateTime.now(clock)` một lần đầu mỗi lần thử; entity callback `AppTime.now()`; SQL chỉ dùng `:now`.
- Comment tiếng Việt, không trỏ ADR / BR / mục tài liệu; không FQN; không wildcard import.
- Câu `… RETURNING` khai native trả projection, không `@Modifying` (MATCH-002 #4 — kiểm khi tích hợp).
- Sau mỗi phase: `progress-tracker`.

### P0 — Phụ thuộc tạm (compile-only, đúng package/tên/chữ ký của mã sở hữu)
Tạo đúng danh sách ở mục "Phụ thuộc tạm" bên dưới. Không logic nghiệp vụ của mã sở hữu; test duy nhất: `ConcurrencyConflictRetrierTest` thêm ca "ném `ConcurrencyConflictException` lần 1 → lần 2 thành công; hết lượt → mã truyền vào" (cần cho mọi luồng CAS của mã này).

### P1 — Domain: migration V10, entity, converter, mã lỗi
- Tạo: `api/src/main/resources/db/migration/V10__mercenary_recruitment_chat_reputation.sql` (đúng CCD §5.1; FK V1 giữ; tên index V1 kiểm từ `V1__initial_schema.sql`).
- Tạo: `domain/entity/converter/PositionConverter.java`; `domain/entity/mercenary/ExternalMatch.java`.
- Sửa: `domain/entity/mercenary/MercenaryRecruitment.java` (enum `Position` 4 hằng + nhãn, `@Convert`, `matchId` nullable, + `externalMatchId`, `createdByUserId`, `closedAt`, cột trạng thái/số lượng `updatable = false`, thời gian → `LocalDateTime` + `AppTime.now()`), `MercenaryApplication.java` (`updatable = false`, + `gameCancelNotifiedAt` read-only, `LocalDateTime`), `rating/Rating.java`, `rating/Report.java` (+ `externalMatchId`, `applicationId`, `matchId` nullable — trên bản tạm theo REPUTATION-001 §6), `user/User.java` (+ `verifiedAt` read-only).
- Sửa: `common/exception/ErrorCode.java` (+ `MERCENARY_001..025`, message/HTTP theo API_Spec §5).
- Test viết trước (unit): `domain/entity/converter/PositionConverterTest` (4 nhãn hai chiều, nhãn lạ → `IllegalArgumentException`, `null`); `domain/entity/mercenary/MercenaryEntityTest` (builder default `OPEN`/`PENDING`; cột CAS `updatable = false`; `verifiedAt`, `gameCancelNotifiedAt` `insertable = false` — đọc annotation bằng reflection như `TeamEntityTest`); `ExternalMatchEntityTest` (`@Version`, `skillLevels` default `List.of()`, `@PrePersist` dùng `AppTime`); `ErrorCodeTest` (+ 25 mã, HTTP, message).

### P2 — Support dùng chung của mã
- Tạo `application/support/mercenary/`: `MercenaryRules`, `MercenarySide`, `MercenaryGuard` (CCD §4.3), `MercenaryAccountCleanup` (P8).
- Sửa (bản tạm MATCH-001): `application/support/reputation/TimeBandPenaltyPolicy` + `MERCENARY_WITHDRAWAL`.
- Validation: tạo `UniquePositions` + `UniquePositionsValidator`, `ValidExternalMatchSchedule` + validator (Spring bean, `Clock`; lỗi gắn `startsAt`/`endsAt`); sửa `ValidGeoQuery` / `ValidGeoQueryValidator` + tạo `GeoQuery` (chờ Q2); tạo `application/support/GeoBoundingBox` + sửa `VenueServiceImpl` dùng lại (chờ Q3).
- Test viết trước (unit): `MercenaryGuardTest` (từng mã lỗi + thứ tự `requireCaptain` DISBANDED→INACTIVE→captain, `requireRecruitableMatch` MATCH_009→003→004→013, `resolveSide` cả hai → `MERCENARY_019`, không bên → `MERCENARY_014`); `TimeBandPenaltyPolicyMercenaryTest` (bảng kiểm PF §3.9: 30h/24h → 1; 23h59/4h → 3; 3h59/1 phút → 5); `CustomValidatorsTest` (+ `UniquePositions` trùng/không trùng/null; `ValidExternalMatchSchedule` biên `now+1h`, `end ≤ start`, 12h đúng / 12h+1 phút; `ValidGeoQuery` cho cả hai request); `GeoBoundingBoxTest` (so giá trị cũ của `VenueServiceImpl`); `VenueServiceImplTest` hiện có vẫn PASS.

### P3 — Repository, `MercenarySql`, projection (SQL; test chạy khi tích hợp)
- Tạo `domain/repository/`: `MercenarySql`, `MercenaryRecruitmentRepository`, `MercenaryApplicationRepository`, `ExternalMatchRepository` (method + SQL đúng CCD §5.2, §5.3, PF §3.12 bước 2–3, §3.16).
- Sửa: `UserRepository` (+ `findProfileArea`, `findReputation`, `applyMercenaryCompletion`, `applyReportPenalty`, `refundReportPenalty`, `refreshVerification`, `findIdsForVerification` — mẫu `TeamRepository` REPUTATION-001 §5.2 trên `"user"`); `RatingRepository` (+ `findMercenaryRatingContext`, `findMercenaryRatingsOfTeam`), `ReportRepository` (+ `findMercenaryReportContext`, `findMercenaryReports`, `countMercenaryReports`), `DisputeRepository` (+ `resolveMercenaryIfPending`, `existsMercenaryDispute`, `findAdminMercenaryDisputes`, `countAdminMercenaryDisputes`, `findAdminMercenaryDisputeDetail`) — ba repository là bản tạm REPUTATION-001 + phần mercenary của mã này.
- Tạo `domain/projection/` (23 view CCD §5.5): `RecruitContextView`, `RecruitmentRowView`, `RecruitmentView`, `RecruitmentWriteContextView`, `ApplyContextView`, `ApplicationContextView`, `ApplicationView`, `ApplicantRowView`, `WithdrawnApplicationView`, `ApprovedCountView`, `RejectedApplicationView`, `GameCancelNoticeView`, `DueRecruitmentView`, `MercenaryRatingContextView`, `MercenaryReportContextView`, `MercenaryReportView`, `AdminMercenaryDisputeView`, `AdminMercenaryDisputeDetailView`, `UserReputationView`, `TeamMercenaryRatingView`, `ProfileAreaView`, `ExternalMatchContextView`, `ExternalMatchView`.
- `MercenarySql` thêm hằng khối `MERCENARY_APPLICATION` cho `ConversationRepository.findContext` / `findMine` (MỞ RỘNG — cắm vào `ConversationSql` của MATCH-001 khi tích hợp).
- Test viết trước (unit, thuần chuỗi): `MercenarySqlTest` — hằng ghép đúng alias (`GAME_JOIN` cho `r`/`r2`/`rc`), không chứa `now()`/`CURRENT_TIMESTAMP`, không nối chuỗi tên cột động. Test repository: để tích hợp (mục dưới).

### P4 — Tin tuyển + trận ngoài (2.1–2.6, 2.28–2.33)
- Tạo service: `MercenaryRecruitmentService(+Impl)`, `ExternalMatchService(+Impl)`, `RecruitmentStatusFilter`, `ExternalMatchStatusFilter`.
- DTO request: `CreateRecruitmentsRequest`, `RecruitmentPositionRequest`, `AddRecruitmentsRequest`, `UpdateRecruitmentRequest`, `SearchRecruitmentsRequest`, `CreateExternalMatchRequest`, `UpdateExternalMatchRequest`. Response: `RecruitmentResponse` (+ `RecruitmentMatchResponse`, `VenueRevealResponse`, `AreaResponse`), `ExternalMatchResponse`, `ExternalMatchCancellationResponse`.
- Mapper: `MercenaryMapper` (ẩn/hiện sân + đội trưởng, `source`, làm tròn 0,5 km, `maskName`).
- Controller: `api/mercenary/MatchRecruitmentController` (2.1), `RecruitmentController` (2.3–2.6), `TeamMercenaryController` (2.2, 2.28, 2.29), `ExternalMatchController` (2.30–2.33).
- Event: `MercenaryRecruitmentClosedEvent`, `MercenaryGameCancelledEvent`.
- Test viết trước (unit, Mockito + `Clock.fixed(…, AppTime.ZONE)`): `MercenaryRecruitmentServiceImplTest` (thứ tự guard 2.1/2.5/2.6/2.33; `MERCENARY_023` từ `activePositions` và từ `DataIntegrityViolationException`; 2.5 `q == needed` không ghi; tăng số khi `now ≥ start` → `MERCENARY_013`; CAS 0 dòng → `ConcurrencyConflictException`; 2.6 phát event `CAPTAIN` kèm id đơn bị từ chối; 2.3 không GPS: `USER_001`, `MERCENARY_018`; có GPS gọi hộp đúng); `ExternalMatchServiceImplTest` (2.28 thứ tự Q + `markSelected`; 2.31 chỉ đổi `opponentName` bỏ Q2/Q4/Q5, `sum(quantity_approved) > 0` → `MERCENARY_025`, `updateIfVersion` 0 dòng → retry; 2.32 `now ≥ start` → `MERCENARY_013`, phát `ClosedEvent` mỗi tin + `GameCancelledEvent`); `MercenaryMapperTest` (BR-ME04 bảng hiện/ẩn; `distanceKm` 0.01→0.5, 0.5→0.5, 0.51→1.0; `source`); `RequestDtoValidationTest` (+ DTO mới: trim, `""`→`null`, `@Size(max=4)`, quantity 1..10, `radiusKm` 1..20, mặc định `page=1`, `pageSize=20`, `radiusKm=3` khi có toạ độ).

### P5 — Đơn ứng tuyển + chat theo đơn (2.7–2.13; ngữ cảnh chat)
- Tạo service: `MercenaryApplicationService(+Impl)`, `ApplicationStatusFilter`; DTO `WithdrawApplicationRequest`; response `ApplicationResponse`, `ApplicantResponse` (+ `ReputationSummaryResponse`), `ApprovalResponse`, `WithdrawalResponse`.
- Controller: `ApplicationController` (2.10–2.13), `RecruitmentController` thêm 2.7, 2.8; `ProfileMercenaryController` (2.9).
- Event: `MercenaryApplicationSubmittedEvent`, `…ApprovedEvent`, `…RejectedEvent`, `…WithdrawnEvent`.
- Chat: tạo `application/support/chat/MercenaryApplicationConversationPolicy`; sửa (bản tạm MATCH-001) `RealtimeFrame` + `ApplicationUpdated`, `Conversation` + `applicationId`, `externalMatchId`, `ContextType.MERCENARY_APPLICATION`, `ConversationContextView` + cột mercenary.
- Test viết trước (unit): `MercenaryApplicationServiceImplTest` (2.7 thứ tự guard `USER_001`→…→`MERCENARY_011`, `MERCENARY_009` từ index, tạo `Conversation` đúng bên A/B + `matchId`/`externalMatchId`; 2.11 CAS tin + đơn, 0 dòng → retry, frame + event, response `FILLED`; 2.12; 2.13 `PENDING` penalty 0, `APPROVED` gọi `adjustUserReliability(delta âm, MERCENARY_WITHDRAWN_AFTER_APPROVED, breaksCleanStreak=true)` + `refreshUsersAfterCommit`, response `reliabilityPenalty = -delta`, `expectedStatus` sai → `MERCENARY_012`, `now ≥ start` → `MERCENARY_013`); `MercenaryApplicationConversationPolicyTest` (đội trưởng ACTIVE → A gửi được khi đơn hiệu lực `PENDING`/`APPROVED`; `REJECTED`/`WITHDRAWN` → chỉ xem; người giải tán đội → A `viewerReadOnly`; ứng viên → B, kiểm trước; thành viên khác → `null`; `expiryDue` luôn `false`).

### P6 — Đánh giá, report, uy tín cá nhân (2.18–2.24)
- Tạo service: `MercenaryRatingService(+Impl)`, `MercenaryReportService(+Impl)`, `UserReputationService(+Impl)`, `MercenaryReportDirection`; DTO `CreateMercenaryRatingRequest`; response `MercenaryRatingResponse`, `MercenaryRatingsResponse`, `MercenaryReportResponse`, `UserReputationResponse`, `TeamMercenaryRatingResponse`; mapper `MercenaryReputationMapper`.
- Sửa (bản tạm MATCH-001/REPUTATION-001 + phần của mã này): `ReputationService(+Impl)` thêm `applyMercenaryCompletion`, `applyUserReportPenalty`, `refundUserReportPenalty`; `ReliabilityReason` + 2 giá trị; tạo `VerificationRefresher` (đội + cá nhân), `UserVerificationChangedEvent`.
- Controller: `ApplicationController` + 2.18–2.20; `TeamMercenaryController` + 2.21, 2.24; `ProfileMercenaryController` + 2.22; `UserReputationController` (2.23).
- Event: `MercenaryRatingCreatedEvent`, `MercenaryReportCreatedEvent`.
- Test viết trước (unit): `MercenaryRatingServiceImplTest` (thứ tự guard 2.18; chấm sao gọi `applyMercenaryCompletion(bonus = score==5 ? 1 : 0)` + refresh; "Không đến"/phía mercenary không gọi; mercenary `isNoShow=true` → `MERCENARY_016`; `MERCENARY_017` từ index; 2.19 `received` chỉ khi đã có `given`); `MercenaryReportServiceImplTest` (bảng kiểm ngữ cảnh report PF §3.11; người giải tán đội → `TEAM_016`; captain → `applyUserReportPenalty`, ứng viên → `applyReportPenalty` đội; `setReliabilityPenalty(points)`); `UserReputationServiceImplTest`; `ReputationServiceImplMercenaryTest` (event khi điểm đổi, 0 dòng → `IllegalStateException`; tham số hằng `ReputationRules`); `VerificationRefresherTest` (fake `PlatformTransactionManager` + `TransactionSynchronizationManager`: lần thử 1 rollback → lần 2 commit gọi `refreshNow` đúng 1 lần; hai "request" liên tiếp cùng thread mỗi lần đúng tập id; tối đa 1 câu đội + 1 câu cá nhân; lỗi → `WARN` không ném); `MercenaryReputationMapperTest`.
- Bảng kiểm `applyMercenaryCompletion` (PF §3.11) là test SQL → để tích hợp.

### P7 — Admin tranh chấp mercenary (2.25–2.27)
- Tạo `AdminMercenaryDisputeService(+Impl)`, response `AdminMercenaryDisputeResponse`, `AdminMercenaryDisputeDetailResponse`, controller `api/mercenary/AdminMercenaryDisputeController`.
- Test viết trước (unit): `AdminMercenaryDisputeServiceImplTest` (CAS rỗng → `existsMercenaryDispute` false `DISPUTE_001` / true `DISPUTE_002`; `REJECTED` `INDIVIDUAL` → `refundUserReportPenalty`, `TEAM` → `refundReportPenalty` + refresh đúng loại; `CONFIRMED` không hoàn; audit `afterCommit`).
- Phần sửa REPUTATION-001 (CCD §4.12): **MỞ RỘNG áp khi tích hợp** (class chưa có) — danh sách ở mục "Phụ thuộc tạm" (cột "MỞ RỘNG khi tích hợp").

### P8 — Job, xoá tài khoản, cấu hình
- Tạo `MercenaryLifecycleJobService(+Impl)` (3 method, mỗi lô `TransactionTemplate`, đọc `arenaflow.mercenary.lifecycle.*`), `application/support/mercenary/MercenaryLifecycleProperties` (`@ConfigurationProperties`).
- Tạo `MercenaryAccountCleanup`; sửa `ProfileServiceImpl#deleteAccount` (gọi sau `leaveAndRejectAllOpen`, trước `softDelete`).
- Sửa `api/src/main/resources/application.properties` (+ 3 khoá CCD §8).
- Gắn vào `MatchLifecycleScheduler` (sau `archiveFinishedMatches`) và vòng cá nhân của `VerificationJobService`: **MỞ RỘNG khi tích hợp** (class MATCH-001/REPUTATION-001).
- Test viết trước (unit): `MercenaryLifecycleJobServiceImplTest` (tách `closedIds`/`expiredIds` theo `cause`; dừng khi lô < `batchSize` hoặc hết `maxBatches`; event mỗi tin / mỗi đơn / mỗi trận (gom theo trận); lỗi một lô log `ERROR`, lô sau vẫn chạy); `MercenaryAccountCleanupTest` (số `wasApproved` theo tin ≠ `n` → `ConcurrencyConflictException`; event `ACCOUNT_DELETED` mỗi đơn); `ProfileServiceImplTest` (+ thứ tự gọi `leaveAndRejectAllOpen` → cleanup → `softDelete`); `MercenaryLifecyclePropertiesTest`.

### P9 — Kiểm chứng giai đoạn 2
- `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh test` chỉ unit (loại `*IntegrationTest`, `*RepositoryTest`, `*MigrationTest`, `*Nfs*`, `*Race*`, `ControllerTest`) → dán output; `code-reviewer` phần chưa review; cập nhật state.

### Ước lượng file
~95 file main mới, ~22 file main sửa, ~30 file phụ thuộc tạm, ~30 file test unit mới/sửa, ~12 file test tích hợp (viết, chưa chạy) → **≈ 190 file**.

## Phụ thuộc tạm (thuộc mã khác)
_Bản tối thiểu đúng package / tên / chữ ký thiết kế của mã sở hữu, không logic nghiệp vụ của mã đó. Khi tích hợp: bản của mã sở hữu thắng; phần "MỞ RỘNG" của mã này áp lên bản đó._

| File | Mã sở hữu | Phần tạo tạm | MỞ RỘNG của MERCENARY-001 (áp khi tích hợp) |
|---|---|---|---|
| `common/.../common/time/AppTime.java` | SYSTEM-002 | `ZONE`, `ZONE_ID`, `now()`, `today()` | — |
| `api/.../config/ClockConfig.java` | SYSTEM-002 | bean `Clock` zone VN | — |
| `MercenaryRecruitment`, `MercenaryApplication`, `Rating`, `Report` (thời gian → `LocalDateTime`) | SYSTEM-002 | đổi kiểu field thời gian + callback `AppTime.now()` | Cột mercenary (P1) — trộn tay khi tích hợp |
| `VenueSelectionService#markSelected(…, LocalDateTime)` | SYSTEM-002 | overload `default` đổi kiểu (không sửa impl) | — |
| `ProfileServiceImpl#deleteAccount` `now` | SYSTEM-002 (`Clock`) | — | Gọi `MercenaryAccountCleanup` (P8) |
| `common/exception/ErrorCode` `MATCH_001`, `MATCH_009`, `MATCH_014`, `CHAT_002..004` | MATCH-001 | hằng mã | — |
| `common/exception/ErrorCode` `DISPUTE_001`, `DISPUTE_002`, `REPORT_009` | REPUTATION-001 | hằng mã | — (Q7) |
| `application/support/ConcurrencyConflictException` + retrier bắt nó | MATCH-002 (#5) | exception + 1 `catch` | — |
| `application/validation/GeoQuery` + `ValidGeoQueryValidator` (nhận `GeoQuery`), `SearchVenueRequest implements GeoQuery`; `application/support/GeoBoundingBox` | MATCH-001 (user chốt Q2/Q3) | interface + validator tổng quát + record hộp toạ độ (chưa sửa `VenueServiceImpl`) | `SearchRecruitmentsRequest implements GeoQuery` |
| `domain/repository/TeamReputationSql` | REPUTATION-001 | `STATS_LATERAL` (completed + sao TB, đã có `application_id IS NULL`) | — |
| `TeamRepository.adjustReliability` — không tạo; `UserRepository.adjustReliability` | MATCH-001 | câu UPDATE | — |
| `application/service/impl/ReputationServiceImpl` | MATCH-001 / REPUTATION-001 | `adjustUserReliability`, `applyReportPenalty`, `refundReportPenalty` (đội) | + 3 method cá nhân |
| `ReportRepository.applyDecision`, `ReportDecisionView` | REPUTATION-001 | câu CAS | RETURNING `reported_type`, `reporter_type` |
| `DisputeRepository.existsTeamDispute` | REPUTATION-001 §4.12 | câu EXISTS | — |
| `ProfileServiceImpl` thêm `Clock` | SYSTEM-002 | `OffsetDateTime.now(clock)` | gọi `MercenaryAccountCleanup` |
| `application/support/reputation/TimeBandPenaltyPolicy` | MATCH-001 | record + `penaltyFor` | + `MERCENARY_WITHDRAWAL` |
| `application/support/match/MatchRules` | MATCH-001 | `MIN_LEAD_TIME`, `MAX_DURATION` | — |
| `application/service/ReputationService`, `ReliabilityChange`, `ReliabilityReason`, `impl/ReputationServiceImpl` | MATCH-001 / REPUTATION-001 | interface + record + enum; impl chỉ các method mã này gọi | + `applyMercenaryCompletion`, `applyUserReportPenalty`, `refundUserReportPenalty`, 2 giá trị enum |
| `application/event/reputation/ReliabilityScoreChangedEvent`, `ReputationSubjectType` | MATCH-001 | record | — |
| `application/support/reputation/ReportPenaltyPolicy`, `ReputationRules`, `ReportStatusResolver` | REPUTATION-001 | Q6 | — |
| `application/support/reputation/VerificationRefresher` (thay `TeamVerificationRefresher`) | REPUTATION-001 | phần đội (gọi `TeamRepository.refreshVerification`) | Đổi tên + tập `users` (Q5) |
| `application/event/reputation/TeamVerificationChangedEvent`, `DisputeResolvedEvent` | REPUTATION-001 | record (payload tổng quát §4.12) | Payload `reporterType/reporterId/reportedType/reportedId` |
| `domain/projection/ScoreChangeView`, `VerificationChangeView` | REPUTATION-001 | interface | + `subjectType` |
| `domain/entity/rating/Rating`, `Report` (shape V9) ; `Dispute` | REPUTATION-001 | field theo REPUTATION §6 | + `externalMatchId`, `applicationId`, `matchId` nullable |
| `domain/repository/RatingRepository`, `ReportRepository`, `DisputeRepository` | REPUTATION-001 | interface `JpaRepository` rỗng | + method mercenary (P3) |
| `TeamRepository.refreshVerification`, `applyReportPenalty`, `refundReportPenalty` | REPUTATION-001 | chữ ký (SQL REPUTATION) | — |
| `application/dto/request/CreateReportRequest`, `ResolveDisputeRequest`; `application/service/DisputeStatusFilter`; `validation/ValidRating` | REPUTATION-001 | record / enum / annotation | `ValidRating` tổng quát hoá qua interface (CCD §7) |
| `application/support/TeamGuard#requireActiveCaptain`, `#requireCaptainAllowInactive` + `TeamRepository.findCaptainContext` + `TeamCaptainContextView` | MATCH-001 | method + câu context | — |
| `application/support/AdminAuditLog#afterCommit(String, UUID, String, UUID)` | MATCH-001 | overload | — |
| `domain/entity/chat/Conversation` | MATCH-001 | entity theo MATCH-001 §6 | + `applicationId`, `externalMatchId`, `ContextType.MERCENARY_APPLICATION` |
| `domain/projection/ConversationContextView` | MATCH-001 | cột chung | + cột khối mercenary (CCD §5.5) |
| `domain/repository/ConversationRepository` | MATCH-001 | `JpaRepository` rỗng (2.7 chỉ `save`) | Khối mercenary của `findContext`, `findMine` (hằng ở `MercenarySql`) |
| `application/support/chat/ConversationAccessPolicy`, `ConversationAccess`, `ChatRealtimeGateway`, `RealtimeFrame` | MATCH-001 | interface / record / sealed | + `RealtimeFrame.ApplicationUpdated` |
| `application/mapper/ConversationMapper` | MATCH-001 | — (không tạo tạm) | Khối `MERCENARY_APPLICATION` (CCD §4.7) |
| `api/scheduler/MatchLifecycleScheduler` | MATCH-001 | — (không tạo tạm) | Gọi 3 method job sau `archiveFinishedMatches` |
| `VerificationJobService` / `VerificationScheduler` (đổi tên từ `TeamVerification…`) | REPUTATION-001 | — | Vòng cá nhân |
| REPUTATION-001 §4.12: `RatingContextView`, `MatchRatingRowView`, `findByMatchIdAndRaterType…`, `ReportContextView`, `findTeamReports`/`count…`, `findAdminDisputes`/`count…`/`findAdminDisputeDetail`/`resolveIfPending`, `AdminDisputeServiceImpl.resolveDispute` (`existsTeamDispute`), `TeamReputationSql.STATS`, `applyCompletion` (mệnh đề mercenary), `DisputeService.createDispute` + `DisputeContextView`, `applyDecision` RETURNING `reported_type` | REPUTATION-001 | — | Toàn bộ áp khi tích hợp (cần cột V10) |

## Test để giai đoạn tích hợp
_Viết ở giai đoạn 2, chỉ CHẠY khi tích hợp (cần DB dev sau V6–V10 / Spring context)._
_Đã viết (biên dịch được, chưa chạy): `api/.../migration/MercenaryMigrationTest`, `api/.../domain/repository/MercenaryRepositoryIntegrationTest`, `api/.../application/service/MercenaryNfsQueryCountTest`; đã sửa `NfsWriteQueryCountTest`, `ProfileServiceIntegrationTest` (xoá tài khoản 5 câu). Các dòng còn lại của bảng: viết khi tích hợp (C2)._

| Test | Nội dung |
|---|---|
| `migration/MercenaryMigrationTest` | V10 áp sau V9; `ddl-auto=validate`; CHECK XOR, `rating_application_check`, `report_application_check`, `conversation_*_check`, unique partial index; kiểm số dòng `mercenary_recruitment` trước V10 (#4) |
| `domain/repository/MercenaryRecruitmentRepositoryTest`, `MercenaryApplicationRepositoryTest`, `ExternalMatchRepositoryTest` | Mọi câu native: trạng thái hiệu lực, `insertAll` (bind `text[]`/`int[]` — #10), CAS, `claimDue` SKIP LOCKED, `markGameCancelNotified` (UNION ALL + khoá tầng ngoài), `decrementApprovedOfApplicant`/`withdrawActiveOfApplicant`, `PositionConverter` ghi `'Thủ môn'` |
| `domain/repository/UserReputationRepositoryTest` | Bảng kiểm `applyMercenaryCompletion` (PF §3.11), `findReputation` (§5.4 tử/mẫu số), `applyReportPenalty`/`refundReportPenalty`/`refreshVerification` trên `"user"` |
| `ConversationMercenaryContextTest` | Khối mercenary của `findContext`/`findMine`; người giải tán đội chỉ xem; 2.14–2.17 + `SUBSCRIBE` (PF §3.10) |
| `ReputationTeamCleanMercenaryTest` | Bảng kiểm trận sạch đội có mệnh đề mercenary (PF §3.11 bảng 2) |
| `application/service/MercenaryNfsQueryCountTest` | Đếm truy vấn 33 endpoint + USER-001 2.7 + job theo PF §3 |
| `MercenaryRaceTest` | Hai duyệt chỗ cuối; duyệt ↔ rút; xoá tài khoản ↔ duyệt; 2.31 ↔ duyệt; đóng tin ↔ job; tạo tin trùng vị trí |
| `api/mercenary/*ControllerTest` (8 controller) | Mã HTTP / envelope / Bean Validation / `AUTH_004` admin |
| `ProfileServiceIntegrationTest` (sửa) | Xoá tài khoản ứng viên (BR-ME18) — 5/W6 |
| `MercenaryLifecycleJobIntegrationTest` | 3 tác vụ job, nhiều instance không báo trùng |

## Blockers / Ghi chú
- **Chờ user duyệt kế hoạch** (cổng duy nhất) + các câu hỏi Q1–Q9 (trả về coordinator).
- `docs/agent/state/local-dev/*` đang được Git theo dõi (`.gitignore` dòng 36 `!docs/agent/state/*/`) — trái mô tả `CLAUDE.md` §7 "gitignore"; 7 nhánh song song cùng ghi đè file này → conflict khi tích hợp (Q9).
- Không có `.env.dev` trong worktree; V10 phụ thuộc V6–V9 → mọi test DB chạy ở giai đoạn tích hợp.
