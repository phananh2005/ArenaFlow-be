# API Specification — Tra cứu cho người dùng

<!-- File này được publish cho FE (ADR-0010). Viết theo design-standards.md §11:
     không tên class/bảng/cột/migration/transaction/khoá/số truy vấn; không trỏ Component_Class_Design.md,
     Processing_Flow.md, docs/database/. Chi tiết nội bộ BE ghi ở hai file đó.
     Số mục §2.1–§2.5 được USER-001 / USER-002 trỏ tới — giữ nguyên khi sửa. -->

| Mục | Giá trị |
|---|---|
| Mã chức năng | `USER-003` |
| Phạm vi tầng | Backend (Spring Boot REST API) |
| Repo | ArenaFlow-be |
| Quy ước áp dụng | `api-convention.md` (envelope, phân trang, định dạng lỗi, kiểu dữ liệu) |
| Spec nghiệp vụ | `docs/agent/business-spec/USER-003/` (v1.0 — UC-U04, UC-U05, UC-T07, UC-T08; BR-U07..U17, BR-T15..T21) · `docs/agent/business-spec/SYSTEM-001/` (v1.12) |
| Phụ thuộc | [`USER-001/API_Spec.md`](../USER-001/API_Spec.md) — hồ sơ, đội, mời / chấp nhận / từ chối lời mời; [`USER-002/API_Spec.md`](../USER-002/API_Spec.md) — giải tán đội, điều kiện xoá tài khoản |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-17 · v1.0 (baseline) |

## 1. Tổng quan

Bốn chức năng **chỉ xem** giúp hoàn thành các luồng đã có: chọn khu vực hồ sơ từ danh mục tỉnh/thành → phường/xã; đội trưởng tìm người để mời vào đội bằng số điện thoại hoặc tên; xem các lời mời đang chờ của tôi; xem các đội tôi đang tham gia (lọc được các đội tôi làm đội trưởng — đúng danh sách đội đang chặn xoá tài khoản). Không có thao tác ghi mới; mời / chấp nhận / từ chối / chuyển quyền / giải tán dùng endpoint của USER-001, USER-002.

### 1.1. Quy ước chung

- Như USER-001 `API_Spec.md` §1.1: base path `/api/v1`, header `Authorization: Bearer <accessToken>` cho **mọi** endpoint ở đây (kể cả danh mục khu vực), id là UUID dạng chuỗi, thời gian ISO-8601 kèm offset, field không có giá trị trả `null`, enum viết HOA, thứ tự lỗi 401 trước 400.
- **Phân trang:** 2.4, 2.5 phân trang offset như USER-001 §1.1 (`page` ≥ 1 mặc định 1, `pageSize` 1..100 mặc định 20, trang vượt quá → `data: []`; `pagination.totalItems` = tổng số phần tử).
- **Không phân trang (ngoại lệ có chặn trên):**
  - 2.1, 2.2 trả **toàn bộ** danh mục trong một lần: danh mục chỉ đọc, số tỉnh/thành và số phường/xã của một tỉnh/thành có giới hạn tự nhiên; ứng dụng tự lọc khi người dùng gõ (BR-U07). `data` là mảng, **không** có `pagination`.
  - 2.3 trả **tối đa 20** gợi ý, kèm `hasMore` cho biết còn người khớp ngoài 20 gợi ý; không có trang tiếp theo (BR-U10).
- **Người đã xoá tài khoản** ở chỗ hiển thị người khác (`captainName`): tên `"Người dùng đã xóa"` (USER-001 §1.1, BR-U17).
- **Chỉ dữ liệu của chính người gọi** ở 2.4, 2.5 — không có tham số xem thay người khác, kể cả Admin (BR-U16).
- **Dữ liệu danh mục hiện là dữ liệu mẫu** (5 tỉnh/thành, 50 phường/xã); nạp đủ toàn quốc trước khi phát hành cho người dùng thật, không đổi hợp đồng API (BC-U05).

### 1.2. Lỗi chung

Envelope, `VALIDATION_ERROR`, `AUTH_001`, `SYS_*`: USER-001 `API_Spec.md` §1.2 (áp dụng cho mọi endpoint ở đây). Bảng **Lỗi** từng endpoint chỉ liệt kê mã riêng, xếp theo thứ tự BE kiểm — nhiều điều kiện cùng sai thì nhận mã ở dòng trên cùng.

## 2. Endpoints

| # | Method + Path | Dùng khi | Ai gọi được | Thành công |
|---|---|---|---|---|
| 2.1 | `GET /api/v1/provinces` | Mở ô "Khu vực" khi sửa hồ sơ | Đã đăng nhập | 200 |
| 2.2 | `GET /api/v1/provinces/{provinceId}/wards` | Chọn một tỉnh/thành | Đã đăng nhập | 200 |
| 2.3 | `GET /api/v1/teams/{teamId}/invitation-candidates` | Gõ vào ô tìm ở màn "Mời thành viên" | Đội trưởng của đội đang hoạt động | 200 |
| 2.4 | `GET /api/v1/profile/invitations` | Màn "Lời mời của tôi" | Đã đăng nhập (chỉ của mình) | 200 |
| 2.5 | `GET /api/v1/profile/teams` | Màn "Đội của tôi"; sau khi xoá tài khoản bị chặn `USER_003` | Đã đăng nhập (chỉ của mình) | 200 |

---

### 2.1. Danh sách tỉnh/thành — `GET /api/v1/provinces`

**Dùng khi:** người dùng mở ô "Khu vực" ở màn sửa hồ sơ (UC-U04 bước 2). Hồ sơ đã có khu vực → tỉnh/thành hiện tại lấy từ `ward.provinceId` của USER-001 2.5.
**Ai gọi được:** người đã đăng nhập.

**Request:** không tham số.

**Sắp xếp:** theo tên, thứ tự chữ cái tiếng Việt (vd "Cần Thơ", "Đà Nẵng", "Hà Nội", "Hải Phòng", "TP. Hồ Chí Minh"); cùng tên thì theo `provinceId`.

**Response 200** — `data`: `ProvinceItem[]` (§4.2), không có `pagination`. Danh mục rỗng → `data: []`.

```json
{
  "data": [
    { "provinceId": "00000000-0000-7000-8000-000000000004", "provinceName": "Cần Thơ" },
    { "provinceId": "00000000-0000-7000-8000-000000000003", "provinceName": "Đà Nẵng" },
    { "provinceId": "00000000-0000-7000-8000-000000000002", "provinceName": "Hà Nội" },
    { "provinceId": "00000000-0000-7000-8000-000000000005", "provinceName": "Hải Phòng" },
    { "provinceId": "00000000-0000-7000-8000-000000000001", "provinceName": "TP. Hồ Chí Minh" }
  ]
}
```

**Lỗi:** chỉ lỗi chung (§1.2).

---

### 2.2. Danh sách phường/xã của một tỉnh/thành — `GET /api/v1/provinces/{provinceId}/wards`

**Dùng khi:** người dùng chọn một tỉnh/thành (UC-U04 bước 4). Đổi tỉnh/thành → gọi lại với `provinceId` mới, bỏ phường/xã đã chọn trước đó. Lưu khu vực: gửi `wardId` đã chọn vào `PATCH /api/v1/profile` (USER-001 2.6).
**Ai gọi được:** người đã đăng nhập.

**Path params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `provinceId` | UUID | Có | Id **tỉnh/thành** lấy từ 2.1 | `"00000000-0000-7000-8000-000000000001"` |

**Sắp xếp:** theo tên, thứ tự chữ cái tiếng Việt; cùng tên thì theo `wardId`.

**Response 200** — `data`: `WardItem[]` (§4.2), không có `pagination`. Tỉnh/thành chưa có phường/xã nào → `data: []` — FE hiện "Chưa có dữ liệu phường/xã cho tỉnh/thành này" (UC-U04 4b).

```json
{
  "data": [
    { "wardId": "10000000-0000-7000-8000-000000000001", "wardName": "Phường Bến Nghé" },
    { "wardId": "10000000-0000-7000-8000-000000000002", "wardName": "Phường Bến Thành" }
  ]
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `USER_004` | Không có tỉnh/thành với `provinceId` này (danh sách FE đang giữ đã cũ), hoặc `provinceId` là id của một phường/xã | Báo "Không tìm thấy tỉnh/thành"; gọi lại 2.1 và cho chọn lại |

---

### 2.3. Tìm người để mời vào đội — `GET /api/v1/teams/{teamId}/invitation-candidates`

**Dùng khi:** đội trưởng gõ vào **một ô** tìm ở màn "Mời thành viên" của đội `teamId` (UC-U05). Chọn một người **không** có nhãn → gửi lời mời bằng `POST /api/v1/teams/{teamId}/invitations` với `userId` của người đó (USER-001 2.12). Tìm kiếm không tạo lời mời; kết quả không bảo đảm mời được vì dữ liệu có thể đổi giữa lúc tìm và lúc mời (BR-U14) — lỗi khi mời xử lý theo bảng lỗi 2.12.
**Ai gọi được:** đội trưởng của đội `teamId`; đội phải đang `ACTIVE` (cùng điều kiện được mời — BR-U15).
**Giới hạn số lần gọi:** mỗi người dùng tối đa **30 lần trong 60 giây gần nhất**, tính chung mọi lần gọi 2.3 của người đó (tìm theo số điện thoại lẫn theo tên, mọi đội, kể cả lần gọi bị lỗi `USER_005`, `USER_006`, `TEAM_*`); lần gọi bị `400 VALIDATION_ERROR` hoặc bị từ chối `429` không tính. Vượt → `429 USER_007` kèm header `Retry-After` (BR-U15, AC-U05-09).

**Path / Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` | UUID | Có | Đội đang mời | `"0193a1c0-1111-7abc-9def-0123456789ab"` |
| `q` | string | Có | Không rỗng, không chỉ gồm khoảng trắng, ≤ 100 ký tự | `nguyen an` |

**BE hiểu `q` thế nào** (bỏ khoảng trắng đầu/cuối trước):

| `q` gồm | Cách tìm | Quy tắc |
|---|---|---|
| Chỉ chữ số và `+`, khoảng trắng, `.`, `-` | **Số điện thoại** | Bỏ khoảng trắng, `.`, `-`; đầu `+84` hoặc `84` đổi thành `0`; kết quả phải là 10 chữ số bắt đầu bằng `0`, nếu không → `400 USER_005`. So khớp **chính xác** → tối đa 1 người. Vd `+84 987 654 321`, `84987654321`, `0987.654.321`, `0987-654-321` đều tìm `0987654321` (BR-U13). Hệ quả: tên chỉ gồm chữ số không tìm được theo tên (BR-U10) |
| Còn lại | **Tên hiển thị** | Không phân biệt hoa/thường và dấu tiếng Việt; ký tự không phải chữ/số được coi là dấu cách. Còn dưới 2 ký tự chữ/số → `400 USER_006`. **Mỗi từ** của `q` phải khớp **phần đầu của một từ** trong tên (BR-U10) |

Ví dụ tìm theo tên với các người dùng "An Phạm", "Ánh Lê", "Lan Nguyễn", "Nguyễn Văn An", "Trần An":

| `q` | Kết quả (theo thứ tự trả về) | Ghi chú |
|---|---|---|
| `an` | "An Phạm", "Ánh Lê", "Nguyễn Văn An", "Trần An" | Không có "Lan Nguyễn" — "an" nằm giữa từ "Lan" |
| `nguyen` | "Nguyễn Văn An", "Lan Nguyễn" | Không dấu vẫn khớp; tên bắt đầu bằng "nguyen" xếp trước |
| `Nguyen An` | "Nguyễn Văn An" | Mỗi từ khớp đầu một từ |
| `ng.an` | "Nguyễn Văn An" | `.` coi như dấu cách → "ng", "an" |
| `a` hoặc ` a ` | — | `400 USER_006` |

**Ai có trong kết quả:** người dùng đang hoạt động. **Không** có: người đã xoá tài khoản (BR-U12), chính người gọi, tài khoản Admin (BR-U15). Người đã là thành viên đang hoạt động hoặc đang có lời mời chờ từ **đội `teamId`** vẫn có, kèm `membershipStatus` (§3.1). Không có số điện thoại của bất kỳ ai, kể cả khi tìm bằng số điện thoại (BR-U11).

**Sắp xếp** (tìm theo tên): tên **bắt đầu bằng** `q` (đã bỏ dấu, hoa/thường) trước; sau đó theo tên, thứ tự chữ cái tiếng Việt; cùng tên thì theo `userId`. Chỉ trả 20 người đầu.

```
GET /api/v1/teams/0193a1c0-1111-7abc-9def-0123456789ab/invitation-candidates?q=an
```

**Response 200** — `data`: `InvitationCandidateList` (§4.2)

```json
{
  "data": {
    "items": [
      {
        "userId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5b02",
        "displayName": "An Phạm",
        "avatarUrl": "https://cdn.example.com/avatar/an.png",
        "membershipStatus": null
      },
      {
        "userId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5b05",
        "displayName": "Ánh Lê",
        "avatarUrl": null,
        "membershipStatus": null
      },
      {
        "userId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5b03",
        "displayName": "Nguyễn Văn An",
        "avatarUrl": null,
        "membershipStatus": "ACTIVE"
      },
      {
        "userId": "0193a1b2-c3d4-7e5f-8a6b-1c2d3e4f5b04",
        "displayName": "Trần An",
        "avatarUrl": null,
        "membershipStatus": "PENDING"
      }
    ],
    "hasMore": false
  }
}
```

- `items: []` → FE hiện "Không tìm thấy người dùng" (UC-U05 4c — gồm số chưa đăng ký, số của người đã xoá tài khoản, của chính mình, của tài khoản Admin).
- `hasMore: true` → hiện 20 gợi ý kèm "Nhập thêm để thu hẹp kết quả" (UC-U05 3a). Tìm theo số điện thoại luôn `hasMore: false`.
- FE nên chờ người dùng ngừng gõ một khoảng ngắn trước khi gọi, và chỉ gọi khi `q` sau khi bỏ khoảng trắng đầu/cuối có từ 2 ký tự (BE vẫn tự kiểm) — vừa đỡ tải, vừa không chạm giới hạn 30 lần/phút khi gõ bình thường.
- Hiếm: tìm theo tên quá chung (rất nhiều người khớp, vd một họ phổ biến) lúc hệ thống bận có thể quá thời gian xử lý và trả `500 SYS_005` (USER-001 §1.2) — FE hiện "Đã xảy ra lỗi hệ thống" và gợi ý nhập thêm chữ để thu hẹp.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `q` thiếu, rỗng, chỉ gồm khoảng trắng hoặc > 100 ký tự; `teamId` không phải UUID | Không gọi khi ô trống; giới hạn ô nhập 100 ký tự |
| 429 | `USER_007` | Người gọi đã gọi 2.3 đủ 30 lần trong 60 giây gần nhất. Header `Retry-After` = số giây (số nguyên ≥ 1) đến khi gọi lại được | Hiện "Bạn tìm quá nhanh, vui lòng thử lại sau"; tạm khoá ô tìm trong `Retry-After` giây (không đọc được header → 60 giây); không tự gọi lại liên tục |
| 400 | `USER_005` | `q` có dạng số điện thoại nhưng không quy về được số Việt Nam hợp lệ (vd `+84 12345`) | Hiện "Số điện thoại không hợp lệ" dưới ô tìm |
| 400 | `USER_006` | `q` tìm theo tên, sau khi bỏ dấu và ký tự không phải chữ/số còn dưới 2 ký tự | Hiện "Nhập ít nhất 2 ký tự" dưới ô tìm |
| 404 | `TEAM_001` | Không có đội | Quay về danh sách đội |
| 422 | `TEAM_016` | Đội đã giải tán | Tải lại chi tiết đội; ẩn nút mời |
| 422 | `TEAM_015` | Đội đang bị Admin tạm ngưng (`INACTIVE`) | Tải lại chi tiết đội; ẩn nút mời |
| 403 | `TEAM_002` | Người gọi không phải đội trưởng của đội (vd vừa chuyển quyền) | Tải lại chi tiết đội; ẩn nút mời |

---

### 2.4. Lời mời của tôi — `GET /api/v1/profile/invitations`

**Dùng khi:** màn "Lời mời của tôi" (UC-T07). Mỗi lời mời: "Chấp nhận" → `POST /api/v1/invitations/{invitationId}/accept`, "Từ chối" → `POST /api/v1/invitations/{invitationId}/reject` (USER-001 2.13, 2.14); mở chi tiết đội → `GET /api/v1/teams/{teamId}` (USER-001 2.9). Lời mời đã xử lý không còn trong danh sách — tải lại sau khi chấp nhận / từ chối.
**Ai gọi được:** người đã đăng nhập; chỉ thấy lời mời gửi tới chính mình.

**Danh sách gồm:** lời mời **đang chờ** (`PENDING`) gửi tới người gọi, của đội đang hoạt động **và** đội đang bị Admin tạm ngưng (§3.2). Không gồm lời mời đã chấp nhận / đã từ chối, lời mời của đội đã giải tán (đã tự bị từ chối — USER-002), lời mời gửi người khác, lời mời đội của tôi đã gửi đi (BR-T15, BR-T16).

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `page` | integer | Không | ≥ 1, mặc định 1 | `1` |
| `pageSize` | integer | Không | 1..100, mặc định 20 | `20` |

**Sắp xếp:** `invitedAt` mới nhất trước; cùng thời điểm thì `invitationId` giảm dần.

**Response 200** — `data`: `MyInvitation[]` (§4.2) + `pagination`. `pagination.totalItems` = tổng số lời mời đang chờ (BR-T17). Không có lời mời → `data: []`, `totalItems: 0` → FE hiện "Bạn chưa có lời mời nào".

```json
{
  "data": [
    {
      "invitationId": "0193a1d0-2222-7abc-9def-0123456789cd",
      "invitedAt": "2026-11-17T12:30:00Z",
      "team": {
        "teamId": "0193a1c0-1111-7abc-9def-0123456789ab",
        "teamName": "Team Alpha",
        "avatarUrl": null,
        "skillLevels": ["Nhiệt tình", "Rèn luyện"],
        "status": "INACTIVE",
        "captainName": "Hùng",
        "memberCount": 5
      }
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

`team.captainName` là đội trưởng **hiện tại** của đội, không phải người đã gửi lời mời (vd Lan mời rồi chuyển quyền cho Hùng → "Hùng" — BR-T17).

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `USER_001` | Tài khoản đã bị xoá nhưng access token còn hạn | Xoá token đã lưu, về màn đăng nhập |

`page` / `pageSize` sai → `400 VALIDATION_ERROR` (§1.2).

---

### 2.5. Đội của tôi — `GET /api/v1/profile/teams`

**Dùng khi:** màn "Đội của tôi" (UC-T08); sau khi xoá tài khoản bị từ chối `422 USER_003` (USER-001 2.7, USER-002 §2.5) → mở với `role=CAPTAIN` để chuyển quyền (USER-001 2.15) hoặc giải tán (USER-002 §2.1) từng đội. Danh sách lọc `role=CAPTAIN` **đúng bằng** tập đội đang chặn xoá tài khoản: rỗng ⇔ vai trò đội trưởng không còn chặn xoá tài khoản (BR-T21). Mở một đội → `GET /api/v1/teams/{teamId}` (USER-001 2.9).
**Ai gọi được:** người đã đăng nhập; chỉ thấy đội của chính mình.

**Danh sách gồm:** các đội người gọi đang là thành viên đang hoạt động (đội trưởng hoặc thành viên), đội `ACTIVE` **và** `INACTIVE`. Không gồm đội đã rời, đội đã giải tán, đội mới chỉ có lời mời đang chờ (BR-T19). Không có lọc theo trạng thái đội.

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `role` | string (enum) | Không | `CAPTAIN` — chỉ đội tôi làm đội trưởng; bỏ trống = mọi đội | `CAPTAIN` |
| `page` | integer | Không | ≥ 1, mặc định 1 | `1` |
| `pageSize` | integer | Không | 1..100, mặc định 20 | `20` |

**Sắp xếp:** đội tôi làm đội trưởng trước; sau đó `joinedAt` mới nhất trước; cùng thời điểm thì `teamId` giảm dần (vd đội trưởng R tham gia 2026-10-01, thành viên Q tham gia 2026-11-10, thành viên P tham gia 2026-11-01 → R, Q, P — BR-T20).

```
GET /api/v1/profile/teams?role=CAPTAIN&page=1&pageSize=20
```

**Response 200** — `data`: `MyTeam[]` (§4.2) + `pagination`. Rỗng → FE hiện "Bạn chưa tham gia đội nào" (không lọc) hoặc "Bạn không làm đội trưởng đội nào" (`role=CAPTAIN`).

```json
{
  "data": [
    {
      "teamId": "0193a1c0-1111-7abc-9def-0123456789ab",
      "teamName": "Team Alpha",
      "avatarUrl": null,
      "skillLevels": ["Thi đấu"],
      "status": "ACTIVE",
      "captainName": "Nguyễn Văn A",
      "memberCount": 5,
      "role": "CAPTAIN",
      "joinedAt": "2026-10-01T03:00:00Z"
    },
    {
      "teamId": "0193a1c0-3333-7abc-9def-0123456789ef",
      "teamName": "Team Beta",
      "avatarUrl": "https://cdn.example.com/team/beta.png",
      "skillLevels": [],
      "status": "INACTIVE",
      "captainName": "Trần Văn C",
      "memberCount": 8,
      "role": "MEMBER",
      "joinedAt": "2026-11-10T12:00:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 2 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `USER_001` | Tài khoản đã bị xoá nhưng access token còn hạn | Xoá token đã lưu, về màn đăng nhập |

`role` khác `CAPTAIN`, `page` / `pageSize` sai → `400 VALIDATION_ERROR` (§1.2).

---

## 3. Hành động theo trạng thái

### 3.1. Kết quả tìm người (2.3)

| `membershipStatus` | Nhãn FE hiện | Đội trưởng làm được |
|---|---|---|
| `null` | — | Mời (USER-001 2.12) |
| `ACTIVE` | "Đã là thành viên" | Không mời (2.12 sẽ trả `409 TEAM_003`) |
| `PENDING` | "Đã được mời" | Không mời (2.12 sẽ trả `409 TEAM_003`); chưa có API huỷ lời mời |

### 3.2. Lời mời của tôi (2.4) theo trạng thái đội

| `team.status` | Người được mời làm được | FE hiện |
|---|---|---|
| `ACTIVE` | Chấp nhận (USER-001 2.13) · Từ chối (2.14) | Hai nút |
| `INACTIVE` | — (2.13 / 2.14 trả `422 TEAM_015`, lời mời vẫn chờ) | "Đội đang tạm ngưng — chưa thể chấp nhận/từ chối"; ẩn hoặc vô hiệu hai nút. Đội được kích hoạt lại → chấp nhận / từ chối bình thường |
| `DISBANDED` | Không có trong danh sách | — (bấm từ màn cũ → `422 TEAM_016`, gỡ lời mời) |

### 3.3. Đội của tôi (2.5)

Thao tác được phép theo `role` × `status`: bảng USER-001 `API_Spec.md` §3.1 (cột "Đội trưởng" / "Thành viên"). Đội trưởng bị chặn xoá tài khoản xử lý từng đội: `ACTIVE` → chuyển quyền (USER-001 2.15) hoặc giải tán (USER-002 §2.1); `INACTIVE` → chỉ giải tán.

## 4. Kiểu dữ liệu

### 4.1. Enum

| Kiểu / enum | Giá trị | Ý nghĩa |
|---|---|---|
| `membershipStatus` (2.3) | `ACTIVE` \| `PENDING` \| `null` | Người đã là thành viên đang hoạt động / đang có lời mời chờ **từ đội đang mời** / chưa có (cùng giá trị trạng thái lời mời–thành viên của USER-001 §4.1) |
| `team.status` (2.4), `status` (2.5) | `ACTIVE` \| `INACTIVE` | Đội đang hoạt động / bị Admin tạm ngưng. `DISBANDED` không xuất hiện ở 2.4, 2.5 |
| `role` (2.5) | `CAPTAIN` \| `MEMBER` | Vai trò của người gọi trong đội (cùng giá trị `role` trong đội của USER-001 §4.1) |
| Query `role` (2.5) | `CAPTAIN` | Lọc theo field `role`: chỉ đội người gọi làm đội trưởng |
| `skillLevels` (phần tử) | Như USER-001 §4.1 | Nhãn phong cách/trình độ đội |

### 4.2. Đối tượng response

Kiểu `datetime` = chuỗi ISO-8601 kèm offset. "Null?" = có thể là `null`.

**`ProvinceItem`** (2.1)

| Field | Kiểu | Null? | Ý nghĩa |
|---|---|---|---|
| `provinceId` | UUID | Không | Dùng cho 2.2 |
| `provinceName` | string | Không | Tên tỉnh/thành |

**`WardItem`** (2.2)

| Field | Kiểu | Null? | Ý nghĩa |
|---|---|---|---|
| `wardId` | UUID | Không | Gửi vào `wardId` của USER-001 2.6 |
| `wardName` | string | Không | Tên phường/xã |

**`InvitationCandidateList`** (2.3)

| Field | Kiểu | Null? | Ý nghĩa |
|---|---|---|---|
| `items` | `InvitationCandidate[]` | Không | Tối đa 20; `[]` khi không có ai |
| `hasMore` | boolean | Không | `true` khi còn người khớp ngoài 20 gợi ý |

**`InvitationCandidate`**

| Field | Kiểu | Null? | Ý nghĩa |
|---|---|---|---|
| `userId` | UUID | Không | Gửi vào `userId` của USER-001 2.12 |
| `displayName` | string | Không | Tên hiển thị |
| `avatarUrl` | string | Có | Ảnh đại diện |
| `membershipStatus` | enum | Có | §4.1, §3.1 |

**`MyInvitation`** (2.4)

| Field | Kiểu | Null? | Ý nghĩa |
|---|---|---|---|
| `invitationId` | UUID | Không | Dùng cho USER-001 2.13 / 2.14 (cùng giá trị `referenceId` của thông báo `TEAM_INVITATION`) |
| `invitedAt` | datetime | Không | Thời điểm mời |
| `team` | object | Không | Đội mời — bảng dưới |

**`MyInvitation.team`**

| Field | Kiểu | Null? | Ý nghĩa |
|---|---|---|---|
| `teamId` | UUID | Không | Dùng cho USER-001 2.9 |
| `teamName` | string | Không | |
| `avatarUrl` | string | Có | Ảnh đội |
| `skillLevels` | string[] | Không | `[]` khi không có nhãn |
| `status` | enum | Không | `ACTIVE` \| `INACTIVE` (§3.2) |
| `captainName` | string | Có | Đội trưởng hiện tại; `null` khi đội không còn đội trưởng đang hoạt động |
| `memberCount` | integer | Không | Số thành viên đang hoạt động, gồm đội trưởng |

**`MyTeam`** (2.5)

| Field | Kiểu | Null? | Ý nghĩa |
|---|---|---|---|
| `teamId` | UUID | Không | Dùng cho USER-001 2.9 |
| `teamName` | string | Không | |
| `avatarUrl` | string | Có | Ảnh đội |
| `skillLevels` | string[] | Không | `[]` khi không có nhãn |
| `status` | enum | Không | `ACTIVE` \| `INACTIVE` |
| `captainName` | string | Có | Đội trưởng hiện tại (chính người gọi khi `role = CAPTAIN`); `null` khi đội không còn đội trưởng đang hoạt động |
| `memberCount` | integer | Không | Số thành viên đang hoạt động, gồm đội trưởng |
| `role` | enum | Không | Vai trò của người gọi trong đội: `CAPTAIN` \| `MEMBER` |
| `joinedAt` | datetime | Không | Thời điểm người gọi tham gia đội |

## 5. Mã lỗi

| Mã | HTTP | Message | Endpoint |
|---|---|---|---|
| `USER_001` | 404 | Không tìm thấy người dùng | 2.4, 2.5 (dùng lại — USER-001) |
| `USER_004` | 404 | Không tìm thấy tỉnh/thành | 2.2 (**mới**) |
| `USER_005` | 400 | Số điện thoại không hợp lệ | 2.3 (**mới**) |
| `USER_006` | 400 | Nhập ít nhất 2 ký tự | 2.3 (**mới**) |
| `USER_007` | 429 | Bạn tìm quá nhanh, vui lòng thử lại sau | 2.3 (**mới**; kèm header `Retry-After`) |
| `TEAM_001`, `TEAM_002`, `TEAM_015`, `TEAM_016` | 404, 403, 422, 422 | Như USER-001 §5 | 2.3 (dùng lại) |
| `VALIDATION_ERROR`, `AUTH_001`, `SYS_*` | — | Như USER-001 §5 | Mọi endpoint |

## 6. Danh sách [cần xác nhận]

1. `[cần xác nhận]` Header `Retry-After` của `429 USER_007`: app chạy trên trình duyệt (khác origin) chỉ đọc được header này khi BE cấu hình CORS cho phép đọc nó — hiện BE chưa có cấu hình CORS. Không đọc được → FE chờ 60 giây (bảng lỗi 2.3).

Quyết định đã chốt (user, 2026-11-17): SRS `Acceptance_Criteria.md` §5 (Q-01..Q-16); ngoại lệ không phân trang của 2.1–2.3, mã lỗi `USER_004`..`USER_007`, đường dẫn 2.3, giới hạn 30 lần/phút của 2.3.
