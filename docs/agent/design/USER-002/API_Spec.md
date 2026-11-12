# API Specification — Giải tán đội & quy tắc xoá tài khoản đội trưởng

<!-- File này được publish cho FE (ADR-0010). Viết theo design-standards.md §11:
     không tên class/bảng/cột/migration/transaction/khoá/số truy vấn; không trỏ Component_Class_Design.md,
     Processing_Flow.md, docs/database/. Chi tiết nội bộ BE ghi ở hai file đó.
     Số mục §2.1a, §2.4, §2.5, §4 được tài liệu khác trỏ tới — giữ nguyên khi sửa. -->

| Mục | Giá trị |
|---|---|
| Mã chức năng | `USER-002` |
| Phạm vi tầng | Backend (Spring Boot REST API) |
| Repo | ArenaFlow-be |
| Quy ước áp dụng | `api-convention.md` (envelope, phân trang, định dạng lỗi, kiểu dữ liệu) |
| Spec nghiệp vụ | `docs/agent/business-spec/USER-002/` (UC-T06, UC-U03 sửa, UC-T05 sửa; BR-T06..BR-T14, BC-T02, BC-T06) · `docs/agent/business-spec/SYSTEM-001/` (v1.7) — BC-U02 |
| Phụ thuộc | [`USER-001/API_Spec.md`](../USER-001/API_Spec.md) — hợp đồng gốc của đội, lời mời, hồ sơ; file này chỉ mô tả phần USER-002 thêm/đổi |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-04 · v1.0 (baseline) · hiện tại **v3.0** (solution-architect · 2026-11-12) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan

Đội trưởng **giải tán vĩnh viễn** đội của mình (kể cả đội đang bị admin khoá). Đội đã giải tán vẫn xem được, có trạng thái `DISBANDED` kèm người/thời điểm/lý do giải tán, nhưng mọi thao tác ghi bị chặn. Admin xem được lịch sử thành viên của đội ở mọi trạng thái. Đội trưởng muốn xoá tài khoản phải chuyển quyền hoặc giải tán hết đội của mình trước.

**Quy ước chung cho mọi endpoint của mã này:**
- Kế thừa toàn bộ quy ước chung của USER-001 (mục "Quy ước chung cho mọi endpoint"): xác thực `Authorization: Bearer <access token>` (thiếu/sai/hết hạn → `401 AUTH_001`), ID là chuỗi UUID, envelope `data` / `error` / `pagination`, giá trị enum **UPPERCASE**, phân trang `page` (≥ 1, mặc định 1) · `pageSize` (1..100, mặc định 20).
- Field thời điểm (`…At`) của mã này là chuỗi ISO-8601 UTC, vd `"2026-11-05T09:15:00Z"`.
- User đã xoá tài khoản (ADR-0002 sửa đổi 2026-11-12, SYSTEM-001 v1.7 BC-U02; quy tắc chung: USER-001 mục "Quy ước chung cho mọi endpoint"):
  - Endpoint thường (ngoài `/api/v1/admin/**`, kể cả khi người gọi là Admin): che tên → `"Người dùng đã xóa"`, ảnh → `null`; id vẫn giữ nguyên.
  - **Mọi endpoint `/api/v1/admin/**`:** không che — tên thật, ảnh thật (còn đường dẫn thì trả), mỗi field tên user có cờ `is…Deleted` đi kèm (`true` = đã xoá tài khoản; `false` cả khi field tên là `null`). Ở mã này: §2.4 (`displayName` + `isUserDeleted`, `avatarUrl` thật), §2.6 (`captainName` + `isCaptainDeleted`, `disbandedByName` + `isDisbandedByDeleted`).
- Thao tác ghi lên đội va chạm với thao tác khác cùng lúc → `409 TEAM_014`; FE cho người dùng bấm lại.

**Ngoài phạm vi:** frontend; trận đấu/ghép kèo/lính đánh thuê gắn với đội; thông báo; khôi phục đội đã giải tán. Header `Idempotency-Key` (`api-convention.md` §13) **chưa hỗ trợ** cho `POST /teams` và `POST /teams/{teamId}/disband` — gửi kèm header không có tác dụng.

## 2. Endpoints

| # | Method + Path | Loại | Dùng khi | Ai gọi được |
|---|---|---|---|---|
| 2.1 | `POST /api/v1/teams/{teamId}/disband` | **Mới** | Đội trưởng bấm "Giải tán đội" | Đội trưởng đang hoạt động của đội |
| 2.2 | `GET /api/v1/teams/{teamId}` | Đổi response | Xem chi tiết đội | User đã đăng nhập |
| 2.3 | `GET /api/v1/teams` | Đổi filter + response | Danh sách đội | User đã đăng nhập |
| 2.4 | `GET /api/v1/admin/teams/{teamId}/members` | **Mới** | Admin xem lịch sử thành viên của đội | Admin |
| 2.5 | `DELETE /api/v1/profile` | Đổi điều kiện lỗi | User xoá tài khoản | User đã đăng nhập |
| 2.6 | `GET /api/v1/admin/teams` | Đổi filter + mặc định + response | Admin tìm đội | Admin |
| 2.7 | 8 endpoint ghi của USER-001 (liệt kê ở mục 2.7) | Thêm mã lỗi | Mọi thao tác ghi lên đội | Như USER-001 |
| 2.8 | `GET /api/v1/teams/{teamId}/captain/history`, `GET /api/v1/teams/{teamId}/members` | Không đổi hợp đồng | Xem lịch sử đội trưởng / thành viên hiện tại | User đã đăng nhập |

Lỗi chung của mọi endpoint (`400 VALIDATION_ERROR`, `401 AUTH_001`, lỗi `SYS_*`) theo `api-convention.md` và USER-001; bảng lỗi từng endpoint dưới đây chỉ liệt kê mã riêng.

### 2.1. Giải tán đội — `POST /api/v1/teams/{teamId}/disband`

**Dùng khi:** Đội trưởng chọn "Giải tán đội" ở màn hình chi tiết đội, xác nhận cảnh báo (giải tán là vĩnh viễn, mọi thành viên rời đội, lời mời đang chờ bị huỷ) và nhập lý do (tuỳ chọn).
**Ai gọi được:** Đội trưởng đang hoạt động của đội; đội đang `ACTIVE` hoặc `INACTIVE` (bị admin khoá). Thành viên thường và người được mời không gọi được. Admin **không** giải tán được với tư cách admin; user có vai trò `ADMIN` vẫn giải tán được đội mà chính mình đang là đội trưởng.

**Path params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` | UUID | Có | — | `"0193a5c2-7e41-7c3a-9f12-4b8e6d2a1c01"` |

**Request body** — **không bắt buộc**: không gửi body, gửi `{}` hoặc `{"reason": null}` đều dùng lý do mặc định.

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `reason` | string \| null | Không | ≤ 100 ký tự, tính cả khoảng trắng đầu/cuối. Server bỏ khoảng trắng đầu/cuối; rỗng hoặc chỉ gồm khoảng trắng → lưu `"Đội trưởng giải tán đội"` | `"Hết người chơi"` |

```json
{ "reason": "Hết người chơi" }
```

**Response 200** — đối tượng Đội (chi tiết) sau khi giải tán (mục 5.1): `status = DISBANDED`, có `disbandedAt`, `disbandedById`, `disbandedByName`, `disbandReason`; `captainId`/`captainName` = `null` và `memberCount = 0` vì mọi thành viên (kể cả đội trưởng) đã rời đội; `inactiveReason` giữ nguyên nếu đội đang bị admin khoá.

```json
{
  "data": {
    "teamId": "0193a5c2-7e41-7c3a-9f12-4b8e6d2a1c01",
    "teamName": "Team Alpha",
    "avatarUrl": null,
    "description": "Đá giao lưu tối thứ 5",
    "skillLevels": ["Nhiệt tình", "Rèn luyện"],
    "status": "DISBANDED",
    "inactiveReason": null,
    "captainId": null,
    "captainName": null,
    "memberCount": 0,
    "createdBy": "0193a5c0-1b2c-7d3e-8f40-5a6b7c8d9e01",
    "createdAt": "2026-11-02T11:00:00Z",
    "updatedAt": "2026-11-05T09:15:00Z",
    "disbandedAt": "2026-11-05T09:15:00Z",
    "disbandedById": "0193a5c0-1b2c-7d3e-8f40-5a6b7c8d9e01",
    "disbandedByName": "Nguyễn Văn A",
    "disbandReason": "Hết người chơi"
  }
}
```

**Kết quả phụ** (FE không nhận trong response, thấy khi gọi lại các endpoint đọc): mọi thành viên đang hoạt động → `LEFT`; mọi lời mời đang chờ của đội → `REJECTED`; lịch sử đội trưởng và lịch sử thành viên giữ nguyên.

**Không idempotent:** gọi lại (kể cả bấm hai lần liên tiếp) → `422 TEAM_016`. Request bị timeout/mất kết nối → gọi `GET /api/v1/teams/{teamId}` để biết đội đã giải tán hay chưa, không gửi lại mù.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `reason` > 100 ký tự (`validationErrors[].field = "reason"`) hoặc JSON sai | Hiện lỗi dưới ô lý do, giữ hộp thoại |
| 403 | `TEAM_002` | Người gọi không phải đội trưởng đang hoạt động (vd quyền vừa được chuyển cho người khác) | Tải lại chi tiết đội; ẩn nút "Giải tán đội" |
| 404 | `TEAM_001` | Đội không tồn tại | Báo "Không tìm thấy đội", quay về danh sách |
| 409 | `TEAM_014` | Đội vừa bị thao tác khác thay đổi cùng lúc, thử lại vẫn va chạm | Tải lại chi tiết đội, cho người dùng bấm lại |
| 422 | `TEAM_016` | Đội đã giải tán (kể cả lần bấm thứ hai) | Tải lại chi tiết đội, hiện nhãn "Đã giải tán", ẩn mọi nút thao tác |

### 2.1a. Thứ tự mã lỗi khi nhiều điều kiện cùng sai

Khi một request vi phạm nhiều điều kiện, server trả **mã đứng trước** trong cột "Thứ tự". Luôn: `401 AUTH_001` trước `400 VALIDATION_ERROR`; `400 VALIDATION_ERROR` trước mọi mã trong bảng.

| Endpoint | Thứ tự | Ghi chú cho FE |
|---|---|---|
| `POST /teams/{teamId}/disband` | `TEAM_001` → `TEAM_016` → `TEAM_002` | Đội `INACTIVE` vẫn giải tán được, không có `TEAM_015` |
| `PATCH /teams/{teamId}` | `TEAM_001` → `TEAM_016` → `TEAM_015` → `TEAM_002` | |
| `POST /teams/{teamId}/invitations` | `TEAM_001` → `TEAM_016` → `TEAM_015` → `TEAM_002` → `USER_001` → `TEAM_003` | |
| `POST /invitations/{invitationId}/accept` · `/reject` | `TEAM_004` → `TEAM_005` → `TEAM_016` → `TEAM_015` → `TEAM_006` | |
| `POST /teams/{teamId}/captain/transfer` | `TEAM_001` → `TEAM_016` → `TEAM_015` → `TEAM_002` → `TEAM_008` → `TEAM_007` | |
| `POST /teams/{teamId}/members/leave` | `TEAM_001` → `TEAM_016` → `TEAM_010` → `TEAM_009` | Đội `INACTIVE` vẫn rời được, không có `TEAM_015` |
| `PATCH /admin/teams/{teamId}/mark-inactive` | `TEAM_001` → `TEAM_016` → `TEAM_011` | |
| `PATCH /admin/teams/{teamId}/reactivate` | `TEAM_001` → `TEAM_016` → `TEAM_012` → `TEAM_013` | |

**Đội vừa bị giải tán bởi người khác trong lúc request đang chạy:** server trả `422 TEAM_016` thay cho `TEAM_002`, `TEAM_007`, `TEAM_010`, `TEAM_013`. FE xử lý `TEAM_016` như bảng lỗi mục 2.1 (tải lại, ẩn nút).

### 2.2. Chi tiết đội — `GET /api/v1/teams/{teamId}` (đổi response)

**Dùng khi / Ai gọi được / Lỗi:** như USER-001 (`404 TEAM_001`). Đội `DISBANDED` vẫn xem được.

**Thay đổi:** response (đối tượng Đội — chi tiết) thêm 4 field `disbandedAt`, `disbandedById`, `disbandedByName`, `disbandReason` (mục 5.1) và `status` có thêm giá trị `DISBANDED`. Các field USER-001 giữ nguyên. Ví dụ đội đã giải tán: response mục 2.1.

**Gợi ý hiển thị:** đội `DISBANDED` không còn đội trưởng (`captainName = null`) → hiển thị `disbandedByName` là "Người giải tán" thay cho đội trưởng, kèm `disbandReason` và `disbandedAt`.

### 2.3. Danh sách đội — `GET /api/v1/teams` (đổi filter + response)

**Dùng khi / Ai gọi được:** như USER-001.

**Query params** (`page`, `pageSize`, thứ tự sắp xếp: như USER-001)

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `status` | enum | Không | `ACTIVE` \| `INACTIVE` \| `DISBANDED` \| `ALL`; mặc định `ACTIVE`. `ALL` = cả ba trạng thái. Giá trị khác → `400 VALIDATION_ERROR` | `DISBANDED` |

**Thay đổi:** thêm giá trị filter `DISBANDED`; `ALL` nay gồm cả đội đã giải tán; mỗi phần tử (Đội — tóm tắt, mục 5.2) thêm `disbandedByName`.

```json
{
  "data": [
    {
      "teamId": "0193a5c2-7e41-7c3a-9f12-4b8e6d2a1c01",
      "teamName": "Team Alpha",
      "avatarUrl": null,
      "skillLevels": ["Nhiệt tình"],
      "status": "DISBANDED",
      "captainName": null,
      "memberCount": 0,
      "createdAt": "2026-11-02T11:00:00Z",
      "disbandedByName": "Nguyễn Văn A"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi:** `400 VALIDATION_ERROR` (`status`/`page`/`pageSize` sai) → sửa tham số lọc, về giá trị mặc định.

### 2.4. Lịch sử thành viên (Admin) — `GET /api/v1/admin/teams/{teamId}/members`

**Dùng khi:** Admin mở tab "Lịch sử thành viên" của một đội ở màn hình quản trị — đội ở **mọi trạng thái** (`ACTIVE`, `INACTIVE`, `DISBANDED`).
**Ai gọi được:** chỉ Admin. Danh sách thành viên đang hoạt động cho user thường vẫn là `GET /api/v1/teams/{teamId}/members` (USER-001, không đổi).

Chỉ gồm người **đã từng tham gia** đội (thành viên `ACTIVE` hoặc đã rời `LEFT`); không gồm lời mời `PENDING`/`REJECTED`.

**Path / Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` (path) | UUID | Có | — | `"0193a5c2-7e41-7c3a-9f12-4b8e6d2a1c01"` |
| `status` | enum | Không | `ACTIVE` \| `LEFT` \| `ALL`; mặc định `ALL` (= `ACTIVE` + `LEFT`). Giá trị khác → `400 VALIDATION_ERROR` | `LEFT` |
| `page` | integer | Không | ≥ 1; mặc định 1 | `1` |
| `pageSize` | integer | Không | 1..100; mặc định 20 | `20` |

**Sắp xếp:** cố định `joinedAt` tăng dần, rồi `memberId` tăng dần; không nhận tham số `sort`.

**Response 200** — mảng Lịch sử thành viên (mục 5.3) + `pagination`. Thành viên đã xoá tài khoản vẫn có tên và ảnh thật, kèm `isUserDeleted = true` (phần tử thứ hai trong ví dụ).

```json
{
  "data": [
    {
      "memberId": "0193a5c3-0a11-7b22-8c33-1d44e55f6601",
      "teamId": "0193a5c2-7e41-7c3a-9f12-4b8e6d2a1c01",
      "userId": "0193a5c0-1b2c-7d3e-8f40-5a6b7c8d9e01",
      "displayName": "Nguyễn Văn A",
      "avatarUrl": "https://res.cloudinary.com/demo/image/upload/a.jpg",
      "isUserDeleted": false,
      "role": "CAPTAIN",
      "status": "LEFT",
      "joinedAt": "2026-11-02T11:00:00Z",
      "leftAt": "2026-11-05T09:15:00Z"
    },
    {
      "memberId": "0193a5c3-0a11-7b22-8c33-1d44e55f6602",
      "teamId": "0193a5c2-7e41-7c3a-9f12-4b8e6d2a1c01",
      "userId": "0193a5c0-1b2c-7d3e-8f40-5a6b7c8d9e02",
      "displayName": "Trần Thị B",
      "avatarUrl": "https://res.cloudinary.com/demo/image/upload/b.jpg",
      "isUserDeleted": true,
      "role": "MEMBER",
      "status": "LEFT",
      "joinedAt": "2026-11-03T08:00:00Z",
      "leftAt": "2026-11-04T10:00:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 2 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `status`, `page` hoặc `pageSize` sai giá trị (`validationErrors[].field` = tên tham số) | Về giá trị lọc mặc định |
| 403 | `AUTH_004` | Người gọi không phải Admin | Không hiển thị màn hình quản trị cho user thường |
| 404 | `TEAM_001` | Đội không tồn tại | Báo "Không tìm thấy đội", quay về danh sách quản trị |

### 2.5. Xoá tài khoản — `DELETE /api/v1/profile` (đổi điều kiện lỗi)

**Dùng khi / Ai gọi được / Response:** như USER-001 — thành công trả `204` không body.

**Thay đổi** ([ADR-0005](../../decisions/0005-captain-must-hand-over-before-account-deletion.md), BR-T06): user còn là **đội trưởng đang hoạt động của bất kỳ đội nào chưa giải tán** — kể cả là người duy nhất trong đội, kể cả đội đang bị admin khoá — bị từ chối `422 USER_003`; tài khoản, đội, tư cách thành viên và lời mời giữ nguyên. Đội đã giải tán không tính. Không đội nào bị hệ thống tự chuyển trạng thái khi xoá tài khoản (trước đây đội có đội trưởng duy nhất tự chuyển `INACTIVE`).

Khi qua điều kiện trên, hành vi như USER-001: user rời mọi đội đang tham gia (`LEFT`), mọi lời mời đang chờ gửi tới user → `REJECTED`; xoá lần 2 vẫn `204`.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 409 | `TEAM_014` | Va chạm với thao tác khác trên đội của user, thử lại vẫn va chạm | Cho người dùng bấm lại |
| 422 | `USER_003` | Còn là đội trưởng của ít nhất một đội chưa giải tán | Hiện message của `USER_003`; người dùng vào từng đội để chuyển quyền hoặc giải tán (chưa có API liệt kê "đội tôi làm đội trưởng" — mục 6) |

Mã `USER_003` giữ nguyên từ USER-001, chỉ mở rộng điều kiện và đổi message (mục 4) — FE đang bắt `USER_003` không phải đổi mã.

### 2.6. Danh sách đội (Admin) — `GET /api/v1/admin/teams` (đổi filter + mặc định + response)

**Dùng khi / Ai gọi được:** như USER-001 (Admin; không phải Admin → `403 AUTH_004`).

**Query params** (`page`, `pageSize`, thứ tự sắp xếp: như USER-001)

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `status` | enum | Không | `ACTIVE` \| `INACTIVE` \| `DISBANDED` \| `ALL`; mặc định `ALL` | `INACTIVE` |

**Thay đổi:** thêm giá trị filter `DISBANDED`; mặc định `ALL` **nay gồm cả đội đã giải tán**; mỗi phần tử (Đội — tóm tắt Admin, mục 5.2) thêm `disbandedByName` + `isDisbandedByDeleted` và `isCaptainDeleted`; tên không che (mục 1). Ví dụ: người giải tán đã xoá tài khoản.

```json
{
  "data": [
    {
      "teamId": "0193a5c2-7e41-7c3a-9f12-4b8e6d2a1c01",
      "teamName": "Team Alpha",
      "avatarUrl": null,
      "skillLevels": ["Nhiệt tình"],
      "status": "DISBANDED",
      "inactiveReason": "Vi phạm quy định sân",
      "captainName": null,
      "isCaptainDeleted": false,
      "memberCount": 0,
      "createdAt": "2026-11-02T11:00:00Z",
      "disbandedByName": "Nguyễn Văn A",
      "isDisbandedByDeleted": true
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi:** `400 VALIDATION_ERROR` (tham số sai) · `403 AUTH_004` — xử lý như mục 2.4.

### 2.7. Thao tác ghi USER-001 trên đội đã giải tán (thêm mã lỗi)

Request/response các endpoint dưới đây **không đổi** (xem USER-001). Thêm:

| Endpoint | Thêm lỗi |
|---|---|
| `PATCH /api/v1/teams/{teamId}` | `422 TEAM_016` |
| `POST /api/v1/teams/{teamId}/invitations` | `422 TEAM_016`; `409 TEAM_014` (nhiều lời mời gửi cùng lúc vào cùng một đội) |
| `POST /api/v1/invitations/{invitationId}/accept` | `422 TEAM_016` |
| `POST /api/v1/invitations/{invitationId}/reject` | `422 TEAM_016` |
| `POST /api/v1/teams/{teamId}/captain/transfer` | `422 TEAM_016` |
| `POST /api/v1/teams/{teamId}/members/leave` | `422 TEAM_016` |
| `PATCH /api/v1/admin/teams/{teamId}/mark-inactive` | `422 TEAM_016` |
| `PATCH /api/v1/admin/teams/{teamId}/reactivate` | `422 TEAM_016` (đội đã giải tán không kích hoạt lại được, kể cả Admin) |

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 409 | `TEAM_014` | Lời mời gửi cùng lúc với thao tác khác trên cùng đội (vd đội trưởng mời nhiều người liên tiếp rất nhanh), thử lại vẫn va chạm | Cho người dùng gửi lại lời mời |
| 422 | `TEAM_016` | Đội đã giải tán (kể cả vừa bị giải tán trong lúc request đang chạy) | Tải lại chi tiết đội, hiện nhãn "Đã giải tán", ẩn nút thao tác; với lời mời: xoá khỏi danh sách lời mời đang chờ |

Khi nhiều lỗi cùng xảy ra: mục 2.1a.

### 2.8. Endpoint đọc USER-001 với đội đã giải tán

Hợp đồng không đổi; hành vi với đội `DISBANDED`:

| Endpoint | Với đội `DISBANDED` |
|---|---|
| `GET /api/v1/teams/{teamId}/captain/history` | Vẫn trả đầy đủ lịch sử đội trưởng |
| `GET /api/v1/teams/{teamId}/members` | Trả danh sách rỗng (endpoint chỉ trả thành viên `ACTIVE`; mọi thành viên đã rời). Admin xem lịch sử ở mục 2.4 |

---

## 3. Hành động theo trạng thái đội

"Đội trưởng" = đội trưởng đang hoạt động; "Thành viên" = thành viên `ACTIVE` không phải đội trưởng; "Người được mời" = người có lời mời `PENDING` vào đội. Mọi user đã đăng nhập xem được chi tiết đội, danh sách đội, lịch sử đội trưởng và thành viên hiện tại ở **mọi** trạng thái.

| Trạng thái | Đội trưởng | Thành viên | Người được mời | Admin |
|---|---|---|---|---|
| `ACTIVE` | Sửa đội · Mời · Chuyển quyền · **Giải tán** (2.1). Không rời đội được (`TEAM_009`) | Rời đội | Chấp nhận · Từ chối lời mời | Khoá đội (mark-inactive) · Xem lịch sử thành viên (2.4). Kích hoạt lại → `TEAM_012` |
| `INACTIVE` | **Giải tán** (2.1). Sửa/Mời/Chuyển quyền → `TEAM_015`. Rời đội → `TEAM_009` | Rời đội | Chấp nhận/Từ chối → `TEAM_015` | Kích hoạt lại · Xem lịch sử thành viên. Khoá lại → `TEAM_011` |
| `DISBANDED` | Không còn đội trưởng; mọi thao tác → `TEAM_016` | Không còn thành viên; rời đội → `TEAM_016` | Không còn lời mời (đã `REJECTED`); chấp nhận/từ chối → `TEAM_016` | Chỉ xem (chi tiết, lịch sử thành viên). Khoá/Kích hoạt lại → `TEAM_016` |

**Xoá tài khoản** (2.5): user đang là đội trưởng của đội `ACTIVE` hoặc `INACTIVE` → `422 USER_003`; FE hiện message của `USER_003`, người dùng vào từng đội để chuyển quyền hoặc giải tán.

## 4. Mã lỗi

Mã mới hoặc đổi ở USER-002 (mã khác giữ nguyên theo USER-001):

| Mã | HTTP | Message | Endpoint | Thay đổi |
|---|---|---|---|---|
| `TEAM_016` | 422 | "Đội đã giải tán" | 2.1, 2.7 | **Mới.** Một mã dùng chung cho mọi thao tác ghi trên đội đã giải tán, kể cả giải tán lại và Admin kích hoạt lại |
| `USER_003` | 422 | "Bạn phải chuyển quyền đội trưởng hoặc giải tán đội trước khi xóa tài khoản" | 2.5 | **Đổi** ([ADR-0005](../../decisions/0005-captain-must-hand-over-before-account-deletion.md)) điều kiện (mọi đội chưa giải tán mà user là đội trưởng, kể cả người duy nhất, kể cả đội `INACTIVE`) và message |
| `TEAM_014` | 409 | "Dữ liệu vừa được cập nhật bởi thao tác khác, vui lòng thử lại" | 2.1, 2.5, 2.7 | Mã USER-001; nay có thêm ở giải tán và mời thành viên |
| `TEAM_001` | 404 | "Không tìm thấy đội" | 2.1, 2.4 | Mã USER-001 |
| `TEAM_002` | 403 | "Chỉ đội trưởng được thực hiện" | 2.1 | Mã USER-001 |
| `AUTH_004` | 403 | "Cần quyền quản trị" | 2.4, 2.6 | Mã USER-001 |
| `VALIDATION_ERROR` | 400 | "Dữ liệu không hợp lệ" | 2.1, 2.3, 2.4, 2.6 | Theo `api-convention.md` §11 |

## 5. Kiểu dữ liệu dùng chung

### 5.1. Đội — chi tiết (response của 2.1, 2.2 và các endpoint USER-001 trả chi tiết đội)

Bản Admin (USER-001 tạm ngưng / kích hoạt lại đội) là đối tượng riêng của USER-001: thêm cờ `isCaptainDeleted`, `isDisbandedByDeleted` và không che tên — xem USER-001 mục "Đối tượng response".

Field USER-001 giữ nguyên. Thêm:

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `disbandedAt` | string (ISO-8601 UTC) \| null | Thời điểm giải tán; `null` nếu chưa giải tán |
| `disbandedById` | UUID \| null | User đã giải tán; `null` nếu chưa giải tán. Giữ nguyên kể cả khi user đó đã xoá tài khoản |
| `disbandedByName` | string \| null | Tên người giải tán để hiển thị; `null` nếu chưa giải tán; `"Người dùng đã xóa"` nếu người đó đã xoá tài khoản (endpoint Admin: tên thật + `isDisbandedByDeleted`) |
| `disbandReason` | string \| null | Lý do giải tán; `null` nếu chưa giải tán; đội đã giải tán luôn có giá trị (tối thiểu là lý do mặc định `"Đội trưởng giải tán đội"`) |

Với đội `DISBANDED`: `captainId`, `captainName` = `null`; `memberCount` = 0; `inactiveReason` giữ lý do khoá của Admin nếu đội bị khoá trước khi giải tán.

### 5.2. Đội — tóm tắt (phần tử của 2.3) và Đội — tóm tắt Admin (phần tử của 2.6)

Field USER-001 giữ nguyên (bản Admin có thêm `inactiveReason` như USER-001). Thêm:

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `disbandedByName` | string \| null | Như mục 5.1. Bản Admin: tên thật kể cả khi người giải tán đã xoá tài khoản |
| `isDisbandedByDeleted` | boolean | **Chỉ bản Admin**, cuối phần tử. `true` nếu người giải tán đã xoá tài khoản; `false` khi `disbandedByName = null` |
| `isCaptainDeleted` | boolean | **Chỉ bản Admin**, ngay sau `captainName` (tên thật). `true` nếu đội trưởng đã xoá tài khoản; `false` khi `captainName = null` |

### 5.3. Lịch sử thành viên (phần tử của 2.4)

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `memberId` | UUID | Id tư cách thành viên |
| `teamId` | UUID | Id đội |
| `userId` | UUID | Id user; giữ nguyên kể cả khi user đã xoá tài khoản |
| `displayName` | string | Tên hiển thị; Admin nhận tên thật kể cả khi user đã xoá tài khoản |
| `isUserDeleted` | boolean | `true` nếu user đã xoá tài khoản; FE Admin dùng để gắn nhãn “đã xoá” |
| `avatarUrl` | string \| null | Ảnh đại diện; `null` nếu user chưa có ảnh. Admin nhận ảnh thật kể cả khi user đã xoá tài khoản |
| `role` | enum | `CAPTAIN` \| `MEMBER` — vai trò hiện tại, hoặc vai trò lúc rời đội nếu đã `LEFT` |
| `status` | enum | `ACTIVE` \| `LEFT` |
| `joinedAt` | string (ISO-8601 UTC) | Thời điểm vào đội |
| `leftAt` | string (ISO-8601 UTC) \| null | Thời điểm rời đội; `null` nếu đang `ACTIVE` |

### 5.4. Enum

| Kiểu / enum | Giá trị | Ý nghĩa |
|---|---|---|
| `status` của đội | `ACTIVE` \| `INACTIVE` \| `DISBANDED` | Đang hoạt động · Bị Admin khoá (mở lại được) · **Đã giải tán** (vĩnh viễn, mới ở USER-002) |
| Filter `status` của 2.3, 2.6 | `ACTIVE` \| `INACTIVE` \| `DISBANDED` \| `ALL` | Lọc một trạng thái; `ALL` = cả ba. Mặc định: 2.3 `ACTIVE`, 2.6 `ALL` |
| Filter `status` của 2.4 | `ACTIVE` \| `LEFT` \| `ALL` | Đang trong đội · Đã rời · Cả hai (mặc định) |
| `status` của thành viên/lời mời | `PENDING` \| `ACTIVE` \| `LEFT` \| `REJECTED` | Như USER-001. Khi đội giải tán: `ACTIVE` → `LEFT`, `PENDING` → `REJECTED` |

## 6. Danh sách [cần xác nhận]

1. **Chưa có endpoint "các đội tôi đang làm đội trưởng"** — FE cần để dẫn người dùng tới đội phải chuyển quyền/giải tán khi nhận `USER_003` và để ẩn/cảnh báo nút "Xoá tài khoản" trước. Là chức năng riêng, chưa làm; hiện người dùng phải tự vào từng đội.

Các điểm đã chốt ngày 2026-11-05 (phạm vi lịch sử thành viên Admin, lý do chỉ gồm khoảng trắng, mã lỗi đội đã giải tán, che tên người giải tán, mã lỗi xoá tài khoản đội trưởng) đã phản ánh ở các mục trên.
