---
task: MATCH-001 đợt 1 — kèo luồng A + lõi chat + realtime (V7)
created: 2026-11-21
status: in_progress
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

## Đang làm
- Dừng trước `code-reviewer` (coordinator chạy).

## Còn lại
- `code-reviewer`; đối chiếu design + cập nhật design; integration test ở giai đoạn tích hợp.

## Ước tính
~85% (code Phase 0–9 + kiểm chứng unit xong; còn review / design / tích hợp).
