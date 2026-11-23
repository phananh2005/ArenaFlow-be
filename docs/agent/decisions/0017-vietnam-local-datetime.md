# ADR-0017: Mọi thời điểm là giờ Việt Nam không kèm múi giờ — `LocalDateTime` / `TIMESTAMP`

| | |
|---|---|
| **Trạng thái** | Accepted (chủ repo chốt 2026-11-21) |
| **Ngày** | 2026-11-21 |
| **Người quyết định** | Chủ repo (chốt kiểu dữ liệu, giờ Việt Nam, JSON không offset, migration V6 — 2026-11-20/21; chốt ngoại lệ epoch, từ chối offset, thứ tự phát hành, số migration — 2026-11-21) · solution-architect (đề xuất cách thực hiện) |
| **Liên quan** | SRS `docs/agent/business-spec/SYSTEM-001/` v1.13 BC-M05 · `docs/agent/design/SYSTEM-002/` · [ADR-0011](0011-domain-events-after-commit.md) (`occurredAt` — Sửa đổi 2026-11-21) · [ADR-0013](0013-reputation-columns-atomic-update.md), [ADR-0014](0014-realtime-chat-stomp-websocket.md), [ADR-0016](0016-user-name-search-fold-tsvector-gin.md) (số migration) · [ADR-0001](0001-jwt-authentication-strategy.md), [ADR-0008](0008-uuid-v7-primary-keys.md), [ADR-0009](0009-cloudinary-signed-direct-upload.md) (giá trị epoch giữ nguyên) · Quyết định 3, 6 đề xuất thay bởi [ADR-0018](0018-jvm-time-zone-as-single-clock-source.md) (Proposed) |

## Bối cảnh (Context)
- SRS cũ: "lưu UTC trong DB, frontend đổi theo múi giờ thiết bị". Code đang dùng `OffsetDateTime` + cột `TIMESTAMPTZ`; response trộn `Z` (đọc qua projection) và `+07:00` (giá trị `OffsetDateTime.now()` theo JVM).
- User đổi nghiệp vụ (BC-M05, 2026-11-21): mọi thời điểm theo giờ Việt Nam, hiển thị nguyên giá trị trên mọi máy, mốc nghiệp vụ tính theo giờ Việt Nam, dữ liệu cũ giữ đúng thời điểm.
- Người dùng và nghiệp vụ chỉ ở Việt Nam; `Asia/Ho_Chi_Minh` cố định UTC+7, không giờ mùa hè.
- `convention-backend.md` hiện cấm `LocalDateTime` (lý do: phụ thuộc timezone JVM). Neon dev có TimeZone phiên `GMT` (user kiểm 2026-11-19) → biểu thức `now()` gán vào cột không múi giờ sẽ lệch 7 giờ.
- Các thiết kế chưa code (MATCH-001 … USER-003) đã giữ chỗ migration V6–V11.

## Quyết định (Decision)
Chúng tôi sẽ:

1. **Kiểu:** thời điểm = `LocalDateTime` (Java) / `TIMESTAMP` (DB), hiểu là giờ `Asia/Ho_Chi_Minh`; ngày lịch = `LocalDate` / `DATE`. Áp cho entity, projection, tham số repository, DTO, frame realtime, domain event, `ErrorResponse`.
2. **JSON:** ISO-8601 không offset (`"2026-11-20T19:00:00"`, có thể kèm micro giây) — mặc định Jackson, không cấu hình định dạng. Request kèm `Z`/offset bị từ chối `400 VALIDATION_ERROR` .
3. **"Bây giờ" có zone tường minh:** bean `Clock` = `Clock.tick(Clock.system(Asia/Ho_Chi_Minh), 1 µs)` cho service/job; `AppTime.now()` (cùng zone, cắt micro giây) cho entity callback và nơi không inject được. Cấm `LocalDateTime.now()` không tham số. JVM vẫn chạy `-Duser.timezone=Asia/Ho_Chi_Minh` (log, phiên JDBC) nhưng code không phụ thuộc vào nó.
4. **SQL không dùng `now()` trần** cho cột thời gian; Java truyền `:now`. DEFAULT = `(now() AT TIME ZONE 'Asia/Ho_Chi_Minh')` — không phụ thuộc TimeZone phiên.
5. **Migration V6** (giữ V1–V5): mỗi bảng một `ALTER TABLE` đổi mọi cột `TIMESTAMPTZ` sang `TIMESTAMP USING col AT TIME ZONE 'Asia/Ho_Chi_Minh'` và đổi DEFAULT — 51 cột / 22 bảng. Migration đã thiết kế của các mã khác dời +1 (V6–V11 → V7–V12; số thực chốt lại khi bắt đầu code từng mã).
6. **`@Scheduled` cron** khai `zone = "Asia/Ho_Chi_Minh"` (hằng `AppTime.ZONE_ID`).
7. **Ngoại lệ epoch** (không lưu DB, không trả dạng datetime): claim JWT `iat`/`exp`, tham số ký Cloudinary `timestamp`/`signedAt` và phép so tuổi asset, bit thời gian của UUID v7 — giữ `Instant`/epoch nội bộ.

**Phương án đã loại**
- Giữ `TIMESTAMPTZ` + `OffsetDateTime`, chỉ ghi JSON theo `+07:00`: vẫn có offset trong JSON, trái quyết định của user; FE vẫn có thể đổi theo múi giờ máy.
- Giữ `TIMESTAMPTZ`, dùng `LocalDateTime` trong Java: Hibernate/pgjdbc đổi theo TimeZone phiên ở mỗi lần đọc/ghi — kết quả phụ thuộc cấu hình kết nối, không thấy được trong schema.
- Đặt `ALTER DATABASE … SET timezone = 'Asia/Ho_Chi_Minh'` thay cho DEFAULT tường minh: cấu hình nằm ngoài migration, không theo môi trường mới / Neon branch mới; phiên công cụ khác vẫn có thể ghi đè.
- Dựa vào TimeZone phiên do pgjdbc gửi theo JVM: đúng cho ứng dụng nhưng sai cho console/MCP/`psql` và migration chạy từ công cụ khác.
- Sửa V1–V5: đổi checksum Flyway trên DB đã migrate.
- `LocalDateTime.now()` theo JVM: sai lặng lẽ khi JVM chạy timezone khác (IDE, runner, container quên `TZ`).

## Hệ quả (Consequences)

**Tích cực**
- Giá trị ở DB, log, JSON và màn hình là cùng một con số giờ Việt Nam; FE hiển thị nguyên giá trị.
- Không còn trộn `Z` / `+07:00` trong response (đóng tech-debt USER-001 "chuẩn hoá thời gian").
- Mốc nghiệp vụ và cron theo giờ Việt Nam tường minh; test cố định `Clock` được.

**Tiêu cực / đánh đổi**
- Đổi hợp đồng mọi API có thời điểm (MAJOR). Hiện chỉ có môi trường dev: FE dev đổi theo ngay khi BE merge. **Khi có production:** FE phát hành bộ đọc nhận cả hai dạng trước, BE deploy sau; tạo điểm khôi phục Neon trước deploy production, xin phép mỗi lần.
- DB không còn tự mang nghĩa tuyệt đối: dữ liệu chỉ đúng khi mọi bên hiểu là giờ Việt Nam; ghi tay SQL phải dùng `now() AT TIME ZONE 'Asia/Ho_Chi_Minh'`.
- V6 viết lại 22 bảng dưới khoá `ACCESS EXCLUSIVE`; không có migration ngược tự động.
- Áp V6 lên DB dev dùng chung làm các nhánh code cũ không khởi động được trên DB dev tới khi rebase → SYSTEM-002 code/test trên Neon branch riêng; DB dev chung chỉ nhận V6 khi PR SYSTEM-002 merge, báo trước các nhánh đang mở. Render dev (chạy trên DB dev chung, code cũ) phải rebase nhánh deploy và deploy lại ngay sau khi V6 áp.
- Phải sửa quy ước `convention-backend.md`, `api-convention.md` (nhánh framework) và tài liệu thiết kế mọi mã.

**Cần theo dõi**
- Có người dùng / nghiệp vụ ngoài Việt Nam, hoặc Việt Nam áp dụng giờ mùa hè → xem lại (cần lưu thời điểm tuyệt đối).
- Tích hợp ngoài yêu cầu thời điểm UTC (push, thanh toán, lịch) → đổi tại biên tích hợp bằng `AppTime.ZONE`, không đổi lưu trữ.

## Sửa đổi đề xuất cho ADR khác (áp khi ADR này `Accepted` **và** PR #75 đã merge)

Đã áp 2026-11-21 cùng PR chuyển ADR này sang `Accepted`: ADR-0011, ADR-0013, ADR-0014 có mục `Sửa đổi 2026-11-21 — SYSTEM-002`.

| ADR | Trạng thái | Đề xuất |
|---|---|---|
| ADR-0011 | Accepted | Thêm vào hàng `Sửa đổi` + mục `## Sửa đổi <ngày> — SYSTEM-002: occurredAt theo giờ Việt Nam` **nối sau** mục `Sửa đổi 2026-11-21 — VENUE-001: event theo package con của miền` mà PR #75 thêm (đọc bản trên `main` trước khi viết; event địa điểm nay ở `application/event/venue/`): quy tắc 2 "thời điểm (`occurredAt`, UTC)" đọc là `occurredAt` kiểu `LocalDateTime` giờ Việt Nam (ADR-0017), áp cho mọi event kể cả `VenueAdminAlertEvent.triggeredAt`; không đổi các quy tắc khác |
| ADR-0013 | Accepted | Mục Sửa đổi: số migration REPUTATION-001 V8 → V9 (V6 thuộc SYSTEM-002) |
| ADR-0014 | Accepted | Mục Sửa đổi: số migration V6/V7/V9 → V7/V8/V10; cột thời gian của bảng chat là `TIMESTAMP` giờ Việt Nam |
| ADR-0016 | Proposed | Đã sửa trực tiếp: V11 → V12, V5–V10 → V5–V11 |
| ADR-0001, ADR-0008, ADR-0009 | Accepted | Không sửa: `iat`/`exp`, bit thời gian UUID v7, `timestamp`/`signedAt` của Cloudinary là epoch — ngoại lệ ở Quyết định 7 |
