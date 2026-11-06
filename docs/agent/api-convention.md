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

---

## 7. Pagination

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
- [ ] Pagination có `page`, `pageSize`, `totalPages`, `totalItems`
- [ ] Auth dùng `Authorization: Bearer <token>`
- [ ] Status code đúng (200/201/204/400/401/403/404/409/500); mã thành công theo tiêu chí B1–B4 / N1 (§5), có dòng trong bảng "Áp dụng cho endpoint hiện có"
- [ ] `201` không kèm header `Location`
