# ADR-0013: Cột uy tín chỉ ghi bằng `UPDATE` nguyên tử qua `ReputationService`

| | |
|---|---|
| **Trạng thái** | Accepted (chủ repo chốt 2026-11-16) |
| **Ngày** | 2026-11-09 |
| **Người quyết định** | solution-architect (đề xuất) · chủ repo (chốt 2026-11-16) |
| **Sửa đổi** | 2026-11-12 — REPUTATION-001: bỏ cột đếm trận / sao trung bình (tính khi đọc); trả điểm cũ/mới bằng `RETURNING`; `verified_at` chỉ ghi bằng câu có điều kiện · 2026-11-21 — SYSTEM-002: migration REPUTATION-001 V8 → V9 ([ADR-0017](0017-vietnam-local-datetime.md)) · 2026-11-27 — REPUTATION-001 v2.1 (user chốt): câu ghi đọc bảng khác để quyết định → khoá dòng bằng câu trước; cột của chính dòng đọc trong CTE `locked` |
| **Liên quan** | [ADR-0003](0003-optimistic-locking-strategy.md) · Business_Rules §3.1, §7 (SYSTEM-001) · `docs/agent/design/MATCH-001/Component_Class_Design.md` §4.8, §6 · `docs/agent/design/REPUTATION-001/Component_Class_Design.md` §4.6, §5 · `docs/agent/design/MERCENARY-001/Component_Class_Design.md` §4.10, §5.2 (điểm uy tín cá nhân: hoàn thành khi được chấm sao, rút lui, report, hoàn điểm; huy hiệu cá nhân) · [ADR-0017](0017-vietnam-local-datetime.md) (thời gian giờ Việt Nam) |

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

## Sửa đổi 2026-11-21 — SYSTEM-002: số migration REPUTATION-001

- Migration của REPUTATION-001 nêu ở Sửa đổi 2026-11-12 ("migration V8") đổi thành **V9**: V6 thuộc SYSTEM-002 ([ADR-0017](0017-vietnam-local-datetime.md) Quyết định 5), các mã chưa code dời +1. Số thực chốt lại khi bắt đầu code REPUTATION-001 (`docs/agent/design/REPUTATION-001/Component_Class_Design.md` §5.1).
- Quyết định của ADR này không đổi.

## Sửa đổi 2026-11-27 — REPUTATION-001 v2.1: khoá trước khi đọc bảng khác; cột của chính dòng đọc trong CTE `locked`

User chốt 2026-11-27. Bổ sung gạch "Điểm thực thay đổi" của Sửa đổi 2026-11-12; thu hẹp "một câu SQL mỗi thay đổi" (§Hệ quả) và "trong **một** câu" (Sửa đổi 2026-11-12) cho luồng thuộc quy tắc 1 dưới:

1. **Câu ghi đọc bảng khác để quyết định** (vd `NOT EXISTS (SELECT 1 FROM report …)` xét trận sạch) → **MUST** khoá các dòng sẽ ghi bằng một câu riêng **trước** đó, cùng transaction (`SELECT id … WHERE id = ANY(:ids) ORDER BY id FOR NO KEY UPDATE` — thứ tự `id` tăng dần), rồi mới chạy câu cập nhật. Áp: `applyMatchCompletion` = `TeamRepository.lockForReputation(ids)` → `applyCompletion` (`docs/agent/design/REPUTATION-001/Component_Class_Design.md` §4.6, §5.2).
2. **Cột của chính dòng dùng để quyết định** (điểm, chuỗi, bộ đếm report) → **MUST** đọc trong CTE `locked` (dòng đã khoá `FOR NO KEY UPDATE`) hoặc từ dòng đích của chính câu `UPDATE` (vd `SET … = t.reliability_score − …` — dòng đích luôn được đọc lại bản mới nhất); **không** đọc qua lần quét thứ hai của bảng đó (subquery / join khác) trong cùng câu. Áp: `applyReportPenalty` đọc `next_count`, `applyCompletion` đọc `old_score`, `old_streak` trong `locked`.
3. Câu chỉ dùng cột của chính dòng (`adjustReliability`, `applyReportPenalty`, `refundReportPenalty`) giữ một câu như Sửa đổi 2026-11-12.

- **Lý do:** ở READ COMMITTED (isolation mặc định), mỗi câu lấy ảnh chụp lúc bắt đầu. Câu phải chờ khoá dòng chỉ đọc lại bản mới nhất của **dòng bị khoá / cập nhật**; subquery đọc bảng khác và lần quét thứ hai của cùng bảng vẫn dùng ảnh chụp đầu câu. Khoá trong CTE của chính câu cập nhật vì vậy không làm câu thấy report commit trong lúc chờ. Câu chạy sau khi đã có khoá lấy ảnh chụp mới → thấy mọi report đã commit của các dòng đó.
- **Bằng chứng** (integration test PostgreSQL, `ReputationRaceTest`): trước khi sửa — report bùng giữ khoá đội khi đánh giá hoàn tất trận → đội bị report vẫn +1 chuỗi (chuỗi 1 thay vì 0); hai report cùng loại đồng thời trừ cùng bậc (5 + 5 thay vì 5 + 8). Sau khi sửa PASS: `completionWaitingOnReportPenaltySeesTheReport`, `reportWaitingOnCompletionIsAppliedAsReportAfterCompletion`, `sameTypePenaltiesAtOnceUseConsecutiveTiers`.
- **Hệ quả:** luồng thuộc quy tắc 1 thêm 1 truy vấn và giữ khoá dòng qua 2 câu (cùng transaction, nhả khi commit / rollback) — REPUTATION-001 2.1 hoàn tất trận 5 → 6 truy vấn (= W6, user duyệt 2026-11-26). Thứ tự khoá `id` tăng dần như CTE cũ → không thêm vòng chờ [Inference — chưa tái hiện deadlock]. Ngoại lệ tương ứng của ADR-0003 (Sửa đổi 2026-11-12 "không giữ khoá qua nhiều câu"): [ADR-0003](0003-optimistic-locking-strategy.md) Sửa đổi 2026-11-27.
- **Áp cho `"user"`:** cùng hai quy tắc cho cột uy tín cá nhân — MERCENARY-001 `applyMercenaryCompletion` xét report của trận bằng bảng khác (`docs/agent/design/MERCENARY-001/Processing_Flow.md` §5 đã ghi rủi ro "kiểm report bằng snapshot đầu câu") → khoá dòng `"user"` bằng câu riêng trước khi cập nhật khi MERCENARY-001 code; bộ đếm report cá nhân đọc trong CTE `locked`.
- §Quyết định 1–3 không đổi.
