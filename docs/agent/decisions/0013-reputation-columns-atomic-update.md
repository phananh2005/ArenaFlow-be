# ADR-0013: Cột uy tín chỉ ghi bằng `UPDATE` nguyên tử qua `ReputationService`

| | |
|---|---|
| **Trạng thái** | Proposed |
| **Ngày** | 2026-11-09 |
| **Người quyết định** | solution-architect (đề xuất) · chờ chủ repo duyệt |
| **Liên quan** | [ADR-0003](0003-optimistic-locking-strategy.md) · Business_Rules §3.1, §7 (SYSTEM-001) · `docs/agent/design/MATCH-001/Component_Class_Design.md` §4.8, §6 · REPUTATION-001, MERCENARY-001 (sau) |

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
