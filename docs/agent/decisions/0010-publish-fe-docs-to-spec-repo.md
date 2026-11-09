# ADR-0010: Tài liệu FE cần được publish tự động từ BE sang repo chỉ-đọc ArenaFlow-spec

| | |
|---|---|
| **Trạng thái** | Proposed |
| **Ngày** | 2026-11-09 |
| **Người quyết định** | Chủ repo (chốt repo riêng `ArenaFlow-spec`, FE gắn submodule, BE là nguồn duy nhất, 2026-11-09) |
| **Liên quan** | `.github/workflows/publish-spec.yml` · `docs/agent/scripts/publish-spec.cjs` · `docs/agent/README.md` mục "Publish tài liệu cho FE" |

## Bối cảnh (Context)
- FE (`ArenaFlow-fe`) cần đọc SRS và API_Spec để code theo hợp đồng API, nhưng không cần (và không nên kéo) mã nguồn BE.
- SRS, API_Spec, CHANGELOG, ADR được viết và review cùng PR code ở BE; tách nơi viết sẽ làm tài liệu lệch code.
- Thiết kế nội bộ (`Component_Class_Design.md`, `Processing_Flow.md`), state agent, hooks không có giá trị cho FE và làm loãng ngữ cảnh khi agent FE đọc.

## Quyết định (Decision)
Chúng tôi sẽ giữ BE là **nguồn duy nhất** và publish một bản phản chiếu **chỉ-đọc** sang repo `phananh2005/ArenaFlow-spec`:
- Workflow `publish-spec` chạy khi merge vào `main` (đổi path tài liệu liên quan) hoặc chạy tay trên `main`; ghi sang repo đích bằng fine-grained PAT `SPEC_REPO_TOKEN` (chỉ Contents:write trên ArenaFlow-spec), `GITHUB_TOKEN` của BE chỉ `contents: read`.
- Phạm vi: `business-spec/<MÃ>/`, `design/<MÃ>/API_Spec.md` + `CHANGELOG.md`, `api-convention.md`, `decisions/` (bỏ `_template`). Mỗi lần xoá sạch rồi chép lại để file bị xoá/đổi tên ở BE cũng biến mất ở đích.
- FE gắn ArenaFlow-spec làm git submodule; tín hiệu đổi hợp đồng là entry MAJOR trong CHANGELOG.

Phương án loại:
- **FE gắn thẳng BE làm submodule** — kéo toàn bộ mã nguồn và thiết kế nội bộ; FE phụ thuộc lịch sử commit BE.
- **Copy tay sang FE** — dễ quên, lệch phiên bản, không biết bản FE đang đọc ứng với commit BE nào.
- **Viết tài liệu ở repo riêng (spec repo là nguồn)** — tài liệu tách khỏi PR code, phá quy trình design → review → code hiện có.
- **GitHub Pages / wiki** — thêm hạ tầng; agent và IDE của FE không đọc được như file trong repo.

## Hệ quả (Consequences)

**Tích cực**
- FE đọc tài liệu đúng bản đã merge, biết nguồn qua file `SOURCE` (SHA BE).
- Không lộ mã nguồn và thiết kế nội bộ cho FE; ngữ cảnh agent FE gọn.
- Logic sao chép chạy được trên máy (dry-run) và có test.

**Tiêu cực / đánh đổi**
- Thêm một repo và một PAT cần quản lý (xoay vòng, có thể hết hạn).
- FE chỉ thấy thay đổi sau khi merge vào `main`, không thấy bản nháp trên nhánh.
- Link trong tài liệu trỏ tới file không publish (vd `Component_Class_Design.md`) bị gãy ở đích.

**Cần theo dõi**
- Workflow lỗi do PAT hết hạn/thu hồi → cấp lại secret.
- Gom thư mục theo domain/cụm (`<DOMAIN>/<MÃ>/`) → script đang dừng với lỗi; phải sửa script trước.
- FE cần thêm loại tài liệu hoặc cần gắn phiên bản theo release (tag) → xem lại phạm vi/cách publish.
