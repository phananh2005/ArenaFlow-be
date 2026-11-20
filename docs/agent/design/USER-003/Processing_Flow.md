# Processing Flow — Tra cứu cho người dùng

| Mục | Giá trị |
|---|---|
| Mã chức năng | `USER-003` |
| Phạm vi tầng | Backend (Spring Boot REST API) |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · PostgreSQL 18 (Neon) · Flyway |
| Quy ước áp dụng | `docs/agent/non-functional-standards.md` (N_DETAIL=3, N_LIST=4, N_WRITE=6) — ngoại lệ phân trang §6 (user chốt D1, 2026-11-17) |
| Ràng buộc dữ liệu | Schema V1–V5 + `V12__user_name_search.sql` (`Component_Class_Design.md` §6) |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-17 · v1.0 (baseline) · hiện tại **v2.0** (solution-architect · 2026-11-21) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan

Năm endpoint đọc (`API_Spec.md` §2). Mỗi request là một transaction `@Transactional(readOnly = true)` ở service (READ COMMITTED mặc định), không khoá, không retrier, không event, không gọi ngoài. 2.3 có thêm giới hạn 30 lần/phút mỗi người trong bộ nhớ (không truy vấn DB — `Component_Class_Design.md` §4.5). Class/method: `Component_Class_Design.md` §3–§5. Mã lỗi chỉ ghi mã — nghĩa ở `API_Spec.md` §5. `401` do filter Security trả trước Bean Validation. Số truy vấn tính cho một request; xác thực JWT không truy vấn DB.

## 2. Sequence — tìm người để mời (2.3, nhánh tên)

```mermaid
sequenceDiagram
    participant C as Client
    participant TC as TeamController
    participant MS as TeamMembershipServiceImpl
    participant RL as InvitationSearchRateLimiter
    participant Q as InvitationSearchQuery
    participant UR as UserRepository
    participant TG as TeamGuard
    participant DB as PostgreSQL

    C->>TC: GET /api/v1/teams/{teamId}/invitation-candidates?q=Nguyen An
    TC->>RL: acquire(callerId) — bộ nhớ, ngoài transaction, không SQL, không mượn kết nối
    alt đã đủ 30 lần trong 60 giây gần nhất
        RL-->>TC: RateLimitExceededException(USER_007, retryAfterSeconds)
        TC-->>C: 429 USER_007 + Retry-After
    end
    TC->>MS: searchInvitationCandidates(teamId, callerId, q) — mở transaction readOnly
    MS->>Q: parse(q)
    alt dạng số điện thoại
        Q-->>MS: Phone("0987654321") hoặc USER_005 (không truy vấn)
    else dạng tên
        Q-->>MS: Name(raw)
        MS->>UR: Q1 foldName(raw) → "nguyen an"
        MS->>Q: ofFolded("nguyen an")
        Q-->>MS: tsQuery "nguyen:* & an:*", prefix "nguyen an" (hoặc USER_006)
    end
    MS->>TG: Q2 activeTeam(teamId) (TEAM_001, TEAM_016, TEAM_015)
    MS->>TG: Q3 requireCaptain(teamId, callerId) (TEAM_002; +1 câu đọc trạng thái nếu lỗi)
    MS->>UR: Q4 searchCandidatesByName(teamId, callerId, tsQuery, prefix, 21) — timeout 2 giây
    UR->>DB: GIN idx_user_name_search → đọc mọi dòng khớp, sắp, LIMIT 21
    MS-->>TC: InvitationCandidatesResponse(items ≤ 20, hasMore)
    TC-->>C: 200
```

## 3. Luồng chi tiết và truy vấn dự kiến

| API | Truy vấn | Số truy vấn | Ngưỡng |
|---|---|---:|---:|
| 2.1 `GET /provinces` | Q1 `findProvinces` | 1 | L4 ✅ |
| 2.2 `GET /provinces/{id}/wards` | Q1 `findWardsOfProvince` (tỉnh LEFT JOIN phường) | 1 | L4 ✅ |
| 2.3 nhánh số điện thoại | Q1 đội → Q2 đội trưởng → Q3 tìm theo SĐT | 3 | L4 ✅ |
| 2.3 nhánh tên | Q1 `foldName` → Q2 đội → Q3 đội trưởng → Q4 tìm theo tên | 4 | L4 ✅ (bằng ngưỡng; thời gian Q4 tăng theo số người khớp — §6) |
| 2.4 `GET /profile/invitations` | Q1 người gọi còn hoạt động → Q2 trang dữ liệu → Q3 đếm | 3 (2 khi trang 1 chưa đầy) | L4 ✅ |
| 2.5 `GET /profile/teams` | như 2.4 | 3 (2 khi trang 1 chưa đầy) | L4 ✅ |

Nhánh lỗi của 2.3: `USER_007` (429) = 0; `USER_005` = 0; `USER_006` = 1; `TEAM_001`/`TEAM_016`/`TEAM_015` = 1 (SĐT) / 2 (tên); `TEAM_002` = 3 (SĐT) / 4 (tên) — gồm câu đọc trạng thái đã commit của `TeamGuard.disbandedOr`. Không nhánh nào vượt 4. Không truy vấn trong vòng lặp; đội trưởng / số thành viên / nhãn thành viên lấy trong cùng câu dữ liệu (JOIN + subquery tương quan).

### 3.1. 2.1 — danh sách tỉnh/thành

`AdministrativeUnitServiceImpl.listProvinces` → Q1 → map. Rỗng → `[]`.

### 3.2. 2.2 — phường/xã của tỉnh/thành

Q1 trả: 0 dòng → `USER_004` (id không tồn tại hoặc là phường/xã); 1 dòng `wardId = NULL` → `[]`; còn lại → danh sách. Một câu phân biệt được cả ba trường hợp, không cần `existsById` riêng.

### 3.3. 2.3 — tìm người để mời

1. Bean Validation (`q` có mặt, `@NotBlank`, `@Size(max = 100)`; `teamId` UUID) → `VALIDATION_ERROR` (không đếm vào giới hạn).
2. Controller gọi `invitationSearchRateLimiter.acquire(callerId)` **trước** khi vào service (ngoài transaction — request bị chặn không mượn kết nối DB): đủ 30 lần trong 60 giây gần nhất → `429 USER_007` + `Retry-After`, không SQL; ngược lại ghi nhận lần gọi (mọi kết quả phía sau — thành công hay lỗi — đều đã được đếm).
3. `InvitationSearchQuery.parse(q)` (`Component_Class_Design.md` §4.4): nhánh SĐT chuẩn hoá trong Java, sai → `USER_005`.
4. Nhánh tên: Q1 `SELECT user_name_fold(:q)` — chuẩn hoá bằng đúng hàm của index (nguồn duy nhất của bảng dấu); `ofFolded` lọc token `[a-z0-9]+`, < 2 ký tự → `USER_006`; dựng `tsQuery` (`w1:* & w2:*`) và `prefix`.
5. Q2 `teamGuard.activeTeam(teamId)`; Q3 `teamGuard.requireCaptain(teamId, callerId)`.
6. Q4 tìm (`Component_Class_Design.md` §5.2), `LIMIT 21`; `hasMore` = có dòng thứ 21; trả 20 dòng đầu. Nhánh tên có giới hạn thời gian 2 giây (query hint); hết giờ → `500 SYS_005` qua handler chung.

Guard và tìm kiếm là các câu đọc riêng trong READ COMMITTED: chuyển quyền / khoá đội commit chen giữa Q3 và Q4 vẫn trả kết quả — vô hại vì chỉ đọc; lời mời (USER-001 2.12) kiểm lại toàn bộ điều kiện (BR-U14).

### 3.4. 2.4 — lời mời của tôi

Q1 `existsByIdAndDeletedAtIsNull(callerId)` sai → `USER_001` (cùng hành vi các danh sách `/profile/*` khác). Q2 `findMyPendingInvitations` (sắp `invited_at DESC, id DESC`), Q3 `countQuery` — Spring Data bỏ Q3 khi trang 1 có ít hơn `pageSize` dòng. Mapper che `captainName` của người đã xoá tài khoản.

### 3.5. 2.5 — đội của tôi

Như 2.4 với `findMyTeams(callerId, role)`; `role = null` → mọi vai trò. Sắp `CAPTAIN` trước, `joined_at DESC`, `team.id DESC`.

## 4. Transaction boundary

| Method | Transaction |
|---|---|
| `AdministrativeUnitServiceImpl.listProvinces`, `listWards` | `@Transactional(readOnly = true)` |
| `TeamMembershipServiceImpl.searchInvitationCandidates`, `listMyInvitations` | `@Transactional(readOnly = true)` |
| `TeamServiceImpl.listMyTeams` | `@Transactional(readOnly = true)` |

Không ghi, không flush, không khoá.

## 5. Concurrency

Không có thao tác ghi nên không xung đột. Dữ liệu có thể đổi giữa lúc xem và lúc thao tác (tìm → mời; danh sách lời mời → chấp nhận; đội của tôi → chuyển quyền / giải tán): thao tác ghi của USER-001 / USER-002 tự kiểm và trả mã lỗi hiện có (`TEAM_003`, `USER_001`, `TEAM_006`, `TEAM_015`, `TEAM_016`…). Không idempotency key (chỉ `GET`).

## 6. Hiệu năng và ngoại lệ phân trang

**Ngoại lệ không phân trang** (`non-functional-standards.md` §2 "MUST phân trang" — user chốt D1 = A, 2026-11-17; ghi vào chuẩn ở nhánh `chore/agent-framework-*`, `Component_Class_Design.md` §10):

| API | Chặn trên | Lý do |
|---|---|---|
| 2.1 | Số tỉnh/thành của danh mục (chỉ đọc, không API thêm) — hiện 5 | SRS BR-U07: trả toàn bộ, ứng dụng tự lọc; tập nhỏ, cố định |
| 2.2 | Số phường/xã của một tỉnh/thành — hiện ≤ 10 `[cần xác nhận #6 CCD]` | Như trên; một tỉnh/thành mỗi lần |
| 2.3 | `LIMIT 21` cố định (trả ≤ 20) | SRS BR-U10: tối đa 20 gợi ý, không phân trang |

**Index:**

| Truy vấn | Index | Ghi chú |
|---|---|---|
| 2.1, 2.2 | `idx_administrative_unit_level`, `idx_administrative_unit_parent` (V1) | Sắp `COLLATE "vi-VN-x-icu"` trong bộ nhớ trên tập nhỏ |
| 2.3 SĐT | `idx_user_phone_unique (phone_number) WHERE … deleted_at IS NULL` (V1) | Khớp chính xác |
| 2.3 tên | `idx_user_name_search` GIN (V12, mới) | GIN chỉ lọc dòng khớp; sắp `starts_with` + ICU phải đọc **mọi** dòng khớp trước `LIMIT`. Đo: 0,05 ms khi ít người khớp (`an:*`, 50.002 tên); dữ liệu lệch 200.000 tên: `nguyen:*` 80.016 dòng 485 ms, `van:*` 266 ms, `an:*` 105 ms, `nguyen:* & an:*` 49 ms. Rủi ro chấp nhận cho MVP + timeout 2 giây + mốc xem lại ~50.000 user (`Component_Class_Design.md` §5.2, §9 #8). Kế hoạch với tham số bind trên bảng thật: `[cần xác nhận #2 CCD]` |
| 2.3 nhãn | `idx_team_member_pending_or_active (team_id, user_id)` (V2) | ≤ 1 dòng mỗi user |
| 2.4, 2.5 | `idx_team_member_user (user_id, status)` (V1) | Sắp trên tập lời mời / đội của một người |

**Đo trước khi merge:** `NfsQueryCountTest` (log SQL thật): 2.1 = 1, 2.2 = 1 (cả `USER_004`), 2.3 SĐT = 3, 2.3 tên = 4 (cả với dữ liệu lệch — số câu không đổi theo số người khớp), 2.3 `USER_007` = 0, 2.3 `USER_006` = 1, 2.3 `TEAM_002` (tên) = 4, 2.4 = 3 với trang đầy / 2 trang chưa đầy, 2.5 như 2.4.

## 7. Error handling

| Trường hợp | Nguồn | HTTP / mã |
|---|---|---|
| Thiếu / sai token | Filter Security | 401 `AUTH_001` |
| `q` thiếu / rỗng / > 100; `page`, `pageSize`, `role`, UUID sai | Bean Validation / binding → `GlobalExceptionHandler` | 400 `VALIDATION_ERROR` |
| `provinceId` không phải tỉnh/thành | `AdministrativeUnitServiceImpl` | 404 `USER_004` |
| Đủ 30 lần gọi 2.3 trong 60 giây gần nhất | `InvitationSearchRateLimiter` → `RateLimitExceededException` → handler riêng | 429 `USER_007` + header `Retry-After` |
| `q` dạng SĐT không hợp lệ | `InvitationSearchQuery.parse` | 400 `USER_005` |
| `q` tên < 2 ký tự sau chuẩn hoá | `InvitationSearchQuery.ofFolded` | 400 `USER_006` |
| Đội không tồn tại / giải tán / tạm ngưng / không phải đội trưởng | `TeamGuard` | `TEAM_001` / `TEAM_016` / `TEAM_015` / `TEAM_002` |
| Người gọi đã xoá tài khoản (2.4, 2.5) | service | 404 `USER_001` |
| Câu tìm theo tên quá 2 giây | `QueryTimeoutException` → handler `Exception` | 500 `SYS_005`, log `ERROR` |
| Lỗi cú pháp tsquery | Không xảy ra: token chỉ `[a-z0-9]`, bind tham số | — (nếu có → 500 `SYS_005`, coi là bug) |

## 8. Test bắt buộc

- **Unit (`InvitationSearchQuery`, không DB):** phân loại (`0987654321`, `+84 987 654 321`, `84987654321`, `0987.654.321`, `0987-654-321` → `0987654321`; `+84 12345`, `09+87654321`, `--`, `123` → `USER_005`; `Nguyen`, `!!` → nhánh tên); `ofFolded` (`""`, `"a"` → `USER_006`; `"a b"` → hợp lệ `a:* & b:*`; token chứa ký tự lạ bị bỏ; `tsQuery`/`prefix` đúng).
- **Migration / repository (DB dev):** `user_name_fold` cho tên có dấu, chữ hoa, NFD, dấu gạch / chấm / nhiều khoảng trắng; `EXPLAIN` dùng `idx_user_name_search`; AC-U05-03 (thứ tự "An Phạm", "Nguyễn Văn An", "Trần An"; không "Lan Nguyễn", không người đã xoá), AC-U05-03b, AC-U05-03e, AC-U05-03c (25 người khớp → 20 + `hasMore = true`), AC-U05-03d (không chính mình, không Admin — cả theo SĐT), AC-U05-05 (người không có SĐT tìm được theo tên), AC-U05-07 (nhãn theo đúng đội: cùng người có nhãn ở đội A, không nhãn ở đội C), AC-U05-08 (đội `INACTIVE` → `TEAM_015`; thành viên thường → `TEAM_002`), số điện thoại của người đã xoá → `items: []`.
- **Danh mục:** AC-U04-01 (thứ tự tiếng Việt "Cần Thơ", "Đà Nẵng", "Hà Nội", "Hải Phòng", "TP. Hồ Chí Minh"), AC-U04-02 (chỉ phường của tỉnh đó), AC-U04-03 (id không tồn tại và id phường → `USER_004`), tỉnh không có phường → `[]`, AC-U04-06 (không token → 401).
- **Lời mời của tôi:** AC-T07-01 (`totalItems = 2`, chỉ `PENDING` của mình, mới nhất trước), AC-T07-01b (đội trưởng hiện tại sau chuyển quyền), AC-T07-02, AC-T07-03 (đội giải tán biến mất), AC-T07-04 (đội `INACTIVE` vẫn có, `team.status = INACTIVE`), đội trưởng đã xoá tài khoản → `"Người dùng đã xóa"` (dữ liệu dựng tay), người gọi đã xoá → `USER_001`.
- **Đội của tôi:** AC-T08-01, AC-T08-01b (R, Q, P), AC-T08-02, AC-T08-03 (lọc `CAPTAIN` trước / sau chuyển quyền và giải tán; lọc rỗng ⇔ `DELETE /profile` không còn `USER_003`), AC-T08-04, AC-T08-05 (mỗi người chỉ thấy của mình, kể cả Admin), `role=MEMBER` → 400.
- **Không lộ số điện thoại:** response 2.3 không có field số điện thoại (assert JSON), cả khi tìm theo SĐT.
- **Giới hạn 2.3 — unit (`InvitationSearchRateLimiter`, đồng hồ giả):** 30 lần liền được chấp nhận, lần 31 → `USER_007` với `retryAfterSeconds` đúng (vd lần đầu ở t = 0, lần 31 ở t = 10 giây → 50); lần bị từ chối không làm lùi thời điểm mở lại; t = 60 giây + ε → được chấp nhận lại (cửa sổ trượt, không cửa sổ cố định); hai user độc lập; dọn: user không gọi quá 2 × 60 giây bị xoá khỏi map; 100 luồng cùng user gọi đồng thời → đúng 30 lần được chấp nhận.
- **Giới hạn 2.3 — integration:** AC-U05-09 (20 lần cho đội A + 10 lần cho đội B, lần 31 → `429`, `USER_007`, header `Retry-After` số nguyên ≥ 1, envelope lỗi chuẩn); lần lỗi `USER_005` / `TEAM_002` có bị đếm; `400 VALIDATION_ERROR` không bị đếm; 2.4, 2.5, 2.1, 2.2 không bị giới hạn.
- **Dữ liệu lệch (tìm theo tên):** seed vài nghìn user cùng họ (vd 3.000 "Nguyễn …", 1.000 có "Văn") + vài tên khác: `nguyen` → đúng 20 dòng, `hasMore = true`, thứ tự đúng (bắt đầu bằng "nguyen" trước, rồi ICU, rồi id); `NfsQueryCountTest` nhánh tên vẫn = 4; `EXPLAIN` vẫn dùng `idx_user_name_search`. Không assert thời gian trong CI (phụ thuộc máy); đo tay khi review theo số đo §6.
- **Timeout:** câu thử `pg_sleep(3)` với cùng query hint (repository chỉ dùng cho test) → `QueryTimeoutException` sau khoảng 2 giây; MockMvc 2.3 khi repository ném `QueryTimeoutException` → `500 SYS_005` `[cần xác nhận #9 CCD]`.
- `NfsQueryCountTest`: §6.

## 9. Danh sách [cần xác nhận]

Không có điểm riêng của luồng xử lý; xem `Component_Class_Design.md` §9 (#2 kế hoạch truy vấn với tham số bind, #3 ký tự ngoài bảng dấu, #6 chặn trên danh mục khi nạp toàn quốc, #7 bộ đếm giới hạn theo instance, #8 mốc xem lại tìm theo tên, #9 kiểm timeout, #11 CORS cho `Retry-After`; #1, #4, #5, #10 đã chốt).
