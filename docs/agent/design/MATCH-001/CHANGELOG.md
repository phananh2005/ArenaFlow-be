# CHANGELOG — MATCH-001 Kèo luồng A (đã có sân) & vòng đời kèo

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
