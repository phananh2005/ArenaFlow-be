# ADR-0016: Tìm người dùng theo tên bằng hàm SQL bỏ dấu, tsvector `simple` và GIN — khớp đầu từ

| | |
|---|---|
| **Trạng thái** | Proposed |
| **Ngày** | 2026-11-17 |
| **Người quyết định** | Chủ repo (chốt D2 khi thiết kế USER-003, 2026-11-17) · solution-architect (đề xuất cách thực hiện) |
| **Liên quan** | `docs/agent/design/USER-003/` (Component_Class_Design §1, §4.4, §5.2, §6) · SRS `docs/agent/business-spec/USER-003/` (BR-U10, Q-08) · VENUE-001 `Component_Class_Design.md` `[cần xác nhận #8]` (tìm `q` bằng `ILIKE` chưa có index) · [ADR-0008](0008-uuid-v7-primary-keys.md) (PostgreSQL ≥ 18) |

## Bối cảnh (Context)
- USER-003 (UC-U05): đội trưởng tìm người để mời theo **tên hiển thị**, gợi ý khi đang gõ, không phân biệt hoa/thường và **dấu tiếng Việt** ("nguyen" khớp "Nguyễn"), tối đa 20 gợi ý, nhập từ 2 ký tự. Tên hiển thị do người dùng tự đặt (≤ 100 ký tự), có thể lưu dạng Unicode NFC hoặc NFD.
- DB: PostgreSQL 18 trên Neon, collation mặc định `C.UTF-8`; `unaccent` và `pg_trgm` có thể cài nhưng chưa cài; `pg_bigm` (chỉ mục bigram cho chuỗi 2 ký tự) không có trên Neon.
- `ILIKE '%…%'` không dùng được index B-tree; `pg_trgm` không giúp chuỗi 2 ký tự (trigram cần ≥ 3), mà SRS cho tìm từ 2 ký tự.
- API danh sách bị giới hạn 4 câu SQL (N_LIST); nhánh tìm theo tên của USER-003 cần sẵn 3 câu (đội, đội trưởng, tìm) cho guard.
- Chưa có quy ước tìm kiếm văn bản nào trong repo; VENUE-001 đang để ngỏ cách tìm `q` nhanh hơn.

## Quyết định (Decision)
Chúng tôi sẽ tìm theo tên bằng **khớp đầu từ** trên một `tsvector` dựng từ tên đã bỏ dấu, có index GIN biểu thức, không cài extension:

1. **Một hàm SQL `IMMUTABLE` `user_name_fold(text)`** (migration V12): `normalize(NFC)` → `lower` → `translate` 67 ký tự có dấu tiếng Việt sang không dấu (gồm `đ → d`) → mọi ký tự không phải `[a-z0-9]` thành một dấu cách → `btrim`. Đây là **nguồn duy nhất** của bảng ký tự.
2. **`user_name_search_vector(text)`** = `to_tsvector('simple', user_name_fold(text))`; index `GIN (user_name_search_vector(display_name)) WHERE deleted_at IS NULL`.
3. **Truy vấn:** `user_name_search_vector(display_name) @@ to_tsquery('simple', :tsQuery)` với `:tsQuery = 'w1:* & w2:* …'` (mỗi từ nhập khớp **đầu một từ** trong tên). Tham số bind, không ghép chuỗi.
4. **BE chuẩn hoá chuỗi nhập qua DB** (`SELECT user_name_fold(:q)`) rồi chỉ giữ token `^[a-z0-9]+$` trước khi dựng `tsQuery` — không giữ bản sao bảng ký tự trong Java, ký tự cú pháp tsquery không lọt vào. Tốn 1 câu SQL không đọc bảng; nhánh tên vừa bằng N_LIST = 4.
5. Thứ tự gợi ý: tên (đã chuẩn hoá) bắt đầu bằng chuỗi nhập trước (`starts_with`), rồi `display_name COLLATE "vi-VN-x-icu"`, rồi id; `LIMIT 21`.

**Phương án đã loại**
- `ILIKE '%…%'` (khớp mọi vị trí — bản đầu của SRS Q-08): quét tuần tự cả bảng; không có index phù hợp.
- `unaccent` + `pg_trgm` + GIN trigram: thêm extension; `unaccent()` không `IMMUTABLE` nên cần hàm bọc; trigram không lọc được chuỗi 2 ký tự.
- `pg_bigm`: không có trên Neon.
- Cột lưu sẵn tên chuẩn hoá (ghi ở Java khi đăng ký / sửa hồ sơ): thêm cột, sửa mọi luồng ghi `display_name`, bảng ký tự nằm ở Java.
- Hằng bảng ký tự trong Java + test đối chiếu với hàm SQL: hai bản sao, test chỉ bắt lệch trên tập ký tự được liệt kê; tiết kiệm 1 câu SQL không đáng.

## Hệ quả (Consequences)

**Tích cực**
- Tìm từ 2 ký tự vẫn dùng index (prefix `:*` trên GIN); không extension, không cột mới.
- Index, vế so khớp, vế sắp xếp và chuẩn hoá chuỗi nhập dùng cùng một hàm — không thể lệch; tên NFD xử lý như NFC.
- Ký tự đặc biệt trong chuỗi nhập không gây lỗi cú pháp `to_tsquery` (500).

**Tiêu cực / đánh đổi**
- **Đổi nghiệp vụ:** "an" không còn khớp "Lan" (chỉ khớp đầu từ) — SRS Q-08 / BR-U10 đã sửa theo (user chốt 2026-11-17).
- **Chi phí sắp xếp khi nhiều người khớp:** GIN chỉ lọc tập khớp; `starts_with` + so ICU phải tính cho **mọi** dòng khớp trước `LIMIT`. Đo trên bảng tạm 200.000 tên dữ liệu lệch (40% họ Nguyễn): `nguyen:*` 80.016 dòng 485 ms; `van:*` 266 ms; `an:*` 105 ms; `nguyen:* & an:*` 49 ms. Chấp nhận cho MVP (user chốt M2 = A, 2026-11-17), kèm giới hạn thời gian 2 giây cho câu tìm và giới hạn 30 lần/phút mỗi người (USER-003 Component_Class_Design §4.5, §5.2).
- **Thứ tự Flyway:** hàm + index nằm ở V12; Flyway không chạy out-of-order nên V12 chỉ áp sau V5–V11 (VENUE-001, SYSTEM-002 … NOTIFICATION-001) — code USER-003 đợi các mã đó.
- **Sửa bảng ký tự** (vd thêm chữ có dấu ngoài tiếng Việt — hiện thành dấu cách) cần migration `CREATE OR REPLACE FUNCTION` **và** `REINDEX` index trong cùng migration.
- Cập nhật `display_name` tốn thêm cập nhật index GIN (không đổi số câu SQL).

**Cần theo dõi**
- Bảng `"user"` đạt khoảng **50.000** người dùng, hoặc log có lỗi hết giờ của API tìm người → đo lại với dữ liệu thật; hướng xem lại: bỏ ưu tiên "bắt đầu bằng" khi tập khớp lớn, lưu sẵn khoá sắp xếp, hoặc xếp hạng bằng `ts_rank` có giới hạn.
- **Dùng lại cho VENUE-001** (`[cần xác nhận #8]` — tìm `q` trong tên / địa chỉ địa điểm): cùng hàm `user_name_fold` (nên đổi tên chung, vd `text_search_fold`, trong migration của mã dùng lại) + index GIN biểu thức trên `venue`. Cần quyết định riêng vì VENUE-001 đang khớp mọi vị trí (`ILIKE`).
- Neon thêm `pg_bigm` hoặc yêu cầu khớp giữa từ quay lại → xem lại quyết định.
