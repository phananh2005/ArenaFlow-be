---
description: >
  Quét tìm thành phần ĐÃ CÓ (trong repo) TRƯỚC khi viết code mới, để không viết lại thứ đã
  tồn tại. Kích hoạt trước khi tạo util/helper/mapper/converter/formatter/validator/constant/
  hàm xử lý chung, hoặc khi nghe "có sẵn chưa", "đã có hàm nào", "tránh trùng",
  "viết lại hay dùng lại". Xuất Reuse Report 3 nhóm và ghi vào current-task.md.
---

# Reuse Scan

## MUST trigger
- Sắp **tạo mới** thành phần mang tính dùng chung (util, helper, mapper, converter, formatter, validator, constant, enum, hàm xử lý lặp lại được).
- Agent `developer` ở bước lập kế hoạch — luôn chạy, kể cả khi "cảm giác" là chưa có.

**NEVER** viết thành phần dùng chung mới khi chưa có Reuse Report.
**NEVER** kết luận "không có sẵn" mà không ghi **đã quét ở đâu, bằng cách nào**.

## Quy trình

### Bước 0 — Xác định nguồn quét
- Các package trong `*/src/main/java/` của 4 module `common`, `domain`, `application`, `api` (ADR-0006) — quét bằng search text, symbol.
- Nguồn nào đọc được nội dung, nguồn nào không → ghi là **vùng mù**.

### Bước 1 — Lập bộ từ khoá
- Khái niệm nghiệp vụ.
- Động từ kỹ thuật: validate, format, parse, convert, map, resolve, paging, retry, mask, export…
- Biến thể tên theo convention Java: `XxxUtil(s)`, `XxxHelper`, `XxxMapper`, `XxxValidator`, `XxxConverter`…

### Bước 2 — Tra cứu
Tự chọn công cụ tốt nhất đang có trong phiên (search text, symbol index…), theo 3 tiêu chí:
1. **Phản ánh source hiện tại** — kết quả từ index/cache chỉ tin khi còn khớp source.
2. **Rộng trước, sâu sau** — phủ mọi nguồn trước, rồi đào vào ứng viên.
3. **Ghi lại được** — cách tra nào không ghi lại được thì không dùng làm căn cứ kết luận.

### Bước 3 — Phân loại

| Nhóm | Nghĩa | Ghi kèm |
|---|---|---|
| **DÙNG LẠI** | Đã có thứ đáp ứng đúng | `path#symbol` + cách gọi |
| **MỞ RỘNG** | Có thứ gần đúng — bổ sung/tham số hoá thay vì viết bản song song | Vị trí + thay đổi dự kiến |
| **VIẾT MỚI** | Thật sự chưa có | Lý do + nguồn đã quét + từ khoá đã dùng |

> **Không thấy ≠ không tồn tại.** Nghi có tương đương nhưng nằm trong vùng mù → **DỪNG, hỏi người dùng**.

### Bước 4 — Ghi Reuse Report
Ghi vào mục `## Tái sử dụng` của `docs/agent/state/local-dev/current-task.md`.

**NEVER** để Reuse Report chỉ trong chat — `code-reviewer` (chiều 5) đối chiếu bảng này.

## Liên kết
- Gọi từ `developer` (bước quét tái sử dụng) · đối chiếu ở `code-reviewer` / `code-review`.
