---
task: MATCH-002 — thương lượng ghép kèo luồng B (pairing, tiền cọc) + nhánh luồng B trong MATCH-001 (V8)
created: 2026-11-21
status: in_progress
---

## Mô tả
Triển khai MATCH-002 theo thiết kế đã duyệt (v3.0): pairing (nhận kèo, chốt phương án, chi phí + tiền cọc, xác nhận / ghép kèo), phần luồng B của chat (tạo cuộc trò chuyện khi nhận kèo, frame `PAIRING_UPDATED`), migration `V8__match_flow_b_pairing.sql`, và các nhánh luồng B nằm trong thành phần MATCH-001 (MATCH-001 CCD §1.1 "đợt MATCH-002").

Bối cảnh (user chốt 2026-11-21): 7 mã còn lại implement **song song**, mỗi mã một worktree/nhánh từ `origin/main`. Chuỗi migration: SYSTEM-002 (V6) → MATCH-001 đợt 1 (V7) → **MATCH-002 (V8)** → REPUTATION-001 (V9) → MERCENARY-001 (V10) → NOTIFICATION-001 (V11) → USER-003 (V12). Class của SYSTEM-002 và MATCH-001 **chưa có** trên `main` → dùng "Phụ thuộc tạm" (mục dưới). Test cần DB / Spring context / migration / NFS viết ở giai đoạn 2, **chỉ chạy** ở giai đoạn tích hợp (worktree không có `.env.dev`).

Nhánh: `feature/MATCH-002-match-flow-b-impl` (tạo từ `origin/main` 6fe6285, 2026-11-21). Nhánh thiết kế `feature/MATCH-002-match-flow-b-pairing` đã merge — không dùng, không xoá.

## Thiết kế liên quan
- `docs/agent/design/MATCH-002/` — `API_Spec.md`, `Component_Class_Design.md`, `Processing_Flow.md` (v3.0), `CHANGELOG.md`.
- `docs/agent/design/MATCH-001/Component_Class_Design.md` §1.1 (hai đợt), §4.1, §4.5–§4.7, §4.9, §4.13–§4.15, §5.2, §5.3, §6, §7; `Processing_Flow.md` §1.1, §3.1–§3.5, §3.11, §3.13 (v3.0).
- `docs/agent/design/SYSTEM-002/Component_Class_Design.md` §2.2 (`AppTime`, `ClockConfig`), §5.1; `docs/agent/convention-backend.md` §6.2 (giờ VN, `LocalDateTime`, `Clock`/`AppTime`).
- ADR-0003 (+ Sửa đổi 2026-11-10: CAS `pairing` bằng `status` + `revision`), ADR-0008, ADR-0011, ADR-0012, ADR-0014, ADR-0017.
- `docs/agent/non-functional-standards.md` — N_DETAIL=3, N_LIST=4, N_WRITE=6; §4.1 flush (2.1 `saveAndFlush` pairing = ngoại lệ 1, comment tại chỗ).

## Definition of Done
- [ ] V8 áp dụng được trên DB dev sau V6 + V7; `ddl-auto=validate` PASS (giai đoạn tích hợp).
- [ ] 7 endpoint MATCH-002 (2.1–2.7) + frame `PAIRING_UPDATED` + phần luồng B của chat đúng `API_Spec.md` / `Processing_Flow.md`.
- [ ] Nhánh luồng B trong thành phần MATCH-001 (bảng "Điểm cắm vào MATCH-001") xong; bỏ chặn `MATCH_016` cho `type = NEED_VENUE` ở MATCH-001 2.1.
- [ ] Cổng chặn `[cần xác nhận #4]` (`INSERT/UPDATE … RETURNING` không `@Modifying`), `#2` (bind mảng `unnest`), `#5` (retrier bắt `ConcurrencyConflictException`) đã kiểm chứng.
- [ ] Unit test PASS ở giai đoạn 2; mọi test ở mục "Test để giai đoạn tích hợp" PASS ở giai đoạn tích hợp (`-Duser.timezone=Asia/Ho_Chi_Minh`, dán output).
- [ ] `NfsQueryCountTest`: 2.1 = 6 (5 khi cuộc trò chuyện đã có), 2.2/2.3 = 3, 2.4 = 2, 2.5 = 3 (2), 2.6 = 5 (3), 2.7 một phía = 2, hoàn tất = 5.
- [ ] Phụ thuộc tạm đã thay bằng bản của mã sở hữu khi tích hợp; không còn stub.
- [ ] `code-reviewer` không còn CRITICAL/MAJOR; lệch thiết kế cập nhật một lần + `architect-reviewer` ĐẠT; tài liệu ở CCD §11 đồng bộ.

## Các bước thực hiện (tổng quan)
- [x] 1. Đối chiếu & phân tích ảnh hưởng (design MATCH-002/MATCH-001/SYSTEM-002, convention §6.2, NFS, ADR) — 2026-11-21
- [x] 2. Quét tái sử dụng + lập kế hoạch chi tiết — 2026-11-21 (chờ user duyệt)
- [x] 3. Giai đoạn 2 — triển khai TDD P0–P6 (unit test chạy ngay; test tích hợp chỉ viết) — 2026-11-21, user duyệt kế hoạch cùng ngày; unit 105/105 PASS, `test-compile` toàn repo PASS; dừng trước `code-reviewer` theo chỉ đạo coordinator
- [ ] 4. Giai đoạn tích hợp — rebase lên `main` có V6 + V7, thay phụ thuộc tạm, P7 (nhánh luồng B trong MATCH-001), chạy toàn bộ test tích hợp
- [ ] 5. Chạy test & xác nhận PASS (dán output)
- [ ] 6. `code-reviewer` trên phần chưa review → sửa CRITICAL/MAJOR, test lại
- [ ] 7. Đối chiếu code với design → cập nhật design theo bảng "Lệch thiết kế" + tài liệu CCD §11 → `architect-reviewer`
- [ ] 8. Bước 7 có sửa code → test lại + `code-reviewer` phần sửa → commit (khi user yêu cầu)

## Lệch thiết kế
_Ghi ngay khi code khác design; KHÔNG sửa design giữa chừng — cập nhật một lần ở bước cuối (`CLAUDE.md` §8). Trống = code khớp design._

| # | Phase | Design | Code thực tế | Lý do | Sửa ở |
|---|---|---|---|---|---|
| 1 | P4 | `PairingMapper.toCreatedResponse(Pairing, Match, TeamCaptainContextView, MatchAvailabilityView)` | + tham số `UUID conversationId` | Response 2.1 cần id cuộc trò chuyện vừa `save` hoặc `existingConversationId`; 4 tham số design không có nguồn | MATCH-002 CCD §7 |
| 2 | P5 | Deps `PairingService` gồm `MatchVenueOptionRepository`, `MatchTimeOptionRepository` | Không inject hai repository này | Không thao tác nào của pairing dùng tới (2.5 đọc lựa chọn qua `PairingRepository.findProposalCandidate`) | MATCH-002 CCD §4.1 |
| 3 | P3 | `PairingContextView` (CCD §5.3) không liệt kê `updatedAt` | + `updatedAt` (cùng câu `findContext`) | 2.5 chốt lại đúng phương án hiện tại trả `updatedAt` hiện tại mà không ghi | MATCH-002 CCD §5.3 |
| 4 | P3 | `findOptionRows`: mapper tách theo `kind` rồi sắp | SQL `ORDER BY kind, displayOrder, startsAt` | Sắp ở DB, mapper (P7) chỉ tách theo `kind` | MATCH-002 CCD §5.2 |
| 5 | P4 | `@ValidDeposit`: `depositHolder` khác null mà `depositAmount` null → lỗi | `depositHolder` rỗng / chỉ khoảng trắng = không nhập (không lỗi; service lưu `null`) | Quy ước `""` ở field tuỳ chọn = không nhập (MATCH-001 `API_Spec.md` §1.1) | MATCH-002 CCD §7 (ghi rõ) |
| 6 | P2 | Pairing/Match… thời gian `LocalDateTime` | Đúng design; 5 entity đổi tạm `OffsetDateTime` → `LocalDateTime` trước khi SYSTEM-002 vào `main` | Phụ thuộc tạm (không phải lệch) | — |

Không có lệch API contract / DB schema / nghiệp vụ.

## Tái sử dụng
_Reuse Report — skill `reuse-scan`, 2026-11-21._

**Nguồn quét:** `common/src/main`, `domain/src/main`, `application/src/main`, `api/src/main` (Grep text + đọc file ứng viên) trên `6fe6285`; nhánh `feature/*-impl` của các mã chạy song song (cùng commit `6fe6285`, chưa có code — chỉ đọc được qua git). **Từ khoá:** `Pairing`, `revision`, `CostAgreement`, `[Dd]eposit`, `ConcurrencyConflict`, `Realtime|Stomp|WebSocket`, `Rules`, `StatusFilter`, `RoleFilter`, `Side`, `Conversation`, `AppTime|ClockConfig`, `MatchGuard|MatchExpiry|MatchWriteOutcome`, `@Constraint`, `ApplicationEventPublisher`, `RETURNING`, `unnest|CAST(:`, `afterCommit`, `maskName`, `DataIntegrityViolations`, `PageRequestFactory`.
**Vùng mù:** code đang viết (chưa commit) trong worktree của SYSTEM-002 / MATCH-001 / REPUTATION-001 / MERCENARY-001 — tên/chữ ký lấy theo thiết kế của mã sở hữu; khi tích hợp, bản của mã sở hữu thắng.

| Thành phần | Kết luận | Nguồn / cách dùng |
|---|---|---|
| Che tên / ảnh người đã xoá (`captainName`) | DÙNG LẠI | `application/mapper/MapperSupport#maskName`, `#maskAvatar` (`uses = MapperSupport.class`) |
| Phân trang 2.2, 2.3 | DÙNG LẠI | `application/support/PageRequestFactory#create(page, pageSize)`; `dto/response/PageResponse`, `PaginationMeta` |
| Đổi vi phạm `idx_pairing_active` → `PAIRING_003` | DÙNG LẠI | `application/support/DataIntegrityViolations#violates(ex, "idx_pairing_active")` |
| Retrier `MATCH_014` | MỞ RỘNG | `application/support/ConcurrencyConflictRetrier#execute(ErrorCode, Supplier)` — thêm `catch (ConcurrencyConflictException)` thử lại như `ObjectOptimisticLockingFailureException` (`[cần xác nhận #5]` MATCH-002, mã này sở hữu thay đổi) |
| Exception "CAS 0 dòng, thử lại" | VIẾT MỚI | `application/support/ConcurrencyConflictException` (RuntimeException). Không có loại tương đương (grep `ConcurrencyConflict` chỉ ra retrier + service dùng retrier). Package chưa ghi trong thiết kế → `[cần xác nhận]` |
| Enum filter 2.2/2.3 (`toQueryValue()` → `null` = ALL) | DÙNG LẠI mẫu | Mẫu `application/service/VenueStatusFilter`; viết `PairingStatusFilter`, `PairingRoleFilter` theo cùng mẫu (thiết kế yêu cầu enum riêng) |
| `MapperSupport.toUtc(Instant)` | KHÔNG dùng | Projection/DTO `LocalDateTime` gán thẳng (convention §6.2) |
| Validator mức lớp | DÙNG LẠI mẫu | `application/validation/ValidGeoQuery(+Validator)` (lỗi gắn field qua property node); viết `@ValidDeposit` mới |
| `@ValidCostAgreement` | MỞ RỘNG (sở hữu MATCH-001) | Thiết kế MATCH-002 §7: áp cho interface `CostAgreementFields` (mới — mã này) để `UpdateCostAgreementRequest` và `UpdatePairingCostAgreementRequest` dùng chung một validator. Logic quy tắc thuộc MATCH-001 → giai đoạn 2 chỉ có bản tạm |
| Native `UPDATE … RETURNING` không `@Modifying` | DÙNG LẠI mẫu | `domain/repository/VenueRepository#markStaleAlerted` (đã kiểm `VenueStaleAlertRepositoryTest`, VENUE-001 #11) — áp cho `confirmSide`, `finalizeConfirmation` |
| Native `INSERT … RETURNING`, bind mảng `unnest(CAST(:x AS uuid[]))` | VIẾT MỚI (chưa có tiền lệ) | Grep `unnest`/`INSERT … RETURNING` = 0 kết quả → `[cần xác nhận #2/#4]`, kiểm ở giai đoạn tích hợp |
| Đăng ký `afterCommit` | DÙNG LẠI (gián tiếp) | Frame đi qua port `ChatRealtimeGateway.publishAfterCommit` (MATCH-001); không viết `TransactionSynchronization` riêng |
| `VenueSelectionService#markSelected` | DÙNG LẠI | Gọi `markSelected({proposedVenueId}, now)` lúc hoàn tất 2.7 (chữ ký `LocalDateTime` — SYSTEM-002) |
| Event record (payload id + thời điểm) | DÙNG LẠI mẫu | `application/event/venue/VenueAdminAlertEvent`; `PairingCreatedEvent` mới trong `application/event/match` |
| `PairingRules.MAX_DEPOSIT_HOLDER = 100` | VIẾT MỚI | Grep `Rules` = 0 kết quả; `MatchRules` (MATCH-001) giữ `MAX_CUSTOM_ITEMS`, `MAX_AMOUNT` |
| `PairingGuard`, `PairingSide` | VIẾT MỚI | Không có guard pairing; mẫu `TeamGuard`, `VenueGuard` |
| Hằng chat, policy, port realtime, `MatchGuard`, `MatchExpiry`, `MatchWriteOutcome`, `TeamGuard.requireActiveCaptain` | DÙNG LẠI (sở hữu MATCH-001) | Chưa có trên `main` → phụ thuộc tạm |
| `AppTime`, `Clock` bean | DÙNG LẠI (sở hữu SYSTEM-002) | Chưa có trên `main` (grep = 0) → phụ thuộc tạm |

## Kế hoạch chi tiết

### Ước tính số truy vấn (NFS §3 — đọc trước khi lập kế hoạch)
Theo `Processing_Flow.md` §3 (MATCH-002) và MATCH-001 `Processing_Flow.md` §3 — mọi endpoint trong ngưỡng, **không** phải dừng:

| Endpoint | Một lần thử | Ngưỡng |
|---|---|---|
| 2.1 `POST /matches/{id}/pairings` | 6 (5 khi cuộc trò chuyện đã có) · [lazy] 4 | W6 ✅ (= ngưỡng) |
| 2.2 / 2.3 | 3 | L4 ✅ |
| 2.4 | 2 | D3 ✅ |
| 2.5 | 3 (2 khi không đổi / `VENUE_002`) | W6 ✅ |
| 2.6 | 5 (3 khi rỗng) | W6 ✅ |
| 2.7 một phía / hoàn tất / race | 2 / 5 / 2 + 5 (mỗi lần thử ≤ 5) | W6 ✅ |
| MATCH-001 2.1 kèo B · 2.3 kèo B · 2.5 `OPEN` kèo B · job hết hạn | 6 · 2–3 · 4 · 3–4/lô | W6 / D3 / W6 ✅ |

[Inference] Số trên lấy từ thiết kế, chưa đo; đo bằng `NfsQueryCountTest` ở giai đoạn tích hợp. Rủi ro: nếu `#4` không chạy được (dự phòng `@Modifying` + đọc lại) → MATCH-001 2.1 kèo B = 7 > W6 → **dừng, hỏi user**.

### Phase (giai đoạn 2 = P0–P6; giai đoạn tích hợp = P7)

**P0 — Phụ thuộc tạm (chỉ để biên dịch / unit test).** Danh sách: mục "Phụ thuộc tạm". Không test (không phải logic của mã này). Thân method thuộc mã khác = `throw new UnsupportedOperationException(...)`; unit test của mã này mock các bean đó.

**P1 — Cổng chặn `#5` + chuẩn bị `#2/#4`.**
- Red → Green: `application/src/test/.../support/ConcurrencyConflictRetrierTest` (sửa) — ném `ConcurrencyConflictException` lần 1 → thử lại, lần 2 thành công trả giá trị; ném cả 3 lần → `BusinessException(MATCH_014)`; mỗi lần thử một transaction mới.
- Tạo: `application/support/ConcurrencyConflictException.java`. Sửa: `application/support/ConcurrencyConflictRetrier.java`.
- Viết (chạy ở tích hợp): `api/src/test/.../migration/PairingReturningSpikeTest` — một `INSERT … RETURNING` (`insertAllReturning`) + một `UPDATE … RETURNING` (`confirmSide`) không `@Modifying`, bind `uuid[]`/`timestamp[]`.

**P2 — V8 + entity + mã lỗi.**
- Test viết trước (unit): `common/.../ErrorCodeTest` (sửa: 8 mã `PAIRING_001..008` — HTTP + message theo `API_Spec.md` §5); `domain/.../entity/match/PairingEntityTest` (mới: builder `revision = 0`, `status`; `@PrePersist` gán `createdAt = updatedAt = AppTime.now()`; `@PreUpdate` đổi `updatedAt`; field chi phí/cọc/phương án); `domain/.../entity/match/MatchFlowBEntityTest` (mới: `Match` không còn `confirmedVenueId`/`confirmedMatchTime`, có `costDepositAmount`; `CostCustomItem.matchId` nullable + `pairingId`; `MatchVenueOption.displayOrder`) — kiểm bằng builder/getter + annotation `@Column(nullable)` qua reflection.
- Tạo: `api/src/main/resources/db/migration/V8__match_flow_b_pairing.sql` (đúng CCD §5.1).
- Sửa: `common/exception/ErrorCode.java` (+`PAIRING_001..008`); `domain/entity/match/Pairing.java`, `Match.java`, `MatchVenueOption.java`, `CostCustomItem.java` (CCD §6; thời gian `LocalDateTime`, callback `AppTime.now()`).
- Viết (chạy ở tích hợp): `api/src/test/.../migration/MatchFlowBPairingMigrationTest`.

**P3 — Repository + projection.** (SQL chỉ kiểm được với DB → test viết trước nhưng chạy ở tích hợp; Red không xác nhận được ở giai đoạn 2 — xem Blockers.)
- Tạo projection (`domain/projection`): `PairingContextView`, `PairingDetailView`, `PairingSummaryView`, `ProposalCandidateView`, `ConfirmSideResult`, `TimeOptionView`, `MatchOptionRowView`.
- Tạo repository: `PairingRepository` (`findContext`, `findDetailById`, `findByMatch`, `findByTeam` + `countQuery`, `findProposalCandidate`, `updateProposal`, `updateCost`, `confirmSide` — hai câu cố định theo `side`, không nối chuỗi, `finalizeConfirmation`, `supersedeNegotiating`, `expireNegotiatingByMatchIds`), `MatchVenueOptionRepository` (`insertAll`, `findOptionRows`), `MatchTimeOptionRepository` (`insertAllReturning`).
- Sửa: `CostCustomItemRepository` (+`findByPairingIdOrderByDisplayOrderAsc`, `deleteByPairingIdBulk`, `insertAllForPairing`, `copyPairingItemsToMatch`), `MatchRepository` (+`confirmPairing`). Mọi `:now` truyền từ Java; không `now()` trần.
- Viết (chạy ở tích hợp): `PairingRepositoryTest`, `MatchOptionRepositoryTest`, `CostCustomItemPairingRepositoryTest`, `MatchConfirmPairingRepositoryTest` (chi tiết ở mục test tích hợp).

**P4 — Support, validation, DTO, mapper, event, frame.**
- Test viết trước (unit): `application/.../support/PairingGuardTest` (`requireSide` → `PAIRING_002`; `requireCaptain` DISBANDED → `TEAM_016`, INACTIVE && !allowInactive → `TEAM_015`, không captain → `TEAM_002`; `requireNegotiating` → `PAIRING_005`; `requireRevision` → `PAIRING_006`; `requireVenueActive("INACTIVE")` → `VENUE_002`); `application/.../dto/request/PairingRequestValidationTest` (`teamId`, `revision ≥ 0`, `venueId`, `timeOptionId` bắt buộc; `depositAmount` 1..1 000 000 000; `depositHolder` ≤ 100; `@ValidDeposit` holder khác null mà amount null → lỗi gắn `depositHolder`; ≤ 10 `customItems`); `application/.../mapper/PairingMapperTest` (`captainName` che "Người dùng đã xóa" / `null` khi đội giải tán; `proposal = null` khi chưa chốt; `customItems` đúng thứ tự; `confirmedAt` ↔ `finalConfirmedAt`; response 2.1 có `conversationId`, chi phí `null`, `customItems = []`); `PairingFilterTest` (`toQueryValue`).
- Tạo: `application/service/PairingStatusFilter`, `PairingRoleFilter`, `PairingSide`; `application/support/match/PairingGuard`, `PairingRules`; `application/validation/CostAgreementFields`, `ValidDeposit`, `ValidDepositValidator`; `application/dto/request/CreatePairingRequest`, `UpdateProposalRequest`, `UpdatePairingCostAgreementRequest`, `ConfirmPairingRequest`; `application/dto/response/PairingResponse`, `PairingTeamResponse`, `ProposalResponse`, `PairingCostAgreementResponse`, `PairingSummaryResponse`, `ProposalUpdateResponse`, `PairingCostUpdateResponse`, `PairingConfirmationResponse`; `application/mapper/PairingMapper` (`unmappedTargetPolicy = ERROR`, `uses = MapperSupport.class`); `application/event/match/PairingCreatedEvent`; `application/support/chat/PairingChange` (enum) + `RealtimeFrame.PairingUpdated` (thêm vào bản tạm — xem Phụ thuộc tạm).
- Sửa: `application/validation/ValidCostAgreement` (bản tạm) → `@Target` áp cho `CostAgreementFields`.

**P5 — `PairingService` + `PairingServiceImpl`.**
- Test viết trước (unit, Mockito, `Clock.fixed(…, AppTime.ZONE)`, retrier thật + `TransactionTemplate` giả chạy callback): `application/.../service/PairingServiceImplTest`:
  - Thứ tự guard từng endpoint đúng `Processing_Flow.md` §1.1 (mỗi mã lỗi một ca, kiểm không ghi).
  - 2.1: `PAIRING_004`, `MATCH_016`, [lazy] → commit + `MATCH_004` (qua `MatchWriteOutcome`), `MATCH_013`, `SPORT_002`; `saveAndFlush` vi phạm `idx_pairing_active` → `PAIRING_003`; vi phạm khác → ném lại; `existingConversationId` có → không `save` cuộc trò chuyện; `null` → `save` `Conversation` `TEAM_MATCH` (A = đội tác giả, B = đội nhận); `forceIncrementVersion` + `PairingCreatedEvent` (payload); response `conversationId`.
  - 2.2: `MATCH_001`, `TEAM_016`/`TEAM_002`, `MATCH_016`; truyền `status.toQueryValue()`, `now`. 2.3: `requireCaptainAllowInactive` + `role`/`status`.
  - 2.4: `PAIRING_001`, cả hai cờ false → `PAIRING_002`; đọc khoản tự nhập theo pairing.
  - 2.5: `revision` cũ → `PAIRING_006` kể cả cùng phương án; `PAIRING_008`; `VENUE_002`; cùng phương án → không ghi, không frame; `updateProposal` 0 dòng → thử lại; thành công → frame `PROPOSAL` (`revision + 1`, xác nhận `null`).
  - 2.6: rỗng → bỏ `insertAllForPairing` + đọc lại; có khoản → `trim`, `""` → `null`; frame `COST`; 0 dòng → thử lại.
  - 2.7: `PAIRING_007`, `VENUE_002`, `MATCH_013`, `SPORT_002`; phía mình đã xác nhận → trả ctx, không ghi; một phía → `confirmSide` → frame `CONFIRMATION`, `NEGOTIATING`; `confirmSide` thấy phía kia khác `null` → `ConcurrencyConflictException` → lần thử sau đi nhánh hoàn tất; hoàn tất: `confirmPairing` 0 dòng → thử lại; `finalizeConfirmation` thiếu `pairingId` → thử lại; `copyPairingItemsToMatch` + `markSelected({proposedVenueId}, now)` + `MatchConfirmedEvent(acceptedRequestId = null, pairingId)` + frame `CONFIRMED`; hết 3 lần → `MATCH_014`; `conversationId = null` trong ctx → `IllegalStateException`.
- Tạo: `application/service/PairingService`, `application/service/impl/PairingServiceImpl` (không `@Transactional` mức class; đọc `readOnly`; ghi qua retrier trả `MatchWriteOutcome`; `now = LocalDateTime.now(clock)` một lần đầu mỗi lần thử).

**P6 — Controller.**
- Tạo: `api/pairing/MatchPairingController` (2.1 → 201 không `Location`, 2.2), `TeamPairingController` (2.3), `PairingController` (2.4–2.7); `@Validated`, `@CurrentUser UUID`, `@RequestParam` enum (sai → 400).
- Test (repo dùng `ApiIntegrationTest` cho controller — cần DB): viết `api/src/test/.../api/pairing/PairingControllerTest` (mã thành công, envelope, 400 path/enum/body, 401), chạy ở tích hợp.

**P7 — Giai đoạn tích hợp (cần user chốt cách làm — Blockers #1).** Sau khi SYSTEM-002 và MATCH-001 đợt 1 merge `main`: rebase, bỏ phụ thuộc tạm (lấy bản mã sở hữu), áp lại delta MATCH-002 lên `Match`/`CostCustomItem`/`Pairing`/`MatchVenueOption`, rồi TDD các điểm cắm bảng dưới, chạy toàn bộ test tích hợp.

**Điểm cắm vào MATCH-001 (nhánh luồng B — làm ở P7):**

| # | Thành phần MATCH-001 | Việc | Test (viết trước) |
|---|---|---|---|
| 1 | `CreateMatchRequest`, `@ValidMatchSchedule` | Nhánh B: field A phải `null`, 1..5 địa điểm không trùng, 1..5 khung giờ không trùng `startsAt`, `MIN_LEAD_TIME`/`MAX_DURATION`, `expiresAt` ∈ [sớm nhất − 7 ngày, sớm nhất] | Unit validator |
| 2 | `MatchServiceImpl.createMatch` | Bỏ chặn `MATCH_016`; nhánh `NEED_VENUE`: `requireSelectable` → `save` (giờ = khung sớm nhất) → `insertAll` → `insertAllReturning` → `MatchMapper.toCreatedNeedVenue` (không `markSelected`, không event) | Unit + `NfsQueryCountTest` 2.1 kèo B = 6 |
| 3 | `MatchRepository.searchOpen` / `searchOpenNearby` / `findTeamMatches` | `venueOptionCount`/`timeOptionCount`, `EXISTS` khung giờ, nearby `LATERAL` trên `match_venue_option` | Integration |
| 4 | `findDetailById`, `getMatch`, `MatchMapper` | `LEFT JOIN pairing` (`confirmedPairingId`), `cost_deposit_amount`; `findOptionRows`; overload `toResponse(…, List<MatchOptionRowView>)` | Unit mapper + integration |
| 5 | `cancelMatch` `OPEN` | Kèo B: bỏ `MATCH_010`, sau CAS → `supersedeNegotiating` | Unit + race integration |
| 6 | `MatchExpiry.expireIfDue`, job `claimDueForExpiry` | `NEED_VENUE` → `expireNegotiatingByMatchIds`; claim trả `type`, tách lô | Unit + integration |
| 7 | `MatchCancellation.cancelConfirmed` | Kèo B: không `withdrawAccepted`; trừ điểm theo khung giờ đã chốt | Unit + integration |
| 8 | `TeamMatchConversationPolicy`, `ConversationSql`, `ConversationContextView`, `ConversationMapper` | Nhánh `NEED_VENUE` (`waiting` = pairing `NEGOTIATING`); join `pairing`; `pairingId`, `pairingStatus`, `pairingEffectiveStatus`; 2.19 trả `pairingId`, `pairingStatus` | Unit policy + integration chat B |
| 9 | `UpdateCostAgreementRequest` | `implements CostAgreementFields` | Unit validation cả hai request |
| 10 | `StompChatRealtimeGateway` | Tuần tự hoá `type = PAIRING_UPDATED` | Integration STOMP |
| 11 | Tài liệu (CCD §11, `[cần xác nhận #16]`) | MATCH-001 `API_Spec.md` gỡ `MATCH_016` của 2.1 + CHANGELOG; `docs/database/*`; SRS SYSTEM-001 18 dòng (coordinator → business-analyst) | — |

## Phụ thuộc tạm (thuộc mã khác)
Bản tối thiểu, đúng package / tên / chữ ký theo thiết kế của mã sở hữu, **không** logic nghiệp vụ của mã đó. Khi tích hợp: bản của mã sở hữu thắng, rồi áp lại delta MATCH-002 (cột "Delta MATCH-002 cần giữ").

| File | Mã sở hữu | Phần tạo (tạm) | Delta MATCH-002 cần giữ khi tích hợp |
|---|---|---|---|
| `common/.../common/time/AppTime.java` | SYSTEM-002 | `ZONE_ID`, `ZONE`, `now()`, `today()` | — |
| `api/.../api/config/ClockConfig.java` | SYSTEM-002 | Bean `Clock` zone VN, tick 1 µs | — |
| `domain/.../entity/match/{Match, Pairing, MatchVenueOption, MatchTimeOption, CostCustomItem}.java` | SYSTEM-002 (thời gian) · MATCH-001 (`Match.version`, `CostType`, `CostSplit`; `CostCustomItem.displayOrder`) | `OffsetDateTime` → `LocalDateTime` + `AppTime.now()` trên 5 entity này; `@Version Long version`, enum `CostType`/`CostSplit` (+ `costType`/`costSplit` kiểu enum), `CostCustomItem.displayOrder` | CCD §6: `Pairing` (+revision, updatedAt, phương án, chi phí/cọc), `Match` (− 2 cột chốt, + `costDepositAmount`), `MatchVenueOption.displayOrder`, `CostCustomItem` (`matchId` nullable, + `pairingId`) |
| `application/.../service/VenueSelectionService.java`, `impl/VenueSelectionServiceImpl.java`, `domain/.../repository/VenueRepository#markSelected` | SYSTEM-002 | Tham số `selectedAt` `OffsetDateTime` → `LocalDateTime` (đổi kiểu, không đổi logic — user chốt #3 2026-11-21); test đi kèm: `VenueSelectionServiceImplTest`, `VenueSelectionIntegrationTest`, `VenueStaleAlertRaceTest` (chỉ đổi kiểu) | — |
| `api/.../api/config/ClockConfig.java` + `PairingServiceImpl` | SYSTEM-002 | `PairingServiceImpl` inject `Clock` bean (có ở bản tạm) | — |
| `common/.../exception/ErrorCode.java` | MATCH-001 | `MATCH_001`, `MATCH_003`, `MATCH_004`, `MATCH_005`, `MATCH_013`, `MATCH_014`, `MATCH_016`, `SPORT_002` (message theo MATCH-001 `API_Spec.md` §5) | `PAIRING_001..008` |
| `domain/.../entity/chat/Conversation.java` | MATCH-001 | Entity theo MATCH-001 CCD §6 (`ContextType { TEAM_MATCH }`, `PartyType`, `Party`, id v7, `createdAt`) | — |
| `domain/.../repository/ConversationRepository.java` | MATCH-001 | `extends JpaRepository<Conversation, UUID>` | — |
| `domain/.../repository/MatchRepository.java` (+ `MatchRepositoryCustom`, `MatchRepositoryImpl`) | MATCH-001 | `forceIncrementVersion(Match)` (Impl: `entityManager.lock(OPTIMISTIC_FORCE_INCREMENT)`); `findAvailability`, `findAuthorAccess` đặt tạm trên fragment `MatchRepositoryCustom` (Impl ném `UnsupportedOperationException`) để Spring Data không suy query từ tên — bản MATCH-001 khai báo trên `MatchRepository` | `confirmPairing` (trên `MatchRepository`) |
| `domain/.../repository/CostCustomItemRepository.java` | MATCH-001 | `extends JpaRepository<CostCustomItem, UUID>` | 4 method pairing (P3) |
| `domain/.../projection/{TeamCaptainContextView, MatchAvailabilityView, MatchAuthorAccessView}.java` | MATCH-001 | Getter theo MATCH-001 CCD §5.3 | — |
| `application/.../support/{MatchGuard, MatchExpiry, MatchWriteOutcome}.java` | MATCH-001 | Chữ ký §4.5–§4.7; thân method guard/expiry = `UnsupportedOperationException`; `MatchWriteOutcome` (sealed, `Done`/`Expired`, `valueOrThrowExpired`) | — |
| `application/.../support/TeamGuard.java` | MATCH-001 | + `requireActiveCaptain(teamId, userId): TeamCaptainContextView`, `requireCaptainAllowInactive(teamId, userId)` (thân `UnsupportedOperationException`) | — |
| `application/.../support/chat/{ChatRealtimeGateway, RealtimeFrame}.java` | MATCH-001 | Port `publishAfterCommit(UUID, RealtimeFrame)`; `sealed interface RealtimeFrame` (không kèm `MessageCreated` — phụ thuộc DTO chat MATCH-001) | `RealtimeFrame.PairingUpdated`, `PairingChange` |
| `application/.../event/match/MatchConfirmedEvent.java` | MATCH-001 | Record `matchId, acceptedRequestId, pairingId, authorTeamId, opponentTeamId, occurredAt` | — |
| `application/.../support/match/MatchRules.java` | MATCH-001 | `MAX_CUSTOM_ITEMS = 10`, `MAX_AMOUNT = 1_000_000_000` | — |
| `application/.../validation/{ValidCostAgreement, ValidCostAgreementValidator}.java` | MATCH-001 | Annotation + validator **không** kiểm quy tắc (luôn hợp lệ) | `@Target` cho `CostAgreementFields` |
| `application/.../dto/request/CostCustomItemRequest.java`, `dto/response/CostCustomItemResponse.java` | MATCH-001 | Record theo MATCH-001 `API_Spec.md` 2.4 (`itemName`, `amount` + ràng buộc; `itemId, itemName, amount`) | — |

## Test để giai đoạn tích hợp
Viết ở giai đoạn 2, **chỉ chạy** ở giai đoạn tích hợp (cần DB dev / Spring context / Flyway V6+V7+V8). Lệnh: `. ./.env.dev && export DATABASE_URL DATABASE_USERNAME DATABASE_PASSWORD JWT_SECRET && ./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -Dtest=<Test> test`.

Đã viết (biên dịch được, **chưa chạy**) — file ở `api/src/test/java/com/phananh/arenaflow/`; fixture chung `fixture/PairingFixtures` (có `deleteAll()` cho test commit thật):

| Test (đã viết) | Nội dung |
|---|---|
| `domain/repository/PairingReturningSpikeTest` | `#2` bind `uuid[]`/`timestamp[]` vào `unnest`; `#4` `INSERT … RETURNING` + `UPDATE … RETURNING` không `@Modifying` trả projection — **chạy đầu tiên**; fail → dừng, hỏi user |
| `migration/MatchFlowBPairingMigrationTest` | V8 sau V7; `ddl-auto=validate` khi khởi động; cột/index; CHECK kèo B ↔ địa điểm, cọc, phương án đủ cặp; FK hai cột; một CONFIRMED mỗi kèo; chủ khoản tự nhập (savepoint mỗi câu vi phạm) |
| `domain/repository/PairingRepositoryTest` | `findContext` (1 câu), `findDetailById` (đội trưởng hiện tại, đã xoá, đội không còn đội trưởng), trạng thái hiệu lực EXPIRED không ghi DB, `findByMatch`/`findByTeam` (role, status, thứ tự, count), `findProposalCandidate`, CAS `updateProposal`/`updateCost`, `finalizeConfirmation`, `supersedeNegotiating`, `expireNegotiatingByMatchIds` |
| `domain/repository/MatchFlowBRepositoryTest` (gộp 3 file dự kiến) | `findOptionRows`; `insertAllForPairing`/`deleteByPairingIdBulk`/`copyPairingItemsToMatch` (thứ tự, id mới, `pairing_id = NULL`, 0 dòng khi rỗng); `confirmPairing` CAS + ghi gói + `version + 1` |
| `application/service/PairingConfirmRaceTest` (commit thật) | Hai phía xác nhận song song (5 vòng); X–P và X–Q cùng xác nhận (5 vòng, không `40P01`, không `MATCH_014`, đúng một CONFIRMED); `revision` cũ sau sửa gói → `PAIRING_006` + xác nhận bị xoá; hoàn tất ghi kèo + chép khoản + `markSelected`; lazy 2.7 → `MATCH_004` + kèo/pairing EXPIRED |
| `application/service/PairingNfsQueryCountTest` (commit thật, file mới thay vì sửa `NfsQueryCountTest`) | 2.1 = 6 (5 khi cuộc trò chuyện đã có), 2.4 = 2, 2.2/2.3 = 3, 2.5 = 3 (2), 2.6 = 5 (3), 2.7 = 2 / 5 |
| `api/pairing/PairingControllerTest` (`ApiIntegrationTest`) | 201 không `Location`; 200 + envelope 2.2–2.7; 400 (UUID path, enum query, body); 403 `PAIRING_002`; 409 `PAIRING_006`; 401 |

Còn phải viết ở giai đoạn tích hợp (cần code MATCH-001 thật):

| Test (chưa viết) | Nội dung |
|---|---|
| Race bổ sung | Hai pairing hoàn tất song song; hoàn tất vs sửa gói chen giữa; tạo pairing vs hoàn tất; huỷ `OPEN` khi có NEGOTIATING / vs hoàn tất; `VENUE_002` ở 2.5/2.7 (kèo vẫn `OPEN`, `last_selected_at` không đổi); 1 `MatchExpiredEvent` khi lazy |
| Quy tắc chi phí của pairing (chốt #6) | `@ValidCostAgreement` thật trên `UpdatePairingCostAgreementRequest` |
| Chat luồng B (MATCH-001 2.16, 2.19) | Nhắn được / chỉ xem theo pairing; gửi tin trên kèo quá hạn → `MATCH_004` + EXPIRED; 2.19 `pairingId`, `pairingStatus` |
| Realtime STOMP | Frame `PAIRING_UPDATED` tới topic cuộc trò chuyện (2.5, 2.6, 2.7) |
| Luồng B trong MATCH-001 (P7) | Tạo / tìm / xem / huỷ / hết hạn kèo `NEED_VENUE`; NFS MATCH-001 2.1 kèo B = 6, 2.3 kèo B = 2–3, 2.5 `OPEN` kèo B = 4 |

Unit test đã chạy ở giai đoạn 2 (PASS 2026-11-21, 105/105): `ErrorCodeTest` 5, `PairingEntityTest` 4, `MatchFlowBEntityTest` 3, `EntityIdGenerationTest` 1, `ConcurrencyConflictRetrierTest` 12, `PairingGuardTest` 5, `PairingRequestValidationTest` 9, `PairingFilterTest` 1, `PairingMapperTest` 4, `PairingServiceImplTest` 36, `VenueSelectionServiceImplTest` 5, `TeamGuardTest` 6, `MapperTest` 11, `VenueMapperTest` 3.

## Câu hỏi chờ duyệt
_Quy tắc user 2026-11-21: câu hỏi chặn (lệch API/DB/nghiệp vụ, thiếu thông tin) → ghi `file:line` · vấn đề · phương án rồi DỪNG; câu hỏi không chặn → ghi rồi làm tiếp._

| # | Chặn? | Vị trí | Vấn đề | Phương án |
|---|---|---|---|---|

Giai đoạn 2 (P0–P6): không có câu hỏi mở. Các điểm #1–#6 của kế hoạch đã được user chốt 2026-11-21.

## Blockers / Ghi chú
Điểm cần user chốt trước khi code (không tự quyết):
1. **Cách làm nhánh luồng B trong thành phần MATCH-001** — đề xuất: giai đoạn 2 chỉ làm phần MATCH-002 sở hữu (P0–P6) + phụ thuộc tạm; mọi điểm cắm vào MATCH-001 (bảng P7) làm ở giai đoạn tích hợp trên nhánh này, sau khi MATCH-001 đợt 1 vào `main`. Phương án khác: (b) viết luôn nhánh B vào bản tạm của class MATCH-001 → nhân đôi logic MATCH-001, xung đột lớn khi tích hợp (không khuyến nghị); (c) chồng nhánh lên `feature/MATCH-001-match-flow-a-impl` khi nhánh đó có commit.
2. **5 entity dùng chung** (`Match`, `Pairing`, `MatchVenueOption`, `MatchTimeOption`, `CostCustomItem`) bị SYSTEM-002, MATCH-001 và MATCH-002 cùng sửa → đề xuất đổi tạm toàn bộ 5 file sang `LocalDateTime` + delta MATCH-001 cần biên dịch, giải xung đột khi tích hợp theo cột "Delta MATCH-002 cần giữ".
3. **`VenueSelectionService.markSelected`** đang nhận `OffsetDateTime` trên `main`; thiết kế gọi với `LocalDateTime` (SYSTEM-002 đổi) → đề xuất đổi kiểu tạm ở interface + impl + `VenueRepository#markSelected` (không đổi logic).
4. **`[cần xác nhận #4]` / `#2` là "chặn code — bước đầu phase 1"** nhưng worktree không có `.env.dev` → không kiểm được ở giai đoạn 2. `UPDATE … RETURNING` không `@Modifying` đã kiểm ở VENUE-001 (#11); `INSERT … RETURNING` và bind mảng `unnest` **chưa** có tiền lệ. Đề xuất: code theo thiết kế, kiểm `PairingReturningSpikeTest` đầu tiên ở tích hợp; fail → dừng, hỏi user (dự phòng làm MATCH-001 2.1 kèo B = 7 > W6).
5. **Package `ConcurrencyConflictException`** chưa có trong thiết kế → đề xuất `application/support/ConcurrencyConflictException` (cạnh retrier). REPUTATION-001, MERCENARY-001 (chạy song song) cũng dùng → coordinator báo cùng FQN cho các agent đó; thay đổi retrier do MATCH-002 sở hữu.
6. **`@ValidCostAgreement`** thuộc MATCH-001 → bản tạm không kiểm quy tắc; test quy tắc chi phí cho `UpdatePairingCostAgreementRequest` dời sang tích hợp.

Rủi ro / ghi chú:
- TDD ở P3 (SQL native) và P6 (controller — mẫu repo là `ApiIntegrationTest`): test viết trước nhưng **không** xác nhận được Red/Green ở giai đoạn 2.
- V8 phụ thuộc cột / index của V7 (`cost_custom_item.display_order`) → nhánh này một mình không khởi động được Spring context (Flyway fail) cho tới khi rebase lên `main` có V6 + V7.
- Unit test `PairingServiceImplTest` mock chữ ký MATCH-001 theo thiết kế → chạy lại sau khi thay bản tạm.
- Không sửa tài liệu thiết kế ở giai đoạn 2; SRS SYSTEM-001 (CCD §11, 18 dòng) do coordinator giao business-analyst.
