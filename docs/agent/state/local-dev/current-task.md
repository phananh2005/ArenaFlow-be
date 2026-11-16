---
task: Mọi màn Admin hiện tên thật + ảnh thật + cờ đã xoá của user đã xoá tài khoản
created: 2026-11-12
status: done
---

## Mô tả
User chốt 2026-11-12 (mở rộng PR #58): mọi API `/api/v1/admin/**` KHÔNG che user đã xoá tài khoản — trả tên thật, ảnh thật (nếu còn link), và mỗi field tên có cờ `is…Deleted` đi kèm để FE gắn nhãn "đã xoá". Không làm job xoá ảnh người dùng. Endpoint user thường giữ nguyên quy tắc che.
Nhánh: `feature/USER-002-admin-show-deleted-users` từ `origin/main` (`bbdfc42`). REPUTATION-001 tạm dừng (nhánh `feature/REPUTATION-001-rating-report-dispute`, chưa có thay đổi), làm tiếp sau PR này.
Loại: sửa chức năng đã có → developer (TDD) → cập nhật design + SRS + ADR → architect-reviewer → code-reviewer trước PR.

## Thiết kế liên quan
- USER-001 `API_Spec.md` §1.1, 2.19, 2.20, 2.21 · USER-002 `API_Spec.md` §1, 2.4, 2.6, §5 · VENUE-001 `API_Spec.md` §1, 2.8–2.11, §4.
- ADR-0002 (mục Sửa đổi 2026-11-11 → mở rộng) · SRS SYSTEM-001 BC-U02, UC-U03, AC-U03.

## Definition of Done
- [x] `GET /admin/teams`: `captainName`, `disbandedByName` tên thật + `isCaptainDeleted`, `isDisbandedByDeleted`.
- [x] `PATCH /admin/teams/{id}/mark-inactive`, `/reactivate`: response Admin riêng (tên thật + 2 cờ); API user thường `GET /teams/{id}` vẫn che.
- [x] `GET /admin/teams/{id}/members`: `avatarUrl` thật của user đã xoá (bỏ che ảnh).
- [x] Test hồi quy: endpoint user thường vẫn che tên/ảnh; số truy vấn không tăng.
- [x] Design USER-001/USER-002/VENUE-001, ADR-0002, SRS cập nhật sau test PASS; `architect-reviewer` review.
- [x] Test liên quan PASS với `-Duser.timezone=Asia/Ho_Chi_Minh`.

## Các bước thực hiện (tổng quan)
- [x] 1. Đọc code/design hiện có + NFS §3.
- [x] 2. Ghi kế hoạch chi tiết; chờ user duyệt.
- [x] 3. TDD code USER-001/USER-002 Admin.
- [x] 4. Cập nhật design + ADR + SRS sau test PASS.
- [x] 5. architect-reviewer review lại phần SRS đã chỉnh; chốt finding.
- [x] 6. Test liên quan + code-reviewer; commit/PR khi user yêu cầu (PR #59).

## Lệch thiết kế
| # | Phase | Design | Code thực tế | Lý do | File design cần sửa |
|---|---|---|---|---|---|

## Tái sử dụng
- **DÙNG LẠI:** projection `TeamDetailView`/`TeamSummaryView` đã có `getCaptainDeleted()`, `getDisbandedByDeleted()`; `MemberHistoryView.getUserDeleted()`; `TeamRepository.findDetailById` (cùng truy vấn `TeamService.getTeam` đang dùng); `TeamMapper`, `MembershipMapper`; test `MapperTest`, `AdminTeamControllerTest`. Không thêm truy vấn.
- **MỞ RỘNG:** `AdminTeamSummaryResponse` + 2 cờ; `TeamMapper.toAdminSummary` bỏ `maskName`; `MembershipMapper.toMemberHistory` bỏ `maskAvatar`.
- **MỚI (DTO, không phải util dùng chung):** record `AdminTeamResponse` = field `TeamResponse` + `isCaptainDeleted` + `isDisbandedByDeleted`; method `TeamMapper.toAdminResponse(TeamDetailView)`. Lý do không thêm cờ vào `TeamResponse` dùng chung: tránh đổi API user thường.
- **KHÔNG code VENUE-001:** chưa implement → chỉ sửa design.

## Kế hoạch chi tiết
### A — TDD code (application + api)
1. **Red** `MapperTest`:
   - `toMemberHistory`: user đã xoá có avatar → `avatarUrl` = avatar gốc (hiện null → fail).
   - `toAdminSummary`: captain/người giải tán đã xoá → tên thật + `isCaptainDeleted`/`isDisbandedByDeleted` = true (hiện che + chưa có field → fail biên dịch).
   - `toAdminResponse(TeamDetailView)` mới: tên thật + 2 cờ.
   - Giữ assert hiện có: `toMember`, `toSummary`, `toResponse` (user thường) vẫn che.
2. **Red** `AdminTeamControllerTest`:
   - Test user đã xoá ở lịch sử thành viên: đổi assert `avatarUrl` null → avatar gốc.
   - Danh sách đội Admin, đội giải tán bởi user đã xoá → `disbandedByName` tên thật, `isDisbandedByDeleted` true; cùng đội qua `GET /api/v1/teams` (user thường) → vẫn "Người dùng đã xóa".
   - `mark-inactive` / `reactivate` response có `isCaptainDeleted` = false, `isDisbandedByDeleted` = false (assert key tồn tại).
3. **Green**:
   - `AdminTeamSummaryResponse` + `boolean isCaptainDeleted`, `boolean isDisbandedByDeleted`.
   - Record mới `AdminTeamResponse`.
   - `TeamMapper`: `toAdminSummary` bỏ mask + map 2 cờ; thêm `toAdminResponse`.
   - `MembershipMapper.toMemberHistory`: bỏ `maskAvatar`.
   - `AdminTeamService`/`Impl`: `markInactive`, `reactivate` trả `AdminTeamResponse` qua `teamRepository.findDetailById(teamId).map(teamMapper::toAdminResponse)` thay `teamService.getTeam` (cùng 1 truy vấn đọc lại; tổng số truy vấn không đổi). Kiểm `AdminTeamServiceImplTest` (mock) cập nhật theo.
   - `AdminTeamController`: kiểu trả về.
4. **Refactor** + chạy `MapperTest`, `AdminTeamServiceImplTest`, `AdminTeamControllerTest`, `TeamControllerTest`, `SecurityConfigIntegrationTest`; đếm SQL log cho mark-inactive/reactivate/list (không tăng).

### B — Tài liệu (sau test PASS)
5. ADR-0002: mục `## Sửa đổi 2026-11-12` mở rộng ngoại lệ 2026-11-11 thành mọi `/api/v1/admin/**` (tên + ảnh + cờ), không viết lại §Quyết định; hàng Sửa đổi/Liên quan.
6. SRS SYSTEM-001 v1.7: BC-U02, UC-U03, AC-U03, Overview (REPUTATION-001 sẽ dùng v1.8).
7. USER-001 (MAJOR v4.0): §1.1 quy tắc; 2.19/2.20 response Admin riêng (+2 cờ); 2.21 tên thật + 2 cờ; CHANGELOG + header 3 tài liệu.
8. USER-002 (MAJOR v3.0): §1; 2.4 avatar thật; 2.6 + §5 cờ; CCD; CHANGELOG + header.
9. VENUE-001 (MAJOR v3.0): §1; 2.8–2.9 `handledByName` + `isHandledByDeleted`; 2.10–2.11 response Admin `createdByName` + `isCreatedByDeleted`; CCD; CHANGELOG + header.
10. `publish-spec` dry run; architect-reviewer; trình findings cho user chọn.

### Kiểm chứng
- Integration test cần DB → nạp `.env.dev`. Không đổi schema/migration. Số truy vấn: mark-inactive/reactivate giữ nguyên (thay 1 truy vấn đọc lại bằng 1 truy vấn tương đương); list/history không đổi.
