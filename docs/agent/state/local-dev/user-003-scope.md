# USER-003 — câu đã chốt (SRS)

> Nhánh `feature/USER-003-user-discovery` (WIP rebase lên `d6f1324` ngày 2026-11-17 → `364c1a4`; SYSTEM-001 v1.12).
> Câu hỏi gốc: `docs/agent/business-spec/USER-003/Acceptance_Criteria.md` §5.

| Câu | Chốt | Ngày |
|---|---|---|
| Q-01 | A — USER-003 chỉ xem danh mục đang có; nạp đủ toàn quốc là việc dữ liệu riêng, điều kiện trước khi phát hành thật | 2026-11-17 |
| Q-02 | A — sắp theo tên (tiếng Việt), không tìm theo tên ở server; app tự lọc | 2026-11-17 |
| Q-03 | A — cần đăng nhập | 2026-11-17 |
| Q-04 | C — tìm trong ngữ cảnh 1 đội: người tìm là đội trưởng đội đó, đội đang hoạt động | 2026-11-17 |
| Q-05 | A — người đã là thành viên / đang có lời mời chờ từ đội vẫn hiện, kèm nhãn, không cho mời | 2026-11-17 |
| Q-06 | B — nhận `+84…`, `84…`, khoảng trắng/chấm/gạch; quy về 10 chữ số bắt đầu bằng 0 rồi so khớp chính xác | 2026-11-17 |
| Q-07 | A — một ô; chuỗi dạng số điện thoại → tìm SĐT chính xác, còn lại → tìm theo tên (tên toàn số không tìm được theo tên — chấp nhận) | 2026-11-17 |
| Q-08 | ~~A — chứa ở bất kỳ vị trí~~ → **sửa khi thiết kế (D2 = C3)**: mỗi từ nhập khớp đầu một từ trong tên, không phân biệt hoa/thường/dấu | 2026-11-17 |
| Q-09 | A — tối đa 20 gợi ý, không phân trang; tên bắt đầu bằng chuỗi nhập xếp trước, rồi A→Z; nhiều hơn → nhắc nhập thêm | 2026-11-17 |
| Q-10 | A — tên hiển thị + ảnh đại diện | 2026-11-17 |
| Q-11 | **B** — loại cả chính người tìm và tài khoản Admin (khác khuyến nghị) | 2026-11-17 |
| Q-12 | A — không giới hạn riêng; dùng giới hạn chung (Q-04 = C) | 2026-11-17 |
| Q-13 | A — hiện, kèm "Đội đang tạm ngưng — chưa thể chấp nhận/từ chối" | 2026-11-17 |
| Q-14 | A — đội trưởng hiện tại của đội | 2026-11-17 |
| Q-15 | A — mới nhất trước, phân trang, có tổng số lời mời đang chờ | 2026-11-17 |
| Q-16 | Thứ tự A (đội trưởng trước, rồi ngày tham gia mới nhất trước), phân trang A (có), lọc trạng thái A (không) | 2026-11-17 |

Đã chốt đủ Q-01..Q-16 (2026-11-17). Đã phản ánh vào SRS USER-003 + SYSTEM-001 v1.12 (chưa commit).

## Việc sửa kèm khi viết lại SRS (đã làm 2026-11-17; mục 3c giữ nguyên — vẫn đúng)
- `USER-003/Overview.md:59` "Thông báo — ngoài phạm vi như USER-001/USER-002" → NOTIFICATION-001 đã có (đã merge #68); cân nhắc trỏ sang NOTIFICATION-001.
- USER-001 §6 #1 đã đóng (người được mời lấy id từ `referenceId` của thông báo `TEAM_INVITATION`, rủi ro chấp nhận) → Overview "Nguồn yêu cầu" trỏ USER-001 §6 mục 1–4 cần cập nhật.
- `USER-003/Use_Cases.md:125` (3c "chấp nhận/từ chối từ thông báo") — đối chiếu với NOTIFICATION-001.

## Câu thiết kế đã chốt (solution-architect, 2026-11-17)

| Câu | Chốt |
|---|---|
| D1 | A — ngoại lệ không phân trang có chặn trên: `GET /provinces`, `GET /provinces/{id}/wards` (danh mục), tìm người tối đa 20 (`{items, hasMore}`); ghi ngoại lệ vào `non-functional-standards.md` §2 (nhánh chore) |
| D2 | B theo cách **C3** — migration **V11**: hàm `IMMUTABLE` bỏ dấu (`translate(lower(...))`, không cần `unaccent`/`pg_trgm`) → `to_tsvector('simple', …)` + GIN index trên `"user".display_name`; truy vấn `@@ to_tsquery('simple', 'w1:* & w2:*')` (không `LIKE`); BE chuẩn hoá chuỗi nhập, chỉ giữ chữ/số trước khi dựng tsquery (tránh lỗi cú pháp → 500). Đã thử trên DB dev (bảng tạm 50.002 tên): "an:*" dùng Bitmap Index Scan, 0,05 ms. Hệ quả: SRS Q-08 đổi sang "khớp đầu từ"; code USER-003 phải đợi code V5→V10 (Flyway không out-of-order). `pg_bigm` không có trên Neon |
| D3 | A — mã lỗi mới `USER_004` 404 không tìm thấy tỉnh/thành, `USER_005` 400 SĐT không hợp lệ, `USER_006` 400 tên < 2 ký tự |
| D4 | A — `GET /api/v1/teams/{teamId}/invitation-candidates?q=` |

Còn lại theo đề xuất phạm vi: thư mục `docs/agent/design/USER-003/`; `GET /profile/invitations`, `GET /profile/teams?role=CAPTAIN` (offset §7.1); sắp tên `COLLATE "vi-VN-x-icu"`; convention sửa trên nhánh `chore/agent-framework-user-003-conventions`.

## Sau khi viết thiết kế (2026-11-17)
- D5 = A: ký tự không phải chữ/số = dấu cách ở cả tên lẫn chuỗi nhập (`ng.an` → `ng an`; "Thị-Ổi" → `thi oi`); ngưỡng 2 = tổng chữ/số sau chuẩn hoá. SRS BR-U10 (a), UC-U05 2a đã sửa.
- D6 = B: không tách đợt — code cả USER-003 sau khi V5–V10 đã code (V11 đi kèm).
- Thiết kế: chuẩn hoá chuỗi nhập bằng chính hàm SQL `user_name_fold` (một nguồn bảng dấu, +1 truy vấn → 2.3 tên = 4 = ngưỡng); field `myRole` → `role` (trùng tên query `role`).
- Tiếp: `architect-reviewer` (USER-003 v1.0 + USER-001 v4.3 + USER-002 v3.1 + ADR-0015 Sửa đổi). Chưa commit.

## Sau architect-reviewer vòng 1 (0C/3M/6m, 2026-11-17)
- M1: đổi `TeamBriefResponse` (trùng REPUTATION-001) → `MyInvitationTeamResponse`.
- M2 = A: chấp nhận chi phí tìm tên khi nhiều người khớp (reviewer đo bảng tạm 200k tên: `nguyen:*` 80k dòng 485 ms) cho MVP + `statement_timeout` + mốc xem lại (~50.000 user) + test dữ liệu lệch.
- M3 = B: USER-003 tự giới hạn 2.3: 30 lần/phút/người, tính chung SĐT + tên + mọi đội. SRS đã sửa: BR-U15, Q-12, UC-U05 1b, AC-U05-09, NFR §3.
- MINOR 1–6 theo khuyến nghị (5 = ghi CCD §10 đồng bộ VENUE-001/NOTIFICATION-001; 6 = ADR `Proposed` chiến lược tìm theo tên).

## Sau architect-reviewer vòng 2 (0C/0M/4m, 2026-11-17) — thiết kế sẵn sàng cho developer (code sau V5–V10)
- Timeout tìm tên (CCD #10) = A: giữ `500 SYS_005`, không đổi convention.
- MINOR 1 ĐÃ SỬA: `acquire` gọi ở `TeamController` trước service (ngoài transaction → request 429 không mượn kết nối DB). CCD §3, §4.2; PF sequence + §3.3.
- MINOR 2 ĐÃ SỬA: rủi ro chấp nhận nhiều tài khoản vượt giới hạn theo userId (CCD §4.5, #7; hướng sau: IP / gateway).
- MINOR 4 ĐÃ SỬA: SRS UC-U05 1b "quá" → "đủ 30 lần".
- **MINOR 3 CHƯA SỬA (user chọn để sau, 2026-11-17):** CCD §9 #9 (`Component_Class_Design.md` ~dòng 361) ghi phương án thay thế khi `@QueryHints` timeout không qua được Neon là "thêm 1 câu SQL, vượt ngưỡng" — chưa đủ: còn cách không tốn SQL mỗi request là `statement_timeout` ở cấp role/database (`ALTER ROLE … SET statement_timeout`) hoặc Hikari `connectionInitSql` (áp cho mọi truy vấn → đặt rộng hơn, vd 5 giây). Sửa câu này khi làm tới #9 / trước khi code 2.3.
- Chưa commit. Tiếp: user quyết commit SRS + design trên nhánh này; rồi nhánh chore convention (CCD §10).
