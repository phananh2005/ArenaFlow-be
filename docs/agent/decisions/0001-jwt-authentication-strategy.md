# ADR-0001: Xác thực bằng JWT HS256 stateless, access 1 giờ / refresh 7 ngày

| | |
|---|---|
| **Trạng thái** | Accepted |
| **Ngày** | 2026-11-02 |
| **Người quyết định** | Product Owner (chốt tham số) · solution-architect (đề xuất) |
| **Liên quan** | ADR-0002 · `docs/agent/design/USER-001/` · `docs/agent/security-backend.md` |

## Bối cảnh (Context)
- REST API phục vụ web + mobile, cần xác thực stateless. Đăng nhập Google/Facebook/Phone OTP đều là **mock** trong MVP.
- Phân quyền tối thiểu: `USER` / `ADMIN`. Chưa có microservice khác cần tự xác minh token.
- `pom.xml` đã có `spring-boot-starter-security-oauth2-resource-server` (kèm Nimbus JOSE); `oauth2-authorization-server` đã gỡ (USER-001 không làm OAuth provider).
- `security-backend.md` §1.2 đã cập nhật: access token 1 giờ (từ 24 giờ), claim `roles`.

## Quyết định (Decision)
Chúng tôi dùng **JWT ký HS256**, xác minh qua Spring Security Resource Server (`NimbusJwtDecoder.withSecretKey`), không lưu token phía server.

| Tham số | Giá trị | Nguồn |
|---|---|---|
| Thuật toán | HS256, secret ≥256 bit, từ env `JWT_SECRET` (property `arenaflow.jwt.secret`) | User chốt |
| Access token | 1 giờ; claims `sub`=userId (UUID), `roles`, `typ=access`, `iat`, `exp` | User chốt TTL |
| Refresh token | 7 ngày; claims `sub`, `typ=refresh`; không rotate, không lưu | User chốt TTL |
| Vai trò | Cột `"user".role` (`USER`/`ADMIN`) — không có bảng role riêng | User chốt |
| Thư viện | Nimbus JOSE qua starter đã có | Đề xuất — chờ duyệt |

Refresh (`POST /api/v1/auth/refresh`) đọc lại `role` và `deleted_at` từ DB → user đã xoá không lấy được token mới.

**Phương án đã loại**
- Session phía server (DB/Redis): stateful, cần hạ tầng thêm.
- RS256: cần quản lý keypair, chưa có bên thứ ba cần public key.
- Authorization server OAuth2 đầy đủ: quá nặng cho xác thực mock.
- JJWT: phải thêm dependency trong khi Nimbus đã có sẵn.

## Hệ quả (Consequences)

**Tích cực**
- Không trạng thái phía server, không thêm hạ tầng; tận dụng filter chuẩn của Spring Security.
- Mỗi request xác thực không cần truy vấn DB.

**Tiêu cực / đánh đổi**
- Không thu hồi được token: access token của user vừa xoá tài khoản còn hiệu lực ≤1 giờ; refresh token bị lộ dùng được ≤7 ngày (trừ khi user bị xoá). **Chấp nhận cho MVP** — client tự xoá token local sau `DELETE /profile` thành công.
- Lộ secret → giả mạo được mọi token; phải giữ secret ngoài Git và xoay vòng thủ công.

**Cần theo dõi**
- Cần đăng xuất/thu hồi tức thời, hoặc có service khác cần xác minh token → xem lại (blacklist, rotate refresh, RS256).
- Thay OAuth/OTP mock bằng provider thật → ADR mới cho luồng xác minh provider.
