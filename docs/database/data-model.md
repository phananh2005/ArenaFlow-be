# Thiết kế dữ liệu — ArenaFlow-be

> Trạng thái: **Đặc tả nghiệp vụ hoàn chỉnh; sẵn sàng thiết kế schema**

> Cập nhật: 2026-11-01 (nội dung nghiệp vụ) · 2026-11-05 (ghi chú đường dẫn multi-module, §2) · 2026-11-12 (uy tín đội — quyết định user chốt cho REPUTATION-001, §3.4)

> **Phạm vi cốt lõi:** tạo kèo / ghép người chơi. Sân là địa điểm tham chiếu; ArenaFlow không quản lý vận hành, đặt sân hoặc thanh toán.

## 1. Mục đích và phạm vi

Tài liệu ghi nhận các quy tắc nghiệp vụ đã được người dùng xác nhận để làm đầu vào cho thiết kế schema. Đây chưa phải migration hay schema triển khai. Không suy ra kiểu ID, kiểu dữ liệu vật lý, timezone, ràng buộc đầy đủ hoặc chi tiết thuật toán khi chưa có căn cứ.

Các quyết định được ghi là **Đã xác nhận**; các nội dung chưa được quyết định được đánh dấu **[cần xác nhận]**. Đề xuất trước đây của trợ lý không được xem là quyết định nếu người dùng chưa đồng ý rõ ràng.

## 2. Cơ sở khảo sát

Khảo sát ngày 2026-11-01:

| Nguồn | Kết quả | Ý nghĩa |
|---|---|---|
| `src/main/java/` | Chỉ có application class; chưa có entity nghiệp vụ | Chưa thể suy ra mô hình từ code |
| Repository | Chưa có migration Flyway trong `src/main/resources/` | Chưa có schema nghiệp vụ trong repo |
| `application.properties` | PostgreSQL datasource; Flyway bật; Hibernate `ddl-auto=validate` | Migration và entity cần đồng bộ khi triển khai |
| `pom.xml` | Spring Data JPA, Flyway và PostgreSQL driver | Persistence đã được cấu hình |
| Neon `neondb`, branch mặc định `main` | Không có bảng tại thời điểm khảo sát | Chỉ là ảnh chụp tại ngày khảo sát |

> Đường dẫn trong bảng là cấu trúc một module tại ngày khảo sát. Từ 2026-11-05 (ADR-0006) repo là Maven multi-module: entity/repository ở `domain/src/main/java/`, migration Flyway và `application.properties` ở `api/src/main/resources/`, dependency persistence ở `domain/pom.xml` và `api/pom.xml`.

## 3. Quyết định nghiệp vụ đã xác nhận

### 3.1 Bộ môn, đội và hồ sơ

- Hệ thống hướng tới nhiều bộ môn; bóng đá là bộ môn đầu tiên. Admin bật/tắt bộ môn qua admin panel. Quy tắc bộ môn dùng chung trong MVP (danh mục vị trí, định dạng trận, trình độ áp dụng cho mọi môn); thiết kế cấu hình riêng theo môn để sau khi có yêu cầu môn thứ 2.
- Chỉ có tài khoản cá nhân; một cá nhân có thể tạo hoặc tham gia nhiều đội. Quan hệ cá nhân–đội là nhiều-nhiều.
- Hồ sơ đội gồm tên, người tạo, ngày tạo, avatar/logo và mô tả/giới thiệu. Tên đội được phép trùng.
- Chỉ admin đánh dấu đội `INACTIVE` (khoá); đội trưởng có thể giải tán đội (`DISBANDED`, xem dưới).
- MVP có một đội trưởng chính và thành viên. Đội trưởng mời; người được mời phải chấp nhận để trở thành thành viên. Giữ lịch sử thành viên, không xóa cứng quan hệ. Đội trưởng quản lý đội và đại diện đội trong kèo.
- **Chuyển đội trưởng:** người đang là đội trưởng chọn thành viên đang hoạt động; người được chọn bắt buộc nhận (không có quyền từ chối). Hệ thống tự chuyển quyền ngay lập tức. Lưu lịch sử chuyển quyền.
- **Xóa tài khoản đội trưởng:**
  - Trước khi xóa tài khoản, đội trưởng phải chuyển quyền cho thành viên khác hoặc **giải tán đội** — kể cả khi là người duy nhất hoặc đội đang bị admin khoá; không còn ngoại lệ đội tự chuyển `INACTIVE` (USER-002, ADR-0005).
  - Không xóa cứng dữ liệu lịch sử. Sau soft-delete tài khoản, lịch sử hiển thị “Người dùng đã xóa”; điểm/đánh giá lịch sử của đội khác được giữ nguyên.
- **Giải tán đội:** chỉ đội trưởng đang hoạt động; đội chuyển `DISBANDED` (trạng thái cuối, không kích hoạt lại), ghi lý do (≤ 100 ký tự, mặc định "Đội trưởng giải tán đội"), thời điểm và người giải tán; mọi thành viên → rời đội, lời mời đang chờ → bị từ chối; lý do khoá của admin (nếu có) giữ nguyên (USER-002).
- **Hồ sơ cá nhân:** 1:1 với tài khoản; các trường gồm tên hiển thị, ảnh đại diện, tiểu sử, vị trí chơi ưa thích, ngày sinh, giới tính và khu vực (phường/xã).
- **Đăng nhập:** Hỗ trợ OAuth với Google, Facebook và Phone number (OTP). Không có email/password truyền thống trong MVP.
- **Soft-delete:** Tài khoản đã xóa tạm thời giữ mãi trong DB, có thể khôi phục. Hard-delete để sau khi có chính sách retention.

### 3.2 Trình độ và định dạng trận

- Trình độ là thuộc tính hồ sơ đội, dùng chung giữa các bộ môn; không phải thuộc tính bắt buộc của từng bài đăng.
- Đội được chọn tối đa 3 trong 9 nhãn độc lập: `Chill`, `Nhiệt tình`, `Máu lửa`, `Vui chơi`, `Rèn luyện`, `Thi đấu`, `Mới bắt đầu`, `Đã có kinh nghiệm`, `Lão làng`.
- Hỗ trợ hiển thị định dạng 5v5, 7v7 và 11v11; định dạng chỉ để hiển thị, không điều khiển logic tuyển người.
- Tuổi và tính chất trận là văn bản tự do; không cần thêm trường khác theo yêu cầu hiện tại.

### 3.3 Kèo, Match_Request và pairing

Một bài đăng Match có một trong hai luồng: **A — đã có sân** hoặc **B — chưa có sân**. Match có thể có nhiều yêu cầu/pairing cùng lúc.

**Trạng thái Match:**

- `OPEN`: đang nhận tương tác.
- `CONFIRMED`: đã ghép thành công.
- `ARCHIVED`: trận đã kết thúc theo thời gian dự kiến, chưa có đội trưởng nào đánh giá.
- `COMPLETED`: có ít nhất một đội trưởng đã đánh giá.
- `EXPIRED`: hết hạn theo thời điểm do tác giả chọn (chưa ghép được).
- `CANCELLED`: tác giả hoặc đội đang tham gia hủy theo quy tắc tương ứng.

`CONFIRMED` là trạng thái cuối về nội dung: không được sửa giờ/sân/trình độ; muốn thay đổi thì hủy kèo. Hủy Match `OPEN` không ảnh hưởng uy tín. Nếu còn Match_Request `PENDING`, tác giả phải từ chối tất cả trước khi Match có thể chuyển `CANCELLED`.

**Luồng A — đã có sân:**

- Tác giả đã tự đặt sân bên ngoài ArenaFlow rồi đăng bài.
- Tác giả chọn thời hạn bài đăng: thời điểm cụ thể, theo giờ đá hoặc một thời điểm trước giờ đá. Thời hạn nằm trong khoảng tối đa 7 ngày trước giờ đá đến giờ bắt đầu trận; quy tắc đầu/cuối khoảng và trường hợp ngày trận thay đổi **[cần xác nhận]**.
- Đội khác gửi Match_Request. Khi tác giả chấp nhận một request, request đó thành `ACCEPTED`, Match thành `CONFIRMED`, các request `PENDING` còn lại tự thành `SUPERSEDED` và được thông báo.
- Cần ràng buộc dữ liệu bảo đảm tối đa một request `ACCEPTED` trên mỗi Match.

**Luồng B — chưa có sân:**

- Một bài đăng có thể nêu nhiều lựa chọn địa điểm và nhiều giờ hẹn. Tác giả tự chọn hạn bài đăng (`expires_at`), không muộn hơn giờ bắt đầu trận sớm nhất trong các lựa chọn.
- Vị trí gốc để gợi ý có thể chọn bằng GPS hiện tại, tìm kiếm địa chỉ, hoặc chọn khu vực dạng văn bản. Dữ liệu GPS/địa chỉ đã lưu được giữ; không cần làm mờ. Không lưu consent riêng trong DB; truy cập GPS dựa trên quyền hệ điều hành.
- Nhiều đội có thể nhận cùng bài đăng và tạo pairing đồng thời. Match vẫn `OPEN` trong lúc trao đổi.
- **Trao đổi và xác nhận:** Hai đội trưởng trao đổi qua chat để thống nhất địa điểm/giờ. MVP chỉ lưu tin nhắn chat; không có cấu trúc đề xuất riêng. Chỉ khi cả hai xác nhận (qua UI riêng, không phải tin nhắn) thì Match thành `CONFIRMED`. Pairing không chốt được thì Match vẫn `OPEN` để tiếp tục nhận đội khác.
- **Thời gian:** Tất cả thời gian lưu theo UTC trong DB; frontend convert theo timezone thiết bị người dùng.
- Pairing hết hạn theo `expires_at` của bài đăng.

**Trạng thái Pairing:** `NEGOTIATING`, `CONFIRMED`, `EXPIRED`, `SUPERSEDED`.

- Pairing bắt đầu ở `NEGOTIATING` khi đội nhận bài đăng luồng B.
- Khi cả hai đội trưởng xác nhận → pairing thành `CONFIRMED`, Match thành `CONFIRMED`.
- Nếu hai pairing cùng được xác nhận gần đồng thời (race condition), pairing có timestamp xác nhận cuối sớm hơn thắng; pairing kia chuyển `SUPERSEDED`.
- Khi Match chuyển `CONFIRMED` hoặc `CANCELLED`: mọi pairing đang `NEGOTIATING` và mọi Match_Request `PENDING` tự chuyển `SUPERSEDED` (nếu Match `CONFIRMED`) hoặc `CANCELLED` (nếu Match `CANCELLED`).

**Trạng thái Match_Request:** `PENDING`, `ACCEPTED`, `REJECTED`, `WITHDRAWN`, `SUPERSEDED`, `EXPIRED`.

- Request hết hạn theo bài đăng, không có timeout riêng 2 giờ. Khi Match hết hạn, các request còn `PENDING` hết hạn theo.
- Đội gửi có thể rút request. Rút khi `PENDING` không bị trừ uy tín; rút sau khi `ACCEPTED` bị trừ Reliability Score theo khoảng cách tới giờ trận như hủy Match xác nhận.
- Các trạng thái request cạnh tranh được chuyển sang `SUPERSEDED` khi một request được chấp nhận ở luồng A.

**Cập nhật trạng thái hết hạn:** người dùng đồng ý cơ chế kết hợp scheduled job mỗi 5 phút và lazy check khi API được gọi. Job phục vụ cập nhật bền vững/cascade và thông báo; lazy check để phản hồi trạng thái hiện thời cho người truy cập. Cơ chế gửi push/in-app cụ thể **[cần xác nhận]**.

### 3.4 Hủy kèo và uy tín

- Match `CONFIRMED` có thể bị hủy bất cứ lúc nào; bên thực hiện hủy bị trừ Reliability Score. Hủy càng sát giờ trận, mức trừ càng cao.
- Các khoảng phạt đã đồng ý: hủy hoặc rút request đã `ACCEPTED` từ 24 giờ trở lên trước trận: `-2`; từ 4 đến dưới 24 giờ: `-5`; dưới 4 giờ: `-10`. Điểm được trừ khỏi Reliability Score, không trực tiếp thay đổi Completion Rate.
- Hủy Match `CANCELLED` từ 24 giờ trở lên trước trận bị loại khỏi mẫu số Completion Rate. Cách xử lý hủy dưới 24 giờ trong mẫu số được mô tả ở mục Completion Rate.
- Hủy bài `OPEN` không ảnh hưởng uy tín; phải từ chối mọi request `PENDING` trước.

**Uy tín có hai loại độc lập: đội và cá nhân.**

- Reliability Score có thang 0–100, điểm khởi đầu 80. Mức hiển thị: 95–100 lời khen; 70–94 không có cảnh báo đặc biệt; 50–69 cảnh báo; dưới 50 cảnh báo mạnh.
- Điểm được chặn trong khoảng [0, 100]: `Math.max(0, Math.min(100, currentScore + delta))`.
- **Các thay đổi điểm:**
  - Hoàn thành trận: `+2`
  - 5 trận sạch liên tiếp: `+5` (cộng dồn với `+2` → `+7` ở trận thứ 5)
  - 10 trận sạch liên tiếp: `+10` (cộng dồn với `+2` → `+12` ở trận thứ 10)
  - Thưởng lặp lại ở mỗi mốc bội số của 5 trận sạch liên tiếp: bội số của 10 → `+10`, bội số khác của 5 → `+5` (vd trận 15 → `+5`, trận 20 → `+10`) (2026-11-12)
  - Rating 5 sao: `+1` (sao là số nguyên 1–5; sửa 2026-11-12, trước đó "từ 4.5 sao")
  - Hủy Match xác nhận: `-2` (≥24 giờ trước), `-5` (4-24 giờ), `-10` (<4 giờ)
  - Rút Match_Request đã `ACCEPTED`: `-2/-5/-10` theo khoảng cách giờ trận
  - Mercenary rút sau khi được duyệt: `-1` (≥24 giờ), `-3` (4-24 giờ), `-5` (<4 giờ)
  - ~~Bị xác nhận bùng qua tranh chấp: `-10`~~ — bỏ 2026-11-12: Admin xác nhận report bùng giữ nguyên mức đã trừ khi report, không trừ thêm
  - Bị report: xem bảng mức trừ theo loại và số lần bên dưới
- ~~**Trận sạch:** trận hoàn thành không có hủy, bùng, rút mercenary, hoặc report được xác nhận.~~ — sửa 2026-11-12: xét lúc Match chuyển `COMPLETED` — đội không bị report nào (chưa bị Admin bác) trong trận → chuỗi trận sạch +1, có → về 0; bị report ở bất kỳ trận nào (kể cả sau `COMPLETED`), hủy Match `CONFIRMED` hoặc rút Match_Request `ACCEPTED` → chuỗi về 0 (không thu hồi điểm đã cộng); đánh giá "đội kia không đến" không đổi chuỗi đội vắng; Admin bác report không khôi phục chuỗi; mercenary rút sau khi được duyệt → không phải trận sạch (SRS SYSTEM-001 v1.8 `Business_Rules.md` §3.1).
- Có Completion Rate, Reliability Score và rating trung bình 1–5 sao. Rating chỉ để hiển thị reputation, không trừ Reliability Score khi thấp; rating 5 sao cộng `+1` (sửa 2026-11-12).
- ~~Verification Badge yêu cầu: liên kết số điện thoại, hoàn thành >5 trận, rating trung bình >3 sao, và **Reliability Score ≥70**.~~ — sửa 2026-11-12: đủ 4 điều kiện — (1) số điện thoại: đội — đội trưởng hiện tại có số điện thoại; cá nhân — tài khoản có số điện thoại; (2) >5 trận hoàn thành (số trận tính vào tử số Completion Rate); (3) sao trung bình >3 ("đội kia không đến" không tính); (4) **Reliability Score ≥70** (SRS SYSTEM-001 v1.8 `Business_Rules.md` BR-R04).

**Completion Rate:**

- Áp dụng riêng cho đội và cá nhân. Completion Rate cá nhân chỉ tính các trận cá nhân tham gia với vai trò mercenary; rating cá nhân cũng chỉ đến từ trận mercenary. Đội bùng không làm giảm uy tín cá nhân thành viên.
- Match có trường **thời gian kết thúc dự kiến**. Đúng lúc thời gian kết thúc dự kiến, hệ thống tự động:
  - Gửi Push Notification cho hai đội trưởng: “Trận đấu hôm nay thế nào? Hãy nhập tỷ số và đánh giá đồng đội!”
  - Chuyển Match sang `ARCHIVED`.
- Khi có đội trưởng đánh giá (nhập tỷ số và/hoặc đánh giá), Match chuyển từ `ARCHIVED` → `COMPLETED` ngay lập tức.
- Đội trưởng còn lại vẫn có thể đánh giá sau đó bất cứ lúc nào, không giới hạn thời gian.
- ~~**Mẫu số:** Match đã `CONFIRMED`, loại trừ Match `CANCELLED` nếu hủy từ 24 giờ trở lên trước trận, và loại trừ Match `ARCHIVED` (không ai đánh giá).~~ — sửa 2026-11-12: Match `COMPLETED` của đội; Match `CANCELLED` sau `CONFIRMED` hủy dưới 24 giờ (chỉ đội hủy — dòng dưới); Match `ARCHIVED` mà đội bị report bùng chưa bị bác (dòng dưới). Không tính: `CONFIRMED` chưa đá, `EXPIRED`, `CANCELLED` khi còn `OPEN`, `CANCELLED` từ 24 giờ trở lên trước trận, `ARCHIVED` mà đội không bị report bùng (SRS SYSTEM-001 v1.8 `Business_Rules.md` §3.3).
- Match `CANCELLED` hủy dưới 24 giờ trước trận chỉ tính vào mẫu số của **đội hủy** (hoặc đội rút request đã `ACCEPTED`), không tính cho đội bị hủy kèo (2026-11-12).
- **Tử số:** Match `COMPLETED` có ít nhất một đội trưởng đánh giá và đội không bị report bùng (report chưa bị Admin bác) — loại khỏi tử số ngay khi report, không chờ hết hạn tranh chấp (sửa 2026-11-12).
- Match `ARCHIVED` (chưa ai đánh giá) mà đội bị report bùng (chưa bị bác) được tính vào mẫu số của đội đó (2026-11-12).
- Completion Rate và rating trung bình tính từ dữ liệu trận/đánh giá/report khi hiển thị, không lưu bộ đếm (2026-11-12).
- Chỉ hiển thị Completion Rate khi có ít nhất 5 trận đã chốt; trước đó hiển thị “Mới tham gia” kèm số trận hiện có, ví dụ `2/5 trận`.

**Đánh giá:**

- Đội trưởng có thể nhập tỷ số (tùy chọn, chỉ để lưu kỷ niệm, không dùng cho logic nghiệp vụ) và/hoặc đánh giá đối phương.
- Khi đánh giá, đội trưởng có hai lựa chọn: (1) **Đánh giá bình thường 1-5 sao**, hoặc (2) **”Đội kia không đến”**.
- Chọn “đội kia không đến” → hệ thống đề xuất người dùng có muốn report bùng không (tách khỏi luồng đánh giá).
- Rating không hiển thị cho bên được đánh giá trước khi bên đánh giá gửi đánh giá của mình; không cho sửa sau khi gửi.
- Không hiển thị trạng thái “đối phương đã/chưa đánh giá”. Bên nào đã đánh giá mới được xem rating đối phương dành cho mình; bên không đánh giá mất quyền xem rating đó.
- Chống đánh giá trùng: mỗi cặp (Match, đội đánh giá, đội được đánh giá) chỉ được đánh giá một lần. Cách lưu đối với mercenary **[cần xác nhận]**.

**Report và tranh chấp:**

- Hệ thống report bao gồm 4 loại: **Bùng**, **Chơi xấu**, **Giao tiếp tệ**, **Không đúng trình độ**. Đội trưởng có thể report đối phương sau trận, không có thời hạn.
- Report phải gắn với một trận cụ thể. Mỗi trận, đội trưởng có thể report đối phương nhiều loại đồng thời.
- **Report bùng:** tự động chấp nhận và trừ Reliability Score. Nếu đội bị report phản hồi trong 24 giờ → tranh chấp chuyển admin xem xét; quyết định của admin là cuối cùng.
- **Report khác (chơi xấu, giao tiếp tệ, không đúng trình độ):** tự động chấp nhận và trừ Reliability Score.
- Mức trừ tăng dần theo số lần bị report từng loại:

| Loại report | Lần 1 | Lần 2 | Lần 3 | Lần 4+ |
|---|---|---|---|---|
| Bùng | -5 | -8 | -12 | -15 |
| Chơi xấu | -3 | -5 | -8 | -10 |
| Giao tiếp tệ | -2 | -4 | -6 | -8 |
| Không đúng trình độ | -1 | -2 | -3 | -4 |

- **Giao tiếp tệ:** bao gồm chửi bới/thiếu tôn trọng trong chat, không phản hồi tin nhắn sau khi ghép kèo, thái độ thiếu hợp tác, ngôn từ khiếm nhã.
- Mỗi loại report đếm số lần riêng. Ví dụ: bị report bùng 2 lần và chơi xấu 1 lần → áp dụng mức lần 2 cho bùng, lần 1 cho chơi xấu.
- **Reset:** Sau khi hoàn thành 5 trận sạch liên tiếp (không bị report), tất cả bộ đếm số lần bị report được reset về 0.
- Report bùng (chưa bị Admin bác) → Match tính vào mẫu số nhưng không tính tử số Completion Rate của đội bị report; Admin bác → hoàn đúng số điểm đã trừ thực tế, giảm 1 lần bị report bùng (2026-11-12).

### 3.5 Địa điểm sân, chi phí, chat và thông báo

**Địa điểm:**

- Admin và người dùng được thêm địa điểm trực tiếp; không cần admin duyệt trước. ArenaFlow cảnh báo admin một lần khi địa điểm không được chọn trong 180 ngày hoặc có báo cáo ngừng hoạt động; không nhắc định kỳ.
- Các trường địa điểm: tên, địa chỉ, tọa độ, người tạo, created/updated, lần chọn gần nhất, trạng thái, điện thoại, website, loại sân.
- **Ảnh:** Lưu trong bảng riêng `venue_images`. Tối đa 5 ảnh mỗi địa điểm. Các trường: `venue_id`, `image_url`, `is_primary` (ảnh đại diện chính), `display_order`, `uploaded_by`, `created_at`. Ràng buộc: mỗi venue chỉ có một ảnh `is_primary = true`.
- **Tiện ích:** Không lưu trong MVP.
- **Report địa điểm:** Mỗi report lưu người report, lý do, thời gian. Admin thấy chi tiết từng report. Report không tự ẩn địa điểm; admin xác minh rồi đánh dấu ngừng hoạt động. Địa điểm ngừng hoạt động bị loại khỏi gợi ý mặc định, nhưng Match cũ giữ tham chiếu.
- Google Maps chỉ dùng hiển thị bản đồ/chỉ đường, không làm nguồn danh sách/master data sân. Availability sân không được ArenaFlow xác minh. Tích hợp Maps/API cụ thể cần chốt trước khi triển khai.
- **Thỏa thuận chi phí:** ArenaFlow chỉ lưu thỏa thuận để hiển thị trên bảng tin; không thu/giữ tiền hay xử lý thanh toán. Hai đội thỏa thuận và thanh toán bên ngoài.
- Các loại chi phí: tiền sân, tiền nước, hoặc cả hai.
- Cách chia: 50-50, 70-30 (đội chủ nhà bao 70%, hoặc ai thua bao 70%), đội thua trả toàn bộ tiền nước.
- Lưu số tiền cụ thể (ví dụ: 500000) chỉ để hiển thị; tiền tệ mặc định VND trong MVP.
- Khoản tự nhập: danh sách các khoản phí khác (tên + số tiền), ví dụ: "Tiền trọng tài 200000", "Phí áo đấu 50000".
- Luồng B có thể có thỏa thuận chia đôi tiền cọc; cọc do thỏa thuận bên ngoài, có trường "ai giữ tiền cọc" để hiển thị.

**Chat và Notification:**

- Chat giữa hai đội trưởng ở luồng B lưu trong DB 90 ngày sau khi Match kết thúc. Thời điểm bắt đầu retention: khi Match chuyển sang `COMPLETED`, `ARCHIVED`, hoặc `CANCELLED` (tính từ lúc hủy). Chưa cần tìm kiếm/xuất chat trong MVP.
- Notification lưu trong DB 30 ngày. Loại notification bao gồm:
  - Match request nhận được
  - Request được chấp nhận/từ chối
  - Match confirmed
  - Match cancelled
  - Pairing mới (luồng B)
  - Reminder trước trận
  - Match completed (kèm lời nhắc đánh giá)
  - Rating nhận được
  - Report nhận được
  - Mercenary application
  - Invitation vào đội
- Push notification: cơ chế push (FCM hay dịch vụ khác) và phạm vi push (tất cả hay một số loại quan trọng) để người dùng xem xét sau.

### 3.6 Tuyển mercenary

- Đội trưởng quyết định số lượng cần tuyển theo từng vị trí và duyệt người đăng ký. Danh mục vị trí cho bóng đá: **Thủ môn**, **Hậu vệ**, **Tiền vệ**, **Tiền đạo**.
- **GPS và tìm kiếm:**
  - App yêu cầu quyền GPS khi: (1) tìm kèo mercenary, (2) tạo kèo luồng B. Không lưu consent riêng trong DB; truy cập GPS dựa vào quyền hệ điều hành.
  - Tìm kiếm ưu tiên GPS hiện tại. Bán kính mặc định 3 km; người dùng có thể điều chỉnh.
  - Nếu người dùng không cấp GPS: vẫn tìm được kèo dựa trên **khu vực hồ sơ** (phường/xã đã chọn trong hồ sơ); hiển thị thông báo nhắc "Bật GPS để tìm kèo gần bạn hơn".
- **Khu vực hồ sơ:** Người dùng chọn đến cấp phường/xã từ danh mục chuẩn (Vietnam Administrative Units). Khu vực này dùng để: dự phòng khi không có GPS, gợi ý kèo, hiển thị trên hồ sơ.
- **Bảo vệ vị trí:** Không hiển thị tọa độ chính xác; chỉ hiển thị khoảng cách (ví dụ: "2.3 km từ bạn") và khu vực chung (ví dụ: "Quận 1").
- Đội trưởng có thể sửa hoặc đóng tin tuyển bất kỳ lúc nào. Khi đến **giờ kết thúc dự kiến** của Match, tin tuyển chuyển `EXPIRED`, kể cả đã đủ người.
- **Trạng thái tin tuyển:** `OPEN` (đang tuyển), `FILLED` (đã đủ người nhưng chưa hết hạn), `EXPIRED` (đã đến giờ kết thúc dự kiến của Match), `CLOSED` (đội trưởng đóng sớm).
- **Trạng thái đơn ứng tuyển:** `PENDING` (chờ duyệt), `APPROVED` (được duyệt), `REJECTED` (bị từ chối), `WITHDRAWN` (ứng viên rút).
- Ứng viên được duyệt có thể rút lui bất kỳ lúc nào; Reliability cá nhân bị trừ theo mức `-1/-3/-5` ứng với các khoảng thời gian ≥24 giờ, 4-24 giờ, <4 giờ trước trận.

### 3.7 Rating và Report cho mercenary

- **Đánh giá:** Đội trưởng đánh giá từng mercenary đã tham gia trận (1-5 sao). Mercenary cũng đánh giá lại đội (1-5 sao).
- **Report:** Cả đội trưởng và mercenary đều có thể report đối phương (bùng, chơi xấu, giao tiếp tệ, không đúng trình độ). Cơ chế report giống §3.4: tự động chấp nhận, trừ Reliability Score theo số lần, reset sau 5 trận sạch.
- **Cấu trúc lưu đánh giá:** bảng `rating` với các trường:
  - `match_id`: khóa ngoại tới Match (trận mà đánh giá gắn với)
  - `rater_type`: enum `TEAM` hoặc `INDIVIDUAL` (người đánh giá là đội hay cá nhân)
  - `rater_id`: ID của đội hoặc cá nhân đánh giá
  - `ratee_type`: enum `TEAM` hoặc `INDIVIDUAL` (người được đánh giá là đội hay cá nhân)
  - `ratee_id`: ID của đội hoặc cá nhân được đánh giá
  - `score`: điểm 1-5
  - `comment`: bình luận (tùy chọn)
  - `created_at`: thời gian đánh giá
- Chống trùng: unique constraint trên (`match_id`, `rater_type`, `rater_id`, `ratee_type`, `ratee_id`).

## 4. Từ điển khái niệm dữ liệu

> **Lưu ý:** Phần này sẽ được thay thế bằng schema khái niệm chi tiết ở bước tiếp theo.

Đây là khung khái niệm nghiệp vụ, không phải danh sách bảng đã chốt. Kiểu dữ liệu, khóa và nullability cần thiết kế ở bước schema.

| Miền / khái niệm | Nội dung đã xác nhận | Còn cần xác nhận |
|---|---|---|
| Cá nhân và hồ sơ | Tài khoản cá nhân; hồ sơ 1:1; OAuth; các thuộc tính hồ sơ ở §3.1; soft delete | Nhà cung cấp/kiểu đăng nhập, khóa liên kết OAuth, cách lưu vị trí và vị trí ưa thích |
| Đội và thành viên | Hồ sơ đội; tên trùng được; nhiều-nhiều; CAPTAIN/MEMBER; lời mời phải chấp nhận; giữ lịch sử | Kiểu khóa, unique/ràng buộc, chuyển đội trưởng bắt buộc nhận, xử lý đội không còn thành viên |
| Team tags / skill | Chọn tối đa 3 trong 9 nhãn đã liệt kê | Lưu enum hay danh mục dữ liệu |
| Match | Luồng A/B; nhiều lựa chọn giờ/địa điểm ở B; trạng thái OPEN/CONFIRMED/EXPIRED/CANCELLED; expiry và hủy ở §3.3–3.4 | Cấu trúc bảng nhiều giờ/địa điểm, thời gian/timezone, completion/check-in |
| Match_Request | Sáu trạng thái ở §3.3; request hết hạn theo bài; tối đa một ACCEPTED mỗi Match | Lưu lịch sử trạng thái; cách đảm bảo nhất quán đồng thời |
| Pairing | Nhiều đội có thể thương lượng đồng thời trên Match B; Match vẫn OPEN; hai bên xác nhận để chốt | Thực thể/trạng thái pairing, dữ liệu proposal và concurrency |
| Venue | Trường đã đồng ý ở §3.5; người dùng thêm trực tiếp; admin xác minh report | Cấu trúc ảnh/tiện ích/report, quy tắc unique/địa chỉ |
| Cost agreement | Loại chi phí và cách chia đã nêu; không giao dịch trong hệ thống | 70-30, tiền tệ, giá trị cọc và khoản tự nhập |
| Chat / Notification | Chat lưu 90 ngày sau khi Match kết thúc; notification 30 ngày | Mô hình người tham gia, sự kiện, trạng thái đọc, push và purge |
| Mercenary | Tin tuyển theo vị trí/số lượng; đội trưởng duyệt; GPS ưu tiên; expire lúc giờ trận | Trạng thái tin/đơn, danh mục vị trí, hủy và truy cập vị trí |
| Reputation / Rating | Đội và cá nhân độc lập; công thức/điều kiện §3.4 | Áp dụng cụ thể rating thấp vào Reliability; công thức aggregate và trạng thái completion |

## 5. Quy ước thiết kế schema

- Chỉ chuyển `[cần xác nhận]` thành quyết định khi có yêu cầu nghiệp vụ hoặc xác nhận rõ; ghi căn cứ cạnh quyết định.
- Trước khi chốt schema, xác định khóa chính/ngoại, ràng buộc, nullability, kiểu thời gian/tiền tệ và index theo truy vấn thực tế. Tuân thủ `docs/agent/non-functional-standards.md` khi thiết kế truy vấn.
- Mọi schema triển khai phải qua migration Flyway có kiểm soát; không chỉnh schema Neon thủ công như một phần của tài liệu này.
- Tài liệu không tự xác định kiểu dữ liệu vật lý hay tạo bảng migration.

## 6. Trạng thái

Tất cả các quy tắc nghiệp vụ đã được làm rõ và xác nhận với người dùng qua hỏi đáp từng nhóm câu hỏi. Tài liệu hiện lưu các quyết định đã xác nhận; **chưa phải schema có thể triển khai**.

**Đã xác nhận:**
- Luồng Match A/B với Pairing, trạng thái và xử lý race condition
- Completion qua đánh giá (không dùng check-in), Match `ARCHIVED`/`COMPLETED`, retention chat
- Reliability Score: công thức đầy đủ, bonus mốc, chặn [0,100], reset sau 5 trận sạch
- Report 4 loại (bùng, chơi xấu, giao tiếp tệ, không đúng trình độ), mức trừ tăng dần
- Rating tách biệt với report; rating 5 sao cộng điểm, rating thấp không trừ
- Verification Badge 4 điều kiện (phone + >5 trận + rating >3 + Reliability ≥70)
- Chi phí: thỏa thuận hiển thị, không thanh toán, 50-50/70-30/thua trả hết, khoản tự nhập
- GPS/mercenary: 4 vị trí, GPS khi tìm kèo/tạo luồng B, dự phòng khu vực hồ sơ (phường/xã), bảo vệ vị trí
- Tài khoản: OAuth (Google, Facebook, Phone OTP), chuyển quyền đội trưởng tức thì, soft-delete giữ mãi
- Venue: ảnh lưu bảng riêng tối đa 5, report chi tiết, không lưu tiện ích MVP
- Chat/Notification: retention 90/30 ngày, 11 loại notification, push để sau
- Bộ môn: admin bật/tắt, quy tắc dùng chung MVP
- Pairing luồng B: chỉ lưu chat, UTC, `expires_at` ≤ giờ trận
- Rating/Report mercenary: đôi bên đánh giá nhau, cấu trúc `rating` table với rater/ratee type

**Bước tiếp theo:** Lập mô hình bảng/thuộc tính/khóa, thiết kế migration Flyway, rà soát với `docs/agent/non-functional-standards.md`.
