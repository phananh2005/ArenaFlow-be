# ADR-0008: Khoá chính UUID v7 (Hibernate `Style.VERSION_7`) cho mọi entity

| | |
|---|---|
| **Trạng thái** | Accepted |
| **Ngày** | 2026-11-06 |
| **Người quyết định** | Chủ repo (chốt backlog 2026-11-06) · developer (đề xuất cách thực hiện) |
| **Liên quan** | `docs/database/schema-design.md` (ID: UUID v7 sinh ở Java) · `docs/agent/convention-backend.md` (ví dụ entity) · `docs/agent/design/USER-001/API_Spec.md` §1 · `docs/agent/design/MATCH-001/Component_Class_Design.md` §5.2 (`insertAll`) |
| **Sửa đổi** | 2026-11-09 — câu INSERT gom lô viết tay (không qua Hibernate) dùng `uuidv7()` của PostgreSQL ≥ 18 |

## Bối cảnh (Context)
- 22 entity trong `domain/.../entity/**` sinh id bằng `@UuidGenerator(style = UuidGenerator.Style.TIME)`, tức UUID v1. Trước thay đổi này, id sinh ra có `version() == 1` (kiểm bằng `UserRepositoryTest` khi viết test Red).
- `docs/database/schema-design.md` đã ghi "Hibernate generates UUID v7 từ Java", nên code đang lệch tài liệu dữ liệu.
- UUID v1 đặt các bit thấp của timestamp lên đầu, nên thứ tự byte của id không theo thời gian tạo. UUID v7 (RFC 9562) đặt Unix timestamp mili-giây 48 bit lên đầu, nên id tăng theo thời gian tạo (ở mức mili-giây).
- Cột id là kiểu `uuid` của PostgreSQL, không có `DEFAULT`; id do Java sinh trước khi INSERT.
- Hibernate 7.2.25 (bản repo đang dùng) có `UuidGenerator.Style.VERSION_7` (đã kiểm bằng `javap`).

## Quyết định (Decision)
Chúng tôi dùng `@UuidGenerator(style = UuidGenerator.Style.VERSION_7)` cho **mọi** entity có id UUID do Hibernate sinh, kể cả entity sau này.

- Không migration: dữ liệu đã có giữ id v1. Cột `uuid` chứa lẫn v1 và v7 hợp lệ; không có logic nào phụ thuộc version của id. `ORDER BY id` khi khoá dòng ở `deleteAccount` (ADR-0007) chỉ cần thứ tự cố định, không cần theo thời gian.
- API contract không đổi: id vẫn là chuỗi UUID.
- Kiểm tra bằng test: `EntityIdGenerationTest` (domain) quét mọi `@Entity`, mọi field `@Id` kiểu `UUID` có `@GeneratedValue` hoặc `@UuidGenerator` (kể cả ở lớp cha) phải có `@UuidGenerator(style = VERSION_7)`; `UserRepositoryTest` kiểm id sinh ra có `version() == 7` và tăng theo thứ tự tạo.

**Phương án đã loại**
- Giữ `TIME` (v1): id không theo thứ tự thời gian, lệch `schema-design.md`.
- `RANDOM` (v4): không theo thứ tự thời gian, chèn ngẫu nhiên vào index.
- Sinh id ở DB (`DEFAULT` + hàm sinh UUID): cần migration cho mọi bảng và phụ thuộc phiên bản PostgreSQL/extension; Hibernate phải đọc lại id sau INSERT.

## Hệ quả (Consequences)

**Tích cực**
- Id tăng theo thời gian tạo: [Inference] chèn vào B-tree index khoá chính gần cuối index hơn so với v1/v4 (chưa đo trên dữ liệu repo).
- Code khớp `schema-design.md`.

**Tiêu cực / đánh đổi**
- Id lộ thời điểm tạo (mili-giây). Không dùng id làm bí mật.
- Thứ tự tăng chặt chỉ đảm bảo trong một JVM (Hibernate `UuidVersion7Strategy` giữ trạng thái trong process). Nhiều instance ứng dụng hoặc đồng hồ lùi sau restart → thứ tự chỉ gần đúng ở mức mili-giây.
- Dữ liệu cũ (v1) và mới (v7) lẫn nhau: sắp xếp theo id không phản ánh thời gian giữa hai nhóm. Cần thứ tự thời gian → dùng cột `*_at`, không dùng id.

**Cần theo dõi**
- Nếu sau này cần sắp xếp/phân trang theo id thay cho cột thời gian, xem lại dữ liệu v1 còn lại.

## Sửa đổi 2026-11-09 — INSERT gom lô viết tay dùng `uuidv7()` của PostgreSQL

- **Bối cảnh:** MATCH-001 ghi danh sách khoản tự nhập (`cost_custom_item`, ≤ 10 dòng) bằng **một** câu native `INSERT … SELECT … FROM unnest(...)` để số truy vấn không tăng theo số dòng (`non-functional-standards.md` §1). Câu này không qua Hibernate nên `@UuidGenerator(VERSION_7)` không chạy; sinh id ở Java ngoài session cần thêm tiện ích riêng.
- **Quyết định bổ sung:** câu INSERT gom lô viết tay (không qua Hibernate) sinh id bằng hàm built-in `uuidv7()` của **PostgreSQL ≥ 18** — cùng định dạng UUID v7 (RFC 9562). Neon dev (`misty-dew-90700890`) đã kiểm 2026-11-09: `server_version = 18.6`, có `uuidv7`. Mọi entity và mọi INSERT qua Hibernate giữ `@UuidGenerator(style = VERSION_7)`; `EntityIdGenerationTest` không đổi.
- **Phương án loại:** sinh id ở Java bằng generator Hibernate gọi ngoài session hoặc tiện ích `UuidV7` riêng (thêm mã tự viết cho định dạng đã có sẵn trong DB); JDBC batch qua `saveAll` (cần bật `hibernate.jdbc.batch_size` toàn ứng dụng, số câu vẫn theo số dòng).
- **Hệ quả:** ứng dụng phụ thuộc PostgreSQL ≥ 18 cho các câu này — môi trường thấp hơn lỗi khi chạy câu (migration không bị ảnh hưởng). Id do DB sinh → caller cần id phải đọc lại hoặc dùng `RETURNING`. Thứ tự tăng của id giữa JVM và DB chỉ gần đúng ở mức mili-giây (giống nhiều instance ở §Hệ quả); thứ tự thời gian vẫn dùng cột `*_at` hoặc cột thứ tự riêng (`display_order`).
