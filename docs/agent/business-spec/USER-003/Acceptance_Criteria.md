# Acceptance Criteria — Tra cứu cho người dùng (USER-003)

## 1. Definition of Done (DoD)

- [ ] UC-U04, UC-U05, UC-T07, UC-T08 trong `Use_Cases.md` được implement
- [ ] BR-U07..U17, BR-T15..T21, BC-U03..BC-U05 trong `Business_Rules.md` được tuân thủ
- [x] Mọi `[cần xác nhận]` ở §5 đã được chốt (2026-11-17) và phản ánh vào spec
- [ ] Không có kết quả tìm nào chứa số điện thoại (kiểm cả tìm theo SĐT lẫn theo tên)
- [ ] Unit + integration tests PASS
- [ ] SRS SYSTEM-001 (UC-U02, UC-T02, UC-T03, AC-T02) khớp USER-003 (v1.12)
- [ ] Code review PASS

## 2. Functional Acceptance Criteria

### AC-U04-01: Xem danh sách tỉnh/thành

**Given:** User đã đăng nhập; danh mục có các tỉnh/thành.
**When:** User mở chọn khu vực.
**Then:** Hiển thị toàn bộ tỉnh/thành trong danh mục, sắp theo tên tiếng Việt (vd "Cần Thơ", "Đà Nẵng", "Hà Nội", "Hải Phòng", "TP. Hồ Chí Minh"), không phân trang; không có phường/xã trong danh sách này (BR-U07).

---

### AC-U04-06: Chưa đăng nhập

**Given:** Người dùng chưa đăng nhập.
**When:** Mở danh mục khu vực.
**Then:** Bị từ chối như mọi chức năng cần đăng nhập (BR-U09).

---

### AC-U04-02: Xem phường/xã của một tỉnh/thành

**Given:** Tỉnh/thành T có các phường/xã P1, P2.
**When:** User chọn T.
**Then:** Hiển thị toàn bộ phường/xã thuộc T (P1, P2), sắp theo tên, không phân trang; không có phường/xã của tỉnh/thành khác (BR-U07).

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

**Given:** Người dùng B đăng ký bằng số `0987654321`, tài khoản đang hoạt động. X là đội trưởng đội A đang hoạt động.
**When:** X tìm `0987654321` khi mời vào đội A.
**Then:** Kết quả có đúng một người là B, chỉ gồm tên hiển thị và ảnh đại diện; **không** có số điện thoại (BR-U11).

---

### AC-U05-01b: Số điện thoại viết theo dạng khác

**Given:** Như AC-U05-01.
**When:** X lần lượt tìm `+84 987 654 321`, `84987654321`, `0987.654.321`, `0987-654-321`.
**Then:** Mỗi lần ra đúng B (BR-U13). Tìm `+84 12345` → "Số điện thoại không hợp lệ".

---

### AC-U05-02: Số điện thoại không khớp ai hoặc thuộc người đã xoá tài khoản

**Given:** Không ai có số `0911111111`; người dùng C có số `0922222222` đã xoá tài khoản.
**When:** X tìm `0911111111`, rồi tìm `0922222222`.
**Then:** Cả hai lần báo "Không tìm thấy người dùng".

---

### AC-U05-03: Tìm theo tên hiển thị

**Given:** Có "Nguyễn Văn An" (đang hoạt động), "Nguyễn Văn Anh" (đã xoá tài khoản), "Trần An" (đang hoạt động), "Lan Nguyễn" (đang hoạt động), "An Phạm" (đang hoạt động).
**When:** X tìm "an".
**Then:** Gợi ý theo thứ tự: "An Phạm" (tên bắt đầu bằng "an" xếp trước), rồi "Nguyễn Văn An", "Trần An" (A→Z); **không** có "Lan Nguyễn" ("an" nằm giữa từ "Lan" — BR-U10) và **không** có "Nguyễn Văn Anh" (BR-U12); không kết quả nào có số điện thoại (BR-U10, BR-U11).

---

### AC-U05-03b: Tìm theo tên không dấu

**Given:** Như AC-U05-03.
**When:** X tìm "nguyen".
**Then:** Gợi ý có "Lan Nguyễn" và "Nguyễn Văn An" (không phân biệt dấu và hoa/thường — BR-U10).

---

### AC-U05-03e: Tìm nhiều từ

**Given:** Như AC-U05-03.
**When:** X tìm "Nguyen An".
**Then:** Gợi ý chỉ có "Nguyễn Văn An" — mỗi từ nhập khớp đầu một từ trong tên; "Lan Nguyễn" không có vì "an" không khớp đầu từ nào (BR-U10).

---

### AC-U05-03c: Hơn 20 người khớp

**Given:** Có 25 người dùng đang hoạt động có tên chứa từ bắt đầu bằng "Minh".
**When:** X tìm "Minh".
**Then:** Hiển thị đúng 20 gợi ý kèm nhắc "Nhập thêm để thu hẹp kết quả"; không có trang tiếp theo (BR-U10).

---

### AC-U05-03d: Không có chính mình và tài khoản Admin

**Given:** X tên "Minh Trí"; tài khoản Admin M tên "Minh Quản trị", có số điện thoại `0900000001`.
**When:** X tìm "Minh", rồi tìm `0900000001`, rồi tìm số điện thoại của chính X.
**Then:** Gợi ý theo tên không có X và M; hai lần tìm theo số điện thoại đều báo "Không tìm thấy người dùng" (BR-U15).

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

**Given:** X là đội trưởng đội A và đội C; Y là thành viên đội A; Z có lời mời đang chờ từ A.
**When:** X tìm Y và Z khi mời vào A, rồi khi mời vào C.
**Then:** Khi mời vào A: Y có nhãn "Đã là thành viên", Z có nhãn "Đã được mời", không mời được từ kết quả. Khi mời vào C: Y và Z không có nhãn, mời được (BR-U15). Lời mời gửi tới Y/Z vào A bằng cách khác vẫn bị từ chối "Người này đã là thành viên hoặc đã được mời" (BR-U14).

---

### AC-U05-08: Chỉ đội trưởng của đội đang hoạt động được tìm

**Given:** X là đội trưởng đội A (đang hoạt động) và đội C (bị admin khoá); W là thành viên thường đội A.
**When:** X tìm người khi mời vào A, rồi khi mời vào C; W tìm người khi mời vào A.
**Then:** X tìm được cho A; X không tìm được cho C; W không tìm được (BR-U15).

---

### AC-U05-09: Tìm quá nhanh

**Given:** X là đội trưởng đội A (đang hoạt động) và đội B (đang hoạt động); trong phút vừa qua X đã tìm 20 lần khi mời vào A và 10 lần khi mời vào B.
**When:** X tìm lần thứ 31 (theo SĐT hoặc theo tên, cho A hoặc B) trong cùng phút đó.
**Then:** Hệ thống không tìm, báo "Bạn tìm quá nhanh, vui lòng thử lại sau"; sau khi hết thời gian chờ X tìm lại được (BR-U15).

---

### AC-T07-01: Xem lời mời đang chờ của tôi

**Given:** B có lời mời đang chờ từ đội A và đội C; một lời mời từ đội E đã bị B từ chối; một lời mời từ đội A gửi người khác.
**When:** B mở "Lời mời của tôi".
**Then:** Tổng số lời mời đang chờ là 2; chỉ có lời mời từ A và C, lời mời mới hơn đứng trước, có phân trang; mỗi lời mời có thông tin theo BR-T17, người đại diện là đội trưởng hiện tại của đội.

---

### AC-T07-01b: Người đại diện là đội trưởng hiện tại

**Given:** Lan (đội trưởng đội A) mời B; sau đó Lan chuyển quyền đội trưởng A cho Hùng.
**When:** B mở "Lời mời của tôi".
**Then:** Lời mời từ A hiển thị đội trưởng là Hùng (BR-T17).

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
**When:** B mở "Lời mời của tôi", rồi chấp nhận hoặc từ chối lời mời của A.
**Then:** Lời mời của A vẫn hiển thị, kèm "Đội đang tạm ngưng — chưa thể chấp nhận/từ chối" (BR-T16). Chấp nhận/từ chối → "Đội đang tạm ngưng hoạt động"; lời mời vẫn chờ (BR-T18). Admin kích hoạt lại A → B chấp nhận/từ chối bình thường.

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

### AC-T08-01b: Thứ tự đội của tôi

**Given:** B là thành viên đội P (tham gia 2026-11-01) và đội Q (tham gia 2026-11-10); là đội trưởng đội R (tham gia 2026-10-01).
**When:** B mở "Đội của tôi".
**Then:** Thứ tự R, Q, P — đội làm đội trưởng trước, sau đó tham gia mới nhất trước; có phân trang; không có lọc theo trạng thái đội (BR-T19, BR-T20).

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
- Bảo mật / riêng tư: không hiển thị số điện thoại của người khác (BC-U03); người dùng không suy ra được lời mời/đội của người khác; chống dò số điện thoại: chỉ đội trưởng của đội đang hoạt động được tìm, tối đa 30 lần tìm mỗi phút mỗi người (BR-U15).
- Phát hành: danh mục khu vực đủ toàn quốc trước khi phát hành cho người dùng thật (BC-U05).

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

## 5. Quyết định đã chốt

> User chốt 2026-11-17. Không còn `[cần xác nhận]`.

| Câu | Chủ đề | Chốt | Phản ánh ở |
|---|---|---|---|
| Q-01 | Phạm vi dữ liệu danh mục | USER-003 chỉ hiển thị danh mục đang có; nạp đủ toàn quốc là việc dữ liệu riêng, bắt buộc xong trước khi phát hành cho người dùng thật | BR-U08, BC-U05 |
| Q-02 | Thứ tự, tìm theo tên trong danh mục | Sắp theo tên tiếng Việt, trả toàn bộ; không tìm theo tên — ứng dụng tự lọc | BR-U07, UC-U04 2a |
| Q-03 | Đăng nhập để xem danh mục | Cần đăng nhập | BR-U09 |
| Q-04 | Ai được tìm người dùng | Chỉ tìm cho một đội cụ thể: người tìm là đội trưởng của đội đó, đội đang hoạt động | BR-U15, UC-U05 |
| Q-05 | Người đã là thành viên / đã được mời | Vẫn hiện, kèm nhãn "Đã là thành viên" / "Đã được mời", không mời được từ kết quả | BR-U15, UC-U05 4a |
| Q-06 | Chuẩn hoá số điện thoại | Nhận `+84…`, `84…`, khoảng trắng, dấu chấm, gạch ngang; quy về 10 chữ số bắt đầu bằng 0 rồi so khớp chính xác | BR-U13, UC-U05 2b |
| Q-07 | Nhận biết tìm SĐT hay tên | Một ô; chuỗi dạng số điện thoại → tìm SĐT, còn lại → tìm tên (tên toàn chữ số không tìm được theo tên — chấp nhận) | BR-U10 |
| Q-08 | So khớp tên | Mỗi từ nhập khớp đầu một từ trong tên, không phân biệt hoa/thường, không phân biệt dấu (sửa 2026-11-17 khi thiết kế — bản đầu là "chứa ở bất kỳ vị trí") | BR-U10, AC-U05-03, AC-U05-03e |
| Q-09 | Giới hạn, thứ tự gợi ý | Tối đa 20, không phân trang; tên bắt đầu bằng chuỗi nhập trước, rồi A→Z; hơn 20 → nhắc nhập thêm | BR-U10, UC-U05 3a |
| Q-10 | Thông tin mỗi kết quả | Tên hiển thị + ảnh đại diện | BR-U11 |
| Q-11 | Loại ai khỏi kết quả | Loại chính người tìm **và** tài khoản Admin — hệ quả: không mời được Admin vào đội qua tìm kiếm | BR-U15 |
| Q-12 | Giới hạn số lần tìm | Tối đa 30 lần tìm mỗi phút mỗi người, tính chung SĐT và tên, mọi đội (sửa 2026-11-17 khi thiết kế — bản đầu "dùng giới hạn chung", nhưng hệ thống chưa có) | BR-U15, UC-U05 1b, AC-U05-09, §3 |
| Q-13 | Lời mời của đội bị admin khoá | Vẫn hiện, kèm "Đội đang tạm ngưng — chưa thể chấp nhận/từ chối" | BR-T16, UC-T07 2c |
| Q-14 | Người đại diện đội trên lời mời | Đội trưởng hiện tại | BR-T17 |
| Q-15 | Thứ tự, phân trang lời mời | Mới nhất trước, có phân trang, kèm tổng số lời mời đang chờ | BR-T17 |
| Q-16 | Thứ tự, phân trang, lọc "Đội của tôi" | Đội trưởng trước, rồi tham gia mới nhất trước; có phân trang; không lọc theo trạng thái đội | BR-T19, BR-T20 |
