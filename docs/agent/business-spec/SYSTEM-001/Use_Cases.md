# Use Cases — ArenaFlow System

> Mô tả chi tiết các use case chính của hệ thống, nhóm theo 8 domains.

---

## DOMAIN: USER MANAGEMENT

### UC-U01: Đăng ký tài khoản

**Actor:** User (chưa đăng ký)

**Preconditions:**
- User chưa có tài khoản trong hệ thống
- User có tài khoản Google/Facebook/số điện thoại hợp lệ

**Main Flow:**
1. User chọn phương thức đăng ký (Google, Facebook, hoặc Phone OTP)
2. Hệ thống redirect đến OAuth provider hoặc gửi OTP
3. User xác thực thành công
4. Hệ thống tạo tài khoản mới với hồ sơ 1:1
5. Ứng dụng đưa User đến màn hình sửa hồ sơ cá nhân để hoàn thiện hồ sơ — dùng chính luồng UC-U02 (2026-11-06, xem "Quyết định bổ sung đã chốt" #8)

**Postconditions:**
- Tài khoản được tạo với trạng thái active
- Hồ sơ cá nhân 1:1 được tạo (tên, ảnh mặc định)
- User nhận notification chào mừng

**Exception Flows:**

**2a. OAuth provider từ chối/timeout:**
- 2a1. Hệ thống hiển thị lỗi "Đăng ký thất bại, vui lòng thử lại"
- 2a2. Use case kết thúc

**3a. Tài khoản OAuth đã tồn tại:**
- 3a1. Hệ thống đăng nhập User vào tài khoản hiện có
- 3a2. Chuyển đến màn hình chính

---

### UC-U02: Quản lý hồ sơ cá nhân

**Actor:** User

**Preconditions:**
- User đã đăng nhập

**Main Flow:**
1. User truy cập màn hình hồ sơ cá nhân
2. Hệ thống hiển thị hồ sơ hiện tại (tên, ảnh, tiểu sử, vị trí ưa thích, ngày sinh, giới tính, khu vực)
3. User cập nhật thông tin
4. User submit
5. Hệ thống validate và lưu thay đổi
6. Hệ thống hiển thị "Cập nhật thành công"

**Postconditions:**
- Hồ sơ được cập nhật
- Thay đổi có hiệu lực ngay lập tức

**Exception Flows:**

**5a. Validation thất bại:**
- 5a1. Hệ thống hiển thị lỗi cụ thể (vd: "Ngày sinh không hợp lệ")
- 5a2. Quay về bước 3

---

### UC-U03: Xóa tài khoản (soft-delete)

**Actor:** User

**Preconditions:**
- User đã đăng nhập
- Không còn là đội trưởng của đội nào chưa giải tán (đã chuyển quyền hoặc giải tán — **sửa bởi `USER-002`**, xem `USER-002/Use_Cases.md` UC-U03)

**Main Flow:**
1. User chọn "Xóa tài khoản"
2. Hệ thống hiển thị cảnh báo và yêu cầu xác nhận
3. User xác nhận xóa
4. Hệ thống kiểm tra điều kiện (có phải đội trưởng không)
5. Hệ thống soft-delete tài khoản (giữ trong DB, đánh dấu deleted)
6. Hệ thống đăng xuất User

**Postconditions:**
- Tài khoản chuyển sang trạng thái soft-deleted
- Lịch sử hiển thị "Người dùng đã xóa"
- Điểm/đánh giá lịch sử của đội khác được giữ nguyên
- Tài khoản có thể khôi phục sau này

**Exception Flows:**

**4a. User đang là đội trưởng của đội chưa giải tán** (kể cả khi là người duy nhất — **sửa bởi `USER-002`**):
- 4a1. Hệ thống hiển thị "Bạn phải chuyển quyền đội trưởng hoặc giải tán đội trước khi xóa tài khoản"
- 4a2. Use case kết thúc

~~**4b. Người duy nhất → đội tự INACTIVE**~~ — bỏ (`USER-002` BC-T02 sửa).

---

## DOMAIN: TEAM MANAGEMENT

### UC-T01: Tạo đội

**Actor:** User

**Preconditions:**
- User đã đăng nhập

**Main Flow:**
1. User chọn "Tạo đội"
2. User nhập thông tin đội (tên, avatar/logo, mô tả, trình độ — tối đa 3 trong 9 nhãn)
3. User submit
4. Hệ thống tạo đội với User làm đội trưởng
5. Hệ thống hiển thị "Đội đã được tạo"

**Postconditions:**
- Đội được tạo với trạng thái active
- User trở thành đội trưởng
- Lịch sử đội trưởng được ghi nhận

**Exception Flows:**

**3a. Validation thất bại:**
- 3a1. Hệ thống hiển thị lỗi (vd: "Tên đội không được để trống", "Chọn tối đa 3 nhãn trình độ")
- 3a2. Quay về bước 2

---

### UC-T02: Mời thành viên vào đội

**Actor:** Team Captain

**Preconditions:**
- User là đội trưởng
- User biết ID/username của người được mời

**Main Flow:**
1. Đội trưởng chọn "Mời thành viên"
2. Đội trưởng nhập ID/username người được mời
3. Đội trưởng submit
4. Hệ thống tạo lời mời với trạng thái pending
5. Hệ thống gửi notification cho người được mời
6. Hệ thống hiển thị "Lời mời đã được gửi"

**Postconditions:**
- Lời mời được tạo với trạng thái pending
- Người được mời nhận notification

**Exception Flows:**

**2a. Người được mời không tồn tại:**
- 2a1. Hệ thống hiển thị "Không tìm thấy người dùng"
- 2a2. Quay về bước 2

**4a. Người được mời đã là thành viên:**
- 4a1. Hệ thống hiển thị "Người này đã là thành viên của đội"
- 4a2. Use case kết thúc

---

### UC-T03: Chấp nhận/Từ chối lời mời

**Actor:** User (người được mời)

**Preconditions:**
- User nhận được lời mời vào đội

**Main Flow:**
1. User xem lời mời trong notification
2. User chọn "Chấp nhận" hoặc "Từ chối"
3. Hệ thống cập nhật trạng thái lời mời
4. (Nếu chấp nhận) Hệ thống thêm User vào đội với vai trò MEMBER
5. Hệ thống gửi notification cho đội trưởng

**Postconditions:**
- Lời mời chuyển sang accepted/rejected
- (Nếu chấp nhận) User trở thành thành viên đội
- Lịch sử thành viên được ghi nhận

**Exception Flows:** (không có exception đặc biệt)

---

### UC-T04: Chuyển quyền đội trưởng

**Actor:** Team Captain

**Preconditions:**
- User là đội trưởng
- Đội có ít nhất 1 thành viên khác đang hoạt động

**Main Flow:**
1. Đội trưởng chọn "Chuyển quyền"
2. Đội trưởng chọn thành viên đang hoạt động
3. Đội trưởng xác nhận chuyển quyền
4. Hệ thống tự động chuyển quyền ngay lập tức (người được chọn bắt buộc nhận)
5. Hệ thống lưu lịch sử chuyển quyền
6. Hệ thống gửi notification cho đội trưởng mới và đội trưởng cũ

**Postconditions:**
- Đội trưởng cũ trở thành thành viên
- Thành viên được chọn trở thành đội trưởng
- Lịch sử chuyển quyền được ghi nhận

**Exception Flows:**

**2a. Đội không có thành viên khác:**
- 2a1. Hệ thống hiển thị "Không có thành viên để chuyển quyền"
- 2a2. Use case kết thúc

---

### UC-T05: Admin đánh dấu đội INACTIVE

**Actor:** Admin

**Preconditions:**
- Admin đã đăng nhập
- Đội vi phạm quy tắc hoặc có yêu cầu đánh dấu INACTIVE

**Main Flow:**
1. Admin tìm đội cần đánh dấu
2. Admin chọn "Đánh dấu INACTIVE"
3. Admin nhập lý do
4. Admin xác nhận
5. Hệ thống chuyển đội sang INACTIVE
6. Hệ thống gửi notification cho đội trưởng

**Postconditions:**
- Đội chuyển sang INACTIVE
- Đội không thể tạo kèo mới
- Lịch sử đội được giữ nguyên

> **Sửa bởi `USER-002`:** admin không khoá được đội đã giải tán. Kích hoạt lại không áp dụng cho đội đã giải tán.

---

### UC-T06: Đội trưởng giải tán đội

→ Định nghĩa tại `docs/agent/business-spec/USER-002/Use_Cases.md` (UC-T06).

---

## DOMAIN: MATCH MANAGEMENT

### UC-M01: Tạo kèo luồng A (đã có sân)

**Actor:** Team Captain

**Preconditions:**
- User là đội trưởng
- Đội trưởng đã tự đặt sân bên ngoài ArenaFlow

**Main Flow:**
1. Đội trưởng chọn "Tạo kèo"
2. Đội trưởng chọn "Đã có sân"
3. Đội trưởng nhập thông tin (sân, giờ đá, định dạng, trình độ, thời hạn bài đăng)
4. Đội trưởng submit
5. Hệ thống tạo Match với trạng thái OPEN
6. Hệ thống hiển thị "Kèo đã được tạo"

**Postconditions:**
- Match được tạo với trạng thái OPEN
- Match hiển thị trong danh sách tìm kèo

**Exception Flows:**

**4a. Thời hạn không hợp lệ:**
- 4a1. Hệ thống hiển thị "Thời hạn phải trong khoảng tối đa 7 ngày trước giờ đá đến giờ bắt đầu"
- 4a2. Quay về bước 3

---

### UC-M02: Tạo kèo luồng B (chưa có sân)

**Actor:** Team Captain

**Preconditions:**
- User là đội trưởng

**Main Flow:**
1. Đội trưởng chọn "Tạo kèo"
2. Đội trưởng chọn "Chưa có sân"
3. Đội trưởng nhập thông tin (nhiều lựa chọn địa điểm, nhiều lựa chọn giờ, định dạng, trình độ, thời hạn bài đăng ≤ giờ trận sớm nhất)
4. Đội trưởng submit
5. Hệ thống tạo Match với trạng thái OPEN
6. Hệ thống hiển thị "Kèo đã được tạo"

**Postconditions:**
- Match được tạo với trạng thái OPEN
- Match có nhiều lựa chọn địa điểm/giờ
- Match hiển thị trong danh sách tìm kèo

**Exception Flows:**

**4a. Thời hạn muộn hơn giờ trận sớm nhất:**
- 4a1. Hệ thống hiển thị "Thời hạn không được muộn hơn giờ bắt đầu trận sớm nhất"
- 4a2. Quay về bước 3

---

### UC-M03: Gửi Match Request (luồng A)

**Actor:** Team Captain

**Preconditions:**
- User là đội trưởng
- Match đang ở trạng thái OPEN
- Đội chưa gửi request cho Match này

**Main Flow:**
1. Đội trưởng xem Match trong danh sách tìm kèo
2. Đội trưởng chọn "Gửi yêu cầu"
3. Đội trưởng submit
4. Hệ thống tạo Match_Request với trạng thái PENDING
5. Hệ thống gửi notification cho đội trưởng tác giả Match
6. Hệ thống hiển thị "Yêu cầu đã được gửi"

**Postconditions:**
- Match_Request được tạo với trạng thái PENDING
- Tác giả Match nhận notification

**Exception Flows:**

**3a. Match đã chuyển sang CONFIRMED:**
- 3a1. Hệ thống hiển thị "Kèo đã được ghép"
- 3a2. Use case kết thúc

---

### UC-M04: Chấp nhận Match Request (luồng A)

**Actor:** Team Captain (tác giả Match)

**Preconditions:**
- User là đội trưởng tạo Match
- Match đang ở trạng thái OPEN
- Có ít nhất 1 Match_Request PENDING

**Main Flow:**
1. Đội trưởng xem danh sách Match_Request
2. Đội trưởng chọn 1 request và chọn "Chấp nhận"
3. Đội trưởng xác nhận
4. Hệ thống chuyển request đó sang ACCEPTED
5. Hệ thống chuyển Match sang CONFIRMED
6. Hệ thống chuyển các request PENDING còn lại sang SUPERSEDED
7. Hệ thống gửi notification cho đội được chấp nhận và các đội bị superseded

**Postconditions:**
- Match chuyển sang CONFIRMED (không được sửa giờ/sân/trình độ)
- 1 request ACCEPTED, các request khác SUPERSEDED
- Tối đa 1 request ACCEPTED mỗi Match (ràng buộc dữ liệu)

**Exception Flows:**

**3a. Match đã chuyển sang CONFIRMED (race condition):**
- 3a1. Hệ thống hiển thị "Kèo đã được ghép bởi request khác"
- 3a2. Use case kết thúc

---

### UC-M05: Tạo Pairing (luồng B)

**Actor:** Team Captain

**Preconditions:**
- User là đội trưởng
- Match luồng B đang ở trạng thái OPEN

**Main Flow:**
1. Đội trưởng xem Match luồng B trong danh sách
2. Đội trưởng chọn "Nhận kèo"
3. Hệ thống tạo Pairing với trạng thái NEGOTIATING
4. Hệ thống mở chat giữa 2 đội trưởng
5. Hệ thống gửi notification cho tác giả Match

**Postconditions:**
- Pairing được tạo với trạng thái NEGOTIATING
- Chat giữa 2 đội trưởng được kích hoạt
- Match vẫn ở OPEN (cho phép đội khác cũng nhận)

---

### UC-M06: Thương lượng qua chat (luồng B)

**Actor:** Team Captain (cả 2 bên)

**Preconditions:**
- Pairing đang ở trạng thái NEGOTIATING

**Main Flow:**
1. Đội trưởng gửi tin nhắn trong chat
2. Hệ thống lưu tin nhắn
3. Hệ thống gửi tin nhắn real-time cho đội trưởng kia
4. Hai bên trao đổi để thống nhất địa điểm/giờ

**Postconditions:**
- Tin nhắn được lưu (retention 90 ngày sau khi Match kết thúc)
- Hai bên có thể thương lượng cho đến khi xác nhận hoặc hết hạn

---

### UC-M07: Xác nhận Pairing (luồng B)

**Actor:** Team Captain (cả 2 bên)

**Preconditions:**
- Pairing đang ở trạng thái NEGOTIATING
- Hai bên đã thống nhất địa điểm/giờ qua chat

**Main Flow:**
1. Đội trưởng A chọn "Xác nhận" (qua UI riêng, không phải tin nhắn)
2. Hệ thống đánh dấu đội trưởng A đã xác nhận, lưu timestamp
3. Đội trưởng B chọn "Xác nhận"
4. Hệ thống kiểm tra cả 2 đã xác nhận
5. Hệ thống chuyển Pairing sang CONFIRMED
6. Hệ thống chuyển Match sang CONFIRMED
7. Hệ thống chuyển các Pairing/Request khác sang SUPERSEDED
8. Hệ thống gửi notification cho 2 đội và các đội bị superseded

**Postconditions:**
- Pairing và Match chuyển sang CONFIRMED
- Các Pairing/Request khác SUPERSEDED
- Match không được sửa giờ/sân/trình độ

**Exception Flows:**

**5a. Race condition — 2 pairing cùng được xác nhận gần đồng thời:**
- 5a1. Hệ thống so sánh timestamp xác nhận cuối
- 5a2. Pairing có timestamp xác nhận cuối sớm hơn thắng
- 5a3. Pairing kia chuyển SUPERSEDED
- 5a4. Tiếp tục bước 6

> **Implementation note:** Optimistic Lock với JPA `@Version` trên entity Match, retry max 3 lần khi conflict. Frontend không gửi version; backend tự đọc version hiện tại khi xử lý request.

---

### UC-M08: Hủy kèo

**Actor:** Team Captain (tác giả hoặc đội tham gia)

**Preconditions:**
- Match đang ở trạng thái OPEN hoặc CONFIRMED

**Main Flow (Match OPEN):**
1. Đội trưởng chọn "Hủy kèo"
2. Hệ thống kiểm tra có request PENDING không
3. (Nếu có) Hệ thống yêu cầu từ chối tất cả request PENDING trước
4. Đội trưởng xác nhận hủy
5. Hệ thống chuyển Match sang CANCELLED
6. Hệ thống không trừ uy tín (hủy kèo OPEN không ảnh hưởng)

**Main Flow (Match CONFIRMED):**
1. Đội trưởng chọn "Hủy kèo"
2. Đội trưởng nhập lý do hủy
3. Đội trưởng xác nhận
4. Hệ thống tính khoảng cách đến giờ trận (≥24h, 4-24h, <4h)
5. Hệ thống trừ Reliability Score (-2/-5/-10)
6. Hệ thống chuyển Match sang CANCELLED
7. Hệ thống gửi notification cho đội kia

**Postconditions:**
- Match chuyển sang CANCELLED
- (Nếu CONFIRMED) Bên hủy bị trừ Reliability Score theo khoảng cách giờ trận
- (Nếu CANCELLED ≥24h trước) Match loại khỏi mẫu số Completion Rate

**Exception Flows:**

**3a. Có request PENDING chưa xử lý:**
- 3a1. Hệ thống hiển thị "Vui lòng từ chối tất cả yêu cầu trước khi hủy"
- 3a2. Use case kết thúc

---

### UC-M09: System cập nhật trạng thái hết hạn

**Actor:** System

**Preconditions:**
- Match/Request/Pairing có `expires_at` trong quá khứ

**Main Flow:**
1. Scheduled job chạy mỗi 5 phút
2. System query Match/Request/Pairing có `expires_at` ≤ now và trạng thái chưa terminal
3. System chuyển trạng thái sang EXPIRED
4. System gửi notification cho các bên liên quan

**Postconditions:**
- Trạng thái được cập nhật sang EXPIRED
- Notification được gửi

**Alternative Flow (Lazy check):**
- 1a. API được gọi (vd: xem chi tiết Match)
- 1a1. System kiểm tra `expires_at`
- 1a2. Nếu hết hạn và chưa EXPIRED → cập nhật ngay
- 1a3. Tiếp tục xử lý API

---

## DOMAIN: REPUTATION SYSTEM

### UC-R01: Đánh giá đối phương sau trận

**Actor:** Team Captain

**Preconditions:**
- Match đã ARCHIVED hoặc COMPLETED
- Đội trưởng chưa đánh giá đối phương

**Main Flow:**
1. Đội trưởng nhận push notification "Trận đấu hôm nay thế nào? Hãy nhập tỷ số và đánh giá đồng đội!"
2. Đội trưởng chọn "Đánh giá"
3. Đội trưởng chọn 1 trong 2: (a) Đánh giá 1-5 sao, hoặc (b) "Đội kia không đến"
4. (Nếu chọn a) Đội trưởng nhập rating 1-5 sao và comment (tùy chọn)
5. (Nếu chọn b) Hệ thống đề xuất "Bạn có muốn report bùng không?" (tách khỏi luồng đánh giá)
6. Đội trưởng nhập tỷ số (tùy chọn, chỉ để lưu kỷ niệm)
7. Đội trưởng submit
8. Hệ thống lưu rating và chuyển Match sang COMPLETED
9. (Nếu rating ≥4.5 sao) Hệ thống cộng +1 Reliability Score cho đội được đánh giá
10. Hệ thống cập nhật rating trung bình của đội được đánh giá

**Postconditions:**
- Match chuyển sang COMPLETED (nếu đang ARCHIVED)
- Rating được lưu
- Đội được đánh giá nhận +1 Reliability nếu rating ≥4.5
- Bên đã đánh giá có thể xem rating đối phương cho mình (sau khi đối phương đánh giá)

**Exception Flows:**

**3a. Đã đánh giá trước đó:**
- 3a1. Hệ thống hiển thị "Bạn đã đánh giá trận này"
- 3a2. Use case kết thúc

---

### UC-R02: Report đối phương

**Actor:** Team Captain

**Preconditions:**
- Match đã kết thúc (ARCHIVED/COMPLETED/CANCELLED)
- Đội trưởng chưa report đối phương trong trận này cho loại report này

**Main Flow:**
1. Đội trưởng chọn "Report"
2. Đội trưởng chọn loại report (bùng, chơi xấu, giao tiếp tệ, không đúng trình độ)
3. Đội trưởng nhập lý do chi tiết
4. Đội trưởng submit
5. Hệ thống tự động chấp nhận report
6. Hệ thống tra số lần bị report loại này của đội bị report
7. Hệ thống trừ Reliability Score theo mức tăng dần (xem Business_Rules.md §6)
8. Hệ thống gửi notification cho đội bị report

**Postconditions:**
- Report được lưu và tự động chấp nhận
- Đội bị report bị trừ Reliability Score
- (Nếu report bùng) Tranh chấp có thể được mở trong 24h

**Exception Flows:**

**5a. Report bùng:**
- 5a1. Tiếp tục bước 6-8
- 5a2. Đội bị report có 24h để phản hồi (UC-R03)

---

### UC-R03: Tranh chấp report bùng

**Actor:** Team Captain (đội bị report)

**Preconditions:**
- Đội bị report bùng trong vòng 24h
- Report chưa qua tranh chấp

**Main Flow:**
1. Đội trưởng bị report nhận notification "Đội [X] report bạn bùng kèo"
2. Đội trưởng chọn "Tranh chấp"
3. Đội trưởng nhập bằng chứng/lý do tranh chấp
4. Đội trưởng submit
5. Hệ thống chuyển tranh chấp cho admin xem xét
6. Hệ thống tạm giữ mức trừ Reliability (chưa hoàn lại)

**Postconditions:**
- Tranh chấp được tạo và chuyển admin
- Admin sẽ xem xét và quyết định cuối cùng (UC-R04)

**Exception Flows:**

**2a. Quá 24h:**
- 2a1. Hệ thống không cho phép tranh chấp
- 2a2. Report được xác nhận tự động

---

### UC-R04: Admin xử lý tranh chấp

**Actor:** Admin

**Preconditions:**
- Có tranh chấp report bùng chờ xử lý

**Main Flow:**
1. Admin xem chi tiết tranh chấp (bằng chứng từ cả 2 bên)
2. Admin đưa ra quyết định: (a) Xác nhận report đúng, hoặc (b) Bác report
3. (Nếu a) Hệ thống giữ nguyên mức trừ Reliability, Match không tính tử số Completion Rate
4. (Nếu b) Hệ thống hoàn lại Reliability đã trừ, Match tính tử số Completion Rate
5. Hệ thống gửi notification cho cả 2 bên

**Postconditions:**
- Tranh chấp được giải quyết (quyết định của admin là cuối cùng)
- Reliability Score và Completion Rate được điều chỉnh đúng

---

### UC-R05: System tính Reliability Score

**Actor:** System

**Preconditions:**
- Có sự kiện ảnh hưởng Reliability Score (hoàn thành trận, hủy kèo, report, rating ≥4.5)

**Main Flow:**
1. System nhận sự kiện (vd: Match COMPLETED, Match CANCELLED, report được chấp nhận)
2. System tra quy tắc điểm (xem Business_Rules.md §7)
3. System tính delta điểm
4. System cập nhật Reliability Score = `Math.max(0, Math.min(100, currentScore + delta))`
5. (Nếu đạt 5 trận sạch liên tiếp) System cộng thêm +5 bonus
6. (Nếu đạt 10 trận sạch liên tiếp) System cộng thêm +10 bonus
7. (Nếu đạt 5 trận sạch liên tiếp) System reset bộ đếm số lần bị report về 0

**Postconditions:**
- Reliability Score được cập nhật (chặn trong [0, 100])
- Bonus trận sạch được áp dụng
- Bộ đếm report được reset nếu đủ điều kiện

---

### UC-R06: System tính Completion Rate

**Actor:** System

**Preconditions:**
- Match chuyển trạng thái ảnh hưởng Completion Rate (COMPLETED, CANCELLED, ARCHIVED)

**Main Flow:**
1. System nhận sự kiện Match chuyển trạng thái
2. System tính mẫu số: Match đã CONFIRMED, loại trừ CANCELLED ≥24h trước và ARCHIVED
3. System tính tử số: Match COMPLETED có ít nhất 1 đội trưởng đánh giá và không có tranh chấp bùng được xác nhận
4. System tính Completion Rate = (tử số / mẫu số) × 100%
5. (Nếu mẫu số < 5) System hiển thị "Mới tham gia X/5 trận"
6. (Nếu mẫu số ≥ 5) System hiển thị Completion Rate

**Postconditions:**
- Completion Rate được cập nhật chính xác
- Hiển thị đúng theo ngưỡng trận

---

### UC-R07: System cấp Verification Badge

**Actor:** System

**Preconditions:**
- User hoặc đội có thay đổi ảnh hưởng điều kiện Verification Badge

**Main Flow:**
1. System kiểm tra 4 điều kiện:
   - Phone linked ✓
   - Hoàn thành >5 trận ✓
   - Rating trung bình >3 sao ✓
   - Reliability Score ≥70 ✓
2. (Nếu đủ 4 điều kiện) System cấp Verification Badge
3. (Nếu thiếu bất kỳ điều kiện nào) System thu hồi Verification Badge (nếu đã có)
4. System gửi notification khi cấp/thu hồi badge

**Postconditions:**
- Verification Badge được cấp/thu hồi đúng
- User/đội nhận notification

---

## DOMAIN: MERCENARY MANAGEMENT

### UC-ME01: Đội trưởng tạo tin tuyển mercenary

**Actor:** Team Captain

**Preconditions:**
- User là đội trưởng
- Đội có Match đã CONFIRMED

**Main Flow:**
1. Đội trưởng chọn "Tuyển mercenary"
2. Đội trưởng chọn vị trí (Thủ môn, Hậu vệ, Tiền vệ, Tiền đạo) và số lượng
3. Đội trưởng submit
4. Hệ thống tạo tin tuyển với trạng thái OPEN
5. Tin tuyển hiển thị trong danh sách tìm kèo mercenary

**Postconditions:**
- Tin tuyển được tạo với trạng thái OPEN
- Tin tuyển tự động EXPIRED khi đến giờ kết thúc dự kiến của Match

**Exception Flows:**

**3a. Match chưa CONFIRMED:**
- 3a1. Hệ thống hiển thị "Chỉ có thể tuyển mercenary sau khi kèo đã ghép"
- 3a2. Use case kết thúc

---

### UC-ME02: Cá nhân tìm kèo mercenary

**Actor:** User (Individual)

**Preconditions:**
- User đã đăng nhập

**Main Flow:**
1. User chọn "Tìm kèo mercenary"
2. App yêu cầu quyền GPS
3. (Nếu User cấp GPS) Hệ thống tìm kèo trong bán kính 3km (có thể điều chỉnh)
4. (Nếu User không cấp GPS) Hệ thống tìm kèo dựa trên khu vực hồ sơ (phường/xã) và hiển thị thông báo "Bật GPS để tìm kèo gần bạn hơn"
5. Hệ thống hiển thị danh sách kèo, mỗi kèo hiển thị: khoảng cách (vd "2.3 km từ bạn"), khu vực chung (vd "Quận 1"), vị trí cần, giờ đá, trình độ
6. User xem chi tiết kèo

**Postconditions:**
- User xem được danh sách kèo phù hợp
- Vị trí được bảo vệ (không hiển thị tọa độ chính xác)

---

### UC-ME03: Cá nhân ứng tuyển

**Actor:** User (Individual)

**Preconditions:**
- Tin tuyển đang ở trạng thái OPEN
- User chưa ứng tuyển tin này

**Main Flow:**
1. User xem chi tiết tin tuyển
2. User chọn "Ứng tuyển"
3. User submit
4. Hệ thống tạo đơn ứng tuyển với trạng thái PENDING
5. Hệ thống gửi notification cho đội trưởng

**Postconditions:**
- Đơn ứng tuyển được tạo với trạng thái PENDING
- Đội trưởng nhận notification

---

### UC-ME04: Đội trưởng duyệt ứng viên

**Actor:** Team Captain

**Preconditions:**
- Có đơn ứng tuyển PENDING
- Tin tuyển chưa FILLED hoặc EXPIRED

**Main Flow:**
1. Đội trưởng xem danh sách ứng viên (hồ sơ cá nhân, rating, Reliability Score)
2. Đội trưởng chọn "Duyệt" hoặc "Từ chối"
3. Đội trưởng xác nhận
4. (Nếu duyệt) Hệ thống chuyển đơn sang APPROVED
5. (Nếu từ chối) Hệ thống chuyển đơn sang REJECTED
6. Hệ thống gửi notification cho ứng viên
7. (Nếu đủ số lượng) Hệ thống chuyển tin tuyển sang FILLED

**Postconditions:**
- Đơn ứng tuyển chuyển sang APPROVED/REJECTED
- (Nếu đủ người) Tin tuyển chuyển FILLED

---

### UC-ME05: Mercenary rút lui

**Actor:** User (mercenary đã được duyệt)

**Preconditions:**
- Đơn ứng tuyển đang ở trạng thái APPROVED

**Main Flow:**
1. User chọn "Rút lui"
2. User xác nhận
3. Hệ thống tính khoảng cách đến giờ trận
4. Hệ thống trừ Reliability cá nhân: -1 (≥24h), -3 (4-24h), -5 (<4h)
5. Hệ thống chuyển đơn sang WITHDRAWN
6. Hệ thống gửi notification cho đội trưởng
7. Hệ thống chuyển tin tuyển từ FILLED về OPEN (nếu thiếu người)

**Postconditions:**
- Đơn chuyển sang WITHDRAWN
- Reliability cá nhân bị trừ theo khoảng cách giờ trận
- Tin tuyển có thể quay lại OPEN

---

### UC-ME06: Đánh giá mercenary/đội

**Actor:** Team Captain & User (mercenary)

**Preconditions:**
- Match đã kết thúc (ARCHIVED/COMPLETED)
- Mercenary đã tham gia trận

**Main Flow:**
1. Đội trưởng đánh giá từng mercenary (1-5 sao)
2. Mercenary đánh giá đội (1-5 sao)
3. Hệ thống lưu rating (cấu trúc: rater_type, rater_id, ratee_type, ratee_id)
4. Hệ thống cập nhật rating trung bình cá nhân/đội
5. (Nếu rating ≥4.5) Hệ thống cộng +1 Reliability

**Postconditions:**
- Rating được lưu
- Rating trung bình được cập nhật
- Reliability được cộng nếu đủ điều kiện

**Exception Flows:**

**3a. Đã đánh giá trước đó:**
- 3a1. Hệ thống hiển thị "Bạn đã đánh giá người chơi này"
- 3a2. Use case kết thúc

---

## DOMAIN: VENUE MANAGEMENT

### UC-V01: Thêm địa điểm sân

**Actor:** Admin hoặc User

**Preconditions:**
- Actor đã đăng nhập

**Main Flow:**
1. Actor chọn "Thêm địa điểm"
2. Actor nhập thông tin (tên, địa chỉ, tọa độ, loại sân — sân 5 / sân 7 / sân 11, không bắt buộc — điện thoại, website)
3. Actor submit
4. Hệ thống tạo địa điểm với trạng thái active, lưu người tạo
5. Hệ thống hiển thị "Địa điểm đã được thêm"

**Postconditions:**
- Địa điểm được tạo và hiển thị trong gợi ý
- Không cần admin duyệt trước

**Exception Flows:**

**3a. Validation thất bại:**
- 3a1. Hệ thống hiển thị lỗi (vd: "Tên không được để trống")
- 3a2. Quay về bước 2

---

### UC-V02: Upload ảnh sân

**Actor:** Admin hoặc User

**Preconditions:**
- Địa điểm đã tồn tại
- Địa điểm có <5 ảnh
- Địa điểm đang hoạt động; địa điểm đã ngừng hoạt động chỉ Admin được thêm ảnh / đổi ảnh chính

**Main Flow:**
1. Actor chọn "Thêm ảnh"
2. Actor chọn file ảnh và đánh dấu có phải ảnh chính không
3. Actor submit
4. Hệ thống lưu ảnh vào bảng `venue_images` (venue_id, image_url, is_primary, display_order, uploaded_by)
5. (Nếu is_primary=true) Hệ thống cập nhật ảnh cũ is_primary=false
6. Hệ thống hiển thị "Ảnh đã được thêm"

**Postconditions:**
- Ảnh được lưu
- Chỉ 1 ảnh is_primary=true mỗi venue

**Exception Flows:**

**2a. Đã có 5 ảnh:**
- 2a1. Hệ thống hiển thị "Tối đa 5 ảnh mỗi địa điểm"
- 2a2. Use case kết thúc

---

### UC-V03: Report địa điểm sân

**Actor:** User (không gồm Admin — Admin sửa hoặc đánh dấu ngừng hoạt động trực tiếp)

**Preconditions:**
- Địa điểm tồn tại và đang hoạt động

**Main Flow:**
1. User chọn "Report địa điểm"
2. User chọn lý do (ngừng hoạt động, thông tin sai, khác)
3. User nhập chi tiết
4. User submit
5. Hệ thống lưu report (người report, lý do, thời gian)
6. Hệ thống gửi notification cho admin

**Postconditions:**
- Report được lưu
- Admin thấy chi tiết từng report
- Report không tự ẩn địa điểm (cần admin xác minh)

**Exception Flows:**

**4a. User đã có report chờ xử lý cùng lý do cho địa điểm này:**
- 4a1. Hệ thống hiển thị "Bạn đã report địa điểm này, đang chờ admin xử lý"
- 4a2. Use case kết thúc

**4b. Địa điểm đã ngừng hoạt động:**
- 4b1. Hệ thống không cho report
- 4b2. Use case kết thúc

---

### UC-V04: Admin xác minh và đánh dấu ngừng hoạt động

**Actor:** Admin

**Preconditions:**
- Có report địa điểm chờ xác minh

**Main Flow:**
1. Admin xem chi tiết report
2. Admin xác minh (liên hệ, kiểm tra)
3. (Nếu đúng ngừng hoạt động) Admin chọn "Đánh dấu ngừng hoạt động"
4. Admin xác nhận
5. Hệ thống chuyển địa điểm sang inactive
6. Hệ thống loại địa điểm khỏi gợi ý mặc định (Match cũ giữ tham chiếu)

**Postconditions:**
- Địa điểm chuyển sang inactive
- Địa điểm bị loại khỏi gợi ý
- Match cũ vẫn giữ tham chiếu

---

### UC-V05: Sửa thông tin địa điểm sân

**Actor:** Người tạo địa điểm hoặc Admin

**Preconditions:**
- Địa điểm tồn tại
- Actor là người tạo địa điểm hoặc Admin
- Địa điểm đã ngừng hoạt động: chỉ Admin được sửa (người tạo không sửa được)

**Main Flow:**
1. Actor chọn "Sửa địa điểm"
2. Actor sửa một hoặc nhiều thông tin: tên, địa chỉ, tọa độ, loại sân, điện thoại, website
3. Actor submit
4. Hệ thống lưu thông tin mới và thời điểm cập nhật
5. Hệ thống hiển thị "Địa điểm đã được cập nhật"

**Postconditions:**
- Địa điểm mang thông tin mới
- Mọi kèo tham chiếu địa điểm (kể cả kèo đã chốt) hiển thị thông tin mới nhất; kèo không lưu bản sao thông tin địa điểm

**Exception Flows:**

**1a. Actor không phải người tạo và không phải Admin:**
- 1a1. Hệ thống không cho sửa; gợi ý "Report thông tin sai" (UC-V03)
- 1a2. Use case kết thúc

**3a. Validation thất bại:**
- 3a1. Hệ thống hiển thị lỗi (vd: "Tên không được để trống")
- 3a2. Quay về bước 2

---

### UC-V06: Xóa ảnh sân

**Actor:** Người tạo địa điểm hoặc Admin

**Preconditions:**
- Địa điểm có ít nhất 1 ảnh
- Địa điểm đã ngừng hoạt động: chỉ Admin được xóa ảnh
- Actor là người tạo địa điểm hoặc Admin

**Main Flow:**
1. Actor chọn ảnh cần xóa
2. Actor xác nhận xóa
3. Hệ thống xóa ảnh khỏi địa điểm
4. (Nếu ảnh bị xóa là ảnh chính và địa điểm còn ảnh khác) Hệ thống chọn ảnh còn lại có thứ tự hiển thị nhỏ nhất làm ảnh chính
5. Hệ thống hiển thị "Ảnh đã được xóa"

**Postconditions:**
- Ảnh không còn hiển thị ở địa điểm; địa điểm có thể thêm ảnh mới (tối đa 5)
- Nếu địa điểm còn ảnh thì có đúng 1 ảnh chính; hết ảnh thì không có ảnh chính

**Exception Flows:**

**1a. Actor không phải người tạo và không phải Admin:**
- 1a1. Hệ thống không cho xóa
- 1a2. Use case kết thúc

---

## DOMAIN: COST AGREEMENT

### UC-C01: Thỏa thuận chi phí

**Actor:** Team Captain (cả 2 bên, luồng B) hoặc Team Captain (tác giả, luồng A)

**Preconditions:**
- Match đang ở trạng thái NEGOTIATING (luồng B) hoặc trước khi CONFIRMED (luồng A)

**Main Flow:**
1. Đội trưởng chọn "Thỏa thuận chi phí"
2. Đội trưởng chọn loại chi phí (tiền sân, tiền nước, hoặc cả hai)
3. Đội trưởng nhập số tiền cụ thể (VND)
4. Đội trưởng chọn cách chia (50-50, 70-30 đội chủ nhà bao 70%, 70-30 ai thua bao 70%, đội thua trả toàn bộ tiền nước)
5. (Nếu luồng B) Đội trưởng nhập thỏa thuận cọc (ai giữ tiền cọc, số tiền)
6. Đội trưởng submit
7. Hệ thống lưu thỏa thuận để hiển thị trên bảng tin Match
8. (Nếu luồng B) Đối phương xem và xác nhận thỏa thuận qua chat

**Postconditions:**
- Thỏa thuận chi phí được lưu
- Hiển thị trên bảng tin Match
- ArenaFlow không thu/giữ tiền; hai đội thanh toán bên ngoài

---

### UC-C02: Nhập khoản phí tự do

**Actor:** Team Captain

**Preconditions:**
- Đang ở màn hình thỏa thuận chi phí

**Main Flow:**
1. Đội trưởng chọn "Thêm khoản phí"
2. Đội trưởng nhập tên khoản phí (vd: "Tiền trọng tài", "Phí áo đấu")
3. Đội trưởng nhập số tiền
4. Đội trưởng submit
5. Hệ thống thêm khoản phí vào danh sách
6. (Lặp lại cho các khoản phí khác)

**Postconditions:**
- Các khoản phí tự do được lưu
- Hiển thị trong thỏa thuận chi phí

---

## DOMAIN: NOTIFICATION & CHAT

### UC-N01: Chat giữa 2 đội trưởng (luồng B)

**Actor:** Team Captain (cả 2 bên)

**Preconditions:**
- Pairing đang ở trạng thái NEGOTIATING

**Main Flow:**
1. Đội trưởng A gửi tin nhắn
2. Hệ thống lưu tin nhắn (sender, receiver, content, timestamp)
3. Hệ thống gửi tin nhắn real-time cho đội trưởng B (WebSocket/polling)
4. Đội trưởng B nhận tin nhắn
5. (Lặp lại cho cuộc hội thoại)

**Postconditions:**
- Tin nhắn được lưu
- Retention: 90 ngày sau khi Match kết thúc (COMPLETED/ARCHIVED/CANCELLED)
- Chat không hỗ trợ tìm kiếm/xuất trong MVP

> **Implementation note:** WebSocket + SockJS (Spring Boot `.withSockJS()`). SockJS tự động fallback Long Polling nếu WebSocket bị firewall chặn.

---

### UC-N02: Gửi notification

**Actor:** System

**Preconditions:**
- Có sự kiện kích hoạt notification (11 loại, xem Overview §4)

**Main Flow:**
1. System nhận sự kiện (vd: Match confirmed, rating nhận được)
2. System tạo notification (type, recipient, content, timestamp, read_status)
3. System lưu notification (retention 30 ngày)
4. System gửi notification qua kênh (in-app, push nếu có)

**Postconditions:**
- Notification được lưu và gửi
- User xem notification trong app

---

### UC-N03: System xóa chat/notification hết retention

**Actor:** System

**Preconditions:**
- Chat/notification đã quá retention (90 ngày cho chat, 30 ngày cho notification)

**Main Flow:**
1. Scheduled job chạy hàng ngày
2. System query chat có `Match.end_time + 90 ngày < now`
3. System xóa chat hết retention
4. System query notification có `created_at + 30 ngày < now`
5. System xóa notification hết retention

**Postconditions:**
- Chat/notification hết retention được xóa
- Giảm tải DB

---

## Quyết định bổ sung đã chốt

1. **Cost agreement (luồng B):** Một bên đề xuất, bên kia xem trong chat/bảng tin. Không cần xác nhận riêng vì xác nhận Pairing = đồng ý cả package.
2. **Verification Badge revocation:** Thu hồi ngay lập tức khi điều kiện không đủ (Reliability <70, rating ≤3, phone unlinked, ≤5 trận).
3. **GPS permission revocation:** Fallback sang khu vực hồ sơ (phường/xã) ngay lập tức. Hiển thị thông báo "Bật GPS để tìm kèo gần bạn hơn".
4. **Report cooldown:** Không giới hạn. Mỗi trận chỉ report đối phương 1 lần/loại là đủ chống spam.
5. **Hard-delete policy:** Giữ mãi, không hard-delete. Lịch sử luôn khả dụng.
6. **Push notification:** FCM + APNs (miễn phí), push tất cả 11 loại. App xin quyền lúc cài; user chấp nhận → push, từ chối → in-app only.
7. **Admin panel scope:** 4 chức năng cốt lõi trong MVP: bật/tắt bộ môn, xử lý tranh chấp report bùng, xem/xác minh report địa điểm, đánh dấu đội/venue INACTIVE.
8. **Hoàn thiện hồ sơ sau đăng ký (2026-11-06):** Khi đăng ký thành công, hệ thống cho ứng dụng biết đây là tài khoản mới; ứng dụng tự đưa User đến màn hình sửa hồ sơ (UC-U02). Không có bước, trạng thái hay quy tắc "hồ sơ chưa hoàn thiện" riêng — lưu hồ sơ theo UC-U02 / BR-U03..U06. User đăng nhập lại vào tài khoản đã có (UC-U01 3a) đi thẳng màn hình chính.
