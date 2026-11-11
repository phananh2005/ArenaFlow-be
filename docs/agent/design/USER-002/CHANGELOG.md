# CHANGELOG — USER-002 Giải tán đội & quy tắc xoá tài khoản đội trưởng

## [v2.0] — 2026-11-11

**Type:** MAJOR (đổi shape response lịch sử thành viên Admin: thêm field `isUserDeleted`; `displayName` của user đã xoá tài khoản đổi từ `"Người dùng đã xóa"` sang tên thật) · **Tóm tắt:** Admin xem tên thật của thành viên đã xoá tài khoản kèm cờ để gắn nhãn "đã xoá"; ảnh vẫn ẩn.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | `API_Spec.md` §1, §2.4, §5.3 | Đổi | User đã xoá tài khoản: `displayName` = `"Người dùng đã xóa"` → tên thật + `isUserDeleted = true`; `avatarUrl` vẫn `null` | Admin cần nhận diện thành viên trong lịch sử và phân biệt tài khoản đã xoá (ADR-0002, ngoại lệ 2026-11-11) | Màn lịch sử thành viên Admin: hiện nhãn "đã xoá" khi `isUserDeleted = true`; không dựa vào chuỗi `"Người dùng đã xóa"` |
| 2 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Khớp hợp đồng mới | Không |
| 3 | Header tài liệu thiết kế USER-002 | Ghi nhận | v1.8 → v2.0 | Đồng bộ phiên bản | Không |

### Ảnh hưởng
- Chỉ endpoint lịch sử thành viên Admin đổi. Các endpoint khác (danh sách thành viên, lịch sử đội trưởng, người giải tán…) vẫn trả `"Người dùng đã xóa"` và ảnh `null`.
- SRS SYSTEM-001 v1.6 cập nhật BC-U02, UC-U03, AC-U03.

## [v1.8] — 2026-11-10

**Type:** MINOR (không đổi endpoint, method, field, kiểu, status, mã lỗi, message) · **Tóm tắt:** Viết lại `API_Spec.md` để FE đọc độc lập, không đổi hợp đồng.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | `API_Spec.md` §2 | Sửa | Bảng endpoint dạng delta → thêm cột Loại (Mới / Đổi), Dùng khi, Ai gọi được; endpoint mới (2.1 giải tán, 2.4 lịch sử thành viên Admin) có bảng field + JSON request/response; endpoint USER-001 bị đổi chỉ nêu phần FE thấy thay đổi và trỏ `USER-001/API_Spec.md` | FE gọi được API chỉ với tài liệu này |
| 2 | `API_Spec.md` §2.1, §2.4, §2.5, §2.7 | Thêm | — → bảng lỗi "Khi nào / FE nên làm gì" | FE biết cách xử lý từng mã |
| 3 | `API_Spec.md` §2.1a | Sửa | Thứ tự kiểm tra kèm giải thích nội bộ → bảng "nhiều lỗi cùng xảy ra thì nhận mã nào"; đội vừa bị giải tán trong lúc request đang chạy → nhận `TEAM_016` | Giữ phần FE dùng được |
| 4 | `API_Spec.md` §3 (mới) | Thêm | — → bảng hành động theo trạng thái đội (`ACTIVE` / `INACTIVE` / `DISBANDED` × đội trưởng / thành viên / người được mời / Admin) | FE biết nút nào hiện |
| 5 | `API_Spec.md` §2.8 (mới), §5 | Thêm | — → với đội `DISBANDED`: lịch sử đội trưởng vẫn đầy đủ, danh sách thành viên rỗng, `memberCount = 0`, `captainId`/`captainName` = `null`; bảng field Lịch sử thành viên; bảng enum | Ghi rõ hành vi đã có, không đổi hợp đồng |
| 6 | `API_Spec.md` §6 | Thêm `[cần xác nhận]` | — → chưa có endpoint "các đội tôi đang làm đội trưởng" (để dẫn người dùng khi nhận `USER_003`) | Chức năng riêng, chưa làm |
| 7 | `API_Spec.md` toàn bộ | Xoá | Chi tiết cài đặt phía BE → chỉ còn mô tả theo góc người gọi API | Tài liệu dành cho FE |
| 8 | `API_Spec.md` §4, §5 | Đổi chỗ | Mã lỗi giữ ở §4, kiểu dữ liệu ở §5 | Giữ số mục §2.1a, §2.4, §2.5, §4 mà tài liệu khác đang trỏ tới |
| 9 | Header tài liệu thiết kế USER-002; tài liệu nội bộ BE | Ghi nhận | v1.7 → v1.8; cập nhật tài liệu nội bộ BE, không ảnh hưởng FE | Đồng bộ phiên bản; giữ phần giải thích nội bộ đã gỡ khỏi `API_Spec.md` |

### Ảnh hưởng
- Hợp đồng API, nghiệp vụ, SRS: không đổi. FE không phải sửa phần gọi API.

## [v1.7] — 2026-11-09

**Type:** MINOR (không đổi endpoint, DTO, mã lỗi, số truy vấn) · **Tóm tắt:** Disband ghi captain/team bằng `save`, Hibernate tự flush một lần trước bulk Q5 (thay 2 lần `saveAndFlush`); transfer ghi lúc commit.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | `Processing_Flow.md` §2 sequence, §3 bảng disband, §4.1 Q3/Q4/Q5 + double-submit, §5 bảng race double-submit | Sửa | Q3/Q4 `saveAndFlush` (2 lần flush) → `save`; Hibernate tự flush một lần trước native Q5, thứ tự `UPDATE team` → `UPDATE team_member` captain | Giảm 1 lần flush/dirty-check; đã kiểm 2026-11-08: 6 câu SQL, test race disband PASS, không `40P01` |
| 2 | `Processing_Flow.md` §4.1 kết quả R2 | Sửa | "transfer flush row captain cũ" → "transfer ghi row captain cũ (lúc commit)" | Transfer dùng `saveAll` (USER-001 v3.6 #1) |
| 3 | `Processing_Flow.md` §5 giả định không deadlock R2 | Sửa | Thêm thứ tự ghi mới của transfer (`INSERT` history → captain cũ → captain mới) và disband (`UPDATE team` → captain cũ → các row khác); transfer không `UPDATE` row `team`; thêm phạm vi đã xét (invite, admin, accept/leave, xoá tài khoản) | Thứ tự khoá giữa các row `team_member` không đổi; test R2 vẫn fail nếu có `40P01` |
| 4 | `Processing_Flow.md` §5 bảng race "Disband vs admin lock" | Sửa | `markInactive`/`reactivate` `saveAndFlush` → ghi entity qua `save` | Code đổi theo quy tắc flush |
| 5 | `Component_Class_Design.md` §4.1 `disbandTeam`, §5.2 quy tắc ghi | Sửa | `saveAndFlush` → `save` + auto-flush trước `disbandRemaining` | Như #1 |
| 6 | Header `API_Spec.md`, `Processing_Flow.md`, `Component_Class_Design.md` | Ghi nhận | v1.6 → v1.7 | design-standards §10 |

### Ảnh hưởng
- Hành vi, contract, DB, nghiệp vụ, SRS: không đổi. Disband vẫn 6 câu SQL (`NfsWriteQueryCountTest`).

## [v1.6] — 2026-11-07

**Type:** MINOR (chỉ đổi tên class; không đổi endpoint, DTO, mã lỗi, luồng) · **Tóm tắt:** Đồng bộ USER-001 v3.5 — `OptimisticLockRetrier` → `ConcurrencyConflictRetrier`.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | `Component_Class_Design.md` (§1, §2 thành phần tái dùng, §4.1 `inviteMember`) | Sửa | `OptimisticLockRetrier` → `ConcurrencyConflictRetrier` | USER-001 v3.5 #1 |
| 2 | `Processing_Flow.md` (§1, §2 participant sequence) | Sửa | như #1 | USER-001 v3.5 #1 |
| 3 | Header `API_Spec.md`, `Processing_Flow.md`, `Component_Class_Design.md` | Ghi nhận | v1.5 → v1.6 | design-standards §10 |

### Ảnh hưởng
- Hành vi, contract, DB, nghiệp vụ, SRS: không đổi.

## [v1.5] — 2026-11-06

**Type:** MINOR (không đổi endpoint, DTO hay mã lỗi) · **Tóm tắt:** Đồng bộ với USER-001 v3.4 — retrier thử lại cả deadlock (`40P01`), hết lượt → `TEAM_014` thay vì 500.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | `Processing_Flow.md` §1 (tổng quan retrier) | Sửa | mô tả quy tắc retry (chỉ optimistic conflict) → trỏ USER-001 `Processing_Flow.md` §5 | Một nguồn cho quy tắc retry (design-standards §8) |
| 2 | `Processing_Flow.md` §5 (giả định không deadlock R2) | Sửa | "deadlock `40P01` → không được retry → 500" → "bên bị huỷ được retrier thử lại; lặp lại đủ 3 lần → `TEAM_014`"; "Test R2 … phát hiện deadlock → dừng, báo" → test R2 fail khi output có `40P01` (retrier che deadlock, log là tín hiệu duy nhất) | USER-001 v3.4 #1; giữ khả năng phát hiện đảo thứ tự khoá |
| 3 | Header `API_Spec.md`, `Processing_Flow.md`, `Component_Class_Design.md` | Ghi nhận | v1.4 → v1.5 | design-standards §10 |
| 4 | `Processing_Flow.md` §4.2 (`deleteAccount`) | Sửa | "luồng này không tự sinh optimistic conflict" → thêm: có thể bị huỷ do deadlock → retrier thử lại, hết lượt `TEAM_014` | USER-001 v3.4 #1 |
| 5 | `Processing_Flow.md` §7 (bảng lỗi) | Sửa | "Hết retry optimistic conflict" → "Hết retry (optimistic conflict hoặc deadlock)" | Như #4 |
| 6 | `API_Spec.md` §4 (bảng lỗi) | Sửa | "Xung đột optimistic lock hết retry" → "… hoặc deadlock hết retry" | Như #4 |

### Ảnh hưởng
- Disband/transfer/xoá tài khoản gặp deadlock: 500 → tự thử lại, hết lượt `409 TEAM_014` (đã khai báo: USER-002 §2.1, USER-001 §2.7/§2.15). Contract, DB, nghiệp vụ, SRS: không đổi.
- Kiểm chứng: `TeamDisbandRaceTest` R2 assert không có `40P01` (mutation đảo thứ tự flush → R2 fail).

## [v1.4] — 2026-11-06

**Type:** MINOR (chỉ cập nhật bảng đồng bộ tài liệu; không đổi endpoint, DTO, mã lỗi, luồng) · **Tóm tắt:** ADR-0003 đã có mục `Sửa đổi` về phạm vi "không deadlock" và liên kết USER-002/ADR-0005 (PR #34) — đánh dấu hoàn tất ở CCD §10; ghi rõ 2 hàng §10 còn chưa làm; header chỉ giữ baseline + phiên bản hiện tại.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | `Component_Class_Design.md` §10 (hàng ADR-0003) | Sửa | "Một phần — còn ghi chú deadlock + liên kết USER-002/ADR-0005, qua MR" → ✅ PR #19 + PR #34 | PR #34 đã thêm `Sửa đổi 2026-11-06 — Phạm vi của "không deadlock"` và liên kết ở "Liên quan" |
| 2 | `Component_Class_Design.md` §10 (hàng `docs/agent/CLAUDE.md` §2, `api-convention.md` §2.1) | Sửa | cột cuối chỉ ghi tên nhánh → "Chưa — qua `chore/agent-framework-*`" | Hai việc này thực tế chưa làm; tránh hiểu nhầm là đã xong |
| 3 | Header `API_Spec.md`, `Processing_Flow.md`, `Component_Class_Design.md` | Sửa | Ô "Tác giả / Ngày / Phiên bản" liệt kê mọi mốc kèm mô tả → baseline v1.0 + phiên bản hiện tại v1.4 + trỏ `CHANGELOG.md` | design-standards §8 (một thông tin = một nơi); §10 chỉ yêu cầu version header khớp entry mới nhất |

### Ảnh hưởng
- API contract, DB schema, nghiệp vụ, SRS: không đổi. Mục "Còn mở" của v1.3 đã xong.
- §10 còn 2 hàng chưa làm: `docs/agent/CLAUDE.md` §2 (điều hướng USER-002), `api-convention.md` §2.1 (ngoại lệ endpoint hành động) — qua `chore/agent-framework-*`.

## [v1.3] — 2026-11-06

**Type:** MINOR (chỉ cập nhật trạng thái ADR được trỏ tới; không đổi endpoint, DTO, mã lỗi, luồng) · **Tóm tắt:** ADR-0005 và ADR-0007 đã `Accepted` (PR #19) — bỏ nhãn "Proposed" khi trỏ tới chúng; cập nhật bảng đồng bộ CCD §10 theo những gì PR #19 đã làm với ADR-0002/0003.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | `API_Spec.md` §2.5 (xoá tài khoản captain) | Sửa | "Quy tắc của ADR-0005 (Proposed)" → "Quy tắc của ADR-0005" | ADR-0005 `Accepted` |
| 2 | `Processing_Flow.md` header "Quy ước áp dụng" | Sửa | "ADR-0007 (Proposed — ngoại lệ khoá bi quan …)" → "ADR-0007 (ngoại lệ khoá bi quan …)" | ADR-0007 `Accepted` |
| 3 | `Processing_Flow.md` §4.2 (khoá bi quan có phạm vi) | Sửa | "ADR-0007 Proposed — ngoại lệ của ADR-0003; chốt qua MR" → "ADR-0007 — ngoại lệ của ADR-0003" | Như #2 |
| 4 | `Component_Class_Design.md` §10 (hàng ADR-0002) | Sửa | "Khi ADR-0005 `Accepted` → `Superseded (một phần)`" · "qua MR" → ✅ PR #19: giữ `Accepted` + mục `Sửa đổi 2026-11-06` | PR #19 thay một phần §Quyết định #4 bằng mục `Sửa đổi` (`decisions/README.md` không có hàng cho thay thế một phần); tránh agent sau đổi trạng thái ADR-0002 sai |
| 5 | `Component_Class_Design.md` §10 (hàng ADR-0003) | Sửa | "qua MR" → "Một phần": đã có Sửa đổi + liên kết ADR-0007; còn ghi chú deadlock khoá nhiều dòng + liên kết USER-002/ADR-0005 | Phản ánh đúng phần PR #19 đã làm |
| 6 | Header `API_Spec.md`, `Processing_Flow.md`, `Component_Class_Design.md` | Ghi nhận | v1.2 → v1.3 | design-standards §10 (version khớp header) |

### Ảnh hưởng
- API contract, DB schema, nghiệp vụ, SRS: không đổi.
- Còn mở: phần còn lại của ADR-0003 (xem #5) — sửa qua MR riêng.

## [v1.2] — 2026-11-06

**Type:** MINOR (không đổi endpoint, shape DTO hay mã lỗi; đổi cơ chế xử lý đồng thời của `deleteAccount`) · **Tóm tắt:** `deleteAccount` chuyển sang khoá bi quan có phạm vi (ADR-0007, ngoại lệ của ADR-0003): khoá membership ACTIVE/PENDING của user (`PESSIMISTIC_WRITE`, theo id) → kiểm captain trên dữ liệu đã khoá → một bulk UPDATE gộp → soft delete; 5 → 3 truy vấn.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do |
|---|---|---|---|---|
| 1 | `Processing_Flow.md` §4.2 | Sửa | Q1 kiểm captain → Q2 `leaveAllActive` → Q3 kiểm lại (R1) → Q4 `rejectAllPending` → Q5 soft delete → Q1 khoá `PESSIMISTIC_WRITE` (PostgreSQL `FOR NO KEY UPDATE`) → kiểm captain trên dữ liệu đã khoá → Q2 `leaveAndRejectAllOpen` (CASE + `GREATEST`) → Q3 soft delete | Luồng cũ hở race delete vs accept của chính user (G1: user đã xoá còn ACTIVE — tái hiện bằng test 2026-11-06) và chỉ an toàn với race chuyển quyền đi nhờ thứ tự Q1 trước Q2 (chứng minh bằng mutation); khó suy luận, dễ hỏng khi sửa |
| 2 | `Processing_Flow.md` §3 | Sửa | `DELETE /profile` 5 → 3 truy vấn (W6 ✅) | Như #1 |
| 3 | `Processing_Flow.md` §5 | Sửa/Thêm | Đoạn mở đầu thêm ngoại lệ ADR-0007; dòng R1 đổi cơ chế sang khoá Q1; **thêm** "chuyển quyền đi" và "accept lời mời của chính user"; ghi chú "Kiểm chứng bắt buộc" cập nhật (delete = 3, test khoá thật) | Như #1 |
| 4 | `Component_Class_Design.md` §4.3, §5.2 | Sửa | `existsActiveCaptainByUserId`, `leaveAllActive` (role MEMBER) → `findByUserIdAndStatusInOrderByIdAsc` (`@Lock(PESSIMISTIC_WRITE)`), `leaveAndRejectAllOpen`; bỏ 3 method cũ | Như #1 |
| 5 | Header `API_Spec.md` (không đổi nội dung), `Processing_Flow.md`, `Component_Class_Design.md`; quy ước áp dụng `Processing_Flow.md` | Ghi nhận | v1.1 → v1.2; thêm ADR-0007 | design-standards §10 (version mới nhất khớp header — theo tiền lệ USER-001 v1.2) |

### Ảnh hưởng
- API contract (`204`, `422 USER_003`), DB schema (không migration), nghiệp vụ: không đổi.
- Đồng bộ USER-001 → v2.1 (sequence, bảng 2.7, repository).
- Kiểm chứng (2026-11-06, DB dev): `TeamDisbandRaceTest` thêm `deleteAccountVersusAcceptOwnInvitation…` (Red thật trên luồng cũ ở vòng 0) và `deleteAccountVersusTransferAway…` (độ nhạy bằng mutation bỏ Q1 cũ — fail vòng 6); sau sửa, cả hai cùng R1: mọi vòng PASS, có đủ hai kết cục cho phép (phân bố thay đổi theo timing — ví dụ một lần chạy: accept `{OK|OK=7, TEAM_006|OK=3}`, chuyển quyền đi `{OK|OK=6, OK|USER_003=4}`, R1 `{OK|USER_003=5, TEAM_007|OK=5}`); `AccountDeletionLockIntegrationTest` (NOWAIT → 55P03 khi đang khoá, lấy được sau commit); `NfsWriteQueryCountTest` delete = 3; full suite 212 test BUILD SUCCESS.
- Rủi ro còn lại (ADR-0007 "Hệ quả"): lời mời được INSERT sau Q1 không bị khoá (user đã xoá có thể còn lời mời PENDING — lỗ có từ trước); deadlock hiếm khi ≥ 2 xoá tài khoản cùng lúc với ≥ 2 transfer/disband trên các đội có chung thành viên → PostgreSQL huỷ một bên (`40P01`) → 500 ở endpoint bên đó (retrier không bắt) [Inference]; không cấu hình `lock_timeout`.

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
