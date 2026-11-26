# Component & Class Design — Đánh giá, report, tranh chấp & uy tín đội

| Mục | Giá trị |
|---|---|
| Mã chức năng | `REPUTATION-001` |
| Phạm vi tầng | Backend (Spring Boot REST API). Frontend: ngoài phạm vi repo này |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · Spring Data JPA · Flyway · PostgreSQL ≥ 18 |
| Quy ước áp dụng | `docs/agent/convention-backend.md`, `docs/agent/security-backend.md`, `docs/agent/non-functional-standards.md` · kế thừa USER-001 `Component_Class_Design.md` §4.6, §5, §10; VENUE-001 `Component_Class_Design.md` §4.9 (retrier overload), §4.13 (audit); MATCH-001 `Component_Class_Design.md` §4.5 (`TeamGuard`), §4.8 (`ReputationService` — sửa ở đây §4.6), §4.9 (event), §4.10 (scheduler), §4.11 (`AdminAuditLog.afterCommit(action, adminId, targetType, targetId)`), §8 (pool) · ADR-0002 (Sửa đổi 2026-11-12), ADR-0003 (Sửa đổi 2026-11-12, 2026-11-27), ADR-0008, ADR-0011, ADR-0012, ADR-0013 (Sửa đổi 2026-11-12, 2026-11-27) |
| Ràng buộc dữ liệu | V1–V8 (V5 = VENUE-001, V6 = SYSTEM-002, V7 = MATCH-001, V8 = MATCH-002 — đã có trên `main`) + `V9__reputation_rating_report_dispute.sql` (§5.1). v1.2 (2.10) đọc `conversation`, `conversation_message` của V7 (MATCH-001 v2.4 §5.1) — không đổi V9. Entity hiện có `domain/.../entity/rating/{Rating, Report}`, `team/Team`, `user/User` |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-13 · v1.0 (baseline) · hiện tại **v2.1** (developer · 2026-11-27) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan kiến trúc

Layered theo 4 module Maven (ADR-0006) như USER-001. Hợp đồng: `API_Spec.md`. Luồng, transaction, số truy vấn, khoá: `Processing_Flow.md`.

- **Ghi uy tín cùng transaction** với thao tác gây ra, chỉ qua `ReputationService` (ADR-0013): mở rộng lõi MATCH-001 bằng 3 method (§4.6), mỗi method **một** câu `UPDATE team` nguyên tử (điểm, chuỗi trận sạch, bộ đếm report); riêng `applyMatchCompletion` khoá các đội bằng một câu riêng trước câu `UPDATE` (v2.1, §4.6). Không listener nào ghi điểm/bộ đếm (ADR-0011 quy tắc 6).
- **Completion Rate, sao trung bình tính khi đọc** (Q5, ADR-0013 Sửa đổi 2026-11-12) từ `match`, `report`, `rating` — một đoạn SQL dùng chung (§5.3) cho 2.9 và huy hiệu. V9 xoá `total_matches_*`, `average_rating` của `team` và `"user"`.
- **Huy hiệu xác minh là dữ liệu dẫn xuất** (Q6-B): cột `team.verified_at`, chỉ ghi bằng một câu `UPDATE … RETURNING` có điều kiện (§4.8), chạy **sau commit** (đăng ký `afterCommit` từ service của mã này) + job đối soát hằng ngày cho mọi thay đổi khác (huỷ kèo, đổi đội trưởng, số điện thoại).
- **Report / tranh chấp đổi trạng thái bằng CAS tự viết** `UPDATE … WHERE status = …` (ADR-0003 Sửa đổi 2026-11-12); entity chỉ INSERT + đọc.
- **Trận `ARCHIVED → COMPLETED`** bằng câu có điều kiện trên `match` (`WHERE status = 'ARCHIVED'`, `version + 1`) — cùng mẫu `expireIfDue` của MATCH-001; 0 dòng = đội kia đã hoàn tất trước, không phải xung đột.
- Phạm vi chỉ **đội** (rater/ratee/reporter/reported type `TEAM`). Cá nhân (`INDIVIDUAL`) — MERCENARY-001 dùng lại bảng, `ReputationService` và mẫu này.

**Điều kiện tiên quyết** (v2.1: đã đủ — MATCH-001, MATCH-002 có trên `main` `04b6136`; V9 đã áp DB dev): code MATCH-001 (`ReputationService`, `TeamGuard.requireActiveCaptain`, `MatchCancellation`, `ClockConfig`, `SchedulingConfig`, `MATCH_001/009/014`), MATCH-002 (MATCH-002 `[cần xác nhận #4]` `UPDATE … RETURNING` không `@Modifying`; MATCH-002 `[cần xác nhận #5]` retrier bắt `ConcurrencyConflictException` — 2.5 cần khi CAS 0 dòng, `Processing_Flow.md` §3.5), VENUE-001 (`AdminAuditLog`, retrier overload) và migration V5–V8 có trước V9 (`[cần xác nhận #8]` đã đóng).

## 2. Package Structure (delta)

```text
common/        com.phananh.arenaflow.common
└── exception/       ErrorCode (+ RATING_001..003, REPORT_001..008, DISPUTE_001..002; v1.1 REPORT_009; v1.2 REPORT_010, REPORT_011)

domain/        com.phananh.arenaflow.domain
├── entity/rating/   Rating, Report (sửa — §6); Dispute (mới)
├── entity/team/     Team (sửa — §6)
├── entity/user/     User (sửa — §6)
├── repository/      RatingRepository, ReportRepository, DisputeRepository (mới); TeamRepository, MatchRepository (sửa)
└── projection/      RatingContextView, ReportContextView, MatchRatingRowView, TeamReportView, DisputeContextView,
                     ReportDecisionView, AdminDisputeView, AdminDisputeDetailView, TeamReputationView,
                     ScoreChangeView, VerificationChangeView (mới); ChatReportContextView (mới — v1.2);
                     MatchSidesView (mới — v2.1, cha của RatingContextView, ReportContextView)

application/   com.phananh.arenaflow.application
├── service/         RatingService, ReportService, DisputeService, AdminDisputeService, TeamReputationService,
│   │                TeamVerificationJobService (mới); ReputationService, ReliabilityChange, ReliabilityReason (sửa);
│   │                CompletionEntry, ReportDirection, DisputeStatusFilter (mới)
│   └── impl/        …ServiceImpl tương ứng; ReputationServiceImpl (sửa)
├── event/reputation/ MatchRatedEvent, TeamReportedEvent, ReportDisputedEvent, DisputeResolvedEvent,
│                    TeamVerificationChangedEvent (mới); ReliabilityScoreChangedEvent (giữ — MATCH-001)
├── support/reputation/ ReportPenaltyPolicy, ReputationRules, TeamVerificationRefresher (mới); ReportStatusResolver (§4.10),
│                    MatchSides (§4.9), TeamVerificationProperties (§8) (mới — v2.1); TimeBandPenaltyPolicy (giữ)
├── validation/      ValidRating (+ validator) (mới); OneOf (tái dùng)
├── dto/request/     CreateRatingRequest, CreateReportRequest, CreateDisputeRequest, ResolveDisputeRequest; CreateChatReportRequest (v1.2)
├── dto/response/    RatingResponse, RatingCreatedResponse, MatchRatingsResponse, TeamBriefResponse, ReportResponse,
│                    ReportDisputeResponse, DisputeCreatedResponse, AdminTeamBriefResponse, AdminDisputeResponse,
│                    AdminDisputeDetailResponse, DisputeMatchResponse, TeamReputationResponse (+ CompletionResponse,
│                    RatingSummaryResponse, VerificationResponse)
└── mapper/          ReputationMapper (MapStruct); MapperSupport (tái dùng)

api/           com.phananh.arenaflow.api
├── reputation/      MatchReputationController, ReportController, TeamReputationController, AdminDisputeController,
│                    ConversationReportController (v1.2)
└── scheduler/       TeamVerificationScheduler (mới)
```

Thành phần dùng chung mới (`TeamVerificationRefresher`, `ReportPenaltyPolicy`, `ReputationRules`, `ReportStatusResolver`, `MatchSides`, đoạn SQL thống kê §5.3) → developer MUST chạy `reuse-scan` trước khi tạo.

## 3. Controller Layer

Quy ước như MATCH-001 §3 (`@Validated`, `ResponseEntity<ApiResponse<…>>`, `@CurrentUser UUID currentUserId`, 201 không `Location`).

| Controller | `@RequestMapping` | Endpoint | Service |
|---|---|---|---|
| `MatchReputationController` | `/api/v1/matches/{matchId}` (`/ratings`, `/reports`) | 2.1, 2.2, 2.3 | `RatingService`, `ReportService` |
| `ReportController` | `/api/v1/reports` | 2.5 | `DisputeService` |
| `TeamReputationController` | `/api/v1/teams/{teamId}` (`/reports`, `/reputation`) | 2.4, 2.9 | `ReportService`, `TeamReputationService` |
| `AdminDisputeController` | `/api/v1/admin/disputes` | 2.6–2.8 | `AdminDisputeService` |
| `ConversationReportController` (v1.2) | `/api/v1/conversations/{conversationId}/reports` | 2.10 | `ReportService` |

| Endpoint | Thành công | Tiêu chí `api-convention.md` §5 |
|---|---|---|
| 2.1, 2.3, 2.5, 2.10 | 201 | B2 |
| 2.2, 2.4, 2.6, 2.7, 2.9 | 200 | B1 |
| 2.8 | 200 | B3 (`resolvedAt`, `resolvedByName`, điểm sau hoàn), B4 |

**Path/method:** 2.1, 2.3 tạo tài nguyên con dưới trận (tiền lệ `POST /matches/{id}/requests`). 2.5 tạo tranh chấp 1:1 dưới report → danh từ số ít `/dispute`, `POST` 201. 2.8 action sub-resource Admin, `PATCH` theo tiền lệ VENUE-001 `PATCH /admin/venue-reports/{id}/resolve`. 2.9 tách khỏi `GET /teams/{id}` (Q8 — không đổi hợp đồng USER-001). 2.10 tạo report dưới **cuộc trò chuyện** (tiền lệ MERCENARY-001 2.20 report theo đơn): đội trưởng đội tạo kèo luồng A có nhiều cuộc trò chuyện (X, Y, Z) trên cùng kèo → `POST /matches/{id}/reports` không xác định được đội bị report; cuộc trò chuyện xác định đúng cặp đội. Không mở rộng 2.3 (giữ hợp đồng, 2.3 chỉ cho hai đội của trận).

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
ReportResponse createChatReport(UUID conversationId, UUID callerId, CreateChatReportRequest r);       // retrier MATCH_014 — v1.2, 2.10
PageResponse<ReportResponse> listTeamReports(UUID teamId, UUID callerId, ReportDirection d, int page, int pageSize); // readOnly
```
Deps: `MatchRepository` (`findReportContext`), `ReportRepository`, `TeamGuard`, `ReputationService`, `TeamVerificationRefresher`, `ConcurrencyConflictRetrier`, `ReputationMapper`, `PageRequestFactory`, `ApplicationEventPublisher`, `Clock`.
- `createReport`: guard (`Processing_Flow.md` §1.1) → `Report.builder()` (`status = AUTO_ACCEPTED`, `reliabilityPenalty = 0`, `createdByUserId`) `saveAndFlush` (bắt `idx_report_unique` → `REPORT_004`) → `applied = reputationService.applyReportPenalty(reportedTeamId, type, reportId)` → `report.setReliabilityPenalty(applied)` (dirty checking, ghi lúc commit) → `TeamReportedEvent` → `refreshAfterCommit({reported})`.
- `createChatReport` (v1.2, BR-R07 trường hợp 3): `reportRepository.findChatReportContext(conversationId, callerId, now)` (§5.2 — cột ngữ cảnh chung `ConversationSql.CONTEXT_COLUMNS` của MATCH-001 + `hasMessages` + đã report; `now` từ `Clock` vì khối cột chung dùng `:now` — v2.1) → `ConversationAccessPolicy` của loại ngữ cảnh (MATCH-001 §4.14, trong bộ nhớ; loại ngữ cảnh không có policy → `IllegalStateException` — lỗi lập trình, mỗi loại đúng một policy; loại khác `TEAM_MATCH` **có** policy → theo thứ tự guard dưới, tới `REPORT_011`) → guard (`Processing_Flow.md` §1.1: `CHAT_002` → `USER_001` → `CHAT_003` (không bên nào) → `REPORT_011` (`contextType ≠ TEAM_MATCH`) → `TEAM_016` (bên chỉ xem = người đã giải tán đội) → `TEAM_015` → `REPORT_005` → `REPORT_010` → `REPORT_004`) → đội report = bên mà caller là đội trưởng ACTIVE (A = đội tạo kèo, B = đội tham gia), đội bị report = bên còn lại → **cùng đường ghi với `createReport`** (helper chung khi reuse-scan): `Report.builder()` (`matchId = ctx.matchId`, `reporterType/reportedType = TEAM`, `reportType = BAD_COMMUNICATION`, `status = AUTO_ACCEPTED`, `reliabilityPenalty = 0`, `createdByUserId`) `saveAndFlush` (bắt `idx_report_unique` → `REPORT_004` — chung khoá với 2.3: một "giao tiếp tệ" mỗi chiều mỗi kèo) → `applyReportPenalty(reported, BAD_COMMUNICATION, reportId)` → `setReliabilityPenalty` → `TeamReportedEvent` → `refreshAfterCommit({reported})`. Không đụng trạng thái kèo / yêu cầu / pairing / cuộc trò chuyện. Deps thêm: `ReportRepository.findChatReportContext` (không cần `ConversationRepository`).
- `listTeamReports`: `requireActiveCaptain` (BR-R10) → `RECEIVED`: `findReceivedReports`/`countReceivedReports`; `SENT`: `findSentReports`/`countSentReports` (§5.2). Mapper tính `status` hiệu lực, `disputeDeadlineAt`, `canDispute` (§4.10); `SENT` → `reliabilityPenalty = null`, `dispute.evidence = null`.

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
`TeamRepository.findReputation(teamId, LATE_CANCEL_WINDOW_HOURS)` (§5.3) → rỗng → `TEAM_001` → mapper: `reliabilityLevel` theo `ReputationRules`, `completionRate = countedMatches < NEWCOMER_THRESHOLD ? null : round(100 × completed / counted)` (HALF_UP), `averageRating` HALF_UP 1 chữ số.

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
- `applyMatchCompletion`: `TeamRepository.lockForReputation(ids)` (khoá mọi đội của entry theo id tăng dần — v2.1) → một câu `TeamRepository.applyCompletion` cho mọi entry (§5.2) → `ScoreChangeView` mỗi đội → event mỗi đội có `newScore ≠ oldScore` (`reason = MATCH_COMPLETED`, `delta = newScore − oldScore`, `referenceId = matchId`). Số dòng ≠ số entry → `IllegalStateException`.
  - Vì sao khoá câu riêng (v2.1): dưới READ COMMITTED, `NOT EXISTS report` trong `applyCompletion` đọc ảnh chụp **đầu câu**; khoá trong CTE của chính câu đó không làm subquery thấy report vừa commit trong lúc chờ (race test tái hiện: đội bị report bùng vẫn được +1 chuỗi). Giữ khoá ở câu trước → câu `applyCompletion` lấy ảnh chụp **sau** khi có khoá → thấy mọi report đã commit của các đội đó (`Processing_Flow.md` §5; ADR-0013 Sửa đổi 2026-11-27; lệch: §10).
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

**`ReputationRules`** (hằng một nơi): `COMPLETION_POINTS = 2`, `STREAK_MILESTONE = 5`, `STREAK_BONUS_5 = 5`, `STREAK_BONUS_10 = 10`, `FIVE_STAR_BONUS = 1`, `DISPUTE_WINDOW = 24h`, `NEWCOMER_THRESHOLD = 5`, `BADGE_MIN_COMPLETED_EXCLUSIVE = 5`, `BADGE_MIN_AVG_EXCLUSIVE = 3.0`, `BADGE_MIN_SCORE = 70`, `LATE_CANCEL_WINDOW_HOURS = 24` (cửa sổ "huỷ dưới 24 giờ trước trận" của mẫu số Completion Rate — tham số `:cancelWindowHours` của STATS §5.3, v2.1), ngưỡng `reliabilityLevel` (95/70/50). Hằng được truyền vào SQL bằng tham số, không viết literal trong câu.

### 4.7. Domain event (`application/event/reputation`, ADR-0011)

| Event | Payload | Phát khi | Người nghe dự kiến |
|---|---|---|---|
| `MatchRatedEvent` | `ratingId, matchId, raterTeamId, rateeTeamId, isNoShow, matchCompleted, occurredAt` | 2.1 | NOTIFICATION-001 (v1.3, user chốt Q3 2026-11-16): `RATING_RECEIVED` cho đội `ratee` **chỉ khi** đội đó đã đánh giá trận trước (listener tự kiểm) — không lộ BC-R04; `matchCompleted` **không** tạo thông báo (nhắc đánh giá gửi lúc `ARCHIVED` — MATCH-001 `MatchArchivedEvent`). Loại thông báo của mọi event dưới: NOTIFICATION-001 `Component_Class_Design.md` §4.3 |
| `TeamReportedEvent` | `reportId, matchId, reporterTeamId, reportedTeamId, reportType, occurredAt` | 2.3 | NOTIFICATION-001 ("report nhận"; `NOSHOW`: "Bạn có 24h để tranh chấp") |
| `ReportDisputedEvent` | `disputeId, reportId, reporterTeamId, reportedTeamId, occurredAt` | 2.5 | NOTIFICATION-001 (`REPORT_DISPUTED` cho bên đã report + `DISPUTE_CREATED` cho mọi Admin — user chốt Q1, Q8 2026-11-16) |
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
- `refreshNow`: `TransactionTemplate` `PROPAGATION_REQUIRES_NEW` (bắt buộc trong `afterCommit` — `[cần xác nhận #9]`) → `TeamRepository.refreshVerification(ids, now, …ngưỡng, LATE_CANCEL_WINDOW_HOURS)` (§5.3) trả các đội **đổi** trạng thái → `TeamVerificationChangedEvent` mỗi đội. Lỗi → log `WARN` (chỉ id), không ném — job hằng ngày sửa lại.
- **Không** dùng `@TransactionalEventListener` cho event của chính REPUTATION-001 (ADR-0011 quy tắc 5 — miền phát không nghe event của mình). **Không nghe `MatchCancelledEvent`** (user chốt A1, 2026-11-12): giữ MATCH-001 2.10 rút yêu cầu `ACCEPTED` ở 6/W6; đội bị trừ điểm do huỷ / rút được cập nhật huy hiệu ở job hằng ngày (trễ tối đa ~1 ngày).
- **Không tự cập nhật ngay** khi huỷ kèo đã ghép / rút yêu cầu đã chấp nhận (MATCH-001), đổi đội trưởng (USER-001 transfer), đổi số điện thoại, xoá tài khoản: chờ job hằng ngày (Q6-B, A1) — ngoại lệ "thu hồi ngay" đã ghi ở SRS `Business_Rules.md` §8.2 (v1.8).

**`TeamVerificationJobService.reconcileAll()`** (+ `TeamVerificationScheduler` ở `api/scheduler`, `@Scheduled(cron = "${arenaflow.reputation.verification.cron}")` — không khai `zone`, chạy theo timezone JVM (v2.1, `convention-backend.md` §6.2 / ADR-0018)): lặp lô theo khoá `id` tăng dần: `TeamRepository.findIdsForVerification(afterId, batchSize)` (`batchSize` từ `TeamVerificationProperties` — §8) → `refreshVerification(ids, now, …)` (tham số như `refreshNow`) → event; mỗi lô một `TransactionTemplate`. Không theo mẫu claim của ADR-0012 vì không chuyển trạng thái theo hàng đợi: câu refresh tự có điều kiện "trạng thái đổi" → hai instance chạy trùng không phát event trùng (bên sau thấy dòng đã đổi).

### 4.9. Guard trận cho đánh giá / report

`RatingServiceImpl` / `ReportServiceImpl` đọc một `…ContextView` (§5.3) và kiểm theo thứ tự `Processing_Flow.md` §1.1. Phần chung của hai service (v2.1 — reuse-scan thấy lặp): `support/reputation/MatchSides` — `record MatchSides(UUID ownTeamId, UUID otherTeamId, boolean ownIsAuthor)` + `static of(MatchSidesView)` (đội của caller → rater/reporter, đội còn lại → ratee/reported), `isCaptainOfAny`, `isCaptainOfBoth`, `isCallerTeamInactive`; nhận `MatchSidesView` (§5.3) — cha của `RatingContextView`, `ReportContextView`.

### 4.10. Trạng thái report hiệu lực

`support/reputation/ReportStatusResolver` (v2.1 — `ReputationMapper` gọi cho 2.4 và response 2.3/2.10), 3 hàm static `effectiveStatus`, `disputeDeadline`, `canDispute`: `status == AUTO_ACCEPTED && type == NOSHOW && now ≥ createdAt + DISPUTE_WINDOW` → `CONFIRMED`; còn lại giữ nguyên. **Không job** ghi `CONFIRMED` khi hết hạn: không luồng nào đọc `report.status = 'CONFIRMED'` để ra quyết định (Completion Rate dùng `status <> 'REJECTED'`; tranh chấp kiểm hạn trong câu CAS). `canDispute` = `RECEIVED && NOSHOW && status (DB) == AUTO_ACCEPTED && now < deadline`.

### 4.11. Transaction

| Method | Ranh giới |
|---|---|
| `getMatchRatings`, `listTeamReports`, `listDisputes`, `getDispute`, `getTeamReputation` | `@Transactional(readOnly = true)` |
| `createRating`, `createReport`, `createChatReport`, `createDispute`, `resolveDispute` | Không annotate; `concurrencyConflictRetrier.execute(MATCH_014, …)` (thử lại xung đột CAS và deadlock `40P01`) |
| `ReputationService.*` | `MANDATORY` |
| `TeamVerificationRefresher.refreshNow`, mỗi lô `reconcileAll` | `TransactionTemplate` (`REQUIRES_NEW` cho `refreshNow`) |

## 5. Repository & Data Model

### 5.1. Migration `V9__reputation_rating_report_dispute.sql`

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
ALTER TABLE report ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');
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
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    CONSTRAINT dispute_resolution_check CHECK (
        (status = 'PENDING' AND decision IS NULL AND resolution_note IS NULL AND resolved_by_user_id IS NULL AND resolved_at IS NULL)
        OR (status = 'RESOLVED' AND decision IS NOT NULL AND resolution_note IS NOT NULL AND resolved_by_user_id IS NOT NULL AND resolved_at IS NOT NULL))
);
CREATE UNIQUE INDEX idx_dispute_report ON dispute(report_id);
CREATE INDEX idx_dispute_status_created ON dispute(status, created_at, id);

-- team / user: Completion Rate, sao trung bình tính khi đọc (Q5); huy hiệu dẫn xuất (Q6)
ALTER TABLE team DROP COLUMN total_matches_completed, DROP COLUMN total_matches_confirmed, DROP COLUMN average_rating;
ALTER TABLE "user" DROP COLUMN total_matches_completed, DROP COLUMN total_matches_confirmed, DROP COLUMN average_rating;
ALTER TABLE team ADD COLUMN verified_at TIMESTAMP;
```
- **Dữ liệu cũ:** đã kiểm Neon dev `misty-dew-90700890` (branch mặc định) ngày 2026-11-12 — `rating`, `report`, `match` 0 dòng; không đội nào có `total_matches_*`/`average_rating`/`clean_streak` khác mặc định; Flyway ở **V4**. Tên constraint V1 (`rating_score_check`, `rating_match_score_*_check`, `report_status_check`) và `user_check` (`total_matches_completed <= total_matches_confirmed` — tự xoá theo `DROP COLUMN`) đã đối chiếu `pg_constraint` cùng ngày. Môi trường khác có dữ liệu: `NOT NULL` cột mới không mặc định (`created_by_user_id`) làm V9 lỗi → dừng, báo, không tự sửa dữ liệu.
- **Số hiệu V9:** V6 (SYSTEM-002), V7 (MATCH-001), V8 (MATCH-002) đã có trên `main` (`04b6136`). Flyway không chạy out-of-order → V9 áp sau V5–V8; đã áp DB dev 2026-11-26 (v2.1 — `[cần xác nhận #8]` đóng).
- Index: `idx_report_unique` (chống trùng + tra report theo trận — tiền tố `match_id` thay `idx_report_match`); `idx_report_*_created` cho 2.4 (lọc + sắp xếp); `idx_dispute_status_created` cho 2.6 (đọc xuôi `PENDING`, đọc ngược `RESOLVED`); `idx_rating_unique` (V1, tiền tố `match_id`) cho 2.2/2.7; `idx_rating_ratee` (V1) cho sao trung bình; Completion Rate dùng `idx_match_author_status` (V1) + `idx_match_opponent_status` (V7). `idx_report_status` bỏ vì không còn truy vấn lọc theo trạng thái report.
- FK `rating.match_id`, `report.match_id` (V1 thiếu) — `ON DELETE RESTRICT` (ADR-0002). `rating.rater_id`/`ratee_id`, `report.reporter_id`/`reported_id` đa hình (`TEAM`/`INDIVIDUAL`) → không FK.

### 5.2. Repository

```java
// RatingRepository extends JpaRepository<Rating, UUID>
List<MatchRatingRowView> findMatchRatingRows(UUID matchId);   // match LEFT JOIN rating (rater_type = 'TEAM'); rỗng = không có trận
List<Rating> findByMatchIdAndRaterTypeOrderByCreatedAtAsc(UUID matchId, Rating.RaterType t); // 2.7

// ReportRepository extends JpaRepository<Report, UUID>
List<TeamReportView> findReceivedReports(UUID teamId, int limit, long offset);  // native — v2.1: một câu cố định mỗi chiều
List<TeamReportView> findSentReports(UUID teamId, int limit, long offset);
long countReceivedReports(UUID teamId);
long countSentReports(UUID teamId);
Optional<DisputeContextView> findDisputeContext(UUID reportId, UUID callerId);
@Modifying int markDisputed(UUID reportId, LocalDateTime now, LocalDateTime createdAfter);
Optional<ReportDecisionView> applyDecision(UUID reportId, String decision, LocalDateTime now); // UPDATE … RETURNING
Optional<ChatReportContextView> findChatReportContext(UUID conversationId, UUID callerId, LocalDateTime now); // v1.2 — 2.10; v2.1 + now

// DisputeRepository extends JpaRepository<Dispute, UUID>
Optional<UUID> resolveIfPending(UUID id, String decision, String note, UUID adminId, LocalDateTime now); // UPDATE … RETURNING report_id
List<AdminDisputeView> findAdminDisputesOldestFirst(String status, int limit, long offset); // v2.1: ORDER BY cố định mỗi câu
List<AdminDisputeView> findAdminDisputesNewestFirst(String status, int limit, long offset); // chọn theo DisputeStatusFilter.oldestFirst()
long countAdminDisputes(String status);
Optional<AdminDisputeDetailView> findAdminDisputeDetail(UUID id);

// MatchRepository (sửa)
Optional<RatingContextView> findRatingContext(UUID matchId, UUID callerId);
Optional<ReportContextView> findReportContext(UUID matchId, UUID callerId, String reportType);
@Modifying int completeIfArchived(UUID matchId, LocalDateTime now);

// TeamRepository (sửa)
@Modifying int adjustReliability(UUID id, int delta, boolean breaksCleanStreak);          // MATCH-001 — thêm tham số
List<UUID> lockForReputation(UUID[] ids);                                                 // v2.1 — gọi ngay trước applyCompletion
List<ScoreChangeView> applyCompletion(UUID matchId, UUID[] ids, boolean[] completes, int[] bonuses, …hằng ReputationRules);
Optional<ScoreChangeView> applyReportPenalty(UUID id, String type, int[] tiers);
@Modifying int refundReportPenalty(UUID id, String type, int points);
Optional<TeamReputationView> findReputation(UUID teamId, int cancelWindowHours);         // v2.1: STATS không dùng :now
List<VerificationChangeView> refreshVerification(UUID[] ids, LocalDateTime now, int minCompleted, double minAvg,
                                                 int minScore, int cancelWindowHours);
List<UUID> findIdsForVerification(UUID afterId, int limit);
```
Câu `… RETURNING` khai báo native trả projection, không `@Modifying` (MATCH-002 `[cần xác nhận #4]`).

Ghi chú SQL:
- `adjustReliability`: `SET reliability_score = LEAST(100, GREATEST(0, reliability_score + :delta)), clean_streak = CASE WHEN :breaks THEN 0 ELSE clean_streak END WHERE id = :id` — không tăng `version`.
- `completeIfArchived`: `UPDATE match SET status = 'COMPLETED', version = version + 1, updated_at = :now WHERE id = :id AND status = 'ARCHIVED'` — cùng mẫu `expireIfDue` (MATCH-001 §4.6), giữ quy tắc bulk tự tăng `version` (ADR-0003 Sửa đổi 2026-11-10 "Không phải ngoại lệ").
- `lockForReputation` (v2.1): `SELECT id FROM team WHERE id = ANY(CAST(:ids AS uuid[])) ORDER BY id FOR NO KEY UPDATE` — cùng thứ tự id với CTE `locked` của `applyCompletion` (không thêm vòng chờ); `applyCompletion` chạy ngay sau trong cùng transaction nên ảnh chụp của nó thấy report đã commit trong lúc chờ khoá (§4.6).
- `applyCompletion` (khung — developer viết bản chạy được, test theo bảng `Processing_Flow.md` §3.1):
  ```sql
  WITH input AS (SELECT * FROM unnest(CAST(:ids AS uuid[]), CAST(:completes AS boolean[]), CAST(:bonuses AS int[])) AS i(team_id, completes, bonus)),
  locked AS (SELECT t.id, t.reliability_score AS old_score, t.clean_streak AS old_streak
             FROM team t JOIN input i ON i.team_id = t.id ORDER BY t.id FOR NO KEY UPDATE OF t),
  calc AS (SELECT l.id, l.old_score, i.completes, i.bonus, l.old_streak + 1 AS next_streak,
                  i.completes AND NOT EXISTS (SELECT 1 FROM report r JOIN match m ON m.id = r.match_id
                                              WHERE r.match_id = :matchId AND r.reported_type = 'TEAM'
                                              AND r.reported_id = l.id AND r.status <> 'REJECTED'
                                              AND r.reporter_type = 'TEAM' AND r.reporter_id IN (m.author_team_id, m.confirmed_opponent_team_id)) AS clean   -- v1.2: chỉ đối thủ thật; MERCENARY-001 §4.12 thêm mệnh đề mercenary APPROVED
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
  v1.2 (user chốt 2026-11-15): trận "sạch" chỉ xét report của **đội đối phương thật** (`reporter_id` là đội tạo kèo / đội đã ghép, khác chính đội — `report_not_self_check`); report của **mercenary đã được duyệt của trận** được MERCENARY-001 thêm vào câu này khi code (`OR EXISTS mercenary_application … status = 'APPROVED'` theo `report.application_id` — cột có từ V10; MERCENARY-001 `Component_Class_Design.md` §4.12, review 2026-11-15) — V9 chưa có report từ mercenary nên mã này không cần mệnh đề đó; report "giao tiếp tệ" qua chat (2.10) của đội không được ghép bỏ qua (đã đưa chuỗi về 0 lúc gửi bằng `applyReportPenalty`). `JOIN match` (khoá chính) trong `EXISTS` — cùng câu, không thêm truy vấn. Thứ tự khoá và chờ khoá do `lockForReputation` ở câu trước (`ORDER BY id` — chống deadlock giữa hai trận cùng cặp đội); `ORDER BY t.id FOR NO KEY UPDATE` trong CTE `locked` giữ như phòng vệ — transaction đã giữ khoá nên câu này không còn chờ (v2.1). `old_score`, `old_streak` đọc trong `locked` (dòng đã khoá — ADR-0013 Sửa đổi 2026-11-27 quy tắc 2; `[cần xác nhận #6]` đóng v2.1). Subquery `NOT EXISTS report` đọc ảnh chụp đầu câu — đúng vì câu bắt đầu sau khi `lockForReputation` đã có khoá (§4.6).
- `applyReportPenalty`: `WITH locked AS (SELECT id, reliability_score AS old_score, CASE CAST(:type AS varchar) WHEN 'NOSHOW' THEN report_count_noshow … END + 1 AS next_count FROM team WHERE id = :id FOR NO KEY UPDATE) UPDATE team t SET report_count_<loại> = report_count_<loại> + 1` (4 cột viết bằng `CASE WHEN :type = '…' THEN … + 1 ELSE … END`, **không** nối chuỗi tên cột), `clean_streak = 0`, `reliability_score = GREATEST(0, t.reliability_score - (CAST(:tiers AS int[]))[LEAST(l.next_count, 4)]) FROM locked l WHERE t.id = l.id RETURNING l.old_score, t.reliability_score AS new_score`. **`next_count` (bộ đếm sau khi tăng) MUST đọc trong CTE `locked`** trên dòng đã khoá (v2.1): đọc bộ đếm ở chỗ khác của câu (quét `team` lần hai) lấy ảnh chụp đầu câu → hai report cùng loại đồng thời dùng cùng bậc (integration test tái hiện: trừ 5 + 5 thay vì 5 + 8).
- `refundReportPenalty`: `SET reliability_score = LEAST(100, reliability_score + :points), report_count_<loại> = GREATEST(0, report_count_<loại> - 1)` (CASE theo `:type`). `GREATEST(0, …)` vì bộ đếm có thể đã về 0 do mốc chuỗi.
- `findChatReportContext` (v1.2): một câu = `ConversationSql.CONTEXT_COLUMNS` / `CONTEXT_JOINS` của MATCH-001 §5.2 (mọi loại ngữ cảnh, gồm cột người gọi, đội trưởng / người đã giải tán, trạng thái đội; tham số `:callerId`, `:now` — v2.1) + `m.match_start_time` (alias `m` = `match` đã `LEFT JOIN` trong `CONTEXT_JOINS`, không join `match` lần nữa — v2.1) + `EXISTS (SELECT 1 FROM conversation_message cm WHERE cm.conversation_id = c.id) AS has_messages` + `EXISTS (report BAD_COMMUNICATION chiều A → B của kèo) AS a_reported_b`, `EXISTS (… B → A) AS b_reported_a` (tiền tố `idx_report_unique`). Tên / ảnh đội bị report cho `counterpartTeam` có trong cột ngữ cảnh — không đọc lại. Policy dùng chung với chat nên quy tắc "ai là bên nào" một nơi.
- `markDisputed`: `UPDATE report SET status = 'DISPUTED', updated_at = :now WHERE id = :id AND status = 'AUTO_ACCEPTED' AND report_type = 'NOSHOW' AND created_at > :createdAfter`.
- `applyDecision`: `UPDATE report SET status = :decision, updated_at = :now WHERE id = :id AND status = 'DISPUTED' RETURNING reported_id, reporter_id, report_type, reliability_penalty`.
- `resolveIfPending`: `UPDATE dispute SET status = 'RESOLVED', decision = :decision, resolution_note = :note, resolved_by_user_id = :adminId, resolved_at = :now, updated_at = :now WHERE id = :id AND status = 'PENDING' RETURNING report_id`.
- `findReceivedReports` / `findSentReports` (v2.1 — tách theo chiều vì một method không chọn được câu cố định): `RECEIVED` → `WHERE r.reported_type = 'TEAM' AND r.reported_id = :teamId`; `SENT` → `reporter_*`; `JOIN match m`, `JOIN team ct ON ct.id = <phía còn lại>`, `LEFT JOIN dispute d ON d.report_id = r.id` → `ORDER BY r.created_at DESC, r.id DESC LIMIT/OFFSET`. Khối cột chung một hằng, phần `FROM … WHERE` cố định mỗi câu (không nối chuỗi cột); `countReceivedReports` / `countSentReports` tương ứng.
- `findAdminDisputesOldestFirst` / `findAdminDisputesNewestFirst` (v2.1 — `ORDER BY` cố định mỗi câu, dùng được index, không `ORDER BY CASE`): `dispute d JOIN report r JOIN match m JOIN team rt (reporter) JOIN team dt (reported) JOIN "user" ru ON ru.id = r.created_by_user_id JOIN "user" du ON du.id = d.created_by_user_id LEFT JOIN "user" au ON au.id = d.resolved_by_user_id` → tên thật + `(x.deleted_at IS NOT NULL)` mỗi người (ADR-0002 Sửa đổi 2026-11-12 — **không** gọi `DeletedUserMasking`). `PENDING` → `OldestFirst` (`ORDER BY d.created_at ASC, d.id ASC`); khác → `NewestFirst` (`DESC`). `ALL` bỏ điều kiện `status`.
- `findAdminDisputeDetail`: như trên + `s.name` (sport), `v.name` (`LEFT JOIN venue v ON v.id = m.venue_id`), `r.reason`, `r.reliability_penalty`, `d.evidence`, `d.resolution_note`, `rt.reliability_score`, `dt.reliability_score`, `m.status`, `m.match_start_time`, `m.match_end_time`.

### 5.3. Projection & SQL dùng chung

| Projection | Nội dung |
|---|---|
| `MatchSidesView` (v2.1) | Interface cha (cho `MatchSides` — §4.9): `authorTeamId`, `opponentTeamId`, `authorTeamStatus`, `opponentTeamStatus` (JOIN `team`, cùng câu — BR-R10), `callerAuthorCaptain`, `callerOpponentCaptain` |
| `RatingContextView` | `extends MatchSidesView` (v2.1) + `matchId`, `status`, `authorRated`, `opponentRated` (EXISTS rating `rater_type = 'TEAM'`) |
| `ReportContextView` | `extends MatchSidesView` (v2.1) + `matchId`, `status`, `matchStartTime`, `authorReportedType`, `opponentReportedType` (EXISTS report cùng `:reportType` theo từng phía), `authorTeamName`, `authorTeamAvatarUrl`, `opponentTeamName`, `opponentTeamAvatarUrl` (v2.1 — cùng câu, dựng `counterpartTeam` của response 2.3 không đọc lại) |
| `MatchRatingRowView` | `authorTeamId`, `opponentTeamId` + cột rating (nullable) |
| `TeamReportView` | cột report + `matchStartTime` + đội còn lại (`id`, `name`, `avatar_url`) + cột dispute (nullable) |
| `DisputeContextView` | `reportId`, `reportType`, `status`, `createdAt`, `reporterTeamId` (v2.1 — cùng câu, cho payload `ReportDisputedEvent`), `reportedTeamId`, `reportedTeamStatus`, `callerReportedCaptain`, `disputeExists` |
| `ReportDecisionView` | `reportedTeamId`, `reporterTeamId`, `reportType`, `reliabilityPenalty` |
| `ChatReportContextView` (v1.2) | `extends ConversationContextView` (MATCH-001 §5.3 — đã có `partyAName`, `partyAAvatarUrl`, `partyBName`, `partyBAvatarUrl`) + `matchStartTime`, `hasMessages`, `aReportedB`, `bReportedA` |
| `AdminDisputeView` / `AdminDisputeDetailView` | cột §5.2 |
| `TeamReputationView` | `teamId`, `reliabilityScore`, `verifiedAt`, `completedMatches`, `countedMatches`, `averageRating` (numeric, chưa làm tròn), `ratingCount` |
| `ScoreChangeView` | `teamId`, `oldScore`, `newScore` |
| `VerificationChangeView` | `teamId`, `verified` |

**Thống kê đội** — một hằng chuỗi SQL `TeamReputationSql.STATS` (class `final` trong `domain/repository`), ghép vào `findReputation` và `refreshVerification` (nguồn duy nhất cho công thức `API_Spec.md` §1.4, §1.5). v2.1: STATS là **subquery một dòng tham chiếu `tx.id`** (bảng `team` ngoài đặt alias `tx`, ghép bằng `CROSS JOIN LATERAL`) — một chuỗi dùng chung cho 1 đội và nhiều đội; tham số `:cancelWindowHours` (không `:now`):
```sql
-- đội = tx.id; m_t = mọi trận của đội (2 nhánh UNION ALL để dùng 2 index; author ≠ opponent luôn đúng)
SELECT st.completed_matches, st.counted_matches, rt.avg_rating, rt.rating_count
FROM (
  SELECT COUNT(*) FILTER (WHERE f.status = 'COMPLETED' AND NOT f.noshow) AS completed_matches,
         COUNT(*) FILTER (WHERE f.status = 'COMPLETED'
                          OR (f.status = 'CANCELLED' AND f.confirmed_opponent_team_id IS NOT NULL AND f.cancelled_by_team_id = tx.id  -- chỉ đội huỷ
                              AND f.cancelled_at > f.match_start_time - make_interval(hours => :cancelWindowHours))
                          OR (f.status = 'ARCHIVED' AND f.noshow)) AS counted_matches
  FROM (SELECT m_t.*, EXISTS (SELECT 1 FROM report r WHERE r.match_id = m_t.id AND r.reported_type = 'TEAM' AND r.reported_id = tx.id
                                                     AND r.report_type = 'NOSHOW' AND r.status <> 'REJECTED') AS noshow
        FROM (SELECT m.id, m.status, m.match_start_time, m.cancelled_at, m.cancelled_by_team_id, m.confirmed_opponent_team_id FROM match m WHERE m.author_team_id = tx.id
              UNION ALL
              SELECT m.id, m.status, m.match_start_time, m.cancelled_at, m.cancelled_by_team_id, m.confirmed_opponent_team_id FROM match m WHERE m.confirmed_opponent_team_id = tx.id) m_t) f
) st
CROSS JOIN (SELECT AVG(r.score) AS avg_rating, COUNT(r.score) AS rating_count FROM rating r
            WHERE r.ratee_type = 'TEAM' AND r.ratee_id = tx.id AND r.rater_type = 'TEAM' AND r.score IS NOT NULL) rt  -- v2.1: chỉ sao của đội đối phương (API_Spec.md §4.8)
```
`findReputation` = `FROM team tx CROSS JOIN LATERAL (STATS) s WHERE tx.id = :teamId` (1 câu). `refreshVerification` dùng cùng `CROSS JOIN LATERAL` cho từng `tx.id = ANY(:ids)`:
```sql
UPDATE team t SET verified_at = CASE WHEN e.eligible THEN CAST(:now AS timestamp) ELSE NULL END  -- v2.1: CAST vì CASE chỉ có tham số + NULL bị suy kiểu text
FROM (SELECT tx.id,
             COALESCE(EXISTS (SELECT 1 FROM team_member cm JOIN "user" u ON u.id = cm.user_id
                              WHERE cm.team_id = tx.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
                                AND u.phone_number IS NOT NULL AND u.deleted_at IS NULL)
                      AND s.completed_matches > :minCompleted AND s.avg_rating > :minAvg AND tx.reliability_score >= :minScore, false) AS eligible
      FROM team tx CROSS JOIN LATERAL (<STATS>) s WHERE tx.id = ANY(CAST(:ids AS uuid[]))) e
WHERE t.id = e.id AND (t.verified_at IS NOT NULL) IS DISTINCT FROM e.eligible
RETURNING t.id, (t.verified_at IS NOT NULL) AS verified
```
`avg_rating` NULL (chưa có sao) → `eligible` NULL → `COALESCE(…, false)`. Đội `DISBANDED` không còn captain ACTIVE → không đủ điều kiện → thu hồi ở lần refresh kế tiếp.

## 6. Entity

| Entity | Thay đổi |
|---|---|
| `rating.Rating` | `score`: `BigDecimal` → `Integer` (nullable, `SMALLINT`) · + `noShow` (`boolean`, cột `is_no_show`, `@Builder.Default false`; getter `isNoShow()`, builder `.noShow()` — Lombok đặt tên theo field boolean; JSON vẫn `isNoShow` — v2.1) · `matchScoreHome/Away` → `raterTeamScore`, `rateeTeamScore` · + `createdByUserId` (`UUID`, `nullable = false`) · mọi cột `updatable = false` (đánh giá không sửa) |
| `rating.Report` | `Status` → `{AUTO_ACCEPTED, DISPUTED, CONFIRMED, REJECTED}`, `@Builder.Default AUTO_ACCEPTED`, `status` `updatable = false` (chỉ đổi bằng CAS native — ADR-0003 Sửa đổi 2026-11-12) · bỏ `adminReviewedAt` · `reason` `nullable = false` · + `reliabilityPenalty` (`Integer`, `@Builder.Default 0`; ghi một lần bằng dirty checking trong 2.3) · + `createdByUserId` · + `updatedAt` (`@PrePersist`/`@PreUpdate`, `@Setter(NONE)`) |
| `rating.Dispute` (mới) | Map V9; `@UuidGenerator(VERSION_7)` (ADR-0008); `status` (`enum {PENDING, RESOLVED}`), `decision` (`enum {CONFIRMED, REJECTED}`), `resolutionNote`, `resolvedByUserId`, `resolvedAt` đều `updatable = false` (chỉ `resolveIfPending`); `createdAt`/`updatedAt` như `Report` |
| `team.Team` | Bỏ `totalMatchesCompleted`, `totalMatchesConfirmed`, `averageRating` · `cleanStreak`, `reportCount*` thêm `updatable = false` (ADR-0013 §4) · + `verifiedAt` (`insertable = false, updatable = false` — chỉ `refreshVerification`) |
| `user.User` | Bỏ `totalMatchesCompleted`, `totalMatchesConfirmed`, `averageRating` · `cleanStreak`, `reportCount*` thêm `updatable = false` |

Kiểm bằng migration integration test (`ddl-auto=validate` sau V9) + test entity: sửa `Team`/`User` qua dirty checking không ghi đè `clean_streak`, `report_count_*`, `verified_at`.

## 7. DTO & Mapper

- DTO là `record`; compact constructor `trim()` chuỗi, `""` ở field tuỳ chọn → `null` trước Bean Validation (như MATCH-001 §7).
- `CreateRatingRequest(Boolean isNoShow, Integer score, @Size(max = 500) String comment, @Min(0) @Max(999) Integer raterTeamScore, @Min(0) @Max(999) Integer rateeTeamScore)` + `@ValidRating` (mức lớp): `isNoShow` vắng → `false`; `!isNoShow` → `score` 1..5 bắt buộc; `isNoShow` → `score`, hai tỷ số phải `null`; hai tỷ số cùng có hoặc cùng vắng. Lỗi gắn property node theo tên field (`score`, `raterTeamScore`…) như `@ValidMatchSchedule`.
- `CreateReportRequest(@NotNull @OneOf({"NOSHOW","BAD_PLAY","BAD_COMMUNICATION","SKILL_MISMATCH"}) String reportType, @NotBlank @Size(max = 1000) String reason)`.
- `CreateDisputeRequest(@NotBlank @Size(max = 1000) String evidence)`; `ResolveDisputeRequest(@NotNull @OneOf({"CONFIRMED","REJECTED"}) String decision, @NotBlank @Size(max = 500) String note)`.
- `ReputationMapper` (`componentModel = "spring"`, `uses MapperSupport`, `unmappedTargetPolicy = ERROR`): `toRating(Rating|MatchRatingRowView)`, `toCreated(Rating, Match.Status)`, `toReport(TeamReportView, ReportDirection, LocalDateTime now)` (status hiệu lực §4.10), `toCreatedReport(Report, ReportContextView…)`, `toDisputeCreated(Dispute)`, `toAdminDispute(AdminDisputeView)`, `toAdminDetail(AdminDisputeDetailView, List<Rating>)`, `toReputation(TeamReputationView)`. Tên user Admin không qua `DeletedUserMasking`.
- `averageRating`, `completionRate`: `BigDecimal.setScale(…, HALF_UP)` ở mapper.

## 8. Cấu hình & Dependencies

```properties
arenaflow.reputation.verification.cron=${REPUTATION_VERIFICATION_CRON:0 0 3 * * *}
arenaflow.reputation.verification.batch-size=500
```
`batch-size` bind vào `support/reputation/TeamVerificationProperties(int batchSize)` (`@ConfigurationProperties(prefix = "arenaflow.reputation.verification")`, mẫu `VenueAlertProperties`); `cron` do `TeamVerificationScheduler` đọc trực tiếp trong `@Scheduled` (v2.1).

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
| ADR-0003 Sửa đổi 2026-11-12 (gạch cuối: một câu, khoá trong CTE, không giữ khoá qua nhiều câu) · ADR-0013 (một câu SQL mỗi thay đổi) | `applyMatchCompletion` khoá đội bằng câu riêng `lockForReputation` rồi một câu `applyCompletion` — giữ khoá qua 2 câu, cùng transaction (§4.6, §5.2) — v2.1 | Subquery `NOT EXISTS report` của `applyCompletion` đọc ảnh chụp đầu câu, khoá trong CTE không làm nó thấy report commit trong lúc chờ; race test tái hiện chuỗi trận sạch 1 thay vì 0 cho đội bị report bùng | ADR-0013 Sửa đổi 2026-11-27 · ADR-0003 Sửa đổi 2026-11-27 |
| ADR-0011 quy tắc 4 (listener `@TransactionalEventListener`) | Huy hiệu sau thao tác của chính mã này dùng `TransactionSynchronization.afterCommit` thay vì nghe event của mình | Quy tắc 5 (miền phát không nghe event của mình); gom id → 1 câu/request | ADR-0011 quy tắc 8 |
| ADR-0012 (job chuyển trạng thái claim lô `SKIP LOCKED`) | Job huy hiệu `reconcileAll` không claim, lặp lô theo `id` | Không chuyển trạng thái theo hàng đợi; câu refresh có điều kiện "trạng thái đổi" → hai instance chạy trùng không phát event trùng (§4.8) | Ghi trong thiết kế |
| NFS §3 W6 | 2.8 `REJECTED` = 6 (= ngưỡng); 2.1 hoàn tất trận = 6 (= ngưỡng — v2.1, user duyệt 2026-11-26) | 2.8: response B4 cần đọc lại chi tiết (2 câu); 2.1: câu khoá đội riêng trước `applyCompletion` (§4.6) | Đạt ngưỡng — mọi truy vấn thêm phải gộp |
| Mã lỗi theo miền | Thử lại hết lượt dùng `MATCH_014` cho cả 2.1, 2.3, 2.5, 2.8 | Message chung, FE xử lý giống nhau; tránh thêm mã cùng nghĩa | Ghi ở `API_Spec.md` §1.1 |

## 11. Tài liệu cần đồng bộ khi implement

Sau khi mọi phase code xong + test PASS (CLAUDE.md §8):

| Tài liệu | Nội dung | Nhánh |
|---|---|---|
| `docs/database/schema.dbml`, `schema-design.md` (`rating`, `report`, `team`, `user`) | V9: cột/CHECK/index/FK mới, bảng `dispute`, bỏ `total_matches_*`, `average_rating`, `admin_reviewed_at` | `feature/REPUTATION-001-*` |
| `docs/agent/api-convention.md` §5, §6 | 9 endpoint vào bảng 2xx (§3) + v1.2 `POST /conversations/{id}/reports` 201 B2; domain `RATING`, `REPORT` (gồm `REPORT_009`..`REPORT_011`), `DISPUTE` | `chore/agent-framework-*` |
| `docs/agent/convention-backend.md` §1 | `support/reputation/` | `chore/agent-framework-*` |
| `docs/agent/CLAUDE.md` §2 | Dòng điều hướng REPUTATION-001 | `chore/agent-framework-*` |
| SRS `SYSTEM-001` v1.8 | Đã cập nhật cùng nhánh thiết kế (BR-R07..R10, AC-R07, §8.2) | `feature/REPUTATION-001-*` |
| MERCENARY-001 `Component_Class_Design.md` §4.10, §5.2; `Processing_Flow.md` §3 (2.18: 4 → 5 / W6), §5 (dòng "Report mercenary gần lúc đội trưởng chấm sao" — bỏ chấp nhận theo `[cần xác nhận #12]` đã đóng), §9 #13 (trỏ #6 đã đóng) | v2.1: `applyMercenaryCompletion` khoá dòng `"user"` bằng câu riêng trước câu cập nhật, bộ đếm report cá nhân đọc trong CTE `locked` / dòng đích (ADR-0013, ADR-0003 Sửa đổi 2026-11-27) | `feature/MERCENARY-001-*` (khi tích hợp) |

## 12. Danh sách [cần xác nhận]

Đánh số toàn thiết kế: 1–4 và 13 đã đóng (§13), 5–11, 14 ở đây, 12 ở `Processing_Flow.md` §9. v2.1: đóng 5, 6, 8 (dưới) và 12 (`Processing_Flow.md` §9).

| # | Nội dung | Loại |
|---|---|---|
| 5 | ~~Native `UPDATE … RETURNING` không `@Modifying` trả projection — cùng MATCH-002 `[cần xác nhận #4]`~~ — **Đóng v2.1:** MATCH-002 đã có trên `main`; integration test repository (`applyCompletion`, `applyReportPenalty`, `applyDecision`, `resolveIfPending`, `refreshVerification`) PASS trên DB dev V9; 2.8 `REJECTED` giữ 6/W6 | Đã đóng |
| 6 | ~~CTE `… FOR NO KEY UPDATE` + `UPDATE … FROM` trả `old_score` là điểm sau khi chờ khoá~~ — **Đóng v2.1:** race test hai giao dịch đồng thời xác nhận cột đọc trong CTE `locked` là giá trị sau khi chờ khoá; phần khác của câu (subquery, quét bảng lần hai) đọc ảnh chụp đầu câu → `applyReportPenalty` đọc `next_count` trong `locked`, `applyMatchCompletion` khoá bằng câu riêng (§4.6, §5.2) | Đã đóng |
| 7 | Bind mảng `uuid[]`/`boolean[]`/`int[]` vào native query (cùng MATCH-001 `[cần xác nhận #1]`, MATCH-002 `[cần xác nhận #2]`) | Không chặn |
| 8 | ~~**Thứ tự implement:** V9 cần V5–V8 (VENUE-001, SYSTEM-002, MATCH-001, MATCH-002) đã code~~ — **Đóng v2.1:** V6–V8 có trên `main` (`04b6136`), V9 đã áp DB dev | Đã đóng |
| 9 | Mở transaction mới (`REQUIRES_NEW`) trong `afterCommit` (Spring: tài nguyên transaction cũ còn bind lúc `afterCommit`) [Inference — theo Javadoc `TransactionSynchronization`] | Không chặn — test tích hợp refresher |
| 10 | Kiểm `spring.task.scheduling.pool.size` 4 đủ (MATCH-001 `[cần xác nhận #10]`) | Không chặn |
| 11 | ~~Thông báo "rating nhận" (Business_Rules §8.6) mâu thuẫn BC-R04~~ — **Đóng v1.3 (user chốt Q3, 2026-11-16):** "nhận đánh giá" chỉ gửi khi người nhận đã đánh giá trước; nhắc đánh giá gửi lúc `ARCHIVED`; `ARCHIVED → COMPLETED` không báo (NOTIFICATION-001 `Component_Class_Design.md` §4.3). Payload `MatchRatedEvent` không đổi | Đã đóng |
| 14 | ~~Report qua chat của đội không được ghép làm trận không sạch lúc hoàn thành~~ — **user chốt 2026-11-15:** chỉ tính report của đối thủ thật + mercenary của trận (§5.2 `applyCompletion`, không thêm câu) | Đã đóng |

## 13. Quyết định đã chốt (user, 2026-11-12)

| # cũ | Quyết định | Nơi áp dụng |
|---|---|---|
| 13 (A1) | Không nghe `MatchCancelledEvent`; huy hiệu sau huỷ kèo / rút yêu cầu đã chấp nhận chỉ cập nhật ở job hằng ngày (trễ tối đa ~1 ngày); MATCH-001 2.10 giữ 6/W6 | §4.8; `API_Spec.md` §1.5 |
| 1 (B) | Đánh giá "không đến": chuỗi trận sạch đội vắng giữ nguyên | `applyMatchCompletion` không có entry đội vắng (§4.1); `API_Spec.md` §1.3 |
| 2 (C) | Trận bị đánh giá "không đến" mà không bị report bùng vẫn vào tử số của đội vắng | §5.3 (tử số chỉ xét report bùng); `API_Spec.md` §1.4 |
| 3 (D) | Kèo huỷ dưới 24 giờ trước trận chỉ vào mẫu số của **đội huỷ** (`cancelled_by_team_id = :teamId`) — đổi so với câu chữ SRS cũ | §5.3; `API_Spec.md` §1.4 |
| 4 (E) | Admin bác report không khôi phục chuỗi trận sạch | §4.6 `refundReportPenalty`; `API_Spec.md` §1.3 |
| F | `reason`/`evidence` ≤ 1000, `note` ≤ 500, `comment` ≤ 500 | §5.1, §7; `API_Spec.md` 2.1, 2.3, 2.5, 2.8 |

## 14. Sửa đổi bởi MERCENARY-001 (v1.1, 2026-11-13, cập nhật 2026-11-14)

Thiết kế chi tiết ở MERCENARY-001 `Component_Class_Design.md` §4.10, §4.12, §5.2 (nguồn duy nhất); các mục dưới của tài liệu này đọc kèm bảng sau.

| Mục ở đây | Sửa |
|---|---|
| §1, §4.6 `ReputationService` | + `applyMercenaryCompletion` (khi đội trưởng chấm sao mercenary — BR-ME15 sửa 2026-11-14), `applyUserReportPenalty`, `refundUserReportPenalty`; `ScoreChangeView` + `subjectType`; `ReliabilityReason` + `MERCENARY_COMPLETED`, `MERCENARY_WITHDRAWN_AFTER_APPROVED`. `applyMatchCompletion` / `TeamRepository.applyCompletion` **không đổi** (mercenary không được cộng điểm lúc trận `COMPLETED`) |
| §4.4 `AdminDisputeService.resolveDispute` (2.8) | CAS 0 dòng: `existsById` → `DisputeRepository.existsTeamDispute(id)` (`EXISTS dispute d JOIN report r … AND r.application_id IS NULL`) → `false` → `DISPUTE_001` — id của tranh chấp mercenary trả 404 thay vì 409 (M5) |
| §4.3 `DisputeService`, §5.3 `DisputeContextView` | + `reportedType`, `callerIsReportedUser`; report nhắm vào cá nhân → caller phải là người bị report (`REPORT_009`), không kiểm trạng thái đội |
| §4.7 event | `TeamReportedEvent`, `ReportDisputedEvent`, `DisputeResolvedEvent`: `reporterTeamId/reportedTeamId` → `reporterType, reporterId, reportedType, reportedId` |
| §4.8 huy hiệu | `TeamVerificationRefresher` → `VerificationRefresher` (gom id đội + cá nhân, tối đa 1 câu mỗi loại); `TeamVerificationJobService` / `TeamVerificationScheduler` → `VerificationJobService` / `VerificationScheduler` (thêm vòng cá nhân) |
| §5.2, §5.3 truy vấn đội ↔ đội | `findRatingContext` (`authorRated`/`opponentRated`), `findMatchRatingRows`, `findByMatchIdAndRaterType…`, `findReportContext`, `findReceivedReports`/`findSentReports`/`countReceivedReports`/`countSentReports`, `findAdminDisputesOldestFirst`/`findAdminDisputesNewestFirst`/`countAdminDisputes`/`findAdminDisputeDetail` (tên v2.1), `resolveIfPending`, sao trung bình trong `TeamReputationSql.STATS`: thêm `application_id IS NULL`. `noshow` của STATS **giữ** mọi report bùng nhắm vào đội của kèo; trận sạch trong `applyCompletion` xét report của đối thủ thật + mercenary (v1.2, user chốt 2026-11-15) (kể cả từ mercenary — BR-ME14, user chốt 2026-11-14); report trận ngoài hệ thống có `match_id = NULL` nên không vào STATS |
| §5.2 `applyDecision` | `RETURNING` thêm `reported_type` |
| Migration | Cột `application_id`, `external_match_id` của `rating`/`report` (`match_id` thành nullable — luồng đội ↔ đội luôn có, CHECK của V10), `"user".verified_at`: V10 (MERCENARY-001 §5.1); V9 không đổi |
| §4.3 `findDisputeContext` | Không `JOIN match` bắt buộc (report trận ngoài có `match_id = NULL`) |
