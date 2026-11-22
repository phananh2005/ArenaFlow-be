# Tiến độ — VENUE-001 sửa (điện thoại/website chỉ Admin, danh sách user chỉ ACTIVE, danh sách Admin)

- Nhánh: `feature/VENUE-001-user-admin-venue-rules` (từ `origin/main` 6fe6285).
- Tiến độ: **95%** — code, review code (4 lượt), SRS v1.14, design v6.0, architect-review (2 lượt) xong; đã commit `feat(VENUE-001)` 31ba005 + `docs(VENUE-001)` (2026-11-23, chưa push). Còn test cần DB ở giai đoạn tích hợp.
- Test 2026-11-22: common 12/12, domain 65/65, application 206 (2 skip có sẵn) PASS; `api` test-compile PASS.
- Chưa chạy (cần DB, giai đoạn tích hợp trên Neon branch riêng): `VenueControllerTest`, `AdminVenueControllerTest` (đã sửa theo hành vi mới).
- Việc treo: SYSTEM-002 (code + state trong `stash@{0}`), câu chéo mã Q2 (khoá cột uy tín), các câu hỏi nhánh song song; nhánh `chore/agent-framework-javadoc-config-comments` làm sau.
