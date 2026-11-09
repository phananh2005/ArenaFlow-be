# Chuẩn viết tài liệu thiết kế kỹ thuật

> Dùng cho `solution-architect` (sinh) + `architect-reviewer` (soi).
> File này **chỉ trỏ** tới quy ước gốc (API, convention) — KHÔNG chép giá trị quy tắc vào đây (tránh trôi lệch).

## 1. Phạm vi theo tầng
- Thiết kế **chỉ tầng backend** (Spring Boot REST API) — repo ArenaFlow-be phụ trách.
- Tầng frontend → một dòng "Ngoài phạm vi repo này".

## 2. Một chức năng = tối đa 3 tài liệu (+ changelog khi sửa)

Thư mục `docs/agent/design/<MÃ>/`, copy từ `docs/agent/design/_template/`:

| Tài liệu | Trả lời | Bám nguồn |
|---|---|---|
| `API_Spec.md` | Hợp đồng ngoài cho FE (publish — §11): endpoint, request/response, lỗi, hành động theo trạng thái | `docs/agent/api-convention.md` |
| `Component_Class_Design.md` | Cấu trúc trong: module, class, entity, DTO, repository, service, **thay đổi schema/migration** — chia phần theo tầng Spring | `architecture.md` (nếu có), `[cần xác nhận: convention backend]` |
| `Processing_Flow.md` | Luồng chạy + ranh giới transaction, số truy vấn dự kiến | `architecture.md` (nếu có), `docs/agent/non-functional-standards.md` |
| `CHANGELOG.md` | Lịch sử sửa — **chỉ tạo ở lần sửa đầu tiên sau baseline** | §8 |

**Gom thư mục khi số chức năng tăng:** `design/<CỤM>/<MÃ>/` theo cụm chức năng, và `design/SHARED/<MÃ>/` cho khung
kỹ thuật dùng chung nhiều cụm. Bố cục đang dùng ghi ở `design/README.md` — agent đọc
file đó để biết đặt thư mục mới ở đâu, không đoán.

**Tham chiếu tài liệu thiết kế phải chịu được việc di chuyển thư mục:**
- **NEVER** trỏ sang bất kỳ tài liệu nào trong comment code: đường dẫn/mục tài liệu thiết kế/spec, ADR, mã neo nghiệp vụ/thiết kế (`BR-*`, `W6`, `L4`…) — tài liệu trỏ tới code, không ngược lại (chi tiết: `convention-backend.md` §2.4).
- Khi di chuyển/đổi tên thư mục thiết kế: grep mọi chỗ trỏ tới (knowledge, ADR, hub, `architecture.md`) và sửa trong cùng thay đổi.

## 3. Header chuẩn
Bảng: **Mã chức năng · Phạm vi tầng · Repo/stack · Quy ước áp dụng · Ràng buộc dữ liệu · Tác giả/Ngày/Phiên bản**.

## 4. Cổng tuân thủ (bắt buộc trước khi trình)
- **Mở lại file quy ước bản hiện hành** và soi **từng** endpoint/field — không dựa trí nhớ hay "thói quen REST".
- Tối thiểu: path & đặt tên (casing là chỗ hay sai nhất) · envelope/mã lỗi/phân trang · kiểu & định dạng field · tên DTO/class/tầng · thuật ngữ.
- Còn 1 điểm lệch → sửa ngay, chưa trình. Tra không thấy quy tắc → `[cần xác nhận]`.

## 5. Sơ đồ
- Mặc định **Mermaid** (`sequenceDiagram` cho luồng, `classDiagram` cho lớp) vì GitHub và Claude Code hiển thị được; không thì ASCII.

## 6. `[cần xác nhận]` (bắt buộc)
- Mọi điểm spec yêu cầu mà chưa map 1-1 (cột, mã trạng thái, liên kết, quyền) → đánh dấu inline **và** gom danh sách đánh số cuối mỗi tài liệu.
- **Không đoán cho xong.**

## 7. Bất biến cho thao tác ghi
- Ranh giới transaction · khoá lạc quan khi sửa/xoá đồng thời · soft-delete vs hard-delete · audit cùng transaction · idempotency cho retry.
- Có DB → nêu **số truy vấn dự kiến** mỗi API so với ngưỡng (`docs/agent/non-functional-standards.md`):
  - API chi tiết: ≤ 3 truy vấn (N_DETAIL)
  - API danh sách: ≤ 4 truy vấn (N_LIST)
  - API ghi: ≤ 6 truy vấn (N_WRITE)

## 8. Súc tích — tối ưu cho AI đọc
- **Một thông tin = một nơi**; tài liệu khác chỉ trỏ (dùng tiêu đề mục làm mỏ neo), không chép lại bảng field/JSON.
- Không lặp trong cùng tài liệu; bỏ diễn giải dài, chép spec nguyên văn, boilerplate chưa điền.
- Ưu tiên bảng / danh sách / code-fence.

## 9. Nhất quán chéo
- Trước khi thiết kế, quét thiết kế đã có (cùng module / dùng chung khái niệm). Trùng → trỏ/tái dùng.
- Xung đột không tự hoà giải → `[cần xác nhận]`, không đè thiết kế cũ.

## 10. Changelog thiết kế
- Bản sinh lần đầu = **baseline vX.Y**, không có changelog.
- Mỗi lần **sửa** sau baseline: thêm 1 entry **lên đầu** `CHANGELOG.md`: `## [vX.Y] — YYYY-MM-DD`, type (MAJOR/MINOR), tóm tắt 1 câu, danh sách thay đổi (vị trí · loại · cũ → mới · lý do), ảnh hưởng.
- **MAJOR** = phá vỡ hợp đồng (xoá/đổi endpoint, đổi shape DTO, đổi/bỏ mã lỗi). **MINOR** = thêm / sửa không phá vỡ / chốt `[cần xác nhận]`.
- Version entry mới nhất **khớp** header các tài liệu. Không sửa/xoá entry cũ.

## 11. API_Spec cho FE — đọc độc lập

`API_Spec.md` (+ `CHANGELOG.md`) được publish cho FE (ADR-0010, `docs/agent/README.md` mục "Publish tài liệu cho FE"); FE chỉ đọc file này, không đọc BE. Viết theo góc nhìn **người gọi API**:
1. **Không chi tiết nội bộ BE:** tên class/package/annotation, bảng/cột/index/migration, transaction, khoá, retrier, số truy vấn, tên job/bean. Những thứ đó chỉ ở `Component_Class_Design.md` / `Processing_Flow.md`.
2. **Chỉ trỏ tới tài liệu được publish:** SRS (`business-spec/`), `API_Spec.md` của mã khác, `api-convention.md`, ADR. **NEVER** trỏ `Component_Class_Design.md`, `Processing_Flow.md`, `docs/database/`, mã nguồn.
3. **Mỗi endpoint đủ để FE gọi:** dùng khi nào (màn hình/hành động) · ai gọi được · bảng field (kiểu, bắt buộc, ràng buộc, ví dụ) · JSON ví dụ request **và** response · liệt kê đủ giá trị enum.
4. **Mỗi mã lỗi có "khi nào" và "FE nên làm gì"** (vd `409 MATCH_015` → tải lại chi tiết).
5. **Đối tượng có trạng thái → bảng hành động theo trạng thái** (trạng thái × vai trò → hành động được phép), để FE biết nút nào hiện.

Đổi hợp đồng → `CHANGELOG.md` entry MAJOR (§10). Chỉ sửa câu chữ cho dễ đọc → MINOR.

**`CHANGELOG.md` cũng được publish → áp quy tắc 1–2:** mỗi entry ghi thay đổi **FE thấy được** (endpoint, field, mã lỗi, hành vi) và FE cần làm gì; không tên class/annotation, bảng/cột, cơ chế nội bộ. Thay đổi chỉ nội bộ BE (CCD/PF) → một dòng chung, vd "cập nhật tài liệu nội bộ BE, không ảnh hưởng FE". Không sửa entry cũ (§10).
