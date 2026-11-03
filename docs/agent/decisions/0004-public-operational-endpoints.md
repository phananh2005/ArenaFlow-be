# ADR-0004: Health public, API docs public nhưng mặc định tắt

| | |
|---|---|
| **Trạng thái** | Proposed |
| **Ngày** | 2026-11-03 |
| **Người quyết định** | Product Owner (duyệt mở endpoint + chọn mặc định tắt) · developer (đề xuất) |
| **Liên quan** | ADR-0001 · `docs/agent/design/USER-001/Component_Class_Design.md` §9.1 · `docs/agent/security-backend.md` §1.3 |

## Bối cảnh (Context)
- Thiết kế USER-001 v1.0 (`Component_Class_Design.md` §9.1) chỉ cho `permitAll` `/api/v1/auth/**`; mọi đường dẫn khác cần access token (cơ chế token: ADR-0001). Mẫu `security-backend.md` §2.1 không có health/API docs trong danh sách public.
- Hạ tầng (load balancer, container orchestrator) cần gọi health check **không có token**.
- Dev FE cần đọc hợp đồng API (OpenAPI/Swagger UI) mà không phải lấy token trước.
- springdoc mặc định **bật**. Nếu chỉ dựa vào việc đặt biến môi trường khi deploy để tắt, quên đặt = lộ toàn bộ contract API ở prod (fail-open). Repo chưa có profile/cấu hình prod.
- Actuator có endpoint nhạy cảm (`metrics`, `env`, `prometheus`…) — không được lộ.

## Quyết định (Decision)
Chúng tôi sẽ:

| Đường dẫn | Quy tắc bảo mật | Bật/tắt |
|---|---|---|
| `GET /actuator/health`, `GET /actuator/health/**` | `permitAll` | Luôn bật; chỉ trả trạng thái tổng `UP`/`DOWN`, đường dẫn thành phần (vd `/health/db`) trả 404 theo mặc định Spring Boot |
| `GET /v3/api-docs/**`, `GET /swagger-ui/**`, `GET /swagger-ui.html` | `permitAll` | **Mặc định tắt**; bật khi env `SPRINGDOC_ENABLED=true` (chỉ dev) |
| Actuator khác | `authenticated()` | Không expose qua web (chưa cấu hình `management.endpoints.web.exposure.*`) |

Cấu hình: `springdoc.api-docs.enabled=${SPRINGDOC_ENABLED:false}`, `springdoc.swagger-ui.enabled=${SPRINGDOC_ENABLED:false}` trong `application.properties`. Tắt → các đường dẫn trên trả 404.

**Phương án đã loại**
- Mặc định bật, tắt ở prod bằng `SPRINGDOC_API_DOCS_ENABLED=false` / `SPRINGDOC_SWAGGER_UI_ENABLED=false`: fail-open — quên biến là lộ API ở prod.
- Swagger cần token: dev FE phải lấy token trước khi đọc tài liệu; UI Swagger không tự gắn token cho request tải `/v3/api-docs`.
- Profile Spring `dev`/`prod`: repo chưa dùng profile; thêm profile chỉ cho 1 cờ là quá mức.
- Không có Swagger, phát tài liệu tĩnh: phải đồng bộ tay với code.

## Hệ quả (Consequences)

**Tích cực**
- Prod an toàn khi quên cấu hình (fail-closed); health check dùng được cho hạ tầng.
- Dev FE đọc tài liệu API không cần token khi `SPRINGDOC_ENABLED=true` có trong môi trường của JVM.

**Tiêu cực / đánh đổi**
- Dev phải đưa `SPRINGDOC_ENABLED=true` vào môi trường JVM: khi tạo `.env.dev` (gitignored) từ `.env.example`, bỏ comment dòng `# SPRINGDOC_ENABLED=true`; file không dùng `export` nên cần `export SPRINGDOC_ENABLED` sau khi `source`, hoặc khai báo trong run config của IDE — thiếu biến thì không thấy Swagger.
- Test `apiDocsAreDisabledByDefault` phụ thuộc môi trường: chạy test trong shell đã `export SPRINGDOC_ENABLED=true` → test fail. Lệnh test chuẩn chỉ export biến DB + `JWT_SECRET`.
- Môi trường staging muốn có Swagger phải tự đặt biến.
- Health check public tiết lộ trạng thái UP/DOWN (không có chi tiết thành phần theo mặc định của Spring Boot).

**Cần theo dõi**
- Expose thêm actuator endpoint (vd `prometheus`) → quyết định quy tắc truy cập riêng, không mở `permitAll`.
- Bật `management.endpoint.health.show-details` hoặc `show-components` → xem lại vì health đang public (`show-components` làm `/actuator/health/db` trả 200 kèm trạng thái DB, trái bảng Quyết định).
- Có API gateway / mạng nội bộ cho hạ tầng → cân nhắc đóng health khỏi internet.
