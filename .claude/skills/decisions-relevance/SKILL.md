---
description: >
  Rà soát kho ADR của repo để bắt quyết định lỗi thời, mồ côi hoặc mâu thuẫn. Kích hoạt khi
  "rà ADR", "soi quyết định kiến trúc", "ADR nào còn hiệu lực"; hoặc khi sửa tài liệu kiến trúc.
  Chỉ phát hiện + gợi ý, KHÔNG tự sửa ADR.
---

# Decisions Relevance — rà soát ADR

> Kho ADR của repo: `docs/agent/decisions/`.

## Phạm vi
- Rà: `docs/agent/decisions/NNNN-*.md`. Bỏ qua `_template.md`, `README.md`, file `_`-prefix.

## Quy trình

### Bước 1 — Thu thập
- Với mỗi ADR: tiêu đề, **Trạng thái**, **Ngày**, **Liên quan**.
- Quét chỗ trỏ tới ADR trong tài liệu kiến trúc và các ADR khác ("Liên quan", "Superseded by").

### Bước 2 — Soi
1. ADR `Deprecated`/`Superseded` vẫn được trỏ như căn cứ hiện hành.
2. Link gãy / mồ côi; thiếu liên kết giữa ADR thay thế và ADR bị thay.
3. Mâu thuẫn giữa ≥ 2 ADR `Accepted` cùng chủ đề.
4. ADR `Proposed` lâu chưa chốt.
5. Trùng số `NNNN` (khoảng trống số không phải lỗi).
6. Sửa nội dung ADR `Accepted` thay vì tạo ADR mới + `Superseded`.
7. Metadata `Sửa đổi` không khớp mục sửa đổi cuối file.
8. Đường dẫn chết trong `Liên quan`.

### Bước 3 — Báo cáo

```text
## Kết quả rà ADR
### Cần xử lý
- [ADR-NNNN + vị trí] mô tả + gợi ý
### Cần theo dõi
- …
### Nhất quán
- ADR còn hiệu lực
```

## Ràng buộc
- **NEVER** tự sửa ADR / tài liệu kiến trúc, **NEVER** tạo ADR mới.
- Kho rỗng → báo "chưa có ADR để rà", không bịa vấn đề.

## Liên kết
- ADR: `docs/agent/decisions/README.md`
- Template: `docs/agent/decisions/_template.md`
- Quyết định nhỏ: `docs/agent/state/local-dev/decisions-log.md`
