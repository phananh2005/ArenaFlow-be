# API Specification — [TÊN CHỨC NĂNG]

| Mục | Giá trị |
|---|---|
| Mã chức năng | `[MÃ]` |
| Phạm vi tầng | Backend (Spring Boot REST API) |
| Repo | ArenaFlow-be |
| Quy ước áp dụng | `[cần xác nhận: api-convention]` |
| Tác giả | [Agent/User] |
| Ngày | [YYYY-MM-DD] |
| Phiên bản | v1.0 (baseline) |

## 1. Tổng quan

[Mô tả ngắn chức năng này làm gì, phục vụ ai]

## 2. Endpoints

### 2.1. [TÊN ENDPOINT 1]

**Method & Path:**
```
[GET|POST|PUT|DELETE] /api/v1/[path]
```

**Mô tả:** [Làm gì]

**Request:**
- Headers: `Authorization: Bearer <token>`
- Path params: [nếu có]
- Query params: [nếu có]
- Body (nếu có):
```json
{
  "field1": "string",
  "field2": 123
}
```

**Response thành công (200/201):**
```json
{
  "data": {
    "field1": "string",
    "field2": 123
  }
}
```

**Response lỗi:**
- `400 Bad Request`: [khi nào]
- `401 Unauthorized`: token không hợp lệ
- `403 Forbidden`: không có quyền
- `404 Not Found`: [khi nào]
- `500 Internal Server Error`: lỗi hệ thống

**Validation:**
- [field]: [ràng buộc]

**Phân quyền:**
- [ROLE hoặc cần xác nhận]

---

## 3. Data Models (DTO)

### 3.1. [TênDTO]

| Field | Type | Required | Mô tả |
|---|---|---|---|
| `field1` | `String` | Yes | [Mô tả] |
| `field2` | `Integer` | No | [Mô tả] |

---

## 4. Mã lỗi

| Mã | HTTP Status | Ngữ cảnh | Message |
|---|---|---|---|
| `[MÃ]_001` | 400 | [khi nào] | [message] |

---

## 5. Danh sách [cần xác nhận]

1. [Điểm chưa rõ 1]
2. [Điểm chưa rõ 2]
