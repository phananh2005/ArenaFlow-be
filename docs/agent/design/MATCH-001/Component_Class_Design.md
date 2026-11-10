# Component & Class Design — Kèo (luồng A, luồng B) & vòng đời kèo

| Mục | Giá trị |
|---|---|
| Mã chức năng | `MATCH-001` |
| Phạm vi tầng | Backend (Spring Boot REST API). Frontend: ngoài phạm vi repo này |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · Spring Data JPA · Flyway · PostgreSQL |
| Quy ước áp dụng | `docs/agent/convention-backend.md`, `docs/agent/security-backend.md`, `docs/agent/non-functional-standards.md` · kế thừa USER-001 `Component_Class_Design.md` §4.6, §5, §10; VENUE-001 `Component_Class_Design.md` §4.6 (port), §4.7 (event), §4.9 (retrier overload), §4.13 (audit) · ADR-0003 (+ Sửa đổi 2026-11-09), ADR-0006, ADR-0008, [ADR-0011](../../decisions/0011-domain-events-after-commit.md), [ADR-0012](../../decisions/0012-scheduled-jobs-claim-with-skip-locked.md), [ADR-0013](../../decisions/0013-reputation-columns-atomic-update.md) (cả ba `Proposed`) |
| Ràng buộc dữ liệu | PostgreSQL ≥ 18 (`uuidv7()` — §5.2) · V1 + V5 (VENUE-001) + `V6__match_flow_a_lifecycle.sql` (§5.1) + `V7__match_flow_b_pairing.sql` (MATCH-002 `Component_Class_Design.md` §5.1); entity hiện có `domain/.../entity/match/{Match, MatchRequest, CostCustomItem, Sport}`, `team/Team`, `user/User` |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-09 · v1.0 (baseline) · hiện tại **v2.1** (solution-architect · 2026-11-11) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan kiến trúc

Layered theo 4 module Maven (ADR-0006) như USER-001. Hợp đồng: `API_Spec.md`. Luồng, transaction, số truy vấn, khoá: `Processing_Flow.md`. Điểm mới:
- **Vòng đời kèo** do `MatchService` / `MatchRequestService` (người dùng) và `MatchLifecycleJobService` (hệ thống) điều khiển; mọi thao tác ghi chạy qua `ConcurrencyConflictRetrier.execute(MATCH_014, …)` (ADR-0003, `@Version` trên `Match`).
- **Lazy check hết hạn** dùng chung một thao tác `MatchExpiry.expireIfDue` cho API ghi và job (§4.6); API đọc chỉ tính trạng thái hiệu lực trong SQL.
- **Lõi `ReputationService`** dùng chung (§4.8, ADR-0013): cộng/trừ Reliability Score đội/cá nhân bằng một `UPDATE` nguyên tử có chặn [0,100].
- **Domain event** `ApplicationEvent` cho thông báo (NOTIFICATION-001) và dữ liệu dẫn xuất tính lại được; không dùng để ghi dữ liệu nghiệp vụ (§4.9, ADR-0011).
- **Scheduled job** hết hạn + `ARCHIVED` claim lô bằng `FOR NO KEY UPDATE SKIP LOCKED` (§4.10, ADR-0012).
- **Bộ môn**: đọc + Admin bật/tắt (§4.4).
- **Kèo luồng B** (v2.0): tạo / tìm / xem / huỷ / hết hạn kèo `NEED_VENUE` ở các thành phần trên (nhánh theo `Match.Type`). Pairing, chat, xác nhận, chi phí + cọc luồng B, migration V7 và repository lựa chọn (`MatchVenueOptionRepository`, `MatchTimeOptionRepository`, `PairingRepository`): MATCH-002 `Component_Class_Design.md`.

**Điều kiện tiên quyết (VENUE-001 code):** `VenueSelectionService`, overload `ConcurrencyConflictRetrier.execute(ErrorCode, Supplier)`, `AdminAuditLog`, `SchedulingConfig`, package `application/event/`, mã `VENUE_001/002`, migration `V5__venue_management.sql` — thiết kế ở VENUE-001, chưa có trong code `[cần xác nhận #5]`.

## 2. Package Structure (delta)

```text
common/        com.phananh.arenaflow.common
└── exception/       ErrorCode (+ MATCH_001..MATCH_015, SPORT_001..SPORT_002)

domain/        com.phananh.arenaflow.domain
├── entity/match/    Match, MatchRequest, CostCustomItem (sửa — §6); Sport (giữ)
├── entity/team/     Team (sửa: reliabilityScore updatable = false)
├── entity/user/     User (sửa: reliabilityScore updatable = false)
├── repository/      MatchRepository (+ fragment MatchRepositoryCustom / MatchRepositoryImpl), MatchRequestRepository,
│                    CostCustomItemRepository, SportRepository (mới); TeamRepository, UserRepository (sửa)
└── projection/      MatchDetailView, MatchSummaryView, MatchRequestView, MatchRequestContextView,
                     MatchAuthorAccessView, MatchAvailabilityView, DueMatchView, TeamCaptainContextView (mới)

application/   com.phananh.arenaflow.application
├── service/         MatchService, MatchRequestService, SportService, MatchLifecycleJobService, ReputationService,
│   │                ReliabilityChange, ReliabilityReason, MatchStatusFilter, MatchRoleFilter,
│   │                MatchRequestStatusFilter (mới)
│   └── impl/        MatchServiceImpl, MatchRequestServiceImpl, SportServiceImpl, MatchLifecycleJobServiceImpl,
│                    ReputationServiceImpl
├── event/match/     MatchConfirmedEvent, MatchRequestSentEvent, MatchRequestRejectedEvent,
│                    MatchRequestWithdrawnEvent, MatchCancelledEvent, MatchExpiredEvent, MatchArchivedEvent (mới)
├── event/reputation/ ReliabilityScoreChangedEvent, ReputationSubjectType (mới)
├── support/         MatchGuard, MatchExpiry, MatchCancellation (+ CancellationResult), MatchWriteOutcome (mới);
│   │                TeamGuard (sửa — §4.5); AdminAuditLog (sửa — §4.11)
│   ├── reputation/  TimeBandPenaltyPolicy (mới)
│   └── match/       MatchLifecycleProperties, MatchRules (mới)
├── validation/      ValidMatchSchedule, ValidCostAgreement, ValidMatchSearch (+ validator) (mới);
│                    OneOf, ValidSkillLevels (tái dùng)
├── dto/request/     CreateMatchRequest, SendMatchRequestRequest, RejectMatchRequestRequest, CancelMatchRequest,
│                    WithdrawMatchRequestRequest, UpdateCostAgreementRequest, CostCustomItemRequest,
│                    SearchMatchRequest, UpdateSportRequest
├── dto/response/    MatchResponse (+ MatchTeamResponse, MatchVenueResponse, MatchCostAgreementResponse,
│                    CostCustomItemResponse), CostAgreementResponse, MatchSummaryResponse, MatchRequestResponse,
│                    AcceptMatchRequestResponse, MatchCancellationResponse, MatchRequestWithdrawalResponse,
│                    SportResponse
└── mapper/          MatchMapper, SportMapper (MapStruct); MapperSupport (tái dùng)

api/           com.phananh.arenaflow.api
├── match/           MatchController, MatchRequestController, TeamMatchController
├── sport/           SportController, AdminSportController
├── scheduler/       MatchLifecycleScheduler (mới)
└── config/          ClockConfig (mới — bean java.time.Clock)
```

Thành phần dùng chung mới/sửa (`TeamGuard` method mới, `ReputationService`, `TimeBandPenaltyPolicy`, `MatchWriteOutcome`, `AdminAuditLog` overload, `ClockConfig`, `ValidMatchSearch` vs `ValidGeoQuery` của VENUE-001) → developer MUST chạy `reuse-scan` trước khi tạo.

## 3. Controller Layer

Quy ước như USER-001 §3 (`@Validated`, `ResponseEntity<ApiResponse<…>>`, 201 không `Location`); `@CurrentUser UUID currentUserId` (không cần `CurrentActor` — không có quyền phụ thuộc role ngoài `/admin/**`).

| Controller | `@RequestMapping` | Endpoint | Service |
|---|---|---|---|
| `MatchController` | `/api/v1/matches` | 2.1–2.7 | `MatchService` (2.1–2.5), `MatchRequestService` (2.6, 2.7) |
| `MatchRequestController` | `/api/v1/match-requests` | 2.8–2.10 | `MatchRequestService` |
| `TeamMatchController` | `/api/v1/teams/{teamId}` (`/matches`, `/match-requests`) | 2.11, 2.12 | `MatchService`, `MatchRequestService` |
| `SportController` | `/api/v1/sports` | 2.13 | `SportService` |
| `AdminSportController` | `/api/v1/admin/sports` | 2.14, 2.15 | `SportService` |

**Mã thành công & tiêu chí `api-convention.md` §5** (chuyển từ `API_Spec.md` v1.0 §2):

| Endpoint | Thành công | Tiêu chí |
|---|---|---|
| 2.1, 2.6 | 201 | B2 |
| 2.2, 2.3, 2.7, 2.11–2.14 | 200 | B1 |
| 2.4 | 200 | B4 (+ B3: `itemId`, `updatedAt`) |
| 2.5 | 200 | B3 (`cancelledAt`, `reliabilityPenalty`), B4 |
| 2.8 | 200 | B3 (`respondedAt`, đội đối thủ) |
| 2.9 | 204 | N1 |
| 2.10 | 200 | B3 (`withdrawnAt`, `reliabilityPenalty`, trạng thái kèo) |
| 2.15 | 200 | B4 |

**Lý do chọn path/method** (chuyển từ `API_Spec.md` v1.0 §2 "Ghi chú hợp đồng"): 2.5, 2.8–2.10 là action sub-resource (`api-convention.md` §2.1), dùng `POST` theo tiền lệ `/invitations/{id}/accept`, `/teams/{id}/disband`. 2.6 tạo tài nguyên con dưới kèo; thao tác trên từng yêu cầu dùng `/match-requests/{id}` (tiền lệ `/teams/{id}/invitations` + `/invitations/{id}/…`). 2.4 `PUT` thay toàn bộ thoả thuận (idempotent). 2.10 luôn trả `200` có body (kể cả rút `PENDING`) để một endpoint có một hình dạng response. 2.15 đổi một field (`isActive`) → `PATCH` thường, không phải action.

- 2.2: `@Valid @ModelAttribute SearchMatchRequest` (§7). Danh sách khác: `@RequestParam` như `AdminTeamController` (enum filter qua `MethodArgumentTypeMismatchException` → 400).
- 2.9: `@RequestBody(required = false)` (tiền lệ USER-002 disband). 2.5, 2.10: body bắt buộc (`expectedStatus`).
- Không đổi `SecurityConfig` (`/api/v1/admin/**` đã `hasRole('ADMIN')`).

## 4. Service Layer

Không `@Transactional` mức class (USER-001 §4.6). Bảng ranh giới: §4.12.

### 4.1. MatchService
```java
MatchResponse createMatch(UUID callerId, CreateMatchRequest r);                               // @Transactional
PageResponse<MatchSummaryResponse> searchMatches(SearchMatchRequest r);                        // readOnly
MatchResponse getMatch(UUID matchId);                                                          // readOnly
CostAgreementResponse updateCostAgreement(UUID matchId, UUID callerId, UpdateCostAgreementRequest r); // retrier
MatchCancellationResponse cancelMatch(UUID matchId, UUID callerId, CancelMatchRequest r);     // retrier
PageResponse<MatchSummaryResponse> listTeamMatches(UUID teamId, MatchStatusFilter s, MatchRoleFilter role, int page, int pageSize); // readOnly
```
Deps: `MatchRepository`, `CostCustomItemRepository`, `SportRepository`, `TeamRepository` (`existsById` cho 2.11), `MatchRequestRepository` (`existsByMatchIdAndStatus`), `MatchVenueOptionRepository`, `MatchTimeOptionRepository`, `PairingRepository` (`supersedeNegotiating`) (MATCH-002 §5.2), `MatchGuard`, `TeamGuard`, `MatchExpiry`, `MatchCancellation`, `VenueSelectionService`, `ConcurrencyConflictRetrier`, `MatchMapper`, `PageRequestFactory`, `ApplicationEventPublisher` (`MatchCancelledEvent` khi huỷ `OPEN`), `Clock`.
- `createMatch`: guard (thứ tự `Processing_Flow.md` §1.1) → theo `r.type()`:
  - `HAS_VENUE`: `Match.builder()` (`status = OPEN`) → `save` → `markSelected({venueId}, now)` → `getMatch`-tương đương (`findDetailById`) dựng response.
  - `NEED_VENUE`: `requireSelectable(venueOptionIds)` trả `SelectableVenueView` → `Match.builder()` (`venueId = null`, `matchStartTime`/`matchEndTime` = khung giờ có `startsAt` nhỏ nhất) → `save` → `matchVenueOptionRepository.insertAll` → `matchTimeOptionRepository.insertAllReturning` → response dựng từ dữ liệu đã nạp (`TeamCaptainContextView`, `Sport`, `SelectableVenueView`, `TimeOptionView`), **không** `findDetailById`, **không** `markSelected` (chỉ lúc ghép — MATCH-002 §4.4). Không phát event.
- `searchMatches`: chọn `searchOpen` / `searchOpenNearby` theo có toạ độ (§5.2); hộp giới hạn + Haversine như VENUE-001 §5.2; lọc `type` tuỳ chọn.
- `getMatch`: `findDetailById` → khoản tự nhập theo `match_id` khi kèo A **hoặc** `confirmedPairingId != null` (kèo B đã ghép — khoản được chép từ thương lượng đã chốt lúc ghép, MATCH-002 §4.4; vẫn đúng khi kèo B bị huỷ sau `CONFIRMED`, pairing giữ `CONFIRMED`); kèo B có `confirmedPairingId == null` → bỏ (chưa có khoản) → kèo B: `findOptionRows`.
- `updateCostAgreement`, `cancelMatch`: trả `MatchWriteOutcome` từ lambda retrier; `Expired` → `MATCH_004` **sau** commit (§4.7).
- `cancelMatch`: **mọi** lần thử so `match.status` với `r.expectedStatus()` (client gửi, bắt buộc) — khác → `MATCH_015`, không ghi, không trừ điểm. So với giá trị client chứ không với trạng thái server đọc ở lần thử đầu, vì màn hình client có thể đã cũ trước khi request tới (BR-M13). `CONFIRMED` → `MatchCancellation.cancelConfirmed`. `OPEN` kèo B: bỏ kiểm `MATCH_010`; sau CAS → `pairingRepository.supersedeNegotiating(matchId, now)`.
- `updateCostAgreement` và `MatchRequestService.sendRequest` / `listMatchRequests`: kèo `NEED_VENUE` → `MATCH_016` (vị trí: `Processing_Flow.md` §1.1).
- `updateCostAgreement`: setter các cột chi phí + `setUpdatedAt(now)` (luôn làm entity dirty → `UPDATE match` CAS tăng `version` ngay cả khi chỉ đổi khoản tự nhập; `@PreUpdate` không chạy nếu entity không dirty) → `deleteByMatchId` → `insertAll` (một câu `unnest`, id do DB sinh) → đọc lại khoản tự nhập để lấy `itemId` cho response; bỏ cả `insertAll` lẫn bước đọc khi danh sách rỗng.
- Luồng chi tiết: `Processing_Flow.md` §3.1–§3.5, §3.11.

### 4.2. MatchRequestService
```java
MatchRequestResponse sendRequest(UUID matchId, UUID callerId, SendMatchRequestRequest r);          // retrier
PageResponse<MatchRequestResponse> listMatchRequests(UUID matchId, UUID callerId, MatchRequestStatusFilter s, int page, int pageSize); // readOnly
AcceptMatchRequestResponse acceptRequest(UUID requestId, UUID callerId);                           // retrier
void rejectRequest(UUID requestId, UUID callerId, RejectMatchRequestRequest r);                    // retrier
MatchRequestWithdrawalResponse withdrawRequest(UUID requestId, UUID callerId, WithdrawMatchRequestRequest r); // retrier
PageResponse<MatchRequestResponse> listTeamRequests(UUID teamId, UUID callerId, MatchRequestStatusFilter s, int page, int pageSize); // readOnly
```
Deps: `MatchRepository`, `MatchRequestRepository`, `MatchGuard`, `TeamGuard`, `MatchExpiry`, `MatchCancellation`, `ConcurrencyConflictRetrier`, `MatchMapper`, `PageRequestFactory`, `ApplicationEventPublisher`, `Clock`.
- `sendRequest`: `MatchRequest.builder()` → `saveAndFlush` (bắt `idx_match_request_active` → `MATCH_006`; NFS §4.1 ngoại lệ 1, comment tại chỗ) → `matchRepository.forceIncrementVersion(match)` (chỉ trên nhánh thành công — §5.2).
- `withdrawRequest`: mọi lần thử so `ctx.status` với `r.expectedStatus()` → khác → `MATCH_015` (cùng lý do `cancelMatch`). `ACCEPTED`: uỷ quyền `MatchCancellation.cancelConfirmed` với đội huỷ = đội gửi.
- Luồng: `Processing_Flow.md` §3.6–§3.10, §3.12.

### 4.3. MatchLifecycleJobService
```java
int expireDueMatches();      // UC-M09, AC-M07 — mỗi lô một TransactionTemplate
int archiveFinishedMatches();// AC-M08 — mỗi lô một TransactionTemplate
```
Deps: `MatchRepository`, `MatchRequestRepository`, `PairingRepository` (`expireNegotiatingByMatchIds`), `ApplicationEventPublisher`, `TransactionTemplate`, `MatchLifecycleProperties`, `Clock`. Luồng: `Processing_Flow.md` §3.13.

### 4.4. SportService
```java
PageResponse<SportResponse> listActiveSports(int page, int pageSize);                 // readOnly
PageResponse<SportResponse> listSports(Boolean isActive, int page, int pageSize);     // readOnly (admin)
SportResponse updateSport(UUID sportId, UUID adminId, UpdateSportRequest r);           // @Transactional
```
`updateSport`: `findById` → `SPORT_001` → giá trị khác → setter (dirty checking) → `adminAuditLog.afterCommit("SET_SPORT_ACTIVE", adminId, "SPORT", sportId)`. `Sport` không version: hai admin cùng bật/tắt → ghi sau thắng, kết quả vẫn là một giá trị hợp lệ.

### 4.5. Guard (`application/support`)

**`MatchGuard`** (`@Component`) — một nguồn cho mã `MATCH_001/003/004/005`:
```java
Match findMatch(UUID matchId);                        // findById; không có → MATCH_001 (không khoá)
boolean isExpiryDue(Match.Status s, OffsetDateTime expiresAt, OffsetDateTime now); // OPEN && expiresAt ≤ now → nhánh lazy
void requireNotExpired(Match.Status s);               // EXPIRED (DB) → MATCH_004 — gọi ngay sau nhánh lazy ở mọi endpoint ghi
void requireOpen(Match m);                            // EXPIRED → MATCH_004; CONFIRMED → MATCH_003; khác OPEN → MATCH_005
```
Mọi endpoint ghi kiểm hết hạn **trước** kiểm trạng thái khác: quá hạn → lazy + `MATCH_004`; đã `EXPIRED` → `MATCH_004` (không phải `MATCH_005`/`MATCH_008`).

**`TeamGuard`** (sửa — thêm 2 method, mỗi method **1 truy vấn** `TeamRepository.findCaptainContext`, đọc trạng thái đội + cờ "caller là captain ACTIVE" trong một câu nên không cần `disbandedOr`):
```java
TeamCaptainContextView requireActiveCaptain(UUID teamId, UUID userId); // TEAM_001 → TEAM_016 → TEAM_015 → TEAM_002; trả dữ liệu đã đọc (tên/ảnh/điểm đội, tên caller) — caller không cần thì bỏ qua
void requireCaptainAllowInactive(UUID teamId, UUID userId); // TEAM_001 → TEAM_016 → TEAM_002 (INACTIVE được)
```
Đội `DISBANDED` không còn captain ACTIVE (BR-T10) nên `TEAM_016` luôn trả trước `TEAM_002`. Method cũ (`activeTeam`, `requireCaptain`…) giữ nguyên cho USER-001/002.

### 4.6. MatchExpiry (`application/support`, `@Component`) — lazy check + job dùng chung
```java
boolean expireIfDue(UUID matchId, UUID authorTeamId, Match.Type type, OffsetDateTime now); // MANDATORY; true nếu chính lời gọi này chuyển EXPIRED
```
Q1 `MatchRepository.expireIfDue(id, now)` (`WHERE status = 'OPEN' AND expires_at <= :now`, `version + 1`) → 1 dòng: Q2 `HAS_VENUE` → `MatchRequestRepository.expirePendingByMatchIds([id], now)`; `NEED_VENUE` → `PairingRepository.expireNegotiatingByMatchIds([id], now)` → phát `MatchExpiredEvent`. 0 dòng → `false` (job/request khác đã làm). Job dùng biến thể lô (§4.3) cùng hai câu bulk.

### 4.7. MatchWriteOutcome — chuyển `EXPIRED` rồi mới báo lỗi
```java
public sealed interface MatchWriteOutcome<T> {
    record Done<T>(T value) implements MatchWriteOutcome<T> {}
    record Expired<T>() implements MatchWriteOutcome<T> {}
    default T valueOrThrowExpired() { … }   // Expired → BusinessException(MATCH_004)
}
```
Lambda retrier gặp `isExpiryDue` → `expireIfDue` → trả `Expired` (transaction **commit** việc chuyển trạng thái); service gọi `valueOrThrowExpired()` **ngoài** retrier. Ném exception trong lambda sẽ rollback việc chuyển `EXPIRED` (UC-M09 1a2 yêu cầu cập nhật).

### 4.8. ReputationService — lõi dùng chung (ADR-0013)
```java
public interface ReputationService {
    void adjustTeamReliability(UUID teamId, ReliabilityChange change);   // @Transactional(propagation = MANDATORY)
    void adjustUserReliability(UUID userId, ReliabilityChange change);   // @Transactional(propagation = MANDATORY)
}
public record ReliabilityChange(int delta, ReliabilityReason reason, UUID referenceId) {}
public enum ReliabilityReason { MATCH_CANCELLED_AFTER_CONFIRMED, MATCH_REQUEST_WITHDRAWN_AFTER_ACCEPTED } // REPUTATION-001 thêm giá trị

public record TimeBandPenaltyPolicy(int atLeast24h, int from4hTo24h, int under4h) {   // application/support/reputation
    public static final TimeBandPenaltyPolicy MATCH_CANCELLATION = new TimeBandPenaltyPolicy(-2, -5, -10);
    public int penaltyFor(OffsetDateTime matchStart, OffsetDateTime at);  // Δ ≥ 24h / 4h ≤ Δ < 24h / Δ < 4h
}
```
- `delta == 0` → không truy vấn. Còn lại: Q1 `TeamRepository.adjustReliability` / `UserRepository.adjustReliability` (§5.2); 0 dòng → `IllegalStateException` (id phải tồn tại — lỗi lập trình) → phát `ReliabilityScoreChangedEvent`.
- **Không đọc lại** điểm mới (event chỉ mang `delta`); REPUTATION-001 cần điểm mới thì thêm biến thể đọc.
- **Mở rộng cho REPUTATION-001 / MERCENARY-001** (không thiết kế ở đây): thêm `ReliabilityReason`, hằng `TimeBandPenaltyPolicy` (vd rút mercenary `-1/-3/-5`), method cho bộ đếm report / chuỗi trận sạch theo cùng mẫu `UPDATE` nguyên tử. MATCH-001 **không** ghi `total_matches_*`, `clean_streak`, `report_count_*` `[cần xác nhận #3]`.
- MATCH-001 chỉ gọi `adjustTeamReliability`; `adjustUserReliability` có test riêng.
- **Lưu ý cho REPUTATION-001:** BR-M18 cho phép một người làm đội trưởng hai đội tự ghép kèo với nhau → có thể dùng để cày cộng điểm hoàn thành trận / Completion Rate. MATCH-001 không trừ điểm trong trường hợp này; REPUTATION-001 cân nhắc quy tắc chống lạm dụng khi thiết kế phần cộng điểm.

### 4.9. Domain event (`application/event/…`, ADR-0011)

| Event | Payload | Phát khi | Người nghe dự kiến |
|---|---|---|---|
| `MatchRequestSentEvent` | `requestId, matchId, requesterTeamId, authorTeamId, occurredAt` | 2.6 | NOTIFICATION-001 (`MATCH_REQUEST_RECEIVED`) |
| `MatchConfirmedEvent` | `matchId, acceptedRequestId` (null ở kèo B)`, pairingId` (null ở kèo A)`, authorTeamId, opponentTeamId, occurredAt` | 2.8; MATCH-002 2.7 hoàn tất | NOTIFICATION-001 (`REQUEST_ACCEPTED` khi có `acceptedRequestId`, `MATCH_CONFIRMED`; tự truy vấn yêu cầu / pairing `SUPERSEDED` của kèo để báo các đội bị thay thế) |
| `MatchRequestRejectedEvent` | `requestId, matchId, requesterTeamId, occurredAt` | 2.9 | NOTIFICATION-001 (`REQUEST_REJECTED`) |
| `MatchRequestWithdrawnEvent` | `requestId, matchId, requesterTeamId, authorTeamId, occurredAt` | 2.10 **chỉ khi rút `PENDING`** | NOTIFICATION-001 (không có loại riêng trong 11 loại — tuỳ chọn) |
| `MatchCancelledEvent` | `matchId, cancelledByTeamId, otherTeamId` (null khi huỷ `OPEN`)`, previousStatus, cause` (`CANCEL`\|`REQUEST_WITHDRAWN`)`, withdrawnRequestId` (khác null khi đội đã ghép kèo A huỷ / rút)`, occurredAt` | 2.5, 2.10 (`ACCEPTED`) | NOTIFICATION-001 (`MATCH_CANCELLED` cho đội còn lại; kèo B `OPEN`: tự truy vấn pairing `SUPERSEDED` để báo các đội đang thương lượng) |
| `MatchExpiredEvent` | `matchId, authorTeamId, occurredAt` | job, lazy check | NOTIFICATION-001 (tự truy vấn yêu cầu `EXPIRED` của kèo — AC-M07) |
| `MatchArchivedEvent` | `matchId, authorTeamId, opponentTeamId, matchEndTime, occurredAt` | job | NOTIFICATION-001 (push "Trận đấu hôm nay thế nào?…" cho 2 đội trưởng — AC-M08) |
| `ReliabilityScoreChangedEvent` | `subjectType` (`TEAM`\|`USER`)`, subjectId, delta, reason, referenceId, occurredAt` | `ReputationService` | Thông báo / dữ liệu dẫn xuất tính lại được (vd cache badge); không dùng để ghi điểm, bộ đếm |

Payload chỉ id + thời điểm + enum, không PII; người nhận tự tra đội trưởng hiện tại sau commit. MATCH-001 không có listener.

**Mỗi thao tác phát đúng một event nghiệp vụ** → NOTIFICATION-001 báo một lần: huỷ / rút sau ghép chỉ phát `MatchCancelledEvent` (nhận biết rút qua `cause = REQUEST_WITHDRAWN` + `withdrawnRequestId`), không kèm `MatchRequestWithdrawnEvent`. `ReliabilityScoreChangedEvent` đi kèm là sự kiện điểm, không phải thông báo huỷ.

**Phạm vi event (ADR-0011):** event không bền → chỉ dùng cho thông báo và dữ liệu dẫn xuất tính lại được. Dữ liệu nghiệp vụ (cộng/trừ điểm, bộ đếm hoàn thành, chuỗi trận sạch) MUST ghi trong cùng transaction qua `ReputationService` (ADR-0013); REPUTATION-001 không nghe `MatchArchivedEvent` để ghi bộ đếm.

### 4.10. Scheduler (ADR-0012)
`MatchLifecycleScheduler` (`api/scheduler`, không logic): `@Scheduled(cron = "${arenaflow.match.lifecycle.cron}", zone = "Asia/Ho_Chi_Minh")` gọi `expireDueMatches()` rồi `archiveFinishedMatches()`; lỗi một phần log `ERROR`, phần kia vẫn chạy. Cron `-` = tắt (test). Dùng `SchedulingConfig` của VENUE-001.

### 4.11. AdminAuditLog (sửa — VENUE-001 §4.13)
Thêm overload trung tính `void afterCommit(String action, UUID adminId, String targetType, UUID targetId)` (cùng cơ chế `TransactionSynchronization.afterCommit`); bản VENUE-001 giữ nguyên `[cần xác nhận #4]`. MATCH-001 dùng cho 2.15.

### 4.12. Transaction trong ServiceImpl

| Method | Ranh giới |
|---|---|
| `searchMatches`, `getMatch`, `listTeamMatches`, `listMatchRequests`, `listTeamRequests`, `listActiveSports`, `listSports` | `@Transactional(readOnly = true)` |
| `createMatch`, `updateSport` | `@Transactional` (không CAS trên dòng có sẵn — `markSelected` là bulk nguyên tử) |
| `updateCostAgreement`, `cancelMatch`, `sendRequest`, `acceptRequest`, `rejectRequest`, `withdrawRequest` | Không annotate; `concurrencyConflictRetrier.execute(MATCH_014, …)` trả `MatchWriteOutcome` |
| `MatchExpiry.expireIfDue`, `MatchCancellation.cancelConfirmed`, `ReputationService.*`, `VenueSelectionService.markSelected` | `MANDATORY` |
| `expireDueMatches`, `archiveFinishedMatches` | Mỗi lô một `TransactionTemplate` |

`rejectRequest` không có CAS nhưng vẫn qua retrier: nhánh lazy-expiry cần commit-rồi-báo-lỗi (§4.7) và thử lại deadlock.

### 4.13. MatchCancellation (`application/support`, `@Component`) — một đường huỷ kèo đã ghép
```java
CancellationResult cancelConfirmed(Match match, UUID cancellingTeamId, String reason, OffsetDateTime now); // MANDATORY
public record CancellationResult(int reliabilityPenalty) {}
```
Dùng bởi 2.5 (`CONFIRMED`) và 2.10 (`ACCEPTED`): setter `status = CANCELLED`, `cancelledByTeamId`, `cancelledAt`, `cancelReason` → nếu đội huỷ là đội đã ghép **và** kèo `HAS_VENUE`: `MatchRequestRepository.withdrawAccepted` (phải = 1 dòng, khác → `IllegalStateException`); kèo `NEED_VENUE` không có yêu cầu, pairing đã chốt giữ `CONFIRMED` → `penalty = MATCH_CANCELLATION.penaltyFor(matchStartTime, now)` → `reputationService.adjustTeamReliability(cancellingTeamId, …)` → `MatchCancelledEvent` (một event duy nhất cho thao tác, §4.9). Gọi `MatchGuard`/kiểm `MATCH_011`, `MATCH_012` **trước** (caller).

## 5. Repository & Data Model

### 5.1. Migration `V6__match_flow_a_lifecycle.sql`

```sql
-- match: khoá lạc quan, trình độ mong muốn, lý do huỷ, enum định dạng / chi phí
ALTER TABLE match ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE match ADD COLUMN skill_levels TEXT NOT NULL DEFAULT '[]';
ALTER TABLE match ADD COLUMN cancel_reason VARCHAR(500);
ALTER TABLE match ALTER COLUMN format TYPE VARCHAR(20);
ALTER TABLE match ADD CONSTRAINT match_format_check
    CHECK (format IS NULL OR format IN ('FIVE_A_SIDE', 'SEVEN_A_SIDE', 'ELEVEN_A_SIDE'));
ALTER TABLE match ADD CONSTRAINT match_has_venue_format_check CHECK (type <> 'HAS_VENUE' OR format IS NOT NULL);
ALTER TABLE match ADD CONSTRAINT match_cost_type_check
    CHECK (cost_type IS NULL OR cost_type IN ('VENUE_ONLY', 'DRINKS_ONLY', 'BOTH'));
ALTER TABLE match ADD CONSTRAINT match_cost_split_check
    CHECK (cost_split IS NULL OR cost_split IN ('FIFTY_FIFTY', 'SEVENTY_THIRTY_HOME', 'SEVENTY_THIRTY_LOSER', 'LOSER_PAYS_DRINKS'));
ALTER TABLE match ADD CONSTRAINT match_cost_amount_check
    CHECK ((cost_venue_amount IS NULL OR cost_venue_amount > 0) AND (cost_drinks_amount IS NULL OR cost_drinks_amount > 0));
CREATE INDEX idx_match_open_start ON match(match_start_time, id) WHERE status = 'OPEN';
CREATE INDEX idx_match_opponent_status ON match(confirmed_opponent_team_id, status);

-- match_request: lý do từ chối/rút; tối đa 1 ACCEPTED mỗi kèo (BR-M06, BC-M03)
ALTER TABLE match_request ADD COLUMN reason VARCHAR(500);
CREATE UNIQUE INDEX idx_match_request_accepted ON match_request(match_id) WHERE status = 'ACCEPTED';

-- cost_custom_item: thứ tự client gửi (BR-C04 ≤ 10 khoản), số tiền > 0 (BR-C02)
ALTER TABLE cost_custom_item ADD COLUMN display_order INTEGER;
UPDATE cost_custom_item c SET display_order = o.rn
    FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY match_id ORDER BY created_at, id) AS rn FROM cost_custom_item) o
    WHERE c.id = o.id;
ALTER TABLE cost_custom_item ALTER COLUMN display_order SET NOT NULL;
ALTER TABLE cost_custom_item ADD CONSTRAINT cost_custom_item_display_order_check CHECK (display_order > 0);
ALTER TABLE cost_custom_item ADD CONSTRAINT cost_custom_item_amount_check CHECK (amount > 0);
DROP INDEX idx_cost_custom_item_match;
CREATE UNIQUE INDEX idx_cost_custom_item_order ON cost_custom_item(match_id, display_order);
```
- Chỉ delta cần: `@Version` (Overview "Implementation notes"), trình độ mong muốn (Data_Model_Conceptual §2.3 — V1 thiếu), lý do huỷ / từ chối / rút (§2.3), `format` `VARCHAR(10)` không chứa `ELEVEN_A_SIDE` (13 ký tự), ràng buộc enum/số tiền, unique "1 ACCEPTED" (V1 chỉ có unique theo đội), `display_order` (thứ tự khoản tự nhập; unique `(match_id, display_order)` thay index `(match_id)` của V1 vì có cùng tiền tố). Tên index theo quy ước `idx_*` của V1/V2/V4.
- `skill_levels` cùng kiểu/converter với `team.skill_levels` (USER-001 §6, `SkillLevelsConverter`).
- Không đổi `cost_deposit_holder`, `match_venue_option`/`match_time_option`/`pairing` ở V6. V7 (MATCH-002 §5.1) sửa tiếp: `cost_custom_item.match_id` nullable + `pairing_id` (XOR), `match` bỏ `confirmed_venue_id`/`confirmed_match_time`, nới CHECK `match_check2`, thêm `cost_deposit_amount`.
- Dữ liệu cũ: đã kiểm Neon dev `misty-dew-90700890` ngày 2026-11-09 — `match`, `match_request`, `cost_custom_item` đều 0 dòng; Flyway ở V4; tên constraint/index V6 không trùng với constraint đang có. Môi trường khác có dữ liệu: dòng có `format`/`cost_*` ngoài enum, hoặc `HAS_VENUE` thiếu `format`, hoặc nhiều `ACCEPTED` cùng kèo làm V6 lỗi → dừng, báo, không tự sửa dữ liệu.
- Index: `idx_match_open_start` cho 2.2 khi không lọc môn (partial khớp literal `'OPEN'`); `idx_match_opponent_status` cho 2.11 vai trò đối thủ. Job dùng `idx_match_expiry (status, expires_at)`, `idx_match_archived (status, match_end_time)` (V1).

### 5.2. Repository

Native + interface projection như USER-001 §5; `Page` native có `countQuery`; bulk `@Modifying` trên `match` MUST `version = version + 1`, `updated_at = :now`. Trạng thái hiệu lực trong SQL: `CASE WHEN m.status = 'OPEN' AND m.expires_at <= :now THEN 'EXPIRED' ELSE m.status END` (yêu cầu: `PENDING` + kèo quá hạn → `EXPIRED`); `:now` truyền từ `Clock`.

```java
public interface MatchRepository extends JpaRepository<Match, UUID> {
    Optional<MatchDetailView> findDetailById(UUID id, OffsetDateTime now);   // JOIN sport, team tác giả, LEFT JOIN team đối thủ,
                                                                            // venue, LEFT JOIN venue_image ảnh chính
    Page<MatchSummaryView> searchOpen(String type /* null = cả hai */, UUID sportId, String format, String skillLevel,
            OffsetDateTime startsAtFrom, OffsetDateTime startsAtTo, OffsetDateTime now, Pageable p);
    Page<MatchSummaryView> searchOpenNearby(String type, UUID sportId, String format, String skillLevel, OffsetDateTime startsAtFrom,
            OffsetDateTime startsAtTo, OffsetDateTime now, double lat, double lng, double radiusKm,
            double minLat, double maxLat, double minLng, double maxLng, Pageable p);
    Page<MatchSummaryView> findTeamMatches(UUID teamId, String role /* null = ALL */, String status /* null = ALL */,
            OffsetDateTime now, Pageable p);
    Optional<MatchAuthorAccessView> findAuthorAccess(UUID matchId, UUID userId); // authorTeamId, trạng thái đội tác giả, caller là captain ACTIVE
    MatchAvailabilityView findAvailability(UUID matchId);                     // trạng thái đội tác giả + bộ môn đang bật (2.6)
    @Modifying int expireIfDue(UUID id, OffsetDateTime now);
    List<DueMatchView> claimDueForExpiry(OffsetDateTime now, int limit);         // FOR NO KEY UPDATE SKIP LOCKED
    List<DueMatchView> claimDueForArchive(OffsetDateTime now, int limit);        // FOR NO KEY UPDATE SKIP LOCKED
    @Modifying int markExpired(Collection<UUID> ids, OffsetDateTime now);
    @Modifying int markArchived(Collection<UUID> ids, OffsetDateTime now);
}
public interface MatchRepositoryCustom {                                   // fragment; MatchRepository extends nó
    void forceIncrementVersion(Match match);   // MatchRepositoryImpl: entityManager.lock(match, OPTIMISTIC_FORCE_INCREMENT) — version tăng lúc commit
}
public interface MatchRequestRepository extends JpaRepository<MatchRequest, UUID> {
    Optional<MatchRequestContextView> findContextById(UUID requestId);   // request + match + đội gửi (status, tên, điểm) + sport.is_active
    boolean existsByMatchIdAndStatus(UUID matchId, MatchRequest.Status status);
    Page<MatchRequestView> findByMatch(UUID matchId, boolean pendingOnly, OffsetDateTime now, Pageable p);
    Page<MatchRequestView> findByRequesterTeam(UUID teamId, String status /* null = ALL */, OffsetDateTime now, Pageable p);
    @Modifying int acceptIfPending(UUID id, OffsetDateTime now);
    @Modifying int rejectIfPending(UUID id, String reason, OffsetDateTime now);
    @Modifying int withdrawIfPending(UUID id, String reason, OffsetDateTime now);
    @Modifying int withdrawAccepted(UUID matchId, UUID requesterTeamId, String reason, OffsetDateTime now);
    @Modifying int supersedePending(UUID matchId, OffsetDateTime now);
    @Modifying int expirePendingByMatchIds(Collection<UUID> matchIds);
}
public interface CostCustomItemRepository extends JpaRepository<CostCustomItem, UUID> {
    List<CostCustomItem> findByMatchIdOrderByDisplayOrderAsc(UUID matchId);
    @Modifying int deleteByMatchIdBulk(UUID matchId);                     // DELETE … WHERE match_id = :matchId
    @Modifying int insertAll(UUID matchId, String[] names, Long[] amounts, OffsetDateTime now); // 1 câu unnest, id do PostgreSQL uuidv7() sinh
}
public interface SportRepository extends JpaRepository<Sport, UUID> {
    Page<Sport> findByIsActiveOrderByNameAscIdAsc(boolean isActive, Pageable p);
    Page<Sport> findAllByOrderByNameAscIdAsc(Pageable p);
}
// TeamRepository (sửa)
Optional<TeamCaptainContextView> findCaptainContext(UUID teamId, UUID userId); // status + EXISTS captain ACTIVE = userId + name, avatar_url, reliability_score + tên/cờ đã xoá của userId
@Modifying int adjustReliability(UUID id, int delta);
// UserRepository (sửa)
@Modifying int adjustReliability(UUID id, int delta);
```

Ghi chú SQL:
- `*IfPending`/`withdrawAccepted`: `WHERE … AND status = '<trạng thái nguồn>'` — 0 dòng = đã bị thao tác khác đổi (READ COMMITTED đánh giá lại sau khi chờ khoá dòng). Gán `responded_at` (accept/reject/supersede), `withdrawn_at` + `reason` (withdraw), `reason` (reject).
- `supersedePending`: `SET status = 'SUPERSEDED', responded_at = :now WHERE match_id = :matchId AND status = 'PENDING'` — chạy **sau** `acceptIfPending` (yêu cầu thắng đã rời `PENDING`).
- `expirePendingByMatchIds`: `SET status = 'EXPIRED' WHERE match_id IN (:ids) AND status = 'PENDING'`.
- `claimDueFor*`: `SELECT id, author_team_id, confirmed_opponent_team_id, match_end_time FROM match WHERE status = :s AND <expires_at|match_end_time> <= :now ORDER BY <cột thời gian>, id LIMIT :limit FOR NO KEY UPDATE SKIP LOCKED` (native, đọc → không `@Modifying`). `markExpired/markArchived`: `WHERE id IN (:ids)` lặp điều kiện trạng thái.
- `adjustReliability`: `UPDATE team SET reliability_score = LEAST(100, GREATEST(0, reliability_score + :delta)) WHERE id = :id` — **không** tăng `version` (cột `updatable = false`, ADR-0013); `user` tương tự.
- `findCaptainContext`: `SELECT t.status, t.name, t.avatar_url, t.reliability_score, EXISTS (SELECT 1 FROM team_member m WHERE m.team_id = t.id AND m.user_id = :userId AND m.role = 'CAPTAIN' AND m.status = 'ACTIVE') AS captain, u.display_name AS user_display_name, (u.deleted_at IS NOT NULL) AS user_deleted FROM team t LEFT JOIN "user" u ON u.id = :userId WHERE t.id = :teamId` (tên/ảnh/điểm cho response dựng từ dữ liệu đã nạp: tạo kèo B, MATCH-002 2.1; `user_*` = tên đội trưởng hiện tại khi `captain = true` — `captainName` của MATCH-002 2.1).
- `findAvailability`: thêm `LEFT JOIN team_member cm ON cm.team_id = m.author_team_id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE' LEFT JOIN "user" cu ON cu.id = cm.user_id` → `authorCaptainName`, `authorCaptainDeleted` (MATCH-002 2.1); cùng câu.
- `claimDueForExpiry` trả thêm `type`; lô tách id theo loại → `expirePendingByMatchIds` (kèo A) và `PairingRepository.expireNegotiatingByMatchIds` (kèo B), bỏ câu khi danh sách rỗng.
- `findDetailById`: `LEFT JOIN venue` (kèo B chưa ghép không có `venue_id`), `LEFT JOIN pairing p ON p.match_id = m.id AND p.status = 'CONFIRMED'` → `confirmedPairingId`; thêm `cost_deposit_amount`, `cost_deposit_holder`.
- Tìm kèo: `m.status = 'OPEN'` (literal) `AND m.expires_at > :now AND (:type IS NULL OR m.type = :type)` + `JOIN team t ON t.id = m.author_team_id AND t.status = 'ACTIVE'` + `JOIN sport s ON s.id = m.sport_id AND s.is_active` + `LEFT JOIN venue v ON v.id = m.venue_id` (kèo B chưa ghép: `null`); khoảng giờ: kèo A so `m.match_start_time`, kèo B `EXISTS (SELECT 1 FROM match_time_option t WHERE t.match_id = m.id AND t.proposed_start_time BETWEEN …)`; `venueOptionCount`/`timeOptionCount`: subquery `count(*)` theo `match_id` (chỉ kèo B); nearby kèo B: `LATERAL (SELECT min(Haversine) FROM match_venue_option o JOIN venue v2 … WHERE o.match_id = m.id AND v2 trong hộp)` → khoảng cách tới lựa chọn gần nhất, lọc `≤ radiusKm` (vẫn 1 câu trang + 1 câu count); `skillLevel`: `(:skillLevel IS NULL OR m.skill_levels = '[]' OR jsonb_exists(m.skill_levels::jsonb, :skillLevel))` (không dùng toán tử `?` — xung đột placeholder JDBC). Nearby: hộp + Haversine trên `venue.latitude/longitude` như VENUE-001 §5.2.
- `findTeamMatches`: `(m.author_team_id = :teamId OR m.confirmed_opponent_team_id = :teamId)` theo `role`; lọc `status` trên biểu thức trạng thái hiệu lực.
- `insertAll` (user chốt 2026-11-09 — một câu bất kể số khoản, không bật JDBC batch toàn ứng dụng): `INSERT INTO cost_custom_item (id, match_id, item_name, amount, display_order, created_at) SELECT uuidv7(), :matchId, t.name, t.amount, t.ord, :now FROM unnest(CAST(:names AS text[]), CAST(:amounts AS bigint[])) WITH ORDINALITY AS t(name, amount, ord)`. Id do hàm built-in `uuidv7()` của **PostgreSQL ≥ 18** sinh (cùng định dạng v7 với entity — ADR-0008 Sửa đổi 2026-11-09); `ord` = 1..n theo thứ tự mảng = thứ tự request. Neon dev đã kiểm 2026-11-09: `server_version = 18.6`, có `uuidv7` `[cần xác nhận #11]` cho môi trường khác. Chạy sau `deleteByMatchIdBulk` (Hibernate tự flush `UPDATE match` trước câu native đầu tiên). Id do DB sinh → response đọc lại `findByMatchIdOrderByDisplayOrderAsc` (1 câu). Danh sách rỗng → không gọi insert lẫn bước đọc `[cần xác nhận #1]`.
- `forceIncrementVersion` (fragment, tiền lệ USER-002 dùng `OPTIMISTIC_FORCE_INCREMENT` cho invite): đặt trong repository để service không cầm `EntityManager`; chỉ gọi trên nhánh thành công của 2.6 (sau mọi guard), để nhánh lazy-expiry (bulk `version + 1`) không bị kiểm version lần hai lúc commit.

### 5.3. Projection

| Projection | Cột |
|---|---|
| `MatchDetailView` | cột `match` cho `MatchResponse` (gồm `type`, `effectiveStatus`, `skillLevels` JSON thô, `costDepositAmount`, `costDepositHolder`) + `sportName`, tác giả/đối thủ (`id`, `name`, `avatarUrl`, `reliabilityScore`), venue nullable (`name`, `address`, `latitude`, `longitude`, `status`, `primaryImageUrl`), `confirmedPairingId` |
| `MatchSummaryView` | cột `MatchSummaryResponse` (gồm `type`, venue nullable, `venueOptionCount`, `timeOptionCount`) + `distanceKm` (`Double`) |
| `MatchRequestView` | cột `MatchRequestResponse` (gồm `effectiveStatus`) + ngữ cảnh kèo cho 2.12 |
| `MatchRequestContextView` | request (`id`, `matchId`, `requesterTeamId`, `status`) + match (`authorTeamId`, `status`, `expiresAt`, `matchStartTime`) + đội gửi (`name`, `status`, `reliabilityScore`) + `sportActive` |
| `MatchAvailabilityView` | `authorTeamStatus`, `sportActive` + `authorTeamName`, `authorTeamAvatarUrl`, `authorTeamReliabilityScore`, `authorCaptainName`, `authorCaptainDeleted` (response MATCH-002 2.1) |
| `MatchAuthorAccessView` | `authorTeamId`, `authorTeamStatus`, `callerIsCaptain`, `type` (`MATCH_016` ở 2.7, MATCH-002 2.2) |
| `DueMatchView` | `id`, `type`, `authorTeamId`, `opponentTeamId`, `matchEndTime` |
| `TeamCaptainContextView` | `status`, `captain`, `name`, `avatarUrl`, `reliabilityScore`, `userDisplayName`, `userDeleted` |

Thời gian là `Instant`, mapper chuyển UTC (USER-001 §5).

## 6. Entity

| Entity | Thay đổi |
|---|---|
| `match.Match` | + `@Version Long version` · `updatedAt`: bỏ `@Setter(AccessLevel.NONE)` để 2.4 gán tường minh (§4.1; `@PreUpdate` vẫn giữ) · + `skillLevels` (`List<String>`, `@Convert(SkillLevelsConverter)`, `columnDefinition = "TEXT"`, `@Builder.Default List.of()`) · + `cancelReason` (`length = 500`) · `format`: `String` → `enum Format { FIVE_A_SIDE, SEVEN_A_SIDE, ELEVEN_A_SIDE }` (`length = 20`) · `costType` → `enum CostType { VENUE_ONLY, DRINKS_ONLY, BOTH }` · `costSplit` → `enum CostSplit { FIFTY_FIFTY, SEVENTY_THIRTY_HOME, SEVENTY_THIRTY_LOSER, LOSER_PAYS_DRINKS }` (`length = 50`, `@Enumerated(STRING)`); sửa Javadoc lớp (version) |
| `match.MatchRequest` | + `reason` (`length = 500`) |
| `team.Team`, `user.User` | `reliabilityScore`: thêm `updatable = false` — chỉ `ReputationService` ghi bằng `UPDATE` nguyên tử (ADR-0013); `insertable` giữ (`@Builder.Default 80`) |
| `match.CostCustomItem` | + `displayOrder` (`Integer`, `display_order`, `nullable = false`) — ghi bằng câu native `insertAll` (id do DB sinh), entity dùng để đọc; giữ `@UuidGenerator(VERSION_7)` trên `id` (ADR-0008, `EntityIdGenerationTest`) |
| `match.Sport` | Giữ |
| `match.Match`, `match.CostCustomItem` (v2.0) | Thay đổi cho luồng B: MATCH-002 `Component_Class_Design.md` §6 |

Giữ Lombok/builder/`@PrePersist` (`convention-backend.md` §6, §6.1); `src/main` không gán `id`, `version`, `createdAt`, `updatedAt` qua builder. Kiểm bằng migration integration test (`ddl-auto=validate` sau V6).

## 7. DTO & Mapper

- Chuỗi trong request được `trim()` ở service; field tuỳ chọn gửi `""` → lưu `null` (chuyển từ `API_Spec.md` v1.0 §3).
- DTO là `record`; enum dạng chuỗi dùng `@OneOf`; `skillLevels` dùng `@ValidSkillLevels` (USER-001).
- **`CreateMatchRequest`** (v2.0): + `type` (`@OneOf({"HAS_VENUE","NEED_VENUE"})`, vắng → `HAS_VENUE` qua compact constructor), `venueOptionIds` (`List<UUID>`), `timeOptions` (`List<@Valid TimeOptionRequest>`, `TimeOptionRequest(@NotNull startsAt, @NotNull endsAt)`); `venueId`, `startsAt`, `endsAt` bỏ `@NotNull` (bắt buộc theo luồng trong `@ValidMatchSchedule`).
- **`@ValidMatchSchedule`** (mức lớp, `CreateMatchRequest`): quy tắc thời gian `API_Spec.md` §2.1 (Request body) theo `type` — luồng A như cũ; luồng B: field luồng A phải `null`, 1..`MAX_VENUE_OPTIONS` địa điểm không trùng, 1..`MAX_TIME_OPTIONS` khung giờ không trùng `startsAt`, mỗi khung theo `MIN_LEAD_TIME`/`MAX_DURATION`, `expiresAt` trong [sớm nhất − `MAX_POSTING_WINDOW`, sớm nhất]; lỗi gắn `timeOptions[i].startsAt` …, `now` từ `Clock` (validator là Spring bean qua `LocalValidatorFactoryBean`); lỗi gắn property node theo tên field của request (`startsAt` / `endsAt` / `expiresAt`) vì `GlobalExceptionHandler` chỉ đọc `getFieldErrors()` (như VENUE-001 `@ValidGeoQuery`). Bỏ qua khi field liên quan `null` (để `@NotNull` báo).
- **`@ValidCostAgreement`** (mức lớp, `UpdateCostAgreementRequest`): quan hệ `costType` ↔ số tiền ↔ `costSplit` (`API_Spec.md` §2.4).
- `CancelMatchRequest.expectedStatus` `@NotNull @OneOf({"OPEN","CONFIRMED"})`; `WithdrawMatchRequestRequest.expectedStatus` `@NotNull @OneOf({"PENDING","ACCEPTED"})`.
- **`SearchMatchRequest`**: compact constructor gán mặc định (`page = 1`, `pageSize = 20`, `radiusKm = 10` khi có toạ độ) trước Bean Validation; `@ValidMatchSearch` kiểm cặp `lat`/`lng`, `radiusKm` cần toạ độ, `startsAtFrom ≤ startsAtTo`. Developer xem có tổng quát được `@ValidGeoQuery` (VENUE-001) qua interface chung không (reuse-scan).
- Hằng nghiệp vụ một nơi (`MatchRules` trong `application/support/match`): `MIN_LEAD_TIME = 1h`, `MAX_POSTING_WINDOW = 7d`, `MAX_DURATION = 12h`, `MAX_CUSTOM_ITEMS = 10`, `MAX_AMOUNT = 1_000_000_000`, `MAX_VENUE_OPTIONS = 5`, `MAX_TIME_OPTIONS = 5` (v2.0). Khung trừ điểm ở `TimeBandPenaltyPolicy.MATCH_CANCELLATION`.
- Tên field API khác tên entity: `startsAt` ↔ `matchStartTime`, `endsAt` ↔ `matchEndTime` (cột V1 giữ nguyên; `api-convention.md` §10 hậu tố `At`).
- `MatchMapper` (`componentModel = "spring"`, `uses`/`imports` `MapperSupport`, `unmappedTargetPolicy = ERROR`): `toResponse(MatchDetailView, List<CostCustomItem>, List<MatchOptionRowView>)` (v2.0: + `venueOptions`, `timeOptions`, `confirmedPairingId`; danh sách rỗng ở kèo A), `toCreatedNeedVenue(Match, TeamCaptainContextView, Sport, List<SelectableVenueView>, List<TimeOptionView>)` (kèo B, không đọc lại); `costAgreement` = `MatchCostAgreementResponse`: `costType`, `costSplit`, `venueAmount`, `drinksAmount`, `depositAmount`, `depositHolder` (v2.0), `customItems` — record lồng riêng, **không** dùng `CostAgreementResponse` 7 field của 2.4; user chốt 2026-11-10, đóng `[cần xác nhận #12]`), `toSummary(MatchSummaryView)` (`distanceKm` HALF_UP 2 chữ số), `toRequest(MatchRequestView)`, `toCreatedRequest(MatchRequest, MatchRequestContext…)`, `toCostAgreement(Match, List<CostCustomItem>)`, `toAccept(…)`, `toCancellation(Match, int penalty)`, `toWithdrawal(…)`. `skillLevels` JSON → list qua `SkillLevelsConverter`. `SportMapper.toResponse(Sport)`.

## 8. Cấu hình (nguồn duy nhất cho giá trị) & Dependencies

```properties
arenaflow.match.lifecycle.cron=${MATCH_LIFECYCLE_CRON:0 */5 * * * *}
arenaflow.match.lifecycle.batch-size=200
arenaflow.match.lifecycle.max-batches-per-run=50
# Cấu hình CHUNG toàn ứng dụng (mọi @Scheduled dùng chung pool này, gồm job VENUE-001)
spring.task.scheduling.pool.size=3
```

| Tham số | Lý do giá trị |
|---|---|
| `cron` mỗi 5 phút | UC-M09, DoD Match; tắt bằng `-` trong test |
| `spring.task.scheduling.pool.size` 3 (chung toàn ứng dụng) | Mặc định Spring Boot là 1 luồng cho mọi `@Scheduled` → job dọn asset VENUE-001 (gọi HTTP Cloudinary, có thể chạy lâu) chặn job vòng đời kèo 5 phút. Hiện có 3 job định kỳ: vòng đời kèo (MATCH-001), cảnh báo 180 ngày và dọn asset (VENUE-001) → mỗi job một luồng, không job nào chờ job khác. Thêm job định kỳ mới → tăng giá trị này `[cần xác nhận #10]` |
| `batch-size` 200 × `max-batches-per-run` 50 | ≤ 10 000 kèo/lần chạy cho mỗi loại (AC §3.1 "≤10000"); lô nhỏ để transaction ngắn, không chặn API |

`ClockConfig`: `@Bean Clock clock() { return Clock.systemUTC(); }` — test thay bằng `Clock.fixed` cho biên khung trừ điểm / hết hạn. Không thêm Maven dependency.

## 9. Security

- Không endpoint public mới; không đổi `SecurityConfig`.
- Quyền đội trưởng kiểm trong service bằng `TeamGuard` (cần thứ tự guard, như USER-001 §4). Admin: `/api/v1/admin/**` + audit log sau commit (§4.11).
- Grace period user đã xoá: dựa JWT tối đa 1h (mức USER-001 `Processing_Flow.md` §9); captain đã xoá không còn `team_member` ACTIVE (USER-002) → `TEAM_002`.

## 10. Lệch convention / chuẩn có chủ đích

| Quy ước | Thiết kế | Lý do | Trạng thái |
|---|---|---|---|
| ADR-0003 / ADR-0007: không `SELECT … FOR UPDATE` ngoài ngoại lệ có ADR | Job claim lô bằng `FOR NO KEY UPDATE SKIP LOCKED` | Phát event chính xác cho đúng các kèo được chuyển mà không cần `UPDATE … RETURNING`; an toàn khi nhiều instance | ADR-0012 `Proposed`; khi `Accepted` → ADR-0003 cần mục Sửa đổi trỏ ADR-0012 |
| UC-M09 1a2 "lazy check cập nhật ngay" | API đọc chỉ tính trạng thái hiệu lực, không ghi | Ghi trong 2.3 vượt N_DETAIL (2 đọc + 2 ghi); kết quả quan sát được như spec | (user chốt 2026-11-09) |
| `non-functional-standards.md` §3: không đọc lại dữ liệu đã nạp | 2.1 đọc `MatchDetailView` sau INSERT | Response cần tên đội/sân/môn (join); dùng chung mapper với 2.3 (tiền lệ USER-001 `PATCH /profile`, VENUE-001 2.10–2.12) | Ghi nhận trong thiết kế |
| `convention-backend.md` §9: `@Transactional(readOnly)` mức class | Transaction theo method; ghi qua retrier | Kế thừa USER-001 §4.6 | Đã có tiền lệ |
| `convention-backend.md` §6.1: field do JPA quản lý không gán từ `src/main` (nêu cho builder) | 2.4 gán `Match.updatedAt` bằng setter | Khi chỉ đổi khoản tự nhập, entity không dirty → không có `UPDATE match` (mất CAS với chấp nhận) và `@PreUpdate` không chạy. Setter chỉ để entity dirty → Hibernate chạy `UPDATE match` CAS (tăng `version`, kích `@PreUpdate`) kể cả khi chỉ đổi khoản tự nhập; giá trị gán bị `@PreUpdate` ghi đè bằng `OffsetDateTime.now()`, nhất quán các entity khác (user chốt không đổi, 2026-11-09) | Ghi nhận trong thiết kế |
| Entity ghi qua dirty checking | `reliability_score` của `Team`/`User` `updatable = false`, chỉ `UPDATE` nguyên tử | Tránh mất cập nhật khi entity `Team`/`User` (không version ở `User`) ghi lại mọi cột | ADR-0013 `Proposed` |

## 11. Tài liệu cần đồng bộ khi implement

Sau khi mọi phase code xong + test PASS (CLAUDE.md §8):

| Tài liệu | Nội dung | Nhánh |
|---|---|---|
| `docs/database/schema.dbml`, `schema-design.md` §3.1, §3.4, §3.5 và quy ước ID (dòng 11) | Cột/CHECK/index V6; bỏ `CUSTOM` ở `cost_type`; dòng "tối đa 1 ACCEPTED (application layer)" → unique index; quy ước ID "Hibernate generates UUID v7 từ Java, không cần DEFAULT trong DB" thêm ngoại lệ: id `cost_custom_item` do `uuidv7()` của PostgreSQL ≥ 18 sinh trong câu INSERT gom lô (ADR-0008 Sửa đổi 2026-11-09) | `feature/MATCH-001-*` |
| `docs/database/data-model.md` §3.3 | Lý do huỷ/từ chối/rút; thoả thuận chi phí chỉ khi `OPEN`; rút `ACCEPTED` ⇒ huỷ kèo | `feature/MATCH-001-*` |
| `docs/agent/api-convention.md` §5, §6 | 15 endpoint vào bảng 2xx (tiêu chí ở §3); domain `MATCH`, `SPORT` (đã soát `ErrorCode` hiện hành 2026-11-09: chưa có `MATCH_*`, `SPORT_*`; mã `VENUE_*` theo VENUE-001) | `chore/agent-framework-*` (qua `workflow-adapt`) |
| `docs/agent/convention-backend.md` §1 | `application/event/<miền>/`, `application/support/<miền>/`, `api/config/ClockConfig` | `chore/agent-framework-*` |
| `docs/agent/design/USER-001/Component_Class_Design.md` §4 (`TeamGuard`), §6 (`Team`/`User` `updatable = false`) | + `USER-001/CHANGELOG.md` MINOR | `feature/MATCH-001-*` |
| `docs/agent/design/VENUE-001/Component_Class_Design.md` §4.13 | Overload `AdminAuditLog` + `VENUE-001/CHANGELOG.md` MINOR (tạo mới) | `feature/MATCH-001-*` |
| `docs/agent/design/VENUE-001/Component_Class_Design.md` §8 | Ghi chú job VENUE-001 chạy trên pool scheduling chung (`spring.task.scheduling.pool.size`, giá trị ở §8 tài liệu này) | `feature/MATCH-001-*` (+ `VENUE-001/CHANGELOG.md` MINOR) |
| ADR-0003 | Mục Sửa đổi trỏ ADR-0012 (khi ADR-0012 `Accepted`) | Theo PR |
| `docs/agent/decisions/0008-uuid-v7-primary-keys.md` | Mục Sửa đổi 2026-11-09 (`uuidv7()` cho INSERT gom lô viết tay) — đã viết sẵn trong thay đổi thiết kế này; kiểm lại khớp code | Cùng nhánh thiết kế |
| `docs/agent/CLAUDE.md` §2 | Dòng điều hướng MATCH-001 | `chore/agent-framework-*` |

## 12. Danh sách [cần xác nhận]

Đánh số toàn thiết kế (sau khi user chốt 11 quyết định nghiệp vụ và xử lý review 2026-11-09): 1–5, 10, 11 ở đây, 6–9 ở `Processing_Flow.md` §10.

| # | Nội dung | Loại |
|---|---|---|
| 1 | `insertAll`: bind mảng Java (`String[]`, `Long[]`) vào native query qua Spring Data / Hibernate 7.2 [Inference — chưa chạy]; không bind được → chạy đúng câu đó bằng `NamedParameterJdbcTemplate` trong cùng transaction (vẫn 1 câu). Lưu ý dự phòng: `JdbcTemplate` không kích auto-flush của Hibernate → phải gọi **sau** một câu native JPA (ở đây `deleteByMatchIdBulk`) để `UPDATE match` đã flush | Không chặn — kiểm bằng integration test đầu tiên của 2.4 |
| 2 | `reliability_score` `updatable = false`: entity `Team`/`User` đang nằm trong persistence context giữ giá trị cũ sau `adjustReliability` cùng transaction (MATCH-001 không đọc lại) | Không chặn |
| 3 | Bộ đếm `total_matches_confirmed/completed`, `clean_streak`, `report_count_*` thuộc REPUTATION-001; huỷ `CONFIRMED` có cắt chuỗi trận sạch không (Business_Rules §3.1 "Trận sạch") | Không chặn |
| 4 | Overload `AdminAuditLog.afterCommit(action, adminId, targetType, targetId)` sửa thiết kế VENUE-001 §4.13 (CHANGELOG MINOR) | Không chặn |
| 5 | **Thứ tự implement:** MATCH-001 cần code VENUE-001 (`VenueSelectionService`, retrier overload, `AdminAuditLog`, `SchedulingConfig`, `application/event/`, `VENUE_001/002`) **và migration V5**. DB dev đang ở Flyway V4; Flyway không chạy out-of-order → nếu phase 0 của MATCH-001 tự dựng phần VENUE-001 thì phải gồm `V5__venue_management.sql`, hoặc đổi số migration (MATCH-001 thành V5 và VENUE-001 lùi sang V6 — phải sửa thiết kế VENUE-001). Lựa chọn còn lại: implement VENUE-001 trước | **Chặn code** — user chọn thứ tự |
| 10 | `spring.task.scheduling.pool.size` còn hiệu lực với Spring Boot 4.0.9 (không bật virtual threads) [Inference — tên thuộc tính theo Spring Boot 3, chưa kiểm trên 4.0.9]; kiểm bằng log tên luồng `scheduling-1..3` khi chạy | Không chặn |
| 11 | Phiên bản PostgreSQL production (và môi trường khác ngoài Neon dev) chưa kiểm; `uuidv7()` cần ≥ 18 — thấp hơn thì V6 vẫn chạy nhưng 2.4 lỗi khi có khoản tự nhập | Không chặn — kiểm trước khi deploy |

## 13. Quyết định đã chốt (user, 2026-11-09 và 2026-11-10)

Chuyển từ `API_Spec.md` v1.0 §6.1 (bảng truy vết thiết kế, không publish cho FE). Quyết định nghiệp vụ 2026-11-09 đã đồng bộ vào SRS `SYSTEM-001` v1.3 (`Business_Rules.md` BR-M09..BR-M18, BR-C03..BR-C04; `Acceptance_Criteria.md` AC-M06 sửa, AC-M09..AC-M12). Quyết định 2026-11-10 (#12) là hợp đồng API, không thuộc SRS.

| Quyết định | Nơi áp dụng (`API_Spec.md` v1.1) |
|---|---|
| Quy tắc trừ điểm (đội bị trừ, khung giờ) | §1.5 |
| Rút yêu cầu `ACCEPTED` ⇒ kèo `CANCELLED`, chỉ đội rút bị trừ; đội đã ghép huỷ qua 2.5 cho cùng kết quả, trừ một lần | 2.5, 2.10, §1.5 |
| Không huỷ / rút khi trận đã bắt đầu (`MATCH_012`) | §1.5, 2.5, 2.10 |
| Huỷ / rút so với `expectedStatus` client gửi; lệch → `MATCH_015`, không trừ điểm | §1.4, 2.5, 2.10 |
| Đội `INACTIVE`/`DISBANDED`, bộ môn tắt: kèo `OPEN` ẩn khỏi tìm kiếm, chặn gửi/chấp nhận; không tự huỷ; đội `INACTIVE` vẫn huỷ / từ chối / rút | §1.2, 2.2, 2.6, 2.8, 2.15 |
| Chi phí: chỉ đội tác giả sửa, chỉ khi `OPEN`; bỏ `CUSTOM` của `cost_type` | 2.4, §4.1 |
| Trận ≤ 12h; ≤ 10 khoản tự nhập; `skillLevels` ≤ 3, rỗng = mọi trình độ; `ageNote` ≤100, `matchNature` ≤200, `reason` ≤500 (bắt buộc khi huỷ/rút sau ghép); số tiền ≤ 1 000 000 000; `LOSER_PAYS_DRINKS` chỉ khi có tiền nước | 2.1, 2.4, 2.5, 2.9, 2.10 |
| Tên field `startsAt` / `endsAt` | 2.1, §4 |
| API đọc trả trạng thái hiệu lực `EXPIRED` không ghi DB; thao tác ghi / job chuyển `EXPIRED` thật | §1.3 |
| Một user làm đội trưởng cả đội tác giả lẫn đội gửi (hai đội khác nhau) → cho phép; không kiểm trùng lịch | §1.2, 2.6 |
| (2026-11-10, đóng `[cần xác nhận #12]`) `costAgreement` lồng trong kèo chi tiết (2.1, 2.3) giữ 5 field như ví dụ đã publish; record riêng `MatchCostAgreementResponse` (§7), response 2.4 giữ `CostAgreementResponse` 7 field | §4.2 |
| (2026-11-10, v2.0) Kèo luồng B dùng chung 2.1/2.2/2.3/2.5/2.11; `costAgreement` lồng thêm `depositAmount`, `depositHolder`; 2.4/2.6/2.7 trên kèo B → `MATCH_016`; huỷ kèo B `OPEN` → pairing `SUPERSEDED`; "gần tôi" kèo B = lựa chọn địa điểm gần nhất (chi tiết MATCH-002 `Component_Class_Design.md` §13) | 2.1–2.7, 2.11, §4 |
