---
task: Mọi màn Admin hiện tên thật + ảnh thật + cờ đã xoá của user đã xoá tài khoản
updated: 2026-11-12
percent: 100
---

## Đang làm
- Chờ user merge PR #59 (https://github.com/phananh2005/ArenaFlow-be/pull/59). Sau merge: tiếp REPUTATION-001 (nhánh `feature/REPUTATION-001-rating-report-dispute`).

## Đã xong
- Code + test (TDD) — commit `d853e9d`; tài liệu (ADR-0002, SRS SYSTEM-001 v1.7, USER-002 AC, design USER-001 v4.0 / USER-002 v3.0 / VENUE-001 v3.0) — commit `9d585c2`; Javadoc `TeamDetailView` (MINOR code-reviewer) — commit `0d4b00e`.
- architect-reviewer: MAJOR SRS đóng; 3 MINOR đã sửa. code-reviewer toàn nhánh: 0 CRITICAL/MAJOR, 1 MINOR đã sửa.
- Test PASS 2026-11-12: MapperTest 11 (chạy lại sau sửa Javadoc), AdminTeamServiceImplTest 9, AdminTeamControllerTest 6, TeamControllerTest 9, SecurityConfigIntegrationTest 10, TeamServiceIntegrationTest 1, NfsQueryCountTest 2, TeamDisbandRaceTest 10.
- `git diff --check` sạch; publish-spec dry run exit 0. Push nhánh + tạo PR #59.

## Còn lại
- Không (merge do user).
