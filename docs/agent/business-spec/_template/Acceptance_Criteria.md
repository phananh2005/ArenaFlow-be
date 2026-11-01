# Acceptance Criteria — [TÊN CHỨC NĂNG]

> Definition of Done (DoD) và test scenarios để xác nhận chức năng đã đúng.

## 1. Definition of Done (DoD)

- [ ] Tất cả use cases trong `Use_Cases.md` được implement
- [ ] Tất cả business rules trong `Business_Rules.md` được tuân thủ
- [ ] Happy path hoạt động đúng
- [ ] Exception flows được xử lý đúng
- [ ] Unit tests PASS (coverage ≥ 80%)
- [ ] Integration tests PASS
- [ ] API documentation cập nhật
- [ ] Code review PASS
- [ ] [DoD khác nếu có]

## 2. Functional Acceptance Criteria

### AC-001: User xem danh sách sân

**Given:** User đã đăng nhập và truy cập trang danh sách sân

**When:** User xem danh sách

**Then:**
- Hiển thị danh sách sân với: tên, địa chỉ, loại, giá, ảnh
- Có thể lọc theo khu vực
- Có thể lọc theo loại sân
- Có phân trang (20 sân/trang)
- Load trong < 2s

---

### AC-002: User đặt sân thành công

**Given:**
- User đã đăng nhập
- Sân có slot khả dụng
- User chọn ngày/giờ hợp lệ (≥ 2h sau thời điểm hiện tại)

**When:** User submit booking

**Then:**
- Booking được tạo với trạng thái `pending`
- User nhận thông báo thành công
- Hệ thống gửi email xác nhận (trong 1 phút)
- Slot được đánh dấu `booked`

---

### AC-003: User không thể đặt sân khi slot đã booked

**Given:**
- User đã đăng nhập
- Slot đã được user khác đặt trước

**When:** User cố gắng đặt slot đó

**Then:**
- Hiển thị lỗi "Slot đã được đặt"
- Booking KHÔNG được tạo
- Gợi ý slot khác gần nhất

---

### AC-004: [Tiêu chí khác]

[Lặp lại cấu trúc trên]

---

## 3. Non-Functional Acceptance Criteria

### Performance
- API danh sách sân: response time < 2s với 1000 sân
- API đặt sân: response time < 1s
- Hệ thống chịu được 100 concurrent requests

### Security
- Tất cả API yêu cầu authentication
- User chỉ xem/sửa booking của mình
- Admin có full access

### Availability
- Uptime ≥ 99%
- Graceful degradation khi có lỗi

---

## 4. Test Scenarios

### Scenario 1: Happy path - Đặt sân thành công
1. User đăng nhập
2. User xem danh sách sân
3. User chọn sân A, ngày mai, 18:00-20:00
4. User submit booking
5. → Booking created, status = pending
6. → User nhận email xác nhận

### Scenario 2: Edge case - Đặt slot bị trùng
1. User A đặt sân X, slot 18:00-20:00 ngày mai
2. User B (cùng lúc) đặt sân X, slot 18:00-20:00 ngày mai
3. → Chỉ 1 người thành công (first-come-first-served)
4. → Người còn lại nhận lỗi "Slot đã được đặt"

### Scenario 3: Exception - Đặt sân quá gần
1. User đăng nhập lúc 16:00
2. User cố gắng đặt slot 17:00-18:00 (< 2h)
3. → Hiển thị lỗi "Booking phải đặt tối thiểu 2h trước"

### Scenario 4: Timeout booking
1. User tạo booking, status = pending
2. 30 phút trôi qua, User không thanh toán
3. → System tự động chuyển booking sang cancelled
4. → Slot được giải phóng, trở lại available

---

## 5. Danh sách [cần xác nhận]

1. [Điểm chưa rõ 1]
2. [Điểm chưa rõ 2]
