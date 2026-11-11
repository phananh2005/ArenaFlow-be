# Component & Class Design — User & Team Management

| Mục | Giá trị |
|---|---|
| Mã chức năng | `USER-001` |
| Phạm vi tầng | Backend (Spring Boot REST API) |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · Spring Data JPA · Flyway |
| Quy ước áp dụng | `docs/agent/convention-backend.md`, `docs/agent/security-backend.md` (lệch có chủ đích: §10) |
| Ràng buộc dữ liệu | `V1__initial_schema.sql`, entity hiện có `com.phananh.arenaflow.domain.entity.*`, ADR-0001/0002/0003/0004 |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-02 · v1.0 (baseline) · hiện tại **v3.8** (solution-architect · 2026-11-11) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan kiến trúc

Layered, tách thành 4 module Maven (ADR-0006): `api` (Controller) → `application` (Service interface + impl) → `domain` (Repository → Entity) → `common`. Maven chỉ chặn phụ thuộc ngược; controller → service → repository (không nhảy tầng) vẫn giữ bằng quy ước `convention-backend.md` §1. Entity **đã có** (24 class, FK lưu dạng cột UUID, không dùng `@ManyToOne`) — USER-001 chỉ sửa 5 entity, xoá 1 (§6). Hợp đồng API: `API_Spec.md`. Luồng/transaction/số truy vấn: `Processing_Flow.md`.

## 2. Package Structure

Base package: `com.phananh.arenaflow`. Maven multi-module, chiều phụ thuộc `api → application → domain → common` (ADR-0006); Maven chặn import ngược ở compile time, không chặn nhảy tầng (api → domain). Entity giữ nguyên bố cục `entity/<domain>/`.

```
common/        com.phananh.arenaflow.common
├── enums/          SkillLevel
├── exception/      ErrorCode, BusinessException, ErrorEnvelope, ErrorResponse, ValidationError
└── DeletedUserMasking

domain/        com.phananh.arenaflow.domain
├── entity/         (đã có) user/, team/ …  + converter/SkillLevelsConverter
├── repository/     UserRepository, OAuthAccountRepository, AdministrativeUnitRepository,
│                   TeamRepository, TeamMemberRepository, TeamCaptainHistoryRepository
└── projection/     ProfileView, UserAuthView, WardView, TeamDetailView, TeamSummaryView,
                    MemberView, CaptainHistoryView (USER-002: bỏ CaptainTeamView, thêm MemberHistoryView)

application/   com.phananh.arenaflow.application
├── service/        AuthService, AuthResult, ProfileService, TeamService, TeamStatusFilter,
│   │               TeamMembershipService, AdminTeamService
│   └── impl/       *ServiceImpl
├── support/        TeamGuard, ConcurrencyConflictRetrier, PageRequestFactory
│   └── security/   JwtTokenService, JwtProperties
├── validation/     OneOf, MinAge, ValidSkillLevels (+ *Validator)
├── dto/request/    OAuthLoginRequest, SendOtpRequest, VerifyOtpRequest, RefreshTokenRequest,
│                   ProfileUpdateRequest, CreateTeamRequest, UpdateTeamRequest, InviteMemberRequest,
│                   TransferCaptainRequest, MarkInactiveRequest
├── dto/response/   ApiResponse<T>, PageResponse<T>, PaginationMeta, AuthResponse, OtpSentResponse,
│                   ProfileResponse, WardResponse, ProviderResponse, TeamResponse, TeamSummaryResponse,
│                   AdminTeamSummaryResponse, InvitationResponse, MemberResponse, CaptainTransferResponse,
│                   CaptainHistoryResponse
└── mapper/         ProfileMapper, TeamMapper, MembershipMapper (MapStruct), MapperSupport

api/           com.phananh.arenaflow  (ArenaFlowApplication; resources: application.properties, db/migration)
└── api/
    ├── auth/       AuthController
    ├── profile/    ProfileController
    ├── team/       TeamController, InvitationController, AdminTeamController
    ├── config/     SecurityConfig (+ bean JwtDecoder, JwtAuthenticationConverter),
    │               WebMvcConfig (đăng ký CurrentUserArgumentResolver), GlobalExceptionHandler
    └── security/   AccessTokenTypeValidator, CurrentUser (annotation), CurrentUserArgumentResolver,
                    RestAuthenticationEntryPoint, RestAccessDeniedHandler,
                    SecurityErrorWriter (package-private)
```

Thành phần trong `common/`, `application/dto/response/ApiResponse|PageResponse`, `application/mapper/MapperSupport`, `application/support/` là **dùng chung** → developer MUST chạy `reuse-scan` trước khi tạo.

Không có `JpaConfig`: Spring Boot auto-config (JPA, Flyway, transaction) đủ dùng. Chỉ thêm khi cần cấu hình JPA tường minh (vd auditing, nhiều datasource).

## 3. Controller Layer

Mọi controller: `@RestController`, `@RequiredArgsConstructor`, `@Validated`; nhận `@Valid @RequestBody`; lấy user hiện tại qua tham số `@CurrentUser UUID currentUserId` (§9.4). Không chứa business logic.

**Tham số danh sách** (chặn ở controller, sai → 400 `VALIDATION_ERROR`; `PageRequestFactory` ném `IllegalArgumentException` nếu nhận giá trị sai nên không để giá trị sai lọt xuống):

```java
@RequestParam(defaultValue = "1")  @Min(1)          int page,
@RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
@RequestParam(defaultValue = "ACTIVE") TeamStatusFilter status   // 2.10; 2.21 mặc định "ALL"
```

`status` bind thẳng enum `TeamStatusFilter`; giá trị lạ → `MethodArgumentTypeMismatchException` → 400 (§10).

| Controller | `@RequestMapping` | Endpoint (`API_Spec.md` §2) | Service |
|---|---|---|---|
| `AuthController` | `/api/v1/auth` | 2.1–2.4 | `AuthService` |
| `ProfileController` | `/api/v1/profile` | 2.5–2.7 | `ProfileService` |
| `TeamController` | `/api/v1/teams` | 2.8–2.12, 2.15–2.18 | `TeamService`, `TeamMembershipService` |
| `InvitationController` | `/api/v1/invitations` | 2.13–2.14 | `TeamMembershipService` |
| `AdminTeamController` | `/api/v1/admin/teams` | 2.19–2.21 | `AdminTeamService` |

Mọi handler trả `ResponseEntity<…>` (`convention-backend.md`): 200 → `ResponseEntity.ok(...)`, 201 → `ResponseEntity.status(HttpStatus.CREATED).body(...)` (không header `Location`, `api-convention.md` §5), 204 → `noContent().build()`. `AuthController` chọn 201/200 theo `AuthResult.isNewUser()`.

## 4. Service Layer

Phân quyền captain/người được mời **làm trong service** (cần thứ tự guard 404 → 403, xem `Processing_Flow.md` §3), không dùng `@PreAuthorize` theo team. Admin chặn ở `SecurityConfig` (`/api/v1/admin/**` → `hasRole('ADMIN')`).

**Guard đội INACTIVE:** Các method `updateTeam`, `inviteMember`, `transferCaptain`, `acceptInvitation`, `rejectInvitation` MUST check `team.status = INACTIVE → TEAM_015` trước khi ghi. Từ USER-002: `DISBANDED → TEAM_016` kiểm **trước** `TEAM_015` (USER-002 `API_Spec.md` §2.1a).

**`application/support/TeamGuard`** (`@Component`, deps `TeamRepository`, `TeamMemberRepository`) — một nguồn cho guard đội, dùng chung bởi `TeamServiceImpl`, `TeamMembershipServiceImpl` và `AdminTeamServiceImpl`:

```java
Team findTeam(UUID teamId);                          // private từ USER-002; không có → TEAM_001, không kiểm trạng thái
Team activeTeam(UUID teamId);                        // findTeam + DISBANDED → TEAM_016 + INACTIVE → TEAM_015 (USER-002)
void requireTeam(UUID teamId);                       // không có → TEAM_001 (chỉ thao tác đọc; rời đội dùng notDisbandedTeam từ USER-002)
TeamMember requireCaptain(UUID teamId, UUID userId); // captain ACTIVE ≠ userId → TEAM_002 (đội vừa giải tán → TEAM_016, USER-002 disbandedOr)
```

`AdminTeamServiceImpl` dùng `TeamGuard.notDisbandedTeam` (USER-002: admin thao tác cả đội INACTIVE nhưng đội DISBANDED → `TEAM_016`; quy tắc riêng TEAM_011/012/013 giữ nguyên). Guard mới của USER-002: USER-002 `Component_Class_Design.md` §4.1.

### 4.1. AuthService
```java
AuthResult loginWithOAuth(OAuthLoginRequest request);      // register-or-login
OtpSentResponse sendOtp(SendOtpRequest request);           // mock, không chạm DB
AuthResult verifyOtp(VerifyOtpRequest request);            // register-or-login, provider=phone
AuthResponse refresh(RefreshTokenRequest request);
```
`AuthResult` = record `(AuthResponse body, boolean isNewUser)`. Deps: `UserRepository`, `OAuthAccountRepository`, `JwtTokenService`, `JwtProperties` (`expiresIn` trong response), `TransactionTemplate` (tạo tài khoản, §4.6).

### 4.2. ProfileService
```java
ProfileResponse getProfile(UUID userId);
ProfileResponse updateProfile(UUID userId, ProfileUpdateRequest request);
void deleteAccount(UUID userId);
```
Deps: `UserRepository`, `OAuthAccountRepository`, `AdministrativeUnitRepository`, `TeamRepository`, `TeamMemberRepository`, `ConcurrencyConflictRetrier`, `ProfileMapper`.

### 4.3. TeamService
```java
TeamResponse createTeam(UUID userId, CreateTeamRequest request);
TeamResponse getTeam(UUID teamId);
PageResponse<TeamSummaryResponse> listTeams(TeamStatusFilter status, int page, int pageSize);
TeamResponse updateTeam(UUID teamId, UUID userId, UpdateTeamRequest request);
CaptainTransferResponse transferCaptain(UUID teamId, UUID userId, TransferCaptainRequest request);
PageResponse<CaptainHistoryResponse> listCaptainHistory(UUID teamId, int page, int pageSize);
```
`TeamStatusFilter` = enum `ACTIVE | INACTIVE | ALL`; `toQueryValue()` trả `null` cho `ALL`, còn lại `name()` (tham số `status` của `TeamRepository.findSummaries`).

Deps: `UserRepository`, `TeamRepository`, `TeamMemberRepository`, `TeamCaptainHistoryRepository`, `ConcurrencyConflictRetrier`, `TeamMapper`, `PageRequestFactory`, `TeamGuard`.

**Phân trang dùng chung** (cho `TeamService`, `TeamMembershipService`, `AdminTeamService`):
- `PageRequestFactory` là `@Component`, inject vào service; dựng `Pageable` từ `page` (bắt đầu 1) + `pageSize`.
- `PageResponse.from(Page<S> source, Function<S, T> mapper, int page, int pageSize)` — factory tĩnh trên record `PageResponse<T>`, dựng `data` + `PaginationMeta`.

### 4.4. TeamMembershipService
```java
InvitationResponse inviteMember(UUID teamId, UUID captainUserId, InviteMemberRequest request);
MemberResponse acceptInvitation(UUID invitationId, UUID userId);
void rejectInvitation(UUID invitationId, UUID userId);
PageResponse<MemberResponse> listMembers(UUID teamId, int page, int pageSize);
void leaveTeam(UUID teamId, UUID userId);
```
Deps: `UserRepository`, `TeamMemberRepository`, `ConcurrencyConflictRetrier`, `MembershipMapper`, `PageRequestFactory`, `TeamGuard` (không inject `TeamRepository` trực tiếp).

### 4.5. AdminTeamService
```java
TeamResponse markInactive(UUID teamId, MarkInactiveRequest request);
TeamResponse reactivate(UUID teamId);
PageResponse<AdminTeamSummaryResponse> listTeams(TeamStatusFilter status, int page, int pageSize);
```
Deps: `TeamRepository`, `TeamMemberRepository`, `ConcurrencyConflictRetrier`, `TeamMapper`, `PageRequestFactory`, `TeamGuard`, `TeamService` (response chi tiết đội dùng lại `TeamService#getTeam`, không dựng song song).

### 4.6. Transaction trong ServiceImpl (lệch convention §9 — xem §10)
- **Không** đặt `@Transactional` ở class level.
- Method đọc: `@Transactional(readOnly = true)`. Gọi lồng từ method ghi (vd `AdminTeamServiceImpl` → `TeamService#getTeam`) → tham gia transaction của retrier (REQUIRED), `readOnly` không áp.
- Method ghi chạm `team`/`team_member` (`updateProfile` không thuộc nhóm này): **không** annotate; thân method gọi `concurrencyConflictRetrier.execute(() -> …)` — mỗi lần thử là 1 transaction mới.
- `loginWithOAuth`, `verifyOtp`: **không** annotate; tạo tài khoản trong `transactionTemplate.execute(...)`, bắt `DataIntegrityViolationException` ngoài transaction đã rollback rồi đọc lại — ranh giới, nguồn đụng độ và các nhánh kết quả: `Processing_Flow.md` §4.
- Method ghi còn lại (`updateProfile`, `createTeam`): `@Transactional`. `inviteMember` chạy qua retrier từ USER-002 (race M1, `OPTIMISTIC_FORCE_INCREMENT`).

## 5. Repository Layer

Truy vấn danh sách/chi tiết có join dùng `@Query(nativeQuery = true)` trả **interface projection** (`domain/projection/`) — không nạp entity rồi lazy-load. SQL cụ thể: `Processing_Flow.md` §3.

```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByIdAndDeletedAtIsNull(UUID id);
    Optional<ProfileView> findProfileViewById(UUID id);                 // @Query native, join ward + province
    @Modifying int softDelete(UUID id, OffsetDateTime now);             // WHERE deleted_at IS NULL
}
public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, UUID> {
    default Optional<UserAuthView> findAuthView(OAuthAccount.Provider provider, String providerUserId) {
        return findAuthViewByProviderName(provider.name(), providerUserId);   // DB lưu lowercase
    }
    Optional<UserAuthView> findAuthViewByProviderName(String provider, String providerUserId); // native, join user
    List<OAuthAccount> findByUserId(UUID userId);
}
public interface AdministrativeUnitRepository extends JpaRepository<AdministrativeUnit, UUID> {
    Optional<WardView> findWardWithProvince(UUID wardId);              // level = 'ward', join parent
}
public interface TeamRepository extends JpaRepository<Team, UUID> {
    Optional<TeamDetailView> findDetailById(UUID teamId);
    Page<TeamSummaryView> findSummaries(String status /* null = ALL */, Pageable pageable);
    // USER-002: bỏ markInactiveByIds; thêm findForInviteById, findCurrentStatusById (USER-002 CCD §5.2)
}
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    Optional<TeamMember> findByTeamIdAndUserIdAndStatus(UUID teamId, UUID userId, TeamMember.Status status);
    Optional<TeamMember> findByTeamIdAndRoleAndStatus(UUID teamId, TeamMember.Role role, TeamMember.Status status);
    boolean existsByTeamIdAndUserIdAndStatusIn(UUID teamId, UUID userId, Collection<TeamMember.Status> statuses);
    // USER-002: bỏ findCaptainTeamsWithActiveCount; thêm disbandRemaining, findMemberHistory (USER-002 CCD §5.2)
    // USER-002 v1.2 (ADR-0007): leaveAllActive + rejectAllPending + existsActiveCaptainByUserId → 2 method dưới
    Page<MemberView> findActiveMembers(UUID teamId, Pageable pageable);
    Optional<MemberView> findMemberViewById(UUID memberId);             // native, join user (response 2.13)
    @Lock(PESSIMISTIC_WRITE)
    List<TeamMember> findByUserIdAndStatusInOrderByIdAsc(UUID userId, Collection<TeamMember.Status> statuses); // 2.7 khoá
    @Modifying int leaveAndRejectAllOpen(UUID userId, OffsetDateTime now); // 2.7: MEMBER ACTIVE → LEFT, PENDING → REJECTED, version + 1
}
public interface TeamCaptainHistoryRepository extends JpaRepository<TeamCaptainHistory, UUID> {
    Page<CaptainHistoryView> findHistory(UUID teamId, Pageable pageable);
}
```

`Page` native query MUST khai báo `countQuery` tường minh. Bulk `@Modifying` tự tăng `version` vì không qua dirty-checking.

**Kiểu dữ liệu trong projection (native query):**

| Dữ liệu | Kiểu trong projection | Chuyển ở mapper (§8) |
|---|---|---|
| Thời gian (`timestamptz`) | `Instant` — Spring Data không chuyển native `timestamptz` sang `OffsetDateTime` cho projection | `Instant` → `OffsetDateTime` UTC |
| `skill_levels` (cột TEXT) | `String` (JSON thô) | JSON → `List<String>` bằng `SkillLevelsConverter` |
| User đã xoá | Cờ `Boolean *Deleted` (`captainDeleted`, `userDeleted`, `fromUserDeleted`, `toUserDeleted`; `UserAuthView.deleted`) | Che tên/avatar bằng `DeletedUserMasking` |

## 6. Entity — thay đổi so với hiện trạng

| Entity | Thay đổi | Cột DB (V2) |
|---|---|---|
| `user.User` | + `role` (`enum Role { USER, ADMIN }`, `@Enumerated(STRING)`, mặc định `USER`) · `districtId` → `wardId` | `role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','ADMIN'))` · rename `district_id` → `ward_id`, index `idx_user_ward` |
| `user.AdministrativeUnit` | `enum Level { province, ward }` (bỏ `district`) | CHECK `level IN ('province','ward')` · CHECK ward phải có `parent_id` |
| `team.Team` | + `skillLevels` (`List<String>`, `@Convert(SkillLevelsConverter)`, `columnDefinition = "TEXT"`) · + `inactiveReason` · + `@Version Long version` | `skill_levels TEXT NOT NULL DEFAULT '[]'` · `inactive_reason VARCHAR(500)` · `version BIGINT NOT NULL DEFAULT 0` |
| `team.TeamMember` | `enum Status { PENDING, ACTIVE, LEFT, REJECTED }` · + `@Version Long version` | CHECK status thêm `'REJECTED'` · `version BIGINT NOT NULL DEFAULT 0` · thay `idx_team_member_active` bằng unique `(team_id, user_id) WHERE status IN ('PENDING','ACTIVE')` |
| `team.TeamCaptainHistory` | + `reason` | `reason VARCHAR(500)` |
| `team.TeamTag` | **Xoá** entity (+ test liên quan) | `DROP TABLE team_tag` |
| — | — | Thay `idx_team_status` bằng `idx_team_status_created (status, created_at DESC)` |

**`SkillLevelsConverter`** (`AttributeConverter<List<String>, String>`): `null`/rỗng ↔ `"[]"`; serialize JSON bằng Jackson có sẵn trong classpath. Validation nhãn ở tầng request (§7), DB không còn CHECK nhãn (đánh đổi khi bỏ `team_tag`).

**`V3__seed_administrative_unit.sql`:** 5 tỉnh/thành + ~50 phường/xã, UUID cố định, `parent_id` của ward = province. Danh sách cụ thể: developer trình trong kế hoạch, user duyệt.

## 7. DTO

- Field + ràng buộc: `API_Spec.md` §2 (request, theo endpoint), §4.2 (response). Request DTO dùng `record` hoặc Lombok theo convention §7; annotation: `@NotBlank`, `@Size`, `@Email`, `@Pattern`, `@Past`.
- Ràng buộc tuỳ biến: `@MinAge(13)` (cho `dateOfBirth`), `@ValidSkillLevels` (≤3, không trùng, ∈ `SkillLevel`), `@OneOf` cho enum dạng chuỗi (`provider`, `gender`, `preferredPosition`).
- `preferredPosition` dùng cùng tập giá trị với cột `mercenary_recruitment.position` (BR-U06) — đổi tập giá trị thì đổi cả hai.
- `SkillLevel`: hằng danh sách 9 nhãn (nguồn duy nhất cho validator).
- **Enum mapping:** `provider` lưu DB lowercase (`google`, `facebook`, `phone`) → API UPPERCASE (`GOOGLE`, `FACEBOOK`, `PHONE`) — chuyển trong `ProfileMapper` (§8).
- Thời gian trả ra: `OffsetDateTime` ISO-8601 — cột DB là `TIMESTAMPTZ`. Response dựng từ projection: `Instant` → UTC (`Z`) qua mapper (§5). **Lệch (chưa sửa code):** giá trị lấy thẳng từ `OffsetDateTime.now()` mang offset của timezone JVM — `createdAt`/`updatedAt` của 2.8 (`Team` `@PrePersist`), `invitedAt` của 2.12 (`TeamMember` `@PrePersist`), `transferredAt` của 2.15 (`TeamServiceImpl#transferCaptain`), `error.timestamp` (`ErrorResponse.from`). [Inference] JVM chạy `Asia/Ho_Chi_Minh` → ra `+07:00` (chưa kiểm ở runtime). Hợp đồng ngoài chỉ hứa "ISO-8601 kèm offset" (`API_Spec.md` §1.1). Tech-debt: §12.
- `DeletedUserMasking`: hàm thuần áp quy tắc tên user đã xoá (`API_Spec.md` §1.1) — chỉ che ở response, DB giữ nguyên hồ sơ (`"user".deleted_at IS NOT NULL` → cờ `*Deleted`). **Chỉ dùng trong mapper** (qua `MapperSupport`), dựa vào cờ `*Deleted` của projection — SQL không che tên, để logic che tên có một nguồn.

## 8. Mapper (MapStruct)

| Mapper | Method |
|---|---|
| `ProfileMapper` | `ProfileResponse toResponse(ProfileView view, List<OAuthAccount> accounts)` |
| `TeamMapper` | `TeamResponse toResponse(TeamDetailView view)` · `TeamSummaryResponse toSummary(TeamSummaryView view)` · `AdminTeamSummaryResponse toAdminSummary(TeamSummaryView view)` · `CaptainHistoryResponse toResponse(CaptainHistoryView view)` |
| `MembershipMapper` | `InvitationResponse toInvitation(TeamMember m, String teamName)` · `MemberResponse toMember(MemberView view)` |

Cấu hình chung: `@Mapper(componentModel = "spring", uses = MapperSupport.class, unmappedTargetPolicy = ReportingPolicy.ERROR)` (`TeamMapper`, `MembershipMapper` thêm `imports = MapperSupport.class` để gọi trong `expression`). Dependency: §11.

**`MapperSupport`** (`final class`, method `static`) — một nguồn chuyển đổi cho 3 mapper:

```java
static OffsetDateTime toUtc(Instant instant);              // null-safe, ZoneOffset.UTC
static List<String> skillLevels(String json);              // qua SkillLevelsConverter
static String maskName(String displayName, Boolean deleted);   // qua DeletedUserMasking
static String maskAvatar(String avatarUrl, Boolean deleted);   // qua DeletedUserMasking
```

`ProfileMapper` có `default String provider(OAuthAccount.Provider)` chuyển lowercase → UPPERCASE.

## 9. Security (ADR-0001)

### 9.1. SecurityConfig
- Stateless, `csrf` tắt, `oauth2ResourceServer(jwt)` với bean `JwtDecoder` HS256 (`NimbusJwtDecoder.withSecretKey`), validator = `JwtValidators.createDefault()` + `AccessTokenTypeValidator` (§9.2).
- Chuyển claim → quyền: **bean `JwtAuthenticationConverter` của Spring** khai báo trong `SecurityConfig`, dùng `JwtGrantedAuthoritiesConverter` (claim `roles`, prefix `ROLE_`). Không viết class converter riêng.
- **`BearerTokenResolver` bỏ qua header trên `/api/v1/auth/**`** (hợp đồng ngoài: `API_Spec.md` §1.1): bộ lọc Bearer của Spring xác thực token ngay khi có header `Authorization: Bearer`, kể cả trên đường dẫn `permitAll` — token sai làm endpoint public trả 401 (client tự gắn access token hết hạn khi gọi `/auth/refresh` → không refresh được). Test: token sai định dạng (`AuthControllerTest.publicAuthEndpointIgnoresInvalidBearerHeader`); token hết hạn đi cùng cơ chế, chưa có test riêng. Resolver dùng chung hằng `PUBLIC_AUTH_PATH` với `permitAll`: khớp → trả `null` (không xác thực), còn lại → `DefaultBearerTokenResolver`.
  - Chỉ áp cho `/api/v1/auth/**`. Health và API docs giữ hành vi mặc định có chủ đích (Bearer sai → 401 theo cơ chế mặc định; chưa có test); đưa vào matcher khi có client gọi các đường dẫn này kèm token.
  - **NEVER** đặt endpoint cần xác thực dưới `/api/v1/auth/**`: SecurityContext ở đó luôn anonymous, `CurrentUserArgumentResolver` ném `AUTH_001` dù token hợp lệ. Cần endpoint như vậy (vd logout) → thu hẹp `PUBLIC_AUTH_PATH` (dùng cho cả `permitAll` và resolver) thành danh sách endpoint public cụ thể, để endpoint mới rơi vào `authenticated()`.
- Phân quyền theo đường dẫn:

| Đường dẫn | Quy tắc |
|---|---|
| `/api/v1/auth/**` | `permitAll` |
| `GET /actuator/health`, `GET /actuator/health/**` | `permitAll` — health check cho hạ tầng. `/actuator/health` trả `{"status":"UP"}` (không có `components`); đường dẫn thành phần (vd `/health/db`) trả 404 vì Spring Boot mặc định ẩn thành phần. `/**` dành cho probe liveness/readiness khi bật |
| `GET /v3/api-docs/**`, `GET /swagger-ui/**`, `GET /swagger-ui.html` | `permitAll` — tài liệu API cho dev FE; **mặc định tắt** (404), bật khi `SPRINGDOC_ENABLED=true` |
| `/api/v1/admin/**` | `hasRole('ADMIN')` |
| Còn lại | `authenticated()` — gồm actuator khác; hiện chỉ `health` được expose qua web (không cấu hình `management.endpoints.web.exposure.*`), quy tắc này là lớp chặn thứ hai khi expose thêm |

- **API docs fail-closed (ADR-0004):** `application.properties` đặt `springdoc.api-docs.enabled` và `springdoc.swagger-ui.enabled` = `${SPRINGDOC_ENABLED:false}` → không đặt biến (prod) = tắt. Dev bật bằng `SPRINGDOC_ENABLED=true` (`.env.dev`; `.env.example` để dạng comment để copy nguyên file không mở Swagger). Quyết định mở health + API docs: user duyệt 2026-11-03.
- `RestAuthenticationEntryPoint` → `401 AUTH_001`; `RestAccessDeniedHandler` → `403 AUTH_004` (envelope `error`). Hai class dùng chung `SecurityErrorWriter` (package-private) để ghi envelope JSON.

### 9.2. JwtTokenService
```java
String issueAccessToken(UUID userId, User.Role role);   // typ=access, exp = 3600s
String issueRefreshToken(UUID userId);                  // typ=refresh, exp = 7 ngày
UUID parseRefreshToken(String token);                   // sai chữ ký/hết hạn/typ≠refresh → AUTH_002
```
Claims: `sub` = userId, `roles` = `["USER"]` hoặc `["ADMIN"]` (theo `security-backend.md` §1.2), `typ`, `iat`, `exp`. `JwtDecoder` của resource server có validator `api/security/AccessTokenTypeValidator` (`OAuth2TokenValidator<Jwt>`) từ chối `typ ≠ access` → refresh token không dùng được để gọi API (401).

### 9.3. JwtProperties (`arenaflow.jwt.*`)
`secret` (từ env `JWT_SECRET`, ≥256 bit — hằng `JwtProperties.MIN_SECRET_BYTES`, `JwtTokenService` dùng chung; base64; không commit giá trị thật) · `access-token-ttl = 1h` · `refresh-token-ttl = 7d`. `security-backend.md` đã đồng bộ (cập nhật từ 24h → 1h).

### 9.4. CurrentUser
- `api/security/CurrentUser`: annotation `@Target(PARAMETER)`, `@Retention(RUNTIME)`.
- `api/security/CurrentUserArgumentResolver` (`HandlerMethodArgumentResolver`): tham số `UUID` có `@CurrentUser` → `UUID.fromString(jwt.getSubject())`.
- `api/config/WebMvcConfig` (`WebMvcConfigurer`) đăng ký resolver.
- Controller nhận `@CurrentUser UUID currentUserId`.

## 10. Exception Handling

- `ErrorCode` enum: `code`, `HttpStatus`, `message` — một nguồn cho mọi mã trong `API_Spec.md` §5 (mã miền, `VALIDATION_ERROR`, `SYS_001`–`SYS_005`). `SYS_004`/`SYS_005` mang status danh nghĩa (400/500); handler trả status gốc của exception framework.
- `BusinessException(ErrorCode, String details)` — mọi lỗi miền ném qua class này. **NEVER** ném `SYS_*` hoặc dùng `VALIDATION_ERROR` qua `BusinessException`: các mã này chỉ do `GlobalExceptionHandler` phát ra.
- Envelope lỗi: `ErrorEnvelope(ErrorResponse error)`; `ErrorResponse` chứa `code`, `message`, `details`, `timestamp`, `path`, `validationErrors` (`List<ValidationError>`).
- `GlobalExceptionHandler` (`@RestControllerAdvice`):

| Exception | Kết quả | Log |
|---|---|---|
| `BusinessException` | status + mã theo `ErrorCode` | `WARN` (mã lỗi + URI) |
| `MethodArgumentNotValidException`, `ConstraintViolationException`, `HandlerMethodValidationException` | 400 `VALIDATION_ERROR`; `field` = tên field body / tên tham số client gửi (`ConstraintViolationException`: chỉ lấy node cuối của property path, bỏ tiền tố tên method — `listTeams.page` → `page`). Controller hiện có `@Validated` ở class → lỗi tham số đi qua `ConstraintViolationException`; nhánh `HandlerMethodValidationException` là lớp phòng hờ khi bỏ `@Validated` | — |
| `HttpMessageNotReadableException` (JSON sai cú pháp) | 400 `VALIDATION_ERROR` | — |
| `MethodArgumentTypeMismatchException` (UUID path sai, enum `status` sai) | 400 `VALIDATION_ERROR` | — |
| `MissingServletRequestParameterException` | 400 `VALIDATION_ERROR`, `field` = tên tham số | — |
| `DataIntegrityViolationException` trên unique index lời mời | 409 `TEAM_003` — **lớp phòng hờ**: hiện không luồng nào chạy tới (`inviteMember` tự bắt lỗi trùng và ném `TEAM_003`); giữ để luồng ghi `team_member` sau này không rơi vào 500 (user chọn giữ 2026-11-04) | `WARN` |
| `DataIntegrityViolationException` khác | 500 `SYS_005` | `ERROR` chỉ ghi tên constraint (`unknown` khi không lấy được) + class lỗi gốc — **không** kèm exception |
| `Exception` là `org.springframework.web.ErrorResponse` (`NoResourceFoundException`, `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`, `ResponseStatusException`…) | 400 → `VALIDATION_ERROR`; 404 `SYS_001`; 405 `SYS_002`; 415 `SYS_003`; 4xx khác `SYS_004`; 5xx `SYS_005` — giữ status + header gốc (vd `Allow`), không lộ `reason`. Status không chuẩn (vd 499) → 500 `SYS_005` | 4xx `WARN` (status + URI); 5xx / status không chuẩn `ERROR` kèm exception |
| `Exception` (fallback còn lại) | 500 `SYS_005`, không lộ stack trace | `ERROR` kèm exception |

Log `WARN` chỉ ghi mã lỗi/status + URI; log `ERROR` kèm exception (stack trace) để điều tra, trừ lỗi SQL (chỉ tên constraint). **NEVER** ghi token/secret/PII/body request.

**Chặn PII từ lỗi SQL ở gốc:** pgjdbc mặc định đưa `Detail: Key (cột)=(giá trị)` vào message `SQLException` → logger `org.hibernate.orm.jdbc.error` (WARN) và mọi log kèm exception đều chứa giá trị dòng (vd số điện thoại). `application.properties` đặt `spring.datasource.hikari.data-source-properties.logServerErrorDetail=false` → message chỉ còn severity + primary message (không có Detail/Hint/Where); với 23505 primary message chứa tên constraint (nhận diện `TEAM_003` theo tên index vẫn đúng). Kiểm bằng `SqlErrorLoggingPiiTest` (đụng unique thật trên DB dev). **NEVER** đặt `logServerErrorDetail` trong `DATABASE_URL` — pgjdbc ưu tiên tham số URL hơn connection properties, đặt `true` ở URL sẽ mở lại rò rỉ. Lưu ý: lỗi SQLState khác (vd 22P02 sai kiểu UUID) có thể chứa giá trị input ngay trong primary message.

### Lệch convention có chủ đích

| Convention | Thiết kế | Lý do |
|---|---|---|
| `convention-backend.md` §8: một exception class cho mỗi lỗi | `BusinessException` + `ErrorCode` | 22 mã lỗi → tránh 22 class + 22 handler trùng lặp |
| §9: `@Transactional(readOnly = true)` ở class level | Annotate theo method; ghi có retry dùng `ConcurrencyConflictRetrier` | Retry phải nằm **ngoài** transaction; class-level tx sẽ bọc cả vòng retry |
| §10: `@SQLDelete` + `@Where` trên entity soft-delete | Lọc `deleted_at IS NULL` tường minh trong query | Lịch sử/thành viên phải join được user đã xoá để hiển thị "Người dùng đã xóa" |
| `api-convention.md` §6: danh sách domain mã lỗi (`ARENA`, `BOOKING`, `USER`, `TEAM`, `AUTH`, `PAYMENT`) | Thêm domain `SYS` cho lỗi tầng HTTP/hệ thống (`SYS_001`–`SYS_005`) | Lỗi không gắn miền nghiệp vụ; giữ format `<DOMAIN>_<NUMBER>`. User chọn 2026-11-04. `[cần xác nhận]` bổ sung `SYS` vào `api-convention.md` trên nhánh `chore/agent-framework-*` riêng |
| `non-functional-standards.md` §3: NEVER truy vấn lại dữ liệu đã nạp trong cùng request | 2.6 `PATCH /profile` đọc lại `ProfileView` sau U user (5/W6) | Response lấy đúng dữ liệu DB sau flush, dùng chung `ProfileMapper.toResponse(ProfileView, …)` với `GET /profile`. User chấp nhận 2026-11-04 |

## 11. Dependencies

> Từ v1.2 dependency chia theo module POM (ADR-0006): `common` (`spring-web` cho `HttpStatus`), `domain` (`data-jpa`, `jackson-databind`), `application` (`validation`, `spring-security-oauth2-jose`, `mapstruct`), `api` (webmvc, security, resource-server, flyway, PostgreSQL, actuator, springdoc, prometheus, devtools). Root POM giữ `mapstruct.version` + `annotationProcessorPaths`.

| Dependency | Trạng thái |
|---|---|
| `spring-boot-starter-webmvc`, `-data-jpa`, `-security`, `-validation`, `-flyway`, `flyway-database-postgresql`, PostgreSQL driver, Lombok | Đã có trong `pom.xml` |
| `micrometer-registry-prometheus`, `spring-boot-devtools` | Đã có — không dùng trong USER-001 (endpoint prometheus chưa expose) |
| `spring-boot-starter-security-oauth2-resource-server` (Nimbus JOSE, dùng cho HS256) | Đã có |
| `org.mapstruct:mapstruct` + `mapstruct-processor` (+ `lombok-mapstruct-binding` trong `annotationProcessorPaths`) | Đã thêm — `mapstruct.version = 1.5.5.Final` |
| `spring-boot-starter-actuator` | Đã có — chỉ `health` public (§9.1) |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` `3.0.3` | Đã có — Swagger UI / OpenAPI, mặc định tắt, bật bằng `SPRINGDOC_ENABLED=true` (§9.1, ADR-0004) |
| `spring-boot-starter-security-oauth2-authorization-server` | Đã gỡ — USER-001 không làm OAuth provider |
| H2 (test) | Đã gỡ — integration test chạy trên DB dev PostgreSQL (`.env.dev`) |

## 12. Ghi chú thiết kế

- **Base package & ID type:** Repo dùng `com.phananh.arenaflow` (đổi từ `com.phananh.ArenaFlow` ở v1.2, ADR-0006) + UUID; `convention-backend.md` đã cập nhật qua `workflow-adapt` (2026-11-02, xem `decisions-log.md`).
- **TTL access token:** `security-backend.md` đã cập nhật từ 24h → 1h (theo ADR-0001).
- **Enum `provider`:** DB lưu lowercase, API trả UPPERCASE — chuyển trong `ProfileMapper` (§8).
- **Tech-debt — chuẩn hoá thời gian về UTC:** các giá trị `OffsetDateTime.now()` ở §7 nên trả `Z` như response đọc từ projection. Làm ở task bugfix riêng (đổi giá trị trả ra → kiểm ảnh hưởng FE, ghi CHANGELOG).
- **ID UUID v7 (ADR-0008):** entity dùng `@UuidGenerator(style = VERSION_7)`; id tạo trước ADR-0008 giữ dạng v1. Hợp đồng ngoài chỉ hứa "UUID, không suy thứ tự từ id" (`API_Spec.md` §1.1).
