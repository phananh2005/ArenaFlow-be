# ArenaFlow — Backend

> Tạo kèo và ghép người chơi cho thể thao nghiệp dư. MVP: bóng đá.

## Giới thiệu

ArenaFlow kết nối các đội bóng nghiệp dư muốn tìm đối thủ, giúp đội thiếu người tuyển thêm
người chơi (mercenary), và xây dựng hệ thống uy tín để mỗi trận đấu diễn ra đúng hẹn, đúng trình độ.

Repo này là **backend** của ArenaFlow: cung cấp REST API cho các chức năng

- **Người dùng & đội:** đăng nhập OAuth (Google, Facebook, Phone OTP), hồ sơ cá nhân, tạo đội, mời thành viên, chuyển quyền đội trưởng.
- **Tạo kèo:** đội đã có sân đăng kèo và nhận lời thách đấu; đội chưa có sân ghép cặp và thương lượng địa điểm, giờ đá.
- **Tuyển người chơi:** đăng tin tuyển theo vị trí, duyệt ứng viên.
- **Uy tín:** điểm tin cậy, tỷ lệ hoàn thành trận, đánh giá và report (bùng kèo, chơi xấu, sai trình độ).
- **Địa điểm & chi phí:** danh sách sân tham chiếu, thỏa thuận chi phí minh bạch.

ArenaFlow **không** quản lý vận hành sân, đặt sân thực tế hay xử lý thanh toán.

## Ý nghĩa trong cuộc sống

Bóng đá phong trào là niềm vui cuối tuần của rất nhiều người: một nhóm bạn cùng công ty,
cùng khu phố, cùng lớp cũ. Nhưng để có một trận đấu, đội trưởng thường phải hỏi khắp các nhóm chat
để tìm đối thủ, gọi từng người để đủ quân số, rồi vẫn lo đối thủ bùng kèo hoặc chênh lệch trình độ
quá nhiều.

ArenaFlow muốn gỡ những rào cản đó:

- **Giữ cho việc chơi thể thao đều đặn.** Tìm được đối thủ phù hợp nhanh hơn thì đội ra sân thường xuyên hơn, và người chơi có thêm lý do để vận động mỗi tuần.
- **Không ai phải ngồi ngoài.** Người lẻ muốn đá có chỗ để nhận lời mời; đội thiếu người có chỗ để tìm thêm người chơi.
- **Xây dựng niềm tin giữa những người lạ.** Điểm uy tín và đánh giá minh bạch khuyến khích sự đúng hẹn và tinh thần fair-play, để hai đội chưa từng gặp nhau vẫn yên tâm ra sân.
- **Mở rộng cộng đồng.** Mỗi trận đấu với một đội mới là một cơ hội làm quen, kết nối những người cùng đam mê trong cùng khu vực.

## Tech stack

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | Java 21 |
| Framework | Spring Boot 4.0.9 (Web MVC, Data JPA, Validation, Actuator) |
| Bảo mật | Spring Security, OAuth2 Resource Server (JWT) |
| Cơ sở dữ liệu | PostgreSQL (Neon) |
| Migration | Flyway |
| Tài liệu API | springdoc-openapi (Swagger UI) |
| Giám sát | Micrometer + Prometheus |
| Build | Maven multi-module (Maven Wrapper `./mvnw`) |
| Tiện ích | Lombok |
| Kiểm thử | JUnit 5, Spring Boot Test, Spring Security Test |

## Cấu trúc dự án

Maven multi-module, base package `com.phananh.arenaflow` ([ADR-0006](docs/agent/decisions/0006-maven-multi-module-structure.md)):

```
ArenaFlow-be
├── common      — enum, mã lỗi, exception nghiệp vụ dùng chung
├── domain      — JPA entity, repository, projection
├── application — service, DTO, MapStruct mapper, validation
└── api         — REST controller, security, cấu hình, Spring Boot entry point, Flyway migration
```

Chiều phụ thuộc: `api → application → domain → common`.

## Build & chạy

```sh
# Build toàn bộ + chạy test (integration test cần DB: nạp biến từ .env.dev)
./mvnw clean test

# Chạy một test cụ thể (ở bất kỳ module nào)
./mvnw -Dtest=TeamGuardTest test

# Đóng gói và chạy ứng dụng
./mvnw install -DskipTests
java -jar api/target/api-0.0.1-SNAPSHOT.jar
```
