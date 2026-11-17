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
- [ ] Race condition Pairing được xử lý đúng (Pairing hoàn tất trước thắng)
- [ ] Scheduled job cập nhật trạng thái hết hạn chạy đúng mỗi 5 phút
- [ ] Lazy check khi API gọi hoạt động đúng
- [ ] Trừ Reliability đúng đội hủy, đúng biên khung giờ; không hủy/rút khi trận đã bắt đầu (BR-M09..BR-M11)
- [ ] Kèo của đội tạm ngưng/giải tán hoặc bộ môn tắt bị ẩn và chặn đúng (BR-M14, BR-M15)

#### REPUTATION DOMAIN
- [ ] Reliability Score chặn trong [0, 100]
- [ ] Completion Rate tính đúng theo Business_Rules §3.3 (mẫu số: COMPLETED, CANCELLED <24h chỉ của đội hủy, ARCHIVED có report bùng; tử số: COMPLETED không bị report bùng)
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
- [ ] Cuộc chat giữa hai đội mở cho cả luồng A (từ khi gửi request) và luồng B (từ khi nhận kèo); nhắn được / chỉ xem đúng BR-N05
- [ ] Chat lưu 90 ngày sau Match kết thúc
- [ ] Notification lưu 30 ngày; quá 30 ngày không còn hiển thị (BR-N02)
- [ ] 35 loại notification hoạt động đúng (Business_Rules §8.6); không tạo thông báo cho các trường hợp BR-N07
- [ ] Người nhận đúng: đội trưởng tại lúc tạo thông báo, mỗi Admin một thông báo, người đã xóa tài khoản không nhận (BR-N08)
- [ ] Nhắc trước trận đúng một lần, khoảng 2 giờ trước giờ bắt đầu (BR-N13); nhắc đánh giá và "nhận đánh giá" đúng BR-N14
- [ ] Xem danh sách, số chưa đọc, đánh dấu một / tất cả đã đọc (BR-N11); app đang mở nhận thông báo ngay (BR-N10)
- [ ] Tin nhắn chat không tạo thông báo; người xem được cuộc chat nhận tín hiệu cập nhật "Tin nhắn" (BR-N12)

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
- Endpoint thường hiển thị "Người dùng đã xóa" và ảnh `null`; mọi endpoint `/api/v1/admin/**` hiển thị tên thật kèm cờ `is…Deleted`; chỉ trả ảnh thật nếu response đã có field ảnh user và còn đường dẫn. FE Admin gắn nhãn "đã xóa" theo cờ; không tự thêm field ảnh user vào response chưa có field đó (BC-U02)
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

**When:** Đội trưởng tìm và chọn người được mời (theo số điện thoại hoặc tên hiển thị — `USER-003` UC-U05) và submit

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
- **Chỉ đội trưởng mới** nhận notification; đội trưởng cũ (người thao tác) không nhận (user chốt 2026-11-16)
- Thông báo đội trưởng cũ đã nhận ở lại người cũ; thông báo của đội tạo sau khi chuyển quyền đến đội trưởng mới (BR-N08)

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

**When:** Đội trưởng nhập 1–5 lựa chọn địa điểm, 1–5 khung giờ, thời hạn sau hiện tại, ≤ giờ trận sớm nhất và ≥ giờ trận sớm nhất − 7 ngày (BR-M03), và submit

**Then:**
- Match được tạo với trạng thái OPEN
- Match có danh sách lựa chọn địa điểm và danh sách khung giờ
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

**Given:** Pairing đang NEGOTIATING và đã có phương án chốt (1 lựa chọn địa điểm + 1 khung giờ của Match)

**When:** Cả 2 đội trưởng xác nhận cùng gói (phương án + chi phí + tiền cọc) qua UI riêng (không phải chat)

**Then:**
- Pairing chuyển CONFIRMED; thời điểm hoàn tất được lưu
- Match chuyển CONFIRMED với địa điểm + giờ của phương án chốt; chi phí + tiền cọc + khoản tự nhập được chép từ Pairing sang Match (BR-C05)
- Các Pairing/Request khác chuyển SUPERSEDED
- Chưa có phương án chốt → không xác nhận được ("Chưa chốt địa điểm và giờ")
- Gói đổi (chốt phương án khác / lưu chi phí) sau khi một bên đã xác nhận → xác nhận của cả hai bị xoá; lần xác nhận trên gói cũ bị từ chối "Phương án đã thay đổi, vui lòng tải lại" (BR-M19)
- Địa điểm của phương án đã ngừng hoạt động → không chốt được phương án đó và không xác nhận được ("Địa điểm đã ngừng hoạt động" — BR-M20)

---

### AC-M05: Race condition — 2 pairing cùng xác nhận gần đồng thời

**Given:** Match luồng B có 2 pairing đang NEGOTIATING

**When:** Cả 2 pairing cùng được cả 2 bên xác nhận gần đồng thời (trong vài giây)

**Then:**
- Pairing **hoàn tất trước** (lần xác nhận của bên thứ hai được ghi nhận trước) thắng → CONFIRMED; thời điểm hoàn tất được lưu trên Pairing thắng
- Pairing kia chuyển SUPERSEDED; lần xác nhận của nó bị từ chối với "Thương lượng đã kết thúc"
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
- Chuỗi trận sạch của đội hủy về 0 (user chốt 2026-11-12)
- (Nếu hủy ≥24h trước) Match không tính vào mẫu số Completion Rate của đội nào
- (Nếu hủy <24h trước) Match chỉ tính vào mẫu số Completion Rate của **đội hủy**; đội còn lại không tính (user chốt 2026-11-12)
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
- Thông báo nhắc gửi cho 2 đội trưởng: "Trận đấu hôm nay thế nào? Hãy nhập tỷ số và đánh giá đồng đội!"; mỗi mercenary APPROVED của trận nhận nhắc đánh giá đội thuê mình (BR-N14)
- Match chuyển tiếp ARCHIVED → COMPLETED (sau đánh giá): không có thông báo

---

### AC-M09: Rút Match_Request đã ACCEPTED (BR-M12)

**Given:** Match CONFIRMED với request ACCEPTED của đội B; trận chưa bắt đầu

**When:** Đội trưởng đội B rút request và nhập lý do

**Then:**
- Request chuyển WITHDRAWN; Match chuyển CANCELLED (đội hủy = đội B)
- Chỉ đội B bị trừ Reliability theo BR-M10; đội tạo kèo không bị trừ
- Chuỗi trận sạch của đội B về 0; rút <24h trước trận → Match chỉ tính vào mẫu số Completion Rate của đội B (user chốt 2026-11-12)
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

**When:** Đội trưởng chọn sao nguyên 1–5 (hoặc "đội kia không đến") và submit

**Then:**
- Đánh giá được lưu (kể cả "đội kia không đến" — không có sao, không có tỷ số); nhận xét ≤500 ký tự (BR-R09)
- Match chuyển COMPLETED (nếu đang ARCHIVED)
- Khi Match chuyển COMPLETED bởi đánh giá sao: cả hai đội +2 Reliability; bởi "đội kia không đến": đội đánh giá +2, đội vắng 0 và chuỗi trận sạch của đội vắng giữ nguyên (user chốt 2026-11-12)
- (Nếu đánh giá 5 sao) Đội được đánh giá nhận +1 Reliability Score; 4 sao trở xuống không cộng (user chốt 2026-11-12)
- Sao trung bình của đội được đánh giá phản ánh đánh giá mới khi hiển thị; "đội kia không đến" không tính vào trung bình
- Bên đã đánh giá có thể xem rating đối phương cho mình (sau khi đối phương đánh giá)
- Đội được đánh giá đã đánh giá trước → đội trưởng của đội đó nhận thông báo "nhận đánh giá"; chưa đánh giá → không nhận thông báo (BR-N14, BC-R04)
- Người là đội trưởng của cả hai đội trong trận → bị từ chối, không lưu đánh giá (BR-R08)
- Đội của người đánh giá INACTIVE hoặc DISBANDED → bị từ chối; đội được đánh giá INACTIVE hoặc DISBANDED vẫn đánh giá được (BR-R10)

---

### AC-R02: Report đối phương (tự động chấp nhận)

**Given:** Match ARCHIVED/COMPLETED, hoặc CANCELLED sau khi đã CONFIRMED (BR-R07); đội trưởng chọn loại report

**When:** Đội trưởng submit report (bùng, chơi xấu, giao tiếp tệ, không đúng trình độ) với lý do bắt buộc 1–1000 ký tự (BR-R09)

**Then:**
- Report được lưu và tự động chấp nhận
- Hệ thống tra số lần bị report loại này
- Reliability Score của đội bị report bị trừ theo mức tăng dần (xem Business_Rules.md §3.2); chuỗi trận sạch của đội bị report về 0, +2 / bonus đã cộng không bị thu hồi (user chốt 2026-11-12)
- Đội bị report nhận notification
- (Nếu report bùng) Match bị loại khỏi tử số Completion Rate của đội bị report ngay; có 24h để tranh chấp
- Match CANCELLED sau khi đã CONFIRMED: chỉ report được "giao tiếp tệ", loại khác bị từ chối; Match OPEN/CONFIRMED/EXPIRED hoặc CANCELLED khi còn OPEN mà hai đội chưa có tin nhắn trong cuộc chat của kèo: không report được (BR-R07 — user chốt 2026-11-12; report qua chat: AC-R08)
- Người là đội trưởng của cả hai đội trong trận → bị từ chối, không lưu report (BR-R08)
- Đội của người report INACTIVE hoặc DISBANDED → bị từ chối; đội bị report INACTIVE hoặc DISBANDED vẫn report được (BR-R10)

---

### AC-R08: Report "giao tiếp tệ" qua chat hai đội

**Given:** Cuộc chat giữa hai đội của một kèo (BR-N04) đã có ít nhất 1 tin nhắn; đội của người report ACTIVE (BR-R10)

**When:** Đội trưởng một đội report đội kia loại "giao tiếp tệ" với lý do 1–1000 ký tự (BR-R09)

**Then:**
- Report được nhận bất kể trạng thái request (PENDING, REJECTED, SUPERSEDED, EXPIRED, WITHDRAWN), Pairing (NEGOTIATING, SUPERSEDED, EXPIRED) và kèo (OPEN, CONFIRMED trước trận, CANCELLED, EXPIRED) (BR-R07)
- Vd: kèo luồng A của Alpha, request của Y còn PENDING, đội trưởng Y chửi Alpha trong chat → Alpha report Y ngay; report tự chấp nhận, Y bị trừ theo §3.2 (−2 nếu lần 1), chuỗi trận sạch của Y về 0, Y nhận notification
- Hai đội đã ghép: report được ngay khi kèo CONFIRMED trước trận
- Chọn loại khác "giao tiếp tệ" khi trận chưa ARCHIVED/COMPLETED → bị từ chối
- Cuộc chat chưa có tin nhắn nào và kèo chưa ARCHIVED/COMPLETED/CANCELLED sau khi ghép → không report được
- Alpha report Y lần thứ hai loại "giao tiếp tệ" cho cùng kèo (qua chat hay sau trận) → bị từ chối; Y vẫn report Alpha được một lần
- Người là đội trưởng của cả hai đội → bị từ chối (BR-R08)
- Người đã giải tán đội của mình → bị từ chối, vẫn đọc được chat (BR-R10, BR-N05)

---

### AC-R03: Tranh chấp report bùng

**Given:** Đội bị report bùng trong 24h; report chưa qua tranh chấp

**When:** Đội trưởng bị report chọn "Tranh chấp" và bắt buộc nhập bằng chứng (chỉ văn bản, 1–1000 ký tự — BR-R09)

**Then:**
- Tranh chấp được tạo và chuyển admin
- Đội đã report và mỗi Admin nhận thông báo tranh chấp mới (BR-N08)
- Mức trừ Reliability tạm giữ (chưa hoàn lại)
- Admin xem xét và đưa ra quyết định cuối cùng, bắt buộc kèm ghi chú lý do 1–500 ký tự (BR-R09)
- (Nếu admin xác nhận đúng) Giữ nguyên mức trừ, không trừ thêm; Match không tính tử số Completion Rate (user chốt 2026-11-12)
- Đội bị report INACTIVE hoặc DISBANDED → không tranh chấp được (BR-R10)
- (Nếu admin bác) Hoàn đúng số điểm đã thực trừ của report (có thể ít hơn mức trong bảng nếu lúc trừ điểm đã chạm 0) và giảm 1 lần bị report bùng; Match tính lại vào tử số Completion Rate (nếu đã COMPLETED); chuỗi trận sạch không được khôi phục (user chốt 2026-11-12)

---

### AC-R04: Reliability Score cộng/trừ đúng

**Given:** Có sự kiện ảnh hưởng Reliability Score

**When:** Match COMPLETED, Match CANCELLED, report được chấp nhận, Admin bác report, nhận đánh giá 5 sao

**Then:**
- Reliability Score được cập nhật đúng theo công thức (xem Business_Rules.md §3.1)
- Score chặn trong [0, 100]
- Bonus trận sạch lặp ở mỗi bội số của 5: bội số của 10 (10, 20, 30…) → +10, còn lại (5, 15, 25…) → +5 (user chốt 2026-11-12)
- Admin xác nhận report bùng đúng không trừ thêm (user chốt 2026-11-12)
- Reset bộ đếm report sau 5 trận sạch

---

### AC-R05: Completion Rate tính đúng

**Given:** Đội/cá nhân có Match COMPLETED, CANCELLED hoặc ARCHIVED

**When:** Người dùng xem uy tín (Completion Rate tính khi hiển thị, không lưu — user chốt 2026-11-12)

**Then:**
- Mẫu số: Match COMPLETED; Match CANCELLED sau khi đã CONFIRMED mà hủy <24h trước trận — chỉ của đội hủy (kể cả đội rút Match_Request đã ACCEPTED), đội còn lại không tính; Match ARCHIVED mà đội bị report bùng (chưa bị Admin bác) (user chốt 2026-11-12)
- Tử số: Match COMPLETED mà đội không bị report bùng (chưa bị Admin bác) — loại ngay khi report; Admin bác → tính lại; đội bị đánh giá "đội kia không đến" mà không bị report bùng vẫn tính (user chốt 2026-11-12)
- Completion Rate = (tử số / mẫu số) × 100%
- (Nếu mẫu số <5) Hiển thị "Mới tham gia X/5 trận"
- (Nếu mẫu số ≥5) Hiển thị phần trăm

---

### AC-R06: Verification Badge cấp/thu hồi tự động

**Given:** User/đội có thay đổi ảnh hưởng điều kiện badge

**When:** System kiểm tra 4 điều kiện BR-R04 (số điện thoại — đội: đội trưởng hiện tại có số điện thoại; cá nhân: tài khoản có số điện thoại + >5 trận hoàn thành (tử số Completion Rate) + sao trung bình >3 + Reliability ≥70)

**Then:**
- (Nếu đủ 4 điều kiện) Badge được cấp; User/đội nhận notification
- (Nếu thiếu bất kỳ điều kiện nào) Badge bị thu hồi (nếu đã có); nhận notification
- Đội: cấp/thu hồi ngay sau đánh giá, report, quyết định tranh chấp; trừ điểm do hủy kèo đã ghép / rút Match_Request đã ACCEPTED, đổi đội trưởng, đội trưởng thêm/bỏ số điện thoại → cập nhật ở lần đối soát hằng ngày (trễ tối đa khoảng 1 ngày — Business_Rules §8.2, user chốt 2026-11-12)

---

### AC-R07: Xem đánh giá của trận và report của đội

**Given:** User là đội trưởng của đội trong trận (xem đánh giá) hoặc của đội (xem report đã gửi / bị nhận)

**When:** Đội trưởng mở xem đánh giá của trận hoặc danh sách report của đội

**Then:**
- Thấy đánh giá đội mình đã gửi; rating đối phương dành cho đội mình chỉ hiện sau khi đội mình đã đánh giá, không hiện trạng thái "đối phương đã/chưa đánh giá" (BC-R03, BC-R04)
- Thấy các report đội mình đã gửi và đã nhận
- Đội của người xem INACTIVE hoặc DISBANDED → bị từ chối (BR-R10)

---

### AC-ME01: Tuyển mercenary (chỉ sau khi Match CONFIRMED)

**Given:** User là đội trưởng; Match đã CONFIRMED

**When:** Đội trưởng (đội tạo kèo hoặc đội đã ghép) chọn một hoặc nhiều vị trí, mỗi vị trí kèm số lượng, và submit

**Then:**
- Mỗi vị trí thành một tin tuyển trạng thái OPEN (BR-ME05)
- Tin tuyển hiển thị trong danh sách tìm kèo mercenary
- Tin tuyển tự động EXPIRED khi đến `Match.scheduled_end_time`

---

### AC-ME02: Tìm kèo mercenary (GPS ưu tiên)

**Given:** User đã đăng nhập

**When:** User chọn "Tìm kèo mercenary"

**Then:**
- (Nếu cấp GPS) Tin tuyển có sân trong bán kính mặc định 3 km, chỉnh được 1–20 km, sắp theo khoảng cách
- (Nếu không cấp GPS) Tin tuyển có sân cùng tỉnh với phường trong hồ sơ, sân cùng phường lên đầu rồi theo giờ đá; không có khoảng cách; hiển thị thông báo "Bật GPS để tìm kèo gần bạn hơn"
- Mỗi kèo hiển thị: khoảng cách (khi có GPS, vd "2.3 km"), khu vực "phường, tỉnh" (vd "Phường Sài Gòn, TP. Hồ Chí Minh"), vị trí cần, giờ đá
- KHÔNG hiển thị tọa độ, tên sân, địa chỉ khi chưa được duyệt; đơn APPROVED thấy tên sân, địa chỉ, tọa độ và tên đội trưởng (BR-ME04)

---

### AC-ME03: Mercenary rút lui sau khi được duyệt

**Given:** Đơn ứng tuyển đang APPROVED

**When:** Mercenary xác nhận rút lui

**Then:**
- Đơn chuyển WITHDRAWN
- Reliability cá nhân bị trừ: -1 (≥24h), -3 (4-24h), -5 (<4h); chuỗi trận sạch cá nhân về 0 (BR-ME11)
- Đội trưởng nhận notification
- (Nếu tin tuyển đang FILLED) Chuyển về OPEN (thiếu người)

---

### AC-ME04: Ứng tuyển và duyệt

**Given:** Tin tuyển OPEN của trận A–B

**When:** User ứng tuyển; đội trưởng duyệt / từ chối

**Then:**
- Thành viên đang hoạt động của đội A hoặc đội B, hoặc người đã có đơn APPROVED ở trận khác trùng giờ → bị từ chối cả lúc ứng tuyển lẫn lúc duyệt (BR-ME06)
- Mỗi người tối đa một đơn PENDING/APPROVED cho mỗi tin; rút đơn PENDING không ảnh hưởng uy tín
- Duyệt đủ số lượng → tin FILLED; đơn PENDING còn lại giữ nguyên, không duyệt được cho tới khi tin về OPEN (BR-ME07)
- Đội trưởng không hủy duyệt được đơn APPROVED (BR-ME10)

---

### AC-ME05: Sửa số lượng / đóng tin tuyển

**Given:** Tin tuyển OPEN hoặc FILLED, đã duyệt 2 người

**When:** Đội trưởng đổi số lượng hoặc đóng tin

**Then:**
- Số lượng 1 → bị từ chối (thấp hơn số đã duyệt); số lượng 2 → FILLED; số lượng 4 → OPEN; vị trí không đổi được (BR-ME08)
- Đóng tin → CLOSED; đơn PENDING tự REJECTED, ứng viên nhận notification (BR-ME07)
- Đến giờ kết thúc dự kiến của Match → tin EXPIRED; đơn PENDING tự REJECTED

---

### AC-ME06: Match bị hủy khi đang tuyển

**Given:** Match CONFIRMED có tin tuyển; một đơn APPROVED, một đơn PENDING

**When:** Match chuyển CANCELLED (hủy kèo hoặc rút Match_Request đã ACCEPTED)

**Then:**
- Mọi tin OPEN/FILLED của trận tự CLOSED; đơn PENDING tự REJECTED; đơn APPROVED giữ nguyên (BR-ME09)
- Ứng viên và mercenary nhận notification
- Mercenary không bị trừ điểm; trận không tính Completion Rate cá nhân; không đánh giá nhau

---

### AC-ME07: Đánh giá giữa đội trưởng và mercenary

**Given:** Match ARCHIVED/COMPLETED; mercenary còn APPROVED lúc Match kết thúc (BR-ME12)

**When:** Đội trưởng đánh giá mercenary; mercenary đánh giá đội

**Then:**
- Đội trưởng chọn sao nguyên 1–5 hoặc "không đến" cho từng mercenary; mercenary chọn sao nguyên 1–5 cho đội (BR-ME13)
- Mercenary nhận 5 sao → +1 Reliability cá nhân; rating tính vào sao trung bình cá nhân
- Rating mercenary dành cho đội chỉ hiển thị riêng trên hồ sơ đội, không vào sao trung bình đội, không cộng Reliability đội
- Mỗi cặp (Match, người đánh giá, người được đánh giá) chỉ đánh giá một lần (BC-R02)
- Mercenary APPROVED nhận nhắc đánh giá đội khi trận kết thúc; bên được đánh giá chỉ nhận thông báo "nhận đánh giá" khi đã đánh giá bên kia trước (BR-N14)

---

### AC-ME08: Report và tranh chấp giữa đội trưởng và mercenary

**Given:** Match ARCHIVED/COMPLETED và mercenary đã tham gia; hoặc đơn ứng tuyển đã có tin nhắn chat; đội của đội trưởng ACTIVE

**When:** Đội trưởng report mercenary, hoặc mercenary report đội thuê mình, với lý do bắt buộc 1–1000 ký tự (BR-R09)

**Then:**
- Report tự chấp nhận; bên bị report bị trừ Reliability theo số lần (§3.2 — cá nhân khi là mercenary); chuỗi trận sạch về 0 (BR-ME14, BR-ME15)
- Report bùng: bên bị report có 24 giờ để tranh chấp, Admin quyết định như AC-R03; tranh chấp mới → bên đã report và mỗi Admin nhận thông báo (BR-N08)
- Đơn đã có tin nhắn chat (kể cả PENDING/REJECTED/WITHDRAWN, trận bị hủy): chỉ report được "giao tiếp tệ"; loại khác bị từ chối (BR-ME14)

---

### AC-ME09: Uy tín cá nhân của mercenary

**Given:** Mercenary còn APPROVED lúc Match kết thúc

**When:** Đội trưởng đánh giá mercenary

**Then:**
- Đánh giá bằng sao → mercenary +2 Reliability cá nhân, tính hoàn thành; không bị report (chưa bị Admin bác) trong trận → chuỗi trận sạch +1 và bonus ở mốc bội số 5/10; bị report → chuỗi về 0 (BR-ME15)
- Đánh giá "không đến", hoặc đội trưởng chưa đánh giá → mercenary không được +2, không tính hoàn thành
- Badge cá nhân cập nhật ngay sau đánh giá, report, quyết định tranh chấp, rút lui; thêm/bỏ số điện thoại cập nhật ở lần đối soát hằng ngày (Business_Rules §8.2)
- Đánh giá giữa đội trưởng và mercenary không làm Match chuyển COMPLETED (BR-ME13)
- Rút lui sau APPROVED dưới 24 giờ trước trận → trận vào mẫu số Completion Rate cá nhân; mercenary không rút được từ giờ bắt đầu trận (BR-ME11)

---

### AC-ME10: Chat giữa đội trưởng và ứng viên

**Given:** Đơn ứng tuyển PENDING hoặc APPROVED

**When:** Đội trưởng hoặc ứng viên gửi tin nhắn 1..1000 ký tự

**Then:**
- Tin được lưu và bên kia nhận real-time; hiển thị người thực sự gửi (BR-ME16)
- Tin nhắn không tạo notification; bên kia đang mở app nhận tín hiệu cập nhật dấu "chưa đọc" của "Tin nhắn" (BR-N12)
- Đơn APPROVED vẫn nhắn được sau khi Match kết thúc hoặc bị hủy, đến khi hết hạn lưu
- Đơn REJECTED/WITHDRAWN → chỉ xem lịch sử, không gửi được
- Đội tuyển đã giải tán → người đã giải tán đội đọc được lịch sử, không gửi được; ứng viên vẫn nhắn theo trạng thái đơn (BR-ME16)
- Tin nhắn lưu 90 ngày sau khi Match kết thúc

---

### AC-ME11: Tuyển cho trận ngoài hệ thống

**Given:** Đội trưởng của đội ACTIVE; trận không có trên hệ thống

**When:** Đội trưởng chọn sân từ danh sách địa điểm, nhập giờ bắt đầu, giờ kết thúc, trình độ, tên đối thủ (tùy chọn), vị trí + số lượng, rồi submit

**Then:**
- Mỗi vị trí thành một tin OPEN; tìm kiếm, ứng tuyển, duyệt, chat, hiển thị như tin của Match trên hệ thống (BR-ME19)
- Sửa sân / giờ / trình độ bị từ chối khi đã có đơn APPROVED
- Đội trưởng hủy trận ngoài trước giờ bắt đầu (từ giờ bắt đầu không hủy được) → tin CLOSED, đơn PENDING tự REJECTED, đơn APPROVED giữ nguyên, không ai bị trừ điểm, mercenary nhận thông báo
- Sau giờ kết thúc: đội trưởng và mercenary đánh giá / report nhau; mercenary được +2 khi được đánh giá bằng sao; đội không được cộng / trừ Reliability vì trận ngoài (trừ report của mercenary)

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
- Admin **không** nhận notification cho từng report; report "ngừng hoạt động" đưa địa điểm vào đợt cảnh báo — mỗi Admin nhận một thông báo, một lần mỗi đợt (BC-V05, BR-N08)
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

**Given:** Pairing đang NEGOTIATING (luồng B) — hai đội trưởng của Pairing đều sửa được; hoặc trước CONFIRMED (luồng A)

**When:** Đội trưởng nhập thỏa thuận chi phí (loại, số tiền, cách chia; luồng B thêm tiền cọc nếu có) và submit

**Then:**
- Thỏa thuận được lưu
- Hiển thị trên bảng tin Match (luồng B: sau khi ghép)
- ArenaFlow KHÔNG thu/giữ tiền (kể cả tiền cọc); 2 đội thanh toán bên ngoài
- (Luồng A) Chỉ đội trưởng đội tạo kèo nhập/sửa, chỉ khi Match OPEN; lưu lại thay toàn bộ thỏa thuận cũ (BR-C03)
- (Luồng B) Lưu trên Pairing, thay toàn bộ thỏa thuận cũ (kể cả khoản tự nhập) và xoá xác nhận của cả hai bên; đối phương đồng ý bằng nút "Xác nhận" (UC-M07), không qua chat; tiền cọc: số tiền 1..1.000.000.000 VND hoặc không cọc, "ai giữ" ≤100 ký tự chỉ khi có số tiền, không chia cọc; khi ghép, chi phí + tiền cọc + khoản tự nhập được chép sang Match (BR-C05)
- Loại chi phí chỉ: tiền sân, tiền nước, cả hai; tối đa 10 khoản tự nhập; số tiền > 0 và ≤ 1.000.000.000 VND (BR-C04)

---

### AC-N01: Chat giữa 2 đội trưởng trong một kèo (luồng A và B, real-time)

**Given:** Cuộc chat giữa đội tạo kèo và đội tham gia đã mở (luồng A: đội gửi Match Request, kể cả request còn PENDING; luồng B: đội nhận kèo) và đang nhắn được (BR-N04, BR-N05)

**When:** Đội trưởng hiện tại của đội A gửi tin nhắn

**Then:**
- Tin nhắn được lưu thuộc cuộc chat: đội gửi, người thực sự gửi, nội dung (1..1000 ký tự), thời điểm gửi, trạng thái đã đọc; không lưu người nhận (= đội còn lại của cuộc chat)
- Tin nhắn được gửi real-time cho đội trưởng B (độ trễ <1s)
- Tin nhắn không tạo notification, không tăng số thông báo chưa đọc; đội trưởng B đang mở app nhận tín hiệu cập nhật dấu "chưa đọc" của "Tin nhắn" (BR-N12)
- Mỗi tin hiển thị tên đội gửi + tên (và ảnh) người thực sự đã gửi; tin cũ giữ người gửi lúc đó sau khi đội chuyển quyền đội trưởng (vd đội Beta: tin của Nam vẫn "Beta · Nam" sau khi Lan làm đội trưởng); người gửi đã xóa tài khoản → "Người dùng đã xóa", không ảnh (BR-N03)
- Đầu phòng chat hiển thị tên hai đội + đội trưởng hiện tại của mỗi đội
- Thành viên không phải đội trưởng, đội trưởng cũ (đã chuyển quyền) và đội không thuộc cuộc chat → không xem, không gửi được (BR-N04)
- Chat lưu 90 ngày sau khi Match kết thúc (BR-N01)

---

### AC-N03: Nhắn được / chỉ xem theo kết quả ghép kèo

**Given:** Kèo luồng A của đội Alpha; đội X, Y, Z gửi request (3 cuộc chat Alpha–X, Alpha–Y, Alpha–Z)

**When:** Alpha chấp nhận request của X; sau đó kèo bị hủy (bởi Alpha hoặc X)

**Then:**
- Trước khi chấp nhận: cả 3 cuộc chat nhắn được; X không thấy chat Alpha–Y, Alpha–Z
- Sau khi chấp nhận: chat Alpha–X vẫn nhắn được trước, trong, sau trận; Alpha–Y, Alpha–Z (SUPERSEDED) chỉ xem — cả hai phía không gửi được, vẫn đọc lịch sử (BR-N05)
- Sau khi kèo bị hủy: chat Alpha–X vẫn nhắn được đến khi chat bị xóa (90 ngày sau lúc hủy — BR-N01)
- Request bị từ chối, rút khi còn PENDING hoặc hết hạn → cuộc chat của request đó chỉ xem
- Luồng B tương tự: Pairing CONFIRMED → chat nhắn tiếp trong chính cuộc chat đó; Pairing SUPERSEDED/EXPIRED → chỉ xem
- Kèo OPEN quá thời hạn bài đăng: gửi tin trong chat của request PENDING / Pairing NEGOTIATING bị từ chối "Kèo đã hết hạn", kèo chuyển EXPIRED (BR-M17)
- Đội tạm ngưng, đội còn lại không hoạt động, bộ môn tắt → vẫn nhắn được; đội đã giải tán → người đã giải tán đội chỉ xem (đọc được, không gửi), thành viên khác của đội đó không xem được; đội còn lại vẫn nhắn (BR-N05)

---

### AC-N04: Danh sách tin nhắn của tôi

**Given:** Minh là đội trưởng Alpha và Delta. Kèo của Alpha có chat với Beta (Beta có tin Minh chưa đọc) và Gamma (request của Gamma đã bị thay thế). Delta có chat theo đơn với ứng viên C. Kèo của Alpha vừa có thêm Epsilon gửi request, chưa ai nhắn

**When:** Minh mở "Tin nhắn"

**Then:**
- Có 4 dòng Alpha–Beta, Alpha–Gamma, Alpha–Epsilon, Delta–C; cuộc chat có tin mới hơn ở trên; Alpha–Epsilon không có tin cuối, xếp theo lúc Epsilon gửi request gần nhất (BR-N06)
- Epsilon rút request rồi gửi lại khi vẫn chưa ai nhắn → Alpha–Epsilon lên trên các cuộc chat có tin cũ hơn lần gửi lại
- Alpha–Beta có dấu chưa đọc; Alpha–Gamma đánh dấu chỉ xem
- Sau khi Minh chuyển quyền đội trưởng Alpha cho Lan: danh sách của Minh chỉ còn Delta–C; danh sách của Lan có các cuộc chat của Alpha
- Nam giải tán Beta → danh sách của Nam vẫn có Alpha–Beta (chỉ xem); thành viên khác của Beta không thấy
- Bên kia đã xóa tài khoản → hiện "Người dùng đã xóa", không ảnh
- Người không tham gia cuộc chat nào → danh sách rỗng

---

### AC-N02: 35 loại notification hoạt động đúng

**Given:** Có sự kiện kích hoạt notification (Business_Rules §8.6) và thao tác gây ra sự kiện đã thành công

**When:** System tạo notification

**Then:**
- Mỗi người nhận theo bảng §8.6 / BR-N08 có một notification chưa đọc (loại, người nhận, tiêu đề, nội dung, màn hình liên quan, thời điểm, trạng thái đọc)
- Tiêu đề + nội dung tiếng Việt, không có tên cá nhân; đội đổi tên sau đó → thông báo cũ giữ tên cũ (BR-N09)
- Thông báo của thao tác người dùng xuất hiện vài giây sau thao tác; thao tác thất bại → không có thông báo (BR-N10)
- Người nhận đang mở app nhận thông báo ngay; không có push điện thoại (Business_Rules §8.6)
- Không có thông báo cho các trường hợp BR-N07 (vd ứng viên tự rút đơn PENDING, Match ARCHIVED → COMPLETED)
- Notification lưu 30 ngày; quá 30 ngày không hiển thị, sau đó bị xóa (BR-N02)

---

### AC-N05: Người nhận thông báo của đội và của Admin

**Given:** Kèo đã ghép giữa Alpha (đội trưởng Minh) và Beta; có 2 Admin đang hoạt động

**When:** Beta hủy kèo; ngay sau đó (trước khi hệ thống tạo thông báo) Minh chuyển quyền đội trưởng Alpha cho Lan

**Then:**
- Lan nhận "Kèo bị hủy"; Minh và thành viên thường của Alpha không nhận (đội trưởng tại lúc tạo thông báo — BR-N08)
- Lan nhận thông báo được chuyển quyền đội trưởng; Minh không nhận (UC-T04)
- Thông báo Minh nhận trước khi chuyển quyền vẫn ở danh sách của Minh, không chuyển sang Lan
- Đội đã giải tán → thông báo nhắm vào đội không đến ai; người đã xóa tài khoản không nhận thông báo mới
- Có tranh chấp report bùng mới → mỗi Admin nhận một thông báo riêng, đọc riêng (Admin A đọc không làm thông báo của Admin B thành đã đọc)
- Một người là đội trưởng cả hai đội của kèo → nhận một thông báo cho mỗi loại

---

### AC-N06: Nhắc trước trận

**Given:** Kèo CONFIRMED bắt đầu 18:00 có 1 mercenary APPROVED; trận ngoài hệ thống bắt đầu 19:00 có 1 mercenary APPROVED

**When:** Hệ thống chạy nhắc trước trận

**Then:**
- Khoảng 16:00–16:05: đội trưởng hai đội và mercenary của kèo nhận một thông báo nhắc (BR-N13)
- Khoảng 17:00–17:05: chỉ mercenary của trận ngoài hệ thống nhận nhắc; đội trưởng đội khai báo không nhận
- Không nhắc lần hai cho cùng trận; mercenary được duyệt sau lần nhắc không được nhắc bù
- Kèo được ghép lúc 16:30 cho trận 18:00 (hoặc trận ngoài hệ thống khai báo khi còn dưới 2 giờ) → không nhắc
- Kèo bị hủy trước lúc nhắc → không nhắc

---

### AC-N07: Nhắc đánh giá và thông báo nhận đánh giá

**Given:** Kèo CONFIRMED giữa Alpha và Beta, kết thúc 20:00, có mercenary C APPROVED của Alpha; trận ngoài hệ thống kết thúc 21:00 có mercenary D APPROVED

**When:** Đến giờ kết thúc; sau đó các bên đánh giá

**Then:**
- Kèo chuyển ARCHIVED (trễ tối đa khoảng 5 phút) → đội trưởng Alpha và Beta nhận nhắc nhập tỷ số và đánh giá; C nhận nhắc đánh giá Alpha (BR-N14)
- Trong khoảng 5 phút sau 21:00 → D nhận nhắc đánh giá đội; trận ngoài hệ thống đã kết thúc quá 24 giờ mà chưa được nhắc (vd hệ thống gián đoạn) → không nhắc bù
- Alpha đánh giá Beta trước → Beta **không** nhận thông báo; sau đó Beta đánh giá Alpha → Alpha nhận "nhận đánh giá"
- Kèo chuyển ARCHIVED → COMPLETED → không có thông báo
- Đội trưởng Alpha đánh giá C trước, C đánh giá Alpha sau → đội trưởng Alpha nhận "nhận đánh giá"; C đánh giá Alpha khi Alpha chưa đánh giá C → đội trưởng Alpha không nhận

---

### AC-N08: Xem và đánh dấu đã đọc thông báo

**Given:** User có 3 thông báo chưa đọc và 1 đã đọc trong 30 ngày, 1 thông báo chưa đọc tạo 31 ngày trước

**When:** User mở màn hình "Thông báo" và thao tác

**Then:**
- Danh sách có 4 thông báo, mới nhất trước; lọc "chỉ chưa đọc" → 3; số chưa đọc = 3 (thông báo 31 ngày không hiển thị, không tính — BR-N02)
- Bấm một thông báo chưa đọc → thành đã đọc, số chưa đọc = 2, app mở màn hình liên quan
- "Đánh dấu tất cả đã đọc" → số chưa đọc = 0; thông báo đến sau đó vẫn chưa đọc
- App đang mở → thông báo mới hiện ngay và số chưa đọc tăng; mất kết nối rồi kết nối lại → app lấy lại số chưa đọc (BR-N10)
- Không xem được thông báo của người khác; không xóa thủ công, không đánh dấu lại chưa đọc (BR-N11)
- Tin nhắn chat không làm tăng số thông báo chưa đọc (BR-N12)

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
| Chat real-time | Latency | <1s | Nhận qua STOMP/WebSocket; bù tin bằng lịch sử theo con trỏ khi kết nối lại |
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
- [ ] Retry logic cho external services (OAuth; push notification — khi tích hợp FCM)
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
3. User B (đội "Team Beta") tìm kèo, thấy kèo của A, gửi Match Request → cuộc chat Alpha–Beta mở; A và B nhắn "Sân X có chỗ để xe không?" — "Có" khi request còn PENDING
4. User A xem request, chấp nhận
5. → Match chuyển CONFIRMED; request chuyển ACCEPTED; User A và B nhận notification; A và B tiếp tục nhắn trong cùng cuộc chat
6. (Sau trận) User A đánh giá User B: 5 sao → User B nhận +1 Reliability
7. → Match chuyển COMPLETED; Completion Rate của cả 2 tăng

**Expected:**
- Match lifecycle: OPEN → CONFIRMED → ARCHIVED → COMPLETED
- Request lifecycle: PENDING → ACCEPTED
- Chat Alpha–Beta nhắn được từ lúc gửi request, kể cả sau trận, đến khi bị xóa 90 ngày sau Match kết thúc (BR-N05, BR-N01)
- Reliability Score: cả 2 đội +2 (hoàn thành), User B thêm +1 (rating 5 sao)
- Completion Rate: tử số +1, mẫu số +1

---

### Scenario 2: Happy path — Tạo kèo luồng B, thương lượng, xác nhận

1. User A tạo kèo luồng B (lựa chọn sân Y/Z, khung giờ 18:00/19:00, chưa chốt)
2. User B nhận bài đăng → Pairing NEGOTIATING → cuộc chat giữa hai đội mở
3. User A và B chat: "18:00 sân Y được không?" — "OK" — "50-50 tiền sân?" — "OK"
4. User B chốt phương án sân Y + khung 18:00; User A nhập chi phí 50-50 tiền sân (mỗi lần gói đổi, xác nhận của cả hai bị xoá)
5. User A xác nhận gói qua UI (thời điểm T1)
6. User B xác nhận cùng gói qua UI (thời điểm T2, T2 > T1)
7. → Pairing CONFIRMED (thời điểm hoàn tất = T2); Match CONFIRMED
8. → Match chốt sân Y, 18:00; chi phí 50-50 tiền sân chép sang Match

**Expected:**
- Pairing lifecycle: NEGOTIATING → CONFIRMED
- Match lifecycle: OPEN → CONFIRMED
- Sau khi ghép, User A và B vẫn nhắn trong chính cuộc chat đó (BR-N05); chat lưu 90 ngày sau Match kết thúc
- Thời điểm hoàn tất được lưu (T2)

---

### Scenario 3: Edge case — Race condition 2 pairing cùng xác nhận

1. User A tạo kèo luồng B
2. User B và User C cùng nhận bài đăng → 2 Pairing (B và C)
3. Pairing B: cả 2 xác nhận gần đồng thời; lần xác nhận của bên thứ hai được ghi nhận lúc 10:00:05.123
4. Pairing C: cả 2 xác nhận gần đồng thời; lần xác nhận của bên thứ hai đến lúc 10:00:05.456
5. → Pairing B hoàn tất trước nên thắng; Pairing C chuyển SUPERSEDED, lần xác nhận cuối của C bị từ chối "Thương lượng đã kết thúc"
6. → Match CONFIRMED với Pairing B

**Expected:**
- Chỉ 1 Pairing CONFIRMED
- Pairing thua chuyển SUPERSEDED
- User C nhận notification "Pairing superseded"
- Chat của Pairing C thành chỉ xem; chat của Pairing B vẫn nhắn được (BR-N05)

> **Implementation note:** Pairing cập nhật bằng CAS trên trạng thái + `revision` (FE gửi `revision` của gói), Match bằng CAS trên version; retry tối đa 3 lần khi xung đột (ADR-0003 Sửa đổi 2026-11-10).

---

### Scenario 4: Edge case — Hủy Match CONFIRMED sát giờ trận

1. User A và B ghép kèo thành công, Match CONFIRMED (giờ trận: ngày mai 18:00)
2. Ngày mai 16:00 (2 giờ trước trận), User A hủy kèo
3. → Reliability Score của A bị trừ -10 (<4 giờ)
4. → Match chuyển CANCELLED
5. → User B nhận notification "Kèo bị hủy"

**Expected:**
- Match CANCELLED
- User A: Reliability -10; chuỗi trận sạch của đội A về 0
- Match chỉ tính vào mẫu số Completion Rate của **đội hủy** (đội A), không tính tử số (hủy <24h); đội B không tính trận này (user chốt 2026-11-12)

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
- Chat A–C chỉ xem; chat A–B vẫn nhắn được (BR-N05)

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
3. → Report tự động chấp nhận; Reliability của B bị trừ -5 (lần 1 bị report bùng); chuỗi trận sạch của B về 0; Match bị loại khỏi tử số Completion Rate của B ngay
4. → User B nhận notification "Bạn bị report bùng. Có 24h để tranh chấp."
5. User B tranh chấp trong 1 giờ, bắt buộc gửi bằng chứng dạng văn bản (vd "Đội tôi có mặt lúc 18:00, đã nhắn tin cho đội trưởng bên kia"; không đính kèm ảnh/tệp, 1–1000 ký tự — BR-R09)
6. → Tranh chấp chuyển admin
7. Admin xem bằng chứng, quyết định: bác report (B đã đến), bắt buộc nhập ghi chú lý do 1–500 ký tự (BR-R09)
8. → Reliability của B được hoàn lại +5 (đúng số điểm đã thực trừ); số lần bị report bùng của B giảm 1
9. → Match tính lại vào tử số Completion Rate của B (nếu đã COMPLETED); chuỗi trận sạch của B không được khôi phục
10. → Cả 2 nhận notification về quyết định

**Expected:**
- Report: auto_accepted → disputed → rejected
- Reliability B: -5 → +5 (hoàn đúng điểm đã thực trừ; nếu lúc trừ điểm đã chạm 0 thì chỉ hoàn phần thực trừ)
- Số lần bị report bùng của B về như trước report; chuỗi trận sạch vẫn là 0 (user chốt 2026-11-12)
- Match tính vào Completion Rate đúng

---

### Scenario 9: Verification Badge — Cấp và thu hồi tự động

1. User A hoàn thành 6 trận (tử số Completion Rate), sao trung bình 4.2, Reliability 85, tài khoản có số điện thoại
2. → System kiểm tra 4 điều kiện BR-R04: ✓ số điện thoại, ✓ >5 trận hoàn thành, ✓ sao trung bình >3, ✓ Reliability ≥70
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
- Chat của các request EXPIRED chỉ xem (BR-N05)
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

### Scenario 12: Chat luồng A — nhiều đội gửi request, ghép rồi hủy

1. Đội Alpha tạo kèo luồng A (ngày mai 18:00)
2. Đội X, Y, Z gửi request → 3 cuộc chat Alpha–X, Alpha–Y, Alpha–Z mở; đội trưởng Alpha nhắn riêng với từng đội
3. Đội Z rút request khi còn PENDING → chat Alpha–Z chỉ xem
4. Alpha chấp nhận X → Match CONFIRMED; request Y SUPERSEDED → chat Alpha–Y chỉ xem
5. Ngày mai 10:00, X hủy kèo (rút request đã ACCEPTED — BR-M12) → Match CANCELLED; X bị trừ -5
6. Đội trưởng Alpha và X vẫn nhắn trong chat Alpha–X (vd hẹn đá lại)

**Expected:**
- Chat Alpha–X nhắn được trước và sau khi hủy; bị xóa 90 ngày sau lúc hủy (BR-N01)
- Chat Alpha–Y, Alpha–Z: đọc được lịch sử, không gửi được (BR-N05)
- Đội trưởng Y không thấy chat Alpha–X, Alpha–Z (BR-N04)

---

## 5. Quyết định bổ sung đã chốt

1. **Cost agreement (luồng B):** Một trong hai đội trưởng nhập/sửa trên Pairing, bên kia xem. Không test xác nhận riêng cho chi phí; test sửa chi phí xoá xác nhận của cả hai bên và chép sang Match khi ghép (BR-C05 — sửa 2026-11-10/11).
2. **Verification Badge revocation:** Thu hồi ngay lập tức khi điều kiện không đủ. Test scenario: Reliability giảm <70 do report → badge thu hồi ngay + notification. Ngoại lệ (đội — user chốt 2026-11-12): đội bị trừ điểm do hủy kèo đã ghép / rút Match_Request đã ACCEPTED, đổi đội trưởng, đội trưởng thêm/bỏ số điện thoại → badge chỉ cập nhật ở lần đối soát hằng ngày (trễ tối đa khoảng 1 ngày) — Business_Rules §8.2.
3. **GPS permission revocation:** Fallback sang khu vực hồ sơ ngay. Test scenario: User thu hồi GPS → tìm kèo dựa khu vực hồ sơ + thông báo "Bật GPS...".
4. **Report cooldown:** Không giới hạn. Test scenario: User report 3 trận khác nhau trong 1 ngày → cả 3 report đều được chấp nhận.
5. **Hard-delete policy:** Giữ mãi. Không có test scenario hard-delete.
6. **Push notification (sửa 2026-11-16 — user chốt):** bản hiện tại chỉ có thông báo trong app (35 loại) + tín hiệu realtime khi app đang mở; **không** có DoD push / lưu thiết bị ở bản này. Push FCM/APNs, đăng ký thiết bị và phạm vi loại được push định nghĩa DoD khi tích hợp FCM (Business_Rules §8.6).
7. **Admin panel:** 4 chức năng MVP. DoD bổ sung:
   - [ ] Admin có thể bật/tắt bộ môn qua UI
   - [ ] Admin xem chi tiết tranh chấp report bùng, đưa ra quyết định (xác nhận/bác)
   - [ ] Admin xem danh sách venue report, xác minh và đánh dấu venue INACTIVE
   - [ ] Admin đánh dấu đội INACTIVE (với lý do)
8. **Load test scale:** 100 req/s đủ cho MVP (ước tính ~10K active users). Điều chỉnh nếu scale lớn hơn sau khi launch.

> **Implementation notes đã chốt:**
> - Concurrent pairing: CAS (Pairing: trạng thái + `revision`; Match: version) + retry, test scenario race condition (Scenario 3) đã phản ánh cơ chế này
> - Chat: gửi / đọc lịch sử qua REST; nhận ngay qua STOMP/WebSocket (SockJS tuỳ MATCH-002 `[cần xác nhận #1]`, ADR-0014), latency target <1s; lịch sử phân trang theo con trỏ để bù tin sau khi kết nối lại

---

## 6. Trạng thái tài liệu

**Trạng thái:** ✅ **Hoàn chỉnh** — Tất cả acceptance criteria đã được định nghĩa rõ ràng  
**Sẵn sàng cho:** Implementation và testing
