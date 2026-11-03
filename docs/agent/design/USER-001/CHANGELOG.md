# CHANGELOG — USER-001 User & Team Management

## [v1.1] — 2026-11-04

**Type:** MINOR (mã 500 đổi `INTERNAL_ERROR` → `SYS_005` không tính là "đổi mã lỗi" vì v1.0 chưa công bố mã này trong `API_Spec.md` — chỉ hard-code trong handler) · **Tóm tắt:** Đồng bộ thiết kế với code sau implement (19 điểm lệch + bổ sung khi review), bugfix lỗi framework (4xx bị trả 500, PII trong log) kèm mã lỗi `SYS_*`, và API docs mặc định tắt (ADR-0004), không đổi endpoint, request/response thành công, mã lỗi miền hay DB schema.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | CCD §2, §9.1 | Sửa | Class `security/JwtAuthenticationConverter` → bean `JwtAuthenticationConverter` của Spring (`JwtGrantedAuthoritiesConverter`, claim `roles`, prefix `ROLE_`) trong `SecurityConfig` | Tái dùng framework |
| 2 | CCD §2, §9.1, §9.2, §9.4 | Thêm | — → `AccessTokenTypeValidator`, `@CurrentUser` + `CurrentUserArgumentResolver`, `config/WebMvcConfig`, `SecurityErrorWriter` (package-private) | Đặt tên/tách file cho thành phần design mô tả bằng lời |
| 3 | CCD §2 | Xoá | `config/JpaConfig` → không có (auto-config đủ) | Chưa có nhu cầu |
| 4 | CCD §11 | Sửa | MapStruct "chốt khi implement" → `1.5.5.Final`; thêm actuator, springdoc `3.0.3`, prometheus, devtools; `starter-web` → `starter-webmvc`; đã gỡ H2, `oauth2-authorization-server` | Khớp `pom.xml` |
| 5 | CCD §5 | Sửa | `findAuthView(Provider, String)` → `default` method gọi native `findAuthViewByProviderName(String, String)` | Native query cần chuỗi lowercase |
| 6 | CCD §5, §7 | Thêm | — → projection trả `Instant`; mapper chuyển `OffsetDateTime` UTC | Spring Data không chuyển native `timestamptz` → `OffsetDateTime` |
| 7 | CCD §5, §7 | Sửa | Che tên user đã xoá "trong mapper hoặc SQL" → chỉ trong mapper, dựa cờ `*Deleted` của projection | Một nguồn logic |
| 8 | CCD §5, §8 | Thêm | — → `skillLevels` trong projection là JSON thô `String`, mapper chuyển `List<String>` | Native query trả cột TEXT |
| 9 | CCD §2, §8 | Thêm | 3 mapper → + `MapperSupport` (static), `unmappedTargetPolicy = ERROR`, `ProfileMapper.provider()` | Một nguồn chuyển đổi |
| 10 | CCD §4.1, §4.6 · PF §2.1, §4 | Sửa | `loginWithOAuth`/`verifyOtp` `@Transactional` → không annotate, tạo tài khoản trong `TransactionTemplate`, bắt `DataIntegrityViolationException` ngoài transaction; deps + `JwtProperties`, `TransactionTemplate` | Transaction rollback-only không đọc lại được |
| 11 | PF §3 (2.1, 2.3) | Sửa | S, I, I (+S) = 3–4 → tạo mới S, I, I, U; đụng độ S, I, I(lỗi), S = 4 ≤ W6 | Tên mặc định cần id sinh lúc persist |
| 12 | PF §3 (2.6) | Sửa | S, S, U, S = 4 → S, (S ward), U, S `ProfileView`, S oauth = 4–5 ≤ W6 | Response dựng qua `ProfileMapper` từ DB |
| 13 | PF §2.2, §5 | Sửa | Retrier bắt "tín hiệu cập nhật thiếu dòng" (chưa nêu cách) → `deleteAccount` ném `ObjectOptimisticLockingFailureException(Team.class, ids)` | Dùng lại cơ chế retry |
| 14 | CCD §4.3 | Thêm | — → `PageResponse.from(...)`, `PageRequestFactory` `@Component`, `TeamStatusFilter.toQueryValue()` | Dùng chung cho 3 service |
| 15 | CCD §5 | Thêm | — → `TeamMemberRepository.findMemberViewById(UUID)` | Response 2.13 |
| 16 | CCD §10 | Thêm | — → handler `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException` → 400; bảng mức log; `ErrorEnvelope` | Trước đó rơi vào 500 |
| 17 | CCD §3 | Thêm | — → `@Min/@Max` cho `page`/`pageSize`, `status` mặc định ACTIVE/ALL bind enum | Chặn giá trị sai trước `PageRequestFactory` |
| 18 | CCD §2, §4 | Thêm | Mỗi service tự guard → `service/support/TeamGuard` (`activeTeam`, `requireTeam`, `requireCaptain`) | Guard lặp ở 2 service |
| 19 | CCD §9.1 | Thêm | `permitAll` chỉ `/api/v1/auth/**` → + GET health, OpenAPI/Swagger | User duyệt 2026-11-03 |
| 20 | CCD §4 · API_Spec §1, §4, §6 | Sửa | Guard TEAM_015 "sửa/mời/chuyển quyền/accept" → thêm reject | Khớp API_Spec 2.14, PF 2.14 và code (danh sách tóm tắt bị sót) |
| 21 | CCD §2 | Thêm | — → package `validation/`, projection `UserAuthView`, `WardView`, `CaptainTeamView`, `ErrorEnvelope` | Liệt kê đủ thành phần đã có |
| 22 | PF §2.1, §4, §5 · CCD §4.6 | Sửa | Nguồn đụng độ chỉ unique oauth_account → thêm `idx_user_phone_unique` (2.3); nhánh đọc lại: đã xoá → AUTH_003, rỗng → ném lại → 500 | Khớp code |
| 23 | CCD §12 | Xoá | Ghi chú cũ (workflow-adapt "sẽ cập nhật", gỡ authorization-server lặp §11) | Đã xong / trùng |
| 24 | CCD §10 · API_Spec §4 | Sửa (bugfix) | Fallback `Exception` trả 500 cho cả lỗi 4xx của framework → exception `org.springframework.web.ErrorResponse` giữ status + header gốc (mã: #26), log WARN; status không chuẩn → 500 | Reproduce bằng `FrameworkErrorStatusTest`: URL sai / method sai / Content-Type sai đều trả 500 + log ERROR |
| 25 | CCD §9.1, §11 · ADR-0004 (mới, Proposed) | Sửa | API docs mặc định bật, tắt ở prod bằng `SPRINGDOC_API_DOCS_ENABLED`/`SPRINGDOC_SWAGGER_UI_ENABLED=false` → mặc định **tắt** (`${SPRINGDOC_ENABLED:false}`), dev bật bằng `SPRINGDOC_ENABLED=true` | architect-reviewer MAJOR: fail-open ở prod; user chọn đảo mặc định 2026-11-03 |
| 26 | API_Spec §4 · CCD §10 · `ErrorCode` | Thêm | v1.0: lỗi framework/ngoài dự kiến đều 500 `INTERNAL_ERROR` (hard-code, chưa có trong API_Spec) → domain `SYS`: `SYS_001` 404, `SYS_002` 405, `SYS_003` 415, `SYS_004` 4xx khác, `SYS_005` 5xx; 400 của framework → `VALIDATION_ERROR` (thiếu query param kèm `field`); mọi mã vào enum `ErrorCode` | architect-reviewer lượt 2 MAJOR: mã lệch format `<DOMAIN>_<NUMBER>`, 400 có 2 mã; user chọn 2026-11-04 |
| 27 | CCD §10 · `application.properties` | Sửa (bugfix) | Message lỗi SQL chứa giá trị dòng (vd số điện thoại) lọt vào log ở 2 tầng → (a) handler `DataIntegrityViolationException` khác chỉ log tên constraint + class lỗi gốc; (b) pgjdbc `logServerErrorDetail=false` — message không còn phần `Detail`, chặn cả log WARN của Hibernate (`org.hibernate.orm.jdbc.error`) | Reproduce: `GlobalExceptionHandlerTest` (handler) + `SqlErrorLoggingPiiTest` (đụng `idx_user_phone_unique` thật trên DB dev — log Hibernate chứa số điện thoại) |
| 28 | CCD §10 · PF §3 (2.6) | Thêm | — → ghi lệch có chủ đích: 2.6 đọc lại `ProfileView` (NFS §3) | architect-reviewer lượt 2 MAJOR; user chấp nhận 2026-11-04 |
| 29 | CCD §9.1 · ADR-0004 | Thêm | `/actuator/health/**` public (chưa nêu hành vi) → `/actuator/health` chỉ trả `UP`/`DOWN`, đường dẫn thành phần trả 404 (mặc định Spring Boot); `/**` dành cho probe liveness/readiness | architect-reviewer lượt 2 MINOR: chưa test; kiểm bằng `SecurityConfigIntegrationTest` |
| 30 | CCD §10 | Sửa | Nhánh `TEAM_003` trong `GlobalExceptionHandler` (chưa nêu vai trò) → ghi rõ là lớp phòng hờ, hiện không luồng nào chạy tới | architect-reviewer lượt 4 MINOR; user chọn giữ 2026-11-04 |

### Ảnh hưởng
- API contract: thêm bảng "Lỗi chung" `SYS_*` (`API_Spec.md` §4) — client nhận 404 `SYS_001` / 405 `SYS_002` / 415 `SYS_003` thay cho 500 khi gọi sai URL/method/Content-Type; mã 500 đổi `INTERNAL_ERROR` → `SYS_005`. Endpoint, request/response thành công, mã lỗi miền: **không đổi**.
- DB schema, nghiệp vụ (SRS): **không đổi**.
- Cấu hình: `application.properties` thêm `logServerErrorDetail=false` cho datasource (#27) và `SPRINGDOC_ENABLED`; `.env.example` có dòng `# SPRINGDOC_ENABLED=true` (comment, chỉ bỏ comment ở dev) (#25) — môi trường deploy **không** đặt biến này → Swagger/OpenAPI trả 404.
- Code: `GlobalExceptionHandler` + `ErrorCode` (#24, #26, #27) + `FrameworkErrorStatusTest`, `GlobalExceptionHandlerTest`, `ErrorCodeTest`, `SqlErrorLoggingPiiTest`; `SecurityConfigIntegrationTest` (API docs tắt mặc định; health `UP` không `components`, `/health/db` 404 — #29) + `ApiDocsEnabledIntegrationTest` (#25); các mục còn lại là tài liệu theo code đã test PASS. Full suite **156 tests, 0 fail** (2026-11-04).
- Theo dõi (nhánh `chore/` riêng): bổ sung domain `SYS` vào `api-convention.md` §6; endpoint public vào `security-backend.md` §1.3.
