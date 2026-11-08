# API Specification — Giải tán đội & quy tắc xoá tài khoản đội trưởng

| Mục | Giá trị |
|---|---|
| Mã chức năng | `USER-002` |
| Phạm vi tầng | Backend (Spring Boot REST API) |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · PostgreSQL (Neon) · Flyway |
| Quy ước áp dụng | `docs/agent/api-convention.md`, `docs/agent/security-backend.md` |
| Ràng buộc dữ liệu | USER-001 schema V1/V2/V3; delta được mô tả tại `Component_Class_Design.md` §5 |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-04 · v1.0 (baseline) · hiện tại **v1.7** (developer · 2026-11-09) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan

Bổ sung giải tán vĩnh viễn bởi captain, biểu diễn trạng thái `DISBANDED`, giữ lịch sử đội và membership, và sửa quy tắc xoá tài khoản captain. Kế thừa envelope, UUID, phân trang, kiểu enum UPPERCASE, JWT và hợp đồng endpoint USER-001; không chép lại các DTO không đổi.

**Ngoài phạm vi:** frontend; xử lý Match/pairing/mercenary gắn đội; thông báo; khôi phục đội đã giải tán; `Idempotency-Key` (`api-convention.md` §13) cho `POST /teams` và `POST /teams/{id}/disband` — làm thành chức năng dùng chung riêng (bảng `idempotency_keys`, component dùng chung, vượt W6 cần duyệt NFS), chốt bởi user 2026-11-05.

## 2. Endpoints

| Endpoint | Thay đổi USER-002 |
|---|---|
| `POST /api/v1/teams/{teamId}/disband` | Mới; captain hoạt động giải tán đội đang `ACTIVE` hoặc `INACTIVE` |
| `GET /api/v1/teams/{teamId}` | Mở rộng `TeamResponse` cho `DISBANDED` và thông tin người giải tán |
| `GET /api/v1/teams` | Filter nhận thêm `DISBANDED`; mặc định vẫn `ACTIVE`; `ALL` gồm các trạng thái |
| `GET /api/v1/admin/teams` | Filter dùng chung `TeamStatusFilter` nên nhận thêm `DISBANDED`; mặc định vẫn `ALL`, **nay gồm cả đội `DISBANDED`**. `AdminTeamSummaryResponse` thêm `disbandedByName` (§3.2) |
| `GET /api/v1/admin/teams/{teamId}/members` | Mới; Admin xem lịch sử membership của đội ở mọi trạng thái (giữ phân trang/response convention USER-001) |
| `GET /api/v1/teams/{teamId}/captain/history` | Tiếp tục đọc lịch sử, kể cả đội `DISBANDED` |
| `DELETE /api/v1/profile` | Captain phải chuyển quyền hoặc giải tán tất cả đội chưa giải tán trước khi xoá; thành viên thường tiếp tục được xử lý theo USER-001 |
| `PATCH /api/v1/teams/{teamId}`; `POST /api/v1/teams/{teamId}/invitations`; `POST /api/v1/invitations/{invitationId}/accept`; `POST /api/v1/invitations/{invitationId}/reject`; `POST /api/v1/teams/{teamId}/captain/transfer`; `POST /api/v1/teams/{teamId}/members/leave`; `PATCH /api/v1/admin/teams/{teamId}/mark-inactive`; `PATCH /api/v1/admin/teams/{teamId}/reactivate` | Thêm `422 TEAM_016` khi đội `DISBANDED` (thứ tự guard §2.1a). Riêng `POST .../invitations` thêm `409 TEAM_014`: nay chạy qua retrier và tăng `team.version`, nên các lời mời song song vào cùng một đội bị tuần tự hoá. Giữ hợp đồng USER-001 còn lại |

### 2.1. POST /api/v1/teams/{teamId}/disband

**Quyền:** User đã xác thực; service xác minh người gọi là captain `ACTIVE` của đội. Không kiểm tra role: Admin không giải tán được **với tư cách admin**, nhưng user role ADMIN đang là captain ACTIVE của đội mình vẫn giải tán được với tư cách đội trưởng (BR-T07; chốt bởi user, 2026-11-05).

**Request:** `DisbandTeamRequest`, **body không bắt buộc** (`@RequestBody(required = false)`): không body, `{}` hoặc `{"reason": null}` đều dùng lý do mặc định.

> Endpoint dạng hành động (`/disband`) theo tiền lệ USER-001 (`/captain/transfer`, `/members/leave`, `/accept`, `/mark-inactive`). Ngoại lệ "action sub-resource" sẽ được bổ sung vào `api-convention.md` qua `workflow-adapt` trên nhánh `chore/agent-framework-*` riêng.

```json
{ "reason": "Hết người chơi" }
```

`reason` tùy chọn, tối đa 100 ký tự tính trên giá trị gốc (`400 VALIDATION_ERROR`). Service `trim()`; nếu vắng mặt, null hoặc rỗng sau trim (kể cả chỉ gồm khoảng trắng) thì lưu `Đội trưởng giải tán đội`, ngược lại lưu giá trị đã trim (BR-T08).

**Response 200:** `TeamResponse` cập nhật; envelope `data` theo USER-001. `status = DISBANDED`, gồm `disbandedAt`, `disbandedById`, `disbandedByName`, `disbandReason`; `inactiveReason` được giữ nguyên nếu có.

**Lỗi:** `400 VALIDATION_ERROR` (reason >100 hoặc lỗi định dạng); `401 AUTH_001`; `403 TEAM_002` (caller không phải captain đang hoạt động); `404 TEAM_001`; `409 TEAM_014` (xung đột đồng thời hết retry); `422 TEAM_016` (đội đã giải tán, kể cả giải tán lặp lại).

**Guard:** xem §2.1a.

### 2.1a. Thứ tự guard theo endpoint (nguồn duy nhất)

Chốt bởi user, 2026-11-05: kiểm tra `DISBANDED` → `422 TEAM_016` **ngay sau** kiểm tra tồn tại, **trước** `INACTIVE` (`TEAM_015`) và trước quyền/tư cách (`TEAM_002`, `TEAM_010`, `TEAM_006`...). Lý do: đội đã giải tán không còn captain hoặc member ACTIVE, nên nếu kiểm tra quyền trước thì `TEAM_016` không bao giờ được trả về. Thứ tự này khớp code `TeamGuard` USER-001 (tồn tại → trạng thái → quyền) và **thay** câu "tồn tại → quyền → state" trong `USER-001/Processing_Flow.md` §1. 401 (filter Security) luôn trả trước 400 (Bean Validation).

| Endpoint | Thứ tự guard (sau JWT + Bean Validation) | Ghi chú |
|---|---|---|
| `POST /teams/{id}/disband` | `TEAM_001` → `TEAM_016` → `TEAM_002` | Không chặn `INACTIVE` (BR-T12) |
| `PATCH /teams/{id}` | `TEAM_001` → `TEAM_016` → `TEAM_015` → `TEAM_002` | |
| `POST /teams/{id}/invitations` | `TEAM_001` → `TEAM_016` → `TEAM_015` → `TEAM_002` → `USER_001` → `TEAM_003` | Tăng `team.version` lúc commit (race M1, `Processing_Flow.md` §5) |
| `POST /invitations/{id}/accept` · `/reject` | `TEAM_004` → `TEAM_005` → `TEAM_016` → `TEAM_015` → `TEAM_006` | |
| `POST /teams/{id}/captain/transfer` | `TEAM_001` → `TEAM_016` → `TEAM_015` → `TEAM_002` → `TEAM_008` → `TEAM_007` | |
| `POST /teams/{id}/members/leave` | `TEAM_001` → `TEAM_016` → `TEAM_010` → `TEAM_009` | **Không** chặn `INACTIVE`: thành viên vẫn rời được đội bị Admin khoá, như USER-001 (chốt bởi user, 2026-11-05) |
| `PATCH /admin/teams/{id}/mark-inactive` | `TEAM_001` → `TEAM_016` → `TEAM_011` | |
| `PATCH /admin/teams/{id}/reactivate` | `TEAM_001` → `TEAM_016` → `TEAM_012` → `TEAM_013` | |

**Bên thua race luôn nhận `TEAM_016`:** nếu đội bị giải tán bởi request song song **sau** khi request này đã qua bước kiểm trạng thái, các guard phía sau (`TEAM_002`, `TEAM_007`, `TEAM_010`, `TEAM_013`) có thể thất bại vì captain/member vừa bị chuyển LEFT. Khi đó service đọc lại trạng thái đã commit của đội; đội `DISBANDED` → trả `422 TEAM_016` thay cho mã gốc (`Processing_Flow.md` §5). Đội chưa giải tán → giữ mã gốc.

### 2.2. Thay đổi response `TeamResponse`

Các field USER-001 giữ nguyên. Thêm:

| Field | Type | Mô tả |
|---|---|---|
| `disbandedAt` | `OffsetDateTime \| null` | Thời điểm giải tán UTC; null nếu chưa giải tán |
| `disbandedById` | `UUID \| null` | User giải tán; null nếu chưa giải tán |
| `disbandedByName` | `String \| null` | Tên người giải tán để hiển thị; với đội đã giải tán hiển thị người giải tán thay vì captain đang hoạt động |
| `disbandReason` | `String \| null` | Lý do giải tán; đội đã giải tán luôn có giá trị, gồm mặc định |

Với đội `DISBANDED`, mọi member đã rời nên `captainId`/`captainName` tiếp tục null theo USER-001; `disbandedByName` là nhãn người chịu trách nhiệm hiển thị. Nếu người giải tán đã soft-delete: `disbandedByName` = `Người dùng đã xóa` qua `DeletedUserMasking` USER-001; `disbandedById` giữ nguyên. Cờ deleted lấy trong cùng query join, không thêm query.

### 2.3. Thay đổi filter `GET /api/v1/teams`

`status` ∈ `ACTIVE | INACTIVE | DISBANDED | ALL`, mặc định `ACTIVE`; sort/page/pageSize và envelope giữ USER-001. `DISBANDED` lọc riêng trạng thái đã giải tán. Danh sách `ALL` gồm cả ba trạng thái.

### 2.4. GET /api/v1/admin/teams/{teamId}/members

**Quyền:** chỉ Admin; User thường bị từ chối theo quy tắc admin USER-001.

**Mô tả:** Xem lịch sử membership của đội ở **mọi trạng thái** (`ACTIVE`, `INACTIVE`, `DISBANDED`). Chỉ gồm người đã từng tham gia: `team_member.status ∈ {ACTIVE, LEFT}`; không gồm lời mời `PENDING`/`REJECTED`. Không thay đổi quyền hoặc nội dung endpoint `GET /api/v1/teams/{teamId}/members` hiện có.

**Query params:**

| Param | Type | Mặc định | Ràng buộc |
|---|---|---|---|
| `status` | `String` | `ALL` | `ACTIVE \| LEFT \| ALL`; giá trị khác → `400 VALIDATION_ERROR` |
| `page`, `pageSize` | `int` | theo USER-001 | theo `PageRequestFactory` USER-001 |

**Sort:** `joined_at ASC, id ASC` (ổn định, cố định; không nhận param sort).

**Response 200:** `PageResponse<MemberHistoryResponse>` trong envelope USER-001.

| Field | Type | Mô tả |
|---|---|---|
| `memberId`, `teamId`, `userId`, `displayName`, `avatarUrl`, `role`, `status`, `joinedAt` | như `MemberResponse` | `displayName`/`avatarUrl` che theo `DeletedUserMasking`; `role` là vai trò tại thời điểm rời đội |
| `leftAt` | `OffsetDateTime \| null` | Thời điểm rời đội; null nếu đang `ACTIVE` |

**Lỗi:** `400 VALIDATION_ERROR`; `401 AUTH_001`; `403 AUTH_004`; `404 TEAM_001`.

### 2.5. Xoá tài khoản captain

Quy tắc của [ADR-0005](../../decisions/0005-captain-must-hand-over-before-account-deletion.md). `DELETE /api/v1/profile` vẫn trả `204` khi thành công. Nếu user còn captain ACTIVE của bất kỳ đội nào chưa `DISBANDED`, trả `422 USER_003`; không thay đổi đội, membership, invitation hoặc tài khoản. Sau khi qua guard, hành vi USER-001 giữ nguyên: membership ACTIVE → LEFT; invitation PENDING gửi tới user → REJECTED; không tự đổi trạng thái đội.

**Mã lỗi:** tái dùng `USER_003` (422) với phạm vi mở rộng: áp dụng cho mọi đội chưa `DISBANDED` mà user là captain ACTIVE, kể cả khi là người duy nhất trong đội và đội đang `INACTIVE`. Message đổi thành “Bạn phải chuyển quyền đội trưởng hoặc giải tán đội trước khi xóa tài khoản” (UC-U03 4a1). Giữ mã để client đang bắt `USER_003` không phải đổi.

## 3. DTO mới/thay đổi

### 3.1. DisbandTeamRequest

| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `reason` | `String` | No | `@Size(max = 100)` trên giá trị gốc; service trim, rỗng sau trim → mặc định |

### 3.2. TeamResponse / TeamSummaryResponse

`TeamResponse` thêm các field ở §2.2. `TeamSummaryResponse` và `AdminTeamSummaryResponse` thêm trạng thái `DISBANDED` và `disbandedByName` (`String | null`, null nếu chưa giải tán) để hiển thị người giải tán trong danh sách; áp cùng quy tắc masking §2.2.

## 4. Mã lỗi mới / cập nhật

| Ngữ cảnh | HTTP | Mã/message |
|---|---:|---|
| Thao tác ghi bất kỳ trên đội đã giải tán; giải tán lần nữa; admin lock/reactivate | 422 | **Mới** `TEAM_016` — “Đội đã giải tán”. Một mã dùng chung cho mọi thao tác, kể cả kích hoạt lại; nhất quán quy ước 422 của `TEAM_015` |
| Xoá tài khoản khi caller còn captain đội chưa giải tán | 422 | **Sửa** `USER_003` — “Bạn phải chuyển quyền đội trưởng hoặc giải tán đội trước khi xóa tài khoản”; điều kiện mở rộng theo §2.5 |
| Request reason vượt 100 ký tự | 400 | `VALIDATION_ERROR` theo convention |
| Xung đột optimistic lock hoặc deadlock hết retry | 409 | `TEAM_014` (tái dùng USER-001) |

Đã soát registry `ErrorCode` hiện hành: `TEAM_001`…`TEAM_015`, `USER_001`…`USER_003`; `TEAM_016` chưa dùng.

## 5. Danh sách [cần xác nhận]

1. ~~Chi tiết endpoint Admin lịch sử thành viên~~ — **đã chốt (user, 2026-11-05):** mọi trạng thái đội; chỉ `ACTIVE`/`LEFT`; DTO mới `MemberHistoryResponse` (§2.4).
2. ~~Whitespace-only reason~~ — **đã chốt (user, 2026-11-05):** trim; rỗng sau trim → mặc định (§2.1).
3. ~~Mã lỗi cho đội đã giải tán~~ — **đã chốt (user, 2026-11-05):** `422 TEAM_016` “Đội đã giải tán” cho mọi thao tác ghi, kể cả kích hoạt lại.
4. ~~Masking người giải tán~~ — **đã chốt (user, 2026-11-05):** `DeletedUserMasking` cho `disbandedByName` ở detail và summary; giữ `disbandedById` (§2.2, §3.2).
5. ~~Mã lỗi xoá tài khoản captain~~ — **đã chốt (user, 2026-11-05):** mở rộng `422 USER_003`, message theo UC-U03 4a1.
