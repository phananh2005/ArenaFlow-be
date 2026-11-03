# Component & Class Design — User & Team Management

| Mục | Giá trị |
|---|---|
| Mã chức năng | `USER-001` |
| Phạm vi tầng | Backend (Spring Boot REST API) |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · Spring Data JPA · Flyway |
| Quy ước áp dụng | `docs/agent/convention-backend.md`, `docs/agent/security-backend.md` (lệch có chủ đích: §10) |
| Ràng buộc dữ liệu | `V1__initial_schema.sql`, entity hiện có `com.phananh.ArenaFlow.entity.*`, ADR-0001/0002/0003 |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-02 · v1.0 (baseline, chờ duyệt) |

## 1. Tổng quan kiến trúc

Layered: Controller → Service (interface + impl) → Repository → Entity. Entity **đã có** (24 class, FK lưu dạng cột UUID, không dùng `@ManyToOne`) — USER-001 chỉ sửa 5 entity, xoá 1 (§6). Hợp đồng API: `API_Spec.md`. Luồng/transaction/số truy vấn: `Processing_Flow.md`.

## 2. Package Structure

Base package: `com.phananh.ArenaFlow`. Entity giữ nguyên bố cục `entity/<domain>/`.

```
com.phananh.ArenaFlow
├── config/        SecurityConfig, JwtConfig (JwtProperties), JpaConfig
├── security/      JwtTokenService, JwtAuthenticationConverter, RestAuthenticationEntryPoint,
│                  RestAccessDeniedHandler, CurrentUser
├── controller/    AuthController, ProfileController, TeamController, InvitationController, AdminTeamController
├── service/       AuthService, ProfileService, TeamService, TeamMembershipService, AdminTeamService
│   └── impl/      *ServiceImpl
├── repository/    UserRepository, OAuthAccountRepository, AdministrativeUnitRepository,
│   │              TeamRepository, TeamMemberRepository, TeamCaptainHistoryRepository
│   └── projection/ TeamSummaryView, TeamDetailView, MemberView, CaptainHistoryView, ProfileView
├── entity/        (đã có) user/, team/ …  + converter/SkillLevelsConverter
├── dto/request/   OAuthLoginRequest, SendOtpRequest, VerifyOtpRequest, RefreshTokenRequest,
│                  ProfileUpdateRequest, CreateTeamRequest, UpdateTeamRequest, InviteMemberRequest,
│                  TransferCaptainRequest, MarkInactiveRequest
├── dto/response/  ApiResponse<T>, PageResponse<T>, PaginationMeta, AuthResponse, OtpSentResponse,
│                  ProfileResponse, WardResponse, ProviderResponse, TeamResponse, TeamSummaryResponse,
│                  AdminTeamSummaryResponse, InvitationResponse, MemberResponse, CaptainTransferResponse,
│                  CaptainHistoryResponse
├── mapper/        ProfileMapper, TeamMapper, MembershipMapper (MapStruct)
├── exception/     ErrorCode, BusinessException, ErrorResponse, ValidationError, GlobalExceptionHandler
└── common/        OptimisticLockRetrier, SkillLevel, PageRequestFactory, DeletedUserMasking
```

Thành phần trong `common/`, `dto/response/ApiResponse|PageResponse`, `exception/` là **dùng chung** → developer MUST chạy `reuse-scan` trước khi tạo.

## 3. Controller Layer

Mọi controller: `@RestController`, `@RequiredArgsConstructor`, `@Validated`; nhận `@Valid @RequestBody`; lấy user hiện tại qua `CurrentUser` (§9.4). Không chứa business logic.

| Controller | `@RequestMapping` | Endpoint (`API_Spec.md` §2) | Service |
|---|---|---|---|
| `AuthController` | `/api/v1/auth` | 2.1–2.4 | `AuthService` |
| `ProfileController` | `/api/v1/profile` | 2.5–2.7 | `ProfileService` |
| `TeamController` | `/api/v1/teams` | 2.8–2.12, 2.15–2.18 | `TeamService`, `TeamMembershipService` |
| `InvitationController` | `/api/v1/invitations` | 2.13–2.14 | `TeamMembershipService` |
| `AdminTeamController` | `/api/v1/admin/teams` | 2.19–2.21 | `AdminTeamService` |

201 trả kèm header `Location` (`api-convention.md` §5). `AuthController` chọn 201/200 theo `AuthResult.isNewUser()`.

## 4. Service Layer

Phân quyền captain/người được mời **làm trong service** (cần thứ tự guard 404 → 403, xem `Processing_Flow.md` §3), không dùng `@PreAuthorize` theo team. Admin chặn ở `SecurityConfig` (`/api/v1/admin/**` → `hasRole('ADMIN')`).

**Guard đội INACTIVE:** Các method `updateTeam`, `inviteMember`, `transferCaptain`, `acceptInvitation` MUST check `team.status = INACTIVE → TEAM_015` trước khi ghi.

### 4.1. AuthService
```java
AuthResult loginWithOAuth(OAuthLoginRequest request);      // register-or-login
OtpSentResponse sendOtp(SendOtpRequest request);           // mock, không chạm DB
AuthResult verifyOtp(VerifyOtpRequest request);            // register-or-login, provider=phone
AuthResponse refresh(RefreshTokenRequest request);
```
`AuthResult` = record `(AuthResponse body, boolean isNewUser)`. Deps: `UserRepository`, `OAuthAccountRepository`, `JwtTokenService`.

### 4.2. ProfileService
```java
ProfileResponse getProfile(UUID userId);
ProfileResponse updateProfile(UUID userId, ProfileUpdateRequest request);
void deleteAccount(UUID userId);
```
Deps: `UserRepository`, `OAuthAccountRepository`, `AdministrativeUnitRepository`, `TeamRepository`, `TeamMemberRepository`, `OptimisticLockRetrier`, `ProfileMapper`.

### 4.3. TeamService
```java
TeamResponse createTeam(UUID userId, CreateTeamRequest request);
TeamResponse getTeam(UUID teamId);
PageResponse<TeamSummaryResponse> listTeams(TeamStatusFilter status, int page, int pageSize);
TeamResponse updateTeam(UUID teamId, UUID userId, UpdateTeamRequest request);
CaptainTransferResponse transferCaptain(UUID teamId, UUID userId, TransferCaptainRequest request);
PageResponse<CaptainHistoryResponse> listCaptainHistory(UUID teamId, int page, int pageSize);
```
`TeamStatusFilter` = enum `ACTIVE | INACTIVE | ALL`.

### 4.4. TeamMembershipService
```java
InvitationResponse inviteMember(UUID teamId, UUID captainUserId, InviteMemberRequest request);
MemberResponse acceptInvitation(UUID invitationId, UUID userId);
InvitationResponse rejectInvitation(UUID invitationId, UUID userId);
PageResponse<MemberResponse> listMembers(UUID teamId, int page, int pageSize);
void leaveTeam(UUID teamId, UUID userId);
```

### 4.5. AdminTeamService
```java
TeamResponse markInactive(UUID teamId, MarkInactiveRequest request);
TeamResponse reactivate(UUID teamId);
PageResponse<AdminTeamSummaryResponse> listTeams(TeamStatusFilter status, int page, int pageSize);
```

### 4.6. Transaction trong ServiceImpl (lệch convention §9 — xem §10)
- **Không** đặt `@Transactional` ở class level.
- Method đọc: `@Transactional(readOnly = true)`.
- Method ghi chạm `team`/`team_member` (`updateProfile` không thuộc nhóm này): **không** annotate; thân method gọi `optimisticLockRetrier.execute(() -> …)` — mỗi lần thử là 1 transaction mới.
- Method ghi còn lại (`loginWithOAuth`, `verifyOtp`, `updateProfile`, `createTeam`, `inviteMember`): `@Transactional`.

## 5. Repository Layer

Truy vấn danh sách/chi tiết có join dùng `@Query(nativeQuery = true)` trả **interface projection** (`repository/projection/`) — không nạp entity rồi lazy-load. SQL cụ thể: `Processing_Flow.md` §3.

```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByIdAndDeletedAtIsNull(UUID id);
    Optional<ProfileView> findProfileViewById(UUID id);                 // @Query native, join ward + province
    @Modifying int softDelete(UUID id, OffsetDateTime now);             // WHERE deleted_at IS NULL
}
public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, UUID> {
    Optional<UserAuthView> findAuthView(OAuthAccount.Provider provider, String providerUserId); // join user
    List<OAuthAccount> findByUserId(UUID userId);
}
public interface AdministrativeUnitRepository extends JpaRepository<AdministrativeUnit, UUID> {
    Optional<WardView> findWardWithProvince(UUID wardId);              // level = 'ward', join parent
}
public interface TeamRepository extends JpaRepository<Team, UUID> {
    Optional<TeamDetailView> findDetailById(UUID teamId);
    Page<TeamSummaryView> findSummaries(String status /* null = ALL */, Pageable pageable);
    @Modifying int markInactiveByIds(Collection<UUID> ids, String reason, OffsetDateTime now); // version = version + 1
}
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    Optional<TeamMember> findByTeamIdAndUserIdAndStatus(UUID teamId, UUID userId, TeamMember.Status status);
    Optional<TeamMember> findByTeamIdAndRoleAndStatus(UUID teamId, TeamMember.Role role, TeamMember.Status status);
    boolean existsByTeamIdAndUserIdAndStatusIn(UUID teamId, UUID userId, Collection<TeamMember.Status> statuses);
    List<CaptainTeamView> findCaptainTeamsWithActiveCount(UUID userId); // 1 truy vấn, count bằng subquery
    Page<MemberView> findActiveMembers(UUID teamId, Pageable pageable);
    @Modifying int leaveAllActive(UUID userId, OffsetDateTime now);      // ACTIVE → LEFT, version + 1
    @Modifying int rejectAllPending(UUID userId);                        // PENDING → REJECTED, version + 1
}
public interface TeamCaptainHistoryRepository extends JpaRepository<TeamCaptainHistory, UUID> {
    Page<CaptainHistoryView> findHistory(UUID teamId, Pageable pageable);
}
```

`Page` native query MUST khai báo `countQuery` tường minh. Bulk `@Modifying` tự tăng `version` vì không qua dirty-checking.

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

- Field + ràng buộc: `API_Spec.md` §3. Request DTO dùng `record` hoặc Lombok theo convention §7; annotation: `@NotBlank`, `@Size`, `@Email`, `@Pattern`, `@Past`.
- Ràng buộc tuỳ biến: `@MinAge(13)` (cho `dateOfBirth`), `@ValidSkillLevels` (≤3, không trùng, ∈ `SkillLevel`), `@OneOf` cho enum dạng chuỗi (`provider`, `gender`, `preferredPosition`).
- `SkillLevel`: hằng danh sách 9 nhãn (nguồn duy nhất cho validator).
- **Enum mapping:** `provider` lưu DB lowercase (`google`, `facebook`, `phone`) → API UPPERCASE (`GOOGLE`, `FACEBOOK`, `PHONE`). Dùng `@JsonProperty` hoặc mapper tuỳ biến.
- Thời gian trả ra: `OffsetDateTime` (UTC, ISO-8601) — cột DB là `TIMESTAMPTZ`.
- `DeletedUserMasking`: hàm thuần áp quy tắc tên user đã xoá (`API_Spec.md` §3.12) — dùng trong mapper, hoặc làm ngay trong SQL bằng `CASE WHEN u.deleted_at IS NOT NULL`.

## 8. Mapper (MapStruct)

| Mapper | Method |
|---|---|
| `ProfileMapper` | `ProfileResponse toResponse(ProfileView view, List<OAuthAccount> accounts)` |
| `TeamMapper` | `TeamResponse toResponse(TeamDetailView view)` · `TeamSummaryResponse toSummary(TeamSummaryView view)` · `AdminTeamSummaryResponse toAdminSummary(TeamSummaryView view)` · `CaptainHistoryResponse toResponse(CaptainHistoryView view)` |
| `MembershipMapper` | `InvitationResponse toInvitation(TeamMember m, String teamName)` · `MemberResponse toMember(MemberView view)` |

`@Mapper(componentModel = "spring")`. Cần thêm dependency (§11).

## 9. Security (ADR-0001)

### 9.1. SecurityConfig
- Stateless, `csrf` tắt, `oauth2ResourceServer(jwt)` với `JwtDecoder` HS256 (`NimbusJwtDecoder.withSecretKey`).
- `permitAll`: `/api/v1/auth/**`. `hasRole('ADMIN')`: `/api/v1/admin/**`. Còn lại: `authenticated()`.
- `RestAuthenticationEntryPoint` → `401 AUTH_001`; `RestAccessDeniedHandler` → `403 AUTH_004` (envelope `error`).

### 9.2. JwtTokenService
```java
String issueAccessToken(UUID userId, User.Role role);   // typ=access, exp = 3600s
String issueRefreshToken(UUID userId);                  // typ=refresh, exp = 7 ngày
UUID parseRefreshToken(String token);                   // sai chữ ký/hết hạn/typ≠refresh → AUTH_002
```
Claims: `sub` = userId, `roles` = `["USER"]` hoặc `["ADMIN"]` (theo `security-backend.md` §1.2), `typ`, `iat`, `exp`. `JwtDecoder` của resource server có validator từ chối `typ ≠ access`.

### 9.3. JwtProperties (`arenaflow.jwt.*`)
`secret` (từ env `JWT_SECRET`, ≥256 bit, base64; không commit giá trị thật) · `access-token-ttl = 1h` · `refresh-token-ttl = 7d`. `security-backend.md` đã đồng bộ (cập nhật từ 24h → 1h).

### 9.4. CurrentUser
Bean/argument resolver lấy `UUID` từ `Jwt.getSubject()`; controller nhận `UUID currentUserId`.

## 10. Exception Handling

- `ErrorCode` enum: `code`, `HttpStatus`, `message` — một nguồn cho bảng `API_Spec.md` §4.
- `BusinessException(ErrorCode, String details)` — mọi lỗi miền ném qua class này.
- `GlobalExceptionHandler` (`@RestControllerAdvice`): `BusinessException` → status + envelope `error`; `MethodArgumentNotValidException`/`ConstraintViolationException`/`HandlerMethodValidationException` → `VALIDATION_ERROR`; `DataIntegrityViolationException` trên unique index lời mời → `TEAM_003`; fallback → 500 (không lộ stack trace).

### Lệch convention có chủ đích

| Convention | Thiết kế | Lý do |
|---|---|---|
| `convention-backend.md` §8: một exception class cho mỗi lỗi | `BusinessException` + `ErrorCode` | 22 mã lỗi → tránh 22 class + 22 handler trùng lặp |
| §9: `@Transactional(readOnly = true)` ở class level | Annotate theo method; ghi có retry dùng `OptimisticLockRetrier` | Retry phải nằm **ngoài** transaction; class-level tx sẽ bọc cả vòng retry |
| §10: `@SQLDelete` + `@Where` trên entity soft-delete | Lọc `deleted_at IS NULL` tường minh trong query | Lịch sử/thành viên phải join được user đã xoá để hiển thị "Người dùng đã xóa" |

## 11. Dependencies

| Dependency | Trạng thái |
|---|---|
| `spring-boot-starter-web`, `-data-jpa`, `-security`, `-validation`, `-flyway`, `flyway-database-postgresql`, PostgreSQL driver, Lombok | Đã có trong `pom.xml` |
| `spring-boot-starter-security-oauth2-resource-server` (Nimbus JOSE, dùng cho HS256) | Đã có |
| `org.mapstruct:mapstruct` + `mapstruct-processor` (+ `lombok-mapstruct-binding` trong `annotationProcessorPaths`) | **Cần thêm** — phiên bản chốt khi implement |
| `spring-boot-starter-security-oauth2-authorization-server` | **Gỡ bỏ** — USER-001 không làm OAuth provider |

## 12. Ghi chú thiết kế

- **Base package & ID type:** Repo dùng `com.phananh.ArenaFlow` + UUID. `convention-backend.md` sẽ cập nhật qua `workflow-adapt` (chốt sau khi thiết kế được duyệt).
- **TTL access token:** `security-backend.md` đã cập nhật từ 24h → 1h (theo ADR-0001).
- **Enum `provider`:** DB lưu lowercase, API trả UPPERCASE — cần mapper trong DTO layer.
- **Dependency `oauth2-authorization-server`:** Gỡ khỏi `pom.xml` (USER-001 không làm OAuth provider).
