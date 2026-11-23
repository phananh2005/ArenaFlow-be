---
dev: local-dev
saved: 2026-11-23 01:04
task: SYSTEM-002 — chuẩn hoá ngày giờ LocalDateTime / TIMESTAMP giờ Việt Nam (V6)
status: in_progress
---

## Đang làm dở
- Task: SYSTEM-002 (nhánh `feature/SYSTEM-002-local-datetime`, mọi thay đổi **chưa commit**, đã rebase lên `origin/main` 5e17d63).
- Bước hiện tại: `code-reviewer` lượt 5 xong (0C / 1 MAJOR / 2 MINOR) — **chờ user trả lời câu 1/2: M1 áp quy tắc Javadoc (#82) cho SYSTEM-002 tới đâu** (A đủ theo quy tắc ~90 chỗ — đề xuất / B chỉ code mới `GlobalExceptionHandler.path`, `ClockConfigTest` / C = B + method sửa có logic). Câu 2/2 dự kiến: getter của projection interface có miễn Javadoc không.
- m13 (`api/src/test/.../venue/AdminVenueControllerTest.java:91` `NOW()` trần → `(now() AT TIME ZONE 'Asia/Ho_Chi_Minh')`) và m14 (bảng "Lệch thiết kế" dòng #8 ghi `jvmArguments` → `systemPropertyVariables`) đã đề xuất sửa luôn, **chưa sửa**, chưa được user xác nhận riêng.

## File đang sửa (git status lúc handoff)
- 127 mục: toàn bộ code SYSTEM-002 (22 entity, 9 projection, repository, DTO, service, mapper, `GlobalExceptionHandler`, 2 scheduler, test các module) — `git status --short` để xem đủ.
- File mới chưa track: `api/.../config/ClockConfig.java`, `JsonNumberConfig.java`, `JsonTimeConfig.java`; `api/.../db/migration/V6__vietnam_local_datetime.sql`; `common/.../time/` (`AppTime` + test); test `ClockConfigTest`, `JsonMapperIntegrationTest`, `JsonTestSupport`, `NoExplicitTimeZoneTest`, `RequestBodyReadErrorTest`, `LocalDatetimeProjectionRepositoryTest`, `migration/V6TimestampColumns`, `VietnamLocalDatetimeMigrationScriptTest`, `VietnamLocalDatetimeMigrationTest`, `EntityTimeTypesTest`; state `pending-review-questions.md`.
- Sửa gần nhất (2026-11-23): root `pom.xml` (surefire `argLine`), `api/pom.xml` (spring-boot `systemPropertyVariables user.timezone`), `AppTimeTest`, `ErrorResponseTest`, `NoExplicitTimeZoneTest`, Javadoc 3 method bean.

## Quyết định / context quan trọng
- 2026-11-22/23 VENUE-001 sửa: PR #81 đã merge (`c5584d0`) — `VENUE_017` 403 (phone/website chỉ Admin), `VENUE_018` 400 (`GET /venues` chỉ ACTIVE), `GET /api/v1/admin/venues`, `LikePatterns`; SRS v1.14, design v6.0. 3 test DB của #81 chưa chạy (tích hợp). Task VENUE vẫn `in_progress` vì vậy.
- PR #82 đã merge (`5e17d63`): Javadoc bắt buộc mọi class/method `src/main` (miễn Lombok, accessor record), class test có Javadoc; comment file cấu hình (§2.6). Áp code mới/sửa; backfill trên `refactor/javadoc-backfill` sau khi các nhánh song song merge (gồm comment `application.properties` + `.env.example` hiện có). `.env.dev` đã comment.
- SYSTEM-002 lượt 3: M1 → A (sửa `AppTime.ZONE` ở 5 worktree khi tích hợp); m6/m8/m12 ghim `user.timezone` ở surefire + spring-boot plugin; m9/m11 `NoExplicitTimeZoneTest`.
- Rebase 6 worktree: user chọn **B** — để sau khi SYSTEM-002 merge (mô phỏng conflict: state + `ErrorCode`/`ErrorCodeTest`; MATCH-001 thêm `VenueServiceImpl` — giữ `GeoBoundingBox`, gọi `searchNearbyActive`).
- Quy tắc mới từ #80: sửa chức năng đã có → xong vòng lặp design (design → `architect-reviewer` → sửa code → `code-reviewer`) **rồi mới** cập nhật SRS.
- Chi tiết finding / quyết định: `docs/agent/state/local-dev/pending-review-questions.md`; bảng "Lệch thiết kế" (9 dòng) trong `current-task.md`.

## Blockers
- Test cần DB (SYSTEM-002 + 3 test của #81) **chỉ chạy trên Neon branch riêng ở giai đoạn tích hợp** — cần xin phép user; NEVER chạy Flyway/test DB trên DB dev chung (`.env.dev`) trước khi SYSTEM-002 merge. V1 không chạy được trên schema trống — hỏi user khi tích hợp.
- Push nhánh SYSTEM-002 sau này cần `--force-with-lease` (remote còn commit docs cũ đã squash vào #77) — chỉ khi user yêu cầu.

## Việc tiếp theo
- [ ] Nhận câu trả lời M1 (A/B/C) → hỏi câu 2/2 (projection getter) nếu cần → sửa M1 + m13 + m14 → chạy unit test → `code-reviewer` phần sửa.
- [ ] Vòng lặp design SYSTEM-002: cập nhật ADR-0017, CCD §2.2/§2.3/§2.5/§6.1/§8 một lần theo bảng "Lệch thiết kế"; convention §6.2 + `docs/agent/README.md:132` trên nhánh `chore/` → `architect-reviewer` → SRS nếu ảnh hưởng.
- [ ] Commit SYSTEM-002 (khi user yêu cầu) → tích hợp: Neon branch, chạy V6→V12, test DB; rebase 6 worktree theo quy tắc M1.
- [ ] Câu chéo mã Q2 (MATCH-001 khoá toàn bộ cột uy tín `updatable = false`) — chưa trả lời.
- [ ] Duyệt câu hỏi 6 nhánh song song (USER-003 CRITICAL V12, MATCH-002, REPUTATION-001, MERCENARY-001, NOTIFICATION-001, MATCH-001) — từng câu một.
