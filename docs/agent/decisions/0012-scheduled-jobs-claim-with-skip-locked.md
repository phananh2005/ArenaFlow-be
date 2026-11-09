# ADR-0012: Scheduled job chuyển trạng thái claim lô bằng `FOR NO KEY UPDATE SKIP LOCKED`

| | |
|---|---|
| **Trạng thái** | Proposed |
| **Ngày** | 2026-11-09 |
| **Người quyết định** | solution-architect (đề xuất) · chờ chủ repo duyệt |
| **Liên quan** | [ADR-0003](0003-optimistic-locking-strategy.md) (ngoại lệ có phạm vi — cần mục Sửa đổi khi ADR này Accepted) · [ADR-0007](0007-scoped-pessimistic-lock-for-account-deletion.md) §Phạm vi ngoại lệ · [ADR-0011](0011-domain-events-after-commit.md) · `docs/agent/design/MATCH-001/Processing_Flow.md` §3.13, §5 · `docs/agent/design/VENUE-001/Component_Class_Design.md` §12 #14 |

## Bối cảnh (Context)
- MATCH-001 cần job mỗi 5 phút: `OPEN` quá `expires_at` → `EXPIRED` (kèm yêu cầu `PENDING`), `CONFIRMED` quá `match_end_time` → `ARCHIVED`; mỗi kèo chuyển phải phát đúng một event (ADR-0011). Sau này: pairing (MATCH-002), tin tuyển (MERCENARY-001), retention (NOTIFICATION-001).
- Người dùng ghi cùng dòng `match` bằng CAS `@Version` (ADR-0003); job chạy song song với request.
- Cần biết **chính xác** các dòng job đã chuyển. `UPDATE … RETURNING` qua Spring Data chưa được kiểm chứng (VENUE-001 `[cần xác nhận #11]`); `SELECT` rồi `UPDATE` không khoá → event có thể phát cho dòng đã bị người dùng đổi giữa hai câu.
- Repo giả định một instance ứng dụng (VENUE-001 `[cần xác nhận #14]`), chưa có ShedLock/Redis.
- ADR-0003 loại `SELECT … FOR UPDATE`; ADR-0007 yêu cầu ADR mới cho ngoại lệ khoá bi quan khác.

## Quyết định (Decision)
Chúng tôi cho **job chuyển trạng thái theo lô** dùng mẫu **claim → cập nhật → phát event** trong một transaction mỗi lô:

1. Claim: native `SELECT <cột cần cho event> FROM <bảng> WHERE <điều kiện đến hạn> ORDER BY <cột thời gian>, id LIMIT :n FOR NO KEY UPDATE SKIP LOCKED`.
2. Cập nhật: bulk `UPDATE … WHERE id IN (:ids) AND <lặp điều kiện trạng thái>` (`version = version + 1` khi bảng có version), rồi bảng con nếu cần.
3. Phát event cho đúng các id đã claim; commit; lặp tới khi lô < `n` hoặc hết ngân sách lô.

- `FOR NO KEY UPDATE` (không phải `FOR UPDATE`): tương thích `FOR KEY SHARE` mà INSERT bảng con lấy qua FK → không chặn / không deadlock với luồng tạo con.
- `SKIP LOCKED`: dòng đang bị request giữ khoá được bỏ qua, xử lý ở lần sau; job không chờ request và ngược lại request chỉ chờ một lô ngắn.
- **Nhiều instance:** hai job đồng thời không claim trùng dòng → không chuyển trùng, không event trùng. Vì vậy MATCH-001 không cần khoá phân tán. Job không theo mẫu này (vd VENUE-001 cảnh báo 180 ngày, dọn asset) vẫn giả định một instance.
- Phạm vi ngoại lệ ADR-0003: chỉ job hệ thống chuyển trạng thái theo lô; luồng người dùng vẫn dùng `@Version` + retrier.

**Phương án đã loại**
- `UPDATE … RETURNING`: một câu, nhưng cách chạy qua Spring Data chưa kiểm chứng; vẫn giữ làm phương án nếu VENUE-001 #11 chứng minh được.
- `SELECT` không khoá rồi `UPDATE … WHERE id IN … AND <điều kiện>`: event sai cho dòng bị đổi giữa hai câu.
- CAS từng dòng qua entity: số truy vấn tăng theo số dòng (NFS §1).
- ShedLock / advisory lock toàn job: thêm bảng/hạ tầng; vẫn không giải quyết event chính xác.

## Hệ quả (Consequences)

**Tích cực**
- Event đúng một lần cho mỗi dòng chuyển; an toàn khi chạy nhiều instance.
- Số truy vấn cố định mỗi lô (2–3), transaction ngắn.

**Tiêu cực / đánh đổi**
- Lệch ADR-0003 (khoá bi quan) — người đọc phải biết ngoại lệ này.
- Request ghi đúng dòng đang claim phải chờ lô đó commit, rồi CAS thất bại → retry → lỗi nghiệp vụ (vd `MATCH_004`).
- Dòng bị khoá lâu bởi request có thể trễ thêm một chu kỳ.

**Cần theo dõi**
- Log `ERROR` lô job lặp lại, hoặc `40P01` liên quan job → xem lại thứ tự khoá.
- Khi ADR này Accepted: thêm mục `Sửa đổi` vào ADR-0003 trỏ tới đây.
- Job mới (MATCH-002, MERCENARY-001, NOTIFICATION-001) theo mẫu này hoặc ghi rõ giả định một instance.
