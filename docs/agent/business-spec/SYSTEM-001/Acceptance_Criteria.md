# Acceptance Criteria — ArenaFlow System

> Definition of Done (DoD) và test scenarios để xác nhận hệ thống đã đúng.

---

## 1. Definition of Done (DoD)

### 1.1. Chung cho mọi chức năng

- [ ] Tất cả use cases trong `Use_Cases.md` được implement
- [ ] Tất cả business rules trong `Business_Rules.md` được tuân thủ
- [ ] Happy path hoạt động đúng
- [ ] Exception flows được xử lý đúng
- [ ] Unit tests PASS (coverage ≥ 80%)
- [ ] Integration tests PASS
- [ ] API documentation cập nhật (OpenAPI/Swagger)
- [ ] Code review PASS (qua `code-reviewer` agent)
- [ ] Security review PASS (authentication, authorization, input validation)
- [ ] Performance đạt non-functional requirements (xem §3)
- [ ] Migration Flyway đã được test trên branch riêng
- [ ] Entity và migration đồng bộ (Hibernate `ddl-auto=validate` không lỗi)

### 1.2. DoD cho từng domain

#### USER DOMAIN
- [ ] OAuth flow (Google, Facebook, Phone OTP) hoạt động đúng
- [ ] Soft-delete giữ lịch sử và có thể khôi phục
- [ ] Hồ sơ cá nhân 1:1 với tài khoản

#### TEAM DOMAIN
- [ ] Chuyển quyền đội trưởng bắt buộc nhận
- [ ] Lịch sử thành viên và chuyển quyền được lưu
- [ ] Xử lý xóa tài khoản đội trưởng đúng (phải chuyển quyền hoặc giải tán đội trước, kể cả khi là người duy nhất — `USER-002` AC-U03-01..03)

#### MATCH DOMAIN
- [ ] Luồng A và B hoạt động độc lập
- [ ] Match CONFIRMED không được sửa giờ/sân/trình độ
- [ ] Tối đa 1 Match_Request ACCEPTED mỗi Match (ràng buộc dữ liệu)
- [ ] Race condition Pairing được xử lý đúng (timestamp xác nhận cuối)
- [ ] Scheduled job cập nhật trạng thái hết hạn chạy đúng mỗi 5 phút
- [ ] Lazy check khi API gọi hoạt động đúng
- [ ] Trừ Reliability đúng đội hủy, đúng biên khung giờ; không hủy/rút khi trận đã bắt đầu (BR-M09..BR-M11)
- [ ] Kèo của đội tạm ngưng/giải tán hoặc bộ môn tắt bị ẩn và chặn đúng (BR-M14, BR-M15)

#### REPUTATION DOMAIN
- [ ] Reliability Score chặn trong [0, 100]
- [ ] Completion Rate tính đúng (loại trừ CANCELLED ≥24h và ARCHIVED)
- [ ] Report tự động chấp nhận và trừ điểm đúng
- [ ] Tranh chấp report bùng trong 24h
- [ ] Reset bộ đếm report sau 5 trận sạch
- [ ] Verification Badge cấp/thu hồi tự động đúng

#### MERCENARY DOMAIN
- [ ] Chỉ tuyển sau khi Match CONFIRMED
- [ ] GPS ưu tiên; không có GPS → dùng khu vực hồ sơ
- [ ] Không hiển thị tọa độ chính xác (chỉ khoảng cách và khu vực)
- [ ] Tin tuyển hết hạn đúng lúc giờ kết thúc dự kiến Match

#### VENUE DOMAIN
- [ ] Admin và User đều có thể thêm địa điểm
- [ ] Ảnh tối đa 5, chỉ 1 is_primary
- [ ] Report không tự ẩn địa điểm (cần admin xác minh)
- [ ] Chỉ người tạo hoặc Admin sửa địa điểm / xóa ảnh; xóa ảnh chính tự chọn ảnh chính mới
- [ ] Không trùng report chờ xử lý cho cùng (user, địa điểm, lý do)

#### COST DOMAIN
- [ ] Thỏa thuận chi phí chỉ hiển thị, không thanh toán

#### NOTIFICATION DOMAIN
- [ ] Chat lưu 90 ngày sau Match kết thúc
- [ ] Notification lưu 30 ngày
- [ ] 11 loại notification hoạt động đúng

---

## 2. Functional Acceptance Criteria

### AC-U01: User đăng ký qua OAuth

**Given:** User chưa có tài khoản

**When:** User chọn đăng ký qua Google/Facebook/Phone OTP và xác thực thành công

**Then:**
- Tài khoản được tạo với trạng thái active
- Hồ sơ cá nhân 1:1 được tạo
- User nhận notification chào mừng
- User được chuyển đến màn hình sửa hồ sơ cá nhân (UC-U02); hồ sơ lưu theo AC-U02 (2026-11-06)

---

### AC-U02: User cập nhật hồ sơ cá nhân

**Given:** User đã đăng nhập

**When:** User cập nhật hồ sơ (tên, ảnh, vị trí ưa thích, khu vực) và submit

**Then:**
- Hồ sơ được lưu
- Validation đúng (tên không rỗng, ngày sinh ≥13 tuổi, khu vực từ danh mục chuẩn)
- Hiển thị "Cập nhật thành công"

---

### AC-U03: User xóa tài khoản (soft-delete)

**Given:** User đã đăng nhập; không còn là đội trưởng của đội nào chưa giải tán (`USER-002` BR-T06 sửa)

**When:** User xác nhận xóa tài khoản

**Then:**
- Tài khoản chuyển soft-deleted
- Lịch sử hiển thị "Người dùng đã xóa"
- Điểm/đánh giá lịch sử của đội khác giữ nguyên
- Không đội nào tự đổi trạng thái (`USER-002` BR-T14)
- User bị đăng xuất

---

### AC-T01: User tạo đội

**Given:** User đã đăng nhập

**When:** User nhập thông tin đội (tên, avatar, trình độ tối đa 3 nhãn) và submit

**Then:**
- Đội được tạo với trạng thái active
- User trở thành đội trưởng
- Lịch sử đội trưởng được ghi nhận (Captain_History)

---

### AC-T02: Đội trưởng mời thành viên

**Given:** User là đội trưởng

**When:** Đội trưởng nhập ID/username người được mời và submit

**Then:**
- Lời mời được tạo với trạng thái pending
- Người được mời nhận notification
- Người được mời phải chấp nhận để trở thành thành viên

---

### AC-T03: Chuyển quyền đội trưởng (bắt buộc nhận)

**Given:** User là đội trưởng, đội có ≥1 thành viên khác đang active

**When:** Đội trưởng chọn thành viên và xác nhận chuyển quyền

**Then:**
- Đội trưởng cũ trở thành thành viên
- Thành viên được chọn trở thành đội trưởng (bắt buộc, không có quyền từ chối)
- Lịch sử chuyển quyền được lưu (Captain_History)
- Cả 2 nhận notification

---

### AC-M01: Tạo kèo luồng A (đã có sân)

**Given:** User là đội trưởng; đội trưởng đã tự đặt sân bên ngoài

**When:** Đội trưởng nhập thông tin (sân, giờ, thời hạn trong khoảng tối đa 7 ngày trước giờ đá đến giờ bắt đầu) và submit

**Then:**
- Match được tạo với trạng thái OPEN
- Match hiển thị trong danh sách tìm kèo
- Thời hạn hợp lệ được lưu

---

### AC-M02: Tạo kèo luồng B (chưa có sân)

**Given:** User là đội trưởng

**When:** Đội trưởng nhập nhiều lựa chọn địa điểm/giờ, thời hạn ≤ giờ trận sớm nhất, và submit

**Then:**
- Match được tạo với trạng thái OPEN
- Match có nhiều lựa chọn địa điểm/giờ
- Match hiển thị trong danh sách tìm kèo

---

### AC-M03: Gửi và chấp nhận Match Request (luồng A)

**Given:** Match đang OPEN; đội khác gửi request

**When:** Tác giả chấp nhận 1 request

**Then:**
- Request đó chuyển ACCEPTED
- Match chuyển CONFIRMED
- Các request PENDING khác chuyển SUPERSEDED
- Tối đa 1 request ACCEPTED mỗi Match (ràng buộc dữ liệu)
- Tất cả đội liên quan nhận notification

---

### AC-M04: Xác nhận Pairing (luồng B, cả 2 bên)

**Given:** Pairing đang NEGOTIATING; 2 đội trưởng đã thương lượng qua chat

**When:** Cả 2 đội trưởng xác nhận qua UI riêng (không phải chat)

**Then:**
- Pairing chuyển CONFIRMED
- Match chuyển CONFIRMED
- Các Pairing/Request khác chuyển SUPERSEDED
- Timestamp xác nhận cuối được lưu (để xử lý race condition)

---

### AC-M05: Race condition — 2 pairing cùng xác nhận gần đồng thời

**Given:** Match luồng B có 2 pairing đang NEGOTIATING

**When:** Cả 2 pairing cùng được cả 2 bên xác nhận gần đồng thời (trong vài giây)

**Then:**
- Pairing có timestamp xác nhận cuối sớm hơn thắng → CONFIRMED
- Pairing kia chuyển SUPERSEDED
- Match chuyển CONFIRMED với pairing thắng
- Cả 2 cặp đội nhận notification (thắng: "Match confirmed", thua: "Pairing superseded")

---

### AC-M06: Hủy Match CONFIRMED

**Given:** Match đang CONFIRMED; trận chưa bắt đầu

**When:** Đội trưởng đội tạo kèo **hoặc** đội đã ghép hủy Match và nhập lý do

**Then:**
- Hệ thống tính khoảng cách từ lúc hủy đến giờ bắt đầu trận
- Reliability Score của **đội hủy** bị trừ: -2 (từ đủ 24h trở lên), -5 (từ đủ 4h đến dưới 24h), -10 (dưới 4h); hủy đúng 24h trước → -2, đúng 4h trước → -5 (BR-M09, BR-M10)
- Đội còn lại không bị trừ; đội trưởng không bị trừ điểm cá nhân
- Điểm sau khi trừ không nhỏ hơn 0 (BR-R03)
- Match chuyển CANCELLED
- (Nếu đội đã ghép hủy) request đã ACCEPTED của đội đó chuyển WITHDRAWN (BR-M12)
- (Nếu hủy ≥24h trước) Match loại khỏi mẫu số Completion Rate
- Đội kia nhận notification
- Chưa nhập lý do → hệ thống yêu cầu nhập, không hủy
- Trận đã bắt đầu → hệ thống báo "Trận đã bắt đầu, không thể hủy", không hủy, không trừ điểm (BR-M11)

---

### AC-M07: System cập nhật trạng thái hết hạn

**Given:** Match có `expires_at` trong quá khứ và trạng thái chưa terminal

**When:** Scheduled job chạy mỗi 5 phút HOẶC API được gọi (lazy check)

**Then:**
- Match chuyển EXPIRED
- Các Match_Request/Pairing PENDING chuyển EXPIRED
- Các bên liên quan nhận notification

---

### AC-M08: Match chuyển ARCHIVED khi hết giờ dự kiến

**Given:** Match đang CONFIRMED; đến `scheduled_end_time`

**When:** System scheduled job chạy

**Then:**
- Match chuyển ARCHIVED
- Push notification gửi cho 2 đội trưởng: "Trận đấu hôm nay thế nào? Hãy nhập tỷ số và đánh giá đồng đội!"

---

### AC-M09: Rút Match_Request đã ACCEPTED (BR-M12)

**Given:** Match CONFIRMED với request ACCEPTED của đội B; trận chưa bắt đầu

**When:** Đội trưởng đội B rút request và nhập lý do

**Then:**
- Request chuyển WITHDRAWN; Match chuyển CANCELLED (đội hủy = đội B)
- Chỉ đội B bị trừ Reliability theo BR-M10; đội tạo kèo không bị trừ
- Rút lần hai (hoặc đội B bấm "Hủy kèo" sau đó) không trừ thêm điểm
- Đội tạo kèo nhận notification "Kèo bị hủy"
- Rút request PENDING: request chuyển WITHDRAWN, không trừ điểm, Match giữ nguyên

---

### AC-M10: Kèo của đội không hoạt động hoặc bộ môn bị tắt (BR-M14, BR-M15)

**Given:** Đội A có Match OPEN; Admin đánh dấu đội A INACTIVE (hoặc đội A giải tán, hoặc Admin tắt bộ môn của Match)

**When:** Người dùng tìm kèo; đội khác gửi request; đội A (khi INACTIVE) chấp nhận/hủy

**Then:**
- Match không xuất hiện trong danh sách tìm kèo
- Không gửi được request mới cho Match
- Không chấp nhận được request (cả khi chính đội gửi request không còn hoạt động)
- Match không bị tự hủy; hết hạn theo thời hạn bài đăng
- Đội A khi INACTIVE vẫn hủy kèo, từ chối và rút request được
- Match CONFIRMED của đội/bộ môn đó vẫn chuyển ARCHIVED khi hết giờ
- Bộ môn đang tắt: không tạo được kèo mới cho bộ môn đó

---

### AC-M11: Hủy kèo / rút request khi trạng thái đã thay đổi (BR-M13)

**Given:** Đội trưởng tác giả đang thấy Match "đang mở"; đội trưởng đội gửi đang thấy request "đang chờ"

**When:** Request được chấp nhận (Match → CONFIRMED) trước khi — hoặc gần như cùng lúc — đội trưởng tác giả bấm hủy kèo, hoặc đội trưởng đội gửi bấm rút request, trên màn hình chưa tải lại

**Then:**
- Chấp nhận diễn ra trước (kể cả từ lâu trước khi bấm): hệ thống so với trạng thái đội trưởng đang thấy → từ chối với thông báo "Trạng thái đã thay đổi, vui lòng tải lại"; Match giữ CONFIRMED; request giữ ACCEPTED; không đội nào bị trừ điểm
- Hủy / rút diễn ra trước: Match CANCELLED (hủy) hoặc request WITHDRAWN (rút); thao tác chấp nhận bị từ chối
- Muốn hủy kèo đã ghép, đội trưởng phải tải lại, thấy "đã ghép" rồi hủy (có lý do, bị trừ điểm theo BR-M10)

---

### AC-M12: Giới hạn khi tạo kèo (BR-M16)

**Given:** Đội trưởng tạo kèo luồng A

**When:** Nhập trận dài hơn 12 giờ, hoặc chọn hơn 3 nhãn trình độ

**Then:**
- Hệ thống báo lỗi tại trường tương ứng; Match không được tạo
- Không chọn nhãn trình độ → Match hiển thị cho mọi trình độ và khớp mọi bộ lọc trình độ khi tìm kèo

---

### AC-R01: Đội trưởng đánh giá đối phương

**Given:** Match đã ARCHIVED hoặc COMPLETED; đội trưởng chưa đánh giá

**When:** Đội trưởng chọn rating 1-5 sao (hoặc "không đến") và submit

**Then:**
- Rating được lưu
- Match chuyển COMPLETED (nếu đang ARCHIVED)
- (Nếu rating ≥4.5) Đội được đánh giá nhận +1 Reliability Score
- Rating trung bình của đội được đánh giá được cập nhật
- Bên đã đánh giá có thể xem rating đối phương cho mình (sau khi đối phương đánh giá)

---

### AC-R02: Report đối phương (tự động chấp nhận)

**Given:** Match đã kết thúc; đội trưởng chọn loại report

**When:** Đội trưởng submit report (bùng, chơi xấu, giao tiếp tệ, không đúng trình độ)

**Then:**
- Report được lưu và tự động chấp nhận
- Hệ thống tra số lần bị report loại này
- Reliability Score của đội bị report bị trừ theo mức tăng dần (xem Business_Rules.md §3.2)
- Đội bị report nhận notification
- (Nếu report bùng) Có 24h để tranh chấp

---

### AC-R03: Tranh chấp report bùng

**Given:** Đội bị report bùng trong 24h; report chưa qua tranh chấp

**When:** Đội trưởng bị report chọn "Tranh chấp" và nhập bằng chứng

**Then:**
- Tranh chấp được tạo và chuyển admin
- Mức trừ Reliability tạm giữ (chưa hoàn lại)
- Admin xem xét và đưa ra quyết định cuối cùng
- (Nếu admin xác nhận đúng) Giữ nguyên mức trừ; Match không tính tử số Completion Rate
- (Nếu admin bác) Hoàn lại Reliability; Match tính tử số Completion Rate

---

### AC-R04: Reliability Score cộng/trừ đúng

**Given:** Có sự kiện ảnh hưởng Reliability Score

**When:** Match COMPLETED, Match CANCELLED, report được chấp nhận, rating ≥4.5

**Then:**
- Reliability Score được cập nhật đúng theo công thức (xem Business_Rules.md §3.1)
- Score chặn trong [0, 100]
- Bonus trận sạch (+5 ở trận 5, +10 ở trận 10) được áp dụng đúng
- Reset bộ đếm report sau 5 trận sạch

---

### AC-R05: Completion Rate tính đúng

**Given:** Match chuyển trạng thái ảnh hưởng Completion Rate

**When:** Match chuyển COMPLETED, CANCELLED, hoặc ARCHIVED

**Then:**
- Mẫu số: Match CONFIRMED, loại trừ CANCELLED ≥24h trước và ARCHIVED
- Tử số: Match COMPLETED có ≥1 đánh giá và không có report bùng được xác nhận
- Completion Rate = (tử số / mẫu số) × 100%
- (Nếu mẫu số <5) Hiển thị "Mới tham gia X/5 trận"
- (Nếu mẫu số ≥5) Hiển thị phần trăm

---

### AC-R06: Verification Badge cấp/thu hồi tự động

**Given:** User/đội có thay đổi ảnh hưởng điều kiện badge

**When:** System kiểm tra 4 điều kiện (phone + >5 trận + rating >3 + Reliability ≥70)

**Then:**
- (Nếu đủ 4 điều kiện) Badge được cấp; User/đội nhận notification
- (Nếu thiếu bất kỳ điều kiện nào) Badge bị thu hồi (nếu đã có); nhận notification

---

### AC-ME01: Tuyển mercenary (chỉ sau khi Match CONFIRMED)

**Given:** User là đội trưởng; Match đã CONFIRMED

**When:** Đội trưởng tạo tin tuyển (chọn vị trí, số lượng) và submit

**Then:**
- Tin tuyển được tạo với trạng thái OPEN
- Tin tuyển hiển thị trong danh sách tìm kèo mercenary
- Tin tuyển tự động EXPIRED khi đến `Match.scheduled_end_time`

---

### AC-ME02: Tìm kèo mercenary (GPS ưu tiên)

**Given:** User đã đăng nhập

**When:** User chọn "Tìm kèo mercenary"

**Then:**
- (Nếu cấp GPS) Hệ thống tìm kèo trong bán kính 3km (có thể điều chỉnh)
- (Nếu không cấp GPS) Hệ thống tìm dựa trên khu vực hồ sơ (phường/xã); hiển thị thông báo "Bật GPS để tìm kèo gần bạn hơn"
- Mỗi kèo hiển thị: khoảng cách (vd "2.3 km"), khu vực chung (vd "Quận 1"), vị trí cần, giờ đá
- KHÔNG hiển thị tọa độ chính xác

---

### AC-ME03: Mercenary rút lui sau khi được duyệt

**Given:** Đơn ứng tuyển đang APPROVED

**When:** Mercenary xác nhận rút lui

**Then:**
- Đơn chuyển WITHDRAWN
- Reliability cá nhân bị trừ: -1 (≥24h), -3 (4-24h), -5 (<4h)
- Đội trưởng nhận notification
- (Nếu tin tuyển đang FILLED) Chuyển về OPEN (thiếu người)

---

### AC-V01: Admin/User thêm địa điểm sân

**Given:** Actor đã đăng nhập (Admin hoặc User)

**When:** Actor nhập thông tin (tên, địa chỉ, tọa độ, loại sân) và submit

**Then:**
- Địa điểm được tạo với trạng thái active
- Người tạo được lưu
- Địa điểm hiển thị trong gợi ý
- KHÔNG cần admin duyệt trước

---

### AC-V02: Upload ảnh sân (tối đa 5)

**Given:** Địa điểm đã tồn tại; địa điểm có <5 ảnh

**When:** Actor upload ảnh và đánh dấu is_primary

**Then:**
- Ảnh được lưu vào `venue_images`
- (Nếu is_primary=true) Ảnh cũ is_primary được chuyển false
- Chỉ 1 ảnh is_primary=true mỗi venue (ràng buộc dữ liệu)

---

### AC-V03: Report địa điểm (không tự ẩn)

**Given:** Địa điểm tồn tại

**When:** User submit report địa điểm (ngừng hoạt động, thông tin sai)

**Then:**
- Report được lưu (người report, lý do, thời gian)
- Admin nhận notification
- Địa điểm KHÔNG tự ẩn (cần admin xác minh)
- Admin xem chi tiết từng report
- User đã có report cùng lý do đang chờ xử lý → bị từ chối, không tạo report mới
- Địa điểm đã ngừng hoạt động → không cho report

---

### AC-V04: Sửa thông tin địa điểm

**Given:** Địa điểm tồn tại

**When:** Actor sửa tên/địa chỉ/tọa độ/loại sân/điện thoại/website và submit

**Then:**
- Actor là người tạo hoặc Admin → thông tin mới được lưu, thời điểm cập nhật thay đổi
- Kèo tham chiếu địa điểm hiển thị thông tin mới
- Actor khác → bị từ chối, thông tin giữ nguyên
- Loại sân ngoài sân 5/7/11 → bị từ chối

---

### AC-V05: Xóa ảnh sân

**Given:** Địa điểm có 3 ảnh, ảnh A là ảnh chính, ảnh B có thứ tự hiển thị nhỏ nhất trong 2 ảnh còn lại

**When:** Người tạo địa điểm (hoặc Admin) xóa ảnh A

**Then:**
- Ảnh A không còn hiển thị; địa điểm còn 2 ảnh
- Ảnh B thành ảnh chính (đúng 1 ảnh chính)
- User không phải người tạo/Admin xóa ảnh → bị từ chối

---

### AC-C01: Thỏa thuận chi phí (chỉ hiển thị)

**Given:** Match đang NEGOTIATING (luồng B) hoặc trước CONFIRMED (luồng A)

**When:** Đội trưởng nhập thỏa thuận chi phí (loại, số tiền, cách chia) và submit

**Then:**
- Thỏa thuận được lưu
- Hiển thị trên bảng tin Match
- ArenaFlow KHÔNG thu/giữ tiền; 2 đội thanh toán bên ngoài
- (Luồng A) Chỉ đội trưởng đội tạo kèo nhập/sửa, chỉ khi Match OPEN; lưu lại thay toàn bộ thỏa thuận cũ (BR-C03)
- Loại chi phí chỉ: tiền sân, tiền nước, cả hai; tối đa 10 khoản tự nhập; số tiền > 0 và ≤ 1.000.000.000 VND (BR-C04)

---

### AC-N01: Chat giữa 2 đội trưởng (luồng B, real-time)

**Given:** Pairing đang NEGOTIATING

**When:** Đội trưởng A gửi tin nhắn

**Then:**
- Tin nhắn được lưu (sender, receiver, content, timestamp)
- Tin nhắn được gửi real-time cho đội trưởng B (độ trễ <1s)
- Chat lưu 90 ngày sau khi Match kết thúc (COMPLETED/ARCHIVED/CANCELLED)

---

### AC-N02: 11 loại notification hoạt động đúng

**Given:** Có sự kiện kích hoạt notification

**When:** System tạo notification

**Then:**
- Notification được lưu (type, recipient, content, timestamp, read_status)
- Notification gửi qua kênh in-app (push nếu có)
- Notification lưu 30 ngày; sau đó xóa

---

## 3. Non-Functional Acceptance Criteria

### 3.1. Performance

| Endpoint/Feature | Metric | Target | Ghi chú |
|---|---|---|---|
| API danh sách Match (tìm kèo) | Response time | <2s | Với ≤1000 Match OPEN |
| API danh sách mercenary | Response time | <2s | Với GPS/khu vực filter |
| API tạo Match | Response time | <1s | Bao gồm validation |
| API chấp nhận Match Request | Response time | <1s | Bao gồm cập nhật cascade (SUPERSEDED) |
| API xác nhận Pairing | Response time | <1s | Bao gồm xử lý race condition |
| API đánh giá/report | Response time | <1s | Bao gồm cập nhật Reliability Score |
| Scheduled job (cập nhật hết hạn) | Interval | Mỗi 5 phút | Xử lý ≤10000 Match/Request/Pairing |
| Chat real-time | Latency | <1s | WebSocket hoặc polling |
| Concurrent requests | Throughput | ≥100 req/s | Load test với 100 concurrent users |

### 3.2. Security

- [ ] Tất cả API yêu cầu authentication (JWT hoặc session)
- [ ] Authorization đúng (User chỉ xem/sửa của mình; đội trưởng quản lý đội; admin có full access)
- [ ] Input validation: chặn SQL injection, XSS, command injection
- [ ] Sensitive data không log (password, OAuth token)
- [ ] HTTPS bắt buộc trong production
- [ ] CORS cấu hình đúng (chỉ frontend domain)
- [ ] Rate limiting: tối đa 100 requests/phút/IP cho public endpoints

### 3.3. Availability & Reliability

- [ ] Uptime ≥99% (downtime <7.2 giờ/tháng)
- [ ] Graceful degradation khi có lỗi (trả HTTP 503, không crash)
- [ ] Database backup tự động hàng ngày
- [ ] Retry logic cho external services (OAuth, push notification)
- [ ] Health check endpoint (`/actuator/health`) hoạt động

### 3.4. Scalability

- [ ] Database connection pool đủ lớn (min 10, max 50 connections)
- [ ] Pagination bắt buộc cho danh sách (tối đa 50 items/page)
- [ ] Index đủ cho truy vấn thường xuyên (xem `non-functional-standards.md`)
- [ ] Scheduled job không block API requests

### 3.5. Data Integrity

- [ ] Ràng buộc dữ liệu được enforce (tối đa 1 ACCEPTED request, chỉ 1 is_primary image)
- [ ] Transaction đúng cho operations phức tạp (chấp nhận request → cascade SUPERSEDED)
- [ ] Soft-delete không xóa dữ liệu vật lý
- [ ] Lịch sử không bị xóa (Team_Member, Captain_History, Rating, Report)

---

## 4. Test Scenarios

### Scenario 1: Happy path — Tạo kèo luồng A và ghép thành công

1. User A đăng ký qua Google, hoàn thiện hồ sơ, tạo đội "Team Alpha"
2. User A tạo kèo luồng A (sân X, ngày mai 18:00-20:00, 7v7, trình độ "Nhiệt tình")
3. User B (đội "Team Beta") tìm kèo, thấy kèo của A, gửi Match Request
4. User A xem request, chấp nhận
5. → Match chuyển CONFIRMED; request chuyển ACCEPTED; User A và B nhận notification
6. (Sau trận) User A đánh giá User B: 5 sao → User B nhận +1 Reliability
7. → Match chuyển COMPLETED; Completion Rate của cả 2 tăng

**Expected:**
- Match lifecycle: OPEN → CONFIRMED → ARCHIVED → COMPLETED
- Request lifecycle: PENDING → ACCEPTED
- Reliability Score: cả 2 đội +2 (hoàn thành), User B thêm +1 (rating 5 sao)
- Completion Rate: tử số +1, mẫu số +1

---

### Scenario 2: Happy path — Tạo kèo luồng B, thương lượng, xác nhận

1. User A tạo kèo luồng B (nhiều lựa chọn sân Y/Z, giờ 18:00/19:00, chưa chốt)
2. User B nhận bài đăng → Pairing NEGOTIATING → chat mở
3. User A và B chat: "18:00 sân Y được không?" — "OK" — "50-50 tiền sân?" — "OK"
4. User A xác nhận qua UI (timestamp T1)
5. User B xác nhận qua UI (timestamp T2, T2 > T1)
6. → Pairing CONFIRMED (timestamp xác nhận cuối = T2); Match CONFIRMED
7. → Match chốt sân Y, 18:00

**Expected:**
- Pairing lifecycle: NEGOTIATING → CONFIRMED
- Match lifecycle: OPEN → CONFIRMED
- Chat lưu 90 ngày sau Match kết thúc
- Timestamp xác nhận cuối được lưu (T2)

---

### Scenario 3: Edge case — Race condition 2 pairing cùng xác nhận

1. User A tạo kèo luồng B
2. User B và User C cùng nhận bài đăng → 2 Pairing (B và C)
3. Pairing B: cả 2 xác nhận gần đồng thời (TB_last = 10:00:05.123)
4. Pairing C: cả 2 xác nhận gần đồng thời (TC_last = 10:00:05.456)
5. → Pairing B thắng (TB_last < TC_last); Pairing C chuyển SUPERSEDED
6. → Match CONFIRMED với Pairing B

**Expected:**
- Chỉ 1 Pairing CONFIRMED
- Pairing thua chuyển SUPERSEDED
- User C nhận notification "Pairing superseded"

> **Implementation note:** Optimistic Lock với JPA `@Version` trên entity Match, retry max 3 lần khi conflict. Frontend không gửi version; backend tự đọc version hiện tại khi xử lý request.

---

### Scenario 4: Edge case — Hủy Match CONFIRMED sát giờ trận

1. User A và B ghép kèo thành công, Match CONFIRMED (giờ trận: ngày mai 18:00)
2. Ngày mai 16:00 (2 giờ trước trận), User A hủy kèo
3. → Reliability Score của A bị trừ -10 (<4 giờ)
4. → Match chuyển CANCELLED
5. → User B nhận notification "Kèo bị hủy"

**Expected:**
- Match CANCELLED
- User A: Reliability -10
- Match tính vào mẫu số Completion Rate nhưng không tính tử số (hủy <24h)

---

### Scenario 5: Exception — Tạo kèo với thời hạn không hợp lệ

1. User A tạo kèo luồng A (giờ trận: ngày mai 18:00)
2. User A chọn thời hạn: 10 ngày trước trận (>7 ngày)
3. → Hệ thống hiển thị lỗi "Thời hạn phải trong khoảng tối đa 7 ngày trước giờ đá đến giờ bắt đầu"
4. → Match KHÔNG được tạo

**Expected:**
- Validation thất bại
- Match không tồn tại trong DB

---

### Scenario 6: Exception — Chấp nhận Match Request khi Match đã CONFIRMED

1. User A tạo kèo; User B và C gửi request
2. User A chấp nhận request của B → Match CONFIRMED; request C chuyển SUPERSEDED
3. User A cố gắng chấp nhận request của C (race condition hoặc UI chậm)
4. → Hệ thống hiển thị "Kèo đã được ghép bởi request khác"
5. → Request C vẫn ở SUPERSEDED

**Expected:**
- Chỉ 1 request ACCEPTED
- Tối đa 1 request ACCEPTED mỗi Match (ràng buộc dữ liệu)

---

### Scenario 7: Mercenary — Tuyển, duyệt, rút lui

1. User A và B ghép kèo thành công (Match CONFIRMED, giờ trận: ngày mai 18:00)
2. User A tạo tin tuyển: Tiền vệ, 2 người
3. User C và D ứng tuyển
4. User A duyệt C, từ chối D
5. Ngày mai 14:00 (4 giờ trước trận), User C rút lui
6. → Reliability cá nhân của C bị trừ -3 (4-24h)
7. → Tin tuyển chuyển từ FILLED về OPEN (thiếu 1 người)
8. → User A nhận notification "Mercenary rút lui"

**Expected:**
- Mercenary Application C: APPROVED → WITHDRAWN
- Reliability cá nhân C: -3
- Tin tuyển: FILLED → OPEN

---

### Scenario 8: Report bùng — Tranh chấp và admin xử lý

1. User A và B ghép kèo (Match CONFIRMED, giờ trận: hôm nay 18:00)
2. 20:00 (sau trận), User A report User B bùng
3. → Report tự động chấp nhận; Reliability của B bị trừ -5 (lần 1 bị report bùng)
4. → User B nhận notification "Bạn bị report bùng. Có 24h để tranh chấp."
5. User B tranh chấp trong 1 giờ, gửi bằng chứng (ảnh check-in sân)
6. → Tranh chấp chuyển admin
7. Admin xem bằng chứng, quyết định: bác report (B đã đến)
8. → Reliability của B được hoàn lại +5
9. → Match tính tử số Completion Rate
10. → Cả 2 nhận notification về quyết định

**Expected:**
- Report: auto_accepted → disputed → rejected
- Reliability B: -5 → +5 (hoàn lại)
- Match tính vào Completion Rate đúng

---

### Scenario 9: Verification Badge — Cấp và thu hồi tự động

1. User A hoàn thành 6 trận, rating trung bình 4.2 sao, Reliability 85, phone linked
2. → System kiểm tra 4 điều kiện: ✓ phone, ✓ >5 trận, ✓ rating >3, ✓ Reliability ≥70
3. → Badge được cấp; User A nhận notification "Bạn đã nhận Verification Badge"
4. User A bị report 5 lần → Reliability giảm xuống 65
5. → System kiểm tra: ✗ Reliability <70
6. → Badge bị thu hồi; User A nhận notification "Verification Badge đã bị thu hồi"

**Expected:**
- Badge cấp khi đủ 4 điều kiện
- Badge thu hồi ngay khi thiếu bất kỳ điều kiện nào

---

### Scenario 10: System — Scheduled job cập nhật hết hạn

1. 10:00: User A tạo kèo với `expires_at` = 10:05
2. 10:05: Scheduled job chạy
3. → Match chuyển EXPIRED
4. → Các Match_Request PENDING chuyển EXPIRED
5. → User A và các đội đã gửi request nhận notification

**Expected:**
- Scheduled job chạy đúng mỗi 5 phút
- Trạng thái cập nhật đúng (EXPIRED)
- Notification gửi đúng

---

### Scenario 11: Lazy check — API gọi khi Match hết hạn

1. 10:00: User A tạo kèo với `expires_at` = 10:05
2. 10:07: User B gọi API xem chi tiết Match (trước khi scheduled job chạy lần tiếp)
3. → Lazy check kiểm tra `expires_at` ≤ now
4. → Match chuyển EXPIRED ngay lập tức
5. → API trả trạng thái EXPIRED

**Expected:**
- Lazy check hoạt động đúng
- Trạng thái cập nhật ngay khi API gọi

---

## 5. Quyết định bổ sung đã chốt

1. **Cost agreement (luồng B):** Một bên đề xuất, bên kia xem. Không test xác nhận riêng cho chi phí.
2. **Verification Badge revocation:** Thu hồi ngay lập tức khi điều kiện không đủ. Test scenario: Reliability giảm <70 → badge thu hồi ngay + notification.
3. **GPS permission revocation:** Fallback sang khu vực hồ sơ ngay. Test scenario: User thu hồi GPS → tìm kèo dựa khu vực hồ sơ + thông báo "Bật GPS...".
4. **Report cooldown:** Không giới hạn. Test scenario: User report 3 trận khác nhau trong 1 ngày → cả 3 report đều được chấp nhận.
5. **Hard-delete policy:** Giữ mãi. Không có test scenario hard-delete.
6. **Push notification:** FCM + APNs, push tất cả 11 loại. DoD bổ sung:
   - [ ] FCM/APNs integration hoạt động (gửi test push thành công)
   - [ ] Device token được lưu khi user cấp quyền notification
   - [ ] 11 loại notification gửi push đúng lúc (±1 phút sau sự kiện)
   - [ ] User từ chối quyền → chỉ in-app, không crash
7. **Admin panel:** 4 chức năng MVP. DoD bổ sung:
   - [ ] Admin có thể bật/tắt bộ môn qua UI
   - [ ] Admin xem chi tiết tranh chấp report bùng, đưa ra quyết định (xác nhận/bác)
   - [ ] Admin xem danh sách venue report, xác minh và đánh dấu venue INACTIVE
   - [ ] Admin đánh dấu đội INACTIVE (với lý do)
8. **Load test scale:** 100 req/s đủ cho MVP (ước tính ~10K active users). Điều chỉnh nếu scale lớn hơn sau khi launch.

> **Implementation notes đã chốt:**
> - Concurrent pairing: Optimistic Lock + retry, test scenario race condition (Scenario 3) đã phản ánh cơ chế này
> - Chat: WebSocket + SockJS, latency target <1s (WebSocket) hoặc ~300ms (Long Polling fallback)

---

## 6. Trạng thái tài liệu

**Trạng thái:** ✅ **Hoàn chỉnh** — Tất cả acceptance criteria đã được định nghĩa rõ ràng, không còn [cần xác nhận]  
**Sẵn sàng cho:** Implementation và testing
