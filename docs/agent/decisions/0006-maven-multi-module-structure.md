# ADR-0006: Tách mã nguồn thành Maven multi-module common / domain / application / api

| | |
|---|---|
| **Trạng thái** | Accepted |
| **Ngày** | 2026-11-05 |
| **Người quyết định** | Chủ repo (phân tích: developer) |
| **Liên quan** | ADR-0001 (`JwtTokenService`/`JwtProperties` chuyển module) · ADR-0003 (`OptimisticLockRetrier` — nay `ConcurrencyConflictRetrier` — chuyển module) · `docs/agent/design/USER-001/Component_Class_Design.md` §2 + `CHANGELOG.md` v1.2 · `docs/agent/convention-backend.md` §1 |

## Bối cảnh (Context)
Repo đang là một module Maven, package chia theo tầng (`controller/`, `service/`, `repository/`, `dto/`…). Mỗi package chứa lẫn auth, profile và team, nên khó đọc. Ranh giới giữa các tầng chỉ dựa vào quy ước: compiler không chặn được tầng dưới import tầng trên, ví dụ entity/repository import DTO hay service. Phía trước còn nhiều nhóm chức năng (match, venue, mercenary, rating, chat) đã có entity. Đổi cấu trúc khi mới có USER-001 tốn ít nhất, và USER-002 chưa bắt đầu code.

Chủ repo muốn cấu trúc giống dự án đang làm việc song song `vdbas_expenditure_service_be`: 4 module, chiều phụ thuộc `api → application → domain → common`.

## Quyết định (Decision)
Chúng tôi tách repo thành Maven multi-module (root `packaging=pom`, parent `spring-boot-starter-parent`) với 4 module:

| Module | Chứa | Package |
|---|---|---|
| `common` | enum, mã lỗi, `BusinessException`, tiện ích dùng chung (chỉ phụ thuộc `spring-web` cho `HttpStatus`) | `com.phananh.arenaflow.common.*` |
| `domain` | JPA entity, repository, projection | `com.phananh.arenaflow.domain.{entity,repository,projection}` |
| `application` | service + impl, DTO, MapStruct mapper, validation, `support/` (guard, retry, JWT) | `com.phananh.arenaflow.application.*` |
| `api` | `ArenaFlowApplication`, controller theo nhóm chức năng, `config/`, `security/`, `application.properties`, Flyway migration | `com.phananh.arenaflow`, `com.phananh.arenaflow.api.*` |

- Đổi base package `com.phananh.ArenaFlow` → `com.phananh.arenaflow`, cho đúng quy ước Java (package viết thường).
- Test cùng package với lớp được kiểm, cùng module, trừ test cần Spring context (`@SpringBootTest`): nằm trong `api` (chỉ module này có lớp `main` và migration) nhưng giữ package của lớp được kiểm.
- Root POM đặt `surefire.failIfNoSpecifiedTests=false`, để lệnh `./mvnw -Dtest=<Test> test` vẫn chạy được trên mọi module.

**Phương án đã loại:**
- *Giữ một module, chỉ chia package theo chức năng (package-by-feature).* Gọn hơn, nhưng ranh giới tầng vẫn chỉ là quy ước, và không giống mẫu chủ repo đang dùng.
- *Spring Modulith / ArchUnit trên một module.* Kiểm tra được cả nhảy tầng, nhưng lỗi chỉ lộ ra khi chạy test và không giống mẫu. Có thể bổ sung sau, kết hợp với multi-module (xem Cần theo dõi).

## Hệ quả (Consequences)

**Tích cực**
- Maven chặn phụ thuộc ngược ngay lúc compile, ví dụ `domain` không import được `application`; `application` không import được lớp webmvc/servlet (module này không khai báo dependency web).
- Mỗi module có trách nhiệm rõ ràng, dễ tìm mã.
- Giống cấu trúc `vdbas_expenditure_service_be`, nên đổi qua lại giữa hai dự án ít phải làm quen lại.

**Tiêu cực / đánh đổi**
- Build và IDE phức tạp hơn: phải re-import Maven; chạy app ví dụ bằng `./mvnw package -DskipTests` rồi `java -jar api/target/api-0.0.1-SNAPSHOT.jar`. Tránh `install` khi phát triển: SNAPSHOT trong `~/.m2` có thể bị dùng thay source khi chạy `-pl <module>` thiếu `-am`.
- Maven **không** chặn nhảy tầng: `api` vẫn thấy `domain` (khai báo trực tiếp và qua phụ thuộc bắc cầu của `application`), nên quy tắc controller chỉ gọi service vẫn giữ bằng quy ước/review.
- `common` phụ thuộc `spring-web` vì `ErrorCode` dùng `HttpStatus`; dependency này đi tiếp (compile, bắc cầu) vào `application`, nên Maven không chặn service import lớp `spring-web` (vd `ResponseEntity`, `RestClient`).
- Trong mỗi module, package vẫn chia theo tầng (`service/`, `dto/`…), chưa chia theo chức năng. Khi số chức năng tăng, các package này sẽ dài ra.
- Lớp dùng chung nhiều tầng phải đặt ở tầng thấp nhất có dùng nó. Ví dụ `JwtTokenService` nằm ở `application`, vì service gọi trực tiếp.

**Cần theo dõi**
- Module có quá nhiều lớp trong một package (vd `application.dto.response`): cân nhắc chia thêm một cấp theo chức năng (`dto.team`, `dto.auth`).
- Có nhu cầu tách deploy độc lập: lúc đó xem lại ranh giới module.
- Review phát hiện controller gọi thẳng repository, hoặc service dùng lớp `spring-web`: cân nhắc thêm ArchUnit test.

> Ghi chú đánh số: 0005 dành cho ADR của USER-002 (nhánh `feature/USER-002-team-disband`, **chưa merge**). Nếu USER-002 đổi số ADR, rà lại số này.
