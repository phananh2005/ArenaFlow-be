# Component & Class Design — Thương lượng ghép kèo luồng B (pairing) & chat

| Mục | Giá trị |
|---|---|
| Mã chức năng | `MATCH-002` |
| Phạm vi tầng | Backend (Spring Boot REST API + STOMP/WebSocket). Frontend: ngoài phạm vi repo này |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · Spring Data JPA · Flyway · PostgreSQL ≥ 18 · + `spring-boot-starter-websocket` (§8) |
| Quy ước áp dụng | `docs/agent/convention-backend.md`, `docs/agent/security-backend.md`, `docs/agent/non-functional-standards.md` · kế thừa MATCH-001 `Component_Class_Design.md` (v2.1) §4.5–§4.9, §4.12, §5.2, §7; VENUE-001 §4.6 (port, v1.2), §4.9 (retrier overload); USER-001 §9 (JWT), §7–§8 (`DeletedUserMasking` qua `MapperSupport`) · ADR-0002, ADR-0003 (+ Sửa đổi 2026-11-09, 2026-11-10), ADR-0008 (+ Sửa đổi 2026-11-09), ADR-0011, ADR-0012, ADR-0013, [ADR-0014](../../decisions/0014-realtime-chat-stomp-websocket.md) (`Proposed`) |
| Ràng buộc dữ liệu | V1 + V5 (VENUE-001) + V6 (MATCH-001) + `V7__match_flow_b_pairing.sql` (§5.1). Neon dev `misty-dew-90700890` kiểm 2026-11-10: Flyway V4, `server_version = 18.6`, `match`/`pairing`/`chat_message`/`match_venue_option`/`match_time_option`/`cost_custom_item` đều 0 dòng; tên CHECK V1 `match_check2` (type ↔ `venue_id`); tên FK V1 `chat_message_sender_user_id_fkey` (`ON DELETE SET NULL`) |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-10 · v1.0 (baseline) · hiện tại **v1.1** (solution-architect · 2026-11-11) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan kiến trúc

Layered theo 4 module Maven (ADR-0006). Hợp đồng FE: `API_Spec.md`. Luồng, transaction, số truy vấn, khoá: `Processing_Flow.md`. Phần luồng B nằm trên endpoint của MATCH-001 (tạo / tìm / xem / huỷ kèo, job hết hạn) thiết kế ở MATCH-001 (v2.1); tài liệu này chứa pairing, chat, kênh realtime, migration V7.

- **Pairing** (`PairingService`): mọi đổi trạng thái là `UPDATE … WHERE` có điều kiện (CAS trên `status` / `revision`), không `@Version` trên `Pairing` — cùng mẫu `MatchRequest` của MATCH-001 (ADR-0003 Sửa đổi 2026-11-10). Ghép kèo CAS `match` bằng native `UPDATE … WHERE status = 'OPEN' AND version = :v` (`version + 1` — cùng cột `@Version` của MATCH-001, không nạp entity `Match`). Thứ tự khoá mọi luồng: `match` → `pairing` → `venue` (`Processing_Flow.md` §5). Ghi qua `ConcurrencyConflictRetrier.execute(MATCH_014, …)` + `MatchWriteOutcome` (lazy-expiry commit-rồi-báo-lỗi).
- **Gói thương lượng** (phương án + chi phí + cọc) nằm trên `pairing`; `revision` vừa là số phiên bản gói FE gửi lên, vừa là điều kiện CAS. Ghép kèo chép chi phí/cọc và khoản tự nhập sang `match` (khoản tự nhập: một câu `INSERT … SELECT`) → kèo đã ghép đọc thoả thuận như kèo luồng A (`Processing_Flow.md` §3.7).
- **Chat** (`ChatService`): REST là nguồn chính; đẩy qua STOMP simple broker sau commit qua port `ChatRealtimeGateway` (ADR-0014). Mỗi tin mang người thực sự đã gửi (`sender_user_id` → tên, ảnh; người đã xoá tài khoản được che ở mapper — ADR-0002, USER-001 `DeletedUserMasking`).
- **Domain event** (ADR-0011): `PairingCreatedEvent`; dùng lại `MatchConfirmedEvent` (+ `pairingId`), `MatchCancelledEvent`, `MatchExpiredEvent`, `ReliabilityScoreChangedEvent` của MATCH-001 (§4.5).

**Điều kiện tiên quyết:** code VENUE-001 (V5) và MATCH-001 (V6) — MATCH-001 `Component_Class_Design.md` §12 `[cần xác nhận #5]`.

## 2. Package Structure (delta)

```text
common/        com.phananh.arenaflow.common
└── exception/       ErrorCode (+ MATCH_016, PAIRING_001..PAIRING_008, CHAT_001; dùng lại VENUE_002)

domain/        com.phananh.arenaflow.domain
├── entity/match/    Pairing, MatchVenueOption, CostCustomItem, Match (sửa — §6)
├── entity/chat/     ChatMessage (sửa — §6)
├── repository/      PairingRepository, MatchVenueOptionRepository, MatchTimeOptionRepository,
│                    ChatMessageRepository (mới); CostCustomItemRepository, MatchRepository (sửa)
└── projection/      PairingContextView, PairingDetailView, PairingSummaryView, ProposalCandidateView,
                     PairingAccessView, ConfirmSideResult, TimeOptionView, MatchOptionRowView,
                     ChatMessageView, ChatCursorView (mới)

application/   com.phananh.arenaflow.application
├── service/         PairingService, ChatService, PairingStatusFilter, PairingRoleFilter, PairingSide (mới)
│   └── impl/        PairingServiceImpl, ChatServiceImpl (mới)
├── event/match/     PairingCreatedEvent (mới); MatchConfirmedEvent (sửa — MATCH-001 §4.9)
├── support/match/   PairingGuard, PairingRules (mới)
├── support/chat/    ChatRealtimeGateway, RealtimeFrame (mới — port, không phụ thuộc spring-websocket)
├── validation/      ValidCostAgreement (sửa: áp cho interface CostAgreementFields), ValidDeposit,
│                    ValidChatCursor (mới)
├── dto/request/     CreatePairingRequest, UpdateProposalRequest, UpdatePairingCostAgreementRequest,
│                    ConfirmPairingRequest, SendChatMessageRequest, MarkChatReadRequest, ChatHistoryQuery (mới)
├── dto/response/    PairingResponse (+ PairingTeamResponse, ProposalResponse, PairingCostAgreementResponse),
│                    PairingSummaryResponse, ProposalUpdateResponse, PairingCostUpdateResponse,
│                    PairingConfirmationResponse, ChatMessageResponse, ChatHistoryResponse (mới)
└── mapper/          PairingMapper, ChatMapper (MapStruct, mới); MapperSupport (tái dùng)

api/           com.phananh.arenaflow.api
├── pairing/         PairingController, MatchPairingController, TeamPairingController (mới)
├── chat/            ChatController (mới)
├── realtime/        WebSocketConfig, StompAuthChannelInterceptor, StompChatRealtimeGateway,
│                    WebSocketProperties (mới)
└── config/          SecurityConfig (sửa — §9)
```

Thành phần dùng chung mới/sửa (`ChatRealtimeGateway`, `CostAgreementFields` + `@ValidCostAgreement`, overload `insertAll` cho pairing, `TeamCaptainContextView` / `MatchAvailabilityView` mở rộng) → developer MUST chạy `reuse-scan` trước khi tạo. Che người đã xoá tài khoản: tái dùng `MapperSupport.maskName` / `maskAvatar` (không viết lại).

## 3. Controller Layer

Quy ước như MATCH-001 §3 (`@Validated`, `ResponseEntity<ApiResponse<…>>`, `@CurrentUser UUID currentUserId`, 201 không `Location`).

| Controller | `@RequestMapping` | Endpoint (`API_Spec.md`) | Service |
|---|---|---|---|
| `MatchPairingController` | `/api/v1/matches/{matchId}/pairings` | 2.1, 2.2 | `PairingService` |
| `TeamPairingController` | `/api/v1/teams/{teamId}/pairings` | 2.3 | `PairingService` |
| `PairingController` | `/api/v1/pairings/{pairingId}` | 2.4–2.7 | `PairingService` |
| `ChatController` | `/api/v1/pairings/{pairingId}/messages` | 2.8–2.10 | `ChatService` |

**Mã thành công & tiêu chí `api-convention.md` §5:**

| Endpoint | Thành công | Tiêu chí |
|---|---|---|
| 2.1, 2.8 | 201 | B2 |
| 2.2, 2.3, 2.4, 2.9 | 200 | B1 |
| 2.5 | 200 | B3 (`revision`, `updatedAt`), B4 (phương án đã giải tên địa điểm/giờ) |
| 2.6 | 200 | B3 (`revision`, `itemId`, `updatedAt`), B4 |
| 2.7 | 200 | B3 (`…ConfirmedAt`, `confirmedAt`, `status`, `matchStatus`) |
| 2.10 | 204 | N1 |

**Lý do chọn path/method:** 2.1 tạo tài nguyên con dưới kèo (tiền lệ `POST /matches/{id}/requests`); thao tác trên từng thương lượng dùng `/pairings/{id}` (tiền lệ `/match-requests/{id}`). 2.5, 2.6 `PUT` thay toàn bộ phần gói tương ứng (idempotent về nội dung). 2.7 và 2.10 là action sub-resource `POST` (`api-convention.md` §2.1, tiền lệ `/match-requests/{id}/accept`). 2.9 cursor (`before`/`after`/`limit`, `{items, hasMore}`) — `api-convention.md` §7 chưa có quy tắc cursor; coordinator bổ sung trên nhánh chore (§10).

- 2.2, 2.3: `@RequestParam` enum như MATCH-001 2.7/2.12 (sai giá trị → 400).
- 2.9: `@Valid @ModelAttribute ChatHistoryQuery` (`before`, `after`: `UUID`; `limit` 1..100, mặc định 50 qua compact constructor) + `@ValidChatCursor` (không cùng lúc `before` và `after`, lỗi gắn `after`).

## 4. Service Layer

Không `@Transactional` mức class (USER-001 §4.6). Bảng ranh giới: §4.7.

### 4.1. PairingService
```java
PairingResponse createPairing(UUID matchId, UUID callerId, CreatePairingRequest r);                       // retrier
PageResponse<PairingSummaryResponse> listMatchPairings(UUID matchId, UUID callerId, PairingStatusFilter s, int page, int pageSize); // readOnly
PageResponse<PairingSummaryResponse> listTeamPairings(UUID teamId, UUID callerId, PairingRoleFilter role, PairingStatusFilter s, int page, int pageSize); // readOnly
PairingResponse getPairing(UUID pairingId, UUID callerId);                                                // readOnly
ProposalUpdateResponse updateProposal(UUID pairingId, UUID callerId, UpdateProposalRequest r);           // retrier
PairingCostUpdateResponse updateCostAgreement(UUID pairingId, UUID callerId, UpdatePairingCostAgreementRequest r); // retrier
PairingConfirmationResponse confirm(UUID pairingId, UUID callerId, ConfirmPairingRequest r);             // retrier
```
Deps: `PairingRepository`, `MatchRepository`, `MatchVenueOptionRepository`, `MatchTimeOptionRepository`, `CostCustomItemRepository`, `MatchGuard`, `TeamGuard`, `PairingGuard`, `MatchExpiry`, `VenueSelectionService`, `ConcurrencyConflictRetrier`, `PairingMapper`, `PageRequestFactory`, `ChatRealtimeGateway`, `ApplicationEventPublisher`, `Clock`.
- `createPairing`: MATCH-001 mẫu 2.6 (`findMatch` → captain ctx → `findAvailability` → `saveAndFlush` bắt `idx_pairing_active` → `PAIRING_003` → `forceIncrementVersion(match)`) + `PairingCreatedEvent`. Response dựng từ dữ liệu đã nạp: `Match` (`expiresAt`), captain ctx (tên/ảnh/điểm đội nhận kèo + tên caller = đội trưởng hiện tại), `MatchAvailabilityView` (tên/ảnh/điểm đội tác giả + đội trưởng hiện tại) — MATCH-001 §5.3.
- `updateProposal`: `findContext` → guard (gồm `requireRevision` — `revision` kiểm **trước** so phương án) → `findProposalCandidate(matchId, venueId, timeOptionId)` (thiếu → `PAIRING_008`) → `requireVenueActive(candidate.venueStatus)` (`VENUE_002`) → giống phương án hiện tại → trả ngay, không ghi → `updateProposal(…, expectedRevision)` 0 dòng → `ConcurrencyConflictException` (retry; lần sau guard trả mã đúng) → `realtimeGateway.publishAfterCommit(PAIRING_UPDATED, PROPOSAL)`.
- `updateCostAgreement`: `findContext` → guard → `updateCost(…, expectedRevision)` 0 dòng → retry → `deleteByPairingIdBulk` → `insertAllForPairing` (bỏ khi rỗng) → `findByPairingIdOrderByDisplayOrderAsc` (bỏ khi rỗng) → frame `PAIRING_UPDATED/COST`.
- `confirm`: §4.4.
- Luồng chi tiết: `Processing_Flow.md` §3.1–§3.7.

### 4.2. ChatService
```java
ChatMessageResponse send(UUID pairingId, UUID callerId, SendChatMessageRequest r);          // retrier (chỉ vì nhánh lazy)
ChatHistoryResponse history(UUID pairingId, UUID callerId, ChatHistoryQuery q);             // readOnly
void markRead(UUID pairingId, UUID callerId, MarkChatReadRequest r);                        // @Transactional
void requireSubscribable(UUID pairingId, UUID userId);                                      // readOnly — cho STOMP SUBSCRIBE
```
Deps: `ChatMessageRepository`, `PairingRepository`, `PairingGuard`, `MatchExpiry`, `ConcurrencyConflictRetrier`, `ChatMapper`, `ChatRealtimeGateway`, `Clock`.
- `send`: `findContext` → guard (đội `INACTIVE` được nhắn) → lazy → `status ≠ NEGOTIATING` → `PAIRING_005` → `ChatMessage.builder()` (`senderTeamId = r.teamId`, `senderUserId = callerId`, `message = trim(content)`) `save` → `MESSAGE_CREATED` sau commit. Response + frame: `chatMapper.toResponse(message, ctx)` — người gửi lấy từ cột caller của `ctx` (`callerName`, `callerAvatarUrl`, `callerDeleted`), không đọc lại. Không phát domain event (không có loại thông báo chat trong 11 loại; realtime đi qua gateway).
- `history`: `findAccess(pairingId, callerId)` → `PAIRING_001`/`PAIRING_002` → có con trỏ: `findCursor(pairingId, messageId)` → không có → `CHAT_001` → `findBefore` / `findAfter` / `findLatest` với `limit + 1` (mỗi dòng đã JOIN người gửi) → `hasMore = size > limit`, cắt còn `limit`.
- `markRead`: `findContext` → guard (không kiểm trạng thái thương lượng) → `markOtherSideRead(pairingId, teamId)`.
- `requireSubscribable`: `findAccess` → `PAIRING_001`/`PAIRING_002` (ném `BusinessException`, interceptor đổi thành frame `ERROR`).

### 4.3. PairingGuard (`application/support/match`, `@Component`)
Một nguồn cho thứ tự guard trên `PairingContextView` (đọc một câu, không truy vấn thêm):
```java
PairingSide requireSide(PairingContextView ctx, UUID teamId);        // teamId ∉ {author, interested} → PAIRING_002; trả AUTHOR | INTERESTED
void requireCaptain(PairingContextView ctx, PairingSide side, boolean allowInactive);
                                                                     // đội phía đó: DISBANDED → TEAM_016; INACTIVE && !allowInactive → TEAM_015;
                                                                     // caller không phải captain ACTIVE → TEAM_002
void requireReader(PairingAccessView a);                             // không phải captain ACTIVE của đội nào → PAIRING_002
void requireNegotiating(PairingContextView ctx);                     // status ≠ NEGOTIATING → PAIRING_005
void requireRevision(PairingContextView ctx, int expected);          // ≠ → PAIRING_006
void requireVenueActive(String venueStatus);                         // INACTIVE → VENUE_002 (2.5: địa điểm được chọn; 2.7: địa điểm đã chốt)
```
`PairingSide` (`AUTHOR`, `INTERESTED`) quyết định cột xác nhận. Thứ tự gọi: `Processing_Flow.md` §1.1.

### 4.4. Xác nhận (UC-M07) — `PairingServiceImpl.confirm`
Lần thử (retrier `MATCH_014`), `now` từ `Clock`. Không nạp entity `Match`; mọi câu ghi là native CAS.
1. `findContext(pairingId, callerId)` → guard (`requireSide`, `requireCaptain(allowInactive = false)`) → lazy (`MatchExpiry`) → `requireNegotiating` → `requireRevision` → `proposedVenueId == null` → `PAIRING_007` → `requireVenueActive(ctx.proposedVenueStatus)` → `VENUE_002` → đội phía kia ≠ `ACTIVE` → `MATCH_013` → `!sportActive` → `SPORT_002` → phía mình đã có `…ConfirmedAt` → trả trạng thái hiện tại (không ghi).
2. Phía kia **đã** xác nhận trong `ctx` → **hoàn tất** (bước 4).
3. Phía kia **chưa** xác nhận → `confirmSide(pairingId, side, revision, now)` (`UPDATE … RETURNING author_confirmed_at, interested_confirmed_at`; 0 dòng → `ConcurrencyConflictException`):
   - phía kia vẫn `null` → frame `PAIRING_UPDATED/CONFIRMATION`, trả `NEGOTIATING`;
   - phía kia khác `null` (bên kia xác nhận đồng thời và commit trước khi câu này được đánh giá lại) → ném `ConcurrencyConflictException`: rollback nhả khoá dòng `pairing` (xác nhận phía mình không được ghi) → retrier thử lại → bước 1 thấy phía kia đã xác nhận → bước 4. **Không** hoàn tất ngay trong lần thử đang giữ khoá `pairing`: khoá `match` sau đó ngược thứ tự `match` → `pairing` của các luồng khác (deadlock).
4. **Hoàn tất** (lần thử chưa giữ khoá dòng nào): `matchRepository.confirmPairing(matchId, ctx.matchVersion, interestedTeamId, proposedVenueId, proposedStartTime, proposedEndTime, cost…, now)` (CAS `match`; 0 dòng → `ConcurrencyConflictException`) → `finalizeConfirmation(matchId, pairingId, revision, now)` (trả id pairing chuyển `CONFIRMED`/`SUPERSEDED`; không chứa `pairingId` → `ConcurrencyConflictException`, rollback cả CAS `match`) → `costCustomItemRepository.copyPairingItemsToMatch(pairingId, matchId, now)` (khoản tự nhập của gói đã chốt thành khoản của kèo; luôn chạy, 0 dòng khi không có khoản) → `venueSelectionService.markSelected({proposedVenueId}, now)` → `MatchConfirmedEvent(matchId, acceptedRequestId = null, pairingId, authorTeamId, opponentTeamId, occurredAt)` → frame `PAIRING_UPDATED/CONFIRMATION` (`status = CONFIRMED`). Response + event + frame dựng từ `ctx` + `now`.

Nhánh race tốn một lần thử (rollback) → còn hai lần cho hoàn tất; hết lượt → `MATCH_014`.

Thắng race giữa hai pairing của cùng kèo = lần hoàn tất commit trước (CAS `match`); `final_confirmed_at` = `now` của lần thử thắng; pairing thua retry → `SUPERSEDED` → `PAIRING_005`. SRS ghi "timestamp xác nhận cuối sớm hơn thắng" → cần sửa (§11). Tình huống chi tiết: `Processing_Flow.md` §5.

### 4.5. Domain event (ADR-0011)

| Event | Payload | Phát khi | Người nghe dự kiến |
|---|---|---|---|
| `PairingCreatedEvent` (mới, `application/event/match`) | `pairingId, matchId, interestedTeamId, authorTeamId, occurredAt` | 2.1 | NOTIFICATION-001 (`PAIRING_NEW` cho đội trưởng đội tác giả) |
| `MatchConfirmedEvent` (MATCH-001, sửa) | + `pairingId` (null ở luồng A); `acceptedRequestId` null ở luồng B | 2.7 hoàn tất | NOTIFICATION-001 (`MATCH_CONFIRMED`; tự truy vấn pairing `SUPERSEDED` của kèo để báo "Pairing superseded" — AC-M05) |
| `MatchCancelledEvent`, `MatchExpiredEvent`, `ReliabilityScoreChangedEvent` (MATCH-001) | Không đổi | Huỷ / hết hạn kèo luồng B (MATCH-001) | NOTIFICATION-001 tự truy vấn pairing `SUPERSEDED` / `EXPIRED` theo `matchId` |

Không phát event cho: chốt phương án, sửa chi phí, một bên xác nhận, gửi / đọc tin (không thuộc 11 loại thông báo; cập nhật tức thời qua kênh realtime). Mỗi thao tác tối đa một event nghiệp vụ.

### 4.6. Kênh realtime (ADR-0014)

**Port (application):**
```java
public interface ChatRealtimeGateway {
    void publishAfterCommit(UUID pairingId, RealtimeFrame frame);   // MANDATORY: đăng ký TransactionSynchronization.afterCommit
}
public sealed interface RealtimeFrame {
    record MessageCreated(ChatMessageResponse message) implements RealtimeFrame {}
    record PairingUpdated(UUID pairingId, Pairing.Status status, int revision, OffsetDateTime authorConfirmedAt,
                          OffsetDateTime interestedConfirmedAt, OffsetDateTime confirmedAt, PairingChange change) implements RealtimeFrame {}
}
public enum PairingChange { PROPOSAL, COST, CONFIRMATION }
```
Đăng ký trong lần thử đang chạy → lần thử rollback không đẩy; lần thử commit đẩy đúng một lần. Dữ liệu frame dựng từ bộ nhớ (không đọc lại DB).

**Cài đặt (api/realtime):**
- `WebSocketConfig` (`@EnableWebSocketMessageBroker`): endpoint `/ws` + `.withSockJS()` `[cần xác nhận #1]`, `setAllowedOriginPatterns(props.allowedOriginPatterns())`; `enableSimpleBroker("/topic").setHeartbeatValue(10000, 10000).setTaskScheduler(wsHeartbeatScheduler).setSelectorHeaderName(null)`; **không** `setApplicationDestinationPrefixes` (client không `SEND`); `configureClientInboundChannel` gắn `StompAuthChannelInterceptor`.
  - `setSelectorHeaderName(null)`: tắt lọc theo header `selector` của simple broker (giá trị header là biểu thức SpEL do client gửi — tiền lệ CVE-2018-1270). Không có use case lọc frame phía server; tắt để bớt bề mặt tấn công. Có method này trên Spring Framework 7 hay không: kiểm cùng `[cần xác nhận #1]`.
- `StompAuthChannelInterceptor` (`ChannelInterceptor.preSend`):
  - `CONNECT`: đọc native header `Authorization` → `Bearer …` → `jwtDecoder.decode` (cùng bean + `AccessTokenTypeValidator` của USER-001) → `accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, authorities))`, lưu `exp` vào session attributes. Lỗi → `MessageDeliveryException` mang mã `AUTH_001` → frame `ERROR`.
  - `SUBSCRIBE`: destination khớp nguyên văn `^/topic/pairings/(UUID)$` (không wildcard — `/topic/**`, `/topic/pairings/*` → `ERROR`), khác → `ERROR`; `exp` ≤ now → `AUTH_001`; `chatService.requireSubscribable(pairingId, userId)` → `PAIRING_001`/`PAIRING_002`.
  - `SEND` → `ERROR`.
  - `StompSubProtocolErrorHandler` tuỳ biến: header `message` = mã lỗi, body `{code, message}`; **NEVER** đưa token / stacktrace vào frame.
- `StompChatRealtimeGateway implements ChatRealtimeGateway`: `afterCommit` → `SimpMessagingTemplate.convertAndSend("/topic/pairings/" + id, json)` với `{type: MESSAGE_CREATED|PAIRING_UPDATED, …}` (`API_Spec.md` §1.6). Lỗi gửi → log `WARN` (không ném — đã commit).
- `WebSocketProperties` (`arenaflow.websocket.*`, §8).

### 4.7. Transaction trong ServiceImpl

| Method | Ranh giới |
|---|---|
| `listMatchPairings`, `listTeamPairings`, `getPairing`, `history`, `requireSubscribable` | `@Transactional(readOnly = true)` |
| `markRead` | `@Transactional` (một UPDATE, không CAS) |
| `createPairing`, `updateProposal`, `updateCostAgreement`, `confirm`, `send` | Không annotate; `concurrencyConflictRetrier.execute(MATCH_014, …)` trả `MatchWriteOutcome` (MATCH-001 §4.7) |
| `ChatRealtimeGateway.publishAfterCommit`, `MatchExpiry.expireIfDue`, `VenueSelectionService.markSelected` | `MANDATORY` |

`send` không có CAS nhưng vẫn qua retrier: nhánh lazy-expiry cần commit-rồi-báo-lỗi (như MATCH-001 2.9). `ConcurrencyConflictException` (mới hoặc dùng lại loại retrier đã bắt — developer kiểm `ConcurrencyConflictRetrier` hiện hành, `reuse-scan`) đánh dấu "điều kiện CAS không khớp, thử lại".

### 4.8. Sửa thành phần của MATCH-001 / VENUE-001 (thiết kế ở tài liệu gốc)

| Thành phần | Sửa | Nơi thiết kế |
|---|---|---|
| `MatchService.createMatch` + `@ValidMatchSchedule` | Nhánh `NEED_VENUE` (lựa chọn địa điểm/giờ) | MATCH-001 CCD §4.1, §7; PF §3.1 |
| Tìm / xem / kèo của đội | Kèo `NEED_VENUE`, `venueOptions`, `timeOptions`, cọc | MATCH-001 CCD §5.2, §5.3; PF §3.2, §3.3, §3.11 |
| `MatchExpiry`, job hết hạn | + pairing `NEGOTIATING` → `EXPIRED` | MATCH-001 CCD §4.3, §4.6 |
| `cancelMatch`, `MatchCancellation` | Luồng B: pairing → `SUPERSEDED`; không `withdrawAccepted` | MATCH-001 CCD §4.1, §4.13; PF §3.5 |
| `MatchConfirmedEvent` | + `pairingId` | MATCH-001 CCD §4.9 |
| `TeamGuard.requireActiveCaptain` | `void` → trả `TeamCaptainContextView` (dữ liệu đã đọc trong cùng câu, cho response 2.1) | MATCH-001 CCD §4.5 (v2.1) |
| `TeamCaptainContextView`, `MatchAvailabilityView` | + tên / ảnh / điểm đội; + tên đội trưởng hiện tại + cờ đã xoá (response 2.1 `captainName`) | MATCH-001 CCD §5.2, §5.3 (v2.1) |
| `MatchRepository` | + `confirmPairing` (CAS hoàn tất kèo B) | §5.2 tài liệu này |
| `VenueSelectionService.requireSelectable` | Trả `List<SelectableVenueView>` (thông tin hiển thị) | VENUE-001 CCD §4.6 (v1.2) |

## 5. Repository & Data Model

### 5.1. Migration `V7__match_flow_b_pairing.sql`

```sql
-- match: luồng B ghi venue_id = địa điểm đã chốt khi ghép; cọc chỉ ở luồng B; bỏ 2 cột chốt của V1 (không dùng)
ALTER TABLE match DROP CONSTRAINT match_check2;
ALTER TABLE match ADD CONSTRAINT match_type_venue_check CHECK (
    (type = 'HAS_VENUE' AND venue_id IS NOT NULL)
    OR (type = 'NEED_VENUE' AND (venue_id IS NULL) = (confirmed_opponent_team_id IS NULL)));
ALTER TABLE match DROP COLUMN confirmed_venue_id;
ALTER TABLE match DROP COLUMN confirmed_match_time;
ALTER TABLE match ADD COLUMN cost_deposit_amount BIGINT;
ALTER TABLE match ADD CONSTRAINT match_deposit_check CHECK (
    (cost_deposit_amount IS NULL OR cost_deposit_amount > 0)
    AND (cost_deposit_amount IS NOT NULL OR cost_deposit_holder IS NULL)
    AND (type = 'NEED_VENUE' OR cost_deposit_amount IS NULL));
DROP INDEX idx_match_search_gps;   -- search_lat/lng không dùng (kèo B tìm theo lựa chọn địa điểm)

-- match_venue_option: thứ tự nhập, không trùng địa điểm; đích FK của phương án
ALTER TABLE match_venue_option ADD COLUMN display_order INTEGER;
UPDATE match_venue_option o SET display_order = r.rn
    FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY match_id ORDER BY created_at, id) AS rn FROM match_venue_option) r
    WHERE o.id = r.id;
ALTER TABLE match_venue_option ALTER COLUMN display_order SET NOT NULL;
ALTER TABLE match_venue_option ADD CONSTRAINT match_venue_option_order_check CHECK (display_order BETWEEN 1 AND 5);
ALTER TABLE match_venue_option ADD CONSTRAINT match_venue_option_match_venue_key UNIQUE (match_id, venue_id);
ALTER TABLE match_venue_option ADD CONSTRAINT match_venue_option_match_order_key UNIQUE (match_id, display_order);
DROP INDEX idx_match_venue_option_match;
CREATE INDEX idx_match_venue_option_venue ON match_venue_option(venue_id);

-- match_time_option: khoảng hợp lệ, không trùng giờ bắt đầu; đích FK của phương án
ALTER TABLE match_time_option ADD CONSTRAINT match_time_option_range_check CHECK (proposed_end_time > proposed_start_time);
ALTER TABLE match_time_option ADD CONSTRAINT match_time_option_match_id_key UNIQUE (match_id, id);
ALTER TABLE match_time_option ADD CONSTRAINT match_time_option_match_start_key UNIQUE (match_id, proposed_start_time);
DROP INDEX idx_match_time_option_match;

-- pairing: gói thương lượng (phương án + chi phí + cọc), revision, updated_at
ALTER TABLE pairing ADD COLUMN revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE pairing ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE pairing ADD COLUMN proposed_venue_id UUID;
ALTER TABLE pairing ADD COLUMN proposed_time_option_id UUID;
ALTER TABLE pairing ADD COLUMN cost_type VARCHAR(50);
ALTER TABLE pairing ADD COLUMN cost_split VARCHAR(50);
ALTER TABLE pairing ADD COLUMN cost_venue_amount BIGINT;
ALTER TABLE pairing ADD COLUMN cost_drinks_amount BIGINT;
ALTER TABLE pairing ADD COLUMN cost_deposit_amount BIGINT;
ALTER TABLE pairing ADD COLUMN cost_deposit_holder VARCHAR(100);
ALTER TABLE pairing ADD CONSTRAINT pairing_proposed_venue_fkey
    FOREIGN KEY (match_id, proposed_venue_id) REFERENCES match_venue_option(match_id, venue_id);
ALTER TABLE pairing ADD CONSTRAINT pairing_proposed_time_fkey
    FOREIGN KEY (match_id, proposed_time_option_id) REFERENCES match_time_option(match_id, id);
ALTER TABLE pairing ADD CONSTRAINT pairing_proposal_pair_check
    CHECK ((proposed_venue_id IS NULL) = (proposed_time_option_id IS NULL));
ALTER TABLE pairing ADD CONSTRAINT pairing_confirmed_proposal_check
    CHECK (status <> 'CONFIRMED' OR proposed_venue_id IS NOT NULL);
ALTER TABLE pairing ADD CONSTRAINT pairing_revision_check CHECK (revision >= 0);
ALTER TABLE pairing ADD CONSTRAINT pairing_cost_type_check
    CHECK (cost_type IS NULL OR cost_type IN ('VENUE_ONLY', 'DRINKS_ONLY', 'BOTH'));
ALTER TABLE pairing ADD CONSTRAINT pairing_cost_split_check
    CHECK (cost_split IS NULL OR cost_split IN ('FIFTY_FIFTY', 'SEVENTY_THIRTY_HOME', 'SEVENTY_THIRTY_LOSER', 'LOSER_PAYS_DRINKS'));
ALTER TABLE pairing ADD CONSTRAINT pairing_cost_amount_check CHECK (
    (cost_venue_amount IS NULL OR cost_venue_amount > 0) AND (cost_drinks_amount IS NULL OR cost_drinks_amount > 0)
    AND (cost_deposit_amount IS NULL OR cost_deposit_amount > 0)
    AND (cost_deposit_amount IS NOT NULL OR cost_deposit_holder IS NULL));
DROP INDEX idx_pairing_race;
CREATE UNIQUE INDEX idx_pairing_confirmed ON pairing(match_id) WHERE status = 'CONFIRMED';

-- cost_custom_item: thuộc kèo (luồng A) hoặc thuộc pairing (luồng B)
ALTER TABLE cost_custom_item ALTER COLUMN match_id DROP NOT NULL;
ALTER TABLE cost_custom_item ADD COLUMN pairing_id UUID;
ALTER TABLE cost_custom_item ADD CONSTRAINT cost_custom_item_pairing_fkey
    FOREIGN KEY (pairing_id) REFERENCES pairing(id) ON DELETE CASCADE;
ALTER TABLE cost_custom_item ADD CONSTRAINT cost_custom_item_owner_check CHECK ((match_id IS NULL) <> (pairing_id IS NULL));
CREATE UNIQUE INDEX idx_cost_custom_item_pairing_order ON cost_custom_item(pairing_id, display_order);

-- chat_message: người gửi không mất khi xoá user (NOT NULL), phía gửi, trạng thái đọc, độ dài; index phân trang theo (created_at, id)
ALTER TABLE chat_message DROP CONSTRAINT chat_message_sender_user_id_fkey;
ALTER TABLE chat_message ADD CONSTRAINT chat_message_sender_user_id_fkey
    FOREIGN KEY (sender_user_id) REFERENCES "user"(id) ON DELETE RESTRICT;
ALTER TABLE chat_message ADD COLUMN sender_team_id UUID NOT NULL;
ALTER TABLE chat_message ADD CONSTRAINT chat_message_sender_team_fkey
    FOREIGN KEY (sender_team_id) REFERENCES team(id) ON DELETE RESTRICT;
ALTER TABLE chat_message ADD COLUMN is_read BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_message ADD CONSTRAINT chat_message_length_check CHECK (char_length(message) BETWEEN 1 AND 1000);
DROP INDEX idx_chat_message_pairing;
CREATE INDEX idx_chat_message_pairing_time ON chat_message(pairing_id, created_at, id);
CREATE INDEX idx_chat_message_unread ON chat_message(pairing_id, sender_team_id) WHERE NOT is_read;
```
- **Đổi so với `docs/database/` và thiết kế cũ:** bỏ `match.confirmed_venue_id`, `match.confirmed_match_time` (luồng B ghi thẳng `venue_id`, `match_start_time`, `match_end_time` khi ghép → mọi truy vấn MATCH-001/MERCENARY-001 đọc một nguồn); nới CHECK V1 `match_check2`; `cost_custom_item.match_id` nullable (sửa ràng buộc V1/V6 của MATCH-001); không dùng JSONB `location_option_ids`/`time_options` (Overview "Implementation notes") mà giữ bảng V1 (FK, unique, FK phương án); `search_lat/lng/radius_km` giữ cột nhưng luôn `null`.
- `match_check1` (`expires_at <= match_start_time`) giữ: kèo B lưu `match_start_time`/`match_end_time` = khung giờ sớm nhất khi tạo → CHECK này thực thi luôn BR-M03; khi ghép đổi sang khung đã chốt (≥ khung sớm nhất ≥ `expires_at`).
- FK phương án hai cột (`MATCH SIMPLE`): `NULL` không kiểm; có giá trị thì DB bảo đảm địa điểm/khung giờ thuộc **đúng** kèo của pairing — lớp chặn cuối sau kiểm `PAIRING_008` ở service.
- `idx_pairing_confirmed` thay `idx_pairing_race` (V1, cùng tiền tố `match_id`): bảo đảm tối đa một pairing `CONFIRMED` mỗi kèo (tương tự `idx_match_request_accepted` của V6).
- `NOT NULL` không default (`chat_message.sender_team_id`) và backfill `display_order`: Neon dev 0 dòng (kiểm 2026-11-10). Môi trường khác có dữ liệu `chat_message` → V7 lỗi → dừng, báo, không tự sửa dữ liệu.
- `pairing` không có cột `version`: CAS bằng `status` + `revision` trong `WHERE` (§5.2).
- `chat_message.sender_user_id`: V1 khai `NOT NULL` + `ON DELETE SET NULL` (mâu thuẫn — xoá cứng user sẽ lỗi NOT NULL thay vì giữ tin). Đổi `ON DELETE RESTRICT`: khớp `NOT NULL` và chính sách chỉ xoá mềm user (ADR-0002); tin giữ người gửi để hiện "Người dùng đã xóa". Giữ tên constraint V1 (Neon dev 2026-11-10). Không thêm index `sender_user_id`: RESTRICT chỉ kiểm khi `DELETE` user, không xảy ra. Cùng thay đổi cho `venue_report.reporter_id` và `venue.created_by_user_id` ở V5 (VENUE-001 §5.1).
- Tên index theo quy ước `idx_*`; constraint đặt tên tường minh để migration sau tham chiếu được (bài học `match_check2`).

### 5.2. Repository

Native + interface projection như MATCH-001 §5.2; `Page` native có `countQuery`. Trạng thái hiệu lực pairing trong SQL: `CASE WHEN p.status = 'NEGOTIATING' AND m.status = 'OPEN' AND m.expires_at <= :now THEN 'EXPIRED' ELSE p.status END`.

```java
public interface PairingRepository extends JpaRepository<Pairing, UUID> {
    Optional<PairingContextView> findContext(UUID pairingId, UUID callerId);       // pairing ⋈ match ⋈ 2 team ⋈ sport ⋈ time option + venue đã chốt ⋈ user caller
    Optional<PairingAccessView> findAccess(UUID pairingId, UUID callerId);         // id + caller là captain ACTIVE của đội nào
    Optional<PairingDetailView> findDetailById(UUID pairingId, UUID callerId, OffsetDateTime now); // + đội trưởng hiện tại 2 đội, cờ quyền caller
    Page<PairingSummaryView> findByMatch(UUID matchId, String status /* null = ALL */, OffsetDateTime now, Pageable p);
    Page<PairingSummaryView> findByTeam(UUID teamId, String role /* null = ALL */, String status, OffsetDateTime now, Pageable p);
    Optional<ProposalCandidateView> findProposalCandidate(UUID matchId, UUID venueId, UUID timeOptionId);
    @Modifying int updateProposal(UUID id, UUID venueId, UUID timeOptionId, int expectedRevision, OffsetDateTime now);
    @Modifying int updateCost(UUID id, String costType, String costSplit, Long venueAmount, Long drinksAmount,
                              Long depositAmount, String depositHolder, int expectedRevision, OffsetDateTime now);
    Optional<ConfirmSideResult> confirmSide(UUID id, String side, int expectedRevision, OffsetDateTime now); // UPDATE … RETURNING
    List<UUID> finalizeConfirmation(UUID matchId, UUID pairingId, int expectedRevision, OffsetDateTime now); // UPDATE … RETURNING id
    @Modifying int supersedeNegotiating(UUID matchId, OffsetDateTime now);            // huỷ kèo OPEN
    @Modifying int expireNegotiatingByMatchIds(Collection<UUID> matchIds, OffsetDateTime now); // MatchExpiry, job
}
// MatchRepository (sửa — MATCH-001 §5.2)
@Modifying int confirmPairing(UUID matchId, long expectedVersion, UUID opponentTeamId, UUID venueId,
                              OffsetDateTime startTime, OffsetDateTime endTime, String costType, String costSplit,
                              Long venueAmount, Long drinksAmount, Long depositAmount, String depositHolder, OffsetDateTime now);
public interface MatchVenueOptionRepository extends JpaRepository<MatchVenueOption, UUID> {
    @Modifying int insertAll(UUID matchId, UUID[] venueIds, OffsetDateTime now);       // 1 câu unnest WITH ORDINALITY, id uuidv7()
    List<MatchOptionRowView> findOptionRows(UUID matchId);                              // UNION ALL lựa chọn địa điểm + khung giờ
}
public interface MatchTimeOptionRepository extends JpaRepository<MatchTimeOption, UUID> {
    List<TimeOptionView> insertAllReturning(UUID matchId, OffsetDateTime[] starts, OffsetDateTime[] ends, OffsetDateTime now);
                                                                                          // 1 câu INSERT … SELECT unnest … RETURNING
}
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    Optional<ChatCursorView> findCursor(UUID pairingId, UUID messageId);               // created_at, id
    List<ChatMessageView> findLatest(UUID pairingId, int limit);
    List<ChatMessageView> findBefore(UUID pairingId, OffsetDateTime createdAt, UUID id, int limit);
    List<ChatMessageView> findAfter(UUID pairingId, OffsetDateTime createdAt, UUID id, int limit);
    @Modifying int markOtherSideRead(UUID pairingId, UUID readerTeamId);
}
// CostCustomItemRepository (sửa)
List<CostCustomItem> findByPairingIdOrderByDisplayOrderAsc(UUID pairingId);
@Modifying int deleteByPairingIdBulk(UUID pairingId);
@Modifying int insertAllForPairing(UUID pairingId, String[] names, Long[] amounts, OffsetDateTime now); // như insertAll, cột pairing_id
@Modifying int copyPairingItemsToMatch(UUID pairingId, UUID matchId, OffsetDateTime now); // 2.7 hoàn tất: 1 câu INSERT … SELECT
```

Ghi chú SQL:
- `findContext`: `SELECT p.*, m.author_team_id, m.status AS match_status, m.expires_at, m.type, m.sport_id, m.version AS match_version, ta.status AS author_team_status, ti.status AS interested_team_status, s.is_active AS sport_active, mto.proposed_start_time, mto.proposed_end_time, pv.status AS proposed_venue_status, cu.display_name AS caller_name, cu.avatar_url AS caller_avatar_url, (cu.deleted_at IS NOT NULL) AS caller_deleted, EXISTS(captain ACTIVE = :callerId của ta) AS caller_author_captain, EXISTS(… của ti) AS caller_interested_captain FROM pairing p JOIN match m … JOIN team ta … JOIN team ti … JOIN sport s … LEFT JOIN match_time_option mto ON mto.id = p.proposed_time_option_id LEFT JOIN venue pv ON pv.id = p.proposed_venue_id LEFT JOIN "user" cu ON cu.id = :callerId WHERE p.id = :pairingId`. Một câu, dùng cho 2.5–2.8, 2.10 (`match_version`, `proposed_venue_status`: 2.7; cột `caller_*`: response/frame 2.8). Join thêm theo khoá chính, không đổi số câu.
- `confirmPairing`: `UPDATE match SET status = 'CONFIRMED', confirmed_opponent_team_id = :opponentTeamId, venue_id = :venueId, match_start_time = :startTime, match_end_time = :endTime, cost_type, cost_split, cost_venue_amount, cost_drinks_amount, cost_deposit_amount, cost_deposit_holder = :…, version = version + 1, updated_at = :now WHERE id = :matchId AND status = 'OPEN' AND version = :expectedVersion`. 0 dòng → `ConcurrencyConflictException`. `version` lấy từ `findContext` (cùng lần thử) — không nạp entity `Match`, không có bản `Match` managed nào bị ghi đè lúc flush. Theo quy tắc bulk `match` của MATCH-001 §5.2 (`version + 1`, `updated_at`).
- `updateProposal`: `SET proposed_venue_id, proposed_time_option_id, author_confirmed_at = NULL, interested_confirmed_at = NULL, revision = revision + 1, updated_at = :now WHERE id = :id AND status = 'NEGOTIATING' AND revision = :expectedRevision`. `updateCost` tương tự với cột chi phí.
- `confirmSide`: `UPDATE pairing SET <author|interested>_confirmed_at = :now, updated_at = :now WHERE id = :id AND status = 'NEGOTIATING' AND revision = :expectedRevision AND <cột> IS NULL RETURNING author_confirmed_at, interested_confirmed_at` — `side` chọn một trong hai câu cố định (hai method hoặc `CASE`), **không** nối chuỗi tên cột từ input. Khai báo native trả projection, không `@Modifying` (như VENUE-001 `markStaleAlerted` — `[cần xác nhận #11]` VENUE-001). READ COMMITTED: câu chờ khoá dòng rồi đánh giá lại điều kiện → `RETURNING` thấy xác nhận của bên kia nếu bên kia commit trước.
- `finalizeConfirmation`: `UPDATE pairing SET status = CASE WHEN id = :pairingId THEN 'CONFIRMED' ELSE 'SUPERSEDED' END, author_confirmed_at = CASE WHEN id = :pairingId THEN COALESCE(author_confirmed_at, :now) ELSE author_confirmed_at END, interested_confirmed_at = (tương tự), final_confirmed_at = CASE WHEN id = :pairingId THEN :now END, updated_at = :now WHERE match_id = :matchId AND status = 'NEGOTIATING' AND (id <> :pairingId OR revision = :expectedRevision) RETURNING id` → service kiểm danh sách chứa `pairingId` (dòng đó luôn thành `CONFIRMED` theo `CASE`). Một câu cho xác nhận + thay thế. Chạy sau `confirmPairing` (khoá `match` trước `pairing`).
- `copyPairingItemsToMatch`: `INSERT INTO cost_custom_item (id, match_id, item_name, amount, display_order, created_at) SELECT uuidv7(), :matchId, item_name, amount, display_order, :now FROM cost_custom_item WHERE pairing_id = :pairingId` (id `uuidv7()` như `insertAll`, ADR-0008 Sửa đổi 2026-11-09; dòng mới `pairing_id = NULL` → thoả `cost_custom_item_owner_check`; giữ `display_order` → `idx_cost_custom_item_order (match_id, display_order)` không trùng vì kèo B không có khoản riêng — MATCH-001 2.4 trả `MATCH_016`). Chạy sau `finalizeConfirmation`: đang giữ khoá dòng `pairing` (`CONFIRMED`) → không lần sửa chi phí (2.6) nào đổi được khoản của pairing giữa lúc chốt `revision` và lúc chép; câu đọc theo `idx_cost_custom_item_pairing_order`. Khoá: chỉ dòng mới + `FOR KEY SHARE` trên `match` (đã giữ khoá mạnh hơn) — không đổi thứ tự `match` → `pairing` → `venue`. Khoản của pairing giữ nguyên (2.4 vẫn đọc theo pairing).
- `supersedeNegotiating` / `expireNegotiatingByMatchIds`: `WHERE match_id … AND status = 'NEGOTIATING'`, `updated_at = :now`.
- `findDetailById`: pairing ⋈ match (`expires_at`, trạng thái hiệu lực) ⋈ 2 team (`name`, `avatar_url`, `reliability_score`) + mỗi đội `LEFT JOIN team_member c* ON c*.team_id = t*.id AND c*.role = 'CAPTAIN' AND c*.status = 'ACTIVE' LEFT JOIN "user" u* ON u*.id = c*.user_id` → `*CaptainName` (`u*.display_name`), `*CaptainDeleted` (`u*.deleted_at IS NOT NULL`); `callerAuthorCaptain = COALESCE(ca.user_id = :callerId, false)`, `callerInterestedCaptain = COALESCE(ci.user_id = :callerId, false)` (dùng lại join đội trưởng, không `EXISTS` riêng; `COALESCE` vì đội đã giải tán không có đội trưởng → so sánh ra `NULL`, map vào `boolean` sẽ lỗi) + phương án (`LEFT JOIN match_time_option`, `LEFT JOIN venue`). Một đội trưởng ACTIVE mỗi đội: bất biến của USER-001 (như `TeamRepository.findDetail`); đội đã giải tán → join `null` → `captainName = null`.
- `findByTeam`: `role = INTERESTED` → `p.interested_team_id = :teamId` (`idx_pairing_team`); `AUTHOR` → `m.author_team_id = :teamId` (`idx_match_author_status` rồi `idx_pairing_match`); `ALL` → `OR` hai điều kiện. Lọc `status` trên biểu thức trạng thái hiệu lực.
- `findProposalCandidate`: `SELECT v.id, v.name, v.address, v.status, t.id, t.proposed_start_time, t.proposed_end_time FROM match_venue_option o JOIN venue v ON v.id = o.venue_id JOIN match_time_option t ON t.match_id = o.match_id AND t.id = :timeOptionId WHERE o.match_id = :matchId AND o.venue_id = :venueId`. Không có dòng → `PAIRING_008`.
- `insertAll` (lựa chọn địa điểm): `INSERT INTO match_venue_option (id, match_id, venue_id, display_order, created_at) SELECT uuidv7(), :matchId, t.venue_id, t.ord, :now FROM unnest(CAST(:venueIds AS uuid[])) WITH ORDINALITY AS t(venue_id, ord)` (ADR-0008 Sửa đổi 2026-11-09).
- `insertAllReturning` (khung giờ): `INSERT INTO match_time_option (id, match_id, proposed_start_time, proposed_end_time, created_at) SELECT uuidv7(), :matchId, t.s, t.e, :now FROM unnest(CAST(:starts AS timestamptz[]), CAST(:ends AS timestamptz[])) AS t(s, e) RETURNING id, proposed_start_time, proposed_end_time` — native trả projection, không `@Modifying` (cùng `[cần xác nhận #11]` VENUE-001); id do DB sinh nên cần `RETURNING` để response 2.1 có `timeOptionId` mà không đọc lại. Bind mảng `UUID[]`/`OffsetDateTime[]`: `[cần xác nhận #2]`.
- `findOptionRows`: `SELECT 'VENUE' AS kind, o.display_order, v.id, v.name, v.address, v.latitude, v.longitude, v.status, img.url, NULL, NULL, NULL FROM match_venue_option o JOIN venue v … LEFT JOIN venue_image img (ảnh chính) WHERE o.match_id = :m UNION ALL SELECT 'TIME', NULL, …, t.id, t.proposed_start_time, t.proposed_end_time FROM match_time_option t WHERE t.match_id = :m` — mapper tách theo `kind`, sắp `display_order` / `proposed_start_time`.
- Chat cursor theo **`(created_at, id)`**, không theo `id` riêng: id là UUID v7 (`ChatMessage` `@UuidGenerator(VERSION_7)`) chỉ tăng chặt trong một JVM, gần đúng ở mức mili-giây giữa instance/đồng hồ (ADR-0008 Hệ quả: "cần thứ tự thời gian → dùng cột `*_at`"). `findBefore`: `WHERE pairing_id = :p AND (created_at, id) < (:createdAt, :id) ORDER BY created_at DESC, id DESC LIMIT :limit`; `findAfter`: `> … ORDER BY created_at ASC, id ASC`; `findLatest`: `ORDER BY created_at DESC, id DESC`. Index `(pairing_id, created_at, id)` phục vụ cả ba (row comparison). Cả ba `JOIN "user" su ON su.id = c.sender_user_id` → `senderName` (`su.display_name`), `senderAvatarUrl`, `senderDeleted` (`su.deleted_at IS NOT NULL`) — join theo khoá chính trong cùng câu, không N+1; `JOIN` (không `LEFT`) vì FK `RESTRICT` + `NOT NULL`. `created_at` gán ở `@PrePersist` (`OffsetDateTime.now()`), không phải `DEFAULT NOW()` của DB.
- `markOtherSideRead`: `UPDATE chat_message SET is_read = TRUE WHERE pairing_id = :p AND sender_team_id <> :readerTeamId AND NOT is_read` (`idx_chat_message_unread`).

### 5.3. Projection

| Projection | Cột |
|---|---|
| `PairingContextView` | pairing (`id`, `matchId`, `interestedTeamId`, `status`, `revision`, `authorConfirmedAt`, `interestedConfirmedAt`, `proposedVenueId`, `proposedTimeOptionId`, cột chi phí/cọc) + match (`authorTeamId`, `status`, `expiresAt`, `type`, `matchVersion` (`Long`)) + `authorTeamStatus`, `interestedTeamStatus`, `sportActive` + `proposedStartTime`, `proposedEndTime`, `proposedVenueStatus` + `callerName`, `callerAvatarUrl`, `callerDeleted` + `callerAuthorCaptain`, `callerInterestedCaptain` |
| `PairingAccessView` | `id`, `callerAuthorCaptain`, `callerInterestedCaptain` |
| `PairingDetailView` | cột `PairingResponse` (gồm `effectiveStatus`, `matchExpiresAt`) + 2 đội (`id`, `name`, `avatarUrl`, `reliabilityScore`, `captainName`, `captainDeleted`) + phương án (`venueName`, `address`, `venueStatus`, giờ) + `callerAuthorCaptain`, `callerInterestedCaptain` |
| `PairingSummaryView` | cột `PairingSummaryResponse` (gồm `effectiveStatus`, `matchStartTime`, `matchExpiresAt`) |
| `ProposalCandidateView` | `venueId`, `venueName`, `address`, `venueStatus`, `timeOptionId`, `startsAt`, `endsAt` |
| `ConfirmSideResult` | `authorConfirmedAt`, `interestedConfirmedAt` |
| `TimeOptionView` | `id`, `proposedStartTime`, `proposedEndTime` |
| `MatchOptionRowView` | `kind`, `displayOrder`, cột địa điểm (như `MatchDetailView` venue), `timeOptionId`, `startsAt`, `endsAt` |
| `ChatMessageView` | `id`, `pairingId`, `senderTeamId`, `senderUserId`, `senderName`, `senderAvatarUrl`, `senderDeleted`, `message`, `isRead`, `createdAt` |
| `ChatCursorView` | `createdAt`, `id` |

Thời gian là `Instant`, mapper chuyển UTC (USER-001 §5).

## 6. Entity

| Entity | Thay đổi |
|---|---|
| `match.Pairing` | + `revision` (`int`, `@Builder.Default 0`), `updatedAt` (`@PrePersist`/`@PreUpdate`), `proposedVenueId`, `proposedTimeOptionId` (`UUID`), `costType` (`Match.CostType`), `costSplit` (`Match.CostSplit`), `costVenueAmount`, `costDrinksAmount`, `costDepositAmount` (`Long`), `costDepositHolder` (`length = 100`). **Không** `@Version`: entity chỉ INSERT qua JPA, mọi đổi trạng thái qua native CAS (§5.2); sửa Javadoc lớp nêu điều này |
| `match.Match` | Bỏ `confirmedVenueId`, `confirmedMatchTime`; + `costDepositAmount` (`Long`); `costDepositHolder` giữ (`length = 100`) |
| `match.MatchVenueOption` | + `displayOrder` (`Integer`, `nullable = false`) — ghi bằng native `insertAll`, entity để đọc / `ddl-auto=validate` |
| `match.MatchTimeOption` | Giữ |
| `match.CostCustomItem` | `matchId` `nullable = true`; + `pairingId` (`UUID`) |
| `chat.ChatMessage` | + `senderTeamId` (`UUID`, `nullable = false`), `isRead` (`boolean`, `@Builder.Default false`); `message` giữ `TEXT` |

Giữ Lombok/builder/`@PrePersist` (`convention-backend.md` §6, §6.1); giữ `@UuidGenerator(VERSION_7)` (ADR-0008, `EntityIdGenerationTest`). Kiểm bằng migration integration test (`ddl-auto=validate` sau V7).

## 7. DTO & Mapper

- DTO là `record`; chuỗi `trim()` ở service; `""` ở field tuỳ chọn → `null` (MATCH-001 §7).
- `CreatePairingRequest(@NotNull UUID teamId)`; `UpdateProposalRequest(@NotNull teamId, @NotNull @PositiveOrZero Integer revision, @NotNull venueId, @NotNull timeOptionId)`; `ConfirmPairingRequest(@NotNull teamId, @NotNull @PositiveOrZero revision)`; `SendChatMessageRequest(@NotNull teamId, @NotBlank @Size(max = 1000) content)` (đếm sau `trim` — validator kiểm trên giá trị đã trim hoặc service ném `VALIDATION_ERROR` field `content` khi rỗng sau trim; developer chọn một nơi); `MarkChatReadRequest(@NotNull teamId)`.
- `UpdatePairingCostAgreementRequest`: cùng field và ràng buộc `UpdateCostAgreementRequest` (MATCH-001 2.4) + `teamId`, `revision`, `depositAmount` (`@Min(1) @Max(1_000_000_000)`), `depositHolder` (`@Size(max = 100)`). Hai record cài chung interface `CostAgreementFields` để dùng một `@ValidCostAgreement`; `@ValidDeposit` (mức lớp): `depositHolder` khác null mà `depositAmount` null → lỗi gắn `depositHolder`.
- Hằng nghiệp vụ một nơi (`PairingRules`): `MAX_MESSAGE_LENGTH = 1000`, `DEFAULT_HISTORY_LIMIT = 50`, `MAX_HISTORY_LIMIT = 100`, `MAX_DEPOSIT_HOLDER = 100`. Giới hạn lựa chọn kèo B (`MAX_VENUE_OPTIONS = 5`, `MAX_TIME_OPTIONS = 5`) ở `MatchRules` (MATCH-001 §7).
- `PairingMapper` (`unmappedTargetPolicy = ERROR`, `uses = MapperSupport.class`): `toResponse(PairingDetailView, List<CostCustomItem>)`, `toCreatedResponse(Pairing, Match, TeamCaptainContextView, MatchAvailabilityView)`, `toSummary(PairingSummaryView)`, `toProposalUpdate(…)`, `toCostUpdate(…)`, `toConfirmation(…)`. `captainName` = `MapperSupport.maskName(*CaptainName, *CaptainDeleted)`; `matchExpiresAt` ↔ `expiresAt` của kèo.
- `ChatMapper` (`uses = MapperSupport.class`): `toResponse(ChatMessage, PairingContextView)` (2.8 + frame — người gửi = cột `caller*` của ctx), `toResponse(ChatMessageView)` (2.9). `senderName` = `maskName(…, senderDeleted)`, `senderAvatarUrl` = `maskAvatar(…, senderDeleted)` — che ở mapper, SQL không che (USER-001 §7). Không thêm `senderTeamName`: FE ghép tên đội từ `authorTeam` / `interestedTeam` của 2.4 theo `senderTeamId` (`API_Spec.md` §4.4).
- Tên field API ↔ entity: `confirmedAt` ↔ `finalConfirmedAt`; `content` ↔ `message`; `interestedTeam` ↔ `interestedTeamId`; `proposal.venueId` ↔ `proposedVenueId`.

## 8. Cấu hình & Dependencies

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

Tên artifact với Boot 4.0.9: `[cần xác nhận #1]`.

| Tham số | Lý do giá trị |
|---|---|
| `allowed-origin-patterns` rỗng mặc định | Fail-closed như ADR-0004: chỉ same-origin; client native không gửi `Origin`. **NEVER** `*` (`security-backend.md` §6). Domain FE thật: `[cần xác nhận #3]` |
| Heartbeat 10s/10s | Phát hiện kết nối chết trong ~20s; scheduler riêng 1 luồng (`ThreadPoolTaskScheduler` bean `wsHeartbeatScheduler`), không chiếm `spring.task.scheduling.pool.size` của job (MATCH-001 §8) |

Không thêm cấu hình cho pairing/chat ngoài hằng ở §7.

## 9. Security

- `SecurityConfig` (USER-001 §9.1, sửa): `/ws/**` → `permitAll` (handshake HTTP; xác thực ở STOMP `CONNECT`); `BearerTokenResolver` bỏ qua `/ws/**` như `/api/v1/auth/**` (header Bearer cũ trên handshake không gây 401). Mọi endpoint REST mới nằm dưới `/api/v1/**` → `authenticated()`.
- Quyền đội trưởng kiểm trong service (`PairingGuard`, `TeamGuard`); không `@PreAuthorize` theo đội.
- Nội dung chat: không log nội dung tin, token, header `Authorization` (`security-backend.md` §5.3). Log lỗi STOMP chỉ mã lỗi + `pairingId`.
- Response / frame tin nhắn chứa thông tin hồ sơ công khai của người gửi (`senderUserId`, tên hiển thị, ảnh) — cùng mức thông tin đội trưởng ở chi tiết đội (USER-001); không email / số điện thoại. Người đã xoá tài khoản → che theo ADR-0002. Chỉ hai đội trưởng của thương lượng đọc được (`PAIRING_002`).
- XSS: lưu nguyên văn (đã trim), FE escape khi hiển thị (`security-backend.md` §4.2).
- Rủi ro đã chấp nhận ở ADR-0014: quyền subscribe không bị thu hồi khi chuyển quyền đội trưởng / token hết hạn cho tới khi ngắt kết nối.

## 10. Lệch convention / chuẩn có chủ đích

| Quy ước | Thiết kế | Lý do | Trạng thái |
|---|---|---|---|
| `api-convention.md` §7: chỉ `page`/`pageSize` | 2.9 cursor `before`/`after`/`limit`, `{items, hasMore}` | Danh sách chat thêm tin liên tục → offset trôi; user chốt 2026-11-10 | Coordinator thêm quy ước cursor vào `api-convention.md` (nhánh chore) |
| ADR-0003: khoá lạc quan `@Version` | `Pairing` CAS bằng `status` + `revision` trong `UPDATE … WHERE` | Mọi đổi trạng thái là native bulk; `revision` cũng là hợp đồng FE (§1.4 API_Spec) — tiền lệ `MatchRequest` (MATCH-001) | Ghi trong ADR-0003 "Sửa đổi 2026-11-10" |
| ADR-0011 quy tắc 3: side effect qua event | Frame realtime qua port `afterCommit`, không qua event | Frame cần nội dung tin (event không mang dữ liệu người dùng); xem ADR-0014 | ADR-0014 `Proposed` |
| `non-functional-standards.md` §4.1: `saveAndFlush` chỉ khi cần | 2.1 `saveAndFlush` pairing | Đổi vi phạm `idx_pairing_active` thành `PAIRING_003` (ngoại lệ 1) | Comment tại chỗ |

## 11. Tài liệu cần đồng bộ khi implement

Sau khi mọi phase code xong + test PASS (CLAUDE.md §8):

| Tài liệu | Nội dung | Nhánh |
|---|---|---|
| `docs/database/schema.dbml`, `schema-design.md` §3.1 (bỏ `confirmed_venue_id`, `confirmed_match_time`; `cost_deposit_amount`; CHECK `match_type_venue_check`), §3.2, §3.3, §3.4 (`cost_custom_item.pairing_id`), §3.6 (cột pairing, `idx_pairing_confirmed`), §7.1 (`sender_team_id`, `is_read`, index) | Cột/CHECK/index V7 | `feature/MATCH-002-*` |
| `docs/database/data-model.md` §3.3 dòng 81 ("MVP chỉ lưu tin nhắn chat; không có cấu trúc đề xuất riêng") → phương án chốt trên pairing; dòng 90 ("… hoặc `CANCELLED` (nếu Match `CANCELLED`)") → `SUPERSEDED`; §3.5 dòng 181 (cọc: số tiền + người giữ, theo thương lượng) | Khớp quyết định 2026-11-10 | `feature/MATCH-002-*` |
| `docs/agent/api-convention.md` §5 (2xx 10 endpoint), §6 (domain `PAIRING`, `CHAT`, `MATCH_016`), §7 (cursor) | Coordinator | `chore/agent-framework-*` |
| `docs/agent/convention-backend.md` §1 | `api/realtime/`, `application/support/chat/` | `chore/agent-framework-*` |
| `docs/agent/design/USER-001/Component_Class_Design.md` §9.1 | `/ws/**` `permitAll` + resolver bỏ qua (+ `USER-001/CHANGELOG.md` MINOR, nội bộ) | `feature/MATCH-002-*` |
| `docs/agent/CLAUDE.md` §2 | Dòng điều hướng MATCH-002 | `chore/agent-framework-*` |
| `docs/database/` (`schema-design.md`, `schema.dbml`) | FK `chat_message.sender_user_id`, `venue_report.reporter_id`, `venue.created_by_user_id` → `ON DELETE RESTRICT` | `feature/MATCH-002-*` (cùng V7) / `feature/VENUE-001-*` (V5) |
| SRS `SYSTEM-001` | Bảng dưới — coordinator giao business-analyst | Theo coordinator |

**SRS `SYSTEM-001` cần sửa** (không sửa trong nhánh thiết kế này; dòng của file tại thời điểm 2026-11-10):

| # | Vị trí | Hiện tại | Sửa thành | Nguồn quyết định |
|---|---|---|---|---|
| 1 | `Use_Cases.md` UC-M07 5a1–5a2 (:465–466); `Acceptance_Criteria.md` AC-M05 (:223), checklist (:40), Scenario (:647); `Business_Rules.md` §2.3 (:165, :167); `Data_Model_Conceptual.md` (:234); `Overview.md` (:74) | "Pairing có timestamp xác nhận cuối sớm hơn thắng" | "Thương lượng **hoàn tất trước** (lần xác nhận của bên thứ hai được ghi nhận trước) thắng; thời điểm hoàn tất = `confirmedAt` của thương lượng thắng; thương lượng kia → SUPERSEDED, lệnh xác nhận nhận `PAIRING_005`" | §4.4; review MAJOR #1 |
| 2 | `Data_Model_Conceptual.md` (:657) | Race condition Pairing `[cần xác nhận: optimistic lock / DB constraint]` | Chốt: CAS trên kèo + ràng buộc một Pairing CONFIRMED mỗi kèo | §4.4, §5.1 |
| 3 | `Use_Cases.md` UC-M07 Implementation note (:470) | "Optimistic Lock `@Version` trên Match … Frontend không gửi version" | FE gửi `revision` của gói khi xác nhận (lệch → `PAIRING_006`); kèo cập nhật bằng CAS trên version | Q7 |
| 4 | `Use_Cases.md` UC-M07 Preconditions + Main Flow 2–4 (:444–451); BR-M07 (`Business_Rules.md` :32) | "Hai bên đã thống nhất địa điểm/giờ qua chat"; xác nhận lưu timestamp | Pairing đã có **phương án chốt** (1 lựa chọn địa điểm + 1 khung giờ của kèo); xác nhận áp cho **gói** (phương án + chi phí + cọc) ở `revision` đang hiển thị; gói đổi → xoá xác nhận của cả hai bên; chưa có phương án → không xác nhận được | Q3, Q7 |
| 5 | `Use_Cases.md` UC-M07 Exception (mới); UC-M06 bước 4 | — | Địa điểm của phương án đã ngừng hoạt động → không chốt được phương án (2.5) và không xác nhận được (2.7) — `VENUE_002`; chốt lại địa điểm khác (nhất quán luồng A) | Review MAJOR #2 |
| 6 | `Use_Cases.md` UC-C01 Preconditions (:1062); `Acceptance_Criteria.md` (:531) | "Match đang ở trạng thái NEGOTIATING (luồng B)" | "Pairing đang NEGOTIATING (luồng B)" — hai đội trưởng của pairing đều sửa được | Q2 |
| 7 | `Use_Cases.md` UC-C01 bước 7–8 (:1072–1073) | Lưu "để hiển thị trên bảng tin Match"; "Đối phương xem và xác nhận thỏa thuận qua chat" | Luồng B: lưu trên Pairing; sửa → xoá xác nhận; đồng ý bằng nút Xác nhận (UC-M07), không qua chat; khi ghép: chi phí + cọc + khoản tự nhập chép sang Match (hiển thị trên bảng tin Match như luồng A) | Q2; quyết định 2026-11-11 (chép khoản tự nhập) |
| 8 | `Data_Model_Conceptual.md` (:338–340); `Use_Cases.md` UC-C01 bước 5 (:1070); `Overview.md` (:130) | "Thỏa thuận cọc (boolean, chỉ luồng B)"; "chia đôi tiền cọc" | Bỏ cờ boolean: số tiền cọc (`null` = không cọc, 1..1 000 000 000) + ai giữ (văn bản ≤ 100 ký tự, chỉ khi có số tiền); chỉ luồng B; không chia cọc | Q1 |
| 9 | `Business_Rules.md` BC-M04 (:353), sơ đồ + bảng trạng thái Pairing (:152, :165) | Match CANCELLED → Pairing NEGOTIATING "CANCELLED" | Match CANCELLED (khi OPEN) → Pairing NEGOTIATING → **SUPERSEDED** (Pairing không có trạng thái CANCELLED); không cần đóng pairing trước khi huỷ | Q5 |
| 10 | `Use_Cases.md` UC-N01 bước 2 (:1120); `Acceptance_Criteria.md` AC-N01 (:551); `Data_Model_Conceptual.md` Chat_Message (:240–252) | Lưu "(sender, receiver, content, timestamp)"; thuộc tính "Người nhận", "Match ID" | Lưu đội gửi + người thực sự gửi + nội dung (1..1000 ký tự) + thời điểm + trạng thái đã đọc; không lưu người nhận (= đội còn lại của Pairing); tin thuộc Pairing | Q12; thiết kế 2026-11-10 |
| 11 | `Use_Cases.md` UC-N01 (thêm bước hiển thị); `Acceptance_Criteria.md` AC-N01 (thêm Then) | — | Mỗi tin hiển thị **tên đội gửi + tên (và ảnh) người thực sự đã gửi**; tin cũ giữ người gửi lúc đó dù đội đã chuyển đội trưởng (vd đội Beta: tin của Nam vẫn "Beta · Nam" sau khi Lan làm đội trưởng); người gửi đã xoá tài khoản → "Người dùng đã xóa", không ảnh. Đầu phòng chat hiển thị tên hai đội + **đội trưởng hiện tại** của mỗi đội | Yêu cầu mới 2026-11-10; ADR-0002 |
| 12 | `Use_Cases.md` UC-N01 bước 3 + Implementation note (:1121, :1130); `Data_Model_Conceptual.md` (:258, :701); `Overview.md` (:211); `Acceptance_Criteria.md` (:821) | "WebSocket/polling"; "WebSocket + SockJS, fallback Long Polling" | Gửi / đọc lịch sử qua REST; nhận ngay qua STOMP/WebSocket (SockJS tuỳ `[cần xác nhận #1]`); lịch sử phân trang theo con trỏ để bù tin | Q6, Q8; ADR-0014 |
| 13 | `Data_Model_Conceptual.md` (:699); `Overview.md` (:210) | JSONB `location_option_ids`, `time_options` | Danh sách lựa chọn địa điểm (1–5) và khung giờ (1–5) của kèo B là tập riêng; phương án chốt tham chiếu một phần tử mỗi tập | Q3, Q9 |
| 14 | `Business_Rules.md` BC-V05 (:376) — "được chọn" | Không nói rõ luồng B | Luồng B: địa điểm "được chọn" = địa điểm của phương án đã chốt, tại lúc kèo ghép (không tính các lựa chọn lúc tạo kèo) | Q11 |
| 15 | `Use_Cases.md` UC-M05 Postconditions (:414–416) | — | Mỗi đội một Pairing cho một kèo; không có thao tác rút / kết thúc Pairing (chỉ kết thúc khi CONFIRMED / SUPERSEDED / EXPIRED) | Q4 |
| 16 | `Data_Model_Conceptual.md` Pairing attributes (:216–225) | "Đội tạo / Đội nhận đã xác nhận (boolean)", timestamp xác nhận hai đội, "Timestamp xác nhận cuối (max …, dùng để xử lý race condition)" | Bỏ hai cờ boolean (đã xác nhận = có thời điểm xác nhận); thêm phương án chốt (lựa chọn địa điểm + khung giờ), phiên bản gói (`revision`), thời điểm xác nhận từng đội (xoá khi gói đổi), thời điểm hoàn tất, thoả thuận chi phí + cọc + khoản tự nhập của Pairing, thời điểm cập nhật; bỏ "timestamp xác nhận cuối" làm tiêu chí race (row 1) | Q2, Q3, Q7; review MAJOR #1 |
| 17 | `Data_Model_Conceptual.md` (:236) | Implementation note "`@Version` trên Match … Frontend không gửi version" | Như row 3: FE gửi `revision` của gói; Pairing CAS bằng trạng thái + `revision`, Match CAS bằng version | Q7; ADR-0003 Sửa đổi 2026-11-10 |
| 18 | `Data_Model_Conceptual.md` (:23, :181, :331–344, :548, :602) | Cost_Agreement "Gắn với Match", "1 Match có 1 Cost_Agreement (0..1)", ERD `MATCH \|\|--o\| COST_AGREEMENT` | Luồng A: thuộc Match (như cũ). Luồng B: thuộc Pairing trong lúc thương lượng (mỗi Pairing 0..1); khi ghép được **chép** sang Match (Match đã ghép có 0..1 như luồng A). Cập nhật bảng thực thể, quan hệ, ERD | Q2; quyết định 2026-11-11 |

## 12. Danh sách [cần xác nhận]

Đánh số toàn thiết kế MATCH-002: 1–5 ở đây, 6–8 ở `Processing_Flow.md` §10.

| # | Nội dung | Loại |
|---|---|---|
| 1 | [Unverified] Spring Framework 7 / Boot 4.0.9 còn SockJS phía server và artifact `spring-boot-starter-websocket`: chưa kiểm (không có trong cache Maven local). Không còn SockJS → bỏ `.withSockJS()`, giữ WebSocket thuần (ADR-0014) | **Chặn code — bước đầu phase realtime** (thêm dependency, kiểm `.withSockJS()` và `setSelectorHeaderName(null)` trên Spring Boot 4.0.9 trước khi viết phần realtime). Không đạt → dừng, hỏi user; dự phòng đã ghi giữ nguyên |
| 2 | Bind mảng `UUID[]`, `OffsetDateTime[]` vào native `unnest` qua Spring Data / Hibernate 7.2 [Inference — chưa chạy]; dự phòng `NamedParameterJdbcTemplate` trong cùng transaction (như MATCH-001 `[cần xác nhận #1]`) | Không chặn |
| 3 | Domain FE cho `WS_ALLOWED_ORIGIN_PATTERNS` | Không chặn — cấu hình môi trường |
| 4 | Native `UPDATE … RETURNING` / `INSERT … RETURNING` khai báo không `@Modifying`, trả projection (cùng VENUE-001 `[cần xác nhận #11]`); không chạy được → `@Modifying` + đọc lại (2.1 MATCH-001 vượt W6 → dừng, báo; 2.7 hoàn tất thêm 1 câu đọc lại kết quả `finalizeConfirmation` → 6 = W6, chạm ngưỡng; 2.7 một phía 3) | **Chặn code — bước đầu phase 1** (integration test tối thiểu cho một câu `UPDATE … RETURNING` và một `INSERT … RETURNING` không `@Modifying` trước khi viết repository). Không chạy được → dừng, hỏi user (dự phòng ở cột Nội dung chưa được duyệt áp dụng) |
| 5 | Retrier hiện hành (`ConcurrencyConflictRetrier`) chỉ bắt `ObjectOptimisticLockingFailureException` và `PessimisticLockingFailureException` deadlock (`40P01`) — chưa bắt `ConcurrencyConflictException` mà thiết kế này ném khi CAS 0 dòng. Cần: tạo / dùng lại exception và cho retrier bắt (thay đổi thành phần dùng chung — `reuse-scan`, ADR-0003) | **Chặn code — bước đầu phase 1** (sửa retrier + test thử lại khi ném `ConcurrencyConflictException` trước mọi luồng ghi pairing). Không làm được theo cách này → dừng, hỏi user |

## 13. Quyết định đã chốt (user, 2026-11-10)

| Quyết định | Nơi áp dụng |
|---|---|
| Cọc: `depositAmount` (null = không cọc) + `depositHolder` ≤ 100; chỉ luồng B; không thu/giữ tiền | API_Spec 2.6, §4.2; §5.1 |
| Chi phí + cọc theo pairing; hai đội trưởng sửa khi `NEGOTIATING`; sửa → reset xác nhận; ghép → chép sang kèo (cột chi phí/cọc + khoản tự nhập — user duyệt chép khoản tự nhập 2026-11-11) | §4.1, §4.4, §5.1, §5.2 |
| Phương án trên pairing: 1 địa điểm + 1 khung giờ trong danh sách của kèo; ai cũng đặt; đổi → reset; chưa có → không xác nhận | API_Spec 2.5, 2.7; §5.1 (FK hai cột) |
| Không có thao tác rút / kết thúc pairing | API_Spec §1 |
| Kèo huỷ khi `OPEN` → pairing `SUPERSEDED`, không cần đóng trước | MATCH-001 2.5 |
| Chat: REST + STOMP/SockJS, JWT ở `CONNECT`, simple broker một instance | §4.6, ADR-0014 |
| `revision` ở 2.5, 2.6, 2.7; lệch → `409 PAIRING_006` | API_Spec §1.4 |
| Cursor `before`/`after`, `limit` ≤ 100 (mặc định 50), `{items, hasMore}`; cursor theo `(created_at, id)` | API_Spec 2.9; §5.2 |
| 1–5 địa điểm, 1–5 khung giờ; `expiresAt` trong [sớm nhất − 7 ngày, sớm nhất] và > now | MATCH-001 2.1 |
| Mở rộng MATCH-001 2.1/2.2/2.3/2.11 (+ 2.4–2.7), CHANGELOG MAJOR; "gần tôi" = lựa chọn địa điểm gần nhất | MATCH-001 v2.0 |
| `markSelected` chỉ địa điểm đã chốt, lúc hoàn tất xác nhận | §4.4 |
| Tin nhắn ≤ 1000 ký tự | API_Spec 2.8; §5.1 |
| Review MAJOR #1: `confirmSide` thấy phía kia đã xác nhận → rollback + retry; thứ tự khoá `match` → `pairing` mọi luồng | §4.4; `Processing_Flow.md` §5 |
| Review MAJOR #2: địa điểm `INACTIVE` → `422 VENUE_002` ở 2.5 và 2.7, đọc trong câu có sẵn | §4.1, §4.3, §4.4, §5.2 |
| FK `chat_message.sender_user_id`, `venue_report.reporter_id`, `venue.created_by_user_id` → `ON DELETE RESTRICT` | §5.1; VENUE-001 §5.1 |
| Tin nhắn mang người thực sự đã gửi (tên, ảnh; đã xoá → che); chi tiết thương lượng mang đội trưởng hiện tại mỗi đội | API_Spec §4.2, §4.4; §5.2, §7 |
| Tắt header `selector` của simple broker | §4.6 |
