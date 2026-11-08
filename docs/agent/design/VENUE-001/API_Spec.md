# API Specification — Quản lý địa điểm sân

| Mục | Giá trị |
|---|---|
| Mã chức năng | `VENUE-001` |
| Phạm vi tầng | Backend (Spring Boot REST API). Frontend: ngoài phạm vi repo này |
| Repo/stack | ArenaFlow-be · Spring Boot 4.0.9 · Java 21 · PostgreSQL (Neon) · Flyway · Cloudinary ([ADR-0009](../../decisions/0009-cloudinary-signed-direct-upload.md) `Proposed`) |
| Quy ước áp dụng | `docs/agent/api-convention.md`, `docs/agent/security-backend.md`; quy ước chung USER-001 `API_Spec.md` §1 (UUID v7, envelope, enum UPPERCASE, phân trang, `VALIDATION_ERROR`) |
| Ràng buộc dữ liệu | V1 (`venue`, `venue_image`, `venue_report`) + `V5__venue_management.sql` (`Component_Class_Design.md` §5.1) |
| Spec nghiệp vụ | `docs/agent/business-spec/SYSTEM-001/` (v1.2) — UC-V01..V06, BR-V01..V10, BC-V01..V07, Business_Rules §2.6, §5.1/§5.4, AC-V01..V05, DoD Venue |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-09 · v1.0 (baseline) |

## 1. Tổng quan

Thêm địa điểm không cần duyệt (UC-V01); người tạo hoặc Admin sửa địa điểm (UC-V05) và xoá ảnh (UC-V06); mọi user thêm ảnh qua Cloudinary (backend ký, client upload thẳng, backend xác nhận) và đổi ảnh chính (UC-V02, BR-V07); User (không gồm Admin) report địa điểm đang hoạt động (UC-V03); Admin xử lý report, ngừng / kích hoạt lại địa điểm (UC-V04, Business_Rules §2.6). Danh sách mặc định loại `INACTIVE`; chi tiết `INACTIVE` vẫn đọc được. Kèo chỉ lưu `venue_id` nên luôn thấy thông tin mới nhất (BR-V10) — sửa địa điểm không bị chặn bởi kèo.

**Ngoài phạm vi:** frontend · gửi notification thật (chỉ phát `ApplicationEvent`, `Component_Class_Design.md` §4.7) · cập nhật `last_selected_at` khi tạo kèo (MATCH-001 gọi port, `Component_Class_Design.md` §4.6) · sắp xếp lại ảnh · `Idempotency-Key` (`api-convention.md` §13, hoãn như USER-001/002).

**Quy ước riêng:**
- Mọi endpoint cần access token; không endpoint public mới. Token sai → `401 AUTH_001`; `/api/v1/admin/**` không phải admin → `403 AUTH_004`.
- Enum (UPPERCASE): `status` địa điểm `ACTIVE|INACTIVE`; `venueType` `FIVE_A_SIDE|SEVEN_A_SIDE|ELEVEN_A_SIDE` (BR-V05); `createdByType` `ADMIN|USER`; `reason` report `CLOSED|WRONG_INFO|OTHER`; `status` report `PENDING|VERIFIED|REJECTED`.
- "Admin" = claim `roles` của JWT chứa `ADMIN` (cùng nguồn `SecurityConfig`). "Người tạo" = `venue.created_by_user_id`.
- **Địa điểm `INACTIVE`:** chỉ Admin được ghi (sửa, cấp chữ ký, thêm/đổi chính/xoá ảnh — BR-V06, BR-V07); người khác → `422 VENUE_002`. Không ai report được (UC-V03 4b).

## 2. Endpoints

| # | Method & Path | Quyền | Thành công | Tiêu chí `api-convention.md` §5 |
|---|---|---|---|---|
| 2.1 | `POST /api/v1/venues` | User, Admin | 201 | B2 |
| 2.2 | `GET /api/v1/venues` | User, Admin | 200 | B1 |
| 2.3 | `GET /api/v1/venues/{venueId}` | User, Admin | 200 | B1 |
| 2.4 | `POST /api/v1/venues/{venueId}/image-upload-signatures` | User, Admin | 200 | B3 (chữ ký, `publicId` do server sinh; không tạo tài nguyên) |
| 2.5 | `POST /api/v1/venues/{venueId}/images` | Người xin chữ ký ở 2.4 | 201 | B2 |
| 2.6 | `PATCH /api/v1/venues/{venueId}/images/{imageId}/set-primary` | User, Admin | 200 | B4 |
| 2.7 | `POST /api/v1/venues/{venueId}/reports` | User (không Admin) | 201 | B2 |
| 2.8 | `GET /api/v1/admin/venue-reports` | Admin | 200 | B1 |
| 2.9 | `PATCH /api/v1/admin/venue-reports/{reportId}/resolve` | Admin | 200 | B3 (`handledAt`, `handledByName`) |
| 2.10 | `PATCH /api/v1/admin/venues/{venueId}/mark-inactive` | Admin | 200 | B4 |
| 2.11 | `PATCH /api/v1/admin/venues/{venueId}/reactivate` | Admin | 200 | B4 |
| 2.12 | `PATCH /api/v1/venues/{venueId}` | Người tạo, Admin | 200 | B4 |
| 2.13 | `DELETE /api/v1/venues/{venueId}/images/{imageId}` | Người tạo, Admin | 200 | B3 (ảnh chính mới do server chọn), B4 |

Ghi chú hợp đồng: `201` không kèm `Location`. 2.6, 2.9–2.11 là action sub-resource đổi trạng thái (`api-convention.md` §2.1), `PATCH` theo tiền lệ `mark-inactive`. 2.4 không đổi trạng thái → danh từ số nhiều, `POST` trả `200` (B3). 2.13 trả `200` có body thay `204` vì server chọn ảnh chính mới (§5 chọn mã theo kết quả). `q`/`lat`/`lng`/`radiusKm` là tham số tìm kiếm, không phải filter theo field (§8.1); sort cố định như USER-001.

### 2.0. Thứ tự guard (nguồn duy nhất)

Sau JWT (401/403 ở filter) và Bean Validation (400). Mẫu USER-002 §2.1a (tồn tại → trạng thái → quyền); kiểm role không cần DB đứng đầu; khi quy tắc trạng thái phụ thuộc role, quyền đứng trước trạng thái.

| Endpoint | Thứ tự guard |
|---|---|
| 2.1 | `USER_001` |
| 2.3 | `VENUE_001` |
| 2.4 | `VENUE_012` → `VENUE_001` → `VENUE_002` (không phải Admin) → `VENUE_003` |
| 2.5 | `VENUE_012` → `VENUE_005` (không chạm DB: mẫu `publicId`, người xin chữ ký, hạn xác nhận, chữ ký response) → `VENUE_001` → `VENUE_002` (không phải Admin) → `VENUE_003` → `VENUE_006` (khi INSERT) |
| 2.6 | `VENUE_001` → `VENUE_002` (không phải Admin) → `VENUE_004` |
| 2.7 | `VENUE_015` (caller là Admin) → `VENUE_001` → `VENUE_002` → `VENUE_014` (khi INSERT) |
| 2.9 | `VENUE_009` → `VENUE_010` |
| 2.10 | `VENUE_001` → `VENUE_007` |
| 2.11 | `VENUE_001` → `VENUE_008` |
| 2.12 | `VENUE_001` → `VENUE_013` → `VENUE_002` (không phải Admin) |
| 2.13 | `VENUE_001` → `VENUE_013` → `VENUE_002` (không phải Admin) → `VENUE_004` |

---

### 2.1. POST /api/v1/venues

UC-V01. Tạo `ACTIVE`, không duyệt. `createdByType` = `ADMIN` nếu `"user".role = ADMIN`, ngược lại `USER`; `createdById` = caller trong cả hai trường hợp `[cần xác nhận #6]`.

**Request:** `CreateVenueRequest` (§3.1).
```json
{ "venueName": "Sân bóng Hoa Lư", "address": "2 Đinh Tiên Hoàng, Phường Đa Kao, TP.HCM",
  "latitude": 10.7881234, "longitude": 106.6991234,
  "venueType": "SEVEN_A_SIDE", "phone": "0281234567", "website": "https://example.com" }
```
**Response 201:** `VenueResponse` (§3.7), `images = []`. **Lỗi:** `400 VALIDATION_ERROR` · `404 USER_001`.

### 2.2. GET /api/v1/venues

Danh sách / tìm / gợi ý (AC-V01, BC-V04). Query: `SearchVenueRequest` (§3.3). Sort: có toạ độ → `distanceKm` ↑, `venueId` ↑; không → `venueName` ↑, `venueId` ↑.
**Response 200:** `VenueSummaryResponse[]` (§3.8) + `pagination`. **Lỗi:** `400 VALIDATION_ERROR` (gồm chỉ gửi một trong `lat`/`lng`, `radiusKm` thiếu toạ độ).

### 2.3. GET /api/v1/venues/{venueId}

Chi tiết + ảnh theo `displayOrder`, kể cả `INACTIVE`. **Response 200:** `VenueResponse`. **Lỗi:** `400 VALIDATION_ERROR` · `404 VENUE_001`.

### 2.4. POST /api/v1/venues/{venueId}/image-upload-signatures

UC-V02 bước 1–2. Cấp tham số upload đã ký; server chọn `publicId` (định dạng: `Component_Class_Design.md` §4.5) gắn với **người gọi** — chỉ người này lưu được ảnh ở 2.5 (danh tính được ràng buộc bằng HMAC, không lộ user id trong URL). Kiểm sớm giới hạn 5 ảnh (kiểm lại ở 2.5). Không body.

**Response 200:** `VenueImageUploadSignatureResponse` (§3.9).

**Client dùng:** `POST multipart` tới `uploadUrl` với `file` + `api_key`, `timestamp`, `public_id`, `allowed_formats`, `overwrite`, `signature` đúng giá trị nhận được. Chữ ký hết hạn 1 giờ sau `timestamp` ([Cloudinary — Authentication signatures](https://cloudinary.com/documentation/authentication_signatures)). Rồi gọi 2.5 với `public_id`, `version`, `format`, `signature` trong response Cloudinary, trong hạn `confirm-window` (`Component_Class_Design.md` §8).

**Lỗi:** `404 VENUE_001` · `422 VENUE_002` · `422 VENUE_003` · `503 VENUE_012`.

### 2.5. POST /api/v1/venues/{venueId}/images

UC-V02 bước 3–5. Backend **không nhận URL**: kiểm `publicId` đúng mẫu, đúng địa điểm trên path, do chính caller xin chữ ký, còn trong hạn xác nhận và không ở tương lai; xác minh chữ ký response Cloudinary; rồi tự dựng `imageUrl` (`Processing_Flow.md` §3.5). Hạn xác nhận ngắn hơn ân hạn của job dọn asset để job không xoá ảnh còn xác nhận được.

**Ảnh chính (BR-V03):** chưa có ảnh → ảnh mới luôn là ảnh chính; có ảnh và `isPrimary = true` → ảnh chính cũ bỏ. `displayOrder` = lớn nhất + 1 (có thể có khoảng trống sau 2.13).

**Request:** `AddVenueImageRequest` (§3.4). **Response 201:** `VenueImageResponse` (§3.10).

**Lỗi:** `400 VALIDATION_ERROR` · `400 VENUE_005` · `404 VENUE_001` · `409 VENUE_006` · `422 VENUE_002` · `422 VENUE_003` · `503 VENUE_012`.

### 2.6. PATCH /api/v1/venues/{venueId}/images/{imageId}/set-primary

Đổi ảnh chính (BR-V03, BR-V07). Ảnh đích đã là ảnh chính → 200, không đổi. Không body; không có thao tác bỏ ảnh chính.
**Response 200:** `VenueImageResponse[]` theo `displayOrder`. **Lỗi:** `400 VALIDATION_ERROR` · `404 VENUE_001` · `404 VENUE_004` · `422 VENUE_002`.

### 2.7. POST /api/v1/venues/{venueId}/reports

UC-V03. Lưu report `PENDING`, không đổi trạng thái địa điểm (BR-V04). Admin → `403 VENUE_015` (Admin sửa hoặc đánh dấu ngừng hoạt động trực tiếp). Địa điểm `INACTIVE` → `422 VENUE_002`; đã có report `PENDING` cùng `(caller, venue, reason)` → `409 VENUE_014` (BR-V09). Phát `VenueReportedEvent` mỗi report; `reason = CLOSED` lần đầu → `VenueAdminAlertEvent(CLOSURE_REPORTED)` `[cần xác nhận #1, #2]`.

**Request:** `ReportVenueRequest` (§3.5) `{ "reason": "CLOSED", "detail": "Sân đã đóng cửa" }`. **Response 201:** `VenueReportResponse` (§3.11).
**Lỗi:** `400 VALIDATION_ERROR` · `403 VENUE_015` · `404 VENUE_001` · `409 VENUE_014` · `422 VENUE_002`.

### 2.8. GET /api/v1/admin/venue-reports

UC-V04 bước 1. Query: `status` ∈ `PENDING|VERIFIED|REJECTED|ALL` (mặc định `PENDING`) · `venueId` (tuỳ chọn) · `page`, `pageSize` như USER-001. Sort `createdAt` ↓, `reportId` ↓.
**Response 200:** `AdminVenueReportResponse[]` (§3.12) + `pagination`. **Lỗi:** `400 VALIDATION_ERROR` · `403 AUTH_004`.

### 2.9. PATCH /api/v1/admin/venue-reports/{reportId}/resolve

`PENDING` → `VERIFIED|REJECTED`, ghi người xử lý. Không đổi trạng thái địa điểm (dùng 2.10). Sau đó user được report lại cùng lý do. Hết report `CLOSED` `PENDING` → mở lại cổng `CLOSURE_REPORTED` `[cần xác nhận #2]`.
**Request:** `ResolveVenueReportRequest` (§3.6) `{ "status": "REJECTED" }`. **Response 200:** `AdminVenueReportResponse`.
**Lỗi:** `400 VALIDATION_ERROR` · `403 AUTH_004` · `404 VENUE_009` · `409 VENUE_010` · `409 VENUE_011`.

### 2.10. PATCH /api/v1/admin/venues/{venueId}/mark-inactive

UC-V04 bước 3–6. `ACTIVE` → `INACTIVE`; report `CLOSED` `PENDING` → `VERIFIED` `[cần xác nhận #3, #4]`. Kèo cũ giữ tham chiếu. Không body.
**Response 200:** `VenueResponse`. **Lỗi:** `403 AUTH_004` · `404 VENUE_001` · `409 VENUE_007` · `409 VENUE_011`.

### 2.11. PATCH /api/v1/admin/venues/{venueId}/reactivate

`INACTIVE` → `ACTIVE`, mở lại cả hai cổng cảnh báo. Không body.
**Response 200:** `VenueResponse`. **Lỗi:** `403 AUTH_004` · `404 VENUE_001` · `409 VENUE_008` · `409 VENUE_011`.

### 2.12. PATCH /api/v1/venues/{venueId}

UC-V05, AC-V04. Cập nhật một phần: field vắng/`null` → giữ nguyên; `""` cho `venueType`/`phone`/`website` → xoá `[cần xác nhận #7]`. Không field nào đổi → 200, không ghi DB. Không đổi `status`, `createdBy*`, `lastSelectedAt`.
**Request:** `UpdateVenueRequest` (§3.2). **Response 200:** `VenueResponse`.
**Lỗi:** `400 VALIDATION_ERROR` · `403 VENUE_013` · `404 VENUE_001` · `409 VENUE_011` · `422 VENUE_002`.

### 2.13. DELETE /api/v1/venues/{venueId}/images/{imageId}

UC-V06, AC-V05. Xoá cứng dòng `venue_image` (lệch có chủ đích `api-convention.md` §12, đã được user duyệt 2026-11-09 — `Component_Class_Design.md` §10). Xoá ảnh chính khi còn ảnh khác → ảnh `displayOrder` nhỏ nhất (hoà → `imageId` nhỏ nhất) thành ảnh chính, cùng transaction (BR-V08). Asset Cloudinary do job dọn xoá sau (`Processing_Flow.md` §3.16). Không body.
**Response 200:** `VenueImageResponse[]` còn lại (có thể rỗng). **Lỗi:** `400 VALIDATION_ERROR` · `403 VENUE_013` · `404 VENUE_001` · `404 VENUE_004` · `422 VENUE_002`.

---

## 3. Data Models (DTO)

Thời gian: `OffsetDateTime` UTC ISO-8601. Chuỗi được `trim()` ở service. Field tuỳ chọn gửi `""`: ở request tạo coi như không nhập (lưu `null`, không báo 400); ở 2.12 nghĩa là xoá.

### 3.1. CreateVenueRequest
| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `venueName` | `String` | Yes | không blank, ≤200 (BR-V01) |
| `address` | `String` | Yes | không blank, ≤500 `[cần xác nhận #5]` |
| `latitude` | `BigDecimal` | Yes | [-90, 90], ≤7 chữ số thập phân |
| `longitude` | `BigDecimal` | Yes | [-180, 180], ≤7 chữ số thập phân |
| `venueType` | `String` | No | `FIVE_A_SIDE\|SEVEN_A_SIDE\|ELEVEN_A_SIDE` hoặc `""` |
| `phone` | `String` | No | `^0[0-9]{9,10}$` hoặc `""` `[cần xác nhận #5]` |
| `website` | `String` | No | URL `http(s)://` ≤500 hoặc `""` |

### 3.2. UpdateVenueRequest
Mọi field tuỳ chọn, cùng ràng buộc §3.1; `venueName`/`address` nếu có thì không blank; `latitude`, `longitude` gửi riêng lẻ được.

### 3.3. SearchVenueRequest (query)
| Param | Type | Mặc định | Ràng buộc |
|---|---|---|---|
| `q` | `String` | — | ≤100; khớp không phân biệt hoa thường trong `venueName` hoặc `address` |
| `status` | `VenueStatusFilter` | `ACTIVE` | `ACTIVE\|INACTIVE\|ALL` |
| `lat`, `lng` | `BigDecimal` | — | đi cùng nhau; miền như §3.1 |
| `radiusKm` | `BigDecimal` | `5` khi có toạ độ | 0.1..50; chỉ khi có toạ độ |
| `page`, `pageSize` | `Integer` | 1 / 20 | `page ≥ 1`, `pageSize` 1..100 |

`pageSize` tối đa 100 (không theo 50 của AC §3.4): user chốt 2026-11-09 để nhất quán `PageRequestFactory` dùng chung.

### 3.4. AddVenueImageRequest
| Field | Type | Required | Ràng buộc |
|---|---|---|---|
| `publicId` | `String` | Yes | không blank, ≤255 |
| `version` | `Long` | Yes | > 0 |
| `format` | `String` | Yes | `jpg\|jpeg\|png\|webp` (chữ thường như Cloudinary trả) |
| `signature` | `String` | Yes | không blank, ≤128 |
| `isPrimary` | `Boolean` | No | mặc định `false` |

### 3.5. ReportVenueRequest
`reason` (Yes, `CLOSED\|WRONG_INFO\|OTHER`) · `detail` (No, ≤1000 `[cần xác nhận #5]`).

### 3.6. ResolveVenueReportRequest
`status` (Yes, `VERIFIED\|REJECTED`).

### 3.7. VenueResponse
```json
{ "data": {
  "venueId": "uuid", "venueName": "Sân bóng Hoa Lư", "address": "…",
  "latitude": 10.7881234, "longitude": 106.6991234, "venueType": "SEVEN_A_SIDE",
  "phone": "0281234567", "website": "https://example.com", "status": "ACTIVE",
  "createdByType": "USER", "createdById": "uuid", "createdByName": "Nguyễn Văn A",
  "lastSelectedAt": null, "adminVerifiedInactiveAt": null,
  "createdAt": "2026-11-09T03:00:00Z", "updatedAt": "2026-11-09T03:00:00Z",
  "images": [ /* VenueImageResponse theo displayOrder */ ]
} }
```
`createdById`/`createdByName` = `null` khi FK bị set null (chỉ Admin sửa được). `createdByName` che theo USER-001 §3.12. Client so `createdById` / role để hiện nút; backend vẫn kiểm.

### 3.8. VenueSummaryResponse
`venueId`, `venueName`, `address`, `latitude`, `longitude`, `venueType`, `status`, `primaryImageUrl` (`null` khi chưa có ảnh), `distanceKm` (2 chữ số; `null` khi không gửi toạ độ), `lastSelectedAt`.

### 3.9. VenueImageUploadSignatureResponse
```json
{ "data": {
  "uploadUrl": "https://api.cloudinary.com/v1_1/<cloudName>/image/upload",
  "cloudName": "<cloudName>", "apiKey": "<api_key>",
  "publicId": "<folder>/venues/<venueId>/1731150000-<nonce>-<tag>", "timestamp": 1731150000,
  "allowedFormats": "jpg,jpeg,png,webp", "overwrite": false, "signature": "<hex>",
  "expiresAt": "2026-11-09T04:00:00Z"
} }
```
`apiKey` không phải secret; **NEVER** trả `api_secret`. `timestamp` (Unix giây) và `overwrite` (boolean) là tham số Cloudinary client gửi lại nguyên tên/giá trị nên không theo hậu tố `…At` / tiền tố `is…` (`api-convention.md` §10); thời điểm ISO là `expiresAt`.

### 3.10. VenueImageResponse
`imageId`, `venueId`, `imageUrl`, `isPrimary`, `displayOrder`, `uploadedById`, `createdAt`. Không trả `publicId`.

### 3.11. VenueReportResponse
`reportId`, `venueId`, `reason`, `detail`, `status`, `createdAt`.

### 3.12. AdminVenueReportResponse
`reportId`, `venueId`, `venueName`, `venueStatus`, `reporterId`, `reporterName`, `reason`, `detail`, `status`, `handledById`, `handledByName`, `handledAt`, `createdAt`. Tên che theo USER-001 §3.12; `handled*` = `null` khi `PENDING`.

---

## 4. Mã lỗi mới (domain `VENUE`)

Đã soát `ErrorCode` hiện hành: chưa có `VENUE_*`. Domain cần thêm vào `api-convention.md` §6 (`Component_Class_Design.md` §11).

| Mã | HTTP | Ngữ cảnh | Message |
|---|---:|---|---|
| `VENUE_001` | 404 | Địa điểm không tồn tại | "Không tìm thấy địa điểm" |
| `VENUE_002` | 422 | Địa điểm `INACTIVE`: report (2.7); ghi khi caller không phải Admin (2.4–2.6, 2.12, 2.13) | "Địa điểm đã ngừng hoạt động" |
| `VENUE_003` | 422 | Đã có 5 ảnh (BR-V02) | "Tối đa 5 ảnh mỗi địa điểm" |
| `VENUE_004` | 404 | Ảnh không tồn tại hoặc không thuộc địa điểm | "Không tìm thấy ảnh" |
| `VENUE_005` | 400 | `publicId` sai mẫu / khác địa điểm / không do caller xin chữ ký / quá hạn xác nhận / thời điểm ký ở tương lai · chữ ký response sai | "Ảnh tải lên không hợp lệ" |
| `VENUE_006` | 409 | `publicId` đã được lưu | "Ảnh này đã được lưu" |
| `VENUE_007` | 409 | Mark-inactive địa điểm đã `INACTIVE` | "Địa điểm đã ở trạng thái ngừng hoạt động" |
| `VENUE_008` | 409 | Reactivate địa điểm đang `ACTIVE` | "Địa điểm đang hoạt động" |
| `VENUE_009` | 404 | Report không tồn tại | "Không tìm thấy báo cáo địa điểm" |
| `VENUE_010` | 409 | Report không còn `PENDING` | "Báo cáo đã được xử lý" |
| `VENUE_011` | 409 | Hết retry do xung đột đồng thời / deadlock (2.9–2.12) | "Dữ liệu vừa được cập nhật bởi thao tác khác, vui lòng thử lại" |
| `VENUE_012` | 503 | Thiếu cấu hình Cloudinary (gồm thư mục) (2.4, 2.5) | "Dịch vụ lưu ảnh tạm thời không khả dụng" |
| `VENUE_013` | 403 | Không phải người tạo, không phải Admin (2.12, 2.13) | "Chỉ người tạo địa điểm hoặc quản trị viên được thực hiện" |
| `VENUE_014` | 409 | Đã có report `PENDING` cùng địa điểm + lý do (UC-V03 4a1) | "Bạn đã report địa điểm này, đang chờ admin xử lý" |
| `VENUE_015` | 403 | Admin gọi report (UC-V03 actor) | "Quản trị viên không report địa điểm; hãy sửa hoặc đánh dấu ngừng hoạt động trực tiếp" |

Mã dùng lại: `VALIDATION_ERROR`, `AUTH_001`, `AUTH_004`, `USER_001`, `SYS_*`. `VENUE_013`/`VENUE_015` là 403 miền (như `TEAM_002`), khác `AUTH_004` của filter.

## 5. Thay đổi schema

`V5__venue_management.sql` — DDL và lý do: `Component_Class_Design.md` §5.1.

## 6. Danh sách [cần xác nhận]

Đánh số toàn thiết kế: 1–7 ở đây, 8–15 ở `Component_Class_Design.md` §12, 16–18 ở `Processing_Flow.md` §10. **Chặn code** = phải chốt/kiểm chứng trước khi viết phần liên quan; **Không chặn** = đã có mặc định, đổi sau được.

| # | Nội dung | Loại |
|---|---|---|
| 1 | Report phát cả `VenueReportedEvent` (mỗi report, UC-V03 bước 6) lẫn `VenueAdminAlertEvent(CLOSURE_REPORTED)` một lần (BC-V05); NOTIFICATION-001 quyết định gộp | Không chặn |
| 2 | Mở lại cổng "cảnh báo 1 lần": `CLOSURE_REPORTED` khi hết report `CLOSED` `PENDING` hoặc reactivate; `STALE_180_DAYS` khi được chọn lại hoặc reactivate | Không chặn |
| 3 | Mark-inactive không bắt buộc có report `PENDING` | Không chặn |
| 4 | Mark-inactive chỉ chuyển report `CLOSED` → `VERIFIED`; `WRONG_INFO`/`OTHER` giữ `PENDING` | Không chặn |
| 5 | Giới hạn spec không nêu: `address` ≤500, `detail` ≤1000, `phone` `^0[0-9]{9,10}$` | Không chặn |
| 6 | Admin tạo địa điểm → `createdByType = ADMIN`, vẫn lưu `created_by_user_id` (admin đó là "người tạo") | Không chặn |
| 7 | 2.12: `""` = xoá field tuỳ chọn, `null` = giữ nguyên | Không chặn |
