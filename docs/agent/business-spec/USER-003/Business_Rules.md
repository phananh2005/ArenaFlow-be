# Business Rules — Tra cứu cho người dùng (USER-003)

> Mã tiếp nối SYSTEM-001 (BR-U01..U06) và USER-002 (BR-T06 sửa, BR-T07..T14, BC-T06). Quy tắc SYSTEM-001 / USER-002 không nhắc ở đây giữ nguyên.
> `[cần xác nhận Q-xx]` — chi tiết và phương án: `Acceptance_Criteria.md` §5.

## 1. Validation Rules

### 1.1. Danh mục khu vực

| Rule ID | Mô tả | Khi nào áp dụng |
|---|---|---|
| BR-U07 | Danh mục khu vực có **2 cấp**: tỉnh/thành và phường/xã; mỗi phường/xã thuộc đúng một tỉnh/thành. User chọn tỉnh/thành trước, rồi chọn phường/xã thuộc tỉnh/thành đó. Chỉ phường/xã được lưu làm khu vực hồ sơ (BR-U05 giữ nguyên). Thứ tự hiển thị và tìm theo tên: `[cần xác nhận Q-02]` | Khi xem danh mục, chọn khu vực hồ sơ |
| BR-U08 | Danh mục **chỉ đọc** với mọi người, kể cả Admin: ArenaFlow không có chức năng thêm/sửa/xoá tỉnh/thành, phường/xã. Phạm vi dữ liệu (toàn quốc hay một phần): `[cần xác nhận Q-01]` | Mọi lúc |
| BR-U09 | Ai được xem danh mục: `[cần xác nhận Q-03]` | Khi xem danh mục |

### 1.2. Tìm người dùng

| Rule ID | Mô tả | Khi nào áp dụng |
|---|---|---|
| BR-U10 | Hai cách tìm: (a) **số điện thoại** — so khớp chính xác, cho ra tối đa **một** người; (b) **tên hiển thị** — chỉ tìm khi chuỗi nhập có **ít nhất 2 ký tự** sau khi bỏ khoảng trắng đầu/cuối, cho ra danh sách gợi ý. Cách nhận biết SĐT hay tên: `[cần xác nhận Q-07]`. Cách so khớp tên: `[cần xác nhận Q-08]`. Giới hạn số gợi ý và thứ tự: `[cần xác nhận Q-09]` | Khi tìm người dùng |
| BR-U11 | Kết quả tìm **không hiển thị số điện thoại** của bất kỳ ai, kể cả khi tìm bằng số điện thoại. Thông tin hiển thị cho mỗi người: `[cần xác nhận Q-10]` | Khi hiển thị kết quả tìm |
| BR-U12 | Người **đã xoá tài khoản** không bao giờ xuất hiện trong kết quả (cả tìm theo SĐT lẫn theo tên) | Khi tìm người dùng |
| BR-U13 | Tìm theo SĐT chỉ khớp người có số điện thoại trong tài khoản (hiện tại: người đăng ký bằng số điện thoại). Người không có số điện thoại chỉ tìm được theo tên hiển thị. Số nhập vào phải là số VN hợp lệ theo BR-U01; nhận các dạng viết khác (`+84`, khoảng trắng, dấu chấm…) hay không: `[cần xác nhận Q-06]` | Khi tìm theo SĐT |
| BR-U14 | Tìm kiếm **không** tạo lời mời. Lời mời chỉ tạo ở UC-T02, và mọi quy tắc mời hiện hành giữ nguyên (đội phải đang hoạt động; người đã là thành viên hoặc đang có lời mời chờ từ đội → bị từ chối; người đã xoá tài khoản → "Không tìm thấy người dùng"). Kết quả tìm không bảo đảm mời được vì dữ liệu có thể đổi giữa lúc tìm và lúc mời | Khi mời từ kết quả tìm |
| BR-U15 | Ai được tìm và trong điều kiện nào: `[cần xác nhận Q-04]`. Người đã là thành viên / đang có lời mời chờ trong kết quả: `[cần xác nhận Q-05]`. Chính người tìm và tài khoản Admin trong kết quả: `[cần xác nhận Q-11]`. Giới hạn số lần tìm theo SĐT: `[cần xác nhận Q-12]` | Khi tìm người dùng |

### 1.3. Lời mời của tôi

| Rule ID | Mô tả | Khi nào áp dụng |
|---|---|---|
| BR-T15 | "Lời mời của tôi" chỉ gồm lời mời **đang chờ** gửi tới **chính người đang xem**. Không gồm: lời mời đã chấp nhận/bị từ chối (không có lịch sử), lời mời gửi tới người khác, lời mời đội của tôi đã gửi đi | Khi xem lời mời của tôi |
| BR-T16 | Lời mời của **đội đã giải tán** không xuất hiện (đã tự chuyển "bị từ chối" khi giải tán — USER-002 BR-T10). Lời mời của **đội đang bị admin khoá** (vẫn đang chờ nhưng chưa chấp nhận/từ chối được): `[cần xác nhận Q-13]` | Khi xem lời mời của tôi |
| BR-T17 | Mỗi lời mời kèm thông tin đủ để quyết định: tên đội, ảnh đội, trình độ đội (nhãn), trạng thái đội, số thành viên đang hoạt động, thời điểm mời, và người đại diện đội (`[cần xác nhận Q-14]`: đội trưởng hiện tại hay người đã gửi lời mời). Thứ tự, phân trang: `[cần xác nhận Q-15]` | Khi xem lời mời của tôi |
| BR-T18 | Chấp nhận/từ chối từ danh sách tuân theo UC-T03 và quy tắc hiện hành (đội bị khoá → chưa xử lý được, lời mời vẫn chờ; đội đã giải tán → "Đội đã giải tán"). Lời mời đã xử lý không còn trong danh sách | Khi chấp nhận/từ chối từ danh sách |

### 1.4. Đội của tôi

| Rule ID | Mô tả | Khi nào áp dụng |
|---|---|---|
| BR-T19 | "Đội của tôi" là **một** danh sách các đội mà người xem đang là **thành viên đang hoạt động** (đội trưởng hoặc thành viên thường), gồm đội **đang hoạt động** và đội **bị admin khoá**. Không gồm: đội đã rời, đội đã giải tán (mọi thành viên đã rời — USER-002 BR-T10), đội mới chỉ có lời mời đang chờ. Lọc theo trạng thái đội: `[cần xác nhận Q-16]` | Khi xem đội của tôi |
| BR-T20 | Mỗi đội kèm: tên đội, ảnh đội, trình độ đội (nhãn), **trạng thái đội** (đang hoạt động / bị khoá), **vai trò của tôi** (đội trưởng / thành viên), thời điểm tôi tham gia đội, tên đội trưởng hiện tại, số thành viên đang hoạt động. Thứ tự, phân trang: `[cần xác nhận Q-16]` | Khi xem đội của tôi |
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
| BC-U04 | USER-003 không thêm thông tin lưu trữ mới; mọi kết quả được suy ra từ dữ liệu sẵn có (có thể đổi nếu Q-14 chọn "người đã gửi lời mời" — xem `Data_Model_Conceptual.md`) |

## 5. Authorization Rules (bổ sung SYSTEM-001 §5)

| Actor | Có thể | Không thể |
|---|---|---|
| User | Xem danh mục khu vực (`[cần xác nhận Q-03]`); xem lời mời đang chờ của mình; xem đội mình đang tham gia | Xem lời mời / đội của người khác qua "Lời mời của tôi" / "Đội của tôi"; thấy số điện thoại người khác; thêm/sửa/xoá danh mục khu vực |
| Team Captain | Tìm người dùng để mời vào đội (`[cần xác nhận Q-04]`); lọc các đội mình làm đội trưởng | Tạo lời mời chỉ bằng thao tác tìm (phải qua UC-T02) |
| Admin | Như User khi tra cứu cho chính mình | Thêm/sửa/xoá danh mục khu vực; xem "Lời mời của tôi" / "Đội của tôi" thay người khác |

## 6. Danh sách [cần xác nhận]

Q-01 … Q-16 — chi tiết `Acceptance_Criteria.md` §5.
