# Component & Class Design — Kèo (luồng A, luồng B) & vòng đời kèo

| Mục | Giá trị |
|---|---|
| Mã chức năng | `MATCH-001` |
| Phạm vi tầng | Backend (Spring Boot REST API). Frontend: ngoài phạm vi repo này |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · Spring Data JPA · Flyway · PostgreSQL |
| Quy ước áp dụng | `docs/agent/convention-backend.md`, `docs/agent/security-backend.md`, `docs/agent/non-functional-standards.md` · kế thừa USER-001 `Component_Class_Design.md` §4.6, §5, §10; VENUE-001 `Component_Class_Design.md` §4.6 (port), §4.7 (event), §4.9 (retrier overload), §4.13 (audit) · ADR-0003 (+ Sửa đổi 2026-11-09), ADR-0006, ADR-0008, [ADR-0011](../../decisions/0011-domain-events-after-commit.md), [ADR-0012](../../decisions/0012-scheduled-jobs-claim-with-skip-locked.md), [ADR-0013](../../decisions/0013-reputation-columns-atomic-update.md), [ADR-0017](../../decisions/0017-vietnam-local-datetime.md), [ADR-0018](../../decisions/0018-jvm-time-zone-as-single-clock-source.md) |
| Ràng buộc dữ liệu | PostgreSQL ≥ 18 (`uuidv7()` — §5.2) · V1 + V5 (VENUE-001) + V6 (SYSTEM-002 — cột thời gian `TIMESTAMP` giờ Việt Nam) + `V7__match_flow_a_lifecycle.sql` (§5.1 — v2.4: gồm bỏ `chat_message`, tạo `conversation` + `conversation_message`, index `team.disbanded_by`) · đợt MATCH-002 (nhánh luồng B — `Component_Class_Design.md` §1.1): + `V8__match_flow_b_pairing.sql` (MATCH-002 `Component_Class_Design.md` §5.1); entity hiện có `domain/.../entity/match/{Match, MatchRequest, CostCustomItem, Sport}`, `team/Team`, `user/User` |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-09 · v1.0 (baseline) · hiện tại **v3.2** (developer · 2026-11-25) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan kiến trúc

Layered theo 4 module Maven (ADR-0006) như USER-001. Hợp đồng: `API_Spec.md`. Luồng, transaction, số truy vấn, khoá: `Processing_Flow.md`. Điểm mới:
- **Vòng đời kèo** do `MatchService` / `MatchRequestService` (người dùng) và `MatchLifecycleJobService` (hệ thống) điều khiển; mọi thao tác ghi chạy qua `ConcurrencyConflictRetrier.execute(MATCH_014, …)` (ADR-0003, `@Version` trên `Match`).
- **Lazy check hết hạn** dùng chung một thao tác `MatchExpiry.expireIfDue` cho API ghi và job (§4.6); API đọc chỉ tính trạng thái hiệu lực trong SQL.
- **Lõi `ReputationService`** dùng chung (§4.8, ADR-0013): cộng/trừ Reliability Score đội/cá nhân bằng một `UPDATE` nguyên tử có chặn [0,100].
- **Domain event** `ApplicationEvent` cho thông báo (NOTIFICATION-001) và dữ liệu dẫn xuất tính lại được; không dùng để ghi dữ liệu nghiệp vụ (§4.9, ADR-0011).
- **Scheduled job** hết hạn + `ARCHIVED` claim lô bằng `FOR NO KEY UPDATE SKIP LOCKED` (§4.10, ADR-0012).
- **Bộ môn**: đọc + Admin bật/tắt (§4.4).
- **Chat dùng chung** (v2.4, §4.14–§4.15): bảng `conversation` + `conversation_message` (V7 — §5.1), `ConversationService`, `ConversationAccessPolicy` (+ `TeamMatchConversationPolicy` luồng A), endpoint 2.16–2.20, kênh realtime STOMP topic `/topic/conversations/{id}` (ADR-0014). Mọi chat của hệ thống dùng chung: chat hai đội luồng A (tạo ở 2.6), luồng B (MATCH-002 thêm nhánh + tạo ở MATCH-002 2.1), chat theo đơn ứng tuyển (MERCENARY-001). `chat_message` (V1) bị bỏ.
- **Kèo luồng B** (v2.0): tạo / tìm / xem / huỷ / hết hạn kèo `NEED_VENUE` ở các thành phần trên (nhánh theo `Match.Type`) — **code ở đợt MATCH-002**, sau V8 (§1.1; đã code 2026-11-25). Pairing, chat, xác nhận, chi phí + cọc luồng B, migration V8 và repository lựa chọn (`MatchVenueOptionRepository`, `MatchTimeOptionRepository`, `PairingRepository`): MATCH-002 `Component_Class_Design.md`.

**Điều kiện tiên quyết (VENUE-001 code):** `VenueSelectionService`, overload `ConcurrencyConflictRetrier.execute(ErrorCode, Supplier)`, `AdminAuditLog`, `SchedulingConfig`, package `application/event/`, mã `VENUE_001/002`, migration `V5__venue_management.sql` — thiết kế ở VENUE-001, chưa có trong code `[cần xác nhận #5]`.

**Thứ tự code (v2.4, user chốt 2026-11-15 — §1.1):** đợt 1 = MATCH-001 phần luồng A + lõi chat (bảng, service, endpoint 2.16–2.20) + kênh realtime, migration V7 — **không** phụ thuộc MATCH-002; xong đợt 1 là chat luồng A chạy đủ. Các nhánh luồng B nằm trong thành phần của mã này (bảng §1.1) dùng bảng / cột của V8 nên code ở **đợt MATCH-002**, sau V8. MATCH-002 / MERCENARY-001 dựa vào lõi chat (thêm nhánh / ngữ cảnh). Kênh realtime cần thêm dependency `spring-boot-starter-websocket` và cấu hình `/ws` (§8, §9) — `[cần xác nhận #12]` chặn phase realtime.

### 1.1. Hai đợt code (user chốt 2026-11-15 — `[cần xác nhận #19]` đã chốt)

> **Trạng thái (2026-11-25):** đợt 1 đã vào `main` (V7); **đợt MATCH-002 đã code** (V8, nhánh `feature/MATCH-002-match-flow-b-impl`) — cột "Đợt MATCH-002" dưới đây là hiện trạng; cột "Đợt 1" chỉ còn giá trị lịch sử (2.1 không còn trả `MATCH_016` cho `NEED_VENUE`). Các thao tác vẫn chặn kèo B bằng `MATCH_016`: 2.4, 2.6, 2.7 (§4.1, `Processing_Flow.md` §1.1).

Đợt 1 không đụng bảng / cột luồng B: `pairing`, `match_venue_option`, `match_time_option` (V1, V8 sửa), `cost_deposit_amount`, `display_order` của lựa chọn (V8). Vì 2.1 chặn tạo kèo `NEED_VENUE`, trong đợt 1 **không** có kèo luồng B nào → các nhánh luồng B của 2.2, 2.3, 2.5, 2.11, job, lazy expiry không bao giờ chạy; field luồng B trong response luôn ở giá trị của kèo A (`venueOptions = []`, `timeOptions = []`, `venueOptionCount = null`, `timeOptionCount = null`, `depositAmount = null`).

| Thành phần (mã này) | Đợt 1 — MATCH-001 (V7) | Đợt MATCH-002 (sau V8) |
|---|---|---|
| `CreateMatchRequest`, `@ValidMatchSchedule` (§7) | Khai báo đủ field (`type`, `venueOptionIds`, `timeOptions`); validator chỉ kiểm nhánh A, bỏ qua khi `type = NEED_VENUE` | Thêm nhánh B của validator |
| `MatchService.createMatch` (§4.1, 2.1) | `type = NEED_VENUE` → `422 MATCH_016` **trước truy vấn đầu tiên** (0 truy vấn) | Nhánh `NEED_VENUE` (§4.1; `Processing_Flow.md` §3.1) + `MatchVenueOptionRepository`, `MatchTimeOptionRepository`; bỏ chặn |
| `searchMatches`, `listTeamMatches` (2.2, 2.11; §5.2 "Tìm kèo") | Lọc `type` giữ nguyên; khoảng giờ theo `match_start_time`; không subquery / `LATERAL` trên bảng lựa chọn | `venueOptionCount` / `timeOptionCount`, khoảng giờ theo `match_time_option`, nearby `LATERAL` trên `match_venue_option` |
| `getMatch` / `findDetailById` (2.3; §5.2) | Chỉ `LEFT JOIN venue`; không `findOptionRows` | `LEFT JOIN pairing` (`confirmedPairingId`), cột `cost_deposit_amount`, `findOptionRows` |
| `cancelMatch` `OPEN` (2.5) | Chỉ kèo A | Nhánh kèo B: bỏ `MATCH_010`, `PairingRepository.supersedeNegotiating` |
| `MatchExpiry` (§4.6), job hết hạn (§4.3; `claimDueForExpiry`) | Chỉ `expirePendingByMatchIds` | `type` trong claim + `PairingRepository.expireNegotiatingByMatchIds` |
| Entity `Match`, `CostCustomItem` | Theo V7 | Sửa theo V8 (MATCH-002 `Component_Class_Design.md` §6) |
| `TeamMatchConversationPolicy`, `ConversationSql` (§4.14, §5.2) | Nhánh luồng A | Nhánh `NEED_VENUE` + cột / join `pairing` (MATCH-002 `Component_Class_Design.md` §4.2) |
| `ErrorCode.MATCH_016` | **Thêm ở đợt 1** (2.1, 2.4, 2.6, 2.7 dùng) | — |
| Event `MatchConfirmedEvent.pairingId`, `MatchCancelledEvent` | Khai báo field; `pairingId` luôn `null` | MATCH-002 điền |
| `MatchMapper` (§7) | `toResponse(MatchDetailView, List<CostCustomItem>)` — `venueOptions`, `timeOptions` = `[]` | Overload `toResponse(…, List<MatchOptionRowView>)` và `toCreatedNeedVenue(…, List<TimeOptionView>)` (kiểu `MatchOptionRowView`, `TimeOptionView` do MATCH-002 thêm) |
| Projection `MatchDetailView` (`costDepositAmount`, `confirmedPairingId`), `MatchSummaryView` (`venueOptionCount`, `timeOptionCount`) (§5.3) | Giữ shape; SQL đợt 1 trả `NULL` cho các cột này (vd `CAST(NULL AS bigint) AS cost_deposit_amount`) | SQL đọc cột / subquery thật |
| Test kèo B (`Processing_Flow.md` §3.1 "Kèo B", §3.5, §3.13, §5) | Test đợt 1: 2.1 `NEED_VENUE` → `MATCH_016`, không ghi gì | Chạy ở đợt MATCH-002 |

**Phần của lõi chat do MERCENARY-001 thêm (sau V10)** — đợt 1 không đụng bảng / cột của V10 (`mercenary_application`, `external_match`, `conversation.application_id`, `conversation.external_match_id`, `report.application_id`):

| Thành phần (mã này) | Đợt 1 — MATCH-001 (V7) | MERCENARY-001 thêm (V10 — MERCENARY-001 `Component_Class_Design.md` §4.7) |
|---|---|---|
| `ConversationRepository.findContext` / `ConversationSql` (§5.2) | Cột chung + khối `TEAM_MATCH` | Khối cột `MERCENARY_APPLICATION` (join `mercenary_application`, `external_match`) |
| `findMine` (2.20, §5.2) | Giờ bắt đầu = `match.match_start_time`; tên / ảnh hai bên theo `TEAM`; nhánh `party_b_type = 'USER'` của `mine` giữ (chạy được ở V7, chưa trả dòng nào) | Giờ bắt đầu `external_match.match_start_time` (theo `c.external_match_id`); tên / ảnh bên `USER` (ứng viên) |
| `ConversationAccessPolicy` (§4.14), kiểm quyền `SUBSCRIBE` (§4.15) | `TeamMatchConversationPolicy` (map policy theo `contextType` đủ cho mọi giá trị enum của đợt 1) | `MercenaryApplicationConversationPolicy` (cùng lúc thêm giá trị enum) |
| Entity `Conversation` (§6) | `ContextType { TEAM_MATCH }` (khớp CHECK của V7; thiếu policy cho một giá trị → lỗi khởi động, §4.14) | Giá trị `MERCENARY_APPLICATION`, `applicationId`, `externalMatchId`; V10 nới CHECK |
| `ConversationContextView`, `ConversationMapper` (§5.3, §7) | Cột / khối `TEAM_MATCH`; field mercenary = `null` | Cột / khối `MERCENARY_APPLICATION` (MERCENARY-001 §5.5, §4.7) |

## 2. Package Structure (delta)

```text
common/        com.phananh.arenaflow.common
└── exception/       ErrorCode (+ MATCH_001..MATCH_016, SPORT_001..SPORT_002, CHAT_001..CHAT_004 — v2.4)

domain/        com.phananh.arenaflow.domain
├── entity/match/    Match, MatchRequest, CostCustomItem (sửa — §6); Sport (giữ)
├── entity/team/     Team (sửa: 6 cột uy tín/bộ đếm report updatable = false — §6)
├── entity/user/     User (sửa: 6 cột uy tín/bộ đếm report updatable = false — §6)
├── entity/chat/     Conversation, ConversationMessage (mới — v2.4, §6); ChatMessage (xoá — bảng V1 bị bỏ)
├── repository/      MatchRepository (+ fragment MatchRepositoryCustom / MatchRepositoryImpl), MatchRequestRepository,
│                    CostCustomItemRepository, SportRepository, ConversationRepository, ConversationMessageRepository,
│                    ConversationSql (hằng SQL dùng chung) (mới); TeamRepository, UserRepository (sửa)
└── projection/      MatchDetailView, MatchSummaryView, MatchRequestView, MatchRequestContextView,
                     MatchAuthorAccessView, MatchAvailabilityView, DueMatchView, TeamCaptainContextView,
                     ConversationContextView, ConversationMessageView, ChatCursorView, ConversationListRowView, MineCountView (mới)

application/   com.phananh.arenaflow.application
├── service/         MatchService, MatchRequestService, SportService, MatchLifecycleJobService, ReputationService,
│   │                ReliabilityChange, ReliabilityReason, MatchStatusFilter, MatchRoleFilter,
│   │                MatchRequestStatusFilter, ConversationService (mới)
│   └── impl/        MatchServiceImpl, MatchRequestServiceImpl, SportServiceImpl, MatchLifecycleJobServiceImpl,
│                    ReputationServiceImpl, ConversationServiceImpl
├── event/match/     MatchConfirmedEvent, MatchRequestSentEvent, MatchRequestRejectedEvent,
│                    MatchRequestWithdrawnEvent, MatchCancelledEvent, MatchExpiredEvent, MatchArchivedEvent (mới)
├── event/reputation/ ReliabilityScoreChangedEvent, ReputationSubjectType (mới)
├── support/         MatchGuard, MatchExpiry, MatchCancellation (+ CancellationResult), MatchWriteOutcome,
│   │                GeoBoundingBox (mới — dùng chung với VenueServiceImpl);
│   │                TeamGuard (sửa — §4.5); AdminAuditLog (sửa — §4.11)
│   ├── reputation/  TimeBandPenaltyPolicy (mới)
│   ├── match/       MatchLifecycleProperties, MatchRules (mới)
│   └── chat/        ChatRealtimeGateway, RealtimeFrame, ConversationAccessPolicy, ConversationAccess,
│                    TeamMatchConversationPolicy, ConversationRules (mới — port + policy, không phụ thuộc spring-websocket)
├── validation/      ValidMatchSchedule, ValidCostAgreement, ValidMatchSearch (+ validator), GeoQueryRequest (mới);
│                    ValidGeoQuery + ValidGeoQueryValidator (sửa — tổng quát hoá từ VENUE-001 qua GeoQueryRequest);
│                    ValidChatCursor (mới); OneOf, ValidSkillLevels (tái dùng)
├── dto/request/     CreateMatchRequest (+ TimeOptionRequest), SendMatchRequestRequest, RejectMatchRequestRequest,
│                    CancelMatchRequest, WithdrawMatchRequestRequest, UpdateCostAgreementRequest, CostCustomItemRequest,
│                    SearchMatchRequest, UpdateSportRequest, SendConversationMessageRequest, ChatHistoryQuery
├── dto/response/    MatchResponse (+ MatchTeamResponse, MatchVenueResponse, MatchTimeOptionResponse,
│                    MatchCostAgreementResponse, CostCustomItemResponse), CostAgreementResponse, MatchSummaryResponse,
│                    MatchRequestResponse, AcceptMatchRequestResponse, MatchCancellationResponse,
│                    MatchRequestWithdrawalResponse, SportResponse, ConversationResponse (+ PartyResponse),
│                    ConversationMessageResponse, ChatHistoryResponse,
│                    ConversationSummaryResponse (+ PartyBriefResponse, LastMessagePreviewResponse)
└── mapper/          MatchMapper, SportMapper, ConversationMapper (MapStruct); MapperSupport (tái dùng)

api/           com.phananh.arenaflow.api
├── match/           MatchController, MatchRequestController, TeamMatchController
├── sport/           SportController, AdminSportController
├── chat/            ConversationController, ProfileConversationController (mới — v2.4)
├── realtime/        WebSocketConfig, StompAuthChannelInterceptor, StompChatRealtimeGateway,
│                    StompErrorHandler, StompRealtimeException, WebSocketProperties (mới — v2.4)
├── scheduler/       MatchLifecycleScheduler (mới)
└── config/          ClockConfig (có từ SYSTEM-002 — dùng lại); LocalDateTimeParamConverter (mới — §7);
                     WebMvcConfig (sửa — đăng ký LocalDateTimeParamConverter); SchedulingConfig (sửa — bean taskScheduler, §8);
                     SecurityConfig (sửa — §9, v2.4)
```

Thành phần dùng chung mới/sửa (`TeamGuard` method mới, `ReputationService`, `TimeBandPenaltyPolicy`, `MatchWriteOutcome`, `AdminAuditLog` overload, `ValidMatchSearch` vs `ValidGeoQuery` của VENUE-001) → developer MUST chạy `reuse-scan` trước khi tạo.

## 3. Controller Layer

Quy ước như USER-001 §3 (`@Validated`, `ResponseEntity<ApiResponse<…>>`, 201 không `Location`); `@CurrentUser UUID currentUserId` (không cần `CurrentActor` — không có quyền phụ thuộc role ngoài `/admin/**`).

| Controller | `@RequestMapping` | Endpoint | Service |
|---|---|---|---|
| `MatchController` | `/api/v1/matches` | 2.1–2.7 | `MatchService` (2.1–2.5), `MatchRequestService` (2.6, 2.7) |
| `MatchRequestController` | `/api/v1/match-requests` | 2.8–2.10 | `MatchRequestService` |
| `TeamMatchController` | `/api/v1/teams/{teamId}` (`/matches`, `/match-requests`) | 2.11, 2.12 | `MatchService`, `MatchRequestService` |
| `SportController` | `/api/v1/sports` | 2.13 | `SportService` |
| `AdminSportController` | `/api/v1/admin/sports` | 2.14, 2.15 | `SportService` |
| `ConversationController` (v2.4) | `/api/v1/conversations/{conversationId}` | 2.16–2.19 (MERCENARY-001 2.14–2.17 là cùng endpoint) | `ConversationService` |
| `ProfileConversationController` (v2.4) | `/api/v1/profile/conversations` | 2.20 | `ConversationService` |

**Mã thành công & tiêu chí `api-convention.md` §5** (chuyển từ `API_Spec.md` v1.0 §2):

| Endpoint | Thành công | Tiêu chí |
|---|---|---|
| 2.1, 2.6, 2.16 | 201 | B2 |
| 2.2, 2.3, 2.7, 2.11–2.14, 2.17, 2.19, 2.20 | 200 | B1 |
| 2.4 | 200 | B4 (+ B3: `itemId`, `updatedAt`) |
| 2.5 | 200 | B3 (`cancelledAt`, `reliabilityPenalty`), B4 |
| 2.8 | 200 | B3 (`respondedAt`, đội đối thủ) |
| 2.9, 2.18 | 204 | N1 |
| 2.10 | 200 | B3 (`withdrawnAt`, `reliabilityPenalty`, trạng thái kèo) |
| 2.15 | 200 | B4 |

**Lý do chọn path/method** (chuyển từ `API_Spec.md` v1.0 §2 "Ghi chú hợp đồng"): 2.5, 2.8–2.10 là action sub-resource (`api-convention.md` §2.1), dùng `POST` theo tiền lệ `/invitations/{id}/accept`, `/teams/{id}/disband`. 2.6 tạo tài nguyên con dưới kèo; thao tác trên từng yêu cầu dùng `/match-requests/{id}` (tiền lệ `/teams/{id}/invitations` + `/invitations/{id}/…`). 2.4 `PUT` thay toàn bộ thoả thuận (idempotent). 2.10 luôn trả `200` có body (kể cả rút `PENDING`) để một endpoint có một hình dạng response. 2.15 đổi một field (`isActive`) → `PATCH` thường, không phải action.

- 2.2: `@Valid @ModelAttribute SearchMatchRequest` (§7). Danh sách khác: `@RequestParam` như `AdminTeamController` (enum filter qua `MethodArgumentTypeMismatchException` → 400).
- 2.9: `@RequestBody(required = false)` (tiền lệ USER-002 disband). 2.5, 2.10: body bắt buộc (`expectedStatus`).
- Chat (v2.4): theo **cuộc trò chuyện** `/conversations/{id}` (không lồng dưới `/match-requests` hay `/pairings`): một cuộc trò chuyện luồng A sống qua nhiều yêu cầu của cùng đội (gửi lại sau khi rút — BR-N04); một bộ endpoint cho mọi loại ngữ cảnh. 2.17 cursor theo `api-convention.md` §7.2 (`@Valid @ModelAttribute ChatHistoryQuery` + `@ValidChatCursor`). 2.16 `@Valid @RequestBody SendConversationMessageRequest`; 2.18 không body. 2.20 dưới `/profile` (tiền lệ danh sách của chính người gọi: USER-001 `/profile`, MERCENARY-001 2.9, 2.22) — `GET /conversations` không có chủ thể sẽ đọc như danh sách toàn hệ thống; phân trang **offset** (`api-convention.md` §7.1): danh sách xếp theo hoạt động gần nhất nên dòng đổi vị trí mỗi khi có tin mới — con trỏ không chống được việc này; offset cho `totalItems`, FE tải lại trang 1 khi có tin mới.
- `SecurityConfig` (v2.4): `/ws/**` — §9. `/api/v1/admin/**` đã `hasRole('ADMIN')`.

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
Deps: `MatchRepository`, `CostCustomItemRepository`, `SportRepository`, `TeamRepository` (`existsById` cho 2.11), `MatchRequestRepository` (`existsByMatchIdAndStatus`), `MatchVenueOptionRepository`, `MatchTimeOptionRepository`, `PairingRepository` (`supersedeNegotiating`) (MATCH-002 §5.2 — thêm ở đợt MATCH-002, §1.1), `MatchGuard`, `TeamGuard`, `MatchExpiry`, `MatchCancellation`, `VenueSelectionService`, `ConcurrencyConflictRetrier`, `MatchMapper`, `PageRequestFactory`, `ApplicationEventPublisher` (`MatchCancelledEvent` khi huỷ `OPEN`), `Clock`.
- `createMatch`: guard (thứ tự `Processing_Flow.md` §1.1) → theo `r.type()`:
  - `HAS_VENUE`: `Match.builder()` (`status = OPEN`) → `save` → `markSelected({venueId}, now)` → `getMatch`-tương đương (`findDetailById`) dựng response.
  - `NEED_VENUE` (đợt MATCH-002 — đã code; đợt 1 từng trả `MATCH_016` trước truy vấn đầu tiên, §1.1): `requireSelectable(venueOptionIds)` trả `SelectableVenueView` → `Match.builder()` (`venueId = null`, `matchStartTime`/`matchEndTime` = khung giờ có `startsAt` nhỏ nhất) → `save` → `matchVenueOptionRepository.insertAll` → `matchTimeOptionRepository.insertAllReturning` → response dựng từ dữ liệu đã nạp (`TeamCaptainContextView`, `Sport`, `SelectableVenueView`, `TimeOptionView`), **không** `findDetailById`, **không** `markSelected` (chỉ lúc ghép — MATCH-002 §4.4). Không phát event.
- `searchMatches`: chọn `searchOpen` / `searchOpenNearby` theo có toạ độ (§5.2); hộp giới hạn toạ độ tính qua record dùng chung `GeoBoundingBox.around(lat, lng, radiusKm)` (`VenueServiceImpl` của VENUE-001 gọi lại cùng helper này) + Haversine như VENUE-001 §5.2; lọc `type` tuỳ chọn.
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
Deps: `MatchRepository`, `MatchRequestRepository`, `ConversationRepository` (§5.2), `MatchGuard`, `TeamGuard`, `MatchExpiry`, `MatchCancellation`, `ConcurrencyConflictRetrier`, `MatchMapper`, `PageRequestFactory`, `ApplicationEventPublisher`, `Clock`.
- `sendRequest`: `MatchRequest.builder()` → `saveAndFlush` (bắt `idx_match_request_active` → `MATCH_006`; NFS §4.1 ngoại lệ 1, comment tại chỗ) → **cuộc trò chuyện** (v2.4, BR-N04): `availability.existingConversationId()` khác `null` (đội gửi lại sau khi rút / hết hạn) → dùng lại; `null` → `conversationRepository.save(Conversation.builder()` `contextType = TEAM_MATCH`, `matchId`, bên A = `TEAM` đội tác giả, bên B = `TEAM` đội gửi`)` (INSERT lúc flush; id v7 có ngay → response) → `matchRepository.forceIncrementVersion(match)` (chỉ trên nhánh thành công — §5.2). Không phát event cho việc mở chat (không có loại thông báo riêng; `MatchRequestSentEvent` đã có). Bảng / entity / policy cuộc trò chuyện: §4.14, §5.1.
- `withdrawRequest`: mọi lần thử so `ctx.status` với `r.expectedStatus()` → khác → `MATCH_015` (cùng lý do `cancelMatch`). `ACCEPTED`: uỷ quyền `MatchCancellation.cancelConfirmed` với đội huỷ = đội gửi.
- Luồng: `Processing_Flow.md` §3.6–§3.10, §3.12.

### 4.3. MatchLifecycleJobService
```java
int expireDueMatches();      // UC-M09, AC-M07 — mỗi lô một TransactionTemplate
int archiveFinishedMatches();// AC-M08 — mỗi lô một TransactionTemplate
```
Deps: `MatchRepository`, `MatchRequestRepository`, `PairingRepository` (`expireNegotiatingByMatchIds` — đợt MATCH-002, §1.1), `ApplicationEventPublisher`, `TransactionTemplate`, `MatchLifecycleProperties`, `Clock`. Luồng: `Processing_Flow.md` §3.13.

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
boolean isExpiryDue(Match.Status s, LocalDateTime expiresAt, LocalDateTime now); // OPEN && expiresAt ≤ now → nhánh lazy
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
boolean expireIfDue(UUID matchId, UUID authorTeamId, Match.Type type, LocalDateTime now); // MANDATORY; true nếu chính lời gọi này chuyển EXPIRED
```
Q1 `MatchRepository.expireIfDue(id, now)` (`WHERE status = 'OPEN' AND expires_at <= :now`, `version + 1`) → 1 dòng: Q2 `HAS_VENUE` → `MatchRequestRepository.expirePendingByMatchIds([id], now)`; `NEED_VENUE` (đợt MATCH-002, §1.1) → `PairingRepository.expireNegotiatingByMatchIds([id], now)` → phát `MatchExpiredEvent`. 0 dòng → `false` (job/request khác đã làm). Job dùng biến thể lô (§4.3) cùng hai câu bulk.

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
public record ReliabilityChange(int delta, ReliabilityReason reason, UUID referenceId, boolean breaksCleanStreak) {} // v2.2: + breaksCleanStreak
public enum ReliabilityReason { MATCH_CANCELLED_AFTER_CONFIRMED, MATCH_REQUEST_WITHDRAWN_AFTER_ACCEPTED } // REPUTATION-001 thêm giá trị

public record TimeBandPenaltyPolicy(int atLeast24h, int from4hTo24h, int under4h) {   // application/support/reputation
    public static final TimeBandPenaltyPolicy MATCH_CANCELLATION = new TimeBandPenaltyPolicy(-2, -5, -10);
    public int penaltyFor(LocalDateTime matchStart, LocalDateTime at);  // Δ ≥ 24h / 4h ≤ Δ < 24h / Δ < 4h
}
```
- `delta == 0 && !breaksCleanStreak` → không truy vấn. Còn lại: Q1 `TeamRepository.adjustReliability` / `UserRepository.adjustReliability` (§5.2); 0 dòng → `IllegalStateException` (id phải tồn tại — lỗi lập trình) → phát `ReliabilityScoreChangedEvent`.
- **Không đọc lại** điểm mới (event chỉ mang `delta`); REPUTATION-001 cần điểm mới thì thêm biến thể đọc.
- **Mở rộng (v2.2):** REPUTATION-001 `Component_Class_Design.md` §4.6 thêm `applyMatchCompletion`, `applyReportPenalty`, `refundReportPenalty` và giá trị `ReliabilityReason`; MERCENARY-001 thêm hằng `TimeBandPenaltyPolicy` (vd rút mercenary `-1/-3/-5`). MATCH-001 chỉ ghi `reliability_score` và — khi huỷ kèo đã ghép — `clean_streak = 0` của đội huỷ (`breaksCleanStreak = true`, user chốt Q3 2026-11-12: huỷ `CONFIRMED` / rút `ACCEPTED` cắt chuỗi trận sạch). Không còn cột `total_matches_*` (REPUTATION-001 V9 xoá — Completion Rate tính khi đọc).
- MATCH-001 chỉ gọi `adjustTeamReliability`; `adjustUserReliability` có test riêng.
- **Lưu ý cho REPUTATION-001:** BR-M18 cho phép một người làm đội trưởng hai đội tự ghép kèo với nhau → có thể dùng để cày cộng điểm hoàn thành trận / Completion Rate. MATCH-001 không trừ điểm trong trường hợp này; REPUTATION-001 chặn đánh giá/report khi người gọi là đội trưởng cả hai đội (Q12) → trận đó không thể `COMPLETED` (REPUTATION-001 `Component_Class_Design.md` §9).

### 4.9. Domain event (`application/event/…`, ADR-0011)

| Event | Payload | Phát khi | Người nghe dự kiến |
|---|---|---|---|
| `MatchRequestSentEvent` | `requestId, matchId, requesterTeamId, authorTeamId, occurredAt` | 2.6 | NOTIFICATION-001 (`MATCH_REQUEST_RECEIVED`) — người nhận và loại thông báo của mọi event dưới đây chốt ở NOTIFICATION-001 `Component_Class_Design.md` §4.3 (MATCH-001 v2.5) |
| `MatchConfirmedEvent` | `matchId, acceptedRequestId` (null ở kèo B)`, pairingId` (null ở kèo A)`, authorTeamId, opponentTeamId, occurredAt` | 2.8; MATCH-002 2.7 hoàn tất | NOTIFICATION-001 (`REQUEST_ACCEPTED` khi có `acceptedRequestId`, `MATCH_CONFIRMED`; tự truy vấn yêu cầu / pairing `SUPERSEDED` của kèo để báo các đội bị thay thế) |
| `MatchRequestRejectedEvent` | `requestId, matchId, requesterTeamId, occurredAt` | 2.9 | NOTIFICATION-001 (`REQUEST_REJECTED`) |
| `MatchRequestWithdrawnEvent` | `requestId, matchId, requesterTeamId, authorTeamId, occurredAt` | 2.10 **chỉ khi rút `PENDING`** | NOTIFICATION-001 (`REQUEST_WITHDRAWN` — user chốt 2026-11-16) |
| `MatchCancelledEvent` | `matchId, cancelledByTeamId, otherTeamId` (null khi huỷ `OPEN`)`, previousStatus, cause` (`CANCEL`\|`REQUEST_WITHDRAWN`)`, withdrawnRequestId` (khác null khi đội đã ghép kèo A huỷ / rút)`, occurredAt` | 2.5, 2.10 (`ACCEPTED`) | NOTIFICATION-001 (`MATCH_CANCELLED` cho đội còn lại; kèo B `OPEN`: tự truy vấn pairing `SUPERSEDED` để báo các đội đang thương lượng) |
| `MatchExpiredEvent` | `matchId, authorTeamId, occurredAt` | job, lazy check | NOTIFICATION-001 (`MATCH_EXPIRED`; tự truy vấn yêu cầu / pairing `EXPIRED` của kèo → `REQUEST_EXPIRED` / `PAIRING_EXPIRED` — AC-M07) |
| `MatchArchivedEvent` | `matchId, authorTeamId, opponentTeamId, matchEndTime, occurredAt` | job | NOTIFICATION-001 (`MATCH_COMPLETED` "Trận đấu hôm nay thế nào?…" cho 2 đội trưởng — AC-M08; `MERCENARY_RATING_REMINDER` cho mercenary `APPROVED` của kèo — listener tự truy vấn; `ARCHIVED → COMPLETED` không báo — user chốt 2026-11-16) |
| `ReliabilityScoreChangedEvent` | `subjectType` (`TEAM`\|`USER`)`, subjectId, delta, reason, referenceId, occurredAt` | `ReputationService` | Thông báo / dữ liệu dẫn xuất tính lại được (vd cache badge); không dùng để ghi điểm, bộ đếm |

Payload chỉ id + thời điểm + enum, không PII; người nhận tự tra đội trưởng hiện tại sau commit. MATCH-001 không có listener.

**Mỗi thao tác phát đúng một event nghiệp vụ** → NOTIFICATION-001 báo một lần: huỷ / rút sau ghép chỉ phát `MatchCancelledEvent` (nhận biết rút qua `cause = REQUEST_WITHDRAWN` + `withdrawnRequestId`), không kèm `MatchRequestWithdrawnEvent`. `ReliabilityScoreChangedEvent` đi kèm là sự kiện điểm, không phải thông báo huỷ.

**Phạm vi event (ADR-0011):** event không bền → chỉ dùng cho thông báo và dữ liệu dẫn xuất tính lại được. Dữ liệu nghiệp vụ (cộng/trừ điểm, bộ đếm hoàn thành, chuỗi trận sạch) MUST ghi trong cùng transaction qua `ReputationService` (ADR-0013); REPUTATION-001 không nghe `MatchArchivedEvent` để ghi bộ đếm.

### 4.10. Scheduler (ADR-0012, ADR-0018)
`MatchLifecycleScheduler` (`api/scheduler`, không logic): `@Scheduled(cron = "${arenaflow.match.lifecycle.cron}")` (không khai `zone` — chạy theo timezone JVM `Asia/Ho_Chi_Minh`, ADR-0018) gọi `expireDueMatches()` rồi `archiveFinishedMatches()`, rồi (khi tích hợp các mã sau, §1.1) `MercenaryLifecycleJobService.closeDueRecruitments()`, `rejectOrphanPending()` và `notifyCancelledGames()` (v2.3 — thiết kế ở MERCENARY-001 `Component_Class_Design.md` §4.9), rồi `NotificationJobService.sendDueStartReminders()` và `sendDueRatingReminders()` (v2.5 — nhắc trước trận 2 giờ; nhắc mercenary đánh giá trận ngoài — NOTIFICATION-001 `Component_Class_Design.md` §4.7); lỗi một phần log `ERROR`, phần kia vẫn chạy. Cron `-` = tắt (test). Dùng `SchedulingConfig` (khai báo bean `taskScheduler` từ `ThreadPoolTaskSchedulerBuilder` — §8).

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

Chat (v2.4): `getConversation`, `history`, `requireSubscribable`, `listMyConversations` → `@Transactional(readOnly = true)`; `markRead` → `@Transactional` (một UPDATE, không CAS); `send` → không annotate, `concurrencyConflictRetrier.execute(MATCH_014, …)` trả `MatchWriteOutcome` (§4.7) cho **mọi** loại ngữ cảnh (một đường code — nhánh lazy chỉ ở `TEAM_MATCH`); `ChatRealtimeGateway.publishAfterCommit` → `MANDATORY`.

### 4.13. MatchCancellation (`application/support`, `@Component`) — một đường huỷ kèo đã ghép
```java
CancellationResult cancelConfirmed(Match match, UUID cancellingTeamId, String reason,
                                   MatchCancelledEvent.Cause cause, LocalDateTime now); // MANDATORY
public record CancellationResult(int reliabilityPenalty) {}
```
Dùng bởi 2.5 (`CONFIRMED`, `cause = CANCEL`) và 2.10 (`ACCEPTED`, `cause = REQUEST_WITHDRAWN`): setter `status = CANCELLED`, `cancelledByTeamId`, `cancelledAt`, `cancelReason` → nếu đội huỷ là đội đã ghép **và** kèo `HAS_VENUE`: `MatchRequestRepository.withdrawAccepted` (`UPDATE … RETURNING id`, phải trả đúng 1 id → gán `withdrawnRequestId`, khác 1 dòng → `IllegalStateException`); kèo `NEED_VENUE` không có yêu cầu (`withdrawnRequestId = null`), pairing đã chốt giữ `CONFIRMED` → `penalty = MATCH_CANCELLATION.penaltyFor(matchStartTime, now)` → `reputationService.adjustTeamReliability(cancellingTeamId, new ReliabilityChange(penalty, reason, matchId, breaksCleanStreak = true))` (v2.2 — cắt chuỗi trận sạch, cùng câu `UPDATE`; `ReliabilityReason` chọn theo `cause`) → `MatchCancelledEvent` (một event duy nhất cho thao tác, §4.9). Gọi `MatchGuard`/kiểm `MATCH_011`, `MATCH_012` **trước** (caller).

### 4.14. ConversationService — lõi chat dùng chung (v2.4; chuyển từ MATCH-002 v2.0, trước đó MERCENARY-001 v1.0 §4.7)

MATCH-001 giữ lõi chat để chat luồng A chạy đủ (REST + realtime) ngay khi MATCH-001 xong, không phụ thuộc MATCH-002 (review 2026-11-15). MATCH-002 thêm nhánh luồng B, MERCENARY-001 thêm ngữ cảnh đơn ứng tuyển.

**Lựa chọn mô hình** (ADR-0014 Sửa đổi 2026-11-13, 2026-11-15): một bảng **tổng quát** `conversation` (loại ngữ cảnh + trận + hai bên, mỗi bên `TEAM` hoặc `USER`) + `conversation_message` cho **mọi** chat. Phương án đã loại (user chọn 2026-11-15): giữ `chat_message` theo pairing + thêm chỗ lưu riêng cho luồng A + bảng của mercenary (ba nơi lưu tin, ba bộ API / topic / job xoá); dùng chung `chat_message` (nới `pairing_id`/`sender_team_id`, bên ứng viên không có đội).

```java
ConversationResponse getConversation(UUID conversationId, UUID callerId);                                // readOnly
ConversationMessageResponse send(UUID conversationId, UUID callerId, SendConversationMessageRequest r);  // retrier MATCH_014 (nhánh lazy)
ChatHistoryResponse history(UUID conversationId, UUID callerId, ChatHistoryQuery q);                     // readOnly
void markRead(UUID conversationId, UUID callerId);                                                      // @Transactional
void requireSubscribable(UUID conversationId, UUID userId);                                             // readOnly — STOMP SUBSCRIBE
PageResponse<ConversationSummaryResponse> listMyConversations(UUID callerId, int page, int pageSize);   // readOnly — 2.20
```
```java
public interface ConversationAccessPolicy {                       // application/support/chat; một bean mỗi loại ngữ cảnh
    Conversation.ContextType contextType();
    ConversationAccess resolve(ConversationContextView ctx, UUID callerId, LocalDateTime now);
}
public record ConversationAccess(Conversation.Party viewerParty /* null = không tham gia */, boolean canSend,
                                 boolean expiryDue /* TEAM_MATCH: gửi tin phải chuyển kèo EXPIRED (BR-M17) */,
                                 boolean viewerReadOnly /* TEAM_MATCH: người đã giải tán đội của mình */) {}
```
Deps: `ConversationRepository`, `ConversationMessageRepository`, `List<ConversationAccessPolicy>` (map theo `contextType`, thiếu bean cho một loại → lỗi khởi động), `MatchExpiry`, `ConcurrencyConflictRetrier`, `ConversationMapper`, `ChatRealtimeGateway`, `Clock`.

- `ConversationRepository.findContext(conversationId, callerId, now)` — **một câu** cho mọi loại ngữ cảnh: cột chung + khối cột của từng loại (`LEFT JOIN … ON c.context_type = '<loại>'`) + cờ / tên / ảnh người gọi (§5.2). Service chọn policy theo `ctx.contextType()`.
- **`TeamMatchConversationPolicy`** (`TEAM_MATCH`, BR-N04, BR-N05 — bảng `API_Spec.md` §1.6):
  - `viewerParty` (thứ tự kiểm): caller là đội trưởng ACTIVE của đội bên A (đội tạo kèo) → `A`; không thì của đội bên B → `B`; không thì đội bên A `DISBANDED` và caller = `team.disbanded_by` của đội đó → `A` **chỉ xem**; không thì tương tự bên B → `B` **chỉ xem**; còn lại → `null` (`CHAT_003`). Caller là đội trưởng cả hai đội → `A` (user chốt 2026-11-15). Người đã bấm giải tán (đội trưởng cuối — USER-002 BR-T10, cột `team.disbanded_by` của V4) xem được cuộc trò chuyện của đội mình nhưng không gửi (BR-N05 (3), user chốt 2026-11-15); thành viên khác của đội đã giải tán không khớp bên nào.
  - `matched` = `ctx.matchOpponentTeamId = ctx.partyBId` (đội đã ghép của kèo — 2.8 / MATCH-002 2.7 ghi, **không** xoá khi kèo bị huỷ).
  - `waiting` = kèo `HAS_VENUE` và yêu cầu **gần nhất** của đội bên B ở `PENDING` (trạng thái DB). Kèo `NEED_VENUE`: nhánh `pairing.status = NEGOTIATING` (cột `pairing_status` + join `pairing`) do MATCH-002 thêm vào policy và `ConversationSql` (MATCH-002 `Component_Class_Design.md` §4.2) — trước khi có MATCH-002 không tồn tại cuộc trò chuyện của kèo `NEED_VENUE`.
  - `expiryDue` = ¬`viewerReadOnly` ∧ `waiting` ∧ `match.status = OPEN` ∧ `expiresAt ≤ now` (người chỉ xem không kích hoạt chuyển `EXPIRED`).
  - `canSend` = `viewerParty ≠ null` ∧ ¬`viewerReadOnly` ∧ (`matched` ∨ (`waiting` ∧ ¬`expiryDue`)). Không xét trạng thái đội (`INACTIVE`, đội kia không hoạt động) / bộ môn (BR-N05 (3)); đội **của người gọi** đã giải tán → chỉ xem. `ConversationAccess` thêm `viewerReadOnly` (`boolean`) — áp chung cho luồng A và B (cùng policy).
- **`MercenaryApplicationConversationPolicy`**: MERCENARY-001 `Component_Class_Design.md` §4.7 (`expiryDue` luôn `false`).
- `send` (retrier `MATCH_014`, trả `MatchWriteOutcome` — §4.7 tài liệu này): lần thử: ctx → `CHAT_002` → `callerDeleted` → `USER_001` → policy → `viewerParty = null` → `CHAT_003` → `expiryDue` → `matchExpiry.expireIfDue(matchId, authorTeamId, matchType, now)` → `Expired` (commit chuyển `EXPIRED`) → `MATCH_004` sau commit → `!canSend` → `CHAT_004` → `ConversationMessage.builder()` (`senderParty = viewerParty`, `senderUserId = callerId`, `message = trim(content)`) `save` → `realtimeGateway.publishAfterCommit(conversationId, MessageCreated)` → (v2.5, NOTIFICATION-001 §4.5) `userRealtimeGateway.publishAfterCommit(viewers − {callerId}, ConversationActivity(conversationId, messageId, createdAt))` — `viewers`: bên `TEAM` → `partyXCaptainUserId` + `partyXDisbandedBy` khi đội `DISBANDED`; bên `USER` → `partyBId`; bỏ `null`, khử trùng; không truy vấn thêm, không tạo dòng `notification`. Response + frame: `conversationMapper.toMessageResponse(message, ctx)` — người gửi lấy từ cột caller của `ctx`, bên gửi = `partyAId`/`partyBId` theo `viewerParty`; không đọc lại. Không phát domain event (tin chat không tạo thông báo — user chốt 2026-11-16; chỉ tín hiệu `CONVERSATION_ACTIVITY` ở trên). Không CAS → retrier chỉ thử lại deadlock (`40P01`); hết lượt → `MATCH_014`.
- `history`: `findContext` → `CHAT_002` → `callerDeleted` → `USER_001` → policy (chỉ cần `viewerParty`, mọi trạng thái) → `CHAT_003` → có con trỏ: `findCursor(conversationId, messageId)` → không có → `CHAT_001` → `findBefore` / `findAfter` / `findLatest` với `limit + 1` (mỗi dòng đã JOIN người gửi) → `hasMore = size > limit`, cắt còn `limit`.
- `markRead`: ctx → `CHAT_002` → `USER_001` → policy → `markOtherPartyRead(conversationId, viewerParty)` (mọi trạng thái, không lazy).
- `getConversation`: ctx → `CHAT_002` → `USER_001` → policy → `conversationMapper.toResponse(ctx, access, now)` (`requestStatus` / `pairingStatus` hiển thị = trạng thái hiệu lực trong SQL).
- `requireSubscribable`: ctx → `CHAT_002` → `USER_001` → policy → `CHAT_003` (ném `BusinessException`, interceptor đổi thành frame `ERROR`).
- `listMyConversations` (2.20, user chốt 2026-11-15): Q1 `conversationRepository.countMine(callerId)` → `MineCountView(total, callerDeleted)` (cờ người gọi đã xoá đọc trong cùng câu đếm) → `callerDeleted` → `USER_001`; `total = 0` → trang rỗng (bỏ Q2) → Q2 `findMine(callerId, now, limit, offset)` (§5.2). Mỗi dòng `ConversationListRowView` **mở rộng** `ConversationContextView` (cùng cột ngữ cảnh + cờ người gọi) → service gọi **đúng policy** của loại ngữ cảnh (`resolve(row, callerId, now)`, trong bộ nhớ) để có `viewerParty`, `canSend` — quy tắc quyền một nơi, không viết lại trong SQL. `hasUnread` = `unreadFromB` nếu bên của người gọi là A, ngược lại `unreadFromA` (hai cờ `EXISTS` tính sẵn trong câu trang). `viewerParty` / `otherParty` / tin cuối dựng từ cột đã nạp; che tên / ảnh người đã xoá bằng `MapperSupport` (USER-001 §7). Dòng mà policy trả `viewerParty = null` (không thể xảy ra theo cách chọn dòng của `findMine` — bất biến) → bỏ dòng, log `WARN` (id). Không lọc theo trạng thái; không ghi gì.
- Hằng một nơi (`ConversationRules`): `MAX_MESSAGE_LENGTH = 1000`, `DEFAULT_HISTORY_LIMIT = 50`, `MAX_HISTORY_LIMIT = 100` .

### 4.15. Kênh realtime (ADR-0014) — chuyển từ MATCH-002 v2.0 §4.6 (v2.4)

> Một topic duy nhất `/topic/conversations/{conversationId}` cho mọi frame (ADR-0014 Sửa đổi 2026-11-15). Hạ tầng STOMP (cấu hình, xác thực, phân quyền đăng ký, gateway) đặt ở MATCH-001 để chat luồng A có realtime ngay (AC-N01 độ trễ < 1s); MATCH-002 thêm frame `PairingUpdated`, MERCENARY-001 thêm `ApplicationUpdated`.

**Port (application):**
```java
public interface ChatRealtimeGateway {
    void publishAfterCommit(UUID conversationId, RealtimeFrame frame);   // MANDATORY: đăng ký TransactionSynchronization.afterCommit
}
public sealed interface RealtimeFrame {
    record MessageCreated(ConversationMessageResponse message) implements RealtimeFrame {}
    // PairingUpdated — MATCH-002 §4.6; ApplicationUpdated — MERCENARY-001 §4.8
}
```
Đăng ký trong lần thử đang chạy → lần thử rollback không đẩy; lần thử commit đẩy đúng một lần. Dữ liệu frame dựng từ bộ nhớ (không đọc lại DB).

**Cài đặt (api/realtime):**
- `WebSocketConfig` (`@EnableWebSocketMessageBroker`): endpoint `/ws` + `.withSockJS()` `[cần xác nhận #12]`, `setAllowedOriginPatterns(props.allowedOriginPatterns())`; (v2.5: `/queue` + `setUserDestinationPrefix("/user")` cho kênh cá nhân — NOTIFICATION-001 §4.5, ADR-0014 Sửa đổi 2026-11-16) `enableSimpleBroker("/topic", "/queue").setHeartbeatValue(10000, 10000).setTaskScheduler(wsHeartbeatScheduler).setSelectorHeaderName(null)`; **không** `setApplicationDestinationPrefixes` (client không `SEND`); `configureClientInboundChannel` gắn `StompAuthChannelInterceptor`.
  - `setSelectorHeaderName(null)`: tắt lọc theo header `selector` của simple broker (giá trị header là biểu thức SpEL do client gửi — tiền lệ CVE-2018-1270). Không có use case lọc frame phía server; tắt để bớt bề mặt tấn công. Có method này trên Spring Framework 7 hay không: kiểm cùng `[cần xác nhận #12]`.
- `StompAuthChannelInterceptor` (`ChannelInterceptor.preSend`; constructor nhận `@Lazy ConversationService` để cắt vòng phụ thuộc lúc khởi động giữa `ConversationService` → `ChatRealtimeGateway` → `SimpMessagingTemplate` → `WebSocketConfig` → interceptor):
  - `CONNECT` / `STOMP`: đọc native header `Authorization` → `Bearer …` → `jwtDecoder.decode` (cùng bean + `AccessTokenTypeValidator` của USER-001) → `accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, List.of()))`, lưu `exp` vào session attributes. Lỗi → `StompRealtimeException(ErrorCode.AUTH_001)` → frame `ERROR`.
  - `SUBSCRIBE` — bước chung: destination không khớp một trong hai mẫu (`^/topic/conversations/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$` chỉ chữ thường để khớp destination `UUID.toString()` mà gateway publish, hoặc đúng nguyên văn `/user/queue/notifications` — v2.5, mở khi tích hợp NOTIFICATION-001) → `StompRealtimeException(ErrorCode.SYS_004)`. Không wildcard: `/topic/**`, `/topic/conversations/*`, `/topic/pairings/{id}`, UUID viết hoa, mọi `/queue/**` thô và `/user/…` khác mẫu → `SYS_004`.
    - `/topic/conversations/{UUID}`: `exp` ≤ now → `AUTH_001`; `conversationService.requireSubscribable(conversationId, userId)` → `CHAT_002`/`USER_001`/`CHAT_003`.
    - `/user/queue/notifications` (v2.5): `exp` ≤ now → `AUTH_001` → chấp nhận, không truy vấn.
  - `SEND` → `StompRealtimeException(ErrorCode.SYS_002)`.
  - `StompErrorHandler extends StompSubProtocolErrorHandler`: giải nguyên nhân `StompRealtimeException` (lỗi không rõ khác → `ErrorCode.SYS_005`), đặt header `message` = mã lỗi, body `{"code":"...","message":"..."}`; **NEVER** đưa token / stacktrace vào frame hay log.
- `StompChatRealtimeGateway implements ChatRealtimeGateway`: `afterCommit` → `SimpMessagingTemplate.convertAndSend("/topic/conversations/" + id, json)` với `{type: MESSAGE_CREATED|PAIRING_UPDATED|APPLICATION_UPDATED, …}` (`API_Spec.md` §1.7). Lỗi gửi → log `WARN` (không ném — đã commit).
- `WebSocketProperties` (`arenaflow.websocket.*`, §8).

## 5. Repository & Data Model

### 5.1. Migration `V7__match_flow_a_lifecycle.sql`

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

-- chat (v2.4): bỏ chat_message theo pairing của V1 (chỉ xoá khi bảng rỗng); mọi chat dùng cuộc trò chuyện tổng quát (ADR-0014 Sửa đổi 2026-11-15)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM chat_message LIMIT 1) THEN
        RAISE EXCEPTION 'chat_message is not empty — aborting V7 migration';
    END IF;
END $$;
DROP TABLE chat_message;

-- conversation: loại ngữ cảnh + trận (tính hạn lưu) + hai bên (TEAM | USER). V7 chỉ có TEAM_MATCH; V10 (MERCENARY-001) thêm MERCENARY_APPLICATION
CREATE TABLE conversation (
    id UUID PRIMARY KEY,
    context_type VARCHAR(30) NOT NULL,
    match_id UUID REFERENCES match(id) ON DELETE RESTRICT,
    party_a_type VARCHAR(10) NOT NULL CHECK (party_a_type IN ('TEAM', 'USER')),
    party_a_id UUID NOT NULL,
    party_b_type VARCHAR(10) NOT NULL CHECK (party_b_type IN ('TEAM', 'USER')),
    party_b_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    CONSTRAINT conversation_context_type_check CHECK (context_type IN ('TEAM_MATCH')),
    CONSTRAINT conversation_team_match_check CHECK (context_type <> 'TEAM_MATCH'
        OR (match_id IS NOT NULL AND party_a_type = 'TEAM' AND party_b_type = 'TEAM')),
    CONSTRAINT conversation_parties_check CHECK (NOT (party_a_type = party_b_type AND party_a_id = party_b_id))
);
CREATE UNIQUE INDEX idx_conversation_team_match ON conversation(match_id, party_b_id) WHERE context_type = 'TEAM_MATCH';
CREATE INDEX idx_conversation_party_a ON conversation(party_a_id);   -- 2.20: cuộc trò chuyện của các đội người gọi làm đội trưởng
CREATE INDEX idx_conversation_party_b ON conversation(party_b_id);   -- 2.20: đội tham gia / ứng viên
-- team: tìm đội người gọi đã giải tán (2.20 — người đã giải tán chỉ xem)
CREATE INDEX idx_team_disbanded_by ON team(disbanded_by) WHERE disbanded_by IS NOT NULL;

-- conversation_message: bên gửi A/B, người thực sự gửi (không mất khi xoá user), trạng thái đọc; index phân trang theo (created_at, id)
CREATE TABLE conversation_message (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    sender_party VARCHAR(1) NOT NULL CHECK (sender_party IN ('A', 'B')),
    sender_user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE RESTRICT,
    message TEXT NOT NULL CHECK (char_length(message) BETWEEN 1 AND 1000),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')
);
CREATE INDEX idx_conversation_message_time ON conversation_message(conversation_id, created_at, id);
CREATE INDEX idx_conversation_message_unread ON conversation_message(conversation_id, sender_party) WHERE NOT is_read;
```
- Chỉ delta cần: `@Version` (Overview "Implementation notes"), trình độ mong muốn (Data_Model_Conceptual §2.3 — V1 thiếu), lý do huỷ / từ chối / rút (§2.3), `format` `VARCHAR(10)` không chứa `ELEVEN_A_SIDE` (13 ký tự), ràng buộc enum/số tiền, unique "1 ACCEPTED" (V1 chỉ có unique theo đội), `display_order` (thứ tự khoản tự nhập; unique `(match_id, display_order)` thay index `(match_id)` của V1 vì có cùng tiền tố). Tên index theo quy ước `idx_*` của V1/V2/V4.
- `skill_levels` cùng kiểu/converter với `team.skill_levels` (USER-001 §6, `SkillLevelsConverter`).
- Không đổi `cost_deposit_holder`, `match_venue_option`/`match_time_option`/`pairing` ở V7. V8 (MATCH-002 §5.1) sửa tiếp: `cost_custom_item.match_id` nullable + `pairing_id` (XOR), `match` bỏ `confirmed_venue_id`/`confirmed_match_time`, nới CHECK `match_check2`, thêm `cost_deposit_amount`.
- Dữ liệu cũ: đã kiểm Neon dev `misty-dew-90700890` ngày 2026-11-09 — `match`, `match_request`, `cost_custom_item` đều 0 dòng; Flyway ở V4; tên constraint/index V7 không trùng với constraint đang có. Môi trường khác có dữ liệu: dòng có `format`/`cost_*` ngoài enum, hoặc `HAS_VENUE` thiếu `format`, hoặc nhiều `ACCEPTED` cùng kèo làm V7 lỗi → dừng, báo, không tự sửa dữ liệu.
- Index: `idx_match_open_start` cho 2.2 khi không lọc môn (partial khớp literal `'OPEN'`); `idx_match_opponent_status` cho 2.11 vai trò đối thủ. Job dùng `idx_match_expiry (status, expires_at)`, `idx_match_archived (status, match_end_time)` (V1).

- **Chat (v2.4):**
  - `conversation_message.sender_user_id` `NOT NULL` + `ON DELETE RESTRICT`: khớp chính sách chỉ xoá mềm user (ADR-0002); tin giữ người gửi để hiện "Người dùng đã xóa". Không index `sender_user_id`: RESTRICT chỉ kiểm khi `DELETE` user, không xảy ra. Cùng chính sách cho `venue_report.reporter_id` và `venue.created_by_user_id` ở V5 (VENUE-001 §5.1).
  - **`conversation`** (v2.4 — chuyển từ MERCENARY-001 V10, qua MATCH-002 V8, về V7 để MATCH-001 dùng độc lập): `party_*_id` đa hình (`team` / `"user"`) → không FK (như `rating.rater_id`). `TEAM_MATCH`: bên A = đội tạo kèo, bên B = đội tham gia (đội gửi yêu cầu luồng A / đội nhận kèo luồng B); không cột khoá yêu cầu / pairing — một cuộc trò chuyện luồng A sống qua nhiều yêu cầu của cùng đội (gửi lại — BR-N04), trạng thái "nhắn được / chỉ xem" suy ra khi đọc (§4.14), không lưu. `idx_conversation_team_match` (unique, partial) thực thi BC-N04 (mỗi (kèo, đội tham gia) tối đa một) và phục vụ tra cuộc trò chuyện theo (kèo, đội) ở 2.6/2.7/2.20, MATCH-002 2.1–2.4 + job xoá theo tiền tố `match_id`. `match_id` `ON DELETE RESTRICT` (ADR-0002); nullable vì V10 thêm ngữ cảnh mercenary của trận ngoài hệ thống. V10 (MERCENARY-001 §5.1) mở rộng `conversation_context_type_check`, thêm `application_id`, `external_match_id` và CHECK của ngữ cảnh mercenary.
  - 2.20 (user chốt 2026-11-15): `idx_conversation_party_a`, `idx_conversation_party_b` tìm cuộc trò chuyện theo bên (đội / người dùng — id UUID không trùng giữa hai loại, loại bên lọc trong câu); `idx_team_disbanded_by` (partial) tìm đội người gọi đã giải tán — cột `team.disbanded_by` của V4 chưa có index; đặt ở V7 vì chỉ 2.20 dùng. Danh sách đội người gọi làm đội trưởng dùng `idx_team_member_user (user_id, status)` của V1.
  - `conversation_message`: bên gửi là `A`/`B` (không cột đội gửi — tên đội lấy từ bên tương ứng của `conversation`); `ON DELETE CASCADE` từ `conversation` để job xoá hạn lưu (NOTIFICATION-001) chỉ xoá dòng `conversation`. `idx_conversation_message_time` cho cursor (row comparison), `idx_conversation_message_unread` cho đánh dấu đã đọc.
  - Bỏ bảng `chat_message` (V1, gắn `pairing`): V7 có khối `DO $$ … IF EXISTS (SELECT 1 FROM chat_message LIMIT 1) THEN RAISE EXCEPTION … $$` ngay trước `DROP TABLE chat_message` để tự động dừng migration nếu bảng khác 0 dòng trên bất kỳ môi trường nào (đóng `[cần xác nhận #16]`; trên Neon dev đã kiểm chứng 0 dòng và chạy V7 thành công). (`chat_message.sender_user_id` của V1 khai `NOT NULL` + `ON DELETE SET NULL` — mâu thuẫn — bị bỏ cùng bảng.)

### 5.2. Repository

Native + interface projection như USER-001 §5; `Page` native có `countQuery`; bulk `@Modifying` trên `match` MUST `version = version + 1`, `updated_at = :now`. Trạng thái hiệu lực trong SQL: `CASE WHEN m.status = 'OPEN' AND m.expires_at <= :now THEN 'EXPIRED' ELSE m.status END` (yêu cầu: `PENDING` + kèo quá hạn → `EXPIRED`); `:now` truyền từ `Clock`.

```java
public interface MatchRepository extends JpaRepository<Match, UUID> {
    Optional<MatchDetailView> findDetailById(UUID id, LocalDateTime now);   // JOIN sport, team tác giả, LEFT JOIN team đối thủ,
                                                                            // venue, LEFT JOIN venue_image ảnh chính
    Page<MatchSummaryView> searchOpen(String type /* null = cả hai */, UUID sportId, String format, String skillLevel,
            LocalDateTime startsAtFrom, LocalDateTime startsAtTo, LocalDateTime now, Pageable p);
    Page<MatchSummaryView> searchOpenNearby(String type, UUID sportId, String format, String skillLevel, LocalDateTime startsAtFrom,
            LocalDateTime startsAtTo, LocalDateTime now, double lat, double lng, double radiusKm,
            double minLat, double maxLat, double minLng, double maxLng, Pageable p);
    Page<MatchSummaryView> findTeamMatches(UUID teamId, String role /* null = ALL */, String status /* null = ALL */,
            LocalDateTime now, Pageable p);
    Optional<MatchAuthorAccessView> findAuthorAccess(UUID matchId, UUID userId); // authorTeamId, trạng thái đội tác giả, caller là captain ACTIVE
    MatchAvailabilityView findAvailability(UUID matchId, UUID teamId);        // trạng thái đội tác giả + bộ môn đang bật + cuộc trò chuyện TEAM_MATCH của (kèo, teamId) nếu có (2.6, MATCH-002 2.1)
    @Modifying int expireIfDue(UUID id, LocalDateTime now);
    List<DueMatchView> claimDueForExpiry(LocalDateTime now, int limit);         // FOR NO KEY UPDATE SKIP LOCKED
    List<DueMatchView> claimDueForArchive(LocalDateTime now, int limit);        // FOR NO KEY UPDATE SKIP LOCKED
    @Modifying int markExpired(Collection<UUID> ids, LocalDateTime now);
    @Modifying int markArchived(Collection<UUID> ids, LocalDateTime now);
}
public interface MatchRepositoryCustom {                                   // fragment; MatchRepository extends nó
    void forceIncrementVersion(Match match);   // MatchRepositoryImpl: entityManager.lock(match, OPTIMISTIC_FORCE_INCREMENT) — version tăng lúc commit
}
public interface MatchRequestRepository extends JpaRepository<MatchRequest, UUID> {
    Optional<MatchRequestContextView> findContextById(UUID requestId);   // request + match + đội gửi (status, tên, điểm) + sport.is_active
    boolean existsByMatchIdAndStatus(UUID matchId, MatchRequest.Status status);
    Page<MatchRequestView> findByMatch(UUID matchId, boolean pendingOnly, LocalDateTime now, Pageable p);
    Page<MatchRequestView> findByRequesterTeam(UUID teamId, String status /* null = ALL */, LocalDateTime now, Pageable p);
    @Modifying int acceptIfPending(UUID id, LocalDateTime now);
    @Modifying int rejectIfPending(UUID id, String reason, LocalDateTime now);
    @Modifying int withdrawIfPending(UUID id, String reason, LocalDateTime now);
    List<UUID> withdrawAccepted(UUID matchId, UUID requesterTeamId, String reason, LocalDateTime now); // UPDATE … RETURNING id (không @Modifying)
    @Modifying int supersedePending(UUID matchId, LocalDateTime now);
    @Modifying int expirePendingByMatchIds(Collection<UUID> matchIds, LocalDateTime now);
}
public interface CostCustomItemRepository extends JpaRepository<CostCustomItem, UUID> {
    List<CostCustomItem> findByMatchIdOrderByDisplayOrderAsc(UUID matchId);
    @Modifying int deleteByMatchIdBulk(UUID matchId);                     // DELETE … WHERE match_id = :matchId
    @Modifying int insertAll(UUID matchId, String[] names, Long[] amounts, LocalDateTime now); // 1 câu unnest, id do PostgreSQL uuidv7() sinh
}
public interface SportRepository extends JpaRepository<Sport, UUID> {
    Page<Sport> findByIsActiveOrderByNameAscIdAsc(boolean isActive, Pageable p);
    Page<Sport> findAllByOrderByNameAscIdAsc(Pageable p);
}
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Optional<ConversationContextView> findContext(UUID id, UUID callerId, LocalDateTime now); // một câu, mọi loại ngữ cảnh
    List<ConversationListRowView> findMine(UUID callerId, LocalDateTime now, int limit, long offset); // 2.20 — trang
    MineCountView countMine(UUID callerId);                                                         // 2.20 — tổng + cờ người gọi đã xoá
}
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {
    Optional<ChatCursorView> findCursor(UUID conversationId, UUID messageId);           // created_at, id
    List<ConversationMessageView> findLatest(UUID conversationId, int limit);
    List<ConversationMessageView> findBefore(UUID conversationId, LocalDateTime createdAt, UUID id, int limit);
    List<ConversationMessageView> findAfter(UUID conversationId, LocalDateTime createdAt, UUID id, int limit);
    @Modifying int markOtherPartyRead(UUID conversationId, String readerParty);
}
// TeamRepository (sửa)
Optional<TeamCaptainContextView> findCaptainContext(UUID teamId, UUID userId); // status + EXISTS captain ACTIVE = userId + name, avatar_url, reliability_score + tên/cờ đã xoá của userId
@Modifying int adjustReliability(UUID id, int delta, boolean breaksCleanStreak);
// UserRepository (sửa)
@Modifying int adjustReliability(UUID id, int delta, boolean breaksCleanStreak);
```

Ghi chú SQL:
- `*IfPending`/`withdrawAccepted`: `WHERE … AND status = '<trạng thái nguồn>'` — 0 dòng = đã bị thao tác khác đổi (READ COMMITTED đánh giá lại sau khi chờ khoá dòng). Gán `responded_at` (accept/reject/supersede), `withdrawn_at` + `reason` (withdraw), `reason` (reject).
- `supersedePending`: `SET status = 'SUPERSEDED', responded_at = :now WHERE match_id = :matchId AND status = 'PENDING'` — chạy **sau** `acceptIfPending` (yêu cầu thắng đã rời `PENDING`).
- `expirePendingByMatchIds`: `SET status = 'EXPIRED' WHERE match_id IN (:ids) AND status = 'PENDING'`.
- `claimDueFor*`: `SELECT id, author_team_id, confirmed_opponent_team_id, match_end_time FROM match WHERE status = :s AND <expires_at|match_end_time> <= :now ORDER BY <cột thời gian>, id LIMIT :limit FOR NO KEY UPDATE SKIP LOCKED` (native, đọc → không `@Modifying`). `markExpired/markArchived`: `WHERE id IN (:ids)` lặp điều kiện trạng thái.
- `adjustReliability(id, delta, breaksCleanStreak)` (v2.2 thêm tham số): `UPDATE team SET reliability_score = LEAST(100, GREATEST(0, reliability_score + :delta)), clean_streak = CASE WHEN :breaksCleanStreak THEN 0 ELSE clean_streak END WHERE id = :id` — **không** tăng `version` (cột `updatable = false`, ADR-0013); `user` tương tự.
- `findCaptainContext`: `SELECT t.status, t.name, t.avatar_url, t.reliability_score, EXISTS (SELECT 1 FROM team_member m WHERE m.team_id = t.id AND m.user_id = :userId AND m.role = 'CAPTAIN' AND m.status = 'ACTIVE') AS captain, u.display_name AS user_display_name, (u.deleted_at IS NOT NULL) AS user_deleted FROM team t LEFT JOIN "user" u ON u.id = :userId WHERE t.id = :teamId` (tên/ảnh/điểm cho response dựng từ dữ liệu đã nạp: tạo kèo B, MATCH-002 2.1; `user_*` = tên đội trưởng hiện tại khi `captain = true` — `captainName` của MATCH-002 2.1).
- `findAvailability`: thêm `LEFT JOIN team_member cm ON cm.team_id = m.author_team_id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE' LEFT JOIN "user" cu ON cu.id = cm.user_id` → `authorCaptainName`, `authorCaptainDeleted` (MATCH-002 2.1); v2.4: `LEFT JOIN conversation cv ON cv.context_type = 'TEAM_MATCH' AND cv.match_id = m.id AND cv.party_b_id = :teamId` → `existingConversationId` (`idx_conversation_team_match`); cùng câu.
- `findByMatch`, `findByRequesterTeam` (2.7, 2.12): v2.4 thêm `LEFT JOIN conversation cv ON cv.context_type = 'TEAM_MATCH' AND cv.match_id = r.match_id AND cv.party_b_id = r.requester_team_id` → `conversationId` (trong câu trang; câu count không join). Yêu cầu tạo trước V7 không tồn tại (Neon dev 0 dòng `match_request` — kiểm 2026-11-09) → luôn có cuộc trò chuyện.
- `claimDueForExpiry` (đợt MATCH-002, §1.1; đợt 1 chỉ kèo A → `expirePendingByMatchIds`) trả thêm `type`; lô tách id theo loại → `expirePendingByMatchIds` (kèo A) và `PairingRepository.expireNegotiatingByMatchIds` (kèo B), bỏ câu khi danh sách rỗng.
- `findDetailById` (đợt 1: chỉ `LEFT JOIN venue`; phần pairing + `cost_deposit_amount` của V8 thêm ở đợt MATCH-002, §1.1): `LEFT JOIN venue` (kèo B chưa ghép không có `venue_id`), `LEFT JOIN pairing p ON p.match_id = m.id AND p.status = 'CONFIRMED'` → `confirmedPairingId`; thêm `cost_deposit_amount`, `cost_deposit_holder`.
- Tìm kèo: `m.status = 'OPEN'` (literal) `AND m.expires_at > :now AND (:type IS NULL OR m.type = :type)` + `JOIN team t ON t.id = m.author_team_id AND t.status = 'ACTIVE'` + `JOIN sport s ON s.id = m.sport_id AND s.is_active` + `LEFT JOIN venue v ON v.id = m.venue_id` (kèo B chưa ghép: `null`); khoảng giờ: kèo A so `m.match_start_time`, kèo B `EXISTS (SELECT 1 FROM match_time_option t WHERE t.match_id = m.id AND t.proposed_start_time BETWEEN …)`; `venueOptionCount`/`timeOptionCount`: subquery `count(*)` theo `match_id` (chỉ kèo B); nearby kèo B: `LATERAL (SELECT min(Haversine) FROM match_venue_option o JOIN venue v2 … WHERE o.match_id = m.id AND v2 trong hộp)` → khoảng cách tới lựa chọn gần nhất, lọc `≤ radiusKm` (vẫn 1 câu trang + 1 câu count); `skillLevel`: `(:skillLevel IS NULL OR m.skill_levels = '[]' OR jsonb_exists(m.skill_levels::jsonb, :skillLevel))` (không dùng toán tử `?` — xung đột placeholder JDBC). Nearby: hộp + Haversine trên `venue.latitude/longitude` như VENUE-001 §5.2.
- `findTeamMatches`: `(m.author_team_id = :teamId OR m.confirmed_opponent_team_id = :teamId)` theo `role`; lọc `status` trên biểu thức trạng thái hiệu lực.
- `insertAll` (user chốt 2026-11-09 — một câu bất kể số khoản, không bật JDBC batch toàn ứng dụng): `INSERT INTO cost_custom_item (id, match_id, item_name, amount, display_order, created_at) SELECT uuidv7(), :matchId, t.name, t.amount, t.ord, :now FROM unnest(CAST(:names AS text[]), CAST(:amounts AS bigint[])) WITH ORDINALITY AS t(name, amount, ord)`. Id do hàm built-in `uuidv7()` của **PostgreSQL ≥ 18** sinh (cùng định dạng v7 với entity — ADR-0008 Sửa đổi 2026-11-09); `ord` = 1..n theo thứ tự mảng = thứ tự request. Neon dev đã kiểm 2026-11-09: `server_version = 18.6`, có `uuidv7` `[cần xác nhận #11]` cho môi trường khác. Chạy sau `deleteByMatchIdBulk` (Hibernate tự flush `UPDATE match` trước câu native đầu tiên). Id do DB sinh → response đọc lại `findByMatchIdOrderByDisplayOrderAsc` (1 câu). Danh sách rỗng → không gọi insert lẫn bước đọc `[cần xác nhận #1]`.
- `forceIncrementVersion` (fragment, tiền lệ USER-002 dùng `OPTIMISTIC_FORCE_INCREMENT` cho invite): đặt trong repository để service không cầm `EntityManager`; chỉ gọi trên nhánh thành công của 2.6 (sau mọi guard), để nhánh lazy-expiry (bulk `version + 1`) không bị kiểm version lần hai lúc commit.

Ghi chú SQL chat (v2.4):
- **`ConversationRepository.findContext(id, callerId, now)`** — một câu cho mọi loại ngữ cảnh:
  ```sql
  SELECT c.id, c.context_type, c.match_id, c.party_a_type, c.party_a_id, c.party_b_type, c.party_b_id,
         me.display_name AS caller_name, me.avatar_url AS caller_avatar_url, (me.deleted_at IS NOT NULL) AS caller_deleted,
         -- TEAM_MATCH
         m.type AS match_type, m.status AS match_status, m.expires_at, m.author_team_id, m.confirmed_opponent_team_id,
         ta.name AS party_a_name, ta.avatar_url AS party_a_avatar_url, ua.display_name AS party_a_captain_name, (ua.deleted_at IS NOT NULL) AS party_a_captain_deleted,
         COALESCE(ca.user_id = :callerId, false) AS caller_party_a_captain,
         ta.status AS party_a_team_status, COALESCE(ta.disbanded_by = :callerId, false) AS caller_party_a_disbanded_by,
         tb.name AS party_b_name, …(tương tự bên B)…, COALESCE(cb.user_id = :callerId, false) AS caller_party_b_captain,
         tb.status AS party_b_team_status, COALESCE(tb.disbanded_by = :callerId, false) AS caller_party_b_disbanded_by,
         lr.id AS request_id, lr.status AS request_status
         -- MERCENARY_APPLICATION: khối cột của MERCENARY-001 §5.2 (LEFT JOIN … ON c.context_type = 'MERCENARY_APPLICATION')
  FROM conversation c
  LEFT JOIN "user" me ON me.id = :callerId
  LEFT JOIN match m ON c.context_type = 'TEAM_MATCH' AND m.id = c.match_id
  LEFT JOIN team ta ON c.context_type = 'TEAM_MATCH' AND ta.id = c.party_a_id
  LEFT JOIN team_member ca ON ca.team_id = ta.id AND ca.role = 'CAPTAIN' AND ca.status = 'ACTIVE' LEFT JOIN "user" ua ON ua.id = ca.user_id
  LEFT JOIN team tb … LEFT JOIN team_member cb … LEFT JOIN "user" ub …            -- bên B, như bên A
  LEFT JOIN LATERAL (SELECT r.id, r.status FROM match_request r
                     WHERE m.type = 'HAS_VENUE' AND r.match_id = c.match_id AND r.requester_team_id = c.party_b_id
                     ORDER BY r.requested_at DESC, r.id DESC LIMIT 1) lr ON true
  -- kèo NEED_VENUE: MATCH-002 thêm LEFT JOIN pairing pr … và cột pr.id, pr.status (MATCH-002 §5.2)
  WHERE c.id = :id
  ```
  Yêu cầu **gần nhất** của đội (luồng A): đội chỉ có một yêu cầu đang hiệu lực mỗi kèo (`idx_match_request_active`), yêu cầu cũ chỉ có thể `WITHDRAWN`/`EXPIRED` → yêu cầu gần nhất là yêu cầu quyết định (BR-N04 gửi lại); LATERAL theo `idx_match_request_match (match_id, status)` + lọc đội (số yêu cầu của một đội cho một kèo rất nhỏ). Trạng thái hiển thị `request_status` tính bằng `CASE` trạng thái hiệu lực như §5.2 (yêu cầu `PENDING` + kèo quá hạn → `EXPIRED`); policy dùng trạng thái DB + `expires_at` (§4.14). `COALESCE` vì đội đã giải tán không có đội trưởng. `team.status`, `team.disbanded_by` (V4 — USER-002) đọc trên dòng `team` đã join (người đã giải tán chỉ xem — user chốt 2026-11-15): thêm cột, **không** thêm câu. Join theo khoá chính / index, không đổi số câu.
- Tin nhắn — cursor theo **`(created_at, id)`**, không theo `id` riêng: id là UUID v7 (`ConversationMessage` `@UuidGenerator(VERSION_7)`) chỉ tăng chặt trong một JVM, gần đúng ở mức mili-giây giữa instance/đồng hồ (ADR-0008 Hệ quả: "cần thứ tự thời gian → dùng cột `*_at`"). `findBefore`: `WHERE conversation_id = :c AND (created_at, id) < (:createdAt, :id) ORDER BY created_at DESC, id DESC LIMIT :limit`; `findAfter`: `> … ORDER BY created_at ASC, id ASC`; `findLatest`: `ORDER BY created_at DESC, id DESC`. `idx_conversation_message_time` phục vụ cả ba (row comparison). Cả ba `JOIN "user" su ON su.id = cm.sender_user_id` → `senderName`, `senderAvatarUrl`, `senderDeleted` — join theo khoá chính trong cùng câu, không N+1; `JOIN` (không `LEFT`) vì FK `RESTRICT` + `NOT NULL`. `created_at` gán ở `@PrePersist` (`AppTime.now()` — SYSTEM-002).
- **`findMine` / `countMine` (2.20):**
  ```sql
  WITH my_teams AS (                         -- đội người gọi là đội trưởng ACTIVE, hoặc đã giải tán (chỉ xem)
      SELECT tm.team_id FROM team_member tm WHERE tm.user_id = :callerId AND tm.role = 'CAPTAIN' AND tm.status = 'ACTIVE'
      UNION SELECT t.id FROM team t WHERE t.disbanded_by = :callerId AND t.status = 'DISBANDED'),
  mine AS (                                  -- cuộc trò chuyện có một bên là đội của tôi, hoặc bên B là chính tôi (ứng viên)
      SELECT c.id FROM conversation c JOIN my_teams mt ON c.party_a_type = 'TEAM' AND c.party_a_id = mt.team_id
      UNION SELECT c.id FROM conversation c JOIN my_teams mt ON c.party_b_type = 'TEAM' AND c.party_b_id = mt.team_id
      UNION SELECT c.id FROM conversation c WHERE c.party_b_type = 'USER' AND c.party_b_id = :callerId),
  ranked AS (                                -- khoá sắp xếp: tin cuối; chưa có tin → luồng A lần gửi yêu cầu gần nhất, còn lại lúc mở (user chốt 2026-11-15)
      SELECT c.id, COALESCE(lm.created_at, lr.requested_at, c.created_at) AS last_activity_at, lm.id AS last_message_id
      FROM mine x JOIN conversation c ON c.id = x.id
      LEFT JOIN LATERAL (SELECT cm.id, cm.created_at FROM conversation_message cm WHERE cm.conversation_id = c.id
                         ORDER BY cm.created_at DESC, cm.id DESC LIMIT 1) lm ON true
      LEFT JOIN LATERAL (SELECT max(r.requested_at) AS requested_at FROM match_request r   -- chỉ khi chưa có tin, luồng A
                         WHERE lm.id IS NULL AND c.context_type = 'TEAM_MATCH'
                           AND r.match_id = c.match_id AND r.requester_team_id = c.party_b_id) lr ON true
      ORDER BY last_activity_at DESC, c.id DESC LIMIT :limit OFFSET :offset)
  SELECT r.last_activity_at,
         <cột ngữ cảnh + cờ người gọi y hệt findContext cho c.id = r.id>,              -- policy cần
         lmsg.id, left(lmsg.message, 100) AS last_content_preview, lmsg.sender_party, lmsg.created_at,
         su.display_name, (su.deleted_at IS NOT NULL) AS last_sender_deleted,
         EXISTS (SELECT 1 FROM conversation_message u WHERE u.conversation_id = r.id AND u.sender_party = 'A' AND NOT u.is_read) AS unread_from_a,
         EXISTS (SELECT 1 FROM conversation_message u WHERE u.conversation_id = r.id AND u.sender_party = 'B' AND NOT u.is_read) AS unread_from_b,
         <tên / ảnh hai bên: team theo party_*_id khi TEAM; "user" (display_name, avatar_url, deleted_at) khi USER>,
         <giờ bắt đầu: match.match_start_time (đợt 1); MERCENARY-001 thêm external_match.match_start_time — §1.1>
  FROM ranked r JOIN conversation c ON c.id = r.id
  LEFT JOIN conversation_message lmsg ON lmsg.id = r.last_message_id LEFT JOIN "user" su ON su.id = lmsg.sender_user_id
  …(join của findContext)…
  ORDER BY r.last_activity_at DESC, r.id DESC
  ```
  `countMine`: `WITH my_teams … , mine … SELECT (SELECT count(*) FROM mine) AS total, COALESCE((SELECT deleted_at IS NOT NULL FROM "user" WHERE id = :callerId), true) AS caller_deleted` — một câu. LATERAL yêu cầu gần nhất chỉ chạy cho cuộc trò chuyện chưa có tin (`lm.id IS NULL`), theo `idx_match_request_match (match_id, status)` rồi lọc đội — số yêu cầu của một đội cho một kèo rất nhỏ; kèo `NEED_VENUE` không có `match_request` → `max` = `NULL` → lúc mở. Khối cột ngữ cảnh viết **một lần** (hằng SQL dùng chung `ConversationSql.CONTEXT_COLUMNS` / `CONTEXT_JOINS` cho `findContext` và `findMine`) — không chép tay hai nơi. Join ngữ cảnh / tên chỉ chạy cho ≤ `pageSize` dòng sau `LIMIT`; LATERAL tin cuối chạy cho mọi cuộc trò chuyện của người gọi (một lần dò index `idx_conversation_message_time` lùi mỗi dòng) để sắp xếp. `UNION` (không `UNION ALL`) bỏ trùng khi người gọi làm đội trưởng cả hai đội của một cuộc trò chuyện. `contentPreview` cắt 100 ký tự ở SQL (`left`) — giới hạn dữ liệu trả về (tin tối đa 1000 ký tự × 100 dòng).
- `markOtherPartyRead`: `UPDATE conversation_message SET is_read = TRUE WHERE conversation_id = :c AND sender_party <> :readerParty AND NOT is_read` (`idx_conversation_message_unread`).

### 5.3. Projection

| Projection | Cột |
|---|---|
| `MatchDetailView` | cột `match` cho `MatchResponse` (gồm `type`, `effectiveStatus`, `skillLevels` JSON thô, `costDepositAmount`, `costDepositHolder`) + `sportName`, tác giả/đối thủ (`id`, `name`, `avatarUrl`, `reliabilityScore`), venue nullable (`name`, `address`, `latitude`, `longitude`, `status`, `primaryImageUrl`), `confirmedPairingId` |
| `MatchSummaryView` | cột `MatchSummaryResponse` (gồm `type`, venue nullable, `venueOptionCount`, `timeOptionCount`) + `distanceKm` (`Double`) |
| `MatchRequestView` | cột `MatchRequestResponse` (gồm `effectiveStatus`, `conversationId` — v2.4) + ngữ cảnh kèo cho 2.12 |
| `MatchRequestContextView` | request (`id`, `matchId`, `requesterTeamId`, `status`) + match (`authorTeamId`, `status`, `expiresAt`, `matchStartTime`) + đội gửi (`name`, `status`, `reliabilityScore`) + `sportActive` |
| `MatchAvailabilityView` | `authorTeamStatus`, `sportActive` + `authorTeamName`, `authorTeamAvatarUrl`, `authorTeamReliabilityScore`, `authorCaptainName`, `authorCaptainDeleted` (response MATCH-002 2.1) + `existingConversationId` (v2.4) |
| `MatchAuthorAccessView` | `authorTeamId`, `authorTeamStatus`, `callerIsCaptain`, `type` (`MATCH_016` ở 2.7, MATCH-002 2.2) |
| `DueMatchView` | `id`, `type`, `authorTeamId`, `opponentTeamId`, `matchEndTime` |
| `TeamCaptainContextView` | `status`, `captain`, `name`, `avatarUrl`, `reliabilityScore`, `userDisplayName`, `userDeleted` |
| `ConversationContextView` | chung: `id`, `contextType`, `matchId`, `partyAType`, `partyAId`, `partyBType`, `partyBId`, `callerName`, `callerAvatarUrl`, `callerDeleted` · `TEAM_MATCH`: `matchType`, `matchStatus`, `expiresAt`, `authorTeamId`, `matchOpponentTeamId`, `partyAName`, `partyAAvatarUrl`, `partyACaptainName`, `partyACaptainDeleted`, `callerPartyACaptain`, `partyATeamStatus`, `callerPartyADisbandedBy`, `partyBName`, `partyBAvatarUrl`, `partyBCaptainName`, `partyBCaptainDeleted`, `callerPartyBCaptain`, `partyBTeamStatus`, `callerPartyBDisbandedBy`, `partyACaptainUserId`, `partyADisbandedBy`, `partyBCaptainUserId`, `partyBDisbandedBy` (v2.5 — người nhận `CONVERSATION_ACTIVITY`; lấy từ join đội trưởng / `team` đã có, không thêm câu; khối `MERCENARY_APPLICATION` điền hai cột bên A, bên B `USER` = `partyBId`), `requestId`, `requestStatus` (DB), `requestEffectiveStatus` (+ `pairingId`, `pairingStatus`, `pairingEffectiveStatus` — MATCH-002 §5.3) · `MERCENARY_APPLICATION`: MERCENARY-001 §5.5 (cùng projection) |
| `ConversationMessageView` | `id`, `conversationId`, `senderParty`, `senderUserId`, `senderName`, `senderAvatarUrl`, `senderDeleted`, `message`, `isRead`, `createdAt` |
| `ConversationListRowView` (2.20) | `extends ConversationContextView` (+ cột của MERCENARY-001 §5.5) + `lastActivityAt`, `lastMessageId`, `lastContentPreview`, `lastSenderParty`, `lastMessageCreatedAt`, `lastSenderName`, `lastSenderDeleted`, `unreadFromA`, `unreadFromB`, tên / ảnh / cờ đã xoá của hai bên (`partyAName`… khi `USER`), `startsAt` |
| `ChatCursorView` | `createdAt`, `id` |
| `MineCountView` (2.20) | `total`, `callerDeleted` |

Thời gian là `LocalDateTime` giờ Việt Nam, mapper gán thẳng (SYSTEM-002 `Component_Class_Design.md` §2).

## 6. Entity

| Entity | Thay đổi |
|---|---|
| `match.Match` | + `@Version Long version` · `updatedAt`: bỏ `@Setter(AccessLevel.NONE)` để 2.4 gán tường minh (§4.1; `@PreUpdate` vẫn giữ) · + `skillLevels` (`List<String>`, `@Convert(SkillLevelsConverter)`, `columnDefinition = "TEXT"`, `@Builder.Default List.of()`) · + `cancelReason` (`length = 500`) · `format`: `String` → `enum Format { FIVE_A_SIDE, SEVEN_A_SIDE, ELEVEN_A_SIDE }` (`length = 20`) · `costType` → `enum CostType { VENUE_ONLY, DRINKS_ONLY, BOTH }` · `costSplit` → `enum CostSplit { FIFTY_FIFTY, SEVENTY_THIRTY_HOME, SEVENTY_THIRTY_LOSER, LOSER_PAYS_DRINKS }` (`length = 50`, `@Enumerated(STRING)`); sửa Javadoc lớp (version) |
| `match.MatchRequest` | + `reason` (`length = 500`) |
| `team.Team`, `user.User` | Khoá `updatable = false` cho đủ 6 cột uy tín / bộ đếm report: `reliabilityScore`, `cleanStreak`, `reportCountNoshow`, `reportCountBadPlay`, `reportCountBadCommunication`, `reportCountSkillMismatch` — chỉ `ReputationService` (và REPUTATION-001 / MERCENARY-001) ghi bằng `UPDATE` nguyên tử (ADR-0013), tránh dirty checking ghi đè khi sửa/giải tán đội hoặc sửa hồ sơ; `insertable` giữ (`@Builder.Default 80` / `0`) |
| `match.CostCustomItem` | + `displayOrder` (`Integer`, `display_order`, `nullable = false`) — ghi bằng câu native `insertAll` (id do DB sinh), entity dùng để đọc; giữ `@UuidGenerator(VERSION_7)` trên `id` (ADR-0008, `EntityIdGenerationTest`) |
| `match.Sport` | Giữ |
| `match.Match`, `match.CostCustomItem` (v2.0) | Thay đổi cho luồng B: MATCH-002 `Component_Class_Design.md` §6 |
| `chat.ChatMessage` | **Xoá** (bảng `chat_message` bị bỏ ở V7) |
| `chat.Conversation` (mới) | `id` (`@UuidGenerator(VERSION_7)`), `contextType` (`enum ContextType { TEAM_MATCH }` ở đợt 1; MERCENARY-001 thêm `MERCENARY_APPLICATION` — §1.1; `@Enumerated(STRING)`), `matchId` (`UUID`, nullable), `partyAType`, `partyBType` (`enum PartyType { TEAM, USER }`), `partyAId`, `partyBId` (`UUID`), `createdAt` (`@PrePersist`); `enum Party { A, B }`. Chỉ INSERT (builder) + đọc. MERCENARY-001 thêm `applicationId`, `externalMatchId` (§6 của mã đó) |
| `chat.ConversationMessage` (mới) | `id` (v7), `conversationId`, `senderParty` (`Conversation.Party`, `@Enumerated(STRING)`, `@JdbcTypeCode(SqlTypes.VARCHAR)`, `length = 1` — chỉ định `SqlTypes.VARCHAR` để khớp cột `VARCHAR(1)` của V7 dưới Hibernate 7 `ddl-auto=validate`), `senderUserId`, `message` (`TEXT`), `isRead` (`boolean`, `@Builder.Default false`), `createdAt` (`@PrePersist`) |

Giữ Lombok/builder/`@PrePersist` (`convention-backend.md` §6, §6.1); `src/main` không gán `id`, `version`, `createdAt`, `updatedAt` qua builder. Kiểm bằng migration integration test (`ddl-auto=validate` sau V7).

## 7. DTO & Mapper

- Chuỗi trong request được `trim()` ở service; field tuỳ chọn gửi `""` → lưu `null` (chuyển từ `API_Spec.md` v1.0 §3).
- DTO là `record`; enum dạng chuỗi dùng `@OneOf`; `skillLevels` dùng `@ValidSkillLevels` (USER-001).
- **`CreateMatchRequest`** (v2.0): + `type` (`@OneOf({"HAS_VENUE","NEED_VENUE"})`, vắng → `HAS_VENUE` qua compact constructor), `venueOptionIds` (`List<UUID>`), `timeOptions` (`List<@Valid TimeOptionRequest>`, `TimeOptionRequest(@NotNull startsAt, @NotNull endsAt)`); `venueId`, `startsAt`, `endsAt` bỏ `@NotNull` (bắt buộc theo luồng trong `@ValidMatchSchedule`).
- **`@ValidMatchSchedule`** (mức lớp, `CreateMatchRequest`): quy tắc thời gian `API_Spec.md` §2.1 (Request body) theo `type` — luồng A như cũ; luồng B: field luồng A phải `null`, 1..`MAX_VENUE_OPTIONS` địa điểm không trùng, 1..`MAX_TIME_OPTIONS` khung giờ không trùng `startsAt`, mỗi khung theo `MIN_LEAD_TIME`/`MAX_DURATION`, `expiresAt` trong [sớm nhất − `MAX_POSTING_WINDOW`, sớm nhất] (hai message riêng cho quá muộn / quá sớm); số lượng, phần tử `null`, trùng địa điểm → lỗi gắn cả danh sách (`venueOptionIds` / `timeOptions`), lỗi từng khung (gồm trùng `startsAt`) gắn `timeOptions[i].startsAt` / `timeOptions[i].endsAt` (bảng lỗi: `API_Spec.md` 2.1); `now` từ `AppTime.now()` (`convention-backend.md` §6.2); lỗi gắn property node theo tên field của request (`startsAt` / `endsAt` / `expiresAt`) vì `GlobalExceptionHandler` chỉ đọc `getFieldErrors()` (như VENUE-001 `@ValidGeoQuery`). Bỏ qua khi field liên quan `null` (để `@NotNull` báo).
- **`@ValidCostAgreement`** (mức lớp, kiểu `CostAgreementFields` — `UpdateCostAgreementRequest` và `UpdatePairingCostAgreementRequest` của MATCH-002 §7 cùng cài): quan hệ `costType` ↔ số tiền ↔ `costSplit`; phần tử `null` trong `customItems` → lỗi gắn `customItems` (`API_Spec.md` §2.4). Compact constructor của hai request đổi `costType`, `costSplit` rỗng / chỉ khoảng trắng → `null` trước khi validate (`@OneOf` và validator chỉ thấy `null`).
- `CancelMatchRequest.expectedStatus` `@NotNull @OneOf({"OPEN","CONFIRMED"})`; `WithdrawMatchRequestRequest.expectedStatus` `@NotNull @OneOf({"PENDING","ACCEPTED"})`.
- **`SearchMatchRequest`**: implement `GeoQueryRequest`; compact constructor gán mặc định (`page = 1`, `pageSize = 20`, `radiusKm = 10` khi có đủ `lat` và `lng`) trước Bean Validation; tái sử dụng `@ValidGeoQuery` (tổng quát hoá từ VENUE-001 qua interface `GeoQueryRequest`) để kiểm cặp `lat`/`lng` và `radiusKm` cần toạ độ; `@ValidMatchSearch` kiểm `skillLevel` ∈ 9 nhãn trình độ và `startsAtFrom ≤ startsAtTo` (lỗi gắn `startsAtTo`). Query param `LocalDateTime` (`startsAtFrom`, `startsAtTo`) được đọc qua `LocalDateTimeParamConverter` (`DateTimeFormatter.ISO_LOCAL_DATE_TIME`, từ chối chuỗi có `Z`/offset → `400 VALIDATION_ERROR` gắn tên tham số) đăng ký trong `WebMvcConfig#addFormatters` (theo SYSTEM-002 `Component_Class_Design.md` §2.5).
- Hằng nghiệp vụ một nơi (`MatchRules` trong `application/support/match`): `MIN_LEAD_TIME = 1h`, `MAX_POSTING_WINDOW = 7d`, `MAX_DURATION = 12h`, `MAX_CUSTOM_ITEMS = 10`, `MAX_AMOUNT = 1_000_000_000`, `MAX_VENUE_OPTIONS = 5`, `MAX_TIME_OPTIONS = 5` (v2.0). Khung trừ điểm ở `TimeBandPenaltyPolicy.MATCH_CANCELLATION`.
- `MatchRequestResponse` (v2.4) + `conversationId` (`UUID`): 2.6 từ `existingConversationId` của `MatchAvailabilityView` hoặc id của `Conversation` vừa `save`; 2.7, 2.12 từ `MatchRequestView`.
- Tên field API khác tên entity: `startsAt` ↔ `matchStartTime`, `endsAt` ↔ `matchEndTime` (cột V1 giữ nguyên; `api-convention.md` §10 hậu tố `At`).
- `MatchResponse`: `venueOptions` dùng `List<MatchVenueResponse>`, `timeOptions` dùng record `MatchTimeOptionResponse(UUID timeOptionId, LocalDateTime startsAt, LocalDateTime endsAt)` (đợt 1 luôn `[]`, khớp `API_Spec.md` §4.2).
- `MatchMapper` (`componentModel = "spring"`, `uses`/`imports` `MapperSupport`, `unmappedTargetPolicy = ERROR`): `toResponse(MatchDetailView, List<CostCustomItem>)` (đợt 1 — §1.1) và overload `toResponse(MatchDetailView, List<CostCustomItem>, List<MatchOptionRowView>)` (đợt MATCH-002; v2.0: + `venueOptions`, `timeOptions`, `confirmedPairingId`; danh sách rỗng ở kèo A), `toCreatedNeedVenue(Match, TeamCaptainContextView, Sport, List<SelectableVenueView>, List<TimeOptionView>)` (kèo B, không đọc lại — đợt MATCH-002); `costAgreement` = `MatchCostAgreementResponse`: `costType`, `costSplit`, `venueAmount`, `drinksAmount`, `depositAmount`, `depositHolder` (v2.0), `customItems` — record lồng riêng, **không** dùng `CostAgreementResponse` 7 field của 2.4; user chốt 2026-11-10, đóng `[cần xác nhận #12]`), `toSummary(MatchSummaryView)` (`distanceKm` HALF_UP 2 chữ số), `toRequest(MatchRequestView)`, `toCreatedRequest(MatchRequest, MatchRequestContext…)`, `toCostAgreement(Match, List<CostCustomItem>)`, `toAccept(…)`, `toCancellation(Match, int penalty)`, `toWithdrawal(…)`. `skillLevels` JSON → list qua `SkillLevelsConverter`. `SportMapper.toResponse(Sport)`.

- Chat (v2.4): `SendConversationMessageRequest(@NotBlank @Size(max = 1000) content)` (đếm sau `trim`; không `teamId` — bên gửi do policy xác định); `ChatHistoryQuery(before, after: UUID; limit 1..100, mặc định 50 qua compact constructor)` + `@ValidChatCursor` (không cùng lúc `before` và `after`, lỗi gắn `after`).
- `ConversationMapper` (`uses = MapperSupport.class`, `unmappedTargetPolicy = ERROR`): `toResponse(ConversationContextView, ConversationAccess)` (2.19 — `parties` theo loại ngữ cảnh: `TEAM_MATCH` = [đội A, đội B] với `captainName = maskName(…CaptainName, …CaptainDeleted)`; field của loại khác = `null`; `source = SYSTEM` cho `TEAM_MATCH`), `toMessageResponse(ConversationMessage, ConversationContextView)` (2.16 + frame — người gửi = cột `caller*` của ctx, `senderPartyId` = `partyAId`/`partyBId` theo `senderParty`), `toMessageResponse(ConversationMessageView, ConversationContextView)` (2.17). `senderName` = `maskName(…, senderDeleted)`, `senderAvatarUrl` = `maskAvatar(…, senderDeleted)` — che ở mapper, SQL không che (USER-001 §7). Không thêm tên đội vào tin: FE lấy từ `parties` của 2.19 (`API_Spec.md` §4.8). Mapper khối mercenary (`MERCENARY_APPLICATION`): MERCENARY-001 §7.
- `ChatHistoryResponse(List<ConversationMessageResponse> items, boolean hasMore)` — dùng chung mọi ngữ cảnh.
- `ConversationSummaryResponse` (2.20, `API_Spec.md` §4.9) + `PartyBriefResponse(partyType, partyId, name, avatarUrl)`, `LastMessagePreviewResponse(messageId, contentPreview, senderPartyId, senderName, createdAt)`; `ConversationMapper.toSummary(ConversationListRowView, ConversationAccess)` — `otherParty` = bên khác `viewerParty`; tên / ảnh `USER` qua `maskName` / `maskAvatar`.

## 8. Cấu hình (nguồn duy nhất cho giá trị) & Dependencies

```properties
arenaflow.match.lifecycle.cron=${MATCH_LIFECYCLE_CRON:0 */5 * * * *}
arenaflow.match.lifecycle.batch-size=200
arenaflow.match.lifecycle.max-batches-per-run=50
# Cấu hình CHUNG toàn ứng dụng (mọi @Scheduled dùng chung pool này, gồm job VENUE-001)
spring.task.scheduling.pool.size=5
```

| Tham số | Lý do giá trị |
|---|---|
| `cron` mỗi 5 phút | UC-M09, DoD Match; tắt bằng `-` trong test |
| `spring.task.scheduling.pool.size` 5 (chung toàn ứng dụng; v2.2: 3 → 4; v2.5: 4 → 5 — thêm `RetentionScheduler` của NOTIFICATION-001) | Mặc định Spring Boot là 1 luồng cho mọi `@Scheduled` → job dọn asset VENUE-001 (gọi HTTP Cloudinary, có thể chạy lâu) chặn job vòng đời kèo 5 phút. Hiện có 5 job định kỳ: vòng đời kèo (MATCH-001), cảnh báo 180 ngày và dọn asset (VENUE-001), đối soát huy hiệu (REPUTATION-001), xoá hạn lưu (NOTIFICATION-001) → mỗi job một luồng, không job nào chờ job khác. **Lưu ý cài đặt (`SchedulingConfig`):** vì STOMP đăng ký thêm bean `TaskScheduler` (`wsHeartbeatScheduler`, `messageBrokerTaskScheduler`), auto-config `@ConditionalOnMissingBean(TaskScheduler.class)` của Spring Boot không tự tạo scheduler cho `@Scheduled`; `SchedulingConfig` khai báo tường minh `@Bean(name = "taskScheduler")` dựng từ `ThreadPoolTaskSchedulerBuilder` để giữ pool 5 luồng `scheduling-*` (đóng `[cần xác nhận #10]`). Thêm job định kỳ mới → tăng giá trị này |
| `batch-size` 200 × `max-batches-per-run` 50 | ≤ 10 000 kèo/lần chạy cho mỗi loại (AC §3.1 "≤10000"); lô nhỏ để transaction ngắn, không chặn API |

`ClockConfig`: tạo ở SYSTEM-002 (`Component_Class_Design.md` §2.2, ADR-0018 — `Clock.tick(Clock.systemDefaultZone(), 1µs)` theo timezone JVM `Asia/Ho_Chi_Minh`); mã này dùng lại, không tạo lần hai. Test thay bằng `Clock.fixed(…, ZoneId.systemDefault())` cho biên khung trừ điểm / hết hạn.

**Kênh realtime (v2.4 — chuyển từ MATCH-002):**

```xml
<!-- api/pom.xml — version theo spring-boot-starter-parent -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```
```properties
arenaflow.websocket.allowed-origin-patterns=${WS_ALLOWED_ORIGIN_PATTERNS:}
arenaflow.websocket.heartbeat-ms=10000
```

Tên artifact với Boot 4.0.9: đã kiểm chứng `spring-boot-starter-websocket` (kéo `spring-websocket` 7.0.10, đóng `[cần xác nhận #12]`).

| Tham số | Lý do giá trị |
|---|---|
| `allowed-origin-patterns` rỗng mặc định | Fail-closed như ADR-0004: chỉ same-origin; client native không gửi `Origin`. **NEVER** `*` (`security-backend.md` §6). Domain FE thật: `[cần xác nhận #13]` |
| Heartbeat 10s/10s | Phát hiện kết nối chết trong ~20s; scheduler riêng 1 luồng (`ThreadPoolTaskScheduler` bean `wsHeartbeatScheduler`), không chiếm `spring.task.scheduling.pool.size` của job (§8) |

Không thêm cấu hình cho chat ngoài hằng ở §4.14.

## 9. Security

- Không endpoint public mới; không đổi `SecurityConfig`.
- Quyền đội trưởng kiểm trong service bằng `TeamGuard` (cần thứ tự guard, như USER-001 §4). Admin: `/api/v1/admin/**` + audit log sau commit (§4.11).
- Grace period user đã xoá: dựa JWT tối đa 1h (mức USER-001 `Processing_Flow.md` §9); captain đã xoá không còn `team_member` ACTIVE (USER-002) → `TEAM_002`.

Chat và kênh realtime (v2.4):

- `SecurityConfig` (USER-001 §9.1, sửa): `/ws/**` → `permitAll` (handshake HTTP; xác thực ở STOMP `CONNECT`); `BearerTokenResolver` bỏ qua `/ws/**` như `/api/v1/auth/**` (header Bearer cũ trên handshake không gây 401). Mọi endpoint REST mới nằm dưới `/api/v1/**` → `authenticated()`.
- Quyền đội trưởng kiểm trong service (`TeamGuard`, `ConversationAccessPolicy`); không `@PreAuthorize` theo đội.
- Nội dung chat: không log nội dung tin, token, header `Authorization` (`security-backend.md` §5.3). Log lỗi STOMP chỉ mã lỗi + `conversationId`.
- Response / frame tin nhắn chứa thông tin hồ sơ công khai của người gửi (`senderUserId`, tên hiển thị, ảnh) — cùng mức thông tin đội trưởng ở chi tiết đội (USER-001); không email / số điện thoại. Người đã xoá tài khoản → che theo ADR-0002. Chỉ hai bên của cuộc trò chuyện đọc được (`CHAT_003`): `TEAM_MATCH` — đội trưởng ACTIVE hiện tại của hai đội, và người đã giải tán đội của mình (chỉ xem — user chốt 2026-11-15); đội khác của cùng kèo không thấy (BR-N04). `conversationId` là UUID v7 (đoán được thời điểm tạo, không đoán được giá trị) — quyền vẫn kiểm mọi request, không dựa vào việc id khó đoán.
- XSS: lưu nguyên văn (đã trim), FE escape khi hiển thị (`security-backend.md` §4.2).
- Rủi ro đã chấp nhận ở ADR-0014: quyền subscribe không bị thu hồi khi chuyển quyền đội trưởng / token hết hạn cho tới khi ngắt kết nối.

## 10. Lệch convention / chuẩn có chủ đích

| Quy ước | Thiết kế | Lý do | Trạng thái |
|---|---|---|---|
| ADR-0003 / ADR-0007: không `SELECT … FOR UPDATE` ngoài ngoại lệ có ADR | Job claim lô bằng `FOR NO KEY UPDATE SKIP LOCKED` | Phát event chính xác cho đúng các kèo được chuyển mà không cần `UPDATE … RETURNING`; an toàn khi nhiều instance | ADR-0012; ADR-0003 Sửa đổi 2026-11-16 |
| UC-M09 1a2 "lazy check cập nhật ngay" | API đọc chỉ tính trạng thái hiệu lực, không ghi | Ghi trong 2.3 vượt N_DETAIL (2 đọc + 2 ghi); kết quả quan sát được như spec | (user chốt 2026-11-09) |
| `non-functional-standards.md` §3: không đọc lại dữ liệu đã nạp | 2.1 đọc `MatchDetailView` sau INSERT | Response cần tên đội/sân/môn (join); dùng chung mapper với 2.3 (tiền lệ USER-001 `PATCH /profile`, VENUE-001 2.10–2.12) | Ghi nhận trong thiết kế |
| `convention-backend.md` §9: `@Transactional(readOnly)` mức class | Transaction theo method; ghi qua retrier | Kế thừa USER-001 §4.6 | Đã có tiền lệ |
| `convention-backend.md` §6.1: field do JPA quản lý không gán từ `src/main` (nêu cho builder) | 2.4 gán `Match.updatedAt` bằng setter | Khi chỉ đổi khoản tự nhập, entity không dirty → không có `UPDATE match` (mất CAS với chấp nhận) và `@PreUpdate` không chạy. Setter chỉ để entity dirty → Hibernate chạy `UPDATE match` CAS (tăng `version`, kích `@PreUpdate`) kể cả khi chỉ đổi khoản tự nhập; giá trị gán bị `@PreUpdate` ghi đè bằng `AppTime.now()`, nhất quán các entity khác (user chốt không đổi, 2026-11-09) | Ghi nhận trong thiết kế |
| Entity ghi qua dirty checking | 6 cột uy tín / bộ đếm report (`reliability_score`, `clean_streak`, 4 `report_count_*`) của `Team`/`User` `updatable = false`, chỉ `UPDATE` nguyên tử | Tránh mất cập nhật khi entity `Team`/`User` (không version ở `User`, `adjustReliability` trên `Team` không tăng version) ghi lại mọi cột | ADR-0013 |
| `api-convention.md` §7: mặc định `page`/`pageSize` | 2.17 cursor `before`/`after`/`limit`, `{items, hasMore}` (v2.4, chuyển từ MATCH-002) | Danh sách chat thêm tin liên tục → offset trôi; user chốt 2026-11-10 | Đã có `api-convention.md` §7.2 |
| ADR-0011 quy tắc 3: side effect qua event | Frame realtime qua port `ChatRealtimeGateway` `afterCommit`, không qua event | Frame cần nội dung tin (event không mang dữ liệu người dùng) | ADR-0014 |

## 11. Tài liệu cần đồng bộ khi implement

Sau khi mọi phase code xong + test PASS (CLAUDE.md §8):

| Tài liệu | Nội dung | Nhánh |
|---|---|---|
| `docs/database/schema.dbml`, `schema-design.md` §3.1, §3.4, §3.5 và quy ước ID (dòng 11) | Cột/CHECK/index V7; bỏ `CUSTOM` ở `cost_type`; dòng "tối đa 1 ACCEPTED (application layer)" → unique index; quy ước ID "Hibernate generates UUID v7 từ Java, không cần DEFAULT trong DB" thêm ngoại lệ: id `cost_custom_item` do `uuidv7()` của PostgreSQL ≥ 18 sinh trong câu INSERT gom lô (ADR-0008 Sửa đổi 2026-11-09) | `feature/MATCH-001-*` |
| `docs/database/data-model.md` §3.3 | Lý do huỷ/từ chối/rút; thoả thuận chi phí chỉ khi `OPEN`; rút `ACCEPTED` ⇒ huỷ kèo | `feature/MATCH-001-*` |
| `docs/agent/api-convention.md` §5, §6 | 15 endpoint vào bảng 2xx (tiêu chí ở §3); domain `MATCH`, `SPORT` (đã soát `ErrorCode` hiện hành 2026-11-09: chưa có `MATCH_*`, `SPORT_*`; mã `VENUE_*` theo VENUE-001) | `chore/agent-framework-*` (qua `workflow-adapt`) |
| `docs/agent/convention-backend.md` §1 | `application/event/<miền>/`, `application/support/<miền>/`, `api/config/ClockConfig` | `chore/agent-framework-*` |
| `docs/agent/design/USER-001/Component_Class_Design.md` §4 (`TeamGuard`), §6 (`Team`/`User` `updatable = false`) | + `USER-001/CHANGELOG.md` MINOR | `feature/MATCH-001-*` |
| `docs/agent/design/VENUE-001/Component_Class_Design.md` §4.13 | Overload `AdminAuditLog` + `VENUE-001/CHANGELOG.md` MINOR (tạo mới) | `feature/MATCH-001-*` |
| `docs/agent/design/VENUE-001/Component_Class_Design.md` §8 | Ghi chú job VENUE-001 chạy trên pool scheduling chung (`spring.task.scheduling.pool.size`, giá trị ở §8 tài liệu này) | `feature/MATCH-001-*` (+ `VENUE-001/CHANGELOG.md` MINOR) |
| ADR-0003 | Mục Sửa đổi 2026-11-16 trỏ ADR-0012 — đã viết (ADR-0012 `Accepted`) | Đã xong |
| `docs/agent/decisions/0008-uuid-v7-primary-keys.md` | Mục Sửa đổi 2026-11-09 (`uuidv7()` cho INSERT gom lô viết tay) — đã viết sẵn trong thay đổi thiết kế này; kiểm lại khớp code | Cùng nhánh thiết kế |
| `docs/agent/CLAUDE.md` §2 | Dòng điều hướng MATCH-001 | `chore/agent-framework-*` |
| `docs/database/schema.dbml`, `schema-design.md` §7.1 (chat), `data-model.md` (chat) | v2.4: bỏ `chat_message`; thêm `conversation` (`context_type`, `match_id`, hai bên, `idx_conversation_team_match`, `idx_conversation_party_a/b`), `conversation_message` (`sender_party`, `sender_user_id` `RESTRICT`, `is_read`, index), `idx_team_disbanded_by`; ERD `match` 1–n `conversation` 1–n `conversation_message`; chat hai đội theo cặp (kèo, đội tham gia), nhắn được / chỉ xem suy ra từ yêu cầu / pairing (BR-N04, BR-N05) | `feature/MATCH-001-*` |
| `docs/agent/api-convention.md` §5, §6, §7.2 (chat — v2.4) | Bảng 2xx: `POST /conversations/{id}/messages` 201 B2; `GET /conversations/{id}`, `GET /conversations/{id}/messages`, `GET /profile/conversations` 200 B1; `POST /conversations/{id}/messages/read` 204 N1 (bỏ các dòng `/pairings/{id}/messages…`); §6 `CHAT_001`..`CHAT_004`; §7.2 bảng "Áp dụng": `GET /pairings/{id}/messages` → `GET /conversations/{id}/messages` (`/profile/conversations` dùng offset — đã phủ) | `chore/agent-framework-*` |
| `docs/agent/convention-backend.md` §1 (v2.4) | `api/realtime/`, `api/chat/`, `application/support/chat/` | `chore/agent-framework-*` |
| `docs/agent/design/USER-001/Component_Class_Design.md` §9.1 (v2.4 — chuyển từ MATCH-002) | `/ws/**` `permitAll` + `BearerTokenResolver` bỏ qua (+ `USER-001/CHANGELOG.md` MINOR, nội bộ) | `feature/MATCH-001-*` |
| ~~Thiết kế NOTIFICATION-001 (chưa có) — job xoá chat hết hạn lưu~~ **Đã thiết kế (v2.5):** NOTIFICATION-001 `Component_Class_Design.md` §4.8, §5.2 `deleteExpiredConversations` — điều kiện dưới đây giữ làm nguồn | Xoá dòng `conversation` (tin xoá theo `ON DELETE CASCADE`) khi trận của cuộc trò chuyện kết thúc quá 90 ngày (BR-N01, BC-N03): `match_id` khác `null` → `match.status` = `COMPLETED`/`ARCHIVED` (mốc `match_end_time`), `CANCELLED` (mốc `cancelled_at`), `EXPIRED` (mốc `expires_at`) + 90 ngày < now — mọi cuộc trò chuyện của kèo (gồm đội không được ghép, và `MERCENARY_APPLICATION`); `external_match_id` khác `null` → MERCENARY-001 §11. Đi từ `match` đã kết thúc rồi tra `conversation` theo tiền tố `match_id` | Khi thiết kế NOTIFICATION-001 |

## 12. Danh sách [cần xác nhận]

Đánh số toàn thiết kế (sau khi user chốt 11 quyết định nghiệp vụ và xử lý review 2026-11-09): 1–5, 10–13, 16, 19 ở đây, 12 và 18 cũng ở `API_Spec.md` §6, 14–15 ở `Processing_Flow.md` §10; 17 không dùng (số không dùng lại); 6–9 ở `Processing_Flow.md` §10.

| # | Nội dung | Loại |
|---|---|---|
| 1 | ~~`insertAll`: bind mảng Java (`String[]`, `Long[]`) vào native query qua Spring Data / Hibernate 7.2~~ — **Đóng v3.1 (2026-11-25):** bind trực tiếp qua Spring Data JPA / Hibernate 7.2.25 trên PostgreSQL 18.7 hoạt động đúng 1 câu (`MatchControllerTest`, `MatchNfsQueryCountTest` 2.4 = 6 PASS) | Đã đóng |
| 2 | `reliability_score`, `clean_streak`, `report_count_*` `updatable = false`: entity `Team`/`User` đang nằm trong persistence context giữ giá trị cũ sau `adjustReliability` cùng transaction (MATCH-001 không đọc lại) | Không chặn |
| 3 | ~~Bộ đếm `total_matches_confirmed/completed`, `clean_streak`, `report_count_*` thuộc REPUTATION-001; huỷ `CONFIRMED` có cắt chuỗi trận sạch không~~ — **Đóng v2.2 (2026-11-12):** user chốt Q3 — huỷ `CONFIRMED` / rút `ACCEPTED` cắt chuỗi trận sạch của đội huỷ (§4.8, §4.13); `total_matches_*` bị xoá (Q5, REPUTATION-001 V9); bộ đếm report do REPUTATION-001 ghi | Đã đóng |
| 4 | Overload `AdminAuditLog.afterCommit(action, adminId, targetType, targetId)` sửa thiết kế VENUE-001 §4.13 (CHANGELOG MINOR) | Đã đóng (đồng bộ ở v3.1) |
| 5 | ~~**Thứ tự implement:** MATCH-001 cần code VENUE-001 và migration V5~~ — **Đóng v3.1 (2026-11-25):** VENUE-001 (V5) và SYSTEM-002 (V6) đã merge lên `main` trước khi tích hợp V7 | Đã đóng |
| 10 | ~~`spring.task.scheduling.pool.size` còn hiệu lực với Spring Boot 4.0.9~~ — **Đóng v3.1 (2026-11-25):** khai báo `@Bean(name = "taskScheduler")` từ `ThreadPoolTaskSchedulerBuilder` trong `SchedulingConfig` giữ đúng `corePoolSize = 5` và prefix `scheduling-` khi có STOMP (`SecurityConfigIntegrationTest` PASS) | Đã đóng |
| 11 | Phiên bản PostgreSQL production (và môi trường khác ngoài Neon dev) chưa kiểm; `uuidv7()` cần ≥ 18 — thấp hơn thì V7 vẫn chạy nhưng 2.4 lỗi khi có khoản tự nhập | Không chặn — kiểm trước khi deploy |
| 12 | ~~(v2.4 — chuyển từ MATCH-002 #1) Spring Framework 7 / Boot 4.0.9 còn SockJS phía server và artifact `spring-boot-starter-websocket`~~ — **Đóng v3.1 (2026-11-25):** `spring-boot-starter-websocket` 4.0.9 (`spring-websocket` 7.0.10) có đầy đủ `.withSockJS()` và `setSelectorHeaderName(null)` (`StompChatIntegrationTest` PASS) | Đã đóng |
| 13 | (v2.4 — chuyển từ MATCH-002 #3) Domain FE cho `WS_ALLOWED_ORIGIN_PATTERNS` | Không chặn — cấu hình môi trường |
| 16 | ~~(v2.4 — chuyển từ MATCH-002 #11) V7 bỏ bảng `chat_message` (V1)~~ — **Đóng v3.1 (2026-11-25):** V7 thêm chốt chặn `DO $$ … IF EXISTS (SELECT 1 FROM chat_message LIMIT 1) THEN RAISE EXCEPTION … $$` trước `DROP TABLE chat_message`; đã chạy V7 thành công trên Neon dev | Đã đóng |
| 18 | Không có frame khi yêu cầu ghép kèo luồng A đổi trạng thái / kèo bị huỷ (`API_Spec.md` §1.7, §6) | Đã đóng — user chốt 2026-11-15: giữ không có frame |
| 19 | **Đã chốt — thứ tự code** (user chọn 2026-11-15, review 2026-11-15): đợt 1 = MATCH-001 luồng A + lõi chat + kênh realtime (V7); mọi nhánh luồng B trong thành phần của mã này code ở đợt MATCH-002 sau V8 (bảng §1.1); trong lúc chờ, 2.1 trả `422 MATCH_016` cho `type = NEED_VENUE` — **đã thực hiện 2026-11-25 (v3.2)**: đợt MATCH-002 đã code, 2.1 nhận `NEED_VENUE` | Kế hoạch implement — không chặn |

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

**Chat dùng chung (v2.4 — user chốt 2026-11-13, 2026-11-15; chuyển từ MATCH-002 v2.0 §13):**

| Quyết định | Nơi áp dụng |
|---|---|
| Chat hai đội mở từ lúc gửi yêu cầu (luồng A, kể cả `PENDING`) / nhận kèo (luồng B); mỗi cặp (kèo, đội tham gia) đúng một; gửi lại yêu cầu dùng tiếp cuộc trò chuyện cũ | API_Spec §1.6; §4.2, §5.1 |
| Nhắn được / chỉ xem theo BR-N05; trạng thái đội / bộ môn không chặn; chỉ đội trưởng hiện tại | §4.14 |
| Mọi chat (luồng A, B, mercenary) dùng `conversation` + `conversation_message`; bỏ `chat_message`; API `/conversations/{id}`; topic `/topic/conversations/{id}` | §4.14, §4.15, §5.1; ADR-0014 Sửa đổi 2026-11-15 |
| Người làm đội trưởng cả hai đội → vai trò đội tạo kèo; người đã giải tán đội → chỉ xem; chấp nhận lọt một tin khi đúng lúc chuyển "chỉ xem" (không khoá) | §4.14; `Processing_Flow.md` §5 |
| "Tin nhắn của tôi" (2.20) — gồm cả cuộc trò chuyện chưa có tin; cuộc trò chuyện chưa có tin xếp theo lần gửi yêu cầu gần nhất (luồng A) / lúc mở (còn lại) | API_Spec 2.20; §4.14, §5.2 |
| Lõi chat + kênh realtime đặt ở MATCH-001 (chat không phụ thuộc MATCH-002 — review 2026-11-15) | §1, §4.14, §4.15, §5.1 |
| Hai đợt code: MATCH-001 (luồng A + chat + realtime) trước; nhánh luồng B trong thành phần MATCH-001 code ở đợt MATCH-002; 2.1 `NEED_VENUE` → `MATCH_016` trong lúc chờ (user chốt 2026-11-15) | §1.1; `API_Spec.md` 2.1 |
