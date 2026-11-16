# ADR-0011: Miền nghiệp vụ phát Spring ApplicationEvent, listener chạy sau commit

| | |
|---|---|
| **Trạng thái** | Accepted (chủ repo chốt 2026-11-16) |
| **Ngày** | 2026-11-09 |
| **Người quyết định** | Chủ repo (chốt "domain phát ApplicationEvent, NOTIFICATION-001 nghe", 2026-11-09) · solution-architect (đề xuất quy ước) |
| **Liên quan** | [ADR-0003](0003-optimistic-locking-strategy.md) §Cần theo dõi (side effect sau commit) · [ADR-0013](0013-reputation-columns-atomic-update.md) (dữ liệu uy tín ghi cùng transaction) · `docs/agent/design/VENUE-001/Component_Class_Design.md` §4.7 · `docs/agent/design/MATCH-001/Component_Class_Design.md` §4.9 · [ADR-0014](0014-realtime-chat-stomp-websocket.md) — frame realtime chat / thương lượng **không** đi qua event của ADR này mà qua port `ChatRealtimeGateway` đăng ký `afterCommit` (cùng nguyên tắc "chỉ phát sau commit, lần thử rollback không phát"; payload cần nội dung tin nên không hợp quy tắc 2 về payload) · NOTIFICATION-001 (`docs/agent/design/NOTIFICATION-001/` — người nghe; listener `@Async` theo [ADR-0015](0015-notification-async-listener-and-user-channel.md)) · `docs/agent/design/REPUTATION-001/Component_Class_Design.md` §4.8 (`TeamVerificationRefresher` — quy tắc 8) · `docs/agent/design/MERCENARY-001/Component_Class_Design.md` §4.9, §4.11 (payload danh sách id nhỏ, event theo trận) |
| **Sửa đổi** | 2026-11-14 — MERCENARY-001: ngoại lệ có phạm vi của quy tắc 2 (payload mang danh sách id nhỏ); event theo trận phát từ job thay vì nghe event của miền khác khi việc nghe làm tăng truy vấn của request gốc · 2026-11-16 — NOTIFICATION-001: listener thông báo chạy `@Async` theo ADR-0015; việc "đóng VENUE-001 `[cần xác nhận #10]`" ở §Cần theo dõi đã xong |

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

## Sửa đổi 2026-11-14 — MERCENARY-001: danh sách id trong payload; event theo trận phát từ job

Bổ sung, không thay §Quyết định 1–8.

- **Quy tắc 2 — ngoại lệ có phạm vi:** payload được mang **danh sách id nhỏ, có chặn trên theo nghiệp vụ** khi người nghe cần đúng tập đối tượng vừa đổi trạng thái và không truy vấn lại được chính xác sau commit (trạng thái có thể đã đổi tiếp). Áp dụng: `MercenaryRecruitmentClosedEvent.rejectedApplicationIds` (đơn chờ của **một** tin vừa bị từ chối tự động), `MercenaryGameCancelledEvent.applicationIds` (đơn đã duyệt của **một** trận — ≤ tổng số lượng các tin của trận, mỗi tin ≤ 10). Vẫn chỉ id, không PII. Danh sách không chặn được trên (vd mọi thành viên của mọi đội) vẫn theo quy tắc 2 gốc — người nghe tự truy vấn.
- **Event theo trận khi nguồn là miền khác:** cần báo mọi mercenary đã duyệt khi kèo (MATCH-001) bị huỷ. Không dùng listener `@TransactionalEventListener` trên `MatchCancelledEvent` để phát event mới: listener chạy sau commit trên luồng request và cần một truy vấn, làm tăng số truy vấn của MATCH-001 2.5 / 2.10 (đang chạm ngưỡng NFS W6). Thay bằng job định kỳ của miền nhận (ADR-0012 claim `SKIP LOCKED`) đánh dấu cột "đã báo" trên đơn rồi phát `MercenaryGameCancelledEvent` trong transaction của lô — cùng nguyên tắc "chỉ phát trong transaction ghi, giao sau commit" (quy tắc 3–4); thông báo trễ tối đa một chu kỳ job (~5 phút — BR-ME09, user chốt 2026-11-14). Khi miền nhận tự gây ra việc huỷ (huỷ trận ngoài — MERCENARY-001 2.32) thì đánh dấu và phát ngay trong request; cột "đã báo" chống phát trùng giữa request và job.
- Chi tiết: `docs/agent/design/MERCENARY-001/Component_Class_Design.md` §4.9, §4.11; `Processing_Flow.md` §3.12, §3.13.

## Sửa đổi 2026-11-16 — NOTIFICATION-001: listener thông báo bất đồng bộ (ADR-0015)

Bổ sung, không thay §Quyết định 1–8.

- Listener tạo thông báo in-app (NOTIFICATION-001) vẫn nghe `@TransactionalEventListener(AFTER_COMMIT)` (quy tắc 4) nhưng chạy `@Async` trên executor riêng theo [ADR-0015](0015-notification-async-listener-and-user-channel.md). Hệ quả "Listener chạy đồng bộ trên thread của request sau commit … (NOTIFICATION-001 cân nhắc `@Async`)" không còn áp cho listener thông báo, trừ khi hàng đợi của executor đầy (`CallerRunsPolicy` — listener chạy trên luồng phát, xem ADR-0015); listener khác vẫn chạy đồng bộ trừ khi có ADR riêng.
- Tính không bền (Hệ quả "Không bền") giữ nguyên; với listener thông báo, rủi ro mất rộng hơn một chút (hàng đợi trong bộ nhớ) — ADR-0015 Hệ quả.
- §Cần theo dõi "Khi Accepted: VENUE-001 `[cần xác nhận #10]` đóng theo ADR này" đã xong: VENUE-001 `Component_Class_Design.md` §12 #10 đã đóng.
