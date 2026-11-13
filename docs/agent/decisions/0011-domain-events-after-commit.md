# ADR-0011: Miền nghiệp vụ phát Spring ApplicationEvent, listener chạy sau commit

| | |
|---|---|
| **Trạng thái** | Proposed |
| **Ngày** | 2026-11-09 |
| **Người quyết định** | Chủ repo (chốt "domain phát ApplicationEvent, NOTIFICATION-001 nghe", 2026-11-09) · solution-architect (đề xuất quy ước) |
| **Liên quan** | [ADR-0003](0003-optimistic-locking-strategy.md) §Cần theo dõi (side effect sau commit) · [ADR-0013](0013-reputation-columns-atomic-update.md) (dữ liệu uy tín ghi cùng transaction) · `docs/agent/design/VENUE-001/Component_Class_Design.md` §4.7 · `docs/agent/design/MATCH-001/Component_Class_Design.md` §4.9 · [ADR-0014](0014-realtime-chat-stomp-websocket.md) (`Proposed`) — frame realtime chat / thương lượng **không** đi qua event của ADR này mà qua port `ChatRealtimeGateway` đăng ký `afterCommit` (cùng nguyên tắc "chỉ phát sau commit, lần thử rollback không phát"; payload cần nội dung tin nên không hợp quy tắc 2 về payload) · NOTIFICATION-001 (sau) · `docs/agent/design/REPUTATION-001/Component_Class_Design.md` §4.8 (`TeamVerificationRefresher` — quy tắc 8) |

## Bối cảnh (Context)
- VENUE-001 và MATCH-001 cần báo cho người dùng (11 loại thông báo — Overview §4) và cho REPUTATION-001, nhưng NOTIFICATION-001 được thiết kế sau; module miền không nên phụ thuộc module thông báo.
- Thao tác ghi chạy qua retrier: mỗi lần thử là một transaction, có thể rollback rồi thử lại (ADR-0003). Side effect phát trong lần thử bị rollback không được đến người nhận.
- Một instance PostgreSQL, chưa có message broker; chưa có bảng outbox.
- VENUE-001 đặt tiền lệ (`VenueReportedEvent`, `VenueAdminAlertEvent`) và để mở `[cần xác nhận #10]` "ADR cho quy ước domain event".

## Quyết định (Decision)
Chúng tôi dùng **Spring `ApplicationEvent` trong process** làm hợp đồng giữa miền phát và miền nghe:

1. **Đặt tên / vị trí:** `record <Aggregate><Động từ quá khứ>Event` (vd `MatchConfirmedEvent`) trong `application/event/<miền>/` (VENUE-001 đặt ở `application/event/` — giữ nguyên, miền mới dùng package con).
2. **Payload:** id + enum + thời điểm (`occurredAt`, UTC) đủ để người nghe truy vấn phần còn lại sau commit; **không** PII, không entity, không danh sách lớn (vd danh sách đội bị thay thế → người nghe tự truy vấn theo `matchId`).
3. **Phát:** `ApplicationEventPublisher.publishEvent` **bên trong** transaction ghi (lambda retrier, method `@Transactional`, lô job), sau khi bước ghi tương ứng đã thực hiện.
4. **Nghe:** `@TransactionalEventListener(phase = AFTER_COMMIT)`; listener cần ghi DB tự mở transaction mới (`REQUIRES_NEW`). Lần thử rollback → event bị bỏ; lần thử commit → event giao đúng một lần trong process.
5. Miền phát không có listener của chính mình; không dùng event để điều khiển nghiệp vụ đồng bộ trong cùng request.
6. **Phạm vi:** event không bền (xem Hệ quả) → chỉ dùng cho **thông báo** và **dữ liệu dẫn xuất tính lại được** từ dữ liệu gốc (vd cache badge). Dữ liệu nghiệp vụ — cộng/trừ điểm uy tín, bộ đếm hoàn thành, chuỗi trận sạch, bộ đếm report — MUST ghi trong **cùng transaction** với thao tác gây ra (qua `ReputationService`, ADR-0013), không qua listener.
7. Mỗi thao tác nghiệp vụ phát **một** event mô tả nó (vd rút yêu cầu đã chấp nhận = một `MatchCancelledEvent` có `cause`), để người nghe không thông báo trùng.
8. **Dữ liệu dẫn xuất của chính miền phát** (không được nghe event của mình — quy tắc 5): đăng ký `TransactionSynchronization.afterCommit` trong transaction ghi, ghi DB bằng `TransactionTemplate` `REQUIRES_NEW` — cùng nguyên tắc "chỉ chạy sau commit, lần thử rollback không chạy". Resource tự bind vào `TransactionSynchronizationManager` MUST gỡ ở `afterCompletion` với mọi trạng thái. Áp dụng: `TeamVerificationRefresher` (REPUTATION-001).

**Phương án đã loại**
- Gọi thẳng service thông báo từ service miền: phụ thuộc chéo, side effect chạy cả khi lần thử bị rollback.
- Outbox table + worker: giao bền, nhưng thêm bảng, job, quy tắc dọn — chưa cần với MVP một instance.
- Message broker (Kafka/RabbitMQ): thêm hạ tầng chưa có.

## Hệ quả (Consequences)

**Tích cực**
- Miền phát không biết người nghe; NOTIFICATION-001 (và dữ liệu dẫn xuất) thêm listener không sửa miền phát.
- Lần thử bị rollback không gửi thông báo sai.

**Tiêu cực / đánh đổi**
- **Không bền:** crash giữa commit và listener → mất event (mất thông báo). Chấp nhận cho MVP.
- Listener chạy đồng bộ trên thread của request sau commit → listener chậm làm chậm response (NOTIFICATION-001 cân nhắc `@Async`).
- Người nghe tự truy vấn thêm dữ liệu → dữ liệu có thể đã đổi tiếp so với lúc phát.

**Cần theo dõi**
- Mất thông báo gây khiếu nại, hoặc chạy nhiều instance cần giao giữa process → xem lại outbox.
- Khi Accepted: VENUE-001 `[cần xác nhận #10]` đóng theo ADR này.
