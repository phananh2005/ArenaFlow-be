# CHANGELOG — MATCH-002 Thương lượng ghép kèo luồng B (pairing) & chat

## [v2.1] — 2026-11-16

**Type:** MINOR (không đổi endpoint, field, mã thành công, mã lỗi, frame) · **Tóm tắt:** ADR-0014 đã `Accepted` (2026-11-16) — cập nhật tài liệu nội bộ BE, bỏ nhãn "Proposed" khi trỏ tới nó; không ảnh hưởng FE.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | ADR-0014 `Accepted` | Không |
| 2 | Header 3 tài liệu | Ghi nhận | v2.0 → v2.1 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract MATCH-002: không đổi.

## [v2.0] — 2026-11-15

**Type:** MAJOR (bỏ 3 endpoint chat theo thương lượng và topic `/topic/pairings/{pairingId}`; chat chuyển sang bộ endpoint dùng chung của MATCH-001 với path, body, shape tin nhắn, mã lỗi khác) · **Tóm tắt:** Chat giữa hai đội trở thành **cuộc trò chuyện** dùng chung cho luồng A (từ lúc gửi yêu cầu ghép kèo), luồng B (từ lúc nhận kèo) và chat mercenary, định nghĩa ở MATCH-001 (v2.4); tài liệu này chỉ giữ phần riêng của thương lượng. Hai đội đã ghép nhắn tiếp sau khi ghép, sau trận và khi kèo bị huỷ; đội không được ghép chỉ xem (SRS `SYSTEM-001` v1.10 — BR-N04, BR-N05).

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | 2.8 gửi tin | Bỏ | `POST /pairings/{pairingId}/messages` body `{teamId, content}` → dùng MATCH-001 2.16 `POST /conversations/{conversationId}/messages` body `{content}` (không gửi `teamId`) | Một bộ endpoint cho mọi chat (luồng A, B, mercenary) | Gọi MATCH-001 2.16 với `conversationId` của thương lượng; bỏ `teamId` |
| 2 | 2.9 lịch sử | Bỏ | `GET /pairings/{pairingId}/messages` → MATCH-001 2.17 `GET /conversations/{conversationId}/messages` (query `before` / `after` / `limit` giữ nguyên) | Như #1 | Đổi path |
| 3 | 2.10 đánh dấu đã đọc | Bỏ | `POST /pairings/{pairingId}/messages/read` body `{teamId}` → MATCH-001 2.18 `POST /conversations/{conversationId}/messages/read` không body | Như #1 | Đổi path, bỏ body |
| 4 | Đầu phòng chat | Đổi | `authorTeam` / `interestedTeam` của 2.4 → MATCH-001 2.19 `GET /conversations/{conversationId}` (hai bên, đội trưởng hiện tại, bên của người gọi, `canSend`) | Đầu phòng chat dùng chung; FE không tự suy "gửi được" từ trạng thái thương lượng | Dùng MATCH-001 2.19 cho đầu phòng và để khoá ô chat |
| 5 | Tin nhắn (cũ §4.4) | Đổi shape | `pairingId`, `senderTeamId` → `conversationId`, `senderPartyType` (`TEAM`), `senderPartyId` (= `teamId` đội gửi) — MATCH-001 §4.8 | Shape chung của cuộc trò chuyện | Lấy tên đội gửi từ `parties` của MATCH-001 2.19 theo `senderPartyId` |
| 6 | §1.6 kênh realtime | Đổi | `SUBSCRIBE /topic/pairings/{pairingId}` → `SUBSCRIBE /topic/conversations/{conversationId}` (kênh mô tả ở MATCH-001 §1.7); frame `PAIRING_UPDATED` giữ nội dung, đi trên topic cuộc trò chuyện của thương lượng; lỗi đăng ký `PAIRING_001` / `PAIRING_002` → `CHAT_002` / `CHAT_003` | Một topic cho mỗi cuộc trò chuyện | Đăng ký topic mới theo `conversationId`; xử lý mã lỗi mới |
| 7 | §1.3, §1.7, §3 | Đổi hành vi | Chat chỉ khi thương lượng `NEGOTIATING`; `CONFIRMED` chỉ xem → `CONFIRMED` **vẫn nhắn** (trước, trong, sau trận, kể cả kèo bị huỷ); `SUPERSEDED` / `EXPIRED` chỉ xem; trạng thái đội / bộ môn không chặn nhắn | BR-N05 | Khoá ô chat theo `canSend` (MATCH-001 2.19), không theo `status` thương lượng |
| 8 | Lỗi chat | Đổi mã | `PAIRING_005` (thương lượng đã kết thúc), `PAIRING_001`, `PAIRING_002`, `TEAM_002`, `TEAM_016` của chat → `CHAT_004` (chỉ còn xem), `CHAT_002`, `CHAT_003`, `USER_001` (MATCH-001 2.16–2.19); `CHAT_001` chuyển sang MATCH-001 | Mã chung của cuộc trò chuyện | Xử lý mã mới theo bảng lỗi MATCH-001 2.16–2.19 |
| 9 | §4.2, §4.3 (2.1–2.4) | Thêm field | — → `conversationId` | FE mở phòng chat của thương lượng | Dùng `conversationId` để gọi MATCH-001 2.16–2.19 và `SUBSCRIBE` |
| 10 | §1.7 | Ghi rõ | — → quy tắc chat riêng của thương lượng; quy tắc chung (cả luồng A, đội trưởng của cả hai đội, đội đã giải tán chỉ xem, report qua chat, hạn lưu, "Tin nhắn của tôi") ở MATCH-001 §1.6, 2.20 | Một nơi cho quy tắc chat chung | Đọc MATCH-001 §1.6 |
| 11 | §5 | Bỏ mã | `CHAT_001` (mã của tài liệu này ở v1.x) → chuyển sang MATCH-001 (mã, HTTP, message không đổi) | Chủ sở hữu bộ endpoint chat | Không |
| 12 | §6 | Đánh số lại | Mục 2 → 15; mục 1 (SockJS) chuyển MATCH-001 | Số `[cần xác nhận]` duy nhất toàn thiết kế | Không |
| 13 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE ngoài các mục trên | — | — |
| 14 | Header 3 tài liệu | Ghi nhận | v1.2 → v2.0 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract: **phá vỡ** — FE đổi path chat, body gửi tin / đánh dấu đọc, topic realtime, shape tin nhắn, mã lỗi chat. Endpoint thương lượng 2.1–2.7 giữ nguyên (chỉ thêm `conversationId`).
- MATCH-001 v2.4 (bộ endpoint chat 2.16–2.20, yêu cầu ghép kèo có `conversationId`), MERCENARY-001 v2.0 (dùng chung chat), REPUTATION-001 v1.2 (report qua chat) đổi cùng thay đổi này.
- Nghiệp vụ / SRS: theo SRS `SYSTEM-001` v1.10 (BR-N04, BR-N05, BC-N04, UC-N01, AC-N01, AC-N03, Scenario 12).

## [v1.2] — 2026-11-13

**Type:** MINOR (không đổi endpoint, field, mã thành công, mã lỗi, frame của thương lượng) · **Tóm tắt:** Cập nhật tài liệu nội bộ BE cho MERCENARY-001 (kênh realtime dùng chung thêm topic cuộc trò chuyện theo đơn ứng tuyển), không ảnh hưởng FE của thương lượng.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Kênh realtime nhận thêm `SUBSCRIBE /topic/conversations/{conversationId}` (hợp đồng ở MERCENARY-001 `API_Spec.md` §1.7); mẫu `/topic/pairings/{pairingId}` và frame của thương lượng giữ nguyên | Không (FE dùng topic mới theo MERCENARY-001) |
| 2 | Header 3 tài liệu | Ghi nhận | v1.1 → v1.2 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract MATCH-002: không đổi.

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
