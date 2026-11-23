# ADR-0018: "Bây giờ" lấy theo timezone JVM — code không khai múi giờ

| | |
|---|---|
| **Trạng thái** | Accepted (chủ repo chốt nội dung 2026-11-21 / 2026-11-23 khi review code SYSTEM-002; `Accepted` qua PR SYSTEM-002 #84) |
| **Ngày** | 2026-11-23 |
| **Người quyết định** | Chủ repo (bỏ cả 5 chỗ khai zone trong code — 2026-11-21; ghim timezone cho JVM test và `spring-boot:run` — 2026-11-23) · developer (đề xuất cách thực hiện) |
| **Liên quan** | Thay **Quyết định 3 và 6** của [ADR-0017](0017-vietnam-local-datetime.md) (các quyết định khác của ADR-0017 giữ nguyên) · `docs/agent/design/SYSTEM-002/` v1.2 · SRS `docs/agent/business-spec/SYSTEM-001/` BC-M05 (không đổi) |

## Bối cảnh (Context)
- ADR-0017 chốt mọi thời điểm là giờ Việt Nam không kèm múi giờ (`LocalDateTime` / `TIMESTAMP`). Quyết định 3 của nó lấy "bây giờ" có zone tường minh (`Clock.system(Asia/Ho_Chi_Minh)`, hằng `AppTime.ZONE` / `ZONE_ID`) để code không phụ thuộc timezone JVM; quyết định 6 khai `zone = AppTime.ZONE_ID` cho mọi `@Scheduled(cron)`.
- Khi code SYSTEM-002, zone tường minh xuất hiện ở 5 nơi (bean `Clock`, `AppTime`, đổi epoch → giờ địa phương của chữ ký upload, 2 scheduler) trong khi JVM vốn đã được yêu cầu chạy `-Duser.timezone=Asia/Ho_Chi_Minh` cho log và phiên JDBC. Hai nguồn giờ (zone trong code và timezone JVM) cùng tồn tại; lệch nhau thì log và dữ liệu ghi khác giờ.
- Người dùng và nghiệp vụ chỉ ở Việt Nam; `Asia/Ho_Chi_Minh` cố định UTC+7, không giờ mùa hè.

## Quyết định (Decision)
Chúng tôi sẽ:

1. **Một nguồn giờ = timezone JVM**, JVM **luôn** chạy `Asia/Ho_Chi_Minh`. Bean `Clock` = `Clock.tick(Clock.systemDefaultZone(), 1 µs)` cho service/job; `AppTime.now()` = `LocalDateTime.now()` cắt micro giây, `AppTime.today()` = `LocalDate.now()` cho entity callback và nơi không inject được. `AppTime` không có hằng zone. Ngoài `AppTime`, cấm `LocalDateTime.now()` / `LocalDate.now()` không tham số.
2. **Đổi epoch → giờ địa phương** (vd hạn chữ ký upload) dùng `clock.getZone()`.
3. **`@Scheduled(cron)` không khai `zone`** — cron chạy theo timezone JVM.
4. **Bảo đảm JVM chạy giờ Việt Nam ở mọi nơi chạy code:** Dockerfile nhánh deploy (`TZ` + `-Duser.timezone`); lệnh `./mvnw` (`-Duser.timezone`); Maven ghim cho mọi JVM test (surefire `argLine`) và cho `spring-boot:run` (`systemPropertyVariables`); test kiểm timezone của JVM test.
5. **Chặn zone quay lại code:** test quét tĩnh `src/main` cấm `ZoneId.of(`, `ZoneOffset.`, `TimeZone.getTimeZone(`, `Clock.systemUTC()`, chuỗi `"Asia/Ho_Chi_Minh"`, `zone =` trong `@Scheduled`.
6. **SQL không đổi** (ADR-0017 quyết định 4): DEFAULT / migration vẫn `(now() AT TIME ZONE 'Asia/Ho_Chi_Minh')` — TimeZone phiên DB (Neon mặc định `GMT`) không do JVM quyết định với mọi công cụ.

**Phương án đã loại**
- Giữ zone tường minh trong code (ADR-0017 quyết định 3, 6): hai nguồn giờ cùng tồn tại (code và JVM); vẫn phải ghim timezone JVM cho log/JDBC nên không bớt được điều kiện vận hành; hằng zone lặp ở nhiều nơi và ở mọi nhánh đang code song song.
- Chỉ dựa vào cờ `-Duser.timezone` trong lệnh, không ghim trong Maven: JVM test và `spring-boot:run` (JVM riêng) có thể chạy timezone của máy khi quên cờ.

## Hệ quả (Consequences)

**Tích cực**
- Code, log, phiên JDBC dùng cùng một giờ; không còn hằng zone ở code nghiệp vụ.
- Test cố định `Clock` bằng `Clock.fixed(…, ZoneId.systemDefault())`; mốc nghiệp vụ và cron theo giờ Việt Nam **khi JVM chạy đúng timezone**.

**Tiêu cực / đánh đổi**
- Đúng giờ phụ thuộc điều kiện vận hành: môi trường chạy thiếu timezone JVM → mọi giá trị ghi và mọi mốc lệch theo timezone máy, không có lỗi báo.
- Các nhánh đang code song song (MATCH-001, MATCH-002, NOTIFICATION-001, REPUTATION-001, MERCENARY-001) dùng `AppTime.ZONE` / `zone = AppTime.ZONE_ID` → sửa khi rebase lên `main` có SYSTEM-002 (test → `ZoneId.systemDefault()`, service → `clock.getZone()`, bỏ `zone =`).

**Cần theo dõi**
- Mọi môi trường chạy mới phải đặt timezone JVM `Asia/Ho_Chi_Minh` trước khi chạy: CI, production, container khác, `java -jar`, IDE (chạy main class hoặc test runner của IDE không qua Maven).
- Tích hợp ngoài yêu cầu thời điểm UTC → đổi tại biên bằng zone của `Clock`, không đổi lưu trữ.
- Có người dùng / nghiệp vụ ngoài Việt Nam, hoặc Việt Nam áp dụng giờ mùa hè → xem lại cùng ADR-0017.
