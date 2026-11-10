# CHANGELOG — MATCH-002 Thương lượng ghép kèo luồng B (pairing) & chat

## [v1.1] — 2026-11-11

**Type:** MINOR (thêm field vào response, thêm mã lỗi `VENUE_002` cho 2.5 / 2.7, ghi rõ hành vi; không đổi endpoint, method, field đã có, mã thành công) · **Tóm tắt:** Sửa sau review thiết kế: chặn địa điểm đã ngừng hoạt động khi chốt / xác nhận, tin nhắn hiện người thực sự đã gửi, chi tiết thương lượng có đội trưởng hiện tại và hạn kèo.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | 2.5, 2.7, §1.2, §3, §5 | Thêm | — → `422 VENUE_002` "Địa điểm đã ngừng hoạt động": 2.5 khi chọn địa điểm đã ngừng hoạt động; 2.7 khi địa điểm của phương án đã chốt bị ngừng hoạt động sau khi chốt | Không ghép kèo vào địa điểm đã ngừng hoạt động, nhất quán kèo luồng A (MATCH-001 2.1) | Xử lý mã mới; ẩn "Xác nhận" khi `proposal.venueStatus = INACTIVE`; cho chốt lại địa điểm khác |
| 2 | §4.4 Tin nhắn — response 2.8, 2.9, frame `MESSAGE_CREATED` | Thêm | — → `senderUserId`, `senderName`, `senderAvatarUrl` (người thực sự đã gửi; tài khoản đã xoá → `"Người dùng đã xóa"`, ảnh `null`) | Phòng chat hiển thị "tên đội · tên người gửi"; tin cũ giữ người gửi lúc đó khi đội chuyển đội trưởng | Hiển thị `<tên đội từ 2.4 theo senderTeamId> · <senderName>` |
| 3 | §4.2 Thương lượng (chi tiết) — response 2.1, 2.4 | Thêm | — → `authorTeam.captainName`, `interestedTeam.captainName` (đội trưởng hiện tại; đã xoá → `"Người dùng đã xóa"`; đội giải tán → `null`) và `matchExpiresAt` | Đầu phòng chat hiện đội trưởng hiện tại; FE khoá thao tác khi kèo hết hạn mà không cần gọi thêm API | Hiển thị ở đầu phòng chat; dùng `matchExpiresAt` để khoá ô chat / nút |
| 4 | §1.4, 2.5 | Ghi rõ | "Chốt lại đúng phương án đang có → 200" → `revision` được kiểm trước: `revision` cũ → `409 PAIRING_006` kể cả khi phương án giống cũ; chỉ khi `revision` đúng và phương án giống cũ mới trả `200` không đổi | Câu cũ hiểu được hai cách | Khi nhận `PAIRING_006` luôn tải lại 2.4 |
| 5 | §1.6 | Ghi rõ | "Kết nối lại → gọi 2.9 `after`" → thứ tự: kết nối + `SUBSCRIBE` trước, rồi 2.9 `after` + 2.4, bỏ trùng theo `messageId`; tải lại trang mới nhất khi mở lại màn hình chat | Gọi 2.9 trước khi `SUBSCRIBE` có thể lỡ tin | Đổi thứ tự xử lý khi kết nối lại; bỏ trùng theo `messageId` |
| 6 | §1.6 | Ghi rõ | — → `SUBSCRIBE` phải đúng mẫu `/topic/pairings/{pairingId}` (wildcard → `ERROR`); header `selector` bị bỏ qua | Giới hạn kênh realtime | Không dùng wildcard / `selector`; lọc frame theo `type` ở FE |
| 7 | 2.3 | Thêm | "ví dụ như 2.2" → JSON ví dụ riêng (`role=ALL`, một thương lượng mỗi phía) | Mỗi endpoint có ví dụ response riêng | Không |
| 8 | 2.7 | Ghi rõ | — → hai bên bấm gần như cùng lúc: một bên nhận `NEGOTIATING`, bên kia `CONFIRMED` | Hành vi khi đồng thời | Không |
| 8a | §1.5, 2.7 (hoàn tất) | Ghi rõ | — → khi kèo ghép, thoả thuận chi phí + tiền cọc + khoản tự nhập của thương lượng được chép sang kèo; kèo hiển thị bản chép (MATCH-001 2.3, khoản tự nhập có `itemId` mới); thương lượng giữ nguyên thoả thuận của mình (2.4) | Kèo đã ghép hiển thị thoả thuận chi phí ở một chỗ (trên kèo) cho cả hai luồng | Sau `matchStatus = CONFIRMED` đọc thoả thuận ở MATCH-001 2.3; không so `itemId` giữa kèo và thương lượng |
| 9 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE ngoài các mục trên | — | — |
| 10 | Header `API_Spec.md` | Ghi nhận | v1.0 → v1.1 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract: thêm field response (tương thích ngược), thêm mã lỗi `VENUE_002` cho 2.5 / 2.7.
- Nghiệp vụ / SRS: chặn địa điểm đã ngừng hoạt động khi chốt / xác nhận; chat hiển thị tên đội + người gửi, đầu phòng hiển thị đội trưởng hiện tại — câu SRS `SYSTEM-001` liên quan được cập nhật riêng.
