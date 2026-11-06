# Processing Flow — Giải tán đội & quy tắc xoá tài khoản đội trưởng

| Mục | Giá trị |
|---|---|
| Mã chức năng | `USER-002` |
| Phạm vi tầng | Backend (Spring Boot REST API) |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · PostgreSQL |
| Quy ước áp dụng | `docs/agent/non-functional-standards.md` (N_DETAIL=3, N_LIST=4, N_WRITE=6), ADR-0003 optimistic retry, ADR-0007 (ngoại lệ khoá bi quan cho `deleteAccount`) |
| Ràng buộc dữ liệu | USER-001 V1/V2/V3 + delta `Component_Class_Design.md` §5 |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-04 · v1.0 (baseline) · hiện tại **v1.6** (developer · 2026-11-07) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan

Các luồng ghi dùng `ConcurrencyConflictRetrier` hiện hữu (quy tắc retry: USER-001 `Processing_Flow.md` §5). Transaction theo READ_COMMITTED. Không gọi service ngoài, không notification. Thứ tự guard theo từng endpoint: xem `API_Spec.md` §2.1a (nguồn duy nhất).

## 2. Sequence — giải tán đội

```mermaid
sequenceDiagram
    participant C as Client
    participant TC as TeamController
    participant TS as TeamServiceImpl
    participant RT as ConcurrencyConflictRetrier
    participant TR as TeamRepository
    participant MR as TeamMemberRepository
    participant DB as PostgreSQL
    C->>TC: POST /api/v1/teams/{teamId}/disband
    TC->>TS: disbandTeam(teamId, currentUserId, request)
    TS->>RT: execute(transaction)
    RT->>TR: Q1 findById team (TEAM_001, TEAM_016)
    RT->>MR: Q2 requireCaptain (SELECT; không phải captain → TEAM_002)
    RT->>MR: Q3 saveAndFlush captain → LEFT (@Version; 0 dòng → retry)
    RT->>TR: Q4 saveAndFlush Team → DISBANDED (@Version; 0 dòng → retry)
    RT->>MR: Q5 1 bulk CASE: ACTIVE→LEFT, PENDING→REJECTED
    RT->>TR: Q6 load response projection
    TS-->>C: 200 TeamResponse
```

## 3. Luồng chi tiết và truy vấn dự kiến

Số query là mức dự kiến một lần thử; JWT xác thực không truy vấn DB. Bao gồm SELECT/UPDATE statements, count query riêng nếu có. Các truy vấn bulk được thực hiện set-based, không lặp theo thành viên/đội. Persistence batching/entity flush behavior phải được đo integration test.

| API | Guard/logic & DB work | Số truy vấn | Ngưỡng |
|---|---|---:|---:|
| `POST /teams/{teamId}/disband` | S team → S captain → U captain → U team → U bulk CASE → S projection (chi tiết §4.1) | 6 | W6 ✅ (bằng ngưỡng) |
| `DELETE /profile` | S khoá membership ACTIVE/PENDING (`PESSIMISTIC_WRITE`) → U bulk member + pending (một `CASE`) → U user (chi tiết §4.2, ADR-0007) | 3 | W6 ✅ |
| `GET /teams/{teamId}` | Q1 TeamDetailView join captain/user + disband actor/user + memberCount projection | 1 | D3 ✅ |
| `GET /teams` | Q1 page summary projection (status filter, disband actor display); Q2 count | 2 | L4 ✅ |
| `GET /admin/teams` | Như USER-001; projection thêm người giải tán (join user trong cùng query); mặc định `ALL` nên gồm cả đội `DISBANDED` | 2 | L4 ✅ |
| `GET /teams/{teamId}/captain/history` | USER-001 query existence + page + count | 3 | L4 ✅ |
| `GET /admin/teams/{teamId}/members` | Admin authorization (JWT, không query) → Q1 `existsById` team (`TEAM_001`) → Q2 page projection `team_member` JOIN `user` lọc `team_id` + `status IN (ACTIVE, LEFT)` (hoặc 1 giá trị), sort `joined_at, id` → Q3 count | 3 | L4 ✅ |
| `POST /teams/{teamId}/invitations` | Q1 S team qua `TeamGuard.activeTeamForInvite` (`TeamRepository.findForInviteById` có `@Lock(OPTIMISTIC_FORCE_INCREMENT)`) → Q2 S captain → Q3 S invitee → Q4 S tồn tại membership → Q5 I invitation → Q6 U `team.version` (force increment trước khi commit) | 6 | W6 ✅ (bằng ngưỡng; USER-001 = 5) |
| `POST /teams/{teamId}/members/leave` | Như USER-001; `requireTeam` (`existsById`) đổi thành `notDisbandedTeam` (`findById`), vẫn 1 query | 3 | W6 ✅ |
| Các mutation USER-001 khác bị chặn thêm khi status DISBANDED | Dùng team query hiện có rồi guard trước mutation; không cộng query riêng | Như USER-001 | Không vượt so với USER-001 |

**NFS gate:** các API thiết kế hiện không vượt 6/6; disband 6/6 và invite 6/6 (vừa khít ngưỡng). Không thêm truy vấn audit/notification/reload khác cho endpoint disband nếu chưa gộp trong projection/statement hoặc được user duyệt tăng ngưỡng. Truy vấn list đều phân trang, sort ổn định theo USER-001. Không N+1.

## 4. Transaction & guard

### 4.1. `disbandTeam`

- Bean Validation trước service: `reason` ≤100; lỗi `400 VALIDATION_ERROR`.
- Mỗi retry transaction mới. Q1 `findById` team: không có → `TEAM_001`; `DISBANDED` → `422 TEAM_016`. `ACTIVE` và `INACTIVE` cho phép.
- **Tách kiểm tra quyền khỏi ghi (chốt bởi user, 2026-11-05, thay phương án gộp trước đó):** `TEAM_002` chỉ phát sinh ở bước đọc Q2; mọi bước ghi cập nhật 0 dòng đều được hiểu là "có thao tác khác vừa sửa" → retry.
- Q2 `teamGuard.requireCaptain(teamId, callerId)` (SELECT, có sẵn USER-001): caller không phải captain ACTIVE → `403 TEAM_002`.
- Q3 gán `status=LEFT`, `leftAt=now` trên entity captain đọc ở Q2; `saveAndFlush` → `UPDATE team_member ... WHERE id=? AND version=?`. 0 dòng → `ObjectOptimisticLockingFailureException` → retry. `left_at=now` luôn ≥ `joined_at` của captain (đã ở quá khứ).
- Q4 chuẩn hoá reason (`trim()`, rỗng → `Đội trưởng giải tán đội`, BR-T08); gán `status=DISBANDED`, `disbandReason`, `disbandedAt`, `disbandedBy` trên entity `Team` đọc ở Q1, giữ nguyên `inactiveReason`; `saveAndFlush` → `UPDATE team ... WHERE id=? AND version=?`; 0 dòng → retry. `@PreUpdate` cập nhật `updated_at`.
- Q5 chuyển mọi row ACTIVE/PENDING còn lại của đội trong một câu `UPDATE ... SET status = CASE ...`; với row ACTIVE ghi `left_at = GREATEST(:now, COALESCE(joined_at, invited_at))` để luôn thoả CHECK `left_at >= COALESCE(joined_at, invited_at)` khi một accept commit sau thời điểm `:now` (N1); row PENDING không set `left_at`. Q5 chạy sau khi Q3/Q4 đã flush nên không đụng row captain và không va chạm version với chính transaction. Q6 dựng response.
- Kết quả theo tình huống:
  - Đội trưởng bấm giải tán 2 lần chồng nhau: request sau qua được Q1/Q2 (request trước chưa commit), Q3 chờ khoá rồi cập nhật 0 dòng → retry → Q1 thấy `DISBANDED` → `422 TEAM_016`. Nếu request trước commit giữa Q1 và Q2 của request sau → Q2 không thấy captain ACTIVE → đọc lại trạng thái (§5) → `TEAM_016`.
  - Transfer commit trước (R2): Q3 cập nhật 0 dòng → retry → Q2 → `403 TEAM_002`. Disband commit trước: transfer flush row captain cũ lệch version → retry → `TEAM_016`; hoặc transfer đọc đội trước commit nhưng đọc captain/member mới sau commit (đã LEFT) → đọc lại trạng thái (§5) → `TEAM_016` (đã tái hiện bằng test race).
  - Thành viên thường gọi disband: Q2 → `TEAM_002`, không ghi gì.
- Ranh giới transaction bao gồm team, membership và invitation updates; mọi lỗi rollback cả ba nhóm. Không hard-delete history.

### 4.2. `deleteAccount`

**Khoá bi quan có phạm vi (v1.2, ADR-0007 — ngoại lệ của ADR-0003).** Lý do: các bước ghi là bulk UPDATE (chỉ tăng, không kiểm `version`), nên `@Version` không bảo vệ chính luồng này; bản v1.1 (kiểm → ghi có điều kiện → kiểm lại, 5 truy vấn) để hở race delete vs accept của chính user (đã tái hiện bằng test, ADR-0007 Bối cảnh).

- Mỗi lần thử của retrier là một transaction. Luồng này không tự sinh optimistic conflict, nhưng có thể bị PostgreSQL huỷ do deadlock → retrier thử lại (USER-001 `Processing_Flow.md` §5), lặp lại đủ 3 lần → `TEAM_014`.
- Q1 `findByUserIdAndStatusInOrderByIdAsc(userId, {ACTIVE, PENDING})` với `PESSIMISTIC_WRITE` (Hibernate 7.2 + PostgreSQL sinh `SELECT … FOR NO KEY UPDATE` — chặn mọi UPDATE khác trên các row này), sắp theo `id`. Khoá giữ tới hết transaction. Không cần lọc `team.status <> 'DISBANDED'` vì bất biến: đội `DISBANDED` không còn row ACTIVE/PENDING nào (BR-T10).
- Kiểm trên dữ liệu đã khoá: có row `ACTIVE` + `CAPTAIN` → `422 USER_003`, rollback, không ghi gì.
- Q2 `leaveAndRejectAllOpen(userId, now)`: một bulk `CASE` — MEMBER ACTIVE → LEFT (`left_at = GREATEST(:now, COALESCE(joined_at, invited_at))`), PENDING → REJECTED, `version + 1`; điều kiện `role = 'MEMBER'` cho nhánh ACTIVE giữ làm lớp an toàn (không bao giờ chuyển row CAPTAIN sang LEFT). `now` lấy **sau** Q1.
- Q3 soft-delete như USER-001. Gọi lại khi đã xoá: không lỗi (idempotent). Bỏ nhánh tự INACTIVE đội solo (`markInactiveByIds`) theo BC-T02 sửa.
- Thao tác song song cập nhật row đã khoá chờ tới khi transaction xoá kết thúc. Xoá commit → luồng ghi entity (accept, transfer cho/khỏi user, leave) thấy `version` đã tăng → retry (ADR-0003) → lỗi nghiệp vụ; bulk `disbandRemaining` của disband đánh giá lại `WHERE status IN ('ACTIVE','PENDING')` trên bản mới → bỏ qua row đã LEFT/REJECTED, disband vẫn thành công. Xoá rollback (`USER_003`) → bên kia tiếp tục bình thường.
- Không chuyển status đội do xoá tài khoản.

## 5. Concurrency & locking

Dùng `@Version` Team/TeamMember và retrier USER-001/ADR-0003; cập nhật bulk tự tăng version. Mỗi race dưới đây được tuần tự hoá bằng việc hai luồng cùng ghi một dòng có kiểm tra version. **Ngoại lệ (v1.2, ADR-0007):** `deleteAccount` khoá trước mọi row ACTIVE/PENDING của user (`PESSIMISTIC_WRITE`, §4.2); các race của nó được tuần tự hoá bằng khoá dòng đó.

**Mã lỗi bên thua — đọc lại trạng thái đã commit (bổ sung sau implement, chốt bởi user 2026-11-05):** với READ COMMITTED, request thua có thể đọc đội (Q1) **trước** khi disband commit nhưng đọc captain/member **sau** commit → guard thất bại với mã sai ngữ cảnh (đã tái hiện: transfer nhận `TEAM_007` ở test race R2). Các guard đọc captain/member sau Q1 dùng `TeamGuard#disbandedOr(teamId, fallback)`: đọc `team.status` bằng native query (`TeamRepository#findCurrentStatusById`, bỏ qua persistence context); `DISBANDED` → `TEAM_016`, ngược lại → `fallback`. Áp cho `requireCaptain` (`TEAM_002`: disband, update, invite, transfer), transfer `TEAM_007`, leave `TEAM_010`, reactivate `TEAM_013`. Chỉ thêm **1 truy vấn ở nhánh lỗi**; luồng thành công không đổi số truy vấn (§3).

| Race | Kết quả bất biến |
|---|---|
| Disband vs accept invitation | Accept thắng → Q5 chuyển member mới sang LEFT, `left_at` theo công thức §4.1 Q5 (không vi phạm CHECK; có thể lớn hơn `disbanded_at` vài ms — test chỉ assert `left_at >= joined_at`, không assert `left_at == disbanded_at`); disband thắng → Q5 đã tăng version row lời mời → accept retry → `TEAM_016`. Không có ACTIVE member trên team đã disband |
| Disband vs transfer captain (R2) | Transfer thắng → `TEAM_002`; disband thắng → transfer `TEAM_016`. Cơ chế: §4.1 Q2–Q3 |
| Disband vs disband (double-submit) | Request sau → `TEAM_016`. Cơ chế: §4.1 Q3 |
| Disband vs admin lock | Cả hai CAS `team.version` (disband Q4, `markInactive`/`reactivate` `saveAndFlush`). Lock thắng → disband retry, giải tán từ INACTIVE, giữ `inactive_reason`; disband thắng → lock retry → `notDisbandedTeam` → `TEAM_016` |
| Disband vs invite (M1) | Invite tăng `team.version` lúc commit (`OPTIMISTIC_FORCE_INCREMENT`), disband CAS `team.version` ở Q4. Disband thắng → invite lệch version, rollback cả INSERT → retry → `TEAM_016`; invite thắng → Q4 cập nhật 0 dòng → disband retry → Q5 chuyển lời mời mới sang REJECTED. Hệ quả phụ: các invite song song vào cùng một đội bị tuần tự hoá trên `team.version` và có thể nhận `409 TEAM_014` khi hết 3 lần thử. Đội `DISBANDED` không bao giờ còn lời mời PENDING |
| Leave vs disband | Leave thắng → member LEFT trước, disband Q5 bỏ qua row đó; disband thắng → leave không còn row ACTIVE (`TEAM_010`) → đọc lại trạng thái → `TEAM_016` |
| Admin reactivate vs disband | Đội INACTIVE: disband thắng → reactivate không thấy captain (`TEAM_013`) → đọc lại trạng thái → `TEAM_016`; reactivate thắng → disband giải tán từ ACTIVE |
| Delete captain vs accept invitation | Guard captain ownership ngăn xoá bất kể thứ tự; accept không được làm mất captain |
| Delete account vs transfer-cho-chính-user (R1) | Transfer thắng → Q1 chờ khoá, nhận row đã là CAPTAIN → delete `USER_003`; delete thắng → transfer chờ khoá, lệch version → retry → `TEAM_007`. Đội không bao giờ mất captain. Cơ chế: §4.2 Q1 (v1.2; v1.1 dùng kiểm lại Q3) |
| Delete account vs captain chuyển quyền **đi** (v1.2) | U là captain, chuyển cho X trong lúc U xoá tài khoản. Transfer thắng → U thành MEMBER → delete xoá được (U LEFT); delete khoá trước → thấy U CAPTAIN → `USER_003`, rollback → transfer tiếp tục. Luôn đúng 1 captain; user đã xoá không còn ACTIVE. Cơ chế: §4.2 Q1 (v1.1 chỉ an toàn nhờ thứ tự Q1 trước Q2 — đã kiểm bằng mutation) |
| Delete account vs accept lời mời của chính user (v1.2) | Accept thắng → delete chuyển row vừa ACTIVE sang LEFT (`left_at` theo `GREATEST`); delete khoá trước → accept chờ, lệch version → retry → `TEAM_006`. User đã xoá không còn ACTIVE/PENDING, không vi phạm CHECK. v1.1 hở: accept commit giữa Q2 và Q4 → user đã xoá còn ACTIVE (tái hiện bằng test 2026-11-06) |
| Retry/replay disband | Chỉ trạng thái ACTIVE/INACTIVE được chuyển; lặp sau DISBANDED trả lỗi state, không ghi đè metadata |

**Kiểm chứng bắt buộc khi implement (chốt bởi user, 2026-11-05):** integration test cho R1, R2, M1 (invite vs disband), double-submit disband (`TEAM_016`) và disband vs accept (assert `left_at >= joined_at`, không 500), viết Red trước để reproduce race, rồi mới sửa; thêm `NfsQueryCountTest` cho disband (= 6), delete account (~~= 5~~ → **= 3** từ v1.2) và invite (= 6). Không dùng `SELECT … FOR UPDATE` (ADR-0003) — **ngoại trừ `deleteAccount` (ADR-0007, v1.2)**. **Cập nhật v1.2 (2026-11-06):** thêm race "chuyển quyền đi", "accept lời mời của chính user" và test khoá thật `AccountDeletionLockIntegrationTest`.

- Test race và test đếm query cho invite phải chạy **không** `@Transactional` ở mức test (commit thật qua retrier). `NfsQueryCountTest` hiện có `@Transactional` mức class và chỉ `flush` (`NfsQueryCountTest.java:32`); bước tăng version của `OPTIMISTIC_FORCE_INCREMENT` chạy trước khi commit, không chạy khi flush, nên harness hiện tại sẽ đo thiếu. [Inference: theo hành vi Hibernate, test sẽ kiểm chứng.]
- Conflict của `OPTIMISTIC_FORCE_INCREMENT` nổi lên lúc commit (ngoài lambda). Retrier phải nhận được `ObjectOptimisticLockingFailureException` ở đó để retry. [Unverified: Spring dịch lỗi commit sang `ObjectOptimisticLockingFailureException`; test M1 kiểm chứng, sai → dừng, báo.]
- Giả định không deadlock R2: `transferCaptain` flush captain cũ trước captain mới, cùng thứ tự khoá với disband (captain cũ ở Q3 → các row khác ở Q5). [Inference] Thứ tự flush theo thứ tự nạp entity (`hibernate.order_updates` chưa bật). Đảo thứ tự → deadlock `40P01` (đã kiểm bằng mutation flush captain mới trước, 2026-11-06) → bên bị huỷ được retrier thử lại (USER-001 `Processing_Flow.md` §5); lặp lại đủ 3 lần → `TEAM_014`. Vì retrier che deadlock (kết quả nghiệp vụ vẫn hợp lệ), test R2 assert output **không** chứa `40P01` → đảo thứ tự khoá làm R2 fail (cùng mutation trên).
- `spring.jpa.open-in-view` chưa cấu hình nên mặc định bật: trong một request HTTP, mọi lần thử của retrier dùng chung một `EntityManager` gắn theo request (Spring `clear()` khi rollback). Hành vi có từ USER-001. Ngoài test race ở tầng service, thêm **1 test double-submit disband qua MockMvc** để kiểm chứng retry vẫn trả `TEAM_016` trên đường HTTP thật. [Inference]

## 6. Idempotency

`POST .../disband` không idempotent về response: first call chuyển state; replay trên `DISBANDED` trả `422 TEAM_016`, không sửa metadata. Client retry sau timeout phải GET team để xác định outcome. Double-submit chồng nhau cũng trả `TEAM_016` (§4.1). `Idempotency-Key` (`api-convention.md` §13) không làm trong USER-002 (xem `API_Spec.md` §1 "Ngoài phạm vi").

## 7. Error handling

| Tình huống | Kết quả |
|---|---|
| Request reason không hợp lệ | 400 `VALIDATION_ERROR` |
| Team không có | 404 `TEAM_001` |
| Caller không phải captain ACTIVE | 403 `TEAM_002` |
| Team đã disbanded / mutation bị chặn | 422 `TEAM_016` |
| Captain xoá tài khoản khi còn team chưa disbanded | 422 `USER_003` (phạm vi mở rộng) |
| Hết retry (optimistic conflict hoặc deadlock — USER-001 `Processing_Flow.md` §5) | 409 `TEAM_014` |

## 8. Performance / index

- Detail/list dùng projection SQL join, không lazy-load; history/list phân trang, sort stable; count/data queries cố định, N_LIST ≤4.
- Các filter/sort hiện dùng index `(status, created_at DESC)` theo USER-001.
- `disbanded_by` FK lookup có primary key index phía user; không đề xuất index mới nếu không có truy vấn chứng minh cần.
- Disband ghi bằng 2 UPDATE entity có `@Version` (captain, team) + 1 bulk `CASE`, không truy vấn trong vòng lặp. Đo số SQL thực bằng log SQL/`NfsQueryCountTest` trong integration test trước khi merge.

## 9. Danh sách [cần xác nhận]

1. ~~Query endpoint Admin membership history~~ — **đã chốt:** 3 query (§3); dùng index `idx_team_member_team_role (team_id, role, status)` theo tiền tố `team_id`.
2. ~~Số truy vấn disband~~ — **đã chốt (user, 2026-11-05):** tách kiểm tra quyền (Q2 SELECT) khỏi ghi (Q3 `@Version`) để double-submit trả đúng `TEAM_016` → 6 truy vấn (= W6). Đo bằng `NfsQueryCountTest`; vượt → dừng xin xác nhận.
3. ~~Error code/status~~ — **đã chốt:** `422 TEAM_016`, `422 USER_003` mở rộng (`API_Spec.md` §4).
4. ~~Race coordination~~ — **đã chốt (user, 2026-11-05):** R1/R2 theo §4.1, §4.2, §5; đã đối chiếu `ProfileServiceImpl.deleteAccount` và `TeamServiceImpl.transferCaptain`.
