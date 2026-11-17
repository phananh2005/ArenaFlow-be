# Component & Class Design — Thông báo in-app & tín hiệu realtime cá nhân

| Mục | Giá trị |
|---|---|
| Mã chức năng | `NOTIFICATION-001` |
| Phạm vi tầng | Backend (Spring Boot REST API + kênh STOMP của MATCH-001). Frontend: ngoài phạm vi repo này |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · Spring Data JPA · Flyway · PostgreSQL ≥ 18 |
| Quy ước áp dụng | `docs/agent/convention-backend.md`, `docs/agent/security-backend.md`, `docs/agent/non-functional-standards.md` · kế thừa USER-001 `Component_Class_Design.md` §4.6, §5; MATCH-001 `Component_Class_Design.md` §4.9 (event), §4.10 (scheduler), §4.14 (`ConversationService`, `ConversationContextView`), §4.15 (kênh realtime), §5.2 (`insertAll` mảng + `uuidv7()`), §7 (`@ValidChatCursor`), §8 (pool scheduling); MERCENARY-001 §4.11 (event), §5.3 (`MercenarySql.GAME_*`); REPUTATION-001 §4.7 (event); VENUE-001 §4.7 (event) · ADR-0002, ADR-0008 (+ Sửa đổi 2026-11-09), [ADR-0011](../../decisions/0011-domain-events-after-commit.md), [ADR-0012](../../decisions/0012-scheduled-jobs-claim-with-skip-locked.md), [ADR-0014](../../decisions/0014-realtime-chat-stomp-websocket.md) (+ Sửa đổi 2026-11-16), [ADR-0015](../../decisions/0015-notification-async-listener-and-user-channel.md) |
| Ràng buộc dữ liệu | V1–V4 (code) + V5 (VENUE-001), V6 (MATCH-001), V7 (MATCH-002), V8 (REPUTATION-001), V9 (MERCENARY-001) — **chưa có trong code** + `V10__notification.sql` (§5.1). Bảng `notification` có từ V1; entity hiện có `domain/.../entity/chat/Notification`. Neon dev `misty-dew-90700890` (kiểm 2026-11-16): Flyway V4, `notification` 0 dòng, CHECK tên `notification_type_check` |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-16 · v1.0 (baseline) · hiện tại **v1.1** (solution-architect · 2026-11-18) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan kiến trúc

Layered theo 4 module Maven (ADR-0006). Hợp đồng FE: `API_Spec.md`. Luồng, transaction, số truy vấn: `Processing_Flow.md`. Phạm vi nghiệp vụ: user chốt Q1–Q13 ngày 2026-11-16 (§13).

```text
Miền phát (MATCH/MERCENARY/REPUTATION/VENUE/USER)        NOTIFICATION-001
  publishEvent trong transaction ghi ──commit──▶ *NotificationListener  (@Async notificationExecutor + @TransactionalEventListener AFTER_COMMIT)
                                                   └▶ NotificationDispatcher (REQUIRES_NEW): 1 S audience → List<NotificationDraft>
                                                        └▶ NotificationWriter (MANDATORY): 1 I gom lô RETURNING → afterCommit: UserRealtimeGateway + PushSender
MatchLifecycleScheduler (5 phút) ──▶ NotificationJobService.sendDueStartReminders() / sendDueRatingReminders()  (claim bằng INSERT … ON CONFLICT DO NOTHING) ──▶ NotificationWriter
RetentionScheduler (hằng ngày)   ──▶ RetentionJobService: xoá cuộc trò chuyện hết hạn lưu, thông báo > 30 ngày, dấu nhắc cũ
ConversationService.send (MATCH-001 2.16) ──▶ UserRealtimeGateway: frame CONVERSATION_ACTIVITY (không tạo thông báo)
REST /api/v1/profile/notifications ──▶ NotificationService (đọc / đánh dấu đọc)
```

- **Listener bất đồng bộ sau commit** (ADR-0015): miền phát không chờ việc tạo thông báo; lần thử rollback không tạo thông báo (ADR-0011 quy tắc 3–4). Mỗi event: tối đa **1** câu đọc người nhận + **1** câu ghi gom lô, bất kể số người nhận.
- **Người nhận tính lúc listener chạy** (Q2 — user chốt 2026-11-16: "đội trưởng" = đội trưởng **tại lúc tạo thông báo**, không phải đúng lúc sự kiện): đội → đội trưởng `ACTIVE` tại lúc Q1 audience; người dùng đã xoá bị loại ngay trong câu INSERT (§5.2) — không truy vấn riêng.
- **Nội dung soạn sẵn** (Q5): `NotificationTexts` (§4.4) dựng `title`/`body` tiếng Việt từ tên đội / sân / giờ / vị trí đã đọc; không tên cá nhân.
- **Kênh realtime cá nhân** (Q6, Q9; ADR-0014 Sửa đổi 2026-11-16): `/user/queue/notifications` trên hạ tầng STOMP của MATCH-001; port mới `UserRealtimeGateway` (§4.5).
- **`PushSender`** (Q10): port + bản no-op; FCM sau.
- **Job**: nhắc trước trận (§4.7) chạy trong lần chạy 5 phút của `MatchLifecycleScheduler`; xoá hạn lưu (§4.8) là scheduler hằng ngày mới.

**Điều kiện tiên quyết:** code VENUE-001, MATCH-001, MATCH-002, REPUTATION-001, MERCENARY-001 (event, bảng, `ConversationService`, kênh realtime) phải có trước — listener tham chiếu event của cả năm mã, V10 cần V9 (`external_match`) `[cần xác nhận #1]`.

## 2. Package Structure (delta)

```text
common/        com.phananh.arenaflow.common
└── exception/       ErrorCode (+ NOTIFICATION_001, NOTIFICATION_002)

domain/        com.phananh.arenaflow.domain
├── entity/chat/     Notification (sửa — §6)
├── repository/      NotificationRepository, NotificationAudienceRepository, MatchReminderRepository,
│                    RetentionRepository (mới)
└── projection/      NotificationView, NotificationCallerView, UnreadCountView, NotificationReadContextView,
                     InsertedNotificationView, MatchAudienceRowView, ApplicationAudienceRowView,
                     ReportAudienceRowView, TeamAudienceRowView, InvitationAudienceView,
                     ReminderGameView, GameAudienceRowView (mới)

application/   com.phananh.arenaflow.application
├── service/         NotificationService, NotificationDispatcher, NotificationJobService, RetentionJobService (mới)
│   └── impl/        NotificationServiceImpl, NotificationDispatcherImpl, NotificationJobServiceImpl, RetentionJobServiceImpl
├── listener/        MatchNotificationListener, MercenaryNotificationListener, ReputationNotificationListener,
│                    AccountNotificationListener (mới — package mới)
├── event/user/      UserRegisteredEvent (mới — sửa USER-001, §4.9)
├── event/team/      TeamInvitationCreatedEvent, TeamInvitationRespondedEvent, CaptainTransferredEvent,
│                    TeamStatusChangedByAdminEvent (mới — sửa USER-001, §4.9)
├── support/
│   ├── notification/ NotificationDraft, NotificationTexts, NotificationWriter, NotificationRules,
│   │                 NotificationProperties, PushSender, PushMessage, NoOpPushSender (mới)
│   └── realtime/    UserRealtimeGateway, UserFrame, UserFrameDelivery (mới — port, không phụ thuộc spring-websocket)
├── dto/request/     NotificationListQuery (mới)
├── dto/response/    NotificationResponse, NotificationHistoryResponse, UnreadCountResponse (mới)
└── mapper/          NotificationMapper (mới, MapStruct)

api/           com.phananh.arenaflow.api
├── notification/    NotificationController (mới)
├── realtime/        StompUserRealtimeGateway (mới); WebSocketConfig, StompAuthChannelInterceptor (sửa — MATCH-001 §4.15)
├── scheduler/       RetentionScheduler (mới); MatchLifecycleScheduler (sửa — MATCH-001 §4.10)
└── config/          AsyncConfig (mới — @EnableAsync, bean notificationExecutor)
```

Thành phần dùng chung mới (`UserRealtimeGateway`, `NotificationWriter`, `PushSender`, validator con trỏ) → developer MUST chạy `reuse-scan` trước khi tạo; `@ValidChatCursor` (MATCH-001 §7) nên tổng quát hoá thành validator con trỏ dùng chung thay vì viết bản thứ hai.

## 3. Controller Layer

Quy ước như USER-001 §3 (`@Validated`, `ResponseEntity<ApiResponse<…>>`); `@CurrentUser UUID currentUserId`.

| Controller | `@RequestMapping` | Endpoint | Service | Thành công / tiêu chí `api-convention.md` §5 |
|---|---|---|---|---|
| `NotificationController` | `/api/v1/profile/notifications` | 2.1 `GET ""` | `listMine` | 200 B1 |
| | | 2.2 `GET /unread-count` | `countUnread` | 200 B1 |
| | | 2.3 `POST /{notificationId}/read` | `markRead` | 204 N1 |
| | | 2.4 `POST /read` | `markAllRead` | 204 N1 |

Kiểu phân trang 2.1: **cursor** (`api-convention.md` §7.2 — danh sách thêm phần tử liên tục ở đầu; offset trôi khi thông báo mới đến giữa hai lần tải). `NotificationListQuery` dùng validator con trỏ (cấm `before` + `after`, `limit` 1..100, mặc định 50).

## 4. Service Layer

Không `@Transactional` mức class (USER-001 §4.6). Ranh giới: §4.10.

### 4.1. NotificationService — API của người nhận
```java
NotificationHistoryResponse listMine(UUID callerId, NotificationListQuery q);   // readOnly — 2.1
UnreadCountResponse countUnread(UUID callerId);                                 // readOnly — 2.2
void markRead(UUID callerId, UUID notificationId);                              // @Transactional — 2.3
void markAllRead(UUID callerId);                                                // @Transactional — 2.4
```
Deps: `NotificationRepository`, `NotificationMapper`, `Clock`. `visibleFrom = now − NotificationRules.RETENTION` (30 ngày) truyền vào **mọi** câu — thông báo quá hạn lưu không hiện dù job xoá chưa chạy.
- `listMine`: Q1 `findCaller(callerId, anchorId, visibleFrom)` (anchor = `before` ?: `after`) → rỗng hoặc `callerDeleted` → `USER_001` → có con trỏ mà `anchorCreatedAt = null` → `NOTIFICATION_002` → Q2 `findLatest` / `findBefore` / `findAfter` (biến thể `…Unread` khi `unreadOnly`) với `limit + 1` → `hasMore = size > limit`, cắt còn `limit`. Không con trỏ vẫn chạy Q1 (cờ đã xoá).
- `countUnread`: Q1 `countUnread(callerId, visibleFrom)` → rỗng / `callerDeleted` → `USER_001`.
- `markRead`: Q1 `findReadContext(callerId, notificationId, visibleFrom)` → `USER_001` → không có thông báo của caller → `NOTIFICATION_001` → đã đọc → return (idempotent, 1 truy vấn) → Q2 `markRead(callerId, notificationId)`.
- `markAllRead`: Q1 `findCaller(callerId, null, visibleFrom)` → `USER_001` → Q2 `markAllRead(callerId, visibleFrom)`.
- Không khoá, không retrier: `UPDATE … SET is_read = TRUE` idempotent; hai request đồng thời cho cùng kết quả.

### 4.2. Listener (`application/listener`) — chuyển event thành lời gọi dispatcher

```java
@Component @RequiredArgsConstructor @Slf4j
public class MatchNotificationListener {
    private final NotificationDispatcher dispatcher;

    /** Chạy trên notificationExecutor sau khi transaction phát event commit; lần thử bị rollback không tới đây. */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchRequestSentEvent e) { dispatchSafely(e, () -> dispatcher.matchRequestSent(e)); }
    // … mỗi event một method; dispatchSafely: catch RuntimeException → log ERROR (tên event + id trong payload), không ném
}
```
- Bốn class theo miền nguồn: `MatchNotificationListener` (MATCH-001, MATCH-002), `MercenaryNotificationListener`, `ReputationNotificationListener` (REPUTATION-001 + `UserVerificationChangedEvent`), `AccountNotificationListener` (USER-001, VENUE-001).
- Listener **không** mang `@Transactional` (Spring chỉ cho `@TransactionalEventListener` kèm `@Transactional` khi `REQUIRES_NEW`/`NOT_SUPPORTED` [Unverified — theo hành vi Spring Framework 6.1+]); transaction mở ở `NotificationDispatcher` (bean khác → qua proxy).
- **Không nghe:** `VenueReportedEvent` (Q8 — chỉ báo `VenueAdminAlertEvent`), `ReliabilityScoreChangedEvent`, `MatchRatedEvent.matchCompleted` (Q3 — chuyển `COMPLETED` không báo).
- Executor đầy → `CallerRunsPolicy`: chạy đồng bộ trên luồng phát event (luồng request đã commit / luồng job) — chậm response thay vì mất thông báo (ADR-0015). Rủi ro: ở pha sau commit, kết nối DB của transaction nguồn chưa trả về pool; dispatcher `REQUIRES_NEW` mở kết nối thứ hai → luồng phát giữ **2 kết nối** cùng lúc; nhiều luồng cùng rơi vào nhánh này có thể cạn pool HikariCP [Inference — chưa đo] `[cần xác nhận #12]`. Chỉ ghi rủi ro, chưa đổi thiết kế.

### 4.3. NotificationDispatcher — event → người nhận → bản nháp

Mỗi method `@Transactional(propagation = REQUIRES_NEW)` (ADR-0011 quy tắc 4; đúng cả khi chạy đồng bộ do `CallerRunsPolicy`): **Q1** một câu audience (§5.2) → dựng `List<NotificationDraft>` bằng `NotificationTexts` → **Q2** `notificationWriter.write(drafts, now)` (bỏ câu khi danh sách rỗng). Dòng audience có `captainUserId = null` (đội đã giải tán / chưa có đội trưởng) bị bỏ. Payload event chỉ có id (ADR-0011) → dispatcher đọc lại trạng thái hiện tại; đối tượng đã đổi tiếp (vd lời mời đã được chấp nhận trước khi listener chạy) xử lý theo cột "Bỏ qua khi".

| Event (miền) | Q1 audience | Thông báo → người nhận (`referenceType` / id) | Bỏ qua khi |
|---|---|---|---|
| `UserRegisteredEvent` (USER-001 — §4.9) | — (không Q1) | `WELCOME` → `userId` (`USER` / `userId`) | — |
| `TeamInvitationCreatedEvent` (USER-001 — §4.9) | `findInvitationAudience(invitationId)` | `TEAM_INVITATION` → người được mời (`TEAM_INVITATION` / `invitationId`) | Lời mời không còn `PENDING` |
| `TeamInvitationRespondedEvent` (USER-001 — §4.9) | `findTeamAudience([teamId])` | `TEAM_INVITATION_ACCEPTED` / `TEAM_INVITATION_REJECTED` theo `accepted` → đội trưởng (`TEAM` / `teamId`) | — |
| `CaptainTransferredEvent` (USER-001 — §4.9) | `findTeamAudience([teamId])` (tên đội) | `CAPTAIN_TRANSFERRED` → **chỉ** `newCaptainUserId` (payload — không lấy từ audience; Q13) (`TEAM`) | — |
| `TeamStatusChangedByAdminEvent` (USER-001 — §4.9) | `findTeamAudience([teamId])` | `TEAM_MARKED_INACTIVE` / `TEAM_REACTIVATED` theo `status` → đội trưởng (`TEAM`). Không đưa lý do tạm ngưng vào nội dung (văn bản tự do của Admin — Q5) | — |
| `VenueAdminAlertEvent` (VENUE-001) | `findActiveAdminIds()` | `VENUE_ADMIN_ALERT` theo `alertType` → mỗi Admin (`VENUE` / `venueId`); tên sân lấy từ payload | Không có Admin |
| `MatchRequestSentEvent` | `findMatchAudience(matchId, [author, requester], null, null)` | `MATCH_REQUEST_RECEIVED` → đội trưởng đội tạo kèo (`MATCH`) | — |
| `MatchRequestRejectedEvent` | `(matchId, [requester], null, null)` | `REQUEST_REJECTED` → đội trưởng đội gửi (`MATCH`) | — |
| `MatchRequestWithdrawnEvent` (chỉ rút `PENDING`) | `(matchId, [author, requester], null, null)` | `REQUEST_WITHDRAWN` → đội trưởng đội tạo kèo (`MATCH`) | — |
| `MatchConfirmedEvent` có `acceptedRequestId` (luồng A) | `(matchId, [author, opponent], 'SUPERSEDED', null)` | `REQUEST_ACCEPTED` → đội trưởng đội đã ghép; `REQUEST_SUPERSEDED` → đội trưởng mỗi dòng nguồn `REQUEST` (`MATCH`). Đội tạo kèo (người bấm chấp nhận) không nhận (UC-M04 bước 7) | — |
| `MatchConfirmedEvent` có `pairingId` (luồng B) | `(matchId, [author, opponent], null, 'SUPERSEDED')` | `MATCH_CONFIRMED` → đội trưởng **hai** đội (UC-M07 bước 8); `PAIRING_SUPERSEDED` → mỗi dòng nguồn `PAIRING` (`MATCH`) | — |
| `PairingCreatedEvent` (MATCH-002) | `(matchId, [author, interested], null, null)` | `PAIRING_NEW` → đội trưởng đội tạo kèo (`PAIRING` / `pairingId`) | — |
| `MatchCancelledEvent` `previousStatus = CONFIRMED` (huỷ, hoặc rút yêu cầu đã chấp nhận) | `(matchId, [cancelledBy, other], null, null)` | `MATCH_CANCELLED` → đội trưởng đội `otherTeamId` (`MATCH`) | — |
| `MatchCancelledEvent` `previousStatus = OPEN` | `(matchId, [author], null, 'SUPERSEDED')` | `MATCH_CANCELLED` (biến thể "đang thương lượng") → mỗi dòng nguồn `PAIRING` (`MATCH`). Kèo A `OPEN` không có yêu cầu chờ khi huỷ (MATCH-001 `MATCH_010`) → không ai | Không có dòng `PAIRING` |
| `MatchExpiredEvent` | `(matchId, [author], 'EXPIRED', 'EXPIRED')` | `MATCH_EXPIRED` → đội trưởng đội tạo kèo; `REQUEST_EXPIRED` / `PAIRING_EXPIRED` → mỗi dòng nguồn tương ứng (`MATCH`) | — |
| `MatchArchivedEvent` | `findGameAudience([matchId], ['ARCHIVED','COMPLETED'], [])` (đội trưởng hai đội + mercenary `APPROVED` của kèo; `COMPLETED` vì một đội có thể đã đánh giá trước khi listener chạy) | `MATCH_COMPLETED` → đội trưởng hai đội (`MATCH`); `MERCENARY_RATING_REMINDER` → mỗi mercenary `APPROVED` (`MERCENARY_APPLICATION` / `applicationId`) (Q11d) | — |
| `MatchRatedEvent` (REPUTATION-001) | `(matchId, [rater, ratee], null, null)` (cột `hasRated`) | `RATING_RECEIVED` → đội trưởng đội `ratee` **chỉ khi** dòng `ratee` có `hasRated` (Q3, BC-R04) (`MATCH`) | `ratee` chưa đánh giá |
| `TeamReportedEvent` (2.3, 2.10) | `findReportAudience(reportId, false)` | `REPORT_RECEIVED` → bên bị report (`REPORT` / `reportId`); `NOSHOW` thêm câu hạn 24 giờ | — |
| `ReportDisputedEvent` (đội và mercenary) | `findReportAudience(reportId, true)` | `REPORT_DISPUTED` → bên đã report (`REPORT`); `DISPUTE_CREATED` → mỗi Admin (`DISPUTE` khi `applicationId = null`, `MERCENARY_DISPUTE` khi khác — id = `disputeId`) | — |
| `DisputeResolvedEvent` (đội và mercenary) | `findReportAudience(reportId, false)` | `DISPUTE_RESOLVED` → hai bên (`REPORT`), biến thể theo `decision` × bên | — |
| `TeamVerificationChangedEvent` | `findTeamAudience([teamId])` | `VERIFICATION_BADGE_GRANTED` / `…_REVOKED` theo `verified` → đội trưởng (`TEAM`) | — |
| `UserVerificationChangedEvent` (MERCENARY-001) | — (không Q1) | như trên → `userId` (`USER`) | — |
| `MercenaryApplicationSubmittedEvent` | `findApplicationAudience([applicationId])` | `MERCENARY_APPLICATION` → đội trưởng đội tuyển (`MERCENARY_APPLICATION`) | Đơn không còn `PENDING` |
| `MercenaryApplicationApprovedEvent` | như trên | `MERCENARY_APPLICATION_APPROVED` → ứng viên | — |
| `MercenaryApplicationRejectedEvent` | như trên | `MERCENARY_APPLICATION_REJECTED` (biến thể `cause`) → ứng viên | — |
| `MercenaryApplicationWithdrawnEvent` | như trên | `MERCENARY_WITHDRAWN` → đội trưởng đội tuyển, khi `previousStatus = APPROVED` hoặc `cause = ACCOUNT_DELETED` (MERCENARY-001 §4.11) | `cause = APPLICANT` và `previousStatus = PENDING` (không đọc DB) |
| `MercenaryRecruitmentClosedEvent` | `findApplicationAudience(rejectedApplicationIds)` | `MERCENARY_APPLICATION_REJECTED` (biến thể "tin đã đóng") → mỗi ứng viên | Danh sách rỗng (không đọc DB) |
| `MercenaryGameCancelledEvent` | `findApplicationAudience(applicationIds)` | `MERCENARY_GAME_CANCELLED` → mỗi mercenary | Danh sách rỗng |
| `MercenaryRatingCreatedEvent` | `findApplicationAudience([applicationId])` (cột `ratedByTeam`, `ratedByApplicant`) | `RATING_RECEIVED` → `CAPTAIN` chấm: ứng viên **chỉ khi** `ratedByApplicant`; `APPLICANT` chấm: đội trưởng đội tuyển **chỉ khi** `ratedByTeam` (Q3) (`MERCENARY_APPLICATION`) | Người nhận chưa đánh giá |
| `MercenaryReportCreatedEvent` | `findReportAudience(reportId, false)` | `REPORT_RECEIVED` → bên bị report (`REPORT`) | — |

- **Một thông báo mỗi (người nhận, loại, đối tượng)** trong một lần dispatch: writer khử trùng (BR-M18 — một người làm đội trưởng hai đội).
- Dispatcher không phát event (ADR-0011 quy tắc 5).
- Nhắc trước trận không có event nguồn → `NotificationJobService` (§4.7).

### 4.4. NotificationWriter, NotificationDraft, NotificationTexts (`support/notification`)

```java
public record NotificationDraft(UUID recipientUserId, Notification.Type type, String title, String body,
                                Notification.ReferenceType referenceType, UUID referenceId) {}

@Component
public class NotificationWriter {
    /** MANDATORY. Khử trùng (người nhận, loại, đối tượng), bỏ người nhận null, ghi một câu cho cả lô; người dùng đã xoá bị loại trong câu ghi.
        Sau commit: đẩy NOTIFICATION_CREATED cho từng dòng đã ghi và gọi PushSender. Trả số dòng đã ghi. */
    public int write(List<NotificationDraft> drafts, OffsetDateTime now);
}
```
- Q: `NotificationRepository.insertAllReturning(...)` (§5.2) → `List<InsertedNotificationView>` → `userRealtimeGateway.publishAfterCommit(deliveries)` (một synchronization cho cả lô) → `pushSender` cũng chạy trong `afterCommit` (ngoài transaction — NFS §5).
- `title` ≤ 200 (cố định theo loại — luôn ngắn); `body` không giới hạn (TEXT). Không ghi log nội dung `title`/`body` (`security-backend.md` §5.3 — có tên đội); log chỉ loại + số dòng.

**`NotificationTexts`** — nguồn duy nhất của câu chữ. `{giờ}` = `HH:mm dd/MM` theo `Asia/Ho_Chi_Minh` của giờ bắt đầu trận; `{ tại sân}` = `" tại " + tên sân` khi có sân (kèo luồng B chưa ghép không có sân/giờ chốt → bỏ cả `{giờ}` lẫn `{ tại sân}`); `{loại}` = nhãn report SRS §6.1 (`NOSHOW` "bùng kèo", `BAD_PLAY` "chơi xấu", `BAD_COMMUNICATION` "giao tiếp tệ", `SKILL_MISMATCH` "không đúng trình độ"); `{vị trí}` = nhãn vị trí của tin. **Không** tên cá nhân (Q5); với mercenary chưa được duyệt **không** có tên sân (BR-ME04).

| `Type` | `title` | `body` (biến thể) |
|---|---|---|
| `WELCOME` | Chào mừng bạn đến với ArenaFlow | Hoàn thiện hồ sơ để bắt đầu tìm đội và tìm kèo. |
| `TEAM_INVITATION` | Lời mời vào đội | Đội {đội} mời bạn tham gia đội. |
| `TEAM_INVITATION_ACCEPTED` | Lời mời vào đội được chấp nhận | Có thành viên mới vừa chấp nhận lời mời vào đội {đội}. |
| `TEAM_INVITATION_REJECTED` | Lời mời vào đội bị từ chối | Một lời mời vào đội {đội} đã bị từ chối. |
| `CAPTAIN_TRANSFERRED` | Bạn là đội trưởng mới | Bạn đã trở thành đội trưởng đội {đội}. |
| `TEAM_MARKED_INACTIVE` | Đội bị tạm ngưng | Admin đã tạm ngưng hoạt động đội {đội}. Xem chi tiết đội để biết lý do. |
| `TEAM_REACTIVATED` | Đội được kích hoạt lại | Admin đã kích hoạt lại đội {đội}. |
| `MATCH_REQUEST_RECEIVED` | Yêu cầu ghép kèo mới | Đội {đội gửi} muốn ghép kèo {giờ}{ tại sân}. |
| `REQUEST_WITHDRAWN` | Yêu cầu ghép kèo đã bị rút | Đội {đội gửi} đã rút yêu cầu ghép kèo {giờ}{ tại sân}. |
| `REQUEST_ACCEPTED` | Yêu cầu ghép kèo được chấp nhận | Đội {đội tạo kèo} đã chấp nhận yêu cầu ghép kèo {giờ}{ tại sân}. |
| `REQUEST_REJECTED` | Yêu cầu ghép kèo bị từ chối | Đội {đội tạo kèo} đã từ chối yêu cầu ghép kèo {giờ}{ tại sân}. |
| `REQUEST_SUPERSEDED` | Kèo đã ghép với đội khác | Kèo {giờ}{ tại sân} của đội {đội tạo kèo} đã ghép với đội khác. |
| `REQUEST_EXPIRED` | Yêu cầu ghép kèo đã hết hạn | Kèo {giờ}{ tại sân} của đội {đội tạo kèo} đã hết hạn. |
| `PAIRING_NEW` | Có đội nhận kèo | Đội {đội nhận} đã nhận kèo của đội bạn — vào thương lượng để chốt sân, giờ và chi phí. |
| `PAIRING_SUPERSEDED` | Kèo đã ghép với đội khác | Kèo của đội {đội tạo kèo} mà đội bạn đang thương lượng đã ghép với đội khác. |
| `PAIRING_EXPIRED` | Thương lượng đã hết hạn | Kèo của đội {đội tạo kèo} mà đội bạn đang thương lượng đã hết hạn. |
| `MATCH_CONFIRMED` | Kèo đã chốt | Kèo giữa đội {đội tạo kèo} và đội {đội đã ghép} đã chốt: {giờ}{ tại sân}. |
| `MATCH_CANCELLED` | Kèo bị huỷ | Đã ghép: Đội {đội huỷ} đã huỷ kèo {giờ}{ tại sân}. · Đang thương lượng: Đội {đội tạo kèo} đã huỷ kèo mà đội bạn đang thương lượng. |
| `MATCH_EXPIRED` | Kèo đã hết hạn | Kèo {giờ}{ tại sân} của đội bạn đã hết hạn mà chưa ghép được đội. |
| `REMINDER_BEFORE_MATCH` | Sắp đến giờ đá | Đội trưởng: Trận với đội {đối thủ} lúc {giờ}{ tại sân} bắt đầu sau khoảng 2 giờ. · Mercenary: Trận của đội {đội tuyển} (vị trí {vị trí}) lúc {giờ}{ tại sân} bắt đầu sau khoảng 2 giờ. |
| `MATCH_COMPLETED` | Trận đấu hôm nay thế nào? | Hãy nhập tỷ số và đánh giá đội {đối thủ} (trận {giờ}). |
| `MERCENARY_RATING_REMINDER` | Trận đấu hôm nay thế nào? | Hãy đánh giá đội {đội tuyển} (trận {giờ}, vị trí {vị trí}). |
| `RATING_RECEIVED` | Bạn nhận được đánh giá | Hai đội: Đội {đội đánh giá} đã đánh giá đội bạn sau trận {giờ}. · Đội → mercenary: Đội {đội tuyển} đã đánh giá bạn sau trận {giờ}. · Mercenary → đội: Mercenary vị trí {vị trí} đã đánh giá đội bạn sau trận {giờ}. |
| `REPORT_RECEIVED` | Bạn bị report | Đội {đội report} đã report đội bạn / bạn: {loại}. · Mercenary vị trí {vị trí} đã report đội bạn: {loại}. · `NOSHOW` thêm: " Bạn có 24 giờ để tranh chấp." |
| `REPORT_DISPUTED` | Report của bạn bị tranh chấp | {Đội {đội} \| Mercenary vị trí {vị trí}} đã tranh chấp report bùng kèo của bạn; Admin sẽ xem xét. |
| `DISPUTE_RESOLVED` | Tranh chấp đã có kết quả | `CONFIRMED`: Admin giữ nguyên report bùng kèo (trận {giờ}). · `REJECTED` — bên bị report: Admin bác report bùng kèo (trận {giờ}); điểm uy tín đã bị trừ được hoàn lại. · `REJECTED` — bên report: Admin bác report bùng kèo của bạn (trận {giờ}). |
| `DISPUTE_CREATED` | Tranh chấp mới cần xử lý | Có tranh chấp report bùng kèo mới (trận {giờ}) đang chờ Admin xem xét. |
| `VERIFICATION_BADGE_GRANTED` | Đã nhận huy hiệu xác minh | Đội {đội} đã được cấp huy hiệu xác minh. · Bạn đã được cấp huy hiệu xác minh. |
| `VERIFICATION_BADGE_REVOKED` | Huy hiệu xác minh bị thu hồi | Đội {đội} không còn đủ điều kiện nên huy hiệu xác minh đã bị thu hồi. · (cá nhân) Bạn không còn đủ điều kiện… |
| `MERCENARY_APPLICATION` | Có người ứng tuyển | Có người ứng tuyển vị trí {vị trí} cho trận {giờ} của đội {đội tuyển}. |
| `MERCENARY_APPLICATION_APPROVED` | Bạn đã được duyệt | Đội {đội tuyển} đã duyệt bạn vị trí {vị trí} cho trận {giờ}{ tại sân}. |
| `MERCENARY_APPLICATION_REJECTED` | Đơn ứng tuyển không được duyệt | `CAPTAIN`: Đội {đội tuyển} đã từ chối đơn vị trí {vị trí} (trận {giờ}). · Tin đóng / hết hạn / trận huỷ: Tin tuyển vị trí {vị trí} của đội {đội tuyển} (trận {giờ}) đã đóng nên đơn của bạn không được duyệt. |
| `MERCENARY_WITHDRAWN` | Mercenary rút lui | `APPLICANT`: Mercenary vị trí {vị trí} đã rút lui khỏi trận {giờ}. · `ACCOUNT_DELETED`: Một ứng viên vị trí {vị trí} (trận {giờ}) đã xoá tài khoản nên đơn bị rút. |
| `MERCENARY_GAME_CANCELLED` | Trận đấu bị huỷ | Trận {giờ} của đội {đội tuyển} (vị trí {vị trí}) đã bị huỷ. |
| `VENUE_ADMIN_ALERT` | Cảnh báo địa điểm sân | `CLOSURE_REPORTED`: Địa điểm {sân} bị report đã đóng cửa — cần xác minh. · `STALE_180_DAYS`: Địa điểm {sân} không được chọn cho kèo nào trong 180 ngày — cần kiểm tra. |

Câu chữ là nội dung, không phải hợp đồng API (FE hiển thị nguyên văn) — sửa câu chữ là thay đổi nội bộ, chỉ áp cho thông báo tạo sau.

### 4.5. Kênh realtime cá nhân — port `UserRealtimeGateway` (`support/realtime`) + adapter (ADR-0014 Sửa đổi 2026-11-16)

```java
public interface UserRealtimeGateway {
    void publishAfterCommit(Collection<UserFrameDelivery> deliveries);   // MANDATORY: một TransactionSynchronization.afterCommit cho cả lô
}
public record UserFrameDelivery(UUID userId, UserFrame frame) {}
public sealed interface UserFrame {
    record NotificationCreated(NotificationResponse notification) implements UserFrame {}
    record ConversationActivity(UUID conversationId, UUID messageId, OffsetDateTime createdAt) implements UserFrame {}
}
```
- **Adapter `StompUserRealtimeGateway`** (`api/realtime`): `afterCommit` → `SimpMessagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", json)` với `{type: NOTIFICATION_CREATED | CONVERSATION_ACTIVITY, …}` (`API_Spec.md` §1.4). Lỗi gửi → log `WARN` (mã + id), không ném (đã commit). Frame dựng từ bộ nhớ, không đọc DB.
- **Sửa `WebSocketConfig`** (MATCH-001 §4.15): `enableSimpleBroker("/topic", "/queue")` (giữ heartbeat, scheduler, `setSelectorHeaderName(null)`); user destination prefix mặc định `/user` (ghi tường minh `setUserDestinationPrefix("/user")`).
- **Sửa `StompAuthChannelInterceptor.SUBSCRIBE`**: chấp nhận **đúng nguyên văn** `/user/queue/notifications` (không truy vấn DB — server chỉ giao frame của principal của phiên; vẫn kiểm `exp` của JWT lưu ở `CONNECT`: `exp ≤ now` → `AUTH_001`, như `/topic/conversations/{id}` — MATCH-001 §4.15) **hoặc** `^/topic/conversations/(UUID)$` như cũ; mọi destination khác (gồm `/queue/**` thô, `/user/queue/**`, `/user/{id}/…`) → `ERROR`. Principal của phiên: `UsernamePasswordAuthenticationToken(userId, …)` đã đặt ở `CONNECT` → `getName()` = `userId.toString()` = khoá của `convertAndSendToUser` `[cần xác nhận #7]`.
- **`CONVERSATION_ACTIVITY` từ MATCH-001 2.16** (Q9 — sửa MATCH-001 §4.14): `ConversationService.send` sau khi lưu tin → `userRealtimeGateway.publishAfterCommit(viewers − {callerId})` với `viewers` lấy từ cột đã có của `findContext` (MATCH-001 §5.3 thêm 4 cột: `partyACaptainUserId`, `partyADisbandedBy`, `partyBCaptainUserId`, `partyBDisbandedBy`): bên `TEAM` → đội trưởng `ACTIVE` hiện tại + người đã giải tán đội khi đội `DISBANDED` (chỉ xem — vẫn "xem được"); bên `USER` → `partyBId`. Không thêm truy vấn (2.16 giữ 2 / W6). Không tạo dòng `notification`.

### 4.6. PushSender (`support/notification`) — Q10
```java
public interface PushSender { void send(UUID recipientUserId, PushMessage message); }   // gọi sau commit, ngoài transaction
public record PushMessage(Notification.Type type, String title, String body, Notification.ReferenceType referenceType, UUID referenceId) {}
@Component class NoOpPushSender implements PushSender { /* không làm gì; log DEBUG loại + id người nhận */ }
```
Bản FCM (bảng thiết bị, API đăng ký, timeout, retry) thiết kế cùng lúc tích hợp FCM (Q10) — thay bean, không sửa writer.

### 4.7. NotificationJobService — nhắc trước trận (Q4) và nhắc mercenary đánh giá trận ngoài (Q11d)
```java
int sendDueStartReminders();    // mỗi lô một TransactionTemplate
int sendDueRatingReminders();   // mỗi lô một TransactionTemplate — chỉ trận ngoài hệ thống
```
Deps: `MatchReminderRepository`, `NotificationAudienceRepository`, `NotificationWriter`, `NotificationTexts`, `TransactionTemplate`, `NotificationProperties`, `Clock`. Gọi từ `MatchLifecycleScheduler` (MATCH-001 §4.10 — sửa) **sau** các tác vụ MERCENARY-001 trong cùng lần chạy 5 phút, lần lượt hai method; lỗi log `ERROR`, không chặn tác vụ khác. Không thêm job định kỳ.

**Nhắc trước trận** — mỗi lô (`now` cố định đầu lần chạy, `leadEnd = now + NotificationRules.REMINDER_LEAD` (2 giờ)):
1. **Claim** `matchReminderRepository.claimDueStart(now, leadEnd, leadSeconds, batchSize)` — một câu `INSERT INTO match_reminder … SELECT … ON CONFLICT DO NOTHING RETURNING match_id, external_match_id` (SQL §5.2). Điều kiện "đến hạn": kèo `CONFIRMED` có `now < match_start_time ≤ leadEnd` và **lúc ghép** (`match_request.responded_at` của yêu cầu `ACCEPTED` / `pairing.final_confirmed_at` của thương lượng `CONFIRMED`) ≤ `match_start_time − 2h` (Q4: ghép khi còn < 2 giờ → không nhắc); trận ngoài `SCHEDULED` cùng khoảng giờ, `created_at ≤ match_start_time − 2h` và có ≥ 1 đơn `APPROVED` (Q12 — không nhắc đội trưởng đội khai báo). Dòng `match_reminder` `kind = BEFORE_START` (unique theo trận + loại) là dấu "đã nhắc" — chống nhắc trùng giữa các lần chạy và nhiều instance.
2. Rỗng → dừng. **Audience** `findGameAudience(matchIds, ['CONFIRMED'], externalMatchIds)` — đội trưởng `ACTIVE` hai đội của kèo + mọi mercenary `APPROVED` tại lúc này (kèo và trận ngoài), kèm tên đội / đối thủ / sân / giờ / vị trí. Mercenary được duyệt sau lần nhắc không được nhắc bù (Q12).
3. `NotificationTexts` → `notificationWriter.write(drafts, now)` → commit (frame + push sau commit).
- Kèo bị huỷ giữa claim và audience: audience lọc `status = 'CONFIRMED'` / `'SCHEDULED'` → không nhắc; dấu nhắc vẫn còn (đúng: không nhắc lại).
- Job dừng lâu (> 2 giờ) → trận đã bắt đầu không được nhắc (điều kiện `match_start_time > now`).
- Mẫu claim khác ADR-0012 (không `SKIP LOCKED` trên `match`): không chuyển trạng thái `match`, không khoá dòng người dùng ghi; unique index của `match_reminder` bảo đảm đúng một lần (§10).

**Nhắc mercenary đánh giá trận ngoài** (Q11d — kèo trên hệ thống đi qua `MatchArchivedEvent`, §4.3; trận ngoài không có trạng thái "đã kết thúc" nên không có event) — mỗi lô:
1. **Claim** `claimDueRating(now, now − RATING_REMINDER_LOOKBACK, batchSize)`: trận ngoài `SCHEDULED` có `match_end_time ≤ now` và `> now − 24 giờ`, có ≥ 1 đơn `APPROVED`, chưa có dấu `kind = AFTER_END` → chèn dấu `RETURNING external_match_id`. Cửa sổ 24 giờ chặn nhắc hàng loạt trận cũ (lần chạy đầu sau triển khai, job ngừng lâu).
2. **Audience** `findGameAudience([], [], externalMatchIds)` (chỉ nhánh mercenary `APPROVED`) → `MERCENARY_RATING_REMINDER` (`MERCENARY_APPLICATION`).
3. `write` → commit. Trận ngoài bị huỷ (`CANCELLED`) không thoả `SCHEDULED` → không nhắc (BR-ME09: không đánh giá).

### 4.8. RetentionJobService — xoá hết hạn lưu (UC-N03; BR-N01, BR-N02, BC-N03)
```java
int purgeExpiredConversations();   // mỗi lô một TransactionTemplate
int purgeExpiredNotifications();   // mỗi lô một TransactionTemplate
int purgeOldReminders();           // một câu
```
Gọi từ `RetentionScheduler` (`api/scheduler`, không logic): `@Scheduled(cron = "${arenaflow.notification.retention.cron}", zone = "Asia/Ho_Chi_Minh")` lần lượt ba tác vụ; lỗi một tác vụ log `ERROR`, tác vụ khác vẫn chạy. Cron `-` = tắt (test).
- **Cuộc trò chuyện** (điều kiện MATCH-001 §11, MERCENARY-001 §11): `cutoff = now − CHAT_RETENTION` (90 ngày). Xoá dòng `conversation` (tin xoá theo `ON DELETE CASCADE`) khi trận của cuộc trò chuyện: kèo `COMPLETED`/`ARCHIVED` có `match_end_time < cutoff`; `CANCELLED` có `cancelled_at < cutoff`; `EXPIRED` có `expires_at < cutoff`; trận ngoài `SCHEDULED` có `match_end_time < cutoff`; `CANCELLED` có `cancelled_at < cutoff`. Áp cho mọi loại ngữ cảnh (`TEAM_MATCH` gồm đội không được ghép, `MERCENARY_APPLICATION`). Mỗi lô: `retentionRepository.deleteExpiredConversations(cutoff, batchSize)` (SQL §5.2) → dừng khi < `batchSize` hoặc hết `max-batches-per-run`.
- **Thông báo**: `cutoff = now − RETENTION` (30 ngày) → `deleteExpiredNotifications(cutoff, batchSize)` theo lô.
- **Dấu nhắc**: `deleteRemindersSentBefore(now − RETENTION)` — trận đã qua lâu, dấu không còn tác dụng.
- `DELETE` idempotent: hai instance chạy trùng → subquery `FOR UPDATE SKIP LOCKED` bỏ dòng instance kia đang xoá; không phát event (không có thông báo cho việc xoá).

### 4.9. Event mới ở USER-001 — sửa chức năng đã có (thiết kế gốc cập nhật cùng thay đổi này — `USER-001/CHANGELOG.md` v4.2)

| Event (`application/event/<miền>`) | Payload | Phát ở | Số truy vấn |
|---|---|---|---|
| `UserRegisteredEvent` (`event/user`) | `userId, occurredAt` | `AuthServiceImpl.createAccount` — trong `TransactionTemplate` tạo tài khoản, sau `saveAndFlush` liên kết OAuth (đăng ký song song đụng unique → rollback → không phát) | Không đổi |
| `TeamInvitationCreatedEvent` (`event/team`) | `invitationId, teamId, inviteeUserId, occurredAt` | `TeamMembershipServiceImpl.inviteMember` — trong lambda retrier, sau `saveAndFlush` lời mời | Không đổi (2.12 giữ 6/6) |
| `TeamInvitationRespondedEvent` (`event/team`) | `invitationId, teamId, accepted, occurredAt` | `TeamMembershipServiceImpl.acceptInvitation` (`accepted = true`) / `rejectInvitation` (`false`) — trong lambda retrier, sau `save` lời mời. Lời mời bị từ chối tự động khi người được mời xoá tài khoản (2.7) **không** phát (thao tác hệ thống, không phải người được mời trả lời) | Không đổi (2.13: 4/6, 2.14: 3/6) |
| `CaptainTransferredEvent` (`event/team`) | `teamId, newCaptainUserId, previousCaptainUserId, occurredAt` | `TeamServiceImpl.transferCaptain` — trong lambda retrier, sau bước ghi lịch sử đội trưởng | Không đổi (2.15 giữ 6/6) |
| `TeamStatusChangedByAdminEvent` (`event/team`) | `teamId, status` (`INACTIVE` \| `ACTIVE`)`, occurredAt` | `AdminTeamServiceImpl.markInactive` / `reactivate` — trong lambda retrier, sau `UPDATE` trạng thái | Không đổi (2.19: 3/6, 2.20: 4/6) |

Phát qua `ApplicationEventPublisher` (thêm dependency cho `AuthServiceImpl`, `TeamMembershipServiceImpl`, `TeamServiceImpl`, `AdminTeamServiceImpl`). Không đổi API USER-001. USER-002 không bị ảnh hưởng: các thao tác trên thuộc USER-001 (USER-002 chỉ đổi guard của chúng).

### 4.10. Transaction

| Method | Ranh giới |
|---|---|
| `listMine`, `countUnread` | `@Transactional(readOnly = true)` |
| `markRead`, `markAllRead` | `@Transactional` (không CAS, không retrier — `UPDATE` idempotent) |
| `NotificationDispatcher.*` | `@Transactional(propagation = REQUIRES_NEW)` — chạy trên `notificationExecutor` (hoặc luồng gọi khi `CallerRunsPolicy`) |
| `NotificationWriter.write`, `UserRealtimeGateway.publishAfterCommit` | `MANDATORY` |
| `sendDueStartReminders`, `sendDueRatingReminders`, `purgeExpiredConversations`, `purgeExpiredNotifications` | Mỗi lô một `TransactionTemplate` |
| `purgeOldReminders` | Một `TransactionTemplate` |
| Listener | Không transaction (gọi dispatcher qua proxy) |

## 5. Repository & Data Model

### 5.1. Migration `V10__notification.sql`

```sql
-- notification: thêm loại thông báo (giữ 12 loại của V1), ràng buộc màn hình liên quan, index danh sách theo thời gian
ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (type IN (
    'WELCOME', 'TEAM_INVITATION', 'TEAM_INVITATION_ACCEPTED', 'TEAM_INVITATION_REJECTED',
    'CAPTAIN_TRANSFERRED', 'TEAM_MARKED_INACTIVE', 'TEAM_REACTIVATED',
    'MATCH_REQUEST_RECEIVED', 'REQUEST_WITHDRAWN', 'REQUEST_ACCEPTED', 'REQUEST_REJECTED', 'REQUEST_SUPERSEDED', 'REQUEST_EXPIRED',
    'PAIRING_NEW', 'PAIRING_SUPERSEDED', 'PAIRING_EXPIRED',
    'MATCH_CONFIRMED', 'MATCH_CANCELLED', 'MATCH_EXPIRED', 'REMINDER_BEFORE_MATCH', 'MATCH_COMPLETED', 'MERCENARY_RATING_REMINDER',
    'RATING_RECEIVED', 'REPORT_RECEIVED', 'REPORT_DISPUTED', 'DISPUTE_RESOLVED', 'DISPUTE_CREATED',
    'VERIFICATION_BADGE_GRANTED', 'VERIFICATION_BADGE_REVOKED',
    'MERCENARY_APPLICATION', 'MERCENARY_APPLICATION_APPROVED', 'MERCENARY_APPLICATION_REJECTED',
    'MERCENARY_WITHDRAWN', 'MERCENARY_GAME_CANCELLED',
    'VENUE_ADMIN_ALERT'));
ALTER TABLE notification ADD CONSTRAINT notification_reference_type_check CHECK (reference_type IS NULL OR reference_type IN (
    'MATCH', 'PAIRING', 'TEAM', 'TEAM_INVITATION', 'USER', 'VENUE', 'REPORT', 'DISPUTE', 'MERCENARY_DISPUTE', 'MERCENARY_APPLICATION'));
ALTER TABLE notification ADD CONSTRAINT notification_reference_check CHECK ((reference_type IS NULL) = (reference_id IS NULL));
DROP INDEX idx_notification_recipient;
CREATE INDEX idx_notification_recipient_time ON notification(recipient_user_id, created_at DESC, id DESC);
CREATE INDEX idx_notification_recipient_unread ON notification(recipient_user_id, created_at DESC, id DESC) WHERE NOT is_read;
-- idx_notification_retention (created_at) của V1 giữ nguyên — job xoá 30 ngày

-- match_reminder: dấu "đã nhắc" — một dòng mỗi (kèo / trận ngoài, loại nhắc)
CREATE TABLE match_reminder (
    id UUID PRIMARY KEY,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('BEFORE_START', 'AFTER_END')),
    match_id UUID REFERENCES match(id) ON DELETE RESTRICT,
    external_match_id UUID REFERENCES external_match(id) ON DELETE RESTRICT,
    sent_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT match_reminder_game_check CHECK ((match_id IS NULL) <> (external_match_id IS NULL))
);
CREATE UNIQUE INDEX idx_match_reminder_match ON match_reminder(match_id, kind) WHERE match_id IS NOT NULL;
CREATE UNIQUE INDEX idx_match_reminder_external ON match_reminder(external_match_id, kind) WHERE external_match_id IS NOT NULL;
CREATE INDEX idx_match_reminder_sent ON match_reminder(sent_at);

-- job nhắc trước trận: kèo đã ghép / trận ngoài theo giờ bắt đầu
CREATE INDEX idx_match_confirmed_start ON match(match_start_time) WHERE status = 'CONFIRMED';
CREATE INDEX idx_external_match_scheduled_start ON external_match(match_start_time) WHERE status = 'SCHEDULED';
CREATE INDEX idx_external_match_scheduled_end ON external_match(match_end_time) WHERE status = 'SCHEDULED';   -- nhắc mercenary đánh giá
-- thông báo cho Admin: danh sách Admin đang hoạt động
CREATE INDEX idx_user_active_admin ON "user"(id) WHERE role = 'ADMIN' AND deleted_at IS NULL;
```
- **CHECK `type`:** 35 giá trị — chỉ **thêm**, giữ đủ 12 giá trị V1 → dữ liệu cũ (nếu có) không vi phạm. Tên `notification_type_check` đã kiểm trên Neon dev 2026-11-16 (PostgreSQL tự đặt cho CHECK cột của V1); môi trường khác kiểm lại trước khi chạy — tên khác → dừng, báo, không đoán `[cần xác nhận #8]`.
- `notification_reference_check`: `referenceType` và `referenceId` cùng `null` hoặc cùng có (`API_Spec.md` §4.1). V1 không có ràng buộc này; Neon dev 0 dòng (2026-11-16).
- **Index:** `idx_notification_recipient_time` cho 2.1 không lọc (thứ tự `(created_at DESC, id DESC)` khớp con trỏ row comparison); `idx_notification_recipient_unread` (partial) cho 2.1 `unreadOnly`, 2.2, 2.4. Thay `idx_notification_recipient (recipient_user_id, is_read, created_at DESC)` của V1 — index V1 không phục vụ được danh sách không lọc (phải trộn hai nhánh `is_read`) và thiếu `id` cho con trỏ.
- **`match_reminder`** (thay vì cột trên `match` / `external_match`): không đụng entity `Match` / `ExternalMatch` có `@Version` (bulk `UPDATE match` phải tăng `version` — MATCH-001 §5.2 — làm thao tác người dùng đồng thời bị retry vì một cờ không nghiệp vụ); claim bằng unique index (§4.7) không khoá dòng `match`. FK `RESTRICT` (ADR-0002: kèo không bị xoá cứng). Hai khoá XOR như `mercenary_recruitment` (MERCENARY-001 §5.1); `kind`: `BEFORE_START` (nhắc 2 giờ trước — kèo và trận ngoài), `AFTER_END` (nhắc mercenary đánh giá — chỉ trận ngoài; kèo dùng `MatchArchivedEvent`).
- `idx_user_active_admin`: số Admin nhỏ nhưng bảng `"user"` lớn và chưa có index `role`; `VenueAdminAlertEvent` của job 180 ngày có thể phát nhiều event trong một lần chạy.
- Cuộc trò chuyện: dùng `idx_conversation_match`, `idx_conversation_external_match` (V9) và khoá chính `match` / `external_match` cho job xoá — không thêm index (§8 `Processing_Flow.md`).
- Số hiệu V10: thiết kế đã chiếm V5–V9 (VENUE-001, MATCH-001, MATCH-002, REPUTATION-001, MERCENARY-001); Flyway không chạy out-of-order → V10 chỉ áp sau V9 `[cần xác nhận #1]`.

### 5.2. Repository

Native + interface projection như USER-001 §5; mảng Java bind vào `CAST(:x AS uuid[])` / `text[]` như MATCH-001 `insertAll` `[cần xác nhận #4]`; câu `INSERT … RETURNING` khai báo native trả projection, không `@Modifying` (như MATCH-002 `confirmSide`, MATCH-001 `insertAllReturning`).

**`NotificationRepository`**
```java
Optional<NotificationCallerView> findCaller(UUID callerId, UUID anchorId, OffsetDateTime visibleFrom);
List<NotificationView> findLatest(UUID callerId, OffsetDateTime visibleFrom, int limit);         // + findLatestUnread
List<NotificationView> findBefore(UUID callerId, OffsetDateTime visibleFrom, OffsetDateTime anchorAt, UUID anchorId, int limit); // + …Unread
List<NotificationView> findAfter(UUID callerId, OffsetDateTime visibleFrom, OffsetDateTime anchorAt, UUID anchorId, int limit);  // + …Unread
Optional<UnreadCountView> countUnread(UUID callerId, OffsetDateTime visibleFrom);
Optional<NotificationReadContextView> findReadContext(UUID callerId, UUID notificationId, OffsetDateTime visibleFrom);
@Modifying int markRead(UUID callerId, UUID notificationId);
@Modifying int markAllRead(UUID callerId, OffsetDateTime visibleFrom);
List<InsertedNotificationView> insertAllReturning(UUID[] recipients, String[] types, String[] titles, String[] bodies,
                                                  String[] referenceTypes, UUID[] referenceIds, OffsetDateTime now);
```
- `findCaller`: `SELECT u.deleted_at IS NOT NULL AS caller_deleted, a.created_at AS anchor_created_at FROM "user" u LEFT JOIN notification a ON a.id = :anchorId AND a.recipient_user_id = u.id AND a.created_at >= :visibleFrom WHERE u.id = :callerId`.
- Trang: `WHERE n.recipient_user_id = :callerId AND n.created_at >= :visibleFrom [AND NOT n.is_read] [AND (n.created_at, n.id) < (:anchorAt, :anchorId)] ORDER BY n.created_at DESC, n.id DESC LIMIT :limit` (`after`: `>` và `ASC`). Hai **câu cố định** theo `unreadOnly` (không `(:flag OR …)` — để planner dùng đúng index partial).
- `countUnread`: `SELECT u.deleted_at IS NOT NULL AS caller_deleted, (SELECT count(*) FROM notification n WHERE n.recipient_user_id = u.id AND NOT n.is_read AND n.created_at >= :visibleFrom) AS unread_count FROM "user" u WHERE u.id = :callerId`.
- `findReadContext`: như `findCaller` với `n.id = :notificationId` → `caller_deleted`, `n.id IS NOT NULL AS found`, `n.is_read`.
- `markRead`: `UPDATE notification SET is_read = TRUE WHERE id = :id AND recipient_user_id = :callerId AND NOT is_read` (lọc người nhận cả ở câu ghi, không chỉ dựa vào Q1). `markAllRead`: `… WHERE recipient_user_id = :callerId AND NOT is_read AND created_at >= :visibleFrom`.
- `insertAllReturning`:
  ```sql
  INSERT INTO notification (id, recipient_user_id, type, title, body, reference_type, reference_id, is_read, created_at)
  SELECT uuidv7(), t.recipient, t.type, t.title, t.body, t.ref_type, t.ref_id, FALSE, :now
  FROM unnest(CAST(:recipients AS uuid[]), CAST(:types AS text[]), CAST(:titles AS text[]), CAST(:bodies AS text[]),
              CAST(:referenceTypes AS text[]), CAST(:referenceIds AS uuid[])) WITH ORDINALITY AS t(recipient, type, title, body, ref_type, ref_id, ord)
  JOIN "user" u ON u.id = t.recipient AND u.deleted_at IS NULL      -- người nhận đã xoá tài khoản: bỏ trong cùng câu
  ORDER BY t.ord
  RETURNING id, recipient_user_id, type, title, body, reference_type, reference_id, created_at
  ```
  Id do `uuidv7()` của PostgreSQL ≥ 18 sinh (ADR-0008 Sửa đổi 2026-11-09); `created_at = :now` từ `Clock` (không theo `@PrePersist`).

**`NotificationAudienceRepository`** (chỉ đọc; mỗi method **một** câu)
```java
List<MatchAudienceRowView> findMatchAudience(UUID matchId, UUID[] teamIds, String requestStatus, String pairingStatus);
List<ApplicationAudienceRowView> findApplicationAudience(UUID[] applicationIds);
List<ReportAudienceRowView> findReportAudience(UUID reportId, boolean includeAdmins);
List<TeamAudienceRowView> findTeamAudience(UUID[] teamIds);
Optional<InvitationAudienceView> findInvitationAudience(UUID invitationId);
List<UUID> findActiveAdminIds();
List<GameAudienceRowView> findGameAudience(UUID[] matchIds, String[] matchStatuses, UUID[] externalMatchIds);
```
- `findMatchAudience`:
  ```sql
  WITH g AS (SELECT m.id, m.type, m.status, m.match_start_time, m.author_team_id, m.confirmed_opponent_team_id,
                    v.name AS venue_name, at.name AS author_team_name
             FROM match m JOIN team at ON at.id = m.author_team_id LEFT JOIN venue v ON v.id = m.venue_id WHERE m.id = :matchId),
       aud AS (SELECT x.team_id, 'LISTED' AS source FROM unnest(CAST(:teamIds AS uuid[])) AS x(team_id)
               UNION ALL SELECT r.requester_team_id, 'REQUEST' FROM match_request r WHERE r.match_id = :matchId AND r.status = :requestStatus
               UNION ALL SELECT p.interested_team_id, 'PAIRING' FROM pairing p WHERE p.match_id = :matchId AND p.status = :pairingStatus)
  SELECT g.*, a.source, a.team_id, t.name AS team_name, cm.user_id AS captain_user_id,
         EXISTS (SELECT 1 FROM rating rt WHERE rt.match_id = g.id AND rt.rater_type = 'TEAM' AND rt.rater_id = a.team_id
                 AND rt.application_id IS NULL) AS has_rated
  FROM g CROSS JOIN aud a JOIN team t ON t.id = a.team_id
  LEFT JOIN team_member cm ON cm.team_id = a.team_id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
  ```
  Tham số trạng thái `null` → nhánh không trả dòng. Yêu cầu / thương lượng `SUPERSEDED`, `EXPIRED` của một kèo chỉ phát sinh ở đúng một lần ghép / hết hạn (kèo không ghép lại, không hết hạn hai lần) → "mọi dòng trạng thái đó của kèo" = đúng các đội vừa bị ảnh hưởng (MATCH-001 `Processing_Flow.md` #6). Đội trưởng bị xoá tài khoản không còn `team_member` `ACTIVE` (USER-002) → `captain_user_id = null`.
- `findApplicationAudience`: đơn + tin + đội tuyển + đội trưởng `ACTIVE` + trận qua `MercenarySql.GAME_*` (giờ bắt đầu, tên sân; MERCENARY-001 §5.3) + `EXISTS rating` theo `application_id` cho `rated_by_team` (`rater_type = 'TEAM'`) / `rated_by_applicant` (`rater_type = 'INDIVIDUAL'`), `WHERE a.id = ANY(:applicationIds)`.
- `findReportAudience`: `report r` (+ `dispute d` theo `report_id` — quan hệ 1:1, mỗi report tối đa một tranh chấp: unique `idx_dispute_report` (REPUTATION-001 §5.1), + đơn / tin khi `application_id` khác null, + trận qua `match_id` / `external_match_id` lấy giờ bắt đầu) → `UNION ALL` các dòng người nhận: `REPORTER` (đội: đội trưởng `ACTIVE`; cá nhân: `reporter_id`), `REPORTED` (tương tự), `ADMIN` (`"user"` `role = 'ADMIN' AND deleted_at IS NULL`, chỉ khi `:includeAdmins`). Mỗi dòng mang `audience`, `recipient_user_id`, tên đội hai bên, vị trí (đơn), `report_type`, `application_id`, `dispute_id`, `decision`, `game_start`.
- `findTeamAudience`: `team` + đội trưởng `ACTIVE` theo `ANY(:teamIds)`. `findInvitationAudience`: `team_member tm JOIN team t` → `tm.status`, `tm.user_id`, `t.name`. `findActiveAdminIds`: `SELECT id FROM "user" WHERE role = 'ADMIN' AND deleted_at IS NULL` (`idx_user_active_admin`).
- `findGameAudience`: `UNION ALL` ba nhánh — (1) đội trưởng `ACTIVE` của `author_team_id` và `confirmed_opponent_team_id` cho `match.id = ANY(:matchIds) AND status = ANY(:matchStatuses)` (mỗi dòng kèm tên đội đối thủ, sân, giờ); (2) đơn `APPROVED` của tin `match_id = ANY(:matchIds)` (cùng điều kiện trạng thái kèo); (3) đơn `APPROVED` của tin `external_match_id = ANY(:externalMatchIds)` với trận ngoài `SCHEDULED` — nhánh tách theo cột khoá trận để dùng `idx_mercenary_recruitment_match_all` / `…_external_all` (V9), không `OR`.

**`MatchReminderRepository`**
```java
List<ReminderGameView> claimDueStart(OffsetDateTime now, OffsetDateTime leadEnd, long leadSeconds, int limit); // leadSeconds = REMINDER_LEAD
List<UUID> claimDueRating(OffsetDateTime now, OffsetDateTime lookbackStart, int limit);                       // trận ngoài
```
```sql
-- claimDueStart
INSERT INTO match_reminder (id, kind, match_id, external_match_id, sent_at)
SELECT uuidv7(), 'BEFORE_START', d.match_id, d.external_match_id, :now FROM (
    SELECT m.id AS match_id, CAST(NULL AS uuid) AS external_match_id, m.match_start_time AS start_time
    FROM match m
    LEFT JOIN match_request mr ON mr.match_id = m.id AND mr.status = 'ACCEPTED'
    LEFT JOIN pairing p ON p.match_id = m.id AND p.status = 'CONFIRMED'
    WHERE m.status = 'CONFIRMED' AND m.match_start_time > :now AND m.match_start_time <= :leadEnd
      AND COALESCE(mr.responded_at, p.final_confirmed_at) <= m.match_start_time - make_interval(secs => :leadSeconds)
      AND NOT EXISTS (SELECT 1 FROM match_reminder x WHERE x.match_id = m.id AND x.kind = 'BEFORE_START')
    UNION ALL
    SELECT NULL, e.id, e.match_start_time
    FROM external_match e
    WHERE e.status = 'SCHEDULED' AND e.match_start_time > :now AND e.match_start_time <= :leadEnd
      AND e.created_at <= e.match_start_time - make_interval(secs => :leadSeconds)
      AND NOT EXISTS (SELECT 1 FROM match_reminder x WHERE x.external_match_id = e.id AND x.kind = 'BEFORE_START')
      AND EXISTS (SELECT 1 FROM mercenary_recruitment rc JOIN mercenary_application a ON a.recruitment_id = rc.id
                  WHERE rc.external_match_id = e.id AND a.status = 'APPROVED')
    ORDER BY start_time LIMIT :limit) d
ON CONFLICT DO NOTHING
RETURNING match_id, external_match_id

-- claimDueRating (trận ngoài)
INSERT INTO match_reminder (id, kind, external_match_id, sent_at)
SELECT uuidv7(), 'AFTER_END', e.id, :now FROM external_match e
WHERE e.status = 'SCHEDULED' AND e.match_end_time <= :now AND e.match_end_time > :lookbackStart
  AND NOT EXISTS (SELECT 1 FROM match_reminder x WHERE x.external_match_id = e.id AND x.kind = 'AFTER_END')
  AND EXISTS (SELECT 1 FROM mercenary_recruitment rc JOIN mercenary_application a ON a.recruitment_id = rc.id
              WHERE rc.external_match_id = e.id AND a.status = 'APPROVED')
ORDER BY e.match_end_time LIMIT :limit
ON CONFLICT DO NOTHING
RETURNING external_match_id
```
- `ON CONFLICT DO NOTHING` không chỉ định đích → áp cho mọi unique index, gồm hai index partial `[cần xác nhận #6]`: hai instance chọn trùng một trận → chỉ một bên chèn được (bên kia chờ commit rồi bỏ dòng) → nhắc đúng một lần, không khoá `match`.
- `match_request.responded_at` được ghi khi chấp nhận (MATCH-001 §5.2 `acceptIfPending`); `pairing.final_confirmed_at` khi hoàn tất (MATCH-002 §5.2). Unique `idx_match_request_accepted` (V6) → tối đa một dòng `mr`; pairing `CONFIRMED` tối đa một mỗi kèo.

**`RetentionRepository`**
```java
@Modifying int deleteExpiredConversations(OffsetDateTime cutoff, int limit);
@Modifying int deleteExpiredNotifications(OffsetDateTime cutoff, int limit);
@Modifying int deleteRemindersSentBefore(OffsetDateTime cutoff);
```
```sql
-- cuộc trò chuyện: tin nhắn xoá theo ON DELETE CASCADE
DELETE FROM conversation WHERE id IN (
    SELECT c.id FROM conversation c
    LEFT JOIN match m ON m.id = c.match_id
    LEFT JOIN external_match e ON e.id = c.external_match_id
    WHERE (m.status IN ('COMPLETED', 'ARCHIVED') AND m.match_end_time < :cutoff)
       OR (m.status = 'CANCELLED' AND m.cancelled_at < :cutoff)
       OR (m.status = 'EXPIRED' AND m.expires_at < :cutoff)
       OR (e.status = 'SCHEDULED' AND e.match_end_time < :cutoff)
       OR (e.status = 'CANCELLED' AND e.cancelled_at < :cutoff)
    ORDER BY c.id LIMIT :limit FOR UPDATE OF c SKIP LOCKED);

DELETE FROM notification WHERE id IN (
    SELECT id FROM notification WHERE created_at < :cutoff ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED);
```
- Câu cuộc trò chuyện đi từ `conversation` (chỉ còn cuộc trò chuyện chưa hết hạn lưu, vì cuộc đã hết hạn bị xoá dần) thay vì đi từ `match` (mọi kèo đã kết thúc tồn tại mãi → quét lại mỗi ngày). Đánh đổi: quét toàn bảng `conversation` mỗi ngày — `Processing_Flow.md` §9.
- Mốc `COMPLETED`/`ARCHIVED` = `match_end_time`, `CANCELLED` = `cancelled_at`, `EXPIRED` = `expires_at` (BC-N03, MATCH-001 §11); trận ngoài `SCHEDULED` = `match_end_time`, `CANCELLED` = `cancelled_at` (MERCENARY-001 §11). Kèo `OPEN`/`CONFIRMED` chưa kết thúc → không xoá.
- Không bảng nào có FK tới `conversation` ngoài `conversation_message` (`CASCADE`); report qua chat lưu `match_id`, không lưu `conversation_id` (REPUTATION-001 §4.2) → xoá không đụng report.

### 5.3. Projection

| Projection | Cột |
|---|---|
| `NotificationView`, `InsertedNotificationView` | `id`, `recipientUserId` (chỉ Inserted), `type`, `title`, `body`, `referenceType`, `referenceId`, `isRead` (chỉ View), `createdAt` |
| `NotificationCallerView` | `callerDeleted`, `anchorCreatedAt`, `anchorId` |
| `UnreadCountView` | `callerDeleted`, `unreadCount` |
| `NotificationReadContextView` | `callerDeleted`, `found`, `isRead` |
| `MatchAudienceRowView` | `matchId`, `matchType`, `matchStatus`, `matchStartTime`, `venueName`, `authorTeamId`, `authorTeamName`, `opponentTeamId`, `source` (`LISTED`\|`REQUEST`\|`PAIRING`), `teamId`, `teamName`, `captainUserId`, `hasRated` |
| `ApplicationAudienceRowView` | `applicationId`, `applicationStatus`, `applicantId`, `position`, `recruitmentId`, `teamId`, `teamName`, `captainUserId`, `matchId`, `externalMatchId`, `gameStartTime`, `venueName`, `ratedByTeam`, `ratedByApplicant` |
| `ReportAudienceRowView` | `audience` (`REPORTER`\|`REPORTED`\|`ADMIN`), `recipientUserId`, `reportId`, `reportType`, `reporterType`, `reportedType`, `reporterTeamName`, `reportedTeamName`, `position`, `applicationId`, `disputeId`, `decision`, `gameStartTime` |
| `TeamAudienceRowView` | `teamId`, `teamName`, `captainUserId` |
| `InvitationAudienceView` | `invitationStatus`, `inviteeUserId`, `teamName` |
| `ReminderGameView` | `matchId`, `externalMatchId` |
| `GameAudienceRowView` | `kind` (`CAPTAIN`\|`MERCENARY`), `recipientUserId`, `matchId`, `externalMatchId`, `applicationId`, `startTime`, `venueName`, `teamName`, `opponentTeamName`, `position` |

## 6. Entity

`Notification` (sửa — `domain/.../entity/chat/`, giữ vị trí hiện có):
- `type`: `String` → `@Enumerated(EnumType.STRING) Notification.Type` (35 giá trị — khớp CHECK V10); `referenceType`: `String` → `Notification.ReferenceType` (10 giá trị — khớp CHECK).
- Entity chỉ để `ddl-auto=validate` và đọc lẻ khi cần; **mọi INSERT** qua `insertAllReturning` (id `uuidv7()`, `created_at = :now`), mọi UPDATE qua câu `@Modifying` — không ghi qua dirty checking. Không `@Version` (chỉ cột `is_read` đổi, idempotent).
- Enum `Type` / `ReferenceType` đặt trong entity (như `Conversation.ContextType` — MATCH-001 §6) để `domain` và `application` cùng dùng.

## 7. DTO & Mapper

- `NotificationListQuery(Boolean unreadOnly, UUID before, UUID after, Integer limit)` — validator con trỏ (§2); `unreadOnly` mặc định `false`; hằng `NotificationRules.DEFAULT_LIMIT = 50`, `MAX_LIMIT = 100`.
- `NotificationResponse(UUID notificationId, Notification.Type type, String title, String body, Notification.ReferenceType referenceType, UUID referenceId, boolean isRead, OffsetDateTime createdAt)` — dùng cho 2.1 và frame `NOTIFICATION_CREATED` (`isRead = false`).
- `NotificationHistoryResponse(List<NotificationResponse> items, boolean hasMore)`; `UnreadCountResponse(long unreadCount)`.
- `NotificationMapper` (MapStruct, `unmappedTargetPolicy = ERROR`): `toResponse(NotificationView)`, `toResponse(InsertedNotificationView)` (`isRead = false`). Không che tên (nội dung không có tên cá nhân).
- **`NotificationRules`** (hằng một nơi): `RETENTION = 30 ngày` (BR-N02), `CHAT_RETENTION = 90 ngày` (BR-N01), `REMINDER_LEAD = 2 giờ` (Q4), `DEFAULT_LIMIT`, `MAX_LIMIT`, `ZONE = Asia/Ho_Chi_Minh` (định dạng giờ trong nội dung).

## 8. Cấu hình (nguồn duy nhất cho giá trị) & Dependencies

```properties
arenaflow.notification.reminder.batch-size=200
arenaflow.notification.reminder.max-batches-per-run=50
arenaflow.notification.reminder.rating-lookback=PT24H
arenaflow.notification.retention.cron=${NOTIFICATION_RETENTION_CRON:0 30 3 * * *}
arenaflow.notification.retention.conversation-batch-size=200
arenaflow.notification.retention.notification-batch-size=1000
arenaflow.notification.retention.max-batches-per-run=100
arenaflow.notification.executor.core-size=2
arenaflow.notification.executor.max-size=4
arenaflow.notification.executor.queue-capacity=1000
```

| Tham số | Lý do giá trị |
|---|---|
| Nhắc: lô 200 × 50 | Như job vòng đời kèo (MATCH-001 §8); số trận bắt đầu trong một cửa sổ 5 phút nhỏ hơn nhiều |
| `rating-lookback` 24 giờ | Nhắc mercenary đánh giá trận ngoài chỉ cho trận vừa kết thúc; tránh nhắc hàng loạt trận cũ lần chạy đầu / sau khi job ngừng lâu |
| Cron xoá 03:30 hằng ngày | UC-N03 "chạy hàng ngày"; sau job cảnh báo 180 ngày (03:00 — VENUE-001), giờ thấp điểm. `-` = tắt (test) |
| Lô xoá cuộc trò chuyện 200 | Mỗi cuộc kéo theo xoá tin (`CASCADE`) — lô nhỏ để transaction ngắn |
| Lô xoá thông báo 1000 × 100 lô | ≤ 100 000 dòng/lần; dòng nhỏ, xoá theo index `created_at` |
| Executor 2–4 luồng, hàng đợi 1000, `CallerRunsPolicy` | Mỗi việc ≤ 2 truy vấn ngắn; giới hạn số kết nối DB do thông báo chiếm (HikariCP mặc định 10 — `Processing_Flow.md` §9). Đầy hàng đợi → chạy trên luồng gọi (chậm thay vì mất); khi đó luồng gọi giữ 2 kết nối DB (§4.2) `[cần xác nhận #12]` |

- `AsyncConfig` (`api/config`): `@EnableAsync`; bean `ThreadPoolTaskExecutor notificationExecutor` (tiền tố luồng `notification-`, `setWaitForTasksToCompleteOnShutdown(true)`, `setAwaitTerminationSeconds(30)`, `CallerRunsPolicy`); `AsyncUncaughtExceptionHandler` log `ERROR` (chỉ tên method). Chỉ method có `@Async("notificationExecutor")` chạy bất đồng bộ.
- **`spring.task.scheduling.pool.size` 4 → 5** (giá trị ở MATCH-001 §8 — nguồn duy nhất, sửa cùng thay đổi này): thêm `RetentionScheduler`. Nhắc trước trận chạy trong `MatchLifecycleScheduler` (không thêm luồng).
- Không thêm Maven dependency (`spring-boot-starter-websocket` đã có từ MATCH-001; `@EnableAsync` thuộc `spring-context`).

## 9. Security

- Endpoint dưới `/api/v1/**` → `authenticated()`; không endpoint public, không đổi `SecurityConfig`. Mọi câu lọc `recipient_user_id = :callerId` — không có tham số người nhận; thông báo của người khác trả `404 NOTIFICATION_001` (không tiết lộ tồn tại — không dùng `403`).
- Tài khoản đã xoá còn token → `404 USER_001` (cùng quy tắc chat MATCH-001 §9); thông báo mới không tạo cho người đã xoá (câu INSERT lọc).
- **Kênh `/user/queue/notifications`:** chỉ phiên của principal nhận frame của mình; interceptor chặn mọi destination `/queue/**` thô và mẫu `/user/…` khác (§4.5) → không đăng ký được hàng đợi phiên của người khác. Xác thực ở `CONNECT` như MATCH-001 §4.15. Rủi ro ADR-0014 "kết nối không hết hạn theo token": người vừa xoá tài khoản không nhận frame mới (không có thông báo mới cho họ); `CONVERSATION_ACTIVITY` chỉ chứa id + thời điểm, không nội dung tin.
- **Nội dung:** không tên cá nhân, không email / số điện thoại (Q5); mercenary chưa được duyệt không thấy tên sân (BR-ME04). Admin nhận cảnh báo địa điểm / tranh chấp chỉ có tên sân / giờ trận.
- **Log:** không log `title`/`body` (có tên đội), không log token; listener / job log loại event + id + số dòng (`security-backend.md` §5.3).
- XSS: nội dung do server soạn từ tên đội / sân người dùng nhập — FE escape khi hiển thị (`security-backend.md` §4.2).

## 10. Lệch convention / chuẩn có chủ đích

| Quy ước | Thiết kế | Lý do | Trạng thái |
|---|---|---|---|
| ADR-0011 hệ quả "listener chạy đồng bộ sau commit" | Listener `@Async` trên executor riêng, `CallerRunsPolicy` | Không làm chậm response / lô job của miền phát; số truy vấn của API nguồn không đổi | [ADR-0015](../../decisions/0015-notification-async-listener-and-user-channel.md) |
| ADR-0012 mẫu claim `SELECT … FOR NO KEY UPDATE SKIP LOCKED` | Nhắc trước trận claim bằng `INSERT … ON CONFLICT DO NOTHING RETURNING` vào bảng dấu `match_reminder` | Không chuyển trạng thái `match` / `external_match`, không khoá dòng người dùng ghi; unique index bảo đảm một lần kể cả nhiều instance | Ghi nhận trong thiết kế (ADR-0012 cho phép "theo mẫu này hoặc ghi rõ" — ở đây an toàn nhiều instance) |
| ADR-0014 quyết định 3 / Sửa đổi 2026-11-15 (một mẫu destination `/topic/conversations/{id}`) | Thêm `/user/queue/notifications` + broker prefix `/queue` | Frame theo người dùng (thông báo, tín hiệu chat) không gắn cuộc trò chuyện | ADR-0014 Sửa đổi 2026-11-16 |
| `non-functional-standards.md` §4.1 ghi qua `save()` / `saveAll()` | INSERT gom lô native `unnest` + `uuidv7()` + `RETURNING` | Một câu cho n người nhận (không bật JDBC batch toàn ứng dụng — user chốt 2026-11-09, MATCH-001 §5.2); cần id cho frame | Tiền lệ MATCH-001 `insertAll`, ADR-0008 Sửa đổi 2026-11-09 |
| `convention-backend.md` §9 `@Transactional(readOnly)` mức class | Transaction theo method | Kế thừa USER-001 §4.6 | Đã có tiền lệ |
| `api-convention.md` §7 mặc định offset | 2.1 cursor | Danh sách thêm phần tử liên tục ở đầu (§3) | `api-convention.md` §7.2 |

## 11. Tài liệu cần đồng bộ

**Đã sửa cùng thay đổi này** (nhánh `feature/NOTIFICATION-001-in-app-notification`, commit `docs(NOTIFICATION-001): ...`):

| Tài liệu | Nội dung | Phiên bản |
|---|---|---|
| MATCH-001 `API_Spec.md` §1.7, `Component_Class_Design.md` §4.9, §4.10, §4.14, §4.15, §5.3, §8, §11, `Processing_Flow.md` §1.1, 2.16, §3.20 | Kênh `/user/queue/notifications` + frame `CONVERSATION_ACTIVITY` khi gửi tin; người nghe event chốt; scheduler gọi nhắc trước trận; pool 5; mục job xoá chat → đã thiết kế | v2.5 (MINOR) |
| MERCENARY-001 `Component_Class_Design.md` §4.11, §11, §12 #5 | Người nghe event chốt (đánh giá chỉ khi người nhận đã đánh giá); job xoá chat đã thiết kế | v2.1 (MINOR) |
| REPUTATION-001 `Component_Class_Design.md` §4.7, §12 #11 | Đóng #11 (Q3); `matchCompleted` không còn tạo thông báo | v1.3 (MINOR) |
| VENUE-001 `Component_Class_Design.md` §4.7, `Processing_Flow.md` §10 #1 | Đóng #1 (Q8): chỉ `VenueAdminAlertEvent` tạo thông báo; bỏ `VenueReportedEvent` | v4.2 (MINOR) |
| USER-001 `Component_Class_Design.md` §4.1, §4.3, §4.4, §4.5; `API_Spec.md` 2.13 (path param `invitationId`), §6 #1; `CHANGELOG.md` | `UserRegisteredEvent`, `TeamInvitationCreatedEvent`, `TeamInvitationRespondedEvent`, `CaptainTransferredEvent`, `TeamStatusChangedByAdminEvent` (§4.9); người được mời lấy `invitationId` từ thông báo `TEAM_INVITATION` (đóng §6 #1, chấp nhận rủi ro) | v4.2 (MINOR) |
| ADR-0014 | Sửa đổi 2026-11-16 — kênh theo người dùng | — |
| ADR-0015 (mới, `Accepted` 2026-11-16) | Listener thông báo bất đồng bộ + pipeline ghi gom lô + `PushSender` | — |
| ADR-0011 | Metadata "Liên quan" thêm ADR-0015; mục Sửa đổi 2026-11-16 "listener thông báo chạy `@Async`" (ADR-0015 `Accepted`); đóng §Cần theo dõi VENUE-001 #10 | — |

**Khi implement** (sau khi mọi phase code xong + test PASS — CLAUDE.md §8):

| Tài liệu | Nội dung | Nhánh |
|---|---|---|
| `docs/database/schema.dbml`, `schema-design.md` (`notification`, `match_reminder`), `data-model.md` §3.5 | V10: loại thông báo, CHECK, index; bảng dấu nhắc; retention 30/90 ngày đã thực thi | `feature/NOTIFICATION-001-*` |
| `docs/agent/api-convention.md` §5 (bảng 2xx: `GET /profile/notifications`, `GET /profile/notifications/unread-count` 200 B1; `POST /profile/notifications/{id}/read`, `POST /profile/notifications/read` 204 N1), §6 (domain `NOTIFICATION`: `NOTIFICATION_001`, `NOTIFICATION_002`), §7.2 (bảng "Áp dụng": `GET /profile/notifications` cursor, mốc `notificationId`) | Coordinator | `chore/agent-framework-*` |
| `docs/agent/convention-backend.md` §1 | `application/listener/`, `application/support/notification/`, `application/support/realtime/`, `api/notification/` | `chore/agent-framework-*` |
| `docs/agent/CLAUDE.md` §2 | Dòng điều hướng NOTIFICATION-001 | `chore/agent-framework-*` |

**SRS `SYSTEM-001` cần cập nhật** (business-analyst — không sửa ở đây):

| # | Chỗ SRS | Hiện tại | Cần thành (user chốt 2026-11-16) |
|---|---|---|---|
| S1 | Overview §4 (dòng "11 loại notification"), Business_Rules §8.6, AC checklist "NOTIFICATION DOMAIN", AC-N02, UC-N02 preconditions | 11 loại | 35 loại (`API_Spec.md` §4.2) — Q1, Q11 |
| S2 | Overview §4 "reminder trước trận"; thêm BR | Không có thời điểm / người nhận | Một lần, khoảng 2 giờ trước giờ bắt đầu (trễ ≤ ~5 phút); đội trưởng hai đội của kèo đã ghép + mercenary đã duyệt (kể cả trận ngoài); ghép khi còn < 2 giờ → không nhắc — Q4 (quy tắc riêng của trận ngoài: S9 — Q12) |
| S3 | Overview §4 "rating nhận", "Match completed (kèm lời nhắc đánh giá)"; AC-M08; BC-R04 | "Rating nhận" mọi lúc; completed chưa rõ thời điểm | Nhắc đánh giá lúc kèo `ARCHIVED` (AC-M08); `ARCHIVED → COMPLETED` không báo; "nhận đánh giá" chỉ khi người nhận đã đánh giá trước; đội ↔ mercenary như vậy — Q3 |
| S4 | UC-V03 bước 6, AC-V03 "Admin nhận notification"; UC-R03/R04; UC-ME08, AC-ME08 (tranh chấp report bùng giữa đội trưởng và mercenary) | Admin nhận mỗi report địa điểm; tranh chấp chưa ghi báo Admin | Admin (mỗi người một dòng) nhận cảnh báo địa điểm một lần mỗi đợt (BC-V05) và mỗi tranh chấp mới (đội và mercenary); không báo từng report — Q8 |
| S5 | UC-N01 / UC-ME09 / BR-N06 | Không nói thông báo cho tin nhắn | Tin nhắn không tạo thông báo; app đang mở nhận tín hiệu realtime để cập nhật chấm đỏ "Tin nhắn" — Q9 |
| S6 | UC-N02 bước 2–4; thêm BR | Người nhận / nội dung chưa định nghĩa | Sự kiện của đội → **đội trưởng tại lúc tạo thông báo** (ghi rõ trong SRS: có thể vài giây sau sự kiện, không phải đội trưởng đúng lúc sự kiện — chuyển quyền trong khoảng đó thì đội trưởng mới nhận; user chốt 2026-11-16), không chuyển thông báo cũ khi đổi đội trưởng (Q2); nội dung tiếng Việt soạn sẵn, không tên cá nhân (Q5); app đang mở nhận ngay (Q6); người dùng xem danh sách, đếm chưa đọc, đọc một / tất cả, không xoá (Q7) |
| S7 | Business_Rules §8.6, Overview Implementation note 6, Acceptance_Criteria DoD push (FCM/APNs, device token) | Push FCM/APNs cả 11 loại, lưu device token | Bản này chỉ in-app + realtime; push và thiết bị làm sau cùng FCM — Q10 |
| S8 | UC-N03 / BR-N02 | Xoá sau 30 ngày | Thêm: thông báo quá 30 ngày không còn hiển thị ngay cả trước lượt xoá hằng ngày |
| S9 | UC-T03 bước 5, UC-T04 bước 6 / AC-T03, UC-T05 bước 6; UC-ME06 / BR-ME13 | UC-T04 / AC-T03: "cả 2 (đội trưởng cũ và mới) nhận"; chưa có nhắc mercenary đánh giá | UC-T03: đội trưởng nhận khi lời mời được chấp nhận / từ chối; UC-T04 / AC-T03: **chỉ đội trưởng mới** nhận (Q13); UC-T05: đội trưởng nhận khi Admin tạm ngưng / kích hoạt lại (không kèm lý do); thêm: mercenary đã duyệt nhận nhắc đánh giá đội khi trận kết thúc (kèo: lúc `ARCHIVED`; trận ngoài: ≤ ~5 phút sau giờ kết thúc, không nhắc trận kết thúc quá 24 giờ) — Q11; trận ngoài: chỉ nhắc trước trận cho mercenary, khai báo khi còn < 2 giờ không nhắc, không nhắc bù — Q12 |

## 12. Danh sách [cần xác nhận]

Đánh số toàn thiết kế NOTIFICATION-001: 1–8 ở đây, 9–12 ở `Processing_Flow.md` §10. #2, #3 đã đóng (Q11–Q13); #11 đã đóng (user, 2026-11-16); `API_Spec.md` §6 không còn điểm mở.

| # | Nội dung | Loại |
|---|---|---|
| 1 | **Thứ tự implement:** listener tham chiếu event của VENUE-001, MATCH-001, MATCH-002, REPUTATION-001, MERCENARY-001, USER-001 (event mới §4.9); V10 cần V9 (`external_match`) | **Chặn code** — implement sau năm mã kia |
| 2 | ~~**Câu nghiệp vụ chưa chốt** (ngoài Q1–Q10): (a) báo đội trưởng khi lời mời được chấp nhận / từ chối (UC-T03); (b) báo đội trưởng cũ và mới khi chuyển quyền (UC-T04, AC-T03); (c) báo đội trưởng khi Admin tạm ngưng / kích hoạt lại đội (UC-T05); (d) nhắc mercenary đánh giá sau trận (kèo `ARCHIVED`; trận ngoài sau giờ kết thúc). Đề xuất: (a)–(c) có — SRS đã ghi, cần event mới ở USER-001 / USER-002 (không thêm truy vấn); (d) có — kèo: cùng `MatchArchivedEvent` thêm nhánh mercenary `APPROVED` vào audience; trận ngoài: thêm nhánh vào job 5 phút với dấu riêng~~ — **Đã chốt Q11, Q13 (user, 2026-11-16):** thêm cả 4 nhóm (6 loại), chuyển quyền chỉ báo đội trưởng mới — §4.3, §4.4, §4.7, §4.9, §5.1 | Đã đóng |
| 3 | ~~**Nhắc trước trận ngoài hệ thống** (Q4 không nói rõ): bản này chỉ nhắc mercenary `APPROVED` (không nhắc đội trưởng đội khai báo); trận ngoài khai báo khi còn < 2 giờ → không nhắc (như "ghép khi còn < 2 giờ"); mercenary được duyệt sau lần nhắc → không nhắc. Đề xuất giữ~~ — **Đã chốt Q12 (user, 2026-11-16):** giữ như thiết kế | Đã đóng |
| 4 | Bind mảng `uuid[]` / `text[]` vào native query và `INSERT … RETURNING` không `@Modifying` qua Spring Data / Hibernate 7.2 [Inference — cùng điểm mở MATCH-001 #1, MATCH-002 #4]; không bind được → `NamedParameterJdbcTemplate` trong cùng transaction (vẫn 1 câu) | Không chặn — kiểm bằng integration test đầu tiên của writer |
| 5 | `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` trên Spring Framework 7: listener chạy trên executor sau commit; lần thử rollback của retrier không giao event [Inference — theo cơ chế Spring, chưa chạy] | Không chặn — test "rollback không tạo thông báo", "commit tạo đúng một lần" |
| 6 | `ON CONFLICT DO NOTHING` không chỉ định đích áp cho unique index **partial** (`idx_match_reminder_match`, `…_external`) [Inference — theo tài liệu PostgreSQL, chưa chạy] | Không chặn — test hai lần claim đồng thời |
| 7 | Principal STOMP `getName()` = `userId.toString()` khớp `convertAndSendToUser`; interceptor inbound thấy destination gốc `/user/queue/notifications` trước khi chuyển thành hàng đợi theo phiên [Inference — chưa chạy] | Không chặn — kiểm cùng phase realtime (MATCH-001 #12) |
| 8 | Tên CHECK `notification_type_check` ở môi trường ngoài Neon dev | Không chặn — kiểm trước khi chạy V10; khác → dừng, báo |

## 13. Quyết định đã chốt (user, 2026-11-16)

| # | Quyết định | Nơi áp dụng |
|---|---|---|
| Q1 | 11 loại SRS + mọi loại design đã ghi người nghe, kể cả tuỳ chọn (rút yêu cầu chờ, mở tranh chấp) | §4.3, §5.1; `API_Spec.md` §4.2 |
| Q2 | Sự kiện của đội → đội trưởng **tại lúc tạo thông báo** (không phải đúng lúc sự kiện — làm rõ 2026-11-16, đóng `Processing_Flow.md` #11); không chuyển thông báo khi đổi đội trưởng | §1, §4.3; `API_Spec.md` §1.2 |
| Q3 | Nhắc đánh giá lúc `ARCHIVED`; `COMPLETED` không báo; "nhận đánh giá" chỉ khi người nhận đã đánh giá; đội ↔ mercenary như vậy | §4.3 (`hasRated`, `ratedBy*`) |
| Q4 | Nhắc một lần 2 giờ trước giờ bắt đầu; đội trưởng hai đội + mercenary `APPROVED`; ghép khi < 2 giờ không nhắc; chống trùng | §4.7, §5.1 `match_reminder` |
| Q5 | `title`/`body` soạn sẵn tiếng Việt, ảnh chụp lúc tạo, không tên cá nhân; `reference_type`/`reference_id` | §4.4; `API_Spec.md` §4.3 |
| Q6 | Frame `NOTIFICATION_CREATED` qua `/user/queue/notifications`; kết nối lại → gọi đếm chưa đọc | §4.5; ADR-0014 Sửa đổi 2026-11-16 |
| Q7 | Danh sách (mới nhất trước, lọc chưa đọc), đếm, đọc một, đọc tất cả; không xoá; phân trang do solution-architect chọn → cursor | §3, §4.1 |
| Q8 | Admin: cảnh báo địa điểm (một lần) + mỗi tranh chấp mới (đội và mercenary), mỗi Admin một dòng; không báo từng report địa điểm | §4.3; VENUE-001 v4.2 |
| Q9 | Tin chat không tạo thông báo; frame `CONVERSATION_ACTIVITY` tới người xem được (trừ người gửi) | §4.5; MATCH-001 v2.5 |
| Q10 | Không làm device token; `PushSender` no-op | §4.6 |
| Q11 | Thêm: lời mời được chấp nhận / từ chối → đội trưởng; chuyển quyền → đội trưởng mới; Admin tạm ngưng / kích hoạt lại → đội trưởng; nhắc mercenary `APPROVED` đánh giá sau trận (kèo: `MatchArchivedEvent`; trận ngoài: job 5 phút) | §4.3, §4.4, §4.7, §4.9, §5.1 |
| Q12 | Trận ngoài: chỉ nhắc trước trận cho mercenary `APPROVED`; khai báo khi còn < 2 giờ không nhắc; không nhắc bù | §4.7 |
| Q13 | Chuyển quyền đội trưởng: chỉ đội trưởng mới nhận | §4.3 |
