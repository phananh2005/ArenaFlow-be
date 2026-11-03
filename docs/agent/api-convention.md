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
| DELETE | Xóa resource | ✅ | ❌ | ❌ hoặc 204 No Content |

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
- `200 OK` — GET/PUT/PATCH thành công
- `201 Created` — POST tạo mới thành công (kèm `Location` header)
- `204 No Content` — DELETE thành công, không trả body

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
- [ ] Status code đúng (200/201/204/400/401/403/404/409/500)
