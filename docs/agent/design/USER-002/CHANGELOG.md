# CHANGELOG — USER-002 Giải tán đội & quy tắc xoá tài khoản đội trưởng

## [v1.1] — 2026-11-05

**Type:** MINOR (không đổi endpoint, shape DTO hay mã lỗi; làm rõ cơ chế để bên thua race nhận đúng `TEAM_016` như v1.0 đã cam kết) · **Tóm tắt:** Đồng bộ thiết kế với code sau implement (`fb6de5f`) theo bảng "Lệch thiết kế".

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | `Component_Class_Design.md` §4.1 | Sửa | `inviteMember`/`leaveTeam` dưới `TeamService` → nằm ở `TeamMembershipService(Impl)` (USER-001) | Code USER-001 đã tách membership service; không di chuyển method |
| 2 | `API_Spec.md` §2.1a, `Processing_Flow.md` §4.1, §5, `Component_Class_Design.md` §4.1, §5.2 | Thêm | Cơ chế race chỉ CAS/retry → thêm `TeamGuard#disbandedOr` + `TeamRepository#findCurrentStatusById`: guard thất bại sau Q1 đọc lại trạng thái đã commit, `DISBANDED` → `TEAM_016`; áp cho `TEAM_002`/`007`/`010`/`013`; `findTeam` private | Test race R2 tái hiện transfer nhận `TEAM_007` thay vì `TEAM_016` (READ COMMITTED); user chọn sửa code (2026-11-05) |
| 3 | `Processing_Flow.md` §5 | Thêm | Bảng race thêm "Leave vs disband", "Admin reactivate vs disband" | Cùng cơ chế #2 |
| 4 | `Component_Class_Design.md` §10 | Sửa | Đánh dấu tài liệu USER-001 và database đã đồng bộ | Hoàn tất mục đồng bộ của nhánh feature |
| 5 | `Component_Class_Design.md` §4.1 | Sửa | Tiêu đề "TeamService" → "TeamService · TeamMembershipService · TeamGuard"; `activeTeam` không còn dùng cho invite | Khớp code (`activeTeamForInvite`) |
| 6 | `Component_Class_Design.md` header | Ghi nhận | Lần sửa đường dẫn package theo ADR-0006 (commit `ad31b61`, 2026-11-05) trước đây không tăng phiên bản; gộp ghi nhận vào v1.1 | Tuân thủ design-standards §10 |

### Ảnh hưởng
- API contract, DB schema, nghiệp vụ: không đổi so với v1.0. Số truy vấn luồng thành công không đổi; nhánh lỗi thêm 1 câu đọc.
- Kiểm chứng: `TeamDisbandRaceTest` (R1, R2, M1 service + HTTP, double-submit service + HTTP, accept, admin lock), `NfsWriteQueryCountTest` (disband 6, delete account 5, invite 6). Hai race "Leave vs disband", "Admin reactivate vs disband" chỉ kiểm bằng unit test (mock `findCurrentStatusById`), chưa có test race thật.
