---
task: USER-001 — health liveness/readiness không chạm DB (`/actuator/health/liveness`) cho UptimeRobot / nền tảng deploy
created: 2026-11-23
updated: 2026-11-23
percent: 95
status: in_progress
---

## Mô tả
Sửa chức năng đã có (health check của USER-001 CCD §9.1, ADR-0004): thêm nhóm health `liveness` (chỉ trạng thái sống của ứng dụng, **không** kiểm DataSource) và `readiness`, public không token, để UptimeRobot / Render ping mà không đánh thức Neon. User chọn cách actuator probes thay vì API rỗng `/ping` (2026-11-23).

Nhánh: `feature/USER-001-liveness-probe` (từ `origin/main` `865a855`). Task trước (SYSTEM-002) đã `done` — lịch sử trên `main`.

## Thiết kế liên quan
- `docs/agent/design/USER-001/Component_Class_Design.md` §9.1 (bảng đường dẫn public: `GET /actuator/health`, `/actuator/health/**` `permitAll`, thành phần ẩn → 404).
- `docs/agent/decisions/0004-public-operational-endpoints.md` (Accepted).
- `docs/agent/security-backend.md` l.47 (framework — sửa trên nhánh `chore/`).
- NFS: không thêm truy vấn DB (liveness không chạm DB).

## Definition of Done
- [x] `GET /actuator/health/liveness` không token → 200 `{"status":"UP"}`; nhóm `liveness` không chứa `db`. (Render dev curl 200 — 2026-11-23)
- [x] `GET /actuator/health/readiness` không token → 200.
- [x] Hành vi cũ giữ nguyên: `/actuator/health` 200 (+ `groups`); `/actuator/health/db` 404; actuator khác cần token.
- [x] Test liên quan PASS (`SecurityConfigIntegrationTest` 12/12); `code-reviewer` 0 finding.
- [x] Design: USER-001 CCD §9.1 v5.2 + ADR-0004 mục `Sửa đổi` → `architect-reviewer` lượt 1–3 (lượt 3: 0 finding); không ảnh hưởng SRS.

## Các bước thực hiện (tổng quan)
- [x] 1. Đọc context (CCD §9.1, ADR-0004, `SecurityConfig`, `SecurityConfigIntegrationTest`)
- [x] 2. Kế hoạch chi tiết — user duyệt 2026-11-23
- [x] 3. TDD: Red **không đỏ** — Spring Boot 4.0.9 mặc định `management.endpoint.health.probes.enabled=true` (metadata `spring-boot-health`), liveness/readiness đã public 200. User chọn: giữ 2 test (chốt hành vi) + tài liệu, **không** thêm cấu hình. `SecurityConfigIntegrationTest` 12/12 PASS
- [x] 4. Chạy test + `code-reviewer`
- [x] 5. Cập nhật design (CCD §9.1, ADR-0004 `Sửa đổi`) → `architect-reviewer`; SRS không ảnh hưởng
- [x] 6. Commit (user yêu cầu 2026-11-23); push / PR chờ user

## Lệch thiết kế
_Trống = code khớp design._

| # | Phase | Design | Code thực tế | Lý do | Sửa ở |
|---|---|---|---|---|---|

## Tái sử dụng
Không tạo thành phần dùng chung mới (chỉ 1 khoá cấu hình). **DÙNG LẠI:** nhóm health `liveness` / `readiness` có sẵn của Spring Boot Actuator (`management.endpoint.health.probes.enabled`), rule `permitAll` `/actuator/health/**` có sẵn ở `SecurityConfig.java:48`. Không thêm dependency.

## Kế hoạch chi tiết
**Phase 1 — TDD (1 phase):**
- Test viết trước — `api/src/test/java/com/phananh/arenaflow/api/config/SecurityConfigIntegrationTest.java` (integration, cần `.env.dev`):
  - `livenessIsPublicAndDoesNotCheckDatabase`: `GET /actuator/health/liveness` không token → 200, `$.status = UP`; bean `HealthEndpointGroups` có nhóm `liveness`, nhóm không chứa `db` (chứng minh không chạm DB).
  - `readinessIsPublic`: `GET /actuator/health/readiness` không token → 200.
  - Red dự kiến: chưa bật probes ngoài Kubernetes → `/actuator/health/liveness` 404 [Inference — theo mặc định Spring Boot, xác nhận khi chạy Red].
  - Test cũ giữ nguyên: `healthIsPublic`, `healthComponentPathIsPublicButHidden` (`/db` vẫn 404), `otherActuatorEndpointsStillRequireToken`.
- ~~Code — `application.properties`: thêm `management.endpoint.health.probes.enabled=true`~~ — **bỏ** (2026-11-23): Red không đỏ vì đây là mặc định của Spring Boot 4; user chọn không thêm cấu hình. Không đổi code main.
- Lệnh: `. ./.env.dev && export DATABASE_URL DATABASE_USERNAME DATABASE_PASSWORD JWT_SECRET && ./mvnw -Duser.timezone=Asia/Ho_Chi_Minh -pl api -am -Dtest=SecurityConfigIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`.

**Sau code:** `code-reviewer` → design: USER-001 CCD §9.1 thêm 2 đường dẫn + CHANGELOG USER-001 (MINOR, nội bộ BE — endpoint vận hành không có trong API_Spec [cần kiểm khi sửa]); ADR-0004 hàng + mục `Sửa đổi` (bổ sung không đảo ngược) → `architect-reviewer`. `security-backend.md` l.47 → nhánh `chore/` sau.

**File:** sửa 1 (`SecurityConfigIntegrationTest.java` — 2 test + Javadoc class) + tài liệu 3 (CCD, CHANGELOG USER-001, ADR-0004).
