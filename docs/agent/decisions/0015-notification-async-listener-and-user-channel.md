# ADR-0015: Thông báo in-app tạo bởi listener bất đồng bộ sau commit, ghi gom lô, đẩy qua kênh STOMP theo người dùng

| | |
|---|---|
| **Trạng thái** | Accepted (chủ repo chốt 2026-11-16) |
| **Ngày** | 2026-11-16 |
| **Người quyết định** | Chủ repo (chốt phạm vi NOTIFICATION-001 Q1–Q13, 2026-11-16) · solution-architect (đề xuất cách thực hiện) |
| **Sửa đổi** | 2026-11-17 — rủi ro "lời mời vào đội phụ thuộc thông báo" ở §Hệ quả sẽ được khắc phục khi USER-003 triển khai endpoint "Lời mời của tôi" (mục "Sửa đổi 2026-11-17" cuối file) |
| **Liên quan** | [ADR-0011](0011-domain-events-after-commit.md) (event `AFTER_COMMIT`, hệ quả "NOTIFICATION-001 cân nhắc `@Async`") · [ADR-0014](0014-realtime-chat-stomp-websocket.md) (Sửa đổi 2026-11-16 — destination `/user/queue/notifications`) · [ADR-0012](0012-scheduled-jobs-claim-with-skip-locked.md) · [ADR-0008](0008-uuid-v7-primary-keys.md) (Sửa đổi 2026-11-09 — `uuidv7()` cho INSERT gom lô) · `docs/agent/design/NOTIFICATION-001/` (Component_Class_Design §4.2–§4.7) |

## Bối cảnh (Context)
- Sáu mã thiết kế (MATCH-001, MATCH-002, REPUTATION-001, MERCENARY-001, VENUE-001, USER-001) phát 28 `ApplicationEvent` mà NOTIFICATION-001 phải biến thành thông báo in-app (user chốt Q1, Q11, 2026-11-16; danh sách: Component_Class_Design §4.3). ADR-0011 cho listener `@TransactionalEventListener(AFTER_COMMIT)` chạy **đồng bộ** trên luồng của request / lô job sau commit và để mở việc dùng `@Async`.
- Nhiều API nguồn đang chạm đúng ngưỡng 6 truy vấn ghi (MATCH-001 2.8, 2.10; USER-001 2.12). Một listener đồng bộ cần ít nhất 2 câu (đọc người nhận, ghi) — không cộng vào số truy vấn của transaction nguồn nhưng kéo dài thời gian response và giữ luồng request / luồng job.
- Một event có thể có nhiều người nhận (các đội bị thay thế của một kèo, mọi Admin, mọi mercenary của trận). Repo không bật JDBC batch toàn ứng dụng (user chốt 2026-11-09).
- App đang mở cần nhận thông báo mới ngay (Q6) và tín hiệu "có tin nhắn mới" cho màn hình "Tin nhắn" (Q9); hạ tầng STOMP đã có ở MATCH-001 (ADR-0014) nhưng chỉ có topic theo cuộc trò chuyện. Push điện thoại để sau (Q10).

## Quyết định (Decision)
Chúng tôi:
1. **Listener bất đồng bộ:** mỗi listener thông báo là `@Async("notificationExecutor")` + `@TransactionalEventListener(phase = AFTER_COMMIT)`. Executor riêng có giới hạn (2–4 luồng, hàng đợi 1000), `CallerRunsPolicy` khi đầy (chạy trên luồng phát — chậm thay vì mất; rủi ro giữ 2 kết nối DB: xem Hệ quả), chờ hàng đợi tối đa 30s khi tắt. Listener không mang `@Transactional`; gọi `NotificationDispatcher` (`REQUIRES_NEW`). Lỗi → log, không thử lại, không ném.
2. **Mỗi event ≤ 2 truy vấn:** một câu đọc người nhận + dữ liệu hiển thị (đội trưởng `ACTIVE` hiện tại, tên đội / sân, giờ), một câu `INSERT … SELECT … FROM unnest(...) JOIN "user" … RETURNING` gom mọi người nhận (id `uuidv7()`), loại người dùng đã xoá ngay trong câu ghi. Khử trùng (người nhận, loại, đối tượng) trong bộ nhớ.
3. **Nội dung soạn sẵn** (`title`, `body` tiếng Việt) lúc tạo, một nơi giữ câu chữ (`NotificationTexts`); không tên cá nhân.
4. **Đẩy sau commit qua kênh theo người dùng:** port `UserRealtimeGateway.publishAfterCommit(deliveries)` (một `TransactionSynchronization` cho cả lô); adapter STOMP `convertAndSendToUser(userId, "/queue/notifications", …)` (ADR-0014 Sửa đổi 2026-11-16). Cùng port dùng cho tín hiệu `CONVERSATION_ACTIVITY` khi gửi tin chat (không tạo dòng thông báo).
5. **Push qua port `PushSender`**, bản đầu no-op; gọi sau commit, ngoài transaction. FCM thay bean sau.
6. Job nhắc trước trận (không có event nguồn) ghi thông báo **trong** transaction lô của chính nó qua cùng writer; claim bằng `INSERT … ON CONFLICT DO NOTHING RETURNING` vào bảng dấu (unique theo trận) — không khoá dòng `match`.

**Phương án đã loại**
- Listener đồng bộ (mặc định ADR-0011): mỗi thao tác nguồn chờ thêm 2 câu + dựng nội dung trước khi trả response; lô job vòng đời kèo chậm theo số thông báo.
- Outbox table + worker: giao bền nhưng thêm bảng, worker, quy tắc dọn — vẫn chưa cần ở MVP một instance (ADR-0011 đã loại cùng lý do).
- Lưu loại + tham số, FE tự ghép câu: user chọn nội dung soạn sẵn (Q5); FE không phải biết mọi mẫu câu.
- `saveAll` từng entity: n câu INSERT cho n người nhận (không bật JDBC batch).
- Gửi frame thông báo qua topic chung `/topic/users/{id}`: phải phân quyền `SUBSCRIBE` bằng so khớp id; user destination của Spring gắn hàng đợi với principal của phiên, không cần truy vấn.

## Hệ quả (Consequences)

**Tích cực**
- Thời gian response và số truy vấn của API nguồn không đổi; miền phát không biết NOTIFICATION-001.
- Số truy vấn mỗi event cố định (≤ 2) bất kể số người nhận.
- Một kênh cá nhân cho mọi tín hiệu theo người dùng (thông báo, hoạt động chat).

**Tiêu cực / đánh đổi**
- **Mất thông báo** khi crash / tắt máy giữa commit nguồn và dispatch, hoặc dispatch lỗi (không retry) — rộng hơn bản đồng bộ một chút (hàng đợi trong bộ nhớ). Chấp nhận cho MVP.
- Người nhận tính lúc dispatch (vài giây sau sự kiện) — chuyển quyền đội trưởng đúng khoảng đó thì đội trưởng mới nhận. User chốt 2026-11-16: "đội trưởng" nhận thông báo = đội trưởng **tại lúc tạo thông báo**, không phải đúng lúc sự kiện; event nguồn không đổi.
- Thêm luồng chiếm kết nối DB (tối đa 4) — cần đo cùng pool HikariCP.
- `CallerRunsPolicy` có thể làm chậm request khi hàng đợi đầy (hiếm, thay vì mất). Khi đó luồng phát giữ **2 kết nối DB** cùng lúc (kết nối của transaction nguồn chưa trả về pool ở pha sau commit + kết nối `REQUIRES_NEW` của dispatcher); nhiều luồng cùng rơi vào nhánh này có thể cạn pool HikariCP [Inference — chưa đo; NOTIFICATION-001 `[cần xác nhận #12]`]. Chỉ ghi rủi ro, đo khi load test.
- **Lời mời vào đội phụ thuộc thông báo:** thông báo `TEAM_INVITATION` là kênh duy nhất để người được mời biết lời mời (chưa có endpoint "lời mời của tôi" — để USER-003). Thông báo mất khi crash (như trên) hoặc hết hiển thị sau 30 ngày trong khi lời mời không hết hạn → người được mời không tìm lại được lời mời. User chấp nhận rủi ro 2026-11-16.
- Simple broker trong bộ nhớ: frame chỉ tới client cùng instance (như ADR-0014).

**Cần theo dõi**
- Khiếu nại mất thông báo, hoặc chạy nhiều instance → outbox + worker, broker relay.
- Log `ERROR` dispatch lặp lại, hàng đợi thường xuyên đầy → tăng executor / xem lại truy vấn audience.
- Tích hợp FCM: ADR riêng cho thiết bị, retry, timeout của `PushSender`.

## Sửa đổi 2026-11-17 — USER-003: "Lời mời của tôi" sẽ khắc phục rủi ro lời mời phụ thuộc thông báo

Bổ sung, không đổi §Quyết định. Rủi ro ở §Hệ quả "Lời mời vào đội phụ thuộc thông báo" (user chấp nhận 2026-11-16) **sẽ được khắc phục khi USER-003 triển khai** (hiện mới có thiết kế; tới lúc đó rủi ro vẫn còn): USER-003 thêm `GET /api/v1/profile/invitations` liệt kê mọi lời mời đang chờ của người gọi (gồm cả lời mời của đội bị Admin tạm ngưng), không phụ thuộc thông báo. Khi đó, thông báo `TEAM_INVITATION` mất (crash / dispatch lỗi) hoặc hết hiển thị sau 30 ngày thì người được mời vẫn thấy và chấp nhận / từ chối được lời mời. Rủi ro mất thông báo nói chung (§Hệ quả) giữ nguyên. Chi tiết: `docs/agent/design/USER-003/` (API_Spec §2.4), SRS `docs/agent/business-spec/USER-003/` (UC-T07).
