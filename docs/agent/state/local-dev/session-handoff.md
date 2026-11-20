---
dev: local-dev
saved: 2026-11-20 01:30
task: VENUE-001 — quản lý địa điểm sân (V5)
status: in_progress
---

## Đang làm dở
- Task: VENUE-001, ~95%. Phase 1–6 code xong; Phase 7 (final verify + đồng bộ tài liệu + review) đang làm.
- Bước hiện tại: `code-reviewer` đã chạy → CHƯA ĐẠT (0 CRITICAL, 1 MAJOR, 10 MINOR). Đang đi **từng MINOR một** với user trước khi sửa (memory: hỏi từng câu; trình finding cho user chọn).
  - **M1 (MAJOR) — user ĐÃ CHỌN sửa:** live test xoá lô 100 `public_ids[]` không tồn tại trên cloud-dev (1 request Admin API, không xoá asset thật); bị từ chối → giảm `DELETE_BATCH`. Chưa làm.
  - **m1 (thời gian `+07:00` thay vì `Z`) — đang thảo luận, CHƯA chốt.** User đã chốt: **JVM giữ `Asia/Ho_Chi_Minh`**; muốn "toàn bộ dự án dùng UTC Z". Câu hỏi đang chờ (đã bị ngắt 1 lần — **hỏi lại**): chuẩn hoá đến mức nào?
    1. Chỉ output API (đề xuất): mọi thời điểm trong JSON response + `error.timestamp` ra `Z`, không đụng entity.
    2. Output + giá trị nội bộ: thêm đổi mọi `OffsetDateTime.now()` trong entity/service sang `now(ZoneOffset.UTC)` (~30 entity).
  - m2–m10: chưa đi qua chi tiết (đã giải thích tóm tắt một lượt; user muốn đi sâu từng mục).
- `progress.md` mục "Đang làm" còn ghi "Chờ user cho bắt đầu Phase 7" — đã cũ, cập nhật khi tiếp tục.
- Nhánh: `feature/VENUE-001-venue-management` (rebase lên `2204f39`, PR #74). Chưa có commit riêng; toàn bộ việc VENUE-001 chưa commit.

## File đang sửa (git status lúc handoff)
- Không đổi so với handoff trước: 27 Modified, 72 Untracked (chưa sửa code trong phiên này).
- Modified: `.env.example`; `api/pom.xml`; `api/src/main/resources/application.properties`; `api` security resolver + test; `CommittedDataIntegrationTest`; `application` `ConcurrencyConflictRetrier` + test; `common` `ErrorCode`; `domain` entity `Venue`/`VenueImage`/`VenueReport`; `docs/agent/design/VENUE-001/*` (v4.3), `docs/agent/design/USER-001/*` (v4.4), `docs/database/{schema.dbml,schema-design.md,data-model.md}`; state `docs/agent/state/local-dev/{current-task,progress,session-handoff,tech-debt}.md`.
- Untracked: migration `V5__venue_management.sql`; `api/.../venue/`, `api/.../scheduler/`, `api/.../config/SchedulingConfig.java`; DTO/mapper/service/impl/event/support/validation venue trong `application`; repository/projection venue trong `domain`; test unit + integration/race/NFS/migration. Xem `git status --short`.

## Quyết định / context quan trọng
- **Thời gian — dữ kiện đã xác minh trong phiên:**
  - DB: mọi thời điểm là `TIMESTAMPTZ` (V1/V4/V5); `date_of_birth` là `DATE`. Neon dev session mặc định `TimeZone = GMT` (`SELECT current_setting('TimeZone')`, 2026-11-19 18:02 UTC).
  - Thiết kế: `VENUE-001/API_Spec.md:27` quy định ISO-8601 UTC `Z`; CCD VENUE-001:351 (projection `Instant` → mapper UTC); CCD USER-001:249/:365 ghi lệch `OffsetDateTime.now()` ra `+07:00` + hướng tech-debt "nên trả `Z`"; `USER-001/API_Spec.md:29` hiện cho phép offset bất kỳ; tech-debt #7 cùng loại (USER-001).
  - m1 vị trí: `VenueServiceImpl.java:84` (2.1), `VenueMapper.java:57` (`toImage`), `VenueMapper.java:61` (`toReport`). Phương án m1 đã trình: A1 qua `MapperSupport.toUtc` (đề xuất; cần `reuse-scan` nếu thêm overload `toUtc(OffsetDateTime)`), A2 entity `now(UTC)`, A3 DTO `Instant`, B Jackson toàn cục (chưa kiểm hoạt động với Jackson 3), C tech-debt.
  - Rà toàn repo: request DTO chỉ có `ProfileUpdateRequest.dateOfBirth` (`LocalDate`); chưa request nào gửi thời điểm. `OffsetDateTime.now()` ở ~30 `@PrePersist` entity + `Team*`/`Profile*`/`AdminVenue*`/`VenueServiceImpl` + `GlobalExceptionHandler:175` + `ErrorResponse:20`. `MinAgeValidator` dùng `LocalDate.now()` (JVM VN) — giữ nguyên.
  - Quy ước đề xuất cho mã sau (chưa ghi tài liệu): request thời điểm = `OffsetDateTime` (bắt buộc offset, nhận `Z`/`+07:00`), ngày thuần = `LocalDate` `yyyy-MM-dd`, không dùng `LocalDateTime`; response luôn `Z`. Nếu ghi vào `design-standards.md` → nhánh `chore/agent-framework-...` riêng.
  - Phạm vi đụng USER-001 (bugfix chức năng đã có + đổi `USER-001/API_Spec.md:29`, CHANGELOG, tech-debt #7) → cần chốt **nhánh** (làm trên nhánh VENUE-001 hay nhánh riêng) trước khi code — chưa hỏi.
- Tham khảo ngoài (user yêu cầu, chỉ đọc): repo `vdbas_expenditure_service_be` dùng `LocalDateTime`/`LocalDate`, JSON `dd-MM-yyyy[ HH:mm:ss]` qua `JacksonConfig` của lib `vdbas-common-be`, Oracle `TIMESTAMP` không múi giờ, JVM không cấu hình TZ. Không ảnh hưởng ArenaFlow.
- **Finding `code-reviewer` (2026-11-20)** còn lại: m2 `markStaleAlerted` WHERE ngoài lặp `status='ACTIVE' AND COALESCE(...) < :threshold` (`VenueRepository:61-66`) · m3 race report CLOSED vs `reopenClosureAlertIfNoPending` (`VenueServiceImpl:159-178`, `AdminVenueServiceImpl:95-100`) · m4 map `DataIntegrityViolationException` theo tên constraint (`VenueServiceImpl:169`, `VenueImageServiceImpl:165`) · m5 `Pattern` static (`CloudinaryImageUploadSigner#parsePublicId`) · m6 log status/message (`VenueImageCleanupServiceImpl:93`) · m7 comment "TEAM_014" cũ (`ConcurrencyConflictRetrier:44`) · m8 import FQN (`VenueServiceImpl:101`) · m9 2.4 không rate limit (`VenueImageServiceImpl:70-83`) · m10 V5 preflight report legacy trùng trước khi áp ở môi trường khác (Render).
- **`architect-reviewer` (tài liệu, 2026-11-19) — CHƯA ĐẠT, chưa sửa:** C1 CHANGELOG v4.3/v4.4 chứa tên nội bộ BE · M1 nhãn `[cần xác nhận #9/#11/#12]` còn trong nội dung · M2 "chưa chạy thử" · M3 tách mục "Không chặn" · M4 `PATCH` → `MINOR` · 3 MINOR.
- Lệch thiết kế #1–#4 trong `current-task.md`; #1 user chốt "sửa design, giữ code".
- Full suite gần nhất: 364/364 PASS (2026-11-19, trước rebase PR #74 — chỉ tài liệu).
- `git stash list` có `stash@{0}` (handoff cũ) — chỉ xoá khi user yêu cầu.
- Integration/live test nạp `.env.dev` (có `CLOUDINARY_*`); KHÔNG commit `.env.dev`.

## Blockers
- _không có_ — chờ user chốt mức chuẩn hoá UTC (m1).

## Việc tiếp theo
- [ ] Hỏi lại: chuẩn hoá UTC `Z` mức 1 (chỉ output API) hay mức 2 (output + giá trị nội bộ).
- [ ] Hỏi: làm phần USER-001 (tech-debt #7) trên nhánh VENUE-001 hay nhánh riêng; cập nhật `USER-001/API_Spec.md:29` + CHANGELOG.
- [ ] Đi sâu từng MINOR m2 → m10, mỗi mục user chọn sửa / ghi tech-debt.
- [ ] Sửa M1 + các mục đã chọn theo TDD → test liên quan + full suite (`.env.dev`, `-Duser.timezone=Asia/Ho_Chi_Minh`).
- [ ] `code-reviewer` phần sửa → đối chiếu lệch design → xử lý finding `architect-reviewer` đã chọn → `architect-reviewer` phần sửa.
- [ ] Commit chỉ khi user yêu cầu (tách `docs(VENUE-001): ...`); không push/PR khi chưa yêu cầu.
