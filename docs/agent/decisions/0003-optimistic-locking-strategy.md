# ADR-0003: Optimistic locking bằng @Version, tự retry ngoài transaction, hết lượt trả 409

| | |
|---|---|
| **Trạng thái** | Accepted |
| **Ngày** | 2026-11-02 |
| **Người quyết định** | Product Owner (chốt retry 3 lần → 409) · solution-architect (đề xuất cách thực hiện) |
| **Liên quan** | `docs/agent/design/USER-001/Processing_Flow.md` §5 · `docs/agent/convention-backend.md` §9 · ngoại lệ có phạm vi cho `deleteAccount` (khoá bi quan): [ADR-0007](0007-scoped-pessimistic-lock-for-account-deletion.md) · `docs/agent/design/USER-002/Processing_Flow.md` §4–§5 (disband, transfer, thứ tự khoá) · [ADR-0005](0005-captain-must-hand-over-before-account-deletion.md) |
| **Sửa đổi** | 2026-11-06 — `deleteAccount` là ngoại lệ có phạm vi: bất biến giữ bằng khoá bi quan theo ADR-0007, không dựa vào kiểm `@Version` · 2026-11-06 — phạm vi của "không deadlock" ở Hệ quả (chỉ đúng cho bước đọc) |

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

## Sửa đổi 2026-11-06 — Ngoại lệ có phạm vi cho xoá tài khoản (ADR-0007)

- `DELETE /api/v1/profile` (`ProfileServiceImpl.deleteAccount`) không dựa vào `@Version` để giữ bất biến: khoá `PESSIMISTIC_WRITE` các row `team_member` ACTIVE/PENDING của user (theo `id`), kiểm captain trên dữ liệu đã khoá, rồi bulk UPDATE — xem [ADR-0007](0007-scoped-pessimistic-lock-for-account-deletion.md) (Accepted). Hàm vẫn chạy qua `OptimisticLockRetrier` (mỗi lần thử một transaction mới, `TransactionTemplate`).
- Lý do: các bước ghi của hàm là bulk UPDATE, chỉ tăng `version` mà không kiểm, nên `@Version` không bảo vệ bất biến nhiều dòng của chính hàm này.
- Mọi luồng ghi khác trên `team` / `team_member` giữ nguyên quyết định của ADR này (`@Version`, tối đa 3 lần thử → `409 TEAM_014`).

## Sửa đổi 2026-11-06 — Phạm vi của "không deadlock"

- Câu "không deadlock" ở §Hệ quả chỉ đúng cho bước **đọc**. Bước ghi (`UPDATE … WHERE version = ?` lúc flush/commit) vẫn giữ khoá dòng tới hết transaction; luồng ghi nhiều row `team_member` (transfer: captain cũ + người nhận; disband: captain rồi bulk mọi row của đội) có thể deadlock nếu hai transaction khoá theo thứ tự khác nhau. Hiện dựa vào thứ tự flush cố định — xem USER-002 `Processing_Flow.md` §5.
- Từ ADR-0007, `deleteAccount` khoá bi quan theo `id` → có thể khép vòng chờ với transfer/disband trên các đội có chung thành viên: xem [ADR-0007](0007-scoped-pessimistic-lock-for-account-deletion.md) §Hệ quả (không chép lại ở đây; kịch bản suy ra từ đọc code, chưa tái hiện).
- Deadlock (`40P01`) không được `OptimisticLockRetrier` bắt (chỉ bắt `ObjectOptimisticLockingFailureException`) → bên bị PostgreSQL huỷ trả 500. Theo dõi theo ADR-0007 §Cần theo dõi.
- Quyết định của ADR này không đổi.
