# ADR-0009: Ảnh lưu trên Cloudinary, client upload trực tiếp bằng chữ ký do backend cấp

| | |
|---|---|
| **Trạng thái** | Proposed |
| **Ngày** | 2026-11-09 |
| **Người quyết định** | Chủ repo (chốt Cloudinary + signed upload, job dọn định kỳ, chỉ người xin chữ ký được lưu ảnh, 2026-11-09) · solution-architect (đề xuất cách thực hiện) |
| **Liên quan** | `docs/agent/design/VENUE-001/` (`Component_Class_Design.md` §4.5, §4.12, §8; `Processing_Flow.md` §3.4–§3.5, §3.16, §9) · `docs/agent/security-backend.md` §12 · UC-V02, UC-V06, BR-V02, BR-V03 |

## Bối cảnh (Context)
- UC-V02: địa điểm có tối đa 5 ảnh, đúng 1 ảnh chính. Repo chưa có nơi lưu file; DB chỉ lưu `venue_image.image_url`.
- `team.avatar_url`, `"user".avatar_url` hiện nhận URL do client gửi (USER-001), không có lưu trữ riêng.
- Backend chạy request đồng bộ; NFS §5 cấm tác vụ dài và giữ transaction khi chờ I/O ngoài. Nhận file qua backend rồi đẩy tiếp làm request dài và tốn băng thông backend.
- Lưu URL tuỳ ý do client gửi cho phép trỏ tới nội dung ngoài kiểm soát (ảnh độc hại, link theo dõi).
- Gói free Cloudinary: 25 credit/tháng (thông tin chủ repo cung cấp; hành vi khi vượt hạn mức chưa xác minh). Admin API gói free: 500 request/giờ ([Admin API overview](https://cloudinary.com/documentation/admin_api_overview.md)).
- Các chức năng sau (avatar đội/user, ảnh khác) có thể cần cùng cơ chế → quyết định có tính xuyên chức năng.

## Quyết định (Decision)
Chúng tôi lưu ảnh trên **Cloudinary** theo luồng **signed direct upload**:

1. Backend cấp tham số upload đã ký (SHA-1 theo [Authentication signatures](https://cloudinary.com/documentation/authentication_signatures)): `public_id` do server sinh, `timestamp`, `allowed_formats`, `overwrite=false`. Chữ ký hợp lệ 1 giờ.
2. `public_id` = `<folder>/<scope>/<signedAt>-<nonce>-<tag>`: `signedAt` (= `timestamp`) cho biết tuổi asset; `tag` là HMAC của người xin chữ ký (khoá dẫn xuất từ `api_secret`) → chỉ người đó lưu được ảnh, URL công khai không lộ user id.
3. Client upload thẳng lên Cloudinary rồi gửi `public_id`, `version`, `format`, `signature` của response về backend trong hạn xác nhận. Backend kiểm mẫu `public_id`, phạm vi tài nguyên, người xin chữ ký, hạn, chữ ký response ([Response signatures](https://cloudinary.com/documentation/response_signatures)), rồi **tự dựng** URL. Backend **không** nhận URL từ client.
4. Lưu `public_id` (unique) cạnh `image_url`.
5. Ký/xác minh bằng JDK, không thêm SDK; request API không gọi HTTP tới Cloudinary.
6. Bí mật và thư mục chỉ qua biến môi trường (`CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `CLOUDINARY_FOLDER`); **NEVER** commit/log/trả client `api_secret`. `CLOUDINARY_FOLDER` **bắt buộc, khác nhau mỗi môi trường, không có giá trị mặc định**. Thiếu cấu hình → endpoint ảnh trả 503, phần còn lại chạy bình thường.
7. Hai interface trung tính để thay nhà cung cấp và tái dùng cho ảnh khác: `ImageUploadSigner` (ký/xác minh, trong request) và `ImageAssetAdmin` (liệt kê/xoá asset, chỉ job dọn).
8. Xoá ảnh: xoá cứng dòng DB; **không** gọi API xoá asset trong request. **Job dọn định kỳ** (mặc định tắt, chỉ bật tường minh ở môi trường có thư mục riêng; fail-fast nếu thư mục trống/giá trị mặc định cũ) liệt kê asset trong thư mục qua Admin API, đối chiếu `public_id` trong DB theo lô, xoá asset không còn tham chiếu và cũ hơn ân hạn (> hạn xác nhận + hạn chữ ký).

**Phương án đã loại**
- Upload qua backend (multipart) rồi đẩy lên Cloudinary/S3: request dài, giữ kết nối; trái NFS §5.
- Unsigned upload preset: ai biết preset cũng upload được, không gắn được upload với tài nguyên/người dùng.
- Nhận URL ảnh tuỳ ý từ client: không kiểm soát nội dung, không dọn được.
- Lưu file trong DB hoặc đĩa server: không hợp Neon serverless và nhiều instance.
- Neon Object Storage / S3: chủ repo chọn Cloudinary (CDN + biến đổi ảnh); có thể đổi qua interface ở #7.
- Gắn người upload bằng tham số `context` của Cloudinary: backend không đọc lại được khi xác nhận nếu không gọi Admin API; chữ ký response không phủ `context`. User id trong `public_id`: lộ trên URL.
- Listener `AFTER_COMMIT` gọi API xoá asset ngay khi xoá ảnh: không xử lý ảnh mồ côi (vẫn cần job), lỗi mạng sau commit làm sót asset, thêm HTTP đồng bộ trên luồng request.

## Hệ quả (Consequences)

**Tích cực**
- Backend không xử lý file; request ngắn.
- Chỉ ảnh do hệ thống ký, đúng người xin chữ ký, còn hạn mới được lưu; URL luôn thuộc cloud và thư mục của ArenaFlow.
- Dùng lại được cho avatar đội/user.

**Tiêu cực / đánh đổi**
- Phụ thuộc dịch vụ ngoài; hạn mức gói free có thể chặn upload/hiển thị (chưa xác minh hành vi khi vượt).
- **Dùng chung một cloud cho nhiều môi trường:** nếu hai môi trường trỏ cùng thư mục, job dọn của môi trường này xoá asset môi trường kia (không có trong DB của nó). Fail-fast chỉ chặn được thư mục trống/mặc định, **không** phát hiện được hai môi trường cấu hình trùng thư mục → quy tắc vận hành: mỗi môi trường một thư mục (tốt nhất một cloud riêng cho production).
- Ảnh mồ côi và ảnh đã xoá còn trên Cloudinary tới lần chạy job sau ân hạn; ảnh đã xoá vẫn mở được qua URL trong khoảng đó và tới khi cache CDN hết hạn (xoá cache CDN mặc định tắt).
- Job dọn tiêu hạn mức Admin API và chỉ quét được một số trang mỗi lần chạy (không lưu cursor) → thư viện ảnh rất lớn thì phần cuối không được quét tới khi bổ sung cursor.
- Client phải xác nhận upload trong hạn; quá hạn phải upload lại.
- Cách nối chuỗi chữ ký response, tác dụng của `allowed_formats`/`overwrite=false`, giới hạn kích thước file và các lời gọi Admin API mới được tra tài liệu, chưa chạy thử — phải kiểm trên cloud dev trước khi implement.
- Xoay vòng `api_secret` làm hỏng các upload chưa kịp xác nhận (chữ ký response và tag HMAC đều đổi).

**Cần theo dõi**
- Mức dùng credit hằng tháng; gần hạn mức → nâng gói, nén ảnh phía client, hoặc giới hạn tần suất upload.
- Số asset job dọn xoá mỗi lần, số lần dừng do HTTP 420, tổng số asset so với giới hạn quét mỗi lần chạy.
- Khi áp cho avatar đội/user: cập nhật ADR (mục Sửa đổi sau khi `Accepted`) thay vì tạo cơ chế song song.
