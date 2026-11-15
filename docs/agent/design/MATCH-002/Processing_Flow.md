# Processing Flow — Thương lượng ghép kèo luồng B (pairing) & chat giữa hai đội

| Mục | Giá trị |
|---|---|
| Mã chức năng | `MATCH-002` |
| Phạm vi tầng | Backend (Spring Boot REST API; frame trên kênh STOMP của MATCH-001). Frontend: ngoài phạm vi repo này |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · PostgreSQL ≥ 18 · Flyway |
| Quy ước áp dụng | `docs/agent/non-functional-standards.md` (N_DETAIL=3, N_LIST=4, N_WRITE=6; §4.1 flush) · ADR-0003 (+ Sửa đổi 2026-11-09, 2026-11-10), ADR-0011, ADR-0012, ADR-0014 (+ Sửa đổi 2026-11-15) · MATCH-001 `Processing_Flow.md` (v2.4) §1 (lazy check), §4, §5 |
| Ràng buộc dữ liệu | V1 + V5 + V6 + `V7__match_flow_b_pairing.sql` (`Component_Class_Design.md` §5.1) |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-10 · v1.0 (baseline) · hiện tại **v2.0** (solution-architect · 2026-11-15) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan

7 endpoint REST. Chat (gửi / đọc / đầu phòng / "Tin nhắn của tôi") và kênh STOMP: MATCH-001 `Processing_Flow.md` §1.1, §2.4, §3.15–§3.20 (v2.4 — chuyển từ tài liệu này); mã này chỉ thêm nhánh luồng B (§3.1 tạo cuộc trò chuyện, `Component_Class_Design.md` §4.2) và frame `PAIRING_UPDATED`. Mã lỗi: `API_Spec.md` §5. Số truy vấn tính cho **một lần thử**; JWT không truy vấn DB. Ký hiệu **S** SELECT, **I** INSERT, **U** UPDATE, **D** DELETE; **[lazy]** = nhánh kèo `OPEN` quá `expiresAt` (MATCH-001 `Processing_Flow.md` §1: `MatchExpiry.expireIfDue` → U match + U pairing `NEGOTIATING` → `EXPIRED` + `MatchExpiredEvent`, commit rồi `MATCH_004`). `now` lấy từ `Clock` một lần đầu mỗi lần thử. Không gọi HTTP ra ngoài. Phần luồng B trên endpoint MATCH-001 (tạo / tìm / xem / huỷ kèo, job): MATCH-001 `Processing_Flow.md` §3.

### 1.1. Thứ tự guard (nguồn duy nhất)

Sau JWT (401) và Bean Validation (400). Tồn tại → phía / đội (trạng thái rồi quyền) → hết hạn [lazy] → trạng thái thương lượng → `revision` → điều kiện còn lại. Quyền đứng trước hết hạn để người không có quyền không kích hoạt chuyển `EXPIRED`.

| Endpoint | Thứ tự guard |
|---|---|
| 2.1 | `MATCH_001` → `TEAM_001` → `TEAM_016` → `TEAM_015` → `TEAM_002` (đội `teamId`) → `PAIRING_004` → `MATCH_016` → `MATCH_004` → `MATCH_003` → `MATCH_005` → `MATCH_013` → `SPORT_002` → `PAIRING_003` (khi INSERT) |
| 2.2 | `MATCH_001` → `TEAM_002` (đội tác giả; giải tán → `TEAM_016`) → `MATCH_016` |
| 2.3 | `TEAM_001` → `TEAM_002` (giải tán → `TEAM_016`) |
| 2.4 | `PAIRING_001` → `PAIRING_002` |
| 2.5 | `PAIRING_001` → `PAIRING_002` → `TEAM_016` → `TEAM_015` → `TEAM_002` → `MATCH_004` → `PAIRING_005` → `PAIRING_006` → `PAIRING_008` → `VENUE_002` (địa điểm được chọn) → [giống phương án hiện tại → 200 không ghi] |
| 2.6 | `PAIRING_001` → `PAIRING_002` → `TEAM_016` → `TEAM_015` → `TEAM_002` → `MATCH_004` → `PAIRING_005` → `PAIRING_006` |
| 2.7 | `PAIRING_001` → `PAIRING_002` → `TEAM_016` → `TEAM_015` → `TEAM_002` → `MATCH_004` → `PAIRING_005` → `PAIRING_006` → `PAIRING_007` → `VENUE_002` (địa điểm đã chốt) → `MATCH_013` (đội phía kia) → `SPORT_002` → [phía mình đã xác nhận → 200 không ghi] |

`TEAM_001` cho `teamId` ở 2.5–2.7 không xảy ra: `teamId` khác hai đội của pairing → `PAIRING_002` trước. Đội đã giải tán không còn đội trưởng ACTIVE → `TEAM_016` (không phải `TEAM_002`), như USER-002 §2.1a. 2.4 không có `teamId` → người của đội đã giải tán nhận `PAIRING_002`. Chat: MATCH-001 `Processing_Flow.md` §1.1.

## 2. Sequence

### 2.1. Xác nhận (2.7)

```mermaid
sequenceDiagram
    participant C as Client
    participant PS as PairingServiceImpl
    participant R as ConcurrencyConflictRetrier
    participant DB as PostgreSQL
    participant GW as ChatRealtimeGateway
    participant EP as ApplicationEventPublisher
    C->>PS: POST /pairings/{id}/confirm (teamId, revision)
    PS->>R: execute(MATCH_014, lambda)
    Note over R,DB: mỗi lần thử = 1 transaction
    R->>DB: Q1 S context (pairing ⋈ match + version ⋈ 2 team ⋈ sport ⋈ khung giờ + trạng thái địa điểm đã chốt, cờ captain)
    alt kèo OPEN quá expiresAt
        R->>DB: U match EXPIRED, U pairing NEGOTIATING → EXPIRED
        R-->>PS: Expired (commit) → MATCH_004
    else phía kia CHƯA xác nhận (theo Q1)
        R->>DB: Q2 U pairing đặt xác nhận phía mình WHERE NEGOTIATING ∧ revision RETURNING 2 cột xác nhận
        alt phía kia vẫn null
            R->>GW: afterCommit PAIRING_UPDATED (CONFIRMATION)
            R-->>PS: NEGOTIATING
        else phía kia vừa xác nhận (commit trước khi Q2 được đánh giá lại)
            Note over R,DB: ConcurrencyConflictException → rollback (nhả khoá pairing) → lần thử mới từ Q1:<br/>Q1 thấy phía kia đã xác nhận → nhánh hoàn tất
        end
    else phía kia ĐÃ xác nhận (theo Q1) — hoàn tất (chưa giữ khoá nào)
        R->>DB: Q2 U match CAS WHERE OPEN ∧ version → CONFIRMED, venue/giờ/chi phí đã chốt (khoá match)
        R->>DB: Q3 U pairing: chính nó → CONFIRMED, NEGOTIATING khác của kèo → SUPERSEDED RETURNING id (khoá pairing)
        R->>DB: Q4 I khoản tự nhập của kèo = INSERT … SELECT khoản của pairing
        R->>DB: Q5 U venue markSelected (địa điểm đã chốt)
        R->>EP: MatchConfirmedEvent (pairingId)
        R->>GW: afterCommit PAIRING_UPDATED (CONFIRMED)
    end
    Note over DB: CAS / điều kiện 0 dòng → retry; hết 3 lần → MATCH_014
    PS-->>C: 200 PairingConfirmationResponse
```

## 3. Luồng chi tiết

### Bảng số truy vấn

| # | Endpoint | Truy vấn (một lần thử) | Tổng / Ngưỡng |
|---|---|---|---|
| 2.1 | `POST /matches/{id}/pairings` | S match, S captain ctx (+ tên đội, tên caller), S availability (đội tác giả + đội trưởng hiện tại + bộ môn + cuộc trò chuyện đã có), I pairing (`saveAndFlush`), I cuộc trò chuyện (lúc commit; bỏ khi đã có), U match version (lúc commit) · [lazy] S, S, U, U | **6 / W6 ✅ (= ngưỡng; v1.2: 5)** · [lazy] 4 |
| 2.2 | `GET /matches/{id}/pairings` | S quyền tác giả (+ `type`), S trang, S count | 3 / L4 ✅ |
| 2.3 | `GET /teams/{id}/pairings` | S captain ctx, S trang, S count | 3 / L4 ✅ |
| 2.4 | `GET /pairings/{id}` | S detail (+ đội trưởng hiện tại 2 đội, cờ quyền, `expiresAt` kèo), S khoản tự nhập | 2 / D3 ✅ |
| 2.5 | `PUT …/proposal` | S ctx, S phương án hợp lệ (+ trạng thái địa điểm), U pairing CAS · không đổi phương án / `VENUE_002`: S, S · [lazy] S, U, U | 3 / W6 ✅ |
| 2.6 | `PUT …/cost-agreement` | S ctx, U pairing CAS, D khoản tự nhập, I khoản tự nhập (1 câu `unnest`), S khoản tự nhập (lấy `itemId`); bỏ I + S khi rỗng · [lazy] S, U, U | 5 / W6 ✅ (3 khi rỗng) |
| 2.7 | `POST …/confirm` một phía | S ctx, U pairing `RETURNING` | 2 / W6 ✅ |
| 2.7 | `POST …/confirm` hoàn tất | S ctx (+ `version` kèo, trạng thái địa điểm), U match CAS, U pairing (xác nhận + thay thế) `RETURNING`, I khoản tự nhập kèo (`INSERT … SELECT`), U venue `markSelected` | 5 / W6 ✅ |
| 2.7 | `POST …/confirm` race (phía kia xác nhận đồng thời) | Lần thử 1: S ctx, U pairing `RETURNING` → rollback · lần thử 2: như "hoàn tất" | 2 + 5 (mỗi lần thử ≤ 5 / W6 ✅) |
| 2.7 | [lazy] | S ctx, U match, U pairing | 3 |

**NFS gate:** 7/7 trong ngưỡng (chat: MATCH-001 2.16–2.20). **2.1 chạm đúng W6** (v2.0 thêm INSERT cuộc trò chuyện) — mọi truy vấn thêm vào 2.1 phải gộp hoặc xin duyệt; cuộc trò chuyện đã có được tìm trong câu `findAvailability` sẵn có (không thêm SELECT); 2.7 tối đa 5 / lần thử (hoàn tất, gồm chép khoản tự nhập sang kèo bằng một `INSERT … SELECT` — user duyệt 2026-11-11). Dự phòng `[cần xác nhận #4]` (`RETURNING` không chạy được → `@Modifying` + đọc lại): hoàn tất 6 = **chạm W6**, một phía 3 — khi đó mọi câu thêm vào 2.7 phải gộp hoặc xin duyệt. Response 2.7 dựng từ `RETURNING` + ctx. Đã gộp: pairing + kèo (+ `version`) + 2 đội + bộ môn + khung giờ / trạng thái địa điểm đã chốt + quyền + người gọi thành một câu (`findContext`); CAS `match` bằng native với `version` từ ctx (không đọc lại kèo); xác nhận + thay thế thành một `UPDATE` (`finalizeConfirmation`); đội trưởng hiện tại / người gửi tin JOIN vào câu có sẵn; khung giờ lấy id bằng `INSERT … RETURNING` (MATCH-001 2.1). Không truy vấn trong vòng lặp; frame realtime dựng từ bộ nhớ (không đọc lại). Retry nhân số truy vấn tối đa 3 (ADR-0003); nhánh race của 2.7 dùng một lần thử.

### 3.1. `POST /matches/{id}/pairings` (retrier `MATCH_014`)
Lần thử: Q1 `matchGuard.findMatch` → Q2 `teamGuard.requireActiveCaptain(teamId, callerId)` (trả `TeamCaptainContextView` có tên/ảnh/điểm — MATCH-001 §5.3) → `teamId == authorTeamId` → `PAIRING_004` → `type ≠ NEED_VENUE` → `MATCH_016` → `isExpiryDue` → [lazy] → `requireOpen` (`MATCH_003/004/005`) → Q3 `matchRepository.findAvailability(matchId, teamId)` (trạng thái + tên/ảnh/điểm đội tác giả, bộ môn bật, `existingConversationId`): đội tác giả ≠ `ACTIVE` → `MATCH_013`; bộ môn tắt → `SPORT_002` → Q4 `Pairing.builder()` (`NEGOTIATING`, `revision = 0`) `saveAndFlush`; `idx_pairing_active` → `PAIRING_003` (flush tường minh để đổi lỗi ràng buộc, comment tại chỗ) → `existingConversationId == null` → `conversationRepository.save(Conversation …TEAM_MATCH…)` (Q5 INSERT lúc commit; id v7 có ngay sau `save`) → `matchRepository.forceIncrementVersion(match)` → `PairingCreatedEvent` → Q6 `UPDATE match SET version = version + 1 … WHERE version = ?` lúc commit (lệch → retry). Response từ entity + Q2 + Q3 + `conversationId`. INSERT cuộc trò chuyện chạy **sau** INSERT pairing: hai lần nhận kèo đồng thời của cùng đội đã bị `idx_pairing_active` tuần tự hoá (bên sau `PAIRING_003` trước khi tới bước này) → không đụng `idx_conversation_team_match`; vi phạm unique này (chỉ khi lỗi lập trình) → 500.

### 3.2. `GET /matches/{id}/pairings` (readOnly)
Q1 `matchRepository.findAuthorAccess(matchId, callerId)` (MATCH-001, + `type`) → `MATCH_001`; `!callerIsCaptain` → `DISBANDED` ? `TEAM_016` : `TEAM_002`; `type ≠ NEED_VENUE` → `MATCH_016` → Q2/Q3 `pairingRepository.findByMatch(matchId, status, now, p)` — thứ tự `created_at, id` ↑.

### 3.3. `GET /teams/{id}/pairings` (readOnly)
Q1 `teamGuard.requireCaptainAllowInactive(teamId, callerId)` → Q2/Q3 `findByTeam(teamId, role, status, now, p)` — thứ tự `updated_at, id` ↓.

### 3.4. `GET /pairings/{id}` (readOnly)
Q1 `findDetailById(id, callerId, now)` (cờ `callerAuthorCaptain` / `callerInterestedCaptain`, đội trưởng hiện tại hai đội, `expiresAt` kèo — cùng câu) → không có dòng → `PAIRING_001`; cả hai cờ `false` → `PAIRING_002` → Q2 `costCustomItemRepository.findByPairingIdOrderByDisplayOrderAsc` → mapper (che tên đội trưởng đã xoá tài khoản).

### 3.5. `PUT …/proposal` (retrier `MATCH_014`)
Lần thử: Q1 `findContext` → `requireSide` → `requireCaptain(allowInactive = false)` → [lazy] → `requireNegotiating` → `requireRevision` (`revision` cũ → `PAIRING_006` kể cả khi phương án giống cũ) → Q2 `findProposalCandidate(matchId, venueId, timeOptionId)` → rỗng → `PAIRING_008` → `venueStatus = INACTIVE` → `VENUE_002` → giống `proposedVenueId`/`proposedTimeOptionId` hiện tại → trả ctx + Q2 (không ghi, không frame) → Q3 `updateProposal(…, revision)` 0 dòng → retry (lần sau guard trả `PAIRING_005`/`PAIRING_006`) → frame `PAIRING_UPDATED/PROPOSAL` → response `revision + 1`, xác nhận `null`, `updatedAt = now`, phương án từ Q2. Vi phạm FK hai cột (lỗi lập trình — Q2 đã kiểm) → 500.

### 3.6. `PUT …/cost-agreement` (retrier `MATCH_014`)
Bean Validation (`@ValidCostAgreement`, `@ValidDeposit`, ≤ 10 khoản) → lần thử: Q1 `findContext` → guard như 3.5 (không có `PAIRING_008`) → Q2 `updateCost(…, revision)` 0 dòng → retry → Q3 `deleteByPairingIdBulk` → Q4 `insertAllForPairing` (1 câu `unnest`, id `uuidv7()`) → Q5 `findByPairingIdOrderByDisplayOrderAsc`; bỏ Q4, Q5 khi rỗng → frame `PAIRING_UPDATED/COST` → response. Gửi lại cùng nội dung vẫn ghi và reset xác nhận (`API_Spec.md` 2.6).

### 3.7. `POST …/confirm` (retrier `MATCH_014`)
Như `Component_Class_Design.md` §4.4 và §2.1 ở trên. Bổ sung:
- Thứ tự kiểm sau `requireRevision`: `PAIRING_007` → `VENUE_002` (`ctx.proposedVenueStatus = INACTIVE` — địa điểm đã chốt bị admin ngừng hoạt động sau khi chốt; FE chốt lại địa điểm khác bằng 2.5) → `MATCH_013` → `SPORT_002` → phía mình đã có `…ConfirmedAt` (đúng `revision`, vì đổi gói xoá xác nhận) → trả trạng thái từ ctx, không ghi, không frame.
- Một phía: Q2 `confirmSide` thấy phía kia khác `null` → `ConcurrencyConflictException` (không hoàn tất trong lần thử đang giữ khoá `pairing`) → lần thử sau Q1 thấy phía kia đã xác nhận → hoàn tất.
- Hoàn tất: Q2 `matchRepository.confirmPairing(matchId, ctx.matchVersion, …)` với `confirmedOpponentTeamId = interestedTeamId`, `venueId = proposedVenueId`, `matchStartTime = proposedStartTime`, `matchEndTime = proposedEndTime`, `costType`, `costSplit`, `costVenueAmount`, `costDrinksAmount`, `costDepositAmount`, `costDepositHolder` từ ctx; 0 dòng (kèo vừa đổi trạng thái / `version` — tạo pairing, hoàn tất pairing khác, huỷ, job) → `ConcurrencyConflictException` → retry → guard trả mã từ ctx mới.
- Q3 `finalizeConfirmation` không trả `pairingId` → `ConcurrencyConflictException` (gói vừa bị sửa) → rollback cả CAS `match` → retry → `PAIRING_006`.
- Q4 `copyPairingItemsToMatch(pairingId, matchId, now)`: khoản tự nhập của gói đã chốt thành khoản của kèo (MATCH-001 2.3 đọc theo kèo như luồng A). Q5 `markSelected`.
- Response: `status`, ba mốc xác nhận, `matchStatus` từ ctx + `RETURNING` + `now`.

### 3.8. Frame realtime
`afterCommit` của 2.5–2.7 → `PAIRING_UPDATED` lên topic cuộc trò chuyện của thương lượng (`ctx.conversationId()`) qua kênh của MATCH-001 §3.20. Lỗi gửi → log `WARN` (`conversationId`, loại frame), không ảnh hưởng response.

## 4. Transaction Boundary

Bảng theo method: `Component_Class_Design.md` §4.7. `READ_COMMITTED`. Event (ADR-0011) và frame realtime (ADR-0014) chỉ đi ra sau commit → lần thử bị rollback không phát. Nhánh [lazy] commit việc chuyển `EXPIRED` rồi báo `MATCH_004` (MATCH-001 `Component_Class_Design.md` §4.7). Không I/O ngoài trong transaction (gửi frame chạy sau commit, trong bộ nhớ process).

## 5. Concurrency & Locking (ADR-0003 + ADR-0012)

- `match`: `@Version` (MATCH-001). Tạo pairing tăng `version` lúc commit (force-increment); hoàn tất xác nhận (native `confirmPairing`, `WHERE version = :v`, `version + 1`) và huỷ `OPEN` CAS `match` → tuần tự hoá ba thao tác này với nhau và với job.
- `pairing`: không version; mọi đổi trạng thái là `UPDATE … WHERE status = 'NEGOTIATING' [AND revision = :r]` → chờ khoá dòng rồi đánh giá lại (READ COMMITTED); 0 dòng = đã bị đổi → retry, lần sau guard trả mã đúng (ADR-0003 Sửa đổi 2026-11-10).
- Bất biến "≤ 1 pairing `CONFIRMED` mỗi kèo": CAS `match` + `idx_pairing_confirmed` (lớp chặn cuối).

| Race | Kết quả bất biến |
|---|---|
| Hai phía cùng xác nhận một pairing (phía kia chưa xác nhận theo Q1 của cả hai) | `confirmSide` thứ hai chờ khoá dòng, đánh giá lại, `RETURNING` thấy xác nhận thứ nhất → `ConcurrencyConflictException`, rollback → lần thử sau Q1 thấy phía kia đã xác nhận → hoàn tất (`match` trước `pairing`). Đúng một lần hoàn tất |
| X–P và X–Q (đội X trong hai pairing P, Q của cùng kèo) cùng xác nhận khi phía kia của mỗi pairing đã xác nhận | Cả hai đi nhánh hoàn tất, cùng CAS `match` trước: bên sau chờ khoá `match`, đánh giá lại → `version` lệch → 0 dòng → retry → Q1 thấy pairing của mình `SUPERSEDED` → `PAIRING_005`. Không bên nào giữ `pairing` khi chờ `match` → không deadlock. Bên commit trước thắng (AC-M05) |
| Hai pairing của cùng kèo cùng hoàn tất (đội khác nhau) | Như dòng trên |
| Hoàn tất X–P vs hai phía X–Q cùng xác nhận (trường hợp gây deadlock ở v1.0) | Phía X–Q thấy bên kia vừa xác nhận → rollback nhả khoá `pairing` X–Q trước khi đụng `match` → hoàn tất X–P (đang giữ `match`, chờ `pairing` X–Q để thay thế) chạy tiếp. Lần thử sau của X–Q: CAS `match` thua → retry → `SUPERSEDED` → `PAIRING_005`; hoặc X–Q hoàn tất trước → X–P `PAIRING_005` |
| Xác nhận (2.7) vs admin ngừng hoạt động địa điểm đã chốt | Ngừng hoạt động commit trước Q1 → `VENUE_002`. Commit sau Q1 → kèo vẫn ghép với địa điểm vừa `INACTIVE` và `markSelected` cập nhật địa điểm đó (cửa sổ một transaction) — chấp nhận, cùng cách luồng A (VENUE-001 `Processing_Flow.md` §5 "`markSelected` vs mark-inactive") |
| Chốt phương án (2.5) vs admin ngừng hoạt động địa điểm commit sau `findProposalCandidate` | Phương án được lưu với địa điểm vừa `INACTIVE` (cửa sổ một transaction). 2.4 hiển thị `proposal.venueStatus = INACTIVE`; 2.7 chặn `VENUE_002` → FE cho chốt lại. Chấp nhận — không ghép được kèo vào địa điểm đó |
| Xác nhận vs sửa gói (2.5/2.6) | Sửa commit trước → `revision` tăng → xác nhận 0 dòng → retry → `PAIRING_006`. Xác nhận (một phía) commit trước → sửa vẫn khớp `revision` → thành công, xoá xác nhận vừa ghi. Hoàn tất commit trước → sửa 0 dòng → retry → `PAIRING_005` |
| Hoàn tất vs sửa gói commit giữa Q1 và `finalizeConfirmation` | `finalizeConfirmation` không chọn được pairing (revision lệch) → rollback cả CAS `match` → retry → `PAIRING_006`; các pairing khác không bị thay thế |
| Hai lần sửa gói cùng `revision` | Bên sau 0 dòng → retry → `PAIRING_006` (không ghi đè lặng lẽ) |
| Tạo pairing vs hoàn tất | Tạo commit trước → hoàn tất lệch version → retry → `finalizeConfirmation` thay thế cả pairing mới. Hoàn tất commit trước → tạo retry → `MATCH_003`. Không còn `NEGOTIATING` trên kèo không `OPEN` |
| Huỷ `OPEN` vs hoàn tất | CAS `match`: huỷ thua → `MATCH_015`; hoàn tất thua → retry → pairing `SUPERSEDED` → `PAIRING_005` |
| Cùng đội nhận kèo 2 lần | INSERT thứ hai vi phạm `idx_pairing_active` → `PAIRING_003` |
| Job hết hạn vs xác nhận / sửa | Job claim `match` (`FOR NO KEY UPDATE SKIP LOCKED`) rồi U pairing. Hoàn tất / tạo pairing chờ CAS `match` → 0 dòng → retry → [lazy thấy `EXPIRED`] `MATCH_004`. Sửa gói / xác nhận một phía commit trước → job expire pairing sau; commit sau → 0 dòng → retry → `MATCH_004` |

**Thứ tự khoá** [Inference — suy từ thứ tự câu lệnh; kiểm bằng test đồng thời bên dưới]: mọi luồng khoá `match` trước `pairing`, không luồng nào giữ khoá `pairing` rồi chờ `match`:

| Luồng | Khoá theo thứ tự |
|---|---|
| 2.7 hoàn tất | `match` (CAS) → `pairing` của kèo (`finalizeConfirmation`) → INSERT khoản tự nhập của kèo (dòng mới, `FOR KEY SHARE` trên `match` đã giữ) → `venue` (`markSelected`, một dòng) |
| 2.7 một phía, 2.5, 2.6 | chỉ `pairing` (+ `cost_custom_item` của pairing ở 2.6); nhánh race rollback trước khi khoá `match` |
| [lazy] trong 2.5–2.7 và gửi tin (MATCH-001 2.16, kèo B), `MatchExpiry` | `match` → `pairing` (chạy trước mọi câu ghi khác của lần thử) |
| Job hết hạn | `match` (`FOR NO KEY UPDATE SKIP LOCKED`) → `pairing` |
| Huỷ kèo `OPEN` (MATCH-001 2.5) | `match` (CAS) → `pairing` (`supersedeNegotiating`) |
| 2.1 tạo pairing | INSERT `pairing` mới (dòng chưa ai thấy) → INSERT `conversation` mới (`FOR KEY SHARE` trên `match`) → `match` (`version` lúc commit) |

`markSelected` khoá một dòng `venue` → không có vấn đề thứ tự nhiều dòng (VENUE-001 `Processing_Flow.md` §3.15). Deadlock ngoài dự kiến (`40P01`) vẫn được retrier thử lại (ADR-0003 Sửa đổi 2026-11-06).

**Test bắt buộc** (integration, commit thật, không `@Transactional` mức test; `Clock` cố định khi cần): hai phía xác nhận song song (đúng một hoàn tất, kèo `CONFIRMED` một lần, một `MatchConfirmedEvent`; bên đi nhánh race trả `CONFIRMED` sau một lần retry); hai pairing hoàn tất song song (đúng một `CONFIRMED`, pairing kia `SUPERSEDED`, bên thua `PAIRING_005`); **X–P và X–Q cùng xác nhận** — kèo của đội X có pairing P (P đã xác nhận) và Q (chưa ai xác nhận); đồng thời: X xác nhận X–P, X và Q cùng xác nhận X–Q (lặp nhiều vòng, latch cho các luồng xuất phát cùng lúc) → không `40P01` trong log retrier, không `MATCH_014`, đúng một pairing `CONFIRMED`, pairing kia `SUPERSEDED`, lệnh xác nhận thua nhận `PAIRING_005`; xác nhận với `revision` cũ sau khi sửa gói → `PAIRING_006`, không ghi; sửa gói sau một phía xác nhận → xác nhận bị xoá; 2.5 chọn địa điểm `INACTIVE` → `VENUE_002`, không ghi; 2.7 khi địa điểm đã chốt bị ngừng hoạt động → `VENUE_002`, kèo vẫn `OPEN`, `last_selected_at` không đổi; hoàn tất vs sửa gói chen giữa → `PAIRING_006`, không pairing nào bị thay thế, kèo vẫn `OPEN`; tạo pairing vs hoàn tất (không `NEGOTIATING` sót trên kèo `CONFIRMED`); huỷ `OPEN` khi có pairing `NEGOTIATING` → tất cả `SUPERSEDED`; huỷ `OPEN` vs hoàn tất (`MATCH_015` / `PAIRING_005`); lazy: 2.7 trên kèo quá hạn → `MATCH_004` **và** DB kèo `EXPIRED` + pairing `EXPIRED` + 1 event; 2.4 trên kèo quá hạn → `status = EXPIRED`, DB không đổi; hoàn tất ghi đúng `venue_id`, giờ, chi phí, cọc vào `match` và chép đủ khoản tự nhập (đúng thứ tự, id mới, `pairing_id = NULL`; pairing giữ khoản của mình), `markSelected` đúng địa điểm, MATCH-001 2.3 trả khoản tự nhập đọc theo kèo; hoàn tất khi pairing không có khoản → 0 dòng chép; huỷ kèo B `CONFIRMED` trừ điểm đúng khung giờ đã chốt; FK hai cột chặn phương án thuộc kèo khác (gọi repository trực tiếp); 2.4 `captainName` là đội trưởng hiện tại, đội đã giải tán → `null`; frame `PAIRING_UPDATED` đến trên topic cuộc trò chuyện của thương lượng; `NfsQueryCountTest`: 2.1 = 6 (5 khi cuộc trò chuyện đã có), 2.4 = 2, 2.5 = 3 (2 khi không đổi / `VENUE_002`), 2.6 = 5 (3 khi rỗng), 2.7 một phía = 2, hoàn tất = 5, race = 2 (lần thử rollback) + 5, 2.2/2.3 = 3. **Chat luồng B (v2.0):** 2.1 tạo đúng một cuộc trò chuyện `TEAM_MATCH` (bên A = đội tạo kèo, bên B = đội nhận kèo), response + 2.2/2.3/2.4 cùng `conversationId`; nhắn được / chỉ xem qua MATCH-001 2.16 theo pairing (`NEGOTIATING`, `CONFIRMED`, `CONFIRMED` + kèo `CANCELLED`, `ARCHIVED`/`COMPLETED`, `SUPERSEDED`, `EXPIRED`); gửi tin khi pairing `NEGOTIATING` trên kèo quá hạn → `MATCH_004` **và** kèo + pairing `EXPIRED` + 1 `MatchExpiredEvent`; MATCH-001 2.19 trả `pairingId`, `pairingStatus`. Test chat chung: MATCH-001 `Processing_Flow.md` §5.

## 6. Error Handling

Như MATCH-001 §6. Bổ sung: `DataIntegrityViolationException` của `idx_pairing_active` → `PAIRING_003`; `idx_pairing_confirmed` (chỉ khi lỗi lập trình) → 500 + log `ERROR`. `ConcurrencyConflictException` → retry; hết lượt → `MATCH_014`. STOMP: `BusinessException` / lỗi JWT ở interceptor → frame `ERROR` header `message` = mã lỗi, body `{code, message}`; lỗi khác → `ERROR` mã `SYS_005`, log `ERROR` (không kèm token / nội dung tin).

## 7. Idempotency

| Endpoint | Gọi lặp |
|---|---|
| 2.1 | Khoá tự nhiên → `PAIRING_003` |
| 2.5 | `revision` kiểm trước: `revision` cũ → `PAIRING_006` (kể cả cùng phương án). `revision` đúng + cùng phương án → 200, không đổi `revision` |
| 2.6 | Gửi lại với `revision` mới nhận được → ghi lại (reset xác nhận); với `revision` cũ → `PAIRING_006` |
| 2.7 | Phía mình đã xác nhận → 200 trạng thái hiện tại; sau `CONFIRMED` → `PAIRING_005` |

## 8. Performance & Index

| Bảng | Index | Dùng cho | Nguồn |
|---|---|---|---|
| `pairing` | `(match_id, status)` | 2.2, `finalizeConfirmation`, `supersedeNegotiating`, `expireNegotiatingByMatchIds` | V1 |
| `pairing` | `(interested_team_id, status)` | 2.3 `INTERESTED` | V1 |
| `pairing` | unique `(match_id, interested_team_id) WHERE status NOT IN ('EXPIRED','SUPERSEDED')` | 2.1 chống trùng | V1 |
| `pairing` | unique `(match_id) WHERE status = 'CONFIRMED'` | Bất biến một `CONFIRMED` | V7 (thay `idx_pairing_race`) |
| `match` | `(author_team_id, status)` | 2.3 `AUTHOR` | V1 |
| `match_venue_option` | unique `(match_id, venue_id)`, `(match_id, display_order)`; `(venue_id)` | Phương án, MATCH-001 2.3 / 2.2 gần tôi | V7 |
| `match_time_option` | unique `(match_id, id)`, `(match_id, proposed_start_time)` | Phương án, MATCH-001 2.2 lọc giờ | V7 |
| `cost_custom_item` | unique `(pairing_id, display_order)` | 2.4, 2.6, 2.7 (đọc khoản để chép) | V7 |
| `team_member` | `(team_id, role, status)` | 2.4 đội trưởng hiện tại hai đội (`role = 'CAPTAIN' AND status = 'ACTIVE'`) | V1 (`idx_team_member_team_role`) |
| `"user"` | khoá chính | 2.4 tên đội trưởng | V1 |

`findByTeam` với `role = ALL` dùng `OR` hai điều kiện (bitmap OR hai index); số pairing của một đội nhỏ. Đo SQL thật bằng `NfsQueryCountTest` trước khi merge.

## 9. Rủi ro vận hành

| Rủi ro | Ảnh hưởng | Xử lý |
|---|---|---|
| Mất frame `PAIRING_UPDATED` (ngắt mạng, restart) | Bên kia chưa thấy gói mới | FE kết nối lại theo MATCH-001 `API_Spec.md` §1.7 rồi tải lại 2.4. Rủi ro kênh realtime chung: MATCH-001 `Processing_Flow.md` §9 |
| Event không bền | Mất thông báo `PAIRING_NEW` / `MATCH_CONFIRMED` khi crash sau commit | Chấp nhận MVP (ADR-0011) |

## 10. Danh sách [cần xác nhận]

Đánh số toàn thiết kế MATCH-002 (số không dùng lại): 2, 4, 5, 16 ở `Component_Class_Design.md` §12, 6 ở đây, 15 ở `API_Spec.md` §6; 7, 8 chuyển MATCH-001 (#14, #15), 12 đã chốt (MATCH-001 §13). **Cổng chặn code** (chi tiết ở `Component_Class_Design.md` §12): #4 (`RETURNING` không `@Modifying`) và #5 (retrier bắt `ConcurrencyConflictException`) — bước đầu phase 1; kênh realtime: MATCH-001 `[cần xác nhận #12]`. Không đạt → dừng, hỏi user.

| # | Nội dung | Loại |
|---|---|---|
| 6 | ~~Deadlock (`40P01`) giữa "hoàn tất sau race" và job / huỷ `OPEN` / hoàn tất pairing khác~~ — **đã bỏ (v1.1):** nhánh race rollback rồi thử lại, thứ tự khoá thống nhất `match` → `pairing` (§5); kiểm bằng test "X–P và X–Q cùng xác nhận" | — |
