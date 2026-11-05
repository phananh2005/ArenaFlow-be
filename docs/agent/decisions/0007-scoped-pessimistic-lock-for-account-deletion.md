# ADR-0007: Khoá bi quan có phạm vi (`PESSIMISTIC_WRITE`) cho xoá tài khoản

| | |
|---|---|
| **Trạng thái** | Proposed |
| **Ngày** | 2026-11-06 |
| **Người quyết định** | Product Owner (chốt hướng khoá bi quan cho `deleteAccount`, 2026-11-06) · solution-architect (đề xuất cách thực hiện) |
| **Liên quan** | [ADR-0003](0003-optimistic-locking-strategy.md) (đây là ngoại lệ có phạm vi, không thay thế) · [ADR-0005](0005-captain-must-hand-over-before-account-deletion.md) · `docs/agent/design/USER-002/Processing_Flow.md` §4.2, §5 |

## Bối cảnh (Context)

- `DELETE /api/v1/profile` (`ProfileServiceImpl.deleteAccount`) phải giữ bất biến trên **nhiều dòng** `team_member` của cùng một user: user còn là captain ACTIVE → từ chối (`USER_003`, ADR-0005); xoá thành công → user không còn row ACTIVE/PENDING nào.
- Các bước ghi của hàm là bulk UPDATE (`leaveAllActive`, `rejectAllPending`, `softDelete`): chỉ **tăng** `version`, không **kiểm** `version`. `@Version` (ADR-0003) vì vậy không bảo vệ chính hàm này; nó chỉ khiến luồng ghi entity song song (transfer, accept) phát hiện xung đột.
- ADR-0003 loại `SELECT … FOR UPDATE`, nên thiết kế hiện tại (5 truy vấn) dựa vào chuỗi "kiểm (Q1) → UPDATE có điều kiện `role = 'MEMBER'` (Q2) → kiểm lại (Q3) → UPDATE (Q4) → soft delete (Q5)" và vào cách PostgreSQL READ COMMITTED đánh giá lại `WHERE` sau khi chờ khoá dòng.
- Phân tích và test ngày 2026-11-06 cho thấy cách này đúng với R1 nhưng **hở G1 (và có thể G2), đồng thời khó suy luận, dễ hỏng khi sửa**:
  - Q1 và Q3 chặn hai race ngược chiều nhau (Q1: user đang là captain và chuyển quyền **đi**; Q3: user vừa **nhận** quyền captain — R1). Race "chuyển quyền đi" trước đó chưa có test; **đã kiểm bằng mutation** (bỏ Q1 → `TeamDisbandRaceTest#deleteAccountVersusTransferAway…` fail: user đã xoá còn MEMBER ACTIVE).
  - Accept lời mời của chính user chạy song song: commit giữa Q2 và Q4 → user đã xoá còn ACTIVE (**G1 — đã tái hiện** bằng `TeamDisbandRaceTest#deleteAccountVersusAcceptOwnInvitation…`, vòng 0); commit giữa thời điểm lấy `now` và Q2 → `left_at < joined_at` vi phạm CHECK → 500 (G2 — [Inference], chưa tái hiện riêng).
- Đặc điểm thao tác: hiếm (mỗi user tối đa một lần thành công), tranh chấp thấp, transaction ngắn, không gọi I/O ngoài.

## Quyết định (Decision)

Chúng tôi dùng **khoá dòng bi quan có phạm vi** cho `deleteAccount`, các luồng khác giữ nguyên ADR-0003:

1. **Q1 — khoá:** nạp mọi row `team_member` của user có `status IN ('ACTIVE','PENDING')`, `ORDER BY id`, với `PESSIMISTIC_WRITE` (Spring Data `@Lock`; Hibernate 7.2 trên PostgreSQL sinh `FOR NO KEY UPDATE` — vẫn chặn mọi UPDATE khác trên các row này). Thứ tự `id` cố định để các lần khoá nhiều dòng của `deleteAccount` luôn cùng thứ tự.
2. **Kiểm trên dữ liệu đã khoá:** có row `ACTIVE` + `CAPTAIN` → `422 USER_003`, rollback, không ghi gì.
3. **Q2 — một bulk UPDATE gộp:** ACTIVE MEMBER → `LEFT` (`left_at = GREATEST(:now, COALESCE(joined_at, invited_at))`), PENDING → `REJECTED`, `version = version + 1`, giữ điều kiện không đụng row CAPTAIN.
4. **Q3 — soft delete user** như hiện tại.

Tổng 3 truy vấn (W6 ✅). Luồng transfer/accept/leave/disband không đổi: khi cần cập nhật một row đang bị khoá, chúng chờ; sau khi `deleteAccount` commit, `version` đã lệch → retry theo ADR-0003 → lỗi nghiệp vụ. (Invite chèn row mới nên không chờ khoá của Q1 — xem rủi ro còn lại.)

**Phạm vi ngoại lệ:** chỉ áp cho thao tác (a) giữ bất biến trên nhiều dòng của cùng một chủ thể, (b) hiếm và tranh chấp thấp, (c) transaction ngắn không I/O ngoài; luôn khoá theo thứ tự ổn định. Áp cho thao tác khác cần ADR mới hoặc mục sửa đổi của ADR này.

**Phương án đã loại**
- Giữ optimistic + gộp Q2/Q4 thành một câu + giữ Q1/Q3 + `GREATEST`: đúng (đóng G1/G2) nhưng vẫn phụ thuộc chuỗi kiểm–ghi–kiểm lại và hành vi đánh giá lại `WHERE` của READ COMMITTED; mỗi race mới cần suy luận riêng.
- Optimistic theo `@Version` từng dòng (đọc entity → sửa → `saveAll`): không cần kiểm lại nhưng số truy vấn `2 + N`, vi phạm `non-functional-standards.md` §1 (số truy vấn phải là hằng số).
- `SERIALIZABLE` cho `deleteAccount`: PostgreSQL tự phát hiện xung đột đọc–ghi, nhưng cần retry lỗi `40001` (retrier chưa hỗ trợ) và khó dự đoán tỷ lệ huỷ.
- Khoá dòng `user` (`FOR UPDATE` ở xoá tài khoản, `FOR SHARE` ở invite/accept/transfer): đóng cả lời mời được INSERT sau Q1, nhưng phải sửa mọi luồng ghi `team_member` theo user — vượt phạm vi thay đổi này.
- Advisory lock: thêm cơ chế khoá mới chỉ cho một thao tác.

## Hệ quả (Consequences)

**Tích cực**
- Kiểm và ghi trên cùng trạng thái đã khoá: bỏ bước kiểm lại (Q3) và lập luận về race theo thứ tự câu lệnh; R1, race chuyển quyền đi, accept song song (G1/G2) cùng được tuần tự hoá.
- Số truy vấn giảm 5 → 3.

**Tiêu cực / đánh đổi**
- Lệch ADR-0003 ở một thao tác: người đọc phải biết ngoại lệ này (ghi trong design USER-002 §4.2/§5).
- Trong lúc xoá, các thao tác ghi khác trên row của chính user (accept, transfer cho/khỏi user, leave, disband đội có user) phải chờ đến khi transaction xoá kết thúc.
- **Deadlock vẫn có thể xảy ra (hiếm)** [Inference — từ đọc code, chưa tái hiện]: transfer khoá captain cũ rồi người nhận (thứ tự nạp entity), disband khoá captain rồi bulk mọi row của đội (thứ tự không xác định), còn `deleteAccount` khoá theo `id`. Vòng chờ cần ≥ 2 xoá tài khoản cùng lúc với ≥ 2 transfer/disband trên các đội có chung thành viên (ví dụ: U, Y cùng ở đội A, B; transfer B U→Y, delete Y, transfer A Y→U, delete U khép vòng). PostgreSQL phát hiện và huỷ một bên (`40P01`) → **500 ở endpoint của bên bị huỷ** (có thể là transfer/disband, không chỉ `DELETE /profile`) vì retrier chỉ bắt `ObjectOptimisticLockingFailureException`. Lớp rủi ro này không hoàn toàn mới: bulk `leaveAllActive` cũ cũng khoá nhiều row của user không theo thứ tự.
- Repo **không cấu hình `lock_timeout`** → bên chờ khoá chờ không giới hạn cho tới khi transaction xoá kết thúc (transaction ngắn nên chấp nhận).
- Vẫn hở lời mời được INSERT sau Q1 (invite không chờ khoá của Q1):
  - Ca đơn giản: invite đọc user trước khi xoá commit, INSERT commit sau Q2 → user đã xoá còn lời mời PENDING; `guardInvitation` không kiểm user đã xoá nên trong thời gian access token còn hiệu lực (tối đa 1h) user vẫn có thể accept. Lỗ này có từ trước, không phải hồi quy.
  - Ca dài: chuỗi invite → accept → transfer hoàn tất trong lúc xoá → user đã xoá thành captain.
  - Chấp nhận vì hiếm; đóng triệt để cần khoá dòng `user` (phương án đã loại ở trên).

**Cần theo dõi**
- Log xuất hiện `40P01` (deadlock) ở `DELETE /profile`, `POST …/captain/transfer` hoặc `POST …/disband`, hoặc request chờ khoá lâu → xem lại thứ tự khoá, cân nhắc `lock_timeout` và cho retrier bắt `PessimisticLockingFailureException`.
- Thêm luồng ghi nhiều row `team_member` theo user → kiểm thứ tự khoá với ADR này.
- Cần chặn hẳn lời mời phantom → xem lại phương án khoá dòng `user`.
- Khi ADR này `Accepted`: thêm hàng `Sửa đổi` + mục `## Sửa đổi` vào ADR-0003 trỏ tới ADR-0007 (ADR-0003 vẫn hiệu lực cho mọi thao tác khác).
