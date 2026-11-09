# Overview — Tra cứu cho người dùng (USER-003)

| Mục | Giá trị |
|---|---|
| Mã chức năng | `USER-003` |
| Domain | User Management, Team Management |
| Spec gốc | `docs/agent/business-spec/SYSTEM-001/` (UC-U02, UC-U03, UC-T02, UC-T03, BR-U01, BR-U05, BC-U02) · `docs/agent/business-spec/USER-002/` (BR-T06 sửa, BR-T10, BR-T11) |
| Stakeholders | Product Owner, đội FE (phát hiện thiếu chức năng khi viết lại tài liệu cho FE) |
| Tác giả | business-analyst agent |
| Ngày | 2026-11-10 |
| Phiên bản | v1.0 (baseline) |
| Nguồn yêu cầu | Các luồng trong SYSTEM-001 chưa hoàn thành được vì thiếu chức năng tra cứu: chọn khu vực hồ sơ, tìm người để mời, xem lời mời đang chờ, xem đội của mình (ghi nhận trong tài liệu thiết kế USER-001 §6 mục 1–4 và USER-002 §6 mục 1). User chọn gom 4 chức năng vào mã `USER-003` — 2026-11-10 |

## 1. Tổng quan

Bổ sung 4 chức năng **chỉ xem** để người dùng hoàn thành các luồng đã có trong SYSTEM-001:

| # | Chức năng | Phục vụ luồng |
|---|---|---|
| 1 | Danh mục khu vực (tỉnh/thành → phường/xã) | UC-U02 — chọn khu vực hồ sơ |
| 2 | Tìm người dùng để mời vào đội (theo số điện thoại hoặc tên hiển thị) | UC-T02 — mời thành viên |
| 3 | Lời mời của tôi (lời mời đang chờ) | UC-T03 — chấp nhận/từ chối lời mời |
| 4 | Đội của tôi (kèm vai trò, trạng thái đội; lọc đội tôi làm đội trưởng) | UC-U03 (USER-002) — đội trưởng phải chuyển quyền/giải tán trước khi xoá tài khoản |

Không thêm thao tác ghi mới, không thêm thông tin lưu trữ mới (xem `Data_Model_Conceptual.md`).

## 2. Objectives (Mục tiêu)

- Người dùng chọn được khu vực hồ sơ từ danh mục chuẩn, không phải tự nhập.
- Đội trưởng tìm được người cần mời mà không cần biết mã định danh nội bộ; số điện thoại của người khác không bị lộ.
- Người được mời thấy được mọi lời mời đang chờ ở một nơi, không phụ thuộc thông báo.
- Người dùng thấy được các đội mình đang tham gia; đội trưởng biết ngay những đội cần chuyển quyền/giải tán trước khi xoá tài khoản.

## 3. Actors

| Actor | Vai trò trong chức năng |
|---|---|
| User | Xem danh mục khu vực; xem lời mời đang chờ gửi tới mình; xem đội mình đang tham gia |
| Đội trưởng (Team Captain) | Tìm người dùng để mời vào đội (phạm vi quyền: `[cần xác nhận Q-04]`); lọc các đội mình làm đội trưởng |
| Người dùng được tìm thấy | Xuất hiện trong kết quả tìm (không lộ số điện thoại) |
| Admin | Không có vai trò riêng; dùng như User khi tra cứu cho chính mình. Không có chức năng quản trị danh mục khu vực |

## 4. In Scope

- UC-U04: Xem danh mục khu vực — `Use_Cases.md`.
- UC-U05: Tìm người dùng để mời vào đội.
- UC-T07: Xem lời mời của tôi.
- UC-T08: Xem đội của tôi.
- Sửa câu chữ UC-U02, UC-T02, UC-T03, AC-T02 của SYSTEM-001 cho khớp (SYSTEM-001 v1.4).

## 5. Out of Scope

- Thêm/sửa/xoá danh mục khu vực (danh mục chỉ đọc). Bổ sung dữ liệu danh mục: `[cần xác nhận Q-01]`.
- Lịch sử lời mời (đã chấp nhận/từ chối) và danh sách lời mời đội đã gửi đi.
- Đội trưởng huỷ lời mời đã gửi.
- Lịch sử các đội tôi đã rời; đội đã giải tán trong "Đội của tôi".
- Xem hồ sơ chi tiết của người khác (SYSTEM-001 §5.1: User không xem hồ sơ người khác).
- Tìm người dùng cho mục đích khác ngoài mời vào đội (vd tìm bạn, nhắn tin).
- Thông báo (notification) — ngoài phạm vi như USER-001/USER-002.

## 6. Assumptions

- Người dùng đã đăng nhập khi dùng các chức năng tìm người, lời mời của tôi, đội của tôi. Danh mục khu vực: `[cần xác nhận Q-03]`.
- Lời mời không hết hạn; một người có thể có lời mời đang chờ từ nhiều đội cùng lúc (hành vi hiện hành).
- Một người có thể là đội trưởng của nhiều đội cùng lúc (SYSTEM-001 Data_Model §9 #8).
- Người đăng ký bằng Google/Facebook có thể không có số điện thoại; người đó chỉ tìm được theo tên hiển thị.

## 7. Constraints

- Kết quả tìm người dùng **không** hiển thị số điện thoại của bất kỳ ai (BR-U11).
- Người đã xoá tài khoản không xuất hiện trong kết quả tìm (BR-U12).
- Các quy tắc mời / chấp nhận / từ chối / xoá tài khoản hiện có giữ nguyên; USER-003 chỉ thêm cách xem.

## 8. Success Metrics

- Đội trưởng mời được một người mà không cần biết mã định danh của người đó.
- Người được mời chấp nhận/từ chối được lời mời mà không cần thông báo.
- Đội trưởng bị chặn xoá tài khoản thấy ngay danh sách đội cần xử lý, không phải tự mở từng đội.

## 9. Danh sách [cần xác nhận]

Chi tiết và phương án: `Acceptance_Criteria.md` §5 (một nơi duy nhất). Mã: Q-01 … Q-16.
