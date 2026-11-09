# Acceptance Criteria — Tra cứu cho người dùng (USER-003)

## 1. Definition of Done (DoD)

- [ ] UC-U04, UC-U05, UC-T07, UC-T08 trong `Use_Cases.md` được implement
- [ ] BR-U07..U17, BR-T15..T21, BC-U03, BC-U04 trong `Business_Rules.md` được tuân thủ
- [ ] Mọi `[cần xác nhận]` ở §5 đã được chốt và phản ánh vào spec trước khi thiết kế kỹ thuật
- [ ] Không có kết quả tìm nào chứa số điện thoại (kiểm cả tìm theo SĐT lẫn theo tên)
- [ ] Unit + integration tests PASS
- [ ] SRS SYSTEM-001 (UC-U02, UC-T02, UC-T03, AC-T02) khớp USER-003 (v1.4)
- [ ] Code review PASS

## 2. Functional Acceptance Criteria

### AC-U04-01: Xem danh sách tỉnh/thành

**Given:** User đã đăng nhập; danh mục có các tỉnh/thành.
**When:** User mở chọn khu vực.
**Then:** Hiển thị đủ các tỉnh/thành trong danh mục (thứ tự theo Q-02); không có phường/xã trong danh sách này.

---

### AC-U04-02: Xem phường/xã của một tỉnh/thành

**Given:** Tỉnh/thành T có các phường/xã P1, P2.
**When:** User chọn T.
**Then:** Hiển thị P1, P2 và chỉ các phường/xã thuộc T.

---

### AC-U04-03: Tỉnh/thành không tồn tại

**Given:** User đang xem danh sách đã cũ; tỉnh/thành T không còn trong danh mục.
**When:** User chọn T.
**Then:** Báo "Không tìm thấy tỉnh/thành"; User chọn lại từ danh sách tỉnh/thành.

---

### AC-U04-04: Chọn khu vực và lưu hồ sơ

**Given:** User chọn tỉnh/thành T rồi phường/xã P thuộc T.
**When:** User lưu hồ sơ (UC-U02).
**Then:** Hồ sơ có khu vực P, hiển thị kèm tên T. Lưu với khu vực là tỉnh/thành (không phải phường/xã) → "Khu vực không hợp lệ" (BR-U05).

---

### AC-U04-05: Danh mục chỉ đọc

**Given:** Bất kỳ ai, kể cả Admin.
**When:** Tìm cách thêm/sửa/xoá tỉnh/thành hoặc phường/xã.
**Then:** Không có chức năng nào cho phép (BR-U08).

---

### AC-U05-01: Tìm theo số điện thoại chính xác

**Given:** Người dùng B đăng ký bằng số `0987654321`, tài khoản đang hoạt động.
**When:** Đội trưởng X tìm `0987654321`.
**Then:** Kết quả có đúng một người là B; kết quả **không** có số điện thoại; thông tin hiển thị theo Q-10.

---

### AC-U05-02: Số điện thoại không khớp ai hoặc thuộc người đã xoá tài khoản

**Given:** Không ai có số `0911111111`; người dùng C có số `0922222222` đã xoá tài khoản.
**When:** X tìm `0911111111`, rồi tìm `0922222222`.
**Then:** Cả hai lần báo "Không tìm thấy người dùng".

---

### AC-U05-03: Tìm theo tên hiển thị

**Given:** Có "Nguyễn Văn An" (đang hoạt động), "Nguyễn Văn Anh" (đã xoá tài khoản), "Trần An" (đang hoạt động).
**When:** X tìm "An".
**Then:** Gợi ý gồm "Nguyễn Văn An" và "Trần An" (so khớp theo Q-08; giới hạn và thứ tự theo Q-09); **không** có "Nguyễn Văn Anh"; không kết quả nào có số điện thoại.

---

### AC-U05-04: Tên dưới 2 ký tự

**Given:** X đang ở ô tìm người.
**When:** X nhập "A" hoặc " A " (một ký tự sau khi bỏ khoảng trắng đầu/cuối).
**Then:** Không tìm; nhắc "Nhập ít nhất 2 ký tự".

---

### AC-U05-05: Người không có số điện thoại chỉ tìm được theo tên

**Given:** D đăng ký bằng Google, không có số điện thoại, tên "Lê Dũng".
**When:** X tìm "Lê Dũng".
**Then:** D xuất hiện trong gợi ý. Không có số điện thoại nào tìm ra D.

---

### AC-U05-06: Chọn kết quả rồi mời

**Given:** X là đội trưởng đội A đang hoạt động; B không thuộc đội A và không có lời mời chờ từ A.
**When:** X tìm ra B, chọn B và gửi lời mời (UC-T02).
**Then:** Lời mời đang chờ gửi B được tạo; B thấy lời mời trong "Lời mời của tôi" (AC-T07-01). Thao tác tìm trước đó không tự tạo lời mời (BR-U14).

---

### AC-U05-07: Người đã là thành viên / đã được mời

**Given:** Y là thành viên đội A; Z có lời mời đang chờ từ A.
**When:** X tìm ra Y hoặc Z.
**Then:** Hiển thị theo Q-05. Nếu X vẫn gửi được lời mời cho Y/Z → bị từ chối "Người này đã là thành viên hoặc đã được mời" (quy tắc hiện hành, BR-U14).

---

### AC-T07-01: Xem lời mời đang chờ của tôi

**Given:** B có lời mời đang chờ từ đội A và đội C; một lời mời từ đội E đã bị B từ chối; một lời mời từ đội A gửi người khác.
**When:** B mở "Lời mời của tôi".
**Then:** Chỉ có lời mời từ A và C; mỗi lời mời có thông tin theo BR-T17; thứ tự/phân trang theo Q-15.

---

### AC-T07-02: Không có lời mời

**Given:** B không có lời mời đang chờ.
**When:** B mở "Lời mời của tôi".
**Then:** Hiển thị "Bạn chưa có lời mời nào".

---

### AC-T07-03: Lời mời của đội đã giải tán biến mất

**Given:** B có lời mời đang chờ từ đội A.
**When:** Đội trưởng giải tán A (UC-T06), rồi B mở "Lời mời của tôi".
**Then:** Không còn lời mời từ A (đã tự bị từ chối — USER-002 BR-T10).

---

### AC-T07-04: Lời mời của đội đang bị admin khoá

**Given:** B có lời mời đang chờ từ đội A; Admin khoá A.
**When:** B mở "Lời mời của tôi", rồi chấp nhận hoặc từ chối lời mời của A (nếu lời mời được hiển thị).
**Then:** Hiển thị hay không theo Q-13. Chấp nhận/từ chối → "Đội đang tạm ngưng hoạt động"; lời mời vẫn chờ (BR-T18).

---

### AC-T07-05: Chấp nhận từ danh sách

**Given:** B có lời mời đang chờ từ đội A đang hoạt động.
**When:** B chấp nhận lời mời từ "Lời mời của tôi".
**Then:** Lời mời không còn trong danh sách; A xuất hiện trong "Đội của tôi" với vai trò thành viên (AC-T08-01).

---

### AC-T08-01: Xem đội của tôi

**Given:** B là đội trưởng đội A (đang hoạt động), thành viên đội C (bị admin khoá), đã rời đội E, có lời mời chờ từ đội F, từng là thành viên đội G đã giải tán.
**When:** B mở "Đội của tôi".
**Then:** Chỉ có A (vai trò đội trưởng, trạng thái đang hoạt động) và C (vai trò thành viên, trạng thái bị khoá); mỗi đội có thông tin theo BR-T20; không có E, F, G.

---

### AC-T08-02: Lọc đội tôi làm đội trưởng

**Given:** Như AC-T08-01.
**When:** B bật lọc "Chỉ đội tôi làm đội trưởng".
**Then:** Chỉ có A.

---

### AC-T08-03: Lọc đội trưởng khớp điều kiện xoá tài khoản

**Given:** X là đội trưởng đội A (đang hoạt động) và đội C (bị admin khoá).
**When:** X xoá tài khoản (bị chặn — USER-002 AC-U03-01/02), mở "Đội của tôi" với lọc đội trưởng, chuyển quyền A (UC-T04) và giải tán C (UC-T06), rồi xoá tài khoản lại.
**Then:** Lọc lúc đầu có A và C; sau khi chuyển quyền A, A còn trong "Đội của tôi" với vai trò thành viên nhưng không còn trong lọc; sau khi giải tán C, C không còn trong danh sách; lọc trống → xoá tài khoản thành công.

---

### AC-T08-04: Không tham gia đội nào

**Given:** B không là thành viên đang hoạt động của đội nào.
**When:** B mở "Đội của tôi" (có hoặc không bật lọc).
**Then:** Hiển thị "Bạn chưa tham gia đội nào" (không lọc) hoặc "Bạn không làm đội trưởng đội nào" (có lọc).

---

### AC-T08-05: Lời mời và đội — chỉ xem của chính mình

**Given:** B và C là hai người dùng khác nhau; Admin đăng nhập.
**When:** B, C, Admin lần lượt mở "Lời mời của tôi" và "Đội của tôi".
**Then:** Mỗi người chỉ thấy lời mời và đội của chính mình; không có cách xem thay người khác (BR-U16).

## 3. Non-Functional Acceptance Criteria

- Hiệu năng: theo chuẩn của dự án (`docs/agent/non-functional-standards.md`) — thiết kế kỹ thuật chứng minh. Tìm theo tên phải phản hồi đủ nhanh để gợi ý khi đang gõ.
- Bảo mật / riêng tư: không hiển thị số điện thoại của người khác (BC-U03); người dùng không suy ra được lời mời/đội của người khác; chống dò số điện thoại: Q-12.

## 4. Test Scenarios

### Scenario 1: Mời bằng tên từ đầu đến cuối
1. X (đội trưởng đội A) tìm "Trần" → chọn "Trần An" → gửi lời mời.
2. Trần An mở "Lời mời của tôi" → thấy lời mời từ A → chấp nhận.
3. → Trần An thấy A trong "Đội của tôi" (thành viên); lời mời không còn trong danh sách.

### Scenario 2: Người được mời xoá tài khoản giữa lúc tìm và lúc mời
1. X tìm ra B.
2. B xoá tài khoản.
3. X gửi lời mời cho B → "Không tìm thấy người dùng" (BR-U14). Tìm lại → B không xuất hiện (BR-U12).

### Scenario 3: Lời mời bị xử lý song song
1. B mở "Lời mời của tôi" trên hai thiết bị.
2. Thiết bị 1 chấp nhận lời mời từ A; thiết bị 2 sau đó từ chối cùng lời mời.
3. → Thiết bị 2 nhận "lời mời đã được xử lý"; B là thành viên A.

### Scenario 4: Đội trưởng dọn đội trước khi xoá tài khoản
Như AC-T08-03.

## 5. Danh sách [cần xác nhận]

> Mỗi câu có phương án; **khuyến nghị** là đề xuất của BA, chưa phải quyết định. Chưa chốt → không thiết kế kỹ thuật phần liên quan.

### Danh mục khu vực

1. **Q-01 — Phạm vi dữ liệu danh mục.** Danh mục hiện có **5 tỉnh/thành và 50 phường/xã** (dữ liệu mẫu), chưa phải toàn quốc.
   - A: USER-003 chỉ hiển thị danh mục đang có; nạp đủ danh mục toàn quốc là việc dữ liệu riêng, ghi là điều kiện trước khi phát hành cho người dùng thật.
   - B: USER-003 gồm cả việc nạp đủ danh mục toàn quốc (theo danh mục chuẩn hiện hành).
   - **Khuyến nghị: A** — tách việc dữ liệu khỏi chức năng xem; vẫn chặn phát hành thật nếu chưa đủ dữ liệu.
2. **Q-02 — Thứ tự và tìm theo tên trong danh mục.**
   - A: Tỉnh/thành và phường/xã sắp theo tên (thứ tự chữ cái tiếng Việt); không có tìm theo tên — ứng dụng tự lọc trong danh sách đang hiển thị.
   - B: Như A, thêm tìm phường/xã theo tên trong một tỉnh/thành.
   - C: Như A, thêm tìm phường/xã theo tên trên toàn quốc (kết quả kèm tỉnh/thành).
   - **Khuyến nghị: A** — danh sách phường/xã của một tỉnh/thành đủ nhỏ để ứng dụng tự lọc. [Inference] dựa trên quy mô danh mục hành chính; chưa đo trên dữ liệu toàn quốc.
3. **Q-03 — Có cần đăng nhập để xem danh mục không.**
   - A: Chỉ người đã đăng nhập (cùng quy tắc với mọi chức năng khác).
   - B: Ai cũng xem được, không cần đăng nhập.
   - **Khuyến nghị: A** — danh mục chỉ dùng khi sửa hồ sơ, lúc đó User đã đăng nhập.

### Tìm người dùng

4. **Q-04 — Ai được tìm, trong điều kiện nào.**
   - A: Mọi người dùng đã đăng nhập.
   - B: Chỉ người đang là đội trưởng của ít nhất một đội đang hoạt động.
   - C: Tìm **trong ngữ cảnh một đội cụ thể**: người tìm phải là đội trưởng của đội đó và đội phải đang hoạt động (đúng điều kiện được mời).
   - **Khuyến nghị: C** — đúng mục đích "mời vào đội", giảm người có thể dò số điện thoại, và cho phép đánh dấu người đã trong đội (Q-05).
5. **Q-05 — Người đã là thành viên / đang có lời mời chờ từ đội.**
   - A: Vẫn hiện, kèm nhãn "Đã là thành viên" / "Đã được mời"; không cho mời (cần Q-04 = C).
   - B: Loại khỏi kết quả (cần Q-04 = C).
   - C: Hiện như mọi người; mời thì nhận lỗi hiện hành "đã là thành viên hoặc đã được mời".
   - **Khuyến nghị: A** — đội trưởng hiểu vì sao không mời được, tránh tưởng "không tìm thấy".
6. **Q-06 — Chuẩn hoá số điện thoại khi tìm.**
   - A: Chỉ nhận đúng dạng 10 chữ số bắt đầu bằng 0 (như khi đăng nhập); dạng khác → báo "Số điện thoại không hợp lệ".
   - B: Nhận cả `+84…`, `84…`, có khoảng trắng / dấu chấm / gạch ngang; quy về dạng 10 chữ số bắt đầu bằng 0 rồi so khớp chính xác.
   - **Khuyến nghị: B** — người dùng thường dán số từ danh bạ có `+84` hoặc dấu cách.
7. **Q-07 — Cách nhận biết tìm theo SĐT hay theo tên.**
   - A: Một ô nhập; chuỗi chỉ gồm chữ số (và ký tự số điện thoại theo Q-06) → tìm SĐT chính xác; ngược lại → tìm theo tên.
   - B: Hai lựa chọn riêng ("Theo số điện thoại" / "Theo tên"), người dùng chọn.
   - **Khuyến nghị: A** — gọn cho người dùng. Hệ quả: tên hiển thị chỉ gồm chữ số chỉ tìm được nếu trùng một số điện thoại — chấp nhận được.
8. **Q-08 — Cách so khớp tên.**
   - A: Tên **chứa** chuỗi nhập ở bất kỳ vị trí, không phân biệt hoa/thường, **không phân biệt dấu** ("nguyen" khớp "Nguyễn").
   - B: Như A nhưng **phân biệt dấu**.
   - C: Tên (hoặc một từ trong tên) **bắt đầu bằng** chuỗi nhập, không phân biệt hoa/thường và dấu.
   - **Khuyến nghị: A** — người dùng hay gõ không dấu.
9. **Q-09 — Giới hạn số gợi ý và thứ tự.**
   - A: Tối đa **20** gợi ý, không phân trang; tên bắt đầu bằng chuỗi nhập xếp trước, sau đó theo tên A→Z. Nhiều hơn 20 → nhắc "Nhập thêm để thu hẹp kết quả".
   - B: Phân trang đầy đủ như các danh sách khác, sắp theo tên A→Z.
   - **Khuyến nghị: A** — đây là gợi ý khi đang gõ, không phải danh bạ.
10. **Q-10 — Thông tin hiển thị mỗi kết quả.**
    - A: Tên hiển thị + ảnh đại diện (giống danh sách thành viên đội hiện có).
    - B: Như A, thêm khu vực (tỉnh/thành) và vị trí ưa thích để phân biệt người trùng tên.
    - **Khuyến nghị: A** — khớp quy tắc SYSTEM-001 §5.1 "User không xem hồ sơ người khác". Chọn B thì phải sửa quy tắc đó.
11. **Q-11 — Chính người tìm và tài khoản Admin trong kết quả.**
    - A: Loại chính người tìm; tài khoản Admin vẫn xuất hiện như người dùng khác.
    - B: Loại cả chính người tìm và tài khoản Admin.
    - C: Không loại ai (chính người tìm hiện theo Q-05 vì đã là thành viên).
    - **Khuyến nghị: A** — không có lý do nghiệp vụ để chặn mời Admin vào đội; hiện chính mình chỉ gây nhiễu.
12. **Q-12 — Giới hạn số lần tìm theo số điện thoại (chống dò danh bạ).**
    - A: Không giới hạn riêng; dùng giới hạn chung của hệ thống (SYSTEM-001 AC §3.2).
    - B: Giới hạn riêng cho tìm theo SĐT (vd tối đa 30 lần/giờ/người); vượt → báo "Bạn tìm quá nhiều lần, vui lòng thử lại sau".
    - **Khuyến nghị: A cho MVP** nếu Q-04 = C (chỉ đội trưởng của đội đang hoạt động được tìm); chọn B nếu Q-04 = A.

### Lời mời của tôi

13. **Q-13 — Lời mời của đội đang bị admin khoá.**
    - A: Hiện, kèm trạng thái đội "Đang tạm ngưng — chưa thể chấp nhận/từ chối".
    - B: Ẩn cho tới khi đội được kích hoạt lại.
    - **Khuyến nghị: A** — lời mời vẫn đang chờ; ẩn đi làm người dùng tưởng lời mời đã mất.
14. **Q-14 — Người đại diện đội hiển thị trên lời mời.**
    - A: Đội trưởng **hiện tại** của đội (không cần lưu thêm thông tin).
    - B: Người **đã gửi** lời mời (phải lưu thêm "người mời"; lời mời cũ không có thông tin này).
    - **Khuyến nghị: A** — đủ để quyết định; không đổi dữ liệu lưu trữ.
15. **Q-15 — Thứ tự và phân trang của lời mời.**
    - A: Mới nhất trước; có phân trang như các danh sách khác; hiển thị tổng số lời mời đang chờ.
    - B: Mới nhất trước; trả toàn bộ, không phân trang.
    - **Khuyến nghị: A** — lời mời không hết hạn và đến từ nhiều đội nên có thể tích luỹ.

### Đội của tôi

16. **Q-16 — Thứ tự, phân trang và lọc trạng thái.**
    - Thứ tự — A: đội tôi làm đội trưởng trước, sau đó theo thời điểm tôi tham gia mới nhất trước; B: chỉ theo thời điểm tham gia mới nhất trước; C: theo tên đội A→Z.
    - Phân trang — A: có, như các danh sách khác; B: trả toàn bộ.
    - Lọc trạng thái — A: không có (luôn gồm đang hoạt động và bị khoá; đội đã giải tán tự nhiên không có vì không còn thành viên); B: thêm lọc "đang hoạt động" / "bị khoá".
    - **Khuyến nghị: thứ tự A, phân trang A, lọc trạng thái A.**
