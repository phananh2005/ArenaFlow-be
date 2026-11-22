---
task: VENUE-001 sửa — điện thoại/website chỉ Admin, danh sách user chỉ ACTIVE, tách danh sách Admin
created: 2026-11-22
updated: 2026-11-23
percent: 95
status: in_progress
branch: feature/VENUE-001-user-admin-venue-rules
---

## Mô tả
Sửa chức năng đã triển khai VENUE-001 (PR #75) theo quyết định user 2026-11-22:
1. Người dùng thường tạo sân gửi `phone`/`website` khác `null` (kể cả `""`) → 403 `VENUE_017`.
2. Người dùng thường sửa sân gửi `phone`/`website` khác `null` (kể cả `""`) → 403 `VENUE_017`; Admin toàn quyền.
3. Giữ BR-V06: người dùng thường chỉ sửa sân `ACTIVE` do mình tạo.
4. `GET /api/v1/venues`: chỉ sân `ACTIVE`; `status` khác `ACTIVE` → 400 `VENUE_018` (Admin gọi endpoint này cũng vậy).
5. Endpoint mới `GET /api/v1/admin/venues` (`q`, `status`, `page`, `pageSize`; không toạ độ; sắp theo tên); `status` mặc định `ALL`.

Quy tắc Javadoc mới (user chốt, áp cho code mới/sửa từ nay): mọi class/method mình thêm hoặc sửa trong `src/main` có Javadoc tiếng Việt; class test mới/sửa có Javadoc ở class.

## Thiết kế liên quan
- `docs/agent/business-spec/SYSTEM-001/` — BR-V06, AC-V01, AC-V04, UC-V01/UC-V04 (sửa sau khi code PASS).
- `docs/agent/design/VENUE-001/` — `API_Spec.md` §2.1, §2.2, §2.12, bảng quyền, endpoint mới; `Component_Class_Design.md`; `Processing_Flow.md`; `CHANGELOG.md`.
- `docs/agent/non-functional-standards.md` — danh sách phân trang ≤ 4 truy vấn (dùng 2: trang + count), `pageSize` chặn trên 100.

## Definition of Done
- [x] 5 thay đổi hành vi trên có test Red → Green.
- [x] Unit test liên quan PASS với `-Duser.timezone=Asia/Ho_Chi_Minh` (2026-11-22: common 12/12, domain 65/65, application 206 — 2 skip có sẵn; `api` test-compile PASS).
- [ ] Test cần DB (`VenueControllerTest`, `AdminVenueControllerTest`) — **chưa chạy**: chỉ chạy trên Neon branch riêng ở giai đoạn tích hợp (không chạy DB dev chung trước khi SYSTEM-002 merge).
- [x] `code-reviewer` không còn CRITICAL/MAJOR (4 lượt, lượt cuối 2026-11-23: 0/0/0).
- [x] Cập nhật SRS (v1.14) + design VENUE-001 (v6.0); `architect-reviewer` 2 lượt, findings user chọn đã sửa hết.

## Các bước thực hiện (tổng quan)
- [x] 1. Kế hoạch — trình user duyệt.
- [x] 2. Mã lỗi `VENUE_017` (403), `VENUE_018` (400) — TDD.
- [x] 3. Tạo sân: chặn `phone`/`website` với người dùng thường — TDD.
- [x] 4. Sửa sân: chặn `phone`/`website` với người dùng thường — TDD.
- [x] 5. `GET /venues` chỉ `ACTIVE`, `status` khác → 403; bỏ `searchNearbyAny` (hết dùng) — TDD.
- [x] 6. `GET /admin/venues` mới — TDD.
- [x] 7. Chạy unit test liên quan + test-compile toàn repo.
- [x] 8. `code-reviewer` → đối chiếu ảnh hưởng → cập nhật SRS/design → `architect-reviewer`.

## Kế hoạch chi tiết

### Bước 2 — Mã lỗi (`common`)
- `ErrorCode.VENUE_017(FORBIDDEN, "Chỉ quản trị viên được nhập số điện thoại và website của địa điểm")`.
- `ErrorCode.VENUE_018(BAD_REQUEST, "Danh sách này chỉ gồm địa điểm đang hoạt động; quản trị viên dùng danh sách quản trị")`.
- Test: `ErrorCodeTest` (nếu có kiểm danh sách/HTTP status) — thêm case.
- Kiểm 6 worktree: không nhánh nào thêm `VENUE_017+` (đã grep 2026-11-22).

### Bước 3 — Tạo sân (`VenueServiceImpl.createVenue`)
- Sau Q1 (đọc caller, đã có role từ DB), trước Q2 (phường): caller không phải `ADMIN` và (`phone != null` hoặc `website != null`) → `VENUE_017`. Không đổi chữ ký `createVenue(UUID, CreateVenueRequest)`.
- Test (Red trước): user gửi `phone="0901234567"` → `VENUE_017`, không gọi ward/save; user gửi `website=""` → `VENUE_017`; user gửi cả hai `null` → tạo bình thường; admin gửi cả hai → lưu đủ.
- Sửa test hiện có `createVenue…` đang truyền `""` cho phone/website với user thường (đổi sang `null`).

### Bước 4 — Sửa sân (`VenueServiceImpl.updateVenue`)
- Kiểm đầu tiên, trước mọi truy vấn và trước retrier: `!actor.admin()` và (`phone != null` hoặc `website != null`) → `VENUE_017`.
- Test: owner thường gửi `phone="0901234567"` / `website=""` → `VENUE_017`, `verifyNoInteractions(venueRepository)`; admin gửi `""` → xoá được; owner gửi `null` → giữ giá trị cũ do Admin nhập.
- Sửa `updateVenueChecksGuardsThenAppliesRequestedFieldsAndRereadsDetail` (đang dùng owner thường xoá phone/website) → tách phần phone/website sang case admin.

### Bước 5 — `GET /api/v1/venues` (`VenueServiceImpl.listVenues`)
- `request.status() != ACTIVE` → `VENUE_018` trước mọi truy vấn.
- Không toạ độ → `searchByStatus("ACTIVE", …)`; có toạ độ → `searchNearbyActive(…)`.
- Bỏ nhánh `searchAll`/`searchNearbyAny` khỏi `listVenues`; xoá `VenueRepository.searchNearbyAny` (hết nơi dùng). `searchAll`/`searchByStatus` giữ cho Admin.
- Test: `status=ALL`/`INACTIVE` (có và không toạ độ) → `VENUE_018`, `verifyNoInteractions(venueRepository)`; `ACTIVE`/không gửi → giữ hành vi cũ.

### Bước 6 — `GET /api/v1/admin/venues`
- DTO mới `AdminSearchVenueRequest(@Size(max=100) q, VenueStatusFilter status, @Min(1) page, @Min(1) @Max(100) pageSize)`; compact constructor: trim `q`, `status` mặc định `ALL`, `page`=1, `pageSize`=20.
- `AdminVenueService.listVenues(AdminSearchVenueRequest)` → `AdminVenueServiceImpl`: `ALL` → `searchAll`; khác → `searchByStatus`; trả `PageResponse<VenueSummaryResponse>` (`distanceKm = null`). 2 truy vấn (trang + count).
- Escape LIKE: chuyển `escapeLike` (private trong `VenueServiceImpl`) thành dùng chung cho 2 service → chạy `reuse-scan` trước; dự kiến đặt `application/support/LikePatterns.likeContains(String)` (null/rỗng → null).
- `AdminVenueController`: `@GetMapping` + `@Valid @ModelAttribute AdminSearchVenueRequest`; quyền đã chặn bởi `/api/v1/admin/**` → 403 `AUTH_004`.
- Test: `AdminVenueServiceImplTest` (mặc định ALL → `searchAll`; `INACTIVE` → `searchByStatus("INACTIVE")`; escape từ khoá); `AdminSearchVenueRequestValidationTest` (mặc định, giới hạn); `LikePatternsTest`. Controller test cần DB → để giai đoạn tích hợp, chỉ test-compile.

### Bước 7 — Test
- `./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -pl common,domain,application -am test` + `-pl api test-compile`; unit test `api` không cần DB.

### Ảnh hưởng (sửa sau khi code PASS + review)
- SRS SYSTEM-001: BR-V06 (thêm: điện thoại/website chỉ Admin), BR mới về danh sách (user chỉ ACTIVE; Admin xem toàn bộ, không lọc vị trí), AC-V01/AC-V04.
- Design VENUE-001: API_Spec §2.1 (lỗi 403 `VENUE_017`), §2.2 (bỏ `INACTIVE`/`ALL` cho user, 400 `VENUE_018`), §2.12, endpoint mới §2.14, bảng mã lỗi, bảng quyền; CCD (DTO, service, repository bỏ `searchNearbyAny`); CHANGELOG.
- FE: màn Admin đổi sang `GET /api/v1/admin/venues`.
- Hiệu năng (review m2, user chọn (a) 2026-11-22): `GET /api/v1/admin/venues` mặc định `ALL` → `searchAll` không có `WHERE status`, không dùng được `idx_venue_status_name` để sắp; chấp nhận vì bảng `venue` nhỏ, ghi vào CCD; chậm thì thêm index `venue(name, id)` bằng migration sau.

## Review code 2026-11-22
- `code-reviewer`: 0 CRITICAL, 1 MAJOR, 3 MINOR. User chọn: sửa M1 (UPDATE test thiếu `admin_verified_inactive_at`), sửa m1 (đo số truy vấn `GET /api/v1/admin/venues` trong `VenueNfsQueryCountTest`), m2 chọn (a), m3 ghi chú tích hợp.
- Đã sửa M1, m1; `api` test-compile PASS. Hai test này cần DB → chạy ở giai đoạn tích hợp.

## Ghi chú tích hợp (review m3)
- Nhánh này xoá `VenueRepository.searchNearbyAny`; `VenueServiceImpl.listVenues` chỉ còn nhánh ACTIVE (`searchByStatus("ACTIVE")` / `searchNearbyActive`) và ném `VENUE_018` khi `status` khác ACTIVE.
- Worktree MATCH-001 (`feature/MATCH-001-match-flow-a-impl`) sửa cùng khối `listVenues` (thêm `GeoBoundingBox`) và vẫn gọi `searchNearbyAny` → khi gộp: giữ `GeoBoundingBox`, chỉ gọi `searchNearbyActive`, bỏ nhánh `searchNearbyAny`.
- Chép mục này vào `pending-review-questions.md` (đang nằm trong `stash@{0}` của SYSTEM-002) khi khôi phục stash.

## Lệch thiết kế
| # | Phase | Design | Code thực tế | Lý do | File design cần sửa |
|---|---|---|---|---|---|

## Tái sử dụng
Reuse Report 2026-11-22 — escape từ khoá LIKE cho `GET /api/v1/admin/venues`.

| Nhóm | Thành phần | Ghi chú |
|---|---|---|
| **MỞ RỘNG** | `application/.../service/impl/VenueServiceImpl.java#escapeLike` (private static) | Chuyển thành `application/support/LikePatterns.contains(String)` (null/rỗng → null; escape `\`, `%`, `_`; bọc `%…%`); `VenueServiceImpl` và `AdminVenueServiceImpl` cùng gọi. Không viết bản song song. |
| **DÙNG LẠI** | `PageRequestFactory#create`, `PageResponse#from`, `VenueMapper#toSummary`, `VenueRepository#searchAll` / `#searchByStatus`, `VenueStatusFilter` | Danh sách Admin dùng nguyên các thành phần này. |

- Nguồn đã quét: `common`, `domain`, `application`, `api` `src/main/java` trên nhánh này (grep `escape`, `ILIKE`, `LIKE`, `replace("%"`, `replace("_"`, `LikePattern`, `qPattern`); 6 worktree song song (file sửa/mới trong `src/main`, cùng bộ từ khoá).
- Kết quả: chỉ có `VenueServiceImpl#escapeLike`; USER-003 `InvitationSearchQuery` chỉ có regex `PHONE_LIKE` (không escape LIKE). Vùng mù: không có.

## Sau architect-review (user chốt 2026-11-22/23)
- `VENUE_017` → 403 (quyền), `VENUE_018` → 400 + message "Danh sách này chỉ gồm địa điểm đang hoạt động; quản trị viên dùng danh sách quản trị" (khớp `api-convention.md` §5, không ghi lệch ở CCD §10).
- SRS không chép message nguyên văn; thứ tự `VENUE_017`: sau kiểm tra định dạng, trước mọi kiểm tra khác.
- Đã sửa A1, a1–a8, D1–D5, C1. Unit test: common 12/12, domain 65/65, application 206 (2 skip) PASS; `api` test-compile PASS.
- 2026-11-23: rebase lên `origin/main` 7040840, unit test chạy lại PASS; commit `feat(VENUE-001)` 31ba005 + `docs(VENUE-001)`. Chưa push/PR.
- Còn lại: test cần DB (`VenueControllerTest`, `AdminVenueControllerTest`, `VenueNfsQueryCountTest`) ở giai đoạn tích hợp → mới đổi `status: done`.
