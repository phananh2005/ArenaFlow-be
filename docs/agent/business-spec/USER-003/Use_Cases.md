# Use Cases — Tra cứu cho người dùng (USER-003)

> Quy tắc được trích bằng mã (`BR-U*`, `BR-T*`) — chi tiết trong `Business_Rules.md`. Mã UC tiếp nối SYSTEM-001 (UC-U01..U03) và USER-002 (UC-T06).
> `[cần xác nhận Q-xx]` — chi tiết và phương án: `Acceptance_Criteria.md` §5.

## UC-U04: Xem danh mục khu vực (mới)

**Actor:** User

**Preconditions:**
- User đã đăng nhập `[cần xác nhận Q-03]`.

**Main Flow:**
1. User chọn ô "Khu vực" khi sửa hồ sơ (UC-U02).
2. Hệ thống hiển thị danh sách tỉnh/thành (BR-U07, thứ tự: `[cần xác nhận Q-02]`).
3. User chọn một tỉnh/thành.
4. Hệ thống hiển thị danh sách phường/xã thuộc tỉnh/thành đó (BR-U07).
5. User chọn một phường/xã.
6. Khu vực vừa chọn (phường/xã kèm tên tỉnh/thành) được điền vào hồ sơ đang sửa; hồ sơ được lưu theo UC-U02 (BR-U05).

**Postconditions:**
- Danh mục không thay đổi (chỉ xem — BR-U08).
- Hồ sơ chỉ thay đổi khi User lưu theo UC-U02.

**Alternative Flows:**

**1a. Hồ sơ đã có khu vực:**
- 1a1. Hệ thống hiển thị khu vực hiện tại (phường/xã, tỉnh/thành); User có thể chọn lại từ bước 2.

**2a. User tìm tỉnh/thành hoặc phường/xã theo tên:** `[cần xác nhận Q-02]`.

**3a. User đổi tỉnh/thành sau khi đã chọn phường/xã:**
- 3a1. Lựa chọn phường/xã trước đó bị bỏ; quay về bước 4 với tỉnh/thành mới.

**Exception Flows:**

**4a. Tỉnh/thành không còn trong danh mục** (danh sách User đang xem đã cũ):
- 4a1. Hệ thống báo "Không tìm thấy tỉnh/thành". Quay về bước 2.

**4b. Tỉnh/thành chưa có phường/xã nào trong danh mục:**
- 4b1. Hệ thống hiển thị danh sách trống kèm "Chưa có dữ liệu phường/xã cho tỉnh/thành này". Quay về bước 2.

**6a. User lưu hồ sơ với khu vực không phải phường/xã hoặc không còn trong danh mục:**
- 6a1. Hệ thống báo "Khu vực không hợp lệ" (UC-U02 5a, BR-U05). Quay về bước 2.

---

## UC-U05: Tìm người dùng để mời vào đội (mới)

**Actor:** Đội trưởng (phạm vi quyền: `[cần xác nhận Q-04]`)

**Preconditions:**
- Đội trưởng đã đăng nhập.
- Điều kiện quyền và trạng thái đội: `[cần xác nhận Q-04]`.

**Main Flow:**
1. Đội trưởng chọn "Mời thành viên" ở đội của mình (UC-T02).
2. Đội trưởng nhập số điện thoại hoặc tên hiển thị của người cần mời (cách nhận biết: `[cần xác nhận Q-07]`).
3. Hệ thống tìm:
   - Theo **số điện thoại**: so khớp chính xác, cho ra **tối đa một** người (BR-U10, BR-U13).
   - Theo **tên hiển thị**: khi đã nhập **ít nhất 2 ký tự**, cho ra danh sách gợi ý (BR-U10, so khớp: `[cần xác nhận Q-08]`, giới hạn và thứ tự: `[cần xác nhận Q-09]`).
4. Hệ thống hiển thị kết quả: **không** có số điện thoại (BR-U11); thông tin mỗi người: `[cần xác nhận Q-10]`; không gồm người đã xoá tài khoản (BR-U12).
5. Đội trưởng chọn một người trong kết quả.
6. Tiếp tục UC-T02 (gửi lời mời cho người đã chọn).

**Postconditions:**
- Tìm kiếm không thay đổi dữ liệu. Lời mời chỉ được tạo ở UC-T02 (BR-U14).

**Alternative Flows:**

**2a. Tên hiển thị nhập vào có ít hơn 2 ký tự** (không tính khoảng trắng đầu/cuối):
- 2a1. Hệ thống không tìm, nhắc "Nhập ít nhất 2 ký tự". Quay về bước 2.

**2b. Số điện thoại nhập theo dạng khác dạng 10 chữ số bắt đầu bằng 0** (vd có `+84`, khoảng trắng, dấu chấm): `[cần xác nhận Q-06]`.

**4a. Người tìm thấy đã là thành viên đang hoạt động hoặc đang có lời mời chờ từ đội này:** `[cần xác nhận Q-05]`.

**4b. Kết quả có chính người đang tìm, hoặc tài khoản Admin:** `[cần xác nhận Q-11]`.

**Exception Flows:**

**4c. Không có người nào phù hợp** (gồm số điện thoại chưa đăng ký hoặc thuộc người đã xoá tài khoản):
- 4c1. Hệ thống báo "Không tìm thấy người dùng". Quay về bước 2.

**6a. Người đã chọn xoá tài khoản, hoặc vừa trở thành thành viên / vừa được mời, trong khoảng giữa lúc tìm và lúc gửi lời mời:**
- 6a1. Xử lý theo ngoại lệ hiện có của UC-T02 ("Không tìm thấy người dùng" / "Người này đã là thành viên hoặc đã được mời") (BR-U14).

---

## UC-T07: Xem lời mời của tôi (mới)

**Actor:** User (người được mời)

**Preconditions:**
- User đã đăng nhập.

**Main Flow:**
1. User mở "Lời mời của tôi".
2. Hệ thống hiển thị các lời mời **đang chờ** gửi tới chính User (BR-T15, BR-T16), mỗi lời mời kèm thông tin đội để quyết định (BR-T17). Thứ tự và phân trang: `[cần xác nhận Q-15]`.
3. User chọn "Chấp nhận" hoặc "Từ chối" một lời mời.
4. Tiếp tục UC-T03 (SYSTEM-001) cho lời mời đó.
5. Lời mời đã xử lý không còn trong danh sách (BR-T18). Nếu chấp nhận, đội xuất hiện trong "Đội của tôi" (UC-T08).

**Postconditions:**
- Xem danh sách không thay đổi dữ liệu; thay đổi chỉ xảy ra ở UC-T03.

**Alternative Flows:**

**2a. User không có lời mời đang chờ:**
- 2a1. Hệ thống hiển thị "Bạn chưa có lời mời nào". Use case kết thúc.

**2b. User muốn xem thêm về đội trước khi quyết định:**
- 2b1. User mở chi tiết đội từ lời mời (chức năng xem chi tiết đội đã có). Quay về bước 2.

**2c. Lời mời của đội đang bị admin khoá:** hiển thị hay không: `[cần xác nhận Q-13]`.

**Exception Flows:**

**3a. Đội đang bị admin khoá:**
- 3a1. Hệ thống báo "Đội đang tạm ngưng hoạt động"; lời mời vẫn chờ, User thử lại sau (hành vi hiện hành).

**3b. Đội vừa bị giải tán:**
- 3b1. Hệ thống báo "Đội đã giải tán"; lời mời đã tự bị từ chối (USER-002 BR-T10) và không còn trong danh sách.

**3c. Lời mời đã được xử lý ở nơi khác** (vd User đã chấp nhận/từ chối từ thông báo):
- 3c1. Hệ thống báo lời mời đã được xử lý; danh sách được tải lại.

---

## UC-T08: Xem đội của tôi (mới)

**Actor:** User (gồm cả Đội trưởng)

**Preconditions:**
- User đã đăng nhập.

**Main Flow:**
1. User mở "Đội của tôi".
2. Hệ thống hiển thị **một** danh sách các đội User đang là thành viên đang hoạt động, gồm đội đang hoạt động và đội bị admin khoá (BR-T19), mỗi đội kèm vai trò của User và trạng thái đội (BR-T20). Thứ tự và phân trang: `[cần xác nhận Q-16]`.
3. (Tuỳ chọn) User bật lọc "Chỉ đội tôi làm đội trưởng" (BR-T21).
4. Hệ thống chỉ hiển thị các đội User đang là đội trưởng.
5. User chọn một đội để mở chi tiết đội (chức năng đã có).

**Postconditions:**
- Không thay đổi dữ liệu.

**Alternative Flows:**

**1a. User vừa bị chặn xoá tài khoản vì còn là đội trưởng** (UC-U03 4a, USER-002):
- 1a1. User mở "Đội của tôi" với lọc "Chỉ đội tôi làm đội trưởng" (BR-T21).
- 1a2. Với từng đội: chuyển quyền (UC-T04) hoặc giải tán (UC-T06). Đội đã giải tán biến khỏi danh sách; đội đã chuyển quyền chuyển sang vai trò thành viên và biến khỏi danh sách đang lọc.
- 1a3. Khi danh sách lọc trống, User xoá tài khoản lại (UC-U03).

**2a. User không tham gia đội nào:**
- 2a1. Hệ thống hiển thị "Bạn chưa tham gia đội nào". Use case kết thúc.

**4a. Lọc "Chỉ đội tôi làm đội trưởng" không có đội nào:**
- 4a1. Hệ thống hiển thị "Bạn không làm đội trưởng đội nào".

**Exception Flows:**

**5a. Đội đã thay đổi giữa lúc xem và lúc thao tác** (vd đội vừa bị khoá, User vừa bị chuyển quyền):
- 5a1. Thao tác tiếp theo trên đội tuân theo quy tắc hiện hành của thao tác đó; User tải lại danh sách để thấy trạng thái mới.

---

## Use Case Diagram

```mermaid
graph LR
    User -->|Chọn khu vực hồ sơ| UC_U04[UC-U04: Xem danh mục khu vực]
    Captain[Đội trưởng] -->|Trước khi mời| UC_U05[UC-U05: Tìm người dùng]
    UC_U05 -->|Chọn người| UC_T02[UC-T02: Mời thành viên]
    User -->|Lời mời đang chờ| UC_T07[UC-T07: Lời mời của tôi]
    UC_T07 -->|Chấp nhận/Từ chối| UC_T03[UC-T03]
    User -->|Đội đang tham gia| UC_T08[UC-T08: Đội của tôi]
    UC_U03[UC-U03: Xoá tài khoản] -.->|Bị chặn: lọc đội tôi làm đội trưởng| UC_T08
```

---

## Danh sách [cần xác nhận]

Q-02, Q-03, Q-04, Q-05, Q-06, Q-07, Q-08, Q-09, Q-10, Q-11, Q-13, Q-15, Q-16 — chi tiết `Acceptance_Criteria.md` §5.
