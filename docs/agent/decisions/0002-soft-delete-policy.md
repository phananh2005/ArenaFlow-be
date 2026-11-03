# ADR-0002: Xoá tài khoản là soft-delete, giữ nguyên hồ sơ và lịch sử, che danh tính khi hiển thị

| | |
|---|---|
| **Trạng thái** | Accepted |
| **Ngày** | 2026-11-02 |
| **Người quyết định** | Product Owner (chốt chính sách) · solution-architect (đề xuất cách thực hiện) |
| **Liên quan** | ADR-0001 · UC-U03, BR-T06, AC-U03 · Use_Cases "Quyết định bổ sung" #5 (không hard-delete) · `docs/agent/design/USER-001/` |

## Bối cảnh (Context)
- UC-U03/AC-U03: tài khoản soft-delete; lịch sử hiển thị "Người dùng đã xóa"; điểm/đánh giá của đội khác giữ nguyên; tài khoản "có thể khôi phục sau này"; captain duy nhất xoá → đội INACTIVE.
- Spec chốt: giữ dữ liệu mãi, không hard-delete.
- Schema V1: hồ sơ nằm ngay trong bảng `"user"` (có `deleted_at`); `team_member`, `team_captain_history`, `rating`… tham chiếu `user.id`.
- `convention-backend.md` §10 gợi ý `@SQLDelete` + `@Where` trên entity.

## Quyết định (Decision)
Chúng tôi soft-delete bằng `"user".deleted_at` và **không** xoá/ẩn danh dữ liệu hồ sơ trong DB.

1. **Đăng nhập/refresh:** tài khoản đã xoá → `403 AUTH_003`. Khoá provider (`oauth_account`) vẫn giữ → không đăng ký lại được bằng cùng email/số điện thoại.
2. **Hiển thị:** API che danh tính user đã xoá ở mọi chỗ hiển thị người khác (`"Người dùng đã xóa"`, avatar `null`) — che ở tầng truy vấn/mapper, không ghi đè DB.
3. **Membership khi xoá** *(đề xuất, chờ xác nhận)*: `ACTIVE` → `LEFT`, lời mời `PENDING` → `REJECTED`; lịch sử captain giữ nguyên.
4. **Đội:** captain còn thành viên ACTIVE khác → chặn xoá (`422 USER_003`); captain duy nhất → đội `INACTIVE`. Admin kích hoạt lại đội qua `PATCH /api/v1/admin/teams/{teamId}/reactivate` (yêu cầu đội còn captain ACTIVE).
5. **Lọc tường minh:** query tự thêm `deleted_at IS NULL` khi cần; **không** dùng `@Where`/`@SQLRestriction` trên `User`, vì lịch sử phải join được user đã xoá.
6. **Khôi phục tài khoản:** ngoài phạm vi USER-001 (dữ liệu được giữ nên có thể làm sau).

**Phương án đã loại**
- Hard-delete: trái quyết định "không hard-delete", phá lịch sử/rating.
- Ẩn danh hoá dữ liệu trong DB: không khôi phục được (trái UC-U03 "có thể khôi phục").
- `@Where` trên entity: ẩn user đã xoá khỏi mọi JPQL → lịch sử/thành viên mất tên/ID.

## Hệ quả (Consequences)

**Tích cực**
- Toàn vẹn FK, lịch sử đầy đủ, có đường khôi phục.

**Tiêu cực / đánh đổi**
- Dữ liệu cá nhân còn trong DB vô thời hạn — rủi ro quyền riêng tư nếu mở thị trường có luật "right to be forgotten".
- Mọi query đọc user phải nhớ điều kiện `deleted_at` (dễ sót; cần test).
- User đã xoá không đăng ký lại được bằng cùng tài khoản provider cho đến khi có chức năng khôi phục.

**Cần theo dõi**
- Yêu cầu pháp lý về xoá dữ liệu → ADR về purge/ẩn danh.
- Khi làm chức năng khôi phục: quyết định có khôi phục membership/đội hay không.
