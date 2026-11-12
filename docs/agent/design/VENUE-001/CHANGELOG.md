# CHANGELOG — VENUE-001 Quản lý địa điểm sân

## [v3.0] — 2026-11-12

**Type:** MAJOR (đổi shape response Admin: report Admin thêm `isHandledByDeleted`, `handledByName` của admin đã xoá tài khoản đổi sang tên thật; ngừng hoạt động / kích hoạt lại địa điểm đổi response từ Venue sang AdminVenue: thêm `isCreatedByDeleted`, `createdByName` không che) · **Tóm tắt:** Mọi endpoint Admin không che user đã xoá tài khoản — tên thật, mỗi field tên có cờ đã xoá; endpoint thường giữ nguyên.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | `API_Spec.md` §1 | Đổi | "Che ở mọi endpoint, ngoại lệ duy nhất `reporterName`" → endpoint thường vẫn che (kể cả khi Admin gọi 2.3, 2.12); **mọi** endpoint `/api/v1/admin/**` trả tên thật, mỗi field tên user có cờ `is…Deleted` | User chốt 2026-11-12 (ADR-0002 sửa đổi 2026-11-12, SYSTEM-001 v1.7 BC-U02) | Màn Admin: gắn nhãn "đã xoá" theo cờ, không so chuỗi `"Người dùng đã xóa"` |
| 2 | `API_Spec.md` 2.8, 2.9, §4.7 | Đổi | AdminVenueReport: thêm `isHandledByDeleted` (sau `handledByName`); `handledByName` không che; `reporterName` + `isReporterDeleted` giữ như v2.0 | Như #1 | Màn report Admin: gắn nhãn "đã xoá" cho người xử lý theo `isHandledByDeleted` |
| 3 | `API_Spec.md` 2.10, 2.11, §4.2, §4.8 (mới) | Đổi | Response Venue (§4.2) → AdminVenue (§4.8): cùng field + `isCreatedByDeleted` (sau `createdByName`); `createdByName` không che. Venue (§4.2) nay chỉ dùng cho 2.1, 2.3, 2.12 | Như #1; Venue dùng chung với endpoint thường nên tách đối tượng Admin | Sau ngừng hoạt động / kích hoạt lại: đọc `isCreatedByDeleted`; cập nhật kiểu response nếu FE khai báo chặt |
| 4 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Khớp hợp đồng mới | Không |
| 5 | Header tài liệu thiết kế VENUE-001 | Ghi nhận | v2.0 → v3.0; tham chiếu SRS SYSTEM-001 v1.6 → v1.7 | Đồng bộ phiên bản | Không |

### Ảnh hưởng
- Đổi hợp đồng: 2.8–2.11 (chỉ thêm field và đổi giá trị tên của user đã xoá; path, method, status code, mã lỗi giữ nguyên). Response Admin của mã này không có field ảnh user.
- Endpoint thường (2.1–2.7, 2.12, 2.13): giữ nguyên, vẫn `"Người dùng đã xóa"`.
- Mã khác cùng đổi: USER-001 v4.0, USER-002 v3.0. SRS SYSTEM-001 v1.7 cập nhật BC-U02, UC-U03, AC-U03.

## [v2.0] — 2026-11-11

**Type:** MAJOR (đổi shape response report Admin: thêm field `isReporterDeleted`; `reporterName` của người report đã xoá tài khoản đổi từ `"Người dùng đã xóa"` sang tên thật) · **Tóm tắt:** Admin xem tên thật người report đã xoá tài khoản kèm cờ để gắn nhãn "đã xoá"; tên người khác vẫn che.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | `API_Spec.md` §1, §2.8–2.9, §4.7 | Đổi | Người report đã xoá tài khoản: `reporterName` = `"Người dùng đã xóa"` → tên thật + `isReporterDeleted = true`; `handledByName`, `createdByName` vẫn che | Admin cần nhận diện người report và phân biệt tài khoản đã xoá (ADR-0002, ngoại lệ 2026-11-11) | Màn report Admin: hiện nhãn "đã xoá" khi `isReporterDeleted = true` |
| 2 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Khớp hợp đồng mới | Không |
| 3 | Header tài liệu thiết kế VENUE-001 | Ghi nhận | v1.3 → v2.0 | Đồng bộ phiên bản | Không |

### Ảnh hưởng
- Chỉ response report Admin (§2.8–2.9) đổi; response report không có avatar người report.
- SRS SYSTEM-001 v1.6 cập nhật BC-U02, UC-U03, AC-U03.

## [v1.3] — 2026-11-11

**Type:** MINOR (không đổi endpoint, method, field, kiểu, mã thành công, mã lỗi, message) · **Tóm tắt:** Cập nhật tài liệu nội bộ BE (ràng buộc dữ liệu người tạo / người báo cáo, ghi chú cho kèo luồng B), không ảnh hưởng FE.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Người tạo và người báo cáo địa điểm không bị mất khi xoá tài khoản (khớp chính sách xoá mềm); bỏ ghi chú lỗi thời về kèo luồng B | Không |
| 2 | Header tài liệu thiết kế VENUE-001 | Ghi nhận | v1.2 → v1.3 | Đồng bộ phiên bản | Không |

### Ảnh hưởng
- Hợp đồng API, nghiệp vụ, SRS: không đổi.

## [v1.2] — 2026-11-10

**Type:** MINOR (không đổi endpoint, method, field, kiểu, mã thành công, mã lỗi, message) · **Tóm tắt:** Cập nhật tài liệu nội bộ BE cho MATCH-002, không ảnh hưởng FE.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Kèo luồng B (MATCH-002) cần thông tin địa điểm khi tạo kèo | Không |
| 2 | Header tài liệu thiết kế VENUE-001 | Ghi nhận | v1.1 → v1.2 | Đồng bộ phiên bản | Không |

### Ảnh hưởng
- Hợp đồng API, nghiệp vụ, SRS: không đổi.

## [v1.1] — 2026-11-10

**Type:** MINOR (không đổi endpoint, method, field, kiểu, mã thành công, mã lỗi, message) · **Tóm tắt:** Viết lại `API_Spec.md` để FE đọc độc lập, không đổi hợp đồng.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | `API_Spec.md` §2 | Sửa | Bảng endpoint + mục DTO riêng → mỗi endpoint có Dùng khi, Ai gọi được, bảng field, JSON ví dụ request/response, bảng lỗi "Khi nào / FE nên làm gì" | FE gọi được API chỉ với tài liệu này | Không phải sửa code; dùng bảng lỗi để xử lý từng mã |
| 2 | `API_Spec.md` mục "Luồng thêm ảnh (2.4 → Cloudinary → 2.5)" | Thêm | — → 3 bước (xin chữ ký → upload thẳng lên Cloudinary → lưu ảnh); bảng field multipart gửi Cloudinary lấy từ response 2.4; `timestamp` gửi dạng số nguyên thập phân giữ nguyên giá trị, `overwrite` gửi đúng chuỗi `false`; không gửi thêm tham số; hạn upload `expiresAt` (1 giờ sau `timestamp`), hạn lưu ảnh 6 giờ; xử lý lỗi upload | Ghi rõ luồng đã có (ADR-0009), không thêm hành vi | Đối chiếu code upload với bảng field; gửi thêm tham số hoặc sai dạng giá trị → Cloudinary từ chối |
| 3 | `API_Spec.md` §1 | Sửa | `400 VALIDATION_ERROR` khi id trên path không phải UUID ghi riêng ở một số endpoint (thiếu ở 2.4, 2.10, 2.11) → ghi một lần trong quy ước chung, áp cho mọi endpoint | Hành vi không đổi, chỉ gom về một chỗ | Không |
| 4 | `API_Spec.md` §1, bảng lỗi từng endpoint | Sửa | Bảng thứ tự kiểm riêng → quy tắc "request vi phạm nhiều điều kiện thì nhận lỗi đầu tiên": `AUTH_*` → `VALIDATION_ERROR` → bảng lỗi của endpoint từ trên xuống | Phần FE cần là biết sẽ nhận mã nào | Xử lý theo mã nhận được |
| 5 | `API_Spec.md` §1, §5 | Thêm | — → FE nên làm gì với `403 AUTH_004` (ẩn chức năng quản trị); `401 AUTH_001` và `SYS_*` trỏ USER-001 `API_Spec.md` §1.2 "Lỗi chung"; header trỏ USER-001 §1.1 "Quy ước chung", §1.2 "Lỗi chung" | FE cần biết xử lý mã lỗi dùng chung | Áp xử lý chung cho mọi endpoint của mã này |
| 6 | `API_Spec.md` §2.2 | Sửa | Ghi thêm: có `lat`/`lng` → chỉ trả địa điểm trong bán kính `radiusKm`; lỗi thiếu cặp toạ độ có `validationErrors[].field` = `lat`, gửi `radiusKm` khi thiếu toạ độ có `field` = `radiusKm` | Hành vi đã có, trước chưa ghi | Gắn lỗi vào đúng ô bộ lọc |
| 7 | `API_Spec.md` §4.2, §4.7 | Sửa | "Che tên theo quy tắc USER-001" → ghi thẳng: `createdByName`, `reporterName`, `handledByName` = `"Người dùng đã xóa"` khi tài khoản đó đã xoá | Đọc được không cần mở tài liệu khác | Hiển thị nguyên chuỗi; không coi là lỗi |
| 8 | `API_Spec.md` §2.10, §2.11 | Sửa | Ghi thêm: 2.10 đặt `adminVerifiedInactiveAt`, report `CLOSED` đang `PENDING` thành `VERIFIED` với người xử lý là admin gọi; 2.11 đặt `adminVerifiedInactiveAt = null`, không đổi report | Hành vi đã có, trước chưa ghi | Sau 2.10 tải lại danh sách report nếu đang mở |
| 9 | `API_Spec.md` §2.13 | Sửa | Ghi thêm: URL ảnh đã xoá có thể vẫn mở được thêm một thời gian | Hành vi đã có, trước chưa ghi | Bỏ ảnh khỏi giao diện ngay theo response, không kiểm bằng cách mở URL |
| 10 | `API_Spec.md` §2.5 | Sửa | Ghi thêm: response chỉ chứa ảnh mới (ảnh chính cũ đổi cờ không trả về); gửi lại khi lỗi mạng: `409 VENUE_006` = lần trước đã lưu, ảnh thứ 5 có thể nhận `422 VENUE_003` dù đã lưu | Hành vi đã có, trước chưa ghi | Cập nhật `isPrimary` ảnh cũ hoặc tải lại 2.3; khi gửi lại gặp hai mã trên → tải lại 2.3 |
| 11 | `API_Spec.md` §3 (mới) | Thêm | — → bảng hành động theo trạng thái địa điểm (`ACTIVE` / `INACTIVE` × người tạo / user khác / Admin) và report (`PENDING` / `VERIFIED` / `REJECTED`) | FE cần biết nút nào hiện theo trạng thái | Dùng để quyết định nút nào hiện |
| 12 | `API_Spec.md` §4 | Sửa | Danh sách field rời → bảng enum có ý nghĩa từng giá trị + bảng field từng kiểu dữ liệu (kiểu, `null` khi nào) | Dễ tra | Không |
| 13 | `API_Spec.md` §6 | Sửa | Bỏ #1, #2 (không ảnh hưởng FE); thêm #9 phần FE: giới hạn dung lượng file và việc Cloudinary chặn định dạng ngoài `allowedFormats` / chặn ghi đè chưa chạy thử | Chỉ giữ điểm chưa chốt mà FE cần biết | Tự kiểm định dạng file (`jpg`, `jpeg`, `png`, `webp`) trước khi upload |
| 14 | `API_Spec.md` ví dụ response 2.4 | Sửa | `timestamp` 1731150000 không khớp `expiresAt` → `timestamp` 1794193200 (= 2026-11-09T03:00:00Z), `expiresAt` 2026-11-09T04:00:00Z | Ví dụ nhất quán | Không |
| 15 | `API_Spec.md` toàn bộ | Xoá | Chi tiết cài đặt phía BE → chỉ còn mô tả theo góc người gọi API | Tài liệu dành cho FE | Không |
| 16 | Header tài liệu thiết kế VENUE-001; tài liệu nội bộ BE | Ghi nhận | v1.0 → v1.1; cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Đồng bộ phiên bản; giữ phần giải thích nội bộ đã gỡ khỏi `API_Spec.md` | Không |

### Ảnh hưởng
- Hợp đồng API, nghiệp vụ, SRS: không đổi. FE không phải sửa phần gọi API; nên đối chiếu code upload ảnh với mục "Luồng thêm ảnh".
