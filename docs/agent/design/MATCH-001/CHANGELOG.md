# CHANGELOG — MATCH-001 Kèo (luồng A, luồng B) & vòng đời kèo

## [v2.4] — 2026-11-15

**Type:** MINOR (thêm endpoint chat 2.16–2.20, thêm field `conversationId` vào yêu cầu ghép kèo, thêm mã lỗi `CHAT_*` cho endpoint mới, thêm `MATCH_016` tạm thời ở 2.1; tương thích ngược, trừ 2.1 `type = NEED_VENUE` tạm trả `422 MATCH_016` cho tới khi luồng B phát hành (chưa có bản chạy; user duyệt 2026-11-15); endpoint, field, mã thành công đã có của 2.1–2.15 giữ nguyên) · **Tóm tắt:** Gửi yêu cầu ghép kèo luồng A mở cuộc trò chuyện giữa đội gửi và đội tạo kèo; bộ endpoint chat và kênh realtime dùng chung cho mọi cuộc trò chuyện (luồng A, luồng B, mercenary) đặt ở mã này (chuyển từ MATCH-002 v2.0); thêm màn hình "Tin nhắn của tôi" (SRS `SYSTEM-001` v1.10 — BR-N01, BR-N03..BR-N06).

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | §4.4 Yêu cầu ghép kèo (2.6, 2.7, 2.12) | Thêm field | — → `conversationId` (các yêu cầu của cùng đội cho cùng kèo có cùng giá trị) | Mỗi cặp (kèo, đội gửi) một cuộc trò chuyện với đội tạo kèo, mở từ lúc gửi yêu cầu (BR-N04) | Mở phòng chat bằng 2.19 + 2.17 với `conversationId`; `SUBSCRIBE /topic/conversations/{conversationId}` |
| 2 | 2.6 | Thêm hành vi | Chỉ tạo yêu cầu → tạo yêu cầu **và** mở cuộc trò chuyện; gửi lại sau khi rút / hết hạn dùng tiếp cuộc trò chuyện cũ (giữ lịch sử) | BR-N04 | Không đổi cách gọi |
| 3 | 2.16–2.19, §4.7, §4.8 | Thêm endpoint | — → gửi tin (2.16), lịch sử chat (2.17), đánh dấu đã đọc (2.18), đầu phòng chat (2.19) theo **cuộc trò chuyện** `/conversations/{conversationId}/…` — endpoint mới ở mã này. FE đã code theo chat thương lượng của MATCH-002 v1.x (`/pairings/{pairingId}/messages`, body có `teamId`, tin nhắn có `pairingId` / `senderTeamId`, lỗi `PAIRING_*`) phải đổi path, body, shape tin nhắn, mã lỗi theo MATCH-002 `CHANGELOG.md` v2.0 | Bộ endpoint chat chung đặt ở mã đầu tiên mở chat (luồng A) — review 2026-11-15; MATCH-002 v2.0 bỏ chat theo thương lượng và trỏ về đây | Gọi chat bằng 2.16–2.19 với `conversationId` |
| 4 | 2.20, §4.9 | Thêm endpoint | — → `GET /api/v1/profile/conversations` "Tin nhắn của tôi": mọi cuộc trò chuyện của người gọi (luồng A, B, mercenary; gồm phòng chỉ xem), mới nhất trước; phòng chưa có tin của luồng A xếp theo lúc gửi yêu cầu gần nhất, luồng B và mercenary theo lúc mở | User chốt 2026-11-15 (MATCH-002 #13 / MERCENARY-001 #17; review 2026-11-15) | Màn hình "Tin nhắn" dùng 2.20 |
| 5 | §1.6, §1.7 | Thêm quy tắc | — → ai là bên nào, ai nhắn được / chỉ xem theo trạng thái yêu cầu (luồng A) hoặc thương lượng (luồng B); người đã giải tán đội chỉ xem; kênh realtime `/ws` + topic `/topic/conversations/{id}` + frame `MESSAGE_CREATED` | BR-N03, BR-N05, BR-N06; ADR-0014 | Khoá ô chat theo `canSend` (2.19); kết nối lại → 2.17 với `after` |
| 6 | §1, §3.2 | Ghi rõ | — → chat theo trạng thái yêu cầu: `PENDING`, `ACCEPTED`, `WITHDRAWN` sau khi được chấp nhận → nhắn được; `REJECTED`, `SUPERSEDED`, `EXPIRED`, `WITHDRAWN` khi còn chờ → chỉ xem; report "giao tiếp tệ" qua chat (REPUTATION-001 2.10) | BR-N05, BR-R07 | Như #5 |
| 7 | §5 | Thêm mã | — → `CHAT_001` (422 — trước là mã của chat thương lượng MATCH-002 v1.x), `CHAT_002` (404), `CHAT_003` (403), `CHAT_004` (409) (trước là mã của chat theo đơn MERCENARY-001 v1.0) cho 2.16–2.19 — mã, HTTP, message giữ nguyên; `USER_001` (404, tài khoản đã xoá) và `MATCH_004`, `MATCH_014` (2.16) cho endpoint mới | Chủ sở hữu bộ endpoint chat | Xử lý theo bảng §5 |
| 7a | 2.1 lỗi, §1, §5 | Thêm mã (tạm thời) | — → `422 MATCH_016` khi `type = NEED_VENUE` trong lúc luồng B chưa được phát hành (MATCH-001 code trước, luồng B phát hành cùng MATCH-002) | Thứ tự code hai đợt (user chốt 2026-11-15). Ngoại lệ của MINOR: chưa có bản chạy nên chưa FE nào gọi 2.1 với `NEED_VENUE` (user duyệt 2026-11-15); đợt MATCH-002 gỡ bằng entry mới "2.1 nhận lại `NEED_VENUE`" | Ẩn lựa chọn "Chưa có sân" tới khi luồng B được phát hành |
| 8 | §6 | Thêm mục | — → `[cần xác nhận #12]` đường dẫn / SockJS của kênh realtime (chuyển từ MATCH-002), không có frame khi yêu cầu luồng A đổi trạng thái (user chốt 2026-11-15 — giữ ở bản này) | Chưa chốt được khi thiết kế | Theo dõi §6 |
| 9 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE (bảng `conversation`, `conversation_message` tạo ở V6; lõi chat, cấu hình WebSocket chuyển từ MATCH-002), không ảnh hưởng FE ngoài các mục trên | — | — |
| 10 | Header 3 tài liệu | Ghi nhận | v2.3 → v2.4 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract: chỉ thêm (endpoint 2.16–2.20, field `conversationId`, mã lỗi cho endpoint mới, `MATCH_016` tạm thời ở 2.1) — tương thích ngược, trừ 2.1 `type = NEED_VENUE` tạm trả `422 MATCH_016` cho tới khi luồng B phát hành (chưa có bản chạy; user duyệt 2026-11-15). FE đã code chat theo MATCH-002 v1.x: đổi path, body, shape tin nhắn, topic, mã lỗi theo MATCH-002 `CHANGELOG.md` v2.0.
- Thứ tự phát hành (user chốt 2026-11-15): bản đầu của mã này gồm luồng A, chat và kênh realtime; kèo luồng B (`type = NEED_VENUE` — tạo, tìm, xem, huỷ, hết hạn) mở cùng bản phát hành MATCH-002, trước đó 2.1 trả `422 MATCH_016` (mục 7a). Chat của MATCH-002, MERCENARY-001, REPUTATION-001 dùng các endpoint chat của mã này (ADR-0014, sửa đổi 2026-11-15).
- Nghiệp vụ / SRS: theo SRS `SYSTEM-001` v1.10 (BR-N01, BR-N03..BR-N06, UC-N01, UC-N04, AC-N01, AC-N03, AC-N04, UC-M03, Scenario 12).

## [v2.3] — 2026-11-13

**Type:** MINOR (không đổi endpoint, field, mã thành công, mã lỗi, message) · **Tóm tắt:** Cập nhật tài liệu nội bộ BE cho MERCENARY-001 (job vòng đời kèo chạy thêm tác vụ đóng / hết hạn tin tuyển mercenary), không ảnh hưởng FE.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | MERCENARY-001: tin tuyển đóng khi kèo bị huỷ / hết hạn lúc trận kết thúc, báo mercenary đã duyệt khi kèo bị huỷ — chạy cùng job định kỳ | Không |
| 2 | Header 3 tài liệu | Ghi nhận | v2.2 → v2.3 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract: không đổi. Huỷ kèo (2.5, 2.10) không thêm bước nào; trạng thái tin tuyển của kèo bị huỷ do MERCENARY-001 tính.

## [v2.2] — 2026-11-12

**Type:** MINOR (không đổi endpoint, field, mã thành công, mã lỗi, message) · **Tóm tắt:** Huỷ kèo đã ghép / rút yêu cầu đã chấp nhận nay cũng làm mất chuỗi trận sạch của đội huỷ; cập nhật tài liệu nội bộ BE cho REPUTATION-001.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | Hành vi 2.5 (`CONFIRMED`), 2.10 (`ACCEPTED`) | Thêm | Chỉ trừ điểm theo khung giờ → trừ điểm + chuỗi trận sạch của đội huỷ về 0 (cách tính chuỗi: REPUTATION-001 `API_Spec.md` §1.3) | Business_Rules §3.1 "Trận sạch" — user chốt 2026-11-12 | Không (chuỗi trận sạch không có trong response MATCH-001) |
| 2 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | REPUTATION-001 mở rộng phần uy tín dùng chung, thêm job định kỳ | Không |
| 3 | Header tài liệu thiết kế MATCH-001 | Ghi nhận | v2.1 → v2.2 | Đồng bộ phiên bản | Không |

### Ảnh hưởng
- Hợp đồng API: không đổi.
- Nghiệp vụ / SRS: chuỗi trận sạch bị cắt khi huỷ kèo đã ghép — SRS cập nhật cùng REPUTATION-001 (SYSTEM-001 v1.8).

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
