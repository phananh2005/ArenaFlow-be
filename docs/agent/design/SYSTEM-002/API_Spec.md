# API Specification — Ngày giờ theo giờ Việt Nam, không kèm múi giờ

<!-- File này được publish cho FE (ADR-0010). Viết theo design-standards.md §11:
     không tên class/bảng/cột/migration/transaction/khoá/số truy vấn; không trỏ Component_Class_Design.md,
     Processing_Flow.md, docs/database/. Chi tiết nội bộ BE ghi ở hai file đó. -->

| Mục | Giá trị |
|---|---|
| Mã chức năng | `SYSTEM-002` |
| Phạm vi tầng | Backend (Spring Boot REST API). Frontend: ngoài phạm vi repo này |
| Repo | ArenaFlow-be |
| Quy ước áp dụng | `api-convention.md` (envelope, định dạng lỗi, kiểu dữ liệu); [ADR-0017](../../decisions/0017-vietnam-local-datetime.md) |
| Spec nghiệp vụ | `docs/agent/business-spec/SYSTEM-001/` (v1.13) — BC-M05 (Business_Rules §4), Overview §ràng buộc |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-21 · v1.0 (baseline) · hiện tại **v1.1** (solution-architect · 2026-11-21) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan

Mọi thời điểm trong ArenaFlow là **giờ Việt Nam (UTC+7)** và được gửi/nhận **không kèm múi giờ**. Kèo 19:00 ngày 20/11 là `"2026-11-20T19:00:00"` trong mọi request và response, và app hiển thị 19:00 trên mọi máy, kể cả máy đặt múi giờ khác (BC-M05).

Thay đổi này áp cho **mọi endpoint** của mọi mã chức năng, kể cả các mã chưa có code. Mã này không thêm endpoint, không thêm mã lỗi. Dữ liệu đã có vẫn chỉ đúng thời điểm cũ, chỉ đổi cách ghi: `"2026-11-20T12:00:00Z"` cũ nay trả `"2026-11-20T19:00:00"`.

### 1.1. Quy ước định dạng thời gian (thay quy ước cũ "ISO-8601 kèm offset, trả UTC")

| Kiểu | Định dạng | Ví dụ | Ghi chú |
|---|---|---|---|
| `datetime` | `YYYY-MM-DDTHH:mm:ss` theo giờ Việt Nam, **không** có `Z`, **không** có `+07:00` | `"2026-11-20T19:00:00"` | Giây luôn có. Response có thể có phần lẻ giây **1–6 chữ số, không có số 0 ở cuối** (vd `"…19:00:00.5"`, `"…19:00:00.12"`, `"…19:00:00.123456"`); giây tròn thì không có phần lẻ. FE phải đọc được cả hai dạng |
| `date` | `YYYY-MM-DD` (ngày lịch, không có giờ) | `"1995-05-15"` | Không đổi so với trước (ngày sinh) |

**Request** (body và query param):
- Gửi `datetime` theo đúng định dạng trên, giờ Việt Nam. Có thể bỏ giây (`"2026-11-20T19:00"`) hoặc gửi phần lẻ giây.
- Gửi kèm `Z` hoặc offset (vd `"2026-11-20T12:00:00Z"`, `"2026-11-20T19:00:00+07:00"`) → `400 VALIDATION_ERROR`, `validationErrors[].field` là đường dẫn field đó (vd `startsAt`, `timeOptions[0].startsAt`).

**Response:**
- Mọi field `…At`, `startsAt`, `endsAt`, `expiresAt`… và `error.timestamp` trả `datetime` theo §1.1.
- Mốc nghiệp vụ (hết hạn bài đăng, 24 giờ, 180 ngày, nhắc trước trận, xoá chat sau 90 ngày, ẩn thông báo sau 30 ngày…) BE tính theo giờ Việt Nam. FE không tự tính lại theo múi giờ của máy.

**FE nên làm gì:**
- **Hiển thị:** in nguyên giờ/ngày trong chuỗi, không đổi theo múi giờ máy. Không dùng `new Date("2026-11-20T19:00:00")` rồi định dạng theo giờ máy để hiển thị khi máy có thể ở múi giờ khác.
- **So với "bây giờ"** (đếm ngược, "còn 2 giờ", ẩn nút khi quá giờ): coi chuỗi là giờ UTC+7, vd ghép `"+07:00"` trước khi đổi sang thời điểm tuyệt đối để so với đồng hồ máy.
- **Gửi lên:** lấy giờ người dùng chọn trên màn hình (giờ Việt Nam), ghi theo định dạng §1.1, không đổi sang UTC.

### 1.2. Giá trị không đổi (không phải `datetime`)

Giá trị dạng giây Unix dưới đây là chuẩn của Cloudinary / JWT, không phải giá trị hiển thị, nên giữ nguyên.

| Field | Endpoint | Kiểu | Lý do giữ nguyên |
|---|---|---|---|
| `expiresIn` | USER-001 `API_Spec.md` 2.1, 2.3, 2.4 (đăng nhập, refresh); 2.2 (OTP) | integer (giây) | Là khoảng thời gian, không phải thời điểm |
| `timestamp` | VENUE-001 `API_Spec.md` 2.4 (chữ ký upload ảnh) | integer (giây Unix) | Tham số Cloudinary, FE gửi nguyên cho Cloudinary |
| `iat`, `exp` trong access/refresh token | USER-001 | giây Unix (chuẩn JWT) | FE không cần đọc token; nếu đọc thì vẫn là giây Unix |

## 2. Endpoints

Không có endpoint mới. Các endpoint đang chạy có field `datetime` đổi định dạng theo §1.1:

| # | Mã · Endpoint (theo `API_Spec.md` của mã đó) | Field đổi định dạng |
|---|---|---|
| 2.1 | Mọi endpoint, mọi response lỗi | `error.timestamp` |
| 2.2 | USER-001 2.5, 2.6 (hồ sơ) | `createdAt`, `updatedAt` (`dateOfBirth` không đổi) |
| 2.3 | USER-001 2.8, 2.9, 2.10, 2.11, 2.19, 2.20, 2.21 · USER-002 2.1, 2.2, 2.3, 2.6 (đội, giải tán đội, danh sách đội, đội Admin) | `createdAt`, `updatedAt`, `disbandedAt` |
| 2.4 | USER-001 2.12 (mời) | `invitedAt` |
| 2.5 | USER-001 2.13, 2.17 (thành viên) | `joinedAt` |
| 2.6 | USER-001 2.15, 2.16 (đội trưởng) | `transferredAt` |
| 2.7 | USER-002 2.4 (lịch sử thành viên Admin) | `joinedAt`, `leftAt` |
| 2.8 | VENUE-001 2.1, 2.2, 2.3, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12, 2.13 (địa điểm, ảnh, report) | `lastSelectedAt`, `adminVerifiedInactiveAt`, `createdAt`, `updatedAt`, `handledAt` |
| 2.9 | VENUE-001 2.4 (chữ ký upload) | `expiresAt` (hạn upload, giờ Việt Nam); `timestamp` không đổi (§1.2) |

Mã chưa có code (USER-003, MATCH-001, MATCH-002, REPUTATION-001, MERCENARY-001, NOTIFICATION-001): `API_Spec.md` của từng mã đã ghi ví dụ và quy ước theo §1.1 (xem `CHANGELOG.md` của mã đó). Field thời gian trong **request** của các mã này (vd `startsAt`, `endsAt`, `startsAtFrom`, `startsAtTo`) nhận theo §1.1.

Frame realtime (STOMP) có field thời gian (MATCH-001, MATCH-002, NOTIFICATION-001) theo cùng định dạng §1.1.

**Lỗi:** không thêm mã. `400 VALIDATION_ERROR` khi request gửi `datetime` sai định dạng hoặc kèm múi giờ (§1.1).

**`validationErrors[].field` khi body JSON không đọc được** (áp cho **mọi** endpoint có body, không riêng thời gian): field sai kiểu / sai định dạng (ngày giờ, số, enum, UUID) → `field` là đường dẫn field đó, phần tử mảng dạng `[i]` (vd `"dateOfBirth"`, `"startsAt"`, `"timeOptions[0].startsAt"`); JSON sai cú pháp hoặc thiếu body → `field = "body"`. Trước đây mọi lỗi đọc body đều trả `"body"`.

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Field `datetime` trong body/query kèm `Z`/offset hoặc sai định dạng; field khác sai kiểu trong body | Gắn lỗi vào ô theo `field`; với `datetime` sửa cách ghi chuỗi theo §1.1 (lỗi lập trình, không phải lỗi người dùng nhập) |

Ví dụ response lỗi:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Dữ liệu không hợp lệ",
    "details": null,
    "timestamp": "2026-11-21T10:15:30.123456",
    "path": "/api/v1/matches",
    "validationErrors": [ { "field": "startsAt", "message": "…" } ]
  }
}
```

Ví dụ response trước/sau (USER-001 2.5 — chỉ phần thời gian):

```json
// Trước
{ "data": { "createdAt": "2026-11-01T10:00:00Z", "updatedAt": "2026-11-02T10:00:00Z" } }
// Sau (cùng thời điểm, ghi theo giờ Việt Nam)
{ "data": { "createdAt": "2026-11-01T17:00:00", "updatedAt": "2026-11-02T17:00:00" } }
```

## 3. Hành động theo trạng thái

Không áp dụng (mã này không có đối tượng có trạng thái).

## 4. Kiểu dữ liệu dùng chung

| Kiểu | Giá trị | Ý nghĩa |
|---|---|---|
| `datetime` | `YYYY-MM-DDTHH:mm[:ss[.ffffff]]` | Thời điểm theo giờ Việt Nam, không kèm múi giờ (§1.1). Mọi API_Spec ghi "string (thời gian)" / "datetime" hiểu theo kiểu này |
| `date` | `YYYY-MM-DD` | Ngày lịch |

## 5. Mã lỗi

Không có mã lỗi mới.

## 6. Triển khai — FE cần làm gì và khi nào

1. **Trước khi BE triển khai mã này:** FE cho bộ đọc thời gian nhận **cả hai** dạng: có offset (`Z`/`+07:00` — đổi sang giờ Việt Nam rồi hiển thị) và không offset (dùng nguyên giá trị). Hiển thị theo §1.1 "FE nên làm gì".
2. **Khi BE triển khai:** response chuyển hẳn sang dạng không offset; `validationErrors[].field` của lỗi đọc body đổi theo §2. Endpoint đang chạy hiện chưa có field `datetime` trong request, nên không có request cũ bị từ chối.
3. **Sau khi BE triển khai:** mọi request gửi `datetime` (các mã chưa code) theo §1.1; bỏ nhánh đọc dạng có offset khi không còn cần.

**Hiện chỉ có môi trường dev** (chưa có production): khi BE merge mã này, môi trường dev đổi định dạng ngay — FE dev cần sẵn bước 1. **Khi có production — FE phát hành trước, BE sau:** BE chỉ triển khai mã này khi FE đã phát hành bước 1, nên BE và FE không phải phát hành cùng lúc.

## 7. Danh sách [cần xác nhận]

Không còn. Đã chốt 2026-11-21 (user): request kèm `Z`/offset → `400 VALIDATION_ERROR` (§1.1); giá trị giây Unix giữ nguyên (§1.2); hiện chỉ có môi trường dev — **khi có production** FE phát hành trước, BE sau (§6).
