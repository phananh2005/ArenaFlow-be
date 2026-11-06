# CHANGELOG — USER-001 User & Team Management

## [v3.5] — 2026-11-07

**Type:** MINOR (chỉ đổi tên class; không đổi endpoint, DTO, mã lỗi, luồng) · **Tóm tắt:** `OptimisticLockRetrier` → `ConcurrencyConflictRetrier` — từ v3.4 class thử lại cả deadlock, tên cũ chỉ nói optimistic lock.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | `Component_Class_Design.md` (cây package `support/`, Deps của 4 service, quy tắc method ghi, bảng lệch convention) | Sửa | `OptimisticLockRetrier` / `optimisticLockRetrier` → `ConcurrencyConflictRetrier` / `concurrencyConflictRetrier` | Tên khớp hành vi: thử lại xung đột đồng thời (version conflict, deadlock) |
| 2 | `Processing_Flow.md` (participant sequence §2, §5 bảng transaction + quy tắc retry) | Sửa | như #1 | Như #1 |
| 3 | Header `API_Spec.md`, `Processing_Flow.md`, `Component_Class_Design.md` | Ghi nhận | v3.4 → v3.5 | design-standards §10 |

### Ảnh hưởng
- Hành vi, contract, DB, nghiệp vụ, SRS: không đổi. Tên logger của dòng WARN deadlock đổi theo tên class; câu log giữ nguyên.
- Các entry CHANGELOG cũ giữ tên cũ (không sửa entry cũ).

## [v3.4] — 2026-11-06

**Type:** MINOR (không đổi endpoint, DTO hay mã lỗi; `409 TEAM_014` đã khai báo ở mọi endpoint dùng retrier) · **Tóm tắt:** `OptimisticLockRetrier` thử lại cả deadlock (`40P01`), ghi log `WARN` mỗi lần gặp; hết lượt → `409 TEAM_014` thay vì 500.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | `Processing_Flow.md` §5 (retrier) | Sửa | chỉ bắt `ObjectOptimisticLockingFailureException` → thêm `PessimisticLockingFailureException` có SQLState `40P01` (log `WARN` kèm SQLState + số lần thử); NOWAIT `55P03`, lock timeout, `40001` không retry | Bên bị PostgreSQL huỷ do deadlock trước đây trả 500; [Inference] thử lại thường thành công (bên kia đã xong) — kiểm chứng: `RetrierDeadlockIntegrationTest` |
| 2 | `API_Spec.md` §1 (quy ước chung) | Sửa | "tự retry" → "tự retry (cả khi PostgreSQL huỷ do deadlock)" | Như #1 |
| 3 | `API_Spec.md` §2.7 (Lỗi) | Sửa | `409 TEAM_014` "giữ khai báo… luồng khoá bi quan không còn phát sinh mã này" → phát sinh khi deadlock lặp lại đủ 3 lần thử | Như #1 — ghi chú cũ hết đúng |
| 4 | Header `API_Spec.md`, `Processing_Flow.md`, `Component_Class_Design.md` | Ghi nhận | v3.3 → v3.4 | design-standards §10 |
| 5 | `Processing_Flow.md` §2.3 (sequence chuyển quyền, endpoint 2.15) | Sửa | `alt ObjectOptimisticLockingFailureException` → `alt xung đột version / deadlock (xem §5)` | Khớp §5 |
| 6 | `API_Spec.md` §1 (quy ước chung) | Sửa | "Trừ `DELETE /profile`: khoá bi quan có phạm vi" → "khoá bi quan … nhưng vẫn chạy qua retrier" | Câu cũ dễ hiểu là 2.7 không retry/không có `TEAM_014`, mâu thuẫn §2.7 |

### Ảnh hưởng
- Hành vi: deadlock → tự thử lại; lặp lại đủ 3 lần → `409 TEAM_014` (trước: 500). Contract không đổi (mã đã khai báo).
- DB schema, nghiệp vụ, SRS: không đổi.
- Kiểm chứng: `OptimisticLockRetrierTest` (retry deadlock, TEAM_014 khi hết lượt, log WARN kèm `40P01`, NOWAIT/`40001` không retry), `RetrierDeadlockIntegrationTest` (deadlock thật trên PostgreSQL → `CannotAcquireLockException` SQLState `40P01`; retrier cho cả hai thao tác thành công).

## [v3.3] — 2026-11-06

**Type:** MINOR (chỉ đổi cách ghi phiên bản ở header; không đổi nội dung thiết kế) · **Tóm tắt:** Ô "Tác giả / Ngày / Phiên bản" của 3 tài liệu chỉ giữ baseline + phiên bản hiện tại + trỏ `CHANGELOG.md` (đồng bộ cách làm với USER-002 v1.4).

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | Header `API_Spec.md`, `Processing_Flow.md`, `Component_Class_Design.md` | Sửa | Liệt kê mọi mốc v1.0 → v3.2 kèm mô tả → baseline v1.0 + phiên bản hiện tại v3.3 + trỏ `CHANGELOG.md` | design-standards §8 (một thông tin = một nơi); §10 chỉ yêu cầu version header khớp entry mới nhất. Mọi mốc bị bỏ đã có entry v1.1–v3.2 trong file này |

### Ảnh hưởng
- API contract, DB schema, nghiệp vụ, SRS: không đổi.

## [v3.2] — 2026-11-06

**Type:** MINOR (chỉ cập nhật trạng thái ADR được trỏ tới; không đổi endpoint, DTO, mã lỗi, luồng) · **Tóm tắt:** ADR-0007 đã `Accepted` (PR #19) — bỏ nhãn "Proposed" khi trỏ tới nó.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | `API_Spec.md` §1 (quy ước chung) | Sửa | "khoá bi quan có phạm vi (ADR-0007, Proposed)" → "(ADR-0007)" | ADR-0007 `Accepted` |
| 2 | `Processing_Flow.md` §5 (ghi chú 2.7) | Sửa | "ngoại lệ của ADR-0003 do ADR-0007 (Proposed) đề xuất" → "ngoại lệ của ADR-0003 theo ADR-0007" | Như #1 |
| 3 | Header `API_Spec.md`, `Processing_Flow.md`, `Component_Class_Design.md` | Ghi nhận | v3.1 → v3.2 | design-standards §10 (version khớp header) |

### Ảnh hưởng
- API contract, DB schema, nghiệp vụ, SRS: không đổi. Entry cũ nhắc "Proposed" (ADR-0004 ở v1.1) giữ nguyên theo §10.

## [v3.1] — 2026-11-06

**Type:** MINOR (không đổi endpoint, shape DTO hay mã lỗi; id vẫn là chuỗi UUID) · **Tóm tắt:** Khoá chính sinh bằng UUID v7 (`@UuidGenerator(style = VERSION_7)`) thay UUID v1 (`TIME`) — ADR-0008.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | `API_Spec.md` §1 (quy ước chung) | Sửa | entity dùng `@UuidGenerator(style = TIME)` → `@UuidGenerator(style = VERSION_7)` (UUID v7); id đã có giữ dạng v1 | ADR-0008 (giới hạn thứ tự xem mục Hệ quả của ADR); khớp `docs/database/schema-design.md` |
| 2 | Header `API_Spec.md`, `Processing_Flow.md`, `Component_Class_Design.md` | Ghi nhận | v3.0 → v3.1 | design-standards §10 (version khớp header) |

### Ảnh hưởng
- API contract, DB schema (cột `uuid`, không `DEFAULT`), dữ liệu đã có, nghiệp vụ: không đổi. SRS: không ảnh hưởng (thuần kỹ thuật).
- Kiểm chứng: `EntityIdGenerationTest` (mọi `@Entity` dùng `VERSION_7`), `UserRepositoryTest#generatesTimeOrderedUuidVersion7Ids` (`version() == 7`).

## [v3.0] — 2026-11-06

**Type:** MAJOR (đổi shape response: `POST /invitations/{id}/reject` `200 + InvitationResponse` → `204` không body; bỏ header `Location` ở mọi `201`) · **Tóm tắt:** Áp quy tắc mã thành công 2xx theo kết quả (B1–B4 / N1, `api-convention.md` §5 — user chốt 2026-11-06) cho USER-001; chốt giữ `TEAM_014`.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | `API_Spec.md` §2 (bảng), §2.14 | Sửa | 2.14 `200` + `InvitationResponse` (`status = REJECTED`) → `204` không body | N1: kết quả suy ra từ việc request thành công; không có giá trị server sinh mà client cần (B3) |
| 2 | `API_Spec.md` §2.1, §2.3, §2.8, §2.12 | Sửa | `201` kèm header `Location` → `201` không `Location` (client lấy id từ body) | Quy tắc user chốt; `Location` trỏ tới URI không phải lúc nào cũng là tài nguyên vừa tạo (2.1/2.3 trỏ `/profile`, 2.12 trỏ `/invitations/{id}` không có GET) |
| 3 | `API_Spec.md` §2 (dưới bảng) | Thêm | — → ghi chú "Mã thành công theo `api-convention.md` §5; `201` không `Location`" | Nguồn quy tắc duy nhất |
| 4 | `Component_Class_Design.md` §3 (controller) | Sửa | 201 "kèm header `Location`" → `ResponseEntity.status(HttpStatus.CREATED).body(...)` | Như #2 |
| 5 | `Component_Class_Design.md` §4.4 | Sửa | `InvitationResponse rejectInvitation(...)` → `void rejectInvitation(...)` | Như #1 — service không dựng DTO không ai dùng (user chọn 2026-11-06) |
| 6 | `Processing_Flow.md` §2 (sequence 2.1), §3 (2.14) | Sửa | `201 + Location` → `201`; 2.14 "response dùng tên đội từ team đã nạp" → "204 không body" | Như #1, #2. Số truy vấn 2.14 giữ 3 (S, S, U) |
| 7 | `API_Spec.md` §2.7 (Lỗi) | Chốt `[cần xác nhận]` của v2.1 | Ghi chú `409 TEAM_014` trỏ "`CHANGELOG.md` v2.1" (còn "[cần xác nhận] xem xét bỏ ở lần MAJOR kế tiếp") → thêm "user chốt giữ — v3.0 #7"; **giữ** `409 TEAM_014` cho `DELETE /profile` | User chốt giữ 2026-11-06: mã vẫn dùng ở 9 thao tác ghi qua retrier (tính cả `disbandTeam` của USER-002; không tính `deleteAccount`) |
| 8 | Header `API_Spec.md`, `Processing_Flow.md`, `Component_Class_Design.md` | Ghi nhận | v2.1 → v3.0 | design-standards §10 |

### Ảnh hưởng
- **Client:** 2.14 không còn đọc `data` sau khi từ chối; 2.1/2.3/2.8/2.12 không dùng header `Location` (id có trong body).
- Endpoint, request, mã lỗi, DB schema, nghiệp vụ: không đổi. SRS: không ảnh hưởng (thuần kỹ thuật).
- Tham chiếu: quy tắc §5 mới của `api-convention.md` đến từ nhánh `chore/agent-framework-success-status-codes` (PR agent-framework "success status codes") — khớp khi nhánh đó merge.
- Kiểm chứng: `AuthControllerTest`, `TeamControllerTest` (`header().doesNotExist(LOCATION)`), `InvitationControllerTest` (204, body rỗng), `TeamMembershipServiceImplTest`, `NfsQueryCountTest` (2.14 = 3); full suite 212 PASS.

## [v2.1] — 2026-11-06

**Type:** MINOR (không đổi endpoint, shape DTO hay mã lỗi; đổi cơ chế xử lý đồng thời bên trong `DELETE /profile`) · **Tóm tắt:** Đồng bộ với USER-002 v1.2 — `deleteAccount` dùng khoá bi quan có phạm vi (ADR-0007): khoá membership ACTIVE/PENDING → kiểm captain → một bulk UPDATE → soft delete (5 → 3 truy vấn).

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | `Processing_Flow.md` §2.2 (sequence xoá tài khoản) | Sửa | `existsActiveCaptainByUserId` ×2 + `leaveAllActive` + `rejectAllPending` + `softDelete` → `findByUserIdAndStatusInOrderByIdAsc` (`PESSIMISTIC_WRITE`) + `leaveAndRejectAllOpen` + `softDelete` | ADR-0007; race delete vs accept của chính user tái hiện bằng test trên luồng cũ |
| 2 | `Processing_Flow.md` §3 (2.7), §5 | Sửa | "kiểm captain lần 2 (R1)", `S, U, S, U, U` 5/W6 → khoá trước rồi kiểm, `S (khoá), U, U` 3/W6; ghi chú ngoại lệ ADR-0003 | Như #1 |
| 3 | `Component_Class_Design.md` §5 (`TeamMemberRepository`) | Sửa | `leaveAllActive`, `rejectAllPending` → `findByUserIdAndStatusInOrderByIdAsc` (`@Lock(PESSIMISTIC_WRITE)`), `leaveAndRejectAllOpen` | Như #1 |
| 4 | `API_Spec.md` §1 (quy ước chung), §2.7 (Lỗi) | Thêm ghi chú | "Ghi lên team/team_member có optimistic lock + tự retry" → thêm "trừ `DELETE /profile` (ADR-0007)"; `409 TEAM_014` của 2.7 thêm ghi chú không còn phát sinh | Nhất quán với #1; contract không đổi |
| 5 | Header `API_Spec.md`, `Processing_Flow.md`, `Component_Class_Design.md` | Ghi nhận | v2.0 → v2.1 | design-standards §10 (version khớp header) |

### Ảnh hưởng
- API contract (`204`, `422 USER_003`, `409 TEAM_014`), DB schema, nghiệp vụ: không đổi. [Inference] `409 TEAM_014` của `DELETE /profile` **không phát sinh từ luồng hiện tại**: Q1 chỉ đọc entity (không sửa), Q2/Q3 là native bulk, `clearAutomatically` xoá persistence context — không bước nào để Hibernate kiểm `@Version`. Giữ khai báo để không đổi contract (bỏ mã lỗi = MAJOR theo design-standards §10); [cần xác nhận] xem xét bỏ ở lần MAJOR kế tiếp.
- Kiểm chứng: xem USER-002 `CHANGELOG.md` v1.2.

## [v2.0] — 2026-11-05

**Type:** MAJOR (phá vỡ hợp đồng: `DELETE /api/v1/profile` với captain duy nhất trước trả `204` nay trả `422 USER_003`; `USER_003` giữ mã nhưng đổi điều kiện + message; các endpoint ghi đội thêm mã lỗi `TEAM_016` — user chốt MAJOR 2026-11-05) · **Tóm tắt:** Đồng bộ USER-001 với USER-002 (giải tán đội, ADR-0005): bỏ nhánh "captain duy nhất xoá tài khoản → đội tự INACTIVE", thay thứ tự guard bằng USER-002 §2.1a, ghi chú repository/guard đổi.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | `API_Spec.md` §2.7, §4 (`USER_003`) | Sửa | Captain duy nhất → đội `INACTIVE`; `USER_003` khi còn thành viên khác → mọi captain ACTIVE (kể cả người duy nhất, đội INACTIVE) bị `USER_003`; message "…hoặc giải tán đội trước khi xóa tài khoản" | ADR-0005, USER-002 BR-T06/BC-T02 sửa |
| 2 | `Processing_Flow.md` §1 | Sửa | Liệt kê thứ tự guard "Bean Validation (400) → JWT (401) → tồn tại → quyền → trạng thái" → chỉ còn con trỏ tới USER-002 `API_Spec.md` §2.1a (bỏ liệt kê, tránh trùng nguồn) | Thứ tự cũ sai (filter Security trả 401 trước; trạng thái đội kiểm trước quyền); USER-002 §2.1a là nguồn duy nhất theo từng endpoint |
| 3 | `Processing_Flow.md` §2.2, §3 (2.7), §5 | Sửa | Sequence `findCaptainTeamsWithActiveCount` + `markInactiveByIds` → `existsActiveCaptainByUserId` ×2 + `leaveAllActive` (MEMBER) + `rejectAllPending` + `softDelete` (5 truy vấn); bỏ ghi chú "cập nhật thiếu dòng"; dòng race captain xoá tài khoản vs accept → luôn `USER_003` | Luồng USER-002 `Processing_Flow.md` §4.2 |
| 4 | `Component_Class_Design.md` §2, §4 (TeamGuard, transaction), §5 | Sửa | Ghi chú bỏ `CaptainTeamView`, `markInactiveByIds`, `findCaptainTeamsWithActiveCount`; `findTeam` private; `activeTeam` thêm `TEAM_016`; `requireTeam` chỉ cho thao tác đọc; `requireCaptain` → `TEAM_016` khi đội vừa giải tán; admin dùng `notDisbandedTeam`; `inviteMember` bỏ `@Transactional`, chạy qua retrier | Code USER-002 (`fb6de5f`) |
| 5 | `Processing_Flow.md` §3 (2.11–2.15, 2.18–2.20), §4 | Sửa | Thêm guard `TEAM_016` (và đọc lại trạng thái ở nhánh lỗi); 2.12 invite: 5 → 6 truy vấn (force increment `team.version` lúc commit), chuyển từ "ghi không retry" sang "ghi có retry" | USER-002 `Processing_Flow.md` §3, §5 |
| 6 | `API_Spec.md` §4 | Thêm | Dòng `TEAM_016` (trỏ tới USER-002) + ghi chú các endpoint ghi có thêm `TEAM_016`, 2.12 thêm `TEAM_014` | Đồng bộ bảng mã lỗi |

### Ảnh hưởng
- API contract: `DELETE /api/v1/profile` từ chối thêm trường hợp captain duy nhất (trước đây 204) — thay đổi nghiệp vụ đã chốt ở USER-002/ADR-0005; message `USER_003` đổi.
- DB schema: không đổi ở USER-001 (USER-002 thêm V4).
- SRS: SYSTEM-001 `Use_Cases.md` UC-U03 4b đã gạch bỏ ở USER-002.

## [v1.2] — 2026-11-05

**Type:** MINOR (cấu trúc mã nguồn, không đổi hợp đồng) · **Tóm tắt:** Tách repo thành Maven multi-module `common` / `domain` / `application` / `api` theo mẫu `vdbas_expenditure_service_be`, đổi base package `com.phananh.ArenaFlow` → `com.phananh.arenaflow` (ADR-0006). Không đổi endpoint, request/response, mã lỗi, DB schema hay nghiệp vụ.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | CCD §1, §2 | Sửa | Một module, package theo tầng → 4 module Maven, chiều phụ thuộc `api → application → domain → common` | ADR-0006 |
| 2 | CCD §4, §5, §9.2, §9.4 | Sửa | `service/support/TeamGuard`, `repository/projection/`, `security/*`, `config/WebMvcConfig` → `application/support/`, `domain/projection/`, `api/security/`, `api/config/` | Đường dẫn theo module mới |
| 3 | CCD §2 | Sửa | `JwtTokenService`, `JwtProperties` (`security/`, `config/`) → `application/support/security/`; `GlobalExceptionHandler` (`exception/`) → `api/config/`; `SkillLevel` → `common/enums/`; `OptimisticLockRetrier`, `PageRequestFactory` (`common/`) → `application/support/` | `AuthServiceImpl` gọi trực tiếp JWT service; handler HTTP thuộc tầng api; `SkillLevel` được cả entity converter (`domain`) lẫn validator (`application`) dùng → tầng thấp nhất `common`; retry/phân trang chỉ service dùng và cần Spring tx/data → `application` |
| 4 | CCD §2 | Sửa | `controller/` (5 controller chung một package) → `api/{auth,profile,team}/` theo nhóm chức năng | Theo mẫu `vdbas_expenditure_service_be` (`api/<chức-năng>/`) |
| 5 | CCD §2 | Sửa | `exception/{ErrorCode, BusinessException, ErrorEnvelope, ErrorResponse, ValidationError}` → `common/exception/`; `dto/`, `mapper/`, `validation/` → `application/` | Service và security handler đều dùng mã lỗi → `common`; DTO/mapper/validation là hợp đồng của service |
| 6 | CCD header, §11, §12 | Sửa | `com.phananh.ArenaFlow.entity.*` → `com.phananh.arenaflow.domain.entity.*`; §11 thêm ghi chú dependency theo module POM; §12 base package mới | Đồng bộ với code |
| 7 | `API_Spec.md`, `Processing_Flow.md` header | Sửa | v1.1 → v1.2 | Đồng bộ phiên bản theo `design-standards.md` §10; nội dung không đổi |

### Ảnh hưởng
- API contract, DB schema, nghiệp vụ (SRS): **không đổi**.
- Code: 161 file Java + 4 resource (`application.properties`, `db/migration/V1..V3`) `git mv` sang module (resource → `api/src/main/resources/`); `ApiIntegrationTest` → `public` (controller test tách package). Full suite **163 tests, 0 fail** (2026-11-05), số test từng lớp khớp trước khi tách.

## [v1.1] — 2026-11-04

**Type:** MINOR (mã 500 đổi `INTERNAL_ERROR` → `SYS_005` không tính là "đổi mã lỗi" vì v1.0 chưa công bố mã này trong `API_Spec.md` — chỉ hard-code trong handler) · **Tóm tắt:** Đồng bộ thiết kế với code sau implement (19 điểm lệch + bổ sung khi review), bugfix lỗi framework (4xx bị trả 500, PII trong log) kèm mã lỗi `SYS_*`, API docs mặc định tắt (ADR-0004), bugfix header Bearer chặn endpoint auth (#31), ghi rủi ro mạo danh của mock (#32), `avatarUrl` chỉ http/https (#34), chuẩn hoá email OAuth (#35), controller thống nhất `ResponseEntity` (#37), gộp logic trùng ở admin service và kiểm secret (#38), `field` lỗi validation tham số đúng tên client gửi (#39); không đổi endpoint, request/response thành công, mã lỗi miền hay DB schema.

> Entry v1.1 còn mở đến khi PR #8 merge: mục phát sinh khi review trong PR này gộp vào đây. Sau merge, mọi thay đổi → entry mới (design-standards §10).

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
| 31 | CCD §9.1 · API_Spec §2 · `SecurityConfig` | Sửa (bugfix) | `/api/v1/auth/**` permitAll nhưng header Bearer hết hạn/sai vẫn bị xác thực → 401 → `BearerTokenResolver` bỏ qua header trên `/api/v1/auth/**` | code-reviewer PR #8 M1; reproduce `AuthControllerTest.publicAuthEndpointIgnoresInvalidBearerHeader` (refresh kèm `Bearer garbage` → 401) |
| 32 | ADR-0001 (Sửa đổi) | Thêm | Mock OAuth/OTP: rủi ro mạo danh (kể cả ADMIN) chưa ghi → ghi rõ + **NEVER** deploy build còn mock ra môi trường truy cập từ ngoài | code-reviewer PR #8 M3; user chọn ghi rủi ro (phương án b) 2026-11-04 |
| 33 | API_Spec §1, §2 · CCD §9.1 · ADR-0001 (Sửa đổi) | Sửa | Làm rõ sau #31/#32: hợp đồng bỏ qua Bearer ghi ở API_Spec; CCD thêm phạm vi resolver (chỉ `/auth/**`), NEVER đặt endpoint cần xác thực dưới `/auth/**`, mức phủ test; API_Spec trỏ lệnh cấm deploy; ADR-0001: hai lối ra trước deploy ngoài đều cần ADR mới, trạng thái "chờ PO xác nhận" | architect-reviewer lượt 5 (phần sửa PR #8): M1, m1, m2, m4–m8; user chọn 2026-11-04 |
| 34 | API_Spec §3.5–3.7 · `ProfileUpdateRequest`, `CreateTeamRequest`, `UpdateTeamRequest` | Sửa (bảo mật) | `avatarUrl` `@URL` nhận mọi giao thức (`file:`, `ftp:`, `javascript:`) → `@URL(regexp = "^https?://.*")` (scheme chữ thường) | code-reviewer PR #8 MINOR #3; reproduce `RequestDtoValidationTest.avatarUrlAcceptsOnlyHttpAndHttps` (`file:///etc/passwd` hợp lệ); user duyệt 2026-11-04 |
| 35 | API_Spec 2.1 · SRS BR-U02 · `AuthServiceImpl` | Sửa (nghiệp vụ) | `email` dùng nguyên văn làm `provider_user_id` → chữ thường trước khi tra/lưu (`trim` trong service là lớp phòng hờ — qua API, `@Email` đã chặn khoảng trắng) | code-reviewer PR #8 MINOR #4: khác hoa/thường tạo tài khoản trùng; reproduce `AuthServiceImplTest.oauthLoginNormalizesEmailBeforeLookupAndRegistration`; dữ liệu dev 0 dòng google/facebook → không migration; user duyệt 2026-11-04 |
| 36 | PF §3 (2.11, 2.14) · `NfsQueryCountTest` | Sửa | 2.11 "S, U, S = 3" → S, S, U, S = 4; 2.14 "S, S, U, S = 4" → S, S, U = 3 (đo thật; test siết về số chính xác) | code-reviewer PR #8 MINOR #6; user duyệt 2026-11-04 |
| 37 | CCD §3 · 5 controller | Sửa (refactor) | Handler 200 trả `ApiResponse`/`PageResponse` trực tiếp, 201/204 dùng `ResponseEntity` → mọi handler trả `ResponseEntity<…>`; response không đổi | code-reviewer PR #8 MINOR #5 (`convention-backend.md`); controller test hiện có PASS nguyên; user duyệt 2026-11-04 |
| 38 | CCD §4, §4.5, §4.6, §9.3 · `NfsQueryCountTest` · `TeamGuard`, `AdminTeamServiceImpl`, `JwtTokenService` | Sửa (refactor) | `AdminTeamServiceImpl#findTeam`/`#detail` trùng guard và `TeamServiceImpl#getTeam` → `TeamGuard#findTeam` (mới) + `TeamService#getTeam`; kiểm secret ≥ 32 byte lặp → hằng `JwtProperties.MIN_SECRET_BYTES`; §4.6 ghi method đọc gọi lồng tham gia transaction ghi; test số truy vấn 2.19 = 3, 2.20 = 4 siết về số chính xác | code-reviewer PR #8 MINOR #8, tech-debt #2; Reuse Report 2026-11-04; `TeamGuardTest` (Red: thiếu `findTeam`); user duyệt 2026-11-04 |
| 39 | CCD §10 · API_Spec §4 (`VALIDATION_ERROR`) · `GlobalExceptionHandler` | Sửa (bugfix) | Query param sai (`page=0`) → `field = "listTeams.page"` (lộ tên method); nhánh `HandlerMethodValidationException` (lớp phòng hờ — controller có `@Validated` nên client chưa từng nhận) → `field = "request"` → `field` = tên tham số (`page`) | code-reviewer PR #8 MINOR #2; reproduce `TeamControllerTest.listTeamsPaginationAndQueryValidation` (was `listTeams.page`) + `GlobalExceptionHandlerTest.methodParameterValidationReportsParameterNameAsField` (was `request`); user duyệt 2026-11-04 |

### Ảnh hưởng
- API contract: thêm bảng "Lỗi chung" `SYS_*` (`API_Spec.md` §4) — client nhận 404 `SYS_001` / 405 `SYS_002` / 415 `SYS_003` thay cho 500 khi gọi sai URL/method/Content-Type; mã 500 đổi `INTERNAL_ERROR` → `SYS_005`. #31: endpoint Public `/api/v1/auth/**` không còn trả 401 khi kèm Bearer sai/hết hạn (`API_Spec.md` §2). #34: `avatarUrl` không phải http/https → `400 VALIDATION_ERROR`. #35: email OAuth khác hoa/thường → cùng tài khoản (khoảng trắng đầu/cuối vẫn 400 do `@Email`). #39: `validationErrors[].field` của query param sai = tên tham số (`page`), trước là `listTeams.page`. Endpoint, request/response thành công, mã lỗi miền: **không đổi**.
- DB schema: **không đổi**. Nghiệp vụ (SRS): BR-U02 thêm chuẩn hoá email (#35).
- Cấu hình: `application.properties` thêm `logServerErrorDetail=false` cho datasource (#27) và `SPRINGDOC_ENABLED`; `.env.example` có dòng `# SPRINGDOC_ENABLED=true` (comment, chỉ bỏ comment ở dev) (#25) — môi trường deploy **không** đặt biến này → Swagger/OpenAPI trả 404.
- Code: `SecurityConfig` (#31) + `AuthControllerTest`; 3 request DTO (#34) + `RequestDtoValidationTest`; `AuthServiceImpl` (#35) + `AuthServiceImplTest`; `NfsQueryCountTest` (#36); 5 controller (#37); `TeamGuard` + `TeamGuardTest`, `AdminTeamServiceImpl` + `AdminTeamServiceImplTest`, `JwtProperties`/`JwtTokenService` (#38); `GlobalExceptionHandler` + `TeamControllerTest`, `GlobalExceptionHandlerTest` (#39); `GlobalExceptionHandler` + `ErrorCode` (#24, #26, #27) + `FrameworkErrorStatusTest`, `GlobalExceptionHandlerTest`, `ErrorCodeTest`, `SqlErrorLoggingPiiTest`; `SecurityConfigIntegrationTest` (API docs tắt mặc định; health `UP` không `components`, `/health/db` 404 — #29) + `ApiDocsEnabledIntegrationTest` (#25); các mục còn lại là tài liệu theo code đã test PASS. Full suite **163 tests, 0 fail** (2026-11-04).
- Theo dõi: domain `SYS` (`api-convention.md` §6) + endpoint public health/API docs (`security-backend.md` §1.3/§2.1) + quy tắc "endpoint public bỏ qua Bearer" (`security-backend.md` §2.1, từ #31) — PR #9, merge sau PR này. Race condition trạng thái đội (code-review M2) — task riêng sau merge.
