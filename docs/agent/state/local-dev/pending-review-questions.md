# Câu hỏi / finding chờ user duyệt — giai đoạn code song song 7 mã

> User chốt 2026-11-21: giữ toàn bộ câu hỏi của code review ở mọi task implement song song để duyệt sau; task nào có câu hỏi chặn thì dừng ngay. File state, không commit.

## Trạng thái mã

| Mã | Trạng thái | Chặn? |
|---|---|---|
| SYSTEM-002 | m4 (A), m5 (tách) đã sửa; bỏ zone tường minh đã sửa, review lượt 3: 0C / 1 MAJOR (điều phối tích hợp) / 4 MINOR chờ duyệt | Không |
| USER-003 | **DỪNG** — chờ duyệt CRITICAL V12 | Có |
| MATCH-002 | **DỪNG** — review lượt 1: 0C / 1 MAJOR (test đếm truy vấn) / 5 MINOR chờ duyệt | Có (MAJOR chặn commit) |
| NOTIFICATION-001 | Review lượt 1 đạt (0C/0M), 7 MINOR chờ duyệt; chờ tích hợp | Không |
| REPUTATION-001 | **DỪNG** — review lượt 1: 0C / 2 MAJOR / 3 MINOR chờ duyệt | Có (MAJOR chặn commit) |
| MERCENARY-001 | **DỪNG** — review lượt 1: 0C / 4 MAJOR / 7 MINOR chờ duyệt | Có (MAJOR chặn commit) |
| MATCH-001 | **DỪNG** — review lượt 1: 0C / 1 MAJOR code (+1 MAJOR = blocker tích hợp) / 7 MINOR chờ duyệt | Có (MAJOR chặn commit) |

## Chéo mã — đã quyết

- **VENUE-001 sửa (PR #81, nhánh `feature/VENUE-001-user-admin-venue-rules`, 2026-11-23) — ghi chú tích hợp (review m3):** PR #81 xoá `VenueRepository.searchNearbyAny`; `VenueServiceImpl.listVenues` chỉ còn nhánh ACTIVE và ném `VENUE_018` (400) khi `status` khác ACTIVE; thêm `GET /api/v1/admin/venues`, `VENUE_017` (403), `LikePatterns`. MATCH-001 sửa cùng khối `listVenues` (thêm `GeoBoundingBox`) và vẫn gọi `searchNearbyAny` → khi gộp: giữ `GeoBoundingBox`, chỉ gọi `searchNearbyActive`. SYSTEM-002 cũng sửa `VenueServiceImpl`, `AdminVenueServiceImpl`, `VenueRepository`, `ErrorCode`… → PR nào merge sau phải rebase giải conflict (thời gian `OffsetDateTime` → `LocalDateTime` vs logic mới của #81).

- **Jackson ép số thập phân → số nguyên (REPUTATION M2, MERCENARY, MATCH-001 m5; có sẵn trên `main`: `AddVenueImageRequest.version`)** — user quyết 2026-11-21: **phương án A (toàn ứng dụng)**, làm trên **SYSTEM-002** (sở hữu cấu hình Jackson + `bodyField`; merge đầu tiên → 6 nhánh rebase tự có). Ghi "Lệch thiết kế" SYSTEM-002 + bổ sung CCD. Các nhánh khác KHÔNG sửa cục bộ field; khi tích hợp kiểm lại test 400 của field mình. Chốt: **chặt** — `4.5`, `4.0`, chuỗi `"4"` đều 400. **Đã làm** trên SYSTEM-002 (`JsonNumberConfig`, Lệch thiết kế #7), unit PASS, chờ `code-reviewer`.

## MATCH-001 đợt 1 (worktree `.claude/worktrees/agent-ae792d52783b81019`, nhánh `feature/MATCH-001-match-flow-a-impl`) — câu hỏi không chặn của developer

- **#1** Frame ERROR của STOMP (lệch #8): đích sai mẫu → `SYS_004`, client SEND → `SYS_002`, lỗi không rõ → `SYS_005`. Giữ các mã này?
- **#2** Khi tích hợp kiểm job vẫn chạy trên pool `scheduling-*` (lệch #7: thêm bean `taskScheduler` vì STOMP thêm `TaskScheduler` làm Boot không tự tạo scheduler).
- **#16** (từ kế hoạch) Đếm `chat_message` trên Neon dev trước khi chạy V7.
- TDD tự báo: Phase 3, 4, 7 viết test trước nhưng không chạy riêng bước Red.
- Lệch thiết kế: 10 mục (trong current-task.md của worktree), gồm `LocalDateTimeParamConverter` từ chối offset ở query param, `DueMatchView` chưa có `type` (đợt MATCH-002).
- Dependency: Boot 4.0.9 kéo spring-websocket 7.0.10, có `.withSockJS()` và `setSelectorHeaderName`.

### code-reviewer lượt 1 — 0 CRITICAL / 2 MAJOR / 7 MINOR — CHỜ DUYỆT

Unit reviewer chạy lại: common 13 / domain 21 / application 306, 0 lỗi. Số truy vấn theo code đúng (5 endpoint = 6); thứ tự mã lỗi khớp spec; không injection; STOMP xác thực/phân quyền đúng; `AppTime`/`ClockConfig` khớp SYSTEM-002.

#### MAJOR
- **M1** (chặn commit) Mất cập nhật `clean_streak`: `domain/.../entity/team/Team.java:108-110`, `domain/.../entity/user/User.java:96-98`, `domain/.../repository/TeamRepository.java:95`. `adjustReliability` đặt `clean_streak = 0` không tăng `version`, còn entity để `cleanStreak` ghi được qua dirty checking (chỉ `reliabilityScore` `updatable = false`). Kịch bản: `TeamServiceImpl.updateTeam` (dòng 109–125) nạp Team (v, streak 5) song song huỷ kèo `CONFIRMED` → `adjustReliability` streak 0 commit (version vẫn v) → `updateTeam` commit `clean_streak = 5 WHERE version = v` → mất lần cắt chuỗi. Giải tán đội (dòng 190) tương tự; `User` không có version càng dễ ghi đè. Comment dòng 95 sai. Sửa (TDD): `updatable = false` cho `cleanStreak` ở `Team`/`User` + test metadata cột; sửa comment; ghi "Lệch thiết kế" (CCD §6 chỉ ghi `reliabilityScore`). **Liên quan MERCENARY-001 M4 (cùng `User`)** — quyết chung.
- **M2** (không phải lỗi code — blocker tích hợp) Integration test chưa chạy: V7 + `ddl-auto=validate`, toàn bộ SQL native (`CAST` null, `jsonb_exists`, `UPDATE … RETURNING`, `unnest … WITH ORDINALITY` + `uuidv7()`, `SKIP LOCKED`, CTE/LATERAL `findMine`), controller test, `StompChatIntegrationTest`, race, `MatchNfsQueryCountTest`. Không được tick DoD / báo xong tới khi PASS (cần V6, `.env.dev`, đếm `chat_message` #16).

#### MINOR
- **m1** `api/.../realtime/StompAuthChannelInterceptor.java:124, :160` (lệch #8, = câu #1 ở trên): mã `SYS_002`/`SYS_004` trong header `message` của frame ERROR là hợp đồng FE thấy → bổ sung API_Spec §1.7 sau khi user chốt. `SYS_002` mang nghĩa HTTP 405.
- **m2** `api/.../config/WebMvcConfig.java:20-22` (lệch #6): `LocalDateTimeParamConverter` đặt lên đầu `GenericConversionService` → ghi đè mọi `@DateTimeFormat(pattern=…)` trên `LocalDateTime` toàn ứng dụng; thuộc SYSTEM-002 §2.5 → ghi "Phụ thuộc tạm" để khi tích hợp không có 2 cơ chế song song.
- **m3** `StompAuthChannelInterceptor.java:9` import `StompCommand` không dùng.
- **m4** `StompAuthChannelInterceptor.java:97-98` regex nhận UUID viết hoa → SUBSCRIBE `/topic/conversations/ABC…` qua phân quyền nhưng gateway publish chữ thường → client không bao giờ nhận frame, không lỗi. Chỉ nhận `[0-9a-f]` hoặc chuẩn hoá chữ thường.
- **m5** [Unverified] Chéo mã Jackson: `UpdateCostAgreementRequest`, `CostCustomItemRequest` (`venueAmount`, `drinksAmount`, `customItems[].amount` kiểu `Long`) — `600000.5` có thể thành `600000` thay vì 400. Quyết chung với REPUTATION-001 M2.
- **m6** `api/.../db/migration/V7__match_flow_a_lifecycle.sql:36` `DROP TABLE chat_message` không chốt chặn → thêm `DO $$ … IF EXISTS (SELECT 1 FROM chat_message) THEN RAISE EXCEPTION …` để "khác 0 thì dừng" trên mọi môi trường (rủi ro thấp: chưa có code ghi bảng này).
- **m7** TDD: Phase 3, 4, 7 không chạy Red riêng → ghi `progress.md`, chấp nhận có ghi chú.
- Chéo mã: snapshot CTE không áp dụng; `""` cho `LocalDateTime` → vẫn 400 sau SYSTEM-002.

## MERCENARY-001 (worktree `.claude/worktrees/agent-a5be6d5ff61e51a83`, nhánh `feature/MERCENARY-001-mercenary-impl`) — câu hỏi không chặn của developer

- **C1** 2.8 nhận thêm `status=ACTIVE` ngoài hợp đồng (dùng chung enum với 2.9). Giữ, hay tách enum riêng để trả 400?
- **C2** Test tích hợp chưa viết (để tích hợp): luồng kèo, race, controller, khối chat, trận sạch của đội.
- **C3** Khối SQL chat mercenary là hằng `MercenarySql.CONVERSATION_MERCENARY_*`, chưa cắm vào `ConversationSql` (tích hợp).
- Lệch thiết kế: 12 mục nội bộ (trong current-task.md của worktree). Xoá tài khoản (USER-001 2.7) tăng 3 → 5 câu (≤ W6).
- Rủi ro: agent cũng lỡ `install` một lần (SNAPSHOT vào `~/.m2` dùng chung) — cùng rủi ro như REPUTATION-001.

### code-reviewer lượt 1 — 0 CRITICAL / 4 MAJOR / 7 MINOR — CHỜ DUYỆT

Unit reviewer chạy lại (offline): 236 test, 0 fail. V10 khớp CCD §5.1; số truy vấn khớp PF §3; mã lỗi khớp; ẩn sân đúng; job `SKIP LOCKED` đúng; bản tạm khớp chữ ký.

#### MAJOR — chặn commit
- **M1** 2.13 rút lui đơn `APPROVED` bấm 2 lần → 500. `domain/.../repository/MercenaryRecruitmentRepository.java:208-217` (`decrementApproved` chỉ `WHERE id = :id`), `application/.../service/impl/MercenaryApplicationServiceImpl.java:255-257`. T2 chạy `0 - 1` → vi phạm CHECK `quantity_approved >= 0` (23514) → retrier không bắt → 500 (đúng ra thử lại → `MERCENARY_012`). Sửa: `AND quantity_approved > 0`; 0 dòng → `ConcurrencyConflictException`; test unit ca 0 dòng + race khi tích hợp.
- **M2** Xoá tài khoản: subquery đọc snapshot cũ khi chờ khoá tin (cùng mẫu lỗi REPUTATION-001 M1). `MercenaryRecruitmentRepository.java:223-241` (`decrementApprovedOfApplicant`), `application/.../support/mercenary/MercenaryAccountCleanup.java:41-46`. Rút lui trên thiết bị A + xoá tài khoản trên thiết bị B đồng thời → `0 - 1` → 23514 → 500. Sửa: thêm `AND rr.quantity_approved >= c.n` vào `WHERE`.
- **M3** (= C1 ở trên, nặng hơn) 2.8 nhận `status=ACTIVE` → `findApplicants` không có nhánh `ACTIVE` → điều kiện `effectiveStatus = 'ACTIVE'` → 200 danh sách rỗng; API_Spec yêu cầu 400. `application/.../service/ApplicationStatusFilter.java`, `api/.../mercenary/RecruitmentController.java:88`, `domain/.../repository/MercenaryApplicationRepository.java:99`. Đề xuất: tách enum riêng cho 2.8 / từ chối `ACTIVE` → 400.
- **M4** `domain/.../entity/user/User.java:65-98`: cột uy tín cá nhân (`reliability_score`, `clean_streak`, `report_count_*`) chưa `updatable = false` (ADR-0013) trong khi nhánh này đã ghi native → `ProfileServiceImpl.updateProfile` (`:57-64`) nạp `User` rồi `save` có thể ghi đè điểm/bộ đếm cũ, mất lần trừ (không `@Version`). Phần sửa entity thuộc MATCH-001 CCD §6 / REPUTATION-001, **chưa có trong bảng "Phụ thuộc tạm"** → đưa vào checklist tích hợp (hoặc thêm bản tạm nếu nhánh này merge trước).
- **Chéo mã Jackson** (áp dụng ở đây): `CreateMercenaryRatingRequest.score`, `RecruitmentPositionRequest.quantity`, `UpdateRecruitmentRequest.quantityNeeded` nhận `4.5 → 4` thay vì 400. Quyết cùng REPUTATION-001 M2.

#### MINOR
- **m1** `api/src/main/resources/application.properties:36` comment tiếng Anh (§2.4).
- **m2** `common/.../exception/ErrorCode.java:46` comment trỏ mã thiết kế — gỡ khi tích hợp.
- **m3** `application/.../service/impl/ExternalMatchServiceImpl.java:162` (lệch #10): 400 cặp giờ 2.31 trả sau `TEAM_*`/`MERCENARY_004`, spec đặt 400 đầu tiên → đổi thứ tự ưu tiên lỗi, ghi API_Spec khi cập nhật design.
- **m4** [Inference] `domain/.../repository/MercenarySql.java:220` alias chung `a/r/m/x/ap` có thể trùng alias `TEAM_MATCH` trong `ConversationSql` (vd `match m`) — kiểm khi cắm C3.
- **m5** [Inference] `MercenaryRecruitmentRepository.java:105-154` (2.3): sau DROP `idx_mercenary_recruitment_search`, `r.status = 'OPEN'` có thể không còn index — EXPLAIN khi tích hợp.
- **m6** `application/.../service/impl/ProfileServiceImpl.java:91,94` đọc `clock` 2 lần → `withdrawn_at`/`deleted_at` lệch vài µs; gộp khi SYSTEM-002 vào.
- **m7** (mức thiết kế, cho architect) `application/.../mapper/MercenaryMapper.java:194-201` làm tròn 0,5 km không chặn được dò ranh giới + trilateration ra toạ độ sân; cân nhắc rate limit.

## REPUTATION-001 (worktree `.claude/worktrees/agent-ae20f7ae909465469`, nhánh `feature/REPUTATION-001-reputation-impl`) — câu hỏi không chặn của developer

- **Q1** `score: 4.5` có thể bị Jackson ép thành 4 thay vì 400 — kiểm trong `ReputationControllerTest` khi tích hợp.
- **Q2** Nếu SQL thật `ConversationSql` (MATCH-001) dùng `:now` → `findChatReportContext` phải thêm tham số `now`.
- **Q3** Trên nhánh này 2.5 CAS 0 dòng → 500 thay vì thử lại, tới khi có retrier MATCH-002 (dự kiến).
- Bản tạm MATCH-001 ném `UnsupportedOperationException` → 2.2, 2.4, 2.8, +1 điểm khi 5 sao chưa chạy thật trên nhánh này.
- Lệch thiết kế: 13 mục nội bộ (danh sách trong current-task.md của worktree).
### code-reviewer lượt 1 — 0 CRITICAL / 2 MAJOR / 3 MINOR — CHỜ DUYỆT

Unit reviewer chạy lại: exit 0, 0 failure. Hợp đồng 2.1–2.10, V9, `updatable = false`, refresher, event, bản tạm: đạt. 13 mục "Lệch thiết kế" đều nội bộ.

#### MAJOR — chặn commit
- **M1** [Inference — ngữ nghĩa READ COMMITTED, chưa chạy DB] `domain/src/main/java/com/phananh/arenaflow/domain/repository/TeamRepository.java:166-184` `applyReportPenalty`: CTE `counted` lấy `next_count` từ lần quét `team t` riêng (snapshot đầu câu), không từ dòng đã khoá ở `locked`. Hai report cùng loại vào một đội đồng thời (vd `NOSHOW`, đếm 0): Tx2 chờ khoá xong có `old_score` mới nhưng `next_count` cũ → trừ 5 thay vì 8; `reliability_penalty` lưu sai bậc (lệch bảng §1.3, mâu thuẫn PF §5). Sửa: tính `next_count` ngay trong `locked` (`… CASE … END + 1 AS next_count FROM team WHERE id = :id FOR NO KEY UPDATE`), bỏ `counted`; thêm test hai giao dịch đồng thời khác trận cùng loại vào `TeamReputationRepositoryTest` (`[cần xác nhận #6]` chưa viết).
- **M2** (đã kiểm bằng Jackson 3.1.7) `application/src/main/java/com/phananh/arenaflow/application/dto/request/CreateRatingRequest.java:12`: `{"score":4.5}` → `score=4`, trả 201 thay vì 400 theo API 2.1. Xác nhận câu Q1 ở trên là lỗi thật. Phương án: (a) cục bộ — deserializer riêng cho `score` từ chối số thập phân, hoặc nhận `BigDecimal`/`Number` rồi `ValidRatingValidator` kiểm `scale == 0`; (b) đổi `CoercionConfig` toàn ứng dụng (cần user duyệt — ảnh hưởng mọi field số nguyên của mọi mã).
  - **Lưu ý chéo mã (coordinator):** [Inference] mọi field `int`/`Integer` trong request của các mã khác cũng bị ép `4.5 → 4` tương tự; nên quyết một lần ở mức toàn ứng dụng hay từng field.

#### MINOR
- **m1** Grep "Bản tạm" phân biệt hoa thường bỏ sót 4 file viết "bản tạm": `domain/.../entity/team/Team.java:77`, `domain/.../repository/MatchRepository.java:16`, `application/.../service/ReliabilityReason.java:5`, `application/.../service/ReputationService.java:11` → thống nhất cách viết hoặc grep `-i` khi tích hợp.
- **m2** `application/.../service/impl/ReportServiceImpl.java:177-182` (lệch #11): nhánh "không policy và không `TEAM_MATCH`" → `REPORT_011` thành code chết sau MERCENARY-001; nhánh thiếu policy `TEAM_MATCH` ném `IllegalStateException` → 2.10 trả 500 trên nhánh này tới khi có MATCH-001. Ghi chú để xoá khi tích hợp.
- **m3** [Unverified] `progress.md` chỉ ghi "Red quan sát được", không có output.

- **Rủi ro môi trường:** agent lỡ `install` module `common` vào `~/.m2` dùng chung một lần → lệnh `-pl <module>` không kèm `-am` ở worktree khác có thể lấy nhầm artifact `common` này. Các lệnh đều dùng `-am` nên [Inference] ảnh hưởng thấp; khi tích hợp chạy build từ gốc.

## NOTIFICATION-001 (worktree `.claude/worktrees/agent-a84f87c0d85615cd5`, nhánh `feature/NOTIFICATION-001-notification-impl`) — câu hỏi không chặn của developer

- **Q1** [Unverified] bean `notificationExecutor` có thể làm Spring Boot không tạo `applicationTaskExecutor` mặc định — kiểm khi tích hợp.
- **Q2** Chưa rõ MATCH-002 ghi sân đã chốt vào cột nào (`confirmed_venue_id`?) — đối chiếu khi tích hợp (code dùng `COALESCE(confirmed_venue_id, venue_id)`).
- **Q3** SYSTEM-002 thêm `Clock` vào `TeamServiceImpl`/`TeamMembershipServiceImpl` → conflict khi tích hợp (dự kiến).
- **Q4** V11 trên nhánh → mọi test cần Flyway ở nhánh này fail tới khi có V6–V10 (dự kiến).
- Lệch TDD tự báo: `ErrorCode` `NOTIFICATION_001/002` viết test và code cùng lượt (không thấy Red riêng).

### code-reviewer lượt 1 — 0 CRITICAL / 0 MAJOR (đạt cổng) — CHỜ DUYỆT 7 MINOR

Unit reviewer chạy lại: `-pl common,domain,application test` exit 0 (12/12/289, 0 lỗi; 2 skip Cloudinary live có sẵn). Unit `api` + integration chưa chạy.

- **m1** `common/src/main/java/com/phananh/arenaflow/common/exception/ErrorCode.java:50-51`: `NOTIFICATION_001/002` chèn giữa `SYS_003` và `SYS_004` (nhóm "Lỗi tầng HTTP/hệ thống") → chuyển lên nhóm mã miền.
- **m2** Comment vi phạm §2.4: `V11__notification.sql:1` "(NOTIFICATION-001)"; `NotificationDispatcherImpl.java:63`, `NotificationServiceImpl.java:34` dùng "(Q1)/(Q2)" đánh số câu truy vấn (trùng mã quyết định Q1–Q13 của thiết kế) → "câu 1 / câu 2". Javadoc "Bản tạm của NOTIFICATION-001" ở file tạm chấp nhận được, phải không sót khi tích hợp.
- **m3** Lặp: `dispatchSafely` giống hệt ở 4 listener (`MatchNotificationListener.java:81`, `MercenaryNotificationListener.java:81`, `ReputationNotificationListener.java:67`, `AccountNotificationListener.java:67`) → helper/lớp cha; `NotificationDispatcherImpl.java:502-507` lọc người nhận null/rỗng trùng `NotificationWriter.write` (`:50-54`).
- **m4** [Inference] Tên sân không đồng nhất: `findMatchAudience`/`findGameAudience` dùng `COALESCE(m.confirmed_venue_id, m.venue_id)` (`NotificationAudienceRepository.java:33,157,166`), `findApplicationAudience` dùng `MercenarySql.GAME_VENUE` = `COALESCE(m.venue_id, x.venue_id)` (`MercenarySql.java:12`, repo `:71`) → kèo luồng B chỉ chốt `confirmed_venue_id` thì `MERCENARY_APPLICATION_APPROVED` thiếu tên sân. Kiểm khi tích hợp với MATCH-002/MERCENARY-001 (liên quan Q2 ở trên).
- **m5** `support/notification/NotificationRules.java:20` hằng `ZONE` không dùng → bỏ hoặc dùng.
- **m6** [Unverified] `api/config/AsyncConfig.java:22` bean `ThreadPoolTaskExecutor` không override `getAsyncExecutor()` → Boot có thể bỏ `applicationTaskExecutor`; `@Async` không tên sau này lấy nhầm `notificationExecutor`. Kiểm khi tích hợp (trùng Q1 ở trên).
- **m7** TDD: `ErrorCode` `NOTIFICATION_*` không có Red riêng (ghi nhận quy trình).
- Ghi chú: entity `Notification.createdAt` `LocalDateTime` vs cột `TIMESTAMPTZ` tới V6 (`ddl-auto=validate`); V11 cần V7–V10 → nhánh không chạy độc lập trước tích hợp; job nhắc chưa nối scheduler (đúng kế hoạch).

## USER-003 (worktree `.claude/worktrees/agent-a39625570a485474c`, nhánh `feature/USER-003-user-discovery-impl`) — code-reviewer lượt 1

Unit test reviewer chạy lại: PASS. Kết luận: chưa commit.

### CRITICAL — chặn
- **C1** `api/src/main/resources/db/migration/V12__user_name_search.sql:17-25` [Inference — release notes PG17, chưa kiểm trên Neon PG18]: từ PG17, `CREATE INDEX`/`REINDEX`/`ANALYZE` chạy với `search_path = pg_catalog, pg_temp`; `user_name_search_vector` thân `$$…$$` gọi `user_name_fold(name)` không schema → `function user_name_fold(text) does not exist` → V12 fail trên DB đã có user; `ANALYZE`/`REINDEX` sau cũng lỗi. Lỗi từ thiết kế (CCD §6 "không SET search_path, không schema-qualify").
  - Phương án: (a) sửa ngay — 2 hàm sang thân chuẩn SQL `RETURN …`, test migration thêm `ANALYZE "user"` + `REINDEX INDEX idx_user_name_search` (hoặc `SET LOCAL search_path = pg_catalog, pg_temp`), cập nhật CCD §6 + ADR-0016 → `architect-reviewer`; (b) giữ, thêm bước kiểm vào test, sửa khi tích hợp nếu fail; (c) xin phép tạo Neon branch tạm chạy thử ngay.

### MINOR
- **m1** `application/.../service/impl/TeamMembershipServiceImpl.java:156-168`: biến `query` gán lại → nhánh `case Name -> throw new IllegalStateException` chết; tách bước chuẩn hoá trả `Phone | FoldedName`.
- **m2** `api/src/test/.../domain/repository/UserDiscoveryRepositoryTest.java:169-183`: test EXPLAIN chép SQL thay vì dùng câu của repository; `SET LOCAL enable_seqscan = off` chỉ chứng minh index dùng được, comment "khi bảng đủ lớn" gây hiểu nhầm.
- **m3** `api/src/test/.../domain/repository/QueryTimeoutHintTest.java:46-49`: `pg_sleep(3)` cận trên 2.9 s dễ chập chờn → `pg_sleep(5)`, cận ~4.5 s.
- **m4** `V12__user_name_search.sql:1`: bỏ tiền tố `USER-003:` trong comment (§2.4).
- **m5** Không có bằng chứng Red trong `progress.md`; `GlobalExceptionHandlerTest.queryTimeoutFallsBackTo500Sys005` là test hồi quy (PASS ngay).

### Ghi chú (không phải finding)
- `MyInvitationView`/`MyTeamView` trả `LocalDateTime` khi cột còn `TIMESTAMPTZ` tới V6 — chỉ đúng sau khi rebase lên V6.

## MATCH-002 (worktree `.claude/worktrees/agent-afe2f2ada6a54efbf`, nhánh `feature/MATCH-002-match-flow-b-impl`) — code-reviewer lượt 1 — CHỜ DUYỆT

Unit reviewer chạy lại: 72/72 PASS. Code production đúng API_Spec + thứ tự guard PF §1.1; V8 khớp CCD §5.1; bản tạm khớp chữ ký MATCH-001/SYSTEM-002. SQL native/`RETURNING`/`unnest` chỉ đọc code, chưa kiểm DB.

### MAJOR — chặn commit
- **M1** `api/src/test/java/com/phananh/arenaflow/application/service/PairingNfsQueryCountTest.java:79-82`: 2.2 và 2.3 assert `isEqualTo(3)` với `pageSize = 20`, chỉ 1 pairing → Spring Data bỏ câu count ở trang đầu chưa đầy → đo được 2, test fail khi tích hợp; cũng không đo ca xấu nhất. Sửa: `pageSize = 1` (trang đầy, count luôn chạy) như `VenueNfsQueryCountTest:86-90, 150-152`.

### MINOR
- **m1** `application/.../dto/request/UpdatePairingCostAgreementRequest.java:33-34`, `PairingServiceImpl.java:234`: `@OneOf` của `costType`/`costSplit` không cho `""` → FE gửi `""` (= không nhập theo MATCH-001 API_Spec §1.1) nhận 400. Tiền lệ VENUE: cho `""` vào `@OneOf`, service đổi `null`. Đối chiếu `UpdateCostAgreementRequest` của MATCH-001 khi tích hợp.
- **m2** `api/src/test/.../domain/repository/PairingRepositoryTest.java:144-155`: lần gọi đầu `finalizeConfirmation` đã đổi `other` sang `SUPERSEDED` → assert sau lần gọi 2 luôn đúng, tên test sai hành vi → assert kết quả lần đầu `containsExactly(other)` hoặc tách 2 kịch bản.
- **m3** `application/src/test/.../PairingServiceImplTest.java:133, 161`: mock `saveAndFlush` trả entity chưa có id → `PairingCreatedEvent.pairingId` so `null` với `null`, không kiểm thật → stub gán id.
- **m4** `api/src/test/.../PairingConfirmRaceTest.java:67-70, 89-93`: thiếu kiểm "đúng một `MatchConfirmedEvent`" khi hai phía xác nhận song song và "lệnh thua nhận `PAIRING_005`" ở ca X–P/X–Q (PF §5).
- **m5** `domain/.../repository/PairingRepository.java:252` `expireNegotiatingByMatchIds` `IN (:matchIds)` [Inference] danh sách rỗng → `IN ()` lỗi cú pháp; chưa có caller (điểm cắm #6, P7) → chặn rỗng như `VenueSelectionServiceImpl.markSelected`.

## SYSTEM-002 (repo chính, nhánh `feature/SYSTEM-002-local-datetime`) — code-reviewer lượt 1 — ĐÃ DUYỆT 2026-11-21

Unit test reviewer chạy lại: PASS (`-pl common,domain,application test` exit 0; `-pl api -am` 25 test, 0 fail). Code production đạt, 0 CRITICAL. V6 đối chiếu đủ 51 cột / 22 bảng / 27 DEFAULT.

| # | Mức | Vị trí | Vấn đề | User quyết |
|---|---|---|---|---|
| M1 | MAJOR | `api/src/test/java/com/phananh/arenaflow/migration/VietnamLocalDatetimeMigrationTest.java:48-56` | `all27DefaultsUseVietnamLocalTime` quét cả `flyway_schema_history` (`installed_on TIMESTAMP DEFAULT now()`) → 28 dòng, test fail khi tích hợp | **Sửa**: chỉ quét 22 bảng của V6 |
| M2 | MAJOR | `api/src/test/java/com/phananh/arenaflow/migration/VietnamLocalDatetimeConversionTest.java:32` | Chạy V1 trên schema trống; V1 dòng 152 `INSERT INTO sport (name, is_active)` không có `id`, `sport.id UUID PRIMARY KEY` không DEFAULT → lỗi NOT NULL (coordinator đã kiểm V1) | **Sửa**: bỏ test #3, giữ test #2; ghi Lệch thiết kế CCD §8 #3 |
| m1 | MINOR | `common/.../time/AppTimeTest.java:21`, `domain/.../entity/EntityTimeTypesTest.java:104` | So với `LocalDateTime.now(VN)` khi JVM chạy `-Duser.timezone=Asia/Ho_Chi_Minh` → cài đặt sai (`now()` trần) vẫn PASS, không Red được | **Sửa**: tạm `TimeZone.setDefault(UTC)` trong test, khôi phục ở `finally` |
| m2 | MINOR | `api/src/main/java/com/phananh/arenaflow/api/config/JsonTimeConfig.java:20` | Chưa có test chứng minh `JsonMapper` do Spring Boot cấu hình nhận customizer; khi tắt leniency, `""` cho `LocalDateTime` có thể thành 400 [Inference] | **Sửa**: thêm assert vào `@SpringBootTest` sẵn có (chạy khi tích hợp); ghi chú cho MATCH-001 |
| m3 | MINOR | `api/src/main/resources/db/migration/V6__vietnam_local_datetime.sql:1` | Comment mở đầu `-- SYSTEM-002:` trỏ mã thiết kế (§2.4) | **Sửa**: bỏ tiền tố |

- Trạng thái: agent đã sửa cả 5 mục (unit PASS, `test-compile` qua; Red của m1 đã chứng minh bằng cài sai tạm).

### code-reviewer lượt 2 (phần sửa) — 0 CRITICAL / 0 MAJOR, 5 finding lượt 1 sửa đúng — CHỜ DUYỆT 2 MINOR

Unit test reviewer chạy lại: `-pl common,domain` 78 test, 0 fail; 3 lớp `api` 12 test, 0 fail. `VietnamLocalDatetimeMigrationTest`, `FrameworkErrorStatusTest` cần DB — chưa chạy.

- **m4** `api/src/test/java/com/phananh/arenaflow/migration/VietnamLocalDatetimeMigrationTest.java:46`, `:56`: chốt cứng tổng `51` cột / `27` DEFAULT → migration sau (V7+, vd NOTIFICATION-001 thêm `match.reminded_at`) thêm cột `TIMESTAMP` vào 22 bảng này làm test đỏ dù V6 đúng. Phương án: assert danh sách cặp `(table, column)` V6 bằng `containsAll`; hoặc Javadoc ghi rõ phải cập nhật 2 con số.
- **m5** `api/src/test/java/com/phananh/arenaflow/api/config/FrameworkErrorStatusTest.java:35-44`: test `JsonMapper` nằm trong class về HTTP status lỗi framework, khó tìm → tách sang class riêng (vd `JsonTimeConfigIntegrationTest extends ApiIntegrationTest`).
- Ghi chú (không phải finding): assert `+07:00` có lẽ không đỏ được khi thiếu customizer (ISO local đã từ chối offset) [Inference]; chỉ assert `Z` kiểm `forLeniency(false)`. Nếu sau này bật test song song → thêm `@ResourceLock(Resources.TIME_ZONE)`.

- **User quyết 2026-11-21:** m4 → **A** (liệt kê cặp bảng.cột, `containsAll`; dữ liệu dùng chung `migration/V6TimestampColumns`); m5 → **tách** sang `JsonTimeConfigIntegrationTest`. Đã sửa: `VietnamLocalDatetimeMigrationScriptTest` 3/3 PASS, `test-compile` api PASS; test DB chưa chạy.

### Bỏ zone tường minh (user chốt 2026-11-21, Lệch thiết kế #6) — code-reviewer lượt 3 — 0C / 1 MAJOR / 4 MINOR — CHỜ DUYỆT
Reviewer chạy lại 13 lớp unit liên quan: PASS.
- **M1** (điều phối, không sửa diff) `AppTime.java`: bỏ `ZONE`/`ZONE_ID` làm vỡ 5 worktree đang dùng (MERCENARY 14 file, NOTIFICATION 12, MATCH-001 12, REPUTATION 8, MATCH-002 2; USER-003 0) — `ClockConfig` bản tạm, `@Scheduled(zone = AppTime.ZONE_ID)` ở `MatchLifecycleScheduler:18`, `RetentionScheduler:18`, `TeamVerificationScheduler:16`, `NotificationRules.java:20` (`ZONE = AppTime.ZONE`), `VenueSelectionService.java:23` (MERCENARY). Phương án: ghi quy tắc thay thế vào kế hoạch tích hợp (test → `ZoneId.systemDefault()`, service → `clock.getZone()`, bỏ `zone =`, lấy `AppTime`/`ClockConfig` bản SYSTEM-002) hoặc sửa ngay trong từng worktree.
  - **User quyết 2026-11-23: A** — không sửa worktree bây giờ; khi rebase từng nhánh lên `main` (sau SYSTEM-002 merge) áp quy tắc: test → `ZoneId.systemDefault()`, service → `clock.getZone()`, bỏ `zone =` trong `@Scheduled`, bỏ hằng `ZONE` cục bộ (vd `NotificationRules.ZONE`), lấy `AppTime`/`ClockConfig` bản SYSTEM-002.
- **m6** `pom.xml` surefire chưa ghim timezone: 9 test application kỳ vọng giờ VN, chỉ đúng khi JVM test +07:00; máy này `SE Asia Standard Time` → `Asia/Bangkok` (+07) nên PASS không chứng minh cờ tới JVM. [Unverified] thời điểm surefire đặt `user.timezone`. Phương án: `<argLine>-Duser.timezone=Asia/Ho_Chi_Minh</argLine>` ở root `pom.xml`.
  - **User quyết 2026-11-23: A** — đã thêm `argLine` ở root `pom.xml` + test `AppTimeTest.jvmRunningTestsUsesVietnamTimeZone` (Red không cờ = `Asia/Saigon`, Green sau argLine; `api` kế thừa, kiểm bằng effective-pom). Kiểm thêm: cờ `-Duser.timezone` trong lệnh **có** tới JVM test (trước khi có argLine test PASS khi có cờ). Lệch thiết kế #8.
- **m7** `ErrorResponseTest.java:26` tên `fromStampsVietnamLocalTime` không khớp nội dung → `fromStampsCurrentLocalTimeTruncatedToMicros`.
  - **User quyết 2026-11-23: A** — đã đổi tên `fromStampsCurrentLocalTimeTruncatedToMicros` + Javadoc class; `ErrorResponseTest` 2/2 PASS.
- **m8** [Inference] `./mvnw -Duser.timezone=… spring-boot:run` có thể không truyền cờ vào JVM app (fork) → máy không +07 ghi giờ lệch vào DB dev chung. Phương án: `<jvmArguments>-Duser.timezone=Asia/Ho_Chi_Minh</jvmArguments>` ở `spring-boot-maven-plugin` (`api/pom.xml`) hoặc ghi hướng dẫn.
  - **User quyết 2026-11-23: A** — `api/pom.xml` `spring-boot-maven-plugin` thêm `<jvmArguments>-Duser.timezone=Asia/Ho_Chi_Minh</jvmArguments>` (kiểm bằng effective-pom; chạy app thật để kiểm ở giai đoạn tích hợp trên Neon branch). Lệch thiết kế #8 (gộp với m6).
- **m9** (tuỳ chọn) Không test nào chặn zone tường minh quay lại → test quét tĩnh `*/src/main/java` cấm `ZoneId.of(`, `zone =` trong `@Scheduled`, `"Asia/Ho_Chi_Minh"`.
  - **User quyết 2026-11-23: A** — thêm `api/src/test/.../api/config/NoExplicitTimeZoneTest` (quét `*/src/main/java` 4 module: `ZoneId.of(`, `ZoneOffset.`, `TimeZone.getTimeZone(`, `"Asia/Ho_Chi_Minh"`, `@Scheduled(... zone =`) + test tự kiểm mẫu; Red bằng class vi phạm tạm (bắt đúng file + 2 mẫu), Green 2/2 sau khi xoá. Dùng làm công cụ kiểm khi tích hợp 5 nhánh (M1).
- Ngoài phạm vi: `RequestBodyReadErrorTest.java:13` import `JsonMapper` thừa.

### code-reviewer lượt 4 (phần chưa review: m4/m5, Lệch #7, m6–m9) — 0C / 0M / 3 MINOR — user chọn sửa tất cả (2026-11-23)
- **m10** Javadoc 3 method bean (`JsonNumberConfig.strictIntegerCustomizer`, `JsonTimeConfig.strictLocalDateTimeCustomizer`, `ClockConfig.clock`) — đã thêm.
- **m11** `NoExplicitTimeZoneTest` thiếu `Clock.systemUTC()` — test mới `detectsEachWayOfPinningTimeZone` (Red: chỉ đoạn `Clock.systemUTC()` không bị bắt) → thêm mẫu → Green 3/3.
- **m12** `api/pom.xml`: `jvmArguments` → `systemPropertyVariables` `user.timezone` (để `-Dspring-boot.run.jvmArguments` từ dòng lệnh vẫn dùng được); effective-pom có `<user.timezone>`, không còn `jvmArguments`. Kiểm khi chạy app ở giai đoạn tích hợp.
- 2026-11-23: merge PR #81, #82 → rebase SYSTEM-002 lên `origin/main` 5e17d63 (giữ bản state của nhánh; code tự gộp, không conflict). Unit sau rebase: common 14/14, domain 65/65, application 208 (2 skip), api unit 39/39; api test-compile PASS. 6 worktree **chưa rebase** (user chọn B: rebase sau khi SYSTEM-002 merge; mô phỏng conflict: state + `ErrorCode`/`ErrorCodeTest`, MATCH-001 thêm `VenueServiceImpl`).

### Việc còn lại trước commit SYSTEM-002
- Tích hợp: chạy test cần DB trên Neon branch (xin phép).
- Sau khi mọi test PASS: developer đối chiếu bảng "Lệch thiết kế" (5 dòng: `JsonTimeConfig` đã kích hoạt, `bodyField`/`getPath()`, test #6 ở `RequestBodyReadErrorTest`, thêm 2 test, bỏ test #3) → cập nhật CCD SYSTEM-002 một lần → `architect-reviewer`.
- Phát hiện kèm (chưa hỏi): V1 không chạy được trên DB trống → chưa rõ DB dev nhận V1 bằng cách nào; Neon branch trống cũng không chạy được V1 — **hỏi user khi tích hợp**.

### code-reviewer lượt 5 (m10–m12 + phần gộp sau rebase) — 0C / 1 MAJOR / 2 MINOR
- **M1** Javadoc theo quy tắc #82 (convention §2.4) cho code mới/sửa của SYSTEM-002.
  - **User quyết 2026-11-23: A** — bổ sung đủ theo quy tắc (~90 chỗ). Câu 2/2: getter projection interface **được miễn** (mô tả cột ở Javadoc interface); ghi miễn trừ vào convention §2.4 trên nhánh `chore/`.
- **m13** `api/src/test/.../venue/AdminVenueControllerTest.java:91` `NOW()` trần → `(now() AT TIME ZONE 'Asia/Ho_Chi_Minh')` — **User quyết 2026-11-23: sửa** — đã sửa.
- **m14** `current-task.md` Lệch thiết kế #8 ghi `jvmArguments` → `systemPropertyVariables` — **User quyết 2026-11-23: sửa** — đã sửa.
- Trạng thái: M1 đã sửa (0 chỗ thiếu theo quét diff), unit PASS → chờ `code-reviewer` lượt 6.

### code-reviewer lượt 6 (phần sửa lượt 5: M1, m13, m14) — 0C / 0M / 1 MINOR — CHỜ DUYỆT
- M1, m13, m14 sửa đúng (Javadoc khớp code, không trỏ tài liệu, không đổi code ngoài comment, CRLF nhất quán).
- **m15** 11 record đổi kiểu field trong diff (`OffsetDateTime` → `LocalDateTime`) chưa có `@param` theo §2.4 (7 record venue đã có → áp không đều): `dto/response/{AdminTeamResponse,AdminTeamSummaryResponse,CaptainHistoryResponse,CaptainTransferResponse,InvitationResponse,MemberHistoryResponse,MemberResponse,ProfileResponse,TeamResponse,TeamSummaryResponse}`, `event/venue/VenueAdminAlertEvent`. Phương án: (A) thêm `@param` đủ field, field thời điểm ghi "giờ Việt Nam không kèm múi giờ" + ca null; (B) bỏ qua — record chỉ đổi kiểu coi như "class cũ được chạm" (chỉ cần Javadoc class), ghi quyết định vào §2.4 trên nhánh `chore/`.
- **User quyết 2026-11-23: A** — đã thêm `@param` cho 11 record (field thời điểm ghi "giờ Việt Nam không kèm múi giờ" + ca null). `-pl application -am test` 208 (2 skip), 0 fail. → `code-reviewer` lượt 7 trên phần sửa m15.

### code-reviewer lượt 7 (phần sửa m15) — 0C / 0M / 0 MINOR — ĐẠT
- 11/11 record đủ `@param`, đúng tên/thứ tự; enum, ca null, che tên (thường/Admin), field thời điểm khớp code; CRLF đồng nhất.
- Tuỳ chọn (không phải finding): `AdminTeamResponse`/`AdminTeamSummaryResponse` chưa ghi "giữ nguyên khi đội bị giải tán" cho `inactiveReason` như `TeamResponse`.

### architect-reviewer lượt 1 (design SYSTEM-002 v1.2, VENUE-001 v6.1, ADR-0017 Sửa đổi) — 0C / 2 MAJOR / 6 MINOR — CHỜ DUYỆT
- 9 dòng Lệch thiết kế phản ánh đủ, design khớp code. **Không ảnh hưởng SRS** (BC-M05 giữ nguyên; số nguyên chặt là validation kỹ thuật).
- **M1** ADR-0017: mục Sửa đổi + chú thích inline đảo ngược quyết định 3, 6 → trái `decisions/README.md` §3 (đảo ngược → ADR mới + `Superseded by`). (a) ADR-0018 "Nguồn giờ theo timezone JVM" thay quyết định 3, 6; gỡ chú thích inline ADR-0017, thêm metadata trỏ ADR mới; (b) chấp nhận ngoại lệ, ghi vào README qua `workflow-adapt` (nhánh `chore/`).
- **M2** Số nguyên chặt chưa lan: VENUE-001 2.5 `version` (Long, field integer body duy nhất đang chạy) → CHANGELOG v6.1 ghi "không ảnh hưởng FE" sai, mâu thuẫn SYSTEM-002 CHANGELOG; SYSTEM-002 API_Spec §2/§6 không nêu endpoint bị ảnh hưởng; USER-001 API_Spec §1.2 (lỗi chung, l.61) chưa có quy tắc (entry MINOR); CCD §9 thiếu `api-convention.md` + dòng JSON convention §6.2 (nhánh `chore/`).
- **m1** ADR-0017 l.42 Hệ quả "cron theo giờ VN tường minh" lỗi thời.
- **m2** Điều kiện vận hành thiếu IDE / `java -jar` (ADR "Cần theo dõi", CCD §2.2 bảng).
- **m3** CCD §2.5 khẳng định `int/short/BigInteger` chưa test → nhãn [Inference] hoặc thêm ca `int`.
- **m4** `""` vào field integer chưa nói (EmptyString không cấu hình) → ghi hành vi hoặc thêm test.
- **m5** API_Spec SYSTEM-002 l.76 ví dụ `amount`/`items[0].amount` không có thật → `customItems[0].amount`/`venueAmount`; §1 Tổng quan thêm 1 câu về số nguyên.
- **m6** CHANGELOG SYSTEM-002 v1.2 Type: ghi rõ vế không tương thích ngược (MINOR vẫn đúng).
- **User quyết 2026-11-23:** M1 → **(a)** ADR-0018 mới thay quyết định 3, 6 của ADR-0017 (m1 xử lý trong ADR-0018); M2 → **sửa đủ 4 chỗ** (SYSTEM-002 API_Spec §2/§6 nêu VENUE-001 2.5 `version`; VENUE-001 CHANGELOG v6.1 dòng FE thấy được; USER-001 API_Spec §1.2 + entry MINOR; CCD §9 thêm `api-convention.md` + dòng JSON convention §6.2 cho nhánh `chore/`); m2, m3+m4 (thêm test `int` nguyên thuỷ và `""`, đổi code thì hỏi riêng), m5, m6 → **sửa**.

### code-reviewer lượt 8 (`RequestBodyReadErrorTest` — ca `int`, `""`) — 0C / 0M / 3 MINOR — CHỜ DUYỆT
- Assert có ý nghĩa (`doesNotExist` với null fail khi BE trả số), không làm yếu test cũ, CRLF nhất quán.
- **m16** `RequestBodyReadErrorTest.java:26` Javadoc class "chuỗi vào field số nguyên bị từ chối" mâu thuẫn ca `""` → null.
- **m17** `JsonNumberConfig.java:11-12` Javadoc class "chuỗi bị từ chối → 400" — `EmptyString` không cấu hình (chỉ sửa comment).
- **m18** `RequestBodyReadErrorTest.java:165` thiếu comment lý do tách `PrimitiveRequest` (Jackson 3 từ chối thiếu giá trị nguyên thuỷ).
- **User quyết 2026-11-23:** sửa cả m16, m17, m18 — đã sửa (chỉ comment). `RequestBodyReadErrorTest` 14/14, `NoExplicitTimeZoneTest` PASS. Chờ `code-reviewer` phần sửa (gộp sau `architect-reviewer` lượt 2).

### architect-reviewer lượt 2 (phần sửa sau lượt 1) — 0C / 1 MAJOR / 3 MINOR — CHỜ DUYỆT
- Findings lượt 1 xử lý đúng; version khớp header; `publish-spec` chạy thử PUBLISHED. Không ảnh hưởng SRS.
- **M3** ADR-0017 hàng `Trạng thái` trỏ ADR-0018 + kế hoạch `Superseded (một phần)` lệch tiền lệ ADR-0005/ADR-0002 (giữ `Accepted`, con trỏ ở `Liên quan`, khi ADR mới Accepted thêm hàng + mục `Sửa đổi`). Đề xuất: trả `Trạng thái` về HEAD, con trỏ sang `Liên quan`; lúc merge thêm `Sửa đổi` (kèm dòng Hệ quả l.41 thôi áp dụng); sửa CCD §9 dòng ADR-0017.
- **m19** `""` = "không gửi" hứa cho mọi field `integer` nhưng `int` nguyên thuỷ → 400; đề xuất quy tắc DTO dùng `Integer`/`Long` (CCD §2.5 + §9 (c) convention); API_Spec SYSTEM-002 §2 / USER-001 §1.2 đổi "`""` đọc như `null`" (hoặc giữ "không gửi").
- **m20** API_Spec SYSTEM-002 header thêm link ADR-0018 nhưng CHANGELOG không ghi; ADR-0018 nội bộ → bỏ link khỏi API_Spec (giữ ở CCD) hoặc thêm vào CHANGELOG #4.
- **m21** ADR-0018 `Trạng thái` ghi "chốt nội dung 2026-11-21" thiếu 2026-11-23.
- **User quyết 2026-11-23:** M3 → **theo tiền lệ** (ADR-0017 `Trạng thái` về HEAD, con trỏ ở `Liên quan`; **lúc merge PR SYSTEM-002**: ADR-0018 → `Accepted`, ADR-0017 thêm hàng + mục `Sửa đổi` "Quyết định 3, 6 thay bởi ADR-0018" kèm dòng Hệ quả "cron tường minh" thôi áp dụng); m19 → **sửa cả hai** (quy tắc DTO `Integer`/`Long`, API_Spec "`""` đọc như `null`"); m20, m21 → **sửa**. Đã sửa; không request DTO nào dùng kiểu nguyên thuỷ (grep). → `architect-reviewer` lượt 3 + `code-reviewer` (m16–m18).

### code-reviewer lượt 9 (comment m16–m18) — 0C / 0M / 0 MINOR — ĐẠT
- Comment khớp cấu hình và test; CRLF nhất quán. Điểm [Unverified] của reviewer (Jackson 3 từ chối `int` thiếu giá trị) đã được chứng minh trong phiên: đặt `int rank` vào `ProbeRequest` làm mọi request thiếu `rank` trả 400 `field=rank`.

### architect-reviewer lượt 3 (phần sửa sau lượt 2) — 0C / 0M / 3 MINOR — CHỜ DUYỆT
- M3, m19, m20, m21 sửa đúng. Không ảnh hưởng SRS.
- **m22** ADR-0017 l.8 `Liên quan` "Quyết định 3, 6 thay bởi ADR-0018 (Proposed)" → thêm "đề xuất" theo tiền lệ commit `6317ccf`.
- **m23** Kế hoạch sửa ADR-0017 lúc merge (CCD §9) thiếu: (a) l.52 `AppTime.ZONE` → zone của `Clock`; (b) bỏ "đề xuất"/"(Proposed)" ở `Liên quan`; (c) tuỳ chọn: phương án loại l.36 nay được dùng lại kèm điều kiện vận hành.
- **m24** Comment `JsonNumberConfig.java:12` và `RequestBodyReadErrorTest.java:28` "như không gửi" lệch thiết kế "đọc như `null`" → sửa comment (cần `code-reviewer`).
- **User quyết 2026-11-23:** sửa cả m22, m23, m24 — đã sửa (ADR-0017 "đề xuất thay bởi"; CCD §9 kế hoạch merge đủ (1)(2); comment `JsonNumberConfig`/`RequestBodyReadErrorTest` "đọc như null" + đổi tên test `emptyStringIntoIntegerObjectFieldIsReadAsNull`; 14/14 PASS). → `code-reviewer` lượt 10 + `architect-reviewer` lượt 4.

### code-reviewer lượt 10 (m24) — 0C / 0M / 0 MINOR — ĐẠT

### architect-reviewer lượt 4 (m22, m23) — 0C / 0M / 1 MINOR — CHỜ DUYỆT
- m22, m23 sửa đúng; không ảnh hưởng SRS.
- **m25** CCD §9 dòng ADR-0017 bước (2) trích thiếu Hệ quả l.41: phải là "Mốc nghiệp vụ và cron theo giờ Việt Nam tường minh" thôi áp dụng → "theo giờ Việt Nam khi JVM chạy `Asia/Ho_Chi_Minh` (ADR-0018)"; vế "test cố định `Clock` được" vẫn đúng.
- **User quyết 2026-11-23: sửa** m25 — đã sửa CCD §9. → `architect-reviewer` lượt 5.

### architect-reviewer lượt 5 (m25) — 0C / 0M / 0 MINOR — ĐẠT. Vòng lặp design SYSTEM-002 xong; không ảnh hưởng SRS (cả 5 lượt).
