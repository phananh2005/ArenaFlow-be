# Component & Class Design — Đánh giá, report, tranh chấp & uy tín đội

| Mục | Giá trị |
|---|---|
| Mã chức năng | `REPUTATION-001` |
| Phạm vi tầng | Backend (Spring Boot REST API). Frontend: ngoài phạm vi repo này |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · Spring Data JPA · Flyway · PostgreSQL ≥ 18 |
| Quy ước áp dụng | `docs/agent/convention-backend.md`, `docs/agent/security-backend.md`, `docs/agent/non-functional-standards.md` · kế thừa USER-001 `Component_Class_Design.md` §4.6, §5, §10; VENUE-001 `Component_Class_Design.md` §4.9 (retrier overload), §4.13 (audit); MATCH-001 `Component_Class_Design.md` §4.5 (`TeamGuard`), §4.8 (`ReputationService` — sửa ở đây §4.6), §4.9 (event), §4.10 (scheduler), §4.11 (`AdminAuditLog.afterCommit(action, adminId, targetType, targetId)`), §8 (pool) · ADR-0002 (Sửa đổi 2026-11-12), ADR-0003 (Sửa đổi 2026-11-12), ADR-0008, ADR-0011, ADR-0012, ADR-0013 (Sửa đổi 2026-11-12) |
| Ràng buộc dữ liệu | V1–V4 (code) + V5 (VENUE-001), V6 (MATCH-001), V7 (MATCH-002) — **chưa có trong code** + `V8__reputation_rating_report_dispute.sql` (§5.1). Entity hiện có `domain/.../entity/rating/{Rating, Report}`, `team/Team`, `user/User` |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-13 · v1.0 |

## 1. Tổng quan kiến trúc

Layered theo 4 module Maven (ADR-0006) như USER-001. Hợp đồng: `API_Spec.md`. Luồng, transaction, số truy vấn, khoá: `Processing_Flow.md`.

- **Ghi uy tín cùng transaction** với thao tác gây ra, chỉ qua `ReputationService` (ADR-0013): mở rộng lõi MATCH-001 bằng 3 method (§4.6), mỗi method **một** câu `UPDATE team` nguyên tử (điểm, chuỗi trận sạch, bộ đếm report). Không listener nào ghi điểm/bộ đếm (ADR-0011 quy tắc 6).
- **Completion Rate, sao trung bình tính khi đọc** (Q5, ADR-0013 Sửa đổi 2026-11-12) từ `match`, `report`, `rating` — một đoạn SQL dùng chung (§5.3) cho 2.9 và huy hiệu. V8 xoá `total_matches_*`, `average_rating` của `team` và `"user"`.
- **Huy hiệu xác minh là dữ liệu dẫn xuất** (Q6-B): cột `team.verified_at`, chỉ ghi bằng một câu `UPDATE … RETURNING` có điều kiện (§4.8), chạy **sau commit** (đăng ký `afterCommit` từ service của mã này) + job đối soát hằng ngày cho mọi thay đổi khác (huỷ kèo, đổi đội trưởng, số điện thoại).
- **Report / tranh chấp đổi trạng thái bằng CAS tự viết** `UPDATE … WHERE status = …` (ADR-0003 Sửa đổi 2026-11-12); entity chỉ INSERT + đọc.
- **Trận `ARCHIVED → COMPLETED`** bằng câu có điều kiện trên `match` (`WHERE status = 'ARCHIVED'`, `version + 1`) — cùng mẫu `expireIfDue` của MATCH-001; 0 dòng = đội kia đã hoàn tất trước, không phải xung đột.
- Phạm vi chỉ **đội** (rater/ratee/reporter/reported type `TEAM`). Cá nhân (`INDIVIDUAL`) — MERCENARY-001 dùng lại bảng, `ReputationService` và mẫu này.

**Điều kiện tiên quyết:** code MATCH-001 (`ReputationService`, `TeamGuard.requireActiveCaptain`, `MatchCancellation`, `ClockConfig`, `SchedulingConfig`, `MATCH_001/009/014`), MATCH-002 (MATCH-002 `[cần xác nhận #4]` `UPDATE … RETURNING` không `@Modifying`; MATCH-002 `[cần xác nhận #5]` retrier bắt `ConcurrencyConflictException` — 2.5 cần khi CAS 0 dòng, `Processing_Flow.md` §3.5), VENUE-001 (`AdminAuditLog`, retrier overload) và migration V5–V7 phải có trước V8 `[cần xác nhận #8]`.

## 2. Package Structure (delta)

```text
common/        com.phananh.arenaflow.common
└── exception/       ErrorCode (+ RATING_001..003, REPORT_001..008, DISPUTE_001..002)

domain/        com.phananh.arenaflow.domain
├── entity/rating/   Rating, Report (sửa — §6); Dispute (mới)
├── entity/team/     Team (sửa — §6)
├── entity/user/     User (sửa — §6)
├── repository/      RatingRepository, ReportRepository, DisputeRepository (mới); TeamRepository, MatchRepository (sửa)
└── projection/      RatingContextView, ReportContextView, MatchRatingRowView, TeamReportView, DisputeContextView,
                     ReportDecisionView, AdminDisputeView, AdminDisputeDetailView, TeamReputationView,
                     ScoreChangeView, VerificationChangeView (mới)

application/   com.phananh.arenaflow.application
├── service/         RatingService, ReportService, DisputeService, AdminDisputeService, TeamReputationService,
│   │                TeamVerificationJobService (mới); ReputationService, ReliabilityChange, ReliabilityReason (sửa);
│   │                CompletionEntry, ReportDirection, DisputeStatusFilter (mới)
│   └── impl/        …ServiceImpl tương ứng; ReputationServiceImpl (sửa)
├── event/reputation/ MatchRatedEvent, TeamReportedEvent, ReportDisputedEvent, DisputeResolvedEvent,
│                    TeamVerificationChangedEvent (mới); ReliabilityScoreChangedEvent (giữ — MATCH-001)
├── support/reputation/ ReportPenaltyPolicy, ReputationRules, TeamVerificationRefresher (mới); TimeBandPenaltyPolicy (giữ)
├── validation/      ValidRating (+ validator) (mới); OneOf (tái dùng)
├── dto/request/     CreateRatingRequest, CreateReportRequest, CreateDisputeRequest, ResolveDisputeRequest
├── dto/response/    RatingResponse, RatingCreatedResponse, MatchRatingsResponse, TeamBriefResponse, ReportResponse,
│                    ReportDisputeResponse, DisputeCreatedResponse, AdminTeamBriefResponse, AdminDisputeResponse,
│                    AdminDisputeDetailResponse, DisputeMatchResponse, TeamReputationResponse (+ CompletionResponse,
│                    RatingSummaryResponse, VerificationResponse)
└── mapper/          ReputationMapper (MapStruct); MapperSupport (tái dùng)

api/           com.phananh.arenaflow.api
├── reputation/      MatchReputationController, ReportController, TeamReputationController, AdminDisputeController
└── scheduler/       TeamVerificationScheduler (mới)
```

Thành phần dùng chung mới (`TeamVerificationRefresher`, `ReportPenaltyPolicy`, `ReputationRules`, đoạn SQL thống kê §5.3) → developer MUST chạy `reuse-scan` trước khi tạo.

## 3. Controller Layer

Quy ước như MATCH-001 §3 (`@Validated`, `ResponseEntity<ApiResponse<…>>`, `@CurrentUser UUID currentUserId`, 201 không `Location`).

| Controller | `@RequestMapping` | Endpoint | Service |
|---|---|---|---|
| `MatchReputationController` | `/api/v1/matches/{matchId}` (`/ratings`, `/reports`) | 2.1, 2.2, 2.3 | `RatingService`, `ReportService` |
| `ReportController` | `/api/v1/reports` | 2.5 | `DisputeService` |
| `TeamReputationController` | `/api/v1/teams/{teamId}` (`/reports`, `/reputation`) | 2.4, 2.9 | `ReportService`, `TeamReputationService` |
| `AdminDisputeController` | `/api/v1/admin/disputes` | 2.6–2.8 | `AdminDisputeService` |

| Endpoint | Thành công | Tiêu chí `api-convention.md` §5 |
|---|---|---|
| 2.1, 2.3, 2.5 | 201 | B2 |
| 2.2, 2.4, 2.6, 2.7, 2.9 | 200 | B1 |
| 2.8 | 200 | B3 (`resolvedAt`, `resolvedByName`, điểm sau hoàn), B4 |

**Path/method:** 2.1, 2.3 tạo tài nguyên con dưới trận (tiền lệ `POST /matches/{id}/requests`). 2.5 tạo tranh chấp 1:1 dưới report → danh từ số ít `/dispute`, `POST` 201. 2.8 action sub-resource Admin, `PATCH` theo tiền lệ VENUE-001 `PATCH /admin/venue-reports/{id}/resolve`. 2.9 tách khỏi `GET /teams/{id}` (Q8 — không đổi hợp đồng USER-001).

- 2.2: `@RequestParam UUID teamId` bắt buộc. 2.4: `direction` enum `ReportDirection` mặc định `RECEIVED`. 2.6: `DisputeStatusFilter` (`PENDING` mặc định, `RESOLVED`, `ALL`). Enum sai → `MethodArgumentTypeMismatchException` → 400 (như `AdminTeamController`).
- Không đổi `SecurityConfig` (`/api/v1/admin/**` đã `hasRole('ADMIN')`).

## 4. Service Layer

Không `@Transactional` mức class (USER-001 §4.6). Ranh giới: §4.11.

### 4.1. RatingService
```java
RatingCreatedResponse createRating(UUID matchId, UUID callerId, CreateRatingRequest r);   // retrier MATCH_014
MatchRatingsResponse getMatchRatings(UUID matchId, UUID teamId, UUID callerId);          // readOnly
```
Deps: `MatchRepository` (`findRatingContext`, `completeIfArchived`), `RatingRepository`, `TeamGuard`, `ReputationService`, `TeamVerificationRefresher`, `ConcurrencyConflictRetrier`, `ReputationMapper`, `ApplicationEventPublisher`, `Clock`.
- `createRating`: guard theo thứ tự `Processing_Flow.md` §1.1 → đội đánh giá = phía mà `callerId` là captain ACTIVE (không nhận `teamId`) → `Rating.builder()` (`raterType/rateeType = TEAM`, `createdByUserId = callerId`) `saveAndFlush` (bắt `idx_rating_unique` → `RATING_002`; NFS §4.1 ngoại lệ 1, comment tại chỗ) → `ARCHIVED`: `completeIfArchived(matchId, now)` → 1 dòng: `applyMatchCompletion` (§4.6) với entry đội đánh giá (`completes = true`, `ratingBonus = 0`) và — khi `!isNoShow` — đội được đánh giá (`completes = true`, `ratingBonus = score == 5 ? 1 : 0`); 0 dòng hoặc trận đã `COMPLETED`: `score == 5` → `adjustTeamReliability(ratee, +1, FIVE_STAR_RATING, ratingId, false)` → `MatchRatedEvent` → `refreshAfterCommit({ratee} ∪ {rater nếu hoàn tất})`.
- `getMatchRatings`: `findMatchRatingRows(matchId)` (trận + 0..2 đánh giá; rỗng → `MATCH_001`) → `requireActiveCaptain(teamId, callerId)` (`TEAM_001` → `TEAM_016` → `TEAM_015` → `TEAM_002`, BR-R10) → `teamId` ∉ {author, opponent} → `MATCH_009` → `given` = dòng `raterId = teamId`; `received` = dòng `rateeId = teamId` **chỉ khi** `given != null` (BC-R03/R04).

### 4.2. ReportService
```java
ReportResponse createReport(UUID matchId, UUID callerId, CreateReportRequest r);                       // retrier MATCH_014
PageResponse<ReportResponse> listTeamReports(UUID teamId, UUID callerId, ReportDirection d, int page, int pageSize); // readOnly
```
Deps: `MatchRepository` (`findReportContext`), `ReportRepository`, `TeamGuard`, `ReputationService`, `TeamVerificationRefresher`, `ConcurrencyConflictRetrier`, `ReputationMapper`, `PageRequestFactory`, `ApplicationEventPublisher`, `Clock`.
- `createReport`: guard (`Processing_Flow.md` §1.1) → `Report.builder()` (`status = AUTO_ACCEPTED`, `reliabilityPenalty = 0`, `createdByUserId`) `saveAndFlush` (bắt `idx_report_unique` → `REPORT_004`) → `applied = reputationService.applyReportPenalty(reportedTeamId, type, reportId)` → `report.setReliabilityPenalty(applied)` (dirty checking, ghi lúc commit) → `TeamReportedEvent` → `refreshAfterCommit({reported})`.
- `listTeamReports`: `requireActiveCaptain` (BR-R10) → `findTeamReports`/`countTeamReports` theo `direction` (§5.2). Mapper tính `status` hiệu lực, `disputeDeadlineAt`, `canDispute` (§4.10); `SENT` → `reliabilityPenalty = null`, `dispute.evidence = null`.

### 4.3. DisputeService
```java
DisputeCreatedResponse createDispute(UUID reportId, UUID callerId, CreateDisputeRequest r);  // retrier MATCH_014
```
Deps: `ReportRepository` (`findDisputeContext`, `markDisputed`), `DisputeRepository`, `ConcurrencyConflictRetrier`, `ReputationMapper`, `ApplicationEventPublisher`, `Clock`.
- Guard (`Processing_Flow.md` §1.1, kiểm đội bị report trên dữ liệu `findDisputeContext` — không gọi `TeamGuard` để giữ 1 câu) → `markDisputed(reportId, now, now − DISPUTE_WINDOW)` 0 dòng → `ConcurrencyConflictException` (lần thử sau guard trả `REPORT_008`/`REPORT_007`) → `Dispute.builder()` `save` (unique `report_id` được CAS bảo vệ; flush lúc commit) → `ReportDisputedEvent`.

### 4.4. AdminDisputeService
```java
PageResponse<AdminDisputeResponse> listDisputes(DisputeStatusFilter s, int page, int pageSize); // readOnly
AdminDisputeDetailResponse getDispute(UUID disputeId);                                        // readOnly
AdminDisputeDetailResponse resolveDispute(UUID disputeId, UUID adminId, ResolveDisputeRequest r); // retrier MATCH_014
```
Deps: `DisputeRepository`, `ReportRepository` (`applyDecision`), `RatingRepository` (`findByMatchId…`), `ReputationService`, `TeamVerificationRefresher`, `AdminAuditLog`, `ConcurrencyConflictRetrier`, `ReputationMapper`, `PageRequestFactory`, `ApplicationEventPublisher`, `Clock`.
- `resolveDispute`: `resolveIfPending(...)` trả `Optional<UUID reportId>`; rỗng → `existsById` → `DISPUTE_001` / `DISPUTE_002` → `applyDecision(reportId, decision, now)` trả `ReportDecisionView` (phải có dòng, không → `IllegalStateException`) → `REJECTED`: `refundReportPenalty(reportedTeamId, type, penalty, reportId)` + `refreshAfterCommit({reported})`; `CONFIRMED`: không ghi điểm (Q2) → `DisputeResolvedEvent` → `adminAuditLog.afterCommit("RESOLVE_DISPUTE", adminId, "DISPUTE", disputeId)` → đọc lại chi tiết như `getDispute` trong cùng lần thử.

### 4.5. TeamReputationService
```java
TeamReputationResponse getTeamReputation(UUID teamId);   // readOnly
```
`TeamRepository.findReputation(teamId, now)` (§5.3) → rỗng → `TEAM_001` → mapper: `reliabilityLevel` theo `ReputationRules`, `completionRate = countedMatches < NEWCOMER_THRESHOLD ? null : round(100 × completed / counted)` (HALF_UP), `averageRating` HALF_UP 1 chữ số.

### 4.6. ReputationService — mở rộng lõi MATCH-001 (ADR-0013)

```java
public interface ReputationService {                                   // mọi method @Transactional(propagation = MANDATORY)
    void adjustTeamReliability(UUID teamId, ReliabilityChange change);  // MATCH-001; sửa: change.breaksCleanStreak()
    void adjustUserReliability(UUID userId, ReliabilityChange change);  // MATCH-001; giữ
    void applyMatchCompletion(UUID matchId, List<CompletionEntry> entries);
    int  applyReportPenalty(UUID teamId, Report.ReportType type, UUID reportId);          // trả điểm thực trừ (≥ 0)
    void refundReportPenalty(UUID teamId, Report.ReportType type, int points, UUID reportId);
}
public record ReliabilityChange(int delta, ReliabilityReason reason, UUID referenceId, boolean breaksCleanStreak) {}
public record CompletionEntry(UUID teamId, boolean completes, int ratingBonus) {}
public enum ReliabilityReason { MATCH_CANCELLED_AFTER_CONFIRMED, MATCH_REQUEST_WITHDRAWN_AFTER_ACCEPTED,
                                MATCH_COMPLETED, FIVE_STAR_RATING, REPORT_RECEIVED, REPORT_REJECTED_REFUND }
```
- `adjustTeamReliability`: `delta == 0 && !breaksCleanStreak` → không truy vấn. SQL `TeamRepository.adjustReliability(id, delta, breaksCleanStreak)` (§5.2). MATCH-001 `MatchCancellation` truyền `breaksCleanStreak = true` (Q3, MATCH-001 v2.2).
- `applyMatchCompletion`: một câu `TeamRepository.applyCompletion` cho mọi entry (§5.2) → `ScoreChangeView` mỗi đội → event mỗi đội có `newScore ≠ oldScore` (`reason = MATCH_COMPLETED`, `delta = newScore − oldScore`, `referenceId = matchId`). Số dòng ≠ số entry → `IllegalStateException`.
- `applyReportPenalty`: `TeamRepository.applyReportPenalty(id, type.name(), ReportPenaltyPolicy.tiers(type))` trả `oldScore`, `newScore` → `points = oldScore − newScore` → event `REPORT_RECEIVED` (khi `points > 0`) → trả `points`.
- `refundReportPenalty`: `TeamRepository.refundReportPenalty(id, type.name(), points)` (luôn chạy — giảm bộ đếm cả khi `points = 0`; **không** khôi phục `clean_streak` — user chốt E) → event `REPORT_REJECTED_REFUND` khi `points > 0`.
- Event `ReliabilityScoreChangedEvent` giữ payload MATCH-001; `delta` của 3 method mới là **thay đổi thực** (sau chặn [0,100]).

**`ReportPenaltyPolicy`** (`support/reputation`, enum map một nơi — Business_Rules §3.2):

| Loại | Lần 1 | 2 | 3 | 4+ |
|---|---|---|---|---|
| `NOSHOW` | 5 | 8 | 12 | 15 |
| `BAD_PLAY` | 3 | 5 | 8 | 10 |
| `BAD_COMMUNICATION` | 2 | 4 | 6 | 8 |
| `SKILL_MISMATCH` | 1 | 2 | 3 | 4 |

`int[] tiers(ReportType)` trả mảng 4 phần tử (giá trị dương); lần tính = bộ đếm **sau khi tăng**, chặn 4.

**`ReputationRules`** (hằng một nơi): `COMPLETION_POINTS = 2`, `STREAK_MILESTONE = 5`, `STREAK_BONUS_5 = 5`, `STREAK_BONUS_10 = 10`, `FIVE_STAR_BONUS = 1`, `DISPUTE_WINDOW = 24h`, `NEWCOMER_THRESHOLD = 5`, `BADGE_MIN_COMPLETED_EXCLUSIVE = 5`, `BADGE_MIN_AVG_EXCLUSIVE = 3.0`, `BADGE_MIN_SCORE = 70`, ngưỡng `reliabilityLevel` (95/70/50). Hằng được truyền vào SQL bằng tham số, không viết literal trong câu.

### 4.7. Domain event (`application/event/reputation`, ADR-0011)

| Event | Payload | Phát khi | Người nghe dự kiến |
|---|---|---|---|
| `MatchRatedEvent` | `ratingId, matchId, raterTeamId, rateeTeamId, isNoShow, matchCompleted, occurredAt` | 2.1 | NOTIFICATION-001 (`Match completed` khi `matchCompleted`; "rating nhận" — xem `[cần xác nhận #11]`) |
| `TeamReportedEvent` | `reportId, matchId, reporterTeamId, reportedTeamId, reportType, occurredAt` | 2.3 | NOTIFICATION-001 ("report nhận"; `NOSHOW`: "Bạn có 24h để tranh chấp") |
| `ReportDisputedEvent` | `disputeId, reportId, reporterTeamId, reportedTeamId, occurredAt` | 2.5 | NOTIFICATION-001 (tuỳ chọn — báo đội report) |
| `DisputeResolvedEvent` | `disputeId, reportId, reporterTeamId, reportedTeamId, decision, occurredAt` | 2.8 | NOTIFICATION-001 (cả hai đội — UC-R04 bước 5) |
| `TeamVerificationChangedEvent` | `teamId, verified, occurredAt` | `TeamVerificationRefresher` (sau commit của thao tác gốc, trong transaction riêng) | NOTIFICATION-001 (cấp/thu hồi huy hiệu — UC-R07 bước 4) |
| `ReliabilityScoreChangedEvent` | MATCH-001 §4.9 | `ReputationService` | Như MATCH-001 |

Mỗi thao tác phát **một** event nghiệp vụ (ADR-0011 quy tắc 7); `ReliabilityScoreChangedEvent` là sự kiện điểm đi kèm (như MATCH-001). Payload id + enum + thời điểm, không PII.

### 4.8. Huy hiệu xác minh — `TeamVerificationRefresher` (`support/reputation`, `@Component`)

```java
void refreshAfterCommit(Collection<UUID> teamIds);  // gọi trong transaction đang chạy; gom id theo transaction
void refreshNow(Collection<UUID> teamIds);          // TransactionTemplate REQUIRES_NEW; dùng ở afterCommit
```
- `refreshAfterCommit`: lần gọi đầu trong transaction đăng ký **một** `TransactionSynchronization` (khoá `TransactionSynchronizationManager.bindResource` theo bean) giữ `Set<UUID>`; lần gọi sau chỉ thêm id → mỗi request tối đa **1** câu refresh. `afterCommit()` → `refreshNow(ids)`. Lần thử rollback → không chạy (như `AdminAuditLog`). Không có transaction → `IllegalStateException`.
  - `afterCompletion(status)` với **mọi** `status` (commit lẫn rollback) → `TransactionSynchronizationManager.unbindResource(this)` — Spring dọn danh sách synchronization nhưng không gỡ resource do code tự bind; không gỡ thì lần thử sau của retrier hoặc request sau trên cùng thread thấy `Set` cũ, không đăng ký synchronization mới → huy hiệu không được refresh mà không báo lỗi.
  - Test bắt buộc: (1) lần thử 1 rollback (`ConcurrencyConflictException`) → lần thử 2 commit vẫn gọi `refreshNow` đúng một lần; (2) hai request liên tiếp trên cùng thread → mỗi request gọi `refreshNow` một lần với đúng tập id của mình.
- `refreshNow`: `TransactionTemplate` `PROPAGATION_REQUIRES_NEW` (bắt buộc trong `afterCommit` — `[cần xác nhận #9]`) → `TeamRepository.refreshVerification(ids, now, …ngưỡng)` (§5.3) trả các đội **đổi** trạng thái → `TeamVerificationChangedEvent` mỗi đội. Lỗi → log `WARN` (chỉ id), không ném — job hằng ngày sửa lại.
- **Không** dùng `@TransactionalEventListener` cho event của chính REPUTATION-001 (ADR-0011 quy tắc 5 — miền phát không nghe event của mình). **Không nghe `MatchCancelledEvent`** (user chốt A1, 2026-11-12): giữ MATCH-001 2.10 rút yêu cầu `ACCEPTED` ở 6/W6; đội bị trừ điểm do huỷ / rút được cập nhật huy hiệu ở job hằng ngày (trễ tối đa ~1 ngày).
- **Không tự cập nhật ngay** khi huỷ kèo đã ghép / rút yêu cầu đã chấp nhận (MATCH-001), đổi đội trưởng (USER-001 transfer), đổi số điện thoại, xoá tài khoản: chờ job hằng ngày (Q6-B, A1) — ngoại lệ "thu hồi ngay" đã ghi ở SRS `Business_Rules.md` §8.2 (v1.8).

**`TeamVerificationJobService.reconcileAll()`** (+ `TeamVerificationScheduler` ở `api/scheduler`, `@Scheduled(cron = "${arenaflow.reputation.verification.cron}", zone = "Asia/Ho_Chi_Minh")`): lặp lô theo khoá `id` tăng dần: `TeamRepository.findIdsForVerification(afterId, batchSize)` → `refreshVerification(ids, now)` → event; mỗi lô một `TransactionTemplate`. Không theo mẫu claim của ADR-0012 vì không chuyển trạng thái theo hàng đợi: câu refresh tự có điều kiện "trạng thái đổi" → hai instance chạy trùng không phát event trùng (bên sau thấy dòng đã đổi).

### 4.9. Guard trận cho đánh giá / report

Không thêm component: `RatingServiceImpl` / `ReportServiceImpl` đọc một `…ContextView` (§5.3) và kiểm theo thứ tự `Processing_Flow.md` §1.1. Hàm private chung `resolveSides(ctx)` → `(raterTeamId, rateeTeamId)`; developer gom vào helper khi reuse-scan thấy lặp.

### 4.10. Trạng thái report hiệu lực

`ReportStatusResolver` (hàm static trong mapper hoặc `support/reputation`): `status == AUTO_ACCEPTED && type == NOSHOW && now ≥ createdAt + DISPUTE_WINDOW` → `CONFIRMED`; còn lại giữ nguyên. **Không job** ghi `CONFIRMED` khi hết hạn: không luồng nào đọc `report.status = 'CONFIRMED'` để ra quyết định (Completion Rate dùng `status <> 'REJECTED'`; tranh chấp kiểm hạn trong câu CAS). `canDispute` = `RECEIVED && NOSHOW && status (DB) == AUTO_ACCEPTED && now < deadline`.

### 4.11. Transaction

| Method | Ranh giới |
|---|---|
| `getMatchRatings`, `listTeamReports`, `listDisputes`, `getDispute`, `getTeamReputation` | `@Transactional(readOnly = true)` |
| `createRating`, `createReport`, `createDispute`, `resolveDispute` | Không annotate; `concurrencyConflictRetrier.execute(MATCH_014, …)` (thử lại xung đột CAS và deadlock `40P01`) |
| `ReputationService.*` | `MANDATORY` |
| `TeamVerificationRefresher.refreshNow`, mỗi lô `reconcileAll` | `TransactionTemplate` (`REQUIRES_NEW` cho `refreshNow`) |

## 5. Repository & Data Model

### 5.1. Migration `V8__reputation_rating_report_dispute.sql`

```sql
-- rating: sao nguyên 1..5 (Q14), "không đến" (Q1), tỷ số theo góc nhìn đội đánh giá, người gửi, FK trận
ALTER TABLE rating DROP CONSTRAINT rating_score_check;
ALTER TABLE rating ALTER COLUMN score DROP NOT NULL;
ALTER TABLE rating ALTER COLUMN score TYPE SMALLINT USING score::smallint;
ALTER TABLE rating ADD CONSTRAINT rating_score_check CHECK (score IS NULL OR score BETWEEN 1 AND 5);
ALTER TABLE rating ADD COLUMN is_no_show BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE rating RENAME COLUMN match_score_home TO rater_team_score;
ALTER TABLE rating RENAME COLUMN match_score_away TO ratee_team_score;
ALTER TABLE rating RENAME CONSTRAINT rating_match_score_home_check TO rating_rater_team_score_check;
ALTER TABLE rating RENAME CONSTRAINT rating_match_score_away_check TO rating_ratee_team_score_check;
ALTER TABLE rating ADD COLUMN created_by_user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE RESTRICT;
ALTER TABLE rating ADD CONSTRAINT rating_match_fk FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE RESTRICT;
ALTER TABLE rating ADD CONSTRAINT rating_no_show_check CHECK (
    (is_no_show AND score IS NULL AND rater_team_score IS NULL AND ratee_team_score IS NULL)
    OR (NOT is_no_show AND score IS NOT NULL));
ALTER TABLE rating ADD CONSTRAINT rating_team_scores_pair_check CHECK ((rater_team_score IS NULL) = (ratee_team_score IS NULL));
ALTER TABLE rating ADD CONSTRAINT rating_comment_length_check CHECK (comment IS NULL OR char_length(comment) <= 500);
ALTER TABLE rating ADD CONSTRAINT rating_not_self_check CHECK (NOT (rater_type = ratee_type AND rater_id = ratee_id));

-- report: trạng thái theo spec (decisions-log 2026-11-09), điểm thực trừ (Q9), người gửi, FK trận, chống trùng (Business_Rules §8.4)
ALTER TABLE report DROP CONSTRAINT report_status_check;
UPDATE report SET status = 'AUTO_ACCEPTED' WHERE status = 'PENDING';
ALTER TABLE report ALTER COLUMN status SET DEFAULT 'AUTO_ACCEPTED';
ALTER TABLE report ADD CONSTRAINT report_status_check CHECK (status IN ('AUTO_ACCEPTED', 'DISPUTED', 'CONFIRMED', 'REJECTED'));
ALTER TABLE report DROP COLUMN admin_reviewed_at;
ALTER TABLE report ALTER COLUMN reason SET NOT NULL;
ALTER TABLE report ADD CONSTRAINT report_reason_length_check CHECK (char_length(reason) BETWEEN 1 AND 1000);
ALTER TABLE report ADD COLUMN reliability_penalty INTEGER NOT NULL DEFAULT 0 CHECK (reliability_penalty >= 0);
ALTER TABLE report ADD COLUMN created_by_user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE RESTRICT;
ALTER TABLE report ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE report ADD CONSTRAINT report_match_fk FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE RESTRICT;
ALTER TABLE report ADD CONSTRAINT report_not_self_check CHECK (NOT (reporter_type = reported_type AND reporter_id = reported_id));
DROP INDEX idx_report_match;
DROP INDEX idx_report_reported;
DROP INDEX idx_report_status;
CREATE UNIQUE INDEX idx_report_unique ON report(match_id, reporter_type, reporter_id, reported_type, reported_id, report_type);
CREATE INDEX idx_report_reported_created ON report(reported_type, reported_id, created_at DESC, id DESC);
CREATE INDEX idx_report_reporter_created ON report(reporter_type, reporter_id, created_at DESC, id DESC);

-- dispute: 1:1 với report bùng (decisions-log 2026-11-09)
CREATE TABLE dispute (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES report(id) ON DELETE RESTRICT,
    evidence TEXT NOT NULL CHECK (char_length(evidence) BETWEEN 1 AND 1000),
    created_by_user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RESOLVED')),
    decision VARCHAR(20) CHECK (decision IN ('CONFIRMED', 'REJECTED')),
    resolution_note VARCHAR(500) CHECK (resolution_note IS NULL OR char_length(resolution_note) BETWEEN 1 AND 500),
    resolved_by_user_id UUID REFERENCES "user"(id) ON DELETE RESTRICT,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT dispute_resolution_check CHECK (
        (status = 'PENDING' AND decision IS NULL AND resolution_note IS NULL AND resolved_by_user_id IS NULL AND resolved_at IS NULL)
        OR (status = 'RESOLVED' AND decision IS NOT NULL AND resolution_note IS NOT NULL AND resolved_by_user_id IS NOT NULL AND resolved_at IS NOT NULL))
);
CREATE UNIQUE INDEX idx_dispute_report ON dispute(report_id);
CREATE INDEX idx_dispute_status_created ON dispute(status, created_at, id);

-- team / user: Completion Rate, sao trung bình tính khi đọc (Q5); huy hiệu dẫn xuất (Q6)
ALTER TABLE team DROP COLUMN total_matches_completed, DROP COLUMN total_matches_confirmed, DROP COLUMN average_rating;
ALTER TABLE "user" DROP COLUMN total_matches_completed, DROP COLUMN total_matches_confirmed, DROP COLUMN average_rating;
ALTER TABLE team ADD COLUMN verified_at TIMESTAMPTZ;
```
- **Dữ liệu cũ:** đã kiểm Neon dev `misty-dew-90700890` (branch mặc định) ngày 2026-11-12 — `rating`, `report`, `match` 0 dòng; không đội nào có `total_matches_*`/`average_rating`/`clean_streak` khác mặc định; Flyway ở **V4**. Tên constraint V1 (`rating_score_check`, `rating_match_score_*_check`, `report_status_check`) và `user_check` (`total_matches_completed <= total_matches_confirmed` — tự xoá theo `DROP COLUMN`) đã đối chiếu `pg_constraint` cùng ngày. Môi trường khác có dữ liệu: `NOT NULL` cột mới không mặc định (`created_by_user_id`) làm V8 lỗi → dừng, báo, không tự sửa dữ liệu.
- **Số hiệu V8:** code có V1–V4; thiết kế đã chiếm V5 (VENUE-001), V6 (MATCH-001), V7 (MATCH-002). Flyway không chạy out-of-order → V8 chỉ áp sau V5–V7 `[cần xác nhận #8]`.
- Index: `idx_report_unique` (chống trùng + tra report theo trận — tiền tố `match_id` thay `idx_report_match`); `idx_report_*_created` cho 2.4 (lọc + sắp xếp); `idx_dispute_status_created` cho 2.6 (đọc xuôi `PENDING`, đọc ngược `RESOLVED`); `idx_rating_unique` (V1, tiền tố `match_id`) cho 2.2/2.7; `idx_rating_ratee` (V1) cho sao trung bình; Completion Rate dùng `idx_match_author_status` (V1) + `idx_match_opponent_status` (V6). `idx_report_status` bỏ vì không còn truy vấn lọc theo trạng thái report.
- FK `rating.match_id`, `report.match_id` (V1 thiếu) — `ON DELETE RESTRICT` (ADR-0002). `rater_id`/`reported_id` đa hình (TEAM/USER) → không FK.

### 5.2. Repository

```java
// RatingRepository extends JpaRepository<Rating, UUID>
List<MatchRatingRowView> findMatchRatingRows(UUID matchId);   // match LEFT JOIN rating (rater_type = 'TEAM'); rỗng = không có trận
List<Rating> findByMatchIdAndRaterTypeOrderByCreatedAtAsc(UUID matchId, Rating.RaterType t); // 2.7

// ReportRepository extends JpaRepository<Report, UUID>
List<TeamReportView> findTeamReports(UUID teamId, String direction, int limit, long offset);  // native
long countTeamReports(UUID teamId, String direction);
Optional<DisputeContextView> findDisputeContext(UUID reportId, UUID callerId);
@Modifying int markDisputed(UUID reportId, OffsetDateTime now, OffsetDateTime createdAfter);
Optional<ReportDecisionView> applyDecision(UUID reportId, String decision, OffsetDateTime now); // UPDATE … RETURNING

// DisputeRepository extends JpaRepository<Dispute, UUID>
Optional<UUID> resolveIfPending(UUID id, String decision, String note, UUID adminId, OffsetDateTime now); // UPDATE … RETURNING report_id
List<AdminDisputeView> findAdminDisputes(String status, boolean oldestFirst, int limit, long offset);
long countAdminDisputes(String status);
Optional<AdminDisputeDetailView> findAdminDisputeDetail(UUID id);

// MatchRepository (sửa)
Optional<RatingContextView> findRatingContext(UUID matchId, UUID callerId);
Optional<ReportContextView> findReportContext(UUID matchId, UUID callerId, String reportType);
@Modifying int completeIfArchived(UUID matchId, OffsetDateTime now);

// TeamRepository (sửa)
@Modifying int adjustReliability(UUID id, int delta, boolean breaksCleanStreak);          // MATCH-001 — thêm tham số
List<ScoreChangeView> applyCompletion(UUID matchId, UUID[] ids, boolean[] completes, int[] bonuses, …hằng ReputationRules);
Optional<ScoreChangeView> applyReportPenalty(UUID id, String type, int[] tiers);
@Modifying int refundReportPenalty(UUID id, String type, int points);
Optional<TeamReputationView> findReputation(UUID teamId, OffsetDateTime now);
List<VerificationChangeView> refreshVerification(UUID[] ids, OffsetDateTime now, …ngưỡng huy hiệu);
List<UUID> findIdsForVerification(UUID afterId, int limit);
```
Câu `… RETURNING` khai báo native trả projection, không `@Modifying` (MATCH-002 `[cần xác nhận #4]`).

Ghi chú SQL:
- `adjustReliability`: `SET reliability_score = LEAST(100, GREATEST(0, reliability_score + :delta)), clean_streak = CASE WHEN :breaks THEN 0 ELSE clean_streak END WHERE id = :id` — không tăng `version`.
- `completeIfArchived`: `UPDATE match SET status = 'COMPLETED', version = version + 1, updated_at = :now WHERE id = :id AND status = 'ARCHIVED'` — cùng mẫu `expireIfDue` (MATCH-001 §4.6), giữ quy tắc bulk tự tăng `version` (ADR-0003 Sửa đổi 2026-11-10 "Không phải ngoại lệ").
- `applyCompletion` (khung — developer viết bản chạy được, test theo bảng `Processing_Flow.md` §3.1):
  ```sql
  WITH input AS (SELECT * FROM unnest(CAST(:ids AS uuid[]), CAST(:completes AS boolean[]), CAST(:bonuses AS int[])) AS i(team_id, completes, bonus)),
  locked AS (SELECT t.id, t.reliability_score AS old_score, t.clean_streak AS old_streak
             FROM team t JOIN input i ON i.team_id = t.id ORDER BY t.id FOR NO KEY UPDATE OF t),
  calc AS (SELECT l.id, l.old_score, i.completes, i.bonus, l.old_streak + 1 AS next_streak,
                  i.completes AND NOT EXISTS (SELECT 1 FROM report r WHERE r.match_id = :matchId AND r.reported_type = 'TEAM'
                                              AND r.reported_id = l.id AND r.status <> 'REJECTED') AS clean
           FROM locked l JOIN input i ON i.team_id = l.id)
  UPDATE team t SET
    clean_streak = CASE WHEN NOT c.completes THEN t.clean_streak WHEN c.clean THEN c.next_streak ELSE 0 END,
    reliability_score = LEAST(100, GREATEST(0, t.reliability_score + c.bonus
        + CASE WHEN c.completes THEN :completionPoints ELSE 0 END
        + CASE WHEN c.clean AND c.next_streak % (2 * :milestone) = 0 THEN :bonus10
               WHEN c.clean AND c.next_streak % :milestone = 0 THEN :bonus5 ELSE 0 END)),
    report_count_noshow = CASE WHEN c.clean AND c.next_streak % :milestone = 0 THEN 0 ELSE t.report_count_noshow END
    -- tương tự report_count_bad_play, report_count_bad_communication, report_count_skill_mismatch
  FROM calc c WHERE t.id = c.id
  RETURNING t.id, c.old_score, t.reliability_score AS new_score
  ```
  `ORDER BY t.id FOR NO KEY UPDATE` khoá hai đội theo thứ tự id cố định (chống deadlock giữa hai trận cùng cặp đội); `old_score` đọc sau khi khoá → điểm trước thay đổi `[cần xác nhận #6]`.
- `applyReportPenalty`: `WITH locked AS (SELECT id, reliability_score AS old_score FROM team WHERE id = :id FOR NO KEY UPDATE) UPDATE team t SET report_count_<loại> = report_count_<loại> + 1` (4 cột viết bằng `CASE WHEN :type = '…' THEN … + 1 ELSE … END`, **không** nối chuỗi tên cột), `clean_streak = 0`, `reliability_score = GREATEST(0, t.reliability_score - (CAST(:tiers AS int[]))[LEAST(<bộ đếm loại đó> + 1, 4)]) FROM locked l WHERE t.id = l.id RETURNING l.old_score, t.reliability_score AS new_score`.
- `refundReportPenalty`: `SET reliability_score = LEAST(100, reliability_score + :points), report_count_<loại> = GREATEST(0, report_count_<loại> - 1)` (CASE theo `:type`). `GREATEST(0, …)` vì bộ đếm có thể đã về 0 do mốc chuỗi.
- `markDisputed`: `UPDATE report SET status = 'DISPUTED', updated_at = :now WHERE id = :id AND status = 'AUTO_ACCEPTED' AND report_type = 'NOSHOW' AND created_at > :createdAfter`.
- `applyDecision`: `UPDATE report SET status = :decision, updated_at = :now WHERE id = :id AND status = 'DISPUTED' RETURNING reported_id, reporter_id, report_type, reliability_penalty`.
- `resolveIfPending`: `UPDATE dispute SET status = 'RESOLVED', decision = :decision, resolution_note = :note, resolved_by_user_id = :adminId, resolved_at = :now, updated_at = :now WHERE id = :id AND status = 'PENDING' RETURNING report_id`.
- `findTeamReports`: `direction = RECEIVED` → `WHERE r.reported_type = 'TEAM' AND r.reported_id = :teamId`; `SENT` → `reporter_*`; `JOIN match m`, `JOIN team ct ON ct.id = <phía còn lại>`, `LEFT JOIN dispute d ON d.report_id = r.id` → `ORDER BY r.created_at DESC, r.id DESC LIMIT/OFFSET`. Hai câu cố định theo `direction` (không nối chuỗi cột).
- `findAdminDisputes`: `dispute d JOIN report r JOIN match m JOIN team rt (reporter) JOIN team dt (reported) JOIN "user" ru ON ru.id = r.created_by_user_id JOIN "user" du ON du.id = d.created_by_user_id LEFT JOIN "user" au ON au.id = d.resolved_by_user_id` → tên thật + `(x.deleted_at IS NOT NULL)` mỗi người (ADR-0002 Sửa đổi 2026-11-12 — **không** gọi `DeletedUserMasking`). `PENDING` → `ORDER BY d.created_at ASC, d.id ASC`; khác → `DESC`. `ALL` bỏ điều kiện `status`.
- `findAdminDisputeDetail`: như trên + `s.name` (sport), `v.name` (`LEFT JOIN venue v ON v.id = m.venue_id`), `r.reason`, `r.reliability_penalty`, `d.evidence`, `d.resolution_note`, `rt.reliability_score`, `dt.reliability_score`, `m.status`, `m.match_start_time`, `m.match_end_time`.

### 5.3. Projection & SQL dùng chung

| Projection | Nội dung |
|---|---|
| `RatingContextView` | `matchId`, `status`, `authorTeamId`, `opponentTeamId`, `authorTeamStatus`, `opponentTeamStatus` (JOIN `team`, cùng câu — BR-R10), `callerAuthorCaptain`, `callerOpponentCaptain`, `authorRated`, `opponentRated` (EXISTS rating `rater_type = 'TEAM'`) |
| `ReportContextView` | `matchId`, `status`, `authorTeamId`, `opponentTeamId`, `authorTeamStatus`, `opponentTeamStatus` (như trên), `matchStartTime`, `callerAuthorCaptain`, `callerOpponentCaptain`, `authorReportedType`, `opponentReportedType` (EXISTS report cùng `:reportType` theo từng phía) |
| `MatchRatingRowView` | `authorTeamId`, `opponentTeamId` + cột rating (nullable) |
| `TeamReportView` | cột report + `matchStartTime` + đội còn lại (`id`, `name`, `avatar_url`) + cột dispute (nullable) |
| `DisputeContextView` | `reportId`, `reportType`, `status`, `createdAt`, `reportedTeamId`, `reportedTeamStatus`, `callerReportedCaptain`, `disputeExists` |
| `ReportDecisionView` | `reportedTeamId`, `reporterTeamId`, `reportType`, `reliabilityPenalty` |
| `AdminDisputeView` / `AdminDisputeDetailView` | cột §5.2 |
| `TeamReputationView` | `teamId`, `reliabilityScore`, `verifiedAt`, `completedMatches`, `countedMatches`, `averageRating` (numeric, chưa làm tròn), `ratingCount` |
| `ScoreChangeView` | `teamId`, `oldScore`, `newScore` |
| `VerificationChangeView` | `teamId`, `verified` |

**Thống kê đội** — một hằng chuỗi SQL `TeamReputationSql.STATS` (class `final` trong `domain/repository`), ghép vào `findReputation` và `refreshVerification` (nguồn duy nhất cho công thức `API_Spec.md` §1.4, §1.5):
```sql
-- :teamId; m_t = mọi trận của đội (2 nhánh UNION ALL để dùng 2 index; author ≠ opponent luôn đúng)
WITH m_t AS (
  SELECT m.id, m.status, m.match_start_time, m.cancelled_at, m.cancelled_by_team_id, m.confirmed_opponent_team_id FROM match m WHERE m.author_team_id = :teamId
  UNION ALL
  SELECT m.id, m.status, m.match_start_time, m.cancelled_at, m.cancelled_by_team_id, m.confirmed_opponent_team_id FROM match m WHERE m.confirmed_opponent_team_id = :teamId),
f AS (
  SELECT m_t.*, EXISTS (SELECT 1 FROM report r WHERE r.match_id = m_t.id AND r.reported_type = 'TEAM' AND r.reported_id = :teamId
                                               AND r.report_type = 'NOSHOW' AND r.status <> 'REJECTED') AS noshow
  FROM m_t)
SELECT COUNT(*) FILTER (WHERE status = 'COMPLETED' AND NOT noshow) AS completed_matches,
       COUNT(*) FILTER (WHERE status = 'COMPLETED'
                        OR (status = 'CANCELLED' AND confirmed_opponent_team_id IS NOT NULL AND cancelled_by_team_id = :teamId  -- chỉ đội huỷ
                            AND cancelled_at > match_start_time - make_interval(hours => :cancelWindowHours))
                        OR (status = 'ARCHIVED' AND noshow)) AS counted_matches
FROM f
-- sao: SELECT AVG(score), COUNT(score) FROM rating WHERE ratee_type = 'TEAM' AND ratee_id = :teamId AND score IS NOT NULL
```
`findReputation` = `team` + hai phần trên dạng subquery vô hướng (1 câu). `refreshVerification` dùng `CROSS JOIN LATERAL` cho từng `team.id = ANY(:ids)`:
```sql
UPDATE team t SET verified_at = CASE WHEN e.eligible THEN :now ELSE NULL END
FROM (SELECT t2.id,
             EXISTS (SELECT 1 FROM team_member cm JOIN "user" u ON u.id = cm.user_id
                     WHERE cm.team_id = t2.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
                       AND u.phone_number IS NOT NULL AND u.deleted_at IS NULL)
             AND s.completed_matches > :minCompleted AND s.avg_rating > :minAvg AND t2.reliability_score >= :minScore AS eligible
      FROM team t2 CROSS JOIN LATERAL (<STATS + sao cho t2.id>) s WHERE t2.id = ANY(CAST(:ids AS uuid[]))) e
WHERE t.id = e.id AND (t.verified_at IS NOT NULL) IS DISTINCT FROM e.eligible
RETURNING t.id, (t.verified_at IS NOT NULL) AS verified
```
`avg_rating` NULL (chưa có sao) → `eligible` NULL → `COALESCE(…, false)`. Đội `DISBANDED` không còn captain ACTIVE → không đủ điều kiện → thu hồi ở lần refresh kế tiếp.

## 6. Entity

| Entity | Thay đổi |
|---|---|
| `rating.Rating` | `score`: `BigDecimal` → `Integer` (nullable, `SMALLINT`) · + `isNoShow` (`boolean`, `is_no_show`, `@Builder.Default false`) · `matchScoreHome/Away` → `raterTeamScore`, `rateeTeamScore` · + `createdByUserId` (`UUID`, `nullable = false`) · mọi cột `updatable = false` (đánh giá không sửa) |
| `rating.Report` | `Status` → `{AUTO_ACCEPTED, DISPUTED, CONFIRMED, REJECTED}`, `@Builder.Default AUTO_ACCEPTED`, `status` `updatable = false` (chỉ đổi bằng CAS native — ADR-0003 Sửa đổi 2026-11-12) · bỏ `adminReviewedAt` · `reason` `nullable = false` · + `reliabilityPenalty` (`Integer`, `@Builder.Default 0`; ghi một lần bằng dirty checking trong 2.3) · + `createdByUserId` · + `updatedAt` (`@PrePersist`/`@PreUpdate`, `@Setter(NONE)`) |
| `rating.Dispute` (mới) | Map V8; `@UuidGenerator(VERSION_7)` (ADR-0008); `status` (`enum {PENDING, RESOLVED}`), `decision` (`enum {CONFIRMED, REJECTED}`), `resolutionNote`, `resolvedByUserId`, `resolvedAt` đều `updatable = false` (chỉ `resolveIfPending`); `createdAt`/`updatedAt` như `Report` |
| `team.Team` | Bỏ `totalMatchesCompleted`, `totalMatchesConfirmed`, `averageRating` · `cleanStreak`, `reportCount*` thêm `updatable = false` (ADR-0013 §4) · + `verifiedAt` (`insertable = false, updatable = false` — chỉ `refreshVerification`) |
| `user.User` | Bỏ `totalMatchesCompleted`, `totalMatchesConfirmed`, `averageRating` · `cleanStreak`, `reportCount*` thêm `updatable = false` |

Kiểm bằng migration integration test (`ddl-auto=validate` sau V8) + test entity: sửa `Team`/`User` qua dirty checking không ghi đè `clean_streak`, `report_count_*`, `verified_at`.

## 7. DTO & Mapper

- DTO là `record`; compact constructor `trim()` chuỗi, `""` ở field tuỳ chọn → `null` trước Bean Validation (như MATCH-001 §7).
- `CreateRatingRequest(Boolean isNoShow, Integer score, @Size(max = 500) String comment, @Min(0) @Max(999) Integer raterTeamScore, @Min(0) @Max(999) Integer rateeTeamScore)` + `@ValidRating` (mức lớp): `isNoShow` vắng → `false`; `!isNoShow` → `score` 1..5 bắt buộc; `isNoShow` → `score`, hai tỷ số phải `null`; hai tỷ số cùng có hoặc cùng vắng. Lỗi gắn property node theo tên field (`score`, `raterTeamScore`…) như `@ValidMatchSchedule`.
- `CreateReportRequest(@NotNull @OneOf({"NOSHOW","BAD_PLAY","BAD_COMMUNICATION","SKILL_MISMATCH"}) String reportType, @NotBlank @Size(max = 1000) String reason)`.
- `CreateDisputeRequest(@NotBlank @Size(max = 1000) String evidence)`; `ResolveDisputeRequest(@NotNull @OneOf({"CONFIRMED","REJECTED"}) String decision, @NotBlank @Size(max = 500) String note)`.
- `ReputationMapper` (`componentModel = "spring"`, `uses MapperSupport`, `unmappedTargetPolicy = ERROR`): `toRating(Rating|MatchRatingRowView)`, `toCreated(Rating, Match.Status)`, `toReport(TeamReportView, ReportDirection, OffsetDateTime now)` (status hiệu lực §4.10), `toCreatedReport(Report, ReportContextView…)`, `toDisputeCreated(Dispute)`, `toAdminDispute(AdminDisputeView)`, `toAdminDetail(AdminDisputeDetailView, List<Rating>)`, `toReputation(TeamReputationView)`. Tên user Admin không qua `DeletedUserMasking`.
- `averageRating`, `completionRate`: `BigDecimal.setScale(…, HALF_UP)` ở mapper.

## 8. Cấu hình & Dependencies

```properties
arenaflow.reputation.verification.cron=${REPUTATION_VERIFICATION_CRON:0 0 3 * * *}
arenaflow.reputation.verification.batch-size=500
```

| Tham số | Lý do |
|---|---|
| `cron` 03:00 hằng ngày (giờ VN) | Q6-B đối soát hằng ngày; giờ thấp tải; `-` = tắt trong test |
| `batch-size` 500 | Transaction ngắn; mỗi lô 2 câu |
| `spring.task.scheduling.pool.size` 3 → **4** (giá trị ở MATCH-001 `Component_Class_Design.md` §8 — sửa ở MATCH-001 v2.2) | Thêm 1 job định kỳ; mỗi job một luồng |

Không thêm Maven dependency.

## 9. Security

- Không endpoint public mới; không đổi `SecurityConfig`.
- Quyền đội trưởng kiểm trong service từ `…ContextView`/`TeamGuard` (USER-001 §4). Admin: `/api/v1/admin/**` + audit log sau commit (`RESOLVE_DISPUTE`).
- Tên người dùng chỉ xuất hiện ở API Admin (tên thật + cờ — ADR-0002 Sửa đổi 2026-11-12). Endpoint thường không trả tên user → không cần che. Log chỉ id (không `reason`, `evidence`, `comment`).
- Chống lạm dụng BR-M18 (MATCH-001 §4.8 "Lưu ý cho REPUTATION-001"): Q12 chặn đánh giá/report khi người gọi là đội trưởng cả hai đội → trận giữa hai đội cùng đội trưởng không thể `COMPLETED` (không cộng +2/Completion Rate).

## 10. Lệch convention / chuẩn có chủ đích

| Chuẩn | Lệch | Lý do | Trạng thái |
|---|---|---|---|
| ADR-0003 (`@Version` + retrier) | `report`, `dispute` không `@Version`; đổi trạng thái bằng `UPDATE … WHERE status` | Thoả 3 điều kiện ngoại lệ ADR-0003 Sửa đổi 2026-11-10 | ADR-0003 Sửa đổi 2026-11-12 |
| ADR-0011 quy tắc 4 (listener `@TransactionalEventListener`) | Huy hiệu sau thao tác của chính mã này dùng `TransactionSynchronization.afterCommit` thay vì nghe event của mình | Quy tắc 5 (miền phát không nghe event của mình); gom id → 1 câu/request | ADR-0011 quy tắc 8 |
| ADR-0012 (job chuyển trạng thái claim lô `SKIP LOCKED`) | Job huy hiệu `reconcileAll` không claim, lặp lô theo `id` | Không chuyển trạng thái theo hàng đợi; câu refresh có điều kiện "trạng thái đổi" → hai instance chạy trùng không phát event trùng (§4.8) | Ghi trong thiết kế |
| NFS §3 W6 | 2.8 `REJECTED` = 6 (= ngưỡng) | Response B4 cần đọc lại chi tiết (2 câu) | Đạt ngưỡng — mọi truy vấn thêm phải gộp |
| Mã lỗi theo miền | Thử lại hết lượt dùng `MATCH_014` cho cả 2.1, 2.3, 2.5, 2.8 | Message chung, FE xử lý giống nhau; tránh thêm mã cùng nghĩa | Ghi ở `API_Spec.md` §1.1 |

## 11. Tài liệu cần đồng bộ khi implement

Sau khi mọi phase code xong + test PASS (CLAUDE.md §8):

| Tài liệu | Nội dung | Nhánh |
|---|---|---|
| `docs/database/schema.dbml`, `schema-design.md` (`rating`, `report`, `team`, `user`) | V8: cột/CHECK/index/FK mới, bảng `dispute`, bỏ `total_matches_*`, `average_rating`, `admin_reviewed_at` | `feature/REPUTATION-001-*` |
| `docs/agent/api-convention.md` §5, §6 | 9 endpoint vào bảng 2xx (§3); domain `RATING`, `REPORT`, `DISPUTE` | `chore/agent-framework-*` |
| `docs/agent/convention-backend.md` §1 | `support/reputation/` | `chore/agent-framework-*` |
| `docs/agent/CLAUDE.md` §2 | Dòng điều hướng REPUTATION-001 | `chore/agent-framework-*` |
| SRS `SYSTEM-001` v1.8 | Đã cập nhật cùng nhánh thiết kế (BR-R07..R10, AC-R07, §8.2) | `feature/REPUTATION-001-*` |

## 12. Danh sách [cần xác nhận]

Đánh số toàn thiết kế: 1–4 và 13 đã đóng (§13), 5–11 ở đây, 12 ở `Processing_Flow.md` §9.

| # | Nội dung | Loại |
|---|---|---|
| 5 | Native `UPDATE … RETURNING` không `@Modifying` trả projection — cùng MATCH-002 `[cần xác nhận #4]` (chặn code bước đầu phase 1 của MATCH-002). Không chạy được → `@Modifying` + đọc lại: 2.1 hoàn tất 5 → 6 (= W6), 2.8 `REJECTED` 6 → 7 (**vượt** W6 → dừng, hỏi user) | **Chặn code** nếu MATCH-002 chưa kiểm |
| 6 | CTE `… FOR NO KEY UPDATE` + `UPDATE … FROM` trả `old_score` là điểm sau khi chờ khoá (không phải snapshot đầu câu) [Inference — chưa chạy] | Không chặn — integration test đầu tiên của `applyReportPenalty` với hai giao dịch đồng thời |
| 7 | Bind mảng `uuid[]`/`boolean[]`/`int[]` vào native query (cùng MATCH-001 `[cần xác nhận #1]`, MATCH-002 `[cần xác nhận #2]`) | Không chặn |
| 8 | **Thứ tự implement:** V8 cần V5–V7 (VENUE-001, MATCH-001, MATCH-002) đã code — MATCH-001 `[cần xác nhận #5]` | **Chặn code** — implement sau ba mã kia |
| 9 | Mở transaction mới (`REQUIRES_NEW`) trong `afterCommit` (Spring: tài nguyên transaction cũ còn bind lúc `afterCommit`) [Inference — theo Javadoc `TransactionSynchronization`] | Không chặn — test tích hợp refresher |
| 10 | Kiểm `spring.task.scheduling.pool.size` 4 đủ (MATCH-001 `[cần xác nhận #10]`) | Không chặn |
| 11 | Thông báo "rating nhận" (Business_Rules §8.6) mâu thuẫn BC-R04 (không tiết lộ đối phương đã đánh giá) — NOTIFICATION-001 chốt nội dung; REPUTATION-001 chỉ phát `MatchRatedEvent` | Không chặn — chuyển NOTIFICATION-001 |

## 13. Quyết định đã chốt (user, 2026-11-12)

| # cũ | Quyết định | Nơi áp dụng |
|---|---|---|
| 13 (A1) | Không nghe `MatchCancelledEvent`; huy hiệu sau huỷ kèo / rút yêu cầu đã chấp nhận chỉ cập nhật ở job hằng ngày (trễ tối đa ~1 ngày); MATCH-001 2.10 giữ 6/W6 | §4.8; `API_Spec.md` §1.5 |
| 1 (B) | Đánh giá "không đến": chuỗi trận sạch đội vắng giữ nguyên | `applyMatchCompletion` không có entry đội vắng (§4.1); `API_Spec.md` §1.3 |
| 2 (C) | Trận bị đánh giá "không đến" mà không bị report bùng vẫn vào tử số của đội vắng | §5.3 (tử số chỉ xét report bùng); `API_Spec.md` §1.4 |
| 3 (D) | Kèo huỷ dưới 24 giờ trước trận chỉ vào mẫu số của **đội huỷ** (`cancelled_by_team_id = :teamId`) — đổi so với câu chữ SRS cũ | §5.3; `API_Spec.md` §1.4 |
| 4 (E) | Admin bác report không khôi phục chuỗi trận sạch | §4.6 `refundReportPenalty`; `API_Spec.md` §1.3 |
| F | `reason`/`evidence` ≤ 1000, `note` ≤ 500, `comment` ≤ 500 | §5.1, §7; `API_Spec.md` 2.1, 2.3, 2.5, 2.8 |
