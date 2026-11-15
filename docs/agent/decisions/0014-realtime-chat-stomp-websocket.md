# ADR-0014: Chat (cuộc trò chuyện): REST là nguồn chính, đẩy tin qua STOMP/WebSocket (SockJS) với broker trong bộ nhớ

| | |
|---|---|
| **Trạng thái** | Proposed |
| **Ngày** | 2026-11-10 |
| **Sửa đổi** | 2026-11-13 — MERCENARY-001: thêm topic cuộc trò chuyện tổng quát `/topic/conversations/{conversationId}` · 2026-11-15 — mọi chat (hai đội luồng A, B và mercenary) dùng một mô hình cuộc trò chuyện; bỏ `chat_message`, REST `/pairings/{id}/messages` và topic `/topic/pairings/{id}` |
| **Người quyết định** | Chủ repo (chốt cơ chế chat MATCH-002, 2026-11-10) · solution-architect (đề xuất cách thực hiện) |
| **Liên quan** | `docs/agent/design/MATCH-001/` (lõi chat + kênh realtime từ 2026-11-15 — `API_Spec.md` §1.6, §1.7; `Component_Class_Design.md` §4.14, §4.15) · [ADR-0001](0001-jwt-authentication-strategy.md) (JWT) · [ADR-0011](0011-domain-events-after-commit.md) (side effect sau commit, một instance) · `docs/agent/design/MATCH-002/` (chỉ còn frame `PAIRING_UPDATED` — `API_Spec.md` §1.6, `Component_Class_Design.md` §4.6) · SRS `SYSTEM-001` UC-N01, AC-N01, Acceptance_Criteria §3.1 · `docs/agent/design/MERCENARY-001/` (chat theo đơn ứng tuyển — Component_Class_Design §4.7–§4.8; BR-ME16, UC-ME09, AC-ME10) · SRS `SYSTEM-001` v1.10 BR-N04, BR-N05 (chat hai đội luồng A và B) |

## Bối cảnh (Context)
- MATCH-002 có chat giữa hai đội trưởng của một thương lượng ghép kèo (luồng B). SRS: tin nhắn tới bên kia "real-time … (WebSocket/polling)", độ trễ < 1s; implementation note "WebSocket + SockJS (Spring Boot `.withSockJS()`)".
- Ứng dụng là REST stateless, xác thực bằng JWT Bearer qua `oauth2ResourceServer` (ADR-0001). Chưa có dependency WebSocket. Envelope lỗi, validation, retrier và đếm truy vấn đều xây cho request HTTP.
- Một instance PostgreSQL, chưa có message broker; ADR-0011 đã giả định một instance ứng dụng.
- Tin nhắn phải lưu bền (retention 90 ngày, NOTIFICATION-001 xoá). Kênh đẩy chỉ để giảm độ trễ.

## Quyết định (Decision)
Chúng tôi:
1. **Gửi tin và đọc lịch sử bằng REST** (`POST`/`GET …/pairings/{id}/messages`). REST là nguồn dữ liệu chính: lưu DB rồi mới đẩy; lịch sử phân trang bằng cursor để client bù tin bị lỡ.
2. **Đẩy tin qua STOMP over WebSocket**, endpoint `/ws` bật **SockJS** (fallback khi WebSocket bị chặn). Client chỉ `SUBSCRIBE`, **không** `SEND` (không cấu hình application destination prefix); mọi ghi đi qua REST.
3. **Simple broker trong bộ nhớ** (`/topic`), mỗi thương lượng một topic `/topic/pairings/{pairingId}`; `SUBSCRIBE` chỉ nhận đúng mẫu này (không wildcard); tắt lọc theo header `selector` của simple broker (biểu thức do client gửi, không có use case). Heartbeat 10s/10s chạy trên `TaskScheduler` riêng của WebSocket (không dùng pool `@Scheduled` chung).
4. **Xác thực ở STOMP `CONNECT`:** header `Authorization: Bearer <access token>`, kiểm bằng cùng `JwtDecoder` và validator của REST (chặn refresh token). Handshake HTTP `/ws/**` để `permitAll` và bộ lọc Bearer bỏ qua đường dẫn này (SockJS không gửi header tuỳ ý ở mọi transport). Lỗi → frame `ERROR` rồi đóng phiên.
5. **Phân quyền ở `SUBSCRIBE`:** chỉ đội trưởng ACTIVE hiện tại của một trong hai đội trong thương lượng; kiểm bằng một truy vấn DB lúc subscribe. Token đã hết hạn lúc subscribe → `ERROR`.
6. **Đẩy sau commit:** service đăng ký `TransactionSynchronization.afterCommit` qua một port trung tính ở tầng application (`ChatRealtimeGateway`); cài đặt STOMP nằm ở module `api`. Lần thử bị rollback không đẩy. Payload frame dựng từ dữ liệu service đã có trong bộ nhớ, không đọc lại DB.
7. Origin cho handshake lấy từ cấu hình (`arenaflow.websocket.allowed-origin-patterns`), mặc định rỗng (chỉ same-origin; client native không gửi `Origin`). **NEVER** `*`.

**Phương án đã loại**
- Polling REST thuần: không thêm dependency, chạy nhiều instance được; muốn < 1s phải poll ~1s mỗi màn hình chat → tải DB cao, vẫn không đạt chặt < 1s.
- SSE (`SseEmitter`): không thêm dependency; `EventSource` trình duyệt không gửi được header Bearer → cần token trên query string (lộ trong log) hoặc thư viện riêng ở FE; SRS đã chọn WebSocket.
- Gửi tin qua STOMP `SEND`: phải dựng lại validation, envelope lỗi, retrier, đếm truy vấn cho kênh STOMP; khó trả lỗi theo mã cho từng tin.
- Broker relay (RabbitMQ/ActiveMQ STOMP): chạy nhiều instance được nhưng thêm hạ tầng chưa có.
- Đẩy qua listener của domain event (ADR-0011): event không mang nội dung tin (quy tắc payload không chứa dữ liệu người dùng) → listener phải đọc lại DB; port `afterCommit` dùng dữ liệu đã có.

## Hệ quả (Consequences)

**Tích cực**
- Một đường ghi duy nhất (REST) với validation, mã lỗi, đếm truy vấn như mọi endpoint; WebSocket hỏng thì chat vẫn dùng được bằng `GET …/messages?after=`.
- Dùng lại JWT và validator của REST, không có cơ chế xác thực thứ hai.

**Tiêu cực / đánh đổi**
- Thêm dependency `spring-boot-starter-websocket` (module `api`) — tên artifact với Spring Boot 4.0.9 `[cần xác nhận]`.
- [Unverified] Spring Framework 7 / Spring Boot 4.0.9 còn hỗ trợ SockJS phía server (`.withSockJS()`): chưa kiểm được bằng nguồn (artifact `spring-websocket` không có trong cache Maven local) `[cần xác nhận]` — kiểm khi thêm dependency; không còn → bỏ SockJS, giữ WebSocket thuần, FE dùng `GET …/messages?after=` khi không mở được WebSocket.
- **Chỉ một instance:** simple broker nằm trong bộ nhớ của từng process; chạy ≥ 2 instance thì client chỉ nhận tin từ instance mình kết nối → phải đổi sang broker relay.
- **Phân quyền chỉ kiểm lúc subscribe:** đội trưởng bị chuyển quyền hoặc token hết hạn sau khi subscribe vẫn nhận frame tới khi ngắt kết nối. Chấp nhận cho MVP: nội dung chỉ là tin nhắn của chính cuộc trò chuyện người đó đã tham gia.
- Không bảo đảm giao frame (mất kết nối, restart): client bù bằng REST sau khi kết nối lại.
- Thêm một truy vấn DB cho mỗi `SUBSCRIBE`.

**Cần theo dõi**
- Cần chạy nhiều instance → ADR mới cho broker relay (hoặc chuyển sang polling).
- Cần thu hồi quyền tức thời khi chuyển quyền đội trưởng → ngắt phiên theo user (registry phiên) hoặc kiểm lại định kỳ. **Ưu tiên cao từ Sửa đổi 2026-11-15** (cuộc trò chuyện sống lâu, có người chỉ xem) — làm trước khi mở rộng người dùng.
- Số kết nối đồng thời / bộ nhớ broker khi tăng người dùng; giới hạn kích thước frame vào.

## Sửa đổi 2026-11-13 — MERCENARY-001: topic cuộc trò chuyện tổng quát

Bổ sung, không thay §Quyết định 1–2, 4, 6–7. Mở rộng §Quyết định 3, 5:

- Thêm mẫu topic `/topic/conversations/{conversationId}` cho **cuộc trò chuyện** (ngữ cảnh + hai bên, mỗi bên là đội hoặc người dùng). Ngữ cảnh đầu tiên: chat 1–1 giữa đội trưởng đội tuyển và ứng viên của một đơn ứng tuyển mercenary (`docs/agent/design/MERCENARY-001/` — API_Spec §1.7, Component_Class_Design §4.7–§4.8). `SUBSCRIBE` vẫn chỉ nhận đúng nguyên văn hai mẫu (`/topic/pairings/{id}`, `/topic/conversations/{id}`), không wildcard.
- Phân quyền `SUBSCRIBE` của topic mới: một truy vấn DB lúc subscribe, theo luật đọc của loại ngữ cảnh (mercenary: ứng viên hoặc đội trưởng ACTIVE hiện tại của đội tuyển). Rủi ro "chỉ kiểm lúc subscribe" như §Hệ quả.
- Port `ChatRealtimeGateway` thêm phương thức đẩy theo cuộc trò chuyện, cùng cơ chế `afterCommit`.
- **Mô hình dữ liệu cuộc trò chuyện** (MERCENARY-001 `Component_Class_Design.md` §4.7, §5.1): bảng tổng quát `conversation` (loại ngữ cảnh + khoá ngữ cảnh dạng cột FK riêng theo loại + trận để tính hạn lưu + hai bên `TEAM`/`USER`) và `conversation_message` (bên gửi A/B + người thực sự gửi). Tách khỏi `chat_message` của pairing — MATCH-002 giữ nguyên. Đã loại: dùng chung `chat_message` (phải nới `pairing_id`/`sender_team_id`, sửa mọi truy vấn MATCH-002; phía ứng viên không có đội); bảng riêng chỉ cho mercenary (ngữ cảnh kế tiếp lại cần bảng thứ ba). Quyền đọc / gửi theo loại ngữ cảnh qua một policy; lưu tin, cursor, đánh dấu đọc, kênh realtime dùng chung.
- Lý do chọn topic tổng quát thay vì `/topic/mercenary-applications/{id}`: chat đội–đội theo cặp (kèo, đội) — thay đổi đã chốt, làm sau trên nhánh riêng — có thể thêm loại ngữ cảnh mà không thêm mẫu topic.

## Sửa đổi 2026-11-15 — Một mô hình cuộc trò chuyện cho mọi chat; bỏ chat theo pairing

**Bối cảnh.** SRS `SYSTEM-001` v1.10 (BR-N04, BR-N05; user chốt 2026-11-13, 2026-11-15): chat giữa hai đội mở cho cả **luồng A** (từ lúc gửi yêu cầu ghép kèo, mỗi đội gửi một cuộc chat với đội tạo kèo, gửi lại yêu cầu dùng tiếp cuộc cũ) và luồng B (mỗi pairing một cuộc chat); hai đội đã ghép nhắn tiếp sau khi ghép, sau trận, khi kèo bị huỷ; đội không được ghép chỉ xem. Một cuộc chat luồng A sống qua nhiều yêu cầu của cùng đội nên không gắn được vào một dòng `match_request`; giữ `chat_message` theo pairing sẽ cần thêm nơi lưu thứ hai cho luồng A (thứ ba nếu tính mercenary).

**Quyết định bổ sung** (đảo câu "Tách khỏi `chat_message` của pairing — MATCH-002 giữ nguyên" của Sửa đổi 2026-11-13; giữ §Quyết định 1–2, 4, 6–7):
- **Mọi chat** dùng bảng `conversation` + `conversation_message`: ngữ cảnh `TEAM_MATCH` (đội tạo kèo ↔ một đội tham gia; mỗi cặp (kèo, đội tham gia) một dòng — unique) cho luồng A và B; `MERCENARY_APPLICATION` cho chat theo đơn. Bảng chuyển lên migration của MATCH-001 (V6); bỏ bảng `chat_message` (V1). MERCENARY-001 (V9) chỉ thêm cột / CHECK ngữ cảnh mercenary.
- **Nơi đặt (review 2026-11-15):** lõi chat (bảng, `ConversationService`, policy, endpoint `/conversations/…`, `/profile/conversations`) và **toàn bộ hạ tầng STOMP** của ADR này (cấu hình `/ws`, xác thực `CONNECT`, phân quyền `SUBSCRIBE`, gateway) thuộc MATCH-001 — phần **chat** không còn phụ thuộc MATCH-002; chat luồng A có realtime ngay khi MATCH-001 xong (AC-N01 độ trễ < 1s). Phần luồng B nằm trong thành phần của MATCH-001 (tạo / tìm / xem / huỷ / hết hạn kèo `NEED_VENUE`) vẫn dùng bảng của MATCH-002 (V7) nên code theo đợt: MATCH-001 trước, nhánh luồng B ở đợt MATCH-002 (MATCH-001 `Component_Class_Design.md` §1.1, user chốt 2026-11-15). MATCH-002 chỉ thêm nhánh luồng B và frame `PAIRING_UPDATED`; MERCENARY-001 thêm ngữ cảnh đơn và frame `APPLICATION_UPDATED`.
- "Nhắn được / chỉ xem" **suy ra khi gửi** từ trạng thái hiện hành của yêu cầu gần nhất / pairing / đơn (không lưu trạng thái trên cuộc trò chuyện); quyền xem / gửi theo loại ngữ cảnh qua một policy.
- REST chat chỉ còn `/api/v1/conversations/{id}` (đầu phòng, gửi, lịch sử, đánh dấu đọc) và `/api/v1/profile/conversations` ("Tin nhắn của tôi"). Thay §Quyết định 1 (`…/pairings/{id}/messages`).
- Topic duy nhất `/topic/conversations/{id}`; frame của pairing (`PAIRING_UPDATED`) đi trên topic cuộc trò chuyện của pairing. Thay mẫu `/topic/pairings/{id}` ở §Quyết định 3, 5 — `SUBSCRIBE` chỉ nhận đúng một mẫu. Port `ChatRealtimeGateway` còn một phương thức `publishAfterCommit(conversationId, frame)`.
- Phân quyền `SUBSCRIBE`: luật đọc của loại ngữ cảnh (`TEAM_MATCH`: đội trưởng ACTIVE hiện tại của một trong hai đội, hoặc người đã giải tán đội của mình — chỉ đọc, user chốt 2026-11-15), một truy vấn DB như cũ.

**Phương án loại:** giữ `chat_message` theo pairing + nơi lưu riêng cho luồng A + bảng mercenary (ba nơi lưu tin, ba bộ API / topic, job xoá hạn lưu phải biết ba bảng); thêm cột `match_request_id` vào cuộc chat (một cuộc chat luồng A gắn nhiều yêu cầu khi gửi lại).

**Hệ quả:** hợp đồng chat của MATCH-002 đổi (MAJOR — MATCH-002 `CHANGELOG.md` v2.0); MERCENARY-001 v2.0 đổi hợp đồng (MAJOR — người đã giải tán đội tuyển xem được chat, report theo đơn trả `TEAM_016`); MATCH-001 v2.4 thêm bộ endpoint chat (MINOR). Thêm một INSERT cuộc trò chuyện vào gửi yêu cầu (MATCH-001 2.6) và nhận kèo (MATCH-002 2.1) — cả hai chạm đúng ngưỡng 6 truy vấn ghi. Job xoá hạn lưu (NOTIFICATION-001) chỉ xoá dòng `conversation` (tin xoá theo `ON DELETE CASCADE`). "Một instance" giữ nguyên như §Hệ quả.

**Rủi ro "chỉ kiểm quyền lúc `SUBSCRIBE`" — phạm vi lớn hơn bản 2026-11-10** (review 2026-11-15): cuộc trò chuyện nay sống tới 90 ngày sau khi kèo kết thúc (hai đội đã ghép vẫn nhắn sau trận), có người chỉ xem (người đã giải tán đội) và có danh sách mọi cuộc trò chuyện của một người. Một kết nối mở lâu của đội trưởng vừa chuyển quyền / người vừa xoá tài khoản / token đã hết hạn vẫn nhận frame của các cuộc trò chuyện đã đăng ký trong thời gian dài. Vẫn chấp nhận cho MVP, nhưng **nâng ưu tiên** mục "thu hồi quyền tức thời" ở §Cần theo dõi: làm trước khi mở rộng người dùng — ngắt phiên / huỷ đăng ký theo user khi chuyển quyền đội trưởng, giải tán đội, xoá tài khoản (registry phiên STOMP), hoặc kiểm lại quyền khi đẩy frame. Theo dõi ở MATCH-001 `Processing_Flow.md` §9.

Chi tiết: `docs/agent/design/MATCH-001/` (Component_Class_Design §4.14, §4.15, §5.1 — v2.4), `docs/agent/design/MATCH-002/` (Component_Class_Design §4.2, §4.6 — v2.0), `docs/agent/design/MERCENARY-001/` (Component_Class_Design §4.7–§4.8, §5.1 — v2.0).
