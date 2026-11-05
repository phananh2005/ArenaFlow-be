# Overview — Giải tán đội & quy tắc xoá tài khoản đội trưởng (USER-002)

| Mục | Giá trị |
|---|---|
| Mã chức năng | `USER-002` |
| Domain | Team Management, User Management |
| Spec gốc | `docs/agent/business-spec/SYSTEM-001/` (UC-U03, UC-T01..T05, BR-T06, BC-T02) |
| Ngày / Phiên bản | 2026-11-04 · v1.0 |
| Nguồn quyết định | Code review PR #8 (MINOR #1: lý do khoá của admin bị ghi đè khi đội tự INACTIVE); user chọn mô hình "chặn xoá khi còn sở hữu" (tham khảo Discord, Slack) — 2026-11-04 |

## 1. Tổng quan

Thêm chức năng **đội trưởng giải tán đội** và đổi quy tắc **đội trưởng xoá tài khoản**: thay vì hệ thống tự chuyển đội sang INACTIVE khi đội trưởng duy nhất xoá tài khoản, đội trưởng phải **chuyển quyền hoặc giải tán đội trước** rồi mới được xoá tài khoản.

## 2. Objectives (Mục tiêu)

- Đội trưởng chủ động kết thúc đội của mình khi không còn hoạt động.
- Mọi đội luôn có đội trưởng thật cho tới khi đội kết thúc — không còn đội "mồ côi" do đội trưởng xoá tài khoản.
- Phân biệt rõ hai cách đội dừng hoạt động: **admin khoá** (có thể mở lại) và **đội trưởng giải tán** (vĩnh viễn).
- Giữ nguyên lý do khoá của admin (không bị hệ thống ghi đè).

## 3. Actors

| Actor | Vai trò trong chức năng |
|---|---|
| Đội trưởng (Team Captain) | Giải tán đội; khi xoá tài khoản phải xử lý trước các đội mình làm đội trưởng |
| User | Xoá tài khoản (thành viên thường: không đổi so với hiện tại) |
| Thành viên đội (Team Member) | Bị chuyển sang "đã rời đội" khi đội giải tán |
| Người được mời | Lời mời đang chờ bị từ chối khi đội giải tán |
| Admin | Không kích hoạt lại được đội đã giải tán |

## 4. In Scope

- UC-T06 (mới): Đội trưởng giải tán đội — xem `Use_Cases.md`.
- UC-U03 (sửa): Xoá tài khoản — quy tắc mới cho đội trưởng.
- UC-T05 / kích hoạt lại (sửa): admin không kích hoạt lại đội đã giải tán.
- Hiển thị đội đã giải tán trong danh sách / chi tiết.

## 5. Out of Scope

- Gửi thông báo cho thành viên / người được mời khi đội giải tán (giống SYSTEM-001: notification ngoài phạm vi USER-001).
- Khôi phục đội đã giải tán.
- Xử lý kèo/trận của đội khi giải tán — domain Match chưa triển khai (xem `[cần xác nhận]` #1).
- Admin giải tán đội (admin chỉ khoá đội như UC-T05).

## 6. Assumptions

- Một đội có đúng một đội trưởng đang hoạt động (BR-T03, BR-T05 của SYSTEM-001).
- Lịch sử thành viên và lịch sử đội trưởng không bao giờ bị xoá (BC-T04, BC-T05 của SYSTEM-001).

## 7. Constraints

- Không xoá cứng đội, thành viên hay lịch sử (BC-T04 của SYSTEM-001).

## 8. Success Metrics

- Không còn đội nào ở trạng thái "không có đội trưởng" do đội trưởng xoá tài khoản.
- Lý do khoá của admin không bị thay đổi bởi thao tác của đội trưởng.

## 9. Danh sách [cần xác nhận]

Xem `Acceptance_Criteria.md` §5 (một nơi duy nhất).
