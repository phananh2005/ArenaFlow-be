# Data Model (Conceptual) — Giải tán đội & quy tắc xoá tài khoản đội trưởng (USER-002)

> Chỉ ghi phần **thay đổi** so với `SYSTEM-001/Data_Model_Conceptual.md`. KHÔNG có DB schema.

## 1. Entity Overview (thay đổi)

| Entity | Thay đổi | Lifecycle |
|---|---|---|
| Team | Thêm trạng thái "Đã giải tán"; thêm thông tin giải tán | Hoạt động ⇄ Bị khoá → Đã giải tán (xem `Business_Rules.md` §2) |
| Team_Member | Không thêm thuộc tính; dùng trạng thái "đã rời" + ngày rời sẵn có | Đang hoạt động → Đã rời (cả khi đội giải tán) |

## 2. Entity Details

### Team (Đội) — bổ sung

**Attributes (thêm):**
- Trạng thái: hoạt động / bị khoá (admin) / **đã giải tán**
- Lý do giải tán (tuỳ chọn; mặc định "Đội trưởng giải tán đội")
- Thời điểm giải tán
- Người giải tán (đội trưởng tại thời điểm giải tán)

**Giữ nguyên:** lý do khoá của admin là thông tin **riêng**, không bị lý do giải tán thay thế (BR-T12).

**Lifecycle (thay thế SYSTEM-001):** Hoạt động ⇄ Bị khoá (admin) → Đã giải tán (đội trưởng, vĩnh viễn).

### Team_Member — không đổi thuộc tính

Khi đội giải tán: thành viên đang hoạt động → đã rời (ghi ngày rời); lời mời đang chờ → bị từ chối (BR-T10).

## 3. Lifecycle Summary (thay thế dòng Team trong SYSTEM-001 §5)

| Entity | Khởi tạo | Terminal States | Có thể quay lại? |
|---|---|---|---|
| Team | User tạo đội | Đã giải tán | ❌ Đã giải tán: không bao giờ · ✅ Bị khoá: admin kích hoạt lại |
