# CHANGELOG — MATCH-001 Kèo (luồng A, luồng B) & vòng đời kèo

## [v2.1] — 2026-11-11

**Type:** MINOR (không đổi endpoint, method, field, kiểu, mã thành công, mã lỗi, message) · **Tóm tắt:** Ghi rõ khoản tự nhập của kèo luồng B đã ghép là bản chép từ thương lượng; cập nhật tài liệu nội bộ BE cho MATCH-002.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | §4.2 `costAgreement` — response 2.1, 2.3 | Ghi rõ | Kèo B đã ghép: "thoả thuận của thương lượng đã chốt" → bản chép tại lúc ghép; `customItems[].itemId` là id mới, khác `itemId` trong thương lượng (MATCH-002 2.4) | Kèo đã ghép hiển thị thoả thuận chi phí ở một chỗ (trên kèo) cho cả hai luồng | Không so `itemId` giữa kèo và thương lượng |
| 1a | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | MATCH-002 hiển thị đội trưởng hiện tại của hai đội; kèo đọc khoản tự nhập theo kèo cho cả hai luồng | Không |
| 2 | Tiêu đề `CHANGELOG.md` | Sửa | "Kèo luồng A (đã có sân) & vòng đời kèo" → "Kèo (luồng A, luồng B) & vòng đời kèo" | Khớp phạm vi từ v2.0 và tiêu đề `API_Spec.md` | Không |
| 3 | Header tài liệu thiết kế MATCH-001 | Ghi nhận | v2.0 → v2.1 | Đồng bộ phiên bản | Không |

### Ảnh hưởng
- Hợp đồng API: không đổi field / mã; chỉ ghi rõ nguồn khoản tự nhập của kèo B đã ghép.
- Nghiệp vụ / SRS: khoản tự nhập chép sang kèo khi ghép — câu SRS liên quan cập nhật riêng (MATCH-002).

## [v2.0] — 2026-11-10

**Type:** MAJOR (response kèo đổi cấu trúc: field mới, `venue` và field địa điểm có thể `null`, `type` có thêm giá trị) · **Tóm tắt:** Thêm kèo luồng B (`NEED_VENUE` — chưa có sân) vào tạo / tìm / xem / huỷ kèo; ghép kèo luồng B theo MATCH-002.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | 2.1 request | Thêm | — → `type` (`HAS_VENUE` mặc định \| `NEED_VENUE`), `venueOptionIds` (1..5), `timeOptions` (1..5 `{startsAt, endsAt}`); `venueId`, `startsAt`, `endsAt` chỉ bắt buộc ở luồng A, không gửi ở luồng B; luồng B: `expiresAt` trong [khung giờ sớm nhất − 7 ngày, khung giờ sớm nhất] | UC-M02, AC-M02, BR-M03 (user chốt 2026-11-10: 1–5 lựa chọn, cận 7 ngày) | Luồng A không phải đổi (vắng `type` = `HAS_VENUE`). Màn hình "Chưa có sân" gửi theo ví dụ luồng B |
| 2 | §4.2 Kèo (chi tiết) — response 2.1, 2.3 | Sửa | `type` luôn `HAS_VENUE` → `HAS_VENUE` \| `NEED_VENUE`; `venue` luôn có → `null` ở kèo B chưa ghép; thêm `venueOptions`, `timeOptions` (`[]` ở kèo A), `confirmedPairingId`; `costAgreement` 5 → 7 field (thêm `depositAmount`, `depositHolder`, chỉ kèo B) | Hiển thị kèo B và phương án đã chốt | Xử lý `venue = null`; đọc thêm field mới; kèo B chưa ghép hiển thị danh sách lựa chọn |
| 3 | §4.3 Kèo rút gọn — 2.2, 2.11 | Sửa | Thêm `type`, `venueOptionCount`, `timeOptionCount` (`null` ở kèo A); `venueId`, `venueName`, `venueAddress` string → string \| null (`null` ở kèo B chưa ghép) | Danh sách có cả kèo B | Xử lý field địa điểm `null`; hiện "Nhiều lựa chọn" cho kèo B |
| 4 | 2.2 | Sửa | Chỉ kèo A → cả kèo A và B; thêm query `type`; kèo B khớp `startsAtFrom`/`startsAtTo` khi có một khung giờ thoả; `distanceKm` kèo B = tới lựa chọn địa điểm gần nhất | UC-M02 "hiển thị trong danh sách tìm kèo" | Lọc `type=HAS_VENUE` nếu màn hình chỉ muốn kèo A |
| 5 | §1.3, §4.2 | Ghi rõ | Kèo B: `startsAt`/`endsAt` = khung giờ sớm nhất khi chưa ghép, khung giờ đã chốt khi đã ghép | Kèo B chưa có một giờ cố định | Hiển thị theo `type` |
| 6 | 2.4, 2.6, 2.7 | Thêm | — → `422 MATCH_016` khi gọi trên kèo B (mã định nghĩa ở MATCH-002) | Kèo B ghép và nhập chi phí qua thương lượng | Ẩn các nút này trên kèo B; dùng MATCH-002 |
| 7 | 2.5 | Thêm | Huỷ kèo B `OPEN`: không cần đóng thương lượng trước, mọi thương lượng đang trao đổi → `SUPERSEDED`; `MATCH_010` chỉ áp cho kèo A. Huỷ kèo B `CONFIRMED`: như kèo A, đội đã ghép = đội nhận kèo | User chốt 2026-11-10 | Không đổi cách gọi |
| 8 | §3.1 | Thêm | Hàng `OPEN` (luồng B) và ghi chú cho kèo B | Bảng hành động theo trạng thái | Dùng để hiện nút cho kèo B |
| 9 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE ngoài các mục trên | — | — |
| 10 | Header `API_Spec.md` | Ghi nhận | v1.1 → v2.0 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract: đổi cấu trúc response 2.1, 2.2, 2.3, 2.11 (MAJOR); thêm field request 2.1 và mã lỗi `MATCH_016` (tương thích ngược với cách gọi luồng A).
- Nghiệp vụ / SRS: luồng B chốt 2026-11-10; câu SRS `SYSTEM-001` liên quan được cập nhật riêng.

## [v1.1] — 2026-11-10

**Type:** MINOR (không đổi path, method, field, kiểu, status, mã lỗi, message) · **Tóm tắt:** Viết lại `API_Spec.md` cho FE đọc độc lập, không đổi hợp đồng.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | `API_Spec.md` toàn bộ | Sửa | Bố cục gọn v1.0 → mỗi endpoint có "Dùng khi · Ai gọi được", bảng field, JSON ví dụ request/response, bảng lỗi "Khi nào / FE nên làm gì" | Viết lại cho FE đọc độc lập (ADR-0010); thêm ví dụ JSON và hướng dẫn FE cho mã lỗi | Đọc lại theo bố cục mới; không phải sửa code đã gọi đúng v1.0 |
| 2 | `API_Spec.md` §1.3 | Thêm | Gom quy tắc: kèo `OPEN` quá `expiresAt` hiển thị `EXPIRED` ngay; kèo `CONFIRMED` chuyển `ARCHIVED` sau `endsAt`, có thể trễ vài phút (chu kỳ 5 phút) | Nêu rõ hành vi theo thời gian FE thấy được (BR-M17) | Ẩn nút thao tác khi quá `expiresAt` / từ `startsAt`; nhận `MATCH_004` → tải lại |
| 3 | `API_Spec.md` §1.4 | Thêm | Luồng `expectedStatus` ở huỷ kèo (2.5) và rút yêu cầu (2.10) | Thêm hướng dẫn FE cho `MATCH_015` (BR-M13) | Gửi trạng thái đang hiển thị; nhận `409 MATCH_015` → tải lại, không tự gửi lại |
| 4 | `API_Spec.md` §1.5 | Thêm | Bảng trừ điểm theo khoảng cách giờ; `reliabilityPenalty` là `0` \| `-2` \| `-5` \| `-10` | Gom quy tắc trừ điểm một chỗ (BR-M09..BR-M11) | Có thể hiện cảnh báo mức trừ trước khi xác nhận |
| 5 | `API_Spec.md` §3 | Thêm | Bảng hành động theo trạng thái kèo (× đội tạo kèo / đội đã ghép / đội khác) và trạng thái yêu cầu (× đội gửi / đội tạo kèo), kèm mã lỗi khi gọi sai trạng thái | Giúp FE biết nút nào hiện theo trạng thái | Dùng để quyết định nút nào hiện |
| 6 | `API_Spec.md` §4 | Sửa | Enum liệt kê đủ giá trị + ý nghĩa (gồm 9 nhãn trình độ); bảng field cho các object dùng chung | Liệt kê đủ giá trị enum và field cho FE | — |
| 7 | `API_Spec.md` §2.4, §4.5 | Ghi rõ | `itemId` khoản tự nhập được cấp mới mỗi lần lưu; `updatedAt` đổi mỗi lần lưu | Hành vi đã có ở v1.0, trước đây chưa ghi cho FE | Không giữ `itemId` cũ sau khi lưu |
| 8 | `API_Spec.md` §4.2 | Ghi rõ | `costAgreement` trong kèo chi tiết (2.1, 2.3) chỉ có 5 field (`costType`, `costSplit`, `venueAmount`, `drinksAmount`, `customItems`), không có `matchId`, `updatedAt` như response 2.4 (user chốt 2026-11-10) | User chốt 2026-11-10 (ví dụ v1.0 mơ hồ) | Không đọc `matchId`/`updatedAt` từ object lồng này |
| 9 | `API_Spec.md` §1.1 | Thêm | `401 AUTH_001` → gọi refresh token, thất bại thì đăng nhập lại | Thêm hướng dẫn FE cho `AUTH_001` | — |
| 10 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Tách chi tiết nội bộ BE khỏi tài liệu FE (ADR-0010) | — |
| 11 | Header `API_Spec.md` | Ghi nhận | v1.0 → v1.1 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract: không đổi. Mục 7, 8 ghi rõ hành vi đã có ở v1.0.
- Nghiệp vụ, SRS: không đổi.
