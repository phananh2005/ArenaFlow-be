# API Specification — User & Team Management

<!-- File này được publish cho FE (ADR-0010). Viết theo design-standards.md §11:
     không tên class/bảng/cột/migration/transaction/khoá/số truy vấn; không trỏ Component_Class_Design.md,
     Processing_Flow.md, docs/database/. Chi tiết nội bộ BE ghi ở hai file đó. -->

| Mục | Giá trị |
|---|---|
| Mã chức năng | `USER-001` |
| Phạm vi tầng | Backend (Spring Boot REST API) |
| Repo | ArenaFlow-be |
| Quy ước áp dụng | `api-convention.md` (envelope, phân trang, định dạng lỗi, kiểu dữ liệu) |
| Spec nghiệp vụ | `docs/agent/business-spec/SYSTEM-001/` (UC-U01..U03, UC-T01..T05, BR-U01..U06, BR-T01..T06) |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-02 · v1.0 (baseline) · hiện tại **v3.7** (solution-architect · 2026-11-10) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan

Đăng ký/đăng nhập (Google, Facebook, số điện thoại — hiện là **mock**), hồ sơ cá nhân, xoá tài khoản; tạo và quản lý đội, mời thành viên, chấp nhận/từ chối lời mời, rời đội, chuyển quyền đội trưởng; Admin tạm ngưng/kích hoạt lại đội.

**Mock xác thực:** BE không xác minh với Google/Facebook và không gửi SMS — ai biết email/số điện thoại đều đăng nhập được. Chỉ dùng ở môi trường nội bộ (ADR-0001 §"Sửa đổi 2026-11-04").

**Ngoài phạm vi:** thông báo (lời mời, chuyển quyền…) · khôi phục tài khoản đã xoá · liên kết nhiều cách đăng nhập vào một tài khoản. Giải tán đội và lịch sử thành viên cho Admin: USER-002 `API_Spec.md`.

### 1.1. Quy ước chung cho mọi endpoint

- **Base path** `/api/v1`, JSON, field camelCase. Thành công: body bọc trong `data`; danh sách thêm `pagination` (`api-convention.md` §4, §7).
- **Xác thực:** header `Authorization: Bearer <accessToken>` cho mọi endpoint trừ `/api/v1/auth/*`. Access token hết hạn sau `expiresIn` giây (3600). Nhóm `/api/v1/auth/*` **bỏ qua** header `Authorization`: gửi kèm token sai/hết hạn không gây 401 — FE giữ interceptor gắn token cho mọi request được, kể cả `/auth/refresh`.
- **ID:** UUID dạng chuỗi (UUID v7 — ADR-0008; id tạo trước đó có thể là phiên bản khác). Không suy thứ tự thời gian từ id — dùng `createdAt`.
- **Thời gian:** chuỗi ISO-8601 kèm offset (vd `2026-11-02T14:00:00Z` hoặc `2026-11-02T21:00:00+07:00` — cùng một thời điểm). Hiện một số field có thể trả offset khác `Z` (vd `createdAt`/`updatedAt` ở 2.8, `invitedAt` ở 2.12, `transferredAt` ở 2.15, `timestamp` của lỗi); FE luôn đọc offset trong chuỗi, không giả định `Z`. Ngày sinh: `YYYY-MM-DD`.
- **Field `null`:** response luôn có đủ field; field không có giá trị trả `null` (không bị lược bỏ).
- **Enum:** giá trị viết HOA (`ACTIVE`, `GOOGLE`…), trừ nhãn tiếng Việt (`preferredPosition`, `skillLevels`). Danh sách đầy đủ: §4.1.
- **Chuỗi:** giới hạn độ dài tính trên chuỗi FE gửi. BE cắt khoảng trắng đầu/cuối trước khi lưu ở `displayName`, `teamName`, `reason` (tạm ngưng đội).
- **PATCH:** field vắng mặt hoặc `null` → giữ nguyên giá trị cũ.
- **Phân trang:** `page` (≥ 1, mặc định 1), `pageSize` (1..100, mặc định 20). Thứ tự sắp xếp cố định theo từng endpoint, chưa có tham số `sort`. Trang vượt quá → `data: []`.
- **User đã xoá tài khoản** (ADR-0002): ở mọi chỗ hiển thị người khác — `captainName`, `fromUserName`, `toUserName`, `displayName`/`avatarUrl` của thành viên, `disbandedByName` — trả tên `"Người dùng đã xóa"` và ảnh `null`; id giữ nguyên.
- **Thứ tự lỗi:** 401 luôn trả trước 400. Bảng lỗi mỗi endpoint xếp theo thứ tự BE kiểm — nhiều điều kiện cùng sai thì nhận mã ở dòng trên cùng (nguồn: USER-002 `API_Spec.md` §2.1a).

### 1.2. Lỗi chung

Envelope lỗi (`api-convention.md` §4.4, §11). `details` thường là `null`; `validationErrors` là `[]` khi không phải lỗi field.

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Dữ liệu không hợp lệ",
    "details": null,
    "timestamp": "2026-11-10T14:00:00+07:00",
    "path": "/api/v1/auth/send-otp",
    "validationErrors": [ { "field": "phone", "message": "…" } ]
  }
}
```

`validationErrors[].message` do bộ kiểm tra sinh, nội dung không cố định — FE gắn lỗi vào ô nhập theo `field`, tự chọn câu hiển thị.

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Sai định dạng/độ dài/enum, thiếu field bắt buộc, JSON sai cú pháp, thiếu body, id trên path không phải UUID, query param sai (`field` = tên tham số, vd `page`) | Hiện lỗi tại ô theo `field`; không gửi lại nguyên request |
| 401 | `AUTH_001` | Thiếu access token, token sai, hết hạn, hoặc dùng refresh token thay access token (endpoint không thuộc `/auth/*`) | Gọi `POST /auth/refresh` (2.4) rồi thử lại **một** lần; refresh lỗi → về màn đăng nhập |
| 403 | `AUTH_004` | Gọi `/api/v1/admin/*` khi tài khoản không phải `ADMIN` | Ẩn chức năng Admin với `role = USER` |
| 404 | `SYS_001` | URL không khớp endpoint nào (đã đăng nhập) | Lỗi lập trình FE — kiểm tra path |
| 405 | `SYS_002` | Sai HTTP method; response có header `Allow` | Lỗi lập trình FE — kiểm tra method |
| 415 | `SYS_003` | `Content-Type` không phải `application/json` | Gửi `Content-Type: application/json` |
| 4xx khác | `SYS_004` | Lỗi request khác do hệ thống phát hiện (giữ HTTP status gốc) | Hiện thông báo chung "Yêu cầu không hợp lệ" |
| 500 (hoặc 5xx gốc, vd 503) | `SYS_005` | Lỗi ngoài dự kiến; không kèm chi tiết | Hiện "Đã xảy ra lỗi hệ thống", cho thử lại sau |
| 409 | `TEAM_014` | Thao tác ghi đụng độ với thao tác khác trên cùng dữ liệu xảy ra cùng lúc (hiếm). Chỉ ở các endpoint có dòng `TEAM_014` trong bảng lỗi | Hiện message; tải lại dữ liệu đang hiển thị (chi tiết đội, danh sách…) rồi cho người dùng bấm lại |

Bảng **Lỗi** ở từng endpoint chỉ liệt kê mã riêng của endpoint đó; các mã trên (trừ `TEAM_014`) áp dụng cho mọi endpoint.

## 2. Endpoints

| # | Method + Path | Dùng khi | Ai gọi được | Thành công |
|---|---|---|---|---|
| 2.1 | `POST /api/v1/auth/oauth` | Đăng nhập/đăng ký bằng Google hoặc Facebook | Không cần đăng nhập | 201 / 200 |
| 2.2 | `POST /api/v1/auth/send-otp` | Bấm "Gửi mã" ở màn đăng nhập bằng số điện thoại | Không cần đăng nhập | 200 |
| 2.3 | `POST /api/v1/auth/verify-otp` | Nhập mã OTP để đăng nhập/đăng ký | Không cần đăng nhập | 201 / 200 |
| 2.4 | `POST /api/v1/auth/refresh` | Access token hết hạn (nhận 401 `AUTH_001`) | Người có refresh token | 200 |
| 2.5 | `GET /api/v1/profile` | Mở màn hồ sơ của tôi | Đã đăng nhập | 200 |
| 2.6 | `PATCH /api/v1/profile` | Lưu sửa hồ sơ | Đã đăng nhập | 200 |
| 2.7 | `DELETE /api/v1/profile` | Xoá tài khoản | Đã đăng nhập | 204 |
| 2.8 | `POST /api/v1/teams` | Tạo đội mới | Đã đăng nhập | 201 |
| 2.9 | `GET /api/v1/teams/{teamId}` | Mở chi tiết đội | Đã đăng nhập | 200 |
| 2.10 | `GET /api/v1/teams` | Danh sách/tìm đội | Đã đăng nhập | 200 |
| 2.11 | `PATCH /api/v1/teams/{teamId}` | Sửa thông tin đội | Đội trưởng | 200 |
| 2.12 | `POST /api/v1/teams/{teamId}/invitations` | Mời người vào đội | Đội trưởng | 201 |
| 2.13 | `POST /api/v1/invitations/{invitationId}/accept` | Chấp nhận lời mời | Người được mời | 200 |
| 2.14 | `POST /api/v1/invitations/{invitationId}/reject` | Từ chối lời mời | Người được mời | 204 |
| 2.15 | `POST /api/v1/teams/{teamId}/captain/transfer` | Chuyển quyền đội trưởng | Đội trưởng | 200 |
| 2.16 | `GET /api/v1/teams/{teamId}/captain/history` | Xem lịch sử đội trưởng | Đã đăng nhập | 200 |
| 2.17 | `GET /api/v1/teams/{teamId}/members` | Xem danh sách thành viên | Đã đăng nhập | 200 |
| 2.18 | `POST /api/v1/teams/{teamId}/members/leave` | Rời đội | Thành viên (không phải đội trưởng) | 204 |
| 2.19 | `PATCH /api/v1/admin/teams/{teamId}/mark-inactive` | Admin tạm ngưng đội | Admin | 200 |
| 2.20 | `PATCH /api/v1/admin/teams/{teamId}/reactivate` | Admin kích hoạt lại đội | Admin | 200 |
| 2.21 | `GET /api/v1/admin/teams` | Admin tìm đội | Admin | 200 |

`201` không kèm header `Location` — lấy id từ body (`api-convention.md` §5).

---

### 2.1. Đăng nhập/đăng ký Google, Facebook — `POST /api/v1/auth/oauth`

**Dùng khi:** người dùng chọn "Đăng nhập bằng Google/Facebook". Chưa có tài khoản → tạo mới (201, `isNewUser = true`); đã có → đăng nhập (200, `isNewUser = false`) — UC-U01.
**Ai gọi được:** không cần đăng nhập.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `provider` | string (enum) | Có | `GOOGLE` \| `FACEBOOK` | `"GOOGLE"` |
| `oauthToken` | string | Có | Không rỗng. Mock: BE không xác minh | `"any-non-blank-token"` |
| `email` | string | Có | Email hợp lệ, ≤ 255, không có khoảng trắng đầu/cuối (BR-U02) | `"An.Nguyen@example.com"` |

Mock: `email` thay cho email lẽ ra lấy từ token của provider. Email không phân biệt hoa/thường (`An@Gmail.com` và `an@gmail.com` là một tài khoản). Cùng email ở Google và Facebook là **hai tài khoản khác nhau**.

```json
{ "provider": "GOOGLE", "oauthToken": "any-non-blank-token", "email": "An.Nguyen@example.com" }
```

**Response 201 (tạo mới) / 200 (đã có)** — `AuthResponse` (§4.2)

```json
{
  "data": {
    "userId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5a01",
    "role": "USER",
    "isNewUser": true,
    "accessToken": "eyJhbGciOiJIUzI1NiJ9…",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9…",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 403 | `AUTH_003` | Tài khoản của email này đã bị xoá | Báo "Tài khoản đã bị xoá"; không cho đăng nhập (chưa có khôi phục) |

---

### 2.2. Gửi OTP — `POST /api/v1/auth/send-otp`

**Dùng khi:** người dùng nhập số điện thoại và bấm "Gửi mã".
**Ai gọi được:** không cần đăng nhập.

Mock: không gửi SMS, BE không lưu mã. `expiresIn` chỉ để FE hiển thị đếm ngược.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `phone` | string | Có | Đúng 10 chữ số, bắt đầu bằng `0` (`^0\d{9}$`, BR-U01) | `"0987654321"` |

```json
{ "phone": "0987654321" }
```

**Response 200**

```json
{ "data": { "phone": "0987654321", "expiresIn": 300 } }
```

**Lỗi:** chỉ lỗi chung (§1.2).

---

### 2.3. Xác thực OTP — `POST /api/v1/auth/verify-otp`

**Dùng khi:** người dùng nhập mã OTP. Đăng ký-hoặc-đăng nhập như 2.1, cách đăng nhập `PHONE`; tài khoản mới có `phoneNumber` = số đã nhập.
**Ai gọi được:** không cần đăng nhập.

Mock: mọi mã 6 chữ số đều đúng; BE không kiểm đã gọi 2.2 trước hay chưa.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `phone` | string | Có | `^0\d{9}$` | `"0987654321"` |
| `otp` | string | Có | Đúng 6 chữ số (`^\d{6}$`) | `"123456"` |

```json
{ "phone": "0987654321", "otp": "123456" }
```

**Response 201 (tạo mới) / 200 (đã có)** — `AuthResponse` (§4.2), cùng dạng ví dụ 2.1.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 403 | `AUTH_003` | Tài khoản của số này đã bị xoá | Như 2.1 |

---

### 2.4. Làm mới token — `POST /api/v1/auth/refresh`

**Dùng khi:** request nhận 401 `AUTH_001` (access token hết hạn) — đổi refresh token lấy cặp token mới.
**Ai gọi được:** người có refresh token (hết hạn sau 7 ngày).

Refresh token **không** bị thu hồi sau khi dùng: dùng lại được tới khi hết hạn (ADR-0001). `role` trong response là vai trò hiện tại của tài khoản.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `refreshToken` | string | Có | Không rỗng | `"eyJhbGciOiJIUzI1NiJ9…"` |

```json
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9…" }
```

**Response 200** — `AuthResponse` (§4.2), luôn `isNewUser = false`.

```json
{
  "data": {
    "userId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5a01",
    "role": "USER",
    "isNewUser": false,
    "accessToken": "eyJhbGciOiJIUzI1NiJ9…",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9…",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 401 | `AUTH_002` | Refresh token sai, hết hạn, hoặc gửi nhầm access token | Xoá token đã lưu, về màn đăng nhập |
| 403 | `AUTH_003` | Tài khoản đã bị xoá | Xoá token đã lưu, về màn đăng nhập, báo "Tài khoản đã bị xoá" |

---

### 2.5. Xem hồ sơ — `GET /api/v1/profile`

**Dùng khi:** mở màn hồ sơ của tôi.
**Ai gọi được:** người đã đăng nhập (chỉ xem hồ sơ của chính mình).

**Response 200** — `ProfileResponse` (§4.2)

```json
{
  "data": {
    "userId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5a01",
    "role": "USER",
    "providers": [ { "provider": "GOOGLE", "providerUserId": "an.nguyen@example.com" } ],
    "phoneNumber": null,
    "displayName": "Nguyễn Văn A",
    "avatarUrl": "https://cdn.example.com/avatar/a.png",
    "bio": "Đá sân 7 tối thứ 4",
    "preferredPosition": "Tiền vệ",
    "dateOfBirth": "1995-05-15",
    "gender": "MALE",
    "ward": {
      "wardId": "0192f000-aaaa-7000-8000-000000000101",
      "wardName": "Phường Bến Nghé",
      "provinceId": "0192f000-aaaa-7000-8000-000000000001",
      "provinceName": "TP. Hồ Chí Minh"
    },
    "createdAt": "2026-11-01T10:00:00Z",
    "updatedAt": "2026-11-02T10:00:00Z"
  }
}
```

Tài khoản vừa tạo: `displayName` = `"User "` + 6 ký tự cuối của `userId` (vd `"User 4f5a01"`), `avatarUrl`, `bio`, `preferredPosition`, `dateOfBirth`, `gender`, `ward` = `null`.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `USER_001` | Tài khoản đã bị xoá nhưng access token còn hạn | Xoá token đã lưu, về màn đăng nhập |

---

### 2.6. Sửa hồ sơ — `PATCH /api/v1/profile`

**Dùng khi:** lưu màn sửa hồ sơ. Chỉ gửi field muốn đổi; field vắng mặt/`null` giữ nguyên.
**Ai gọi được:** người đã đăng nhập (chỉ sửa hồ sơ của chính mình).

**Request body** (mọi field tuỳ chọn)

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `displayName` | string | Không | Nếu gửi: không chỉ gồm khoảng trắng, ≤ 100 (BR-U03) | `"Nguyễn Văn A"` |
| `avatarUrl` | string | Không | URL bắt đầu bằng `http://` hoặc `https://` (chữ thường; `HTTPS://`, `ftp:`, `file:`, `javascript:` → 400), ≤ 500 | `"https://cdn.example.com/avatar/a.png"` |
| `bio` | string | Không | ≤ 1000 | `"Đá sân 7 tối thứ 4"` |
| `preferredPosition` | string (enum) | Không | `Thủ môn` \| `Hậu vệ` \| `Tiền vệ` \| `Tiền đạo` (BR-U06) | `"Tiền vệ"` |
| `dateOfBirth` | string (date) | Không | `YYYY-MM-DD`, trong quá khứ, đủ 13 tuổi (BR-U04) | `"1995-05-15"` |
| `gender` | string (enum) | Không | `MALE` \| `FEMALE` \| `OTHER` | `"MALE"` |
| `wardId` | UUID | Không | Id của **phường/xã** (không nhận tỉnh/thành) (BR-U05) | `"0192f000-aaaa-7000-8000-000000000101"` |

```json
{ "displayName": "Nguyễn Văn A", "preferredPosition": "Tiền vệ", "wardId": "0192f000-aaaa-7000-8000-000000000101" }
```

**Response 200** — `ProfileResponse` (§4.2) sau khi cập nhật, cùng dạng ví dụ 2.5.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `USER_001` | Tài khoản đã bị xoá nhưng access token còn hạn | Xoá token đã lưu, về màn đăng nhập |
| 400 | `USER_002` | `wardId` không tồn tại hoặc không phải phường/xã | Báo "Khu vực không hợp lệ", cho chọn lại khu vực |

---

### 2.7. Xoá tài khoản — `DELETE /api/v1/profile`

**Dùng khi:** người dùng xác nhận xoá tài khoản (UC-U03, sửa bởi USER-002; ADR-0002, ADR-0005).
**Ai gọi được:** người đã đăng nhập.

- Còn là đội trưởng của **bất kỳ** đội nào chưa giải tán (kể cả đội chỉ có mình, kể cả đội đang `INACTIVE`) → `422 USER_003`, không có gì thay đổi. Phải chuyển quyền (2.15) hoặc giải tán đội (USER-002 `API_Spec.md` §2.1) trước.
- Thành công: rời mọi đội đang tham gia (`ACTIVE` → `LEFT`), mọi lời mời đang chờ thành `REJECTED`; trạng thái các đội không đổi. Người khác thấy tên `"Người dùng đã xóa"` (§1.1).
- Gọi lại khi đã xoá → vẫn `204`.
- Sau khi xoá: access token cũ còn dùng được tới khi hết hạn (tối đa 1 giờ) nhưng `GET/PATCH /profile` trả `USER_001`, refresh/đăng nhập lại trả `AUTH_003`. FE nên xoá token đã lưu ngay sau `204`.

**Request:** không body.

**Response 204:** không body.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 422 | `USER_003` | Còn là đội trưởng của đội chưa giải tán | Hiện message; dẫn tới đội để chuyển quyền hoặc giải tán |
| 409 | `TEAM_014` | Đụng độ với thao tác khác cùng lúc (§1.2) | Như §1.2 |

---

### 2.8. Tạo đội — `POST /api/v1/teams`

**Dùng khi:** người dùng tạo đội mới (UC-T01). Người tạo thành đội trưởng; lịch sử đội trưởng (2.16) có bản ghi đầu tiên với `fromUserId = null`.
**Ai gọi được:** người đã đăng nhập.

Không chống gửi trùng: tên đội được trùng nên gửi 2 lần tạo 2 đội — FE khoá nút "Tạo" khi đang chờ response.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamName` | string | Có | Không chỉ gồm khoảng trắng, ≤ 100, được trùng tên đội khác (BR-T01) | `"Team Alpha"` |
| `avatarUrl` | string | Không | Như `avatarUrl` của 2.6, ≤ 500 | `null` |
| `description` | string | Không | ≤ 1000 | `"Đá giao lưu tối thứ 4"` |
| `skillLevels` | string[] | Không | ≤ 3 phần tử, không trùng, mỗi phần tử thuộc 9 nhãn ở §4.1 (BR-T02). Không gửi → `[]` | `["Nhiệt tình", "Rèn luyện"]` |

```json
{ "teamName": "Team Alpha", "description": "Đá giao lưu tối thứ 4", "skillLevels": ["Nhiệt tình", "Rèn luyện"] }
```

**Response 201** — `TeamResponse` (§4.2)

```json
{
  "data": {
    "teamId": "0193a1c0-1111-7abc-9def-0123456789ab",
    "teamName": "Team Alpha",
    "avatarUrl": null,
    "description": "Đá giao lưu tối thứ 4",
    "skillLevels": ["Nhiệt tình", "Rèn luyện"],
    "status": "ACTIVE",
    "inactiveReason": null,
    "captainId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5a01",
    "captainName": "Nguyễn Văn A",
    "memberCount": 1,
    "createdBy": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5a01",
    "createdAt": "2026-11-02T18:00:00+07:00",
    "updatedAt": "2026-11-02T18:00:00+07:00",
    "disbandedAt": null,
    "disbandedById": null,
    "disbandedByName": null,
    "disbandReason": null
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `USER_001` | Tài khoản đã bị xoá nhưng access token còn hạn | Xoá token đã lưu, về màn đăng nhập |

---

### 2.9. Chi tiết đội — `GET /api/v1/teams/{teamId}`

**Dùng khi:** mở màn chi tiết đội. Xem được đội ở mọi trạng thái (`ACTIVE`, `INACTIVE`, `DISBANDED`).
**Ai gọi được:** người đã đăng nhập.

**Path params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` | UUID | Có | — | `"0193a1c0-1111-7abc-9def-0123456789ab"` |

**Response 200** — `TeamResponse` (§4.2). Ví dụ đội đang bị Admin tạm ngưng:

```json
{
  "data": {
    "teamId": "0193a1c0-1111-7abc-9def-0123456789ab",
    "teamName": "Team Alpha",
    "avatarUrl": null,
    "description": "Đá giao lưu tối thứ 4",
    "skillLevels": ["Nhiệt tình", "Rèn luyện"],
    "status": "INACTIVE",
    "inactiveReason": "Vi phạm quy định đặt tên",
    "captainId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5a01",
    "captainName": "Nguyễn Văn A",
    "memberCount": 5,
    "createdBy": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5a01",
    "createdAt": "2026-11-02T11:00:00Z",
    "updatedAt": "2026-11-05T09:00:00Z",
    "disbandedAt": null,
    "disbandedById": null,
    "disbandedByName": null,
    "disbandReason": null
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `TEAM_001` | Không có đội với `teamId` này | Báo "Không tìm thấy đội", quay về danh sách |

---

### 2.10. Danh sách đội — `GET /api/v1/teams`

**Dùng khi:** màn danh sách/tìm đội. Trả mọi đội trong hệ thống theo trạng thái (không phải "đội của tôi").
**Ai gọi được:** người đã đăng nhập.

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `status` | string (enum) | Không | `ACTIVE` \| `INACTIVE` \| `DISBANDED` \| `ALL`; mặc định `ACTIVE` | `ACTIVE` |
| `page` | integer | Không | ≥ 1, mặc định 1 | `1` |
| `pageSize` | integer | Không | 1..100, mặc định 20 | `20` |

**Sắp xếp:** `createdAt` mới nhất trước, cùng thời điểm thì `teamId` giảm dần.

```
GET /api/v1/teams?status=ACTIVE&page=1&pageSize=20
```

**Response 200** — `data`: `TeamSummaryResponse[]` (§4.2) + `pagination`

```json
{
  "data": [
    {
      "teamId": "0193a1c0-1111-7abc-9def-0123456789ab",
      "teamName": "Team Alpha",
      "avatarUrl": null,
      "skillLevels": ["Nhiệt tình", "Rèn luyện"],
      "status": "ACTIVE",
      "captainName": "Nguyễn Văn A",
      "memberCount": 5,
      "createdAt": "2026-11-02T11:00:00Z",
      "disbandedByName": null
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi:** chỉ lỗi chung (§1.2) — `status`/`page`/`pageSize` sai → `400 VALIDATION_ERROR`.

---

### 2.11. Sửa thông tin đội — `PATCH /api/v1/teams/{teamId}`

**Dùng khi:** đội trưởng lưu màn sửa đội. Chỉ gửi field muốn đổi; `skillLevels` có mặt → **thay toàn bộ** danh sách (`[]` = xoá hết).
**Ai gọi được:** đội trưởng của đội; đội phải đang `ACTIVE`.

**Path params:** `teamId` (UUID) — như 2.9.

**Request body** (mọi field tuỳ chọn; ràng buộc như 2.8)

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamName` | string | Không | Nếu gửi: không chỉ gồm khoảng trắng, ≤ 100 | `"Team Alpha FC"` |
| `avatarUrl` | string | Không | Như 2.8 | `"https://cdn.example.com/team/alpha.png"` |
| `description` | string | Không | ≤ 1000 | `"Đá giao lưu tối thứ 4, thứ 6"` |
| `skillLevels` | string[] | Không | Như 2.8 | `["Thi đấu"]` |

```json
{ "teamName": "Team Alpha FC", "skillLevels": ["Thi đấu"] }
```

**Response 200** — `TeamResponse` (§4.2) sau khi cập nhật, cùng dạng ví dụ 2.8.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `TEAM_001` | Không có đội | Quay về danh sách đội |
| 422 | `TEAM_016` | Đội đã giải tán | Tải lại chi tiết đội; ẩn nút sửa |
| 422 | `TEAM_015` | Đội đang bị Admin tạm ngưng (`INACTIVE`) | Tải lại chi tiết đội; ẩn nút sửa, hiện `inactiveReason` |
| 403 | `TEAM_002` | Người gọi không phải đội trưởng (vd vừa chuyển quyền) | Tải lại chi tiết đội; ẩn nút sửa |
| 409 | `TEAM_014` | Đụng độ với thao tác khác cùng lúc (§1.2) | Như §1.2 |

---

### 2.12. Mời thành viên — `POST /api/v1/teams/{teamId}/invitations`

**Dùng khi:** đội trưởng mời một người vào đội (UC-T02). Lời mời không hết hạn; một người được nhận lời mời từ nhiều đội cùng lúc. Người đã rời đội hoặc đã từ chối được mời lại (tạo lời mời mới, `invitationId` mới).
**Ai gọi được:** đội trưởng của đội; đội phải đang `ACTIVE`.

**Path params:** `teamId` (UUID) — như 2.9.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `userId` | UUID | Có | Id người được mời | `"0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5b02"` |

```json
{ "userId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5b02" }
```

**Response 201** — `InvitationResponse` (§4.2)

```json
{
  "data": {
    "invitationId": "0193a1d0-2222-7abc-9def-0123456789cd",
    "teamId": "0193a1c0-1111-7abc-9def-0123456789ab",
    "teamName": "Team Alpha",
    "userId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5b02",
    "status": "PENDING",
    "invitedAt": "2026-11-02T19:30:00+07:00"
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `TEAM_001` | Không có đội | Quay về danh sách đội |
| 422 | `TEAM_016` | Đội đã giải tán | Tải lại chi tiết đội; ẩn nút mời |
| 422 | `TEAM_015` | Đội đang `INACTIVE` | Tải lại chi tiết đội; ẩn nút mời |
| 403 | `TEAM_002` | Người gọi không phải đội trưởng | Tải lại chi tiết đội; ẩn nút mời |
| 404 | `USER_001` | Người được mời không tồn tại hoặc đã xoá tài khoản | Báo "Không tìm thấy người dùng" |
| 409 | `TEAM_003` | Người này đã là thành viên hoặc đang có lời mời chờ từ đội (kể cả do bấm gửi 2 lần) | Báo "đã là thành viên hoặc đã được mời"; coi như đã mời |
| 409 | `TEAM_014` | Đụng độ với thao tác khác cùng lúc (§1.2) | Như §1.2 |

---

### 2.13. Chấp nhận lời mời — `POST /api/v1/invitations/{invitationId}/accept`

**Dùng khi:** người được mời bấm "Tham gia" (UC-T03). Lời mời chuyển `PENDING` → `ACTIVE`, người dùng thành thành viên.
**Ai gọi được:** chính người được mời; đội phải đang `ACTIVE`.

**Path params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `invitationId` | UUID | Có | `invitationId` từ response 2.12 | `"0193a1d0-2222-7abc-9def-0123456789cd"` |

**Request:** không body.

**Response 200** — `MemberResponse` (§4.2); `memberId` = `invitationId`.

```json
{
  "data": {
    "memberId": "0193a1d0-2222-7abc-9def-0123456789cd",
    "teamId": "0193a1c0-1111-7abc-9def-0123456789ab",
    "userId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5b02",
    "displayName": "Trần Văn B",
    "avatarUrl": null,
    "role": "MEMBER",
    "status": "ACTIVE",
    "joinedAt": "2026-11-02T13:00:00Z"
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `TEAM_004` | Không có lời mời với id này | Báo "Không tìm thấy lời mời", gỡ khỏi màn hình |
| 403 | `TEAM_005` | Người gọi không phải người được mời | Gỡ lời mời khỏi màn hình |
| 422 | `TEAM_016` | Đội đã giải tán | Báo "Đội đã giải tán", gỡ lời mời khỏi màn hình |
| 422 | `TEAM_015` | Đội đang `INACTIVE` (lời mời vẫn chờ) | Báo "Đội đang tạm ngưng hoạt động"; giữ lời mời, thử lại sau |
| 409 | `TEAM_006` | Lời mời đã được xử lý (đã chấp nhận/từ chối, hoặc bấm 2 lần) | Tải lại; gỡ lời mời khỏi danh sách chờ |
| 409 | `TEAM_014` | Đụng độ với thao tác khác cùng lúc (§1.2) | Như §1.2 |

---

### 2.14. Từ chối lời mời — `POST /api/v1/invitations/{invitationId}/reject`

**Dùng khi:** người được mời bấm "Từ chối" (UC-T03). Lời mời chuyển `PENDING` → `REJECTED`; đội trưởng mời lại được sau đó.
**Ai gọi được:** chính người được mời; đội phải đang `ACTIVE`.

**Path params:** `invitationId` (UUID) — như 2.13.

**Request:** không body.

**Response 204:** không body.

**Lỗi:** giống bảng lỗi 2.13 (`TEAM_004`, `TEAM_005`, `TEAM_016`, `TEAM_015`, `TEAM_006`, `TEAM_014`, cùng thứ tự và cách xử lý).

---

### 2.15. Chuyển quyền đội trưởng — `POST /api/v1/teams/{teamId}/captain/transfer`

**Dùng khi:** đội trưởng chọn một thành viên làm đội trưởng mới (UC-T04). Có hiệu lực ngay; người nhận không được từ chối (BR-T03, BR-T05). Đội trưởng cũ thành thành viên thường. Ghi vào lịch sử đội trưởng (2.16).
**Ai gọi được:** đội trưởng của đội; đội phải đang `ACTIVE`.

**Path params:** `teamId` (UUID) — như 2.9.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `newCaptainId` | UUID | Có | `userId` của một thành viên đang hoạt động, khác người gọi | `"0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5b02"` |
| `reason` | string | Không | ≤ 500 | `"Bận công tác dài ngày"` |

```json
{ "newCaptainId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5b02", "reason": "Bận công tác dài ngày" }
```

**Response 200** — `CaptainTransferResponse` (§4.2). Sau đó FE tải lại chi tiết đội (2.9) và danh sách thành viên (2.17) để cập nhật vai trò.

```json
{
  "data": {
    "teamId": "0193a1c0-1111-7abc-9def-0123456789ab",
    "fromUserId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5a01",
    "toUserId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5b02",
    "reason": "Bận công tác dài ngày",
    "transferredAt": "2026-11-02T21:00:00+07:00"
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `TEAM_001` | Không có đội | Quay về danh sách đội |
| 422 | `TEAM_016` | Đội đã giải tán | Tải lại chi tiết đội; ẩn nút chuyển quyền |
| 422 | `TEAM_015` | Đội đang `INACTIVE` | Tải lại chi tiết đội; ẩn nút chuyển quyền |
| 403 | `TEAM_002` | Người gọi không còn là đội trưởng | Tải lại chi tiết đội; ẩn nút chuyển quyền |
| 422 | `TEAM_008` | `newCaptainId` là chính người gọi | Không cho chọn chính mình trong danh sách |
| 422 | `TEAM_007` | Người nhận không phải thành viên đang hoạt động (vd vừa rời đội). Đội không còn thành viên nào khác cũng rơi vào mã này hoặc `TEAM_008` (UC-T04 luồng 2a) | Tải lại danh sách thành viên, cho chọn lại |
| 409 | `TEAM_014` | Đụng độ với thao tác khác cùng lúc (§1.2) | Như §1.2 |

---

### 2.16. Lịch sử đội trưởng — `GET /api/v1/teams/{teamId}/captain/history`

**Dùng khi:** xem các lần đổi đội trưởng của đội; đọc được ở mọi trạng thái đội.
**Ai gọi được:** người đã đăng nhập.

**Path params:** `teamId` (UUID) — như 2.9. **Query params:** `page`, `pageSize` (§1.1).

**Sắp xếp:** `transferredAt` mới nhất trước, cùng thời điểm thì `historyId` giảm dần.

**Response 200** — `data`: `CaptainHistoryResponse[]` (§4.2) + `pagination`. Bản ghi `fromUserId = null` là lúc tạo đội.

```json
{
  "data": [
    {
      "historyId": "0193a1e0-3333-7abc-9def-0123456789ef",
      "teamId": "0193a1c0-1111-7abc-9def-0123456789ab",
      "fromUserId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5a01",
      "fromUserName": "Nguyễn Văn A",
      "toUserId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5b02",
      "toUserName": "Trần Văn B",
      "reason": "Bận công tác dài ngày",
      "transferredAt": "2026-11-02T14:00:00Z"
    },
    {
      "historyId": "0193a1e0-3333-7abc-9def-0123456789e0",
      "teamId": "0193a1c0-1111-7abc-9def-0123456789ab",
      "fromUserId": null,
      "fromUserName": null,
      "toUserId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5a01",
      "toUserName": "Nguyễn Văn A",
      "reason": null,
      "transferredAt": "2026-11-02T11:00:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 2 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `TEAM_001` | Không có đội | Quay về danh sách đội |

---

### 2.17. Danh sách thành viên — `GET /api/v1/teams/{teamId}/members`

**Dùng khi:** xem thành viên đang hoạt động của đội. Chỉ gồm thành viên `ACTIVE` (không gồm lời mời chờ, người đã rời, lời mời bị từ chối). Đội đã giải tán → danh sách rỗng (lịch sử thành viên cho Admin: USER-002 `API_Spec.md` §2.4).
**Ai gọi được:** người đã đăng nhập.

**Path params:** `teamId` (UUID) — như 2.9. **Query params:** `page`, `pageSize` (§1.1).

**Sắp xếp:** đội trưởng trước, rồi `joinedAt` cũ nhất trước, rồi `userId` tăng dần.

**Response 200** — `data`: `MemberResponse[]` (§4.2) + `pagination`

```json
{
  "data": [
    {
      "memberId": "0193a1c0-1111-7abc-9def-0123456789a0",
      "teamId": "0193a1c0-1111-7abc-9def-0123456789ab",
      "userId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5a01",
      "displayName": "Nguyễn Văn A",
      "avatarUrl": "https://cdn.example.com/avatar/a.png",
      "role": "CAPTAIN",
      "status": "ACTIVE",
      "joinedAt": "2026-11-02T11:00:00Z"
    },
    {
      "memberId": "0193a1d0-2222-7abc-9def-0123456789cd",
      "teamId": "0193a1c0-1111-7abc-9def-0123456789ab",
      "userId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5b02",
      "displayName": "Trần Văn B",
      "avatarUrl": null,
      "role": "MEMBER",
      "status": "ACTIVE",
      "joinedAt": "2026-11-02T13:00:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 2 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `TEAM_001` | Không có đội | Quay về danh sách đội |

---

### 2.18. Rời đội — `POST /api/v1/teams/{teamId}/members/leave`

**Dùng khi:** thành viên bấm "Rời đội". Rời được cả khi đội đang `INACTIVE`. Đội trưởng phải chuyển quyền (2.15) trước.
**Ai gọi được:** thành viên `ACTIVE` của đội, không phải đội trưởng.

**Path params:** `teamId` (UUID) — như 2.9.

**Request:** không body.

**Response 204:** không body.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `TEAM_001` | Không có đội | Quay về danh sách đội |
| 422 | `TEAM_016` | Đội đã giải tán (người gọi đã tự rời khi giải tán) | Tải lại chi tiết đội; ẩn nút rời đội |
| 422 | `TEAM_010` | Người gọi không phải thành viên đang hoạt động | Tải lại chi tiết đội; ẩn nút rời đội |
| 422 | `TEAM_009` | Người gọi là đội trưởng | Hiện message; dẫn tới chuyển quyền (2.15) |
| 409 | `TEAM_014` | Đụng độ với thao tác khác cùng lúc (§1.2) | Như §1.2 |

---

### 2.19. Admin tạm ngưng đội — `PATCH /api/v1/admin/teams/{teamId}/mark-inactive`

**Dùng khi:** Admin khoá một đội vi phạm (UC-T05). Đội `ACTIVE` → `INACTIVE`; thành viên vẫn xem và rời đội được, đội trưởng chỉ còn giải tán được (§3.1).
**Ai gọi được:** Admin (`role = ADMIN`).

**Path params:** `teamId` (UUID) — như 2.9.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `reason` | string | Có | Không chỉ gồm khoảng trắng, ≤ 500 | `"Vi phạm quy định đặt tên"` |

```json
{ "reason": "Vi phạm quy định đặt tên" }
```

**Response 200** — `TeamResponse` (§4.2) với `status = "INACTIVE"`, `inactiveReason` = lý do đã gửi; cùng dạng ví dụ 2.9.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 403 | `AUTH_004` | Người gọi không phải Admin | Ẩn chức năng Admin |
| 404 | `TEAM_001` | Không có đội | Tải lại danh sách Admin |
| 422 | `TEAM_016` | Đội đã giải tán | Tải lại; ẩn nút tạm ngưng |
| 409 | `TEAM_011` | Đội đã `INACTIVE` | Tải lại; hiện nút kích hoạt lại thay cho tạm ngưng |
| 409 | `TEAM_014` | Đụng độ với thao tác khác cùng lúc (§1.2) | Như §1.2 |

---

### 2.20. Admin kích hoạt lại đội — `PATCH /api/v1/admin/teams/{teamId}/reactivate`

**Dùng khi:** Admin mở khoá đội. Đội `INACTIVE` → `ACTIVE`, `inactiveReason` về `null`. Đội phải còn đội trưởng đang hoạt động.
**Ai gọi được:** Admin.

**Path params:** `teamId` (UUID) — như 2.9.

**Request:** không body.

**Response 200** — `TeamResponse` (§4.2) với `status = "ACTIVE"`, `inactiveReason = null`; cùng dạng ví dụ 2.8.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 403 | `AUTH_004` | Người gọi không phải Admin | Ẩn chức năng Admin |
| 404 | `TEAM_001` | Không có đội | Tải lại danh sách Admin |
| 422 | `TEAM_016` | Đội đã giải tán | Tải lại; ẩn nút kích hoạt lại |
| 409 | `TEAM_012` | Đội đang `ACTIVE` | Tải lại; hiện nút tạm ngưng thay cho kích hoạt lại |
| 422 | `TEAM_013` | Đội không còn đội trưởng đang hoạt động (`captainId = null`) | Hiện message; không cho kích hoạt lại |
| 409 | `TEAM_014` | Đụng độ với thao tác khác cùng lúc (§1.2) | Như §1.2 |

---

### 2.21. Admin tìm đội — `GET /api/v1/admin/teams`

**Dùng khi:** màn quản lý đội của Admin (UC-T05 bước 1).
**Ai gọi được:** Admin.

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `status` | string (enum) | Không | `ACTIVE` \| `INACTIVE` \| `DISBANDED` \| `ALL`; mặc định `ALL` (gồm cả đội đã giải tán) | `INACTIVE` |
| `page` | integer | Không | ≥ 1, mặc định 1 | `1` |
| `pageSize` | integer | Không | 1..100, mặc định 20 | `20` |

**Sắp xếp:** như 2.10.

**Response 200** — `data`: `AdminTeamSummaryResponse[]` (§4.2) + `pagination`

```json
{
  "data": [
    {
      "teamId": "0193a1c0-1111-7abc-9def-0123456789ab",
      "teamName": "Team Alpha",
      "avatarUrl": null,
      "skillLevels": ["Nhiệt tình", "Rèn luyện"],
      "status": "INACTIVE",
      "inactiveReason": "Vi phạm quy định đặt tên",
      "captainName": "Nguyễn Văn A",
      "memberCount": 5,
      "createdAt": "2026-11-02T11:00:00Z",
      "disbandedByName": null
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 403 | `AUTH_004` | Người gọi không phải Admin | Ẩn chức năng Admin |

---

## 3. Hành động theo trạng thái

### 3.1. Đội

Mọi người đã đăng nhập luôn xem được chi tiết đội (2.9), danh sách thành viên (2.17), lịch sử đội trưởng (2.16) ở mọi trạng thái. Bảng dưới chỉ liệt kê thao tác ghi; ngoài bảng → BE trả mã ở cột "Bị chặn".

| Trạng thái | Đội trưởng | Thành viên | Người có lời mời chờ | Admin | Bị chặn |
|---|---|---|---|---|---|
| `ACTIVE` | Sửa đội (2.11) · Mời (2.12) · Chuyển quyền (2.15) · Giải tán (USER-002 §2.1) | Rời đội (2.18) | Chấp nhận (2.13) · Từ chối (2.14) | Tạm ngưng (2.19) | Đội trưởng rời đội → `TEAM_009`; Admin kích hoạt lại → `TEAM_012` |
| `INACTIVE` | Giải tán (USER-002 §2.1) | Rời đội (2.18) | — (lời mời vẫn chờ) | Kích hoạt lại (2.20, cần còn đội trưởng) | Sửa/mời/chuyển quyền/chấp nhận/từ chối → `TEAM_015`; đội trưởng rời đội → `TEAM_009`; Admin tạm ngưng lần nữa → `TEAM_011` |
| `DISBANDED` | — | — | — | — | Mọi thao tác ghi → `TEAM_016` |

Xoá tài khoản (2.7) bị chặn `USER_003` khi người gọi là đội trưởng của đội `ACTIVE` hoặc `INACTIVE`.

### 3.2. Lời mời và tư cách thành viên

Lời mời và tư cách thành viên là cùng một đối tượng: `invitationId` (2.12) = `memberId` (2.13, 2.17).

| Trạng thái | Nghĩa | Người được mời / thành viên | Đội trưởng | Chuyển sang |
|---|---|---|---|---|
| `PENDING` | Lời mời đang chờ | Chấp nhận (2.13) · Từ chối (2.14) | — (chưa có API huỷ lời mời) | `ACTIVE` (chấp nhận) · `REJECTED` (từ chối, người được mời xoá tài khoản, hoặc đội bị giải tán) |
| `ACTIVE` | Đang là thành viên | `MEMBER`: rời đội (2.18). `CAPTAIN`: chuyển quyền (2.15) trước khi rời | Chuyển quyền cho thành viên `MEMBER` (2.15) | `LEFT` (rời đội, xoá tài khoản, đội bị giải tán) |
| `LEFT` | Đã rời đội | — | Mời lại (2.12) → lời mời mới | — (kết thúc) |
| `REJECTED` | Đã từ chối lời mời | — | Mời lại (2.12) → lời mời mới | — (kết thúc) |

Đổi vai trò `MEMBER` ↔ `CAPTAIN` chỉ qua chuyển quyền (2.15).

### 3.3. Tài khoản

| Trạng thái | Được làm | Bị chặn |
|---|---|---|
| Đang hoạt động | Mọi endpoint theo vai trò | — |
| Đã xoá (2.7) | — | Đăng nhập (2.1, 2.3), refresh (2.4) → `AUTH_003` · Hồ sơ (2.5, 2.6), tạo đội (2.8) → `USER_001` · Được mời (2.12) → `USER_001` |

## 4. Kiểu dữ liệu dùng chung

### 4.1. Enum

| Kiểu / enum | Giá trị | Ý nghĩa |
|---|---|---|
| `provider` | `GOOGLE` \| `FACEBOOK` \| `PHONE` | Cách đăng nhập. Request 2.1 chỉ nhận `GOOGLE` \| `FACEBOOK`; `PHONE` dùng qua 2.2–2.3 |
| `role` (tài khoản) | `USER` \| `ADMIN` | `ADMIN` được gọi `/api/v1/admin/*` |
| `role` (trong đội) | `CAPTAIN` \| `MEMBER` | Đội trưởng / thành viên thường |
| Trạng thái đội (`status`) | `ACTIVE` \| `INACTIVE` \| `DISBANDED` | Hoạt động / bị Admin tạm ngưng / đã giải tán (USER-002) — §3.1 |
| Bộ lọc đội (query `status`) | `ACTIVE` \| `INACTIVE` \| `DISBANDED` \| `ALL` | `ALL` = mọi trạng thái |
| Trạng thái lời mời/thành viên (`status`) | `PENDING` \| `ACTIVE` \| `LEFT` \| `REJECTED` | §3.2 |
| `gender` | `MALE` \| `FEMALE` \| `OTHER` | Nam / nữ / khác |
| `preferredPosition` | `Thủ môn` \| `Hậu vệ` \| `Tiền vệ` \| `Tiền đạo` | Vị trí ưa thích (BR-U06) |
| `skillLevels` (phần tử) | `Chill` \| `Nhiệt tình` \| `Máu lửa` \| `Vui chơi` \| `Rèn luyện` \| `Thi đấu` \| `Mới bắt đầu` \| `Đã có kinh nghiệm` \| `Lão làng` | Nhãn phong cách/trình độ đội (BR-T02), tối đa 3 |

### 4.2. Đối tượng response

Kiểu `datetime` = chuỗi ISO-8601 kèm offset (§1.1). "Null?" = có thể là `null`.

**`AuthResponse`** (2.1, 2.3, 2.4)

| Field | Kiểu | Null? | Ý nghĩa |
|---|---|---|---|
| `userId` | UUID | Không | Id tài khoản |
| `role` | enum | Không | `USER` \| `ADMIN` |
| `isNewUser` | boolean | Không | `true` khi request vừa tạo tài khoản (response 201) |
| `accessToken` | string | Không | Gửi trong header `Authorization: Bearer …` |
| `refreshToken` | string | Không | Dùng cho 2.4; hết hạn sau 7 ngày |
| `tokenType` | string | Không | Luôn `"Bearer"` |
| `expiresIn` | integer | Không | Số giây access token còn hiệu lực (3600) |

**`ProfileResponse`** (2.5, 2.6)

| Field | Kiểu | Null? | Ý nghĩa |
|---|---|---|---|
| `userId` | UUID | Không | |
| `role` | enum | Không | `USER` \| `ADMIN` |
| `providers` | object[] | Không | Cách đăng nhập đã liên kết: `{ "provider": "GOOGLE"\|"FACEBOOK"\|"PHONE", "providerUserId": string }` — `providerUserId` là email (chữ thường) hoặc số điện thoại |
| `phoneNumber` | string | Có | Có khi đăng ký bằng số điện thoại |
| `displayName` | string | Không | Tên hiển thị (mặc định `"User "` + 6 ký tự cuối `userId`) |
| `avatarUrl`, `bio` | string | Có | |
| `preferredPosition` | enum | Có | §4.1 |
| `dateOfBirth` | date (`YYYY-MM-DD`) | Có | |
| `gender` | enum | Có | §4.1 |
| `ward` | object | Có | `{ "wardId": UUID, "wardName": string, "provinceId": UUID, "provinceName": string }`; `null` khi chưa chọn |
| `createdAt`, `updatedAt` | datetime | Không | |

**`TeamResponse`** (2.8, 2.9, 2.11, 2.19, 2.20)

| Field | Kiểu | Null? | Ý nghĩa |
|---|---|---|---|
| `teamId` | UUID | Không | |
| `teamName` | string | Không | |
| `avatarUrl`, `description` | string | Có | |
| `skillLevels` | string[] | Không | `[]` khi không có nhãn |
| `status` | enum | Không | `ACTIVE` \| `INACTIVE` \| `DISBANDED` |
| `inactiveReason` | string | Có | Lý do Admin tạm ngưng; `null` khi chưa từng tạm ngưng hoặc đã kích hoạt lại; giữ nguyên khi đội bị giải tán |
| `captainId`, `captainName` | UUID, string | Có | Đội trưởng đang hoạt động; `null` khi đội không còn đội trưởng (vd đã giải tán) |
| `memberCount` | integer | Không | Số thành viên `ACTIVE`, gồm đội trưởng |
| `createdBy` | UUID | Không | Người tạo đội |
| `createdAt`, `updatedAt` | datetime | Không | |
| `disbandedAt`, `disbandedById`, `disbandedByName`, `disbandReason` | datetime, UUID, string, string | Có | Thông tin giải tán (USER-002 `API_Spec.md` §5.1); `null` khi chưa giải tán |

**`TeamSummaryResponse`** (2.10): `teamId`, `teamName`, `avatarUrl`, `skillLevels`, `status`, `captainName`, `memberCount`, `createdAt`, `disbandedByName` — cùng nghĩa `TeamResponse`.

**`AdminTeamSummaryResponse`** (2.21): field của `TeamSummaryResponse` + `inactiveReason`.

**`InvitationResponse`** (2.12)

| Field | Kiểu | Null? | Ý nghĩa |
|---|---|---|---|
| `invitationId` | UUID | Không | Dùng cho 2.13 / 2.14 |
| `teamId`, `teamName` | UUID, string | Không | |
| `userId` | UUID | Không | Người được mời |
| `status` | enum | Không | Luôn `PENDING` khi vừa tạo |
| `invitedAt` | datetime | Không | |

**`MemberResponse`** (2.13, 2.17)

| Field | Kiểu | Null? | Ý nghĩa |
|---|---|---|---|
| `memberId` | UUID | Không | = `invitationId` của lời mời đã chấp nhận |
| `teamId`, `userId` | UUID | Không | |
| `displayName` | string | Không | Tên thành viên (`"Người dùng đã xóa"` khi đã xoá tài khoản) |
| `avatarUrl` | string | Có | |
| `role` | enum | Không | `CAPTAIN` \| `MEMBER` |
| `status` | enum | Không | `ACTIVE` ở 2.13, 2.17 |
| `joinedAt` | datetime | Không | |

**`CaptainTransferResponse`** (2.15): `teamId`, `fromUserId` (đội trưởng cũ), `toUserId` (đội trưởng mới), `reason` (string, có thể `null`), `transferredAt` (datetime).

**`CaptainHistoryResponse`** (2.16)

| Field | Kiểu | Null? | Ý nghĩa |
|---|---|---|---|
| `historyId`, `teamId` | UUID | Không | |
| `fromUserId`, `fromUserName` | UUID, string | Có | `null` = lần tạo đội |
| `toUserId`, `toUserName` | UUID, string | Không | Người nhận quyền |
| `reason` | string | Có | |
| `transferredAt` | datetime | Không | |

## 5. Mã lỗi

| Mã | HTTP | Message | Endpoint |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Dữ liệu không hợp lệ | Mọi endpoint (§1.2) |
| `AUTH_001` | 401 | Token không hợp lệ hoặc đã hết hạn | Mọi endpoint ngoài `/auth/*` (§1.2) |
| `AUTH_002` | 401 | Refresh token không hợp lệ hoặc đã hết hạn | 2.4 |
| `AUTH_003` | 403 | Tài khoản đã bị xoá | 2.1, 2.3, 2.4 |
| `AUTH_004` | 403 | Cần quyền quản trị | 2.19–2.21 (mọi `/admin/*`) |
| `USER_001` | 404 | Không tìm thấy người dùng | 2.5, 2.6, 2.8, 2.12 |
| `USER_002` | 400 | Khu vực không hợp lệ | 2.6 |
| `USER_003` | 422 | Bạn phải chuyển quyền đội trưởng hoặc giải tán đội trước khi xóa tài khoản | 2.7 |
| `TEAM_001` | 404 | Không tìm thấy đội | 2.9, 2.11, 2.12, 2.15–2.20 |
| `TEAM_002` | 403 | Chỉ đội trưởng được thực hiện | 2.11, 2.12, 2.15 |
| `TEAM_003` | 409 | Người này đã là thành viên hoặc đã được mời | 2.12 |
| `TEAM_004` | 404 | Không tìm thấy lời mời | 2.13, 2.14 |
| `TEAM_005` | 403 | Chỉ người được mời được xử lý lời mời | 2.13, 2.14 |
| `TEAM_006` | 409 | Lời mời đã được xử lý | 2.13, 2.14 |
| `TEAM_007` | 422 | Chỉ chuyển quyền cho thành viên đang hoạt động | 2.15 |
| `TEAM_008` | 422 | Không thể chuyển quyền cho chính mình | 2.15 |
| `TEAM_009` | 422 | Đội trưởng phải chuyển quyền trước khi rời đội | 2.18 |
| `TEAM_010` | 422 | Bạn không phải thành viên của đội | 2.18 |
| `TEAM_011` | 409 | Đội đã ở trạng thái INACTIVE | 2.19 |
| `TEAM_012` | 409 | Đội đang hoạt động | 2.20 |
| `TEAM_013` | 422 | Đội không còn đội trưởng, không thể kích hoạt lại | 2.20 |
| `TEAM_014` | 409 | Dữ liệu vừa được cập nhật bởi thao tác khác, vui lòng thử lại | 2.7, 2.11–2.15, 2.18–2.20 |
| `TEAM_015` | 422 | Đội đang tạm ngưng hoạt động | 2.11–2.15 |
| `TEAM_016` | 422 | Đội đã giải tán | 2.11–2.15, 2.18–2.20 (thêm ở USER-002) |
| `SYS_001` | 404 | Không tìm thấy tài nguyên | §1.2 |
| `SYS_002` | 405 | Phương thức không được hỗ trợ | §1.2 |
| `SYS_003` | 415 | Kiểu nội dung không được hỗ trợ | §1.2 |
| `SYS_004` | 4xx (giữ status gốc) | Yêu cầu không hợp lệ | §1.2 |
| `SYS_005` | 500 (5xx gốc giữ nguyên) | Đã xảy ra lỗi hệ thống | §1.2 |

## 6. Danh sách [cần xác nhận]

Khoảng trống phạm vi nghiệp vụ: FE cần nhưng chưa có endpoint, SRS (`business-spec/SYSTEM-001/`) chưa đặc tả — cần BA bổ sung và chốt làm ở mã nào. Không chặn code BE của USER-001; mức chặn luồng FE ghi ở từng mục.

1. `[cần xác nhận]` Người được mời lấy `invitationId` từ đâu: UC-T03 bước 1 "User xem lời mời trong notification", nhưng thông báo ngoài phạm vi và chưa có endpoint "lời mời của tôi". **Chặn luồng FE 2.13–2.14.**
2. `[cần xác nhận]` Đội trưởng lấy `userId` để mời từ đâu: UC-T02 bước 2 cho nhập "ID/username", còn 2.12 chỉ nhận `userId` và chưa có endpoint tìm người dùng. **Chặn luồng FE 2.12.**
3. `[cần xác nhận]` FE lấy `wardId` từ đâu: chưa có endpoint danh mục tỉnh/thành, phường/xã. **Chặn chọn khu vực ở 2.6** (các field khác của 2.6 không bị chặn).
4. `[cần xác nhận]` Chưa có endpoint "đội của tôi": 2.10 trả mọi đội trong hệ thống. Không chặn luồng nào; FE chưa lọc được đội mình tham gia.
