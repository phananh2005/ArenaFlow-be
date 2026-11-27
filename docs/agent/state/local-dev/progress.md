---
task: USER-003 — tra cứu cho người dùng (V12)
updated: 2026-11-21
percent: 70
---

## Đang làm
- Bước hiện tại: Phase 1–6 xong — DỪNG trước `code-reviewer` (theo coordinator).
- Tóm tắt: Giai đoạn 1 (chỉ lập kế hoạch) xong: nhánh `feature/USER-003-user-discovery-impl` từ `origin/main` `6fe6285`; Reuse Report + kế hoạch 7 phase + phụ thuộc tạm (không có) + test để tích hợp.

## Đã xong (phiên này)
- Tạo nhánh `feature/USER-003-user-discovery-impl` từ `origin/main`.
- Đọc thiết kế USER-003 v2.0, ADR-0016, ADR-0017/`convention-backend.md` §6.2, NFS, SYSTEM-002 CCD (phần chồng file).
- `reuse-scan` → mục "Tái sử dụng"; kế hoạch → mục "Kế hoạch chi tiết".
- Phase 1 xong: `USER_004..007`, `RateLimitExceededException`, handler 429 + `Retry-After` (TDD).
- Phase 2 xong: `InvitationSearchQuery`, `InvitationSearchRateLimiter` (TDD).
- Phase 3 xong (code + integration test viết, CHƯA chạy): V12, 5 projection, 3 repository, fixture SQL `testsupport/UserDiscoveryFixtures`; `test-compile` api qua.
- Phase 4 xong: 7 DTO, `AdministrativeUnitMapper`, `MembershipMapper` +3, `TeamMapper` +1 (TDD).
- Phase 5 xong: `AdministrativeUnitService(Impl)`, `MyTeamRoleFilter`, `TeamMembershipService` +2, `TeamService` +1 (TDD).
- Phase 6 xong: `AdministrativeUnitController`, `ProfileTeamController`, `TeamController` +2.3 (limiter trước service) (TDD); integration test controller/NFS/timeout viết xong, CHƯA chạy.

## Còn lại
- Phase 1–6 (TDD, unit chạy ngay, integration chỉ viết) → `code-reviewer`.
- Phase 7 giai đoạn tích hợp (sau V6–V11 trên `main`): integration + NFS + EXPLAIN + timeout, docs database, đối chiếu design, `architect-reviewer`.

## Kiểm chứng đã chạy
- Phase 1: `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -pl common,api -am -Dtest='RateLimitExceededExceptionTest,BusinessExceptionTest,GlobalExceptionHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false test` → common 3/3, api 13/13 PASS, BUILD SUCCESS.
- Phase 2: `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -pl application -am -Dtest='InvitationSearchQueryTest,InvitationSearchRateLimiterTest' -Dsurefire.failIfNoSpecifiedTests=false test` → 35/35 PASS, BUILD SUCCESS.
- Phase 4: `-pl application -am -Dtest='UserDiscoveryMapperTest,UserDiscoveryResponseJsonTest,MapperTest'` → 20/20 PASS, BUILD SUCCESS.
- Phase 5: `-pl application -am -Dtest='TeamMembershipServiceImplSearchTest,AdministrativeUnitServiceImplTest,TeamServiceImplMyTeamsTest,TeamMembershipServiceImplTest,TeamServiceImplTest'` → 41/41 PASS, BUILD SUCCESS.
- Phase 6: `-pl api -am -Dtest='TeamControllerInvitationSearchTest,GlobalExceptionHandlerTest'` → 15/15 PASS.
- Tổng kết: `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -pl common,application,api -am -Dtest='<15 lớp unit>' -Dsurefire.failIfNoSpecifiedTests=false test` → common 3, application 98, api 15 = 116 PASS, BUILD SUCCESS. `./mvnw -q -Duser.timezone=Asia/Ho_Chi_Minh test-compile` toàn repo → exit 0.
- CHƯA chạy (cần DB/Spring/Flyway): migration, repository, controller MockMvc, NFS, timeout.

## Blockers
- Không có `.env.dev` trong worktree; không chạy test Spring/Flyway trước tích hợp (B1 trong `current-task.md`).
