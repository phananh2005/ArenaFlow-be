---
task: REPUTATION-001 — đánh giá trận, report đội & tranh chấp, uy tín đội (V9)
updated: 2026-11-21
percent: 60
status: in_progress
---

## Đã xong
- Phase 0 — phụ thuộc tạm (bảng "Phụ thuộc tạm" ở `current-task.md`).
- Phase 1 — mã lỗi, entity `Rating`/`Report`/`Dispute`/`Team`/`User`, migration V9.
- Phase 2 — `ReputationRules`, `ReportPenaltyPolicy`, `ReportStatusResolver`, `MatchSides`, filter / enum / properties.
- Phase 3 — projection, `TeamReputationSql`, repository (SQL uy tín, ngữ cảnh, CAS, danh sách Admin).
- Phase 4 — `ReputationService` (+3), `TeamVerificationRefresher`, job + scheduler huy hiệu.
- Phase 5 — 2.1, 2.2 (`RatingService`, `@ValidRating`, `MatchReputationController`).
- Phase 6 — 2.3, 2.4, 2.10 (`ReportService`, `TeamReputationController`, `ConversationReportController`).
- Phase 7 — 2.5–2.8 (`DisputeService`, `AdminDisputeService`, `ReportController`, `AdminDisputeController`).
- Phase 8 — 2.9 (`TeamReputationService`).
- Phase 9 (phần không cần DB) — integration test viết, `test-compile` toàn repo PASS.
- Mỗi phase: Red quan sát được (compile thiếu symbol / assertion) → Green.
- Unit test: 110/110 PASS (`-pl common,domain,application -am -Dtest=…`, gồm test cũ bị ảnh hưởng: `TeamGuardTest`, `AdminAuditLogTest`, `ConcurrencyConflictRetrierTest`, `MapperTest`, `RequestDtoValidationTest`, `CustomValidatorsTest`, `EntityIdGenerationTest`).

## Còn lại
- `code-reviewer` (dừng chờ coordinator) → đối chiếu design theo bảng "Lệch thiết kế" → `architect-reviewer`.
- Giai đoạn tích hợp: chạy 10 file integration test (mục "Test để giai đoạn tích hợp").

## 2026-11-26 — Tích hợp: rebase lên `origin/main` `04b6136` (sau MATCH-001 #87, MATCH-002 #88)
- Câu review lượt 1 user chốt: M1 sửa (TDD, test 2 giao dịch đồng thời); M2 kiểm sau rebase bằng `ReputationControllerTest`; m1 grep `-i`; m2 gộp nhánh `policy == null`; m3 từ nay dán output Red/Green.
- Commit tạm local `ef092df` (gộp lại trước PR). 26 file conflict, user cho agent tự gộp theo nhóm:
  - Bản tạm → lấy bản `main`: `ReliabilityScoreChangedEvent`, `ReputationSubjectType`, `ReliabilityChange`, `ConcurrencyConflictException`, `ConversationAccess(Policy)`, `TeamGuard`, `Conversation`, `ConversationContextView`, `TeamCaptainContextView`, `ConversationSql`, `ColumnMapping`; `AdminAuditLog` (auto-merge sinh 2 method trùng chữ ký → về bản `main`).
  - Gộp hai bên: `ReliabilityReason` (+4 giá trị), `ReputationService(Impl)` (adjust* thật của `main` + 3 method uy tín), `ReputationServiceImplTest` (4 + 7 test, chung `Clock` 2026-11-21 10:00), `MatchRepository` (+3 method), `ErrorCode(Test)` (+RATING/REPORT/DISPUTE), `TeamRepository` (giữ `findCaptainContext`/`adjustReliability` của `main` + method uy tín), `Team`/`User` (bỏ 3 field V9 xoá cột), `TeamEntityTest`, `UserEntityTest`.
  - State → giữ bản REPUTATION.
- `grep -rni "bản tạm" --include=*.java .` → 0.
- `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -o test-compile` → exit 0.
- `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -o -pl common,domain,application -am test` → `Tests run: 471, Failures: 0, Errors: 0, Skipped: 2` (2 skip = `CloudinaryImageAssetAdminLiveTest`), BUILD SUCCESS.
- Chưa chạy: integration test (DB dev đang V8; chạy sẽ áp V9 lên DB dev chung — hỏi user).

## 2026-11-26 — P7 (user duyệt; DB test = DB dev chung)
- **1.2 m2** Red: `ReportServiceImplTest#chatContextTypeWithoutPolicyIsProgrammingError` → `Tests run: 8, Failures: 1` — `Expecting actual throwable to be an instance of: java.lang.IllegalStateException but was: BusinessException: Cuộc trò chuyện này không report theo cách này (ReportServiceImpl.java:181)`. Green: nhánh `policy == null` → `IllegalStateException("Thiếu policy cho loại cuộc trò chuyện " + type)` → `Tests run: 8, Failures: 0`. Refactor: Javadoc `guardChatReport`.
- **1.3 Q2** Red: stub `findChatReportContext(conversationId, callerId, NOW)` → compile `method findChatReportContext … cannot be applied to given types; required: UUID,UUID found: UUID,UUID,LocalDateTime`. Green: `ReportRepository#findChatReportContext(+ now)`, `m.match_start_time` của `CONTEXT_JOINS` (bỏ `LEFT JOIN match km`), service lấy `now` trước truy vấn → `Tests run: 8, Failures: 0`; `test-compile` toàn repo exit 0. Phần SQL thật kiểm ở 1.5 (integration).
- **1.4** Red (DB dev, Flyway `Migrating schema "public" to version "9"` → `Successfully applied 1 migration … now at version v9`): `VietnamLocalDatetimeMigrationTest.noColumnKeepsTimeZoneAndEveryV6ColumnIsLocalTimestamp` FAIL — `Expecting ArrayList: ["report.admin_reviewed_at"]`. Green: + `report.admin_reviewed_at` vào `DROPPED_AFTER_V6` → `Tests run: 3, Failures: 0`.
- **1.1 M1** (test đặt ở `ReputationRaceTest` — lớp cha `RepositoryIntegrationTest` rollback, không chạy được 2 giao dịch thật; mẫu `pg_backend_pid` + `pg_stat_activity` của `VenueStaleAlertRaceTest`). Red: `sameTypePenaltiesAtOnceUseConsecutiveTiers` — `expected: 8 but was: 5` (Tx2 đọc `oldScore = 75` đúng nhưng bậc từ ảnh chụp → **lỗi thật trên PostgreSQL**). Green: `next_count` tính trong CTE `locked`, bỏ `counted` → `ReputationRaceTest` `Tests run: 3, Failures: 0`.
- Lỗi test/SQL lộ ra khi chạy DB lần đầu (code chưa từng chạy trên PostgreSQL):
  - `ReputationDbFixtures.team`: `VALUES (…, invited_at)` → `ERROR: column "invited_at" does not exist` (17/17 lỗi setUp) → gán `invited_at = joined_at = AppTime.now()`.
  - `TeamRepository#refreshVerification`: `CASE WHEN … THEN :now ELSE NULL END` → `column "verified_at" is of type timestamp without time zone but expression is of type text` → `CAST(:now AS timestamp)` (mẫu `PairingRepository:232`).
  - `TeamReputationRepositoryTest` → `Tests run: 17, Failures: 0, Errors: 0`.
- Chạy toàn bộ test lượt 1 (2026-11-26 17:1x) **dừng giữa chừng** (phiên kết thúc) sau 147 lớp test; 1 FAIL `SecurityConfigIntegrationTest.apiDocsAreDisabledByDefault` (`Status expected:<404> but was:<200>`) — **do lệnh**: `set -a` export cả `SPRINGDOC_ENABLED` của `.env.dev`. Không dùng `set -a`; chỉ `. .env.dev && export DATABASE_URL DATABASE_USERNAME DATABASE_PASSWORD JWT_SECRET CLOUDINARY_*`. Chạy lại lượt 2.
- Chạy toàn bộ test lượt 2 (lệnh đúng, không `set -a`) — chạy nền bị ngắt khi phiên kết thúc sau **168 lớp, 868 test, 0 fail / 0 error** (gồm mọi lớp REPUTATION: `ReputationControllerTest` 6/6 → **M2 đóng** — `{"score":4.5}` → 400; `ReputationNfsQueryCountTest` 2/2; `ReputationRaceTest` 3/3; `ReputationContextRepositoryTest` 9/9; hồi quy MATCH-001/002 PASS).
- 9 lớp còn lại chạy foreground: `Tests run: 60, Failures: 1` — `ReputationMigrationTest.resolvedDisputeNeedsDecisionNoteAdminAndTime`: câu vi phạm thứ 2 → `SQL state [25P02] current transaction is aborted` (lỗi test: hai vi phạm trong cùng transaction). Sửa: helper `assertViolates(constraint, …)` với `SAVEPOINT` (mẫu `MatchFlowBPairingMigrationTest`) → `Tests run: 6, Failures: 0`. Còn lại 8 lớp PASS (TeamReputationRepository 17, UserRepository 7, MatchFlowAMigration 7, MatchFlowBPairingMigration 6, UserTeamMigration 5, VenueManagementMigration 6, VietnamLocalDatetimeMigrationScript 3, VietnamLocalDatetimeMigration 3).
- **Kết luận Bước 2 (trước 1.5): toàn repo PASS trên DB dev V9.**
- **1.5** (chỉ test, không sửa code chính):
  - Mới `api/.../reputation/ConversationReportControllerTest` — ma trận PF §3.3a trừ phần mercenary (thuộc MERCENARY-001): luồng A 8 cặp (yêu cầu × kèo), luồng B 4 cặp (thương lượng × kèo) có tin → 201; chưa có tin → `REPORT_010`; 2.10 A→B rồi 2.3 cùng chiều → `REPORT_004`, B→A 201, hiện ở 2.4 của cả hai đội; người ngoài `CHAT_003`; xoá tài khoản `USER_001`; đội trưởng cả hai đội `REPORT_005`; đội người gọi INACTIVE `TEAM_015`; đội bị report DISBANDED → 201, người giải tán report → `TEAM_016`. Lần 1: 18/19 (lỗi fixture `left_at` < `joined_at` → `team_member_check1`) → sửa fixture → `Tests run: 19, Failures: 0`. Xác nhận SQL thật của 1.3.
  - `ReputationRaceTest` + `twoAdminsRejectingSameDisputeTogetherRefundOnce` (một `DISPUTE_002`, điểm 75 → 80 một lần) + `twoDisputesOfSameReportTogetherCreateOne` (một `REPORT_008` qua retrier, 1 dispute) → `Tests run: 5, Failures: 0`. Biên 24h đã có ở `ReputationContextRepositoryTest#markDisputedChecksStatusTypeAndDeadlineInTheSameStatement` (PASS).
- **Bước 1 + 2 xong.** Code chính không đổi sau lượt chạy toàn bộ 2; các lớp test sửa/mới đã chạy PASS.

## 2026-11-26/27 — Bước 3: code-reviewer lượt 2 (gộp conflict + P7) — 0C / 1M / 2m
- User chốt: MAJOR sửa + race test; MINOR 1 → tech-debt #12 (repo chính); MINOR 2 → checklist MERCENARY-001.
- **MAJOR `applyCompletion`** Red: `ReputationRaceTest#completionWaitingOnReportPenaltySeesTheReport` (T1 report bùng + trừ điểm giữ khoá B; T2 hoàn tất trận chờ khoá B) → `expected: 0 but was: 1` (`clean_streak` B) — **lỗi thật**. Red unit: `ReputationServiceImplTest` InOrder `lockForReputation` → `applyCompletion` → compile `cannot find symbol method lockForReputation(java.util.UUID[])`. Green: `TeamRepository#lockForReputation(ids)` (`SELECT id … ORDER BY id FOR NO KEY UPDATE`) gọi trước `applyCompletion` trong `ReputationServiceImpl#applyMatchCompletion` → `ReputationServiceImplTest` 11/11, `ReputationRaceTest` 6/6. NFS 2.1 hoàn tất 5 → **6 (= W6)**, user đã duyệt → `ReputationNfsQueryCountTest` kỳ vọng 6 → 2/2.
- Chạy lại: unit `common,domain,application` (17 + 99 + 472, 2 skip) BUILD SUCCESS; integration REPUTATION + `MatchFlowCommittedTest` + `MatchControllerTest` `Tests run: 85, Failures: 0` BUILD SUCCESS.
- **code-reviewer lượt 3** (phần sửa MAJOR) — 0C / 0M / 1m: (a) không còn kẽ hở — mọi kịch bản report / bác tranh chấp ↔ hoàn tất trận tương đương một thứ tự tuần tự; (b) không vòng chờ mới; (c) mảng rỗng/trùng an toàn; (d) không chỗ gọi `applyCompletion` khác; (e) race test đúng kịch bản. MINOR: thông báo `awaitBlockedOnLock` ghi cứng "penalty session" → user chọn sửa → `"session " + pid + " never waited on a row lock"` → `ReputationRaceTest` 6/6 → code-reviewer lượt 4 (dòng đó).
- **code-reviewer lượt 4** (dòng 274 `ReputationRaceTest`) — 0C / 0M / 0m. **Bước 3 xong** → Bước 4 (đối chiếu + cập nhật design một lần theo bảng "Lệch thiết kế" #1–#16, trừ #11).
- architect-reviewer m1: thêm `ReputationRaceTest#reportWaitingOnCompletionIsAppliedAsReportAfterCompletion` (trường hợp b: 80 → 87 → 82, chuỗi 0, bộ đếm 1) → `ReputationRaceTest` `Tests run: 7, Failures: 0` (PASS ngay — test ghi nhận hành vi, không đổi code). **code-reviewer lượt 5** (test mới) — 0C / 0M / 0m.
- architect-reviewer v2.1 — sửa tài liệu theo 8 finding user chốt 2026-11-27 (không đổi code / test): **M1** `Component_Class_Design.md` §10 (+ dòng lệch ADR-0003 Sửa đổi 2026-11-12 / ADR-0013 → `lockForReputation` + `applyCompletion` giữ khoá qua 2 câu), §4.6 (trỏ ADR-0013 Sửa đổi 2026-11-27, §10), header (ADR-0003/0013 + Sửa đổi 2026-11-27); `decisions/0013-…md` + mục "Sửa đổi 2026-11-27" (quy tắc khoá câu trước khi đọc bảng khác; cột chính dòng đọc trong CTE `locked`; áp `"user"` MERCENARY-001) + metadata; `decisions/0003-…md` + mục "Sửa đổi 2026-11-27" + metadata · **m1** `Processing_Flow.md` §5 dòng "Report gửi gần như cùng lúc…" ((a)/(b) + ví dụ 70 / 82, race test cả hai), §3.1 (trỏ §5 (a), (b)) · **m2 + m4** `CHANGELOG.md` `[v2.1]` hàng #2 (vị trí "Hành vi 2.1 / 2.3 / 2.10", mô tả mới), hàng #3 ("Header 4 tài liệu…", "Đồng bộ phiên bản") · **m3** `CHANGELOG.md` xoá comment HTML NFS · **m5** `Processing_Flow.md` §3.3a (3 ca mercenary "chạy khi MERCENARY-001 có policy và V10") · **m6** `Component_Class_Design.md` §5.2 ghi chú `applyCompletion` (khoá CTE phòng vệ; thứ tự / chờ khoá do `lockForReputation`) · **m7** `Processing_Flow.md` §2.1 ghi chú sơ đồ "Q6 / Q5 / Q4 tuỳ nhánh", header (ADR + Sửa đổi 2026-11-27).
- Đồng bộ `docs/database/` với V9 (§11 hàng đầu, chỉ tài liệu): `schema.dbml` (header; `user`, `team` bỏ `total_matches_*`, `average_rating`, `team` + `verified_at`; `rating`, `report` theo V9; bảng mới `dispute`) · `schema-design.md` (header; §1.1 `user`, §2.1 `team`, §4.1 `rating`, §4.2 `report`, §4.3 `dispute` mới, §8.1 25 bảng, §8.3 + `idx_report_unique`, `idx_dispute_report`) · `data-model.md` (header; §3.4 huy hiệu `team.verified_at`, Completion Rate, Đánh giá, Report và tranh chấp "Lưu trữ (V9)"; §3.7 cột `rating`). `git diff --check` chỉ báo 2 dấu cách cuối dòng ở §8.1 (ngắt dòng Markdown như các dòng cùng khối).
- **architect-reviewer** lượt 1 (1M/7m) → 2 (1M/2m) → 3 (0M/4m) → 4 (0M/1m) → 5 (0/0/0): user chốt sửa mọi finding; ADR-0003/0013 Sửa đổi 2026-11-27; `docs/database/*` đồng bộ V9 (24 bảng, `team_tag` V2 xoá). **Vòng lặp design xong (2026-11-27); không ảnh hưởng SRS.** Kế tiếp: Bước 5 (commit khi user yêu cầu).
- **Bước 5 (2026-11-27):** user chọn 2 commit, không commit state. `reset --soft 04b6136` (bỏ commit tạm `ef092df`, local chưa push) → `1ac0bb9` feat (115 file) + `a5d04c0` docs (9 file); pre-commit secret scan PASS; `test-compile` trên HEAD exit 0. State sao lưu scratchpad `rep-state-backup/`, giữ chưa commit trong worktree. Chưa push / chưa PR.
- **PR [#89](https://github.com/phananh2005/ArenaFlow-be/pull/89)** tạo 2026-11-27 (push lần đầu `feature/REPUTATION-001-reputation-impl`, `origin/main` = `04b6136`). Chờ user merge / yêu cầu merge squash.
