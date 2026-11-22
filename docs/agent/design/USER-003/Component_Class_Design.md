# Component & Class Design — Tra cứu cho người dùng

| Mục | Giá trị |
|---|---|
| Mã chức năng | `USER-003` |
| Phạm vi tầng | Backend (Spring Boot REST API). Frontend: ngoài phạm vi repo này |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · Spring Data JPA · PostgreSQL 18 (Neon) · Flyway |
| Quy ước áp dụng | `docs/agent/convention-backend.md`, `docs/agent/security-backend.md`, `docs/agent/api-convention.md` · kiến trúc hiện hành theo USER-001, USER-002 |
| Ràng buộc dữ liệu | Schema V1–V5 (code) · migration mới `V12__user_name_search.sql` (§6) — chỉ áp sau V5–V11 của VENUE-001, SYSTEM-002 (V6), MATCH-001, MATCH-002, REPUTATION-001, MERCENARY-001, NOTIFICATION-001 |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-17 · v1.0 (baseline) · hiện tại **v2.0** (solution-architect · 2026-11-21) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan kiến trúc

Năm endpoint **chỉ đọc** (`API_Spec.md` §2), layered Controller → Service → Repository như USER-001, đặt trong module `api` → `application` → `domain` → `common` (ADR-0006). Không entity mới, không thao tác ghi, không event/thông báo. Dùng lại: `TeamGuard` (`activeTeam`, `requireCaptain`, `disbandedOr`), `PageRequestFactory`, `PageResponse.from`, `ApiResponse.of`, `MapperSupport` (`skillLevels`, `toUtc`, `maskName`), `DeletedUserMasking`, `UserRepository`, `TeamMemberRepository`, `AdministrativeUnitRepository`, `ErrorCode`/`BusinessException`, `GlobalExceptionHandler`.

**Thành phần mới** (developer chạy `reuse-scan` trước khi tạo — `CLAUDE.md` §8): `AdministrativeUnitController`, `ProfileTeamController`, `AdministrativeUnitService(Impl)`, `InvitationSearchQuery` (phân loại + chuẩn hoá `q`), `InvitationSearchRateLimiter` (giới hạn 2.3 — §4.5), `RateLimitExceededException` + handler `429`, `MyTeamRoleFilter`, 5 projection, 7 DTO response, 1 mapper, 4 mã lỗi, migration V12 (hàm SQL + index).

**Bảng ký tự bỏ dấu — một nguồn duy nhất là hàm SQL `user_name_fold` (§6).** BE **không** giữ bảng dấu trong Java: chuỗi tìm theo tên được chuẩn hoá bằng chính hàm đó qua một câu `SELECT user_name_fold(:q)` (§5.2). Lý do chọn (so với hằng Java khớp + test đối chiếu):
- Không thể lệch: index, vế so khớp và vế sắp xếp đều dùng cùng một hàm; sửa bảng dấu chỉ ở một migration (kèm `REINDEX`).
- Chi phí: thêm 1 truy vấn không đọc bảng (< 1 ms) chỉ ở nhánh tìm theo tên; 2.3 vẫn trong ngưỡng N_LIST = 4 (`Processing_Flow.md` §3.3).
- Phương án loại: hằng Java + test so từng ký tự với hàm SQL — hai bản sao, test chỉ bắt lệch trên tập ký tự được liệt kê; tiết kiệm 1 truy vấn không đáng.

**Chiến lược tìm theo tên** (hàm bỏ dấu `IMMUTABLE` + `tsvector('simple')` + GIN, khớp đầu từ, phương án loại, chi phí khi nhiều người khớp): [ADR-0016](../../decisions/0016-user-name-search-fold-tsvector-gin.md) (`Proposed`).

## 2. Package Structure (delta)

```text
api/           com.phananh.arenaflow.api
├── area/            AdministrativeUnitController (mới — /api/v1/provinces)
├── profile/         ProfileTeamController (mới — /api/v1/profile/invitations, /profile/teams)
└── team/            TeamController (thêm 2.3)

application/   com.phananh.arenaflow.application
├── service/         AdministrativeUnitService (mới), TeamMembershipService (+2 method), TeamService (+1 method),
│                    MyTeamRoleFilter (enum mới, cạnh TeamStatusFilter)
├── service/impl/    AdministrativeUnitServiceImpl (mới), TeamMembershipServiceImpl, TeamServiceImpl
├── support/         InvitationSearchQuery, InvitationSearchRateLimiter (mới)
├── dto/response/    ProvinceResponse, WardSummaryResponse, InvitationCandidateResponse,
│                    InvitationCandidatesResponse, MyInvitationResponse, MyInvitationTeamResponse, MyTeamResponse (mới)
└── mapper/          AdministrativeUnitMapper (mới), MembershipMapper (+3 method), TeamMapper (+1 method)

domain/        com.phananh.arenaflow.domain
├── repository/      AdministrativeUnitRepository (+2), UserRepository (+4), TeamMemberRepository (+2)
└── projection/      ProvinceView, ProvinceWardView, InvitationCandidateView, MyInvitationView, MyTeamView (mới)

common/        com.phananh.arenaflow.common.exception
├── ErrorCode        USER_004, USER_005, USER_006, USER_007 (mới)
└── RateLimitExceededException (mới, extends BusinessException)

api/           com.phananh.arenaflow.api.config
└── GlobalExceptionHandler   (+ handler RateLimitExceededException → 429 + Retry-After)

api/src/main/resources/db/migration/   V12__user_name_search.sql (mới)
```

Test cần Spring context (migration, repository, integration, đếm truy vấn) đặt ở module `api`, giữ package của lớp được kiểm (`convention-backend.md` §1).

## 3. Controller Layer

| Controller | `@RequestMapping` | Endpoint (`API_Spec.md`) | Service | Thành công |
|---|---|---|---|---|
| `AdministrativeUnitController` (mới) | `/api/v1/provinces` | 2.1 `GET ""` · 2.2 `GET "/{provinceId}/wards"` | `AdministrativeUnitService` | 200 B1, `ApiResponse<List<…>>` |
| `TeamController` | `/api/v1/teams` | 2.3 `GET "/{teamId}/invitation-candidates"` | `TeamMembershipService` | 200 B1, `ApiResponse<InvitationCandidatesResponse>` |
| `ProfileTeamController` (mới, theo mẫu `ProfileMercenaryController` của MERCENARY-001) | `/api/v1/profile` | 2.4 `GET "/invitations"` · 2.5 `GET "/teams"` | `TeamMembershipService`, `TeamService` | 200 B1, `PageResponse<…>` |

- Mọi controller `@Validated`, người gọi lấy bằng `@CurrentUser UUID`; không logic nghiệp vụ. Bảo vệ bằng cấu hình `SecurityConfig` hiện có (mọi path ngoài `/api/v1/auth/**` và endpoint vận hành cần token) — không sửa cấu hình.
- 2.3: `@RequestParam @NotBlank @Size(max = 100) String q` → thiếu (`MissingServletRequestParameterException`), rỗng / quá dài (`ConstraintViolationException`) đều ra `VALIDATION_ERROR` qua `GlobalExceptionHandler` hiện có.
- 2.3: handler gọi `invitationSearchRateLimiter.acquire(callerId)` (§4.5) **trước** khi gọi service — tức là ngoài ranh giới `@Transactional(readOnly = true)` của service. Lý do: transaction mở ở service mượn một kết nối từ pool ngay khi bắt đầu, nên nếu đếm bên trong thì mỗi request bị `429` vẫn chiếm một kết nối trong chốc lát — đúng lúc bị gọi dồn dập. Đặt ở controller thì request bị chặn không chạm DB và không mượn kết nối. Giới hạn số lần gọi là mối quan tâm tầng HTTP (`429` + `Retry-After`), không phải business logic.
- 2.4, 2.5: `page`, `pageSize` như `TeamController.listTeams` (`@RequestParam(defaultValue = "1") @Min(1) int page`, `@RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize`). 2.5 thêm `@RequestParam(required = false) MyTeamRoleFilter role` — giá trị lạ → `MethodArgumentTypeMismatchException` → `VALIDATION_ERROR`.

## 4. Service Layer

### 4.1. AdministrativeUnitService (mới)

```java
List<ProvinceResponse> listProvinces();
List<WardSummaryResponse> listWards(UUID provinceId);   // USER_004 khi provinceId không phải tỉnh/thành
```

`@Transactional(readOnly = true)`. `listWards`: một truy vấn `findWardsOfProvince` (§5.1); kết quả rỗng → `USER_004`; một dòng có `wardId = null` → `[]`; còn lại map từng dòng.

### 4.2. TeamMembershipService (+2 method)

```java
InvitationCandidatesResponse searchInvitationCandidates(UUID teamId, UUID callerId, String q);
PageResponse<MyInvitationResponse> listMyInvitations(UUID userId, int page, int pageSize);
```

**`searchInvitationCandidates`** — `@Transactional(readOnly = true)`. Giới hạn số lần gọi đã chạy ở controller trước khi vào service (§3, §4.5). Thứ tự trong service (khớp bảng lỗi `API_Spec.md` §2.3):
1. `InvitationSearchQuery.parse(q)` (§4.4): dạng số điện thoại → `Phone(normalized)`; không hợp lệ → `USER_005` (không truy vấn). Ngược lại → `Name(raw)`.
2. Nhánh tên: `userRepository.foldName(raw)` → `InvitationSearchQuery.ofFolded(folded)`: tổng số ký tự chữ/số < 2 → `USER_006`; ngược lại dựng `tsQuery` và `prefix`.
3. `teamGuard.activeTeam(teamId)` (`TEAM_001` → `TEAM_016` → `TEAM_015`) → `teamGuard.requireCaptain(teamId, callerId)` (`TEAM_002`, hoặc `TEAM_016` qua `disbandedOr` nếu đội vừa giải tán).
4. `searchCandidatesByPhone(teamId, callerId, phone, LIMIT)` hoặc `searchCandidatesByName(teamId, callerId, tsQuery, prefix, LIMIT)` với `LIMIT = MAX_CANDIDATES + 1` (`MAX_CANDIDATES = 20`, hằng trong service).
5. `hasMore = rows.size() > MAX_CANDIDATES`; `items` = tối đa 20 dòng đầu → `MembershipMapper.toCandidate`.

Không ghi log `q` nguyên văn (có thể là số điện thoại của người khác — `security-backend.md` §5.3); log nếu cần chỉ ghi `teamId`, `callerId`, nhánh tìm và số kết quả.

Giới hạn đặt **đầu tiên** (ở controller, sau Bean Validation, trước khi vào service — tức trước transaction, kiểm `q` và guard đội): lần gọi bị chặn không tốn câu SQL nào và không mượn kết nối DB, và mọi lần gọi 2.3 đã xác thực của một người đều bị đếm — kể cả lần lỗi `USER_005` / `USER_006` / `TEAM_*` — nên không gửi request sai để dò mà không bị tính (BR-U15 "mỗi người tìm tối đa 30 lần mỗi phút"). Phương án loại: đếm sau guard đội — request của người không phải đội trưởng / `q` sai vẫn tốn 1–2 truy vấn mà không bị giới hạn. Lần bị `VALIDATION_ERROR` (không tới service) và lần bị `429` không đếm.

Kiểm `q` trước guard đội: không tốn truy vấn với nhánh số điện thoại sai, và lỗi đầu vào không phụ thuộc trạng thái đội. Không kiểm người gọi đã xoá tài khoản: đội trưởng không xoá được tài khoản (`USER_003`) nên người đã xoá luôn nhận `TEAM_002`.

**`listMyInvitations`** — `@Transactional(readOnly = true)`: `userRepository.existsByIdAndDeletedAtIsNull(userId)` sai → `USER_001`; `pageRequestFactory.create(page, pageSize)` → `teamMemberRepository.findMyPendingInvitations(userId, pageable)` → `PageResponse.from(…, membershipMapper::toMyInvitation, page, pageSize)`.

### 4.3. TeamService (+1 method)

```java
PageResponse<MyTeamResponse> listMyTeams(UUID userId, MyTeamRoleFilter role /* null = mọi vai trò */, int page, int pageSize);
```

`@Transactional(readOnly = true)`: `existsByIdAndDeletedAtIsNull` → `USER_001`; `teamMemberRepository.findMyTeams(userId, role == null ? null : role.name(), pageable)` → `PageResponse.from(…, teamMapper::toMyTeam, …)`. `MyTeamRoleFilter { CAPTAIN }` — tên hằng trùng giá trị cột `team_member.role`.

### 4.4. InvitationSearchQuery (mới, `application/support/`)

Lớp thuần (không Spring bean, không truy vấn) — unit test độc lập.

```java
public sealed interface InvitationSearchQuery {
    record Phone(String phoneNumber) implements InvitationSearchQuery {}   // "0" + 9 chữ số
    record Name(String raw) implements InvitationSearchQuery {}            // q đã trim, chưa chuẩn hoá
    record FoldedName(String tsQuery, String prefix) implements InvitationSearchQuery {}

    /** q đã qua @NotBlank. Chỉ chữ số và + . - khoảng trắng → nhánh số điện thoại. */
    static InvitationSearchQuery parse(String q);

    /** folded = kết quả user_name_fold(q): các từ [a-z0-9]+ cách nhau một dấu cách. */
    static FoldedName ofFolded(String folded);
}
```

- **Phân loại:** `trimmed.matches("[0-9+.\\-\\s]+")` → số điện thoại.
- **Chuẩn hoá số điện thoại:** bỏ `[\s.\-]`; phải khớp `^(\+84|84|0)(\d{9})$` → `"0" + nhóm 2`; không khớp (gồm `+` ở giữa, sai độ dài) → `USER_005`. Kết quả cùng dạng cột `phone_number` (đăng ký chỉ nhận `^0\d{9}$` — `SendOtpRequest`).
- **`ofFolded`:** tách theo dấu cách, **giữ lại token khớp `^[a-z0-9]+$`** (lớp phòng thủ nếu hàm SQL bị sửa sai — mọi ký tự cú pháp tsquery `& | ! : ' ( ) * <` không thể lọt vào), bỏ token rỗng; tổng độ dài token < 2 → `USER_006`. `tsQuery` = token nối `":*"` rồi nối bằng `" & "` (vd `nguyen:* & an:*`); `prefix` = token nối bằng một dấu cách (vd `nguyen an`). `tsQuery` bind làm tham số, không ghép chuỗi SQL.

### 4.5. InvitationSearchRateLimiter (mới, `application/support/`)

Giới hạn 2.3: tối đa `MAX_CALLS = 30` lần trong cửa sổ trượt `WINDOW = 60 giây` mỗi `userId` (lấy từ JWT), tính chung mọi đội và cả hai nhánh tìm (BR-U15, AC-U05-09). **Trong bộ nhớ, không thêm dependency, không truy vấn DB.**

```java
@Component
public class InvitationSearchRateLimiter {
    static final int MAX_CALLS = 30;
    static final Duration WINDOW = Duration.ofSeconds(60);

    private final ConcurrentHashMap<UUID, ArrayDeque<Long>> callsByUser = new ConcurrentHashMap<>();
    private final AtomicLong nextSweepAt;
    private final LongSupplier nanoTime;          // System::nanoTime; test truyền đồng hồ giả qua constructor package-private

    /**
     * Ghi nhận một lần tìm của userId. Đã đủ MAX_CALLS lần trong WINDOW gần nhất → ném
     * RateLimitExceededException(USER_007, số giây tới khi lần cũ nhất ra khỏi cửa sổ, tối thiểu 1) và không ghi nhận.
     */
    public void acquire(UUID userId);
}
```

- **Thuật toán (cửa sổ trượt theo nhật ký):** mỗi user một `ArrayDeque<Long>` mốc `nanoTime` của các lần được chấp nhận. Trong `callsByUser.compute(userId, …)` (nguyên tử với cùng key): bỏ mốc ≤ `now − WINDOW`; còn đủ `MAX_CALLS` mốc → từ chối, `retryAfterSeconds = ceil((oldest + WINDOW − now) / 1 giây)`, tối thiểu 1; ngược lại thêm `now`. Ghi kết quả vào biến cục bộ, ném exception **sau** `compute` (không ném trong hàm remapping). Đúng ngữ nghĩa "trong phút vừa qua" của AC-U05-09 — cửa sổ cố định theo phút bị loại vì cho tới 60 lần quanh ranh giới phút.
- **Bộ nhớ có chặn và được dọn:** mỗi user ≤ 30 mốc `long`; lần bị từ chối không thêm mốc. Dọn lười, không cần `@Scheduled`: đầu mỗi `acquire`, nếu `now ≥ nextSweepAt` và thắng `compareAndSet(nextSweepAt, now + WINDOW)` thì duyệt mọi key bằng `computeIfPresent` — bỏ mốc hết hạn, deque rỗng → trả `null` (xoá key). Map chỉ giữ user đã gọi 2.3 trong khoảng 2 × `WINDOW` gần nhất; chi phí dọn O(số user hoạt động), tối đa một lần mỗi 60 giây.
- **`nanoTime`** (đơn điệu) thay giờ hệ thống: không lệch khi đổi giờ máy; repo chưa có bean `Clock`.
- **Phạm vi:** bộ đếm theo **một instance**, mất khi khởi động lại. Repo đang giả định một instance ứng dụng (ADR-0012 §Bối cảnh; simple broker của ADR-0014); chạy N instance → giới hạn thực tế tới 30 × N lần/phút `[cần xác nhận #7]`.
- **Rủi ro chấp nhận — nhiều tài khoản:** giới hạn tính theo `userId`; tạo tài khoản dễ (xác thực OTP đang mock — USER-001) và ai cũng tự tạo được đội để thành đội trưởng, nên một người dùng K tài khoản dò được 30 × K số/phút. Chấp nhận trong phạm vi user chốt (M3 = B, giới hạn theo người dùng). Hướng sau này: thêm giới hạn theo IP hoặc đặt ở gateway trước ứng dụng `[cần xác nhận #7]`. Loại: Bucket4j / Caffeine (thêm dependency), bảng DB (thêm truy vấn mỗi lần tìm — nhánh tên đã bằng ngưỡng N_LIST = 4).
- **`RateLimitExceededException`** (`common/exception`, `extends BusinessException`): thêm `long retryAfterSeconds`. `GlobalExceptionHandler` thêm `@ExceptionHandler(RateLimitExceededException.class)` (cụ thể hơn handler `BusinessException` nên Spring chọn nó): envelope như `BusinessException` (`429`, `USER_007`, `details = null`) + header `Retry-After: <retryAfterSeconds>` (số nguyên giây, RFC 9110); log `WARN` mã lỗi + path như handler hiện có, không log `userId` hay `q`.
- Test: `Processing_Flow.md` §8 (unit với đồng hồ giả, chạy đồng thời, dọn bộ nhớ; integration AC-U05-09).

## 5. Repository Layer

Mọi truy vấn mới là native + interface projection (mẫu `TeamRepository.findSummaries`), sắp xếp tường minh và ổn định.

### 5.1. AdministrativeUnitRepository (+2)

```sql
-- findProvinces(): List<ProvinceView>
SELECT id AS provinceId, name AS provinceName
FROM administrative_unit
WHERE level = 'province'
ORDER BY name COLLATE "vi-VN-x-icu", id

-- findWardsOfProvince(:provinceId): List<ProvinceWardView>
-- 0 dòng = không phải tỉnh/thành; 1 dòng wardId NULL = tỉnh/thành chưa có phường/xã
SELECT p.id AS provinceId, w.id AS wardId, w.name AS wardName
FROM administrative_unit p
LEFT JOIN administrative_unit w ON w.parent_id = p.id AND w.level = 'ward'
WHERE p.id = :provinceId AND p.level = 'province'
ORDER BY w.name COLLATE "vi-VN-x-icu", w.id
```

`COLLATE "vi-VN-x-icu"` bắt buộc: DB dùng collation mặc định `C.UTF-8`, `ORDER BY name` trơn xếp "Đà Nẵng" sau "TP. Hồ Chí Minh" (đã kiểm DB dev 2026-11-17); collation ICU `vi-VN-x-icu` có sẵn, không cần migration. Index sẵn có: `idx_administrative_unit_level`, `idx_administrative_unit_parent` (V1).

### 5.2. UserRepository (+4)

```java
boolean existsByIdAndDeletedAtIsNull(UUID id);

/** Chuẩn hoá chuỗi tìm theo tên bằng đúng hàm của index; không đọc bảng. */
@Query(value = "SELECT user_name_fold(:q)", nativeQuery = true)
String foldName(@Param("q") String q);

List<InvitationCandidateView> searchCandidatesByPhone(UUID teamId, UUID callerId, String phone, int limit);
List<InvitationCandidateView> searchCandidatesByName(UUID teamId, UUID callerId, String tsQuery, String prefix, int limit);
```

```sql
-- searchCandidatesByName — khung chung; bản theo SĐT thay điều kiện khớp bằng u.phone_number = :phone
-- và ORDER BY u.id (tối đa 1 dòng nhờ idx_user_phone_unique … WHERE deleted_at IS NULL)
SELECT u.id AS userId, u.display_name AS displayName, u.avatar_url AS avatarUrl,
       m.status AS membershipStatus
FROM "user" u
LEFT JOIN team_member m
       ON m.team_id = :teamId AND m.user_id = u.id AND m.status IN ('PENDING', 'ACTIVE')
WHERE user_name_search_vector(u.display_name) @@ to_tsquery('simple', :tsQuery)
  AND u.deleted_at IS NULL
  AND u.role <> 'ADMIN'
  AND u.id <> :callerId
ORDER BY starts_with(user_name_fold(u.display_name), :prefix) DESC,
         u.display_name COLLATE "vi-VN-x-icu", u.id
LIMIT :limit
```

- Vế `@@` khớp đúng biểu thức của index GIN `idx_user_name_search` và điều kiện `deleted_at IS NULL` khớp predicate của partial index (§6). `to_tsquery('simple', :tsQuery)` nhận tham số bind — không ghép chuỗi.
- `LEFT JOIN team_member` cho tối đa 1 dòng mỗi user nhờ unique index `idx_team_member_pending_or_active (team_id, user_id) WHERE status IN ('PENDING','ACTIVE')` (V2) → không nhân dòng.
- Không dùng `LIKE`. GIN chỉ thu hẹp tập dòng **khớp**; `ORDER BY starts_with(user_name_fold(…), :prefix) DESC, display_name COLLATE "vi-VN-x-icu"` không có index hỗ trợ nên PostgreSQL phải đọc **mọi dòng khớp** (heap), tính `user_name_fold` và so ICU cho từng dòng rồi mới cắt `LIMIT 21` (top-N heapsort). Thời gian tăng theo **số người khớp**, không theo `LIMIT`.
- **Số đo** (DB dev, generic plan có dùng GIN):
  - user, bảng tạm 50.002 tên: `'an:*'` → Bitmap Index Scan, 0,05 ms — chỉ đúng khi ít người khớp.
  - architect-reviewer, bảng tạm 200.000 tên dữ liệu lệch (40% họ Nguyễn, 50% có Văn/Thị): `nguyen:*` khớp 80.016 dòng **485 ms**; `van:*` 50.227 dòng 266 ms; `an:*` 20.136 dòng 105 ms; `nguyen:* & an:*` 49 ms.
- **Rủi ro chấp nhận cho MVP** (user chốt M2 = A, 2026-11-17): tìm bằng một từ rất phổ biến chậm dần theo số người dùng. Chặn bằng giới hạn thời gian của câu (dưới) + giới hạn 30 lần/phút (§4.5) + FE debounce. **Mốc xem lại:** khoảng **50.000** người dùng, hoặc log có lỗi hết giờ của 2.3 `[cần xác nhận #8]`; hướng xem lại ở ADR-0016 §Cần theo dõi.
- **Giới hạn thời gian câu tìm theo tên:** `@QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))` trên `searchCandidatesByName` → Hibernate gọi `Statement.setQueryTimeout(2)` (JDBC tính theo giây); hết giờ PgJDBC gửi cancel request, PostgreSQL huỷ câu với SQLState `57014`. **Không** dùng `SET LOCAL statement_timeout`: thêm một câu SQL → nhánh tên thành 5 > N_LIST = 4. Giá trị 2 giây: gấp ~4 lần số đo xấu nhất ở 200.000 tên (485 ms) và vẫn giữ kết nối DB ngắn. Hết giờ → Spring dịch thành `QueryTimeoutException` (`DataAccessException`) → handler `Exception` hiện có của `GlobalExceptionHandler` trả **`500 SYS_005`**, log `ERROR` — theo convention hiện hành, không thêm mã lỗi (`API_Spec.md` §2.3 ghi hành vi cho FE). Kiểm ở integration test `[cần xác nhận #9]` — không đạt thì dùng phương án dự phòng ở #9; #10 đã chốt: giữ `500 SYS_005`.
- Kế hoạch với **tham số bind** + partial predicate + `LIMIT` trên bảng thật kiểm lại ở integration test bằng `EXPLAIN` `[cần xác nhận #2]`.

### 5.3. TeamMemberRepository (+2)

```sql
-- findMyPendingInvitations(:userId, Pageable): Page<MyInvitationView>
SELECT m.id AS invitationId, m.invited_at AS invitedAt,
       t.id AS teamId, t.name AS teamName, t.avatar_url AS avatarUrl, t.skill_levels AS skillLevels,
       t.status AS status, cu.display_name AS captainName, (cu.deleted_at IS NOT NULL) AS captainDeleted,
       (SELECT COUNT(*) FROM team_member am WHERE am.team_id = t.id AND am.status = 'ACTIVE') AS memberCount
FROM team_member m
JOIN team t ON t.id = m.team_id
LEFT JOIN team_member cm ON cm.team_id = t.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
LEFT JOIN "user" cu ON cu.id = cm.user_id
WHERE m.user_id = :userId AND m.status = 'PENDING' AND t.status <> 'DISBANDED'
ORDER BY m.invited_at DESC, m.id DESC
-- countQuery: SELECT COUNT(*) FROM team_member m JOIN team t ON t.id = m.team_id
--             WHERE m.user_id = :userId AND m.status = 'PENDING' AND t.status <> 'DISBANDED'

-- findMyTeams(:userId, :role /* NULL = mọi vai trò */, Pageable): Page<MyTeamView>
SELECT t.id AS teamId, t.name AS teamName, t.avatar_url AS avatarUrl, t.skill_levels AS skillLevels,
       t.status AS status, cu.display_name AS captainName, (cu.deleted_at IS NOT NULL) AS captainDeleted,
       (SELECT COUNT(*) FROM team_member am WHERE am.team_id = t.id AND am.status = 'ACTIVE') AS memberCount,
       m.role AS role, m.joined_at AS joinedAt
FROM team_member m
JOIN team t ON t.id = m.team_id
LEFT JOIN team_member cm ON cm.team_id = t.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
LEFT JOIN "user" cu ON cu.id = cm.user_id
WHERE m.user_id = :userId AND m.status = 'ACTIVE'
  AND (CAST(:role AS VARCHAR) IS NULL OR m.role = CAST(:role AS VARCHAR))
ORDER BY CASE WHEN m.role = 'CAPTAIN' THEN 0 ELSE 1 END, m.joined_at DESC, t.id DESC
-- countQuery: SELECT COUNT(*) FROM team_member m WHERE m.user_id = :userId AND m.status = 'ACTIVE'
--             AND (CAST(:role AS VARCHAR) IS NULL OR m.role = CAST(:role AS VARCHAR))
```

- Lời mời: `t.status <> 'DISBANDED'` là lớp an toàn — giải tán đã chuyển mọi `PENDING` sang `REJECTED` trong cùng transaction (`disbandRemaining`).
- Đội của tôi **không** lọc `t.status`: tư cách `ACTIVE` ⇒ đội chưa giải tán (giải tán chuyển mọi `ACTIVE` sang `LEFT`). Giữ đúng tập điều kiện của bước chặn xoá tài khoản (`findByUserIdAndStatusInOrderByIdAsc` + kiểm `ACTIVE`/`CAPTAIN` trong `ProfileServiceImpl.deleteAccount`) để lọc `role=CAPTAIN` bằng đúng tập đội chặn xoá tài khoản.
- Không dùng lại `findByUserIdAndStatusInOrderByIdAsc` cho đọc: method này giữ `PESSIMISTIC_WRITE`.
- Phần chọn đội trưởng / đếm thành viên giống `TeamRepository.findSummaries`; giữ hai câu riêng vì nguồn dòng (`team_member` của người gọi) và thứ tự khác.
- Index sẵn có: `idx_team_member_user (user_id, status)` (V1) cho điều kiện người gọi; thứ tự sắp xếp tính trên tập lời mời/đội của một người (nhỏ). Không thêm index.

### 5.4. Projection (mới, `domain/projection/`)

| Projection | Getter |
|---|---|
| `ProvinceView` | `UUID getProvinceId()`, `String getProvinceName()` |
| `ProvinceWardView` | `UUID getProvinceId()`, `UUID getWardId()`, `String getWardName()` |
| `InvitationCandidateView` | `UUID getUserId()`, `String getDisplayName()`, `String getAvatarUrl()`, `String getMembershipStatus()` |
| `MyInvitationView` | `UUID getInvitationId()`, `LocalDateTime getInvitedAt()`, `UUID getTeamId()`, `String getTeamName()`, `String getAvatarUrl()`, `String getSkillLevels()`, `String getStatus()`, `String getCaptainName()`, `Boolean getCaptainDeleted()`, `Long getMemberCount()` |
| `MyTeamView` | như `MyInvitationView` bỏ `invitationId`/`invitedAt`, thêm `String getRole()`, `LocalDateTime getJoinedAt()` |

## 6. Schema — migration `V12__user_name_search.sql`

Không bảng / cột mới, không cài extension. Hai hàm SQL `IMMUTABLE` + một index GIN biểu thức trên `"user".display_name`.

```sql
-- Chuẩn hoá tên để tìm: NFC → chữ thường → bỏ dấu tiếng Việt → ký tự không phải [a-z0-9] thành một dấu cách.
-- Nguồn duy nhất của bảng ký tự: index, câu tìm và BE (SELECT user_name_fold(:q)) cùng gọi hàm này.
CREATE FUNCTION user_name_fold(name text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$
SELECT btrim(regexp_replace(
           translate(lower(normalize(name, NFC)),
                     'àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ',
                     'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd'),
           '[^a-z0-9]+', ' ', 'g'))
$$;

CREATE FUNCTION user_name_search_vector(name text) RETURNS tsvector
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$
SELECT to_tsvector('simple'::regconfig, user_name_fold(name))
$$;

CREATE INDEX idx_user_name_search ON "user"
    USING GIN (user_name_search_vector(display_name))
    WHERE deleted_at IS NULL;
```

- Đã kiểm trên DB dev (Neon `misty-dew-90700890`, PostgreSQL 18, 2026-11-17, chỉ đọc): `normalize`, `lower`, `translate`, `regexp_replace`, `btrim`, `starts_with`, `to_tsvector(regconfig, text)`, `to_tsquery(regconfig, text)` đều `IMMUTABLE`; biểu thức cho `"ĐẶNG  Thị-Ổi!!"` → `dang thi oi`, tên lưu dạng NFD → như NFC; ví dụ `API_Spec.md` §2.3 (`an`, `nguyen`, `Nguyen An`, `ng.an`) cho đúng tập kết quả và thứ tự. Bảng ánh xạ 67 ký tự thường (`lower` chạy trước nên phủ chữ hoa).
- Hàm không đặt `SET search_path` (giữ khả năng inline của hàm SQL); chỉ gọi hàm `pg_catalog`. Tên hàm không schema-qualify, như mọi đối tượng hiện có ở schema mặc định.
- Ký tự ngoài bảng (chữ có dấu không phải tiếng Việt, vd `ë`) thành dấu cách — tên "Zoë" tìm bằng "zo" `[cần xác nhận #3]` (không chặn).
- **Sửa bảng ký tự sau này:** migration mới `CREATE OR REPLACE FUNCTION user_name_fold …` **và** `REINDEX INDEX idx_user_name_search` trong cùng migration — index biểu thức không tự tính lại.
- Chi phí ghi: cập nhật `display_name` (đăng ký, sửa hồ sơ) cập nhật thêm index GIN — không đổi số câu SQL của các API đó.
- **Số hiệu V12 và thứ tự áp:** code đang có V1–V5 (V5 = VENUE-001); thiết kế đã giữ V6 (SYSTEM-002), V7 (MATCH-001), V8 (MATCH-002), V9 (REPUTATION-001), V10 (MERCENARY-001), V11 (NOTIFICATION-001). Flyway không chạy out-of-order → V12 chỉ áp sau V5–V11; **code USER-003 đợi code V5–V11** (user chốt 2026-11-17). Phương án tách đợt: `[cần xác nhận #1]`.
- Test migration: hàm tồn tại, `IMMUTABLE`; index được tạo; `user_name_fold` cho các ví dụ trên; `EXPLAIN` câu §5.2 (tham số bind) dùng `idx_user_name_search`.

## 7. DTO & Mapper

### 7.1. Response DTO (record, `application/dto/response/`)

| DTO | Field (thứ tự = JSON `API_Spec.md` §4.2) |
|---|---|
| `ProvinceResponse` | `UUID provinceId`, `String provinceName` |
| `WardSummaryResponse` | `UUID wardId`, `String wardName` |
| `InvitationCandidateResponse` | `UUID userId`, `String displayName`, `String avatarUrl`, `String membershipStatus` |
| `InvitationCandidatesResponse` | `List<InvitationCandidateResponse> items`, `boolean hasMore` |
| `MyInvitationTeamResponse` | `UUID teamId`, `String teamName`, `String avatarUrl`, `List<String> skillLevels`, `String status`, `String captainName`, `long memberCount` |
| `MyInvitationResponse` | `UUID invitationId`, `LocalDateTime invitedAt`, `MyInvitationTeamResponse team` |
| `MyTeamResponse` | `UUID teamId`, `String teamName`, `String avatarUrl`, `List<String> skillLevels`, `String status`, `String captainName`, `long memberCount`, `String role`, `LocalDateTime joinedAt` |

Không request DTO mới (chỉ query param). `WardResponse` hiện có (4 field, cho hồ sơ) giữ nguyên. Không dùng tên `TeamBriefResponse`: REPUTATION-001 đã giữ tên này cùng package với shape khác (REPUTATION-001 `Component_Class_Design.md` §2).

### 7.2. Mapper (MapStruct, `unmappedTargetPolicy = ERROR`, `uses = MapperSupport.class`)

- `AdministrativeUnitMapper` (mới): `ProvinceView → ProvinceResponse`, `ProvinceWardView → WardSummaryResponse`.
- `MembershipMapper`: `toCandidate(InvitationCandidateView)` (không che — người đã xoá không có trong kết quả); `toMyInvitation(MyInvitationView)` → `team` dựng qua `toMyInvitationTeam`; `captainName` qua `MapperSupport.maskName(…, captainDeleted)`; `skillLevels` qua `MapperSupport.skillLevels`; `invitedAt` qua `MapperSupport.toUtc`.
- `TeamMapper`: `toMyTeam(MyTeamView)` — che `captainName`, `skillLevels`, `joinedAt` gán thẳng (`LocalDateTime`, SYSTEM-002).

### 7.3. ErrorCode (mới)

```java
USER_004("USER_004", HttpStatus.NOT_FOUND, "Không tìm thấy tỉnh/thành"),
USER_005("USER_005", HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ"),
USER_006("USER_006", HttpStatus.BAD_REQUEST, "Nhập ít nhất 2 ký tự"),
USER_007("USER_007", HttpStatus.TOO_MANY_REQUESTS, "Bạn tìm quá nhanh, vui lòng thử lại sau"),
```

`USER_007` là mã `429` đầu tiên của repo: `api-convention.md` §5 chưa liệt kê `429`, chưa có quy ước header `Retry-After` — bổ sung ở nhánh chore (§10).

## 8. Dependencies

Không thêm dependency (giới hạn 2.3 viết bằng `ConcurrentHashMap` của JDK — §4.5), không cài extension PostgreSQL (`unaccent`, `pg_trgm` có sẵn trên Neon nhưng không dùng; `pg_bigm` không có — user kiểm 2026-11-17).

## 9. Danh sách [cần xác nhận]

1. ~~`[cần xác nhận #1]`~~ **Đã chốt 2026-11-17 (user): không tách đợt** — cả USER-003 code một lần sau khi V5–V11 đã vào code (V12 đi kèm).
2. `[cần xác nhận #2]` Kế hoạch truy vấn §5.2 với tham số bind (generic plan), partial predicate `deleted_at IS NULL` và `LIMIT` trên bảng `"user"` thật — đo bằng `EXPLAIN` trong integration test; chưa dùng index → dừng, báo trước khi merge.
3. `[cần xác nhận #3]` Ký tự có dấu ngoài bảng tiếng Việt (vd `ë`, `ç`) thành dấu cách (tên "Zoë" → `zo`). Chấp nhận cho MVP; mở rộng bảng = migration mới + `REINDEX`.
4. ~~`[cần xác nhận #4]`~~ **Đã chốt 2026-11-17 (user):** mọi ký tự không phải `[a-z0-9]` là dấu cách ở **cả** tên lẫn chuỗi nhập (`ng.an` → `ng`, `an`; "Thị-Ổi" → `thi`, `oi`) — hai vế chuẩn hoá bằng cùng một hàm nên luôn đối xứng. SRS BR-U10 (a) đã ghi.
5. ~~`[cần xác nhận #5]` Giới hạn số lần gọi (chống dò số điện thoại): code chưa có rate limiter, USER-003 không thêm.~~ — **Đã chốt 2026-11-17 (user, M3 = B):** USER-003 tự giới hạn 2.3 tối đa 30 lần/phút mỗi người (SRS BR-U15, Q-12, AC-U05-09) — §4.5, `429 USER_007`.
6. `[cần xác nhận #6]` Số phường/xã tối đa của một tỉnh/thành khi nạp đủ toàn quốc (BC-U05) [Unverified — khoảng vài trăm] — chặn trên của ngoại lệ không phân trang 2.2; kiểm lại khi nạp dữ liệu.
7. `[cần xác nhận #7]` Bộ đếm giới hạn 2.3 nằm trong bộ nhớ từng instance (§4.5): đúng khi chạy **một** instance (giả định hiện hành). Trước khi chạy nhiều instance → chọn bộ đếm dùng chung (Redis, bảng DB, gateway). Giới hạn theo `userId` không chặn được người dùng nhiều tài khoản (§4.5 rủi ro chấp nhận) — hướng sau: giới hạn theo IP / gateway. Không chặn MVP.
8. `[cần xác nhận #8]` **Mốc xem lại tìm theo tên** (rủi ro theo dõi, M2): khoảng 50.000 người dùng, hoặc có lỗi hết giờ của 2.3 trong log → đo lại với dữ liệu thật, chọn hướng ở ADR-0016 §Cần theo dõi.
9. `[cần xác nhận #9]` `@QueryHints` timeout áp được lên native query và cancel request của PgJDBC đi qua được kết nối DB dev (Neon, có thể qua pooler) — kiểm bằng integration test (vd câu thử `pg_sleep(3)` với cùng hint trong repository chỉ dùng cho test → `QueryTimeoutException` sau khoảng 2 giây). Không đạt → dừng, báo trước khi merge, chọn phương án dự phòng **không tốn câu SQL nào mỗi request**: đặt `statement_timeout` ở **cấp role** — `ALTER ROLE CURRENT_USER SET statement_timeout = '5s'` (migration hoặc thao tác vận hành; `CURRENT_USER` để không ghi cứng tên role khác nhau giữa môi trường — [Unverified] cú pháp chưa chạy thử trên Neon). Setting cấp role áp vào mọi kết nối server **mới** của role, nên đúng cả khi đi qua pooler Neon (PgBouncer, `pool_mode=transaction`). Đánh đổi (phải xử lý trước khi áp):
   - **Áp cho mọi truy vấn của role**, không riêng câu tìm tên → giá trị rộng hơn (vd 5 giây); câu tìm tên mất mốc 2 giây riêng.
   - **Flyway dùng chung datasource / role:** migration nặng (vd tạo GIN index của V12 trên bảng `"user"` lớn) có thể bị huỷ (`57014`) → migration fail, ứng dụng không khởi động. Điều kiện: migration chạy bằng role / kết nối khác, hoặc migration nặng tự `SET LOCAL statement_timeout = 0` ở đầu (chỉ có hiệu lực khi migration chạy trong transaction — mặc định của Flyway). Migration chạy **ngoài** transaction (vd `CREATE INDEX CONCURRENTLY`) → `SET LOCAL` vô tác dụng và `SET` thường qua pooler sẽ lây sang kết nối khác → chạy bằng role / kết nối trực tiếp khác (không qua host `-pooler`).
   - **Kết nối server đang mở giữ giá trị cũ** tới khi đóng → sau khi áp phải khởi động lại ứng dụng / chờ pool thay kết nối (ghi bước vận hành).
   - **Job định kỳ cùng role** (vd xoá chat 90 ngày, xoá thông báo 30 ngày theo lô) cũng bị giới hạn → đối chiếu thời gian mỗi lô với ngưỡng trước khi áp.
   - **Query hint 2 giây khi đã chuyển sang dự phòng:** dự phòng chỉ dùng khi lệnh huỷ của driver không qua được pooler, lúc đó hint không còn tác dụng thật → bỏ hint trên `searchCandidatesByName` (giữ lại chỉ khi kiểm lại thấy lệnh huỷ vẫn qua được); đổi test `pg_sleep` ở `Processing_Flow.md` §8 sang kiểm `statement_timeout` của role.

   Phương án loại: `SET LOCAL statement_timeout` trong transaction (thêm 1 câu SQL → nhánh tên 5 > N_LIST = 4); Hikari `connectionInitSql = SET statement_timeout …` — qua pooler chế độ transaction, `SET` theo phiên chỉ rơi vào một kết nối server ngẫu nhiên và không được reset khi trả về pool, nên timeout lúc có lúc không và "lây" sang request khác (chỉ đúng khi nối thẳng, không qua host `-pooler`). **Chọn phương án dự phòng → trước khi merge:** cập nhật `Processing_Flow.md` (các chỗ ghi "2 giây", bảng lỗi, test timeout) và ghi quyết định bằng ADR (hoặc mục Sửa đổi ADR-0016), vì timeout cấp cấu hình ảnh hưởng mọi truy vấn của ứng dụng.
10. ~~`[cần xác nhận #10]`~~ **Đã chốt 2026-11-17 (user): giữ `500 SYS_005`** — không đổi convention. Hết giờ câu tìm trả `500 SYS_005` theo handler hiện có. Ánh xạ `QueryTimeoutException` → `503` (giữ `SYS_005`) cho mọi endpoint là đổi convention (`api-convention.md` §6 `SYS_005`) ở nhánh chore; không chặn USER-003.
11. `[cần xác nhận #11]` Header `Retry-After` chỉ đọc được từ JS trình duyệt khác origin khi có CORS `Access-Control-Expose-Headers: Retry-After`; BE chưa cấu hình CORS. FE không đọc được → chờ 60 giây (`API_Spec.md` §2.3). Khi thêm CORS → expose header này.

## 10. Tài liệu cần đồng bộ

| Tài liệu | Nội dung | Khi nào / nhánh |
|---|---|---|
| `docs/agent/api-convention.md` §5 (bảng 2xx) | 5 dòng `GET /provinces`, `GET /provinces/{provinceId}/wards`, `GET /teams/{teamId}/invitation-candidates`, `GET /profile/invitations`, `GET /profile/teams` — 200 B1 | `chore/agent-framework-*` |
| `docs/agent/api-convention.md` §6 | Ví dụ `USER_004`..`USER_007` | `chore/agent-framework-*` |
| `docs/agent/api-convention.md` §5 (Client Error 4xx) | Thêm `429 Too Many Requests` — vượt giới hạn số lần gọi; response kèm header `Retry-After` (số nguyên giây); mã lỗi theo miền (`USER_007` là mã đầu tiên) | `chore/agent-framework-*` |
| `docs/agent/security-backend.md` §7 | Ghi mẫu đang dùng: giới hạn theo `userId` trong bộ nhớ, cửa sổ trượt, không dependency (USER-003 2.3); ví dụ resilience4j hiện có chưa áp dụng ở repo | `chore/agent-framework-*` |
| VENUE-001 `API_Spec.md` (dòng `wardId`, khoảng :69) | Đang trỏ danh mục khu vực sang USER-001 → trỏ USER-003 `API_Spec.md` §2.1–§2.2 | ✅ Đã làm ở VENUE-001 v6.0 (2026-11-22) |
| NOTIFICATION-001 `API_Spec.md` (thông báo `TEAM_INVITATION`, khoảng :281) | Ghi thêm: lời mời cũng xem được ở USER-003 `API_Spec.md` §2.4 | Lần sửa NOTIFICATION-001 kế tiếp (không sửa trong USER-003) |
| `docs/agent/api-convention.md` §7 (bảng "Áp dụng") | Dòng ngoại lệ "không phân trang": 2.1, 2.2 (danh mục, trả toàn bộ), 2.3 (tối đa 20, `{items, hasMore}` — dùng hình dạng §7.2 nhưng không có con trỏ) | `chore/agent-framework-*` |
| `docs/agent/non-functional-standards.md` §2 | Ngoại lệ "danh mục chỉ đọc có chặn trên tự nhiên / gợi ý tìm kiếm có `LIMIT` cố định" — user chốt D1 2026-11-17 | `chore/agent-framework-*` |
| `docs/agent/CLAUDE.md` §2 | Dòng điều hướng USER-003 | `chore/agent-framework-*` |
| `docs/database/data-model.md`, `schema.dbml`, `schema-design.md` | Hàm `user_name_fold`, `user_name_search_vector`, index `idx_user_name_search` (V12) | Sau khi code + test PASS, nhánh `feature/USER-003-*` |
