# ADR-0003: Optimistic locking bằng @Version, tự retry ngoài transaction, hết lượt trả 409

| | |
|---|---|
| **Trạng thái** | Accepted |
| **Ngày** | 2026-11-02 |
| **Người quyết định** | Product Owner (chốt retry 3 lần → 409) · solution-architect (đề xuất cách thực hiện) |
| **Liên quan** | `docs/agent/design/USER-001/Processing_Flow.md` §5 · `docs/agent/convention-backend.md` §9 · ngoại lệ có phạm vi cho `deleteAccount` (khoá bi quan): [ADR-0007](0007-scoped-pessimistic-lock-for-account-deletion.md) · `docs/agent/design/USER-002/Processing_Flow.md` §4–§5 (disband, transfer, thứ tự khoá) · [ADR-0005](0005-captain-must-hand-over-before-account-deletion.md) · `docs/agent/design/VENUE-001/Processing_Flow.md` §5 (khoá dòng `venue` cho ghi ảnh) · `docs/agent/design/MATCH-001/Component_Class_Design.md` §5.2 (`match_request`), `docs/agent/design/MATCH-002/Processing_Flow.md` §5 (`pairing`, thứ tự khoá `match` → `pairing`) · `docs/agent/design/REPUTATION-001/Component_Class_Design.md` §5.2 (`report`, `dispute`) · [ADR-0013](0013-reputation-columns-atomic-update.md) (khoá dòng `team` khi ghi điểm uy tín) |
| **Sửa đổi** | 2026-11-06 — `deleteAccount` là ngoại lệ có phạm vi: bất biến giữ bằng khoá bi quan theo ADR-0007, không dựa vào kiểm `@Version` · 2026-11-06 — phạm vi của "không deadlock" ở Hệ quả (chỉ đúng cho bước đọc) · 2026-11-06 — retrier thử lại cả deadlock (`40P01`) · 2026-11-07 — đổi tên `OptimisticLockRetrier` → `ConcurrencyConflictRetrier` · 2026-11-09 — ghi ảnh sân tuần tự hoá bằng khoá dòng `venue` (không retry); retrier thêm overload nhận mã lỗi hết lượt · 2026-11-10 — CAS tự viết `UPDATE … WHERE status/revision` (không `@Version`) cho `match_request`, `pairing` · 2026-11-12 — thêm `report`, `dispute` vào ngoại lệ CAS tự viết; khoá dòng `team` khi ghi điểm uy tín (ADR-0013) |

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

## Sửa đổi 2026-11-06 — Retrier thử lại cả deadlock

- Bổ sung cho §Quyết định (retry tối đa 3 lần → `409 TEAM_014`): `OptimisticLockRetrier` thử lại cả `PessimisticLockingFailureException` có SQLState `40P01` (deadlock) — PostgreSQL huỷ một bên; [Inference] transaction thử lại thường thành công vì bên kia đã xong (kiểm chứng bằng một kịch bản integration). Mỗi lần gặp deadlock retrier ghi log `WARN` kèm SQLState và số lần thử. Lỗi khoá khác (NOWAIT `55P03`, lock timeout, serialization failure `40001`) ném nguyên, không thử lại.
- Thay câu "Deadlock (`40P01`) không được `OptimisticLockRetrier` bắt … → 500" ở mục Sửa đổi "Phạm vi của 'không deadlock'": nay deadlock lặp lại đủ 3 lần thử → `409 TEAM_014`.
- Kiểm chứng trên PostgreSQL: deadlock thật được Spring dịch thành `CannotAcquireLockException` (SQLState `40P01`).

## Sửa đổi 2026-11-07 — Đổi tên retrier

- `OptimisticLockRetrier` đổi tên thành `ConcurrencyConflictRetrier` (cùng package `application/support/`; field ở caller: `concurrencyConflictRetrier`). Từ mục Sửa đổi "retrier thử lại cả deadlock" class không chỉ xử lý optimistic lock nên tên cũ sai nghĩa.
- Không đổi quyết định hay hành vi. Tên `OptimisticLockRetrier` ở các mục phía trên giữ nguyên (không viết lại ADR đã `Accepted`), đọc là `ConcurrencyConflictRetrier`.

## Sửa đổi 2026-11-09 — Ghi ảnh sân khoá dòng `venue`; retrier nhận mã lỗi theo miền

- **Ghi ảnh sân** (thêm ảnh, đổi ảnh chính, xoá ảnh — `docs/agent/design/VENUE-001/` 2.5, 2.6, 2.13) tuần tự hoá theo địa điểm bằng khoá dòng `venue` ở **đầu** transaction: `UPDATE venue SET version = version + 1 WHERE id = ?` — **không** so `version`, **không** chạy qua `ConcurrencyConflictRetrier`. Request sau chờ request trước commit rồi đọc dữ liệu mới; không thất bại do xung đột nên không cần retry. Entity `Venue` được nạp **sau** câu khoá và không bị ghi lại.
- Lý do: chỉ kiểm `@Version` lúc commit (vd `OPTIMISTIC_FORCE_INCREMENT`) thì hai request cùng đặt ảnh chính đều đã INSERT trước điểm tuần tự hoá → đụng unique index một phần `idx_venue_image_primary` → 500. Khoá trước khi đọc số ảnh / ảnh chính loại bỏ trường hợp này.
- `version` vẫn tăng để các luồng ghi `venue` dùng `@Version` (sửa địa điểm, admin ngừng / kích hoạt lại) đang giữ bản cũ phát hiện xung đột và retry như quyết định gốc.
- Phạm vi: chỉ `venue` / `venue_image`. Mọi luồng khác giữ quyết định của ADR này.
- `ConcurrencyConflictRetrier` thêm overload `execute(ErrorCode exhaustedCode, Supplier<T>)`; bản cũ giữ `TEAM_014` nên luồng team không đổi. VENUE-001 dùng cho sửa địa điểm, admin ngừng / kích hoạt lại, xử lý report; hết lượt → `409 VENUE_011`.
- Deadlock: ghi ảnh khoá `venue` rồi `venue_image`, không chạm `venue_report` → không thấy vòng chờ với các luồng VENUE-001 khác [Inference — chưa tái hiện; nếu gặp sẽ bọc retrier].

## Sửa đổi 2026-11-10 — CAS tự viết cho `match_request` và `pairing`

- **Ngoại lệ:** `match_request` (MATCH-001) và `pairing` (MATCH-002) **không** có cột / `@Version`. Mọi đổi trạng thái là câu `UPDATE … WHERE` tự viết, điều kiện nằm trong chính câu ghi: trạng thái nguồn (`status = 'PENDING'` / `'NEGOTIATING'`) và với `pairing` thêm `revision = :expectedRevision`. 0 dòng → hoặc trả **mã nghiệp vụ trực tiếp** khi trạng thái nguồn đã đổi là kết luận đủ (`match_request`: chấp nhận / từ chối → `MATCH_008`, rút → `MATCH_015` — MATCH-001 `Processing_Flow.md` §3), hoặc **ném exception xung đột** → `ConcurrencyConflictRetrier` thử lại như quyết định gốc (mỗi lần thử một transaction, tối đa 3 lần, hết lượt → mã lỗi theo miền `MATCH_014`) và lần thử sau đọc lại, guard trả mã nghiệp vụ đúng (`pairing`: vd `PAIRING_005`, `PAIRING_006`). Entity chỉ INSERT qua JPA.
- **Lý do:**
  1. Đổi trạng thái gồm câu **bulk nhiều dòng** (thay thế mọi yêu cầu `PENDING` còn lại của kèo sau khi chấp nhận một yêu cầu; xác nhận một pairing + thay thế các pairing khác của kèo trong một câu; hết hạn / huỷ kèo chuyển mọi dòng của kèo). `@Version` chỉ kiểm từng entity đã nạp → phải nạp từng dòng (truy vấn tăng theo số dòng) hoặc bulk tăng `version` mà không kiểm (không bảo vệ gì — như mục Sửa đổi 2026-11-06 về `deleteAccount`).
  2. `revision` của `pairing` là **hợp đồng FE** (gửi lên ở 2.5–2.7 MATCH-002, lệch → `409 PAIRING_006`): một cột vừa là số phiên bản gói người dùng đã thấy vừa là điều kiện CAS; thêm `@Version` song song là hai nguồn cho cùng một khái niệm.
  3. Giữ ngưỡng NFS **≤ 6 truy vấn** cho thao tác ghi (`non-functional-standards.md` §3): điều kiện trong câu ghi không cần SELECT kiểm trước, `UPDATE … RETURNING` trả luôn dữ liệu cho response.
- **Khi nào được dùng ngoại lệ này** (cả ba điều kiện): (a) mọi thay đổi trạng thái của bảng đi qua câu ghi native có điều kiện — không có đường ghi nào qua dirty checking; (b) điều kiện CAS diễn đạt đầy đủ được bằng cột nghiệp vụ (trạng thái nguồn, số phiên bản nghiệp vụ); (c) có test đồng thời chứng minh 0 dòng → mã nghiệp vụ / hành vi đúng (trực tiếp hoặc sau khi thử lại). Không thoả → dùng `@Version` theo quyết định gốc.
- **Không phải ngoại lệ:** câu native trên bảng **có** `@Version` mà kiểm và tăng chính cột đó (`… WHERE version = :v`, `SET version = version + 1`, vd hoàn tất ghép kèo luồng B trên `match`) — vẫn là khoá lạc quan của quyết định gốc; giữ quy tắc bulk tự `SET version = version + 1`.
- **Thứ tự khoá:** luồng ghi cả `match` và `pairing` / `match_request` khoá `match` trước. Luồng chỉ khoá `pairing` thấy cần khoá `match` (vd phía kia vừa xác nhận) → ném xung đột, rollback, thử lại theo thứ tự đúng — không khoá `match` khi đang giữ `pairing` (MATCH-002 `Processing_Flow.md` §5).
- Mọi bảng khác giữ quyết định của ADR này.

## Sửa đổi 2026-11-12 — CAS tự viết cho `report` và `dispute`

- Thêm `report`, `dispute` (REPUTATION-001 — `docs/agent/design/REPUTATION-001/Component_Class_Design.md` §5.2) vào ngoại lệ của mục Sửa đổi 2026-11-10; thoả cả ba điều kiện:
  (a) mọi đổi trạng thái là câu native có điều kiện — `report`: `AUTO_ACCEPTED → DISPUTED` (kèm hạn 24 giờ trong câu), `DISPUTED → CONFIRMED|REJECTED`; `dispute`: `PENDING → RESOLVED`; entity map cột trạng thái `updatable = false`, chỉ INSERT + đọc qua JPA (riêng `report.reliability_penalty` ghi một lần bằng dirty checking ngay trong transaction tạo report);
  (b) điều kiện CAS là cột trạng thái nghiệp vụ;
  (c) test đồng thời: hai Admin quyết định cùng tranh chấp → bên sau `DISPUTE_002`; tranh chấp sát hạn / trùng → `REPORT_007` / `REPORT_008`.
- 0 dòng → quyết định Admin: mã nghiệp vụ trực tiếp (`DISPUTE_001`/`DISPUTE_002`); tranh chấp: ném xung đột → `ConcurrencyConflictRetrier` thử lại, lần sau guard trả mã đúng; hết lượt → `409 MATCH_014`.
- Không phải ngoại lệ: `UPDATE match SET status = 'COMPLETED', version = version + 1 WHERE status = 'ARCHIVED'` (đánh giá đầu tiên hoàn tất trận) — bảng có `@Version`, câu tự tăng `version` như `expireIfDue` của MATCH-001.
- Điểm uy tín trên `team` ở luồng người dùng (đánh giá, report, quyết định tranh chấp) ghi bằng một câu khoá dòng `FOR NO KEY UPDATE` trong CTE rồi `UPDATE … RETURNING` — cùng mức khoá với chính câu UPDATE, không giữ khoá qua nhiều câu; chi tiết [ADR-0013](0013-reputation-columns-atomic-update.md).
- Mọi bảng khác giữ quyết định của ADR này.
