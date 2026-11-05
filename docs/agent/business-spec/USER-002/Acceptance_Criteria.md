# Acceptance Criteria — Giải tán đội & quy tắc xoá tài khoản đội trưởng (USER-002)

## 1. Definition of Done (DoD)

- [ ] UC-T06, UC-U03 (sửa), kích hoạt lại / khoá đội (sửa) trong `Use_Cases.md` được implement
- [ ] BR-T06 (sửa), BR-T07..T14, BC-T02 (sửa), BC-T06 trong `Business_Rules.md` được tuân thủ
- [ ] Unit + integration tests PASS (gồm kịch bản đồng thời §4)
- [ ] SRS SYSTEM-001 không còn quy tắc "đội tự INACTIVE khi đội trưởng duy nhất xoá tài khoản"
- [ ] Code review PASS

## 2. Functional Acceptance Criteria

### AC-T06-01: Đội trưởng giải tán đội đang hoạt động

**Given:** Đội A đang hoạt động; đội trưởng X; thành viên Y đang hoạt động; lời mời đang chờ gửi Z.

**When:** X giải tán đội A, không nhập lý do.

**Then:**
- Đội A "Đã giải tán", lý do "Đội trưởng giải tán đội", ghi thời điểm và người giải tán là X.
- X và Y "đã rời đội" (có ngày rời); lời mời của Z "bị từ chối".
- Lịch sử thành viên và lịch sử đội trưởng của A giữ nguyên.

---

### AC-T06-02: Giải tán có lý do

**Given:** Đội A đang hoạt động, đội trưởng X.
**When:** X giải tán, nhập lý do "Hết người chơi".
**Then:** Lý do giải tán là "Hết người chơi".

---

### AC-T06-03: Chỉ đội trưởng được giải tán

**Given:** Đội A đang hoạt động; Y là thành viên thường; Admin đăng nhập.
**When:** Y (hoặc Admin) cố giải tán đội A.
**Then:** Bị từ chối ("Chỉ đội trưởng mới được giải tán đội"); đội A không đổi.

---

### AC-T06-04: Giải tán đội đang bị admin khoá — giữ lý do của admin

**Given:** Admin đã khoá đội A với lý do "Vi phạm quy định"; X vẫn là đội trưởng.
**When:** X giải tán đội A.
**Then:**
- Đội A "Đã giải tán".
- Lý do khoá của admin vẫn là "Vi phạm quy định" (không bị ghi đè); lý do giải tán ghi riêng.

---

### AC-T06-05: Đội đã giải tán bị chặn mọi thao tác

**Given:** Đội A đã giải tán.
**When:** Bất kỳ ai: sửa đội, mời, chấp nhận/từ chối lời mời cũ, chuyển quyền, rời đội, giải tán lại; Admin khoá hoặc kích hoạt lại.
**Then:** Tất cả bị từ chối; đội A không đổi. Mọi thao tác, kể cả Admin kích hoạt lại, nhận "Đội đã giải tán".

---

### AC-T06-06: Đội đã giải tán vẫn xem được

**Given:** Đội A đã giải tán.
**When:** User xem chi tiết đội A / lịch sử đội trưởng; Admin xem lịch sử thành viên; xem danh sách đội mặc định; lọc danh sách theo trạng thái gồm đội đã giải tán.
**Then:** Chi tiết và lịch sử đội trưởng xem được; chỉ Admin xem được lịch sử thành viên; User thường truy cập lịch sử thành viên bị từ chối; các màn hình liên quan đánh dấu "Đã giải tán"; danh sách mặc định không có A; lọc theo trạng thái thì có A.

---

### AC-U03-01 (thay AC-U03 phần đội trưởng): Đội trưởng duy nhất không xoá được tài khoản

**Given:** X là đội trưởng và là thành viên duy nhất của đội A (đang hoạt động).
**When:** X xoá tài khoản.
**Then:** Bị từ chối ("Bạn phải chuyển quyền đội trưởng hoặc giải tán đội trước khi xóa tài khoản"); tài khoản và đội A không đổi.

---

### AC-U03-02: Đội trưởng của đội bị admin khoá không xoá được tài khoản

**Given:** X là đội trưởng của đội A đang bị admin khoá.
**When:** X xoá tài khoản.
**Then:** Bị từ chối như AC-U03-01. X giải tán A (AC-T06-04) rồi xoá tài khoản → thành công.

---

### AC-U03-03: Sau khi giải tán / chuyển quyền thì xoá được tài khoản

**Given:** X từng là đội trưởng đội A; A đã giải tán (hoặc X đã chuyển quyền cho Y và còn là thành viên thường).
**When:** X xoá tài khoản.
**Then:** Thành công; tư cách thành viên đang hoạt động của X → đã rời; lời mời đang chờ gửi X → bị từ chối; không đội nào đổi trạng thái.

---

### AC-U03-04: Thành viên thường xoá tài khoản (không đổi)

**Given:** Y là thành viên thường của đội A.
**When:** Y xoá tài khoản.
**Then:** Thành công; Y "đã rời" đội A; đội A không đổi trạng thái.

## 3. Non-Functional Acceptance Criteria

- Hiệu năng: theo chuẩn số truy vấn của dự án (`docs/agent/non-functional-standards.md`) — thiết kế kỹ thuật chứng minh.
- Bảo mật: chỉ đội trưởng đang hoạt động giải tán được; người khác không suy được thông tin đội ngoài những gì đã công khai.

## 4. Test Scenarios (đồng thời)

### Scenario 1: Giải tán trong lúc có người chấp nhận lời mời
1. Đội A có lời mời đang chờ gửi Z.
2. Cùng lúc: X giải tán A, Z chấp nhận lời mời.
3. → Chỉ một kết quả hợp lệ: **hoặc** A đã giải tán và Z không trở thành thành viên (lời mời bị từ chối), **hoặc** Z vào đội trước rồi bị chuyển "đã rời" khi giải tán. Không bao giờ: A đã giải tán mà Z là thành viên đang hoạt động.

### Scenario 2: Giải tán trong lúc chuyển quyền
1. Cùng lúc: X giải tán A, X chuyển quyền cho Y.
2. → Một thao tác thắng; nếu chuyển quyền thắng thì giải tán của X bị từ chối (X không còn là đội trưởng). Không bao giờ: đội đã giải tán mà còn đội trưởng đang hoạt động.

### Scenario 3: Giải tán trong lúc admin khoá
1. Cùng lúc: X giải tán A, Admin khoá A.
2. → Kết quả cuối: A "Đã giải tán" (khoá trước rồi giải tán, BR-T12) **hoặc** khoá bị từ chối vì A đã giải tán (BR-T11). Lý do của admin nếu đã ghi thì giữ nguyên.

### Scenario 4: Đội trưởng xoá tài khoản trong lúc có người chấp nhận lời mời
1. X là đội trưởng duy nhất đội A; A có lời mời gửi Z.
2. Cùng lúc: X xoá tài khoản, Z chấp nhận lời mời.
3. → X bị từ chối xoá tài khoản (BR-T06 sửa) bất kể thứ tự; A giữ đội trưởng X.

## 5. Danh sách [cần xác nhận]

1. **Kèo/trận đang có khi giải tán** (domain Match chưa triển khai): chặn giải tán, tự huỷ kèo, hay cách khác — **chốt khi triển khai Match** (user quyết 2026-11-04). USER-002 không xét kèo.
2. **Độ dài tối đa lý do giải tán** (BR-T08): tối đa **100 ký tự** (user chốt 2026-11-04).
3. **Hiển thị "đội trưởng" của đội đã giải tán**: hiển thị **người giải tán** (user chốt 2026-11-04). Lưu ý người này được ghi nhận là người giải tán; tư cách đội trưởng của mọi thành viên đã kết thúc theo BR-T10.
4. **Lý do giải tán chỉ gồm khoảng trắng**: bỏ khoảng trắng đầu/cuối; rỗng → dùng lý do mặc định (user chốt 2026-11-05, BR-T08).
5. **Người giải tán đã xoá tài khoản**: hiển thị "Người dùng đã xóa" thay tên, như quy tắc che tên người dùng đã xoá của SYSTEM-001 (user chốt 2026-11-05).
6. **Phạm vi lịch sử thành viên cho Admin**: áp dụng cho đội ở mọi trạng thái; chỉ gồm người đã từng tham gia (đang hoạt động hoặc đã rời), không gồm lời mời (user chốt 2026-11-05, BR-T13).
