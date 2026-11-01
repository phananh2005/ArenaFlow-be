# Tra cứu Neon ngoài repo

> Neon MCP được cấu hình trong `.mcp.json`, giới hạn vào project `misty-dew-90700890`.
> Lần đầu sử dụng cần hoàn tất OAuth trong Claude Code. Không ghi API key hoặc database connection string vào repo.

## Quy tắc

- Dùng source local làm chuẩn cho code của repo hiện tại; dùng Neon MCP để xem trạng thái project/branch/schema khi cần.
- Chỉ thao tác trong project đã pin; trước thay đổi database, migration, role, branch hoặc cấu hình bảo mật phải trình kế hoạch và chờ xác nhận.
- Không thực hiện lệnh SQL ghi/xóa, migration, reset, restore, branch deletion hoặc credential rotation nếu chưa được người dùng yêu cầu rõ.
- Không đưa connection string, password, token hoặc PII vào prompt, log, tài liệu hay output.
- Kết quả tra cứu phải nêu thời điểm và project/branch liên quan; thông tin ngoài repo chưa kiểm chứng thì ghi rõ.
- MCP project-scoped chỉ cung cấp quyền theo OAuth hiện tại; nếu không truy cập được, dừng và yêu cầu người dùng xác thực lại, không chuyển sang key account-wide.

## Kết nối ứng dụng

Cấu hình runtime qua biến môi trường `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`. Không commit giá trị thật; chỉ lưu secret trong môi trường chạy hoặc secret manager được người dùng chọn.

Project hiện dùng PostgreSQL database `neondb`, branch mặc định `main`. Connection string không lưu trong file này.