# Use Cases — Giải tán đội & quy tắc xoá tài khoản đội trưởng (USER-002)

> Quy tắc được trích bằng mã (`BR-T*`) — chi tiết trong `Business_Rules.md`.

## UC-T06: Đội trưởng giải tán đội (mới)

**Actor:** Đội trưởng

**Preconditions:**
- Đội trưởng đã đăng nhập.
- Người thực hiện là đội trưởng đang hoạt động của đội (BR-T07).
- Đội chưa bị giải tán. Đội đang hoạt động **hoặc** đang bị admin khoá đều được giải tán (BR-T12).

**Main Flow:**
1. Đội trưởng chọn "Giải tán đội".
2. Hệ thống cảnh báo: giải tán là **vĩnh viễn**, mọi thành viên sẽ rời đội, lời mời đang chờ bị huỷ (BR-T09, BR-T10).
3. Đội trưởng nhập lý do (tuỳ chọn, BR-T08) và xác nhận.
4. Hệ thống chuyển đội sang trạng thái **Đã giải tán**.
5. Hệ thống chuyển mọi thành viên đang hoạt động (kể cả đội trưởng) sang **đã rời đội**, ghi ngày rời.
6. Hệ thống chuyển mọi lời mời đang chờ của đội sang **bị từ chối**.
7. Hệ thống thông báo giải tán thành công.

**Postconditions:**
- Đội ở trạng thái Đã giải tán, ghi lý do (hoặc lý do mặc định "Đội trưởng giải tán đội"), thời điểm và người giải tán.
- Không còn thành viên đang hoạt động, không còn lời mời đang chờ.
- Lịch sử thành viên, lịch sử đội trưởng và (nếu có) lý do khoá của admin được giữ nguyên (BR-T10, BR-T12).
- Đội vẫn xem được, đánh dấu "Đã giải tán" (BR-T13).
- Mọi thao tác trên đội bị chặn vĩnh viễn (BR-T11).

**Exception Flows:**

**1a. Người thực hiện không phải đội trưởng đang hoạt động của đội:**
- 1a1. Hệ thống báo "Chỉ đội trưởng mới được giải tán đội". Use case kết thúc.

**1b. Đội không tồn tại:**
- 1b1. Hệ thống báo "Không tìm thấy đội". Use case kết thúc.

**1c. Đội đã bị giải tán:**
- 1c1. Hệ thống báo "Đội đã giải tán". Use case kết thúc.

**3a. Lý do vượt độ dài cho phép:**
- 3a1. Hệ thống báo lỗi dữ liệu, không giải tán. Quay lại bước 3.

**4a. Đồng thời có thay đổi khác trên đội** (vd thành viên vừa chấp nhận lời mời, đội trưởng vừa chuyển quyền, admin vừa khoá đội):
- 4a1. Chỉ một thao tác được ghi nhận; thao tác còn lại được xử lý lại trên trạng thái mới nhất (vd đội đã giải tán → lời mời không thể chấp nhận; quyền đã chuyển → người cũ không còn là đội trưởng → 1a).

---

## UC-U03: Xoá tài khoản (sửa — thay thế các mục tương ứng trong SYSTEM-001)

**Actor:** User

**Preconditions (thay thế):**
- User đã đăng nhập.
- User **không còn là đội trưởng** của đội nào chưa giải tán — tức đã chuyển quyền (UC-T04) hoặc giải tán (UC-T06) mọi đội mình làm đội trưởng, **kể cả khi là người duy nhất trong đội** và kể cả đội đang bị admin khoá (BR-T06 sửa).

**Main Flow:** giữ nguyên SYSTEM-001 (bước 1–6).

**Postconditions (bổ sung):**
- Mọi tư cách thành viên đang hoạt động của User → đã rời đội; lời mời đang chờ gửi tới User → bị từ chối (BR-T14).
- Không đội nào bị hệ thống tự chuyển trạng thái do User xoá tài khoản.

**Exception Flows (thay thế 4a, bỏ 4b):**

**4a. User đang là đội trưởng của ít nhất một đội chưa giải tán** (kể cả khi là người duy nhất, kể cả đội bị admin khoá):
- 4a1. Hệ thống báo "Bạn phải chuyển quyền đội trưởng hoặc giải tán đội trước khi xóa tài khoản".
- 4a2. Use case kết thúc.

~~**4b. User là đội trưởng và là người duy nhất trong đội → đội tự INACTIVE**~~ — **bỏ** (BC-T02 sửa).

---

## UC-T05 / Kích hoạt lại đội (sửa)

**Bổ sung exception cho admin kích hoạt lại đội:**

**Đội đã giải tán:**
- Hệ thống báo "Đội đã giải tán" (BR-T09). Đội giữ nguyên trạng thái.

**Bổ sung exception cho admin khoá đội (UC-T05):**

**Đội đã giải tán:**
- Hệ thống báo "Đội đã giải tán" (BR-T11). Không khoá.

---

## Danh sách [cần xác nhận]

Xem `Acceptance_Criteria.md` §5.
