# ADR-0013: Cột uy tín chỉ ghi bằng `UPDATE` nguyên tử qua `ReputationService`

| | |
|---|---|
| **Trạng thái** | Proposed |
| **Ngày** | 2026-11-09 |
| **Người quyết định** | solution-architect (đề xuất) · chờ chủ repo duyệt |
| **Sửa đổi** | 2026-11-12 — REPUTATION-001: bỏ cột đếm trận / sao trung bình (tính khi đọc); trả điểm cũ/mới bằng `RETURNING`; `verified_at` chỉ ghi bằng câu có điều kiện |
| **Liên quan** | [ADR-0003](0003-optimistic-locking-strategy.md) · Business_Rules §3.1, §7 (SYSTEM-001) · `docs/agent/design/MATCH-001/Component_Class_Design.md` §4.8, §6 · `docs/agent/design/REPUTATION-001/Component_Class_Design.md` §4.6, §5 · MERCENARY-001 (sau) |

## Bối cảnh (Context)
- Reliability Score đội (`team.reliability_score`) và cá nhân (`"user".reliability_score`) bị cộng/trừ từ nhiều luồng: huỷ kèo / rút yêu cầu (MATCH-001), hoàn thành trận, rating, report, tranh chấp (REPUTATION-001), rút mercenary (MERCENARY-001). Điểm chặn [0, 100] (BR-R03).
- Entity `Team` có `@Version` nhưng Hibernate ghi **mọi cột** khi flush: luồng sửa đội / giải tán đang giữ bản cũ sẽ ghi đè điểm vừa trừ nếu không đụng version; `User` không có version (ADR-0003) nên `PATCH /profile` có thể ghi đè điểm mà không ai phát hiện.
- Tăng `version` khi trừ điểm sẽ làm các thao tác đội không liên quan thất bại/retry (`TEAM_014`).

## Quyết định (Decision)
Chúng tôi ghi cột uy tín **chỉ** qua `ReputationService`, bằng một câu `UPDATE` nguyên tử:

1. `UPDATE <bảng> SET reliability_score = LEAST(100, GREATEST(0, reliability_score + :delta)) WHERE id = :id` — chặn [0,100] trong DB, cộng dồn đúng khi đồng thời.
2. Entity map cột uy tín `updatable = false` (vẫn `insertable` với mặc định 80) → dirty checking của `Team`/`User` không bao giờ ghi đè. Không tăng `version`.
3. `ReputationService` chạy `MANDATORY` (cùng transaction với thao tác gây ra thay đổi), phát `ReliabilityScoreChangedEvent` (ADR-0011).
4. MATCH-001 áp cho `reliability_score`. REPUTATION-001 mở rộng cùng mẫu cho `clean_streak`, `report_count_*`, `total_matches_*` (và đánh dấu `updatable = false`).

**Phương án đã loại**
- Đọc entity, sửa, CAS `@Version`: retry chéo với mọi thao tác đội; `User` không có version.
- Thêm `@Version` cho `User`: mọi thao tác hồ sơ phải chạy qua retrier (thay đổi USER-001 rộng).
- `@DynamicUpdate`: chỉ ghi cột đổi nhưng vẫn mất cập nhật khi hai luồng cùng sửa điểm qua entity; không chặn [0,100] ở DB.
- Bảng `reliability_score` riêng (Data_Model_Conceptual §2.7): schema V1 đã đặt cột trên `team`/`"user"`; tách bảng là thay đổi schema lớn không cần cho MVP.

## Hệ quả (Consequences)

**Tích cực**
- Không mất cập nhật, không retry chéo với thao tác đội/hồ sơ; một câu SQL mỗi thay đổi.

**Tiêu cực / đánh đổi**
- Entity đã nạp trong cùng transaction giữ giá trị cũ sau `UPDATE` → muốn đọc điểm mới phải truy vấn lại.
- Không lưu lịch sử thay đổi điểm (Data_Model_Conceptual §9 #10 — chỉ giá trị hiện tại); event là dấu vết duy nhất (không bền).
- Mọi code ghi điểm phải đi qua service; ghi thẳng qua entity bị bỏ qua lặng lẽ (cần test).

**Cần theo dõi**
- Cần lịch sử / kiểm toán điểm → bảng log thay đổi.
- REPUTATION-001 cần điểm mới để cấp/thu hồi badge → thêm biến thể đọc lại hoặc `RETURNING`.

## Sửa đổi 2026-11-12 — REPUTATION-001: tính khi đọc, `RETURNING`, huy hiệu

Bổ sung, không thay §Quyết định 1–3. Thay vế cuối của §Quyết định 4 (danh sách cột REPUTATION-001 mở rộng):

- **Bỏ `total_matches_completed`, `total_matches_confirmed`, `average_rating`** (cả `team` và `"user"`, migration V8 — `docs/agent/design/REPUTATION-001/Component_Class_Design.md` §5.1). Completion Rate và sao trung bình **tính khi đọc** từ `match` / `report` / `rating` (user chốt Q5, 2026-11-12). Lý do: Completion Rate phụ thuộc trạng thái report có thể đổi về sau (report bùng loại trận khỏi tử số ngay, Admin bác thì tính lại; trận `ARCHIVED` có report bùng vào mẫu số) — giữ bộ đếm đồng bộ với mọi chuyển trạng thái đó dễ lệch, trong khi đọc từ dữ liệu gốc là 1 câu có index trên trận của một đội. Data_Model_Conceptual §9 #9 đã chọn tính sao trung bình on-the-fly.
- Mẫu `UPDATE` nguyên tử áp cho `reliability_score`, `clean_streak`, `report_count_*` (đánh dấu `updatable = false` trên `Team`/`User`). Một thao tác ghi mọi cột uy tín của một đội trong **một** câu (vd hoàn tất trận: +2, chuỗi, thưởng mốc, reset bộ đếm; report: bộ đếm, chuỗi, trừ điểm).
- **Điểm thực thay đổi:** câu ghi khoá dòng (`FOR NO KEY UPDATE` trong CTE, theo thứ tự `id` khi nhiều đội) rồi `UPDATE … RETURNING` điểm cũ và mới — dùng để lưu số điểm report đã thực trừ (hoàn đúng số đó khi Admin bác, Q9) và làm `delta` của `ReliabilityScoreChangedEvent`. Đóng mục "Cần theo dõi" về `RETURNING`.
- **`team.verified_at`** (huy hiệu xác minh, dữ liệu dẫn xuất — ADR-0011 quy tắc 6): entity map `insertable = false, updatable = false`; chỉ ghi bằng câu `UPDATE … WHERE (verified_at IS NOT NULL) IS DISTINCT FROM <đủ điều kiện> RETURNING` chạy sau commit và trong job đối soát hằng ngày.

**Cần theo dõi (thêm):** thống kê tính khi đọc quét mọi trận của một đội — số trận/đội lớn làm 2.9 hoặc job huy hiệu chậm (đo `EXPLAIN ANALYZE`) → cân nhắc lại cột đếm kèm cơ chế đối soát.
