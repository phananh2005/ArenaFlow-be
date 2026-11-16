# API Convention — ArenaFlow-be

> Quy ước REST API cho backend. Agent `solution-architect` MUST tuân thủ khi thiết kế endpoint.

## 1. Base URL & Versioning

```
Base: /api/v1
Version: v1 (trong path, không dùng header)
```

**Ví dụ:**
- ✅ `/api/v1/arenas`
- ❌ `/arenas` (thiếu version)
- ❌ `/api/arenas` (thiếu version number)

---

## 2. Resource Naming

### 2.1. Tên resource

- **Số nhiều** cho collection: `/arenas`, `/bookings`, `/users`
- **Danh từ**, không dùng động từ: `/arenas` ✅, `/getArenas` ❌
- **Lowercase, gạch nối** cho multi-word: `/time-slots`, `/booking-history`
- **Không** viết tắt trừ khi rất phổ biến: `/api` ✅, `/usr` ❌
- **Ngoại lệ — endpoint hành động (action sub-resource):** thao tác nghiệp vụ đổi trạng thái có guard/side-effect riêng,
  không diễn đạt tự nhiên bằng cập nhật field → `/{resource}/{id}/{dong-tu-kebab}`
  (vd `/teams/{id}/disband`, `/invitations/{id}/accept`, `/admin/teams/{id}/mark-inactive`).
  Nhóm `/auth/*` (`send-otp`, `verify-otp`, `refresh`, `oauth`) cũng theo dạng này.
  `[cần xác nhận]` quy tắc chọn `POST` hay `PATCH` — repo đang dùng cả hai.

### 2.2. Hierarchical resources

```
GET    /api/v1/arenas/{arenaId}/time-slots           # Slots của 1 sân
GET    /api/v1/users/{userId}/bookings                # Bookings của 1 user
POST   /api/v1/arenas/{arenaId}/reviews              # Tạo review cho sân
```

---

## 3. HTTP Methods

| Method | Dùng cho | Idempotent | Request Body | Response Body |
|---|---|---|---|---|
| GET | Đọc resource | ✅ | ❌ | ✅ (data) |
| POST | Tạo resource mới | ❌ | ✅ | ✅ (created resource) |
| PUT | Replace toàn bộ resource | ✅ | ✅ (full) | ✅ (updated resource) |
| PATCH | Update một phần resource | ❌ | ✅ (partial) | ✅ (updated resource) |
| DELETE | Xóa resource | ✅ | ❌ | ❌ (204 No Content — §5) |

**Lưu ý:**
- POST `/bookings` → tạo booking mới
- PUT `/bookings/{id}` → replace toàn bộ booking
- PATCH `/bookings/{id}` → update status hoặc một vài fields
- DELETE `/bookings/{id}` → soft delete (chuyển status = deleted)

---

## 4. Request & Response Format

### 4.1. Content-Type

```
Content-Type: application/json
Accept: application/json
```

### 4.2. Request Body (JSON)

**CamelCase** cho field names:

```json
{
  "arenaName": "Sân Cầu Lông ABC",
  "address": "123 Đường XYZ",
  "pricePerHour": 150000,
  "timeSlots": [...]
}
```

### 4.3. Response Envelope (SUCCESS)

**Luôn wrap** trong object `data`:

```json
{
  "data": {
    "id": "arena-123",
    "arenaName": "Sân Cầu Lông ABC",
    ...
  }
}
```

**Collection:**
```json
{
  "data": [
    { "id": "1", ... },
    { "id": "2", ... }
  ],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "totalPages": 5,
    "totalItems": 100
  }
}
```

### 4.4. Response Envelope (ERROR)

```json
{
  "error": {
    "code": "ARENA_001",
    "message": "Sân không tồn tại",
    "details": "Arena with ID 'xyz' not found",
    "timestamp": "2026-11-01T07:00:00Z",
    "path": "/api/v1/arenas/xyz"
  }
}
```

---

## 5. HTTP Status Codes

### Success (2xx)

Mã thành công chọn theo **kết quả** của thao tác, không theo HTTP method:

| Mã | Khi nào | Body |
|---|---|---|
| `201 Created` | Request **tạo ra tài nguyên mới** (kể cả đăng ký-hoặc-đăng nhập khi lần đầu tạo user) | Có — tài nguyên vừa tạo. **Không** trả header `Location` (client lấy id từ body) |
| `200 OK` | Thành công và **có body** theo tiêu chí bên dưới | Có — bọc trong `data` |
| `204 No Content` | Thành công và **không có body** theo tiêu chí bên dưới | Không |

**Phải trả body (`200`/`201`) khi thoả ít nhất một tiêu chí:**
- **B1 — Đọc:** mọi `GET`.
- **B2 — Tạo mới:** client cần id, timestamp và giá trị server sinh ra của tài nguyên vừa tạo.
- **B3 — Có giá trị do server tạo/ghi nhận mà client không tự biết trước:** id, token, thời điểm (`…At`), người thực hiện (`…ById` / `…ByName`), trạng thái hoặc trường tính ở server. Giá trị client đã gửi lên hoặc suy ra được từ việc request thành công (vd. `status = REJECTED` sau khi từ chối) **không tính**.
- **B4 — Cập nhật tài nguyên client còn hiển thị:** trả bản đầy đủ sau cập nhật để client không phải `GET` lại.

**Không trả body (`204`) khi:**
- **N1 — Client rời khỏi tài nguyên:** kết quả suy ra hoàn toàn từ việc request thành công (xoá, rời đội, từ chối lời mời…) và không có giá trị nào thoả B3.

**Áp dụng cho endpoint hiện có** (cập nhật bảng khi thêm endpoint):

| Endpoint | Mã | Tiêu chí |
|---|---|---|
| Mọi `GET` | 200 | B1 |
| `POST /auth/oauth`, `POST /auth/verify-otp` | 201 khi tạo user mới / 200 khi user đã có | B2 / B3 (token) |
| `POST /auth/send-otp`, `POST /auth/refresh` | 200 | B3 (`expiresIn`, token) |
| `PATCH /profile`, `PATCH /teams/{id}`, `PATCH /admin/teams/{id}/mark-inactive`, `…/reactivate` | 200 | B4 |
| `POST /teams`, `POST /teams/{id}/invitations` | 201 | B2 |
| `POST /invitations/{id}/accept` | 200 | B3 (`joinedAt`, thông tin đội) |
| `POST /teams/{id}/captain/transfer` | 200 | B3 (`transferredAt`), B4 (vai trò trong danh sách thành viên) |
| `POST /teams/{id}/disband` | 200 | B3 (`disbandedAt`, `disbandedByName`), B4 (đội giải tán vẫn xem được) |
| `POST /invitations/{id}/reject` | 204 | N1 |
| `POST /teams/{id}/members/leave`, `DELETE /profile` | 204 | N1 |
| `POST /conversations/{id}/messages` (gửi tin), `POST /conversations/{id}/reports` (report giao tiếp tệ qua chat hai đội) | 201 | B2 |
| `GET /conversations/{id}`, `GET /conversations/{id}/messages`, `GET /profile/conversations` | 200 | B1 |
| `POST /conversations/{id}/messages/read` | 204 | N1 |
| `POST /matches/{matchId}/pairings` (nhận kèo B) | 201 | B2 |
| `GET /matches/{matchId}/pairings`, `GET /teams/{teamId}/pairings`, `GET /pairings/{id}` | 200 | B1 |
| `PUT /pairings/{id}/proposal` | 200 | B3 (`revision`, `updatedAt`), B4 |
| `PUT /pairings/{id}/cost-agreement` | 200 | B3 (`revision`, `itemId`, `updatedAt`), B4 |
| `POST /pairings/{id}/confirm` | 200 | B3 (`…ConfirmedAt`, `confirmedAt`, `status`, `matchStatus`) |
| `POST /matches/{matchId}/mercenary-recruitments`, `POST /external-matches/{id}/mercenary-recruitments` (đăng tin tuyển), `POST /mercenary-recruitments/{id}/applications` (ứng tuyển), `POST /mercenary-applications/{id}/ratings`, `POST /mercenary-applications/{id}/reports`, `POST /teams/{teamId}/external-matches` | 201 | B2 |
| `GET /teams/{teamId}/mercenary-recruitments`, `GET /mercenary-recruitments`, `GET /mercenary-recruitments/{id}`, `GET /mercenary-recruitments/{id}/applications`, `GET /profile/mercenary-applications`, `GET /mercenary-applications/{id}`, `GET /mercenary-applications/{id}/ratings`, `GET /teams/{teamId}/mercenary-reports`, `GET /profile/mercenary-reports`, `GET /users/{userId}/reputation`, `GET /teams/{teamId}/mercenary-ratings`, `GET /admin/mercenary-disputes`, `GET /admin/mercenary-disputes/{id}`, `GET /teams/{teamId}/external-matches`, `GET /external-matches/{id}` | 200 | B1 |
| `PATCH /mercenary-recruitments/{id}`, `PATCH /external-matches/{id}` | 200 | B3 (`status` tính lại / `updatedAt`), B4 |
| `POST /mercenary-recruitments/{id}/close` | 200 | B3 (`closedAt`, số đơn bị từ chối), B4 |
| `POST /mercenary-applications/{id}/approve` | 200 | B3 (`respondedAt`, trạng thái / số đã duyệt của tin) |
| `POST /mercenary-applications/{id}/withdraw` | 200 | B3 (`withdrawnAt`, `reliabilityPenalty`, trạng thái tin) |
| `PATCH /admin/mercenary-disputes/{id}/resolve` | 200 | B3 (`resolvedAt`, `resolvedByName`), B4 |
| `POST /external-matches/{id}/cancel` | 200 | B3 (`cancelledAt`, số tin đóng, số đơn bị từ chối) |
| `POST /mercenary-applications/{id}/reject` | 204 | N1 |

### Client Error (4xx)
- `400 Bad Request` — request không hợp lệ (validation fail)
- `401 Unauthorized` — chưa đăng nhập / token không hợp lệ
- `403 Forbidden` — không có quyền truy cập resource
- `404 Not Found` — resource không tồn tại
- `409 Conflict` — conflict (vd: slot đã được đặt)
- `422 Unprocessable Entity` — dữ liệu hợp lệ nhưng nghiệp vụ không cho phép

### Server Error (5xx)
- `500 Internal Server Error` — lỗi hệ thống
- `503 Service Unavailable` — service tạm thời không khả dụng

---

## 6. Error Codes

Format: `<DOMAIN>_<NUMBER>`

**Domains:**
- `ARENA` — Sân thể thao
- `BOOKING` — Đặt lịch
- `USER` — Người dùng
- `TEAM` — Đội
- `AUTH` — Authentication/Authorization
- `PAYMENT` — Thanh toán
- `VENUE` — Địa điểm sân
- `REPORT` — Report (báo cáo vi phạm)
- `CHAT` — Cuộc trò chuyện (dùng chung cho mọi ngữ cảnh chat)
- `MERCENARY` — Tuyển mercenary: tin tuyển, đơn ứng tuyển, đánh giá / report theo đơn, trận ngoài hệ thống
- `SYS` — Lỗi tầng HTTP/hệ thống, không gắn miền nghiệp vụ; **chỉ** global exception handler phát ra (NEVER ném qua exception nghiệp vụ):
  - `SYS_001` — 404, URL không khớp endpoint nào
  - `SYS_002` — 405, sai HTTP method (kèm header `Allow`)
  - `SYS_003` — 415, `Content-Type` không được hỗ trợ
  - `SYS_004` — 4xx khác do framework phát hiện (giữ status gốc)
  - `SYS_005` — 500 lỗi ngoài dự kiến (5xx của framework giữ status gốc); không lộ chi tiết

Lỗi 400 do framework phát hiện (thiếu query param bắt buộc, sai kiểu path/query, JSON sai) dùng `VALIDATION_ERROR` (§11), **không** dùng `SYS_*`.

**Ví dụ:**
- `ARENA_001` — Sân không tồn tại
- `BOOKING_001` — Slot đã được đặt
- `BOOKING_002` — Booking phải đặt tối thiểu 2h trước
- `AUTH_001` — Token không hợp lệ
- `USER_001` — User không tồn tại
- `MATCH_016` — Thao tác không áp dụng cho loại kèo này (luồng A gọi endpoint luồng B hoặc ngược lại; tạo kèo luồng B khi luồng B chưa phát hành)
- `PAIRING_001`..`PAIRING_008` — Thương lượng ghép kèo (luồng B): không tìm thấy, quyền, trùng, tự nhận, đã kết thúc, revision lệch, chưa chốt, ngoài lựa chọn
- `CHAT_001` — Tin nhắn mốc không thuộc cuộc trò chuyện
- `CHAT_002`..`CHAT_004` — Cuộc trò chuyện: không tìm thấy, người gọi không tham gia, chỉ còn xem (không gửi tin được)
- `VENUE_016` — Địa điểm chưa có phường/xã mà lần sửa không gửi `wardId`
- `REPORT_009` — Report nhắm vào cá nhân mà người gọi tranh chấp không phải người bị report
- `REPORT_010`, `REPORT_011` — Report qua chat hai đội: cuộc trò chuyện chưa có tin nhắn nào; cuộc trò chuyện không phải giữa hai đội (vd chat theo đơn ứng tuyển)
- `MERCENARY_001`..`MERCENARY_025` — Tuyển mercenary: không tìm thấy tin / đơn / trận ngoài (`001`, `002`, `024`); điều kiện kèo / trận (`003`, `004`, `013`); đội tuyển không hoạt động (`005`); tin đủ người / đã đóng (`006`, `007`); số lượng thấp hơn số đã duyệt (`008`); trùng đơn / trùng vị trí / trùng đánh giá / trùng report (`009`, `023`, `017`, `022`); thành viên đội trong trận (`010`); trùng giờ (`011`); trạng thái đơn đã đổi (`012`); không tham gia đơn (`014`); điều kiện đánh giá / report (`015`, `016`, `019`–`021`); thiếu vị trí để tìm kèo (`018`); trận ngoài đã có mercenary được duyệt (`025`)

---

## 7. Pagination

### 7.1. Offset-based (mặc định)

**Query params:**
- `page` — số trang (bắt đầu từ 1)
- `pageSize` — số items mỗi trang (default: 20, max: 100)

**Request:**
```
GET /api/v1/arenas?page=2&pageSize=20
```

**Response:**
```json
{
  "data": [...],
  "pagination": {
    "page": 2,
    "pageSize": 20,
    "totalPages": 5,
    "totalItems": 100
  }
}
```

### 7.2. Cursor-based

Dùng cho danh sách thêm phần tử liên tục (vd chat) — offset trôi khi có phần tử mới.

**Query params:**
- `before` — id phần tử mốc; lấy phần tử **cũ hơn** mốc
- `after` — id phần tử mốc; lấy phần tử **mới hơn** mốc
- `limit` — số phần tử tối đa (default: 50, max: 100)

Không gửi đồng thời `before` và `after` (`400 VALIDATION_ERROR`).

**Response:**
```json
{
  "data": {
    "items": [...],
    "hasMore": true
  }
}
```

| Gửi | Trả | Thứ tự `items` | `hasMore = true` khi |
|---|---|---|---|
| Không con trỏ | `limit` phần tử mới nhất | Mới nhất trước | Còn phần tử cũ hơn |
| `before` | `limit` phần tử ngay trước mốc | Mới nhất trước | Còn phần tử cũ hơn |
| `after` | `limit` phần tử ngay sau mốc | **Cũ nhất trước** | Còn phần tử mới hơn |

Bản thân phần tử mốc (`before` / `after`) không có trong kết quả. Không có `pagination` (không có `totalItems` / `totalPages`).

**Áp dụng cho endpoint hiện có:**

| Endpoint | Kiểu | Con trỏ |
|---|---|---|
| Mọi danh sách trừ bảng dưới | Offset (`page`/`pageSize`) | — |
| `GET /conversations/{id}/messages` (lịch sử chat theo cuộc trò chuyện) | Cursor | `before` / `after` = `messageId` |

---

## 8. Filtering & Sorting

### 8.1. Filter

Query params dùng tên field:

```
GET /api/v1/arenas?type=football&location=hanoi
GET /api/v1/bookings?status=pending&userId=123
```

### 8.2. Sort

```
GET /api/v1/arenas?sort=pricePerHour:asc
GET /api/v1/bookings?sort=createdAt:desc
```

Multiple sorts:
```
GET /api/v1/arenas?sort=location:asc,pricePerHour:asc
```

---

## 9. Authentication & Authorization

### 9.1. Header

```
Authorization: Bearer <JWT_TOKEN>
```

### 9.2. Không có token
→ `401 Unauthorized`

### 9.3. Có token nhưng không đủ quyền
→ `403 Forbidden`

---

## 10. Field Naming Convention

- **CamelCase:** `arenaName`, `pricePerHour`, `userId`
- **Boolean:** prefix `is`, `has`, `can`: `isAvailable`, `hasDiscount`, `canBook`
- **Timestamp:** suffix `At`: `createdAt`, `updatedAt`, `deletedAt`
- **ID:** suffix `Id`: `arenaId`, `userId`, `bookingId`

---

## 11. Validation Response

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Dữ liệu không hợp lệ",
    "validationErrors": [
      {
        "field": "arenaName",
        "message": "Tên sân không được để trống"
      },
      {
        "field": "pricePerHour",
        "message": "Giá phải lớn hơn 0"
      }
    ],
    "timestamp": "2026-11-01T07:00:00Z",
    "path": "/api/v1/arenas"
  }
}
```

---

## 12. Soft Delete

- Không xóa vật lý (DELETE from DB)
- Đánh dấu `deletedAt` hoặc `status = DELETED`
- GET không trả về resource đã xóa
- Admin có thể restore

---

## 13. Idempotency (cho POST tạo resource)

**Header:**
```
Idempotency-Key: <UUID>
```

Cùng key → trả về resource đã tạo trước đó (không tạo duplicate).

---

## Checklist tuân thủ

- [ ] Base URL = `/api/v1`
- [ ] Resource dùng số nhiều, lowercase, danh từ
- [ ] HTTP method đúng ngữ nghĩa
- [ ] Response wrap trong `data` (success) hoặc `error` (fail)
- [ ] Field names dùng camelCase
- [ ] Error có `code` + `message` + `timestamp` + `path`
- [ ] Pagination: offset (`page`, `pageSize`, `totalPages`, `totalItems`) hoặc cursor (`before`/`after`, `limit`, `items`, `hasMore`) — chọn đúng kiểu theo §7
- [ ] Auth dùng `Authorization: Bearer <token>`
- [ ] Status code đúng (200/201/204/400/401/403/404/409/500); mã thành công theo tiêu chí B1–B4 / N1 (§5), có dòng trong bảng "Áp dụng cho endpoint hiện có"
- [ ] `201` không kèm header `Location`
