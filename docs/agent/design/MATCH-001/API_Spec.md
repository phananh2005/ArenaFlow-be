# API Specification — Kèo luồng A (đã có sân) & vòng đời kèo

| Mục | Giá trị |
|---|---|
| Mã chức năng | `MATCH-001` |
| Phạm vi tầng | Backend (Spring Boot REST API). Frontend: ngoài phạm vi repo này |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · PostgreSQL (Neon) · Flyway |
| Quy ước áp dụng | `docs/agent/api-convention.md`, `docs/agent/security-backend.md`; quy ước chung USER-001 `API_Spec.md` §1 (UUID v7, envelope, enum UPPERCASE, phân trang `page`/`pageSize` 1..100, `VALIDATION_ERROR`, che tên user đã xoá); mã lỗi `TEAM_*`, `VENUE_*` dùng lại từ USER-001/002, VENUE-001 |
| Ràng buộc dữ liệu | V1 (`match`, `match_request`, `cost_custom_item`, `sport`) + `V6__match_flow_a_lifecycle.sql` (§5) |
| Spec nghiệp vụ | `docs/agent/business-spec/SYSTEM-001/` (v1.3) — UC-M01, M03, M04, M08, M09, UC-C01, C02; BR-M01, M02, M04–M06, BR-M09..M18, BR-C01..C04; Business_Rules §2.1, §2.2, §3.1, §4 (BC-M01..M07), §5.2, §5.4, §7.3; AC-M01, M03, M06..M12, AC-C01; Scenario 1, 4, 5, 6, 10, 11 |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-09 · v1.0 (baseline) |

> Tài liệu này được publish cho FE (ADR-0010) và phải đọc được độc lập. Các mục ghi "(nội bộ BE)" trỏ tới `Component_Class_Design.md` / `Processing_Flow.md` — không publish, FE không cần.

## 1. Tổng quan

Đội trưởng tạo kèo **luồng A** (đã tự đặt sân) cho một bộ môn đang bật; đội khác gửi yêu cầu ghép (Match Request); tác giả chấp nhận đúng một yêu cầu → kèo `CONFIRMED`, các yêu cầu còn chờ → `SUPERSEDED`. Từ chối / rút yêu cầu. Huỷ kèo (`OPEN` không ảnh hưởng uy tín, phải từ chối hết yêu cầu chờ trước; `CONFIRMED` trừ Reliability Score của **đội huỷ**). Hệ thống tự chuyển kèo hết hạn bài đăng → `EXPIRED`, kèo đã ghép tới giờ kết thúc dự kiến → `ARCHIVED`. Thoả thuận chi phí + khoản tự nhập (chỉ lưu để hiển thị). Admin bật/tắt bộ môn.

**Ngoài phạm vi:** luồng B (pairing, chat, tiền cọc — MATCH-002) · đánh giá/report, `ARCHIVED → COMPLETED`, Completion Rate (REPUTATION-001) · mercenary · gửi notification thật (NOTIFICATION-001 nghe sự kiện nội bộ do MATCH-001 phát) · sửa nội dung kèo sau khi tạo (spec không có UC sửa kèo `OPEN`; `CONFIRMED` cấm sửa — BR-M04; muốn đổi thì huỷ và tạo lại — BR-M02) · `Idempotency-Key` (`api-convention.md` §13, hoãn như USER-001/002) · frontend.

**Quy ước riêng:**
- Mọi endpoint cần access token; không endpoint public mới. Token sai → `401 AUTH_001`; `/api/v1/admin/**` không phải admin → `403 AUTH_004`.
- **Hành động thay mặt đội:** chỉ **đội trưởng ACTIVE** của đội. Đội xác định bởi `teamId` trong body (tạo kèo, gửi yêu cầu, huỷ kèo) hoặc suy ra từ tài nguyên (chấp nhận/từ chối: đội tác giả; rút: đội gửi yêu cầu). Không phải đội trưởng → `403 TEAM_002`.
- **Trạng thái đội:** thao tác tạo/mở rộng (tạo kèo, gửi yêu cầu, chấp nhận, sửa chi phí) cần đội `ACTIVE` (`TEAM_016` giải tán, `TEAM_015` tạm ngưng). Thao tác rời bỏ (huỷ kèo, từ chối, rút) cho phép đội `INACTIVE` (user chốt 2026-11-09).
- Enum (UPPERCASE): `type` `HAS_VENUE` (luồng A; `NEED_VENUE` thuộc MATCH-002) · `status` kèo `OPEN|CONFIRMED|ARCHIVED|COMPLETED|EXPIRED|CANCELLED` · `status` yêu cầu `PENDING|ACCEPTED|REJECTED|WITHDRAWN|SUPERSEDED|EXPIRED` · `format` `FIVE_A_SIDE|SEVEN_A_SIDE|ELEVEN_A_SIDE` (cùng giá trị `venueType` của VENUE-001) · `costType` `VENUE_ONLY|DRINKS_ONLY|BOTH` · `costSplit` `FIFTY_FIFTY|SEVENTY_THIRTY_HOME|SEVENTY_THIRTY_LOSER|LOSER_PAYS_DRINKS` · `skillLevels` (nhãn tiếng Việt, không phải enum UPPERCASE; cùng danh sách trình độ đội): `Chill`, `Nhiệt tình`, `Máu lửa`, `Vui chơi`, `Rèn luyện`, `Thi đấu`, `Mới bắt đầu`, `Đã có kinh nghiệm`, `Lão làng`.
- **Trạng thái hiệu lực (lazy check, UC-M09):** kèo `OPEN` có `expiresAt ≤ now` được **trả về** là `EXPIRED` ở mọi API đọc (và yêu cầu `PENDING` của nó là `EXPIRED`), kể cả khi DB chưa cập nhật. Thao tác ghi gặp kèo như vậy sẽ chuyển `EXPIRED` thật rồi trả `409 MATCH_004`. Job hệ thống cập nhật DB mỗi 5 phút (user chốt 2026-11-09).
- **Quy tắc trừ điểm (nguồn duy nhất trong tài liệu này):** huỷ / rút sau khi ghép bị trừ Reliability Score của đội thực hiện (Data_Model_Conceptual §1: uy tín đội gắn Match). Mức trừ theo khoảng cách từ lúc huỷ tới `startsAt` (Business_Rules §7.3): Δ ≥ 24h → `-2`; 4h ≤ Δ < 24h → `-5`; Δ < 4h → `-10` (user chốt 2026-11-09). Điểm chặn [0, 100]. Trận đã bắt đầu → không huỷ/rút được (user chốt 2026-11-09).
- **Huỷ / rút theo trạng thái người dùng đang thấy:** 2.5 và 2.10 bắt buộc gửi `expectedStatus` (trạng thái kèo / yêu cầu trên màn hình). Server so ở **mọi** lần xử lý; khác trạng thái hiện tại → `409 MATCH_015`, không huỷ, không trừ điểm (BR-M13; user chốt 2026-11-09).
- **Kèo hết hạn:** kèo `EXPIRED` trong DB hoặc `OPEN` quá `expiresAt` → mọi endpoint ghi (2.4–2.6, 2.8–2.10) trả cùng `409 MATCH_004`.
- Thời gian ISO-8601 UTC (`OffsetDateTime`), lưu UTC (BC-M05). Số tiền VND, kiểu số nguyên (BR-C01).

## 2. Endpoints

| # | Method & Path | Quyền | Thành công | Tiêu chí `api-convention.md` §5 |
|---|---|---|---|---|
| 2.1 | `POST /api/v1/matches` | Đội trưởng đội tác giả | 201 | B2 |
| 2.2 | `GET /api/v1/matches` | User | 200 | B1 |
| 2.3 | `GET /api/v1/matches/{matchId}` | User | 200 | B1 |
| 2.4 | `PUT /api/v1/matches/{matchId}/cost-agreement` | Đội trưởng đội tác giả | 200 | B4 (+ B3: `itemId`, `updatedAt`) |
| 2.5 | `POST /api/v1/matches/{matchId}/cancel` | Đội trưởng đội tác giả hoặc đội đã ghép | 200 | B3 (`cancelledAt`, `reliabilityPenalty`), B4 |
| 2.6 | `POST /api/v1/matches/{matchId}/requests` | Đội trưởng đội gửi | 201 | B2 |
| 2.7 | `GET /api/v1/matches/{matchId}/requests` | Đội trưởng đội tác giả | 200 | B1 |
| 2.8 | `POST /api/v1/match-requests/{requestId}/accept` | Đội trưởng đội tác giả | 200 | B3 (`respondedAt`, đội đối thủ) |
| 2.9 | `POST /api/v1/match-requests/{requestId}/reject` | Đội trưởng đội tác giả | 204 | N1 |
| 2.10 | `POST /api/v1/match-requests/{requestId}/withdraw` | Đội trưởng đội gửi | 200 | B3 (`withdrawnAt`, `reliabilityPenalty`, trạng thái kèo) |
| 2.11 | `GET /api/v1/teams/{teamId}/matches` | User | 200 | B1 |
| 2.12 | `GET /api/v1/teams/{teamId}/match-requests` | Đội trưởng của đội | 200 | B1 |
| 2.13 | `GET /api/v1/sports` | User | 200 | B1 |
| 2.14 | `GET /api/v1/admin/sports` | Admin | 200 | B1 |
| 2.15 | `PATCH /api/v1/admin/sports/{sportId}` | Admin | 200 | B4 |

Ghi chú hợp đồng: `201` không kèm `Location`. 2.5, 2.8–2.10 là action sub-resource (`api-convention.md` §2.1), dùng `POST` theo tiền lệ `/invitations/{id}/accept`, `/teams/{id}/disband`. 2.6 tạo tài nguyên con dưới kèo, thao tác trên từng yêu cầu dùng `/match-requests/{id}` (tiền lệ `/teams/{id}/invitations` + `/invitations/{id}/…`). 2.4 `PUT` thay toàn bộ thoả thuận (idempotent). 2.10 luôn trả `200` có body (kể cả rút khi `PENDING`) để một endpoint có một hình dạng response. 2.15 đổi một field (`isActive`) → `PATCH` thường, không phải action.

### 2.0. Thứ tự guard (nguồn duy nhất)

Sau JWT (401/403 ở filter) và Bean Validation (400). Tồn tại → đội (trạng thái rồi quyền, như USER-002 §2.1a) → trạng thái kèo → trạng thái yêu cầu. Quyền đứng trước trạng thái kèo để người không có quyền không kích hoạt chuyển `EXPIRED`. `MATCH_014` (hết lượt thử do xung đột đồng thời) có thể xảy ra ở 2.4–2.6, 2.8–2.10.

| Endpoint | Thứ tự guard |
|---|---|
| 2.1 | `TEAM_001` → `TEAM_016` → `TEAM_015` → `TEAM_002` → `SPORT_001` → `SPORT_002` → `VENUE_001` → `VENUE_002` |
| 2.3 | `MATCH_001` |
| 2.4 | `MATCH_001` → `TEAM_016` → `TEAM_015` → `TEAM_002` (đội tác giả) → `MATCH_004` → `MATCH_003` → `MATCH_005` |
| 2.5 | `MATCH_001` → `TEAM_001` → `TEAM_016` → `TEAM_002` (đội `teamId`) → `MATCH_009` → `MATCH_004` → `MATCH_005` (`CANCELLED`/`ARCHIVED`/`COMPLETED`) → `MATCH_015` (≠ `expectedStatus`) → `OPEN`: `MATCH_010` · `CONFIRMED`: `MATCH_011` → `MATCH_012` |
| 2.6 | `MATCH_001` → `TEAM_001` → `TEAM_016` → `TEAM_015` → `TEAM_002` (đội `teamId`) → `MATCH_007` → `MATCH_004` → `MATCH_003` → `MATCH_005` → `MATCH_013` (đội tác giả không `ACTIVE`) → `SPORT_002` → `MATCH_006` (khi INSERT) |
| 2.7 | `MATCH_001` → `TEAM_002` (đội tác giả; đội giải tán → `TEAM_016`) |
| 2.8 | `MATCH_002` → `TEAM_016` → `TEAM_015` → `TEAM_002` (đội tác giả) → `MATCH_004` → `MATCH_003` → `MATCH_005` → `MATCH_008` → `MATCH_013` (đội gửi không `ACTIVE`) → `SPORT_002` |
| 2.9 | `MATCH_002` → `TEAM_016` → `TEAM_002` (đội tác giả) → `MATCH_004` (kể cả kèo đã `EXPIRED` trong DB) → `MATCH_008` |
| 2.10 | `MATCH_002` → `TEAM_016` → `TEAM_002` (đội gửi) → `MATCH_004` → `MATCH_008` (yêu cầu không ở `PENDING`/`ACCEPTED`) → `MATCH_015` (≠ `expectedStatus`) → `PENDING`: `MATCH_015` (khi ghi — yêu cầu đổi trạng thái sau lúc đọc) · `ACCEPTED`: `MATCH_005` (kèo không `CONFIRMED`) → `MATCH_011` → `MATCH_012` |
| 2.11 | `TEAM_001` |
| 2.12 | `TEAM_001` → `TEAM_002` (đội giải tán → `TEAM_016`) |
| 2.15 | `SPORT_001` |

`TEAM_001` của đội tác giả / đội gửi suy ra từ tài nguyên không xảy ra (FK). Đội đã giải tán không còn đội trưởng ACTIVE → trả `TEAM_016` (không phải `TEAM_002`) ở mọi endpoint, gồm cả đọc (2.7, 2.12) — như USER-002 §2.1a.

---

### 2.1. POST /api/v1/matches

UC-M01, AC-M01. Tạo kèo `HAS_VENUE`, `OPEN`. Ghi nhận địa điểm "được chọn" (VENUE-001 `lastSelectedAt`) cùng transaction.

**Request:** `CreateMatchRequest` (§3.1).
```json
{ "teamId": "uuid", "sportId": "uuid", "venueId": "uuid", "format": "SEVEN_A_SIDE",
  "startsAt": "2026-11-12T11:00:00Z", "endsAt": "2026-11-12T13:00:00Z",
  "expiresAt": "2026-11-12T05:00:00Z", "skillLevels": ["Nhiệt tình"],
  "ageNote": "U23", "matchNature": "Giao hữu" }
```
**Response 201:** `MatchResponse` (§3.9), `costAgreement` rỗng (§3.9).
**Lỗi:** `400 VALIDATION_ERROR` (gồm quy tắc thời gian §3.1) · `403 TEAM_002` · `404 TEAM_001` · `404 SPORT_001` · `404 VENUE_001` · `422 TEAM_015` · `422 TEAM_016` · `422 SPORT_002` · `422 VENUE_002`.

### 2.2. GET /api/v1/matches

Tìm kèo (UC-M03 bước 1, AC-M01 "hiển thị trong danh sách tìm kèo"). Chỉ trả kèo **đang nhận yêu cầu**: `OPEN`, `expiresAt > now`, đội tác giả `ACTIVE`, bộ môn đang bật (user chốt 2026-11-09). Query: `SearchMatchRequest` (§3.7).
Sort: có toạ độ → `distanceKm` ↑, `startsAt` ↑, `matchId` ↑; không → `startsAt` ↑, `matchId` ↑.
**Response 200:** `MatchSummaryResponse[]` (§3.10) + `pagination`. **Lỗi:** `400 VALIDATION_ERROR` (gồm chỉ gửi một trong `lat`/`lng`, `radiusKm` thiếu toạ độ, `startsAtFrom > startsAtTo`).

### 2.3. GET /api/v1/matches/{matchId}

Chi tiết mọi trạng thái, gồm thoả thuận chi phí (hiển thị trên bảng tin kèo — UC-C01 bước 7) và thông tin địa điểm **mới nhất** (BR-V10). `status` là trạng thái hiệu lực (§1).
**Response 200:** `MatchResponse`. **Lỗi:** `400 VALIDATION_ERROR` · `404 MATCH_001`.

### 2.4. PUT /api/v1/matches/{matchId}/cost-agreement

UC-C01, UC-C02, AC-C01. Thay **toàn bộ** thoả thuận chi phí và danh sách khoản tự nhập; chỉ khi kèo `OPEN` (trước khi ghép) (user chốt 2026-11-09). Gửi mọi field `null` + `customItems: []` = xoá thoả thuận. ArenaFlow không thu/giữ tiền (BC-C01). Tiền cọc thuộc MATCH-002.

**Request:** `UpdateCostAgreementRequest` (§3.6).
```json
{ "costType": "BOTH", "costSplit": "FIFTY_FIFTY", "venueAmount": 600000, "drinksAmount": 100000,
  "customItems": [ { "itemName": "Tiền trọng tài", "amount": 200000 } ] }
```
**Response 200:** `CostAgreementResponse` (§3.12).
**Lỗi:** `400 VALIDATION_ERROR` · `403 TEAM_002` · `404 MATCH_001` · `409 MATCH_003` · `409 MATCH_004` · `409 MATCH_005` · `409 MATCH_014` · `422 TEAM_015` · `422 TEAM_016`.

### 2.5. POST /api/v1/matches/{matchId}/cancel

UC-M08, AC-M06, Scenario 4.
- Kèo `OPEN`: chỉ đội tác giả; còn yêu cầu `PENDING` → `422 MATCH_010` (BR-M05, phải từ chối hết bằng 2.9); không trừ điểm; `reason` tuỳ chọn.
- Kèo `CONFIRMED`: đội tác giả hoặc đội đã ghép; `reason` bắt buộc (`422 MATCH_011`); trừ điểm đội huỷ (§1); trận đã bắt đầu → `422 MATCH_012`. Đội đã ghép huỷ ⇒ yêu cầu `ACCEPTED` của đội đó chuyển `WITHDRAWN` (cùng kết quả 2.10 rút yêu cầu đã chấp nhận; trừ điểm một lần) (user chốt 2026-11-09).
- `expectedStatus` (bắt buộc) = trạng thái kèo client đang hiển thị; khác trạng thái hiện tại (vd màn hình cũ còn `OPEN` nhưng kèo đã được ghép) → `409 MATCH_015`, không trừ điểm, client tải lại kèo (§1).

**Request:** `CancelMatchRequest` (§3.4) `{ "teamId": "uuid", "expectedStatus": "CONFIRMED", "reason": "Thiếu người" }`.
**Response 200:** `MatchCancellationResponse` (§3.14).
**Lỗi:** `400 VALIDATION_ERROR` · `403 TEAM_002` · `403 MATCH_009` · `404 MATCH_001` · `404 TEAM_001` · `409 MATCH_004` · `409 MATCH_005` · `409 MATCH_014` · `409 MATCH_015` · `422 MATCH_010` · `422 MATCH_011` · `422 MATCH_012` · `422 TEAM_016`.

### 2.6. POST /api/v1/matches/{matchId}/requests

UC-M03. Tạo yêu cầu `PENDING`. Mỗi đội tối đa một yêu cầu đang hiệu lực cho một kèo; sau khi rút (`WITHDRAWN`) hoặc hết hạn được gửi lại, sau khi bị từ chối / bị thay thế thì không (`409 MATCH_006`).
**Request:** `SendMatchRequestRequest` (§3.2) `{ "teamId": "uuid" }`.
**Response 201:** `MatchRequestResponse` (§3.11).
**Lỗi:** `400 VALIDATION_ERROR` · `403 TEAM_002` · `404 MATCH_001` · `404 TEAM_001` · `409 MATCH_003` ("Kèo đã được ghép" — UC-M03 3a) · `409 MATCH_004` · `409 MATCH_005` · `409 MATCH_006` · `409 MATCH_014` · `422 MATCH_007` · `422 MATCH_013` · `422 SPORT_002` · `422 TEAM_015` · `422 TEAM_016`.

### 2.7. GET /api/v1/matches/{matchId}/requests

UC-M04 bước 1. Query: `status` ∈ `PENDING|ALL` (mặc định `PENDING`; trạng thái hiệu lực §1) · `page`, `pageSize`. Sort `requestedAt` ↑, `requestId` ↑.
**Response 200:** `MatchRequestResponse[]` + `pagination`. **Lỗi:** `400 VALIDATION_ERROR` · `403 TEAM_002` · `404 MATCH_001` · `422 TEAM_016`.

### 2.8. POST /api/v1/match-requests/{requestId}/accept

UC-M04, AC-M03, Scenario 6. Yêu cầu → `ACCEPTED`; kèo → `CONFIRMED` (đội đối thủ = đội gửi); mọi yêu cầu `PENDING` khác → `SUPERSEDED`. Tối đa một yêu cầu `ACCEPTED` mỗi kèo (ràng buộc DB, BR-M06). Kèo đã được ghép bởi yêu cầu khác (kể cả chấp nhận đồng thời) → `409 MATCH_003`. Không body.
**Response 200:** `AcceptMatchRequestResponse` (§3.13).
**Lỗi:** `403 TEAM_002` · `404 MATCH_002` · `409 MATCH_003` · `409 MATCH_004` · `409 MATCH_005` · `409 MATCH_008` · `409 MATCH_014` · `422 MATCH_013` · `422 SPORT_002` · `422 TEAM_015` · `422 TEAM_016`.

### 2.9. POST /api/v1/match-requests/{requestId}/reject

Business_Rules §2.2 `PENDING → REJECTED`, không ảnh hưởng uy tín. Dùng trước khi huỷ kèo `OPEN` (BR-M05). Body tuỳ chọn.
**Request:** `RejectMatchRequestRequest` (§3.3) `{ "reason": "Đã có lịch" }`. **Response 204.**
**Lỗi:** `400 VALIDATION_ERROR` · `403 TEAM_002` · `404 MATCH_002` · `409 MATCH_004` · `409 MATCH_008` · `409 MATCH_014` · `422 TEAM_016`.

### 2.10. POST /api/v1/match-requests/{requestId}/withdraw

Business_Rules §2.2. Thứ tự kiểm như 2.5: trạng thái kèo / yêu cầu trước, thiếu lý do sau (§2.0). `PENDING → WITHDRAWN`: không trừ điểm. `ACCEPTED → WITHDRAWN`: kèo → `CANCELLED` (đội huỷ = đội gửi), trừ điểm đội gửi theo §1, `reason` bắt buộc (user chốt 2026-11-09). `expectedStatus` (bắt buộc) = trạng thái yêu cầu client đang hiển thị; khác trạng thái hiện tại (vd màn hình còn `PENDING` nhưng yêu cầu vừa được chấp nhận) → `409 MATCH_015`, không rút, không trừ điểm (§1). Yêu cầu `PENDING` bị đổi trạng thái chen giữa lúc đọc và lúc ghi (vd vừa được chấp nhận) cũng trả `409 MATCH_015`; rút lặp lại trên yêu cầu đã `WITHDRAWN` vẫn trả `409 MATCH_008`. Gửi rút lặp lại tuần tự → `MATCH_008`; gửi hai lần đồng thời → lần sau có thể nhận `MATCH_015`. FE xử lý hai mã như nhau: tải lại trạng thái yêu cầu; không có lần rút thứ hai, không trừ điểm.
**Request:** `WithdrawMatchRequestRequest` (§3.5) `{ "expectedStatus": "ACCEPTED", "reason": "Không đủ người" }`.
**Response 200:** `MatchRequestWithdrawalResponse` (§3.15).
**Lỗi:** `400 VALIDATION_ERROR` · `403 TEAM_002` · `404 MATCH_002` · `409 MATCH_004` · `409 MATCH_005` · `409 MATCH_008` · `409 MATCH_014` · `409 MATCH_015` · `422 MATCH_011` · `422 MATCH_012` · `422 TEAM_016`.

### 2.11. GET /api/v1/teams/{teamId}/matches

Kèo đội tạo hoặc đã ghép. Query: `status` ∈ 6 trạng thái kèo hoặc `ALL` (mặc định `ALL`; lọc theo trạng thái hiệu lực) · `role` ∈ `AUTHOR|OPPONENT|ALL` (mặc định `ALL`) · `page`, `pageSize`. Sort `startsAt` ↓, `matchId` ↓.
**Response 200:** `MatchSummaryResponse[]` + `pagination` (`distanceKm` = `null`). **Lỗi:** `400 VALIDATION_ERROR` · `404 TEAM_001`.

### 2.12. GET /api/v1/teams/{teamId}/match-requests

Yêu cầu đội đã gửi (để theo dõi / rút). Query: `status` ∈ 6 trạng thái yêu cầu hoặc `ALL` (mặc định `ALL`; trạng thái hiệu lực) · `page`, `pageSize`. Sort `requestedAt` ↓, `requestId` ↓.
**Response 200:** `MatchRequestResponse[]` + `pagination`. **Lỗi:** `400 VALIDATION_ERROR` · `403 TEAM_002` · `404 TEAM_001` · `422 TEAM_016`.

### 2.13. GET /api/v1/sports

Bộ môn **đang bật** (dùng khi tạo kèo). Query `page`, `pageSize`. Sort `sportName` ↑, `sportId` ↑.
**Response 200:** `SportResponse[]` (§3.16) + `pagination`. **Lỗi:** `400 VALIDATION_ERROR`.

### 2.14. GET /api/v1/admin/sports

Mọi bộ môn. Query `isActive` (tuỳ chọn, `true|false`; vắng = tất cả) · `page`, `pageSize`. Sort như 2.13.
**Response 200:** `SportResponse[]` + `pagination`. **Lỗi:** `400 VALIDATION_ERROR` · `403 AUTH_004`.

### 2.15. PATCH /api/v1/admin/sports/{sportId}

Bật/tắt bộ môn (Overview §9.7, Business_Rules §5.4). Đặt giá trị đang có → 200, không ghi. Tắt: chặn tạo kèo mới; kèo `OPEN` của môn ẩn khỏi 2.2, không gửi / chấp nhận yêu cầu được (`SPORT_002`); không tự huỷ, kèo đã ghép giữ vòng đời (user chốt 2026-11-09). Ghi audit log admin.
**Request:** `UpdateSportRequest` (§3.8) `{ "isActive": false }`. **Response 200:** `SportResponse`.
**Lỗi:** `400 VALIDATION_ERROR` · `403 AUTH_004` · `404 SPORT_001`.

---

## 3. Data Models (DTO)

Chuỗi được `trim()` ở service; field tuỳ chọn gửi `""` → lưu `null`.

### 3.1. CreateMatchRequest
| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `teamId` | `UUID` | Yes | đội tác giả |
| `sportId` | `UUID` | Yes | |
| `venueId` | `UUID` | Yes | |
| `format` | `String` | Yes | `FIVE_A_SIDE\|SEVEN_A_SIDE\|ELEVEN_A_SIDE` |
| `startsAt` | `OffsetDateTime` | Yes | ≥ now + 1h (BR-M01) |
| `endsAt` | `OffsetDateTime` | Yes | > `startsAt`, ≤ `startsAt` + 12h (user chốt 2026-11-09) |
| `expiresAt` | `OffsetDateTime` | Yes | > now; `startsAt` − 7 ngày ≤ `expiresAt` ≤ `startsAt` (BR-M02, AC-M01, Scenario 5) |
| `skillLevels` | `String[]` | No | ≤3 nhãn, không trùng, thuộc 9 nhãn; vắng/`[]` = mọi trình độ (user chốt 2026-11-09) |
| `ageNote` | `String` | No | ≤100 (user chốt 2026-11-09) |
| `matchNature` | `String` | No | ≤200 (user chốt 2026-11-09) |

Lỗi quy tắc thời gian → `400 VALIDATION_ERROR`, `validationErrors[].field` là field vi phạm; message `expiresAt` theo spec: "Thời hạn phải trong khoảng tối đa 7 ngày trước giờ đá đến giờ bắt đầu".

### 3.2. SendMatchRequestRequest
`teamId` (Yes, `UUID`) — đội gửi.

### 3.3. RejectMatchRequestRequest
Body tuỳ chọn. `reason` (No, ≤500).

### 3.4. CancelMatchRequest
| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `teamId` | `UUID` | Yes | đội huỷ (tác giả, hoặc đội đã ghép khi `CONFIRMED`) |
| `expectedStatus` | `String` | Yes | `OPEN\|CONFIRMED` — trạng thái kèo client đang thấy |
| `reason` | `String` | Kèo `CONFIRMED`: Yes (`422 MATCH_011` nếu vắng/blank) | ≤500 |

### 3.5. WithdrawMatchRequestRequest
| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `expectedStatus` | `String` | Yes | `PENDING\|ACCEPTED` — trạng thái yêu cầu client đang thấy |
| `reason` | `String` | Yêu cầu `ACCEPTED`: Yes (`422 MATCH_011` nếu vắng/blank) | ≤500 |

### 3.6. UpdateCostAgreementRequest
| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `costType` | `String` | No | `VENUE_ONLY\|DRINKS_ONLY\|BOTH` |
| `costSplit` | `String` | Khi có `costType` | `FIFTY_FIFTY\|SEVENTY_THIRTY_HOME\|SEVENTY_THIRTY_LOSER\|LOSER_PAYS_DRINKS`; `LOSER_PAYS_DRINKS` chỉ khi `costType` có tiền nước |
| `venueAmount` | `Long` | Khi `costType` ∈ `VENUE_ONLY\|BOTH` | 1..1 000 000 000 (BR-C04); phải `null` khi `costType` không có tiền sân |
| `drinksAmount` | `Long` | Khi `costType` ∈ `DRINKS_ONLY\|BOTH` | 1..1 000 000 000; phải `null` khi `costType` không có tiền nước |
| `customItems` | `CostCustomItemRequest[]` | No (vắng = `[]`) | ≤10 (user chốt 2026-11-09) |
| `customItems[].itemName` | `String` | Yes | không blank, ≤100 |
| `customItems[].amount` | `Long` | Yes | 1..1 000 000 000 |

`costType = null` → `costSplit`, `venueAmount`, `drinksAmount` phải `null` (chỉ có khoản tự nhập hoặc xoá). Vi phạm quan hệ giữa các field → `400 VALIDATION_ERROR` gắn vào field sai.

### 3.7. SearchMatchRequest (query 2.2)
| Param | Type | Mặc định | Ràng buộc |
|---|---|---|---|
| `sportId` | `UUID` | — | |
| `format` | `String` | — | như §3.1 |
| `skillLevel` | `String` | — | 1 trong 9 nhãn; khớp kèo có nhãn này **hoặc** không chọn trình độ (user chốt 2026-11-09) |
| `startsAtFrom`, `startsAtTo` | `OffsetDateTime` | — | lọc `startsAt` trong [`startsAtFrom`, `startsAtTo`]; `startsAtFrom ≤ startsAtTo` |
| `lat`, `lng` | `BigDecimal` | — | đi cùng nhau; [-90, 90] / [-180, 180]; khoảng cách tới toạ độ địa điểm của kèo |
| `radiusKm` | `BigDecimal` | `10` khi có toạ độ | 0.1..50; chỉ khi có toạ độ |
| `page`, `pageSize` | `Integer` | 1 / 20 | `page ≥ 1`, `pageSize` 1..100 (như VENUE-001 `SearchVenueRequest`) |

### 3.8. UpdateSportRequest
`isActive` (Yes, `Boolean`).

### 3.9. MatchResponse
```json
{ "data": {
  "matchId": "uuid", "type": "HAS_VENUE", "status": "OPEN",
  "sportId": "uuid", "sportName": "Bóng đá", "format": "SEVEN_A_SIDE", "skillLevels": ["Nhiệt tình"],
  "ageNote": "U23", "matchNature": "Giao hữu",
  "startsAt": "2026-11-12T11:00:00Z", "endsAt": "2026-11-12T13:00:00Z", "expiresAt": "2026-11-12T05:00:00Z",
  "authorTeam": { "teamId": "uuid", "teamName": "Team Alpha", "avatarUrl": null, "reliabilityScore": 80 },
  "opponentTeam": null,
  "venue": { "venueId": "uuid", "venueName": "Sân bóng Hoa Lư", "address": "…", "latitude": 10.7881234,
             "longitude": 106.6991234, "venueStatus": "ACTIVE", "primaryImageUrl": null },
  "costAgreement": { "costType": null, "costSplit": null, "venueAmount": null, "drinksAmount": null, "customItems": [] },
  "cancelledByTeamId": null, "cancelledAt": null, "cancelReason": null,
  "createdAt": "2026-11-09T03:00:00Z", "updatedAt": "2026-11-09T03:00:00Z"
} }
```
- `opponentTeam` cùng dạng `authorTeam`; khác `null` khi kèo đã ghép (`CONFIRMED`, `ARCHIVED`, `COMPLETED`, hoặc `CANCELLED` sau khi ghép).
- `venue` luôn là dữ liệu địa điểm hiện tại (BR-V10), kể cả `venueStatus = INACTIVE` (BC-V04: kèo cũ giữ tham chiếu).
- `costAgreement.customItems[]`: `CostCustomItemResponse` (§3.12).

### 3.10. MatchSummaryResponse
`matchId`, `status`, `sportId`, `sportName`, `format`, `skillLevels`, `startsAt`, `endsAt`, `expiresAt`, `authorTeamId`, `authorTeamName`, `authorTeamReliabilityScore`, `opponentTeamId`, `opponentTeamName`, `venueId`, `venueName`, `venueAddress`, `distanceKm` (2 chữ số; `null` khi không gửi toạ độ), `createdAt`.

### 3.11. MatchRequestResponse
`requestId`, `matchId`, `requesterTeamId`, `requesterTeamName`, `requesterTeamReliabilityScore`, `status` (hiệu lực, §1), `reason` (lý do từ chối / rút), `requestedAt`, `respondedAt` (chấp nhận / từ chối / bị thay thế), `withdrawnAt`. 2.12 thêm ngữ cảnh kèo: `startsAt`, `authorTeamId`, `authorTeamName` (`null` ở 2.6, 2.7).

### 3.12. CostAgreementResponse
`matchId`, `costType`, `costSplit`, `venueAmount`, `drinksAmount`, `customItems` (`CostCustomItemResponse[]`: `itemId`, `itemName`, `amount`; đúng thứ tự client gửi lên — lưu thứ tự ở server), `updatedAt` (thời điểm cập nhật kèo).

### 3.13. AcceptMatchRequestResponse
`matchId`, `matchStatus` (`CONFIRMED`), `requestId`, `requestStatus` (`ACCEPTED`), `opponentTeamId`, `opponentTeamName`, `respondedAt`.

### 3.14. MatchCancellationResponse
`matchId`, `status` (`CANCELLED`), `cancelledByTeamId`, `cancelledAt`, `cancelReason`, `reliabilityPenalty` (`0` khi huỷ `OPEN`; khi huỷ `CONFIRMED`: điểm trừ của đội huỷ theo §1).

### 3.15. MatchRequestWithdrawalResponse
`requestId`, `matchId`, `status` (`WITHDRAWN`), `withdrawnAt`, `matchStatus` (trạng thái kèo sau thao tác: giữ nguyên khi rút `PENDING`, `CANCELLED` khi rút `ACCEPTED`), `reliabilityPenalty` (`0` khi rút `PENDING`; khi rút `ACCEPTED`: điểm trừ của đội gửi theo §1).

### 3.16. SportResponse
`sportId`, `sportName`, `isActive`, `createdAt`.

Tên đội không che (đội không soft-delete). Kèo không trả thông tin cá nhân của đội trưởng.

---

## 4. Mã lỗi mới

Đã soát `ErrorCode` hiện hành (chưa có `MATCH_*`, `SPORT_*`) và mã `VENUE_*` của VENUE-001. Domain `MATCH`, `SPORT` cần thêm vào `api-convention.md` §6 (`Component_Class_Design.md` §11, nội bộ BE).

| Mã | HTTP | Ngữ cảnh | Message |
|---|---:|---|---|
| `MATCH_001` | 404 | Kèo không tồn tại | "Không tìm thấy kèo" |
| `MATCH_002` | 404 | Yêu cầu ghép kèo không tồn tại | "Không tìm thấy yêu cầu ghép kèo" |
| `MATCH_003` | 409 | Kèo đã `CONFIRMED`: gửi yêu cầu (UC-M03 3a), chấp nhận (UC-M04 3a, Scenario 6), sửa chi phí | "Kèo đã được ghép" |
| `MATCH_004` | 409 | Kèo đã hết hạn bài đăng (`EXPIRED` trong DB hoặc `OPEN` quá `expiresAt`) — mọi endpoint ghi | "Kèo đã hết hạn" |
| `MATCH_005` | 409 | Kèo ở trạng thái không cho phép thao tác (`CANCELLED`, `ARCHIVED`, `COMPLETED`; rút yêu cầu `ACCEPTED` khi kèo không còn `CONFIRMED`) | "Kèo không còn ở trạng thái cho phép thao tác này" |
| `MATCH_006` | 409 | Đội đã có yêu cầu cho kèo (đang chờ, đã chấp nhận, bị từ chối hoặc bị thay thế) | "Đội đã gửi yêu cầu cho kèo này" |
| `MATCH_007` | 422 | Gửi yêu cầu cho kèo của chính đội | "Không thể gửi yêu cầu cho kèo của đội mình" |
| `MATCH_008` | 409 | Yêu cầu không còn ở trạng thái cần cho thao tác (đã được xử lý / rút / thay thế / hết hạn). 2.10 rút lặp lại: tuần tự → `MATCH_008`, đồng thời → có thể `MATCH_015`; FE tải lại như nhau | "Yêu cầu đã được xử lý" |
| `MATCH_009` | 403 | `teamId` không phải đội tác giả, hoặc đội đã ghép khi kèo `CONFIRMED` | "Đội không có quyền với kèo này" |
| `MATCH_010` | 422 | Huỷ kèo `OPEN` khi còn yêu cầu `PENDING` (BR-M05, UC-M08 3a) | "Vui lòng từ chối tất cả yêu cầu trước khi hủy" |
| `MATCH_011` | 422 | Thiếu lý do khi huỷ kèo `CONFIRMED` / rút yêu cầu `ACCEPTED` (UC-M08) | "Vui lòng nhập lý do hủy" |
| `MATCH_012` | 422 | Huỷ / rút khi trận đã bắt đầu | "Trận đã bắt đầu, không thể hủy" |
| `MATCH_013` | 422 | Đội còn lại không `ACTIVE`: đội tác giả (2.6) / đội gửi (2.8) | "Đội đối phương hiện không hoạt động" |
| `MATCH_014` | 409 | Hết lượt thử do xung đột đồng thời / deadlock | "Dữ liệu vừa được cập nhật bởi thao tác khác, vui lòng thử lại" |
| `MATCH_015` | 409 | Huỷ (2.5) / rút (2.10): trạng thái kèo / yêu cầu hiện tại khác `expectedStatus` client gửi, hoặc yêu cầu `PENDING` đổi trạng thái giữa lúc đọc và lúc ghi (2.10) | "Trạng thái đã thay đổi, vui lòng tải lại" |
| `SPORT_001` | 404 | Bộ môn không tồn tại | "Không tìm thấy bộ môn" |
| `SPORT_002` | 422 | Bộ môn đang tắt (tạo kèo 2.1, gửi yêu cầu 2.6, chấp nhận 2.8) | "Bộ môn hiện không được hỗ trợ" |

Mã dùng lại: `VALIDATION_ERROR`, `AUTH_001`, `AUTH_004`, `TEAM_001`, `TEAM_002`, `TEAM_015`, `TEAM_016`, `VENUE_001`, `VENUE_002`, `SYS_*`. `MATCH_009` là 403 miền (như `TEAM_002`), khác `AUTH_004` của filter.

## 5. Thay đổi schema

`V6__match_flow_a_lifecycle.sql`: `match` thêm `version`, `skill_levels`, `cancel_reason`; `format` đổi `VARCHAR(20)` + CHECK enum; CHECK `cost_type`/`cost_split`/số tiền; `match_request` thêm `reason` + unique một phần "tối đa 1 `ACCEPTED`"; `cost_custom_item` thêm `display_order` (thứ tự client gửi) + CHECK `amount > 0`; index tìm kèo. DDL và lý do: `Component_Class_Design.md` §5.1 (nội bộ BE).

## 6. Danh sách [cần xác nhận]

Không còn mục mở ở tài liệu này. Mục còn lại (kỹ thuật): 1–5, 10, 11 ở `Component_Class_Design.md` §12, 6–9 ở `Processing_Flow.md` §10 (nội bộ BE). **Chặn code** = phải chốt/kiểm chứng trước khi viết phần liên quan; **Không chặn** = đã có mặc định, đổi sau được.

### 6.1. Quyết định nghiệp vụ đã chốt (user, 2026-11-09)

Đồng bộ vào SRS `SYSTEM-001` v1.3 (`Business_Rules.md` BR-M09..BR-M18, BR-C03..BR-C04; `Acceptance_Criteria.md` AC-M06 sửa, AC-M09..AC-M12).

| Quyết định | Nơi áp dụng |
|---|---|
| Quy tắc trừ điểm (đội bị trừ, khung giờ) | §1 |
| Rút yêu cầu `ACCEPTED` ⇒ kèo `CANCELLED`, chỉ đội rút bị trừ; đội đã ghép huỷ qua 2.5 cho cùng kết quả, trừ một lần | 2.5, 2.10 |
| Không huỷ / rút khi trận đã bắt đầu (`MATCH_012`) | 2.5, 2.10 |
| Huỷ / rút so với `expectedStatus` client gửi; lệch → `MATCH_015`, không trừ điểm | §1, 2.5, 2.10 |
| Đội `INACTIVE`/`DISBANDED`, bộ môn tắt: kèo `OPEN` ẩn khỏi tìm kiếm, chặn gửi/chấp nhận; không tự huỷ; đội `INACTIVE` vẫn huỷ / từ chối / rút | §1, 2.2, 2.6, 2.8, 2.15 |
| Chi phí: chỉ đội tác giả sửa, chỉ khi `OPEN`; bỏ `CUSTOM` của `cost_type` | 2.4, §3.6 |
| Trận ≤ 12h; ≤ 10 khoản tự nhập; `skillLevels` ≤ 3, rỗng = mọi trình độ; `ageNote` ≤100, `matchNature` ≤200, `reason` ≤500 (bắt buộc khi huỷ/rút sau ghép); số tiền ≤ 1 000 000 000; `LOSER_PAYS_DRINKS` chỉ khi có tiền nước | §3 |
| Tên field `startsAt` / `endsAt` | §3 |
| API đọc trả trạng thái hiệu lực `EXPIRED` không ghi DB; thao tác ghi / job chuyển `EXPIRED` thật | §1 |
| Một user làm đội trưởng cả đội tác giả lẫn đội gửi (hai đội khác nhau) → cho phép; không kiểm trùng lịch | 2.6 |
