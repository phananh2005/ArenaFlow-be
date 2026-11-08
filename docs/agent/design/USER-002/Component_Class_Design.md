# Component & Class Design — Giải tán đội & quy tắc xoá tài khoản đội trưởng

| Mục | Giá trị |
|---|---|
| Mã chức năng | `USER-002` |
| Phạm vi tầng | Backend (Spring Boot REST API) |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · Spring Data JPA · Flyway |
| Quy ước áp dụng | `docs/agent/convention-backend.md`, `docs/agent/security-backend.md` · kiến trúc hiện hành theo USER-001 |
| Ràng buộc dữ liệu | USER-001 schema V1/V2/V3; delta USER-002 dưới đây cần migration Flyway |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-04 · v1.0 (baseline) · hiện tại **v1.7** (developer · 2026-11-09) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan kiến trúc

Mở rộng module USER-001 theo layered Controller → Service → Repository → Entity, đặt trong các module Maven `api` → `application` → `domain` (ADR-0006). Tái dùng `TeamController`, `ProfileController`, `TeamService`, `ProfileService`, `TeamRepository`, `TeamMemberRepository`, `TeamGuard`, `ConcurrencyConflictRetrier`, `TeamMapper`, DTO/envelope/error handling hiện có. Không tạo service/component dùng chung mới.

## 2. Package Structure (delta)

Base package `com.phananh.arenaflow`, Maven multi-module (ADR-0006, `convention-backend.md` §1).

```text
api/           com.phananh.arenaflow.api
└── team/            TeamController (endpoint disband; danh sách/detail đã có), AdminTeamController (membership history admin)

application/   com.phananh.arenaflow.application
├── service/         TeamService, ProfileService, AdminTeamService, TeamStatusFilter (thêm DISBANDED), MemberHistoryStatusFilter (enum mới: ACTIVE | LEFT | ALL, cùng package với TeamStatusFilter)
├── service/impl/    TeamServiceImpl, ProfileServiceImpl, AdminTeamServiceImpl
├── dto/request/     DisbandTeamRequest
├── dto/response/    TeamResponse, TeamSummaryResponse, AdminTeamSummaryResponse, MemberHistoryResponse (mới)
└── mapper/          TeamMapper, MembershipMapper (tái sử dụng)

domain/        com.phananh.arenaflow.domain
├── repository/      TeamRepository, TeamMemberRepository
├── projection/      TeamDetailView, TeamSummaryView (thêm người giải tán; dùng chung list user/admin), MemberHistoryView (mới)
└── entity/team/     Team (thêm lifecycle/disband fields), TeamMember (giữ model)
```

Tái dùng không đổi vị trí: `TeamGuard`, `ConcurrencyConflictRetrier` (`application/support/`), `ProfileController` (`api/profile/`). Test cần Spring context (migration, integration, đếm query) đặt ở module `api`, giữ package của lớp được kiểm.

## 3. Controller Layer

### 3.1. TeamController (mở rộng)

- `POST /api/v1/teams/{teamId}/disband` → `@RequestBody(required = false) @Valid DisbandTeamRequest` (null → coi như không có reason), nhận `@CurrentUser UUID`, gọi `TeamService.disbandTeam`; không chứa domain logic.
- Endpoint list/detail và history giữ controller/service hiện hữu, chỉ mở rộng contract/status filter.

### 3.2. AdminTeamController

Thêm `GET /api/v1/admin/teams/{teamId}/members`; route `/api/v1/admin/**` yêu cầu role ADMIN theo SecurityConfig hiện hành. Gọi `AdminTeamService` để trả lịch sử membership phân trang.

### 3.3. ProfileController

`DELETE /api/v1/profile` giữ route hiện tại; xử lý HTTP như USER-001, việc guard captain ownership cập nhật ở service/repository.

## 4. Service Layer

### 4.1. TeamService · TeamMembershipService · TeamGuard

Thêm phương thức:
```java
TeamResponse disbandTeam(UUID teamId, UUID captainUserId, DisbandTeamRequest request);
```
Mở rộng filter trạng thái hỗ trợ `DISBANDED`, không đổi `ACTIVE` mặc định. Kiểm tra captain ACTIVE và team ở `ACTIVE|INACTIVE`; trạng thái cuối từ chối theo lỗi chốt tại `API_Spec.md` §4. `TeamGuard` mở rộng theo thứ tự `API_Spec.md` §2.1a:
- `activeTeam(teamId)`: tồn tại → `DISBANDED` (`TEAM_016`) → `INACTIVE` (`TEAM_015`). Dùng cho update, accept/reject, transfer (invite dùng `activeTeamForInvite`).
- `notDisbandedTeam(teamId)` (mới): `findById` → `TEAM_001` → `DISBANDED` (`TEAM_016`); không chặn `INACTIVE`. Dùng cho disband, leave (thay `requireTeam`, vẫn 1 query), admin mark-inactive/reactivate.
- `disbandedOr(teamId, fallback)` (mới, bổ sung sau implement — `Processing_Flow.md` §5): guard thất bại **sau** Q1 đọc lại trạng thái đã commit; `DISBANDED` → `TEAM_016`, ngược lại `fallback`. Dùng trong `requireCaptain` (`TEAM_002`) và tại `transferCaptain` (`TEAM_007`), `leaveTeam` (`TEAM_010`), admin `reactivate` (`TEAM_013`). `findTeam` chuyển `private` để mọi thao tác ghi bắt buộc qua `activeTeam`/`notDisbandedTeam`.
- `inviteMember`, `leaveTeam` nằm ở `TeamMembershipService(Impl)` (tách từ USER-001), không phải `TeamService`; thay đổi áp tại đó.
- `inviteMember`: chuyển sang chạy trong `ConcurrencyConflictRetrier` (bỏ `@Transactional` ở method theo ADR-0003). Đọc team bằng `teamGuard.activeTeamForInvite(teamId)` (mới, chốt bởi user 2026-11-05): gọi `TeamRepository.findForInviteById` có `@Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)` rồi áp cùng kiểm tra trạng thái với `activeTeam()` (`TEAM_001 → TEAM_016 → TEAM_015`, tách `requireActiveState` dùng chung). `team.version` tăng trước khi commit. Không inject `EntityManager` vào service; JPA giữ ở tầng repository. Race M1: `Processing_Flow.md` §5. Giữ xử lý `DataIntegrityViolationException` → `TEAM_003`; thêm khả năng `409 TEAM_014` khi hết retry.
- `disbandTeam`: `notDisbandedTeam` → `requireCaptain` (SELECT, `TEAM_002`) → captain entity LEFT + `save` → team entity DISBANDED + `save` → `disbandRemaining` (Hibernate tự flush captain + team một lần trước native query) → projection. Chi tiết: `Processing_Flow.md` §4.1.

### 4.2. AdminTeamService

Thêm `PageResponse<MemberHistoryResponse> listMemberHistory(UUID teamId, MemberHistoryStatusFilter status, int page, int pageSize)`: `existsById` → `TEAM_001`, rồi `findMemberHistory` (§5.2). Mapper ánh xạ `MemberHistoryView` → `MemberHistoryResponse`, che tên qua `MapperSupport`; không expose entity. `listTeams` giữ nguyên, projection thêm người giải tán.

### 4.3. ProfileService

`deleteAccount(UUID userId)` giữ signature. Luồng Q1–Q3 (khoá bi quan có phạm vi, ADR-0007) và các race liên quan: `Processing_Flow.md` §4.2, §5. Không tự đổi trạng thái team.

## 5. Repository & Data Model

### 5.1. Delta dữ liệu Team (cần migration)

USER-001 có `team.status` với `ACTIVE|INACTIVE`, `inactive_reason VARCHAR(500)`, `version BIGINT`; `Team` có `@Version`. USER-002 cần bổ sung:

| Khái niệm | Thiết kế đề xuất | Ràng buộc |
|---|---|---|
| Lifecycle | Thêm `DISBANDED` vào status enum/DB CHECK constraint | Không xóa cứng; terminal |
| Disband reason | `disband_reason VARCHAR(100) NOT NULL` cho `DISBANDED`, null trước đó | Mặc định nghiệp vụ nếu không có reason |
| Disband time | `disbanded_at` nullable timestamp UTC | Có giá trị đúng khi `DISBANDED` |
| Disband actor | `disbanded_by UUID` FK tới user | Giữ tham chiếu lịch sử kể cả soft-delete |
| Admin reason | `inactive_reason` giữ nguyên | Giải tán khi INACTIVE không ghi đè |

**Migration `V4__team_disband.sql`** (`api/src/main/resources/db/migration/`) (đã đối chiếu V1/V2 và DB dev Neon `misty-dew-90700890`, 2026-11-05: CHECK tên `team_status_check`; bảng `team` có 0 dòng, không có đội thiếu captain → không cần migration dữ liệu):

```sql
ALTER TABLE team DROP CONSTRAINT team_status_check;
ALTER TABLE team ADD CONSTRAINT team_status_check CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISBANDED'));
ALTER TABLE team ADD COLUMN disband_reason VARCHAR(100);
ALTER TABLE team ADD COLUMN disbanded_at TIMESTAMPTZ;
ALTER TABLE team ADD COLUMN disbanded_by UUID REFERENCES "user"(id) ON DELETE RESTRICT;
ALTER TABLE team ADD CONSTRAINT team_disband_consistency_check CHECK (
    (status = 'DISBANDED' AND disband_reason IS NOT NULL AND disbanded_at IS NOT NULL AND disbanded_by IS NOT NULL)
    OR (status <> 'DISBANDED' AND disband_reason IS NULL AND disbanded_at IS NULL AND disbanded_by IS NULL));
```

- `ON DELETE RESTRICT` nhất quán `team.created_by`; user chỉ soft-delete (ADR-0002) nên không chặn luồng hiện có.
- Không thêm index: `disbanded_by` không dùng làm điều kiện lọc; list tiếp tục dùng `idx_team_status_created (status, created_at DESC)` (V2).
- Bulk `CASE` khi disband chỉ set `left_at` cho row ACTIVE, để không vi phạm CHECK `left_at >= COALESCE(joined_at, invited_at)` của `team_member` với row PENDING.
- Kiểm chứng bằng migration integration test. Match impact không xử lý ở đây.
- **Giả định dữ liệu cũ:** hiện chỉ có môi trường dev (0 đội). Trước khi deploy lên môi trường có dữ liệu, nếu tồn tại đội `INACTIVE` do captain duy nhất xoá tài khoản (theo ADR-0002 #4 cũ), các đội này không còn captain: không giải tán được (`TEAM_002`), không kích hoạt lại được (`TEAM_013`), vi phạm BC-T06. Khi đó phải có phương án dữ liệu riêng được duyệt (xem ADR-0005 "Cần theo dõi"). Bản thân V4 an toàn với dữ liệu cũ.

### 5.2. Repository query (delta)

- `TeamRepository`: detail projection thêm disband metadata + người giải tán (join user); page summary thêm người giải tán; filter status enum hỗ trợ `DISBANDED` và `ALL`.
- `TeamRepository.findCurrentStatusById(id)` (mới, bổ sung sau implement): native `SELECT status FROM team WHERE id = :id` → `Optional<String>`; chỉ dùng trong `TeamGuard#disbandedOr` (nhánh lỗi).
- `TeamRepository.findForInviteById(id)` (mới): JPQL `SELECT t FROM Team t WHERE t.id = :id` với `@Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)`; chỉ dùng cho `inviteMember` (race M1).
- Captain → LEFT và `Team` → DISBANDED đều qua entity + `save` (CAS `@Version` khi flush; `Team` có `@PreUpdate`), **không** thêm query viết tay. Kiểm tra quyền dùng `TeamGuard.requireCaptain` có sẵn.
- `TeamMemberRepository.disbandRemaining(teamId, now)` (mới, `@Modifying` native): **một** bulk transition dùng `CASE`: `ACTIVE` → `LEFT` với `left_at = GREATEST(:now, COALESCE(joined_at, invited_at))`, `PENDING` → `REJECTED` (không set `left_at`), `version = version + 1`, điều kiện `team_id = :teamId AND status IN ('ACTIVE','PENDING')`. Gọi sau khi captain và team đã flush.
- `TeamMemberRepository.findByUserIdAndStatusInOrderByIdAsc(userId, statuses)` (v1.2, ADR-0007): derived query với `@Lock(LockModeType.PESSIMISTIC_WRITE)` (PostgreSQL: `FOR NO KEY UPDATE`) trên mọi membership ACTIVE/PENDING của user, sắp theo `id` (thứ tự khoá ổn định); trả entity `TeamMember` sẵn có (không projection mới). Chỉ dùng cho `deleteAccount`; phải gọi trong transaction.
- `TeamMemberRepository.leaveAndRejectAllOpen(userId, now)` (v1.2, ADR-0007, `@Modifying(clearAutomatically = true)` native): **một** bulk `CASE` theo mẫu `disbandRemaining`: MEMBER `ACTIVE` → `LEFT` với `left_at = GREATEST(:now, COALESCE(joined_at, invited_at))`, `PENDING` → `REJECTED` (không set `left_at`), `version = version + 1`, điều kiện `user_id = :userId AND (status = 'PENDING' OR (status = 'ACTIVE' AND role = 'MEMBER'))` — không đụng row CAPTAIN (lớp an toàn).
- **Bỏ ở v1.2** (thay bằng hai method trên; chỉ `deleteAccount` dùng): `existsActiveCaptainByUserId`, `leaveAllActive`, `rejectAllPending` và test riêng của chúng trong `TeamRepositoryTest`.
- **Xoá code chết** (BC-T02 sửa, đã kiểm tra chỉ `deleteAccount` dùng): `TeamRepository.markInactiveByIds`, `TeamMemberRepository.findCaptainTeamsWithActiveCount`, projection `CaptainTeamView`, hằng `ProfileServiceImpl.SOLE_CAPTAIN_DELETED_REASON`; cùng các test liên quan trong `ProfileServiceImplTest` và `TeamRepositoryTest`. Viết lại theo hành vi mới: `ProfileServiceIntegrationTest.deleteAccountInactivatesSoloTeamLeavesRejectsSoftDeletesAndIsIdempotent` (đang assert đội INACTIVE).
- `TeamMemberRepository.findMemberHistory(teamId, statuses, pageable)`: native projection `MemberHistoryView` (cột của `MemberView` + `left_at`), `WHERE team_id = :teamId AND status IN (:statuses)`, `ORDER BY joined_at ASC, id ASC`, kèm `countQuery`. Phục vụ endpoint Admin `API_Spec.md` §2.4 (mọi trạng thái đội; `ACTIVE`/`LEFT`).

## 6. Entity

### 6.1. Team

Bổ sung trạng thái `DISBANDED`; fields tương ứng `disbandReason`, `disbandedAt`, `disbandedBy`; giữ `inactiveReason` hiện có. Giữ `@Version Long version`. Không sửa `TeamMember` schema để thể hiện rời đội/lời mời bị từ chối vì status và `leftAt` đã tồn tại.

## 7. DTO & Mapper

### 7.1. Request

- `DisbandTeamRequest`: `reason` optional, `@Size(max = 100)` trên giá trị gốc. `TeamServiceImpl` trim; rỗng sau trim → `Đội trưởng giải tán đội`.

### 7.2. Response

- `TeamResponse`: thêm `disbandedAt`, `disbandedById`, `disbandedByName`, `disbandReason`.
- `TeamSummaryResponse`, `AdminTeamSummaryResponse`: trạng thái mới và `disbandedByName`.
- `MemberHistoryResponse` (mới): field của `MemberResponse` + `leftAt`.
- `TeamMapper`: ánh xạ projection/fields mới; `disbandedByName` che qua `MapperSupport`/`DeletedUserMasking` dựa trên cờ deleted của người giải tán trong projection. Không expose entity.

## 8. Dependencies

Không thêm dependency. Spring Web, Spring Data JPA, Spring Security, Validation, Flyway, PostgreSQL và MapStruct đã có theo USER-001.

## 9. Danh sách [cần xác nhận]

1. ~~Delta vật lý~~ — **đã đối chiếu:** `V4__team_disband.sql` §5.1; migration chạy qua Flyway, không sửa DB thủ công.
2. ~~Query endpoint Admin lịch sử thành viên~~ — **đã chốt:** §5.2.
3. ~~Chuẩn hoá `reason`~~ — **đã chốt:** trim, rỗng → mặc định.
4. ~~Lỗi state `DISBANDED` và error code xoá account~~ — **đã chốt:** `ErrorCode` thêm `TEAM_016`, sửa message `USER_003`; xem `API_Spec.md` §4.
5. ~~Mask tên người giải tán~~ — **đã chốt:** `DeletedUserMasking`, detail và summary.

## 10. Tài liệu cần đồng bộ khi implement

Cập nhật sau khi mọi phase code xong và test PASS (CLAUDE.md §8), ghi vào `USER-001/CHANGELOG.md` khi sửa tài liệu USER-001:

| Tài liệu | Nội dung lỗi thời | Nhánh |
|---|---|---|
| `docs/agent/design/USER-001/API_Spec.md` §2.7 · `Processing_Flow.md` §2.7 + dòng race xoá captain · `Component_Class_Design.md` (`markInactiveByIds`) | Nhánh "captain duy nhất → đội INACTIVE" | `feature/USER-002-*` — ✅ USER-001 v2.0 |
| `docs/agent/design/USER-001/Processing_Flow.md` §1 | Thứ tự guard "tồn tại → quyền → state" → thay bằng `API_Spec.md` §2.1a USER-002; "Bean Validation (400) → JWT (401)" sai thứ tự thực tế (filter Security trả 401 trước) | `feature/USER-002-*` — ✅ USER-001 v2.0 |
| `docs/database/data-model.md` · `schema.dbml` · `schema-design.md` | Ngoại lệ tự INACTIVE; CHECK `team.status` chỉ ACTIVE/INACTIVE; thiếu cột disband | `feature/USER-002-*` — ✅ (bảng `team`, `team_member` tới V4) |
| `docs/agent/CLAUDE.md` §2 | Thêm dòng điều hướng USER-002 | Chưa — qua `chore/agent-framework-*` |
| `docs/agent/api-convention.md` §2.1 | Ngoại lệ endpoint hành động (action sub-resource), qua `workflow-adapt` | Chưa — qua `chore/agent-framework-*` |
| `docs/agent/decisions/0002-*.md` | §Quyết định #4 (captain duy nhất xoá tài khoản → đội tự `INACTIVE`) thay bởi ADR-0005 | ✅ PR #19 — giữ `Accepted` + hàng/mục `Sửa đổi 2026-11-06`, không chuyển `Superseded` (thay một phần §Quyết định #4; `decisions/README.md` không có hàng cho thay thế một phần nên PR #19 dùng `Sửa đổi`) |
| `docs/agent/decisions/0003-*.md` | Hệ quả "không deadlock" chưa tính trường hợp nhiều dòng `team_member` bị khoá theo thứ tự khác nhau (`Processing_Flow.md` §5); thêm mục Sửa đổi + liên kết USER-002/ADR-0005 ở "Liên quan" | ✅ PR #19 (`Sửa đổi` ngoại lệ `deleteAccount` + liên kết ADR-0007) · PR #34 (`Sửa đổi` "Phạm vi của 'không deadlock'" trỏ ADR-0007 §Hệ quả + liên kết USER-002/ADR-0005); Hệ quả gốc không viết lại |
