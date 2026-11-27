---
task: USER-003 — tra cứu cho người dùng (danh mục tỉnh/phường, tìm người để mời, lời mời của tôi, đội của tôi) · migration V12
created: 2026-11-21
status: in_progress
---

## Mô tả
Triển khai USER-003 theo thiết kế đã duyệt (v2.0): 5 endpoint **chỉ đọc**
- 2.1 `GET /api/v1/provinces` · 2.2 `GET /api/v1/provinces/{provinceId}/wards` (danh mục, không phân trang)
- 2.3 `GET /api/v1/teams/{teamId}/invitation-candidates?q=` (tìm theo SĐT / tên khớp đầu từ, tối đa 20 + `hasMore`, giới hạn 30 lần / 60 giây mỗi user → `429 USER_007` + `Retry-After`)
- 2.4 `GET /api/v1/profile/invitations` · 2.5 `GET /api/v1/profile/teams?role=CAPTAIN` (phân trang offset)
- Migration `V12__user_name_search.sql`: hàm `user_name_fold`, `user_name_search_vector` (`IMMUTABLE`) + index GIN `idx_user_name_search`.

Bối cảnh (user chốt 2026-11-21): code **song song** 7 mã, mỗi mã một worktree/nhánh từ `origin/main`; PR USER-003 merge **sau cùng** (V12 áp sau V6–V11). Test tích hợp + test chéo làm ở **giai đoạn tích hợp**. Nhánh: `feature/USER-003-user-discovery-impl` (nhánh thiết kế `feature/USER-003-user-discovery` đã merge — không dùng, không xoá).

## Thiết kế liên quan
- `docs/agent/design/USER-003/` — `API_Spec.md`, `Component_Class_Design.md` (CCD), `Processing_Flow.md` (PF), `CHANGELOG.md` (v2.0).
- `docs/agent/business-spec/USER-003/` — UC-U04, UC-U05, UC-T07, UC-T08; BR-U07..U17, BR-T15..T21; AC-U04-*, AC-U05-*, AC-T07-*, AC-T08-*.
- ADR-0016 (tìm tên: fold + tsvector `simple` + GIN, khớp đầu từ — trạng thái `Proposed`); ADR-0017 + `convention-backend.md` §6.2 (giờ VN, `LocalDateTime`); ADR-0006 (multi-module).
- `docs/agent/non-functional-standards.md` (N_DETAIL=3, N_LIST=4; ngoại lệ phân trang §2 đã ghi), `api-convention.md` §5/§6/§7 (đã có dòng USER-003, `429`), `security-backend.md` §7 (mẫu rate limit trong bộ nhớ).

## Definition of Done
- [ ] 5 endpoint đúng `API_Spec.md` (shape JSON, sắp xếp, mã lỗi, thứ tự kiểm lỗi); không lộ số điện thoại ở 2.3.
- [ ] `V12__user_name_search.sql` đúng CCD §6; áp được trên DB sau V11 (giai đoạn tích hợp).
- [ ] Giới hạn 2.3 (30 lần / 60 giây trượt, theo `userId`, ngoài transaction, trước service) + `429 USER_007` + `Retry-After`.
- [ ] Số truy vấn ≤ ngưỡng, đo bằng `NfsQueryCountTest` (2.1=1, 2.2=1, 2.3 SĐT=3, 2.3 tên=4, 2.4/2.5=3|2, `USER_007`=0, `USER_006`=1).
- [ ] Unit test thuần PASS ở giai đoạn 2 (dán output); integration test viết xong, **PASS ở giai đoạn tích hợp** (dán output).
- [ ] `[cần xác nhận #2]` (EXPLAIN dùng `idx_user_name_search`) và `#9` (timeout 2 giây qua kết nối DB dev) kiểm ở integration — không đạt → dừng, báo trước merge.
- [ ] `code-reviewer` không còn CRITICAL/MAJOR; đối chiếu design + cập nhật theo bảng "Lệch thiết kế" + `architect-reviewer`.
- [ ] `docs/database/data-model.md`, `schema.dbml`, `schema-design.md` thêm 2 hàm + index V12 (sau test PASS).

## Các bước thực hiện (tổng quan)
- [x] 1. Đối chiếu & phân tích ảnh hưởng (spec / convention / kiến trúc / mã chạy song song)
- [x] 2. Lập kế hoạch chi tiết — file cần sửa (mục dưới) — **chờ user duyệt**
- [x] 3. Triển khai code + test theo TDD (Phase 1–6; unit test chạy ngay, integration test chỉ viết) — xong 2026-11-21
- [x] 4. Chạy unit test & xác nhận PASS (dán output) — 116/116 unit PASS (xem `progress.md`); integration CHƯA chạy
- [ ] 5. `code-reviewer` trên phần chưa review → sửa CRITICAL/MAJOR, test lại
- [ ] 6. Giai đoạn tích hợp: rebase lên `main` đã có V6–V11 → chạy integration test (Phase 7) → PASS
- [ ] 7. Đối chiếu code với design → cập nhật design theo bảng "Lệch thiết kế" → `architect-reviewer` review phần sửa
- [ ] 8. Bước 6–7 có sửa code → test lại + `code-reviewer` phần sửa → commit (`code-reviewer` là bước cuối trước commit có code)

## Lệch thiết kế
_Ghi ngay khi code khác design; KHÔNG sửa design giữa chừng — cập nhật một lần ở bước cuối (`CLAUDE.md` §8). Trống = code khớp design._

| # | Phase | Design | Code thực tế | Lý do | Sửa ở |
|---|---|---|---|---|---|
| 1 | 4 | CCD §7.2: `invitedAt` qua `MapperSupport.toUtc` | Gán thẳng `LocalDateTime` → `LocalDateTime` (như `joinedAt`) | CCD §5.4/§7.1 v2.0 đã đổi kiểu sang `LocalDateTime`; `toUtc(Instant)` không nhận `LocalDateTime` và SYSTEM-002 CCD §6.2 xoá `toUtc` — §7.2 còn sót | CCD §7.2 |
| 2 | — | CCD header, §6, §9 #1; ADR-0016 "Hệ quả": "code USER-003 đợi code V5–V11" | Code song song từ `origin/main` (V1–V5), merge sau cùng | User chốt 2026-11-21 | CCD header/§6/§9 #1 (ADR-0016 nếu cần) |

## Tái sử dụng
_Reuse Report (skill `reuse-scan`, 2026-11-21). Nguồn quét: `*/src/main/java` của 4 module `common`, `domain`, `application`, `api` + `api/src/main/resources/db/migration` + `*/src/test/java` (helper test). Công cụ: Grep/ripgrep trên source hiện tại của worktree (`origin/main` `6fe6285`), đọc trực tiếp file ứng viên. Vùng mù: class của 6 mã đang code song song (SYSTEM-002, MATCH-001, MATCH-002, REPUTATION-001, MERCENARY-001, NOTIFICATION-001) — chưa có trên `main`, chỉ đối chiếu qua tài liệu thiết kế của chúng (grep `docs/agent/design/*/Component_Class_Design.md`: không mã nào khai cùng tên class/DTO/method/mã lỗi với USER-003)._

Từ khoá: `RateLimit`, `Retry-After`, `TOO_MANY`, `nanoTime`, `ConcurrentHashMap`, `ArrayDeque`, `phone`, `Phone`, `normalize`, `fold`, `unaccent`, `tsquery`, `starts_with`, `Filter` (enum), `existsByIdAndDeletedAtIsNull`, `findByIdAndDeletedAtIsNull`, `WardView`, `WardResponse`, `Province`, `MapperSupport`, `maskName`, `skillLevels`, `toUtc`, `PageRequestFactory`, `PageResponse.from`, `ApiResponse.of`, `TeamGuard`, `findSummaries`, `projection(` (test helper), `SpelAwareProxyProjectionFactory`.

| Thành phần | Kết luận | Nguồn / ghi chú |
|---|---|---|
| Guard đội (2.3) | **DÙNG LẠI** | `application/support/TeamGuard#activeTeam`, `#requireCaptain` (gồm `disbandedOr` → `TEAM_016`) |
| Phân trang 2.4, 2.5 | **DÙNG LẠI** | `application/support/PageRequestFactory#create`, `dto/response/PageResponse#from(page, mapper, page, pageSize)` |
| Envelope 2.1–2.3 | **DÙNG LẠI** | `dto/response/ApiResponse#of` |
| Che tên đội trưởng đã xoá, nhãn skill | **DÙNG LẠI** | `mapper/MapperSupport#maskName(name, deleted)`, `#skillLevels(json)` (→ `common/DeletedUserMasking`) |
| Envelope lỗi 429 | **DÙNG LẠI** | `common/exception/ErrorResponse#from(errorCode, details, path)`, `ErrorEnvelope` — không cần "bây giờ" mới (không phụ thuộc `AppTime`) |
| Lỗi 400 cho `q`, `role`, `page` | **DÙNG LẠI** | `GlobalExceptionHandler` (`ConstraintViolation`, `HandlerMethodValidation`, `MissingServletRequestParameter`, `MethodArgumentTypeMismatch`); timeout → handler `Exception` → `500 SYS_005` |
| Kiểm người gọi còn hoạt động (2.4, 2.5) | **MỞ RỘNG** `UserRepository` | Có `findByIdAndDeletedAtIsNull` (nạp entity); thêm derived `existsByIdAndDeletedAtIsNull` theo CCD §5.2 — chỉ cần boolean |
| Truy vấn mới | **MỞ RỘNG** | `AdministrativeUnitRepository` (+`findProvinces`, `findWardsOfProvince`), `UserRepository` (+`foldName`, `searchCandidatesByPhone`, `searchCandidatesByName`), `TeamMemberRepository` (+`findMyPendingInvitations`, `findMyTeams`) — mẫu native + projection theo `TeamRepository#findSummaries`. **Không** dùng `findByUserIdAndStatusInOrderByIdAsc` (có `PESSIMISTIC_WRITE`) |
| Mapper | **MỞ RỘNG** `MembershipMapper` (+`toCandidate`, `toMyInvitation`, `toMyInvitationTeam`), `TeamMapper` (+`toMyTeam`); **VIẾT MỚI** `AdministrativeUnitMapper` | Không có mapper cho `administrative_unit` (ward map trong `ProfileMapper`/`VenueMapper` theo `WardView`) |
| Projection | **VIẾT MỚI** 5 (`ProvinceView`, `ProvinceWardView`, `InvitationCandidateView`, `MyInvitationView`, `MyTeamView`) | `WardView` (4 getter, tỉnh+phường bắt buộc) khác ngữ nghĩa `ProvinceWardView` (`wardId` null = tỉnh chưa có phường); `TeamSummaryView` có `Instant createdAt` + field admin, không có `role`/`joinedAt`/`invitationId` |
| DTO response | **VIẾT MỚI** 7 | `WardResponse` (4 field, cho hồ sơ) giữ nguyên; tránh `TeamBriefResponse` (REPUTATION-001 giữ tên) |
| Phân loại + chuẩn hoá `q` (`InvitationSearchQuery`) | **VIẾT MỚI** | Không có util chuẩn hoá SĐT: chỉ `@Pattern("^0\\d{9}$")` ở `SendOtpRequest`/`VerifyOtpRequest`; không có fold/tsquery trong Java (bảng dấu chỉ ở hàm SQL V12 theo ADR-0016) |
| Rate limiter (`InvitationSearchRateLimiter`) | **VIẾT MỚI** | Không có limiter trong repo; `support/storage/AssetAdminRateLimitedException` là lỗi hạn mức **Cloudinary** (chuỗi `resetAt`, `RuntimeException`) — khác mục đích, không mở rộng. Không thêm dependency (Bucket4j/Caffeine bị loại ở CCD §4.5) |
| `RateLimitExceededException` | **VIẾT MỚI** (`extends BusinessException`) | `BusinessException` chỉ có `errorCode`, `details` — thêm `retryAfterSeconds` ở lớp con, không sửa lớp cha |
| `MyTeamRoleFilter` | **VIẾT MỚI** | Mẫu enum lọc `TeamStatusFilter`/`MemberHistoryStatusFilter` — giá trị khác (`CAPTAIN`, null = mọi vai trò) |
| `AdministrativeUnitService(Impl)`, `AdministrativeUnitController`, `ProfileTeamController` | **VIẾT MỚI** | Không có controller `/provinces`; `ProfileController` giữ hồ sơ (CCD §3 tách controller riêng — `ProfileMercenaryController` của MERCENARY-001 chưa có trên `main`, không phụ thuộc) |
| Helper test projection | **DÙNG LẠI** (mẫu) | `SpelAwareProxyProjectionFactory#createProjection` như `MapperTest#projection` |
| Base integration test | **DÙNG LẠI** | `api/.../api/ApiIntegrationTest` (`saveUser`, `saveTeam`, `as(user, …)`, rollback) |

## Kế hoạch chi tiết

### Đánh giá số truy vấn (đọc `non-functional-standards.md` trước khi lập) — đạt ngưỡng, không dừng
| API | Truy vấn | Số | Ngưỡng |
|---|---|---:|---:|
| 2.1 | `findProvinces` | 1 | 4 ✅ |
| 2.2 | `findWardsOfProvince` (0 dòng → `USER_004`; 1 dòng `wardId` null → `[]`) | 1 | 4 ✅ |
| 2.3 SĐT | đội → đội trưởng → tìm SĐT | 3 | 4 ✅ |
| 2.3 tên | `foldName` → đội → đội trưởng → tìm tên | 4 | 4 ✅ (bằng ngưỡng) |
| 2.3 lỗi | `USER_007`=0, `USER_005`=0, `USER_006`=1, `TEAM_00x` ≤ 4 | ≤ 4 | ✅ |
| 2.4 / 2.5 | exists user → trang → count (bỏ count khi trang 1 chưa đầy) | 3 / 2 | 4 ✅ |
Không truy vấn trong vòng lặp; đội trưởng / số thành viên / nhãn trong cùng câu. Ngoại lệ không phân trang 2.1–2.3 đã ghi ở NFS §2 + `api-convention.md` §7.

### Quy ước thực hiện
- TDD từng phase: test viết trước → chạy xác nhận **fail đúng lý do** (unit) → code tối thiểu → refactor → chạy lại.
- **Unit test thuần** chạy ngay: `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -pl <module> -am -Dtest=<Test> -Dsurefire.failIfNoSpecifiedTests=false test`. **NEVER** chạy `./mvnw test` toàn bộ / test có Spring context trên nhánh này trước giai đoạn tích hợp (xem Blockers B1).
- **Integration test** (Spring context / DB / migration / NFS): viết ở phase tương ứng, **chỉ CHẠY ở giai đoạn tích hợp** (mục "Test để giai đoạn tích hợp").
- Giảm xung đột với SYSTEM-002 và các mã khác: test unit mới đặt ở **file mới** (không sửa `MapperTest`, `TeamMembershipServiceImplTest`, `TeamServiceImplTest`); service test dùng `@InjectMocks` (Mockito truyền `null` cho tham số constructor không mock — chịu được việc SYSTEM-002 thêm `Clock`) + `@Spy` mapper `Mappers.getMapper(...)`. Thêm hằng/method vào file dùng chung ở **cuối khối** liên quan.
- Code mới theo `convention-backend.md` §6.2: projection/DTO dùng `LocalDateTime`; không `OffsetDateTime`/`Instant`; USER-003 không cần "bây giờ" (rate limiter dùng `System.nanoTime` qua `LongSupplier`). Comment tiếng Việt, không trỏ tài liệu (§2.4).

### Phase 1 — Mã lỗi, exception 429, handler (`common`, `api`)
Test viết trước:
- `api/src/test/.../api/config/GlobalExceptionHandlerTest` (sửa — standalone MockMvc, **unit**): endpoint thử ném `RateLimitExceededException(USER_007, 7)` → `429`, `$.error.code = USER_007`, message đúng, `details` null, header `Retry-After: 7`; log `WARN` có mã + path, không có userId.
- `common/src/test/.../common/exception/RateLimitExceededExceptionTest` (mới, unit): giữ `errorCode`, `retryAfterSeconds`; là `BusinessException`.
Code:
- `common/src/main/.../common/exception/ErrorCode.java` (sửa): `USER_004`..`USER_007` (CCD §7.3) — chèn sau `USER_003`.
- `common/src/main/.../common/exception/RateLimitExceededException.java` (mới): `extends BusinessException`, `long retryAfterSeconds`.
- `api/src/main/.../api/config/GlobalExceptionHandler.java` (sửa): `@ExceptionHandler(RateLimitExceededException.class)` → envelope như `BusinessException` + `Retry-After`.

### Phase 2 — Lớp thuần `InvitationSearchQuery`, `InvitationSearchRateLimiter` (`application/support`)
Test viết trước (unit, `application/src/test/.../application/support/`):
- `InvitationSearchQueryTest` (mới): phân loại + chuẩn hoá SĐT (`0987654321`, `+84 987 654 321`, `84987654321`, `0987.654.321`, `0987-654-321` → `0987654321`; `+84 12345`, `09+87654321`, `--`, `123` → `USER_005`; `Nguyen`, `!!` → `Name`); `ofFolded` (`""`, `"a"` → `USER_006`; `"a b"` → `a:* & b:*` / `a b`; token ký tự lạ bị bỏ; `nguyen an` → `nguyen:* & an:*`).
- `InvitationSearchRateLimiterTest` (mới, đồng hồ giả qua constructor package-private): 30 lần OK, lần 31 → `USER_007`, `retryAfterSeconds` = 50 (lần đầu t=0, lần 31 t=10s); lần bị từ chối không ghi mốc / không lùi mốc mở lại; t = 60s + ε → OK (cửa sổ trượt); 2 user độc lập; dọn lười (user im > 2×60s bị xoá khỏi map — kiểm qua accessor package-private kích thước map); 100 luồng cùng user → đúng 30 lần chấp nhận; `retryAfterSeconds` ≥ 1.
Code:
- `application/src/main/.../application/support/InvitationSearchQuery.java` (mới, sealed interface + 3 record, CCD §4.4).
- `application/src/main/.../application/support/InvitationSearchRateLimiter.java` (mới, `@Component`, CCD §4.5).

### Phase 3 — Migration V12, projection, repository (`api` resource, `domain`)
Test viết trước (**integration**, chạy ở giai đoạn tích hợp — `api/src/test/...`):
- `migration/UserNameSearchMigrationTest` (mới): 2 hàm tồn tại + `IMMUTABLE` (`pg_proc.provolatile = 'i'`); index `idx_user_name_search` tồn tại, partial `deleted_at IS NULL`; `user_name_fold` cho `"ĐẶNG  Thị-Ổi!!"` → `dang thi oi`, chữ hoa, NFD, `.`/`-`/nhiều khoảng trắng.
- `domain/repository/AdministrativeUnitRepositoryTest` (mới): thứ tự ICU (AC-U04-01), phường đúng tỉnh (AC-U04-02), id phường / không tồn tại → 0 dòng, tỉnh không phường → 1 dòng `wardId` null.
- `domain/repository/UserRepositoryTest` (sửa): `foldName`; `searchCandidatesByName/ByPhone` — AC-U05-03/03b/03c/03d/03e/05/07, loại người đã xoá / chính mình / ADMIN, nhãn theo đúng đội; `existsByIdAndDeletedAtIsNull`; `EXPLAIN` (tham số bind) dùng `idx_user_name_search` `[cần xác nhận #2]`.
- `domain/repository/TeamMemberRepositoryTest` (mới): `findMyPendingInvitations` (chỉ PENDING của mình, loại DISBANDED, giữ INACTIVE, đội trưởng hiện tại, sắp `invited_at DESC, id DESC`, count); `findMyTeams` (lọc CAPTAIN/null, sắp CAPTAIN → `joined_at DESC` → `t.id DESC`, count).
Code:
- `api/src/main/resources/db/migration/V12__user_name_search.sql` (mới, đúng CCD §6).
- `domain/src/main/.../domain/projection/` (mới): `ProvinceView`, `ProvinceWardView`, `InvitationCandidateView`, `MyInvitationView`, `MyTeamView` (thời gian `LocalDateTime`).
- `domain/src/main/.../domain/repository/AdministrativeUnitRepository.java` (sửa, +2), `UserRepository.java` (sửa, +4; `searchCandidatesByName` có `@QueryHints(jakarta.persistence.query.timeout = 2000)`), `TeamMemberRepository.java` (sửa, +2, `countQuery` tường minh).

### Phase 4 — DTO + mapper (`application`)
Test viết trước (unit):
- `application/src/test/.../application/mapper/AdministrativeUnitMapperTest` (mới): `ProvinceView → ProvinceResponse`, `ProvinceWardView → WardSummaryResponse`.
- `application/src/test/.../application/mapper/UserDiscoveryMapperTest` (mới): `toCandidate` (không che), `toMyInvitation` (team lồng, `captainName` che khi `captainDeleted`, null khi không có đội trưởng, `skillLevels` từ JSON, `invitedAt` giữ nguyên `LocalDateTime`), `toMyTeam` (che tên, `role`, `joinedAt`).
- `application/src/test/.../application/dto/response/UserDiscoveryResponseJsonTest` (mới): thứ tự field JSON khớp `API_Spec.md` §4.2; `LocalDateTime` ghi không offset; `InvitationCandidateResponse` không có field số điện thoại.
Code:
- `application/src/main/.../application/dto/response/` (mới): `ProvinceResponse`, `WardSummaryResponse`, `InvitationCandidateResponse`, `InvitationCandidatesResponse`, `MyInvitationTeamResponse`, `MyInvitationResponse`, `MyTeamResponse`.
- `application/src/main/.../application/mapper/AdministrativeUnitMapper.java` (mới); `MembershipMapper.java` (sửa, +3); `TeamMapper.java` (sửa, +1).

### Phase 5 — Service (`application`)
Test viết trước (unit, Mockito):
- `application/src/test/.../application/service/AdministrativeUnitServiceImplTest` (mới): `listProvinces` map/rỗng; `listWards` 0 dòng → `USER_004`, 1 dòng `wardId` null → `[]`, nhiều dòng → map.
- `application/src/test/.../application/service/TeamMembershipServiceImplSearchTest` (mới): thứ tự `parse` → (`foldName` → `ofFolded`) → `activeTeam` → `requireCaptain` → tìm; `USER_005` không gọi repository nào; `USER_006` chỉ gọi `foldName`; lỗi `TEAM_*` không gọi tìm; `LIMIT 21` truyền xuống; 21 dòng → 20 + `hasMore = true`; 20 dòng → `hasMore = false`; `listMyInvitations` (`USER_001`, pageable, map).
- `application/src/test/.../application/service/TeamServiceImplMyTeamsTest` (mới): `listMyTeams` `USER_001`; `role` null → `null`, `CAPTAIN` → `"CAPTAIN"`; map trang.
Code:
- `application/src/main/.../application/service/AdministrativeUnitService.java` + `impl/AdministrativeUnitServiceImpl.java` (mới, `@Transactional(readOnly = true)`).
- `application/src/main/.../application/service/MyTeamRoleFilter.java` (mới, `{ CAPTAIN }`).
- `TeamMembershipService.java` (+2) / `impl/TeamMembershipServiceImpl.java` (+2 method, hằng `MAX_CANDIDATES = 20`; không đổi constructor); `TeamService.java` (+1) / `impl/TeamServiceImpl.java` (+1, không đổi constructor). Không log `q`.

### Phase 6 — Controller (`api`)
Test viết trước:
- `api/src/test/.../api/team/TeamControllerInvitationSearchTest` (mới, **unit** thuần Mockito, gọi thẳng method controller): `acquire(callerId)` chạy **trước** service (`InOrder`); limiter ném `USER_007` → service không được gọi.
- **Integration** (viết, chạy ở giai đoạn tích hợp): `api/area/AdministrativeUnitControllerTest` (mới: 2.1, 2.2, `USER_004`, 401 AC-U04-06, UUID sai → 400), `api/profile/ProfileTeamControllerTest` (mới: 2.4, 2.5 theo AC-T07-*, AC-T08-*, `role=MEMBER` → 400, người gọi đã xoá → `USER_001`, Admin chỉ thấy của mình), `api/team/TeamControllerTest` (sửa: 2.3 AC-U05-*, `q` thiếu/rỗng/>100 → 400 không đếm, không có field SĐT, AC-U05-09 — 20 lần đội A + 10 lần đội B, lần 31 → `429` + `Retry-After` ≥ 1; lỗi `USER_005`/`TEAM_002` có đếm; 2.1/2.2/2.4/2.5 không bị giới hạn).
Code:
- `api/src/main/.../api/area/AdministrativeUnitController.java` (mới, `/api/v1/provinces`).
- `api/src/main/.../api/profile/ProfileTeamController.java` (mới, `/api/v1/profile/invitations`, `/api/v1/profile/teams`).
- `api/src/main/.../api/team/TeamController.java` (sửa: + `InvitationSearchRateLimiter`, handler 2.3 `@RequestParam @NotBlank @Size(max = 100) String q`).

### Phase 7 — Giai đoạn tích hợp (sau khi `main` có V6–V11)
- Rebase lên `origin/main` → chạy toàn bộ unit + integration test ở "Test để giai đoạn tích hợp" với `.env.dev` (copy vào worktree) → PASS.
- `NfsQueryCountTest` (sửa) + dữ liệu lệch + `EXPLAIN` + timeout (`[cần xác nhận #2]`, `#9`): không đạt → dừng, báo user trước merge.
- Cập nhật `docs/database/data-model.md`, `schema.dbml`, `schema-design.md` (V12).
- `code-reviewer` → đối chiếu design + bảng "Lệch thiết kế" → cập nhật design một lần → `architect-reviewer` → commit `feat(USER-003)` + `docs(USER-003)` riêng.

### Ước lượng file
- Main: 22 mới (1 exception, 2 support, 1 migration, 5 projection, 7 DTO, 1 mapper, 2 service, 1 enum, 2 controller) + 12 sửa (`ErrorCode`, `GlobalExceptionHandler`, 3 repository, 2 mapper, 4 service, `TeamController`).
- Test: 9 unit (7 mới + 1 sửa `GlobalExceptionHandlerTest` + `RateLimitExceededExceptionTest`) · ~10 integration (6 mới + 4 sửa, gồm `NfsQueryCountTest`, timeout/dữ liệu lệch).
- Tài liệu: 3 file `docs/database/` (giai đoạn tích hợp) + state.
- Tổng ≈ 56–60 file.

## Phụ thuộc tạm (thuộc mã khác)
_Bản tối thiểu tạo để biên dịch / unit test; khi tích hợp, bản của mã sở hữu thắng._

| File | Mã sở hữu | Phần tạo |
|---|---|---|
| _(không có)_ | — | USER-003 không cần `AppTime` / `ClockConfig` / `Clock` (không lấy "bây giờ"; limiter dùng `System.nanoTime`); envelope 429 dùng `ErrorResponse.from` hiện có. Phát sinh nhu cầu khi code → thêm dòng ở đây trước khi tạo. |

**File dùng chung dự kiến xung đột khi tích hợp** (thêm mới cạnh code của mã khác — giải quyết bằng gộp cả hai phía):
| File | Mã cùng sửa | USER-003 sửa | Mã kia sửa |
|---|---|---|---|
| `common/.../ErrorCode.java` | MATCH-001, MATCH-002, REPUTATION-001, MERCENARY-001, NOTIFICATION-001 | +`USER_004..007` sau `USER_003` | + mã miền riêng |
| `api/.../GlobalExceptionHandler.java` | SYSTEM-002 | + handler `RateLimitExceededException` | `OffsetDateTime.now()` → `AppTime.now()`, `handleMessageNotReadable` |
| `domain/.../UserRepository.java` | SYSTEM-002 (`softDelete` → `LocalDateTime`), MATCH-001, MERCENARY-001 | +4 method, import projection | sửa/ thêm method |
| `domain/.../TeamMemberRepository.java` | SYSTEM-002 (`disbandRemaining`, `leaveAndRejectAllOpen` → `LocalDateTime`) | +2 query, import | đổi kiểu tham số / import `OffsetDateTime` |
| `application/.../mapper/MembershipMapper.java`, `TeamMapper.java`, (`MapperSupport` — USER-003 không sửa) | SYSTEM-002 (xoá `toUtc`) | +method | bỏ `toUtc` ngầm |
| `application/.../service/impl/TeamMembershipServiceImpl.java`, `TeamServiceImpl.java` | SYSTEM-002 (inject `Clock`, `LocalDateTime.now(clock)`) | +method, import DTO/support | constructor + `now` |
| `api/.../team/TeamController.java` | (không mã nào khai sửa) | +field limiter, +handler 2.3 | — |
| `api/src/test/.../NfsQueryCountTest.java`, `UserRepositoryTest.java`, `TeamControllerTest.java`, `ApiIntegrationTest.java` | SYSTEM-002 (sửa fixture `OffsetDateTime`), mã khác | thêm test | sửa fixture |
| `docs/database/*`, `docs/agent/state/local-dev/*` | mọi mã | V12 / state USER-003 | phần của mã kia |

## Test để giai đoạn tích hợp
_Cần DB dev / Spring context / migration / NFS — viết ở giai đoạn 2, chỉ CHẠY khi tích hợp (sau khi `main` có V6–V11, worktree có `.env.dev`). Lệnh: `. ./.env.dev && export DATABASE_URL DATABASE_USERNAME DATABASE_PASSWORD JWT_SECRET && ./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -Dtest=<Test> test`._

| Test | Loại | Kiểm |
|---|---|---|
| `migration/UserNameSearchMigrationTest` (mới) | migration | Hàm/index V12, `IMMUTABLE`, kết quả `user_name_fold` |
| `domain/repository/AdministrativeUnitRepositoryTest` (mới) | `@SpringBootTest` + DB | Thứ tự ICU, ward của tỉnh, 0 dòng / `wardId` null |
| `domain/repository/UserRepositoryTest` (sửa) | DB | Tìm tên/SĐT, loại trừ, nhãn, `EXPLAIN` dùng GIN (#2) |
| `domain/repository/TeamMemberRepositoryTest` (mới) | DB | Lời mời của tôi, đội của tôi, sắp xếp, count |
| `api/area/AdministrativeUnitControllerTest` (mới) | MockMvc + DB | 2.1, 2.2, 401, 400, `USER_004` |
| `api/profile/ProfileTeamControllerTest` (mới) | MockMvc + DB | 2.4, 2.5 (AC-T07-*, AC-T08-*), `USER_001`, `role` sai |
| `api/team/TeamControllerTest` (sửa) | MockMvc + DB | 2.3 (AC-U05-*), không lộ SĐT, AC-U05-09 (`429` + `Retry-After`, đếm/không đếm) |
| `application/service/NfsQueryCountTest` (sửa) | Hibernate statistics | 2.1=1, 2.2=1 (cả `USER_004`), 2.3 SĐT=3, tên=4 (cả dữ liệu lệch), `USER_007`=0, `USER_006`=1, `TEAM_002` (tên)=4, 2.4/2.5=3|2 |
| `application/service/InvitationSearchSkewedDataTest` (mới) | DB | Seed ~3.000 "Nguyễn …" + 1.000 "Văn": `nguyen` → 20 + `hasMore`, thứ tự, vẫn 4 câu, `EXPLAIN` GIN; không assert thời gian |
| `domain/repository/QueryTimeoutHintTest` (mới, + repository/truy vấn chỉ dùng cho test `pg_sleep(3)` cùng hint 2000 ms) | DB | `QueryTimeoutException` sau ~2 giây qua kết nối DB dev (#9); MockMvc 2.3 khi repository ném `QueryTimeoutException` → `500 SYS_005` |
| `ProfileServiceIntegrationTest` / `DELETE /profile` liên quan (sửa nếu cần) | DB | AC-T08-03: lọc `CAPTAIN` rỗng ⇔ `DELETE /profile` không còn `USER_003` |

## Blockers / Ghi chú
- **Điều chỉnh kế hoạch khi code (không đổi design / API / DB):** để giảm xung đột với các mã chạy song song, test integration đặt ở file mới thay vì sửa file chung — `UserDiscoveryRepositoryTest` (thay sửa `UserRepositoryTest`), `TeamMemberDiscoveryRepositoryTest`, `TeamInvitationCandidatesControllerTest` (thay sửa `TeamControllerTest`), `UserDiscoveryNfsQueryCountTest` (thay sửa `NfsQueryCountTest`; gộp luôn kiểm dữ liệu lệch 3.000 tên thay `InvitationSearchSkewedDataTest`), `QueryTimeoutHintTest` + `QueryTimeoutProbeRepository` (chỉ ở test). Fixture SQL dùng chung `api/src/test/.../testsupport/UserDiscoveryFixtures` (thời điểm dạng chuỗi + `CAST(? AS timestamp)` — không phụ thuộc kiểu thời gian entity, chịu được SYSTEM-002). Handler `QueryTimeoutException` → `500 SYS_005` kiểm thêm ở unit `GlobalExceptionHandlerTest`. EXPLAIN (#2) trong `UserDiscoveryRepositoryTest#namePlanWithBindParametersUsesGinIndex` dùng `PREPARE` + `plan_cache_mode = force_generic_plan` + `enable_seqscan = off` (kiểm index **dùng được** với tham số bind).
- **B1 — KHÔNG chạy test có Spring context / Flyway trên nhánh này trước tích hợp** [Inference từ cấu hình]: `application.properties` bật Flyway, không đặt `outOfOrder`; DB dev đang ở V5. Áp V12 lên DB dev dùng chung khi V6–V11 chưa áp → [Inference] Flyway sẽ coi V6–V11 là migration "bỏ qua" và `validate` lỗi khi các mã kia chạy → hỏng DB dev chung. Worktree hiện không có `.env.dev` (đã kiểm) nên test tích hợp không kết nối được — giữ nguyên tới giai đoạn tích hợp. Lựa chọn khác cần user duyệt: Neon branch riêng cho USER-003.
- **B2 — Integration 2.4/2.5 phụ thuộc V6 (SYSTEM-002):** projection `LocalDateTime` trên cột `invited_at`/`joined_at` — trước V6 cột là `TIMESTAMPTZ` [Unverified] chuyển đổi native → `LocalDateTime` có thể lỗi; chỉ chạy sau V6.
- **B3 — State tracked trong Git:** `.gitignore` có `!docs/agent/state/*/` nên `docs/agent/state/local-dev/*` được track (trái `CLAUDE.md` §7 "gitignore"); 7 nhánh song song cùng ghi đè `current-task.md` → xung đột chắc chắn khi merge `[cần xác nhận]` có commit state trên nhánh này không.
- `[cần xác nhận]` ADR-0016 còn `Proposed` — chuyển `Accepted` trước merge USER-003? (thiết kế đã duyệt, code bám ADR này).
- `[cần xác nhận #9 CCD]` timeout 2 giây qua pooler Neon: kiểm ở integration; không đạt → dừng, user chọn phương án dự phòng (`ALTER ROLE … SET statement_timeout`) + ADR trước merge.
- `[cần xác nhận #2 CCD]` EXPLAIN với tham số bind dùng `idx_user_name_search`: kiểm ở integration; không đạt → dừng, báo.
- Còn mở, không chặn: #3 (ký tự ngoài bảng dấu), #6 (số phường tối đa), #7 (limiter theo instance / nhiều tài khoản), #8 (mốc 50.000 user), #11 (CORS `Retry-After`).
