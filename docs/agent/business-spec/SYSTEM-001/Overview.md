# Overview — ArenaFlow System

| Mục | Giá trị |
|---|---|
| Mã chức năng | `SYSTEM-001` |
| Domain | Toàn hệ thống (8 domains: User, Team, Match, Reputation, Mercenary, Venue, Cost, Notification) |
| Stakeholders | Product Owner, Development Team, End Users (cá nhân & đội bóng) |
| Tác giả | business-analyst agent |
| Ngày | 2026-11-01 |
| Phiên bản | v1.0 (baseline) |

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
- Hỗ trợ chat giữa 2 đội trưởng (luồng B) và notification đa dạng

## 3. Actors (Người dùng/Hệ thống tương tác)

| Actor | Vai trò | Quyền |
|---|---|---|
| **User (Individual)** | Người dùng cá nhân | Quản lý hồ sơ, tham gia đội (phải được mời và chấp nhận), ứng tuyển mercenary, đánh giá đội sau trận mercenary, report |
| **Team Captain** | Đội trưởng | Tạo/quản lý đội, mời thành viên, tạo kèo, duyệt Match Request/Pairing, tuyển mercenary, đánh giá đối phương/mercenary, report, hủy kèo, chuyển quyền đội trưởng |
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

### Match Management (Luồng A — đã có sân)
- Đội trưởng tạo kèo với sân, giờ, định dạng, trình độ
- Chọn thời hạn bài đăng (tối đa 7 ngày trước giờ đá đến giờ bắt đầu)
- Đội khác gửi Match Request
- Đội trưởng chấp nhận 1 request → Match CONFIRMED, các request khác SUPERSEDED
- Tối đa 1 request ACCEPTED mỗi Match

### Match Management (Luồng B — chưa có sân)
- Đội trưởng tạo kèo với 1–5 lựa chọn địa điểm và 1–5 khung giờ
- Nhiều đội có thể nhận bài đăng và tạo Pairing đồng thời (mỗi đội một Pairing cho một kèo; không rút / kết thúc Pairing)
- Chat giữa 2 đội trưởng để thương lượng
- Một trong hai đội trưởng chốt phương án (1 địa điểm + 1 khung giờ của kèo) và nhập chi phí + tiền cọc trên Pairing; gói đổi → xoá xác nhận của cả hai
- Cả 2 xác nhận gói qua UI riêng (không phải chat) → Match CONFIRMED (chi phí + tiền cọc + khoản tự nhập chép sang Match)
- Race condition: Pairing hoàn tất trước (lần xác nhận của bên thứ hai được ghi nhận trước) thắng; Pairing kia → SUPERSEDED

### Match Lifecycle
- Trạng thái: OPEN, CONFIRMED, ARCHIVED, COMPLETED, EXPIRED, CANCELLED
- CONFIRMED: không sửa giờ/sân/trình độ; muốn thay đổi phải hủy
- Hủy Match: OPEN không ảnh hưởng uy tín (phải từ chối mọi request PENDING trước); CONFIRMED bị trừ điểm uy tín của đội hủy theo khoảng cách giờ trận, không hủy được khi trận đã bắt đầu
- Hết hạn: scheduled job 5 phút + lazy check khi API gọi
- Khi đến giờ kết thúc dự kiến: System gửi push cho 2 đội trưởng, Match → ARCHIVED
- Khi có đội trưởng đánh giá: Match → COMPLETED

### Reputation System (Đội & Cá nhân độc lập)
- Reliability Score (0-100, khởi đầu 80, chặn [0,100])
- Hoàn thành trận: +2; 5 trận sạch liên tiếp: +5 bonus; 10 trận sạch: +10 bonus
- Rating ≥4.5 sao: +1
- Hủy Match/rút request đã ACCEPTED: -2/-5/-10 theo khoảng cách giờ trận
- Mercenary rút sau khi duyệt: -1/-3/-5
- Bị xác nhận bùng qua tranh chấp: -10
- Report: mức trừ tăng dần theo số lần (bùng: -5/-8/-12/-15; chơi xấu: -3/-5/-8/-10; giao tiếp tệ: -2/-4/-6/-8; không đúng trình độ: -1/-2/-3/-4)
- Reset số lần report sau 5 trận sạch
- Completion Rate: (Match COMPLETED không bị report bùng) / (Match CONFIRMED, loại trừ CANCELLED ≥24h trước và ARCHIVED)
- Chỉ hiển thị Completion Rate khi ≥5 trận; trước đó hiển thị "Mới tham gia X/5 trận"
- Verification Badge: phone linked + >5 trận + rating >3 sao + Reliability ≥70

### Rating & Report
- Đội trưởng đánh giá đối phương: 1-5 sao hoặc "đội kia không đến"
- Chọn "không đến" → đề xuất report bùng (tách khỏi luồng đánh giá)
- Report 4 loại: bùng, chơi xấu, giao tiếp tệ, không đúng trình độ
- Report bùng: tự động chấp nhận và trừ điểm; nếu đội bị report phản hồi trong 24h → tranh chấp, admin xem xét
- Report khác: tự động chấp nhận và trừ điểm
- Không hiển thị trạng thái "đối phương đã/chưa đánh giá"; bên đã đánh giá mới xem rating đối phương cho mình
- Chống trùng: mỗi cặp (Match, đội đánh giá, đội được đánh giá) chỉ đánh giá 1 lần

### Mercenary Management
- Đội trưởng tạo tin tuyển theo vị trí (Thủ môn, Hậu vệ, Tiền vệ, Tiền đạo) và số lượng
- Cá nhân tìm kèo: GPS hiện tại (bán kính 3km, có thể điều chỉnh); không có GPS → dùng khu vực hồ sơ (phường/xã)
- Cá nhân ứng tuyển → đội trưởng duyệt
- Mercenary được duyệt có thể rút bất kỳ lúc nào → trừ Reliability cá nhân
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
- Chat giữa 2 đội trưởng (luồng B): lưu 90 ngày sau khi Match kết thúc (COMPLETED/ARCHIVED/CANCELLED/EXPIRED)
- Notification: lưu 30 ngày
- 11 loại notification: Match request nhận, request được chấp nhận/từ chối, Match confirmed, Match cancelled, Pairing mới, reminder trước trận, Match completed (kèm lời nhắc đánh giá), rating nhận, report nhận, mercenary application, invitation vào đội

## 5. Out of Scope (Ngoài phạm vi)

- Đặt sân/booking thực tế (ArenaFlow không quản lý vận hành sân)
- Thanh toán/giữ tiền/xử lý giao dịch (chỉ lưu thỏa thuận hiển thị)
- Quản lý availability sân thực tế (ArenaFlow không xác minh sân có sẵn)
- Tích hợp Google Maps/API chi tiết (chỉ hiển thị bản đồ/chỉ đường)
- Push notification cụ thể (FCM/dịch vụ khác để sau)
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
- Thời gian lưu trong DB theo UTC; frontend convert theo timezone thiết bị
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
- Verification Badge yêu cầu: phone linked + >5 trận + rating >3 sao + Reliability ≥70
- Tin tuyển mercenary hết hạn khi đến giờ kết thúc dự kiến của Match
- Chat lưu 90 ngày sau khi Match kết thúc; notification lưu 30 ngày
- Địa điểm ngừng hoạt động bị loại khỏi gợi ý mặc định
- Ảnh sân tối đa 5, chỉ 1 ảnh is_primary

### Kỹ thuật
- Backend: Spring Boot + Spring Data JPA + PostgreSQL + Flyway
- Database: Neon PostgreSQL, branch `main`
- Migration: Flyway có kiểm soát; không chỉnh schema Neon thủ công
- Hibernate: `ddl-auto=validate` (migration và entity phải đồng bộ)
- Thời gian: lưu UTC trong DB, frontend convert theo timezone
- Scheduled job: mỗi 5 phút cập nhật trạng thái hết hạn + lazy check khi API gọi

## 8. Success Metrics (Chỉ số thành công)

- User có thể tạo kèo trong < 3 phút
- Ghép kèo thành công > 80% (OPEN → CONFIRMED)
- Tỉ lệ bùng kèo < 5%
- Thời gian phản hồi API < 2s (danh sách), < 1s (tạo/cập nhật)
- Reliability Score phản ánh chính xác hành vi (trừ đúng khi hủy, cộng đúng khi hoàn thành)
- Completion Rate tính đúng (loại trừ CANCELLED ≥24h và ARCHIVED)
- Chat giữa 2 đội trưởng hoạt động real-time (độ trễ < 1s)
- Notification gửi đúng lúc (push trong 1 phút sau sự kiện)
- Report được xử lý tự động ngay lập tức; tranh chấp được admin xử lý trong 48h

## 9. Quyết định bổ sung đã chốt

1. **Cost agreement (luồng B):** Một trong hai đội trưởng nhập/sửa trên Pairing, bên kia xem. Không cần xác nhận riêng vì xác nhận Pairing = đồng ý cả gói; sửa → xoá xác nhận của cả hai bên (BR-C05 — sửa 2026-11-10).
2. **Verification Badge revocation:** Thu hồi ngay lập tức khi điều kiện không đủ.
3. **GPS permission revocation:** Fallback sang khu vực hồ sơ ngay. Thông báo "Bật GPS để tìm kèo gần bạn hơn".
4. **Report cooldown:** Không giới hạn. Mỗi trận chỉ report 1 lần/loại là đủ.
5. **Hard-delete policy:** Giữ mãi, không hard-delete.
6. **Push notification:** FCM + APNs (miễn phí), push tất cả 11 loại. App xin quyền lúc cài.
7. **Admin panel:** 4 chức năng MVP: bật/tắt bộ môn, tranh chấp report bùng, xác minh venue report, đánh dấu INACTIVE.

> **Implementation notes đã chốt (từ phiên trước):**
> - Đồng thời (luồng B): Pairing CAS trên trạng thái + `revision` (FE gửi `revision` của gói khi chốt phương án / lưu chi phí / xác nhận), Match CAS trên version; retry tối đa 3 lần (ADR-0003 Sửa đổi 2026-11-10)
> - Match luồng B: danh sách lựa chọn địa điểm (1–5) và danh sách khung giờ (1–5) là hai tập riêng; phương án chốt của Pairing tham chiếu một phần tử mỗi tập (sửa 2026-11-10, thay JSONB `location_option_ids` / `time_options`)
> - Chat: gửi / đọc lịch sử qua REST; nhận ngay qua STOMP/WebSocket (SockJS tuỳ MATCH-002 `[cần xác nhận #1]`, ADR-0014); lịch sử phân trang theo con trỏ để bù tin
> - Luồng A expires_at: `expires_at ≤ scheduled_start_time`, không cho đổi ngày trận sau khi Match OPEN (phải hủy và tạo lại)

---

## 10. Trạng thái tài liệu

**Phiên bản:** v1.0 (baseline) → v1.1 (hoàn chỉnh) → v1.2 (2026-11-09: bổ sung UC-V05 sửa địa điểm, UC-V06 xóa ảnh, BR-V05..V10, AC-V04/V05 — cho `VENUE-001`) → v1.3 (2026-11-09: BR-M09..BR-M18, BR-C03..BR-C04, làm rõ BC-M04, sửa §2.1/§2.2/§5.2/§7.3 Business_Rules, UC-M01/M03/M04/M08/M09/C01/C02, sửa AC-M06/AC-C01, thêm AC-M09..AC-M12 — cho `MATCH-001`) → v1.5 (2026-11-11: luồng B — hai đội xác nhận một gói rõ ràng (phương án chốt + chi phí + tiền cọc) thay vì "đã thống nhất qua chat", gói đổi thì xác nhận lại; Pairing hoàn tất trước thắng; chi phí/cọc thuộc Pairing và chép sang Match khi ghép; chat hiển thị người thực sự gửi; quy tắc đội tạm ngưng / bộ môn tắt / kèo quá hạn áp cho thao tác luồng B; chat của kèo hết hạn cũng bị xóa sau 90 ngày; thêm BR-M19..BR-M21, BR-C05, BR-N03; sửa BR-M03, BR-M07, BR-M14, BR-M15, BR-M17, BR-N01, BC-M04, BC-N01, BC-N03, BC-V05, §2.3/§5.2/§5.5/§8.1 Business_Rules, UC-M02/M05/M06/M07/M08/M09/C01/N01/N03, AC-M02/M04/M05/C01/N01, Scenario 2/3, Data_Model Match/Pairing/Chat_Message/Cost_Agreement/§6 — cho `MATCH-002`)  
**Ngày hoàn thiện:** 2026-11-02  
**Trạng thái:** ✅ **Hoàn chỉnh** — Tất cả quyết định nghiệp vụ đã được chốt, không còn [cần xác nhận]  
**Sẵn sàng cho:** Solution design (thiết kế kỹ thuật chi tiết)
