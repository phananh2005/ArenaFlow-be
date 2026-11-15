# Component & Class Design — Tuyển mercenary, ứng tuyển, chat theo đơn, uy tín cá nhân

| Mục | Giá trị |
|---|---|
| Mã chức năng | `MERCENARY-001` |
| Phạm vi tầng | Backend (Spring Boot REST API + kênh STOMP của MATCH-001). Frontend: ngoài phạm vi repo này |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · Spring Data JPA · Flyway · PostgreSQL ≥ 18 |
| Quy ước áp dụng | `docs/agent/convention-backend.md`, `docs/agent/security-backend.md`, `docs/agent/non-functional-standards.md` · kế thừa USER-001 `Component_Class_Design.md` §4.6, §5, §7–§8 (`DeletedUserMasking` qua `MapperSupport`); VENUE-001 §4.6 (`VenueSelectionService`), §4.9 (retrier overload), §5.2 (hộp + Haversine); MATCH-001 §4.5 (`TeamGuard`), §4.8 (`ReputationService`, `TimeBandPenaltyPolicy`), §4.9 (event), §4.10 (scheduler); MATCH-001 (v2.4) §4.14 (lõi cuộc trò chuyện `ConversationService`, `ConversationAccessPolicy`), §4.15 (kênh realtime), §5.1 (bảng `conversation*` ở V6), §5.2 (`ConversationRepository.findContext`, cursor `(created_at, id)`), §7 (`ChatHistoryQuery`, `@ValidChatCursor`, `ConversationMapper`); REPUTATION-001 §4.6 (`ReputationService`, `ReportPenaltyPolicy`, `ReputationRules`), §4.8 (refresher huy hiệu), §4.10 (trạng thái report hiệu lực), §5.3 (thống kê) · ADR-0002, ADR-0003 (+ Sửa đổi 2026-11-10, 2026-11-12, 2026-11-14), ADR-0007 (+ Sửa đổi 2026-11-14), ADR-0008, ADR-0011, ADR-0012, ADR-0013, ADR-0014 (+ Sửa đổi 2026-11-13, 2026-11-15) |
| Ràng buộc dữ liệu | V1–V4 (code) + V5 (VENUE-001, gồm `venue.ward_id` v4.0), V6 (MATCH-001), V6 (MATCH-001 — gồm bảng `conversation`, `conversation_message` từ v2.4), V8 (REPUTATION-001) — **chưa có trong code** + `V9__mercenary_recruitment_chat_reputation.sql` (§5.1 — v2.0: chỉ thêm phần riêng mercenary vào `conversation`). Entity hiện có `domain/.../entity/mercenary/{MercenaryRecruitment, MercenaryApplication}`, `rating/{Rating, Report}`, `user/User`, `venue/Venue` |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-13 · v1.0 (baseline) · hiện tại **v2.0** (solution-architect · 2026-11-15) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan kiến trúc

Layered theo 4 module Maven (ADR-0006). Hợp đồng FE: `API_Spec.md`. Luồng, transaction, số truy vấn, khoá: `Processing_Flow.md`.

- **Trận của tin** là kèo trên hệ thống (`match`) **hoặc** trận ngoài hệ thống (`external_match`, BR-ME19 — §4.15). Tin, cuộc trò chuyện, đánh giá, report mang **một trong hai** khoá (`match_id` XOR `external_match_id`); mọi truy vấn đọc thuộc tính trận qua biểu thức chung `MercenarySql.GAME_*` (§5.3) để các luồng tin / đơn / chat / uy tín không rẽ nhánh theo loại trận.
- **Tin tuyển / đơn ứng tuyển đổi trạng thái bằng CAS tự viết** `UPDATE … WHERE status = … [AND quantity_* = :expected]` (ADR-0003 Sửa đổi 2026-11-14 — cùng mẫu `MatchRequest`, `Pairing`, `Report`); entity chỉ INSERT + đọc. Thứ tự khoá mọi luồng: `mercenary_recruitment` → `mercenary_application` → `"user"`/`team` (`Processing_Flow.md` §5), kể cả xoá tài khoản (ADR-0007 Sửa đổi 2026-11-14).
- **Trạng thái hiệu lực tính trong SQL** (như kèo `OPEN` quá hạn — MATCH-001 §5.2): tin `OPEN`/`FILLED` của trận đã huỷ → `CLOSED`; của trận đã tới giờ kết thúc → `EXPIRED`; đơn `PENDING` của tin hiệu lực `CLOSED`/`EXPIRED` → `REJECTED` (§5.3). API ghi không ghi chuyển trạng thái hệ thống — từ chối theo trạng thái hiệu lực; **job** (§4.9, ADR-0012) ghi thật và phát event. Hệ quả: MATCH-001 huỷ kèo (2.5, 2.10 — đang 6/W6) **không** thêm truy vấn.
- **Uy tín cá nhân** ghi cùng transaction qua `ReputationService` (ADR-0013), mở rộng REPUTATION-001 (§4.10): hoàn thành trận khi đội trưởng chấm sao (BR-ME15, user sửa 2026-11-14 — **không** gắn với lúc trận `COMPLETED`), trừ điểm rút lui, report cá nhân, hoàn điểm. Completion Rate, sao trung bình cá nhân tính khi đọc (§5.4). Huy hiệu cá nhân là dữ liệu dẫn xuất `"user".verified_at`, refresh sau commit + job hằng ngày (như đội).
- **Chat theo đơn** là một loại ngữ cảnh (`MERCENARY_APPLICATION`) của **lõi cuộc trò chuyện dùng chung** do MATCH-001 (v2.4) thiết kế — bảng `conversation` + `conversation_message` (V6), `ConversationService`, endpoint `/conversations/{id}` (MATCH-001 2.19, 2.16, 2.17, 2.18 = 2.14–2.17 ở đây), topic `/topic/conversations/{id}` (ADR-0014 Sửa đổi 2026-11-13, 2026-11-15). Mã này thêm: cột ngữ cảnh mercenary (V9 — §5.1), `MercenaryApplicationConversationPolicy` + khối cột mercenary của `findContext` (§4.7), frame `APPLICATION_UPDATED` (§4.8).
- **Đánh giá / report** dùng lại bảng `rating`, `report`, `dispute` (REPUTATION-001) với `rater_type`/`ratee_type` `TEAM`↔`INDIVIDUAL` và cột mới `application_id`. Luồng đội↔đội của REPUTATION-001 lọc `application_id IS NULL` (§4.12).

**Điều kiện tiên quyết:** code VENUE-001, MATCH-001 (lõi chat, kênh realtime — v2.4), MATCH-002 (retrier bắt `ConcurrencyConflictException` — MATCH-002 `[cần xác nhận #5]`, `UPDATE … RETURNING` không `@Modifying` — MATCH-002 `[cần xác nhận #4]`), REPUTATION-001 (V8, `ReputationService`, refresher) phải có trước V9 `[cần xác nhận #1]`.

## 2. Package Structure (delta)

```text
common/        com.phananh.arenaflow.common
└── exception/       ErrorCode (+ MERCENARY_001..MERCENARY_025, REPORT_009; CHAT_002..CHAT_004 thuộc MATCH-001 v2.4)

domain/        com.phananh.arenaflow.domain
├── entity/mercenary/ MercenaryRecruitment, MercenaryApplication (sửa — §6); ExternalMatch (mới)
├── entity/chat/      Conversation (sửa — + applicationId, externalMatchId; MATCH-001 §6), ConversationMessage (tái dùng)
├── entity/rating/    Rating, Report (sửa: + applicationId, + externalMatchId, matchId nullable)
├── entity/user/      User (sửa: + verifiedAt)
├── entity/converter/ PositionConverter (mới)
├── repository/       MercenaryRecruitmentRepository, MercenaryApplicationRepository, ExternalMatchRepository,
│                     MercenarySql (hằng SQL) (mới); ConversationRepository (sửa — khối mercenary của `findContext`);
│                     UserRepository, RatingRepository, ReportRepository, DisputeRepository (sửa)
└── projection/       RecruitContextView, RecruitmentRowView, RecruitmentView, RecruitmentWriteContextView,
                      ApplyContextView, ApplicationContextView, ApplicationView, ApplicantRowView,
                      WithdrawnApplicationView, ApprovedCountView, ConversationContextView (sửa — cột mercenary),
                      MercenaryRatingContextView, MercenaryReportContextView, MercenaryReportView,
                      AdminMercenaryDisputeView, AdminMercenaryDisputeDetailView, UserReputationView,
                      TeamMercenaryRatingView, DueRecruitmentView, RejectedApplicationView, ProfileAreaView,
                      ExternalMatchContextView, ExternalMatchView (mới)

application/   com.phananh.arenaflow.application
├── service/          MercenaryRecruitmentService, MercenaryApplicationService, ExternalMatchService,
│   │                 MercenaryRatingService, MercenaryReportService, UserReputationService,
│   │                 AdminMercenaryDisputeService, MercenaryLifecycleJobService (mới); ReputationService,
│   │                 ReliabilityReason (sửa — §4.10); RecruitmentStatusFilter, ApplicationStatusFilter,
│   │                 ExternalMatchStatusFilter, MercenaryReportDirection (mới)
│   └── impl/         …ServiceImpl tương ứng
├── event/mercenary/  MercenaryApplicationSubmittedEvent, MercenaryApplicationApprovedEvent,
│                     MercenaryApplicationRejectedEvent, MercenaryApplicationWithdrawnEvent,
│                     MercenaryRecruitmentClosedEvent, MercenaryGameCancelledEvent, MercenaryRatingCreatedEvent,
│                     MercenaryReportCreatedEvent (mới)
├── event/reputation/ UserVerificationChangedEvent (mới)
├── support/mercenary/ MercenaryGuard, MercenaryRules, MercenarySide, MercenaryAccountCleanup (mới)
├── support/chat/     MercenaryApplicationConversationPolicy (mới — bean thứ hai của `ConversationAccessPolicy`, MATCH-001 §4.14);
│                     RealtimeFrame (sửa — + ApplicationUpdated, §4.8)
├── support/reputation/ TimeBandPenaltyPolicy (+ hằng MERCENARY_WITHDRAWAL), VerificationRefresher (sửa — §4.10)
├── validation/       ValidMercenarySearch, UniquePositions, ValidExternalMatchSchedule (+ validator) (mới);
│                     OneOf, ValidSkillLevels, ValidChatCursor (tái dùng)
├── dto/request/      CreateRecruitmentsRequest (+ RecruitmentPositionRequest), AddRecruitmentsRequest,
│                     UpdateRecruitmentRequest, SearchRecruitmentsRequest, WithdrawApplicationRequest,
│                     CreateExternalMatchRequest, UpdateExternalMatchRequest,
│                     CreateMercenaryRatingRequest; ChatHistoryQuery, SendConversationMessageRequest, CreateReportRequest,
│                     ResolveDisputeRequest (tái dùng)
├── dto/response/     RecruitmentResponse (+ RecruitmentMatchResponse, VenueRevealResponse, AreaResponse),
│                     ApplicationResponse, ApplicantResponse (+ ReputationSummaryResponse), ApprovalResponse,
│                     WithdrawalResponse, ExternalMatchResponse, ExternalMatchCancellationResponse,
│                     MercenaryRatingResponse,
│                     MercenaryRatingsResponse, MercenaryReportResponse, UserReputationResponse,
│                     TeamMercenaryRatingResponse, AdminMercenaryDisputeResponse, AdminMercenaryDisputeDetailResponse (mới);
│                     ConversationResponse (+ PartyResponse), ConversationMessageResponse, ChatHistoryResponse (tái dùng MATCH-001)
└── mapper/           MercenaryMapper, MercenaryReputationMapper (MapStruct, mới); ConversationMapper (sửa — khối mercenary); MapperSupport (tái dùng)

api/           com.phananh.arenaflow.api
├── mercenary/        MatchRecruitmentController, RecruitmentController, ApplicationController,
│                     TeamMercenaryController, ProfileMercenaryController, UserReputationController,
│                     ExternalMatchController, AdminMercenaryDisputeController (mới)
├── realtime/         StompAuthChannelInterceptor, StompChatRealtimeGateway (sửa — §4.8)
└── scheduler/        MatchLifecycleScheduler (sửa — §4.9), VerificationScheduler (sửa — §4.10)
```

Thành phần dùng chung mới / sửa → developer MUST chạy `reuse-scan` trước khi tạo: `PositionConverter`, `MercenarySql` (biểu thức trận của tin, trạng thái hiệu lực, thống kê cá nhân), `MercenaryApplicationConversationPolicy` (cài `ConversationAccessPolicy` của MATCH-001), `VerificationRefresher` (gộp đội + cá nhân), `TimeBandPenaltyPolicy.MERCENARY_WITHDRAWAL`, `@ValidMercenarySearch` (so `@ValidMatchSearch` / `@ValidGeoQuery`), `@ValidExternalMatchSchedule` (so `@ValidMatchSchedule` của MATCH-001), `MercenaryAccountCleanup`.

## 3. Controller Layer

Quy ước như MATCH-001 §3 (`@Validated`, `ResponseEntity<ApiResponse<…>>`, `@CurrentUser UUID currentUserId`, 201 không `Location`).

| Controller | `@RequestMapping` | Endpoint (`API_Spec.md`) | Service |
|---|---|---|---|
| `MatchRecruitmentController` | `/api/v1/matches/{matchId}/mercenary-recruitments` | 2.1 | `MercenaryRecruitmentService` |
| `TeamMercenaryController` | `/api/v1/teams/{teamId}` (`/mercenary-recruitments`, `/mercenary-reports`, `/mercenary-ratings`, `/external-matches`) | 2.2, 2.21, 2.24, 2.28, 2.29 | `MercenaryRecruitmentService`, `MercenaryReportService`, `UserReputationService`, `ExternalMatchService` |
| `RecruitmentController` | `/api/v1/mercenary-recruitments` | 2.3–2.8 | `MercenaryRecruitmentService` (2.3–2.6), `MercenaryApplicationService` (2.7, 2.8) |
| `ProfileMercenaryController` | `/api/v1/profile` (`/mercenary-applications`, `/mercenary-reports`) | 2.9, 2.22 | `MercenaryApplicationService`, `MercenaryReportService` |
| `ApplicationController` | `/api/v1/mercenary-applications/{applicationId}` | 2.10–2.13, 2.18–2.20 | `MercenaryApplicationService`, `MercenaryRatingService`, `MercenaryReportService` |
| `ConversationController` (MATCH-001 §3 — tái dùng) | `/api/v1/conversations/{conversationId}` | 2.14–2.17 (= MATCH-001 2.19, 2.16, 2.17, 2.18) | `ConversationService` (MATCH-001) |
| `UserReputationController` | `/api/v1/users/{userId}/reputation` | 2.23 | `UserReputationService` |
| `ExternalMatchController` | `/api/v1/external-matches/{externalMatchId}` | 2.30–2.33 | `ExternalMatchService` (2.30–2.32), `MercenaryRecruitmentService` (2.33) |
| `AdminMercenaryDisputeController` | `/api/v1/admin/mercenary-disputes` | 2.25–2.27 | `AdminMercenaryDisputeService` |

**Mã thành công & tiêu chí `api-convention.md` §5:**

| Endpoint | Thành công | Tiêu chí |
|---|---|---|
| 2.1, 2.7, 2.15, 2.18, 2.20, 2.28, 2.33 | 201 | B2 |
| 2.2–2.4, 2.8–2.10, 2.14, 2.16, 2.19, 2.21–2.26, 2.29, 2.30 | 200 | B1 |
| 2.5, 2.31 | 200 | B3 (`status` tính lại / `updatedAt`), B4 |
| 2.6 | 200 | B3 (`closedAt`, số đơn bị từ chối), B4 |
| 2.11 | 200 | B3 (`respondedAt`, trạng thái / số đã duyệt của tin) |
| 2.12, 2.17 | 204 | N1 |
| 2.13 | 200 | B3 (`withdrawnAt`, `reliabilityPenalty`, trạng thái tin) |
| 2.27 | 200 | B3 (`resolvedAt`, `resolvedByName`), B4 |
| 2.32 | 200 | B3 (`cancelledAt`, số tin đóng, số đơn bị từ chối) |

**Lý do chọn path/method:** 2.1 tạo tài nguyên con dưới kèo (tiền lệ `POST /matches/{id}/requests`, `/pairings`); 2.28 tạo trận ngoài dưới đội (tài nguyên của đội); 2.33 tạo tin dưới trận ngoài (cùng mẫu 2.1); thao tác trên từng tin / đơn / trận ngoài dùng `/mercenary-recruitments/{id}`, `/mercenary-applications/{id}`, `/external-matches/{id}` (tiền lệ `/match-requests/{id}`). 2.6, 2.11–2.13, 2.32 action sub-resource `POST` (tiền lệ `/match-requests/{id}/accept`, `/teams/{id}/disband`). 2.5, 2.31 cập nhật một phần → `PATCH`. Danh sách của chính người gọi đặt dưới `/profile` (tiền lệ USER-001). Chat theo **cuộc trò chuyện** `/conversations/{id}` — v2.0: dùng chung với chat hai đội (MATCH-001 v2.4 §3). Admin `PATCH …/resolve` như REPUTATION-001 2.8.

- 2.3: `@Valid @ModelAttribute SearchRecruitmentsRequest` (compact constructor: `page = 1`, `pageSize = 20`, `radiusKm = 3` khi có toạ độ) + `@ValidMercenarySearch`. 2.4: `lat`/`lng` tuỳ chọn (cặp).
- 2.2, 2.8, 2.9, 2.29: enum lọc qua `@RequestParam` (sai → 400). 2.21, 2.22: `MercenaryReportDirection` mặc định `RECEIVED`. 2.25: `DisputeStatusFilter` (REPUTATION-001).
- 2.16: `@Valid @ModelAttribute ChatHistoryQuery` (MATCH-001 §3, tái dùng).
- Không đổi `SecurityConfig`.

## 4. Service Layer

Không `@Transactional` mức class (USER-001 §4.6). Ranh giới: §4.13. Thứ tự guard: `Processing_Flow.md` §1.1 (nguồn duy nhất).

### 4.1. MercenaryRecruitmentService
```java
List<RecruitmentResponse> createRecruitments(UUID matchId, UUID callerId, CreateRecruitmentsRequest r);           // retrier — 2.1
List<RecruitmentResponse> addExternalRecruitments(UUID externalMatchId, UUID callerId, AddRecruitmentsRequest r);  // retrier — 2.33
PageResponse<RecruitmentResponse> listTeamRecruitments(UUID teamId, UUID callerId, UUID matchId, RecruitmentStatusFilter s, int page, int pageSize); // readOnly
PageResponse<RecruitmentResponse> search(UUID callerId, SearchRecruitmentsRequest r);                             // readOnly
RecruitmentResponse getRecruitment(UUID recruitmentId, UUID callerId, Double lat, Double lng);                   // readOnly
RecruitmentResponse updateQuantity(UUID recruitmentId, UUID callerId, UpdateRecruitmentRequest r);               // retrier
RecruitmentResponse close(UUID recruitmentId, UUID callerId);                                                    // retrier
```
Deps: `MercenaryRecruitmentRepository`, `MercenaryApplicationRepository`, `ExternalMatchRepository`, `UserRepository` (`findProfileArea`), `TeamGuard`, `MercenaryGuard`, `ConcurrencyConflictRetrier`, `MercenaryMapper`, `PageRequestFactory`, `ApplicationEventPublisher`, `Clock`.
- `createRecruitments`: `recruitmentRepository.findRecruitContext(matchId, teamId, callerId, now)` (một câu: trận + hai đội + trạng thái đội `teamId` + caller là captain ACTIVE + mảng vị trí đã có tin `OPEN`/`FILLED` của đội cho trận) → guard → `insertAll(matchId, null, teamId, callerId, positions[], quantities[], now)` (`INSERT … SELECT unnest … RETURNING`, id `uuidv7()` — ADR-0008 Sửa đổi 2026-11-09; bắt `DataIntegrityViolationException` của index vị trí → `MERCENARY_023` — NFS §4.1 ngoại lệ 1, câu native chạy ngay) → response dựng từ ctx + dòng trả về.
- `addExternalRecruitments`: `externalMatchRepository.findRecruitContext(externalMatchId, callerId, now)` → guard → `insertAll(null, externalMatchId, teamId, …)`.
- `search`: có toạ độ → `searchNearby` (hộp + Haversine trên sân của trận, VENUE-001 §5.2); không → `userRepository.findProfileArea(callerId)` → `searchByProvince`. Cả hai lọc tin `OPEN` của trận `g.confirmed AND g.start > now` (BR-ME07, BR-ME19), đội tuyển `ACTIVE` (BR-ME05), sắp xếp theo BR-ME03 (§5.2).
- `getRecruitment`: `findView(recruitmentId, callerId, now, lat, lng)` — một câu gồm cờ người xem → mapper ẩn/hiện địa điểm (§7).
- `updateQuantity`: `findWriteContext(recruitmentId, callerId, now)` → guard (tăng số lượng khi `now ≥ g.start` → `MERCENARY_013`) → `updateQuantity(id, q, expectedApproved, expectedNeeded, now)` (CAS, tự tính `FILLED`/`OPEN`) 0 dòng → `ConcurrencyConflictException` → response từ ctx + giá trị mới.
- `close`: `findWriteContext` → guard → `closeIfActive(id, now)` 0 dòng → retry → `applicationRepository.rejectPendingByRecruitment(id, now)` (RETURNING id) → `MercenaryRecruitmentClosedEvent(cause = CAPTAIN, rejectedApplicationIds)`.

### 4.2. MercenaryApplicationService
```java
ApplicationResponse apply(UUID recruitmentId, UUID callerId);                                           // retrier
PageResponse<ApplicantResponse> listApplicants(UUID recruitmentId, UUID callerId, ApplicationStatusFilter s, int page, int pageSize); // readOnly
PageResponse<ApplicationResponse> listMyApplications(UUID callerId, ApplicationStatusFilter s, int page, int pageSize); // readOnly
ApplicationResponse getApplication(UUID applicationId, UUID callerId);                                  // readOnly
ApprovalResponse approve(UUID applicationId, UUID callerId);                                            // retrier
void reject(UUID applicationId, UUID callerId);                                                         // retrier
WithdrawalResponse withdraw(UUID applicationId, UUID callerId, WithdrawApplicationRequest r);           // retrier
```
Deps: `MercenaryApplicationRepository`, `MercenaryRecruitmentRepository`, `ConversationRepository`, `MercenaryGuard`, `ReputationService`, `VerificationRefresher`, `ChatRealtimeGateway`, `ConcurrencyConflictRetrier`, `MercenaryMapper`, `PageRequestFactory`, `ApplicationEventPublisher`, `Clock`.
- `apply`: `findApplyContext(recruitmentId, callerId, now)` (caller đã xoá + tin + trận + đội tuyển + caller là thành viên ACTIVE của đội trong trận + caller có đơn đang hoạt động ở tin + caller có đơn `APPROVED` trùng giờ) → guard (`callerDeleted` → `USER_001` đầu chuỗi) → `MercenaryApplication.builder()` `saveAndFlush` (bắt `idx_mercenary_application_active` → `MERCENARY_009`; NFS §4.1 ngoại lệ 1) → `Conversation.builder()` (`contextType = MERCENARY_APPLICATION`, `applicationId`, `matchId` / `externalMatchId` theo tin, bên A = `TEAM`/đội tuyển, bên B = `USER`/caller) `save` → `MercenaryApplicationSubmittedEvent`.
- `approve`: `findContext(applicationId, callerId, now)` → guard → `recruitmentRepository.incrementApproved(recruitmentId, expectedApproved, expectedNeeded, now)` 0 dòng → retry → `applicationRepository.approveIfPending(id, now)` 0 dòng → `ConcurrencyConflictException` (rollback cả tăng số) → retry → frame `APPLICATION_UPDATED` → `MercenaryApplicationApprovedEvent`.
- `reject`: `findContext` → guard → `rejectIfPending(id, now)` 0 dòng → retry → frame → `MercenaryApplicationRejectedEvent(cause = CAPTAIN)`.
- `withdraw`: `findContext` → guard (so `r.expectedStatus()` với trạng thái **hiệu lực** ở mọi lần thử) →
  - `PENDING`: `withdrawPending(id, now)` 0 dòng → retry. `reliabilityPenalty = 0`.
  - `APPROVED`: `recruitmentRepository.decrementApproved(recruitmentId, now)` (khoá tin trước) → `withdrawApproved(id, now)` 0 dòng → `ConcurrencyConflictException` → `delta = TimeBandPenaltyPolicy.MERCENARY_WITHDRAWAL.penaltyFor(g.start, now)` (âm) → `reputationService.adjustUserReliability(callerId, new ReliabilityChange(delta, MERCENARY_WITHDRAWN_AFTER_APPROVED, applicationId, true))` → `verificationRefresher.refreshUsersAfterCommit({callerId})`. Response `reliabilityPenalty = -delta` (số dương).
  - → frame `APPLICATION_UPDATED` → `MercenaryApplicationWithdrawnEvent(previousStatus, cause = APPLICANT)`.
- `listApplicants`: `recruitmentRepository.findWriteContext` (quyền captain) → `findApplicants` (tóm tắt uy tín ứng viên — §5.4 dạng `LATERAL`, cùng câu) → count.

### 4.3. MercenaryGuard (`application/support/mercenary`, `@Component`)
Một nguồn cho kiểm trên các `…ContextView` (không truy vấn thêm); thứ tự gọi: `Processing_Flow.md` §1.1.
```java
void requireNotDeleted(boolean callerDeleted);                  // USER_001 (2.7, 2.15, 2.18, 2.20)
void requireCaptain(String teamStatus, boolean callerCaptain, boolean allowInactive); // DISBANDED → TEAM_016; INACTIVE && !allowInactive → TEAM_015; !callerCaptain → TEAM_002
void requireRecruitableMatch(RecruitContextView ctx, OffsetDateTime now); // MATCH_009 → MERCENARY_003 → MERCENARY_004 → MERCENARY_013
void requireActive(String effectiveRecruitmentStatus);          // CLOSED/EXPIRED → MERCENARY_007
void requireOpen(String effectiveRecruitmentStatus);            // + FILLED → MERCENARY_006
void requireNotStarted(OffsetDateTime gameStart, OffsetDateTime now); // now ≥ start → MERCENARY_013
void requireEligibleApplicant(boolean memberOfGameTeams, boolean overlappingApproval); // MERCENARY_010 → MERCENARY_011
MercenarySide resolveSide(boolean callerApplicant, boolean callerCaptain); // cả hai → MERCENARY_019; không bên nào → MERCENARY_014
```
`MercenarySide` = `CAPTAIN` | `APPLICANT`.

### 4.4. MercenaryRatingService
```java
MercenaryRatingResponse createRating(UUID applicationId, UUID callerId, CreateMercenaryRatingRequest r);  // retrier MATCH_014
MercenaryRatingsResponse getRatings(UUID applicationId, UUID callerId);                                  // readOnly
```
- `createRating`: `ratingRepository.findMercenaryRatingContext(applicationId, callerId, now)` (caller đã xoá + đơn + trận của tin (`g.ended`) + đội tuyển + cờ caller + hai đánh giá hiện có của đơn) → guard → `Rating.builder()`: `CAPTAIN` → `raterType = TEAM`, `raterId = teamId`, `rateeType = INDIVIDUAL`, `rateeId = applicantId`; `APPLICANT` → ngược lại, `isNoShow = false` bắt buộc; `matchId` / `externalMatchId`, `applicationId`, `createdByUserId = callerId` → `saveAndFlush` (bắt `idx_rating_mercenary_unique` → `MERCENARY_017`) →
  - `CAPTAIN` chấm sao: `reputationService.applyMercenaryCompletion(applicantId, matchId, externalMatchId, score == 5 ? 1 : 0, ratingId)` (BR-ME15 — hoàn thành + 5 sao trong một câu) + `refreshUsersAfterCommit({applicantId})`.
  - `CAPTAIN` "Không đến", `APPLICANT`: không ghi điểm, không refresh.
  - → `MercenaryRatingCreatedEvent`. Không chuyển trận `COMPLETED` (BR-ME13).
- `getRatings`: ctx → side → `given` = dòng `raterType` khớp side; `received` = dòng kia **chỉ khi** `given != null` (BC-R03/R04).

### 4.5. MercenaryReportService
```java
MercenaryReportResponse createReport(UUID applicationId, UUID callerId, CreateReportRequest r);           // retrier MATCH_014
PageResponse<MercenaryReportResponse> listTeamReports(UUID teamId, UUID callerId, MercenaryReportDirection d, int page, int pageSize); // readOnly
PageResponse<MercenaryReportResponse> listMyReports(UUID callerId, MercenaryReportDirection d, int page, int pageSize);             // readOnly
```
- `createReport`: `reportRepository.findMercenaryReportContext(applicationId, callerId, reportType, now)` (caller đã xoá + đơn + trận (`g.ended`) + đội tuyển + cờ caller + `hasMessages` + đã report loại này) → guard: ngữ cảnh **tham gia** = `g.ended` **và** đơn `APPROVED` → 4 loại; ngược lại `hasMessages` → chỉ `BAD_COMMUNICATION` → `Report.builder()` (`reporterType/reportedType` theo side, `matchId` / `externalMatchId`, `applicationId`, `status = AUTO_ACCEPTED`, `reliabilityPenalty = 0`) `saveAndFlush` (bắt `idx_report_unique` / `idx_report_external_unique` → `MERCENARY_022`) → `CAPTAIN`: `reputationService.applyUserReportPenalty(applicantId, type, reportId)`; `APPLICANT`: `applyReportPenalty(teamId, type, reportId)` (đội — B9) → `report.setReliabilityPenalty(points)` → refresh bên bị report → `MercenaryReportCreatedEvent`.
- `listTeamReports`: `teamGuard.requireActiveCaptain(teamId, callerId)` (BR-R10) → `findMercenaryReports("TEAM", teamId, direction, limit, offset)` / `countMercenaryReports`. `listMyReports`: `("INDIVIDUAL", callerId, …)`. Mapper: trạng thái hiệu lực + `canDispute` dùng chung `ReportStatusResolver` (REPUTATION-001 §4.10); `SENT` → `reliabilityPenalty = null`, `dispute.evidence = null`.
- Tranh chấp report bùng: **endpoint REPUTATION-001 2.5** (mở rộng — §4.12).

### 4.6. UserReputationService, AdminMercenaryDisputeService
```java
UserReputationResponse getUserReputation(UUID userId);                                                   // readOnly
PageResponse<TeamMercenaryRatingResponse> listTeamMercenaryRatings(UUID teamId, int page, int pageSize); // readOnly
PageResponse<AdminMercenaryDisputeResponse> listDisputes(DisputeStatusFilter s, int page, int pageSize);  // readOnly
AdminMercenaryDisputeDetailResponse getDispute(UUID disputeId);                                           // readOnly
AdminMercenaryDisputeDetailResponse resolveDispute(UUID disputeId, UUID adminId, ResolveDisputeRequest r); // retrier MATCH_014
```
- `getUserReputation`: `UserRepository.findReputation(userId, now)` (§5.4) → rỗng / đã xoá → `USER_001` → mapper dùng chung quy tắc làm tròn / `reliabilityLevel` / `isNewcomer` của `ReputationMapper` (REPUTATION-001 §4.5).
- `listTeamMercenaryRatings`: `TeamRepository.existsById` → `TEAM_001` → `RatingRepository.findMercenaryRatingsOfTeam` / count — chỉ đánh giá mà đội đã đánh giá lại mercenary trong cùng đơn (`EXISTS rating` phía đội cùng `application_id` — BR-ME13).
- `resolveDispute`: `disputeRepository.resolveMercenaryIfPending(...)` (chỉ tranh chấp có `report.application_id IS NOT NULL`) → rỗng → `existsMercenaryDispute(disputeId)` (`EXISTS dispute d JOIN report r … AND r.application_id IS NOT NULL`) → `DISPUTE_001` / `DISPUTE_002` (M5) → `applyDecision` (trả thêm `reportedType`) → `REJECTED`: `INDIVIDUAL` → `refundUserReportPenalty`; `TEAM` → `refundReportPenalty` + refresh bên đó → `DisputeResolvedEvent` (payload tổng quát — §4.12) → `adminAuditLog.afterCommit("RESOLVE_DISPUTE", adminId, "DISPUTE", disputeId)` → đọc lại chi tiết. Developer gom phần chung với `AdminDisputeServiceImpl` khi reuse-scan.

### 4.7. Chat theo đơn — ngữ cảnh `MERCENARY_APPLICATION` của lõi cuộc trò chuyện (MATCH-001 §4.14)

v2.0: lõi chat (bảng, entity, `ConversationService`, `ConversationAccessPolicy`, endpoint, kênh realtime, cursor, đánh dấu đọc) chuyển sang MATCH-001 (v2.4) và dùng chung cho chat hai đội luồng A/B (ADR-0014 Sửa đổi 2026-11-15, user chọn 2026-11-15 — một API / topic / job xoá cho mọi chat). Mã này chỉ thêm phần riêng của đơn ứng tuyển:

- **Tạo cuộc trò chuyện** khi ứng tuyển (2.7): `Conversation.builder()` `contextType = MERCENARY_APPLICATION`, `applicationId`, `matchId` **hoặc** `externalMatchId` (theo tin), bên A = `TEAM` đội tuyển, bên B = `USER` ứng viên → `save` cùng transaction với đơn (như v1.0).
- **Khối cột mercenary của `ConversationRepository.findContext`** (một câu chung — MATCH-001 §5.2): `LEFT JOIN mercenary_application a ON c.context_type = 'MERCENARY_APPLICATION' AND a.id = c.application_id JOIN mercenary_recruitment r <GAME_JOIN> LEFT JOIN team t ON t.id = c.party_a_id … LEFT JOIN team_member cm (đội trưởng ACTIVE hiện tại của đội tuyển) LEFT JOIN "user" cu … LEFT JOIN "user" ap ON ap.id = c.party_b_id` → `applicationId`, `applicationEffectiveStatus` (`MercenarySql`), `externalMatchId`, `source`, đội tuyển (`name`, `avatarUrl`, `captainName`, `captainDeleted`), ứng viên (`displayName`, `avatarUrl`, `deleted`), `callerMercenaryCaptain` (`COALESCE(cm.user_id = :callerId, false)`), `callerApplicant` (`ap.id = :callerId`). Cột / join như `findContext` của v1.0 (§5.2), đặt trong khối `LEFT JOIN … ON c.context_type = 'MERCENARY_APPLICATION'`.
- **`MercenaryApplicationConversationPolicy`** (`ConversationAccessPolicy`, `contextType = MERCENARY_APPLICATION`): bên A (`TEAM`) = caller là đội trưởng ACTIVE hiện tại của đội tuyển (đội `INACTIVE` vẫn nhắn); không thì đội tuyển `DISBANDED` và caller = `team.disbanded_by` → bên A **chỉ xem** (`viewerReadOnly = true`, `canSend = false` — user chốt 2026-11-15, cùng quy tắc `TeamMatchConversationPolicy` của MATCH-001 §4.14); thành viên khác của đội đã giải tán → không tham gia; bên B (`USER`) = caller là ứng viên (kiểm trước bên A chỉ xem — ứng viên không thể là thành viên đội tuyển, MERCENARY_010). `canSend` = ¬`viewerReadOnly` ∧ đơn **hiệu lực** ∈ {`PENDING`, `APPROVED`} (BR-ME16, user chốt 2026-11-13): `APPROVED` gửi được cả sau khi trận kết thúc / bị huỷ, tới khi tin bị xoá theo hạn lưu; `PENDING` của tin hiệu lực `CLOSED`/`EXPIRED` là `REJECTED` hiệu lực → chỉ xem. Không xét trạng thái trận. `expiryDue` luôn `false` (không có nhánh lazy — `send` của ngữ cảnh này luôn trả `Done`).
- **Tên đội trưởng trong chat luôn hiện** (BR-ME04 ngoại lệ, user chốt 2026-11-14): `PartyResponse.captainName` và `senderName` không phụ thuộc trạng thái đơn — quy tắc ẩn trước khi duyệt chỉ áp cho tên sân / địa chỉ / toạ độ ở tin, đơn (§7).
- `ConversationMapper` (MATCH-001 §7): khối `MERCENARY_APPLICATION` → `parties` = [đội tuyển (`TEAM`, `captainName`), ứng viên (`USER`, `captainName = null`)], `applicationId`, `applicationStatus`, `source`, `matchId` / `externalMatchId`; field `TEAM_MATCH` (`matchType`, `requestId`, `requestStatus`, `pairingId`, `pairingStatus`) = `null`.
- **`findMine` (MATCH-001 2.20, §5.2)** — thêm khi code mã này (MATCH-001 đợt 1 chỉ có phần `TEAM_MATCH` — MATCH-001 §1.1): giờ bắt đầu `external_match.match_start_time` (`LEFT JOIN external_match ON e.id = c.external_match_id`) bên cạnh `match.match_start_time`; tên / ảnh bên `USER` (ứng viên: `"user"` `display_name`, `avatar_url`, `deleted_at`); khối cột mercenary dùng chung với `findContext`. Vẫn một câu trang — 2.20 giữ 2 truy vấn.
- `send`, `history`, `markRead`, `getConversation`, `requireSubscribable`: MATCH-001 §4.14 (v1.0 của mã này mô tả cùng luồng; nay một nơi).

### 4.8. Kênh realtime — frame của đơn (MATCH-001 §4.15)

| Thành phần | Sửa | Lý do |
|---|---|---|
| `RealtimeFrame` | + `record ApplicationUpdated(UUID applicationId, MercenaryApplication.Status status, UUID recruitmentId, MercenaryRecruitment.Status recruitmentStatus, int quantityApproved)` | Frame `APPLICATION_UPDATED` (`API_Spec.md` §1.7) |
| `ChatRealtimeGateway` | Không thêm method (v1.0 thêm `publishConversationAfterCommit` — v2.0 bỏ: MATCH-001 v2.4 chỉ còn `publishAfterCommit(conversationId, frame)`) | Một topic cho mọi cuộc trò chuyện |
| `StompAuthChannelInterceptor`, `StompChatRealtimeGateway` | Không sửa ở mã này — mẫu `/topic/conversations/{id}` là mẫu duy nhất từ MATCH-001 v2.4 | — |

Frame `APPLICATION_UPDATED` khi **người dùng** duyệt / từ chối / rút (2.11–2.13): `realtimeGateway.publishAfterCommit(conversationId của đơn, ApplicationUpdated)`. Chuyển trạng thái hệ thống (job, huỷ trận ngoài, xoá tài khoản) không đẩy frame.

### 4.9. MercenaryLifecycleJobService (ADR-0012)
```java
int closeDueRecruitments();     // mỗi lô một TransactionTemplate
int rejectOrphanPending();      // mỗi lô một TransactionTemplate
int notifyCancelledGames();     // mỗi lô một TransactionTemplate
```
Gọi từ `MatchLifecycleScheduler` (MATCH-001 §4.10, v2.3) **sau** `archiveFinishedMatches()` trong cùng lần chạy 5 phút; lỗi log `ERROR`, không chặn tác vụ khác. Không thêm job định kỳ → `spring.task.scheduling.pool.size` giữ 4.
- `closeDueRecruitments`: mỗi lô `claimDue(now, batchSize)` (`FOR NO KEY UPDATE OF r SKIP LOCKED`, trả `id, matchId, externalMatchId, teamId, cause`) → `markClosedOrExpired(closedIds, expiredIds, now)` → `rejectPendingByRecruitmentIds(ids, now)` RETURNING `RejectedApplicationView(id, recruitmentId)` → `MercenaryRecruitmentClosedEvent(cause = MATCH_CANCELLED | EXPIRED, rejectedApplicationIds)` mỗi tin.
- `rejectOrphanPending` (MINOR 5): đơn `PENDING` trên tin đã `CLOSED`/`EXPIRED` (ứng tuyển commit ngay sau khi tin đóng) → `REJECTED` + `MercenaryApplicationRejectedEvent(cause = RECRUITMENT_CLOSED)`. SQL: `Processing_Flow.md` §3.12.
- `notifyCancelledGames` (N-M2 — BR-ME09 "mercenary nhận thông báo" khi trận bị huỷ, kể cả tin đã `CLOSED` từ trước): mỗi lô `applicationRepository.markGameCancelNotified(now, now − cancel-notice-lookback-days, batchSize)` (đơn `APPROVED`, `game_cancel_notified_at IS NULL` của kèo / trận ngoài **bị huỷ có `match_end_time > lookbackStart`** → đánh dấu; claim `FOR NO KEY UPDATE OF a2 SKIP LOCKED`; RETURNING `GameCancelNoticeView(id, matchId, externalMatchId)` — SQL: `Processing_Flow.md` §3.12 bước 3) → gom theo trận → `MercenaryGameCancelledEvent` mỗi trận. Đây là đường **duy nhất** báo huỷ kèo trên hệ thống cho mercenary: không nghe `MatchCancelledEvent` (listener chạy sau commit trên luồng request sẽ thêm truy vấn vào MATCH-001 2.5/2.10 — đang 6/W6). Trận ngoài: 2.32 đánh dấu và phát ngay (§4.15). Cột `game_cancel_notified_at` (§5.1) chống báo trùng giữa 2.32, các lần job và nhiều instance.

### 4.10. ReputationService & huy hiệu — mở rộng REPUTATION-001 §4.6, §4.8 (ADR-0013)

```java
// thêm vào ReputationService (mọi method MANDATORY)
void applyMercenaryCompletion(UUID userId, UUID matchId, UUID externalMatchId, int ratingBonus, UUID ratingId); // BR-ME15
int  applyUserReportPenalty(UUID userId, Report.ReportType type, UUID reportId);            // trả điểm thực trừ
void refundUserReportPenalty(UUID userId, Report.ReportType type, int points, UUID reportId);
public enum ReliabilityReason { …, MERCENARY_COMPLETED, MERCENARY_WITHDRAWN_AFTER_APPROVED }   // + giá trị
public record TimeBandPenaltyPolicy(…) { public static final TimeBandPenaltyPolicy MERCENARY_WITHDRAWAL = new TimeBandPenaltyPolicy(-1, -3, -5); }
```
- `applyMercenaryCompletion` (BR-ME15, user sửa 2026-11-14): một câu `UserRepository.applyMercenaryCompletion(userId, matchId, externalMatchId, ratingBonus, …hằng ReputationRules)` — khoá dòng `"user"` (CTE `FOR NO KEY UPDATE`), `+COMPLETION_POINTS`, chuỗi +1 nếu không có report `reported_type = 'INDIVIDUAL'` chưa bị bác của người đó trong trận (theo `match_id` hoặc `external_match_id`) ngược lại về 0, thưởng mốc 5/10, reset mọi `report_count_*` khi chạm mốc, `+ratingBonus` (5 sao) — cùng công thức `applyCompletion` của đội (REPUTATION-001 §5.2) với `completes = true`, `bonus = ratingBonus`; `RETURNING old_score, new_score` → `ReliabilityScoreChangedEvent(USER, …, reason = MERCENARY_COMPLETED)` khi điểm đổi; 0 dòng → `IllegalStateException`. Bảng kiểm: `Processing_Flow.md` §3.11.
- **Không** còn ghi điểm mercenary trong `applyMatchCompletion` của REPUTATION-001 (thay đổi so với bản trước của thiết kế này — REPUTATION-001 2.1 giữ 5 truy vấn).
- `applyUserReportPenalty` / `refundUserReportPenalty`: như bản đội, trên `"user"` (`UserRepository.applyReportPenalty` / `refundReportPenalty`). `ReportPenaltyPolicy` dùng chung.
- `adjustUserReliability` (MATCH-001) dùng cho rút lui (`breaksCleanStreak = true`).
- **`VerificationRefresher`** (đổi tên `TeamVerificationRefresher` — REPUTATION-001 §4.8, chưa code): một `TransactionSynchronization` mỗi transaction giữ hai tập id (`teams`, `users`); `afterCommit` → `refreshNow`: tối đa **1** câu đội + **1** câu cá nhân (bỏ câu khi tập rỗng). `UserRepository.refreshVerification(ids, now, …ngưỡng)` cùng mẫu đội, điều kiện cá nhân BR-R04 (§5.2). Event `UserVerificationChangedEvent(userId, verified, occurredAt)`.
- Job đối soát hằng ngày (`VerificationJobService.reconcileAll()`, đổi tên từ `TeamVerificationJobService`) chạy thêm vòng cá nhân — cập nhật thay đổi số điện thoại (SRS §8.2). Cùng `VerificationScheduler`, không thêm luồng.

### 4.11. Domain event (`application/event/mercenary`, ADR-0011)

| Event | Payload | Phát khi | Người nghe dự kiến (NOTIFICATION-001) |
|---|---|---|---|
| `MercenaryApplicationSubmittedEvent` | `applicationId, recruitmentId, matchId, externalMatchId, teamId, applicantId, occurredAt` | 2.7 | `MERCENARY_APPLICATION` cho đội trưởng hiện tại |
| `MercenaryApplicationApprovedEvent` | như trên | 2.11 | Ứng viên |
| `MercenaryApplicationRejectedEvent` | như trên + `cause` (`CAPTAIN` \| `RECRUITMENT_CLOSED`) | 2.12; job quét đơn mồ côi | Ứng viên |
| `MercenaryApplicationWithdrawnEvent` | như trên + `previousStatus`, `cause` (`APPLICANT` \| `ACCOUNT_DELETED`) | 2.13; xoá tài khoản (§4.14) | Đội trưởng ("Mercenary rút lui" khi `APPROVED`; xoá tài khoản: mọi đơn) |
| `MercenaryRecruitmentClosedEvent` | `recruitmentId, matchId, externalMatchId, teamId, cause` (`CAPTAIN` \| `MATCH_CANCELLED` \| `EXTERNAL_MATCH_CANCELLED` \| `EXPIRED`)`, rejectedApplicationIds, occurredAt` | 2.6, 2.32, job | Ứng viên có đơn bị từ chối (theo `rejectedApplicationIds` — MINOR 12). **Không** báo mercenary `APPROVED` — việc đó thuộc `MercenaryGameCancelledEvent` (tránh báo trùng) |
| `MercenaryGameCancelledEvent` | `matchId` \| `externalMatchId` (một trong hai khác `null`)`, applicationIds` (đơn `APPROVED` vừa được đánh dấu đã báo)`, occurredAt` | 2.32 (trận ngoài); job `notifyCancelledGames` (kèo, và đơn trận ngoài được duyệt đồng thời lúc huỷ) | Mọi mercenary `APPROVED` của trận bị huỷ, kể cả của tin đã `CLOSED` từ trước (BR-ME09, N-M2) |
| `MercenaryRatingCreatedEvent` | `ratingId, applicationId, direction, occurredAt` | 2.18 | "Rating nhận" (REPUTATION-001 `[cần xác nhận #11]`) |
| `MercenaryReportCreatedEvent` | `reportId, applicationId, direction, reportType, occurredAt` | 2.20 | "Report nhận"; `NOSHOW`: "Bạn có 24h để tranh chấp" |
| `UserVerificationChangedEvent` | `userId, verified, occurredAt` | `VerificationRefresher` | Cấp / thu hồi huy hiệu cá nhân |

Mỗi thao tác một event nghiệp vụ mỗi đối tượng (quy tắc 7). `rejectedApplicationIds`, `applicationIds` là danh sách id nhỏ (≤ số đơn chờ của một tin / số mercenary của một trận) — ngoại lệ có chủ đích của ADR-0011 quy tắc 2 (ADR-0011 Sửa đổi 2026-11-14) ("không danh sách lớn") để người nghe không truy vấn lại đơn đã đổi trạng thái. Gửi tin không phát event (realtime qua gateway). Payload id + enum + thời điểm, không PII.

### 4.12. Sửa thành phần REPUTATION-001 (thiết kế gốc cập nhật cùng thay đổi này — `REPUTATION-001/CHANGELOG.md` v1.1, `Component_Class_Design.md` §14)

| Thành phần | Sửa | Lý do |
|---|---|---|
| `RatingContextView` (`authorRated`, `opponentRated`), `MatchRatingRowView`, `findByMatchIdAndRaterType…` (2.1, 2.2, 2.7) | Thêm `application_id IS NULL` | Đánh giá đội trưởng → mercenary có `rater_type = 'TEAM'` — không được tính là "đội đã đánh giá trận" |
| `ReportContextView`, `findTeamReports` / `countTeamReports` (2.3, 2.4), `findAdminDisputes` / `countAdminDisputes` / `findAdminDisputeDetail` / `resolveIfPending` (2.6–2.8) | Thêm `application_id IS NULL` | Giữ hình dạng response REPUTATION-001 |
| `AdminDisputeServiceImpl.resolveDispute` (2.8) — CAS 0 dòng | `existsById` → `existsTeamDispute(id)` (`EXISTS dispute d JOIN report r … AND r.application_id IS NULL`) → `false` → `DISPUTE_001` (M5) | Id của tranh chấp mercenary trả 404, không 409 |
| `TeamReputationSql.STATS` — sao trung bình đội | Thêm `application_id IS NULL` | BR-ME13 |
| `TeamReputationSql.STATS` — `noshow`; `applyCompletion` — trận sạch của đội | `applyCompletion` (REPUTATION-001 §5.2 chỉ có mệnh đề đối thủ thật): **đổi** điều kiện reporter của câu `clean` thành (giữ nguyên ngoặc): `AND ((r.reporter_type = 'TEAM' AND r.reporter_id IN (m.author_team_id, m.confirmed_opponent_team_id)) OR EXISTS (SELECT 1 FROM mercenary_application a WHERE a.id = r.application_id AND a.status = 'APPROVED'))` — cột `report.application_id` có từ V9 (§5.1); cùng câu, không thêm truy vấn. Kết quả: trận sạch xét report của đối thủ thật + mercenary `APPROVED` của trận; bỏ report qua chat của ứng viên bị từ chối / đã rút; trận ngoài hệ thống không vào (`match_id` = `NULL`) (user chốt 2026-11-15, review 2026-11-15). `STATS`: **giữ** report `reported_type = 'TEAM'` của kèo từ mercenary; `noshow` giữ mọi report bùng | BR-ME14 (user chốt 2026-11-14). Report của trận ngoài có `match_id = NULL` → không vào thống kê đội |
| `DisputeService.createDispute` + `DisputeContextView` (2.5) | + `reportedType`, `callerIsReportedUser`; `INDIVIDUAL` → caller phải là người bị report (`REPORT_009`); không `JOIN match` bắt buộc | Mercenary tranh chấp; report trận ngoài |
| `ReportRepository.applyDecision` | `RETURNING` thêm `reported_type` | Hoàn điểm đúng bảng |
| `ReportDisputedEvent`, `DisputeResolvedEvent`, `TeamReportedEvent` | `reporterTeamId/reportedTeamId` → `reporterType, reporterId, reportedType, reportedId` | Bên bị report có thể là cá nhân |
| `TeamVerificationRefresher` → `VerificationRefresher`; `TeamVerificationJobService` → `VerificationJobService`; `TeamVerificationScheduler` → `VerificationScheduler` | §4.10 | Huy hiệu cá nhân |
| `ScoreChangeView` | + `subjectType` | Event điểm cá nhân dùng chung projection |

### 4.13. Transaction

| Method | Ranh giới |
|---|---|
| `listTeamRecruitments`, `search`, `getRecruitment`, `listApplicants`, `listMyApplications`, `getApplication`, `getRatings`, `listTeamReports`, `listMyReports`, `getUserReputation`, `listTeamMercenaryRatings`, `listDisputes`, `getDispute`, `listTeamExternalMatches`, `getExternalMatch` | `@Transactional(readOnly = true)` |
| Chat (`getConversation`, `history`, `requireSubscribable`, `send`, `markRead`) | MATCH-001 `Component_Class_Design.md` §4.12 (v2.0 của mã này: `send` chạy qua retrier `MATCH_014` cho mọi ngữ cảnh — ngữ cảnh đơn không có nhánh lazy nên chỉ thử lại deadlock) |
| `createRecruitments`, `addExternalRecruitments`, `updateQuantity`, `close`, `apply`, `approve`, `reject`, `withdraw`, `createRating`, `createReport`, `resolveDispute`, `createExternalMatch`, `updateExternalMatch`, `cancelExternalMatch` | Không annotate; `concurrencyConflictRetrier.execute(MATCH_014, …)` (thử lại CAS 0 dòng, `@Version`, deadlock `40P01`) |
| `ReputationService.*`, `ChatRealtimeGateway.publish*AfterCommit`, `MercenaryAccountCleanup.withdrawForDeletedApplicant` | `MANDATORY` |
| `VerificationRefresher.refreshNow`, mỗi lô job | `TransactionTemplate` (`REQUIRES_NEW` cho `refreshNow`) |

Không dùng `MatchWriteOutcome` (MATCH-001 §4.7): không có nhánh commit-rồi-báo-lỗi.

### 4.14. MercenaryAccountCleanup — xoá tài khoản ứng viên (BR-ME18; sửa luồng USER-001 v4.1)

```java
void withdrawForDeletedApplicant(UUID userId, OffsetDateTime now);   // application/support/mercenary, @Component, MANDATORY
```
- Gọi từ `ProfileServiceImpl.deleteAccount` (USER-001 §4.2) trong cùng lần thử retrier, sau bước rời đội và trước `softDelete`. Luồng, số truy vấn: `Processing_Flow.md` §3.16.
- **Thứ tự khoá tin → đơn** (M1, ADR-0007 Sửa đổi 2026-11-14): `MercenaryRecruitmentRepository.decrementApprovedOfApplicant(userId, now)` (`UPDATE … RETURNING` → `List<ApprovedCountView(recruitmentId, n)>`) rồi `MercenaryApplicationRepository.withdrawActiveOfApplicant(userId, now)` (`UPDATE … RETURNING` → `List<WithdrawnApplicationView(id, recruitmentId, teamId, matchId, externalMatchId, wasApproved)>`); đếm `wasApproved` theo tin khác `n` → `ConcurrencyConflictException`.
- Không ghi uy tín, không refresh huy hiệu, không đẩy frame. Phát `MercenaryApplicationWithdrawnEvent(cause = ACCOUNT_DELETED)` mỗi đơn.

### 4.15. ExternalMatchService — trận ngoài hệ thống (BR-ME19)

```java
ExternalMatchResponse createExternalMatch(UUID teamId, UUID callerId, CreateExternalMatchRequest r);        // retrier — 2.28
PageResponse<ExternalMatchResponse> listTeamExternalMatches(UUID teamId, UUID callerId, ExternalMatchStatusFilter s, int page, int pageSize); // readOnly — 2.29
ExternalMatchResponse getExternalMatch(UUID externalMatchId, UUID callerId);                                 // readOnly — 2.30
ExternalMatchResponse updateExternalMatch(UUID externalMatchId, UUID callerId, UpdateExternalMatchRequest r); // retrier — 2.31
ExternalMatchCancellationResponse cancelExternalMatch(UUID externalMatchId, UUID callerId);                  // retrier — 2.32
```
Deps: `ExternalMatchRepository`, `MercenaryRecruitmentRepository`, `MercenaryApplicationRepository`, `TeamGuard`, `MercenaryGuard`, `VenueSelectionService` (VENUE-001 — `requireSelectable`, `markSelected`), `ConcurrencyConflictRetrier`, `MercenaryMapper`, `PageRequestFactory`, `ApplicationEventPublisher`, `Clock`.
- **Mô hình:** bảng riêng `external_match` (§5.1), không thêm loại vào `match` — kèo MATCH-001 có vòng đời, tìm kèo, thống kê uy tín đội, job hết hạn / lưu trữ riêng; đưa trận ngoài vào `match` sẽ làm mọi truy vấn đó phải loại trừ (vd Completion Rate đội — BR-ME19 "đội không được cộng / trừ"). Tin / cuộc trò chuyện / đánh giá / report trỏ `match_id` **hoặc** `external_match_id` (CHECK XOR); biểu thức `MercenarySql.GAME_*` thống nhất cách đọc.
- `createExternalMatch`: `teamGuard.requireActiveCaptain(teamId, callerId)` → `venueSelectionService.requireSelectable({venueId})` (VENUE-001 §4.6; v4.0 trả thêm phường/tỉnh) → `ExternalMatch.builder()` (`status = SCHEDULED`) `save` → `recruitmentRepository.insertAll(null, id, teamId, callerId, …)` (auto-flush INSERT trận ngoài trước câu native) → `venueSelectionService.markSelected({venueId}, now)` (BC-V05, user chốt 2026-11-14) → response.
- `updateExternalMatch`: `externalMatchRepository.findContext(id, callerId, now)` → guard → đổi sân: `venueSelectionService.requireSelectable({venueIdMới})` → `externalMatchRepository.updateIfVersion(id, ctx.version(), …giá trị mới, now)` (CAS `version`, **khoá trận ngoài trước** — cùng thứ tự `external_match` → tin → đơn với 2.32, N-m1; 0 dòng → `ConcurrencyConflictException`) → đổi sân / giờ / trình độ: `recruitmentRepository.lockByExternalMatch(id, now)` (khoá tin, trả `quantity_approved`; tổng > 0 → `MERCENARY_025`, rollback cả CAS) → đổi sân: `venueSelectionService.markSelected({venueIdMới}, now)` (BC-V05) → response dựng từ ctx + `SelectableVenueView` + giá trị mới (không đọc lại).
- `cancelExternalMatch`: `externalMatchRepository.findContext` → guard (`now ≥ g.start` → `MERCENARY_013` — BR-ME19) → `cancelIfScheduled(id, now)` (CAS `match_start_time > :now`) → `recruitmentRepository.closeActiveByExternalMatch(id, now)` RETURNING id → `applicationRepository.rejectPendingByRecruitmentIds(ids, now)` RETURNING → `applicationRepository.markGameCancelNotifiedByExternalMatch(id, now)` RETURNING id → `MercenaryRecruitmentClosedEvent` mỗi tin + `MercenaryGameCancelledEvent(null, id, approvedIds)`. Không ghi uy tín.
- Ứng tuyển / duyệt / rút / chat / đánh giá / report trên tin của trận ngoài: các service §4.1–§4.7 không rẽ nhánh — ngữ cảnh lấy qua `GAME_*`; "thành viên của đội trong trận" = đội khai báo (không có đội đối thủ trên hệ thống).

## 5. Repository & Data Model

### 5.1. Migration `V9__mercenary_recruitment_chat_reputation.sql`

```sql
-- external_match: trận ngoài hệ thống do đội trưởng khai báo (BR-ME19)
CREATE TABLE external_match (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES team(id) ON DELETE RESTRICT,
    venue_id UUID NOT NULL REFERENCES venue(id) ON DELETE RESTRICT,
    match_start_time TIMESTAMPTZ NOT NULL,
    match_end_time TIMESTAMPTZ NOT NULL,
    skill_levels TEXT NOT NULL DEFAULT '[]',
    opponent_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    cancelled_at TIMESTAMPTZ,
    created_by_user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT external_match_time_check CHECK (match_end_time > match_start_time),
    CONSTRAINT external_match_cancel_check CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);
CREATE INDEX idx_external_match_team ON external_match(team_id, match_start_time, id);
CREATE INDEX idx_external_match_cancelled ON external_match(match_end_time) WHERE status = 'CANCELLED';   -- job báo huỷ (Processing_Flow §3.12 bước 3)

-- mercenary_recruitment: tin của kèo HOẶC trận ngoài; người tạo, thời điểm đóng; một tin OPEN/FILLED mỗi (trận, đội, vị trí)
ALTER TABLE mercenary_recruitment ALTER COLUMN match_id DROP NOT NULL;
ALTER TABLE mercenary_recruitment ADD COLUMN external_match_id UUID REFERENCES external_match(id) ON DELETE RESTRICT;
ALTER TABLE mercenary_recruitment ADD CONSTRAINT mercenary_recruitment_game_check
    CHECK ((match_id IS NULL) <> (external_match_id IS NULL));
ALTER TABLE mercenary_recruitment ADD COLUMN created_by_user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE RESTRICT;
ALTER TABLE mercenary_recruitment ADD COLUMN closed_at TIMESTAMPTZ;
-- mercenary_application: đã báo mercenary APPROVED về việc trận bị huỷ (chống báo trùng giữa 2.32 / job — N-M2)
ALTER TABLE mercenary_application ADD COLUMN game_cancel_notified_at TIMESTAMPTZ;
CREATE INDEX idx_mercenary_application_cancel_notice ON mercenary_application(recruitment_id)
    WHERE status = 'APPROVED' AND game_cancel_notified_at IS NULL;
ALTER TABLE mercenary_recruitment ADD CONSTRAINT mercenary_recruitment_closed_check
    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL));
DROP INDEX idx_mercenary_recruitment_search;
DROP INDEX idx_mercenary_recruitment_match;
CREATE UNIQUE INDEX idx_mercenary_recruitment_match_position ON mercenary_recruitment(match_id, team_id, position)
    WHERE status IN ('OPEN', 'FILLED') AND match_id IS NOT NULL;
CREATE UNIQUE INDEX idx_mercenary_recruitment_external_position ON mercenary_recruitment(external_match_id, position)
    WHERE status IN ('OPEN', 'FILLED') AND external_match_id IS NOT NULL;
CREATE INDEX idx_mercenary_recruitment_match_all ON mercenary_recruitment(match_id) WHERE match_id IS NOT NULL;
CREATE INDEX idx_mercenary_recruitment_external_all ON mercenary_recruitment(external_match_id) WHERE external_match_id IS NOT NULL;

-- rating / report: gắn đơn ứng tuyển cho cặp đội ↔ mercenary; trận của kèo HOẶC trận ngoài; không có cặp cá nhân ↔ cá nhân
ALTER TABLE rating ALTER COLUMN match_id DROP NOT NULL;
ALTER TABLE rating ADD COLUMN external_match_id UUID REFERENCES external_match(id) ON DELETE RESTRICT;
ALTER TABLE rating ADD COLUMN application_id UUID REFERENCES mercenary_application(id) ON DELETE RESTRICT;
ALTER TABLE rating ADD CONSTRAINT rating_game_check CHECK ((match_id IS NULL) <> (external_match_id IS NULL));
ALTER TABLE rating ADD CONSTRAINT rating_application_check CHECK (
    ((rater_type = 'TEAM' AND ratee_type = 'TEAM') = (application_id IS NULL))
    AND NOT (rater_type = 'INDIVIDUAL' AND ratee_type = 'INDIVIDUAL')
    AND (external_match_id IS NULL OR application_id IS NOT NULL));
ALTER TABLE rating ADD CONSTRAINT rating_mercenary_fields_check CHECK (
    application_id IS NULL
    OR (rater_team_score IS NULL AND ratee_team_score IS NULL AND (rater_type = 'TEAM' OR NOT is_no_show)));
CREATE UNIQUE INDEX idx_rating_mercenary_unique ON rating(application_id, rater_type) WHERE application_id IS NOT NULL;
ALTER TABLE report ALTER COLUMN match_id DROP NOT NULL;
ALTER TABLE report ADD COLUMN external_match_id UUID REFERENCES external_match(id) ON DELETE RESTRICT;
ALTER TABLE report ADD COLUMN application_id UUID REFERENCES mercenary_application(id) ON DELETE RESTRICT;
ALTER TABLE report ADD CONSTRAINT report_game_check CHECK ((match_id IS NULL) <> (external_match_id IS NULL));
ALTER TABLE report ADD CONSTRAINT report_application_check CHECK (
    ((reporter_type = 'TEAM' AND reported_type = 'TEAM') = (application_id IS NULL))
    AND NOT (reporter_type = 'INDIVIDUAL' AND reported_type = 'INDIVIDUAL')
    AND (external_match_id IS NULL OR application_id IS NOT NULL));
CREATE UNIQUE INDEX idx_report_external_unique ON report(external_match_id, reporter_type, reporter_id, reported_type, reported_id, report_type)
    WHERE external_match_id IS NOT NULL;
CREATE INDEX idx_report_application ON report(application_id) WHERE application_id IS NOT NULL;

-- user: huy hiệu cá nhân (dữ liệu dẫn xuất, chỉ ghi bằng câu refresh có điều kiện)
ALTER TABLE "user" ADD COLUMN verified_at TIMESTAMPTZ;

-- conversation (bảng + conversation_message tạo ở V6 — MATCH-001 v2.4 §5.1): thêm ngữ cảnh đơn ứng tuyển mercenary
ALTER TABLE conversation ADD COLUMN application_id UUID REFERENCES mercenary_application(id) ON DELETE RESTRICT;
ALTER TABLE conversation ADD COLUMN external_match_id UUID REFERENCES external_match(id) ON DELETE RESTRICT;
ALTER TABLE conversation DROP CONSTRAINT conversation_context_type_check;
ALTER TABLE conversation ADD CONSTRAINT conversation_context_type_check CHECK (context_type IN ('TEAM_MATCH', 'MERCENARY_APPLICATION'));
ALTER TABLE conversation ADD CONSTRAINT conversation_application_check CHECK ((context_type = 'MERCENARY_APPLICATION') = (application_id IS NOT NULL));
ALTER TABLE conversation ADD CONSTRAINT conversation_game_check CHECK ((match_id IS NULL) <> (external_match_id IS NULL));
ALTER TABLE conversation ADD CONSTRAINT conversation_mercenary_parties_check CHECK (
    context_type <> 'MERCENARY_APPLICATION' OR (party_a_type = 'TEAM' AND party_b_type = 'USER'));
CREATE UNIQUE INDEX idx_conversation_application ON conversation(application_id) WHERE application_id IS NOT NULL;
CREATE INDEX idx_conversation_match ON conversation(match_id) WHERE match_id IS NOT NULL;
CREATE INDEX idx_conversation_external_match ON conversation(external_match_id) WHERE external_match_id IS NOT NULL;
```
- **Số hiệu V9:** code có V1–V4; thiết kế đã chiếm V5–V8 (VENUE-001, MATCH-001, MATCH-002, REPUTATION-001). Flyway không chạy out-of-order → V9 chỉ áp sau V5–V8 `[cần xác nhận #1]`. `venue.ward_id` (BR-V11) nằm ở V5 của VENUE-001 (v4.0), không ở V9.
- **Dữ liệu cũ:** chưa kiểm Neon dev cho mã này. `created_by_user_id NOT NULL` không mặc định và `mercenary_recruitment_closed_check` cần `mercenary_recruitment` 0 dòng (hoặc không có dòng `CLOSED`) — developer kiểm số dòng trên Neon dev trước khi chạy; có dữ liệu → dừng, báo, không tự sửa `[cần xác nhận #4]`. Tên index V1 (`idx_mercenary_recruitment_search`, `idx_mercenary_recruitment_match`) lấy từ `V1__initial_schema.sql`.
- **Vị trí** giữ CHECK V1 (`'Thủ môn' | 'Hậu vệ' | 'Tiền vệ' | 'Tiền đạo'` — cùng nhãn `preferredPosition` của USER-001). Entity hiện map `@Enumerated(STRING)` với hằng `Thủ_môn`… → ghi ra `'Thủ_môn'`, vi phạm CHECK. Sửa bằng `PositionConverter` (§6), không đổi DB.
- **Trận ngoài** (`external_match`): `team_id` là đội khai báo; `venue_id` bắt buộc (BR-ME19 chọn sân có sẵn); `skill_levels` cùng kiểu / converter với `match.skill_levels` (MATCH-001, `SkillLevelsConverter`); `version` cho sửa / huỷ bằng câu native có điều kiện `version` (`updateIfVersion`, `cancelIfScheduled` — khoá trận ngoài trước tin, N-m1). Không cột `ended`: kết thúc tính theo `match_end_time`.
- **Khoá trận XOR** (`match_id` / `external_match_id`) ở tin, đánh giá, report, cuộc trò chuyện — CHECK `…_game_check`. Đánh giá / report của trận ngoài luôn có `application_id` (không có đánh giá đội ↔ đội ngoài hệ thống). `rating.match_id`, `report.match_id` thành nullable: REPUTATION-001 luồng đội ↔ đội luôn có `match_id` (CHECK `application_id IS NULL` ⇒ `TEAM`↔`TEAM`, và `idx_rating_unique` / `idx_report_unique` của V8 vẫn chống trùng cho kèo).
- `conversation` (v2.0 — bảng ở V6 của MATCH-001): V9 thêm khoá ngữ cảnh mercenary là cột FK riêng (`application_id`) + CHECK, `external_match_id` (bảng `external_match` tạo ở V9 nên cột không thể ở V6); `match_id` / `external_match_id` lưu sẵn (bất biến) để tính hạn lưu 90 ngày sau khi trận kết thúc (BR-N01; job xoá thuộc NOTIFICATION-001 — `[cần xác nhận #5]`). `conversation_game_check` cũng đúng cho `TEAM_MATCH` (luôn có `match_id`). Bên A = đội tuyển (`TEAM`), bên B = ứng viên (`USER`) — `conversation_mercenary_parties_check`; `party_*_id` đa hình → không FK (như `rating.rater_id`). Tên constraint `conversation_context_type_check` lấy từ V6 (MATCH-001 §5.1). `idx_conversation_match` phục vụ job xoá cho ngữ cảnh mercenary (ngữ cảnh `TEAM_MATCH` có `idx_conversation_team_match` của V6).
- `conversation_message`: V6 (MATCH-001 §5.1); không đổi ở V9.
- `rating_mercenary_fields_check`: đánh giá liên quan mercenary không có tỷ số; chỉ phía đội được chọn "không đến" (BR-ME13). `rating_no_show_check` (V8) vẫn áp.
- FK mới `ON DELETE RESTRICT` (ADR-0002). FK `mercenary_*` của V1 (`ON DELETE CASCADE` tới `match`) giữ nguyên.
- **Index:** `idx_mercenary_recruitment_match_position`, `idx_mercenary_recruitment_external_position` (unique, partial) chặn tin trùng vị trí (BR-ME05) và phục vụ tìm kiếm (2.3), claim của job (§4.9) qua tiền tố trận; tin của kèo đã huỷ chưa được job đóng vẫn `OPEN` trong DB nhưng không chặn ai (không tạo được tin cho kèo huỷ). `idx_mercenary_recruitment_match_all` / `…_external_all` thay `idx_mercenary_recruitment_match` (V1) cho mọi truy vấn tin theo trận (gồm tin đã đóng). Danh sách tin của đội: `idx_mercenary_recruitment_team (team_id, status)` (V1); đơn theo tin `idx_mercenary_application_recruitment` (V1); đơn theo ứng viên + kiểm trùng giờ + xoá tài khoản `idx_mercenary_application_applicant` (V1); report của đội / cá nhân `idx_report_reported_created`, `idx_report_reporter_created` (V8) + lọc `application_id`; sao cá nhân `idx_rating_ratee` (V1); trận ngoài của đội `idx_external_match_team`.

### 5.2. Repository

Native + interface projection như MATCH-001 §5.2; `Page` native có `countQuery`; `:now` từ `Clock`. Biểu thức trận của tin và trạng thái hiệu lực lấy từ hằng `MercenarySql` (§5.3) — không viết lại trong từng câu; trong ghi chú SQL dưới, `g.<thuộc tính>` là biểu thức `MercenarySql.GAME_*` tương ứng.

```java
public interface MercenaryRecruitmentRepository extends JpaRepository<MercenaryRecruitment, UUID> {
    Optional<RecruitContextView> findRecruitContext(UUID matchId, UUID teamId, UUID callerId, OffsetDateTime now);   // 2.1
    List<RecruitmentRowView> insertAll(UUID matchId, UUID externalMatchId, UUID teamId, UUID createdBy,
                                       String[] positions, int[] quantities, OffsetDateTime now);                     // 2.1, 2.28, 2.33 — INSERT … RETURNING
    Page<RecruitmentView> findByTeam(UUID teamId, UUID matchId, String status, OffsetDateTime now, Pageable p);      // 2.2
    Page<RecruitmentView> searchNearby(UUID callerId, String position, OffsetDateTime now, double lat, double lng, double radiusKm,
                                       double minLat, double maxLat, double minLng, double maxLng, Pageable p);        // 2.3 có GPS
    Page<RecruitmentView> searchByProvince(UUID callerId, String position, OffsetDateTime now, UUID provinceId, UUID wardId,
                                           Pageable p);                                                                // 2.3 không GPS
    Optional<RecruitmentView> findView(UUID id, UUID callerId, OffsetDateTime now, Double lat, Double lng);            // 2.4
    Optional<RecruitmentWriteContextView> findWriteContext(UUID id, UUID callerId, OffsetDateTime now);                // 2.5, 2.6, 2.8
    @Modifying int updateQuantity(UUID id, int quantity, int expectedApproved, int expectedNeeded, OffsetDateTime now);
    @Modifying int closeIfActive(UUID id, OffsetDateTime now);
    @Modifying int incrementApproved(UUID id, int expectedApproved, int expectedNeeded, OffsetDateTime now);
    @Modifying int decrementApproved(UUID id, OffsetDateTime now);
    List<ApprovedCountView> decrementApprovedOfApplicant(UUID userId, OffsetDateTime now);                            // xoá tài khoản — UPDATE … RETURNING
    List<Integer> lockByExternalMatch(UUID externalMatchId, OffsetDateTime now);                                       // 2.31 — UPDATE … RETURNING quantity_approved
    List<UUID> closeActiveByExternalMatch(UUID externalMatchId, OffsetDateTime now);                                   // 2.32 — UPDATE … RETURNING id
    List<DueRecruitmentView> claimDue(OffsetDateTime now, int limit);                                                  // job
    @Modifying int markClosedOrExpired(Collection<UUID> closedIds, Collection<UUID> expiredIds, OffsetDateTime now);
}
public interface MercenaryApplicationRepository extends JpaRepository<MercenaryApplication, UUID> {
    Optional<ApplyContextView> findApplyContext(UUID recruitmentId, UUID callerId, OffsetDateTime now);                // 2.7
    Optional<ApplicationContextView> findContext(UUID id, UUID callerId, OffsetDateTime now);                          // 2.10–2.13
    Page<ApplicantRowView> findApplicants(UUID recruitmentId, String status, OffsetDateTime now, Pageable p);          // 2.8
    Page<ApplicationView> findByApplicant(UUID applicantId, String status, OffsetDateTime now, Pageable p);            // 2.9
    @Modifying int approveIfPending(UUID id, OffsetDateTime now);
    @Modifying int rejectIfPending(UUID id, OffsetDateTime now);
    @Modifying int withdrawPending(UUID id, OffsetDateTime now);
    @Modifying int withdrawApproved(UUID id, OffsetDateTime now);
    List<UUID> rejectPendingByRecruitment(UUID recruitmentId, OffsetDateTime now);                                    // 2.6 — UPDATE … RETURNING id
    List<RejectedApplicationView> rejectPendingByRecruitmentIds(UUID[] recruitmentIds, OffsetDateTime now);           // 2.32, job — UPDATE … RETURNING
    List<RejectedApplicationView> rejectOrphanPending(OffsetDateTime now, int limit);                                  // job — UPDATE … RETURNING
    List<GameCancelNoticeView> markGameCancelNotified(OffsetDateTime now, OffsetDateTime lookbackStart, int limit);                                // job — UPDATE … RETURNING
    List<GameCancelNoticeView> markGameCancelNotifiedByExternalMatch(UUID externalMatchId, OffsetDateTime now);        // 2.32 — UPDATE … RETURNING
    List<WithdrawnApplicationView> withdrawActiveOfApplicant(UUID userId, OffsetDateTime now);                         // xoá tài khoản — UPDATE … RETURNING
}
public interface ExternalMatchRepository extends JpaRepository<ExternalMatch, UUID> {
    Optional<ExternalMatchContextView> findContext(UUID id, UUID callerId, OffsetDateTime now);                        // 2.31, 2.32
    Optional<RecruitContextView> findRecruitContext(UUID id, UUID callerId, OffsetDateTime now);                      // 2.33
    Optional<ExternalMatchView> findDetail(UUID id, UUID callerId);                                                    // 2.30
    Page<ExternalMatchView> findByTeam(UUID teamId, String statusFilter, OffsetDateTime now, Pageable p);             // 2.29
    @Modifying int updateIfVersion(UUID id, long expectedVersion, UUID venueId, OffsetDateTime start, OffsetDateTime end,
                                   String skillLevels, String opponentName, OffsetDateTime now);                       // 2.31 — CAS, version + 1
    @Modifying int cancelIfScheduled(UUID id, OffsetDateTime now);                                                     // 2.32 — CAS, version + 1
}
// ConversationRepository, ConversationMessageRepository: MATCH-001 §5.2 (v2.0 — tái dùng; mã này thêm khối cột mercenary vào findContext, §4.7)
// RatingRepository (sửa)
Optional<MercenaryRatingContextView> findMercenaryRatingContext(UUID applicationId, UUID callerId, OffsetDateTime now); // 2.18, 2.19
Page<TeamMercenaryRatingView> findMercenaryRatingsOfTeam(UUID teamId, Pageable p);                                     // 2.24
// ReportRepository (sửa)
Optional<MercenaryReportContextView> findMercenaryReportContext(UUID applicationId, UUID callerId, String reportType, OffsetDateTime now); // 2.20
List<MercenaryReportView> findMercenaryReports(String subjectType, UUID subjectId, String direction, int limit, long offset); // 2.21, 2.22
long countMercenaryReports(String subjectType, UUID subjectId, String direction);
// DisputeRepository (sửa)
Optional<UUID> resolveMercenaryIfPending(UUID id, String decision, String note, UUID adminId, OffsetDateTime now);   // UPDATE … RETURNING report_id
boolean existsMercenaryDispute(UUID id);                                                                                // M5
boolean existsTeamDispute(UUID id);                                                                                     // M5 — REPUTATION-001 2.8
List<AdminMercenaryDisputeView> findAdminMercenaryDisputes(String status, boolean oldestFirst, int limit, long offset);
long countAdminMercenaryDisputes(String status);
Optional<AdminMercenaryDisputeDetailView> findAdminMercenaryDisputeDetail(UUID id);
// UserRepository (sửa)
Optional<ProfileAreaView> findProfileArea(UUID userId);                                                                 // ward_id, province_id, deleted
Optional<UserReputationView> findReputation(UUID userId, OffsetDateTime now);                                          // §5.4
Optional<ScoreChangeView> applyMercenaryCompletion(UUID userId, UUID matchId, UUID externalMatchId, int ratingBonus, …hằng ReputationRules); // 2.18
Optional<ScoreChangeView> applyReportPenalty(UUID id, String type, int[] tiers);                                       // như TeamRepository (REPUTATION-001)
@Modifying int refundReportPenalty(UUID id, String type, int points);
List<VerificationChangeView> refreshVerification(UUID[] ids, OffsetDateTime now, …ngưỡng huy hiệu);
List<UUID> findIdsForVerification(UUID afterId, int limit);
```
Câu `… RETURNING` khai báo native trả projection, không `@Modifying` (MATCH-002 `[cần xác nhận #4]`).

Ghi chú SQL:
- `findRecruitContext` (kèo): `SELECT m.id, m.status, m.match_start_time, m.match_end_time, m.author_team_id, m.confirmed_opponent_team_id, m.format, m.skill_levels, t.id, t.status, t.name, t.avatar_url, EXISTS(captain ACTIVE = :callerId của t) AS caller_captain, vw.name AS ward_name, vp.name AS province_name, ARRAY(SELECT r.position FROM mercenary_recruitment r WHERE r.match_id = m.id AND r.team_id = :teamId AND r.status IN ('OPEN','FILLED')) AS active_positions FROM match m LEFT JOIN team t ON t.id = :teamId LEFT JOIN venue v ON v.id = m.venue_id LEFT JOIN administrative_unit vw ON vw.id = v.ward_id LEFT JOIN administrative_unit vp ON vp.id = vw.parent_id WHERE m.id = :matchId` (không có dòng → `MATCH_001`; `t.id` null → `TEAM_001`). `ExternalMatchRepository.findRecruitContext`: cùng cột từ `external_match` + đội khai báo.
- `insertAll`: `INSERT INTO mercenary_recruitment (id, match_id, external_match_id, team_id, position, quantity_needed, quantity_approved, status, created_by_user_id, created_at, updated_at) SELECT uuidv7(), :matchId, :externalMatchId, :teamId, p.pos, p.qty, 0, 'OPEN', :createdBy, :now, :now FROM unnest(CAST(:positions AS text[]), CAST(:quantities AS int[])) AS p(pos, qty) RETURNING id, position, quantity_needed, created_at` — mapper sắp kết quả theo thứ tự vị trí trong request.
- `updateQuantity`: `UPDATE mercenary_recruitment SET quantity_needed = :q, status = CASE WHEN quantity_approved = :q THEN 'FILLED' ELSE 'OPEN' END, updated_at = :now WHERE id = :id AND status IN ('OPEN','FILLED') AND quantity_approved = :expectedApproved AND quantity_needed = :expectedNeeded AND quantity_approved <= :q`.
- `closeIfActive`: `SET status = 'CLOSED', closed_at = :now, updated_at = :now WHERE id = :id AND status IN ('OPEN','FILLED')`.
- `incrementApproved`: `SET quantity_approved = quantity_approved + 1, status = CASE WHEN quantity_approved + 1 = quantity_needed THEN 'FILLED' ELSE 'OPEN' END, updated_at = :now WHERE id = :id AND status = 'OPEN' AND quantity_approved = :expectedApproved AND quantity_needed = :expectedNeeded`.
- `decrementApproved`: `SET quantity_approved = quantity_approved - 1, status = CASE WHEN status = 'FILLED' THEN 'OPEN' ELSE status END, updated_at = :now WHERE id = :id`.
- `decrementApprovedOfApplicant` / `withdrawActiveOfApplicant`: `Processing_Flow.md` §3.16.
- `lockByExternalMatch`: `UPDATE mercenary_recruitment SET updated_at = :now WHERE external_match_id = :id RETURNING quantity_approved` (khoá mọi tin của trận ngoài; đọc lại số đã duyệt sau khi chờ khoá — READ COMMITTED đánh giá lại dòng).
- `closeActiveByExternalMatch`: `UPDATE mercenary_recruitment SET status = 'CLOSED', closed_at = :now, updated_at = :now WHERE external_match_id = :id AND status IN ('OPEN','FILLED') RETURNING id`.
- `cancelIfScheduled`: `UPDATE external_match SET status = 'CANCELLED', cancelled_at = :now, version = version + 1, updated_at = :now WHERE id = :id AND status = 'SCHEDULED' AND match_start_time > :now` (chỉ trước giờ bắt đầu — BR-ME19).
- `updateIfVersion`: `UPDATE external_match SET venue_id = :venueId, match_start_time = :start, match_end_time = :end, skill_levels = :skill, opponent_name = :opponent, version = version + 1, updated_at = :now WHERE id = :id AND version = :expectedVersion AND status = 'SCHEDULED' AND match_end_time > :now`.
- `markGameCancelNotified` (job) / `markGameCancelNotifiedByExternalMatch` (2.32): `UPDATE mercenary_application a SET game_cancel_notified_at = :now … WHERE a.status = 'APPROVED' AND a.game_cancel_notified_at IS NULL AND <trận huỷ / tin thuộc trận ngoài :id> RETURNING a.id, r.match_id, r.external_match_id` (bản job xuất phát từ trận bị huỷ có `match_end_time > :lookbackStart` — hai nhánh `UNION ALL` kèo / trận ngoài, mỗi nhánh join một cột khoá trận; claim lô `FOR NO KEY UPDATE OF a2 SKIP LOCKED` ở tầng ngoài không có `UNION` — `Processing_Flow.md` §3.12 bước 3).
- `approveIfPending`: `SET status = 'APPROVED', responded_at = :now WHERE id = :id AND status = 'PENDING'`; `rejectIfPending` tương tự (`REJECTED`); `withdrawPending` / `withdrawApproved`: `SET status = 'WITHDRAWN', withdrawn_at = :now WHERE id = :id AND status = '<PENDING|APPROVED>'` (đơn rút từ `APPROVED` giữ `responded_at` → phân biệt được ở thống kê §5.4).
- `rejectPendingBy…`: `SET status = 'REJECTED', responded_at = :now WHERE recruitment_id … AND status = 'PENDING' RETURNING id[, recruitment_id]`. Chạy **sau** câu khoá tin.
- `claimDue`: `SELECT r.id, r.match_id, r.external_match_id, r.team_id, CASE WHEN g.cancelled THEN 'MATCH_CANCELLED' ELSE 'EXPIRED' END AS cause FROM mercenary_recruitment r <GAME_JOIN> WHERE r.status IN ('OPEN','FILLED') AND (g.cancelled OR g.end <= :now) ORDER BY r.id LIMIT :limit FOR NO KEY UPDATE OF r SKIP LOCKED`. `markClosedOrExpired`: `SET status = CASE WHEN id = ANY(:closedIds) THEN 'CLOSED' ELSE 'EXPIRED' END, closed_at = CASE WHEN id = ANY(:closedIds) THEN :now END, updated_at = :now WHERE (id = ANY(:closedIds) OR id = ANY(:expiredIds)) AND status IN ('OPEN','FILLED')`.
- `rejectOrphanPending`: `Processing_Flow.md` §3.12 bước 2.
- `findApplyContext` / `findContext` (đơn): một câu `mercenary_recruitment r <GAME_JOIN> JOIN team t ON t.id = r.team_id LEFT JOIN "user" me ON me.id = :callerId` (+ `mercenary_application a` cho `findContext`) với: `(me.deleted_at IS NOT NULL) AS caller_deleted`, trạng thái hiệu lực tin / đơn, `MEMBER_OF_GAME_TEAMS` cho caller (2.7) hoặc ứng viên (2.11), `EXISTS` đơn đang hoạt động của caller ở tin (2.7), `OVERLAPPING_APPROVAL`, cờ caller là captain ACTIVE của `t`, `t.status`, `g.start`, `g.end`, `conversation.id` (`LEFT JOIN`).
- `searchNearby`: `FROM mercenary_recruitment r <GAME_JOIN> JOIN team t ON t.id = r.team_id AND t.status = 'ACTIVE' JOIN venue v ON v.id = g.venue_id LEFT JOIN administrative_unit vw … vp … WHERE r.status = 'OPEN' AND g.confirmed AND g.start > :now AND (:position IS NULL OR r.position = :position) AND v.latitude BETWEEN :minLat AND :maxLat AND v.longitude BETWEEN :minLng AND :maxLng AND <Haversine> <= :radiusKm ORDER BY distance_km ASC, g.start ASC, r.id ASC` + cột `my_application_status`. `r.status = 'OPEN' AND g.confirmed AND g.start > :now` ⇒ trạng thái hiệu lực `OPEN` và chưa bắt đầu (BR-ME07; không cần `CASE`). `distance_km` trả số thực; làm tròn 0,5 km ở mapper (§7).
- `searchByProvince`: như trên, thay điều kiện toạ độ bằng `vw.parent_id = :provinceId`; `ORDER BY (v.ward_id = :wardId) DESC, g.start ASC, r.id ASC`; `distance_km = NULL`. Sân chưa có phường không xuất hiện (BR-V11).
- `findView` (2.4): tin + trận + đội + sân + khu vực + `viewer_is_captain` + đơn đang hoạt động của caller + đội trưởng hiện tại + `pending_count` (chỉ dùng khi `viewer_is_captain`) + khoảng cách khi có toạ độ + `source`, `opponent_name`.
- `findApplicants` (2.8): `mercenary_application a JOIN "user" u JOIN conversation c` + `CROSS JOIN LATERAL (<USER_STATS cho u.id>) s`; `ORDER BY a.applied_at ASC, a.id ASC`; lọc trạng thái trên biểu thức hiệu lực.
- `findByApplicant` (2.9): đơn của caller ⋈ tin ⋈ trận (`GAME_JOIN`) ⋈ đội ⋈ sân ⋈ khu vực ⋈ `conversation` ⋈ đội trưởng hiện tại; `ORDER BY a.applied_at DESC, a.id DESC`.
- `ExternalMatchRepository.findDetail` / `findByTeam`: `external_match x JOIN venue v LEFT JOIN administrative_unit vw … vp … JOIN team t` + `EXISTS (SELECT 1 FROM mercenary_recruitment r WHERE r.external_match_id = x.id AND r.quantity_approved > 0) AS has_approved_mercenary` + cờ caller captain; `findByTeam` lọc `UPCOMING` (`status = 'SCHEDULED' AND match_end_time > :now`), `ENDED` (`SCHEDULED` và `≤ :now`), `CANCELLED`, `ALL`.
- `ConversationRepository.findContext` — khối `MERCENARY_APPLICATION` (§4.7) trong câu chung của MATCH-001 §5.2: `LEFT JOIN mercenary_application a ON c.context_type = 'MERCENARY_APPLICATION' AND a.id = c.application_id LEFT JOIN mercenary_recruitment r <GAME_JOIN> LEFT JOIN team t ON c.context_type = 'MERCENARY_APPLICATION' AND t.id = c.party_a_id LEFT JOIN "user" ap ON c.context_type = 'MERCENARY_APPLICATION' AND ap.id = c.party_b_id LEFT JOIN team_member cm ON cm.team_id = t.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE' LEFT JOIN "user" cu ON cu.id = cm.user_id` → cột ngữ cảnh + `COALESCE(cm.user_id = :callerId, false) AS caller_mercenary_captain`, `COALESCE(ap.id = :callerId, false) AS caller_applicant`, `t.status AS mercenary_team_status`, `COALESCE(t.disbanded_by = :callerId, false) AS caller_mercenary_disbanded_by` (v2.0, user chốt 2026-11-15 — cột trên dòng `team` đã join, không thêm câu) (cột người gọi `me.*` dùng chung).
- Tin nhắn (cursor, đánh dấu đã đọc): MATCH-001 §5.2.
- `findMercenaryRatingContext`: caller đã xoá + đơn ⋈ tin ⋈ trận (`g.ended`) ⋈ đội tuyển (`status`) + cờ caller captain / ứng viên + `LEFT JOIN rating` theo `application_id` (0..2 dòng, gom bằng `max(…) FILTER (WHERE rater_type = …)` thành 1 dòng).
- `findMercenaryReportContext`: như trên (không cột đánh giá) + `EXISTS (SELECT 1 FROM conversation_message cm JOIN conversation c ON c.id = cm.conversation_id WHERE c.application_id = :applicationId) AS has_messages` + `EXISTS` report cùng (trận, reporter, reported, `report_type`) của phía caller + `t.status`, `COALESCE(t.disbanded_by = :callerId, false) AS caller_disbanded_by` (v2.0 — `TEAM_016` ở 2.20; cột trên dòng `team` đã join, không thêm câu).
- `findMercenaryReports`: `subjectType = TEAM` → `RECEIVED`: `r.reported_type = 'TEAM' AND r.reported_id = :id`, `SENT`: `r.reporter_type = 'TEAM' AND r.reporter_id = :id`; `INDIVIDUAL` tương tự; luôn `AND r.application_id IS NOT NULL`; `JOIN mercenary_application a JOIN mercenary_recruitment rc <GAME_JOIN rc> JOIN team t ON t.id = rc.team_id JOIN "user" u ON u.id = a.applicant_id LEFT JOIN dispute d` → `ORDER BY r.created_at DESC, r.id DESC LIMIT/OFFSET`. Bốn câu cố định (không nối chuỗi cột).
- `resolveMercenaryIfPending`: như `resolveIfPending` (REPUTATION-001 §5.2) + `AND EXISTS (SELECT 1 FROM report r WHERE r.id = dispute.report_id AND r.application_id IS NOT NULL)`. `existsMercenaryDispute` / `existsTeamDispute`: `SELECT EXISTS (SELECT 1 FROM dispute d JOIN report r ON r.id = d.report_id WHERE d.id = :id AND r.application_id IS [NOT] NULL)`. `findAdminMercenary*`: `dispute d JOIN report r ON r.id = d.report_id AND r.application_id IS NOT NULL JOIN mercenary_application a JOIN mercenary_recruitment rc <GAME_JOIN rc> JOIN team t JOIN "user" mu ON mu.id = a.applicant_id JOIN "user" ru (r.created_by_user_id) JOIN "user" du (d.created_by_user_id) LEFT JOIN "user" au (d.resolved_by_user_id) LEFT JOIN sport s ON s.id = m.sport_id` → tên thật + cờ đã xoá (ADR-0002 Sửa đổi 2026-11-12). Thứ tự như REPUTATION-001 2.6.
- `UserRepository.applyMercenaryCompletion` — khung (cùng công thức `applyCompletion` đội của REPUTATION-001 §5.2 cho một dòng):
  ```sql
  WITH locked AS (SELECT u.id, u.reliability_score AS old_score, u.clean_streak AS old_streak FROM "user" u WHERE u.id = :userId FOR NO KEY UPDATE),
  calc AS (SELECT l.id, l.old_score, l.old_streak + 1 AS next_streak,
                  NOT EXISTS (SELECT 1 FROM report r WHERE r.reported_type = 'INDIVIDUAL' AND r.reported_id = l.id AND r.status <> 'REJECTED'
                              AND (r.match_id = :matchId OR r.external_match_id = :externalMatchId)) AS clean FROM locked l)
  UPDATE "user" u SET
    clean_streak = CASE WHEN c.clean THEN c.next_streak ELSE 0 END,
    reliability_score = LEAST(100, GREATEST(0, u.reliability_score + :completionPoints + :ratingBonus
        + CASE WHEN c.clean AND c.next_streak % (2 * :milestone) = 0 THEN :bonus10
               WHEN c.clean AND c.next_streak % :milestone = 0 THEN :bonus5 ELSE 0 END)),
    report_count_noshow = CASE WHEN c.clean AND c.next_streak % :milestone = 0 THEN 0 ELSE u.report_count_noshow END
    -- tương tự 3 bộ đếm còn lại
  FROM calc c WHERE u.id = c.id
  RETURNING 'USER'::text AS subject_type, u.id, c.old_score, u.reliability_score AS new_score
  ```
- `UserRepository.applyReportPenalty` / `refundReportPenalty` / `refreshVerification`: mẫu `TeamRepository` (REPUTATION-001 §5.2) trên `"user"`; điều kiện huy hiệu cá nhân: `u.phone_number IS NOT NULL AND u.deleted_at IS NULL AND s.completed_matches > :minCompleted AND s.avg_rating > :minAvg AND u.reliability_score >= :minScore` (`COALESCE(…, false)` khi chưa có sao).

### 5.3. Hằng SQL dùng chung — `MercenarySql` (class `final`, `domain/repository`)

| Hằng | Nội dung | Dùng ở |
|---|---|---|
| `GAME_JOIN` | `LEFT JOIN match m ON m.id = r.match_id LEFT JOIN external_match x ON x.id = r.external_match_id` (alias tin `r` tham số hoá) | Mọi câu có tin |
| `GAME_START`, `GAME_END`, `GAME_VENUE`, `GAME_SKILL` | `COALESCE(m.match_start_time, x.match_start_time)`, `COALESCE(m.match_end_time, x.match_end_time)`, `COALESCE(m.venue_id, x.venue_id)`, `COALESCE(m.skill_levels, x.skill_levels)` | `g.start`, `g.end`, `g.venue_id`, `g.skill` |
| `GAME_CANCELLED` | `(m.status = 'CANCELLED' OR x.status = 'CANCELLED')` | `g.cancelled` |
| `GAME_CONFIRMED` | `(m.status = 'CONFIRMED' OR x.status = 'SCHEDULED')` | `g.confirmed` |
| `GAME_ENDED` | `(m.status IN ('ARCHIVED','COMPLETED') OR (x.status = 'SCHEDULED' AND x.match_end_time <= :now))` | `g.ended` (đánh giá / report "đã tham gia") |
| `RECRUITMENT_EFFECTIVE_STATUS` | `CASE WHEN r.status IN ('OPEN','FILLED') AND <GAME_CANCELLED> THEN 'CLOSED' WHEN r.status IN ('OPEN','FILLED') AND <GAME_END> <= :now THEN 'EXPIRED' ELSE r.status END` | Mọi câu đọc tin / đơn, context ghi |
| `APPLICATION_EFFECTIVE_STATUS` | `CASE WHEN a.status = 'PENDING' AND (<RECRUITMENT_EFFECTIVE_STATUS>) IN ('CLOSED','EXPIRED') THEN 'REJECTED' ELSE a.status END` | Như trên; `ConversationRepository.findContext` |
| `OVERLAPPING_APPROVAL` | `EXISTS (SELECT 1 FROM mercenary_application a2 JOIN mercenary_recruitment r2 ON r2.id = a2.recruitment_id <GAME_JOIN r2 → m2, x2> WHERE a2.applicant_id = :userId AND a2.status = 'APPROVED' AND a2.id IS DISTINCT FROM :excludeApplicationId AND NOT <GAME_CANCELLED của m2, x2> AND <GAME_START 2> < <GAME_END> AND <GAME_END 2> > <GAME_START>)` — gồm cả trận hiện tại: tối đa một đơn `APPROVED` mỗi trận (BR-ME06) | 2.7, 2.11 |
| `MEMBER_OF_GAME_TEAMS` | `EXISTS (SELECT 1 FROM team_member tm WHERE tm.user_id = :userId AND tm.status = 'ACTIVE' AND tm.team_id IN (m.author_team_id, m.confirmed_opponent_team_id, x.team_id))` (`NULL` trong danh sách bị bỏ qua) | 2.7, 2.11 |
| `USER_STATS` | §5.4 | 2.8, 2.23, refresh huy hiệu cá nhân |

### 5.4. Thống kê cá nhân (Completion Rate, sao trung bình) — tính khi đọc

Nguồn duy nhất cho công thức `API_Spec.md` §1.6 (Business_Rules §3.3, BR-ME12, BR-ME15 — sửa 2026-11-14: hoàn thành = được đội trưởng chấm sao):
```sql
-- p = trận cá nhân tham gia với vai trò mercenary: đơn APPROVED, hoặc WITHDRAWN sau khi đã APPROVED (responded_at có giá trị)
WITH p AS (
  SELECT a.id AS application_id, COALESCE(r.match_id, r.external_match_id) AS game_id, <GAME_START> AS start_time,
         <GAME_CANCELLED> AS cancelled, <GAME_ENDED> AS ended, a.status AS app_status, a.withdrawn_at
  FROM mercenary_application a JOIN mercenary_recruitment r ON r.id = a.recruitment_id <GAME_JOIN>
  WHERE a.applicant_id = :userId AND a.responded_at IS NOT NULL AND a.status IN ('APPROVED', 'WITHDRAWN')),
f AS (
  SELECT p.*,
         EXISTS (SELECT 1 FROM rating rt WHERE rt.application_id = p.application_id AND rt.rater_type = 'TEAM' AND rt.score IS NOT NULL) AS rated_star,
         EXISTS (SELECT 1 FROM report rp WHERE (rp.match_id = p.game_id OR rp.external_match_id = p.game_id)
                 AND rp.reported_type = 'INDIVIDUAL' AND rp.reported_id = :userId AND rp.report_type = 'NOSHOW' AND rp.status <> 'REJECTED') AS noshow
  FROM p)
SELECT COUNT(DISTINCT game_id) FILTER (WHERE app_status = 'APPROVED' AND rated_star AND NOT noshow) AS completed_matches,
       COUNT(DISTINCT game_id) FILTER (WHERE (app_status = 'APPROVED' AND NOT cancelled AND (rated_star OR (ended AND noshow)))
                                       OR (app_status = 'WITHDRAWN' AND NOT cancelled
                                           AND withdrawn_at > start_time - make_interval(hours => :cancelWindowHours))) AS counted_matches
FROM f
-- sao: SELECT AVG(score), COUNT(score) FROM rating WHERE ratee_type = 'INDIVIDUAL' AND ratee_id = :userId AND score IS NOT NULL
```
- Tử số: được chấm sao và không bị report bùng (chưa bị bác). Mẫu số: được chấm sao; hoặc đã kết thúc + bị report bùng; hoặc rút lui sau duyệt dưới 24 giờ. Đánh giá "Không đến" / chưa đánh giá, không bị report bùng → không tính (BR-ME15). Trận bị huỷ không tính (BR-ME09). `COUNT(DISTINCT game_id)` vì một người có thể có hơn một đơn trong cùng trận (đơn rút rồi ứng tuyển tin khác). Id kèo và id trận ngoài đều là UUID v7 sinh riêng → không trùng nhau [Inference — xác suất trùng UUID không đáng kể].
- `findReputation` = `"user"` (điểm, `verified_at`, `deleted_at`) + hai phần trên dạng subquery vô hướng (1 câu). Chi phí theo số đơn của **một** người (`idx_mercenary_application_applicant`, `idx_rating_mercenary_unique`, `idx_report_unique` / `idx_report_external_unique` theo tiền tố trận).

### 5.5. Projection

| Projection | Cột chính |
|---|---|
| `RecruitContextView` | trận (`id`, `status`, giờ, `authorTeamId`, `opponentTeamId`, `format`, `skillLevels`), đội (`id`, `status`, `name`, `avatarUrl`), `callerCaptain`, `wardName`, `provinceName`, `activePositions` |
| `RecruitmentRowView` | `id`, `position`, `quantityNeeded`, `createdAt` |
| `RecruitmentView` | cột `RecruitmentResponse` + `effectiveStatus`, `source`, `externalMatchId`, `opponentName`, sân (`venueId`, `name`, `address`, `latitude`, `longitude`), `wardName`, `provinceName`, `distanceKm`, `captainName`, `captainDeleted`, `viewerIsCaptain`, `myApplicationId`, `myApplicationStatus`, `myConversationId`, `pendingCount` |
| `RecruitmentWriteContextView` | tin (`id`, `teamId`, `matchId`, `externalMatchId`, `status`, `effectiveStatus`, `quantityNeeded`, `quantityApproved`, `closedAt`) + đội (`status`, `name`, `avatarUrl`) + `callerCaptain` + trận (`gameStart`, `gameEnd`, `format`, `skillLevels`, `source`, `opponentName`) + khu vực + sân + `pendingCount` |
| `ApplyContextView` | `callerDeleted`, tin (`effectiveStatus`, `teamId`, `matchId`, `externalMatchId`, `position`), đội tuyển (`status`, `name`, `avatarUrl`), trận (`gameStart`, `gameEnd`, `source`, `opponentName`), khu vực, `callerMember`, `callerHasActive`, `callerOverlap` |
| `ApplicationContextView` | đơn (`id`, `status`, `effectiveStatus`, `applicantId`, `appliedAt`, `respondedAt`) + tin (`id`, `status`, `effectiveStatus`, `quantityNeeded`, `quantityApproved`) + trận (`gameStart`, `gameCancelled`, `gameEnded`) + đội tuyển (`id`, `status`) + `callerCaptain`, `callerApplicant`, `applicantMember`, `applicantOverlap`, `conversationId` |
| `ApplicationView` | cột `ApplicationResponse` (đơn + tóm tắt tin + trận (`source`, …) + sân / đội trưởng hiện tại) |
| `ApplicantRowView` | đơn + ứng viên (`userId`, `displayName`, `avatarUrl`, `deleted`, `preferredPosition`) + thống kê cá nhân + `reliabilityScore`, `verifiedAt` + `conversationId` |
| `ApprovedCountView` | `recruitmentId`, `n` |
| `WithdrawnApplicationView` | `id`, `recruitmentId`, `teamId`, `matchId`, `externalMatchId`, `wasApproved` |
| `RejectedApplicationView` | `id`, `recruitmentId` |
| `ConversationContextView` (projection chung — MATCH-001 §5.3; cột dưới là khối `MERCENARY_APPLICATION`) | cuộc trò chuyện (`id`, `contextType`, bên A/B) + `applicationId`, `applicationEffectiveStatus`, `matchId`, `externalMatchId` + đội (`name`, `avatarUrl`, `captainName`, `captainDeleted`) + ứng viên (`displayName`, `avatarUrl`, `deleted`) + `callerCaptain`, `callerApplicant`, `callerName`, `callerAvatarUrl`, `callerDeleted` |
| `ConversationMessageView` | Tái dùng MATCH-001 §5.3 (`senderPartyType`, `senderPartyId` suy ra ở mapper từ `senderParty` + bên A/B của ctx) |
| `MercenaryRatingContextView` | `callerDeleted`, đơn (`status`, `applicantId`), trận (`matchId`, `externalMatchId`, `gameEnded`), đội (`id`, `status`), `callerCaptain`, `callerApplicant`, cột đánh giá phía đội và phía mercenary (nullable) |
| `MercenaryReportContextView` | như trên (không cột đánh giá) + `hasMessages`, `alreadyReported` |
| `MercenaryReportView` | cột report + `applicationId`, `gameStart`, đội (`id`, `name`, `avatarUrl`), mercenary (`userId`, `displayName`, `avatarUrl`, `deleted`) + cột dispute |
| `AdminMercenaryDisputeView` / `…DetailView` | cột §5.2 (+ `source`, `opponentName`, `sportName` nullable) |
| `UserReputationView` | `userId`, `deleted`, `reliabilityScore`, `verifiedAt`, `completedMatches`, `countedMatches`, `averageRating`, `ratingCount` |
| `TeamMercenaryRatingView` | `ratingId`, `matchId`, `externalMatchId`, `gameStart`, mercenary (`userId`, `displayName`, `avatarUrl`, `deleted`), `score`, `comment`, `createdAt` |
| `DueRecruitmentView` | `id`, `matchId`, `externalMatchId`, `teamId`, `cause` |
| `GameCancelNoticeView` | `id`, `matchId`, `externalMatchId` |
| `ProfileAreaView` | `wardId`, `provinceId`, `deleted` |
| `ExternalMatchContextView` | trận ngoài (`id`, `teamId`, `status`, giờ, `venueId`, `skillLevels`, `opponentName`, `version`, `createdAt`, `updatedAt`) + sân (`name`, `address`, `latitude`, `longitude`) + `wardName`, `provinceName` + `hasApprovedMercenary` + đội (`status`) + `callerCaptain` |
| `ExternalMatchView` | cột `ExternalMatchResponse` + sân + khu vực + `hasApprovedMercenary` + `callerCaptain`, `teamStatus` |
| `ScoreChangeView` (REPUTATION-001, sửa) | + `subjectType` |

Thời gian là `Instant`, mapper chuyển UTC (USER-001 §5).

## 6. Entity

| Entity | Thay đổi |
|---|---|
| `mercenary.MercenaryRecruitment` | `position`: bỏ `@Enumerated` → `@Convert(converter = PositionConverter.class)`; `enum Position { GOALKEEPER("Thủ môn"), DEFENDER("Hậu vệ"), MIDFIELDER("Tiền vệ"), FORWARD("Tiền đạo") }` · `matchId` nullable · + `externalMatchId` · + `createdByUserId` (`nullable = false`, `updatable = false`) · + `closedAt` · `status`, `quantityNeeded`, `quantityApproved`, `closedAt` `updatable = false` (chỉ CAS native — ADR-0003 Sửa đổi 2026-11-14) · `@Builder.Default status = OPEN` |
| `mercenary.MercenaryApplication` | `status`, `respondedAt`, `withdrawnAt` `updatable = false` (chỉ CAS native) · + `gameCancelNotifiedAt` (`insertable = false, updatable = false`) · `@Builder.Default status = PENDING` |
| `mercenary.ExternalMatch` (mới) | Map V9; `@UuidGenerator(VERSION_7)`; `@Version Long version`; `skillLevels` (`List<String>`, `@Convert(SkillLevelsConverter)`, `@Builder.Default List.of()`); `enum Status { SCHEDULED, CANCELLED }`; mọi cột nghiệp vụ `updatable = false` — chỉ INSERT + đọc qua JPA; sửa (2.31 `updateIfVersion`) và huỷ (2.32 `cancelIfScheduled`) là câu native có điều kiện `version` / trạng thái, tự `version + 1` (N-m1 — để khoá trận ngoài trước khi khoá tin); `createdAt` `@PrePersist` |
| `chat.Conversation` (sửa — entity tạo ở MATCH-001 v2.4 §6) | + `applicationId`, `externalMatchId` (`UUID`, nullable, `updatable = false`); `ContextType` + `MERCENARY_APPLICATION` (MATCH-001 đợt 1 chỉ có `TEAM_MATCH` — MATCH-001 §1.1; thêm cùng `MercenaryApplicationConversationPolicy` vì thiếu policy cho một giá trị là lỗi khởi động) |
| `chat.ConversationMessage` | Tái dùng MATCH-001 §6 |
| `rating.Rating`, `rating.Report` | `matchId` nullable · + `externalMatchId`, `applicationId` (`updatable = false`) |
| `user.User` | + `verifiedAt` (`insertable = false, updatable = false` — chỉ `refreshVerification`) |

`PositionConverter` (`domain/entity/converter`, `@Converter`): enum ↔ nhãn tiếng Việt; nhãn lạ → `IllegalArgumentException`. API trả / nhận **nhãn**. Kiểm bằng migration integration test (`ddl-auto=validate` sau V9) + test ghi/đọc `Position` qua converter.

## 7. DTO & Mapper

- DTO là `record`; chuỗi `trim()`; `""` ở field tuỳ chọn → `null` (MATCH-001 §7).
- `CreateRecruitmentsRequest(@NotNull UUID teamId, @NotEmpty @Size(max = 4) @UniquePositions List<@Valid RecruitmentPositionRequest> positions)`; `AddRecruitmentsRequest(@NotEmpty @Size(max = 4) @UniquePositions List<@Valid RecruitmentPositionRequest> positions)`; `RecruitmentPositionRequest(@NotNull @OneOf({"Thủ môn","Hậu vệ","Tiền vệ","Tiền đạo"}) String position, @NotNull @Min(1) @Max(MercenaryRules.MAX_QUANTITY) Integer quantity)`.
- `UpdateRecruitmentRequest(@NotNull @Min(1) @Max(MAX_QUANTITY) Integer quantityNeeded)`.
- `CreateExternalMatchRequest(@NotNull UUID venueId, @NotNull OffsetDateTime startsAt, @NotNull OffsetDateTime endsAt, @ValidSkillLevels List<String> skillLevels, @Size(max = 100) String opponentName, @NotEmpty @Size(max = 4) @UniquePositions List<@Valid RecruitmentPositionRequest> positions)` + `@ValidExternalMatchSchedule` (mức lớp: `startsAt ≥ now + MIN_LEAD_TIME`, `endsAt > startsAt`, `endsAt − startsAt ≤ MAX_DURATION` — hằng `MatchRules` của MATCH-001, user chốt 2026-11-14 (BR-ME19); lỗi gắn `startsAt` / `endsAt`). `UpdateExternalMatchRequest`: cùng field (tuỳ chọn, không `positions`); validator kiểm cặp giờ sau khi áp thay đổi ở service.
- `SearchRecruitmentsRequest(Double lat, Double lng, @DecimalMin("1") @DecimalMax("20") Double radiusKm, @OneOf(vị trí) String position, Integer page, Integer pageSize)` + `@ValidMercenarySearch`.
- `WithdrawApplicationRequest(@NotNull @OneOf({"PENDING","APPROVED"}) String expectedStatus)`.
- `SendConversationMessageRequest`: tái dùng MATCH-001 §7.
- `CreateMercenaryRatingRequest(Boolean isNoShow, Integer score, @Size(max = 500) String comment)` + `@ValidRating` (REPUTATION-001; developer tổng quát hoá qua interface chung khi reuse-scan). `isNoShow` của phía mercenary kiểm ở service (`MERCENARY_016`).
- Report dùng lại `CreateReportRequest`; Admin dùng lại `ResolveDisputeRequest`.
- Hằng một nơi `MercenaryRules`: `MAX_QUANTITY = 10` (BR-ME05), `DEFAULT_RADIUS_KM = 3`, `MIN_RADIUS_KM = 1`, `MAX_RADIUS_KM = 20`, `MAX_POSITIONS = 4`, `DISTANCE_ROUNDING_KM = 0.5`, `MAX_OPPONENT_NAME = 100`. Giới hạn tin nhắn: `ConversationRules.MAX_MESSAGE_LENGTH` (MATCH-001 §4.14).
- `MercenaryMapper` (`uses MapperSupport`, `unmappedTargetPolicy = ERROR`):
  - **Hiện sân / đội trưởng ở tin, đơn (BR-ME04):** `match.venue` và `captainName` khác `null` khi `viewerIsCaptain` **hoặc** đơn của người xem ở tin có trạng thái hiệu lực `APPROVED`; ngược lại `null`. `match.area` luôn trả khi sân có phường. Chat: `ConversationMapper` luôn trả tên đội trưởng / người gửi (ngoại lệ chat — §4.7).
  - `distanceKm` = `ceil(d / 0.5) × 0.5`, tối thiểu `0.5` (MINOR 15 — làm tròn thô để không tam giác hoá ra vị trí sân).
  - `reliabilityPenalty` mọi response = số điểm bị trừ **≥ 0** (MINOR 11): 2.13 trả `-delta`; report trả cột `reliability_penalty` (đã dương).
  - `match.source` = `SYSTEM` khi `matchId != null`, `EXTERNAL` khi `externalMatchId != null`.
  - Tên user đã xoá → `MapperSupport.maskName` / `maskAvatar` (endpoint thường — BC-U02); API Admin trả tên thật + cờ.
- `ConversationMapper` (MATCH-001 §7): thêm khối `MERCENARY_APPLICATION` (§4.7).
- `MercenaryReputationMapper`: dùng chung hàm làm tròn / mức điểm với `ReputationMapper` (REPUTATION-001 §7).

## 8. Cấu hình & Dependencies

```properties
arenaflow.mercenary.lifecycle.batch-size=200
arenaflow.mercenary.lifecycle.max-batches-per-run=50
arenaflow.mercenary.lifecycle.cancel-notice-lookback-days=7
```

| Tham số | Lý do |
|---|---|
| Chạy trong `MatchLifecycleScheduler` (cron `arenaflow.match.lifecycle.cron` của MATCH-001, 5 phút) | Tin hết hạn theo giờ kết thúc trận — cùng nhịp job `ARCHIVED`; không thêm luồng scheduling (`spring.task.scheduling.pool.size` giữ 4) |
| `batch-size` 200 × `max-batches-per-run` 50 | Như MATCH-001 §8 — transaction ngắn; dùng chung cho đóng tin và quét đơn mồ côi |
| `cancel-notice-lookback-days` 7 | Báo huỷ trận chỉ quét trận bị huỷ có giờ kết thúc trong 7 ngày gần nhất (huỷ chỉ trước giờ bắt đầu → job 5 phút gặp lại ngay); đủ bù job ngừng vài ngày |

Không thêm Maven dependency (WebSocket đã thêm ở MATCH-001 v2.4).

## 9. Security

- Không endpoint public mới; không đổi `SecurityConfig`. Kênh realtime: topic mới kiểm quyền ở `SUBSCRIBE` (§4.8, ADR-0014).
- Quyền kiểm trong service từ một câu context (`MercenaryGuard`, `ConversationAccessPolicy`, `TeamGuard`). Người dùng đã xoá tài khoản (token còn ≤ 1 giờ) bị chặn `USER_001` ở mọi thao tác ghi tạo dữ liệu mới của chính họ (2.7, 2.15, 2.18, 2.20) — cờ trong câu context, không thêm truy vấn. Admin: `/api/v1/admin/**` + audit log sau commit.
- **Vị trí (BC-ME03, BR-ME04):** toạ độ / tên / địa chỉ sân chỉ trả cho đội trưởng đội tuyển và người có đơn `APPROVED`; tìm kiếm không có cột sân. Khoảng cách làm tròn 0,5 km (MINOR 15) để giảm khả năng tam giác hoá vị trí sân từ nhiều toạ độ [Inference — vẫn ước lượng được vùng ~0,5 km].
- Toạ độ người dùng gửi lên (2.3, 2.4) chỉ dùng tính toán trong request; **không lưu, không log** (BC-ME05).
- Nội dung chat, `reason`, `evidence`, `comment`, `opponentName`: không log. Log chỉ id.
- Thông tin ứng viên trả cho đội trưởng (2.8): tên, ảnh, vị trí ưa thích, uy tín công khai — không email / số điện thoại.
- XSS: lưu nguyên văn (đã trim), FE escape khi hiển thị (`security-backend.md` §4.2).

## 10. Lệch convention / chuẩn có chủ đích

| Chuẩn | Lệch | Lý do | Trạng thái |
|---|---|---|---|
| ADR-0003 (`@Version` + retrier) | Tin / đơn không `@Version`; đổi trạng thái bằng CAS `UPDATE … WHERE` | Thoả 3 điều kiện ngoại lệ (tiền lệ `MatchRequest`, `Pairing`, `Report`) | ADR-0003 Sửa đổi 2026-11-14 |
| ADR-0007 (phạm vi khoá của `deleteAccount`) | `deleteAccount` khoá thêm dòng tin (bằng `UPDATE`) rồi đơn của user | BR-ME18; thứ tự tin → đơn trùng các luồng khác | ADR-0007 Sửa đổi 2026-11-14 |
| UC-ME01 / BR-ME07 / BR-ME09 "tự EXPIRED / CLOSED / REJECTED" | Job 5 phút ghi trạng thái; API đọc / ghi dùng trạng thái hiệu lực ngay | Không tăng số truy vấn MATCH-001 2.5/2.10 (đang 6/W6); thông báo trễ tối đa ~5 phút | User chốt 2026-11-14 (BR-ME07) |
| ADR-0011 quy tắc 2 (payload không danh sách lớn) | `MercenaryRecruitmentClosedEvent.rejectedApplicationIds`, `MercenaryGameCancelledEvent.applicationIds` | Danh sách nhỏ (đơn chờ của một tin / đơn đã duyệt của một trận); người nghe không phải đoán đơn nào vừa đổi (MINOR 12, N-M2) | ADR-0011 Sửa đổi 2026-11-14 |
| ADR-0014 quyết định 3 (một mẫu topic) | Thêm `/topic/conversations/{id}` | Chat theo đơn | ADR-0014 Sửa đổi 2026-11-13 |
| Mã lỗi theo miền | Hết lượt thử dùng `MATCH_014`; chat dùng miền `CHAT` | Như REPUTATION-001; `CHAT` là miền chung của cuộc trò chuyện | Ghi ở `API_Spec.md` §5 |

## 11. Tài liệu cần đồng bộ khi implement

Sau khi mọi phase code xong + test PASS (CLAUDE.md §8):

| Tài liệu | Nội dung | Nhánh |
|---|---|---|
| `docs/database/schema.dbml`, `schema-design.md` (`mercenary_*`, `external_match`, `rating`, `report`, `"user"`, cột / CHECK mercenary của `conversation` — bảng do MATCH-001 V6 tạo) | V9 | `feature/MERCENARY-001-*` |
| `docs/database/data-model.md` §3.6, §3.7 | Bảo vệ vị trí "phường, tỉnh"; sửa / đóng tin; trận ngoài hệ thống; cấu trúc lưu đánh giá cho mercenary (đóng `[cần xác nhận]` ở §3.4 "Cách lưu đối với mercenary") | `feature/MERCENARY-001-*` |
| `docs/agent/api-convention.md` §5 (29 endpoint của mã này vào bảng 2xx — §3; 2.14–2.17 do MATCH-001 v2.4 thêm), §6 (domain `MERCENARY`, `REPORT_009`; `CHAT_002..004` do MATCH-001 v2.4) | Coordinator | `chore/agent-framework-*` |
| `docs/agent/convention-backend.md` §1 | `application/support/mercenary/`, `application/event/mercenary/` | `chore/agent-framework-*` |
| `docs/agent/CLAUDE.md` §2 | Dòng điều hướng MERCENARY-001 | `chore/agent-framework-*` |
| NOTIFICATION-001 (khi thiết kế) | Job xoá chat hết hạn lưu dùng chung mọi cuộc trò chuyện — điều kiện chung ở MATCH-001 `Component_Class_Design.md` §11; phần riêng mã này: cuộc trò chuyện có `external_match_id` → trận ngoài `SCHEDULED` (mốc `match_end_time`) / `CANCELLED` (mốc `cancelled_at`) + 90 ngày < now; xoá dòng `conversation` (tin xoá theo `ON DELETE CASCADE`); nghe event §4.11 | — |

## 12. Danh sách [cần xác nhận]

Đánh số toàn thiết kế MERCENARY-001: 1–7, 16, 17 ở đây, 8–15 ở `Processing_Flow.md` §9. Câu hỏi nghiệp vụ còn mở: `API_Spec.md` §6.

| # | Nội dung | Loại |
|---|---|---|
| 1 | **Thứ tự implement:** V9 cần V5–V8 (VENUE-001, MATCH-001, MATCH-002, REPUTATION-001) đã code — gồm lõi cuộc trò chuyện và kênh realtime của MATCH-001 v2.4 (bảng `conversation*` ở V6, `ConversationService`); MATCH-002 `[cần xác nhận #4]`, `#5` đã kiểm | **Chặn code** — implement sau bốn mã kia |
| 2 | ~~Quy tắc chờ câu hỏi nghiệp vụ B1–B12~~ — **đã chốt** (user, 2026-11-14; §13) | Đã đóng |
| 3 | ~~Report bùng của mercenary nhắm vào đội tác động như report đội–đội~~ — **đã chốt** B9 (BR-ME14) | Đã đóng |
| 4 | Dữ liệu `mercenary_recruitment` trên Neon dev trước V9 (cột `NOT NULL` không mặc định) | Không chặn — kiểm khi lập kế hoạch |
| 5 | Job xoá tin nhắn theo hạn lưu cho mọi cuộc trò chuyện thuộc NOTIFICATION-001 (chưa thiết kế) — điều kiện: §11 + MATCH-001 §11 | Không chặn |
| 6 | ~~Hai CTE ghi `team` và `"user"` trong một câu~~ — **bỏ** (2026-11-14): mercenary không còn được cộng điểm trong câu hoàn tất trận của đội (BR-ME15 sửa) | Đã đóng |
| 7 | ~~Rủi ro ước lượng vị trí sân~~ — **đã xử lý** bằng làm tròn 0,5 km (MINOR 15) | Đã đóng |
| 16 | ~~Đội tuyển đã giải tán trong chat theo đơn~~ — **user chốt 2026-11-15:** áp như chat hai đội — người đã giải tán đội tuyển chỉ xem (§4.7); 2.20 với người đó → `TEAM_016` (§4.5); +2 cột ở `findContext` (khối mercenary) và +2 cột ở `findMercenaryReportContext`, không thêm câu | Đã đóng |
| 17 | ~~Người đã giải tán đội tuyển lấy `conversationId` từ đâu~~ — **user chốt 2026-11-15:** MATCH-001 2.20 "Tin nhắn của tôi" (`findMine` dùng khối cột mercenary của `findContext` — §4.7; `ConversationListRowView` mở rộng `ConversationContextView` gồm cột mercenary §5.5) | Đã đóng |

## 13. Quyết định nghiệp vụ đã chốt (user, 2026-11-14 — SRS `SYSTEM-001` cập nhật cùng ngày)

| # | Quyết định | Nơi áp dụng |
|---|---|---|
| B1 | Phường của sân bắt buộc khi thêm / sửa sân; sân cũ chưa có phường: tìm GPS bình thường, `area = null`, không ra khi tìm không GPS (BR-V11) | VENUE-001 v4.0; §5.2 `searchByProvince` |
| B2 | Tối đa một đơn `APPROVED` mỗi trận (BR-ME06) | §5.3 `OVERLAPPING_APPROVAL` |
| B3 | Đội không `ACTIVE`: chặn tạo / sửa / duyệt; `INACTIVE` vẫn đóng tin, từ chối; tin ẩn khỏi tìm, không nhận ứng tuyển (BR-ME05) | `Processing_Flow.md` §1.1 |
| B4 | Mỗi (trận, đội, vị trí) tối đa một tin `OPEN`/`FILLED`; trùng → `409 MERCENARY_023` (BR-ME05) | §5.1 index unique |
| B5 | Số lượng mỗi tin 1..10 | §7 `MAX_QUANTITY` |
| B6 | Từ giờ bắt đầu trận: ẩn khỏi tìm, không ứng tuyển, không duyệt (`MERCENARY_013`); `PENDING` thành `REJECTED` khi tin `EXPIRED` (BR-ME07) | §4.3; §5.2 tìm kiếm |
| B7 | Không GPS + hồ sơ chưa có phường → `422 MERCENARY_018` (BR-ME17) | `Processing_Flow.md` §3.3 |
| B8 | Đánh giá của mercenary trên hồ sơ đội chỉ hiện sau khi đội đánh giá lại (BR-ME13) | §4.6 |
| B9 | Report của mercenary với đội tác động như report đội–đội (BR-ME14) | §4.12; `API_Spec.md` §1.5 |
| B10 | Hàng chờ tranh chấp mercenary riêng | 2.25–2.27 |
| B11 | Thông báo tự đóng / tự từ chối trễ tối đa ~5 phút (BR-ME07) | §4.9 |
| B12 | Xoá tài khoản: đơn `PENDING`/`APPROVED` của trận chưa bắt đầu (đã ghép / trận ngoài chưa huỷ) → `WITHDRAWN`, không trừ điểm, tin `FILLED` → `OPEN`, báo đội trưởng; đơn `APPROVED` của trận đã huỷ giữ nguyên (BR-ME18) | §4.14; USER-001 v4.1 |
| M3 | Tên đội trưởng hiện trong chat từ khi đơn `PENDING`; ẩn trước khi duyệt chỉ áp cho tên sân / địa chỉ / toạ độ (BR-ME04) | §4.7, §7 |
| M4 | Từ giờ bắt đầu: không tạo tin, không tăng số lượng; giảm số lượng và đóng tin vẫn được (BR-ME08) | `Processing_Flow.md` §1.1 (2.1, 2.5, 2.33) |
| E1–E4 | Trận ngoài hệ thống (BR-ME19, AC-ME11) | §4.15, §5.1; `API_Spec.md` 2.28–2.33 |
| 13c | Mercenary hoàn thành (+2, tử số, chuỗi) khi đội trưởng chấm sao; "Không đến" / chưa đánh giá → không (BR-ME15) | §4.10, §5.4 |
| E5 | Giới hạn trận ngoài: bắt đầu ≥ hiện tại + 1 giờ, ≤ 12 giờ, `opponentName` ≤ 100, sửa sân / giờ / trình độ chỉ trước giờ bắt đầu và khi chưa có đơn `APPROVED` (BR-ME19) | §7 `@ValidExternalMatchSchedule`; `Processing_Flow.md` §1.1 |
| E6 | Khai báo / đổi sân của trận ngoài tính là sân "được chọn" (BC-V05) — `markSelected` ở 2.28, 2.31 | §4.15; VENUE-001 §4.6 (v4.0) |
| N-M3 | Huỷ trận ngoài chỉ trước giờ bắt đầu (như BR-M11); từ giờ bắt đầu `422 MERCENARY_013` (BR-ME19, AC-ME11) | §4.15; `Processing_Flow.md` §1.1, §3.13 |
| N-M2 | Mọi mercenary `APPROVED` được báo khi trận bị huỷ, kể cả tin đã đóng — `MercenaryGameCancelledEvent` (BR-ME09) | §4.9, §4.11, §5.1 |
