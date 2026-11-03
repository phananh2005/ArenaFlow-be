# ADR-0003: Optimistic locking bằng @Version, tự retry ngoài transaction, hết lượt trả 409

| | |
|---|---|
| **Trạng thái** | Accepted |
| **Ngày** | 2026-11-02 |
| **Người quyết định** | Product Owner (chốt retry 3 lần → 409) · solution-architect (đề xuất cách thực hiện) |
| **Liên quan** | `docs/agent/design/USER-001/Processing_Flow.md` §5 · `docs/agent/convention-backend.md` §9 |

## Bối cảnh (Context)
- Các thao tác trên `team`/`team_member` có thể chạy song song: sửa đội, chuyển quyền captain, chấp nhận lời mời, rời đội, admin đổi trạng thái, xoá tài khoản captain. Lost update làm sai số captain/thành viên.
- Mức tranh chấp thấp (thao tác do người dùng bấm, theo từng đội).
- Một instance PostgreSQL, chưa có Redis.
- Convention §9 đặt `@Transactional(readOnly = true)` ở mức class — nếu retry nằm trong transaction đó thì mọi lần thử dùng chung persistence context đã hỏng.

## Quyết định (Decision)
Chúng tôi dùng **JPA `@Version`** trên `Team` và `TeamMember` (cột `version BIGINT NOT NULL DEFAULT 0`, thêm ở V2), kèm retry tự động:

- Component `OptimisticLockRetrier` chạy mỗi lần thử trong **transaction mới** (`TransactionTemplate`); service method dùng retrier **không** có `@Transactional`.
- Bắt `ObjectOptimisticLockingFailureException` → thử lại ngay, không backoff; **tối đa 3 lần thử** (1 lần đầu + 2 lần retry) → hết → `409 TEAM_014`.
- Lỗi nghiệp vụ không retry. Mỗi lần thử chạy lại toàn bộ kiểm tra → kết quả có thể là lỗi nghiệp vụ thay cho 409.
- Bulk update (`@Modifying`) phải tự `SET version = version + 1`.
- `User`, `TeamCaptainHistory` không có version (chỉ chủ sở hữu sửa / chỉ insert).
- Trùng lặp lời mời/thành viên chặn bằng unique index một phần, không bằng version.

**Phương án đã loại**
- Pessimistic lock (`SELECT … FOR UPDATE`): giữ khoá suốt transaction, rủi ro deadlock khi khoá nhiều dòng `team_member`; không cần với mức tranh chấp thấp.
- Last-write-wins: mất cập nhật vai trò captain.
- Distributed lock (Redis): thêm hạ tầng chưa có.
- Retry bằng annotation (`@Retryable`) trên cùng bean với `@Transactional`: phụ thuộc thứ tự proxy, dễ retry bên trong transaction đã rollback-only.

## Hệ quả (Consequences)

**Tích cực**
- Không khoá khi đọc, không deadlock; người dùng hiếm khi thấy xung đột.
- Cơ chế dùng lại được cho Match (`match`, `match_request`) sau này.

**Tiêu cực / đánh đổi**
- Lệch convention §9 (transaction theo method, không theo class) — cần ghi vào convention.
- Mỗi lần retry lặp lại toàn bộ truy vấn của endpoint (số truy vấn nhân tối đa 3).
- Thao tác có side effect ngoài DB (notification sau này) phải đặt **sau** khi commit thành công, không nằm trong lần thử.

**Cần theo dõi**
- Tỷ lệ `TEAM_014` tăng → xem lại (backoff, pessimistic lock cho thao tác nóng).
- Khi thêm notification: dùng sự kiện sau commit (`@TransactionalEventListener(AFTER_COMMIT)`).
