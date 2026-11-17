# Business Rules — Tra cứu cho người dùng (USER-003)

> Mã tiếp nối SYSTEM-001 (BR-U01..U06) và USER-002 (BR-T06 sửa, BR-T07..T14, BC-T06). Quy tắc SYSTEM-001 / USER-002 không nhắc ở đây giữ nguyên.
> Quyết định đã chốt (Q-01..Q-16, user chốt 2026-11-17): `Acceptance_Criteria.md` §5.

## 1. Validation Rules

### 1.1. Danh mục khu vực

| Rule ID | Mô tả | Khi nào áp dụng |
|---|---|---|
| BR-U07 | Danh mục khu vực có **2 cấp**: tỉnh/thành và phường/xã; mỗi phường/xã thuộc đúng một tỉnh/thành. User chọn tỉnh/thành trước, rồi chọn phường/xã thuộc tỉnh/thành đó. Chỉ phường/xã được lưu làm khu vực hồ sơ (BR-U05 giữ nguyên). Hệ thống trả **toàn bộ** tỉnh/thành, hoặc **toàn bộ** phường/xã của một tỉnh/thành, sắp theo tên theo thứ tự chữ cái tiếng Việt; không phân trang, không tìm theo tên — ứng dụng tự lọc trong danh sách đang hiển thị (Q-02) | Khi xem danh mục, chọn khu vực hồ sơ |
| BR-U08 | Danh mục **chỉ đọc** với mọi người, kể cả Admin: ArenaFlow không có chức năng thêm/sửa/xoá tỉnh/thành, phường/xã. USER-003 chỉ hiển thị dữ liệu đang có trong danh mục; nạp đủ danh mục toàn quốc là việc dữ liệu riêng, ngoài USER-003 (Q-01, BC-U05) | Mọi lúc |
| BR-U09 | Chỉ người **đã đăng nhập** được xem danh mục, như mọi chức năng khác (Q-03) | Khi xem danh mục |

### 1.2. Tìm người dùng

| Rule ID | Mô tả | Khi nào áp dụng |
|---|---|---|
| BR-U10 | **Một ô tìm** cho cả hai cách (Q-07). Chuỗi nhập (bỏ khoảng trắng đầu/cuối) chỉ gồm chữ số và các ký tự viết số điện thoại (`+`, khoảng trắng, dấu chấm, gạch ngang) → tìm theo **số điện thoại** (BR-U13); còn lại → tìm theo **tên hiển thị**. Hệ quả chấp nhận: tên hiển thị chỉ gồm chữ số không tìm được theo tên. Tìm theo tên: (a) dấu chấm, gạch ngang và mọi ký tự không phải chữ/số được coi như **dấu cách**, ở cả tên hiển thị lẫn chuỗi nhập — vd tên "Thị-Ổi" gồm 2 từ "thi", "oi"; nhập "ng.an" tìm như "ng an"; chỉ tìm khi chuỗi nhập còn **ít nhất 2 chữ cái/chữ số** (sửa 2026-11-17 khi thiết kế); (b) khớp khi **mỗi từ** của chuỗi nhập khớp **đầu một từ** trong tên hiển thị, không phân biệt hoa/thường, **không phân biệt dấu** — vd "nguyen" khớp "Nguyễn Văn An" và "Lan Nguyễn"; "an" khớp "An Phạm", "Trần An", "Ánh Lê" nhưng **không** khớp "Lan Nguyễn" (chuỗi nằm giữa từ); "nguyen an" khớp "Nguyễn Văn An" (Q-08, sửa 2026-11-17 khi thiết kế để tìm được bằng chỉ mục cả khi nhập 2 ký tự); (c) **tối đa 20** gợi ý, không phân trang: tên **bắt đầu bằng** chuỗi nhập xếp trước, sau đó theo tên A→Z; có hơn 20 người khớp → kèm nhắc "Nhập thêm để thu hẹp kết quả" (Q-09) | Khi tìm người dùng |
| BR-U11 | Kết quả tìm **không hiển thị số điện thoại** của bất kỳ ai, kể cả khi tìm bằng số điện thoại. Mỗi người chỉ hiển thị **tên hiển thị và ảnh đại diện** (Q-10) — giữ quy tắc SYSTEM-001 §5.1 "User không xem hồ sơ người khác" | Khi hiển thị kết quả tìm |
| BR-U12 | Người **đã xoá tài khoản** không bao giờ xuất hiện trong kết quả (cả tìm theo SĐT lẫn theo tên) | Khi tìm người dùng |
| BR-U13 | Tìm theo SĐT chỉ khớp người có số điện thoại trong tài khoản (hiện tại: người đăng ký bằng số điện thoại). Người không có số điện thoại chỉ tìm được theo tên hiển thị. Chuỗi nhập được **quy về dạng 10 chữ số bắt đầu bằng 0** trước khi so khớp chính xác: bỏ khoảng trắng, dấu chấm, gạch ngang; đầu `+84` hoặc `84` đổi thành `0` — vd `+84 901 234 567`, `84901234567`, `0901.234.567` đều thành `0901234567`. Quy đổi xong không phải số VN hợp lệ (BR-U01) → "Số điện thoại không hợp lệ" (Q-06) | Khi tìm theo SĐT |
| BR-U14 | Tìm kiếm **không** tạo lời mời. Lời mời chỉ tạo ở UC-T02, và mọi quy tắc mời hiện hành giữ nguyên (đội phải đang hoạt động; người đã là thành viên hoặc đang có lời mời chờ từ đội → bị từ chối; người đã xoá tài khoản → "Không tìm thấy người dùng"). Kết quả tìm không bảo đảm mời được vì dữ liệu có thể đổi giữa lúc tìm và lúc mời | Khi mời từ kết quả tìm |
| BR-U15 | **Ai tìm được:** chỉ tìm **cho một đội cụ thể** — người tìm phải là đội trưởng của đội đó và đội phải đang hoạt động, đúng điều kiện được mời (Q-04). Người không làm đội trưởng đội nào không có chức năng tìm. **Ai có trong kết quả:** (a) người đã là thành viên đang hoạt động hoặc đang có lời mời chờ từ **đội này** vẫn hiện, kèm nhãn "Đã là thành viên" / "Đã được mời", không mời được từ kết quả (Q-05); (b) **không** có chính người tìm và **không** có tài khoản Admin (Q-11) — hệ quả: đội trưởng không tìm được, nên không mời được, tài khoản Admin vào đội. **Giới hạn số lần tìm:** mỗi người tìm tối đa **30 lần mỗi phút**, tính chung cả tìm theo SĐT lẫn theo tên và mọi đội; vượt giới hạn → hệ thống không tìm, báo "Bạn tìm quá nhanh, vui lòng thử lại sau" (Q-12, sửa 2026-11-17 khi thiết kế: hệ thống chưa có giới hạn chung, và ai cũng tự tạo được đội để thành đội trưởng nên điều kiện "chỉ đội trưởng" không đủ chống dò số điện thoại) | Khi tìm người dùng |

### 1.3. Lời mời của tôi

| Rule ID | Mô tả | Khi nào áp dụng |
|---|---|---|
| BR-T15 | "Lời mời của tôi" chỉ gồm lời mời **đang chờ** gửi tới **chính người đang xem**. Không gồm: lời mời đã chấp nhận/bị từ chối (không có lịch sử), lời mời gửi tới người khác, lời mời đội của tôi đã gửi đi | Khi xem lời mời của tôi |
| BR-T16 | Lời mời của **đội đã giải tán** không xuất hiện (đã tự chuyển "bị từ chối" khi giải tán — USER-002 BR-T10). Lời mời của **đội đang bị admin khoá** vẫn xuất hiện, kèm "Đội đang tạm ngưng — chưa thể chấp nhận/từ chối"; đội được kích hoạt lại thì chấp nhận/từ chối bình thường (Q-13) | Khi xem lời mời của tôi |
| BR-T17 | Mỗi lời mời kèm thông tin đủ để quyết định: tên đội, ảnh đội, trình độ đội (nhãn), trạng thái đội, số thành viên đang hoạt động, thời điểm mời, và tên **đội trưởng hiện tại** của đội — không phải người đã gửi lời mời, vd Lan mời rồi chuyển quyền cho Hùng → lời mời hiện "đội trưởng Hùng" (Q-14). Danh sách **mới nhất trước** (theo thời điểm mời), có phân trang, kèm **tổng số** lời mời đang chờ (Q-15) | Khi xem lời mời của tôi |
| BR-T18 | Chấp nhận/từ chối từ danh sách tuân theo UC-T03 và quy tắc hiện hành (đội bị khoá → chưa xử lý được, lời mời vẫn chờ; đội đã giải tán → "Đội đã giải tán"). Lời mời đã xử lý không còn trong danh sách | Khi chấp nhận/từ chối từ danh sách |

### 1.4. Đội của tôi

| Rule ID | Mô tả | Khi nào áp dụng |
|---|---|---|
| BR-T19 | "Đội của tôi" là **một** danh sách các đội mà người xem đang là **thành viên đang hoạt động** (đội trưởng hoặc thành viên thường), gồm đội **đang hoạt động** và đội **bị admin khoá**. Không gồm: đội đã rời, đội đã giải tán (mọi thành viên đã rời — USER-002 BR-T10), đội mới chỉ có lời mời đang chờ. **Không** có lọc theo trạng thái đội (Q-16) | Khi xem đội của tôi |
| BR-T20 | Mỗi đội kèm: tên đội, ảnh đội, trình độ đội (nhãn), **trạng thái đội** (đang hoạt động / bị khoá), **vai trò của tôi** (đội trưởng / thành viên), thời điểm tôi tham gia đội, tên đội trưởng hiện tại, số thành viên đang hoạt động. Thứ tự: đội tôi làm **đội trưởng xếp trước**, sau đó theo **thời điểm tôi tham gia đội, mới nhất trước**; có phân trang (Q-16) | Khi xem đội của tôi |
| BR-T21 | Lọc "Chỉ đội tôi làm đội trưởng" chỉ giữ các đội người xem đang là đội trưởng, ở cả hai trạng thái đang hoạt động và bị khoá. Danh sách này **đúng bằng** tập đội chặn xoá tài khoản theo USER-002 BR-T06 (sửa): lọc ra trống ⇔ vai trò đội trưởng không còn chặn xoá tài khoản | Khi lọc; khi UC-U03 bị chặn |

### 1.5. Chung

| Rule ID | Mô tả | Khi nào áp dụng |
|---|---|---|
| BR-U16 | "Lời mời của tôi" và "Đội của tôi" chỉ trả dữ liệu của **chính người đang đăng nhập**; không có cách xem thay người khác qua hai chức năng này (kể cả Admin) | Khi xem lời mời của tôi, đội của tôi |
| BR-U17 | Mọi chỗ hiển thị tên người khác trong USER-003 tuân theo BC-U02 (SYSTEM-001): người đã xoá tài khoản hiển thị "Người dùng đã xóa", không có ảnh | Khi hiển thị tên người dùng |

## 2. State Transitions

USER-003 **không** thêm hay đổi trạng thái nào. Các chức năng chỉ đọc trạng thái sẵn có:

| Đối tượng | Trạng thái được đọc | Dùng ở |
|---|---|---|
| Tài khoản | đang hoạt động / đã xoá | BR-U12 (loại người đã xoá) |
| Đội | đang hoạt động / bị khoá / đã giải tán | BR-T16, BR-T19, BR-T20 |
| Lời mời / tư cách thành viên | đang chờ / đang hoạt động / đã rời / bị từ chối | BR-T15 (đang chờ), BR-T19 (đang hoạt động) |
| Vai trò trong đội | đội trưởng / thành viên | BR-T20, BR-T21 |

## 3. Calculation Rules

Không có.

## 4. Constraints

| Constraint ID | Mô tả |
|---|---|
| BC-U03 | Số điện thoại của người dùng không bao giờ hiển thị cho người khác qua chức năng tìm người dùng (BR-U11) |
| BC-U04 | USER-003 không thêm thông tin lưu trữ mới; mọi kết quả được suy ra từ dữ liệu sẵn có |
| BC-U05 | Danh mục khu vực phải có đủ tỉnh/thành, phường/xã toàn quốc **trước khi phát hành cho người dùng thật**. Hiện danh mục chỉ có dữ liệu mẫu (5 tỉnh/thành, 50 phường/xã); việc nạp dữ liệu ngoài phạm vi USER-003 (Q-01) |

## 5. Authorization Rules (bổ sung SYSTEM-001 §5)

| Actor | Có thể | Không thể |
|---|---|---|
| User | Xem danh mục khu vực (đã đăng nhập — BR-U09); xem lời mời đang chờ của mình; xem đội mình đang tham gia | Xem lời mời / đội của người khác qua "Lời mời của tôi" / "Đội của tôi"; thấy số điện thoại người khác; thêm/sửa/xoá danh mục khu vực; tìm người dùng khi không làm đội trưởng của đội đang hoạt động |
| Team Captain | Tìm người dùng để mời vào **đội mình làm đội trưởng, đang hoạt động** (BR-U15); lọc các đội mình làm đội trưởng | Tìm cho đội mình không làm đội trưởng hoặc đội bị khoá; tạo lời mời chỉ bằng thao tác tìm (phải qua UC-T02) |
| Admin | Như User khi tra cứu cho chính mình | Thêm/sửa/xoá danh mục khu vực; xem "Lời mời của tôi" / "Đội của tôi" thay người khác. Tài khoản Admin không xuất hiện trong kết quả tìm người dùng (BR-U15) |

## 6. Danh sách [cần xác nhận]

Không còn — Q-01..Q-16 đã chốt 2026-11-17 (`Acceptance_Criteria.md` §5).
