# CHANGELOG — VENUE-001 Quản lý địa điểm sân

## [v5.0] — 2026-11-21

**Type:** MAJOR (đổi định dạng mọi giá trị thời gian của response: bỏ `Z`/offset, giá trị theo giờ Việt Nam; đổi `validationErrors[].field` của lỗi đọc body) · **Tóm tắt:** Thời gian theo giờ Việt Nam, không kèm múi giờ; lỗi body sai kiểu trả đúng tên field (SYSTEM-002).

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | `API_Spec.md` §1 (dòng "Thời gian"), §4.5 (`expiresAt` của chữ ký upload), mọi field `…At` của 2.1–2.3, 2.5–2.13 | Đổi | Chuỗi ISO-8601 UTC (vd `"2026-11-09T03:00:00Z"`) → giờ Việt Nam, **không** kèm múi giờ (vd `"2026-11-09T10:00:00"`); `expiresAt` của 2.4 là hạn upload theo giờ Việt Nam | SRS SYSTEM-001 v1.13 BC-M05 (user chốt 2026-11-21); SYSTEM-002 `API_Spec.md` §1.1 | Đọc thời gian theo SYSTEM-002 `API_Spec.md` §1.1: hiển thị nguyên giá trị, không đổi theo múi giờ máy; khi so với giờ hiện tại coi là UTC+7. Phát hành bộ đọc nhận cả hai dạng **trước** khi BE triển khai (SYSTEM-002 §6) |
| 2 | Ví dụ JSON | Sửa | Giá trị `…Z` đổi sang cùng thời điểm theo giờ Việt Nam (cộng 7 giờ, bỏ `Z`) | Như #1 | Không |
| 3 | Lỗi `400 VALIDATION_ERROR` khi body JSON không đọc được (mọi endpoint có body) | Đổi | `validationErrors[0].field` luôn là `"body"` → field sai kiểu / sai định dạng trả đường dẫn field (vd 2.1 `latitude` gửi chuỗi chữ thay vì số → `"latitude"`; `venueType` sai giá trị vẫn trả `"venueType"` như trước); chỉ JSON sai cú pháp / thiếu body còn `"body"` | SYSTEM-002 `API_Spec.md` §2 | Gắn lỗi vào ô theo `field`; giữ nhánh `"body"` cho lỗi chung |
| 4 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Như #1 | Không |
| 5 | Header 3 tài liệu thiết kế VENUE-001 | Ghi nhận | v4.3 → v5.0 | Đồng bộ phiên bản | Không |

### Ảnh hưởng
- Không đổi endpoint, field, mã lỗi; đổi định dạng giá trị thời gian và giá trị `validationErrors[].field` của lỗi đọc body. Endpoint đang chạy: response đổi khi BE triển khai SYSTEM-002.
- `timestamp` của 2.4 (giây Unix, gửi nguyên cho Cloudinary) không đổi.
- Cùng đợt: SYSTEM-002 `API_Spec.md` v1.0 (quy ước thời gian toàn hệ thống, thứ tự phát hành BE/FE).

## [v4.3] — 2026-11-21

**Type:** MINOR (không đổi endpoint, field, mã thành công, mã lỗi, message) · **Tóm tắt:** Ghi kết quả kiểm thử upload ảnh trên Cloudinary thật vào hướng dẫn upload và §6; tìm kiếm 2.2 khớp đúng mô tả với ký tự `%`, `_`, `\`; cập nhật tài liệu nội bộ BE sau khi implement.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | §2 "Luồng thêm ảnh" (Trước khi bắt đầu; đoạn lỗi sau Bước 3) · §6 #9 → #19 | Chốt `[cần xác nhận]` | "Cloudinary có chặn định dạng ngoài `allowedFormats` hay không chưa chạy thử" → Cloudinary từ chối file ngoài `allowedFormats` (lỗi ở Bước 2); dùng lại chữ ký cho file khác thì Cloudinary không báo lỗi mà trả lại ảnh cũ, 2.5 trả `409 VENUE_006` (hoặc lưu ảnh cũ nếu ảnh cũ chưa được lưu); §6 #9 đổi số thành #19, chỉ còn giới hạn dung lượng file (Không chặn) | Đã kiểm thử trên Cloudinary thật | Tự kiểm định dạng trước khi upload để báo lỗi sớm; file sai định dạng bị từ chối ở Bước 2 → chọn file hợp lệ, xin chữ ký mới. **Mỗi file xin một chữ ký mới**, không dùng lại chữ ký cho file khác |
| 2 | 2.2 tham số `q` | Sửa lỗi | `%`, `_`, `\` trong `q` nay được tìm đúng là ký tự thường, như mô tả "khớp một phần" | Khớp mô tả 2.2 | Không |
| 3 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Đồng bộ với code sau khi implement | Không |
| 4 | Header 3 tài liệu | Ghi nhận | v4.2 → v4.3 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract VENUE-001: không đổi.

## [v4.2] — 2026-11-16

**Type:** MINOR (không đổi endpoint, field, mã thành công, mã lỗi, message) · **Tóm tắt:** ADR-0009 đã `Accepted` (2026-11-16) — bỏ nhãn "Proposed" ở header API_Spec. Cập nhật tài liệu nội bộ BE theo phạm vi thông báo NOTIFICATION-001: Admin chỉ nhận thông báo theo đợt cảnh báo địa điểm, không theo từng report; không ảnh hưởng FE.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | User chốt 2026-11-16 (NOTIFICATION-001 Q8) | Không — thông báo cho Admin: NOTIFICATION-001 `API_Spec.md` §4.2 `VENUE_ADMIN_ALERT` |
| 2 | `API_Spec.md` header "Quyết định liên quan" | Sửa | "ADR-0009 (`Proposed`)" → "ADR-0009" | ADR-0009 `Accepted` 2026-11-16 | Không |
| 3 | Header 3 tài liệu | Ghi nhận | v4.1 → v4.2 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract VENUE-001: không đổi.
- Nghiệp vụ / SRS: UC-V03 bước 6, AC-V03 ("Admin nhận notification" mỗi report) cần sửa theo NOTIFICATION-001 — business-analyst.

## [v4.1] — 2026-11-15

**Type:** MINOR (không đổi endpoint, field, mã thành công, mã lỗi, message) · **Tóm tắt:** Cập nhật tài liệu nội bộ BE: ghi chú FK `venue_report.reporter_id` trỏ chính sách cùng loại sang `conversation_message.sender_user_id` ở V6 (MATCH-001 v2.4 bỏ bảng `chat_message`), không ảnh hưởng FE.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE (`chat_message` ở V7 → `conversation_message` ở V6), không ảnh hưởng FE | MATCH-001 v2.4 / MATCH-002 v2.0: mọi chat dùng `conversation_message` | Không |
| 2 | Header 3 tài liệu | Ghi nhận | v4.0 → v4.1 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract: không đổi.

## [v4.0] — 2026-11-14

**Type:** MAJOR (2.1 thêm field bắt buộc `wardId` — request cũ không gửi sẽ bị `400`) · **Tóm tắt:** Địa điểm có phường/xã bắt buộc khi thêm / sửa (BR-V11) để tìm kèo mercenary không GPS và hiện khu vực "phường, tỉnh" của sân (MERCENARY-001).

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | 2.1 Request | Thêm (bắt buộc) | — → `wardId` (phường/xã từ danh mục đơn vị hành chính) | BR-V11 (user chốt 2026-11-14) | Thêm ô chọn phường/xã (dùng danh mục như hồ sơ USER-001) ở màn "Thêm địa điểm"; gửi `wardId` |
| 2 | 2.12 Request | Thêm | — → `wardId` tuỳ chọn; **bắt buộc** khi địa điểm chưa có phường (`ward = null`); không xoá được phường đã có | BR-V11 | Màn "Sửa địa điểm": hiện ô phường/xã; nếu `ward = null` bắt chọn trước khi lưu |
| 3 | 2.1, 2.12 Lỗi; §5 | Thêm | — → `400 USER_002` "Khu vực không hợp lệ" (`wardId` sai); 2.12: `422 VENUE_016` "Địa điểm cần bổ sung phường/xã" | Như #1, #2 | Hiện message, cho chọn lại phường/xã |
| 4 | §4.2 Venue (2.1, 2.3, 2.12), §4.8 AdminVenue | Thêm | — → `ward` (`wardId`, `wardName`, `provinceId`, `provinceName`) \| `null` | Hiển thị khu vực của sân | Hiển thị "phường, tỉnh"; `ward = null` (địa điểm tạo trước v4.0) → nhắc bổ sung |
| 4a | §1, §4.2 `lastSelectedAt` | Đổi nghĩa | "Lần gần nhất được chọn cho kèo" → thêm: lần khai báo / đổi sang địa điểm này cho trận ngoài hệ thống (MERCENARY-001 2.28, 2.31); cảnh báo admin "không được chọn 180 ngày" tính theo giá trị mới | BC-V05 (user chốt 2026-11-14) | Không đổi cách gọi; nhãn "lần được chọn gần nhất" gồm cả trận ngoài |
| 5 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE ngoài các mục trên | — | — |
| 6 | Header 3 tài liệu | Ghi nhận | v3.0 → v4.0 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract: 2.1 thêm field bắt buộc (phá vỡ), 2.12 thêm field có điều kiện + mã lỗi mới, response thêm `ward`.
- Nghiệp vụ / SRS: BR-V11, BC-V05 (`SYSTEM-001`, user chốt 2026-11-14). Địa điểm tạo trước chưa có phường vẫn hoạt động; không xuất hiện khi tìm kèo mercenary không GPS cho tới khi được bổ sung.

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
