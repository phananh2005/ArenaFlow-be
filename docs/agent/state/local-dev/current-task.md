---
task: NOTIFICATION-001 — thông báo in-app (35 loại), listener bất đồng bộ sau commit, kênh /user/queue/notifications, job nhắc / dọn (V11)
created: 2026-11-21
updated: 2026-11-21
percent: 5
status: in_progress
---

## Mô tả
Triển khai NOTIFICATION-001 theo thiết kế đã duyệt (v2.0) trên nhánh `feature/NOTIFICATION-001-notification-impl` (tạo từ `origin/main` `6fe6285`). Bối cảnh (user chốt 2026-11-21): 7 mã còn lại implement **song song**, mỗi mã một worktree/nhánh; class của mã đứng trước (SYSTEM-002 V6 → MATCH-001 V7 → MATCH-002 V8 → REPUTATION-001 V9 → MERCENARY-001 V10) **chưa có** trên `main` → chỉ tạo bản tối thiểu (mục "Phụ thuộc tạm"); test tích hợp + test chéo làm ở giai đoạn tích hợp. Giai đoạn 1 (hiện tại): chỉ lập kế hoạch, chờ duyệt.

Phạm vi: 4 endpoint `/api/v1/profile/notifications` (2.1–2.4), listener cho 28 event (+ 5 event mới của USER-001), `NotificationWriter` ghi gom lô, port `UserRealtimeGateway` + adapter STOMP, `PushSender` no-op, `NotificationJobService` (nhắc trước trận, nhắc mercenary đánh giá trận ngoài), `RetentionJobService` + `RetentionScheduler` (xoá chat 90 ngày, thông báo 30 ngày, dấu nhắc), migration `V11__notification.sql`.

## Thiết kế liên quan
- `docs/agent/design/NOTIFICATION-001/` — `API_Spec.md`, `Component_Class_Design.md`, `Processing_Flow.md` (v2.0), `CHANGELOG.md`.
- ADR-0011 (event sau commit + Sửa đổi 2026-11-14/16/21), ADR-0012, ADR-0014 (Sửa đổi 2026-11-16), ADR-0015, ADR-0017; ADR-0008 Sửa đổi 2026-11-09 (`uuidv7()`).
- Event / thành phần nguồn: MATCH-001 `Component_Class_Design.md` §4.9, §4.10, §4.14, §4.15, §8; MATCH-002 §4.5; REPUTATION-001 §4.7, §14; MERCENARY-001 §4.11, §5.3; VENUE-001 (event đã có trên `main`); SYSTEM-002 §2.2 (`AppTime`, `ClockConfig`), §5.1.
- `docs/agent/convention-backend.md` (§1, §2.4 comment tiếng Việt, §6.2 thời gian), `docs/agent/non-functional-standards.md` (D3 / L4 / W6), `docs/agent/api-convention.md` §7.2 (cursor).

## Definition of Done
- [ ] Mọi phase ở "Kế hoạch chi tiết" code xong theo TDD (Red → Green → Refactor), unit test thuần PASS (dán output `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh … test`).
- [ ] Test ở "Test để giai đoạn tích hợp" đã **viết** (giai đoạn 2), chỉ **chạy** khi tích hợp với V6–V10 (không ghi PASS trước khi chạy thật).
- [ ] Số truy vấn 2.1–2.4 trong ngưỡng NFS (đo bằng `NotificationNfsQueryCountTest` khi tích hợp).
- [ ] Mọi điểm `[cần xác nhận]` ở "Blockers / Ghi chú" đã được user chốt trước khi code phần liên quan.
- [ ] `code-reviewer` không còn CRITICAL/MAJOR; đối chiếu design + bảng "Lệch thiết kế" → cập nhật design một lần → `architect-reviewer`.
- [ ] Tài liệu `Component_Class_Design.md` §11 "Khi implement" (schema.dbml, schema-design.md, data-model.md; nhánh chore cho api-convention/convention-backend/CLAUDE.md) xử lý sau khi code + test PASS.

## Các bước thực hiện (tổng quan)
- [x] 1. Đối chiếu & phân tích ảnh hưởng (spec / convention / kiến trúc / NFS)
- [x] 2. Lập kế hoạch chi tiết — file cần sửa (mục dưới) + Reuse Report
- [x] 2b. **User duyệt kế hoạch** + chốt các `[cần xác nhận]` (cổng duyệt duy nhất) — 2026-11-21
- [x] P0 — Phụ thuộc tạm
- [x] P1 — Dữ liệu (SQL repository chưa chạy — test tích hợp)
- [x] P2 — Lõi soạn / ghi / đẩy
- [x] P3 — REST 2.1–2.4 (controller chưa chạy — test tích hợp)
- [x] P4 — Dispatcher + listener + executor
- [x] P5 — Event USER-001
- [x] P6 — Job nhắc / dọn (chưa nối vào `MatchLifecycleScheduler` — tích hợp)
- [x] Test tích hợp đã viết + `test-compile` qua (chưa chạy): `NotificationMigrationTest`, `NotificationRepositoryTest`, `NotificationControllerTest`, `NotificationNfsQueryCountTest`, `NotificationListenerIntegrationTest`
- [ ] Test tích hợp cần fixture bảng V7–V10 (viết ở tích hợp): `NotificationAudienceRepositoryTest`, `MatchReminderRepositoryTest`, `RetentionRepositoryTest`, `NotificationJobServiceIntegrationTest`, `UserNotificationChannelIntegrationTest`, cron `RetentionScheduler` (Spring context)
- [ ] P7 — Adapter STOMP (dời sang tích hợp — C2)
- [ ] 3. Triển khai code + unit test theo phase P0–P7 (TDD)
- [ ] 4. Chạy unit test & xác nhận PASS (dán output)
- [ ] 5. `code-reviewer` trên phần chưa review → sửa CRITICAL/MAJOR, test lại
- [ ] 6. Đối chiếu code với design → cập nhật design theo bảng "Lệch thiết kế" → `architect-reviewer`
- [ ] 7. Giai đoạn tích hợp: rebase lên `main` có V6–V10, thay phụ thuộc tạm bằng bản của mã sở hữu, áp delta vào file của MATCH-001, chạy test tích hợp → `code-reviewer` phần sửa → commit (khi user yêu cầu)

## Lệch thiết kế
_Ghi ngay khi code khác design; KHÔNG sửa design giữa chừng — cập nhật một lần ở bước cuối (`CLAUDE.md` §8). Trống = code khớp design._

| # | Phase | Design | Code thực tế | Lý do | Sửa ở |
|---|---|---|---|---|---|
| 1 | P1 | §5.2 audience: sân của kèo `v.id = m.venue_id` | `COALESCE(m.confirmed_venue_id, m.venue_id)` (`findMatchAudience`, `findGameAudience`) | Kèo luồng B chốt sân vào `confirmed_venue_id` (V1); thiếu thì nội dung không có tên sân [Inference — kiểm khi tích hợp với MATCH-002] | `Component_Class_Design.md` §5.2 |
| 2 | P1 | §5.2 tham số có thể null so sánh trực tiếp (`a.id = :anchorId`, `r.status = :requestStatus`) | `CAST(:x AS uuid / varchar)` | PostgreSQL không suy được kiểu tham số null [Inference — kiểm ở test tích hợp] | `Component_Class_Design.md` §5.2 |
| 3 | P2 | §4.5 chỉ có port + adapter STOMP | Thêm `NoOpUserRealtimeGateway` (bean tạm, vẫn đòi transaction) | User chốt C2: chưa có STOMP; adapter làm ở tích hợp — xoá bean tạm khi đó | `Component_Class_Design.md` §2, §4.5 |
| 4 | P3 | §2, §7 validator con trỏ dùng chung (tổng quát `@ValidChatCursor`) | `@ValidNotificationCursor` riêng cho `NotificationListQuery` | User chốt C3: làm riêng, gộp là refactor sau | `Component_Class_Design.md` §2, §7 |
| 5 | P4 | §4.4 `NotificationTexts` — bảng câu chữ | Method theo từng biến thể trả `Text(title, body)`; `{giờ}` thiếu → bỏ cả giờ và sân; chỗ "(trận {giờ})" thiếu giờ → "(trận)" | Hiện thực chi tiết, không đổi câu chữ | `Component_Class_Design.md` §4.4 (ghi chú) |
| 6 | P4 | §4.3 `DisputeResolvedEvent` biến thể theo `decision` của event | Ưu tiên `decision` đọc ở dòng audience (`dispute.decision`), fallback event | Kiểu enum `decision` của mã sở hữu chưa chốt (C4) — giảm phụ thuộc kiểu | `Component_Class_Design.md` §4.3 |

## Tái sử dụng
Reuse Report (skill `reuse-scan`, 2026-11-21). Nguồn quét: `common/src/main`, `domain/src/main`, `application/src/main`, `api/src/main` (+ `db/migration`) bằng `grep -rlE` trên source hiện tại của nhánh (= `origin/main` `6fe6285`). Từ khoá: `afterCommit|TransactionSynchronization`, `Realtime|SimpMessaging|websocket`, `Cursor|before.*after`, `DateTimeFormatter|HH:mm`, `EnableAsync|@Async|TaskExecutor|ThreadPool`, `ConfigurationProperties`, `unnest|RETURNING|uuidv7`, `SKIP LOCKED|TransactionTemplate`, `Push|Notification`, `ApplicationEventPublisher|publishEvent`, `@Mapper`.
**Vùng mù:** code của SYSTEM-002, MATCH-001, MATCH-002, REPUTATION-001, MERCENARY-001 đang được viết song song ở worktree khác (không đọc được) — `@ValidChatCursor`, `StompChatRealtimeGateway`, `ConversationService`, `MercenarySql`, `AppTime`, `ClockConfig` chỉ biết qua tài liệu thiết kế.

| Thành phần | Nhóm | Nguồn / căn cứ | Ghi chú |
|---|---|---|---|
| Đăng ký `TransactionSynchronization.afterCommit` | DÙNG LẠI (mẫu) | `application/support/AdminAuditLog#afterCommit` (kiểm `isSynchronizationActive` → `IllegalStateException`, `registerSynchronization`) | Writer (push) và adapter STOMP theo cùng mẫu; không có helper chung để gọi — chép mẫu 2 chỗ. Khi tích hợp so với `StompChatRealtimeGateway` (MATCH-001) — nếu trùng → đề xuất tách helper (không tự làm) |
| `UserRealtimeGateway`, `UserFrame`, `UserFrameDelivery`, `StompUserRealtimeGateway` | VIẾT MỚI | Không có gì về realtime/STOMP trên `main` (`Realtime|SimpMessaging|websocket` 0 kết quả) | Port mới theo thiết kế §4.5 |
| `NotificationWriter`, `NotificationDraft` | VIẾT MỚI | `unnest|RETURNING|uuidv7`: chỉ `VenueRepository#markStaleAlerted` (UPDATE … RETURNING, không unnest) | INSERT gom lô `unnest` + `uuidv7()` là câu đầu tiên trong repo (tiền lệ thiết kế MATCH-001 `insertAll` chưa code) |
| `PushSender`, `PushMessage`, `NoOpPushSender` | VIẾT MỚI | `Push|Notification` chỉ có entity `Notification` | Port + no-op |
| Validator con trỏ (`before` / `after` không cùng lúc) | VIẾT MỚI (dùng chung) — **`[cần xác nhận C3]`** | Không có validator con trỏ trên `main` (`Cursor` chỉ có ở Cloudinary `AssetPage`); `@ValidChatCursor` (MATCH-001) nằm trong vùng mù | Thiết kế §2 khuyên tổng quát hoá `@ValidChatCursor`; vì MATCH-001 song song → đề xuất tạo `@ValidCursor` + interface `CursorQuery` ở `application/validation`, MATCH-001 chuyển sang dùng khi tích hợp |
| Định dạng giờ `HH:mm dd/MM` (`Asia/Ho_Chi_Minh`) | VIẾT MỚI (private trong `NotificationTexts`) | `DateTimeFormatter|HH:mm` 0 kết quả | Không tách util — chỉ một nơi dùng |
| `AsyncConfig` + `notificationExecutor` | VIẾT MỚI | `EnableAsync|@Async|TaskExecutor|ThreadPool` 0 kết quả | `api/config` như `SchedulingConfig` |
| `NotificationProperties` | DÙNG LẠI (mẫu) | `application/support/venue/VenueAlertProperties` (`record` + `@ConfigurationProperties`; `ArenaFlowApplication` có `@ConfigurationPropertiesScan`) | Record mới cùng mẫu |
| `NotificationRules` (hằng) | VIẾT MỚI | Không có hằng retention / reminder | Hằng một nơi |
| `NotificationMapper` | DÙNG LẠI (mẫu) | `application/mapper/VenueMapper`, `TeamMapper` (MapStruct `componentModel = "spring"`, `unmappedTargetPolicy = ERROR`) | Không cần `MapperSupport` (không che tên) |
| Vòng lô job (mỗi lô một `TransactionTemplate`, lỗi lô → log `ERROR`, dừng) | DÙNG LẠI (mẫu) | `application/service/impl/VenueAlertServiceImpl#alertStaleVenues` | Điều kiện dừng theo thiết kế (< `batchSize`) |
| Scheduler mỏng + test | DÙNG LẠI (mẫu) | `api/scheduler/VenueStaleAlertScheduler` + `VenueStaleAlertSchedulerTest` | `RetentionScheduler` cùng mẫu, `zone = AppTime.ZONE_ID` |
| Phát event qua `ApplicationEventPublisher` | DÙNG LẠI | `VenueServiceImpl`, `VenueAlertServiceImpl` | Thêm dependency vào 4 service USER-001 |
| `ErrorCode` | MỞ RỘNG | `common/exception/ErrorCode` | + `NOTIFICATION_001` (404), `NOTIFICATION_002` (422) |
| Entity `Notification` | MỞ RỘNG | `domain/entity/chat/Notification` | `type`/`referenceType` → enum; `createdAt` → `LocalDateTime` (trùng thay đổi SYSTEM-002 §5.1) |
| `ConcurrencyConflictRetrier`, `PageRequestFactory`, `MapperSupport` | Không dùng | — | Thiết kế: không retrier (UPDATE idempotent), phân trang cursor, không che tên |

## Kế hoạch chi tiết

**NFS gate (đã đọc `non-functional-standards.md` trước khi lập kế hoạch):** 2.1 = 2 truy vấn / L4 ✅ · 2.2 = 1 / D3 ✅ · 2.3 = 1–2 / W6 ✅ · 2.4 = 2 / W6 ✅ · listener 0–2 / event (không cộng vào API nguồn) · job nhắc 3 / lô · job xoá 1 / lô. Không vượt ngưỡng → không dừng. Không gọi DB trong vòng lặp (audience 1 câu, ghi 1 câu `unnest`).

Quy ước chung mọi phase: comment tiếng Việt (§2.4), không trỏ tài liệu trong comment; thời gian `LocalDateTime` + `Clock` / `AppTime` (§6.2), không `now()` trần trong SQL (truyền `:now`); test viết trước, chạy thấy Red đúng lý do rồi mới viết code. Đường dẫn gốc: `common|domain|application|api/src/{main,test}/java/com/phananh/arenaflow/...` (viết tắt `c/`, `d/`, `a/`, `api/`).

### P0 — Phụ thuộc tạm (bản tối thiểu của mã khác, xem mục "Phụ thuộc tạm")
- Tạo record event / enum / hằng / config tối thiểu đúng package, tên, chữ ký theo thiết kế mã sở hữu; **không** logic nghiệp vụ, **không** phát event (miền phát chưa có).
- Test trước: không (chỉ khai báo kiểu; được kiểm gián tiếp qua test dispatcher P4). `AppTime`: test nhỏ `AppTimeTest` (zone, cắt micro giây) — sẽ bị thay bằng test của SYSTEM-002 khi tích hợp.

### P1 — Dữ liệu: V11, entity, projection, repository
- Tạo `api/src/main/resources/db/migration/V11__notification.sql` (đúng §5.1 — 35 loại, CHECK `reference_type`, CHECK cặp reference, 2 index notification, bảng `match_reminder` + 3 index, 3 index `match`/`external_match`, `idx_user_active_admin`) — nội dung phụ thuộc chốt C1.
- Sửa `d/entity/chat/Notification`: `Type` (35), `ReferenceType` (10) enum lồng + `@Enumerated(STRING)`; `createdAt` `LocalDateTime` + `AppTime.now()` ở `@PrePersist`.
- Tạo 12 projection `d/projection/`: `NotificationView`, `NotificationCallerView`, `UnreadCountView`, `NotificationReadContextView`, `InsertedNotificationView`, `MatchAudienceRowView`, `ApplicationAudienceRowView`, `ReportAudienceRowView`, `TeamAudienceRowView`, `InvitationAudienceView`, `ReminderGameView`, `GameAudienceRowView`.
- Tạo 4 repository `d/repository/`: `NotificationRepository` (10 method + 3 biến thể `…Unread`), `NotificationAudienceRepository` (7), `MatchReminderRepository` (2), `RetentionRepository` (3) — native SQL đúng §5.2, mảng bind `CAST(:x AS uuid[])`.
- Test viết trước (unit thuần): `api/src/test/.../migration/NotificationTypeCheckConsistencyTest` — đọc `db/migration/V11__notification.sql` từ classpath, so danh sách CHECK `type` / `reference_type` với `Notification.Type` / `ReferenceType` (không Spring, không DB). SQL repository → test tích hợp (giai đoạn 2).

### P2 — Lõi soạn / ghi / đẩy (`a/support/notification`, `a/support/realtime`)
- Tạo `NotificationRules` (RETENTION 30d, CHAT_RETENTION 90d, REMINDER_LEAD 2h, DEFAULT_LIMIT 50, MAX_LIMIT 100, ZONE), `NotificationProperties` (record `arenaflow.notification.*`: reminder, retention, executor), `NotificationDraft`, `NotificationTexts` (35 loại + biến thể §4.4), `PushSender` / `PushMessage` / `NoOpPushSender`, `UserRealtimeGateway` / `UserFrame` (sealed: `NotificationCreated`, `ConversationActivity`) / `UserFrameDelivery`, `NotificationWriter` (MANDATORY; khử trùng (người nhận, loại, đối tượng); bỏ người nhận `null`; rỗng → không truy vấn; 1 câu `insertAllReturning`; `publishAfterCommit` một lần cho lô; push trong `afterCommit`; log chỉ loại + số dòng).
- Tạo `a/dto/response/NotificationResponse`, `a/mapper/NotificationMapper` (`toResponse(NotificationView)`, `toResponse(InsertedNotificationView)` → `isRead = false`).
- Test viết trước (unit): `NotificationTextsTest` (mỗi loại / biến thể: có `{giờ}` `HH:mm dd/MM`, bỏ `{ tại sân}` khi không có sân, không tên cá nhân, `NOSHOW` thêm câu 24 giờ, `title` ≤ 200), `NotificationWriterTest` (khử trùng, bỏ null, rỗng → 0 truy vấn, không transaction → `IllegalStateException`, deliveries khớp dòng `RETURNING`, push chỉ gọi trong `afterCommit`, rollback → không push), `NotificationMapperTest`, `NoOpPushSenderTest` (không ném).

### P3 — REST 2.1–2.4
- Sửa `c/exception/ErrorCode`: + `NOTIFICATION_001` (404 "Thông báo không tồn tại"), `NOTIFICATION_002` (422 "Thông báo mốc không hợp lệ, vui lòng tải lại").
- Tạo validator con trỏ dùng chung (`a/validation/ValidCursor` + `ValidCursorValidator` + `CursorQuery`) — theo chốt C3; `a/dto/request/NotificationListQuery` (compact constructor: `unreadOnly` mặc định `false`, `limit` mặc định 50; `@Min(1) @Max(100)`), `a/dto/response/NotificationHistoryResponse`, `UnreadCountResponse`.
- Tạo `a/service/NotificationService` + `impl/NotificationServiceImpl` (§4.1: `visibleFrom = now − 30d` cho mọi câu; thứ tự lỗi `USER_001` → `NOTIFICATION_002`; chọn 1 trong 6 câu trang theo `unreadOnly` × con trỏ; `limit + 1` → `hasMore`; `markRead` idempotent 1 truy vấn khi đã đọc).
- Tạo `api/notification/NotificationController` (`@Validated`, `@CurrentUser UUID`, 200 / 204).
- Test viết trước (unit): `NotificationServiceImplTest` (mỗi nhánh thứ tự kiểm, chọn đúng câu, `hasMore`, `after` giữ thứ tự cũ nhất trước, `visibleFrom` theo `Clock.fixed`, `markRead` đã đọc → không gọi `markRead` repo), `CustomValidatorsTest`/`ValidCursorValidatorTest` (cả hai → lỗi gắn `after`; một hoặc không → hợp lệ), `RequestDtoValidationTest` (+ `limit` 0/101). Controller → test tích hợp (mẫu `ApiIntegrationTest` cần DB).

### P4 — Dispatcher + listener + executor
- Tạo `a/service/NotificationDispatcher` + `impl/NotificationDispatcherImpl` (mỗi method `@Transactional(propagation = REQUIRES_NEW)`; 28 event theo bảng §4.3 + 5 event USER-001; cột "Bỏ qua khi"; bỏ dòng `captainUserId = null`; `now` từ `Clock` một lần).
- Tạo `a/listener/MatchNotificationListener`, `MercenaryNotificationListener`, `ReputationNotificationListener`, `AccountNotificationListener` (`@Async("notificationExecutor")` + `@TransactionalEventListener(AFTER_COMMIT)`, `dispatchSafely` bắt `RuntimeException` → log `ERROR` tên event + id, không ném; không nghe `VenueReportedEvent`, `ReliabilityScoreChangedEvent`).
- Tạo `api/config/AsyncConfig` (`@EnableAsync`, `ThreadPoolTaskExecutor notificationExecutor` 2/4/1000, tiền tố `notification-`, `CallerRunsPolicy`, chờ 30s khi tắt, `AsyncUncaughtExceptionHandler` log tên method).
- Test viết trước (unit): `NotificationDispatcherImplTest` (mỗi event ≥ 1 ca: người nhận, loại, `referenceType`/id, biến thể; ca "Bỏ qua khi"; danh sách id rỗng → không truy vấn; `CAPTAIN_TRANSFERRED` chỉ người mới; `RATING_RECEIVED` chỉ khi `hasRated`/`ratedBy*`; một người hai đội → một thông báo), `*NotificationListenerTest` (gọi đúng method dispatcher; dispatcher ném → listener không ném; annotation `@Async` / `@TransactionalEventListener(AFTER_COMMIT)` có mặt — kiểm bằng reflection), `AsyncConfigTest` (thông số executor, policy).

### P5 — Event mới của USER-001 (sửa chức năng đã có)
- Tạo `a/event/user/UserRegisteredEvent`, `a/event/team/TeamInvitationCreatedEvent`, `TeamInvitationRespondedEvent`, `CaptainTransferredEvent`, `TeamStatusChangedByAdminEvent`.
- Sửa `AuthServiceImpl.createAccount` (sau `saveAndFlush` liên kết OAuth, trong `TransactionTemplate`), `TeamMembershipServiceImpl.inviteMember` / `acceptInvitation` / `rejectInvitation` (trong lambda retrier, sau ghi), `TeamServiceImpl.transferCaptain` (sau ghi lịch sử), `AdminTeamServiceImpl.markInactive` / `reactivate` (sau `UPDATE`) — thêm `ApplicationEventPublisher`; lời mời bị từ chối tự động khi xoá tài khoản **không** phát. Không đổi số truy vấn / API.
- Test viết trước (unit, sửa file test hiện có): `AuthServiceImplTest`, `TeamMembershipServiceImplTest`, `TeamServiceImplTest`, `AdminTeamServiceImplTest` — `verify(eventPublisher).publishEvent(<event đúng payload>)` ở nhánh thành công; `never()` ở nhánh lỗi / guard thất bại.

### P6 — Job nhắc / dọn
- Tạo `a/service/NotificationJobService` + `impl/NotificationJobServiceImpl` (`sendDueStartReminders`, `sendDueRatingReminders`: mỗi lô một `TransactionTemplate`: claim → rỗng dừng → audience → `NotificationTexts` → `write`; lỗi lô → log `ERROR`, dừng; log `INFO` số trận / thông báo).
- Tạo `a/service/RetentionJobService` + `impl/RetentionJobServiceImpl` (3 tác vụ, lô theo cấu hình, dừng khi < `batchSize` hoặc hết `max-batches-per-run`).
- Tạo `api/scheduler/RetentionScheduler` (`@Scheduled(cron = "${arenaflow.notification.retention.cron}", zone = AppTime.ZONE_ID)`, 3 tác vụ, lỗi một tác vụ không chặn tác vụ sau).
- Sửa `api/src/main/resources/application.properties` (+ khối `arenaflow.notification.*` §8); `api/src/test/resources` (nếu có properties test) đặt cron `-`.
- **Không** tạo `MatchLifecycleScheduler` (thuộc MATCH-001) — dòng gọi `sendDueStartReminders()` / `sendDueRatingReminders()` và `spring.task.scheduling.pool.size=5` áp ở giai đoạn tích hợp.
- Test viết trước (unit): `NotificationJobServiceImplTest` (mốc `now`/`leadEnd`/`lookback` theo `Clock.fixed`, dừng khi claim rỗng, đúng số lô tối đa, lô lỗi dừng, draft mercenary dùng `MERCENARY_APPLICATION`/`applicationId`), `RetentionJobServiceImplTest` (cutoff 90 / 30 ngày, điều kiện dừng), `RetentionSchedulerTest` (gọi đủ 3 tác vụ, một tác vụ ném vẫn chạy tác vụ sau).

### P7 — Adapter STOMP (phụ thuộc chốt C2)
- Sửa `api/pom.xml`: + `spring-boot-starter-websocket` (dependency đã duyệt ở MATCH-001 §8 / ADR-0014 — tạm, mã sở hữu MATCH-001).
- Tạo `api/realtime/StompUserRealtimeGateway` (MANDATORY; `afterCommit` → `convertAndSendToUser(userId, "/queue/notifications", {type: NOTIFICATION_CREATED | CONVERSATION_ACTIVITY, …})`; lỗi gửi → `WARN` loại frame + id, không ném).
- Theo C2: `api/realtime/WebSocketConfig` bản tối thiểu (chỉ broker `/topic`, `/queue`, `setUserDestinationPrefix("/user")`, **không** endpoint `/ws`) để có bean `SimpMessagingTemplate`.
- Test viết trước (unit): `StompUserRealtimeGatewayTest` (không transaction → `IllegalStateException`; trước commit không gửi; `afterCommit` gửi đúng user / destination / JSON `type`; `SimpMessagingTemplate` ném → không ném ra ngoài).

### Delta áp ở giai đoạn tích hợp (file thuộc MATCH-001 — không tạo ở nhánh này)
- `StompAuthChannelInterceptor.SUBSCRIBE`: chấp nhận đúng nguyên văn `/user/queue/notifications` (kiểm `exp`, không truy vấn); `/queue/**` thô, `/user/…` khác mẫu → `ERROR`. `WebSocketConfig`: `enableSimpleBroker("/topic", "/queue")` + `setUserDestinationPrefix("/user")` (bản MATCH-001 thắng bản tạm P7).
- `ConversationServiceImpl.send`: `userRealtimeGateway.publishAfterCommit(viewers − {caller}, ConversationActivity(...))` dùng 4 cột mới của `findContext`.
- `MatchLifecycleScheduler`: gọi 2 method nhắc sau tác vụ MERCENARY-001; `spring.task.scheduling.pool.size=5`.

## Phụ thuộc tạm (thuộc mã khác)
Bản tối thiểu, đúng package / tên / chữ ký theo thiết kế mã sở hữu, không logic nghiệp vụ. **Khi tích hợp, bản của mã sở hữu thắng** — listener / dispatcher sửa theo nếu kiểu khác.

| File | Mã sở hữu | Phần tạo |
|---|---|---|
| `common/.../common/time/AppTime.java` | SYSTEM-002 | `ZONE_ID`, `ZONE`, `now()`, `today()` đúng §2.2 |
| `api/.../api/config/ClockConfig.java` | SYSTEM-002 | Bean `Clock` (`Clock.tick(Clock.system(AppTime.ZONE), 1µs)`) |
| `domain/.../entity/chat/Notification.java` (chỉ `createdAt` → `LocalDateTime` + `AppTime.now()`) | SYSTEM-002 §5.1 (cùng file NOTIFICATION sửa enum) | Đổi kiểu `createdAt` y như SYSTEM-002 |
| `application/.../event/match/MatchRequestSentEvent`, `MatchConfirmedEvent` (gồm `pairingId` của MATCH-002), `MatchRequestRejectedEvent`, `MatchRequestWithdrawnEvent`, `MatchCancelledEvent`, `MatchExpiredEvent`, `MatchArchivedEvent` | MATCH-001 §4.9 (+ MATCH-002 §4.5 cho `pairingId`) | Record, payload đúng bảng §4.9; `MatchCancelledEvent.previousStatus` = `Match.Status` (entity có sẵn), `cause` = enum lồng `Cause {CANCEL, REQUEST_WITHDRAWN}` `[cần xác nhận C4]` |
| `application/.../event/match/PairingCreatedEvent` | MATCH-002 §4.5 | Record |
| `application/.../event/reputation/MatchRatedEvent`, `TeamReportedEvent`, `ReportDisputedEvent`, `DisputeResolvedEvent`, `TeamVerificationChangedEvent` | REPUTATION-001 §4.7 (payload đã sửa theo §14: `reporterType, reporterId, reportedType, reportedId`) | Record; `reportType` = `Report.ReportType` (có sẵn); `decision` kiểu chưa có tên trong thiết kế `[cần xác nhận C4]` — dispatcher lấy `decision` từ dòng audience, không đọc từ event |
| `application/.../event/reputation/UserVerificationChangedEvent` | MERCENARY-001 §4.11 | Record |
| `application/.../event/mercenary/MercenaryApplicationSubmittedEvent`, `…ApprovedEvent`, `…RejectedEvent`, `…WithdrawnEvent`, `MercenaryRecruitmentClosedEvent`, `MercenaryGameCancelledEvent`, `MercenaryRatingCreatedEvent`, `MercenaryReportCreatedEvent` | MERCENARY-001 §4.11 | Record; `previousStatus` = `MercenaryApplication.Status` (có sẵn); `cause` = enum lồng mỗi record `[cần xác nhận C4]` |
| `application/.../support/mercenary/MercenarySide` | MERCENARY-001 §4.3 (`CAPTAIN` \| `APPLICANT`) | Enum — dùng làm kiểu `direction` `[cần xác nhận C4]` |
| `domain/.../repository/MercenarySql` | MERCENARY-001 §5.3 | Chỉ hằng `GAME_JOIN`, `GAME_START`, `GAME_VENUE` dùng cho `findApplicationAudience` / `findReportAudience` |
| `application/.../support/realtime/NoOpUserRealtimeGateway` | NOTIFICATION-001 (tạm, thay bằng `StompUserRealtimeGateway` khi có STOMP của MATCH-001 — C2) | Bean no-op, vẫn đòi transaction đang chạy |
| ~~`api/pom.xml` websocket, `WebSocketConfig` tạm~~ | MATCH-001 | **Không tạo** (C2) |

## Test để giai đoạn tích hợp
Viết ở giai đoạn 2 cùng phase tương ứng (để `code-reviewer` thấy), chỉ **chạy** khi nhánh có V6–V10 + code mã sở hữu và `.env.dev` (worktree này không có `.env.dev`). Không ghi PASS trước khi chạy thật.

| Test (api/src/test, package của lớp được kiểm) | Kiểm | Cần |
|---|---|---|
| `migration/NotificationMigrationTest` | V11 áp được sau V10; CHECK `type` 35 giá trị, `reference_type`, cặp reference; index; `match_reminder` XOR; tên `notification_type_check` (#8) | DB, V1–V10 |
| `domain/repository/NotificationRepositoryTest` | `insertAllReturning` bind mảng + `RETURNING` không `@Modifying` (#4), bỏ người đã xoá, 6 câu trang + con trỏ row comparison, `visibleFrom`, `markRead`/`markAllRead` | DB |
| `domain/repository/NotificationAudienceRepositoryTest` | 7 câu audience (nhánh `null` trạng thái không trả dòng, đội trưởng `ACTIVE`, `hasRated`, `ratedBy*`, Admin) | DB + bảng V7–V10 |
| `domain/repository/MatchReminderRepositoryTest` | claim đến hạn / chưa đến hạn / ghép < 2h; `ON CONFLICT DO NOTHING` với unique partial, hai claim đồng thời (#6); `AFTER_END` cửa sổ 24h | DB + V7–V10 |
| `domain/repository/RetentionRepositoryTest` | xoá conversation theo từng mốc trạng thái (CASCADE tin), thông báo 30 ngày, `SKIP LOCKED` | DB + V7, V10 |
| `application/listener/NotificationListenerIntegrationTest` | rollback → không thông báo; commit → đúng một lần; chạy trên luồng `notification-` (#5); dispatcher lỗi không ảnh hưởng thao tác gốc | Spring context + DB + event thật của mã sở hữu |
| `application/service/NotificationNfsQueryCountTest` | 2.1 ≤ 4 (2), 2.2 ≤ 3 (1), 2.3 ≤ 6 (1–2), 2.4 ≤ 6 (2); dispatch `MatchConfirmedEvent` luồng A ≤ 2 (log SQL thật) | Spring context + DB |
| `api/notification/NotificationControllerTest` (`ApiIntegrationTest`) | 200/204, `400 VALIDATION_ERROR` (`before`+`after`, `limit`), `404 USER_001`, `404 NOTIFICATION_001`, `422 NOTIFICATION_002`, 401 | Spring context + DB |
| `application/service/NotificationJobServiceIntegrationTest` | nhắc một lần, rollback lô → nhắc lại, kèo huỷ giữa claim và audience | DB + V7–V10 |
| `api/realtime/UserNotificationChannelIntegrationTest` | `SUBSCRIBE /user/queue/notifications` nhận frame của chính mình; `/queue/**`, `/user/{id}/…` → `ERROR`; principal `getName()` = userId (#7); `CONVERSATION_ACTIVITY` khi gửi tin (trừ người gửi) | Spring context + STOMP của MATCH-001 |
| USER-001: `NfsWriteQueryCountTest` (chạy lại) | 2.12, 2.13, 2.14, 2.15, 2.19, 2.20 vẫn đúng số truy vấn cũ sau khi thêm `publishEvent` | Spring context + DB |

Unit test thuần (P0–P7) chạy ngay ở giai đoạn 2: `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -pl common,domain,application,api -Dtest=<Test> test` (không cần `.env.dev`).

## Câu hỏi chờ duyệt
_Chặn → dừng, báo về. Không chặn → ghi ở đây và làm tiếp theo kế hoạch._

| # | Chặn? | Vị trí | Vấn đề | Phương án đề xuất |
|---|---|---|---|---|
| Q1 | Không | `api/src/main/java/com/phananh/arenaflow/api/config/AsyncConfig.java:25` | Bean `ThreadPoolTaskExecutor notificationExecutor` là `Executor` duy nhất do app khai báo → [Unverified] Spring Boot có thể không tạo `applicationTaskExecutor` mặc định (điều kiện "thiếu bean Executor") và `@Async` không qualifier / hạ tầng khác (MVC async, WebSocket của MATCH-001) dùng nhầm executor thông báo | Kiểm ở test tích hợp (context có / không có `applicationTaskExecutor`); nếu thiếu → khai báo `applicationTaskExecutor` tường minh hoặc đánh dấu bean thông báo `defaultCandidate = false` |
| Q2 | Không | `domain/src/main/java/com/phananh/arenaflow/domain/repository/NotificationAudienceRepository.java:31` | Tên sân của kèo luồng B lấy `COALESCE(confirmed_venue_id, venue_id)` (Lệch #1) — chưa biết MATCH-002 ghi sân chốt vào cột nào | Đối chiếu khi tích hợp MATCH-002; sửa câu nếu khác |
| Q3 | Không | `application/src/main/java/com/phananh/arenaflow/application/service/impl/{TeamServiceImpl,TeamMembershipServiceImpl}.java` (field `clock`) | SYSTEM-002 cũng inject `Clock` vào hai service này → conflict khi tích hợp | Bản SYSTEM-002 thắng; giữ `eventPublisher` + dùng chung field `clock` |
| Q4 | Không | `api/src/main/resources/db/migration/V11__notification.sql` | Có V11 trên nhánh trong khi DB dev ở V5 → mọi test cần Flyway / context trên nhánh này fail cho tới khi có V6–V10 | Chỉ chạy test tích hợp sau khi rebase lên `main` có V6–V10 |

## Blockers / Ghi chú
**User đã duyệt kế hoạch (2026-11-21) và chốt:**
- C1 → code đủ 35 loại theo thiết kế (giữ #34, #35); bỏ thông báo Admin làm sau ở chức năng quản lý report.
- C2 → KHÔNG tạo `WebSocketConfig` tạm; port `UserRealtimeGateway` dùng bean no-op tạm (`NoOpUserRealtimeGateway`, vẫn kiểm transaction đang chạy). Adapter `StompUserRealtimeGateway` + dependency websocket → giai đoạn tích hợp sau khi MATCH-001 vào `main` (P7 dời sang tích hợp).
- C3 → validator con trỏ riêng cho thông báo (`@ValidNotificationCursor`); gộp chung là refactor sau.
- C4 → enum lồng trong record ở bản tạm; sửa theo bản thật khi tích hợp.
- C5 → `docs/agent/state/local-dev/*` không vào commit.
- Delta file MATCH-001 (luật SUBSCRIBE, frame `CONVERSATION_ACTIVITY`, gọi job nhắc, `pool.size`) → làm ở tích hợp, trên nhánh này.

**Câu hỏi gốc (đã chốt ở trên):**
- **C1 — Thông báo Admin #34 `VENUE_ADMIN_ALERT`, #35 `DISPUTE_CREATED`:** user đã chốt 2026-11-21 sẽ bỏ mọi thông báo Admin trong chức năng quản lý report sau này, nhưng thiết kế NOTIFICATION-001 v2.0 vẫn có. (a) Code theo thiết kế hiện tại (35 loại, bỏ ở chức năng sau), hay (b) bỏ ngay (33 loại; bỏ `referenceType` `VENUE`/`DISPUTE`/`MERCENARY_DISPUTE`, `findActiveAdminIds`, nhánh `ADMIN` của `findReportAudience`, `idx_user_active_admin`) — (b) đổi API contract + DB schema so với design đã duyệt → cần sửa design + `architect-reviewer` trước khi code phần đó.
- **C2 — Hạ tầng STOMP của MATCH-001 chưa có:** adapter `StompUserRealtimeGateway` cần bean `SimpMessagingTemplate`. Đề xuất: thêm `spring-boot-starter-websocket` + `WebSocketConfig` tối thiểu (chỉ broker, không endpoint `/ws`, không interceptor — không mở kết nối nào) làm phụ thuộc tạm; delta interceptor `SUBSCRIBE` áp khi tích hợp. Phương án khác: chưa tạo adapter STOMP ở nhánh này (port + bean no-op tạm).
- **C3 — Validator con trỏ dùng chung:** tạo `@ValidCursor` + `CursorQuery` ở `application/validation` (MATCH-001 `ChatHistoryQuery` chuyển sang khi tích hợp, bỏ `@ValidChatCursor`) hay tạo validator riêng cho thông báo rồi gộp sau? Tên chưa có trong thiết kế.
- **C4 — Kiểu enum trong payload event chưa được thiết kế mã sở hữu đặt tên:** `MatchCancelledEvent.cause`, `DisputeResolvedEvent.decision`, `cause` của event mercenary, `direction` (`MercenarySide`?). Bản tạm dùng enum lồng; khi tích hợp listener / dispatcher sửa theo bản mã sở hữu.
- **C5 — `current-task.md` đang được Git theo dõi** (`.gitignore` có `!docs/agent/state/*/` — trái `CLAUDE.md` §7 "gitignore"): 7 nhánh song song cùng ghi đè file này → conflict khi tích hợp. Có commit file state ở nhánh này không?

**Ghi chú / rủi ro:**
- Thiết kế §12 #1 ghi "Chặn code — implement sau năm mã kia"; user chốt 2026-11-21 cho code song song → mọi tham chiếu chéo qua phụ thuộc tạm, kiểm thật ở tích hợp.
- `[cần xác nhận]` #4–#8, #12 của thiết kế (bind mảng, `@Async`+AFTER_COMMIT, `ON CONFLICT` partial, principal STOMP, tên CHECK, pool Hikari) chỉ kiểm được bằng test tích hợp.
- Conflict dự kiến khi tích hợp: `Notification.java` (SYSTEM-002), `ErrorCode.java`, `application.properties`, `api/pom.xml` (MATCH-001), 4 service USER-001 (USER-003 song song có thể sửa `TeamMembershipServiceImpl`), file event (bản mã sở hữu thắng).
- V11 tham chiếu `external_match` (V10), `match_request.responded_at` (V7), `pairing.final_confirmed_at` (V8) → không chạy được trên DB nào trước khi có V6–V10.
- Worktree không có `.env.dev` → không chạy được test tích hợp ở nhánh này.
