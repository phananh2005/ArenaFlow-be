# API Specification — Thương lượng ghép kèo luồng B (pairing) & chat

<!-- File này được publish cho FE (ADR-0010). Viết theo design-standards.md §11:
     không tên class/bảng/cột/migration/transaction/khoá/số truy vấn; không trỏ Component_Class_Design.md,
     Processing_Flow.md, docs/database/. Chi tiết nội bộ BE ghi ở hai file đó. -->

| Mục | Giá trị |
|---|---|
| Mã chức năng | `MATCH-002` |
| Phạm vi tầng | Backend (Spring Boot REST API + kênh STOMP/WebSocket). Frontend: ngoài phạm vi repo này |
| Repo | ArenaFlow-be |
| Quy ước áp dụng | `api-convention.md` (envelope, định dạng lỗi, kiểu dữ liệu); quy ước chung và lỗi chung USER-001 `API_Spec.md` §1.1, §1.2; kèo, trạng thái kèo, thoả thuận chi phí, mã `MATCH_*`/`SPORT_*`: MATCH-001 `API_Spec.md`; mã `TEAM_*`: USER-001/USER-002 `API_Spec.md`; mã `VENUE_002`: VENUE-001 `API_Spec.md`; [ADR-0014](../../decisions/0014-realtime-chat-stomp-websocket.md) (kênh realtime) |
| Spec nghiệp vụ | `docs/agent/business-spec/SYSTEM-001/` (v1.5) — UC-M05, M06, M07, M09, UC-N01, UC-C01, C02 (luồng B); BR-M07, BR-M08, BR-M13..BR-M15, BR-M17..BR-M21, BR-C01..C05, BR-N01, BR-N03; Business_Rules §2.3; AC-M04, AC-M05, AC-C01, AC-N01; Scenario 2, 3 |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-10 · v1.0 (baseline) · hiện tại **v1.1** (solution-architect · 2026-11-11) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan

Kèo **luồng B** (`type = NEED_VENUE`) được tạo bằng MATCH-001 `API_Spec.md` 2.1 với nhiều lựa chọn địa điểm và nhiều khung giờ. Đội khác bấm **"Nhận kèo"** → mở một **thương lượng ghép kèo** (pairing) giữa hai đội trưởng. Một kèo có thể có nhiều thương lượng cùng lúc; kèo vẫn `OPEN` trong lúc trao đổi (BR-M08).

Trong một thương lượng, hai đội trưởng:
- nhắn tin (chat) — REST để gửi và đọc lịch sử, kênh STOMP/WebSocket để nhận tin ngay (§1.6);
- chốt **phương án**: một địa điểm + một khung giờ, lấy từ danh sách lựa chọn của kèo (2.5);
- nhập **thoả thuận chi phí và tiền cọc** (2.6) — ArenaFlow chỉ lưu để hiển thị, **không** thu/giữ/hoàn tiền;
- cả hai **xác nhận** gói (phương án + chi phí) qua nút riêng, không qua tin nhắn (2.7). Bên xác nhận sau cùng làm kèo `CONFIRMED`; các thương lượng khác của kèo → `SUPERSEDED`.

**Ngoài phạm vi:** tạo / tìm / xem / huỷ kèo, đánh giá, thông báo đẩy, xoá chat hết hạn lưu (MATCH-001, REPUTATION-001, NOTIFICATION-001). Không có thao tác rút khỏi hay kết thúc thương lượng: thương lượng chỉ kết thúc khi được xác nhận, bị thay thế hoặc hết hạn. Không tìm kiếm / xuất chat.

### 1.1. Quy ước chung

- Theo USER-001 `API_Spec.md` §1.1, §1.2 (xác thực Bearer, envelope, `400 VALIDATION_ERROR`, `401 AUTH_001` → refresh token, `SYS_*`) và MATCH-001 `API_Spec.md` §1.1 (thời gian ISO-8601 trả UTC, tiền VND số nguyên, chuỗi được bỏ khoảng trắng đầu/cuối, `""` ở field tuỳ chọn = không nhập, enum UPPERCASE).
- Danh sách thương lượng (2.2, 2.3) phân trang `page` / `pageSize` như MATCH-001. Lịch sử chat (2.9) phân trang bằng **con trỏ** (`before` / `after`), không có `pagination`.
- `201` không kèm header `Location` — lấy id từ body.

### 1.2. Ai thao tác được

- Chỉ **đội trưởng đang hoạt động** của **đội tạo kèo** hoặc **đội nhận kèo** của thương lượng. Người khác gọi → `403 PAIRING_002`.
- Endpoint ghi (2.1, 2.5–2.8, 2.10) gửi `teamId` = đội của mình trong thương lượng (một người làm đội trưởng cả hai đội vẫn được — BR-M18 — FE cho chọn phía). `teamId` không phải một trong hai đội → `403 PAIRING_002`; không phải đội trưởng của `teamId` → `403 TEAM_002`; đội đã giải tán → `422 TEAM_016`.
- Endpoint đọc (2.4, 2.9) không cần `teamId`: chỉ cần là đội trưởng của một trong hai đội.

**Trạng thái đội ảnh hưởng thao tác:**

| Thao tác | Đội mình `INACTIVE` (tạm ngưng) | Đội còn lại không `ACTIVE` | Bộ môn của kèo đang tắt |
|---|---|---|---|
| Nhận kèo (2.1) | `422 TEAM_015` | `422 MATCH_013` | `422 SPORT_002` |
| Chốt phương án (2.5), sửa chi phí (2.6) | `422 TEAM_015` | Được phép | Được phép |
| Xác nhận (2.7) | `422 TEAM_015` | `422 MATCH_013` | `422 SPORT_002` |
| Xem (2.2–2.4), nhắn tin (2.8), đọc / đánh dấu đã đọc (2.9, 2.10) | Được phép | Được phép | Được phép |

**Địa điểm đã ngừng hoạt động** (`venueStatus = INACTIVE`, Admin đánh dấu — VENUE-001): không chốt được làm phương án (2.5) và không xác nhận được phương án đang chứa địa điểm đó (2.7) → `422 VENUE_002`. Phương án đã chốt trước khi địa điểm bị ngừng hoạt động vẫn hiển thị ở 2.4 (`proposal.venueStatus = "INACTIVE"`); FE cho chốt lại địa điểm khác.

### 1.3. Trạng thái thương lượng & hết hạn

| Trạng thái | Ý nghĩa |
|---|---|
| `NEGOTIATING` | Đang trao đổi — chat, chốt phương án, sửa chi phí, xác nhận |
| `CONFIRMED` | Cả hai đã xác nhận; kèo đã ghép với đội nhận kèo |
| `SUPERSEDED` | Kèo đã ghép với thương lượng khác, hoặc kèo bị huỷ |
| `EXPIRED` | Kèo hết hạn bài đăng khi đang trao đổi |

- Thương lượng `NEGOTIATING` của kèo `OPEN` đã quá `expiresAt` được mọi API đọc trả `status = "EXPIRED"` ngay. Thao tác ghi trên thương lượng đó → `409 MATCH_004`; kèo và thương lượng chuyển hẳn sang `EXPIRED` (như MATCH-001 `API_Spec.md` §1.3).
- Hết hạn kèo được tính theo `expiresAt` của kèo, không có hạn riêng cho thương lượng.
- Kèo bị huỷ khi đang `OPEN` (MATCH-001 2.5) → mọi thương lượng `NEGOTIATING` → `SUPERSEDED`. Đội tạo kèo không phải đóng thương lượng trước khi huỷ.
- `CONFIRMED`, `SUPERSEDED`, `EXPIRED` là trạng thái cuối. Sau đó vẫn **đọc** được chi tiết và lịch sử chat; không nhắn tin, không sửa gói.

### 1.4. Gói thương lượng và `revision`

**Gói** = phương án (địa điểm + khung giờ) + thoả thuận chi phí + tiền cọc của một thương lượng. Mỗi thương lượng có gói riêng; các thương lượng khác của cùng kèo không bị ảnh hưởng.

- `revision` (số nguyên, bắt đầu `0`) tăng 1 mỗi lần chốt phương án (2.5) hoặc lưu chi phí (2.6) thành công.
- Mỗi lần gói đổi, **xác nhận của cả hai bên bị xoá** (`authorConfirmedAt`, `interestedConfirmedAt` → `null`): xác nhận là đồng ý cả gói đang hiển thị.
- 2.5, 2.6, 2.7 **bắt buộc** gửi `revision` = giá trị FE đang hiển thị. `revision` được kiểm **trước** nội dung gửi lên: khác giá trị hiện tại → `409 PAIRING_006` "Phương án đã thay đổi, vui lòng tải lại" (kể cả khi nội dung giống gói hiện tại): không ghi, không xác nhận. FE tải lại chi tiết (2.4), cho người dùng xem gói mới rồi tự bấm lại; **không** tự gửi lại với `revision` mới.
- Chốt lại đúng phương án đang có (2.5) với `revision` **đúng** không làm đổi gói: trả `200`, `revision` và xác nhận giữ nguyên.

### 1.5. Xác nhận và ghép kèo

- Xác nhận cần phương án đã chốt (`422 PAIRING_007` nếu chưa).
- Bên đầu tiên xác nhận → thương lượng vẫn `NEGOTIATING`, ghi thời điểm xác nhận của bên đó. Bên còn lại xác nhận → thương lượng `CONFIRMED`, kèo `CONFIRMED` với:
  - đội đã ghép = đội nhận kèo;
  - `startsAt` / `endsAt` = khung giờ đã chốt; `venue` = địa điểm đã chốt;
  - `costAgreement` của kèo = bản chép thoả thuận chi phí + tiền cọc + khoản tự nhập của thương lượng tại lúc ghép (khoản tự nhập của kèo có `itemId` mới; thương lượng giữ nguyên thoả thuận của mình ở 2.4);
  - mọi thương lượng `NEGOTIATING` khác của kèo → `SUPERSEDED`.
- Hai thương lượng của cùng kèo được hoàn tất gần như cùng lúc: **thương lượng hoàn tất (bên thứ hai xác nhận) trước thắng**; thương lượng kia → `SUPERSEDED`, lệnh xác nhận của nó nhận `409 PAIRING_005` (AC-M05).
- Gọi xác nhận lặp lại khi bên mình đã xác nhận đúng `revision` đó → `200`, không đổi gì.
- Sau khi kèo `CONFIRMED`: huỷ kèo, trừ điểm uy tín, lưu trữ theo MATCH-001 `API_Spec.md` 2.5, §1.3, §1.5 (đội nhận kèo là "đội đã ghép").

### 1.6. Kênh realtime (STOMP over WebSocket)

Dùng để nhận tin nhắn mới và thay đổi của thương lượng ngay khi xảy ra. **Không** gửi tin qua kênh này (gửi bằng 2.8). Không bảo đảm giao frame — sau mỗi lần kết nối lại, FE làm **đúng thứ tự**:
1. `CONNECT` rồi `SUBSCRIBE` lại các thương lượng đang mở (từ lúc này tin mới đến qua frame);
2. gọi 2.9 với `after` = `messageId` mới nhất đang có (lặp khi `hasMore = true`) để bù tin bị lỡ trong lúc mất kết nối, và 2.4 để lấy trạng thái mới;
3. gộp kết quả 2.9 với frame `MESSAGE_CREATED` nhận được trong lúc gọi 2.9, **bỏ trùng theo `messageId`** (một tin có thể đến qua cả hai đường).

Gọi 2.9 trước khi `SUBSCRIBE` có thể lỡ tin gửi giữa hai bước. Hiếm khi một tin gửi đúng lúc mất kết nối có thể không có trong kết quả `after`; tin đó vẫn hiện khi tải lại trang mới nhất (2.9 không con trỏ) — FE nên tải lại trang mới nhất mỗi khi mở lại màn hình chat.

| Bước | Cách làm |
|---|---|
| Kết nối | SockJS tới `/ws` (cùng host với API, không có tiền tố `/api/v1`) `[cần xác nhận #1]`, rồi STOMP `CONNECT` với header `Authorization: Bearer <access token>`. Heartbeat `10000,10000` |
| Lỗi kết nối | Token thiếu / sai / hết hạn / là refresh token → frame `ERROR` header `message: AUTH_001` rồi đóng. FE refresh token (USER-001) rồi kết nối lại |
| Đăng ký | `SUBSCRIBE` `/topic/pairings/{pairingId}` cho từng thương lượng đang mở trên màn hình. Destination phải đúng mẫu này (không wildcard, vd `/topic/**` → `ERROR`). Header `selector` không được hỗ trợ (bị bỏ qua) — FE tự lọc frame theo `type` |
| Lỗi đăng ký | Destination sai mẫu → `ERROR`; không có thương lượng → `ERROR` `message: PAIRING_001`; không phải đội trưởng của một trong hai đội → `ERROR` `message: PAIRING_002`; token đã hết hạn → `ERROR` `message: AUTH_001`. Server đóng kết nối sau frame `ERROR` → FE xử lý rồi kết nối lại |
| Gửi | `SEND` bị từ chối (frame `ERROR`) |
| Thời gian sống | Kết nối không tự hết hạn theo token; quyền chỉ kiểm lúc `CONNECT` và `SUBSCRIBE`. FE kết nối lại sau mỗi lần refresh token |

**Frame server gửi** (body JSON):

| `type` | Khi nào | Nội dung |
|---|---|---|
| `MESSAGE_CREATED` | Có tin mới trong thương lượng (kể cả tin của chính mình) | `message`: object Tin nhắn (§4.4) |
| `PAIRING_UPDATED` | Chốt phương án (2.5), lưu chi phí (2.6), một bên xác nhận, thương lượng `CONFIRMED` (2.7) | `pairingId`, `status`, `revision`, `authorConfirmedAt`, `interestedConfirmedAt`, `confirmedAt`, `change` (`PROPOSAL` \| `COST` \| `CONFIRMATION`) |

```json
{ "type": "MESSAGE_CREATED",
  "message": { "messageId": "01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e01", "pairingId": "01933d40-7e8f-7a90-8b1c-2d3e4f5a6b01",
               "senderTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
               "senderUserId": "01932e00-5a6b-7c7d-8e8f-9a0b1c2d3e12", "senderName": "Nam", "senderAvatarUrl": null,
               "content": "18:00 sân Y được không?", "isRead": false, "createdAt": "2026-11-10T08:00:00Z" } }
```

```json
{ "type": "PAIRING_UPDATED", "pairingId": "01933d40-7e8f-7a90-8b1c-2d3e4f5a6b01", "status": "NEGOTIATING",
  "revision": 3, "authorConfirmedAt": "2026-11-10T08:10:00Z", "interestedConfirmedAt": null, "confirmedAt": null,
  "change": "CONFIRMATION" }
```

- Nhận `PAIRING_UPDATED` có `revision` khác giá trị đang hiển thị → tải lại 2.4 trước khi cho bấm xác nhận.
- **Không** có frame khi thương lượng chuyển `SUPERSEDED` / `EXPIRED` hoặc kèo bị huỷ: FE biết qua lỗi `PAIRING_005` / `MATCH_004` ở thao tác kế tiếp, hoặc khi tải lại `[cần xác nhận #2]`.

## 2. Endpoints

| # | Method + Path | Dùng khi | Ai gọi được | Thành công |
|---|---|---|---|---|
| 2.1 | `POST /api/v1/matches/{matchId}/pairings` | Nhận kèo luồng B | Đội trưởng đội nhận kèo | 201 |
| 2.2 | `GET /api/v1/matches/{matchId}/pairings` | Đội tạo kèo xem các đội đang thương lượng | Đội trưởng đội tạo kèo | 200 |
| 2.3 | `GET /api/v1/teams/{teamId}/pairings` | Danh sách thương lượng của đội | Đội trưởng của đội | 200 |
| 2.4 | `GET /api/v1/pairings/{pairingId}` | Mở màn hình thương lượng | Đội trưởng của một trong hai đội | 200 |
| 2.5 | `PUT /api/v1/pairings/{pairingId}/proposal` | Chốt địa điểm + khung giờ | Đội trưởng của một trong hai đội | 200 |
| 2.6 | `PUT /api/v1/pairings/{pairingId}/cost-agreement` | Lưu chi phí + tiền cọc | Đội trưởng của một trong hai đội | 200 |
| 2.7 | `POST /api/v1/pairings/{pairingId}/confirm` | Bấm "Xác nhận" | Đội trưởng của một trong hai đội | 200 |
| 2.8 | `POST /api/v1/pairings/{pairingId}/messages` | Gửi tin nhắn | Đội trưởng của một trong hai đội | 201 |
| 2.9 | `GET /api/v1/pairings/{pairingId}/messages` | Đọc lịch sử chat, bù tin sau khi kết nối lại | Đội trưởng của một trong hai đội | 200 |
| 2.10 | `POST /api/v1/pairings/{pairingId}/messages/read` | Đánh dấu đã đọc tin của bên kia | Đội trưởng của một trong hai đội | 204 |

Lỗi chung `400 VALIDATION_ERROR`, `401 AUTH_001` (§1.1) áp dụng cho mọi endpoint. Path id sai định dạng UUID → `400 VALIDATION_ERROR`.

---

### 2.1. Nhận kèo — `POST /api/v1/matches/{matchId}/pairings`

**Dùng khi:** nút "Nhận kèo" trên kèo `NEED_VENUE` đang mở (UC-M05).
**Ai gọi được:** đội trưởng của đội `teamId`; đội phải `ACTIVE`; không phải đội tạo kèo.

Tạo thương lượng `NEGOTIATING`, `revision = 0`, chưa có phương án / chi phí. Mỗi đội chỉ có **một** thương lượng cho một kèo (kể cả đã `CONFIRMED`); sau khi thương lượng `SUPERSEDED` / `EXPIRED` thì kèo cũng không còn mở. Kèo vẫn `OPEN` cho đội khác nhận (BR-M08).

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` | string (UUID) | Có | Đội nhận kèo | `"01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02"` |

```json
{ "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02" }
```

**Response 201** — Thương lượng (chi tiết) (§4.2).

```json
{
  "data": {
    "pairingId": "01933d40-7e8f-7a90-8b1c-2d3e4f5a6b01",
    "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b02",
    "status": "NEGOTIATING",
    "revision": 0,
    "matchExpiresAt": "2026-11-12T05:00:00Z",
    "authorTeam": { "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01", "teamName": "Team Alpha", "avatarUrl": null, "reliabilityScore": 80, "captainName": "Minh" },
    "interestedTeam": { "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02", "teamName": "Team Beta", "avatarUrl": null, "reliabilityScore": 75, "captainName": "Nam" },
    "proposal": null,
    "authorConfirmedAt": null,
    "interestedConfirmedAt": null,
    "confirmedAt": null,
    "costAgreement": {
      "costType": null, "costSplit": null, "venueAmount": null, "drinksAmount": null,
      "depositAmount": null, "depositHolder": null, "customItems": []
    },
    "createdAt": "2026-11-10T07:55:00Z",
    "updatedAt": "2026-11-10T07:55:00Z"
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 403 | `TEAM_002` | Không phải đội trưởng của `teamId` | Chỉ cho chọn đội mình làm đội trưởng |
| 404 | `MATCH_001` | Không có kèo | Quay lại danh sách |
| 404 | `TEAM_001` | Không có đội `teamId` | Tải lại danh sách đội |
| 409 | `MATCH_003` | Kèo đã được ghép | Hiện "Kèo đã được ghép"; tải lại kết quả tìm kèo |
| 409 | `MATCH_004` | Kèo đã hết hạn | Hiện message; tải lại kết quả tìm kèo |
| 409 | `MATCH_005` | Kèo đã huỷ / lưu trữ / hoàn thành | Tải lại kèo |
| 409 | `MATCH_014` | Có thao tác khác đang cập nhật kèo cùng lúc | Cho bấm lại |
| 409 | `PAIRING_003` | Đội đã nhận kèo này | Mở thương lượng đã có (tìm ở 2.3) |
| 422 | `MATCH_013` | Đội tạo kèo không còn hoạt động | Hiện message; tải lại kết quả tìm kèo |
| 422 | `MATCH_016` | Kèo là `HAS_VENUE` (luồng A) | Dùng "Gửi yêu cầu" (MATCH-001 2.6) |
| 422 | `PAIRING_004` | `teamId` là đội tạo kèo | Ẩn nút "Nhận kèo" trên kèo của đội mình |
| 422 | `SPORT_002` | Bộ môn của kèo đang tắt | Hiện message; tải lại kết quả tìm kèo |
| 422 | `TEAM_015` | Đội mình đang tạm ngưng | Hiện message |
| 422 | `TEAM_016` | Đội mình đã giải tán | Hiện message; tải lại danh sách đội |

---

### 2.2. Thương lượng của một kèo — `GET /api/v1/matches/{matchId}/pairings`

**Dùng khi:** đội tạo kèo xem các đội đang thương lượng trên kèo của mình.
**Ai gọi được:** đội trưởng đội tạo kèo (đội `INACTIVE` vẫn xem được).

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `status` | string (enum) | Không | `NEGOTIATING` \| `CONFIRMED` \| `SUPERSEDED` \| `EXPIRED` \| `ALL`; mặc định `ALL`; lọc theo trạng thái hiển thị (§1.3) | `NEGOTIATING` |
| `page`, `pageSize` | integer | Không | §1.1 | `1`, `20` |

**Thứ tự:** `createdAt` ↑, `pairingId` ↑ (nhận kèo trước lên trước).

**Response 200** — mảng Thương lượng (rút gọn) (§4.3) + `pagination`.

```json
{
  "data": [
    {
      "pairingId": "01933d40-7e8f-7a90-8b1c-2d3e4f5a6b01",
      "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b02",
      "status": "NEGOTIATING",
      "revision": 2,
      "authorTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01",
      "authorTeamName": "Team Alpha",
      "interestedTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
      "interestedTeamName": "Team Beta",
      "interestedTeamReliabilityScore": 75,
      "proposalVenueId": "01933a10-4b5c-7d6e-8f70-81a2b3c4d5e2",
      "proposalTimeOptionId": "01933b70-1a2b-7c3d-8e4f-5a6b7c8d9e11",
      "authorConfirmedAt": null,
      "interestedConfirmedAt": "2026-11-10T08:05:00Z",
      "confirmedAt": null,
      "matchStartsAt": "2026-11-12T11:00:00Z",
      "matchExpiresAt": "2026-11-12T05:00:00Z",
      "createdAt": "2026-11-10T07:55:00Z",
      "updatedAt": "2026-11-10T08:05:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `status` ngoài danh sách; phân trang sai | Sửa tham số |
| 403 | `TEAM_002` | Không phải đội trưởng đội tạo kèo | Ẩn mục "Đội đang thương lượng" |
| 404 | `MATCH_001` | Không có kèo | Quay lại danh sách |
| 422 | `MATCH_016` | Kèo là `HAS_VENUE` | Dùng danh sách yêu cầu (MATCH-001 2.7) |
| 422 | `TEAM_016` | Đội tạo kèo đã giải tán | Hiện message |

---

### 2.3. Thương lượng của đội — `GET /api/v1/teams/{teamId}/pairings`

**Dùng khi:** trang "Thương lượng" của đội — mở lại chat, theo dõi xác nhận.
**Ai gọi được:** đội trưởng của đội `teamId` (đội `INACTIVE` vẫn xem được).

**Path / Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` (path) | string (UUID) | Có | — | `01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02` |
| `role` | string (enum) | Không | `AUTHOR` (kèo của đội mình) \| `INTERESTED` (đội mình nhận kèo) \| `ALL`; mặc định `ALL` | `INTERESTED` |
| `status` | string (enum) | Không | Như 2.2; mặc định `ALL` | `NEGOTIATING` |
| `page`, `pageSize` | integer | Không | §1.1 | `1`, `20` |

**Thứ tự:** `updatedAt` ↓, `pairingId` ↓ (vừa thay đổi lên trước).

**Response 200** — mảng Thương lượng (rút gọn) (§4.3) + `pagination`. Ví dụ `role=ALL` của Team Beta: một thương lượng Beta nhận kèo của Alpha (đã ghép), một thương lượng trên kèo của Beta (đang trao đổi với Team Gamma):

```json
{
  "data": [
    {
      "pairingId": "01933d40-7e8f-7a90-8b1c-2d3e4f5a6b01",
      "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b02",
      "status": "CONFIRMED",
      "revision": 3,
      "authorTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01",
      "authorTeamName": "Team Alpha",
      "interestedTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
      "interestedTeamName": "Team Beta",
      "interestedTeamReliabilityScore": 75,
      "proposalVenueId": "01933a10-4b5c-7d6e-8f70-81a2b3c4d5e2",
      "proposalTimeOptionId": "01933b70-1a2b-7c3d-8e4f-5a6b7c8d9e11",
      "authorConfirmedAt": "2026-11-10T08:10:00Z",
      "interestedConfirmedAt": "2026-11-10T08:06:00Z",
      "confirmedAt": "2026-11-10T08:10:00Z",
      "matchStartsAt": "2026-11-12T11:00:00Z",
      "matchExpiresAt": "2026-11-12T05:00:00Z",
      "createdAt": "2026-11-10T07:55:00Z",
      "updatedAt": "2026-11-10T08:10:00Z"
    },
    {
      "pairingId": "01933d40-7e8f-7a90-8b1c-2d3e4f5a6b07",
      "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b09",
      "status": "NEGOTIATING",
      "revision": 0,
      "authorTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
      "authorTeamName": "Team Beta",
      "interestedTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e03",
      "interestedTeamName": "Team Gamma",
      "interestedTeamReliabilityScore": 90,
      "proposalVenueId": null,
      "proposalTimeOptionId": null,
      "authorConfirmedAt": null,
      "interestedConfirmedAt": null,
      "confirmedAt": null,
      "matchStartsAt": "2026-11-15T12:00:00Z",
      "matchExpiresAt": "2026-11-15T06:00:00Z",
      "createdAt": "2026-11-10T07:40:00Z",
      "updatedAt": "2026-11-10T07:40:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 2 }
}
```

Phía của đội mình: so `teamId` với `authorTeamId` (kèo của đội mình) hoặc `interestedTeamId` (đội mình nhận kèo).

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `role` / `status` ngoài danh sách; phân trang sai | Sửa tham số |
| 403 | `TEAM_002` | Không phải đội trưởng của đội | Ẩn trang với người không phải đội trưởng |
| 404 | `TEAM_001` | Không có đội | Hiện "Không tìm thấy đội" |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |

---

### 2.4. Xem chi tiết thương lượng — `GET /api/v1/pairings/{pairingId}`

**Dùng khi:** mở màn hình thương lượng (phương án, chi phí, trạng thái xác nhận); tải lại sau `PAIRING_006`, `PAIRING_005`, `MATCH_004`, hoặc frame `PAIRING_UPDATED` có `revision` mới.
**Ai gọi được:** đội trưởng của đội tạo kèo hoặc đội nhận kèo. Danh sách lựa chọn địa điểm / khung giờ lấy từ chi tiết kèo (MATCH-001 2.3, `venueOptions`, `timeOptions`). Đầu phòng chat hiển thị `authorTeam` / `interestedTeam` (tên đội + `captainName` — đội trưởng **hiện tại**); tên đội của từng tin nhắn lấy từ hai object này theo `senderTeamId` (§4.4).

**Response 200** — Thương lượng (chi tiết) (§4.2). Ví dụ đã chốt phương án, đội nhận kèo đã xác nhận:

```json
{
  "data": {
    "pairingId": "01933d40-7e8f-7a90-8b1c-2d3e4f5a6b01",
    "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b02",
    "status": "NEGOTIATING",
    "revision": 2,
    "matchExpiresAt": "2026-11-12T05:00:00Z",
    "authorTeam": { "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01", "teamName": "Team Alpha", "avatarUrl": null, "reliabilityScore": 80, "captainName": "Minh" },
    "interestedTeam": { "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02", "teamName": "Team Beta", "avatarUrl": null, "reliabilityScore": 75, "captainName": "Lan" },
    "proposal": {
      "venueId": "01933a10-4b5c-7d6e-8f70-81a2b3c4d5e2",
      "venueName": "Sân Y",
      "address": "10 Lê Lợi, Phường Bến Nghé, TP.HCM",
      "venueStatus": "ACTIVE",
      "timeOptionId": "01933b70-1a2b-7c3d-8e4f-5a6b7c8d9e11",
      "startsAt": "2026-11-12T11:00:00Z",
      "endsAt": "2026-11-12T13:00:00Z"
    },
    "authorConfirmedAt": null,
    "interestedConfirmedAt": "2026-11-10T08:05:00Z",
    "confirmedAt": null,
    "costAgreement": {
      "costType": "VENUE_ONLY", "costSplit": "FIFTY_FIFTY", "venueAmount": 600000, "drinksAmount": null,
      "depositAmount": 200000, "depositHolder": "Đội chủ nhà",
      "customItems": [ { "itemId": "01933d50-1a2b-7c3d-8e4f-5a6b7c8d9e21", "itemName": "Tiền trọng tài", "amount": 200000 } ]
    },
    "createdAt": "2026-11-10T07:55:00Z",
    "updatedAt": "2026-11-10T08:05:00Z"
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 403 | `PAIRING_002` | Không phải đội trưởng của đội nào trong thương lượng | Quay lại; ẩn lối vào thương lượng |
| 404 | `PAIRING_001` | Không có thương lượng | Quay lại danh sách (2.3) |

---

### 2.5. Chốt phương án — `PUT /api/v1/pairings/{pairingId}/proposal`

**Dùng khi:** chọn một địa điểm + một khung giờ trong danh sách lựa chọn của kèo để hai bên cùng xác nhận (UC-M07 tiền điều kiện "đã thống nhất địa điểm/giờ").
**Ai gọi được:** đội trưởng của một trong hai đội; đội mình `ACTIVE`; thương lượng `NEGOTIATING`.

Thay phương án cũ. Kiểm theo thứ tự: `revision` (khác giá trị hiện tại → `409 PAIRING_006`, kể cả khi phương án giống cũ) → địa điểm / khung giờ thuộc lựa chọn của kèo (`422 PAIRING_008`) → địa điểm đang hoạt động (`422 VENUE_002`) → so với phương án đang có:
- khác → `revision + 1`, xoá xác nhận của cả hai bên (§1.4);
- giống (và `revision` đúng) → không đổi gì, trả trạng thái hiện tại.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` | string (UUID) | Có | Đội của mình trong thương lượng | `"01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02"` |
| `revision` | integer | Có | ≥ 0; giá trị đang hiển thị (§1.4) | `1` |
| `venueId` | string (UUID) | Có | Một trong `venueOptions` của kèo | `"01933a10-4b5c-7d6e-8f70-81a2b3c4d5e2"` |
| `timeOptionId` | string (UUID) | Có | Một trong `timeOptions` của kèo | `"01933b70-1a2b-7c3d-8e4f-5a6b7c8d9e11"` |

```json
{ "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02", "revision": 1,
  "venueId": "01933a10-4b5c-7d6e-8f70-81a2b3c4d5e2", "timeOptionId": "01933b70-1a2b-7c3d-8e4f-5a6b7c8d9e11" }
```

**Response 200**

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `pairingId` | string (UUID) | |
| `revision` | integer | `revision` mới (giữ nguyên khi phương án không đổi) |
| `proposal` | object | Như §4.2 `proposal` |
| `authorConfirmedAt`, `interestedConfirmedAt` | string (thời gian) \| null | `null` sau khi phương án đổi |
| `updatedAt` | string (thời gian) | |

```json
{
  "data": {
    "pairingId": "01933d40-7e8f-7a90-8b1c-2d3e4f5a6b01",
    "revision": 2,
    "proposal": {
      "venueId": "01933a10-4b5c-7d6e-8f70-81a2b3c4d5e2", "venueName": "Sân Y",
      "address": "10 Lê Lợi, Phường Bến Nghé, TP.HCM", "venueStatus": "ACTIVE",
      "timeOptionId": "01933b70-1a2b-7c3d-8e4f-5a6b7c8d9e11",
      "startsAt": "2026-11-12T11:00:00Z", "endsAt": "2026-11-12T13:00:00Z"
    },
    "authorConfirmedAt": null,
    "interestedConfirmedAt": null,
    "updatedAt": "2026-11-10T08:02:00Z"
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu field, `revision` < 0 | Hiện lỗi |
| 403 | `PAIRING_002` | `teamId` không phải một trong hai đội | Gửi đúng `teamId` của phía mình |
| 403 | `TEAM_002` | Không phải đội trưởng của `teamId` | Ẩn nút chốt |
| 404 | `PAIRING_001` | Không có thương lượng | Quay lại danh sách (2.3) |
| 409 | `MATCH_004` | Kèo đã hết hạn | Tải lại (2.4) |
| 409 | `MATCH_014` | Có thao tác khác đang cập nhật cùng lúc | Tải lại (2.4) rồi cho bấm lại |
| 409 | `PAIRING_005` | Thương lượng không còn `NEGOTIATING` | Tải lại (2.4); ẩn nút chốt |
| 409 | `PAIRING_006` | `revision` khác giá trị hiện tại (§1.4) | Hiện message; tải lại (2.4); người dùng tự chọn lại |
| 422 | `PAIRING_008` | `venueId` / `timeOptionId` không thuộc lựa chọn của kèo | Chỉ cho chọn trong `venueOptions` / `timeOptions` của kèo |
| 422 | `TEAM_015` | Đội mình đang tạm ngưng | Hiện message |
| 422 | `TEAM_016` | Đội mình đã giải tán | Hiện message |
| 422 | `VENUE_002` | Địa điểm được chọn đã ngừng hoạt động | Hiện "Địa điểm đã ngừng hoạt động"; làm mờ lựa chọn có `venueStatus = INACTIVE` trong `venueOptions` (MATCH-001 2.3); cho chọn địa điểm khác |

---

### 2.6. Lưu chi phí & tiền cọc — `PUT /api/v1/pairings/{pairingId}/cost-agreement`

**Dùng khi:** một trong hai đội trưởng nhập / sửa / xoá thoả thuận chi phí và tiền cọc của thương lượng (UC-C01, UC-C02 luồng B). Bên kia xem qua 2.4 / frame `PAIRING_UPDATED`; đồng ý bằng cách xác nhận (2.7).
**Ai gọi được:** đội trưởng của một trong hai đội; đội mình `ACTIVE`; thương lượng `NEGOTIATING`.

Mỗi lần gửi **thay toàn bộ** thoả thuận cũ (kể cả khoản tự nhập), `revision + 1`, xoá xác nhận của cả hai bên (§1.4) — kể cả khi nội dung giống bản cũ. ArenaFlow không thu/giữ/hoàn tiền (kể cả tiền cọc); hai đội tự thanh toán bên ngoài.

**Request body** — quy tắc `costType`, `costSplit`, `venueAmount`, `drinksAmount`, `customItems` **giống** MATCH-001 `API_Spec.md` 2.4; thêm:

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` | string (UUID) | Có | Đội của mình trong thương lượng | `"01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01"` |
| `revision` | integer | Có | ≥ 0; giá trị đang hiển thị (§1.4) | `2` |
| `depositAmount` | integer \| null | Không | 1..1 000 000 000; `null` = không có tiền cọc | `200000` |
| `depositHolder` | string \| null | Không | ≤ 100 ký tự; phải `null` khi `depositAmount` = `null` | `"Đội chủ nhà"` |

```json
{
  "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01",
  "revision": 2,
  "costType": "VENUE_ONLY",
  "costSplit": "FIFTY_FIFTY",
  "venueAmount": 600000,
  "drinksAmount": null,
  "depositAmount": 200000,
  "depositHolder": "Đội chủ nhà",
  "customItems": [ { "itemName": "Tiền trọng tài", "amount": 200000 } ]
}
```

**Response 200**

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `pairingId` | string (UUID) | |
| `revision` | integer | `revision` mới |
| `costAgreement` | object | Như §4.2 `costAgreement`; khoản tự nhập được cấp `itemId` mới mỗi lần lưu, đúng thứ tự FE gửi |
| `authorConfirmedAt`, `interestedConfirmedAt` | null | Luôn `null` sau khi lưu |
| `updatedAt` | string (thời gian) | |

```json
{
  "data": {
    "pairingId": "01933d40-7e8f-7a90-8b1c-2d3e4f5a6b01",
    "revision": 3,
    "costAgreement": {
      "costType": "VENUE_ONLY", "costSplit": "FIFTY_FIFTY", "venueAmount": 600000, "drinksAmount": null,
      "depositAmount": 200000, "depositHolder": "Đội chủ nhà",
      "customItems": [ { "itemId": "01933d50-1a2b-7c3d-8e4f-5a6b7c8d9e21", "itemName": "Tiền trọng tài", "amount": 200000 } ]
    },
    "authorConfirmedAt": null,
    "interestedConfirmedAt": null,
    "updatedAt": "2026-11-10T08:04:00Z"
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Sai field; quan hệ `costType` ↔ số tiền ↔ `costSplit`; > 10 khoản; `depositHolder` khi không có `depositAmount` | Hiện lỗi cạnh field |
| 403 | `PAIRING_002` | `teamId` không phải một trong hai đội | Gửi đúng `teamId` |
| 403 | `TEAM_002` | Không phải đội trưởng của `teamId` | Ẩn nút sửa chi phí |
| 404 | `PAIRING_001` | Không có thương lượng | Quay lại danh sách (2.3) |
| 409 | `MATCH_004` | Kèo đã hết hạn | Tải lại (2.4) |
| 409 | `MATCH_014` | Có thao tác khác đang cập nhật cùng lúc | Tải lại (2.4) rồi cho bấm lại |
| 409 | `PAIRING_005` | Thương lượng không còn `NEGOTIATING` | Tải lại (2.4); ẩn nút sửa |
| 409 | `PAIRING_006` | `revision` khác giá trị hiện tại | Hiện message; tải lại (2.4); người dùng nhập lại |
| 422 | `TEAM_015` | Đội mình đang tạm ngưng | Hiện message |
| 422 | `TEAM_016` | Đội mình đã giải tán | Hiện message |

---

### 2.7. Xác nhận — `POST /api/v1/pairings/{pairingId}/confirm`

**Dùng khi:** nút "Xác nhận" (không phải tin nhắn — BR-M07) sau khi hai bên thống nhất gói (UC-M07, AC-M04, AC-M05, Scenario 2, 3).
**Ai gọi được:** đội trưởng của đội `teamId` (một trong hai đội); đội `ACTIVE`; đội còn lại `ACTIVE`; bộ môn đang bật; địa điểm của phương án đang hoạt động.

Ghi xác nhận của phía `teamId` cho gói ở `revision` gửi lên. Phía còn lại đã xác nhận cùng `revision` → thương lượng và kèo `CONFIRMED` (§1.5). Hai bên bấm gần như cùng lúc: kết quả như bấm lần lượt — một bên nhận `NEGOTIATING`, bên kia nhận `CONFIRMED`.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` | string (UUID) | Có | Phía xác nhận | `"01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01"` |
| `revision` | integer | Có | ≥ 0; giá trị đang hiển thị (§1.4) | `3` |

```json
{ "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01", "revision": 3 }
```

**Response 200**

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `pairingId` | string (UUID) | |
| `status` | string | `NEGOTIATING` (chờ bên kia) \| `CONFIRMED` (đã ghép) |
| `revision` | integer | Không đổi |
| `authorConfirmedAt`, `interestedConfirmedAt` | string (thời gian) \| null | |
| `confirmedAt` | string (thời gian) \| null | Thời điểm hoàn tất; khác `null` khi `CONFIRMED` |
| `matchId` | string (UUID) | |
| `matchStatus` | string | `OPEN` \| `CONFIRMED` |

```json
{
  "data": {
    "pairingId": "01933d40-7e8f-7a90-8b1c-2d3e4f5a6b01",
    "status": "CONFIRMED",
    "revision": 3,
    "authorConfirmedAt": "2026-11-10T08:10:00Z",
    "interestedConfirmedAt": "2026-11-10T08:06:00Z",
    "confirmedAt": "2026-11-10T08:10:00Z",
    "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b02",
    "matchStatus": "CONFIRMED"
  }
}
```

`matchStatus = CONFIRMED` → tải lại kèo (MATCH-001 2.3) để hiện địa điểm, giờ, chi phí đã chốt.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu field, `revision` < 0 | Hiện lỗi |
| 403 | `PAIRING_002` | `teamId` không phải một trong hai đội | Gửi đúng `teamId` |
| 403 | `TEAM_002` | Không phải đội trưởng của `teamId` | Ẩn nút xác nhận |
| 404 | `PAIRING_001` | Không có thương lượng | Quay lại danh sách (2.3) |
| 409 | `MATCH_004` | Kèo đã hết hạn | Tải lại (2.4) |
| 409 | `MATCH_014` | Có thao tác khác đang cập nhật cùng lúc | Tải lại (2.4) rồi cho bấm lại |
| 409 | `PAIRING_005` | Thương lượng không còn `NEGOTIATING` — vd kèo vừa ghép với đội khác (`SUPERSEDED`), kèo bị huỷ | Hiện message; tải lại (2.4) |
| 409 | `PAIRING_006` | `revision` khác giá trị hiện tại — gói vừa bị sửa | Hiện message; tải lại (2.4) để người dùng xem gói mới rồi tự bấm lại |
| 422 | `MATCH_013` | Đội còn lại không còn hoạt động | Hiện message |
| 422 | `PAIRING_007` | Chưa chốt phương án | Yêu cầu chốt phương án (2.5) trước |
| 422 | `SPORT_002` | Bộ môn của kèo đang tắt | Hiện message |
| 422 | `TEAM_015` | Đội mình đang tạm ngưng | Hiện message |
| 422 | `TEAM_016` | Đội mình đã giải tán | Hiện message |
| 422 | `VENUE_002` | Địa điểm của phương án đã chốt bị ngừng hoạt động sau khi chốt | Hiện "Địa điểm đã ngừng hoạt động"; tải lại (2.4); cho chốt lại địa điểm khác (2.5) rồi hai bên xác nhận lại |

---

### 2.8. Gửi tin nhắn — `POST /api/v1/pairings/{pairingId}/messages`

**Dùng khi:** ô chat trong màn hình thương lượng (UC-M06, UC-N01, AC-N01).
**Ai gọi được:** đội trưởng của đội `teamId` (một trong hai đội; đội `INACTIVE` vẫn nhắn được); thương lượng `NEGOTIATING`.

Tin được lưu rồi mới đẩy cho cả hai bên qua frame `MESSAGE_CREATED` (§1.6). Không sửa / xoá tin. Tin được lưu tới 90 ngày sau khi kèo kết thúc (BR-N01).

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` | string (UUID) | Có | Phía gửi | `"01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02"` |
| `content` | string | Có | Sau khi bỏ khoảng trắng đầu/cuối: 1..1000 ký tự | `"18:00 sân Y được không?"` |

```json
{ "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02", "content": "18:00 sân Y được không?" }
```

**Response 201** — Tin nhắn (§4.4).

```json
{
  "data": {
    "messageId": "01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e01",
    "pairingId": "01933d40-7e8f-7a90-8b1c-2d3e4f5a6b01",
    "senderTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
    "senderUserId": "01932e00-5a6b-7c7d-8e8f-9a0b1c2d3e12",
    "senderName": "Nam",
    "senderAvatarUrl": null,
    "content": "18:00 sân Y được không?",
    "isRead": false,
    "createdAt": "2026-11-10T08:00:00Z"
  }
}
```

FE hiển thị tin từ response; frame `MESSAGE_CREATED` của chính tin này (cùng `messageId`) bỏ qua để không hiện trùng. Người gửi luôn là người gọi API (đội trưởng hiện tại của `teamId`).

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `content` rỗng / > 1000 ký tự; thiếu `teamId` | Hiện lỗi cạnh ô nhập |
| 403 | `PAIRING_002` | `teamId` không phải một trong hai đội | Gửi đúng `teamId` |
| 403 | `TEAM_002` | Không phải đội trưởng của `teamId` | Khoá ô chat |
| 404 | `PAIRING_001` | Không có thương lượng | Quay lại danh sách (2.3) |
| 409 | `MATCH_004` | Kèo đã hết hạn | Tải lại (2.4); khoá ô chat |
| 409 | `MATCH_014` | Có thao tác khác đang cập nhật cùng lúc | Cho gửi lại |
| 409 | `PAIRING_005` | Thương lượng không còn `NEGOTIATING` | Tải lại (2.4); khoá ô chat, vẫn cho đọc |
| 422 | `TEAM_016` | Đội mình đã giải tán | Hiện message |

---

### 2.9. Lịch sử chat — `GET /api/v1/pairings/{pairingId}/messages`

**Dùng khi:** mở màn hình chat (trang mới nhất), cuộn lên xem tin cũ (`before`), bù tin sau khi kết nối realtime lại (`after`).
**Ai gọi được:** đội trưởng của một trong hai đội; mọi trạng thái thương lượng.

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `before` | string (UUID) | Không | `messageId` trong thương lượng này; lấy tin **cũ hơn** tin đó | `01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e01` |
| `after` | string (UUID) | Không | `messageId` trong thương lượng này; lấy tin **mới hơn** tin đó | `01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e01` |
| `limit` | integer | Không | 1..100, mặc định 50 | `50` |

Không gửi đồng thời `before` và `after` (`400 VALIDATION_ERROR`, `field` = `after`).

| Gửi | Trả | Thứ tự `items` | `hasMore = true` khi |
|---|---|---|---|
| Không con trỏ | `limit` tin mới nhất | Mới nhất trước | Còn tin cũ hơn |
| `before` | `limit` tin ngay trước tin `before` | Mới nhất trước | Còn tin cũ hơn |
| `after` | `limit` tin ngay sau tin `after` | **Cũ nhất trước** | Còn tin mới hơn → gọi tiếp với `after` = `messageId` cuối |

Thứ tự theo thời điểm gửi `createdAt`; hai tin cùng thời điểm xếp theo `messageId`. Bản thân tin mốc (`before` / `after`) không có trong kết quả.

**Response 200** — `items`: mảng Tin nhắn (§4.4).

```json
{
  "data": {
    "items": [
      { "messageId": "01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e03", "pairingId": "01933d40-7e8f-7a90-8b1c-2d3e4f5a6b01",
        "senderTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02", "senderUserId": "01932e00-5a6b-7c7d-8e8f-9a0b1c2d3e13",
        "senderName": "Lan", "senderAvatarUrl": "https://res.cloudinary.com/demo/image/upload/lan.jpg",
        "content": "Mình là đội trưởng mới của Beta, giữ 18:00 nhé", "isRead": false, "createdAt": "2026-11-10T09:00:00Z" },
      { "messageId": "01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e02", "pairingId": "01933d40-7e8f-7a90-8b1c-2d3e4f5a6b01",
        "senderTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01", "senderUserId": "01932e00-5a6b-7c7d-8e8f-9a0b1c2d3e11",
        "senderName": "Người dùng đã xóa", "senderAvatarUrl": null,
        "content": "OK", "isRead": true, "createdAt": "2026-11-10T08:01:00Z" },
      { "messageId": "01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e01", "pairingId": "01933d40-7e8f-7a90-8b1c-2d3e4f5a6b01",
        "senderTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02", "senderUserId": "01932e00-5a6b-7c7d-8e8f-9a0b1c2d3e12",
        "senderName": "Nam", "senderAvatarUrl": null,
        "content": "18:00 sân Y được không?", "isRead": true, "createdAt": "2026-11-10T08:00:00Z" }
    ],
    "hasMore": false
  }
}
```

Ví dụ: đội Beta, Nam nhắn rồi chuyển quyền đội trưởng cho Lan → tin cũ vẫn là "Team Beta · Nam", tin mới "Team Beta · Lan"; người gửi tin "OK" của Team Alpha đã xoá tài khoản.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Gửi cả `before` và `after`; `limit` ngoài 1..100; con trỏ không phải UUID | Sửa tham số |
| 403 | `PAIRING_002` | Không phải đội trưởng của đội nào trong thương lượng | Quay lại |
| 404 | `PAIRING_001` | Không có thương lượng | Quay lại danh sách (2.3) |
| 422 | `CHAT_001` | `before` / `after` không phải tin của thương lượng này | Tải lại trang mới nhất (bỏ con trỏ) |

---

### 2.10. Đánh dấu đã đọc — `POST /api/v1/pairings/{pairingId}/messages/read`

**Dùng khi:** người dùng mở / đang xem màn hình chat.
**Ai gọi được:** đội trưởng của đội `teamId` (một trong hai đội); mọi trạng thái thương lượng.

Đánh dấu **mọi** tin của **bên kia** (gửi bởi đội khác `teamId`) trong thương lượng là đã đọc (`isRead = true`). Không có frame realtime cho việc đánh dấu đọc; bên gửi thấy `isRead` mới khi tải lại 2.9.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` | string (UUID) | Có | Phía đọc | `"01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01"` |

```json
{ "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01" }
```

**Response 204** — không body. Gọi lặp → `204`, không đổi gì.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 403 | `PAIRING_002` | `teamId` không phải một trong hai đội | Gửi đúng `teamId` |
| 403 | `TEAM_002` | Không phải đội trưởng của `teamId` | Bỏ qua (không chặn người dùng) |
| 404 | `PAIRING_001` | Không có thương lượng | Quay lại danh sách (2.3) |
| 422 | `TEAM_016` | Đội mình đã giải tán | Bỏ qua |

## 3. Hành động theo trạng thái

Cột = **đội trưởng** của đội đó. Đội khác (không trong thương lượng) không thấy thương lượng (`PAIRING_002`). Trạng thái là trạng thái hiển thị (§1.3).

| Trạng thái thương lượng | Đội tạo kèo | Đội nhận kèo |
|---|---|---|
| `NEGOTIATING` | Xem (2.4) · chat (2.8–2.10) · chốt phương án (2.5) · sửa chi phí (2.6) · xác nhận (2.7, khi đã có phương án) | Như đội tạo kèo |
| `CONFIRMED` | Xem (2.4), đọc chat (2.9, 2.10). Thao tác trên kèo theo MATCH-001 (huỷ 2.5, …) | Như đội tạo kèo |
| `SUPERSEDED`, `EXPIRED` | Xem (2.4), đọc chat (2.9, 2.10) | Như đội tạo kèo |

Gọi thao tác ghi ở trạng thái không cho phép:
- 2.5, 2.6, 2.7, 2.8 khi `CONFIRMED` / `SUPERSEDED` / `EXPIRED` (đã chuyển hẳn) → `409 PAIRING_005`; khi đang hiển thị `EXPIRED` vì kèo vừa quá `expiresAt` → `409 MATCH_004`.
- 2.7 khi chưa có phương án → `422 PAIRING_007`.
- 2.5 chọn địa điểm đã ngừng hoạt động, 2.7 khi `proposal.venueStatus = INACTIVE` → `422 VENUE_002` (§1.2).

Nhận kèo (2.1) theo trạng thái kèo: chỉ kèo `NEED_VENUE` đang `OPEN`, chưa quá `expiresAt`; `CONFIRMED` → `MATCH_003`; quá hạn → `MATCH_004`; huỷ / lưu trữ / hoàn thành → `MATCH_005`.

**Gợi ý hiển thị nút:** ẩn "Xác nhận" khi `proposal = null` hoặc `proposal.venueStatus = INACTIVE` (hiện "Địa điểm đã ngừng hoạt động — chốt lại địa điểm khác"); hiện "Đã xác nhận, chờ đội kia" khi phía mình có `…ConfirmedAt` ≠ `null`; khoá ô chat khi `status ≠ NEGOTIATING` hoặc giờ hiện tại ≥ `matchExpiresAt` (2.1–2.4).

## 4. Kiểu dữ liệu dùng chung

### 4.1. Enum

| Kiểu / enum | Giá trị | Ý nghĩa |
|---|---|---|
| `status` (thương lượng) | `NEGOTIATING` \| `CONFIRMED` \| `SUPERSEDED` \| `EXPIRED` | §1.3 |
| `role` (query 2.3) | `AUTHOR` \| `INTERESTED` \| `ALL` | Thương lượng trên kèo của đội / đội nhận kèo / cả hai |
| `costType`, `costSplit` | Như MATCH-001 `API_Spec.md` §4.1 | |
| `venueStatus` | `ACTIVE` \| `INACTIVE` | Địa điểm đang hoạt động / đã ngừng (VENUE-001) |
| `type` (frame realtime) | `MESSAGE_CREATED` \| `PAIRING_UPDATED` | §1.6 |
| `change` (frame `PAIRING_UPDATED`) | `PROPOSAL` \| `COST` \| `CONFIRMATION` | Chốt phương án / lưu chi phí / xác nhận (một bên hoặc hoàn tất) |

### 4.2. Thương lượng (chi tiết) — response 2.1, 2.4

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `pairingId` | string (UUID) | |
| `matchId` | string (UUID) | Kèo luồng B |
| `status` | string | Trạng thái hiển thị (§1.3) |
| `revision` | integer | Phiên bản gói (§1.4) |
| `matchExpiresAt` | string (thời gian) | Hạn bài đăng của kèo; từ thời điểm này thương lượng `NEGOTIATING` hiển thị `EXPIRED` (§1.3) — dùng để khoá ô chat / nút |
| `authorTeam`, `interestedTeam` | object Đội | Đội tạo kèo / đội nhận kèo: `teamId`, `teamName`, `avatarUrl` (string \| null), `reliabilityScore` (0..100), `captainName` (string \| null) |
| `authorTeam.captainName`, `interestedTeam.captainName` | string \| null | Tên đội trưởng **hiện tại** của đội (đổi khi đội chuyển quyền đội trưởng). `"Người dùng đã xóa"` nếu tài khoản đó đã xoá; `null` khi đội không còn đội trưởng (đã giải tán) |
| `proposal` | object \| null | Phương án đã chốt; `null` khi chưa chốt |
| `proposal.venueId`, `proposal.venueName`, `proposal.address`, `proposal.venueStatus` | string | Địa điểm (thông tin mới nhất) |
| `proposal.timeOptionId` | string (UUID) | Khung giờ trong `timeOptions` của kèo |
| `proposal.startsAt`, `proposal.endsAt` | string (thời gian) | |
| `authorConfirmedAt` | string (thời gian) \| null | Đội tạo kèo đã xác nhận gói hiện tại |
| `interestedConfirmedAt` | string (thời gian) \| null | Đội nhận kèo đã xác nhận gói hiện tại |
| `confirmedAt` | string (thời gian) \| null | Lúc thương lượng `CONFIRMED` |
| `costAgreement` | object | `costType`, `costSplit` (enum \| null), `venueAmount`, `drinksAmount`, `depositAmount` (integer \| null), `depositHolder` (string \| null), `customItems` (mảng `{ itemId, itemName, amount }`, đúng thứ tự nhập). Chưa nhập → mọi field `null`, `customItems: []` |
| `createdAt`, `updatedAt` | string (thời gian) | `updatedAt` đổi khi gói / xác nhận / trạng thái đổi |

### 4.3. Thương lượng (rút gọn) — phần tử 2.2, 2.3

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `pairingId`, `matchId` | string (UUID) | |
| `status` | string | §1.3 |
| `revision` | integer | |
| `authorTeamId`, `authorTeamName` | string | Đội tạo kèo |
| `interestedTeamId`, `interestedTeamName` | string | Đội nhận kèo |
| `interestedTeamReliabilityScore` | integer | 0..100 |
| `proposalVenueId`, `proposalTimeOptionId` | string (UUID) \| null | Phương án đã chốt |
| `authorConfirmedAt`, `interestedConfirmedAt`, `confirmedAt` | string (thời gian) \| null | §4.2 |
| `matchStartsAt` | string (thời gian) | `startsAt` của kèo (khung giờ sớm nhất khi chưa ghép; giờ đã chốt khi đã ghép) |
| `matchExpiresAt` | string (thời gian) | Hạn bài đăng của kèo |
| `createdAt`, `updatedAt` | string (thời gian) | |

### 4.4. Tin nhắn — response 2.8, 2.9, frame `MESSAGE_CREATED`

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `messageId` | string (UUID) | |
| `pairingId` | string (UUID) | |
| `senderTeamId` | string (UUID) | Phía gửi: bằng `authorTeam.teamId` hoặc `interestedTeam.teamId` của 2.4 — FE lấy tên đội gửi từ đó |
| `senderUserId` | string (UUID) | Người **thực sự** đã gửi tin (đội trưởng của `senderTeamId` tại lúc gửi); không đổi khi đội chuyển quyền đội trưởng sau đó |
| `senderName` | string | Tên hiển thị của người gửi; `"Người dùng đã xóa"` nếu tài khoản đó đã xoá |
| `senderAvatarUrl` | string \| null | Ảnh đại diện người gửi; `null` khi chưa có ảnh hoặc tài khoản đã xoá |
| `content` | string | 1..1000 ký tự |
| `isRead` | boolean | Bên nhận đã đánh dấu đọc (2.10) |
| `createdAt` | string (thời gian) | |

Hiển thị mỗi tin: `<tên đội gửi> · <senderName>` (vd "Team Beta · Nam"). Tên đội không có trong tin vì chỉ có hai giá trị, đều nằm trong 2.4 mà màn hình chat luôn tải cho phần đầu phòng; FE dùng tên đội hiện tại từ 2.4.

## 5. Mã lỗi

Mã mới của MATCH-002:

| Mã | HTTP | Message | Endpoint |
|---|---|---|---|
| `MATCH_016` | 422 | "Thao tác không áp dụng cho loại kèo này" | 2.1, 2.2 (và MATCH-001 2.4, 2.6, 2.7) |
| `PAIRING_001` | 404 | "Không tìm thấy thương lượng ghép kèo" | 2.4–2.10, kênh realtime |
| `PAIRING_002` | 403 | "Đội không tham gia thương lượng này" | 2.4–2.10, kênh realtime |
| `PAIRING_003` | 409 | "Đội đã nhận kèo này" | 2.1 |
| `PAIRING_004` | 422 | "Không thể nhận kèo của đội mình" | 2.1 |
| `PAIRING_005` | 409 | "Thương lượng đã kết thúc" | 2.5–2.8 |
| `PAIRING_006` | 409 | "Phương án đã thay đổi, vui lòng tải lại" | 2.5–2.7 |
| `PAIRING_007` | 422 | "Chưa chốt địa điểm và giờ" | 2.7 |
| `PAIRING_008` | 422 | "Địa điểm hoặc giờ không thuộc lựa chọn của kèo" | 2.5 |
| `CHAT_001` | 422 | "Tin nhắn mốc không thuộc cuộc trò chuyện này" | 2.9 |

Mã dùng lại (message theo `API_Spec.md` của mã gốc):

| Mã | HTTP | Nguồn | Endpoint |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | `api-convention.md` §11 | Mọi endpoint |
| `AUTH_001` | 401 | USER-001 | Mọi endpoint, kênh realtime |
| `TEAM_001` | 404 | USER-001 | 2.1, 2.3 |
| `TEAM_002` | 403 | USER-001 | 2.1–2.3, 2.5–2.8, 2.10 |
| `TEAM_015` | 422 | USER-001 | 2.1, 2.5–2.7 |
| `TEAM_016` | 422 | USER-002 | 2.1–2.3, 2.5–2.8, 2.10 |
| `MATCH_001` | 404 | MATCH-001 | 2.1, 2.2 |
| `MATCH_003` | 409 | MATCH-001 | 2.1 |
| `MATCH_004` | 409 | MATCH-001 | 2.1, 2.5–2.8 |
| `MATCH_005` | 409 | MATCH-001 | 2.1 |
| `MATCH_013` | 422 | MATCH-001 | 2.1, 2.7 |
| `MATCH_014` | 409 | MATCH-001 | 2.1, 2.5–2.8 |
| `SPORT_002` | 422 | MATCH-001 | 2.1, 2.7 |
| `VENUE_002` | 422 | VENUE-001 ("Địa điểm đã ngừng hoạt động") | 2.5, 2.7 |

Request vi phạm nhiều điều kiện nhận lỗi đầu tiên theo thứ tự: `AUTH_*` → `VALIDATION_ERROR` → không tìm thấy (`*_001`) → quyền / đội (`PAIRING_002`, `TEAM_*`) → trạng thái kèo / thương lượng → `revision` → điều kiện còn lại.

## 6. Danh sách [cần xác nhận]

1. Đường dẫn và hỗ trợ SockJS của kênh realtime (`/ws`): phụ thuộc phiên bản framework BE — sẽ chốt khi cài đặt; nếu không còn SockJS, FE kết nối WebSocket thuần tới `/ws` và dùng 2.9 (`after`) khi không mở được WebSocket (ADR-0014).
2. Không có frame khi thương lượng bị thay thế / hết hạn / kèo bị huỷ (§1.6). Nếu FE cần cập nhật tức thời các trường hợp này → bổ sung frame ở bản sau.
