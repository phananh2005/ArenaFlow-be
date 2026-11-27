---
task: MATCH-001 đợt 1 — kèo luồng A + lõi chat `conversation` + kênh realtime STOMP (V7)
created: 2026-11-21
status: in_progress
---

## Mô tả
Triển khai MATCH-001 **đợt 1** (`Component_Class_Design.md` §1.1) trên nhánh `feature/MATCH-001-match-flow-a-impl` (tạo từ `origin/main` `6fe6285`, worktree riêng): vòng đời kèo luồng A (2.1–2.12), bộ môn (2.13–2.15), lõi chat dùng chung (2.16–2.20), kênh realtime STOMP `/topic/conversations/{id}`, job hết hạn / `ARCHIVED`, migration `V7__match_flow_a_lifecycle.sql`. Không code nhánh luồng B (đợt MATCH-002, sau V8): 2.1 `type = NEED_VENUE` → `422 MATCH_016` trước truy vấn đầu tiên.

Bối cảnh song song (user chốt 2026-11-21): 7 mã chạy cùng lúc, mỗi mã một worktree/nhánh từ `origin/main`. SYSTEM-002 (V6) đứng trước → `AppTime`, `ClockConfig`, đổi kiểu thời gian entity **chưa có** trên `main`. Test tích hợp / test chéo làm ở giai đoạn tích hợp. Giai đoạn 1 (phiên này) = chỉ lập kế hoạch.

## Thiết kế liên quan
- `docs/agent/design/MATCH-001/` v3.0 — `API_Spec.md`, `Component_Class_Design.md` (§1.1 hai đợt — chỉ đợt 1), `Processing_Flow.md` (bảng số truy vấn §3, guard §1.1, race §5).
- `docs/agent/design/SYSTEM-002/Component_Class_Design.md` §2 (quy ước thời gian), §2.2 (`AppTime`, `ClockConfig`), §2.5 (query param `LocalDateTime` từ chối offset — "áp khi code MATCH-001"), §6.3 (`VenueSelectionService.markSelected` → `LocalDateTime`).
- `docs/agent/convention-backend.md` §1, §2.4 (comment tiếng Việt, không trỏ tài liệu), §2.5 (import), §6.1, §6.2 (giờ VN), §12.
- `docs/agent/non-functional-standards.md` — N_DETAIL=3, N_LIST=4, N_WRITE=6; §4.1 flush.
- ADR-0003, 0008, 0011, 0012, 0013, 0014, 0017.
- Thiết kế mã sau (chỉ để biết ranh giới, không code): MATCH-002 (nhánh luồng B), MERCENARY-001 (ngữ cảnh đơn ứng tuyển), NOTIFICATION-001 (`UserRealtimeGateway`, `/user/queue/notifications`, job xoá chat).

## Definition of Done
- [ ] Mọi thành phần đợt 1 trong `Component_Class_Design.md` §1.1–§9 được code theo TDD (Red → Green → Refactor).
- [ ] Unit test thuần (không Spring context/DB) PASS với `-Duser.timezone=Asia/Ho_Chi_Minh` — dán output.
- [ ] Test cần DB / Spring context / migration / NFS / race: **đã viết** (giai đoạn 2), **chạy ở giai đoạn tích hợp** — không ghi PASS trước khi chạy thật.
- [ ] Không vượt ngưỡng N_DETAIL/N_LIST/N_WRITE theo `Processing_Flow.md` §3 (đo bằng `MatchNfsQueryCountTest`, `ConversationNfsQueryCountTest` ở giai đoạn tích hợp).
- [ ] Bảng "Phụ thuộc tạm" đầy đủ; bản của mã sở hữu thắng khi tích hợp.
- [ ] `code-reviewer` không còn CRITICAL/MAJOR trước commit có code.
- [ ] Lệch thiết kế (nếu có) cập nhật design một lần sau khi code + test PASS, `architect-reviewer` review phần sửa; tài liệu §11 CCD đồng bộ đúng nhánh.

## Các bước thực hiện (tổng quan)
- [x] 1. Đối chiếu & phân tích ảnh hưởng (design / SYSTEM-002 / NFS / convention / code hiện có trên `main`)
- [x] 2. Quét tái sử dụng + lập kế hoạch chi tiết — file cần tạo/sửa (chờ user duyệt)
- [x] 3. Triển khai code + test theo phase (TDD) — Phase 0–9 xong 2026-11-21
- [x] 4. Chạy unit test & xác nhận PASS (common 13, domain 21, application 306 (2 skip có sẵn), api 13 lớp 43) — integration test để giai đoạn tích hợp
- [ ] 5. `code-reviewer` trên phần chưa review → sửa CRITICAL/MAJOR, test lại
- [ ] 6. Đối chiếu code với design → cập nhật design theo bảng "Lệch thiết kế" → `architect-reviewer` review phần sửa
- [ ] 7. Bước 6 có sửa code → test lại + `code-reviewer` phần sửa → commit (khi user yêu cầu)

## Lệch thiết kế
_Ghi ngay khi code khác design; KHÔNG sửa design giữa chừng — cập nhật một lần ở bước cuối (`CLAUDE.md` §8). Trống = code khớp design._

| # | Phase | Design | Code thực tế | Lý do | Sửa ở |
|---|---|---|---|---|---|
| 1 | 8, 9 | CCD §4.14 `send` gọi `userRealtimeGateway` (`CONVERSATION_ACTIVITY`); §4.15 `WebSocketConfig` `/queue` + `setUserDestinationPrefix`, interceptor nhận `/user/queue/notifications`; §4.10 scheduler gọi `MercenaryLifecycleJobService`, `NotificationJobService` | Không code ở đợt 1 | **LỆCH TẠM THỜI** — user chốt 2026-11-21 (B): thuộc NOTIFICATION-001 / MERCENARY-001, cắm khi tích hợp; hết lệch khi NOTIFICATION-001 merge | Không sửa design |
| 2 | 2 | CCD §4.13 `cancelConfirmed(match, cancellingTeamId, reason, now)`; §5.2 `withdrawAccepted(...)` trả `int` | `cancelConfirmed(match, cancellingTeamId, reason, MatchCancelledEvent.Cause cause, now)`; `withdrawAccepted` = `UPDATE … RETURNING id` (không `@Modifying`) trả `List<UUID>` | Event `MatchCancelledEvent` cần `cause` + `withdrawnRequestId` và `ReliabilityReason` theo nguồn thao tác — chữ ký cũ không có dữ liệu này; RETURNING lấy id không thêm truy vấn (số câu không đổi) | CCD §4.13, §5.2 |
| 3 | 2 | CCD §2 không có `GeoBoundingBox` (VENUE-001 tính hộp inline) | `application/support/GeoBoundingBox` (record + `around`), `VenueServiceImpl` gọi lại | Reuse: một nơi tính hộp cho venue + kèo (MERCENARY-001 dùng lại) — user chốt 2026-11-21 | CCD §2, §4.1; VENUE-001 CCD (ghi chú) |
| 4 | 4 | CCD §7 `@ValidMatchSearch` kiểm cặp `lat`/`lng`, `radiusKm` cần toạ độ, `startsAtFrom ≤ startsAtTo` | `@ValidGeoQuery` tổng quát qua interface `GeoQueryRequest` (`SearchVenueRequest`, `SearchMatchRequest` implement) kiểm toạ độ; `@ValidMatchSearch` chỉ kiểm `skillLevel` ∈ 9 nhãn + `startsAtFrom ≤ startsAtTo` (lỗi gắn `startsAtTo`) | Reuse (CCD §7 giao developer xem xét) — user chốt 2026-11-21; không đổi API | CCD §2, §7 |
| 5 | 4 | CCD §2/§7 không nêu kiểu phần tử `timeOptions` của `MatchResponse` | Record mới `MatchTimeOptionResponse(timeOptionId, startsAt, endsAt)`; `venueOptions` dùng `MatchVenueResponse` | Cần kiểu cho field `timeOptions` (đợt 1 luôn `[]`); đúng hình dạng API_Spec §4.2 | CCD §2 (dto/response), §7 |
| 6 | 4 | SYSTEM-002 §2.5: query param `LocalDateTime` "`@DateTimeFormat(iso = DATE_TIME)` và từ chối chuỗi có offset (constraint/converter)" | `api/config/LocalDateTimeParamConverter` (ISO_LOCAL_DATE_TIME) đăng ký trong `WebMvcConfig#addFormatters` cho mọi tham số `LocalDateTime`; không dùng `@DateTimeFormat` trên `SearchMatchRequest` | Một chỗ, unit test được; `@DateTimeFormat(ISO.DATE_TIME)` nhận offset rồi bỏ | CCD §2 (`api/config`), §7 |
| 7 | 9 | CCD §8: `spring.task.scheduling.pool.size` (Boot tự tạo scheduler cho job); `wsHeartbeatScheduler` là bean riêng | `SchedulingConfig` thêm bean `taskScheduler` dựng từ `ThreadPoolTaskSchedulerBuilder` (vẫn theo `spring.task.scheduling.*`) | Boot chỉ tạo scheduler khi KHÔNG có bean `TaskScheduler` nào; STOMP thêm bean broker/heartbeat → không khai báo thì `@Scheduled` mất pool 5 luồng (đã đọc điều kiện `@ConditionalOnMissingBean` trong `spring-boot-autoconfigure-4.0.9`) | CCD §8 |
| 8 | 9 | CCD §4.15: đích SUBSCRIBE sai mẫu / `SEND` → "frame ERROR" (không nêu mã) | Header `message` = `SYS_004` (đích sai mẫu) / `SYS_002` (`SEND`); lỗi không rõ → `SYS_005`; ngoại lệ mang mã `StompRealtimeException`, bộ xử lý lỗi `StompErrorHandler` | Cần một mã cho frame ERROR; dùng mã hệ thống có sẵn, không thêm mã mới | CCD §4.15; API_Spec §1.7 (ghi mã nếu giữ) |
| 9 | 9 | CCD §4.15 `StompAuthChannelInterceptor` phụ thuộc `ConversationService` | Constructor nhận `@Lazy ConversationService` | Cắt vòng phụ thuộc lúc khởi động: dịch vụ chat → gateway → `SimpMessagingTemplate` → cấu hình STOMP → interceptor | CCD §4.15 |
| 10 | 7 | CCD §5.3 `DueMatchView` có `type` | Đợt 1 không có `type` (claim không trả) | CCD §1.1 ghi `type` trong claim thuộc đợt MATCH-002 | Không sửa (MATCH-002 thêm) |

## Câu hỏi chờ duyệt
_Chặn → ghi rồi dừng; không chặn → ghi và làm tiếp._

| # | Chặn? | Vị trí | Vấn đề | Phương án |
|---|---|---|---|---|
| 1 | Không | `api/.../realtime/StompAuthChannelInterceptor.java` (nhánh SUBSCRIBE/SEND) | Mã trong frame ERROR khi đích sai mẫu / client SEND chưa có trong API_Spec | Đang dùng `SYS_004` / `SYS_002` (lệch #8); user chốt giữ hay đổi khi cập nhật design |
| 2 | Không | `api/.../config/SchedulingConfig.java` | Bean `taskScheduler` thêm vào để job giữ pool 5 luồng khi có STOMP | Kiểm ở tích hợp: log tên luồng `scheduling-1..5` khi app chạy (lệch #7) |

## Tái sử dụng
**Reuse Report 2026-11-21 — MATCH-001 đợt 1.**
**Nguồn và cách quét:** `git ls-files`/`find` toàn bộ `*/src/main/java` của `common`, `domain`, `application`, `api` (đọc danh sách lớp đầy đủ); `grep` nội dung `src/main` (không gồm test) với từ khoá: `clock`, `AppTime`, `ZoneId`, `MATCH_`, `SPORT_`, `CHAT_`, `websocket`, `stomp`, `Reputation`, `reliability`, `Haversine`/`6371`/`minLat`/`111.32`, `forceIncrement`/`OPTIMISTIC_FORCE`, `sealed interface`, `TransactionSynchronization`/`afterCommit`, `HALF_UP`, `escapeLike`; đọc sâu `TeamGuard`, `ConcurrencyConflictRetrier`, `AdminAuditLog`, `MapperSupport`, `DeletedUserMasking`, `PageRequestFactory`, `DataIntegrityViolations`, `VenueSelectionService`, `ValidGeoQueryValidator`, `TeamRepository`, `UserRepository`, `SecurityConfig`, `ErrorCode`, entity `match/*`, `team/Team`, `user/User`, `api/pom.xml`. Cache Maven local: không có artifact websocket/messaging. Không vùng mù trong 4 module.

- **DÙNG LẠI:**
  - `application/.../support/ConcurrencyConflictRetrier#execute(ErrorCode, Supplier)` — mọi thao tác ghi kèo + `send` chat với `ErrorCode.MATCH_014`.
  - `application/.../support/PageRequestFactory#create(page, pageSize)` + `dto/response/PageResponse#from` — 2.2, 2.7, 2.11–2.14, 2.20.
  - `application/.../mapper/MapperSupport#maskName`, `#maskAvatar`, `#skillLevels(String json)` — `MatchMapper`, `ConversationMapper` (không dùng `toUtc` — SYSTEM-002 xoá).
  - `domain/.../entity/converter/SkillLevelsConverter` — cột `match.skill_levels` (như `team.skill_levels`).
  - `application/.../validation/OneOf`, `ValidSkillLevels` — enum chuỗi + `skillLevels` request.
  - `application/.../service/VenueSelectionService#requireSelectable`, `#markSelected` — 2.1 kèo A (xem Phụ thuộc tạm về kiểu `selectedAt`).
  - `application/.../support/DataIntegrityViolations#violates(ex, constraint)` — `idx_match_request_active` → `MATCH_006`, `idx_match_request_accepted` → `MATCH_003`.
  - `api/.../security/CurrentUser` + `CurrentUserArgumentResolver` (`@CurrentUser UUID`), `AccessTokenTypeValidator` + bean `JwtDecoder` hiện có — controller mới và STOMP `CONNECT`.
  - `api/.../config/SchedulingConfig` (`@EnableScheduling`) — `MatchLifecycleScheduler`.
  - `common/.../exception/BusinessException`, `GlobalExceptionHandler` (envelope lỗi, `MethodArgumentTypeMismatchException` → 400).
  - Tiền lệ `TeamRepository#findForInviteById` (`OPTIMISTIC_FORCE_INCREMENT`) cho `MatchRepositoryImpl#forceIncrementVersion`; khuôn test `TeamGuardTest`, `VenueSelectionServiceImplTest`, `AdminAuditLogTest`, `ConcurrencyConflictRetrierTest`, `MapperTest`, `CustomValidatorsTest`, `NfsQueryCountTest`, `*RaceTest`, `*MigrationTest`.
- **MỞ RỘNG:**
  - `common/.../exception/ErrorCode` + `MATCH_001..MATCH_016`, `SPORT_001..SPORT_002`, `CHAT_001..CHAT_004` (+ `ErrorCodeTest`).
  - `application/.../support/TeamGuard` + `requireActiveCaptain(teamId, userId)` → `TeamCaptainContextView`, `requireCaptainAllowInactive(teamId, userId)`; `domain/.../repository/TeamRepository` + `findCaptainContext`, `adjustReliability`; `UserRepository` + `adjustReliability`. Method cũ giữ nguyên.
  - `application/.../support/AdminAuditLog` + overload `afterCommit(String action, UUID adminId, String targetType, UUID targetId)` (bản VENUE-001 giữ nguyên).
  - `application/.../validation/ValidGeoQuery(Validator)`: hiện gắn cứng `SearchVenueRequest` → tổng quát qua interface `GeoQueryRequest { lat(); lng(); radiusKm(); }` (`SearchVenueRequest`, `SearchMatchRequest` cùng implement); `@ValidMatchSearch` chỉ còn kiểm `startsAtFrom ≤ startsAtTo` → **ghi "Lệch thiết kế"** khi code (CCD §7 giao developer quyết; không đổi API).
  - Hộp giới hạn toạ độ đang là code private trong `VenueServiceImpl` (dòng 114–115) → tách `application/.../support/GeoBoundingBox` (record + `around(lat, lng, radiusKm)`), `VenueServiceImpl` gọi lại (refactor không đổi hành vi; chạy lại `VenueServiceImplTest`) — `MatchServiceImpl#searchMatches` dùng chung.
  - Entity `Match`, `MatchRequest`, `CostCustomItem` (delta V7), `Team`, `User` (`reliabilityScore` `updatable = false`).
  - `api/.../config/SecurityConfig`: `/ws/**` `permitAll` + resolver Bearer bỏ qua `/ws/**` (mở rộng `publicAuthIgnoringBearerTokenResolver`).
  - `api/pom.xml` + `spring-boot-starter-websocket` (chờ duyệt — `[cần xác nhận #12]`); `application.properties` + `arenaflow.match.lifecycle.*`, `spring.task.scheduling.pool.size`, `arenaflow.websocket.*`; `.env.example` + `MATCH_LIFECYCLE_CRON`, `WS_ALLOWED_ORIGIN_PATTERNS`.
- **VIẾT MỚI** (đã quét, không có tương đương): `MatchGuard`, `MatchExpiry`, `MatchCancellation`, `MatchWriteOutcome` (chưa có `sealed interface` nào), `ReputationService(Impl)` + `ReliabilityChange`/`ReliabilityReason` (chưa có ghi `reliability_score`), `TimeBandPenaltyPolicy`, `MatchRules`, `MatchLifecycleProperties`, `ConversationRules`, `ChatRealtimeGateway` + `RealtimeFrame`, `ConversationAccessPolicy` + `ConversationAccess` + `TeamMatchConversationPolicy`, `ConversationSql`; validator `@ValidMatchSchedule`, `@ValidCostAgreement`, `@ValidChatCursor` (chưa có validator con trỏ — NOTIFICATION-001 sẽ tổng quát hoá từ bản này); bộ đọc query param `LocalDateTime` từ chối offset (SYSTEM-002 §2.5, chưa có); mapper `MatchMapper`, `SportMapper`, `ConversationMapper`; repository/projection/DTO/event/controller/realtime của miền kèo, bộ môn, chat (chưa có repository nào cho `match*`, `sport`, chat). `AppTime`, `ClockConfig`: chưa có trên `main` (thuộc SYSTEM-002 → Phụ thuộc tạm).

## Kế hoạch chi tiết
Nguyên tắc: mỗi phase Red (test trước, fail đúng lý do) → Green → Refactor → `progress-tracker`. Unit test thuần chạy ngay; test cần DB/Spring context **viết** trong phase tương ứng nhưng chỉ **chạy** ở giai đoạn tích hợp (mục "Test để giai đoạn tích hợp"). Mọi thời gian `LocalDateTime` giờ VN; "bây giờ" = `LocalDateTime.now(clock)` một lần đầu mỗi lần thử; entity callback `AppTime.now()`; SQL truyền `:now`. Comment tiếng Việt, không trỏ tài liệu.

**Số truy vấn mỗi API (so ngưỡng — `Processing_Flow.md` §3, đợt 1):** 2.1 A = 6/W6 (= ngưỡng), 2.1 `NEED_VENUE` = 0; 2.2 = 2/L4; 2.3 = 2/D3; 2.4 = 6/W6 (4 khi rỗng; lazy 4); 2.5 `OPEN` = 4, `CONFIRMED` = 4–5/W6; 2.6 = 6 lần đầu / 5 gửi lại /W6; 2.7 = 3/L4; 2.8 = 6/W6; 2.9 = 3; 2.10 `PENDING` 3, `ACCEPTED` 6/W6; 2.11, 2.12 = 3/L4; 2.13, 2.14 = 2/L4; 2.15 = 1–2; 2.16 = 2 (lazy 3); 2.17 = 2–3/L4; 2.18 = 2; 2.19 = 1/D3; 2.20 = 2/L4; job hết hạn 3/lô, `ARCHIVED` 2/lô; STOMP `SUBSCRIBE` = 1. **Tất cả trong ngưỡng — không cần dừng.** 5 endpoint chạm đúng W6 → không thêm truy vấn nào ngoài thiết kế; đo thật ở giai đoạn tích hợp.

### Phase 0 — Nền tạm thuộc SYSTEM-002 (chờ chốt `[cần xác nhận A]`)
- Tạo `common/.../time/AppTime` (đúng chữ ký SYSTEM-002 §2.2) — test trước `AppTimeTest` (zone, cắt micro giây).
- Tạo `api/.../config/ClockConfig` (`Clock.tick(Clock.system(AppTime.ZONE), 1µs)`).
- Đổi kiểu thời gian `OffsetDateTime` → `LocalDateTime` + callback `AppTime.now()` ở entity `Match`, `MatchRequest`, `CostCustomItem`, `Sport` (đúng SYSTEM-002 §5.1, chỉ các entity mã này đọc/ghi).
- `VenueSelectionService#markSelected(Collection<UUID>, LocalDateTime)` + `VenueSelectionServiceImpl` + tham số `VenueRepository#markSelected` + `VenueSelectionServiceImplTest` (đúng SYSTEM-002 §6.3) — theo phương án user chốt.

### Phase 1 — Migration V7, entity, mã lỗi
- **Red:** `ErrorCodeTest` (mã mới, HTTP status theo `API_Spec.md` §5); `EntityIdGenerationTest` thêm `Conversation`, `ConversationMessage`; unit test builder default (`Match.skillLevels = []`, `ConversationMessage.isRead = false`), enum `Match.Format/CostType/CostSplit`, `Conversation.ContextType/PartyType/Party`. Viết (chưa chạy) `MatchFlowAMigrationTest` (schema/CHECK/index V7, bỏ `chat_message`, `ddl-auto=validate`).
- **Green:** `api/src/main/resources/db/migration/V7__match_flow_a_lifecycle.sql` (nguyên văn CCD §5.1); entity `Match` (+`@Version`, `skillLevels`, `cancelReason`, enum, `updatedAt` có setter), `MatchRequest` (+`reason`), `CostCustomItem` (+`displayOrder`), `Team`/`User` (`reliabilityScore` `updatable = false`), mới `chat/Conversation`, `chat/ConversationMessage`; **xoá** `chat/ChatMessage`; `ErrorCode` mới.

### Phase 2 — Lõi dùng chung kèo + uy tín
- **Red (unit):** `TimeBandPenaltyPolicyTest` (biên 24h, 24h−1s, 4h, 4h−1s), `MatchWriteOutcomeTest`, `MatchGuardTest` (`MATCH_001/003/004/005`, `isExpiryDue`), `TeamGuardTest` (+2 method, thứ tự `TEAM_001→016→015→002`), `ReputationServiceImplTest` (delta 0 không truy vấn, 0 dòng → `IllegalStateException`, phát `ReliabilityScoreChangedEvent`), `AdminAuditLogTest` (overload), `MatchExpiryTest`, `MatchCancellationTest` (đội đã ghép kèo A → `withdrawAccepted` = 1 dòng, penalty, `breaksCleanStreak`, một `MatchCancelledEvent`), `GeoBoundingBoxTest`; chạy lại `VenueServiceImplTest`.
- **Green:** `MatchRules`, `TimeBandPenaltyPolicy`, `MatchWriteOutcome`, `MatchGuard`, `MatchExpiry`, `MatchCancellation` (+`CancellationResult`), `ReputationService(Impl)`, `ReliabilityChange`, `ReliabilityReason`, event `application/event/match/*` (7) + `event/reputation/{ReliabilityScoreChangedEvent, ReputationSubjectType}`, `TeamCaptainContextView`, `TeamRepository#findCaptainContext/#adjustReliability`, `UserRepository#adjustReliability`, `AdminAuditLog` overload, `GeoBoundingBox` (+ refactor `VenueServiceImpl`).

### Phase 3 — Bộ môn (2.13–2.15)
- **Red (unit):** `SportServiceImplTest` (`SPORT_001`, cùng giá trị không ghi/không audit, audit sau commit), `SportMapperTest`. Viết controller test (`SportControllerTest`, `AdminSportControllerTest`) — chạy tích hợp.
- **Green:** `SportRepository`, `SportService(Impl)`, `SportMapper`, `SportResponse`, `UpdateSportRequest`, `api/sport/{SportController, AdminSportController}`.

### Phase 4 — Kèo: tạo / tìm / xem / kèo của đội (2.1, 2.2, 2.3, 2.11)
- **Red (unit):** `CustomValidatorsTest` (+`@ValidMatchSchedule` nhánh A, bỏ qua `NEED_VENUE`; `@ValidMatchSearch`; `@ValidGeoQuery` tổng quát; query param có offset bị từ chối), DTO compact constructor (`type` mặc định `HAS_VENUE`, mặc định trang/`radiusKm`), `MatchMapperTest` (`venueOptions/timeOptions = []`, `distanceKm` HALF_UP, `costAgreement` 5 field, `skillLevels`), `MatchServiceImplTest` (2.1 guard order, `NEED_VENUE` → `MATCH_016` **0 lần gọi mock**, `markSelected(now)`, đọc lại detail; 2.2 chọn `searchOpen`/`searchOpenNearby` + hộp; 2.3 `MATCH_001` + khoản tự nhập; 2.11 `TEAM_001`).
- **Green:** projection `MatchDetailView`, `MatchSummaryView`, `DueMatchView`; `MatchRepository` (`findDetailById`, `searchOpen`, `searchOpenNearby`, `findTeamMatches`; cột luồng B trả `NULL`), `MatchRepositoryCustom` + `MatchRepositoryImpl`, `CostCustomItemRepository`; DTO `CreateMatchRequest` (+`TimeOptionRequest`), `SearchMatchRequest`, response `MatchResponse` (+`MatchTeamResponse`, `MatchVenueResponse`, `MatchCostAgreementResponse`, `CostCustomItemResponse`), `MatchSummaryResponse`; filter `MatchStatusFilter`, `MatchRoleFilter`; validator; `MatchMapper`; `MatchService(Impl)` (4 method); `api/match/MatchController` (2.1–2.3), `TeamMatchController` (2.11).

### Phase 5 — Kèo: chi phí, huỷ (2.4, 2.5)
- **Red (unit):** `@ValidCostAgreement` (quan hệ `costType`↔số tiền↔`costSplit`, ≤10 khoản, ≤1 tỷ, `LOSER_PAYS_DRINKS`); `MatchServiceImplTest` 2.4 (setter + `setUpdatedAt`, bỏ insert/đọc khi rỗng, lazy → `Expired` → `MATCH_004` ngoài retrier), 2.5 (`MATCH_009`, `MATCH_015` mọi lần thử, `MATCH_010`, `MATCH_011`, `MATCH_012`, `OPEN` phát `MatchCancelledEvent` previousStatus `OPEN`).
- **Green:** `UpdateCostAgreementRequest`, `CostCustomItemRequest`, `CancelMatchRequest`, `CostAgreementResponse`, `MatchCancellationResponse`; `CostCustomItemRepository#deleteByMatchIdBulk/#insertAll` (unnest + `uuidv7()`); `MatchRepository#expireIfDue`; `MatchRequestRepository#existsByMatchIdAndStatus/#withdrawAccepted/#expirePendingByMatchIds`; service + controller.

### Phase 6 — Yêu cầu ghép kèo (2.6–2.10, 2.12)
- **Red (unit):** `MatchRequestServiceImplTest`: 2.6 guard order, `saveAndFlush` + `DataIntegrityViolationException(idx_match_request_active)` → `MATCH_006`, tạo `Conversation` chỉ khi `existingConversationId == null`, dùng lại khi gửi lại, `forceIncrementVersion` chỉ nhánh thành công, `MatchRequestSentEvent`; 2.7 `TEAM_016`/`TEAM_002`; 2.8 thứ tự CAS → `acceptIfPending` 0 dòng → `MATCH_008`, `supersedePending`, `MatchConfirmedEvent(pairingId = null)`; 2.9 lazy → `MATCH_004` trước `MATCH_008`; 2.10 `PENDING` 0 dòng → `MATCH_015`, `ACCEPTED` qua `MatchCancellation` (một event, không `MatchRequestWithdrawnEvent`); 2.12. `MatchMapperTest` (+`toRequest`, `toCreatedRequest`, `toAccept`, `toWithdrawal`).
- **Green:** projection `MatchRequestView`, `MatchRequestContextView`, `MatchAuthorAccessView`, `MatchAvailabilityView`; `MatchRequestRepository` (đủ method CCD §5.2), `MatchRepository#findAuthorAccess/#findAvailability`, `ConversationRepository` (chỉ `save` ở phase này); DTO `SendMatchRequestRequest`, `RejectMatchRequestRequest`, `WithdrawMatchRequestRequest`, `MatchRequestResponse` (+`conversationId`), `AcceptMatchRequestResponse`, `MatchRequestWithdrawalResponse`; `MatchRequestStatusFilter`; `MatchRequestService(Impl)`; `api/match/MatchRequestController`; 2.6/2.7 vào `MatchController`, 2.12 vào `TeamMatchController`.

### Phase 7 — Job vòng đời (UC-M09, AC-M08)
- **Red (unit):** `MatchLifecycleJobServiceImplTest` (lô < `batch-size` dừng, `max-batches-per-run`, lô lỗi log + dừng tác vụ đó, event mỗi dòng, bỏ câu khi rỗng), `MatchLifecyclePropertiesTest`.
- **Green:** `MatchRepository#claimDueForExpiry/#claimDueForArchive/#markExpired/#markArchived`; `MatchLifecycleJobService(Impl)` (mỗi lô `TransactionTemplate`), `MatchLifecycleProperties`, `api/scheduler/MatchLifecycleScheduler` (`zone = AppTime.ZONE_ID`; chỉ gọi 2 tác vụ của mã này — `[cần xác nhận B]`); `application.properties` (`arenaflow.match.lifecycle.*`, `spring.task.scheduling.pool.size=5`); surefire `api` đặt cron `-`.

### Phase 8 — Lõi chat (2.16–2.20)
- **Red (unit):** `TeamMatchConversationPolicyTest` (bảng `API_Spec.md` §1.6 đủ dòng: `PENDING`, `ACCEPTED`, `WITHDRAWN` sau `ACCEPTED`, `REJECTED`, `SUPERSEDED`, `EXPIRED`, gửi lại; đội trưởng cả hai đội → `A`; người đã giải tán → chỉ xem; `expiryDue` không áp người chỉ xem), `ConversationServiceImplTest` (thứ tự `CHAT_002→USER_001→CHAT_003→[lazy MATCH_004]→CHAT_004`; `publishAfterCommit` một lần; history `limit+1`/`hasMore`/`CHAT_001`; markRead; 2.20 `total = 0` bỏ câu trang, `hasUnread` theo bên, dòng `viewerParty = null` bị bỏ + `WARN`; thiếu policy cho một `ContextType` → lỗi khởi tạo), `ConversationMapperTest` (che người đã xoá, `senderPartyId`), `@ValidChatCursor`, `ChatHistoryQuery`/`SendConversationMessageRequest` (trim, ≤1000).
- **Green:** `ConversationSql` (`CONTEXT_COLUMNS`/`CONTEXT_JOINS` viết một lần), `ConversationRepository#findContext/#findMine/#countMine`, `ConversationMessageRepository`; projection `ConversationContextView`, `ConversationMessageView`, `ChatCursorView`, `ConversationListRowView`, `MineCountView`; `support/chat/{ChatRealtimeGateway, RealtimeFrame, ConversationAccessPolicy, ConversationAccess, TeamMatchConversationPolicy, ConversationRules}`; DTO + `ConversationMapper`; `ConversationService(Impl)`; `api/chat/{ConversationController, ProfileConversationController}`. Bean `ChatRealtimeGateway` tạm (no-op) chỉ khi Phase 9 bị chặn — ghi "Lệch thiết kế" nếu dùng.

### Phase 9 — Kênh realtime STOMP (cổng `[cần xác nhận #12]` ở bước đầu)
- **Bước đầu (chặn):** user duyệt thêm `spring-boot-starter-websocket` vào `api/pom.xml`; `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -pl api dependency:tree` xác nhận artifact cho Boot 4.0.9; kiểm `.withSockJS()` và `setSelectorHeaderName(null)` tồn tại trên Spring Framework 7 (biên dịch thử). Không đạt → dừng, hỏi user.
- **Red (unit):** `StompAuthChannelInterceptorTest` (CONNECT token sai/refresh → `AUTH_001`; SUBSCRIBE sai mẫu/wildcard/`/topic/pairings/{id}` → ERROR; `exp ≤ now` → `AUTH_001`; `requireSubscribable` lỗi → frame lỗi; `SEND` → ERROR), `StompChatRealtimeGatewayTest` (chỉ gửi sau commit; rollback không gửi; lỗi gửi → `WARN`, không ném), `WebSocketPropertiesTest`, error handler (header `message` = mã lỗi, không stacktrace/token).
- **Green:** `api/realtime/{WebSocketConfig, StompAuthChannelInterceptor, StompChatRealtimeGateway, WebSocketProperties}` + `StompSubProtocolErrorHandler` tuỳ biến; `SecurityConfig` `/ws/**`; `application.properties` `arenaflow.websocket.*`; `.env.example`.

### Phase 10 — Kiểm chứng & đồng bộ
- Chạy toàn bộ unit test (`./mvnw -Duser.timezone=Asia/Ho_Chi_Minh test` giới hạn unit) → dán output. `code-reviewer` → sửa CRITICAL/MAJOR (hỏi user từng finding) → đối chiếu design + bảng "Lệch thiết kế" → cập nhật design một lần → `architect-reviewer`. Tài liệu CCD §11: `docs/database/*`, USER-001/VENUE-001 design + CHANGELOG trên nhánh này; `api-convention.md`, `convention-backend.md`, `docs/agent/CLAUDE.md` → nhánh `chore/agent-framework-*` riêng.

**Ước lượng:** ~145 file main (tạo ~125, sửa ~20, xoá 1) + ~45 file test (≈30 unit, ≈15 tích hợp) ≈ 190 file.

## Phụ thuộc tạm (thuộc mã khác)
_Bản tối thiểu, đúng package/tên/chữ ký thiết kế của mã sở hữu, không logic nghiệp vụ của mã đó. Khi tích hợp, bản của mã sở hữu thắng._

| File | Mã sở hữu | Phần tạo/sửa tạm |
|---|---|---|
| `common/src/main/java/com/phananh/arenaflow/common/time/AppTime.java` (+ `common/src/test/.../time/AppTimeTest.java`) | SYSTEM-002 (CCD §2.2) | Toàn bộ lớp: `ZONE_ID`, `ZONE`, `now()`, `today()` |
| `api/src/main/java/com/phananh/arenaflow/api/config/ClockConfig.java` (+ `ClockConfigTest`) | SYSTEM-002 (CCD §2.2) | Bean `Clock` zone VN, tick 1 µs |
| `domain/.../entity/match/{Match, MatchRequest, CostCustomItem, Sport}.java` — chỉ field thời gian + callback | SYSTEM-002 (CCD §5.1) | `OffsetDateTime` → `LocalDateTime`, `AppTime.now()` (A1, user chốt) |
| `application/.../service/VenueSelectionService.java`, `impl/VenueSelectionServiceImpl.java`, `domain/.../repository/VenueRepository.java#markSelected` | SYSTEM-002 (CCD §6.3) | `selectedAt` `OffsetDateTime` → `LocalDateTime` (A1) |
| Test theo chữ ký trên: `VenueSelectionServiceImplTest`, `VenueSelectionIntegrationTest`, `VenueStaleAlertRaceTest` (chỉ dòng gọi `markSelected`) | SYSTEM-002 (CCD §8 #8) | Đổi fixture `selectedAt` sang `LocalDateTime` |

## Test để giai đoạn tích hợp
_Đã viết, biên dịch được; CHỈ CHẠY khi tích hợp (cần SYSTEM-002 V6 trên DB + `.env.dev`). Không ghi PASS trước khi chạy._

| Test (api/src/test) | Kiểm |
|---|---|
| `migration/MatchFlowAMigrationTest` | V7: cột/index/CHECK, bỏ `chat_message`, `conversation(_message)` `TIMESTAMP`, unique cuộc trò chuyện, FK `RESTRICT` người gửi. **Trước khi chạy:** đếm `chat_message` Neon dev (#16), khác 0 → dừng |
| `api/sport/SportControllerTest` | 2.13–2.15, `AUTH_004`, `SPORT_001` |
| `api/match/MatchControllerTest` | 2.1–2.12 qua HTTP: tạo / tìm (lọc + toạ độ) / xem (giờ không offset), `MATCH_016`, query param có `Z` → 400, chi phí giữ thứ tự, gửi yêu cầu + `conversationId`, `MATCH_006`, chấp nhận, `MATCH_015`, rút ACCEPTED trừ điểm (80 → 78), từ chối không body |
| `api/chat/ConversationControllerTest` | 2.16–2.20: gửi / lịch sử / đọc / đầu phòng / "Tin nhắn của tôi", `CHAT_003`, `CHAT_004` sau từ chối, `CHAT_001`, `CHAT_002`, cursor `before`+`after` → 400 `after` |
| `application/service/MatchNfsQueryCountTest` | Số câu SQL thật: 2.1 = 6, 2.3 = 2, 2.4 = 6 (unnest + `uuidv7()` — #1), 2.6 ≤ 6, 2.7/2.11 ≤ 4, 2.8 = 6, 2.16 = 2, 2.17 = 2, 2.18 = 2, 2.19 = 1, 2.20 = 2 |
| `application/service/MatchFlowCommittedTest` | Commit thật: 2 chấp nhận song song → đúng 1 ACCEPTED + `MATCH_003`; gửi yêu cầu vào kèo quá hạn → DB `EXPIRED` + `MATCH_004`; job `SKIP LOCKED` bỏ dòng đang khoá (#7) |
| `api/realtime/StompChatIntegrationTest` | Cổng thật: CONNECT bằng refresh token bị từ chối, SUBSCRIBE `/topic/**` → `SYS_004`, người ngoài → `CHAT_003`, frame `MESSAGE_CREATED` sau khi gửi (kèm `selector` bị bỏ qua) |
| `api/config/SecurityConfigIntegrationTest` (+1 ca) | `/ws/info` không 401 dù Bearer sai |
| Chưa viết (đề xuất bổ sung khi tích hợp) | race huỷ vs chấp nhận / rút vs chấp nhận, lô job lỗi không phát event, `reliability_score` không bị `updateTeam` ghi đè, double-submit 2.8 với `open-in-view` (#9), luồng scheduler dùng pool `scheduling-*` (câu hỏi #2) |

## Blockers / Ghi chú
- **`[cần xác nhận A]` — phụ thuộc kiểu thời gian SYSTEM-002 (chặn Phase 0):** `main` còn `OffsetDateTime`. Đề xuất: (A1, khuyến nghị) đổi tạm entity `Match`/`MatchRequest`/`CostCustomItem`/`Sport` + `VenueSelectionService#markSelected` sang `LocalDateTime` đúng SYSTEM-002 §5.1/§6.3 (bảng Phụ thuộc tạm); (A2) giữ `main`, đổi kiểu tại chỗ gọi `markSelected` (adapter tạm, phải gỡ khi tích hợp). Chờ user chọn.
- **`[cần xác nhận B]` — phần NOTIFICATION-001/MERCENARY-001 viết sẵn trong design MATCH-001 (mã đứng sau):** `ConversationService.send` gọi `userRealtimeGateway` (`CONVERSATION_ACTIVITY`); `WebSocketConfig` `/queue` + `setUserDestinationPrefix`; interceptor nhận `/user/queue/notifications`; `MatchLifecycleScheduler` gọi `MercenaryLifecycleJobService`, `NotificationJobService`. Đề xuất đợt 1 **không** code các phần này (NOTIFICATION-001 CCD §2 ghi tự sửa `WebSocketConfig`/`StompAuthChannelInterceptor`). Hệ quả FE thấy được tới khi NOTIFICATION-001 vào: `SUBSCRIBE /user/queue/notifications` → `ERROR` (khác `API_Spec.md` §1.7 v2.5). Chờ user chốt.
- **`[cần xác nhận #12]` (chặn bước đầu Phase 9):** thêm dependency `spring-boot-starter-websocket` (chưa có trong cache Maven local) — cần user duyệt; kiểm `.withSockJS()`, `setSelectorHeaderName(null)` trên Boot 4.0.9.
- **`[cần xác nhận #16]` (chặn chạy V7, giai đoạn tích hợp):** đếm `chat_message` trên Neon dev trước khi chạy V7. DB dev đang V5 → V7 chỉ chạy sau V6 (SYSTEM-002).
- **Rủi ro tích hợp:** xung đột với SYSTEM-002 ở `Match`/`MatchRequest`/`CostCustomItem`/`Sport`/`Team`/`User`/`VenueSelectionService` và **`ChatMessage` (SYSTEM-002 sửa, MATCH-001 xoá)**; MATCH-002/MERCENARY-001/NOTIFICATION-001 tạo bản tạm các lớp của mã này (`Match`, `MatchService`, `ConversationService`, `WebSocketConfig`…) → bản của mã này thắng, cần đối chiếu chữ ký; native SQL lớn (`findMine`, `findContext`, `insertAll` unnest, SKIP LOCKED) chưa được kiểm tới giai đoạn tích hợp — unit test không phủ SQL; 5 endpoint chạm đúng W6.
- `[cần xác nhận]` không chặn còn lại theo design: #1, #2, #4, #7–#11, #13–#15.
