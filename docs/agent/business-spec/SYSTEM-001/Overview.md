# Overview — ArenaFlow System

| Mục | Giá trị |
|---|---|
| Mã chức năng | `SYSTEM-001` |
| Domain | Toàn hệ thống (8 domains: User, Team, Match, Reputation, Mercenary, Venue, Cost, Notification) |
| Stakeholders | Product Owner, Development Team, End Users (cá nhân & đội bóng) |
| Tác giả | business-analyst agent |
| Ngày | 2026-11-01 |
| Phiên bản | v1.13 |

## 1. Tổng quan

ArenaFlow là hệ thống **tạo kèo và ghép người chơi** cho thể thao nghiệp dư (MVP: bóng đá). Hệ thống kết nối các đội muốn thi đấu với nhau, hỗ trợ tuyển người chơi bổ sung (mercenary), và xây dựng hệ thống uy tín để đảm bảo chất lượng trải nghiệm.

**Giải quyết vấn đề:**
- Đội thiếu đối thủ để tập luyện/thi đấu
- Đội thiếu người, cần tuyển người chơi bổ sung
- Khó tin tưởng đội lạ (bùng kèo, không đúng trình độ, chơi xấu)
- Tìm sân và thỏa thuận chi phí phức tạp

**Giá trị mang lại:**
- Ghép kèo nhanh với đội phù hợp về trình độ và khoảng cách
- Tuyển mercenary linh hoạt theo vị trí
- Hệ thống uy tín minh bạch (Reliability Score, Rating, Report)
- Hỗ trợ thỏa thuận chi phí rõ ràng (không thanh toán trong app)

**Phạm vi hệ thống:**
ArenaFlow chỉ hỗ trợ **tạo kèo và ghép người**; sân là địa điểm tham chiếu. ArenaFlow **KHÔNG** quản lý vận hành sân, đặt sân thực tế, hoặc xử lý thanh toán.

## 2. Objectives (Mục tiêu)

- Cho phép đội tạo kèo với 2 luồng: đã có sân (luồng A) hoặc chưa có sân (luồng B)
- Ghép đối thủ phù hợp qua Match Request (luồng A) hoặc Pairing với thương lượng (luồng B)
- Cho phép đội tuyển mercenary theo vị trí, duyệt ứng viên
- Xây dựng hệ thống uy tín 2 cấp (đội & cá nhân) với Reliability Score và Completion Rate
- Xử lý report (bùng, chơi xấu, giao tiếp tệ, không đúng trình độ) với tranh chấp
- Quản lý địa điểm sân do Admin/User đóng góp, có cơ chế report địa điểm
- Hỗ trợ chat giữa 2 đội trưởng trong mỗi kèo (luồng A và B, từ lúc gửi yêu cầu / nhận kèo) và notification đa dạng

## 3. Actors (Người dùng/Hệ thống tương tác)

| Actor | Vai trò | Quyền |
|---|---|---|
| **User (Individual)** | Người dùng cá nhân | Quản lý hồ sơ, tham gia đội (phải được mời và chấp nhận), ứng tuyển mercenary, đánh giá đội sau trận mercenary, report |
| **Team Captain** | Đội trưởng | Tạo/quản lý đội, mời thành viên, tạo kèo, duyệt Match Request/Pairing, tuyển mercenary, chat với đội đối phương trong kèo, đánh giá đối phương/mercenary, report, hủy kèo, chuyển quyền đội trưởng |
| **Team Member** | Thành viên đội | Xem thông tin đội, rời đội, có thể được chọn làm đội trưởng mới (bắt buộc nhận) |
| **Admin** | Quản trị viên | Bật/tắt bộ môn, đánh dấu đội INACTIVE, thêm địa điểm sân, xác minh report địa điểm, xử lý tranh chấp report bùng |
| **System** | Hệ thống tự động | Cập nhật trạng thái hết hạn (Match, Request, Pairing, tin tuyển), gửi notification, tính Reliability Score/Completion Rate, chuyển Match sang ARCHIVED khi hết giờ dự kiến, xóa chat/notification sau retention, tự động chấp nhận report và trừ điểm |

## 4. In Scope (Trong phạm vi)

### User & Team Management
- Đăng ký/đăng nhập OAuth (Google, Facebook, Phone OTP)
- Quản lý hồ sơ cá nhân (tên, ảnh, tiểu sử, vị trí ưa thích, ngày sinh, giới tính, khu vực phường/xã)
- Soft-delete tài khoản (giữ mãi, có thể khôi phục)
- Tạo đội, mời thành viên, chấp nhận lời mời
- Chuyển quyền đội trưởng (bắt buộc nhận, tức thì, có lịch sử)
- Xử lý xóa tài khoản đội trưởng (phải chuyển quyền hoặc giải tán đội trước, kể cả khi là người duy nhất — `USER-002`)
- Quản lý trình độ đội (chọn tối đa 3 trong 9 nhãn)
- Tra cứu: danh mục khu vực, tìm người dùng để mời, lời mời của tôi, đội của tôi (`USER-003`)

### Match Management (Luồng A — đã có sân)
- Đội trưởng tạo kèo với sân, giờ, định dạng, trình độ
- Chọn thời hạn bài đăng (tối đa 7 ngày trước giờ đá đến giờ bắt đầu)
- Đội khác gửi Match Request; mỗi đội gửi có một cuộc chat riêng với đội tạo kèo ngay từ lúc gửi (BR-N04)
- Đội trưởng chấp nhận 1 request → Match CONFIRMED, các request khác SUPERSEDED
- Tối đa 1 request ACCEPTED mỗi Match

### Match Management (Luồng B — chưa có sân)
- Đội trưởng tạo kèo với 1–5 lựa chọn địa điểm và 1–5 khung giờ
- Nhiều đội có thể nhận bài đăng và tạo Pairing đồng thời (mỗi đội một Pairing cho một kèo; không rút / kết thúc Pairing)
- Chat giữa 2 đội trưởng để thương lượng (mỗi Pairing một cuộc chat — BR-N04)
- Một trong hai đội trưởng chốt phương án (1 địa điểm + 1 khung giờ của kèo) và nhập chi phí + tiền cọc trên Pairing; gói đổi → xoá xác nhận của cả hai
- Cả 2 xác nhận gói qua UI riêng (không phải chat) → Match CONFIRMED (chi phí + tiền cọc + khoản tự nhập chép sang Match)
- Race condition: Pairing hoàn tất trước (lần xác nhận của bên thứ hai được ghi nhận trước) thắng; Pairing kia → SUPERSEDED

### Match Lifecycle
- Trạng thái: OPEN, CONFIRMED, ARCHIVED, COMPLETED, EXPIRED, CANCELLED
- CONFIRMED: không sửa giờ/sân/trình độ; muốn thay đổi phải hủy
- Hủy Match: OPEN không ảnh hưởng uy tín (phải từ chối mọi request PENDING trước); CONFIRMED bị trừ điểm uy tín của đội hủy theo khoảng cách giờ trận, không hủy được khi trận đã bắt đầu
- Hết hạn: scheduled job 5 phút + lazy check khi API gọi
- Khi đến giờ kết thúc dự kiến: Match → ARCHIVED; System gửi thông báo nhắc nhập tỷ số và đánh giá cho 2 đội trưởng, nhắc mercenary đã duyệt đánh giá đội (BR-N14)
- Khi có đội trưởng đánh giá: Match → COMPLETED (không có thông báo)

### Reputation System (Đội & Cá nhân độc lập)
- Reliability Score (0-100, khởi đầu 80, chặn [0,100])
- Hoàn thành trận: +2 (đánh giá "đội kia không đến": đội đánh giá +2, đội vắng 0); chuỗi trận sạch chạm bội số của 5: +5 bonus, bội số của 10: +10 bonus (lặp lại)
- Nhận đánh giá 5 sao (sao nguyên 1–5): +1
- Hủy Match/rút request đã ACCEPTED: -2/-5/-10 theo khoảng cách giờ trận; chuỗi trận sạch của đội hủy về 0
- Mercenary rút sau khi duyệt: -1/-3/-5
- Report: mức trừ tăng dần theo số lần (bùng: -5/-8/-12/-15; chơi xấu: -3/-5/-8/-10; giao tiếp tệ: -2/-4/-6/-8; không đúng trình độ: -1/-2/-3/-4); Admin xác nhận report bùng đúng không trừ thêm; Admin bác report → hoàn đúng điểm đã thực trừ
- Reset số lần report sau 5 trận sạch
- Completion Rate (tính khi hiển thị): (Match COMPLETED không bị report bùng) / (Match COMPLETED + CANCELLED <24h trước trận — chỉ của đội hủy + ARCHIVED có report bùng)
- Chỉ hiển thị Completion Rate khi ≥5 trận; trước đó hiển thị "Mới tham gia X/5 trận"
- Verification Badge: có số điện thoại (đội: đội trưởng hiện tại có số điện thoại; cá nhân: tài khoản có số điện thoại) + >5 trận hoàn thành + sao trung bình >3 + Reliability ≥70

### Rating & Report
- Đội trưởng đánh giá đối phương: 1-5 sao hoặc "đội kia không đến"
- Chọn "không đến" → đề xuất report bùng (tách khỏi luồng đánh giá)
- Report 4 loại: bùng, chơi xấu, giao tiếp tệ, không đúng trình độ; kèo hủy sau khi đã ghép chỉ report "giao tiếp tệ"; cuộc chat giữa hai đội đã có tin nhắn → report nhau "giao tiếp tệ" bất kể trạng thái request/Pairing/kèo (BR-R07)
- Người là đội trưởng của cả hai đội trong trận không được đánh giá/report
- Report bùng: tự động chấp nhận và trừ điểm; nếu đội bị report phản hồi trong 24h → tranh chấp, admin xem xét
- Report khác: tự động chấp nhận và trừ điểm
- Không hiển thị trạng thái "đối phương đã/chưa đánh giá"; bên đã đánh giá mới xem rating đối phương cho mình
- Chống trùng: mỗi cặp (Match, đội đánh giá, đội được đánh giá) chỉ đánh giá 1 lần

### Mercenary Management
- Đội trưởng tạo tin tuyển theo vị trí (Thủ môn, Hậu vệ, Tiền vệ, Tiền đạo) và số lượng
- Cá nhân tìm kèo: GPS hiện tại (bán kính mặc định 3 km, chỉnh 1–20 km); không có GPS → tin tuyển cùng tỉnh với phường trong hồ sơ, cùng phường lên đầu (BR-ME03)
- Cá nhân ứng tuyển → đội trưởng duyệt
- Mercenary được duyệt có thể rút trước giờ bắt đầu trận → trừ Reliability cá nhân (BR-ME11)
- Tin tuyển hết hạn khi đến giờ kết thúc dự kiến của Match
- Đánh giá: đội trưởng đánh giá mercenary (1-5 sao), mercenary đánh giá đội (1-5 sao)
- Report: cả 2 bên có thể report nhau, cơ chế giống report Match

### Venue Management
- Admin và User thêm địa điểm trực tiếp (không cần duyệt trước)
- Trường: tên, địa chỉ, tọa độ, người tạo, created/updated, lần chọn gần nhất, trạng thái, điện thoại, website, loại sân
- Ảnh: lưu trong bảng riêng, tối đa 5, có is_primary, display_order
- Sửa địa điểm và xóa ảnh: chỉ người tạo địa điểm hoặc Admin; kèo luôn hiển thị thông tin địa điểm mới nhất
- Report địa điểm: mỗi report lưu người report, lý do, thời gian; admin xác minh rồi đánh dấu ngừng hoạt động
- Địa điểm ngừng hoạt động bị loại khỏi gợi ý; Match cũ giữ tham chiếu
- ArenaFlow cảnh báo admin 1 lần khi địa điểm không được chọn trong 180 ngày hoặc có báo cáo ngừng hoạt động

### Cost Agreement
- Lưu thỏa thuận để hiển thị; không thu/giữ tiền
- Loại chi phí: tiền sân, tiền nước, hoặc cả hai
- Cách chia: 50-50, 70-30 (đội chủ nhà bao 70% hoặc ai thua bao 70%), đội thua trả toàn bộ tiền nước
- Lưu số tiền cụ thể (VND trong MVP)
- Khoản tự nhập: danh sách các khoản phí khác (tên + số tiền), tối đa 10 khoản
- Luồng B: có thể có tiền cọc — số tiền cọc (không bắt buộc) + "ai giữ tiền cọc" (văn bản, chỉ khi có số tiền); không chia tiền cọc; cọc do thỏa thuận bên ngoài, ArenaFlow không thu/giữ

### Notification & Chat
- Chat giữa 2 đội trưởng trong một kèo (luồng A và B): mỗi cặp (kèo, đội gửi request / đội nhận kèo) một cuộc chat với đội tạo kèo; chỉ đội trưởng hiện tại xem và nhắn (BR-N04). Hai đội đã ghép nhắn được trước, trong, sau trận và cả khi kèo bị hủy; đội không được ghép chỉ xem (BR-N05). Lưu 90 ngày sau khi Match kết thúc (COMPLETED/ARCHIVED/CANCELLED/EXPIRED)
- Màn hình "Tin nhắn" liệt kê mọi cuộc chat người dùng xem được (chat hai đội + chat theo đơn ứng tuyển), tin mới nhất trước, kể cả cuộc chat chỉ xem và chưa có tin (BR-N06)
- Notification: lưu 30 ngày; quá 30 ngày không còn hiển thị, kể cả trước lượt xóa hằng ngày (BR-N02)
- 35 loại notification trong app (danh sách, thời điểm, người nhận: Business_Rules §8.6): đội & lời mời (chào mừng, lời mời vào đội, lời mời được chấp nhận/từ chối, chuyển quyền đội trưởng, Admin tạm ngưng/kích hoạt lại đội); ghép kèo luồng A/B (request nhận/rút/chấp nhận/từ chối/bị thay thế/hết hạn, Pairing mới/bị thay thế/hết hạn, Match confirmed/cancelled/expired); trận (reminder trước trận, Match completed kèm nhắc đánh giá, nhắc mercenary đánh giá); uy tín (rating nhận, report nhận, report bị tranh chấp, quyết định tranh chấp, cấp/thu hồi huy hiệu); mercenary (ứng tuyển, duyệt, từ chối, rút lui, trận bị hủy); Admin (cảnh báo địa điểm, tranh chấp mới)
- Người nhận: sự kiện của đội → đội trưởng của đội tại lúc tạo thông báo (có thể vài giây sau sự kiện); Admin → mỗi Admin một thông báo; nội dung tiếng Việt soạn sẵn, không có tên cá nhân (BR-N08, BR-N09)
- Reminder trước trận: một lần, khoảng 2 giờ trước giờ bắt đầu (trễ tối đa khoảng 5 phút); đội trưởng hai đội của kèo đã ghép + mercenary đã duyệt (trận ngoài hệ thống: chỉ mercenary); ghép / khai báo khi còn dưới 2 giờ → không nhắc (BR-N13)
- "Rating nhận" chỉ khi người nhận đã đánh giá bên kia trước; Match completed (nhắc đánh giá) gửi lúc Match ARCHIVED, ARCHIVED → COMPLETED không báo (BR-N14)
- Người dùng xem danh sách thông báo, số chưa đọc, đánh dấu một / tất cả đã đọc; không xóa (BR-N11). App đang mở nhận thông báo ngay (tín hiệu realtime); push điện thoại để sau (Business_Rules §8.6)
- Tin nhắn chat không tạo notification; app đang mở nhận tín hiệu realtime để cập nhật dấu "chưa đọc" của "Tin nhắn" (BR-N12)

## 5. Out of Scope (Ngoài phạm vi)

- Đặt sân/booking thực tế (ArenaFlow không quản lý vận hành sân)
- Thanh toán/giữ tiền/xử lý giao dịch (chỉ lưu thỏa thuận hiển thị)
- Quản lý availability sân thực tế (ArenaFlow không xác minh sân có sẵn)
- Tích hợp Google Maps/API chi tiết (chỉ hiển thị bản đồ/chỉ đường)
- Push notification điện thoại khi app đóng (FCM/APNs) và đăng ký thiết bị — làm sau, trọn gói cùng lúc tích hợp FCM (user chốt 2026-11-16)
- Xóa thông báo thủ công (thông báo tự xóa sau 30 ngày)
- Bộ môn thứ 2 (MVP chỉ bóng đá; thiết kế cấu hình riêng theo môn khi có yêu cầu)
- Đội phó (MVP chỉ có 1 đội trưởng chính)
- Hard-delete tài khoản (chỉ soft-delete, giữ mãi)
- Tìm kiếm/xuất chat (MVP không cần)
- Tiện ích sân (không lưu trong MVP)

## 6. Assumptions (Giả định)

- User đăng ký qua OAuth (Google, Facebook, Phone OTP); không có email/password truyền thống
- Đội trưởng đại diện đội trong kèo; thành viên không thể tạo/quản lý kèo
- Hai đội thỏa thuận chi phí bên ngoài ArenaFlow; hệ thống chỉ lưu để hiển thị
- Địa điểm sân do cộng đồng đóng góp; admin xác minh khi có report
- Mọi thời điểm theo giờ Việt Nam (UTC+7), hiển thị nguyên giá trị, không đổi theo múi giờ thiết bị (BC-M05)
- Truy cập GPS dựa vào quyền hệ điều hành; không lưu consent riêng trong DB
- Khu vực hồ sơ (phường/xã) dùng danh mục chuẩn Vietnam Administrative Units
- PostgreSQL hoạt động với datasource được cấu hình (Neon `neondb`, branch `main`)
- Flyway migration và Hibernate entity đồng bộ (`ddl-auto=validate`)

## 7. Constraints (Ràng buộc)

### Nghiệp vụ
- Tên đội được phép trùng (không unique)
- Chỉ admin đánh dấu đội INACTIVE; đội trưởng giải tán đội (`USER-002`)
- Chuyển đội trưởng: người được chọn bắt buộc nhận, không có quyền từ chối
- Match CONFIRMED: không được sửa giờ/sân/trình độ
- Hủy Match OPEN: phải từ chối mọi request PENDING trước
- Reliability Score chặn trong [0, 100]
- Completion Rate chỉ hiển thị khi ≥5 trận đã chốt
- Verification Badge yêu cầu: có số điện thoại (đội: đội trưởng hiện tại; cá nhân: tài khoản) + >5 trận hoàn thành + sao trung bình >3 + Reliability ≥70
- Tin tuyển mercenary hết hạn khi đến giờ kết thúc dự kiến của Match
- Chat lưu 90 ngày sau khi Match kết thúc; notification lưu 30 ngày
- Địa điểm ngừng hoạt động bị loại khỏi gợi ý mặc định
- Ảnh sân tối đa 5, chỉ 1 ảnh is_primary

### Kỹ thuật
- Backend: Spring Boot + Spring Data JPA + PostgreSQL + Flyway
- Database: Neon PostgreSQL, branch `main`
- Migration: Flyway có kiểm soát; không chỉnh schema Neon thủ công
- Hibernate: `ddl-auto=validate` (migration và entity phải đồng bộ)
- Thời gian: giờ Việt Nam, không kèm múi giờ (BC-M05)
- Scheduled job: mỗi 5 phút cập nhật trạng thái hết hạn + lazy check khi API gọi

## 8. Success Metrics (Chỉ số thành công)

- User có thể tạo kèo trong < 3 phút
- Ghép kèo thành công > 80% (OPEN → CONFIRMED)
- Tỉ lệ bùng kèo < 5%
- Thời gian phản hồi API < 2s (danh sách), < 1s (tạo/cập nhật)
- Reliability Score phản ánh chính xác hành vi (trừ đúng khi hủy, cộng đúng khi hoàn thành)
- Completion Rate tính đúng (Business_Rules §3.3: không tính CANCELLED ≥24h, ARCHIVED không có report bùng; CANCELLED <24h chỉ tính cho đội hủy)
- Chat giữa 2 đội trưởng hoạt động real-time (độ trễ < 1s)
- Thông báo in-app đến đúng lúc: vài giây sau thao tác của người dùng; ≤ khoảng 5 phút với sự kiện do hệ thống tự chuyển trạng thái (BR-N10)
- Report được xử lý tự động ngay lập tức; tranh chấp được admin xử lý trong 48h

## 9. Quyết định bổ sung đã chốt

1. **Cost agreement (luồng B):** Một trong hai đội trưởng nhập/sửa trên Pairing, bên kia xem. Không cần xác nhận riêng vì xác nhận Pairing = đồng ý cả gói; sửa → xoá xác nhận của cả hai bên (BR-C05 — sửa 2026-11-10).
2. **Verification Badge revocation:** Thu hồi ngay lập tức khi điều kiện không đủ. Ngoại lệ (đội — user chốt 2026-11-12): đội bị trừ điểm do hủy kèo đã ghép / rút Match_Request đã ACCEPTED, đổi đội trưởng, đội trưởng thêm/bỏ số điện thoại → chỉ cập nhật ở lần đối soát hằng ngày (trễ tối đa khoảng 1 ngày).
3. **GPS permission revocation:** Fallback sang khu vực hồ sơ ngay. Thông báo "Bật GPS để tìm kèo gần bạn hơn".
4. **Report cooldown:** Không giới hạn. Mỗi trận chỉ report 1 lần/loại là đủ.
5. **Hard-delete policy:** Giữ mãi, không hard-delete.
6. **Push notification (sửa 2026-11-16 — user chốt):** bản hiện tại chỉ có thông báo trong app (35 loại) + tín hiệu realtime khi app đang mở; push FCM/APNs và đăng ký thiết bị làm sau cùng lúc tích hợp FCM (Business_Rules §8.6).
7. **Admin panel:** 4 chức năng MVP: bật/tắt bộ môn, tranh chấp report bùng, xác minh venue report, đánh dấu INACTIVE.

> **Implementation notes đã chốt (từ phiên trước):**
> - Đồng thời (luồng B): Pairing CAS trên trạng thái + `revision` (FE gửi `revision` của gói khi chốt phương án / lưu chi phí / xác nhận), Match CAS trên version; retry tối đa 3 lần (ADR-0003 Sửa đổi 2026-11-10)
> - Match luồng B: danh sách lựa chọn địa điểm (1–5) và danh sách khung giờ (1–5) là hai tập riêng; phương án chốt của Pairing tham chiếu một phần tử mỗi tập (sửa 2026-11-10, thay JSONB `location_option_ids` / `time_options`)
> - Chat: gửi / đọc lịch sử qua REST; nhận ngay qua STOMP/WebSocket (SockJS tuỳ MATCH-002 `[cần xác nhận #1]`, ADR-0014); lịch sử phân trang theo con trỏ để bù tin
> - Luồng A expires_at: `expires_at ≤ scheduled_start_time`, không cho đổi ngày trận sau khi Match OPEN (phải hủy và tạo lại)

---

## 10. Trạng thái tài liệu

**Phiên bản:** v1.0 (baseline) → v1.1 (hoàn chỉnh) → v1.2 (2026-11-09: bổ sung UC-V05 sửa địa điểm, UC-V06 xóa ảnh, BR-V05..V10, AC-V04/V05 — cho `VENUE-001`) → v1.3 (2026-11-09: BR-M09..BR-M18, BR-C03..BR-C04, làm rõ BC-M04, sửa §2.1/§2.2/§5.2/§7.3 Business_Rules, UC-M01/M03/M04/M08/M09/C01/C02, sửa AC-M06/AC-C01, thêm AC-M09..AC-M12 — cho `MATCH-001`) → v1.5 (2026-11-11: luồng B — hai đội xác nhận một gói rõ ràng (phương án chốt + chi phí + tiền cọc) thay vì "đã thống nhất qua chat", gói đổi thì xác nhận lại; Pairing hoàn tất trước thắng; chi phí/cọc thuộc Pairing và chép sang Match khi ghép; chat hiển thị người thực sự gửi; quy tắc đội tạm ngưng / bộ môn tắt / kèo quá hạn áp cho thao tác luồng B; chat của kèo hết hạn cũng bị xóa sau 90 ngày; thêm BR-M19..BR-M21, BR-C05, BR-N03; sửa BR-M03, BR-M07, BR-M14, BR-M15, BR-M17, BR-N01, BC-M04, BC-N01, BC-N03, BC-V05, §2.3/§5.2/§5.5/§8.1 Business_Rules, UC-M02/M05/M06/M07/M08/M09/C01/N01/N03, AC-M02/M04/M05/C01/N01, Scenario 2/3, Data_Model Match/Pairing/Chat_Message/Cost_Agreement/§6 — cho `MATCH-002`) → v1.6 (2026-11-11: ngoại lệ hiển thị user đã xóa — Admin xem lịch sử thành viên đội (tên thành viên) và report địa điểm (tên người report) thấy tên thật kèm nhãn "đã xóa", ảnh vẫn ẩn; các tên khác vẫn che; sửa BC-U02, UC-U03, AC-U03 — cho `USER-002`, `VENUE-001`) → v1.7 (2026-11-12: mở rộng ngoại lệ BC-U02 — mọi endpoint `/api/v1/admin/**` trả tên thật kèm cờ `is…Deleted`; ảnh thật chỉ khi response đã có field ảnh user và còn đường dẫn; FE gắn nhãn theo cờ, không tự thêm field ảnh; sửa BC-U02, UC-U03, AC-U03 — cho `USER-001`, `USER-002`, `VENUE-001`) → v1.8 (2026-11-13: uy tín — thêm BR-R07..BR-R10, AC-R07; sửa BR-R04, BC-R05, BC-R07, §2.1/§3.1/§3.3/§6/§7/§8.2 Business_Rules, UC-M08/R01..R07/ME06, AC-M06/M09/R01..R06, Scenario 4/8/9, Data_Model Rating/Report/Dispute/Reliability_Score/Completion_Rate/Verification_Badge — cho `REPUTATION-001`, user chốt 2026-11-12/13) → v1.9 (2026-11-13: mercenary — thêm BR-ME05..BR-ME19, BR-V11, UC-ME07..UC-ME09, AC-ME04..AC-ME11; sửa BR-ME01, BR-ME03, BR-ME04, BC-ME03, BC-ME04, BC-V05, §2.4/§2.5/§3.1/§3.3/§5.1/§5.2/§5.5/§8.2 Business_Rules, UC-U03, UC-ME01..ME06, AC-ME01..ME03, Data_Model Mercenary_Posting/Chat_Message/§8 — cho `MERCENARY-001`, user chốt 2026-11-13/14) → v1.10 (2026-11-15: chat giữa hai đội "bất kỳ lúc nào, chỉ cần tham gia kèo" — mở cả luồng A (từ lúc gửi request, mỗi đội một cuộc chat với đội tạo kèo) và luồng B (từ lúc nhận kèo); hai đội đã ghép nhắn được cả sau trận và khi kèo bị hủy, đội không được ghép chỉ xem; report "giao tiếp tệ" qua chat hai đội đã có tin nhắn, bất kể trạng thái request/Pairing/kèo (như chat mercenary); thêm BR-N04, BR-N05, BC-N04, UC-N01 exception 1a–1d, AC-N03, AC-R08, Scenario 12, Data_Model Team_Chat; sửa BR-R07, UC-R02, AC-R02, Data_Model Report, BR-M14, BR-M15, BR-M17, BR-N01, BR-N03, BC-N01, §2.2/§2.3/§5.2/§5.3/§6.2 Business_Rules, UC-M03..M09, UC-N01, UC-N03, AC-N01, DoD Notification, Scenario 1/2/3/6/10, Data_Model Match_Request/Pairing/Chat_Message/§1/§3/§4/§5/§6/§7; người đã giải tán đội vẫn đọc chat, không gửi (BR-N05, BR-ME16, AC-ME10); trận sạch chỉ xét report của đối thủ trong trận + mercenary (§3.1); thêm UC-N04, BR-N06, AC-N04 danh sách "Tin nhắn của tôi", quyền §5.1/§5.2 — cho `MATCH-001`, `MATCH-002`, `REPUTATION-001`, `MERCENARY-001`, user chốt 2026-11-13/15) → v1.11 (2026-11-16: thông báo trong app — 35 loại thay 11 loại (danh sách, thời điểm, người nhận ở Business_Rules §8.6); người nhận sự kiện của đội = đội trưởng tại lúc tạo thông báo (có thể vài giây sau sự kiện), chuyển quyền không chuyển thông báo cũ, Admin mỗi người một thông báo; nội dung tiếng Việt soạn sẵn, không tên cá nhân; app đang mở nhận ngay, push điện thoại và thiết bị để sau (thay quyết định "FCM + APNs, push 11 loại"); người dùng xem / đếm chưa đọc / đọc một / đọc tất cả, không xóa, quá 30 ngày không hiển thị; tin nhắn chat không tạo thông báo, chỉ tín hiệu cập nhật "Tin nhắn"; nhắc trước trận một lần khoảng 2 giờ trước giờ bắt đầu (trận ngoài hệ thống chỉ nhắc mercenary, không nhắc bù); nhắc đánh giá lúc ARCHIVED, COMPLETED không báo, "nhận đánh giá" chỉ khi đã đánh giá trước; nhắc mercenary đánh giá (trận ngoài: chỉ trận kết thúc trong 24 giờ); Admin chỉ nhận cảnh báo địa điểm một lần mỗi đợt (không báo từng report) và mỗi tranh chấp mới; chuyển quyền chỉ báo đội trưởng mới; báo đội trưởng khi lời mời được chấp nhận / từ chối và khi Admin tạm ngưng / kích hoạt lại đội; rủi ro lời mời chỉ biết qua thông báo; thêm BR-N07..BR-N14, BC-N05, UC-T05 A1, UC-N02 luồng người nhận + exception 1a/2a/3a/U3a, AC-N05..AC-N08; sửa BR-N02, BC-N02, §2.1/§5.1/§5.5/§6.2/§8.6 Business_Rules, UC-T03, UC-T04, UC-T05, UC-R01, UC-R03, UC-ME06, UC-ME08, UC-ME09, UC-V03, UC-N01, UC-N02, UC-N03, AC-T03, AC-M08, AC-R01, AC-R03, AC-ME07, AC-ME08, AC-ME10, AC-V03, AC-N01, AC-N02, DoD Notification, Data_Model Notification/§1/§9, Overview §4/§5/§9 — cho `NOTIFICATION-001`, user chốt 2026-11-16) → v1.12 (2026-11-17: tra cứu cho người dùng — sửa câu chữ UC-U02 bước 3, UC-T02 điều kiện trước + bước 2, UC-T03 bước 1 và ghi chú rủi ro (người được mời thấy lời mời trong "Lời mời của tôi", không còn chỉ qua thông báo), AC-T02, §4 In Scope, §5 Out of Scope (bỏ "màn hình lời mời của tôi") — trỏ sang UC-U04/U05/T07/T08 — cho `USER-003`, user chốt 2026-11-17) → v1.13 (2026-11-21: thời gian theo giờ Việt Nam cho mọi thời điểm, hiển thị nguyên giá trị không đổi theo múi giờ thiết bị, mốc nghiệp vụ tính theo giờ Việt Nam, dữ liệu cũ giữ đúng thời điểm (thay "lưu UTC, frontend đổi theo múi giờ thiết bị"); sửa BC-M05, Overview §ràng buộc — cho `SYSTEM-002`, user chốt 2026-11-20/21)  
**Ngày hoàn thiện:** 2026-11-02  
**Trạng thái:** ✅ **Hoàn chỉnh** — Tất cả quyết định nghiệp vụ đã được chốt  
**Sẵn sàng cho:** Solution design (thiết kế kỹ thuật chi tiết)
