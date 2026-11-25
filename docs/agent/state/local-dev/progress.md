---
task: MATCH-001 đợt 1 — kèo luồng A + lõi chat + realtime (V7)
created: 2026-11-21
updated: 2026-11-25
status: done
---

## Đã xong
- Phase 0 (2026-11-21): `AppTime`, `ClockConfig` (tạm SYSTEM-002); entity `Match`/`MatchRequest`/`CostCustomItem`/`Sport` → `LocalDateTime`; `VenueSelectionService#markSelected(…, LocalDateTime)`. Unit: `AppTimeTest` 3/3, `VenueSelectionServiceImplTest` 5/5, `ClockConfigTest` 1/1 PASS.

- Phase 1 (2026-11-21): `V7__match_flow_a_lifecycle.sql`, entity `Match` (+version, skillLevels, cancelReason, enum), `MatchRequest.reason`, `CostCustomItem.displayOrder`, `Team`/`User` `reliabilityScore updatable=false`, `Conversation`, `ConversationMessage`, xoá `ChatMessage`; `ErrorCode` MATCH/SPORT/CHAT. Unit: `ErrorCodeTest` 5/5, `MatchEntityTest` 5/5, `ConversationEntityTest` 4/4, `EntityIdGenerationTest` 1/1, `TeamEntityTest` 4/4 PASS. `MatchFlowAMigrationTest` viết, chưa chạy (tích hợp).
- Phase 2 (2026-11-21): `MatchGuard`, `MatchExpiry`, `MatchCancellation`, `MatchWriteOutcome`, `ReputationService(Impl)`, `TimeBandPenaltyPolicy`, `MatchRules`, event match/reputation, `TeamGuard` +2, `TeamRepository`/`UserRepository` `adjustReliability`, `AdminAuditLog` overload, `GeoBoundingBox`. Unit: 48/48 PASS (gồm `VenueServiceImplTest` 17/17 sau refactor).

- Phase 3 (2026-11-21): bộ môn 2.13–2.15 (`SportRepository`, `SportService(Impl)`, `SportMapper`, DTO, `SportController`, `AdminSportController`). Unit `SportServiceImplTest` 6/6 PASS; `SportControllerTest` viết, chưa chạy.
- Phase 4 (2026-11-21): 2.1/2.2/2.3/2.11 (projection, `MatchRepository` truy vấn, `CostCustomItemRepository`, DTO, `@ValidMatchSchedule`, `@ValidMatchSearch`, `GeoQueryRequest`, `MatchMapper`, `MatchService(Impl)`, `LocalDateTimeParamConverter`, controller). Unit 25/25 + 3/3 PASS.
- Phase 5 (2026-11-21): 2.4/2.5 (`@ValidCostAgreement`, DTO, service, controller). Unit 34/34 PASS.
- Phase 6 (2026-11-21): 2.6–2.10, 2.12 (`MatchRequestRepository`, projection, `MatchRequestService(Impl)`, `MatchRequestController`). Unit `MatchRequestServiceImplTest` 17/17 PASS.
- Phase 7 (2026-11-21): job vòng đời (`MatchLifecycleJobService(Impl)`, `MatchLifecycleProperties`, `MatchLifecycleScheduler`, properties, surefire cron `-`). Unit 5/5 + 2/2 PASS.

- Phase 8 (2026-11-21): lõi chat 2.16–2.20 (`ConversationSql`, repository, projection, policy, `ConversationService(Impl)`, `ConversationMapper`, DTO, `@ValidChatCursor`, controller). Unit 28/28 PASS.
- Phase 9 (2026-11-21): STOMP (`spring-boot-starter-websocket` 4.0.9 → spring-websocket 7.0.10; `.withSockJS()` + `setSelectorHeaderName` có trên Spring 7), `WebSocketConfig`, `StompAuthChannelInterceptor`, `StompChatRealtimeGateway`, `StompErrorHandler`, `WebSocketProperties`, `SecurityConfig` `/ws/**`, `SchedulingConfig` `taskScheduler`. Unit 17/17 PASS.
- Phase 10 (unit): common 13/13, domain 21/21, application 306 (0 fail, 2 skip có sẵn), api 43/43 (13 lớp không cần Spring context); `test-compile` toàn repo PASS.
- Sửa review lượt 1 + tích hợp DB V7 (2026-11-25):
  - M1 (Q2 → A): `Team` và `User` khoá `updatable = false` cho cả 6 cột (`reliability_score`, `clean_streak`, 4 `report_count_*`); sửa comment `TeamRepository.java:95`; thêm `ColumnMapping` + test `TeamEntityTest`, `UserEntityTest` (Red: `cleanStreak` updatable=true → Green: 8/8 PASS).
  - #1 / m1 (A): giữ `SYS_004`/`SYS_002`/`SYS_005` cho frame `ERROR` STOMP, cập nhật ở bước design.
  - #2 (A): giữ `@Bean(name = "taskScheduler")` trong `SchedulingConfig`, thêm test integration trong `SecurityConfigIntegrationTest` kiểm tra `corePoolSize = 5` & prefix `scheduling-` (14/14 PASS).
  - #16 / m6 (A): thêm khối `DO $$ ... IF EXISTS (SELECT 1 FROM chat_message LIMIT 1) THEN RAISE EXCEPTION ... $$` trước `DROP TABLE chat_message;` trong `V7__match_flow_a_lifecycle.sql`; test tĩnh `SchedulingConfigTest` (Red → Green 2/2) + test runtime `MatchFlowAMigrationTest` (7/7 PASS).
  - m2 (A): giữ `LocalDateTimeParamConverter` trong `WebMvcConfig`.
  - m3, m4 (A): xoá import thừa `StompCommand`; regex `CONVERSATION_TOPIC` chỉ nhận chữ thường `[0-9a-f]` (Red: UUID viết hoa không ném lỗi → Green: `StompAuthChannelInterceptorTest` 8/8 PASS).
  - Comment cấu hình (§2.6): dịch sang tiếng Việt có dấu cho các khoá mới trong `application.properties` và `.env.example`.
  - M2 (Integration test DB dev V7): thêm `@JdbcTypeCode(SqlTypes.VARCHAR)` cho `ConversationMessage.senderParty` khớp `VARCHAR(1)` của V7 dưới Hibernate 7 `ddl-auto=validate` (Red → Green `ConversationEntityTest` 5/5); cập nhật `VietnamLocalDatetimeMigrationTest` loại `chat_message.created_at` đã xoá ở V7. Toàn bộ integration test của MATCH-001 PASS trên Neon dev PG 18.7 (V7): `MatchFlowAMigrationTest` 7/7, `SportControllerTest` 3/3, `MatchControllerTest` 6/6, `ConversationControllerTest` 3/3, `MatchNfsQueryCountTest` 2/2, `MatchFlowCommittedTest` 3/3, `StompChatIntegrationTest` 4/4, `SecurityConfigIntegrationTest` 14/14, `VietnamLocalDatetimeMigrationTest` 3/3, `NoExplicitTimeZoneTest` 3/3.

- Đồng bộ design & tài liệu theo bảng "Lệch thiết kế" + CCD §11 (2026-11-25): `MATCH-001` v3.1 (`API_Spec.md`, `Component_Class_Design.md`, `Processing_Flow.md`, `CHANGELOG.md`), `USER-001` v5.3 (`Component_Class_Design.md`, header, `CHANGELOG.md`), `VENUE-001` v6.2 (`Component_Class_Design.md`, header, `CHANGELOG.md`), `docs/database/{schema.dbml, schema-design.md, data-model.md}`.
- `code-reviewer` lượt 2 (2026-11-25): 0 CRITICAL / 0 MAJOR / 0 MINOR — cổng review code đạt.
- `architect-reviewer` phần sửa design (2026-11-25): 0 CRITICAL / 0 MAJOR / 0 MINOR — khớp bảng "Lệch thiết kế", tuân thủ `design-standards.md` §11 (`publish-spec.cjs`), không ảnh hưởng SRS.

## Đang làm
- Commit (2 commit: code + `docs(MATCH-001)`) → push → PR → merge (user đã yêu cầu 2026-11-25).

## Còn lại
- Không còn (chỉ còn bước git commit/push/PR/merge).

## Ước tính
100% (code + test PASS + design v3.1 + 2 cổng review PASS).
