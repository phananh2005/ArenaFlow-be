# API Specification — Đánh giá, report, tranh chấp & uy tín đội

<!-- File này được publish cho FE (ADR-0010). Viết theo design-standards.md §11:
     không tên class/bảng/cột/migration/transaction/khoá/số truy vấn; không trỏ Component_Class_Design.md,
     Processing_Flow.md, docs/database/. Chi tiết nội bộ BE ghi ở hai file đó. -->

| Mục | Giá trị |
|---|---|
| Mã chức năng | `REPUTATION-001` |
| Phạm vi tầng | Backend (Spring Boot REST API). Frontend: ngoài phạm vi repo này |
| Repo | ArenaFlow-be |
| Quy ước áp dụng | `api-convention.md` (envelope, phân trang, định dạng lỗi, kiểu dữ liệu); quy ước chung USER-001 `API_Spec.md` §1.1, §1.2 và MATCH-001 `API_Spec.md` §1.1; kèo, trạng thái kèo, mã `MATCH_*`: MATCH-001 `API_Spec.md`; mã `TEAM_*`, `AUTH_*`: USER-001/USER-002 `API_Spec.md`; hiển thị user đã xoá ở API Admin: [ADR-0002](../../decisions/0002-soft-delete-policy.md) (Sửa đổi 2026-11-12) |
| Spec nghiệp vụ | `docs/agent/business-spec/SYSTEM-001/` (v1.8) — UC-R01..R07; BR-R01..R10, BC-R01..R07; Business_Rules §3.1–§3.3, §6, §7, §8.2, §8.4; AC-R01..R07; Scenario 8, 9 |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-13 · v1.0 |

## 1. Tổng quan

Sau trận, đội trưởng **đánh giá** đối phương (1–5 sao hoặc "đội kia không đến") và có thể **report** đối phương (bùng, chơi xấu, giao tiếp tệ, không đúng trình độ). Report được chấp nhận ngay và trừ điểm uy tín của đội bị report. Đội bị report **bùng** có 24 giờ để **tranh chấp**; Admin xem bằng chứng hai bên và quyết định cuối cùng. Mọi người dùng xem được **uy tín đội**: điểm uy tín (Reliability Score), tỉ lệ hoàn thành (Completion Rate), sao trung bình và huy hiệu xác minh.

**Ngoài phạm vi:** đánh giá / report / uy tín **cá nhân** (người chơi lẻ — MERCENARY-001) · gửi thông báo (NOTIFICATION-001) · liên kết số điện thoại cho tài khoản đăng nhập bằng Google/Facebook (chưa có API) · sửa / xoá đánh giá hoặc report (không hỗ trợ) · header `Idempotency-Key` (`api-convention.md` §13) chưa hỗ trợ — FE chặn bấm lặp nút gửi.

### 1.1. Quy ước chung

- Như MATCH-001 `API_Spec.md` §1.1: Bearer token (`401 AUTH_001` → refresh), `/api/v1/admin/**` cần quyền Admin (`403 AUTH_004`), envelope `data` / `error` / `pagination`, `201` không kèm header `Location`, thời gian ISO-8601 trả UTC, chuỗi được bỏ khoảng trắng đầu/cuối (`""` ở field tuỳ chọn = không nhập), enum UPPERCASE, `400 VALIDATION_ERROR` kèm `validationErrors[]`.
- Danh sách phân trang `page` (≥ 1, mặc định 1) / `pageSize` (1..100, mặc định 20); thứ tự cố định ghi ở từng endpoint.
- Mọi thao tác ghi có thể trả `409 MATCH_014` "Dữ liệu vừa được cập nhật bởi thao tác khác, vui lòng thử lại" khi nhiều thao tác đụng nhau liên tục → FE tải lại rồi cho người dùng bấm lại.

### 1.2. Ai thao tác được

- Đánh giá (2.1), report (2.3): đội trưởng đang hoạt động của đội `ACTIVE` là đội tạo kèo hoặc đội đã ghép của trận. Không gửi `teamId` — hệ thống lấy đội mà người gọi đang làm đội trưởng; đội được đánh giá / bị report luôn là đội còn lại.
  - Người gọi không là đội trưởng của đội nào trong trận → `403 TEAM_002`. Đội của người gọi đã giải tán cũng trả mã này (đội trưởng đã rời đội khi giải tán).
  - Người gọi là đội trưởng của cả hai đội (BR-M18 cho phép ghép như vậy) → không được đánh giá (`422 RATING_003`) hay report (`422 REPORT_005`).
  - Đội của người gọi đang tạm ngưng (`INACTIVE`, Admin khoá) → `422 TEAM_015`.
  - Chỉ kiểm đội của người gọi: đội được đánh giá / bị report đang tạm ngưng hoặc đã giải tán vẫn được đánh giá / report — FE không ẩn nút theo trạng thái đội đối phương.
- Xem đánh giá của trận (2.2), danh sách report của đội (2.4), tranh chấp (2.5): đội trưởng của đội `ACTIVE` trong path/query hoặc của đội bị report. Đội đã giải tán → `422 TEAM_016`; đội đang tạm ngưng (`INACTIVE`, Admin khoá) → `422 TEAM_015`.
- Xem uy tín đội (2.9): mọi người dùng đã đăng nhập; không hạn chế theo trạng thái đội.
- Thành viên thường (không phải đội trưởng) không xem được đánh giá / report của đội.

### 1.3. Điểm uy tín (Reliability Score) thay đổi khi nào

Điểm đội nằm trong [0, 100], khởi đầu 80; cộng/trừ vượt biên thì dừng ở biên. Mức hiển thị ở §4.1 (`reliabilityLevel`) — chỉ để hiển thị, không hạn chế chức năng.

| Sự kiện | Đội | Thay đổi |
|---|---|---|
| Trận chuyển `COMPLETED` (lần đánh giá **đầu tiên** của trận, 2.1) | Cả hai đội — trừ trường hợp đánh giá "đội kia không đến" | +2 |
| Đánh giá đầu tiên là "đội kia không đến" | Đội đánh giá: +2 (và chuỗi như dưới) · đội bị đánh giá vắng: 0, chuỗi trận sạch giữ nguyên | — |
| Chuỗi trận sạch chạm bội số của 5 lúc trận `COMPLETED` (5, 15, 25… → +5; 10, 20, 30… → +10) | Đội đạt mốc | +5 / +10 (cộng thêm vào +2) |
| Nhận đánh giá 5 sao | Đội được đánh giá | +1 |
| Bị report (2.3) | Đội bị report | Theo bảng dưới, theo **số lần bị report loại đó kể cả lần này** |
| Admin bác report bùng (2.8, `REJECTED`) | Đội bị report | Hoàn lại đúng số điểm đã thực trừ của report đó |
| Admin xác nhận report bùng (2.8, `CONFIRMED`) | — | Không trừ thêm |
| Huỷ kèo đã ghép / rút yêu cầu đã chấp nhận | Đội huỷ | MATCH-001 `API_Spec.md` §1.5 |

| Loại report | Lần 1 | Lần 2 | Lần 3 | Lần 4 trở đi |
|---|---|---|---|---|
| `NOSHOW` (bùng) | -5 | -8 | -12 | -15 |
| `BAD_PLAY` (chơi xấu) | -3 | -5 | -8 | -10 |
| `BAD_COMMUNICATION` (giao tiếp tệ) | -2 | -4 | -6 | -8 |
| `SKILL_MISMATCH` (không đúng trình độ) | -1 | -2 | -3 | -4 |

**Chuỗi trận sạch** (đếm riêng mỗi đội):
- Lúc trận chuyển `COMPLETED`: đội **không** có report nào (chưa bị bác) trong trận → chuỗi +1; có → chuỗi về 0. Đội bị đánh giá "không đến" ở lần đánh giá đầu tiên: chuỗi **giữ nguyên** — chỉ report bùng mới cắt chuỗi.
- Bị report ở bất kỳ trận nào, kể cả sau khi trận đã `COMPLETED` → chuỗi về 0; điểm +2 / thưởng chuỗi đã cộng **không** bị thu hồi.
- Huỷ kèo đã ghép / rút yêu cầu đã chấp nhận → chuỗi của đội huỷ về 0.
- Chuỗi chạm bội số của 5 → số lần bị report của **mọi loại** về 0 (lần report tiếp theo tính "Lần 1").
- Admin bác report: hoàn điểm và giảm số lần bị report bùng đi 1; chuỗi trận sạch **không** khôi phục.

### 1.4. Tỉ lệ hoàn thành (Completion Rate)

Tính tại thời điểm đọc (2.9), cho từng đội:

- **Trận được tính (mẫu số):** trận của đội đang `COMPLETED`; trận `CANCELLED` sau khi đã ghép mà **chính đội này** huỷ (hoặc rút yêu cầu đã chấp nhận) **dưới 24 giờ** trước giờ bắt đầu — đội bị huỷ kèo không bị tính; trận `ARCHIVED` (chưa ai đánh giá) mà đội bị report bùng (chưa bị bác).
- **Trận hoàn thành (tử số):** trận của đội đang `COMPLETED` mà đội **không** bị report bùng (chưa bị bác). Report bùng loại trận khỏi tử số **ngay khi gửi** (trong lúc chờ tranh chấp cũng vậy); Admin bác → trận được tính lại. Đánh giá "không đến" **không** loại trận khỏi tử số — chỉ report bùng mới loại.
- Trận được tính < 5 → `completionRate = null`, `isNewcomer = true` → FE hiện "Mới tham gia X/5 trận" (X = `countedMatches`). ≥ 5 → `completionRate` = phần trăm làm tròn tới số nguyên.
- Trận `CONFIRMED` chưa đá, `EXPIRED`, `CANCELLED` khi chưa ghép, huỷ từ đủ 24 giờ trước trận, hoặc do đội kia huỷ: không tính.

### 1.5. Huy hiệu xác minh (Verification Badge)

Đội có huy hiệu khi đủ cả 4 điều kiện (BR-R04):
1. Đội trưởng hiện tại có số điện thoại trong hồ sơ tài khoản;
2. Số trận hoàn thành (`completedMatches`, §1.4) **> 5**;
3. Sao trung bình nhận được **> 3.0** (đánh giá "không đến" không tính vào trung bình; chưa có sao nào → không đủ);
4. Điểm uy tín **≥ 70**.

Hệ thống cấp / thu hồi ngay sau mỗi đánh giá, report, quyết định tranh chấp của đội. Các thay đổi khác — huỷ kèo đã ghép / rút yêu cầu đã chấp nhận (bị trừ điểm), đổi đội trưởng, số điện thoại của đội trưởng — được cập nhật trong lần đối soát hằng ngày: huy hiệu có thể trễ **tối đa khoảng 1 ngày** so với điều kiện thực tế. `verification.verifiedAt` là thời điểm cấp lần gần nhất.

### 1.6. Ai thấy đánh giá nào (BC-R03, BC-R04)

- Đội trưởng luôn xem được đánh giá **đội mình gửi** (`given`).
- Đánh giá **đối phương gửi cho đội mình** (`received`) chỉ hiện **sau khi đội mình đã đánh giá**. Trước đó `received = null` dù đối phương đã đánh giá hay chưa — API không tiết lộ đối phương đã/chưa đánh giá.
- Không sửa, không xoá đánh giá sau khi gửi.

### 1.7. Report và tranh chấp

- Report được chấp nhận ngay (`AUTO_ACCEPTED`) và trừ điểm (§1.3). Một trận, mỗi đội report đội kia **tối đa một lần cho mỗi loại**; được gửi nhiều loại khác nhau. Không giới hạn thời gian report sau trận.
- Chỉ report **bùng** (`NOSHOW`) có tranh chấp: đội bị report gửi bằng chứng (văn bản) trong **24 giờ** kể từ lúc report (`disputeDeadlineAt`). Hết hạn mà không tranh chấp → report được xác nhận tự động, API trả `status = "CONFIRMED"`.
- Trong lúc tranh chấp (`DISPUTED`) điểm đã trừ được giữ nguyên. Admin quyết định:
  - `CONFIRMED` (report đúng): giữ nguyên mức đã trừ, không trừ thêm; trận không vào tử số Completion Rate.
  - `REJECTED` (bác report): hoàn điểm đã thực trừ (có thể ít hơn mức trong bảng nếu lúc trừ điểm đã chạm 0), giảm số lần bị report bùng đi 1; trận tính lại vào tử số nếu đã `COMPLETED`.
- Quyết định của Admin là cuối cùng; không tranh chấp lại.

## 2. Endpoints

| # | Method + Path | Dùng khi | Ai gọi được | Thành công |
|---|---|---|---|---|
| 2.1 | `POST /api/v1/matches/{matchId}/ratings` | Gửi đánh giá đối phương sau trận | Đội trưởng của một trong hai đội | 201 |
| 2.2 | `GET /api/v1/matches/{matchId}/ratings?teamId=` | Xem đánh giá đã gửi / đã nhận của trận | Đội trưởng của `teamId` | 200 |
| 2.3 | `POST /api/v1/matches/{matchId}/reports` | Report đối phương | Đội trưởng của một trong hai đội | 201 |
| 2.4 | `GET /api/v1/teams/{teamId}/reports` | Danh sách report đội nhận / đã gửi | Đội trưởng của đội | 200 |
| 2.5 | `POST /api/v1/reports/{reportId}/dispute` | Tranh chấp report bùng | Đội trưởng đội bị report | 201 |
| 2.6 | `GET /api/v1/admin/disputes` | Admin: danh sách tranh chấp | Admin | 200 |
| 2.7 | `GET /api/v1/admin/disputes/{disputeId}` | Admin: chi tiết tranh chấp | Admin | 200 |
| 2.8 | `PATCH /api/v1/admin/disputes/{disputeId}/resolve` | Admin: quyết định tranh chấp | Admin | 200 |
| 2.9 | `GET /api/v1/teams/{teamId}/reputation` | Xem uy tín đội | Người dùng đã đăng nhập | 200 |

---

### 2.1. Gửi đánh giá — `POST /api/v1/matches/{matchId}/ratings`

**Dùng khi:** sau trận (thường mở từ thông báo "Trận đấu hôm nay thế nào?"), đội trưởng chọn **1–5 sao** hoặc **"Đội kia không đến"** (UC-R01). Chọn "không đến" → sau khi gửi thành công, FE gợi ý "Bạn có muốn report bùng không?" và mở 2.3 với `reportType = NOSHOW` (hai thao tác tách nhau).
**Ai gọi được:** §1.2. Trận phải đang `ARCHIVED` hoặc `COMPLETED`.

**Path params:** `matchId` (UUID, bắt buộc).

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `isNoShow` | boolean | Không | Mặc định `false`; `true` = "Đội kia không đến" | `false` |
| `score` | integer | Có khi `isNoShow = false` | Số nguyên 1..5; **không gửi** khi `isNoShow = true` | `5` |
| `comment` | string | Không | ≤ 500 ký tự | `"Đá đẹp, đúng giờ"` |
| `raterTeamScore` | integer | Không | 0..999; tỷ số của **đội mình**; gửi cùng `rateeTeamScore`; không gửi khi `isNoShow = true` | `3` |
| `rateeTeamScore` | integer | Không | 0..999; tỷ số của đội đối phương; gửi cùng `raterTeamScore` | `2` |

```json
{ "isNoShow": false, "score": 5, "comment": "Đá đẹp, đúng giờ", "raterTeamScore": 3, "rateeTeamScore": 2 }
```

```json
{ "isNoShow": true, "comment": "Chờ 30 phút không thấy đội bạn" }
```

Tỷ số chỉ để lưu kỷ niệm, không ảnh hưởng điểm. Mỗi đội trưởng nhập tỷ số riêng trong đánh giá của mình.

**Response 201** — Đánh giá (§4.3) kèm `matchStatus` (luôn `COMPLETED` sau khi gửi thành công).

```json
{
  "data": {
    "ratingId": "01936c10-1a2b-7c3d-8e4f-5a6b7c8d9e01",
    "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
    "raterTeamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c",
    "rateeTeamId": "01935e11-dddd-7eee-8fff-1a2b3c4d5e6f",
    "isNoShow": false,
    "score": 5,
    "comment": "Đá đẹp, đúng giờ",
    "raterTeamScore": 3,
    "rateeTeamScore": 2,
    "createdAt": "2026-11-12T14:05:00Z",
    "matchStatus": "COMPLETED"
  }
}
```

Điểm uy tín thay đổi theo §1.3; response không trả điểm — cần hiển thị thì gọi 2.9.

**Lỗi** (thứ tự kiểm từ trên xuống)

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu `score` khi `isNoShow = false`; có `score` hoặc tỷ số khi `isNoShow = true`; `score` ngoài 1..5 hoặc không nguyên; chỉ gửi một trong hai tỷ số; `comment` > 500 | Hiện lỗi cạnh field |
| 404 | `MATCH_001` | Không có trận `matchId` | Quay lại danh sách kèo |
| 403 | `TEAM_002` | Người gọi không là đội trưởng của đội nào trong trận (kể cả đội đã giải tán) | Ẩn nút "Đánh giá" |
| 422 | `TEAM_015` | Đội của người gọi đang tạm ngưng hoạt động | Hiện message |
| 422 | `RATING_003` | Người gọi là đội trưởng của cả hai đội | Ẩn nút, hiện message |
| 422 | `RATING_001` | Trận không ở `ARCHIVED`/`COMPLETED` (chưa tới giờ kết thúc, chưa được hệ thống chuyển — trễ tối đa ~5 phút sau `endsAt`, hoặc trận không diễn ra: `OPEN`, `EXPIRED`, `CANCELLED`) | Hiện message; trận vừa kết thúc → thử lại sau ít phút |
| 409 | `RATING_002` | Đội mình đã đánh giá trận này | Tải lại 2.2, hiện đánh giá đã gửi |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.2. Xem đánh giá của trận — `GET /api/v1/matches/{matchId}/ratings?teamId=`

**Dùng khi:** màn hình chi tiết trận đã kết thúc — hiện đánh giá đội mình đã gửi và (nếu được phép) đánh giá đối phương dành cho đội mình (§1.6). Dùng để quyết định hiện nút "Đánh giá" (`given = null`) hay kết quả.
**Ai gọi được:** đội trưởng của `teamId`; `teamId` là đội tạo kèo hoặc đội đã ghép của trận. Người làm đội trưởng cả hai đội chọn phía bằng `teamId`.

**Path / Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `matchId` | UUID | Có | — | `01936b20-7d8e-7f90-a1b2-c3d4e5f60708` |
| `teamId` | UUID | Có | Đội của người gọi trong trận | `01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c` |

**Response 200**

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `matchId`, `teamId` | UUID | Như request |
| `given` | Đánh giá (§4.3) \| null | Đánh giá đội `teamId` đã gửi; `null` = chưa đánh giá |
| `received` | Đánh giá (§4.3) \| null | Đánh giá đối phương gửi cho `teamId`; luôn `null` khi `given = null` |

```json
{
  "data": {
    "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
    "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c",
    "given": {
      "ratingId": "01936c10-1a2b-7c3d-8e4f-5a6b7c8d9e01",
      "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
      "raterTeamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c",
      "rateeTeamId": "01935e11-dddd-7eee-8fff-1a2b3c4d5e6f",
      "isNoShow": false,
      "score": 5,
      "comment": "Đá đẹp, đúng giờ",
      "raterTeamScore": 3,
      "rateeTeamScore": 2,
      "createdAt": "2026-11-12T14:05:00Z"
    },
    "received": null
  }
}
```

Trận chưa kết thúc / không diễn ra → `200` với `given = null`, `received = null` (không lỗi).

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu `teamId` / sai định dạng | Sửa tham số |
| 404 | `MATCH_001` | Không có trận | Quay lại |
| 404 | `TEAM_001` | Không có đội `teamId` | Quay lại |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 422 | `TEAM_015` | Đội đang tạm ngưng hoạt động | Hiện message |
| 403 | `TEAM_002` | Người gọi không là đội trưởng của `teamId` | Ẩn mục đánh giá |
| 403 | `MATCH_009` | `teamId` không thuộc trận | Kiểm lại `teamId` |

---

### 2.3. Report đối phương — `POST /api/v1/matches/{matchId}/reports`

**Dùng khi:** đội trưởng bấm "Report" ở trận đã kết thúc, chọn loại và nhập lý do (UC-R02); hoặc sau khi đánh giá "không đến" (2.1) và đồng ý report bùng.
**Ai gọi được:** §1.2. Trạng thái trận:

| Trận | Loại được report |
|---|---|
| `ARCHIVED`, `COMPLETED` | Cả 4 loại |
| `CANCELLED` **sau khi đã ghép** (có đội đã ghép) | Chỉ `BAD_COMMUNICATION` |
| `OPEN`, `CONFIRMED`, `EXPIRED`, `CANCELLED` khi chưa ghép | Không |

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `reportType` | string | Có | `NOSHOW` \| `BAD_PLAY` \| `BAD_COMMUNICATION` \| `SKILL_MISMATCH` | `"NOSHOW"` |
| `reason` | string | Có | Bắt buộc; 1..1000 ký tự sau khi bỏ khoảng trắng đầu/cuối | `"Đội bạn không đến, không báo trước"` |

```json
{ "reportType": "NOSHOW", "reason": "Đội bạn không đến, không báo trước" }
```

**Response 201** — Report (§4.4) theo góc nhìn đội gửi (`direction = "SENT"`).

```json
{
  "data": {
    "reportId": "01936c20-2b3c-7d4e-9f50-6a7b8c9d0e01",
    "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
    "matchStartsAt": "2026-11-12T11:00:00Z",
    "direction": "SENT",
    "counterpartTeam": { "teamId": "01935e11-dddd-7eee-8fff-1a2b3c4d5e6f", "name": "FC Sấm Sét", "avatarUrl": null },
    "reportType": "NOSHOW",
    "reason": "Đội bạn không đến, không báo trước",
    "status": "AUTO_ACCEPTED",
    "disputeDeadlineAt": "2026-11-13T14:10:00Z",
    "canDispute": false,
    "reliabilityPenalty": null,
    "dispute": null,
    "createdAt": "2026-11-12T14:10:00Z"
  }
}
```

**Lỗi** (thứ tự kiểm từ trên xuống)

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `reportType` ngoài enum; thiếu `reason` / rỗng sau trim / > 1000 | Hiện lỗi cạnh field |
| 404 | `MATCH_001` | Không có trận | Quay lại |
| 403 | `TEAM_002` | Người gọi không là đội trưởng của đội nào trong trận (kể cả đội đã giải tán) | Ẩn nút "Report" |
| 422 | `TEAM_015` | Đội của người gọi đang tạm ngưng hoạt động | Hiện message |
| 422 | `REPORT_005` | Người gọi là đội trưởng của cả hai đội | Ẩn nút, hiện message |
| 422 | `REPORT_002` | Trận chưa kết thúc hoặc không có đối phương (bảng trên) | Hiện message |
| 422 | `REPORT_003` | Kèo đã huỷ sau khi ghép mà `reportType ≠ BAD_COMMUNICATION` | Chỉ cho chọn "Giao tiếp tệ" với kèo đã huỷ |
| 409 | `REPORT_004` | Đội mình đã report đội kia loại này trong trận | Ẩn loại đã report (xem 2.4 `SENT`) |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.4. Danh sách report của đội — `GET /api/v1/teams/{teamId}/reports`

**Dùng khi:** màn hình "Report" của đội — tab **Đội bị report** (`RECEIVED`: xem lý do, điểm bị trừ, nút "Tranh chấp") và tab **Đội đã report** (`SENT`).
**Ai gọi được:** đội trưởng của đội `ACTIVE` có ID `teamId`; đội đã giải tán → `422 TEAM_016`, đội đang tạm ngưng → `422 TEAM_015`.

**Path / Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` | UUID | Có | — | `01935e11-dddd-7eee-8fff-1a2b3c4d5e6f` |
| `direction` | string | Không | `RECEIVED` (mặc định) \| `SENT` | `RECEIVED` |
| `page` | integer | Không | ≥ 1; mặc định 1 | `1` |
| `pageSize` | integer | Không | 1..100; mặc định 20 | `20` |

**Thứ tự sắp xếp:** `createdAt` mới nhất trước, bằng nhau → `reportId` giảm dần.

**Response 200** — mảng Report (§4.4) + `pagination`. Ví dụ tab `RECEIVED`, report bùng đang chờ Admin:

```json
{
  "data": [
    {
      "reportId": "01936c20-2b3c-7d4e-9f50-6a7b8c9d0e01",
      "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
      "matchStartsAt": "2026-11-12T11:00:00Z",
      "direction": "RECEIVED",
      "counterpartTeam": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": "https://res.cloudinary.com/demo/image/upload/v1/teams/hoalu.jpg" },
      "reportType": "NOSHOW",
      "reason": "Đội bạn không đến, không báo trước",
      "status": "DISPUTED",
      "disputeDeadlineAt": "2026-11-13T14:10:00Z",
      "canDispute": false,
      "reliabilityPenalty": 5,
      "dispute": {
        "disputeId": "01936c30-3c4d-7e5f-a061-7b8c9d0e1f02",
        "status": "PENDING",
        "evidence": "Đội tôi có mặt lúc 18:00, đã nhắn tin cho đội trưởng bên kia",
        "decision": null,
        "resolutionNote": null,
        "createdAt": "2026-11-12T15:00:00Z",
        "resolvedAt": null
      },
      "createdAt": "2026-11-12T14:10:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `direction` ngoài enum; `page`/`pageSize` ngoài miền | Sửa tham số |
| 404 | `TEAM_001` | Không có đội | Quay lại |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |
| 422 | `TEAM_015` | Đội đang tạm ngưng hoạt động | Hiện message |
| 403 | `TEAM_002` | Không phải đội trưởng của đội | Ẩn màn hình report |

---

### 2.5. Tranh chấp report bùng — `POST /api/v1/reports/{reportId}/dispute`

**Dùng khi:** đội trưởng đội bị report bùng bấm "Tranh chấp" (hiện khi `canDispute = true` ở 2.4) và nhập bằng chứng (UC-R03).
**Ai gọi được:** đội trưởng của **đội bị report** khi đội đang `ACTIVE`; đội đã giải tán → `422 TEAM_016`, đội đang tạm ngưng → `422 TEAM_015`. Report phải loại `NOSHOW`, trạng thái `AUTO_ACCEPTED`, trước `disputeDeadlineAt`.

**Path params:** `reportId` (UUID, bắt buộc).

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `evidence` | string | Có | Bắt buộc; văn bản 1..1000 ký tự; không đính kèm ảnh/tệp | `"Đội tôi có mặt lúc 18:00, đã nhắn tin cho đội trưởng bên kia"` |

```json
{ "evidence": "Đội tôi có mặt lúc 18:00, đã nhắn tin cho đội trưởng bên kia" }
```

**Response 201**

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `disputeId` | UUID | Id tranh chấp |
| `reportId` | UUID | Report bị tranh chấp |
| `status` | string | Luôn `PENDING` |
| `evidence` | string | Bằng chứng đã gửi |
| `reportStatus` | string | Luôn `DISPUTED` |
| `createdAt` | string | Thời điểm gửi |

```json
{
  "data": {
    "disputeId": "01936c30-3c4d-7e5f-a061-7b8c9d0e1f02",
    "reportId": "01936c20-2b3c-7d4e-9f50-6a7b8c9d0e01",
    "status": "PENDING",
    "evidence": "Đội tôi có mặt lúc 18:00, đã nhắn tin cho đội trưởng bên kia",
    "reportStatus": "DISPUTED",
    "createdAt": "2026-11-12T15:00:00Z"
  }
}
```

Điểm đã trừ được giữ nguyên tới khi Admin quyết định (§1.7).

**Lỗi** (thứ tự kiểm từ trên xuống)

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu `evidence` / rỗng sau trim / > 1000 | Hiện lỗi cạnh field |
| 404 | `REPORT_001` | Không có report | Tải lại 2.4 |
| 422 | `TEAM_016` | Đội bị report đã giải tán | Hiện message |
| 422 | `TEAM_015` | Đội bị report đang tạm ngưng hoạt động | Hiện message |
| 403 | `TEAM_002` | Người gọi không là đội trưởng đội bị report | Ẩn nút "Tranh chấp" |
| 422 | `REPORT_006` | Report không phải loại bùng | Ẩn nút |
| 409 | `REPORT_008` | Report đã được tranh chấp / Admin đã quyết định | Tải lại 2.4 |
| 422 | `REPORT_007` | Đã quá 24 giờ kể từ lúc report | Tải lại 2.4 (report hiện `CONFIRMED`) |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.6. Admin: danh sách tranh chấp — `GET /api/v1/admin/disputes`

**Dùng khi:** admin panel, hàng chờ tranh chấp report bùng (UC-R04).
**Ai gọi được:** Admin.

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `status` | string | Không | `PENDING` (mặc định) \| `RESOLVED` \| `ALL` | `PENDING` |
| `page` | integer | Không | ≥ 1; mặc định 1 | `1` |
| `pageSize` | integer | Không | 1..100; mặc định 20 | `20` |

**Thứ tự sắp xếp:** `status = PENDING` → `createdAt` **cũ nhất trước** (xử lý theo thứ tự đến), bằng nhau → `disputeId` tăng dần. `RESOLVED`, `ALL` → `createdAt` mới nhất trước, bằng nhau → `disputeId` giảm dần.

**Response 200** — mảng Tranh chấp — Admin (§4.6) + `pagination`. Tên người dùng không che (§4.6).

```json
{
  "data": [
    {
      "disputeId": "01936c30-3c4d-7e5f-a061-7b8c9d0e1f02",
      "reportId": "01936c20-2b3c-7d4e-9f50-6a7b8c9d0e01",
      "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
      "matchStartsAt": "2026-11-12T11:00:00Z",
      "reporterTeam": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null, "status": "ACTIVE" },
      "reportedTeam": { "teamId": "01935e11-dddd-7eee-8fff-1a2b3c4d5e6f", "name": "FC Sấm Sét", "avatarUrl": null, "status": "ACTIVE" },
      "reportedById": "01935f77-3c2d-7e1f-8a9b-0c1d2e3f4a5b",
      "reportedByName": "Nguyễn Văn A",
      "isReportedByDeleted": false,
      "disputedById": "01935f78-4d3e-7f20-9b0c-1d2e3f4a5b6c",
      "disputedByName": "Trần Văn B",
      "isDisputedByDeleted": true,
      "status": "PENDING",
      "decision": null,
      "resolvedById": null,
      "resolvedByName": null,
      "isResolvedByDeleted": false,
      "resolvedAt": null,
      "reportCreatedAt": "2026-11-12T14:10:00Z",
      "createdAt": "2026-11-12T15:00:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

Ví dụ: người gửi tranh chấp đã xoá tài khoản → tên thật + `isDisputedByDeleted = true` (FE gắn nhãn "đã xoá").

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 403 | `AUTH_004` | Không phải Admin | Ẩn menu quản trị |
| 400 | `VALIDATION_ERROR` | `status` ngoài enum; `page`/`pageSize` ngoài miền | Sửa bộ lọc |

---

### 2.7. Admin: chi tiết tranh chấp — `GET /api/v1/admin/disputes/{disputeId}`

**Dùng khi:** Admin mở một tranh chấp để xem bằng chứng hai bên: lý do report (đội report), bằng chứng (đội bị report), đánh giá hai đội đã gửi cho trận.
**Ai gọi được:** Admin.

**Path params:** `disputeId` (UUID, bắt buộc).

**Response 200** — Tranh chấp chi tiết — Admin (§4.7).

```json
{
  "data": {
    "disputeId": "01936c30-3c4d-7e5f-a061-7b8c9d0e1f02",
    "reportId": "01936c20-2b3c-7d4e-9f50-6a7b8c9d0e01",
    "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
    "matchStartsAt": "2026-11-12T11:00:00Z",
    "reporterTeam": { "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c", "name": "FC Hoa Lư", "avatarUrl": null, "status": "ACTIVE" },
    "reportedTeam": { "teamId": "01935e11-dddd-7eee-8fff-1a2b3c4d5e6f", "name": "FC Sấm Sét", "avatarUrl": null, "status": "ACTIVE" },
    "reportedById": "01935f77-3c2d-7e1f-8a9b-0c1d2e3f4a5b",
    "reportedByName": "Nguyễn Văn A",
    "isReportedByDeleted": false,
    "disputedById": "01935f78-4d3e-7f20-9b0c-1d2e3f4a5b6c",
    "disputedByName": "Trần Văn B",
    "isDisputedByDeleted": false,
    "status": "PENDING",
    "decision": null,
    "resolvedById": null,
    "resolvedByName": null,
    "isResolvedByDeleted": false,
    "resolvedAt": null,
    "reportCreatedAt": "2026-11-12T14:10:00Z",
    "createdAt": "2026-11-12T15:00:00Z",
    "match": {
      "sportName": "Bóng đá",
      "status": "COMPLETED",
      "startsAt": "2026-11-12T11:00:00Z",
      "endsAt": "2026-11-12T12:30:00Z",
      "venueName": "Sân bóng Hoa Lư"
    },
    "reportReason": "Đội bạn không đến, không báo trước",
    "reliabilityPenalty": 5,
    "evidence": "Đội tôi có mặt lúc 18:00, đã nhắn tin cho đội trưởng bên kia",
    "resolutionNote": null,
    "reporterTeamReliabilityScore": 84,
    "reportedTeamReliabilityScore": 75,
    "matchRatings": [
      {
        "ratingId": "01936c10-1a2b-7c3d-8e4f-5a6b7c8d9e01",
        "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
        "raterTeamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c",
        "rateeTeamId": "01935e11-dddd-7eee-8fff-1a2b3c4d5e6f",
        "isNoShow": true,
        "score": null,
        "comment": "Chờ 30 phút không thấy đội bạn",
        "raterTeamScore": null,
        "rateeTeamScore": null,
        "createdAt": "2026-11-12T14:05:00Z"
      }
    ]
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 403 | `AUTH_004` | Không phải Admin | Ẩn menu quản trị |
| 404 | `DISPUTE_001` | Không có tranh chấp | Quay lại danh sách |

---

### 2.8. Admin: quyết định tranh chấp — `PATCH /api/v1/admin/disputes/{disputeId}/resolve`

**Dùng khi:** Admin chốt tranh chấp: report đúng (`CONFIRMED`) hoặc bác report (`REJECTED`) (UC-R04). Quyết định là cuối cùng.
**Ai gọi được:** Admin; tranh chấp đang `PENDING`.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `decision` | string | Có | `CONFIRMED` \| `REJECTED` | `"REJECTED"` |
| `note` | string | Có | Lý do quyết định, 1..500 ký tự | `"Tin nhắn cho thấy đội bị report có mặt đúng giờ"` |

```json
{ "decision": "REJECTED", "note": "Tin nhắn cho thấy đội bị report có mặt đúng giờ" }
```

| `decision` | Kết quả (§1.7) |
|---|---|
| `CONFIRMED` | Report → `CONFIRMED`; giữ nguyên điểm đã trừ, không trừ thêm |
| `REJECTED` | Report → `REJECTED`; đội bị report được hoàn `reliabilityPenalty` điểm, giảm số lần bị report bùng đi 1 |

**Response 200** — Tranh chấp chi tiết — Admin (§4.7) sau khi quyết định (`status = RESOLVED`, có `decision`, `resolutionNote`, người xử lý). Ví dụ phần thay đổi so với 2.7:

```json
{
  "data": {
    "disputeId": "01936c30-3c4d-7e5f-a061-7b8c9d0e1f02",
    "status": "RESOLVED",
    "decision": "REJECTED",
    "resolvedById": "01935a02-6e7f-7a1b-9c2d-5e6f7a8b9c0d",
    "resolvedByName": "Admin ArenaFlow",
    "isResolvedByDeleted": false,
    "resolvedAt": "2026-11-12T16:00:00Z",
    "resolutionNote": "Tin nhắn cho thấy đội bị report có mặt đúng giờ",
    "reportedTeamReliabilityScore": 80
  }
}
```

(Response thật có đủ mọi field của §4.7; ví dụ trên chỉ liệt kê field đổi.)

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 403 | `AUTH_004` | Không phải Admin | Ẩn menu quản trị |
| 400 | `VALIDATION_ERROR` | `decision` ngoài enum; thiếu `note` / rỗng sau trim / > 500 | Hiện lỗi cạnh field |
| 404 | `DISPUTE_001` | Không có tranh chấp | Quay lại danh sách |
| 409 | `DISPUTE_002` | Tranh chấp đã được quyết định (Admin khác vừa xử lý) | Tải lại 2.7 |
| 409 | `MATCH_014` | §1.1 | Thử lại |

---

### 2.9. Xem uy tín đội — `GET /api/v1/teams/{teamId}/reputation`

**Dùng khi:** trang hồ sơ đội, thẻ đội trong danh sách kèo / thương lượng (FE gọi khi mở chi tiết đội).
**Ai gọi được:** mọi người dùng đã đăng nhập; đội ở mọi trạng thái (kể cả `DISBANDED` — xem lịch sử).

**Path params:** `teamId` (UUID, bắt buộc).

**Response 200** — Uy tín đội (§4.8).

```json
{
  "data": {
    "teamId": "01935e10-aaaa-7bbb-8ccc-0d1e2f3a4b5c",
    "reliabilityScore": 84,
    "reliabilityLevel": "NORMAL",
    "completion": {
      "completedMatches": 6,
      "countedMatches": 7,
      "completionRate": 86,
      "isNewcomer": false
    },
    "rating": { "averageRating": 4.3, "ratingCount": 6 },
    "verification": { "isVerified": true, "verifiedAt": "2026-11-10T03:00:00Z" }
  }
}
```

Đội mới (3 trận được tính): `"completion": { "completedMatches": 2, "countedMatches": 3, "completionRate": null, "isNewcomer": true }` → FE hiện "Mới tham gia 3/5 trận".

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `teamId` sai định dạng | — |
| 404 | `TEAM_001` | Không có đội | Quay lại |

## 3. Hành động theo trạng thái

Hai bảng dưới áp dụng cho đội trưởng có đội đang `ACTIVE`; đội của người gọi đang tạm ngưng hoặc đã giải tán → không thao tác nào (§1.2).

### 3.1. Trận (đánh giá, report)

| Trạng thái trận | Đội trưởng đội tạo kèo / đội đã ghép | Đội trưởng cả hai đội | Người khác |
|---|---|---|---|
| `OPEN`, `CONFIRMED`, `EXPIRED` | — | — | — |
| `CANCELLED` (chưa ghép) | — | — | — |
| `CANCELLED` (sau khi ghép) | Report `BAD_COMMUNICATION` (2.3) | — | — |
| `ARCHIVED` | Đánh giá (2.1) → trận `COMPLETED`; report mọi loại (2.3) | — | — |
| `COMPLETED` | Đánh giá nếu đội mình chưa đánh giá (2.1); report mọi loại (2.3) | — | — |

Ẩn nút "Đánh giá" khi `given ≠ null` (2.2); ẩn loại report đã gửi cho trận (2.4 `SENT`).

### 3.2. Report và tranh chấp

| Report `status` | Đội trưởng đội bị report | Đội trưởng đội report | Admin |
|---|---|---|---|
| `AUTO_ACCEPTED` (loại `NOSHOW`, trước `disputeDeadlineAt`) | Tranh chấp (2.5) | Xem (2.4) | — |
| `AUTO_ACCEPTED` (loại khác — trạng thái cuối) | Xem | Xem | — |
| `DISPUTED` | Xem | Xem | Xem (2.6, 2.7), quyết định (2.8) |
| `CONFIRMED`, `REJECTED` (trạng thái cuối) | Xem | Xem | Xem |

| Tranh chấp `status` | Admin |
|---|---|
| `PENDING` | Xem, quyết định (2.8) |
| `RESOLVED` | Xem |

## 4. Kiểu dữ liệu dùng chung

### 4.1. Enum

| Kiểu / enum | Giá trị | Ý nghĩa |
|---|---|---|
| `reportType` | `NOSHOW` \| `BAD_PLAY` \| `BAD_COMMUNICATION` \| `SKILL_MISMATCH` | Bùng \| Chơi xấu \| Giao tiếp tệ (chửi bới / thiếu tôn trọng trong chat, không phản hồi sau ghép kèo, thiếu hợp tác, ngôn từ khiếm nhã) \| Không đúng trình độ |
| Report `status` | `AUTO_ACCEPTED` \| `DISPUTED` \| `CONFIRMED` \| `REJECTED` | Đã chấp nhận tự động \| Đang tranh chấp \| Xác nhận (Admin xác nhận, hoặc report bùng quá 24 giờ không tranh chấp) \| Admin bác |
| `direction` | `RECEIVED` \| `SENT` | Đội bị report \| Đội đã report |
| Tranh chấp `status` | `PENDING` \| `RESOLVED` | Chờ Admin \| Đã quyết định |
| `decision` | `CONFIRMED` \| `REJECTED` | Report đúng \| Bác report |
| `reliabilityLevel` | `EXCELLENT` \| `NORMAL` \| `WARNING` \| `CRITICAL` | 95–100 (lời khen) \| 70–94 (không cảnh báo) \| 50–69 (cảnh báo vàng) \| 0–49 (cảnh báo đỏ) — chỉ để hiển thị |
| `matchStatus`, `match.status` | Như MATCH-001 `API_Spec.md` §4.1 | — |
| Đội `status` | `ACTIVE` \| `INACTIVE` \| `DISBANDED` | Như USER-001/USER-002 |

### 4.2. Đội rút gọn

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `teamId` | UUID | — |
| `name` | string | Tên đội |
| `avatarUrl` | string \| null | Ảnh đội |
| `status` | string | **Chỉ ở API Admin** (§4.6) |

### 4.3. Đánh giá

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `ratingId` | UUID | — |
| `matchId` | UUID | — |
| `raterTeamId` | UUID | Đội đánh giá |
| `rateeTeamId` | UUID | Đội được đánh giá |
| `isNoShow` | boolean | `true` = "Đội kia không đến" |
| `score` | integer \| null | 1..5; `null` khi `isNoShow = true` |
| `comment` | string \| null | — |
| `raterTeamScore`, `rateeTeamScore` | integer \| null | Tỷ số đội đánh giá nhập (đội mình, đối phương) |
| `createdAt` | string | — |

### 4.4. Report

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `reportId`, `matchId` | UUID | — |
| `matchStartsAt` | string | Giờ bắt đầu trận |
| `direction` | string | Góc nhìn của đội trong path (2.4) / `SENT` (2.3) |
| `counterpartTeam` | Đội rút gọn (§4.2) | `RECEIVED`: đội đã report mình · `SENT`: đội bị mình report |
| `reportType` | string | §4.1 |
| `reason` | string | Lý do của đội report |
| `status` | string | §4.1 |
| `disputeDeadlineAt` | string \| null | Hạn tranh chấp (lúc report + 24 giờ); `null` nếu không phải `NOSHOW` |
| `canDispute` | boolean | `true` khi `RECEIVED`, `NOSHOW`, `AUTO_ACCEPTED` và trước hạn → hiện nút "Tranh chấp". FE vẫn so `disputeDeadlineAt` với giờ hiện tại khi màn hình mở lâu. |
| `reliabilityPenalty` | integer \| null | `RECEIVED`: số điểm đội mình thực bị trừ (≥ 0; có thể nhỏ hơn bảng §1.3 nếu điểm đã chạm 0). `SENT`: luôn `null` |
| `dispute` | object \| null | `null` nếu chưa tranh chấp. Field: `disputeId`, `status`, `evidence` (`RECEIVED`: bằng chứng đội mình gửi; `SENT`: `null`), `decision`, `resolutionNote`, `createdAt`, `resolvedAt` |
| `createdAt` | string | Thời điểm report |

Report `REJECTED`: điểm đã được hoàn — FE hiện "Đã hoàn `reliabilityPenalty` điểm".

### 4.5. Người dùng ở API Admin

Theo ADR-0002 (Sửa đổi 2026-11-12): API `/api/v1/admin/**` **không che** người dùng đã xoá tài khoản — trả tên thật, mỗi field tên có cờ `is…Deleted` ngay sau (`true` = đã xoá; `false` cả khi tên là `null`). FE Admin gắn nhãn "đã xoá" theo cờ. Response Admin của mã này không có field ảnh người dùng. Endpoint không phải Admin của mã này không trả tên người dùng.

### 4.6. Tranh chấp — Admin (2.6)

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `disputeId`, `reportId`, `matchId` | UUID | — |
| `matchStartsAt` | string | — |
| `reporterTeam`, `reportedTeam` | Đội rút gọn (§4.2) kèm `status` | Đội report / đội bị report |
| `reportedById`, `reportedByName`, `isReportedByDeleted` | UUID, string, boolean | Đội trưởng đã gửi report (tại thời điểm gửi) |
| `disputedById`, `disputedByName`, `isDisputedByDeleted` | UUID, string, boolean | Đội trưởng đã gửi tranh chấp |
| `status` | string | `PENDING` \| `RESOLVED` |
| `decision` | string \| null | Khác `null` khi `RESOLVED` |
| `resolvedById`, `resolvedByName`, `isResolvedByDeleted`, `resolvedAt` | UUID \| null, string \| null, boolean, string \| null | Admin quyết định |
| `reportCreatedAt` | string | Thời điểm report |
| `createdAt` | string | Thời điểm tranh chấp |

### 4.7. Tranh chấp chi tiết — Admin (2.7, 2.8)

Mọi field §4.6, thêm:

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `match` | object | `sportName`, `status`, `startsAt`, `endsAt`, `venueName` (string \| null) |
| `reportReason` | string | Lý do report |
| `reliabilityPenalty` | integer | Điểm đội bị report đã thực bị trừ cho report này |
| `evidence` | string | Bằng chứng tranh chấp |
| `resolutionNote` | string \| null | Lý do quyết định của Admin |
| `reporterTeamReliabilityScore`, `reportedTeamReliabilityScore` | integer | Điểm uy tín **hiện tại** của hai đội |
| `matchRatings` | mảng Đánh giá (§4.3) | Đánh giá hai đội đã gửi cho trận (0–2 phần tử), cũ nhất trước |

### 4.8. Uy tín đội (2.9)

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `teamId` | UUID | — |
| `reliabilityScore` | integer | 0..100 |
| `reliabilityLevel` | string | §4.1 |
| `completion.completedMatches` | integer | Tử số §1.4 |
| `completion.countedMatches` | integer | Mẫu số §1.4 |
| `completion.completionRate` | integer \| null | Phần trăm (làm tròn); `null` khi `countedMatches < 5` |
| `completion.isNewcomer` | boolean | `countedMatches < 5` |
| `rating.averageRating` | number \| null | Sao trung bình, 1 chữ số thập phân (làm tròn); `null` khi chưa có sao nào |
| `rating.ratingCount` | integer | Số đánh giá có sao (không tính "không đến") |
| `verification.isVerified` | boolean | Có huy hiệu xác minh (§1.5) |
| `verification.verifiedAt` | string \| null | Thời điểm cấp; `null` khi không có huy hiệu |

## 5. Mã lỗi

Mã mới của REPUTATION-001:

| Mã | HTTP | Message | Endpoint |
|---|---|---|---|
| `RATING_001` | 422 | "Chỉ đánh giá được trận đã kết thúc" | 2.1 |
| `RATING_002` | 409 | "Bạn đã đánh giá trận này" | 2.1 |
| `RATING_003` | 422 | "Bạn là đội trưởng của cả hai đội, không thể đánh giá trận này" | 2.1 |
| `REPORT_001` | 404 | "Không tìm thấy report" | 2.5 |
| `REPORT_002` | 422 | "Chỉ report được trận đã kết thúc" | 2.3 |
| `REPORT_003` | 422 | "Kèo đã huỷ chỉ report được giao tiếp tệ" | 2.3 |
| `REPORT_004` | 409 | "Bạn đã report đội này với loại này trong trận" | 2.3 |
| `REPORT_005` | 422 | "Bạn là đội trưởng của cả hai đội, không thể report trận này" | 2.3 |
| `REPORT_006` | 422 | "Chỉ tranh chấp được report bùng" | 2.5 |
| `REPORT_007` | 422 | "Đã quá hạn 24 giờ để tranh chấp" | 2.5 |
| `REPORT_008` | 409 | "Report đã được tranh chấp hoặc đã có quyết định" | 2.5 |
| `DISPUTE_001` | 404 | "Không tìm thấy tranh chấp" | 2.7, 2.8 |
| `DISPUTE_002` | 409 | "Tranh chấp đã được xử lý" | 2.8 |

Mã dùng lại:

| Mã | HTTP | Message | Nguồn | Endpoint |
|---|---|---|---|---|
| `AUTH_001` | 401 | "Token không hợp lệ hoặc đã hết hạn" | USER-001 | Mọi endpoint |
| `AUTH_004` | 403 | "Cần quyền quản trị" | USER-001 | 2.6–2.8 |
| `TEAM_001` | 404 | "Không tìm thấy đội" | USER-001 | 2.2, 2.4, 2.9 |
| `TEAM_002` | 403 | "Chỉ đội trưởng được thực hiện" | USER-001 | 2.1–2.5 |
| `TEAM_015` | 422 | "Đội đang tạm ngưng hoạt động" | USER-001 | 2.2, 2.4, 2.5; đội caller ở 2.1/2.3 |
| `TEAM_016` | 422 | "Đội đã giải tán" | USER-002 | 2.2, 2.4, 2.5 |
| `MATCH_001` | 404 | "Không tìm thấy kèo" | MATCH-001 | 2.1–2.3 |
| `MATCH_009` | 403 | "Đội không có quyền với kèo này" | MATCH-001 | 2.2 |
| `MATCH_014` | 409 | "Dữ liệu vừa được cập nhật bởi thao tác khác, vui lòng thử lại" | MATCH-001 | 2.1, 2.3, 2.5, 2.8 |

## 6. Danh sách [cần xác nhận]

Không còn điểm mở ảnh hưởng hợp đồng hay hành vi API (user chốt 2026-11-12: §1.3 chuỗi trận sạch khi "không đến" và khi Admin bác report; §1.4 tử số / mẫu số; §1.5 độ trễ huy hiệu).
