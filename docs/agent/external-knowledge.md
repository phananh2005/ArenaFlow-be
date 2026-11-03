# Tra cứu Neon ngoài repo

> Neon MCP đến từ plugin `neon-postgres@neon` (marketplace `neon`, khai báo trong `.claude/settings.json`), tên server `plugin:neon:neon`.
> URL server không pin project; phạm vi project do **OAuth** quyết định lúc Authenticate qua `/mcp`.
> Khi Authenticate **MUST** chọn project `ArenaFlow-be` (`misty-dew-90700890`, org `org-purple-hall-06771419`); **NEVER** chọn "All projects you can access".
> Kiểm tra phạm vi: `describe_project` không truyền `project_id` phải trả về `misty-dew-90700890`, và danh sách tool không có `list_projects`/`create_project`. Sai → dừng, đăng xuất và Authenticate lại.
> Không ghi API key hoặc database connection string vào repo.

## Quy tắc

- Dùng source local làm chuẩn cho code của repo hiện tại; dùng Neon MCP để xem trạng thái project/branch/schema khi cần.
- Tool có tham số `project_id`: bỏ trống (kết nối đã scope tự điền) hoặc truyền `misty-dew-90700890`; **NEVER** project khác. Không chắc project của một thao tác → dừng, hỏi người dùng.
- Server bật write mode: tool có `destructiveHint: true` **NEVER** gọi khi chưa hỏi người dùng.
- Trước thay đổi database, migration, role, branch hoặc cấu hình bảo mật phải trình kế hoạch và chờ xác nhận.
- Không thực hiện lệnh SQL ghi/xóa, migration, reset, restore, branch deletion hoặc credential rotation nếu chưa được người dùng yêu cầu rõ.
- Không đưa connection string, password, token hoặc PII vào prompt, log, tài liệu hay output.
- Kết quả tra cứu phải nêu thời điểm và project/branch liên quan; thông tin ngoài repo chưa kiểm chứng thì ghi rõ.
- Neon MCP chỉ cung cấp quyền theo OAuth hiện tại; nếu không truy cập được, dừng và yêu cầu người dùng xác thực lại, không chuyển sang key account-wide.

## Kết nối ứng dụng

Cấu hình runtime qua biến môi trường `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`. Không commit giá trị thật; chỉ lưu secret trong môi trường chạy hoặc secret manager được người dùng chọn.

Project hiện dùng PostgreSQL database `neondb`, branch mặc định `main`. Connection string không lưu trong file này.