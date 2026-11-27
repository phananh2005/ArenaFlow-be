---
task: REPUTATION-001 — đánh giá trận, report đội & tranh chấp, uy tín đội (V9)
created: 2026-11-21
updated: 2026-11-26
percent: 70
status: in_progress
---

## Mô tả
Triển khai REPUTATION-001 theo thiết kế đã duyệt (v2.0) trên nhánh `feature/REPUTATION-001-reputation-impl` (tạo từ `origin/main` `6fe6285`): migration V9, 10 endpoint (2.1–2.10), mở rộng `ReputationService` (hoàn tất trận, trừ/hoàn điểm report), huy hiệu xác minh (refresh sau commit + job hằng ngày), domain event cho NOTIFICATION-001.

Bối cảnh (user chốt 2026-11-21): 7 mã còn lại implement **song song**, mỗi mã một worktree/nhánh từ `origin/main`. Chuỗi migration: SYSTEM-002 (V6) → MATCH-001 đợt 1 (V7) → MATCH-002 (V8) → **REPUTATION-001 (V9)** → MERCENARY-001 (V10) → NOTIFICATION-001 (V11) → USER-003 (V12). Class của mã đứng trước **chưa có** trên `main` → tạo bản tối thiểu (mục "Phụ thuộc tạm"). Test cần DB / Spring context / migration / NFS viết ở giai đoạn 2 nhưng chỉ **chạy** ở giai đoạn tích hợp (worktree không có `.env.dev`).

## Thiết kế liên quan
- `docs/agent/design/REPUTATION-001/` — `API_Spec.md`, `Component_Class_Design.md` (CCD), `Processing_Flow.md` (PF) v2.0, `CHANGELOG.md`.
- MATCH-001 CCD §4.5 (`TeamGuard`), §4.8 (`ReputationService` lõi), §4.9 (event), §4.11 (`AdminAuditLog` overload), §4.14 (`ConversationAccessPolicy`), §5.2 (`ConversationSql`, `findCaptainContext`, `adjustReliability`), §5.3 (`ConversationContextView`, `TeamCaptainContextView`), §6 (`Conversation`).
- MATCH-002 CCD §4.7, §12 #4 (`UPDATE … RETURNING` không `@Modifying`), #5 (`ConcurrencyConflictException`).
- MERCENARY-001 CCD §4.10, §4.12 — các sửa đổi **của MERCENARY-001** lên REPUTATION-001 (REPUTATION-001 CCD §14): KHÔNG làm ở mã này.
- SYSTEM-002 CCD §2 (`AppTime`, `ClockConfig`), `docs/agent/convention-backend.md` §6.2.
- ADR-0002, ADR-0003 (Sửa đổi 2026-11-10/12), ADR-0008, ADR-0011, ADR-0012, ADR-0013 (Sửa đổi 2026-11-12, 2026-11-21), ADR-0017.
- `docs/agent/non-functional-standards.md` — N_DETAIL=3, N_LIST=4, N_WRITE=6 (đã đọc trước khi lập kế hoạch).

## Definition of Done
- [ ] Migration `V9__reputation_rating_report_dispute.sql` đúng CCD §5.1 (chạy thật ở giai đoạn tích hợp, sau V6–V8).
- [ ] 10 endpoint 2.1–2.10 đúng `API_Spec.md` (status, body, mã lỗi, thứ tự guard PF §1.1).
- [ ] `ReputationService` + `applyMatchCompletion` / `applyReportPenalty` / `refundReportPenalty`; bảng kiểm PF §3.1, §3.3 PASS (integration, giai đoạn tích hợp).
- [ ] `TeamVerificationRefresher` (2 test bắt buộc CCD §4.8) + job đối soát `TeamVerificationJobService` / `TeamVerificationScheduler`.
- [ ] Số truy vấn mỗi API ≤ ngưỡng (bảng PF §3) — đo bằng `ReputationNfsQueryCountTest` ở giai đoạn tích hợp.
- [ ] Unit test thuần PASS ngay trên nhánh (dán output); integration test đã viết, chạy PASS ở giai đoạn tích hợp.
- [ ] `code-reviewer` không còn CRITICAL/MAJOR; đối chiếu design + bảng "Lệch thiết kế" → cập nhật design một lần → `architect-reviewer`.
- [ ] Mọi comment tiếng Việt, không trỏ tài liệu (`convention-backend.md` §2.4); thời gian `LocalDateTime` giờ VN (§6.2).

## Các bước thực hiện (tổng quan)
- [x] 1. Đối chiếu & phân tích ảnh hưởng (spec / convention / kiến trúc / NFS)
- [x] 2. Lập kế hoạch chi tiết — file cần sửa (mục dưới) — **chờ user duyệt**
- [x] 3. Triển khai code + test theo TDD (Phase 0–8 + phần unit / viết test tích hợp của Phase 9)
- [ ] 4. Chạy unit test & xác nhận PASS (dán output); integration test chạy ở giai đoạn tích hợp — unit PASS 110/110 (2026-11-21); integration chưa chạy
- [x] 5. `code-reviewer` lượt 1 (0C / 2M / 3m) — user chốt từng câu 2026-11-26 (`pending-review-questions.md` repo chính)
- [x] 5a. Rebase lên `origin/main` `04b6136` (commit tạm `ef092df`, 26 conflict gộp theo nhóm — `progress.md` 2026-11-26); unit 471 PASS
- [ ] 5b. **P7 — tích hợp** (mục "Kế hoạch P7" bên dưới) — **chờ user duyệt**
- [ ] 6. Đối chiếu code với design → cập nhật design theo bảng "Lệch thiết kế" → `architect-reviewer` review phần sửa
- [ ] 7. Bước 6 có sửa code → test lại + `code-reviewer` phần sửa → commit (`code-reviewer` là bước cuối trước commit có code)

## Lệch thiết kế
_Ghi ngay khi code khác design; KHÔNG sửa design giữa chừng — cập nhật một lần ở bước cuối (`CLAUDE.md` §8). Trống = code khớp design._

| # | Phase | Design | Code thực tế | Lý do | Sửa ở | Design v2.1 |
|---|---|---|---|---|---|---|
| 1 | 2 | CCD §8 chỉ nêu key `arenaflow.reputation.verification.*` | Thêm record `support/reputation/TeamVerificationProperties(int batchSize)` | Theo mẫu `VenueAlertProperties` (cron đọc trực tiếp ở scheduler) | CCD §2, §8 | Đã cập nhật v2.1 (CCD §2, §8) |
| 2 | 2 | CCD §4.10 "`ReportStatusResolver` (static trong mapper hoặc `support/reputation`)" | Đặt ở `support/reputation`, 3 hàm `effectiveStatus` / `disputeDeadline` / `canDispute` | Dùng chung mapper + `DisputeServiceImpl` | CCD §2, §4.10 | Đã cập nhật v2.1 (CCD §2, §4.10) — code thực tế chỉ `ReputationMapper` gọi (không phải `DisputeServiceImpl`) |
| 3 | 2 | CCD §4.9 hàm private `resolveSides(ctx)` | `support/reputation/MatchSides` (record + `of`, `isCaptainOfAny`, `isCaptainOfBoth`, `isCallerTeamInactive`) + interface `domain/projection/MatchSidesView` (cha của `RatingContextView`, `ReportContextView`) | Lặp ở 2 service (reuse-scan) | CCD §2, §4.9, §5.3 | Đã cập nhật v2.1 (CCD §2, §4.9, §5.3) |
| 4 | 2 | CCD §4.6 hằng không có cửa sổ huỷ muộn | `ReputationRules.LATE_CANCEL_WINDOW_HOURS = 24` (tham số `:cancelWindowHours` của STATS) | Hằng truyền vào SQL bằng tham số | CCD §4.6 | Đã cập nhật v2.1 (CCD §4.6) |
| 5 | 3 | CCD §5.2 `findTeamReports(teamId, direction, limit, offset)` / `countTeamReports(teamId, direction)` | 4 method `findReceivedReports` / `findSentReports` / `countReceivedReports` / `countSentReports` | Hai câu cố định theo chiều (CCD tự yêu cầu không nối chuỗi) — một method không chọn được câu | CCD §5.2 | Đã cập nhật v2.1 (CCD §4.2, §5.2, §14; PF §3.4) |
| 6 | 3 | CCD §5.2 `findAdminDisputes(status, oldestFirst, limit, offset)` | `findAdminDisputesOldestFirst` / `findAdminDisputesNewestFirst`; `DisputeStatusFilter.oldestFirst()` | `ORDER BY` cố định mỗi câu, dùng được index (không `ORDER BY CASE`) | CCD §5.2 | Đã cập nhật v2.1 (CCD §5.2, §14; PF §3.6) |
| 7 | 3 | CCD §5.2 `findReputation(teamId, now)` | `findReputation(teamId, cancelWindowHours)`; `refreshVerification(ids, now, minCompleted, minAvg, minScore, cancelWindowHours)` | STATS không dùng `:now`, cần cửa sổ huỷ; Spring Data báo lỗi tham số khai báo mà không dùng | CCD §5.2 | Đã cập nhật v2.1 (CCD §4.5, §4.8, §5.2; PF §3.8) |
| 8 | 3 | CCD §5.3 STATS ghép theo `:teamId` | STATS là subquery LATERAL tham chiếu `tx.id` (bảng `team` ngoài alias `tx`); sao trung bình thêm `r.rater_type = 'TEAM'` | Một chuỗi dùng chung cho cả 1 đội và nhiều đội; sao chỉ của đội đối phương (API §4.8) | CCD §5.3 | Đã cập nhật v2.1 (CCD §5.3 — khối SQL STATS + `findReputation`) |
| 9 | 3 | CCD §5.3 `DisputeContextView` không có đội report | + `reporterTeamId` (cùng câu) | Payload `ReportDisputedEvent` cần `reporterTeamId` | CCD §5.3 | Đã cập nhật v2.1 (CCD §5.3) |
| 10 | 6 | CCD §5.3 `ReportContextView` | + `authorTeamName/AvatarUrl`, `opponentTeamName/AvatarUrl` (cùng câu) | Dựng `counterpartTeam` của response 2.3 không đọc lại | CCD §5.3 | Đã cập nhật v2.1 (CCD §5.3) |
| ~~11~~ | 6 | (P7 m2: code đã khớp thiết kế — nhánh thiếu policy → `IllegalStateException`) CCD §4.2 thứ tự 2.10: policy trả không bên nào → `CHAT_003` trước `REPORT_011` | Loại ngữ cảnh chưa có policy (khác `TEAM_MATCH`) → `REPORT_011`; có policy thì đúng thứ tự thiết kế | Trên nhánh chưa có policy mercenary; khi MERCENARY-001 thêm policy, nhánh fallback không còn chạy | CCD §4.2 (ghi chú) | Đã cập nhật v2.1 (CCD §4.2 ghi chú: thiếu policy → `IllegalStateException`; loại khác `TEAM_MATCH` có policy → `REPORT_011`) |
| 12 | 7 | PF §1.1 / §3.7 CAS 0 dòng → `existsTeamDispute` | `disputeRepository.existsById` (CCD §4.4) | Coordinator chốt C — `report.application_id` có từ V10; MERCENARY-001 đổi | PF §1.1, §3.7 | Đã cập nhật v2.1 (PF §1.1, §3 bảng NFS, §3.7) |
| 13 | 1 | CCD §6 `Rating.isNoShow` | Field Java `noShow` (cột `is_no_show`, getter `isNoShow()`, builder `.noShow()`); JSON vẫn `isNoShow` | Lombok sinh getter đúng tên cho field boolean | CCD §6 | Đã cập nhật v2.1 (CCD §6) |
| 14 | P7 | CCD §5.2 `findChatReportContext(conversationId, callerId)`, PF §3.3a Q1 | `findChatReportContext(conversationId, callerId, now)`; giờ trận từ `m` của `ConversationSql.CONTEXT_JOINS` (bỏ join `match` riêng) | Khối cột chung của MATCH-001 dùng `:now` (trạng thái hiển thị yêu cầu/thương lượng) | CCD §5.2, PF §3.3a | Đã cập nhật v2.1 (CCD §4.2, §5.2, §5.3; PF §3.3a) |
| 15 | P7 (review lượt 2 MAJOR) | PF §3 2.1 hoàn tất = 5/W6; PF §5 `[cần xác nhận #12]` chấp nhận chuỗi +1 khi report commit trong lúc hoàn tất trận | `TeamRepository#lockForReputation(ids)` khoá đội trước `applyCompletion` → câu cập nhật thấy report đã commit; 2.1 hoàn tất = **6/W6** | Race test tái hiện chuỗi = 1 thay vì 0 (đội bị report bùng vẫn được tính trận sạch); user chọn sửa (2026-11-26) | CCD §5.2, §4.8; PF §3 bảng NFS, §3.1, §5 (bỏ #12) | Đã cập nhật v2.1 (CCD §1, §4.6, §5.2, §10, §12; PF §2.1, §3 bảng NFS + gate, §3.1, §5, §9 — #12 đóng) |
| 16 | P7 | CCD §5.2 `refreshVerification` | `verified_at = CASE … THEN CAST(:now AS timestamp)` | PostgreSQL suy kiểu `text` cho CASE chỉ có tham số + NULL | — (chi tiết SQL, không đổi hợp đồng) | Đã cập nhật v2.1 (CCD §5.3 — câu SQL `refreshVerification` có trong CCD) |
| 17 | P7 (M1, review lượt 1) | CCD §5.2 `applyReportPenalty`: bậc theo "<bộ đếm loại đó> + 1" (không nói đọc ở đâu) | `next_count` tính trong CTE `locked` trên dòng đã khoá, bỏ quét `team` lần hai | Race test: hai report cùng loại đồng thời trừ 5 + 5 thay vì 5 + 8 | CCD §5.2; PF §3.3, §5 | Đã cập nhật v2.1 (ghi thêm khi đối chiếu) |
| 18 | 6 | CCD §5.3 `ChatReportContextView` + `partyAName/AvatarUrl`, `partyBName/AvatarUrl` | 4 cột đó thuộc `ConversationContextView` (MATCH-001); view chỉ thêm `matchStartTime`, `hasMessages`, `aReportedB`, `bReportedA` | Khối cột chung MATCH-001 đã có tên/ảnh hai bên | CCD §5.3 | Đã cập nhật v2.1 (ghi thêm khi đối chiếu) |
| 19 | 4 | CCD §4.8 `@Scheduled(cron = …, zone = AppTime.ZONE_ID)` | `@Scheduled(cron = …)` không `zone` | `convention-backend.md` §6.2 (ADR-0018) cấm `zone =` trong `@Scheduled` | CCD §4.8 | Đã cập nhật v2.1 (ghi thêm khi đối chiếu) |

_(Dự kiến ghi khi code: (a) `TeamVerificationProperties` record — CCD §8 chỉ nêu key cấu hình, không nêu class; (b) `ReportStatusResolver` đặt ở `support/reputation` — CCD §4.10 để mở "mapper hoặc support". Cả hai không đổi API/DB/nghiệp vụ.)_

## Tái sử dụng
Reuse Report (skill `reuse-scan`, 2026-11-21). **Nguồn quét:** `common/src/main/java`, `domain/src/main/java`, `application/src/main/java`, `api/src/main/java` (search text bằng `grep -rln` trên source hiện tại của nhánh, commit `6fe6285`). **Vùng mù:** code của SYSTEM-002 / MATCH-001 / MATCH-002 / MERCENARY-001 / NOTIFICATION-001 đang viết song song ở worktree khác (chưa có trên `main`) — chỉ đối chiếu qua tài liệu thiết kế.
**Từ khoá đã tra:** `Penalty`, `Reputation`, `Reliability`, `Verification|verified`, `TransactionSynchronization`, `bindResource`, `Rules`, `Resolver`, `StatusResolver`, `ConstraintValidator<`, `final class .*Sql`, `TransactionTemplate`, `REQUIRES_NEW`, `HALF_UP`, `unnest|uuid[]`, `RETURNING`, `Optional<UUID>`, `Direction`, `StatusFilter`, `Clock`.

| Thành phần cần | Phân loại | Nguồn / ghi chú |
|---|---|---|
| Thử lại xung đột / deadlock, hết lượt → mã riêng | **DÙNG LẠI** | `application/support/ConcurrencyConflictRetrier#execute(ErrorCode, Supplier)` → `execute(MATCH_014, …)` |
| Bắt `ConcurrencyConflictException` trong retrier (2.5 CAS 0 dòng) | **DÙNG LẠI (của MATCH-002)** | Retrier hiện chỉ bắt optimistic-lock + deadlock `40P01`; MATCH-002 §12 #5 thêm. Mã này chỉ tạo exception tối thiểu (Phụ thuộc tạm), KHÔNG sửa retrier |
| Đổi `DataIntegrityViolationException` theo tên constraint (`idx_rating_unique` → `RATING_002`, `idx_report_unique` → `REPORT_004`) | **DÙNG LẠI** | `application/support/DataIntegrityViolations#violates(ex, constraint)` |
| Phân trang | **DÙNG LẠI** | `application/support/PageRequestFactory#create(page, pageSize)`, `dto/response/PageResponse`, `PaginationMeta` |
| Validate enum dạng chuỗi (`reportType`, `decision`) | **DÙNG LẠI** | `application/validation/OneOf` |
| Ghi audit Admin sau commit | **MỞ RỘNG (của MATCH-001)** | `application/support/AdminAuditLog` — overload `afterCommit(action, adminId, targetType, targetId)` thuộc MATCH-001 §4.11 → Phụ thuộc tạm |
| Guard đội trưởng ACTIVE (2.2, 2.4) | **MỞ RỘNG (của MATCH-001)** | `application/support/TeamGuard` — `requireActiveCaptain` thuộc MATCH-001 §4.5 → Phụ thuộc tạm |
| Mẫu `TransactionSynchronization.afterCommit` | **DÙNG LẠI (mẫu)** | `AdminAuditLog` dùng `registerSynchronization`; `TeamVerificationRefresher` cần thêm `bindResource`/`unbindResource` gom id theo transaction → VIẾT MỚI theo cùng mẫu |
| Job lặp lô, mỗi lô một `TransactionTemplate`, lỗi lô log `ERROR` | **DÙNG LẠI (mẫu)** | `VenueAlertServiceImpl` (vòng lô + `TransactionTemplate`), `VenueStaleAlertScheduler` (`@Scheduled` cron, `-` = tắt) |
| Properties job (`batch-size`) | **VIẾT MỚI theo mẫu** | Mẫu `support/venue/VenueAlertProperties` (record `@ConfigurationProperties`) → `support/reputation/TeamVerificationProperties(int batchSize)` |
| `UPDATE … RETURNING` không `@Modifying` trả projection | **DÙNG LẠI (mẫu, đã chạy thật)** | `VenueRepository#markStaleAlerted` + `VenueStaleAlertRepositoryTest` (VENUE-001) — bằng chứng cho `[cần xác nhận #5]`; dạng `Optional<…>` một dòng còn kiểm ở integration |
| Làm tròn HALF_UP trong mapper | **DÙNG LẠI (mẫu)** | `VenueMapper` (`distanceKm`) — viết hàm trong `ReputationMapper` |
| Mapper MapStruct + hàm chung | **DÙNG LẠI** | `application/mapper/MapperSupport` (`uses`); tên user Admin KHÔNG qua `maskName` (ADR-0002 Sửa đổi 2026-11-12) |
| Filter enum query param → 400 | **DÙNG LẠI (mẫu)** | `TeamStatusFilter`, `VenueReportStatusFilter` + `GlobalExceptionHandler` (`MethodArgumentTypeMismatchException`) → `ReportDirection`, `DisputeStatusFilter` VIẾT MỚI cùng mẫu |
| Validator mức lớp gắn lỗi theo field | **DÙNG LẠI (mẫu)** | `validation/ValidGeoQuery` + `ValidGeoQueryValidator` → `ValidRating` + `ValidRatingValidator` VIẾT MỚI |
| `ReportPenaltyPolicy` (bảng bậc trừ) | **VIẾT MỚI** | Không có `Penalty*` trong source; `TimeBandPenaltyPolicy` (MATCH-001) là chính sách theo khung giờ, khác mô hình (bậc theo số lần) → không gộp |
| `ReputationRules` (hằng) | **VIẾT MỚI** | Không có `*Rules`; `MatchRules` (MATCH-001) thuộc miền khác |
| `ReportStatusResolver` (trạng thái hiệu lực) | **VIẾT MỚI** | Không có `*StatusResolver` |
| `TeamReputationSql.STATS` (SQL dùng chung) | **VIẾT MỚI** | Không có hằng SQL dùng chung; `ConversationSql` (MATCH-001) là mẫu cùng kiểu, khác nội dung |
| `resolveSides(ctx)` (rater/ratee, reporter/reported) | **VIẾT MỚI — helper chung** | Lặp ở `RatingServiceImpl` + `ReportServiceImpl` (2 context view có cùng cột `callerAuthorCaptain`/`callerOpponentCaptain`) → interface `MatchSidesView` (domain/projection) do 2 view kế thừa + hàm static `MatchSides.resolve(view)` trong `support/reputation`; đường ghi report chung 2.3/2.10 = method private `writeReport(...)` trong `ReportServiceImpl` |
| `Clock` bean | **DÙNG LẠI (của SYSTEM-002)** | Chưa có bean trên `main` (chỉ `Clock.systemUTC()` mặc định ở service VENUE) → `ClockConfig` của SYSTEM-002; unit test dùng `Clock.fixed(…, AppTime.ZONE)` |

## Kế hoạch chi tiết

Quy ước: mỗi phase **Red → Green → Refactor**; "U" = unit test thuần (chạy ngay, không `.env.dev`); "I" = integration (viết ở phase, chỉ chạy ở giai đoạn tích hợp — mục "Test để giai đoạn tích hợp"). Sau mỗi phase → `progress-tracker`. Lệnh unit: `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -pl <module> -am -Dtest=<Test> -Dsurefire.failIfNoSpecifiedTests=false test`.

### Kiểm NFS (trước khi code — PF §3)
| Endpoint | Truy vấn / ngưỡng |
|---|---|
| 2.1 hoàn tất trận | 6 / W6 (S context, I rating, U match, khoá team 2 đội, U team 2 đội 1 câu, sau commit U `verified_at`) — v2.1 (#15) |
| 2.1 trận đã `COMPLETED` | 3–4 / W6 |
| 2.2 | 2 / D3 |
| 2.3, 2.10 | 5 / W6 |
| 2.4 | 3 / L4 |
| 2.5 | 3 / W6 |
| 2.6 | 2 / L4 · 2.7 2 / D3 |
| 2.8 `REJECTED` | **6 / W6 (= ngưỡng)** · `CONFIRMED` 4 · CAS 0 dòng 2 |
| 2.9 | 1 / D3 |
| Job huy hiệu | 2 / lô |
Tất cả trong ngưỡng nếu `UPDATE … RETURNING` không `@Modifying` chạy được (đã có tiền lệ `markStaleAlerted`). Không chạy được → 2.8 `REJECTED` = 7 > W6 → **dừng, hỏi user**.

### Phase 0 — Phụ thuộc tạm (bản tối thiểu, chỉ để biên dịch + unit test)
Chi tiết ở mục "Phụ thuộc tạm". Không viết logic của mã sở hữu; method có logic của mã khác → thân `throw new UnsupportedOperationException(...)` (unit test mock). Không test riêng (test thuộc mã sở hữu).

### Phase 1 — Migration V9, entity, mã lỗi
- Tạo `api/src/main/resources/db/migration/V9__reputation_rating_report_dispute.sql` (nguyên văn CCD §5.1).
- Sửa `domain/entity/rating/Rating.java` (score `Integer` nullable, `isNoShow`, `raterTeamScore`/`rateeTeamScore`, `createdByUserId`, mọi cột `updatable = false`, `createdAt` `LocalDateTime` + `AppTime.now()`).
- Sửa `domain/entity/rating/Report.java` (`Status {AUTO_ACCEPTED, DISPUTED, CONFIRMED, REJECTED}` `@Builder.Default AUTO_ACCEPTED`, `status updatable = false`, bỏ `adminReviewedAt`, `reason nullable = false`, `reliabilityPenalty` `@Builder.Default 0`, `createdByUserId`, `updatedAt` `@PrePersist/@PreUpdate` `@Setter(NONE)`, `LocalDateTime`).
- Tạo `domain/entity/rating/Dispute.java` (`@UuidGenerator(VERSION_7)`, `Status {PENDING, RESOLVED}`, `Decision {CONFIRMED, REJECTED}`, cột quyết định `updatable = false`).
- Sửa `domain/entity/team/Team.java` (bỏ `totalMatchesCompleted/Confirmed`, `averageRating`; `cleanStreak`, `reportCount*` `updatable = false`; + `verifiedAt` `LocalDateTime` `insertable = false, updatable = false`). Sửa `domain/entity/user/User.java` (bỏ 3 cột; `cleanStreak`, `reportCount*` `updatable = false`).
- Sửa `common/exception/ErrorCode.java`: + `RATING_001..003`, `REPORT_001..011`, `DISPUTE_001..002` (HTTP + message nguyên văn `API_Spec.md` §5).
- **Test viết trước (U):** `domain/entity/rating/RatingEntityTest`, `ReportEntityTest`, `DisputeEntityTest` (builder default, `@PrePersist` gán `createdAt`/`updatedAt`, cờ `updatable`/`insertable` qua reflection `@Column`); sửa `TeamEntityTest`, `UserEntityTest` (không còn field bị bỏ, `updatable = false`); `EntityIdGenerationTest` + `Dispute`; `common/exception/ErrorCodeTest` (hoặc bổ sung test có sẵn) cho 16 mã mới (HTTP + message).
- **I (viết, để tích hợp):** `api/src/test/.../migration/ReputationMigrationTest` (CHECK/unique/FK/index, `ddl-auto=validate`), `domain/repository/ReputationEntityDirtyCheckTest` (sửa `Team`/`User` qua dirty checking không ghi đè `clean_streak`, `report_count_*`, `verified_at`).

### Phase 2 — Quy tắc & hằng (`application/support/reputation`, `application/service`)
- Tạo `ReputationRules`, `ReportPenaltyPolicy` (`int[] tiers(ReportType)`), `ReportStatusResolver` (trạng thái hiệu lực, `canDispute`, `disputeDeadlineAt`), `MatchSides` (helper `resolveSides`), `TeamVerificationProperties`.
- Tạo `service/CompletionEntry`, `ReportDirection`, `DisputeStatusFilter`; sửa `service/ReliabilityReason` (+ `MATCH_COMPLETED, FIVE_STAR_RATING, REPORT_RECEIVED, REPORT_REJECTED_REFUND`).
- **Test viết trước (U):** `ReportPenaltyPolicyTest` (bảng 4×4, mảng là bản sao — không lộ mảng nội bộ), `ReputationRulesTest` (`reliabilityLevel` biên 95/70/50/49), `ReportStatusResolverTest` (NOSHOW trước/đúng/sau 24h, loại khác giữ nguyên, `canDispute` chỉ `RECEIVED`+`NOSHOW`+DB `AUTO_ACCEPTED`+trước hạn), `MatchSidesTest`.

### Phase 3 — Repository, projection, SQL
- Tạo `domain/repository/RatingRepository`, `ReportRepository`, `DisputeRepository`, `TeamReputationSql` (`STATS`); sửa `TeamRepository` (+ `applyCompletion`, `applyReportPenalty`, `refundReportPenalty`, `findReputation`, `refreshVerification`, `findIdsForVerification`), `MatchRepository` (+ `findRatingContext`, `findReportContext`, `completeIfArchived`).
- Tạo projection `domain/projection/`: `MatchSidesView`, `RatingContextView`, `ReportContextView`, `MatchRatingRowView`, `TeamReportView`, `DisputeContextView`, `ReportDecisionView`, `AdminDisputeView`, `AdminDisputeDetailView`, `TeamReputationView`, `ScoreChangeView`, `VerificationChangeView`, `ChatReportContextView` (`extends ConversationContextView`).
- SQL: CCD §5.2/§5.3 (tham số hằng truyền bằng `:param`, không literal; không nối chuỗi tên cột; `:now` từ Java). Mỗi `@Query` có Javadoc tiếng Việt.
- **Test viết trước:** chỉ **I** (SQL chỉ kiểm được trên PostgreSQL) — xem mục "Test để giai đoạn tích hợp" #2–#4. Không có U cho phase này.

### Phase 4 — `ReputationService` (phần mở rộng của mã này) + huy hiệu
- Sửa `service/ReputationService` (+ `applyMatchCompletion`, `applyReportPenalty`, `refundReportPenalty`), `service/impl/ReputationServiceImpl` (3 method mới; 2 method MATCH-001 là stub).
- Tạo `support/reputation/TeamVerificationRefresher`, `service/TeamVerificationJobService` + `impl/TeamVerificationJobServiceImpl`, `event/reputation/TeamVerificationChangedEvent`; `api/scheduler/TeamVerificationScheduler` (`zone = AppTime.ZONE_ID`); `application.properties` + 2 key `arenaflow.reputation.verification.*` (test properties: cron `-`).
- **Test viết trước (U):** `ReputationServiceImplTest` (`applyMatchCompletion`: event mỗi đội đổi điểm, `delta = new − old`, số dòng ≠ số entry → `IllegalStateException`; `applyReportPenalty`: trả `old − new`, event chỉ khi > 0; `refundReportPenalty`: luôn gọi repo, event chỉ khi `points > 0`); `TeamVerificationRefresherTest` (2 test bắt buộc CCD §4.8: rollback→commit gọi `refreshNow` 1 lần; 2 request liên tiếp cùng thread mỗi request 1 lần đúng tập id; không có transaction → `IllegalStateException`; lỗi `refreshNow` → log, không ném; event mỗi đội đổi) — dùng `TransactionSynchronizationManager.initSynchronization()` + mock `PlatformTransactionManager`; `TeamVerificationJobServiceImplTest` (lặp lô theo `afterId`, dừng khi lô rỗng, lỗi một lô → lô sau vẫn chạy).
- **I:** `TeamVerificationRefresherIntegrationTest` (`REQUIRES_NEW` trong `afterCommit` — #9), `TeamVerificationSchedulerTest` (cron bật/tắt).

### Phase 5 — Đánh giá (2.1, 2.2)
- Tạo `dto/request/CreateRatingRequest` + `validation/ValidRating`, `ValidRatingValidator`; `dto/response/RatingResponse`, `RatingCreatedResponse`, `MatchRatingsResponse`; `event/reputation/MatchRatedEvent`; `service/RatingService` + `impl/RatingServiceImpl`; `mapper/ReputationMapper` (phần rating); `api/reputation/MatchReputationController` (2.1, 2.2).
- **Test viết trước (U):** `CreateRatingRequest` validation (bổ sung `RequestDtoValidationTest` hoặc file mới: `isNoShow` mặc định `false`, score bắt buộc 1..5, no-show cấm score/tỷ số, cặp tỷ số, trim comment, lỗi gắn đúng field); `RatingServiceImplTest` (guard đúng thứ tự PF §1.1 2.1 từng mã; 4 nhánh bảng PF §3.1 — `applyMatchCompletion` entry đúng / chỉ rater khi no-show / `+1` khi 5 sao trận đã `COMPLETED` / không ghi điểm; unique → `RATING_002`; event `matchCompleted`; `refreshAfterCommit` đúng tập đội; `getMatchRatings`: `MATCH_001`, `MATCH_009`, `received = null` khi chưa `given`); `ReputationMapperTest` (phần rating).
- **I:** `MatchReputationControllerTest` (201/200/400/403/404/409/422 qua HTTP).

### Phase 6 — Report (2.3, 2.4) + report qua chat (2.10)
- Tạo `dto/request/CreateReportRequest`, `CreateChatReportRequest`; `dto/response/ReportResponse`, `ReportDisputeResponse`, `TeamBriefResponse`; `event/reputation/TeamReportedEvent`; `service/ReportService` + `impl/ReportServiceImpl` (`createReport`, `createChatReport`, `listTeamReports`, đường ghi chung `writeReport`); `api/reputation/TeamReputationController` (2.4; 2.9 ở Phase 8), `ConversationReportController` (2.10); mở rộng `MatchReputationController` (2.3); `ReputationMapper` phần report.
- **Test viết trước (U):** DTO validation (`reportType` `@OneOf`, `reason` 1..1000 sau trim); `ReportServiceImplTest` (2.3: guard PF §1.1 từng mã, `REPORT_002/003` theo trạng thái trận, unique → `REPORT_004`, `setReliabilityPenalty(points)`, event, refresh; 2.10: thứ tự `CHAT_002 → USER_001 → CHAT_003 → REPORT_011 → TEAM_016 → TEAM_015 → REPORT_005 → REPORT_010 → REPORT_004` với policy mock, bên A/B → reporter/reported, `reportType = BAD_COMMUNICATION`; 2.4: `requireActiveCaptain` trước, `SENT` ẩn `reliabilityPenalty` + `evidence`); `ReputationMapperTest` phần report (status hiệu lực, `canDispute`, `disputeDeadlineAt` null khi khác NOSHOW).
- **I:** `TeamReputationControllerTest` (2.4), `ConversationReportControllerTest` + ma trận PF §3.3a (cần lõi chat MATCH-001 thật).

### Phase 7 — Tranh chấp (2.5) + Admin (2.6–2.8)
- Tạo `dto/request/CreateDisputeRequest`, `ResolveDisputeRequest`; `dto/response/DisputeCreatedResponse`, `AdminTeamBriefResponse`, `AdminDisputeResponse`, `AdminDisputeDetailResponse`, `DisputeMatchResponse`; `event/reputation/ReportDisputedEvent`, `DisputeResolvedEvent`; `service/DisputeService`, `AdminDisputeService` + impl; `api/reputation/ReportController` (2.5), `AdminDisputeController` (2.6–2.8); `ReputationMapper` phần dispute.
- **Test viết trước (U):** DTO validation; `DisputeServiceImplTest` (guard PF §1.1 2.5 nhánh `TEAM`: `REPORT_001 → TEAM_016 → TEAM_015 → TEAM_002 → REPORT_006 → REPORT_008 → REPORT_007`; `markDisputed` 0 dòng → `ConcurrencyConflictException`; `save(Dispute)` + event); `AdminDisputeServiceImplTest` (CAS rỗng → `existsById` → `DISPUTE_001`/`DISPUTE_002`; `applyDecision` rỗng → `IllegalStateException`; `REJECTED` → `refundReportPenalty` + refresh; `CONFIRMED` không ghi điểm; audit `RESOLVE_DISPUTE`; đọc lại chi tiết; `listDisputes` `PENDING` cũ nhất trước); `ReputationMapperTest` phần admin (tên thật + cờ `is…Deleted`, không che).
- **I:** `ReportControllerTest`, `AdminDisputeControllerTest` (403 `AUTH_004` khi không phải Admin).

### Phase 8 — Uy tín đội (2.9)
- Tạo `dto/response/TeamReputationResponse` (+ `CompletionResponse`, `RatingSummaryResponse`, `VerificationResponse`); `service/TeamReputationService` + impl; 2.9 trong `TeamReputationController`; `ReputationMapper.toReputation`.
- **Test viết trước (U):** `TeamReputationServiceImplTest` (`TEAM_001`); `ReputationMapperTest` (`completionRate` null khi `countedMatches < 5`, HALF_UP 0 chữ số, `averageRating` HALF_UP 1 chữ số, `isNewcomer`, `verification`).
- **I:** controller 2.9; STATS (Q2/C, Q3/D, ARCHIVED + NOSHOW, report bị bác tính lại).

### Phase 9 — Hoàn thiện
- `ReputationNfsQueryCountTest` (I), test race (I). Chạy toàn bộ unit test của 4 module; rà comment tiếng Việt, import, không `LocalDateTime.now()` trần. → `code-reviewer` → đối chiếu design.

### Ước lượng file
- `src/main`: ~62 tạo mới + ~10 sửa (mã này) + ~14 phụ thuộc tạm ≈ **86 file**.
- `src/test`: ~18 unit (tạo/sửa) + ~12 integration ≈ **30 file**.

## Kế hoạch P7 — tích hợp sau rebase (`04b6136`, 2026-11-26) — CHỜ DUYỆT

Bối cảnh: bản tạm đã thay bằng bản thật của MATCH-001/002 (grep `-i "bản tạm"` = 0); unit 471 PASS. Mỗi bước có đổi hành vi: **Red → Green → Refactor**, ghi `progress.md` kèm output Red/Green (m3).

### Bước 1 — Điểm cắm & finding đã chốt (TDD)
| # | Việc | Red (test viết trước) | Green | File |
|---|---|---|---|---|
| 1.1 | **M1** `applyReportPenalty` đếm `next_count` từ dòng đã khoá | I `TeamReputationRepositoryTest`: Tx1 (`TransactionTemplate`, thread 1) trừ NOSHOW rồi chờ latch trước commit; Tx2 (thread 2) gọi cùng đội → chờ khoá; Tx1 commit → Tx2 xong. Kỳ vọng điểm 80 → 75 → 67, trả 5 rồi 8, `report_count_noshow = 2`. Kỳ vọng fail: Tx2 trả 5, điểm 70 | Tính `next_count` trong CTE `locked`, bỏ `counted` | `domain/.../repository/TeamRepository.java`, `api/src/test/.../TeamReputationRepositoryTest.java` |
| 1.2 | **m2** gộp nhánh `policy == null` | U `ReportServiceImplTest`: ngữ cảnh loại khác `TEAM_MATCH` không có policy → `IllegalStateException` (hiện trả `REPORT_011`) | `if (policy == null) throw new IllegalStateException("Thiếu policy cho loại cuộc trò chuyện " + type)`; giữ `!teamMatch → REPORT_011` | `application/.../service/impl/ReportServiceImpl.java` |
| 1.3 | **Q2** `ConversationSql` của `main` dùng `:now` → `findChatReportContext` thiếu tham số | U `ReportServiceImplTest`: verify gọi repo với `now` từ `Clock`; I `ReputationContextRepositoryTest#findChatReportContext` chạy với khối cột thật | Thêm `@Param("now") LocalDateTime now`; dùng `m.match_start_time` của `CONTEXT_JOINS` (đã `LEFT JOIN match m`), bỏ `LEFT JOIN match km` (1 join thừa) | `domain/.../repository/ReportRepository.java`, `ReportServiceImpl.java` |
| 1.4 | V9 xoá `report.admin_reviewed_at` (cột V6 đã đổi) | I `VietnamLocalDatetimeMigrationTest` sẽ fail khi chạy V9 (cột V6 biến mất) | + `"report.admin_reviewed_at"` vào `DROPPED_AFTER_V6` | `api/src/test/.../migration/VietnamLocalDatetimeMigrationTest.java` |
| 1.5 | Test trước đây "chưa viết" vì thiếu lõi thật | I mới: ma trận 2.10 PF §3.3a (`ConversationReportControllerTest` — chat `TEAM_MATCH` thật); `ReputationRaceTest` + hai Admin quyết định cùng tranh chấp (một `DISPUTE_002`, hoàn điểm một lần), tranh chấp sát hạn 24h (retrier của `main` đã bắt `ConcurrencyConflictException`) | Chỉ test; fail → báo user trước khi sửa code | `api/src/test/...` |

Không làm: `delta` của `adjust*` (xin trừ) khác 3 method mới (thực) — đúng REPUTATION-001 CCD §4.8 dòng 152; NOTIFICATION-001 không nghe `ReliabilityScoreChangedEvent`.

### Bước 2 — Chạy toàn bộ test trên DB (cần user chọn DB — xem "Câu hỏi chờ duyệt" #4)
- Lệnh: `. <repo chính>/.env.dev && export DATABASE_URL DATABASE_USERNAME DATABASE_PASSWORD JWT_SECRET CLOUDINARY_CLOUD_NAME CLOUDINARY_API_KEY CLOUDINARY_API_SECRET CLOUDINARY_FOLDER && ./mvnw -Duser.timezone=Asia/Ho_Chi_Minh test` (toàn repo — gồm hồi quy MATCH-001/002).
- Kiểm riêng: **M2** `ReputationControllerTest#ratingValidationErrorsPointToFields` (`{"score":4.5}` → 400) — PASS → đóng M2; FAIL → hỏi user. `ReputationNfsQueryCountTest` ≤ ngưỡng (bảng "Kiểm NFS").
- Test fail → skill `debug` (reproduce trước), không đoán.

### Bước 3 — `code-reviewer`
Phần chưa review: 26 file gộp conflict + `AdminAuditLog` + mọi sửa ở Bước 1 (`git diff 04b6136` trên các file đó + test mới). Lượt 1 đã review phần còn lại. CRITICAL/MAJOR/MINOR → trình user chọn từng câu.

### Bước 4 — Đối chiếu design một lần
Cập nhật REPUTATION-001 CCD/PF theo bảng "Lệch thiết kế" (#1–#13; #11 bỏ — m2 làm code khớp thiết kế) + lệch mới từ P7 (dự kiến #14 `findChatReportContext(conversationId, callerId, now)`). Không đổi API/DB/nghiệp vụ → không cần SRS (xác nhận lại sau đối chiếu). → `architect-reviewer` phần sửa → có sửa code → test + `code-reviewer` phần sửa.

### Bước 5 — Commit / PR (chỉ khi user yêu cầu)
`git fetch origin && git rebase --autostash origin/main` → gộp commit tạm `ef092df` thành `feat(REPUTATION-001): …` (code + V9) và `docs(REPUTATION-001): …` (design) → push → `gh pr create --base main` → merge squash khi user yêu cầu.

## Phụ thuộc tạm (thuộc mã khác)
> **2026-11-26: đã thay toàn bộ bằng bản thật của `main` khi rebase** — bảng dưới chỉ còn giá trị lịch sử.

Bản tối thiểu, đúng package / tên / chữ ký theo thiết kế của mã sở hữu; **khi tích hợp, bản của mã sở hữu thắng** (chỉ giữ phần "của REPUTATION-001" ghi rõ bên dưới).

| File | Mã sở hữu | Phần tạo ở đây |
|---|---|---|
| `common/.../common/time/AppTime.java` | SYSTEM-002 (CCD §2.2) | Toàn bộ (`ZONE_ID`, `ZONE`, `now()`, `today()`) — lớp hằng/tiện ích nhỏ, đúng chữ ký thiết kế |
| `common/.../exception/ErrorCode.java` | MATCH-001 | Hằng `MATCH_001`, `MATCH_009`, `MATCH_014`, `CHAT_002`, `CHAT_003` (message nguyên văn MATCH-001 `API_Spec.md` §5). **Của REPUTATION-001:** `RATING_*`, `REPORT_*`, `DISPUTE_*` |
| `application/.../support/ConcurrencyConflictException.java` | MATCH-002 (§12 #5) | `RuntimeException` rỗng; **không** sửa `ConcurrencyConflictRetrier` (MATCH-002 thêm nhánh bắt). Package `[cần xác nhận]` |
| `application/.../service/ReputationService.java` | MATCH-001 (§4.8) | `adjustTeamReliability`, `adjustUserReliability`. **Của REPUTATION-001:** 3 method mới |
| `application/.../service/impl/ReputationServiceImpl.java` | MATCH-001 | Thân 2 method MATCH-001 = `UnsupportedOperationException`. **Của REPUTATION-001:** 3 method mới |
| `application/.../service/ReliabilityChange.java`, `ReliabilityReason.java` | MATCH-001 | Record; enum 2 giá trị MATCH-001. **Của REPUTATION-001:** 4 giá trị mới |
| `application/.../event/reputation/ReliabilityScoreChangedEvent.java`, `ReputationSubjectType.java` | MATCH-001 (§4.9) | Record + enum đúng payload |
| `application/.../support/TeamGuard.java` | MATCH-001 (§4.5) | Method `requireActiveCaptain(teamId, userId)` thân `UnsupportedOperationException` |
| `domain/.../projection/TeamCaptainContextView.java` | MATCH-001 (§5.3) | Interface getter |
| `domain/.../repository/TeamRepository.java` | MATCH-001 (§5.2) | `findCaptainContext`, `adjustReliability` (SQL nguyên văn thiết kế MATCH-001 — cần `@Query` để Spring Data không suy tên). **Của REPUTATION-001:** 6 method §5.2 |
| `domain/.../repository/MatchRepository.java` | MATCH-001 | `interface MatchRepository extends JpaRepository<Match, UUID>`. **Của REPUTATION-001:** `findRatingContext`, `findReportContext`, `completeIfArchived` |
| `application/.../support/AdminAuditLog.java` | MATCH-001 (§4.11) | Overload `afterCommit(action, adminId, targetType, targetId)` thân `UnsupportedOperationException` |
| `domain/.../entity/chat/Conversation.java` | MATCH-001 (§6) | Entity theo §6 (enum `ContextType {TEAM_MATCH}`, `PartyType`, `Party`) |
| `domain/.../projection/ConversationContextView.java` | MATCH-001 (§5.3) | Interface getter (khối chung + `TEAM_MATCH`) |
| `domain/.../repository/ConversationSql.java` | MATCH-001 (§5.2) | Hằng `CONTEXT_COLUMNS`, `CONTEXT_JOINS` = chuỗi giữ chỗ (SQL thật của MATCH-001) — `findChatReportContext` chỉ chạy đúng sau tích hợp `[cần xác nhận]` |
| `application/.../support/chat/ConversationAccessPolicy.java`, `ConversationAccess.java` | MATCH-001 (§4.14) | Interface + record đúng chữ ký |
| `domain/.../entity/team/Team.java` | MATCH-001 (§6) | `reliabilityScore` `updatable = false`. **Của REPUTATION-001:** bỏ 3 cột, `cleanStreak`/`reportCount*` `updatable = false`, + `verifiedAt` |

Mọi bản tạm có Javadoc "Bản tạm tối thiểu … thay thế khi tích hợp" (grep `Bản tạm` để tìm).

Không tạo: `ClockConfig` (SYSTEM-002 — không cần để biên dịch/unit test; context Spring cần ở giai đoạn tích hợp), `Match` entity V7 (mã này không đổi `Match`), `TeamMatchConversationPolicy` (inject `List<ConversationAccessPolicy>`), sửa `ConcurrencyConflictRetrier` (MATCH-002), `spring.task.scheduling.pool.size` (MATCH-001 §8 đã tính job huy hiệu).

## Test để giai đoạn tích hợp
Viết ở phase tương ứng; **chỉ chạy** sau khi gộp V6–V8 + mã sở hữu (cần DB / Spring context / migration / đo SQL).

| # | Test (dự kiến, `api/src/test/...`) | Nội dung |
|---|---|---|
| 1 | `migration/ReputationMigrationTest` | V9 sau V6–V8: CHECK `rating_no_show_check`, `rating_team_scores_pair_check`, `report_status_check`, `dispute_resolution_check`; unique `idx_report_unique`, `idx_dispute_report`; FK `match_id`; cột bị xoá; `ddl-auto=validate` |
| 2 | `domain/repository/TeamReputationRepositoryTest` | `applyCompletion` (8 dòng bảng PF §3.1), `applyReportPenalty` (5 dòng PF §3.3), `refundReportPenalty` (`GREATEST(0, …)`), `findReputation` STATS (Q2/C, Q3/D, ARCHIVED+NOSHOW, report bị bác), `refreshVerification` (4 điều kiện, không đổi → 0 dòng), `findIdsForVerification`; `[cần xác nhận #6]` hai giao dịch đồng thời; `#7` bind mảng |
| 3 | `domain/repository/ReputationContextRepositoryTest` | `findRatingContext`, `findReportContext`, `completeIfArchived`, `findMatchRatingRows`, `findTeamReports`/`count` (2 chiều), `findDisputeContext`, `markDisputed` (biên 24h), `applyDecision`, `resolveIfPending`, `findAdminDisputes`/`count`/`detail`, `findChatReportContext` |
| 4 | `domain/repository/ReputationEntityDirtyCheckTest` | Dirty checking `Team`/`User` không ghi đè cột uy tín / `verified_at`; `Report.status` không ghi qua entity |
| 5 | `application/service/TeamVerificationRefresherIntegrationTest` | `REQUIRES_NEW` trong `afterCommit` (#9), rollback không refresh |
| 6 | `api/scheduler/TeamVerificationSchedulerTest` | cron bật/tắt (`-`) |
| 7 | `api/reputation/ReputationControllerTest` (gộp 5 controller vào 1 file) | 2.1 201 không `Location` + `RATING_002`; 400 theo field (gồm `score: 4.5`); 2.2 ẩn `received`; luồng 2.3 → 2.4 → 2.5 → 2.8 (`AUTH_004`, `DISPUTE_002`, hoàn điểm); 2.4 `direction` sai → 400; 2.9 công khai + `TEAM_001`; 2.10 400 + `CHAT_002`. **Chưa viết:** ma trận 2.10 PF §3.3a (cần lõi chat MATCH-001 thật — viết khi tích hợp) |
| 8 | `application/service/ReputationNfsQueryCountTest` | 2.1 hoàn tất = 5, 2.3 = 5, 2.2 = 2, 2.4 = 3, 2.9 = 1, 2.5 = 3, 2.6 = 2, 2.7 = 2, 2.8 `REJECTED` = 6 (số đếm + 1 câu refresh sau commit) |
| 9 | `application/service/ReputationRaceTest` | Hai đội trưởng đánh giá cùng lúc trận `ARCHIVED` (hoàn tất đúng một lần); report trùng đồng thời → một `REPORT_004`, trừ điểm một lần. **Chưa viết:** hai Admin quyết định, tranh chấp sát hạn (cần retrier bắt `ConcurrencyConflictException` của MATCH-002) |

Tất cả file trên **biên dịch được** (`test-compile` toàn repo PASS 2026-11-21), **chưa chạy**.

## Câu hỏi chờ duyệt
Không có câu hỏi chặn. Câu hỏi không chặn (đã làm tiếp theo kế hoạch):

| # | Vị trí | Vấn đề | Phương án |
|---|---|---|---|
| 1 | `application/src/main/java/com/phananh/arenaflow/application/dto/request/CreateRatingRequest.java:13` | API yêu cầu `score` không nguyên (vd `4.5`) → 400; Jackson mặc định có thể ép `4.5` → `4` [Unverified — chưa chạy] | Kiểm ở `ReputationControllerTest#ratingValidationErrorsPointToFields` khi tích hợp; fail → tắt ép float → int cho request (cấu hình Jackson toàn ứng dụng — cần duyệt) hoặc nhận `score` dạng số thập phân rồi validator kiểm số nguyên |
| 2 | `domain/src/main/java/com/phananh/arenaflow/domain/repository/ReportRepository.java` (`findChatReportContext`) | Ghép hằng `ConversationSql` của MATCH-001; nếu khối cột thật dùng `:now` thì method cần thêm tham số `now` (lệch chữ ký CCD §5.2) | Xử lý khi tích hợp với bản MATCH-001 |
| 3 | `application/src/main/java/com/phananh/arenaflow/application/service/impl/DisputeServiceImpl.java` (`markDisputed` 0 dòng) | Trên nhánh này retrier chưa bắt `ConcurrencyConflictException` → tạm thời 500 thay vì thử lại | Hết khi tích hợp bản retrier của MATCH-002 |

Cập nhật 2026-11-26 (sau rebase `04b6136`): #1 → kiểm ở P7 Bước 2 (`main` đã có `JsonNumberConfig`); #2 → đúng, `ConversationSql` dùng `:now` → P7 1.3; #3 → đã hết (`ConcurrencyConflictRetrier` của `main` bắt `ConcurrencyConflictException`).

| # | Câu hỏi P7 — CHỜ USER | Phương án |
|---|---|---|
| 4 | Integration test áp **V9** lên DB đang chạy test. DB dev chung đang V8; áp V9 → `main` / Render dev (chưa có file V9) khởi động lỗi Flyway validate tới khi PR này merge | (a) DB dev chung như MATCH-002; (b) Neon branch tạm tách từ dev cho REPUTATION-001, xoá sau khi merge — **User chốt 2026-11-26: (a) DB dev chung**; P7 duyệt 2026-11-26 |

## Blockers / Ghi chú
- **[cần xác nhận A] 2.10 phụ thuộc lõi chat MATCH-001** (`ConversationSql.CONTEXT_COLUMNS/JOINS`, `ConversationContextView`, policy `TEAM_MATCH`): đề xuất code service/controller/unit test với mock, SQL `findChatReportContext` ghép hằng `ConversationSql` giữ chỗ → chỉ chạy đúng sau tích hợp. Phương án khác: hoãn 2.10 tới khi MATCH-001 merge. Chờ user chọn.
- **[cần xác nhận B]** Tên/package `ConcurrencyConflictException` chưa chốt ở MATCH-002 (§12 #5 "mới hoặc dùng lại") — đề xuất `application/support/ConcurrencyConflictException`; khác bản MATCH-002 → đổi import khi tích hợp.
- **[cần xác nhận C]** 2.8 CAS 0 dòng: PF §1.1/§3.7 ghi `existsTeamDispute` (v1.1 — lọc `report.application_id`, cột V10) còn CCD §4.4 ghi `existsById`. Đề xuất `existsById` (V9 chỉ có tranh chấp đội ↔ đội → kết quả như nhau); MERCENARY-001 đổi sang `existsTeamDispute` (CCD §14).
- **[cần xác nhận D]** 2.5 nhánh `INDIVIDUAL` (`REPORT_009`, `DisputeContextView.reportedType/callerIsReportedUser`) và payload event dạng `reporterType/reporterId…` thuộc MERCENARY-001 (CCD §14). Đề xuất: mã này chỉ thêm `ErrorCode.REPORT_009`, event theo payload CCD §4.7 (`reporterTeamId/reportedTeamId`).
- **Xung đột tích hợp dự kiến:** `Rating`, `Report`, `Team`, `User` cũng bị SYSTEM-002 sửa (`OffsetDateTime` → `LocalDateTime`); `TeamRepository`, `ReputationService*`, `TeamGuard`, `AdminAuditLog`, `ErrorCode` cũng bị MATCH-001 sửa. Mã này dùng `LocalDateTime` cho field mới/viết lại, không đổi field thời gian có sẵn của `Team`/`User`.
- **Không chạy migration/Flyway lên Neon dev từ nhánh này:** nhánh có V1–V5 + V9 → Flyway ghi V9 trước V6–V8, sau đó V6–V8 bị từ chối (không out-of-order) `[cần xác nhận #8]`.
- **State file được Git theo dõi:** `docs/agent/state/local-dev/*` đang tracked trên `main` (commit `05b6d3e`, PR #64), khác `CLAUDE.md` §7 ("gitignore"). 7 nhánh song song cùng ghi đè `current-task.md` → xung đột khi merge. Đề xuất skill `workflow-adapt` (không tự sửa).
- `[cần xác nhận #5]` (RETURNING không `@Modifying`) có bằng chứng từ `VenueRepository#markStaleAlerted` → không chặn; dạng `Optional` một dòng kiểm ở test tích hợp #2/#3.
- ~~`[cần xác nhận #12]` race report ↔ hoàn tất trận: thiết kế đề xuất chấp nhận~~ — đã sửa (review lượt 2 MAJOR, `lockForReputation`, design v2.1).
