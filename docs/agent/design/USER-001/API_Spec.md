# API Specification — User & Team Management

| Mục | Giá trị |
|---|---|
| Mã chức năng | `USER-001` |
| Phạm vi tầng | Backend (Spring Boot REST API) |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · PostgreSQL (Neon) · Flyway |
| Quy ước áp dụng | `docs/agent/api-convention.md`, `docs/agent/security-backend.md` |
| Ràng buộc dữ liệu | `V1__initial_schema.sql` + delta V2/V3 (xem §5) · `docs/agent/non-functional-standards.md` |
| Spec nghiệp vụ | `docs/agent/business-spec/SYSTEM-001/` (UC-U01..U03, UC-T01..T05, BR-U01..U06, BR-T01..T06) |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-02 · v1.0 (baseline) · hiện tại **v3.6** (developer · 2026-11-09) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan

Foundation module: xác thực mock (OAuth Google/Facebook, Phone OTP) + JWT, hồ sơ cá nhân, đội, lời mời, chuyển quyền đội trưởng, admin đánh dấu/kích hoạt lại đội.

**Ràng buộc vận hành của mock:** rủi ro mạo danh và lệnh cấm deploy ra môi trường truy cập từ ngoài khi còn mock — xem ADR-0001 §"Sửa đổi 2026-11-04".

**Ngoài phạm vi:** notification (UC-U01/T02/T03/T04/T05 bước gửi thông báo) · khôi phục tài khoản đã xoá · liên kết nhiều provider vào 1 tài khoản · frontend (ngoài phạm vi repo này).

**Quy ước chung cho mọi endpoint:**
- ID là UUID (chuỗi) — entity dùng `@UuidGenerator(style = VERSION_7)` (UUID v7 — ADR-0008); id đã có trước đó giữ dạng v1. Client không suy thứ tự thời gian từ id — dùng `createdAt`.
- Envelope `data` / `error` / `pagination` theo `api-convention.md` §4, §7.
- Lỗi Bean Validation (định dạng, độ dài, enum, bắt buộc) → `400 VALIDATION_ERROR` kèm `validationErrors` (`api-convention.md` §11). Mã lỗi miền (§4) chỉ dùng cho quy tắc cần DB hoặc trạng thái.
- **Giá trị enum trên API: UPPERCASE.** `provider` = `GOOGLE|FACEBOOK|PHONE`; `status` = `ACTIVE|INACTIVE|PENDING|LEFT|REJECTED`; `role` = `USER|ADMIN|CAPTAIN|MEMBER`; `gender` = `MALE|FEMALE|OTHER`.
- Danh sách: `page` (≥1, mặc định 1), `pageSize` (1..100, mặc định 20); sort cố định ghi ở từng endpoint, chưa hỗ trợ tham số `sort`.
- Ghi lên `team`/`team_member` có optimistic lock + tự retry (cả khi PostgreSQL huỷ do deadlock); hết retry → `409 TEAM_014` (xem `Processing_Flow.md` §5). `DELETE /profile` khoá bi quan có phạm vi (ADR-0007) nhưng vẫn chạy qua retrier.
- **Đội INACTIVE:** chặn sửa/mời/chuyển quyền/accept/reject lời mời → `422 TEAM_015`.

## 2. Endpoints

| # | Method & Path | Quyền | Thành công |
|---|---|---|---|
| 2.1 | `POST /api/v1/auth/oauth` | Public | 201 / 200 |
| 2.2 | `POST /api/v1/auth/send-otp` | Public | 200 |
| 2.3 | `POST /api/v1/auth/verify-otp` | Public | 201 / 200 |
| 2.4 | `POST /api/v1/auth/refresh` | Public (refresh token) | 200 |
| 2.5 | `GET /api/v1/profile` | User | 200 |
| 2.6 | `PATCH /api/v1/profile` | User | 200 |
| 2.7 | `DELETE /api/v1/profile` | User | 204 |
| 2.8 | `POST /api/v1/teams` | User | 201 |
| 2.9 | `GET /api/v1/teams/{teamId}` | User | 200 |
| 2.10 | `GET /api/v1/teams` | User | 200 |
| 2.11 | `PATCH /api/v1/teams/{teamId}` | Captain | 200 |
| 2.12 | `POST /api/v1/teams/{teamId}/invitations` | Captain | 201 |
| 2.13 | `POST /api/v1/invitations/{invitationId}/accept` | Người được mời | 200 |
| 2.14 | `POST /api/v1/invitations/{invitationId}/reject` | Người được mời | 204 |
| 2.15 | `POST /api/v1/teams/{teamId}/captain/transfer` | Captain | 200 |
| 2.16 | `GET /api/v1/teams/{teamId}/captain/history` | User | 200 |
| 2.17 | `GET /api/v1/teams/{teamId}/members` | User | 200 |
| 2.18 | `POST /api/v1/teams/{teamId}/members/leave` | Member (không phải captain) | 204 |
| 2.19 | `PATCH /api/v1/admin/teams/{teamId}/mark-inactive` | Admin | 200 |
| 2.20 | `PATCH /api/v1/admin/teams/{teamId}/reactivate` | Admin | 200 |
| 2.21 | `GET /api/v1/admin/teams` | Admin | 200 |

Mã thành công theo `api-convention.md` §5 (B1–B4 / N1); `201` **không** kèm header `Location` — client lấy id từ body.

Mọi endpoint không Public: thiếu/sai/hết hạn access token → `401 AUTH_001`.

Endpoint Public `/api/v1/auth/**` (2.1–2.4) **bỏ qua header `Authorization`**: gửi kèm access token sai/hết hạn không gây 401, request được xử lý như không có header (client có thể giữ interceptor gắn token cho mọi request, kể cả `/auth/refresh`).

---

### 2.1. POST /api/v1/auth/oauth

**Mô tả:** Đăng ký-hoặc-đăng nhập qua Google/Facebook (mock). Chưa có tài khoản → tạo `user` + `oauth_account` (201). Đã có → đăng nhập (200) — theo UC-U01 luồng 3a.

**Request:**
```json
{ "provider": "GOOGLE", "oauthToken": "any-non-blank-token", "email": "user@example.com" }
```

**Response 201 (tạo mới) / 200 (đã có):** `AuthResponse` (§3.10), `isNewUser` = `true`/`false`.

**Lỗi:** `400 VALIDATION_ERROR` · `403 AUTH_003` (tài khoản đã bị xoá).

**Validation:** `provider` ∈ {`GOOGLE`,`FACEBOOK`} · `oauthToken` không rỗng (mock: không xác minh với provider) · `email` bắt buộc, định dạng email (BR-U02), ≤255.

**Ghi chú mock:** `email` thay cho claim email lẽ ra lấy từ token provider; chuyển về chữ thường (BR-U02) rồi lưu vào `oauth_account.provider_user_id` — `An@Gmail.com` và `an@gmail.com` là cùng tài khoản; email có khoảng trắng đầu/cuối → `400 VALIDATION_ERROR` (`@Email`). Cùng email ở Google và Facebook → 2 tài khoản khác nhau (chưa liên kết provider).

---

### 2.2. POST /api/v1/auth/send-otp

**Mô tả:** Gửi OTP (mock — không gửi SMS, không lưu OTP).

**Request:** `{ "phone": "0987654321" }`

**Response 200:**
```json
{ "data": { "phone": "0987654321", "expiresIn": 300 } }
```

**Lỗi:** `400 VALIDATION_ERROR`.

**Validation:** `phone` khớp `^0\d{9}$` (BR-U01).

---

### 2.3. POST /api/v1/auth/verify-otp

**Mô tả:** Xác thực OTP (mock: chấp nhận mọi OTP 6 chữ số) rồi đăng ký-hoặc-đăng nhập như §2.1, với `provider = PHONE`, `provider_user_id = phone`, `user.phone_number = phone`.

**Request:** `{ "phone": "0987654321", "otp": "123456" }`

**Response 201 (tạo mới) / 200 (đã có):** `AuthResponse` (§3.10).

**Lỗi:** `400 VALIDATION_ERROR` · `403 AUTH_003`.

**Validation:** `phone` khớp `^0\d{9}$` · `otp` khớp `^\d{6}$`.

---

### 2.4. POST /api/v1/auth/refresh

**Mô tả:** Đổi refresh token lấy cặp token mới. Stateless — không lưu/thu hồi refresh token (xem ADR-0001).

**Request:** `{ "refreshToken": "<jwt>" }`

**Response 200:** `AuthResponse` (§3.10), `isNewUser = false`.

**Lỗi:** `400 VALIDATION_ERROR` · `401 AUTH_002` (refresh token sai/hết hạn/không phải loại refresh) · `403 AUTH_003`.

---

### 2.5. GET /api/v1/profile

**Mô tả:** Hồ sơ của user hiện tại.

**Response 200:** `ProfileResponse` (§3.11).

**Lỗi:** `404 USER_001` (user đã bị xoá nhưng access token còn hạn).

---

### 2.6. PATCH /api/v1/profile

**Mô tả:** Cập nhật một phần hồ sơ. Field vắng mặt/`null` → giữ nguyên.

**Request:** `ProfileUpdateRequest` (§3.5).

**Response 200:** `ProfileResponse` (§3.11).

**Lỗi:** `400 VALIDATION_ERROR` · `400 USER_002` (wardId không tồn tại hoặc không phải cấp `ward`) · `404 USER_001`.

---

### 2.7. DELETE /api/v1/profile

**Mô tả:** Soft-delete tài khoản (UC-U03 sửa bởi USER-002, BR-T06, ADR-0002, ADR-0005). Còn là captain ACTIVE của bất kỳ đội nào (kể cả là người duy nhất, kể cả đội `INACTIVE`) → `422 USER_003`, không đổi gì; phải chuyển quyền hoặc giải tán đội trước (USER-002 `API_Spec.md` §2.5). Không đổi trạng thái đội. Membership `ACTIVE` → `LEFT`, lời mời `PENDING` → `REJECTED` (`Processing_Flow.md` §9 #2). Xoá lần 2 → vẫn 204.

**Response 204.**

**Lỗi:** `422 USER_003` (còn là captain ACTIVE của một đội chưa giải tán) · `409 TEAM_014` (deadlock với thao tác ghi khác lặp lại đủ 3 lần thử — `Processing_Flow.md` §5; khoá bi quan không tự sinh xung đột version).

---

### 2.8. POST /api/v1/teams

**Mô tả:** Tạo đội; người tạo thành captain, ghi `team_captain_history` (UC-T01).

**Request:** `CreateTeamRequest` (§3.6).

**Response 201:** `TeamResponse` (§3.12).

**Lỗi:** `400 VALIDATION_ERROR` · `404 USER_001`.

---

### 2.9. GET /api/v1/teams/{teamId}

**Response 200:** `TeamResponse` (§3.12).

**Lỗi:** `404 TEAM_001`.

---

### 2.10. GET /api/v1/teams

**Query:** `page`, `pageSize`, `status` ∈ {`ACTIVE`,`INACTIVE`,`ALL`} (mặc định `ACTIVE`).

**Sort:** `createdAt` giảm dần, rồi `teamId` giảm dần.

**Response 200:** `data`: `TeamSummaryResponse[]` (§3.13) + `pagination`.

**Lỗi:** `400 VALIDATION_ERROR`.

---

### 2.11. PATCH /api/v1/teams/{teamId}

**Mô tả:** Cập nhật một phần thông tin đội. `skillLevels` có mặt → thay toàn bộ danh sách (mảng rỗng = xoá hết).

**Request:** `UpdateTeamRequest` (§3.7).

**Response 200:** `TeamResponse` (§3.12).

**Lỗi:** `400 VALIDATION_ERROR` · `403 TEAM_002` · `404 TEAM_001` · `422 TEAM_015` (đội INACTIVE) · `409 TEAM_014`.

---

### 2.12. POST /api/v1/teams/{teamId}/invitations

**Mô tả:** Captain mời user → `team_member` (`role=MEMBER`, `status=PENDING`). Một user được nhận lời mời từ nhiều đội. Lời mời không hết hạn.

**Request:** `InviteMemberRequest` (§3.8).

**Response 201:** `InvitationResponse` (§3.15).

**Lỗi:** `400 VALIDATION_ERROR` · `403 TEAM_002` · `404 TEAM_001` · `422 TEAM_015` (đội INACTIVE) · `404 USER_001` (người được mời không tồn tại/đã xoá) · `409 TEAM_003`.

**Idempotency:** dựa trên unique index `(team_id, user_id) WHERE status IN ('PENDING','ACTIVE')` — gửi lại → `409 TEAM_003`.

---

### 2.13. POST /api/v1/invitations/{invitationId}/accept

**Mô tả:** `invitationId` = `team_member.id`. `PENDING` → `ACTIVE`, set `joined_at`.

**Response 200:** `MemberResponse` (§3.16).

**Lỗi:** `403 TEAM_005` · `404 TEAM_004` · `422 TEAM_015` (đội INACTIVE) · `409 TEAM_006` · `409 TEAM_014`.

---

### 2.14. POST /api/v1/invitations/{invitationId}/reject

**Mô tả:** `PENDING` → `REJECTED` (giữ dòng làm lịch sử).

**Response 204:** không body (N1 — `api-convention.md` §5).

**Lỗi:** `403 TEAM_005` · `404 TEAM_004` · `422 TEAM_015` (đội INACTIVE) · `409 TEAM_006` · `409 TEAM_014`.

---

### 2.15. POST /api/v1/teams/{teamId}/captain/transfer

**Mô tả:** Chuyển quyền ngay, người nhận không được từ chối (BR-T03, BR-T05). Ghi `team_captain_history` kèm `reason` (tuỳ chọn).

**Request:** `TransferCaptainRequest` (§3.9).

**Response 200:**
```json
{ "data": { "teamId": "uuid", "fromUserId": "uuid", "toUserId": "uuid", "reason": null, "transferredAt": "2026-11-02T14:00:00Z" } }
```

**Lỗi:** `400 VALIDATION_ERROR` · `403 TEAM_002` · `404 TEAM_001` · `422 TEAM_015` (đội INACTIVE) · `422 TEAM_007` · `422 TEAM_008` · `409 TEAM_014`.

---

### 2.16. GET /api/v1/teams/{teamId}/captain/history

**Query:** `page`, `pageSize`. **Sort:** `transferredAt` giảm dần.

**Response 200:** `data`: `CaptainHistoryResponse[]` (§3.17) + `pagination`.

**Lỗi:** `404 TEAM_001`.

---

### 2.17. GET /api/v1/teams/{teamId}/members

**Mô tả:** Thành viên `ACTIVE` (không gồm PENDING/LEFT/REJECTED).

**Query:** `page`, `pageSize`. **Sort:** captain trước, rồi `joinedAt` tăng dần, rồi `userId`.

**Response 200:** `data`: `MemberResponse[]` (§3.16) + `pagination`.

**Lỗi:** `404 TEAM_001`.

---

### 2.18. POST /api/v1/teams/{teamId}/members/leave

**Mô tả:** `ACTIVE` → `LEFT`, set `left_at`.

**Response 204.**

**Lỗi:** `404 TEAM_001` · `422 TEAM_009` (captain) · `422 TEAM_010` (không phải thành viên ACTIVE) · `409 TEAM_014`.

---

### 2.19. PATCH /api/v1/admin/teams/{teamId}/mark-inactive

**Mô tả:** UC-T05. `ACTIVE` → `INACTIVE`, lưu `inactive_reason`.

**Request:** `MarkInactiveRequest` (§3.14).

**Response 200:** `TeamResponse` (§3.12).

**Lỗi:** `400 VALIDATION_ERROR` · `403 AUTH_004` · `404 TEAM_001` · `409 TEAM_011` · `409 TEAM_014`.

---

### 2.20. PATCH /api/v1/admin/teams/{teamId}/reactivate

**Mô tả:** `INACTIVE` → `ACTIVE`, xoá `inactive_reason`. Đội phải còn captain `ACTIVE` (đội INACTIVE do captain xoá tài khoản thì không còn captain).

**Response 200:** `TeamResponse` (§3.12).

**Lỗi:** `403 AUTH_004` · `404 TEAM_001` · `409 TEAM_012` · `422 TEAM_013` · `409 TEAM_014`.

---

### 2.21. GET /api/v1/admin/teams

**Mô tả:** Admin tìm đội (UC-T05 bước 1).

**Query:** `page`, `pageSize`, `status` ∈ {`ACTIVE`,`INACTIVE`,`ALL`} (mặc định `ALL`). **Sort:** như §2.10.

**Response 200:** `data`: `AdminTeamSummaryResponse[]` = `TeamSummaryResponse` (§3.13) + `inactiveReason` + `pagination`.

**Lỗi:** `400 VALIDATION_ERROR` · `403 AUTH_004`.

---

## 3. Data Models (DTO)

### 3.1. OAuthLoginRequest
| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `provider` | `String` | Yes | `GOOGLE` \| `FACEBOOK` |
| `oauthToken` | `String` | Yes | không rỗng |
| `email` | `String` | Yes | email hợp lệ, ≤255 |

### 3.2. SendOtpRequest
| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `phone` | `String` | Yes | `^0\d{9}$` |

### 3.3. VerifyOtpRequest
| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `phone` | `String` | Yes | `^0\d{9}$` |
| `otp` | `String` | Yes | `^\d{6}$` |

### 3.4. RefreshTokenRequest
| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `refreshToken` | `String` | Yes | không rỗng |

### 3.5. ProfileUpdateRequest
| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `displayName` | `String` | No | nếu có: không blank, ≤100 (BR-U03) |
| `avatarUrl` | `String` | No | URL bắt đầu bằng `http://` hoặc `https://` (chữ thường; giao thức khác như `file:`, `ftp:`, `javascript:` hoặc `HTTPS://` → 400), ≤500 |
| `bio` | `String` | No | ≤1000 |
| `preferredPosition` | `String` | No | `Thủ môn` \| `Hậu vệ` \| `Tiền vệ` \| `Tiền đạo` (BR-U06; cùng giá trị với `mercenary_recruitment.position`) |
| `dateOfBirth` | `LocalDate` | No | ISO date, trong quá khứ, ≥13 tuổi (BR-U04) |
| `gender` | `String` | No | `MALE` \| `FEMALE` \| `OTHER` |
| `wardId` | `UUID` | No | `administrative_unit.id` với `level = ward` (BR-U05) |

### 3.6. CreateTeamRequest
| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `teamName` | `String` | Yes | không blank, ≤100, cho phép trùng (BR-T01) |
| `avatarUrl` | `String` | No | URL bắt đầu bằng `http://` hoặc `https://` (chữ thường; giao thức khác như `file:`, `ftp:`, `javascript:` hoặc `HTTPS://` → 400), ≤500 |
| `description` | `String` | No | ≤1000 |
| `skillLevels` | `List<String>` | No | ≤3 phần tử, không trùng, thuộc 9 nhãn (BR-T02): `Chill`, `Nhiệt tình`, `Máu lửa`, `Vui chơi`, `Rèn luyện`, `Thi đấu`, `Mới bắt đầu`, `Đã có kinh nghiệm`, `Lão làng` |

### 3.7. UpdateTeamRequest
Như §3.6 nhưng mọi field `No`; `teamName` nếu có thì không blank.

### 3.8. InviteMemberRequest
| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `userId` | `UUID` | Yes | — |

### 3.9. TransferCaptainRequest
| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `newCaptainId` | `UUID` | Yes | — |
| `reason` | `String` | No | ≤500 |

### 3.10. AuthResponse
```json
{ "data": {
  "userId": "uuid", "role": "USER", "isNewUser": true,
  "accessToken": "<jwt>", "refreshToken": "<jwt>",
  "tokenType": "Bearer", "expiresIn": 3600
} }
```
`role` = `USER` hoặc `ADMIN`.

### 3.11. ProfileResponse
```json
{ "data": {
  "userId": "uuid", "role": "USER",
  "providers": [ { "provider": "GOOGLE", "providerUserId": "user@example.com" } ],
  "phoneNumber": null,
  "displayName": "User 1a2b3c", "avatarUrl": null, "bio": null,
  "preferredPosition": "Tiền vệ", "dateOfBirth": "1995-05-15", "gender": "MALE",
  "ward": { "wardId": "uuid", "wardName": "Phường Bến Nghé", "provinceId": "uuid", "provinceName": "TP. Hồ Chí Minh" },
  "createdAt": "2026-11-01T10:00:00Z", "updatedAt": "2026-11-02T10:00:00Z"
} }
```
`ward` = `null` khi chưa chọn. Tạo mới: `displayName` = `"User " + 6 ký tự cuối của userId`, `avatarUrl` = `null`.

### 3.12. TeamResponse
```json
{ "data": {
  "teamId": "uuid", "teamName": "Team Alpha", "avatarUrl": null, "description": null,
  "skillLevels": ["Nhiệt tình", "Rèn luyện"], "status": "ACTIVE", "inactiveReason": null,
  "captainId": "uuid", "captainName": "Nguyễn Văn A", "memberCount": 5,
  "createdBy": "uuid", "createdAt": "2026-11-02T11:00:00Z", "updatedAt": "2026-11-02T11:30:00Z"
} }
```
`captainId`/`captainName` = `null` khi đội không còn captain ACTIVE.

**Quy tắc tên user đã xoá (AC-U03, ADR-0002):** mọi field tên/ảnh của user khác (`captainName`, `fromUserName`, `toUserName`, `displayName`/`avatarUrl` trong `MemberResponse`) trả `"Người dùng đã xóa"` / `null` khi `"user".deleted_at IS NOT NULL`. DB vẫn giữ nguyên hồ sơ.

### 3.13. TeamSummaryResponse
`teamId`, `teamName`, `avatarUrl`, `skillLevels`, `status`, `captainName`, `memberCount`, `createdAt` — cùng nghĩa §3.12.

### 3.14. MarkInactiveRequest
| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `reason` | `String` | Yes | không blank, ≤500 |

### 3.15. InvitationResponse
```json
{ "data": { "invitationId": "uuid", "teamId": "uuid", "teamName": "Team Alpha", "userId": "uuid", "status": "PENDING", "invitedAt": "2026-11-02T12:30:00Z" } }
```

### 3.16. MemberResponse
```json
{ "data": { "memberId": "uuid", "teamId": "uuid", "userId": "uuid", "displayName": "Trần Văn B", "avatarUrl": null, "role": "MEMBER", "status": "ACTIVE", "joinedAt": "2026-11-02T13:00:00Z" } }
```

### 3.17. CaptainHistoryResponse
`historyId`, `teamId`, `fromUserId` (null = lần tạo đội), `fromUserName`, `toUserId`, `toUserName`, `reason`, `transferredAt`.

---

## 4. Mã lỗi

| Mã | HTTP | Ngữ cảnh | Message |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Bean Validation fail · JSON sai · sai kiểu path/query · lỗi 400 khác do framework phát hiện (query param sai giá trị hoặc thiếu → `validationErrors[].field` = tên tham số, vd `page`; thiếu header…) | "Dữ liệu không hợp lệ" + `validationErrors` |
| `AUTH_001` | 401 | Access token thiếu/sai/hết hạn | "Token không hợp lệ hoặc đã hết hạn" |
| `AUTH_002` | 401 | Refresh token sai/hết hạn/sai loại | "Refresh token không hợp lệ hoặc đã hết hạn" |
| `AUTH_003` | 403 | Đăng nhập/refresh bằng tài khoản đã xoá | "Tài khoản đã bị xoá" |
| `AUTH_004` | 403 | Không phải admin | "Cần quyền quản trị" |
| `USER_001` | 404 | User không tồn tại/đã xoá | "Không tìm thấy người dùng" |
| `USER_002` | 400 | `wardId` không tồn tại hoặc không phải phường/xã | "Khu vực không hợp lệ" |
| `USER_003` | 422 | Còn là captain ACTIVE của đội chưa giải tán khi xoá tài khoản (mở rộng ở USER-002) | "Bạn phải chuyển quyền đội trưởng hoặc giải tán đội trước khi xóa tài khoản" |
| `TEAM_001` | 404 | Đội không tồn tại | "Không tìm thấy đội" |
| `TEAM_002` | 403 | Không phải captain ACTIVE của đội | "Chỉ đội trưởng được thực hiện" |
| `TEAM_003` | 409 | Đã là thành viên hoặc đã có lời mời PENDING | "Người này đã là thành viên hoặc đã được mời" |
| `TEAM_004` | 404 | Lời mời không tồn tại | "Không tìm thấy lời mời" |
| `TEAM_005` | 403 | Không phải người được mời | "Chỉ người được mời được xử lý lời mời" |
| `TEAM_006` | 409 | Lời mời không còn PENDING | "Lời mời đã được xử lý" |
| `TEAM_007` | 422 | Người nhận không phải thành viên ACTIVE | "Chỉ chuyển quyền cho thành viên đang hoạt động" |
| `TEAM_008` | 422 | Chuyển quyền cho chính mình | "Không thể chuyển quyền cho chính mình" |
| `TEAM_009` | 422 | Captain rời đội | "Đội trưởng phải chuyển quyền trước khi rời đội" |
| `TEAM_010` | 422 | Không phải thành viên ACTIVE (rời đội) | "Bạn không phải thành viên của đội" |
| `TEAM_011` | 409 | Mark-inactive đội đã INACTIVE | "Đội đã ở trạng thái INACTIVE" |
| `TEAM_012` | 409 | Reactivate đội đang ACTIVE | "Đội đang hoạt động" |
| `TEAM_013` | 422 | Reactivate đội không còn captain ACTIVE | "Đội không còn đội trưởng, không thể kích hoạt lại" |
| `TEAM_014` | 409 | Xung đột cập nhật đồng thời sau khi hết retry | "Dữ liệu vừa được cập nhật bởi thao tác khác, vui lòng thử lại" |
| `TEAM_015` | 422 | Thao tác trên đội INACTIVE (sửa/mời/chuyển quyền/accept/reject) | "Đội đang tạm ngưng hoạt động" |
| `TEAM_016` | 422 | Thao tác ghi trên đội đã giải tán — thêm ở USER-002 (USER-002 `API_Spec.md` §2.1a, §4) | "Đội đã giải tán" |

UC-T04 luồng 2a ("Đội không có thành viên khác") được bao bởi `TEAM_007`/`TEAM_008`.

**Lỗi chung (không gắn endpoint)** — miền `SYS`, cùng envelope `error`. Danh sách đóng; chỉ `GlobalExceptionHandler` phát ra (lỗi 400 của framework dùng `VALIDATION_ERROR` ở bảng trên):

| Mã | HTTP | Ngữ cảnh | Message |
|---|---|---|---|
| `SYS_001` | 404 | URL không khớp endpoint nào (đã xác thực) | "Không tìm thấy tài nguyên" |
| `SYS_002` | 405 | Sai HTTP method; kèm header `Allow` | "Phương thức không được hỗ trợ" |
| `SYS_003` | 415 | `Content-Type` không phải `application/json` | "Kiểu nội dung không được hỗ trợ" |
| `SYS_004` | 4xx khác (giữ status gốc, vd 409 từ `ResponseStatusException`) | Lỗi client khác do framework phát hiện | "Yêu cầu không hợp lệ" |
| `SYS_005` | 500 (5xx của framework giữ status gốc, vd 503) | Lỗi ngoài dự kiến; không lộ chi tiết | "Đã xảy ra lỗi hệ thống" |

---

## 5. Thay đổi schema (chi tiết: `Component_Class_Design.md` §6)

| Migration | Nội dung |
|---|---|
| `V2__user_team_management.sql` | `"user".role` · `"user".district_id` → `ward_id` · `administrative_unit.level` bỏ `district` · `team.skill_levels` TEXT + bỏ bảng `team_tag` · `team.inactive_reason` · `team.version`, `team_member.version` · `team_member.status` thêm `REJECTED` · unique index lời mời/thành viên · `team_captain_history.reason` · index `team(status, created_at)` |
| `V3__seed_administrative_unit.sql` | Seed 5 tỉnh/thành + ~50 phường/xã (2 cấp) |

---

## 6. Ghi chú thiết kế

- **Enum UPPERCASE:** Tất cả giá trị enum trên API dùng UPPERCASE (`GOOGLE`, `FACEBOOK`, `PHONE`, `ACTIVE`, `USER`, `ADMIN`...). Implementation cần map `provider` từ DB (lowercase) sang API (UPPERCASE).
- **Hồ sơ mặc định:** Tài khoản mới có `displayName = "User " + 6 ký tự cuối userId`, `avatarUrl = null`.
- **Domain mã lỗi `TEAM`:** Đã bổ sung vào `api-convention.md` §6 (cùng ARENA/BOOKING/USER/AUTH/PAYMENT).
- **Idempotency-Key cho `POST /teams`:** Hoãn MVP — endpoint này không có khoá tự nhiên (tên đội được trùng), hỗ trợ `Idempotency-Key` cần bảng `idempotency_keys`. Ghi tech-debt.
- **Đội INACTIVE:** Chặn sửa/mời/chuyển quyền/accept/reject lời mời → `422 TEAM_015 "Đội đang tạm ngưng hoạt động"`.
- **Đội DISBANDED (từ USER-002):** các endpoint ghi 2.11–2.15, 2.18–2.20 trả thêm `422 TEAM_016`; 2.12 trả thêm `409 TEAM_014` (chạy qua retrier). Thứ tự guard: USER-002 `API_Spec.md` §2.1a.
