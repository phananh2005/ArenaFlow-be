# Chuẩn phi chức năng — Hiệu năng & Truy vấn

> **Chỉ áp dụng khi tầng có DB/ORM.** ArenaFlow-be dùng Spring Data JPA + PostgreSQL + Flyway.
> Living document: thêm mục khi đội gặp nhu cầu thật.

## 1. Nguyên tắc
- **Chi phí tăng theo lượng dữ liệu, không theo số dòng code** — mọi API trả tập hợp có chặn trên ngay từ thiết kế.
- **Số lần gọi DB quan trọng hơn độ phức tạp một câu** — số truy vấn phải là **hằng số**, không tăng theo số bản ghi.
- **Lọc ở nơi có dữ liệu** — đẩy filter/sort/aggregate xuống DB, chỉ lấy trường cần dùng.
- **Đo trước khi tối ưu** — cache/tăng tài nguyên/tăng timeout không thay việc sửa truy vấn sai hoặc thiếu index.

## 2. Phân trang
- **MUST** phân trang mọi API trả danh sách; `size` do server chặn trên.
- **MUST** sắp xếp tường minh, ổn định.
- **NEVER** trả toàn bộ bảng; export lớn xử lý theo lô.

## 3. Ngưỡng số truy vấn mỗi API

> Số dưới là **điểm khởi đầu** — đội chốt lại qua MR.

| Loại endpoint | Tối đa (mặc định đề xuất) | Gồm |
|---|---|---|
| Đọc chi tiết | 3 | bản ghi chính + phụ trợ |
| Đọc danh sách có phân trang | 4 | count + data + phụ trợ |
| Ghi (create/update/delete) | 6 | kiểm tồn tại + ràng buộc + ghi chính + audit |

> Ngưỡng 3/4/6 do người dùng xác nhận ngày 2026-10-31; xem là chuẩn dự án, thay đổi cần được duyệt.

- **MUST** bám ngưỡng **khi thiết kế và trước khi viết code**. Đánh giá không đạt → **DỪNG tại thời điểm đó**, nêu: nghiệp vụ buộc vượt · cách gộp đã thử · số truy vấn dự kiến — **chờ xác nhận**.
- **NEVER** vượt ngưỡng rồi giải trình sau khi code xong.
- **MUST** đếm số truy vấn thật bằng log SQL khi chạy, không suy từ code.
- **NEVER** gọi DB trong vòng lặp (kể cả qua lazy-load); **NEVER** truy vấn lại dữ liệu đã nạp trong cùng request.

## 4. Truy vấn & index
- Nạp sẵn quan hệ cần dùng trong một truy vấn (dùng `@EntityGraph` hoặc `JOIN FETCH`); chỉ lấy trường cần (projection/DTO); ghi nhiều bản ghi theo lô (`saveAll`).
- Cột filter/sort/join của truy vấn danh sách có index — thêm qua migration Flyway có kiểm soát, nêu lý do.

## 5. Ranh giới request đồng bộ
- **NEVER** chạy tác vụ dài (gọi ngoài nhiều lần, file lớn, tính toàn bộ dữ liệu) trong request đồng bộ — tách bất đồng bộ/batch.
- **NEVER** giữ transaction mở khi chờ I/O ngoài; gọi ngoài trong transaction phải có timeout.
