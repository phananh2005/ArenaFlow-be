# ADR-0002: Xoá tài khoản là soft-delete, giữ nguyên hồ sơ và lịch sử, che danh tính khi hiển thị

| | |
|---|---|
| **Trạng thái** | Accepted |
| **Ngày** | 2026-11-02 |
| **Người quyết định** | Product Owner (chốt chính sách) · solution-architect (đề xuất cách thực hiện) |
| **Liên quan** | ADR-0001 · UC-U03, BR-T06, AC-U03, BC-U02 · Use_Cases "Quyết định bổ sung" #5 (không hard-delete) · `docs/agent/design/USER-001/`, `docs/agent/design/USER-002/`, `docs/agent/design/VENUE-001/` · §Quyết định #4 thay bởi [ADR-0005](0005-captain-must-hand-over-before-account-deletion.md) |
| **Sửa đổi** | 2026-11-06 — §Quyết định #4 (captain duy nhất xoá tài khoản → đội tự `INACTIVE`) hết hiệu lực, thay bởi ADR-0005 · 2026-11-11 — ngoại lệ §Quyết định #2 cho Admin (mục "Sửa đổi 2026-11-11" cuối file) |

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

## Sửa đổi 2026-11-06 — §Quyết định #4 thay bởi ADR-0005

- [ADR-0005](0005-captain-must-hand-over-before-account-deletion.md) (Accepted) thay §Quyết định #4: captain ACTIVE của bất kỳ đội chưa giải tán nào — kể cả người duy nhất — bị chặn xoá tài khoản (`422 USER_003`); hệ thống không tự chuyển đội sang `INACTIVE`. Đội có trạng thái cuối `DISBANDED`.
- Các quyết định còn lại của ADR này (soft-delete, giữ hồ sơ và lịch sử, che danh tính khi hiển thị, không hard-delete) giữ nguyên.

## Sửa đổi 2026-11-11 — Ngoại lệ hiển thị cho Admin

- **Bối cảnh:** Admin cần nhận diện người đã xoá tài khoản khi rà lịch sử thành viên đội và xử lý report địa điểm; chuỗi `"Người dùng đã xóa"` không phân biệt được các user đã xoá với nhau (user chốt 2026-11-11, SRS SYSTEM-001 BC-U02 v1.6).
- **Ngoại lệ §Quyết định #2**, chỉ áp dụng cho đúng 2 field:
  - USER-002 `API_Spec.md` §2.4 (lịch sử thành viên Admin): `displayName` trả tên thật, kèm `isUserDeleted`.
  - VENUE-001 `API_Spec.md` §2.8–2.9 (report địa điểm Admin): `reporterName` trả tên thật, kèm `isReporterDeleted`.
  - Cờ `true` khi user đã xoá tài khoản; FE dùng để gắn nhãn "đã xoá". Ảnh đại diện của user đã xoá vẫn `null`.
- **Không thuộc ngoại lệ, vẫn che theo §Quyết định #2:** `createdByName` (người tạo địa điểm), `handledByName` (Admin xử lý report), `disbandedByName` (người giải tán đội, kể cả trong danh sách đội Admin), và mọi endpoint khác.
- **Điều kiện:** cả 2 endpoint nằm dưới `/api/v1/admin/**`, chỉ role `ADMIN` gọi được (kiểm ở server, không dựa vào việc ẩn trên UI). Mở rộng ngoại lệ sang field/endpoint khác → sửa đổi mới của ADR này.
- **Hệ quả:** thu hẹp phạm vi §Quyết định #2: phạm vi Admin có 2 field ngoại lệ, nên Admin đọc được tên người đã xoá tài khoản ở 2 field đó. Cộng thêm vào rủi ro quyền riêng tư ở §Hệ quả: khi có ADR purge/ẩn danh, phải xét lại ngoại lệ này.
- Các quyết định còn lại giữ nguyên.
