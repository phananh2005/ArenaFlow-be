# ADR-0005: Đội trưởng phải chuyển quyền hoặc giải tán đội trước khi xoá tài khoản; đội có trạng thái cuối DISBANDED

| | |
|---|---|
| **Trạng thái** | Proposed |
| **Ngày** | 2026-11-05 |
| **Người quyết định** | Product Owner (chốt nghiệp vụ USER-002) · solution-architect (đề xuất cách thực hiện) |
| **Liên quan** | Thay một phần [ADR-0002](0002-soft-delete-policy.md) §Quyết định #4 · ADR-0003 · `docs/agent/business-spec/USER-002/` (BR-T06 sửa, BR-T07..T14, BC-T02 sửa, BC-T06) · `docs/agent/design/USER-002/` |

## Bối cảnh (Context)
- ADR-0002 #4: captain còn thành viên ACTIVE khác → chặn xoá (`422 USER_003`); captain duy nhất → hệ thống tự chuyển đội sang `INACTIVE`.
- Hệ quả của #4: đội `INACTIVE` do captain duy nhất xoá tài khoản không còn captain ACTIVE. Admin không kích hoạt lại được (`TEAM_013`), không ai giải tán được. Đội tồn tại vĩnh viễn ở trạng thái không có chủ.
- `INACTIVE` đang mang hai nghĩa: Admin khoá (có thể mở lại) và đội bị bỏ (không mở lại được).
- USER-002 bổ sung thao tác đội trưởng giải tán đội. Domain Match sau này cần phân biệt đội tạm khoá với đội đã ngừng tồn tại.

## Quyết định (Decision)
Chúng tôi sẽ:
1. **Chặn xoá tài khoản** khi user còn là captain ACTIVE của bất kỳ đội nào chưa giải tán, **kể cả khi là người duy nhất** và kể cả đội đang bị Admin khoá. Trả `422 USER_003` (mở rộng điều kiện, giữ mã), message "Bạn phải chuyển quyền đội trưởng hoặc giải tán đội trước khi xóa tài khoản". Hệ thống **không tự đổi trạng thái đội** khi xoá tài khoản.
2. Thêm trạng thái **`DISBANDED`** cho `team`: chỉ captain ACTIVE chuyển vào (từ `ACTIVE` hoặc `INACTIVE`); là trạng thái cuối, không ai mở lại được, kể cả Admin. Mọi thao tác ghi trên đội `DISBANDED` trả `422 TEAM_016`.
3. Giải tán **không xoá dữ liệu**: membership ACTIVE → `LEFT`, lời mời PENDING → `REJECTED`; giữ lịch sử thành viên, lịch sử captain, `inactive_reason` của Admin; lưu riêng `disband_reason`, `disbanded_at`, `disbanded_by`. Nhất quán chính sách soft-delete còn lại của ADR-0002.
4. Bất biến: mỗi đội chưa giải tán luôn có đúng một captain ACTIVE (BC-T06). Các luồng ghi tranh chấp được tuần tự hoá bằng optimistic lock theo ADR-0003 (chi tiết `docs/agent/design/USER-002/Processing_Flow.md` §4–§5).

**Phương án đã loại**
- Giữ ADR-0002 #4 (tự `INACTIVE`): sinh đội không chủ, không xử lý được; trộn nghĩa "khoá" với "bỏ".
- Tự giải tán đội khi captain duy nhất xoá tài khoản: hành động không thể đảo ngược xảy ra ngầm, user không được cảnh báo.
- Tự chuyển quyền cho thành viên lâu nhất: thay đổi trách nhiệm của người khác khi họ chưa đồng ý.
- Hard-delete đội khi giải tán: trái chính sách giữ dữ liệu của ADR-0002 và mất lịch sử cho Match/đánh giá.

## Hệ quả (Consequences)

**Tích cực**
- Không còn đội không có captain; `INACTIVE` chỉ còn nghĩa "Admin khoá".
- User chủ động quyết định số phận đội trước khi rời hệ thống.
- Match sau này có trạng thái rõ ràng để chặn đội đã giải tán.

**Tiêu cực / đánh đổi**
- Thêm một bước cho user muốn xoá tài khoản (phải chuyển quyền hoặc giải tán trước).
- Thêm 3 cột và 2 CHECK constraint cho `team` (migration V4); mọi luồng ghi trên đội phải kiểm tra `DISBANDED`.
- Mời thành viên phải tăng `team.version` để không lọt lời mời PENDING vào đội vừa giải tán (5 → 6 query, bằng ngưỡng W6).

**Cần theo dõi**
- Khi triển khai Match: quyết định xử lý kèo/trận đang mở của đội khi giải tán (spec USER-002 `[cần xác nhận]` #1).
- Môi trường có dữ liệu đội `INACTIVE` do captain duy nhất xoá tài khoản (theo ADR-0002 #4 cũ) cần phương án xử lý dữ liệu riêng; tại 2026-11-05 DB dev không có dòng nào.
- Khi ADR này `Accepted`: cập nhật ADR-0002 thành `Superseded (một phần, §Quyết định #4) by ADR-0005`.
