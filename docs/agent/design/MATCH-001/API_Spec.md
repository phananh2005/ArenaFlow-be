# API Specification — Kèo (luồng A, luồng B) & vòng đời kèo

<!-- File này được publish cho FE (ADR-0010). Viết theo design-standards.md §11:
     không tên class/bảng/cột/migration/transaction/khoá/số truy vấn; không trỏ Component_Class_Design.md,
     Processing_Flow.md, docs/database/. Chi tiết nội bộ BE ghi ở hai file đó. -->

| Mục | Giá trị |
|---|---|
| Mã chức năng | `MATCH-001` |
| Phạm vi tầng | Backend (Spring Boot REST API). Frontend: ngoài phạm vi repo này |
| Repo | ArenaFlow-be |
| Quy ước áp dụng | `api-convention.md` (envelope, phân trang, định dạng lỗi, kiểu dữ liệu); quy ước chung USER-001 `API_Spec.md` §1; mã lỗi `TEAM_*` dùng lại từ USER-001/USER-002 `API_Spec.md`, `VENUE_*` từ VENUE-001 `API_Spec.md`; ghép kèo luồng B (thương lượng), frame `PAIRING_UPDATED` và mã `MATCH_016`: MATCH-002 `API_Spec.md`; chat dùng chung (§1.6, §1.7, 2.16–2.20) — cuộc trò chuyện theo đơn ứng tuyển: MERCENARY-001 `API_Spec.md` §1.7; [ADR-0014](../../decisions/0014-realtime-chat-stomp-websocket.md) (kênh realtime) |
| Spec nghiệp vụ | `docs/agent/business-spec/SYSTEM-001/` (v1.10) — UC-M01, M02, M03, M04, M08, M09, UC-C01, C02; BR-M01..M06, BR-M09..M18, BR-C01..C05, BR-N01, BR-N03..BR-N06 (chat giữa hai đội, danh sách tin nhắn); UC-N01, UC-N04; Business_Rules §2.1, §2.2, §3.1, §4 (BC-M01..M07), §5.2, §5.4, §7.3; AC-M01..M03, M06..M12, AC-C01, AC-N01, AC-N03, AC-N04; Scenario 1, 2 (tạo kèo), 4, 5, 6, 10, 11, 12 |
| Tác giả / Ngày / Phiên bản | solution-architect · 2026-11-09 · v1.0 (baseline) · hiện tại **v2.4** (solution-architect · 2026-11-15) — lịch sử thay đổi: `CHANGELOG.md` |

## 1. Tổng quan

Đội trưởng tạo kèo **luồng A** (đội đã tự đặt sân) cho một bộ môn đang bật. Đội khác tìm kèo và gửi yêu cầu ghép — mỗi đội gửi có ngay một **cuộc trò chuyện** riêng với đội tạo kèo (`conversationId` trong yêu cầu; chat theo §1.6, 2.16–2.20 — bộ endpoint chat dùng chung cho mọi cuộc trò chuyện, gồm luồng B và mercenary); đội tạo kèo chấp nhận đúng một yêu cầu → kèo `CONFIRMED`, các yêu cầu đang chờ khác → `SUPERSEDED`. Đội tạo kèo nhập thoả thuận chi phí khi kèo còn mở (chỉ để hiển thị — ArenaFlow không thu/giữ tiền). Huỷ kèo hoặc rút yêu cầu **sau khi đã ghép** bị trừ điểm uy tín của đội thực hiện (§1.5). Kèo tự hết hạn / tự lưu trữ theo thời gian (§1.3). Admin bật/tắt bộ môn.

Kèo **luồng B** (`type = NEED_VENUE` — đội chưa có sân) được tạo, tìm, xem, huỷ bằng chính các endpoint ở đây, với 1–5 lựa chọn địa điểm và 1–5 khung giờ thay cho một địa điểm + một giờ. Kèo B **không** nhận yêu cầu ghép (2.6–2.10) và không dùng 2.4: đội khác "nhận kèo" để mở thương lượng, hai đội trưởng chat, chốt địa điểm/giờ, chi phí và tiền cọc, rồi cùng xác nhận — theo MATCH-002 `API_Spec.md`. Khi thương lượng hoàn tất, kèo B `CONFIRMED` có một địa điểm, một khung giờ, một đội đã ghép như kèo A. Luồng B mở cùng bản phát hành MATCH-002; trước đó 2.1 với `type = NEED_VENUE` trả `422 MATCH_016` — FE ẩn lựa chọn "Chưa có sân" tới khi luồng B được phát hành.

**Ngoài phạm vi:** ghép kèo luồng B — thương lượng, chốt địa điểm/giờ, chi phí và tiền cọc của luồng B (MATCH-002) · report "giao tiếp tệ" qua chat (REPUTATION-001 2.10) · quy tắc chat riêng của thương lượng luồng B (MATCH-002) và của đơn ứng tuyển (MERCENARY-001) · đánh giá trận, `ARCHIVED → COMPLETED`, Completion Rate (REPUTATION-001) · tuyển người lẻ (mercenary) · gửi thông báo đẩy (NOTIFICATION-001) · **sửa nội dung kèo sau khi tạo**: không có endpoint sửa; muốn đổi thì huỷ rồi tạo kèo mới (BR-M02, BR-M04) · header `Idempotency-Key` (`api-convention.md` §13) chưa hỗ trợ — FE chặn bấm lặp nút tạo kèo.

### 1.1. Quy ước chung

- Mọi endpoint cần `Authorization: Bearer <access token>`; thiếu/sai/hết hạn → `401 AUTH_001` → FE gọi refresh token (USER-001 `POST /api/v1/auth/refresh`) rồi gửi lại request; refresh thất bại → chuyển màn hình đăng nhập. `/api/v1/admin/**` cần quyền admin → không phải admin: `403 AUTH_004`.
- Envelope `data` / `error` / `pagination` theo `api-convention.md` §4, §7. `201` **không** kèm header `Location` — lấy id từ body.
- ID là chuỗi UUID. Path id sai định dạng, query/body sai kiểu, sai giá trị enum, thiếu field bắt buộc, vượt độ dài → `400 VALIDATION_ERROR` kèm `validationErrors[]` (`api-convention.md` §11); FE hiện lỗi cạnh field theo `validationErrors[].field`.
- Thời gian: chuỗi ISO-8601 kèm múi giờ (vd `"2026-11-12T11:00:00Z"`); response trả UTC.
- Tiền: VND, số nguyên.
- Chuỗi: server bỏ khoảng trắng đầu/cuối; field tuỳ chọn gửi `""` được coi là không nhập (response trả `null`).
- Danh sách: `page` ≥ 1 (mặc định 1), `pageSize` 1..100 (mặc định 20). Thứ tự cố định, ghi ở từng endpoint; không có tham số `sort`.
- Enum UPPERCASE, trừ nhãn trình độ (tiếng Việt) — đủ giá trị ở §4.1.
- Tên đội luôn hiển thị; kèo không trả thông tin cá nhân của đội trưởng (chat 2.16–2.20 trả tên / ảnh đội trưởng và người gửi — §1.6, §4.7–§4.9).

### 1.2. Thao tác thay mặt đội

- Chỉ **đội trưởng đang hoạt động** của đội được thao tác thay đội; thành viên khác chỉ xem. Không phải đội trưởng → `403 TEAM_002` → FE ẩn nút thao tác với người không phải đội trưởng.
- Đội thực hiện: lấy từ `teamId` trong body (2.1 tạo kèo, 2.5 huỷ kèo, 2.6 gửi yêu cầu), từ path (2.12), hoặc suy ra từ tài nguyên (2.4, 2.7, 2.8, 2.9: đội tạo kèo; 2.10: đội gửi yêu cầu).
- Một người làm đội trưởng của cả hai đội vẫn được ghép hai đội đó với nhau; hệ thống không kiểm trùng lịch thi đấu của một đội (BR-M18). Khi người dùng là đội trưởng nhiều đội, FE cho chọn đội trước khi gửi `teamId`.

**Trạng thái đội ảnh hưởng thao tác:**

| Thao tác | Đội `INACTIVE` (tạm ngưng) | Đội `DISBANDED` (đã giải tán) |
|---|---|---|
| Tạo kèo (2.1), sửa chi phí (2.4), gửi yêu cầu (2.6), chấp nhận (2.8) | `422 TEAM_015` | `422 TEAM_016` |
| Huỷ kèo (2.5), từ chối (2.9), rút yêu cầu (2.10) | Được phép | `422 TEAM_016` |
| Xem yêu cầu của kèo (2.7), yêu cầu của đội (2.12) | Được phép | `422 TEAM_016` |

Đội đã giải tán luôn trả `TEAM_016` (không phải `TEAM_002`). Đội **còn lại** không hoạt động: gửi yêu cầu tới kèo của đội tạo kèo không `ACTIVE`, hoặc chấp nhận yêu cầu của đội gửi không `ACTIVE` → `422 MATCH_013`.

### 1.3. Trạng thái hiển thị `EXPIRED` và chuyển trạng thái theo thời gian

- Kèo `OPEN` có `expiresAt` ≤ thời điểm hiện tại được **mọi API đọc** trả `status = "EXPIRED"` ngay; yêu cầu `PENDING` của kèo đó trả `status = "EXPIRED"`. Bộ lọc `status` ở 2.7, 2.11, 2.12 lọc theo trạng thái hiển thị này.
- Mọi thao tác ghi (2.4, 2.5, 2.6, 2.8, 2.9, 2.10) trên kèo đã hết hạn (đã `EXPIRED`, hoặc `OPEN` quá `expiresAt`) → `409 MATCH_004` "Kèo đã hết hạn"; kèo chuyển hẳn sang `EXPIRED`.
- Màn hình mở lâu có thể vượt `expiresAt` mà chưa tải lại → FE so `expiresAt` với giờ hiện tại để ẩn nút thao tác; nhận `MATCH_004` → tải lại chi tiết kèo (2.3).
- Kèo `CONFIRMED` tự chuyển `ARCHIVED` sau `endsAt`. Hệ thống cập nhật theo chu kỳ 5 phút, nên trong vài phút sau `endsAt` API có thể vẫn trả `CONFIRMED`.
- Từ `startsAt` trở đi kèo vẫn `CONFIRMED` nhưng không huỷ/rút được nữa (`422 MATCH_012`) → FE ẩn nút huỷ/rút khi giờ hiện tại ≥ `startsAt`.
- Kèo luồng B: `startsAt` / `endsAt` là khung giờ **sớm nhất** khi chưa ghép, là khung giờ **đã chốt** sau khi ghép. Kèo B hết hạn → thương lượng đang trao đổi cũng `EXPIRED` (MATCH-002 `API_Spec.md` §1.3).

### 1.4. Huỷ / rút theo trạng thái đang hiển thị (`expectedStatus`)

Huỷ kèo (2.5) và rút yêu cầu (2.10) **bắt buộc** gửi `expectedStatus` = trạng thái FE **đang hiển thị** cho người dùng (BR-M13):

| Endpoint | `expectedStatus` | Lấy từ |
|---|---|---|
| 2.5 Huỷ kèo | `OPEN` \| `CONFIRMED` | `status` của kèo trong dữ liệu đã tải (2.3 / 2.11) |
| 2.10 Rút yêu cầu | `PENDING` \| `ACCEPTED` | `status` của yêu cầu trong dữ liệu đã tải (2.12) |

- Server so với trạng thái hiện tại. Khác → `409 MATCH_015` "Trạng thái đã thay đổi, vui lòng tải lại": **không** huỷ/rút, **không** trừ điểm. Ví dụ: màn hình còn `OPEN` nhưng kèo vừa được ghép; màn hình còn `PENDING` nhưng yêu cầu vừa được chấp nhận.
- FE nhận `MATCH_015`: hiện message, tải lại (2.3 cho kèo, 2.12 cho yêu cầu), để người dùng xem trạng thái mới rồi tự bấm lại. **Không** tự gửi lại với trạng thái mới — sau khi ghép, thao tác bị trừ điểm và cần lý do.
- Lấy `expectedStatus` từ dữ liệu đã tải, không gán cứng theo nút.

### 1.5. Quy tắc trừ điểm uy tín (Reliability Score)

Áp dụng khi **huỷ kèo `CONFIRMED`** (2.5) hoặc **rút yêu cầu `ACCEPTED`** (2.10). Chỉ đội thực hiện bị trừ (điểm của đội, không phải điểm cá nhân đội trưởng); đội còn lại không bị trừ. Huỷ kèo `OPEN` và rút yêu cầu `PENDING` không trừ điểm (BR-M09..BR-M11, Business_Rules §7.3).

| Khoảng cách Δ = `startsAt` − lúc huỷ/rút | Mức trừ (`reliabilityPenalty`) |
|---|---|
| Δ ≥ 24 giờ (đúng 24 giờ → `-2`) | `-2` |
| 4 giờ ≤ Δ < 24 giờ (đúng 4 giờ → `-5`) | `-5` |
| 0 < Δ < 4 giờ | `-10` |
| Trận đã bắt đầu (giờ hiện tại ≥ `startsAt`) | Không huỷ/rút được → `422 MATCH_012` |

- Điểm đội nằm trong [0, 100]; trừ quá 0 thì điểm dừng ở 0. `reliabilityPenalty` trong response là mức trừ theo bảng.
- FE có thể tính trước mức trừ từ `startsAt` để hiện cảnh báo trên hộp xác nhận; con số chính thức là `reliabilityPenalty` trong response.
- Mỗi lần huỷ chỉ trừ điểm một lần: đội đã ghép bấm "Huỷ kèo" (2.5) hay "Rút yêu cầu" (2.10) cho cùng kết quả (BR-M12).
- Huỷ kèo `CONFIRMED` / rút yêu cầu `ACCEPTED` cũng đưa chuỗi trận sạch của đội huỷ về 0 (cách tính chuỗi: REPUTATION-001 `API_Spec.md` §1.3).

### 1.6. Cuộc trò chuyện giữa hai đội (luồng A và B) — chat dùng chung

Chat của hệ thống (giữa hai đội trong một kèo, và chat 1–1 theo đơn ứng tuyển mercenary — MERCENARY-001 `API_Spec.md` §1.7) dùng chung một bộ endpoint **2.16–2.20** (`/conversations/…`, `/profile/conversations`), kiểu dữ liệu §4.7–§4.9 và kênh realtime §1.7. Mục này ghi quy tắc của **chat giữa hai đội**.

- **Mỗi cặp (kèo, đội tham gia) có đúng một cuộc trò chuyện** với đội tạo kèo (BR-N04, BC-N04):
  - Luồng A: mở khi đội gửi yêu cầu ghép kèo (2.6), kể cả khi yêu cầu còn `PENDING`. Mỗi đội gửi có một cuộc trò chuyện riêng với đội tạo kèo; đội khác không thấy (vd X không thấy chat Alpha–Y). Đội **gửi lại** yêu cầu sau khi rút / hết hạn → dùng tiếp cuộc trò chuyện cũ (giữ lịch sử, cùng `conversationId`).
  - Luồng B: mở khi đội nhận kèo (MATCH-002 2.1) — mỗi thương lượng một cuộc trò chuyện.
  - Sau khi ghép, hai đội nhắn tiếp trong chính cuộc trò chuyện đó (không mở cuộc mới).
- `conversationId` có trong: yêu cầu ghép kèo (§4.4 — 2.6, 2.7, 2.12), thương lượng (MATCH-002 §4.2, §4.3 — 2.1–2.4) và danh sách "Tin nhắn của tôi" (2.20 — mọi cuộc trò chuyện người gọi xem được, kể cả người đã giải tán đội). FE mở phòng chat bằng 2.19 + 2.17 với id này.
- **Ai xem / nhắn:** chỉ **đội trưởng đang hoạt động hiện tại** của đội tạo kèo hoặc của đội tham gia. Thành viên thường, đội trưởng cũ (đã chuyển quyền), đội khác → `403 CHAT_003`. Đội trưởng mới sau khi chuyển quyền tiếp quản cuộc trò chuyện (đọc được cả tin cũ). Người làm đội trưởng của **cả hai** đội (BR-M18) xem và nhắn với vai trò **đội tạo kèo** (`viewerPartyId` = đội tạo kèo; tin mang tên đội tạo kèo; report qua chat vẫn bị chặn — REPUTATION-001 2.10 `REPORT_005`) — user chốt 2026-11-15. Tài khoản đã xoá (token còn hạn) → `404 USER_001` ở mọi endpoint chat.
- **Đội đã giải tán** (BR-N05 (3), user chốt 2026-11-15): người đã bấm giải tán (đội trưởng cuối của đội) vẫn mở được cuộc trò chuyện với tư cách đội đó — xem đầu phòng (2.19), đọc lịch sử (2.17), đánh dấu đã đọc (2.18), `SUBSCRIBE`, thấy trong 2.20 — nhưng **không gửi được** (`canSend = false`; 2.16 → `409 CHAT_004`). Thành viên khác của đội đã giải tán → `403 CHAT_003`. Đội còn lại vẫn xem và nhắn theo bảng dưới. Áp như nhau cho luồng A và luồng B (và chat theo đơn — MERCENARY-001 §1.7). Ví dụ: Beta đã chat với Alpha, Nam (đội trưởng Beta) giải tán Beta → Nam vẫn đọc chat Alpha–Beta nhưng ô chat bị khoá; Alpha vẫn nhắn được nếu hai đội đã ghép / yêu cầu còn chờ.
- **Nhắn được / chỉ xem** (BR-N05) — tính tại lúc gửi theo trạng thái hiện hành của yêu cầu / thương lượng; trạng thái đội (tạm ngưng, đội kia không hoạt động) và bộ môn tắt **không** ảnh hưởng:

| Đội tham gia | Luồng A — yêu cầu ghép kèo gần nhất của đội | Luồng B — thương lượng (MATCH-002 §1.3) | Gửi tin (2.16) |
|---|---|---|---|
| Chưa có kết quả | `PENDING`, kèo chưa quá `expiresAt` | `NEGOTIATING`, kèo chưa quá `expiresAt` | Nhắn được |
| Chưa có kết quả nhưng kèo `OPEN` đã quá `expiresAt` | `PENDING` (hiển thị `EXPIRED`) | `NEGOTIATING` (hiển thị `EXPIRED`) | `409 MATCH_004` — kèo, yêu cầu / thương lượng chuyển `EXPIRED` ngay (BR-M17); từ đó chỉ xem |
| **Đã ghép** | `ACCEPTED`, hoặc `WITHDRAWN` sau khi đã được chấp nhận (= huỷ kèo, BR-M12) | `CONFIRMED` | Nhắn được — trước, trong, sau trận và cả khi kèo bị huỷ, tới khi cuộc trò chuyện bị xoá |
| Không được ghép | `REJECTED`, `SUPERSEDED`, `EXPIRED`, `WITHDRAWN` khi còn `PENDING` | `SUPERSEDED`, `EXPIRED` | **Chỉ xem** — `409 CHAT_004` |

  "Đã ghép" = đội tham gia là đội đã ghép của kèo (§4.2 `opponentTeam`), giữ nguyên khi kèo bị huỷ sau đó. Đọc lịch sử (2.17), đánh dấu đã đọc (2.18), đầu phòng (2.19), danh sách (2.20): mọi trạng thái.
- Ví dụ (Scenario 12): kèo luồng A của Alpha; X, Y, Z gửi yêu cầu → 3 cuộc trò chuyện Alpha–X, Alpha–Y, Alpha–Z, đều nhắn được. Z rút khi còn chờ → Alpha–Z chỉ xem. Alpha chấp nhận X → Alpha–Y (`SUPERSEDED`) chỉ xem; Alpha–X nhắn được. X huỷ kèo (rút yêu cầu đã chấp nhận) → Alpha–X **vẫn** nhắn được.
- **Report "giao tiếp tệ" qua chat:** cuộc trò chuyện đã có ít nhất 1 tin (của bất kỳ bên nào) → mỗi đội trưởng report được đội kia một lần mỗi kèo, bất kể trạng thái yêu cầu / thương lượng / kèo — REPUTATION-001 `API_Spec.md` 2.10 (BR-R07).
- **Hạn lưu:** cuộc trò chuyện và tin nhắn bị xoá 90 ngày sau khi kèo kết thúc (`COMPLETED`, `ARCHIVED`, `CANCELLED`, `EXPIRED`) — mọi cuộc trò chuyện của kèo, gồm của đội không được ghép (BR-N01). Sau khi xoá: 2.16–2.19 → `404 CHAT_002`; không còn trong 2.20.

### 1.7. Kênh realtime (STOMP over WebSocket)

Dùng để nhận tin nhắn mới (và thay đổi của thương lượng / đơn ứng tuyển) ngay khi xảy ra. Một **topic cho mỗi cuộc trò chuyện** (`/topic/conversations/{conversationId}`) — dùng chung cho mọi loại cuộc trò chuyện (§1.6). **Không** gửi tin qua kênh này (gửi bằng 2.16). Không bảo đảm giao frame — sau mỗi lần kết nối lại, FE làm **đúng thứ tự**:
1. `CONNECT` rồi `SUBSCRIBE` lại các cuộc trò chuyện đang mở (từ lúc này tin mới đến qua frame);
2. gọi 2.17 với `after` = `messageId` mới nhất đang có (lặp khi `hasMore = true`) để bù tin bị lỡ trong lúc mất kết nối; gọi 2.19 để lấy `canSend` mới; màn hình thương lượng gọi thêm MATCH-002 2.4 để lấy trạng thái gói mới;
3. gộp kết quả 2.17 với frame `MESSAGE_CREATED` nhận được trong lúc gọi 2.17, **bỏ trùng theo `messageId`** (một tin có thể đến qua cả hai đường).

Gọi 2.17 trước khi `SUBSCRIBE` có thể lỡ tin gửi giữa hai bước. Hiếm khi một tin gửi đúng lúc mất kết nối có thể không có trong kết quả `after`; tin đó vẫn hiện khi tải lại trang mới nhất (2.17 không con trỏ) — FE nên tải lại trang mới nhất mỗi khi mở lại màn hình chat.

| Bước | Cách làm |
|---|---|
| Kết nối | SockJS tới `/ws` (cùng host với API, không có tiền tố `/api/v1`) `[cần xác nhận #12]`, rồi STOMP `CONNECT` với header `Authorization: Bearer <access token>`. Heartbeat `10000,10000` |
| Lỗi kết nối | Token thiếu / sai / hết hạn / là refresh token → frame `ERROR` header `message: AUTH_001` rồi đóng. FE refresh token (USER-001) rồi kết nối lại |
| Đăng ký | `SUBSCRIBE` `/topic/conversations/{conversationId}` cho từng cuộc trò chuyện đang mở trên màn hình (`conversationId` từ §4.4, §4.9, MATCH-002 §4.2/§4.3, đơn ứng tuyển MERCENARY-001). Destination phải đúng mẫu này (không wildcard, vd `/topic/**` → `ERROR`). Header `selector` không được hỗ trợ (bị bỏ qua) — FE tự lọc frame theo `type` |
| Lỗi đăng ký | Destination sai mẫu → `ERROR`; không có cuộc trò chuyện → `ERROR` `message: CHAT_002`; tài khoản đã xoá → `ERROR` `message: USER_001`; không tham gia cuộc trò chuyện (§1.6) → `ERROR` `message: CHAT_003`; token đã hết hạn → `ERROR` `message: AUTH_001`. Server đóng kết nối sau frame `ERROR` → FE xử lý rồi kết nối lại |
| Gửi | `SEND` bị từ chối (frame `ERROR`) |
| Thời gian sống | Kết nối không tự hết hạn theo token; quyền chỉ kiểm lúc `CONNECT` và `SUBSCRIBE` — đội trưởng vừa chuyển quyền vẫn nhận frame của cuộc trò chuyện đã đăng ký tới khi ngắt kết nối. FE kết nối lại sau mỗi lần refresh token |

**Frame server gửi** (body JSON):

| `type` | Khi nào | Nội dung |
|---|---|---|
| `MESSAGE_CREATED` | Có tin mới trong cuộc trò chuyện (kể cả tin của chính mình) | `message`: object Tin nhắn (§4.8) |
| `PAIRING_UPDATED` | Chỉ trên cuộc trò chuyện của thương lượng luồng B | MATCH-002 `API_Spec.md` §1.6 |
| `APPLICATION_UPDATED` | Chỉ trên cuộc trò chuyện theo đơn ứng tuyển | MERCENARY-001 `API_Spec.md` §1.7 |

```json
{ "type": "MESSAGE_CREATED",
  "message": { "messageId": "01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e01", "conversationId": "01933d41-1a2b-7c3d-8e4f-5a6b7c8d9e31",
               "senderPartyType": "TEAM", "senderPartyId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
               "senderUserId": "01932e00-5a6b-7c7d-8e8f-9a0b1c2d3e12", "senderName": "Nam", "senderAvatarUrl": null,
               "content": "18:00 sân Y được không?", "isRead": false, "createdAt": "2026-11-10T08:00:00Z" } }
```

- **Không** có frame khi yêu cầu ghép kèo luồng A đổi trạng thái (chấp nhận / từ chối / rút / thay thế / hết hạn) hoặc kèo bị huỷ: FE biết qua lỗi (`CHAT_004`, `MATCH_004`) ở lần gửi kế tiếp, hoặc khi tải lại 2.19 (user chốt 2026-11-15).

## 2. Endpoints

| # | Method + Path | Dùng khi | Ai gọi được | Thành công |
|---|---|---|---|---|
| 2.1 | `POST /api/v1/matches` | Tạo kèo (luồng A hoặc B) | Đội trưởng của đội tạo kèo | 201 |
| 2.2 | `GET /api/v1/matches` | Tìm kèo đang mở | Người dùng đã đăng nhập | 200 |
| 2.3 | `GET /api/v1/matches/{matchId}` | Xem chi tiết kèo | Người dùng đã đăng nhập | 200 |
| 2.4 | `PUT /api/v1/matches/{matchId}/cost-agreement` | Lưu thoả thuận chi phí (luồng A) | Đội trưởng đội tạo kèo | 200 |
| 2.5 | `POST /api/v1/matches/{matchId}/cancel` | Huỷ kèo | Đội trưởng đội tạo kèo, hoặc đội đã ghép (khi `CONFIRMED`) | 200 |
| 2.6 | `POST /api/v1/matches/{matchId}/requests` | Gửi yêu cầu ghép kèo (luồng A) | Đội trưởng của đội gửi | 201 |
| 2.7 | `GET /api/v1/matches/{matchId}/requests` | Xem yêu cầu gửi tới kèo của mình (luồng A) | Đội trưởng đội tạo kèo | 200 |
| 2.8 | `POST /api/v1/match-requests/{requestId}/accept` | Chấp nhận yêu cầu | Đội trưởng đội tạo kèo | 200 |
| 2.9 | `POST /api/v1/match-requests/{requestId}/reject` | Từ chối yêu cầu | Đội trưởng đội tạo kèo | 204 |
| 2.10 | `POST /api/v1/match-requests/{requestId}/withdraw` | Rút yêu cầu đã gửi | Đội trưởng đội gửi | 200 |
| 2.11 | `GET /api/v1/teams/{teamId}/matches` | Xem kèo của một đội | Người dùng đã đăng nhập | 200 |
| 2.12 | `GET /api/v1/teams/{teamId}/match-requests` | Theo dõi yêu cầu đội đã gửi | Đội trưởng của đội | 200 |
| 2.13 | `GET /api/v1/sports` | Chọn bộ môn khi tạo kèo / lọc tìm kèo | Người dùng đã đăng nhập | 200 |
| 2.14 | `GET /api/v1/admin/sports` | Admin xem mọi bộ môn | Admin | 200 |
| 2.15 | `PATCH /api/v1/admin/sports/{sportId}` | Admin bật/tắt bộ môn | Admin | 200 |
| 2.16 | `POST /api/v1/conversations/{conversationId}/messages` | Gửi tin nhắn (mọi cuộc trò chuyện) | Hai bên của cuộc trò chuyện (§1.6; mercenary: MERCENARY-001 §1.7) | 201 |
| 2.17 | `GET /api/v1/conversations/{conversationId}/messages` | Đọc lịch sử chat, bù tin sau khi kết nối lại | Hai bên của cuộc trò chuyện | 200 |
| 2.18 | `POST /api/v1/conversations/{conversationId}/messages/read` | Đánh dấu đã đọc tin của bên kia | Hai bên của cuộc trò chuyện | 204 |
| 2.19 | `GET /api/v1/conversations/{conversationId}` | Đầu phòng chat: hai bên, bên của mình, có gửi được không | Hai bên của cuộc trò chuyện | 200 |
| 2.20 | `GET /api/v1/profile/conversations` | Màn hình "Tin nhắn": mọi cuộc trò chuyện của tôi | Người dùng đã đăng nhập | 200 |

Lỗi chung `400 VALIDATION_ERROR`, `401 AUTH_001` (§1.1) áp dụng cho mọi endpoint; bảng lỗi từng endpoint liệt kê `400` khi có quy tắc riêng cần lưu ý.

---

### 2.1. Tạo kèo — `POST /api/v1/matches`

**Dùng khi:** đội trưởng đăng kèo tìm đối — đã đặt sân (luồng A: UC-M01, AC-M01) hoặc chưa có sân, đưa nhiều lựa chọn địa điểm/giờ (luồng B: UC-M02, AC-M02).
**Ai gọi được:** đội trưởng của đội `teamId`; đội phải `ACTIVE` (§1.2).

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `type` | string (enum) | Không | `HAS_VENUE` (luồng A, mặc định khi vắng) \| `NEED_VENUE` (luồng B) | `"NEED_VENUE"` |
| `teamId` | string (UUID) | Có | Đội tạo kèo | `"01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01"` |
| `sportId` | string (UUID) | Có | Bộ môn đang bật (2.13) | `"01930000-0000-7000-8000-000000000001"` |
| `venueId` | string (UUID) | Luồng A: có · Luồng B: **không gửi** | Địa điểm đang hoạt động (VENUE-001) | `"01933a10-4b5c-7d6e-8f70-81a2b3c4d5e1"` |
| `venueOptionIds` | string[] (UUID) | Luồng B: có · Luồng A: **không gửi** | 1..5 địa điểm khác nhau, đều đang hoạt động; giữ đúng thứ tự gửi | xem ví dụ luồng B |
| `format` | string (enum) | Có | `FIVE_A_SIDE` \| `SEVEN_A_SIDE` \| `ELEVEN_A_SIDE` | `"SEVEN_A_SIDE"` |
| `startsAt` | string (thời gian) | Luồng A: có · Luồng B: **không gửi** | ≥ giờ hiện tại + 1 giờ (BR-M01) | `"2026-11-12T11:00:00Z"` |
| `endsAt` | string (thời gian) | Luồng A: có · Luồng B: **không gửi** | > `startsAt` và ≤ `startsAt` + 12 giờ | `"2026-11-12T13:00:00Z"` |
| `timeOptions` | array | Luồng B: có · Luồng A: **không gửi** | 1..5 khung giờ, `startsAt` không trùng nhau | xem ví dụ luồng B |
| `timeOptions[].startsAt` | string (thời gian) | Có | ≥ giờ hiện tại + 1 giờ | `"2026-11-12T11:00:00Z"` |
| `timeOptions[].endsAt` | string (thời gian) | Có | > `startsAt` và ≤ `startsAt` + 12 giờ | `"2026-11-12T13:00:00Z"` |
| `expiresAt` | string (thời gian) | Có | > giờ hiện tại. Luồng A: `startsAt` − 7 ngày ≤ `expiresAt` ≤ `startsAt` (BR-M02). Luồng B: S − 7 ngày ≤ `expiresAt` ≤ S, với S = `startsAt` sớm nhất trong `timeOptions` (BR-M03) | `"2026-11-12T05:00:00Z"` |
| `skillLevels` | string[] | Không | ≤ 3 nhãn, không trùng, thuộc 9 nhãn trình độ (§4.1); vắng hoặc `[]` = nhận mọi trình độ | `["Nhiệt tình"]` |
| `ageNote` | string | Không | ≤ 100 ký tự | `"U23"` |
| `matchNature` | string | Không | ≤ 200 ký tự | `"Giao hữu"` |

Vi phạm quy tắc thời gian → `400 VALIDATION_ERROR`, `validationErrors[].field` là field vi phạm (`startsAt` / `endsAt` / `expiresAt`; luồng B: `timeOptions[i].startsAt` / `timeOptions[i].endsAt`). Message của `expiresAt`: luồng A "Thời hạn phải trong khoảng tối đa 7 ngày trước giờ đá đến giờ bắt đầu"; luồng B muộn hơn khung giờ sớm nhất: "Thời hạn không được muộn hơn giờ bắt đầu trận sớm nhất" (UC-M02 4a1). Gửi field của luồng kia (vd `venueId` khi `type = NEED_VENUE`), thiếu field bắt buộc của luồng, trùng địa điểm / trùng giờ bắt đầu, quá 5 lựa chọn → `400 VALIDATION_ERROR` gắn field đó.

Ví dụ luồng A:

```json
{
  "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01",
  "sportId": "01930000-0000-7000-8000-000000000001",
  "venueId": "01933a10-4b5c-7d6e-8f70-81a2b3c4d5e1",
  "format": "SEVEN_A_SIDE",
  "startsAt": "2026-11-12T11:00:00Z",
  "endsAt": "2026-11-12T13:00:00Z",
  "expiresAt": "2026-11-12T05:00:00Z",
  "skillLevels": ["Nhiệt tình"],
  "ageNote": "U23",
  "matchNature": "Giao hữu"
}
```

Ví dụ luồng B:

```json
{
  "type": "NEED_VENUE",
  "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01",
  "sportId": "01930000-0000-7000-8000-000000000001",
  "venueOptionIds": ["01933a10-4b5c-7d6e-8f70-81a2b3c4d5e2", "01933a10-4b5c-7d6e-8f70-81a2b3c4d5e3"],
  "format": "SEVEN_A_SIDE",
  "timeOptions": [
    { "startsAt": "2026-11-12T11:00:00Z", "endsAt": "2026-11-12T13:00:00Z" },
    { "startsAt": "2026-11-12T12:00:00Z", "endsAt": "2026-11-12T14:00:00Z" }
  ],
  "expiresAt": "2026-11-12T05:00:00Z",
  "skillLevels": []
}
```

**Response 201** — đối tượng Kèo (§4.2): `status = OPEN`, `opponentTeam = null`, thoả thuận chi phí rỗng. Luồng A: tạo kèo thành công cũng cập nhật `lastSelectedAt` của địa điểm (VENUE-001). Luồng B: `venue = null`, `venueOptions` / `timeOptions` có dữ liệu (`timeOptionId` dùng ở MATCH-002), `startsAt` / `endsAt` = khung giờ sớm nhất; `lastSelectedAt` chỉ cập nhật cho địa điểm được chốt khi ghép. Ví dụ luồng A:

```json
{
  "data": {
    "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b01",
    "type": "HAS_VENUE",
    "status": "OPEN",
    "sportId": "01930000-0000-7000-8000-000000000001",
    "sportName": "Bóng đá",
    "format": "SEVEN_A_SIDE",
    "skillLevels": ["Nhiệt tình"],
    "ageNote": "U23",
    "matchNature": "Giao hữu",
    "startsAt": "2026-11-12T11:00:00Z",
    "endsAt": "2026-11-12T13:00:00Z",
    "expiresAt": "2026-11-12T05:00:00Z",
    "authorTeam": {
      "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01",
      "teamName": "Team Alpha",
      "avatarUrl": null,
      "reliabilityScore": 80
    },
    "opponentTeam": null,
    "venue": {
      "venueId": "01933a10-4b5c-7d6e-8f70-81a2b3c4d5e1",
      "venueName": "Sân bóng Hoa Lư",
      "address": "2 Đinh Tiên Hoàng, Phường Đa Kao, TP.HCM",
      "latitude": 10.7881234,
      "longitude": 106.6991234,
      "venueStatus": "ACTIVE",
      "primaryImageUrl": null
    },
    "venueOptions": [],
    "timeOptions": [],
    "confirmedPairingId": null,
    "costAgreement": {
      "costType": null,
      "costSplit": null,
      "venueAmount": null,
      "drinksAmount": null,
      "depositAmount": null,
      "depositHolder": null,
      "customItems": []
    },
    "cancelledByTeamId": null,
    "cancelledAt": null,
    "cancelReason": null,
    "createdAt": "2026-11-09T03:00:00Z",
    "updatedAt": "2026-11-09T03:00:00Z"
  }
}
```

Ví dụ luồng B (chỉ các field khác luồng A; field còn lại như trên):

```json
{
  "data": {
    "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b02",
    "type": "NEED_VENUE",
    "status": "OPEN",
    "startsAt": "2026-11-12T11:00:00Z",
    "endsAt": "2026-11-12T13:00:00Z",
    "expiresAt": "2026-11-12T05:00:00Z",
    "venue": null,
    "venueOptions": [
      { "venueId": "01933a10-4b5c-7d6e-8f70-81a2b3c4d5e2", "venueName": "Sân Y", "address": "10 Lê Lợi, Phường Bến Nghé, TP.HCM",
        "latitude": 10.7769, "longitude": 106.7009, "venueStatus": "ACTIVE", "primaryImageUrl": null },
      { "venueId": "01933a10-4b5c-7d6e-8f70-81a2b3c4d5e3", "venueName": "Sân Z", "address": "5 Nguyễn Huệ, Phường Bến Nghé, TP.HCM",
        "latitude": 10.7740, "longitude": 106.7035, "venueStatus": "ACTIVE", "primaryImageUrl": null }
    ],
    "timeOptions": [
      { "timeOptionId": "01933b70-1a2b-7c3d-8e4f-5a6b7c8d9e11", "startsAt": "2026-11-12T11:00:00Z", "endsAt": "2026-11-12T13:00:00Z" },
      { "timeOptionId": "01933b70-1a2b-7c3d-8e4f-5a6b7c8d9e12", "startsAt": "2026-11-12T12:00:00Z", "endsAt": "2026-11-12T14:00:00Z" }
    ],
    "confirmedPairingId": null,
    "costAgreement": {
      "costType": null, "costSplit": null, "venueAmount": null, "drinksAmount": null,
      "depositAmount": null, "depositHolder": null, "customItems": []
    }
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Sai field / quy tắc thời gian / nhãn trình độ / field không đúng luồng / số lựa chọn | Hiện lỗi cạnh field |
| 422 | `MATCH_016` | `type = NEED_VENUE` khi luồng B chưa được phát hành (§1) | Ẩn lựa chọn "Chưa có sân"; nếu vẫn gặp → hiện message |
| 403 | `TEAM_002` | Người dùng không phải đội trưởng của `teamId` | Chỉ cho chọn đội mình làm đội trưởng; tải lại danh sách đội |
| 404 | `TEAM_001` | Không có đội `teamId` | Tải lại danh sách đội |
| 404 | `SPORT_001` | Không có bộ môn `sportId` | Tải lại danh sách bộ môn (2.13) |
| 404 | `VENUE_001` | Không có địa điểm `venueId` (hoặc một trong `venueOptionIds`) | Cho chọn lại địa điểm |
| 422 | `TEAM_015` | Đội đang tạm ngưng | Hiện message; không cho tạo kèo bằng đội này |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message; tải lại danh sách đội |
| 422 | `SPORT_002` | Bộ môn đang tắt | Hiện message; tải lại 2.13 |
| 422 | `VENUE_002` | Địa điểm (hoặc một lựa chọn địa điểm) đã ngừng hoạt động | Hiện message; cho chọn địa điểm khác |

---

### 2.2. Tìm kèo — `GET /api/v1/matches`

**Dùng khi:** màn hình tìm kèo (UC-M03 bước 1).
**Ai gọi được:** mọi người dùng đã đăng nhập.

Chỉ trả kèo **đang tìm đối**: `OPEN`, chưa quá `expiresAt`, đội tạo kèo đang `ACTIVE`, bộ môn đang bật — cả luồng A và luồng B (lọc bằng `type`).

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `type` | string (enum) | Không | `HAS_VENUE` \| `NEED_VENUE`; vắng = cả hai | `NEED_VENUE` |
| `sportId` | string (UUID) | Không | — | `01930000-0000-7000-8000-000000000001` |
| `format` | string (enum) | Không | `FIVE_A_SIDE` \| `SEVEN_A_SIDE` \| `ELEVEN_A_SIDE` | `SEVEN_A_SIDE` |
| `skillLevel` | string | Không | 1 trong 9 nhãn (§4.1). Khớp kèo có nhãn này **hoặc** kèo nhận mọi trình độ (`skillLevels = []`) | `Nhiệt tình` |
| `startsAtFrom` | string (thời gian) | Không | Lọc `startsAt` ≥ giá trị này; ≤ `startsAtTo`. Kèo B khớp khi **có một** khung giờ thoả | `2026-11-12T00:00:00Z` |
| `startsAtTo` | string (thời gian) | Không | Lọc `startsAt` ≤ giá trị này (kèo B: có một khung giờ thoả) | `2026-11-13T00:00:00Z` |
| `lat` | number | Không | [-90, 90]; gửi cùng `lng` | `10.78` |
| `lng` | number | Không | [-180, 180]; gửi cùng `lat` | `106.70` |
| `radiusKm` | number | Không | 0.1..50; mặc định `10` khi có toạ độ; chỉ gửi khi có toạ độ | `5` |
| `page` | integer | Không | ≥ 1, mặc định 1 | `1` |
| `pageSize` | integer | Không | 1..100, mặc định 20 | `20` |

Có toạ độ: khoảng cách tính tới toạ độ địa điểm của kèo — kèo B: tới lựa chọn địa điểm **gần nhất**; chỉ trả kèo trong bán kính.

**Thứ tự:** có toạ độ → `distanceKm` ↑, `startsAt` ↑, `matchId` ↑; không toạ độ → `startsAt` ↑, `matchId` ↑. Kèo B dùng `startsAt` = khung giờ sớm nhất.

**Response 200** — mảng Kèo rút gọn (§4.3) + `pagination`. Ví dụ một kèo A và một kèo B:

```json
{
  "data": [
    {
      "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b01",
      "type": "HAS_VENUE",
      "status": "OPEN",
      "sportId": "01930000-0000-7000-8000-000000000001",
      "sportName": "Bóng đá",
      "format": "SEVEN_A_SIDE",
      "skillLevels": ["Nhiệt tình"],
      "startsAt": "2026-11-12T11:00:00Z",
      "endsAt": "2026-11-12T13:00:00Z",
      "expiresAt": "2026-11-12T05:00:00Z",
      "authorTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01",
      "authorTeamName": "Team Alpha",
      "authorTeamReliabilityScore": 80,
      "opponentTeamId": null,
      "opponentTeamName": null,
      "venueId": "01933a10-4b5c-7d6e-8f70-81a2b3c4d5e1",
      "venueName": "Sân bóng Hoa Lư",
      "venueAddress": "2 Đinh Tiên Hoàng, Phường Đa Kao, TP.HCM",
      "venueOptionCount": null,
      "timeOptionCount": null,
      "distanceKm": 1.25,
      "createdAt": "2026-11-09T03:00:00Z"
    },
    {
      "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b02",
      "type": "NEED_VENUE",
      "status": "OPEN",
      "sportId": "01930000-0000-7000-8000-000000000001",
      "sportName": "Bóng đá",
      "format": "SEVEN_A_SIDE",
      "skillLevels": [],
      "startsAt": "2026-11-12T11:00:00Z",
      "endsAt": "2026-11-12T13:00:00Z",
      "expiresAt": "2026-11-12T05:00:00Z",
      "authorTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01",
      "authorTeamName": "Team Alpha",
      "authorTeamReliabilityScore": 80,
      "opponentTeamId": null,
      "opponentTeamName": null,
      "venueId": null,
      "venueName": null,
      "venueAddress": null,
      "venueOptionCount": 2,
      "timeOptionCount": 2,
      "distanceKm": 1.4,
      "createdAt": "2026-11-10T03:00:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 2 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Chỉ gửi một trong `lat`/`lng`; `radiusKm` khi không có toạ độ; `startsAtFrom` > `startsAtTo`; giá trị ngoài khoảng; `type` sai | Sửa bộ lọc; luôn gửi `lat` + `lng` cùng nhau |

---

### 2.3. Xem chi tiết kèo — `GET /api/v1/matches/{matchId}`

**Dùng khi:** mở trang chi tiết kèo (mọi trạng thái), gồm thoả thuận chi phí (UC-C01 bước 7); tải lại sau `MATCH_004` / `MATCH_015`.
**Ai gọi được:** mọi người dùng đã đăng nhập.

**Path params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `matchId` | string (UUID) | Có | — | `01933b6e-8f10-7a2c-9d41-2f6c1a7e5b01` |

**Response 200** — đối tượng Kèo (§4.2). `status` là trạng thái hiển thị (§1.3). `venue` và `venueOptions` luôn là thông tin địa điểm **mới nhất**, kể cả khi địa điểm đã ngừng hoạt động (BR-V10). Kèo B chưa ghép: `venue = null`, xem `venueOptions` / `timeOptions` (ví dụ ở 2.1). Kèo B đã ghép: `venue`, `startsAt`, `endsAt`, `costAgreement` (gồm tiền cọc) là phương án và thoả thuận đã chốt; `confirmedPairingId` là thương lượng đã chốt (mở lịch sử chat — MATCH-002). Ví dụ kèo A đã ghép, có thoả thuận chi phí:

```json
{
  "data": {
    "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b01",
    "type": "HAS_VENUE",
    "status": "CONFIRMED",
    "sportId": "01930000-0000-7000-8000-000000000001",
    "sportName": "Bóng đá",
    "format": "SEVEN_A_SIDE",
    "skillLevels": ["Nhiệt tình"],
    "ageNote": "U23",
    "matchNature": "Giao hữu",
    "startsAt": "2026-11-12T11:00:00Z",
    "endsAt": "2026-11-12T13:00:00Z",
    "expiresAt": "2026-11-12T05:00:00Z",
    "authorTeam": {
      "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01",
      "teamName": "Team Alpha",
      "avatarUrl": null,
      "reliabilityScore": 80
    },
    "opponentTeam": {
      "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
      "teamName": "Team Beta",
      "avatarUrl": "https://res.cloudinary.com/demo/image/upload/beta.png",
      "reliabilityScore": 75
    },
    "venue": {
      "venueId": "01933a10-4b5c-7d6e-8f70-81a2b3c4d5e1",
      "venueName": "Sân bóng Hoa Lư",
      "address": "2 Đinh Tiên Hoàng, Phường Đa Kao, TP.HCM",
      "latitude": 10.7881234,
      "longitude": 106.6991234,
      "venueStatus": "ACTIVE",
      "primaryImageUrl": null
    },
    "venueOptions": [],
    "timeOptions": [],
    "confirmedPairingId": null,
    "costAgreement": {
      "costType": "BOTH",
      "costSplit": "FIFTY_FIFTY",
      "venueAmount": 600000,
      "drinksAmount": 100000,
      "depositAmount": null,
      "depositHolder": null,
      "customItems": [
        { "itemId": "01933c30-6e7f-7a80-9b1c-2d3e4f5a6b01", "itemName": "Tiền trọng tài", "amount": 200000 }
      ]
    },
    "cancelledByTeamId": null,
    "cancelledAt": null,
    "cancelReason": null,
    "createdAt": "2026-11-09T03:00:00Z",
    "updatedAt": "2026-11-10T02:15:00Z"
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `MATCH_001` | Không có kèo `matchId` | Hiện "Không tìm thấy kèo", quay lại danh sách |

---

### 2.4. Lưu thoả thuận chi phí — `PUT /api/v1/matches/{matchId}/cost-agreement`

**Dùng khi:** đội tạo kèo **luồng A** nhập / sửa / xoá thoả thuận chi phí trên trang kèo (UC-C01, UC-C02, AC-C01). Kèo luồng B: chi phí + tiền cọc nhập trong thương lượng (MATCH-002 2.6); gọi 2.4 nhận `422 MATCH_016`.
**Ai gọi được:** đội trưởng đội tạo kèo; đội phải `ACTIVE`; chỉ khi kèo `OPEN` (trước khi ghép) (BR-C03).

Mỗi lần gửi **thay toàn bộ** thoả thuận cũ, kể cả danh sách khoản tự nhập. Gửi lại cùng nội dung cho cùng nội dung thoả thuận; `itemId`, `updatedAt` đổi mỗi lần lưu. Xoá thoả thuận: gửi `costType`, `costSplit`, `venueAmount`, `drinksAmount` là `null` và `customItems: []`. Kèo A không có tiền cọc.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `costType` | string (enum) \| null | Không | `VENUE_ONLY` \| `DRINKS_ONLY` \| `BOTH` | `"BOTH"` |
| `costSplit` | string (enum) \| null | Khi có `costType` | `FIFTY_FIFTY` \| `SEVENTY_THIRTY_HOME` \| `SEVENTY_THIRTY_LOSER` \| `LOSER_PAYS_DRINKS`; `LOSER_PAYS_DRINKS` chỉ khi `costType` có tiền nước (`DRINKS_ONLY` \| `BOTH`) | `"FIFTY_FIFTY"` |
| `venueAmount` | integer \| null | Khi `costType` = `VENUE_ONLY` \| `BOTH` | 1..1 000 000 000; phải `null` khi `costType` không có tiền sân | `600000` |
| `drinksAmount` | integer \| null | Khi `costType` = `DRINKS_ONLY` \| `BOTH` | 1..1 000 000 000; phải `null` khi `costType` không có tiền nước | `100000` |
| `customItems` | array | Không (vắng = `[]`) | ≤ 10 khoản | — |
| `customItems[].itemName` | string | Có | Không rỗng, ≤ 100 ký tự | `"Tiền trọng tài"` |
| `customItems[].amount` | integer | Có | 1..1 000 000 000 | `200000` |

`costType = null` → `costSplit`, `venueAmount`, `drinksAmount` cũng phải `null` (chỉ còn khoản tự nhập, hoặc xoá hết). Vi phạm quan hệ giữa các field → `400 VALIDATION_ERROR` gắn vào field sai.

```json
{
  "costType": "BOTH",
  "costSplit": "FIFTY_FIFTY",
  "venueAmount": 600000,
  "drinksAmount": 100000,
  "customItems": [ { "itemName": "Tiền trọng tài", "amount": 200000 } ]
}
```

**Response 200** — Thoả thuận chi phí (§4.5). `customItems` đúng thứ tự FE gửi; mỗi lần lưu, các khoản được cấp `itemId` mới (FE không giữ `itemId` cũ). `updatedAt` = thời điểm cập nhật kèo.

```json
{
  "data": {
    "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b01",
    "costType": "BOTH",
    "costSplit": "FIFTY_FIFTY",
    "venueAmount": 600000,
    "drinksAmount": 100000,
    "customItems": [
      { "itemId": "01933c30-6e7f-7a80-9b1c-2d3e4f5a6b01", "itemName": "Tiền trọng tài", "amount": 200000 }
    ],
    "updatedAt": "2026-11-10T02:15:00Z"
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Sai field, quan hệ `costType` ↔ số tiền ↔ `costSplit`, > 10 khoản | Hiện lỗi cạnh field |
| 403 | `TEAM_002` | Không phải đội trưởng đội tạo kèo | Ẩn nút sửa chi phí |
| 404 | `MATCH_001` | Không có kèo | Quay lại danh sách |
| 409 | `MATCH_003` | Kèo đã được ghép (`CONFIRMED`) | Tải lại kèo (2.3); ẩn nút sửa chi phí |
| 409 | `MATCH_004` | Kèo đã hết hạn | Tải lại kèo (2.3) |
| 409 | `MATCH_005` | Kèo đã huỷ / lưu trữ / hoàn thành | Tải lại kèo (2.3) |
| 409 | `MATCH_014` | Có thao tác khác đang cập nhật kèo cùng lúc | Hiện message; tải lại kèo rồi cho bấm lưu lại |
| 422 | `MATCH_016` | Kèo luồng B | Nhập chi phí trong thương lượng (MATCH-002 2.6) |
| 422 | `TEAM_015` | Đội tạo kèo đang tạm ngưng | Hiện message |
| 422 | `TEAM_016` | Đội tạo kèo đã giải tán | Hiện message; tải lại kèo |

---

### 2.5. Huỷ kèo — `POST /api/v1/matches/{matchId}/cancel`

**Dùng khi:** nút "Huỷ kèo" trên trang chi tiết kèo (UC-M08, AC-M06, Scenario 4).
**Ai gọi được:** đội trưởng của đội `teamId`, với `teamId` là:
- kèo `OPEN`: đội tạo kèo;
- kèo `CONFIRMED`: đội tạo kèo **hoặc** đội đã ghép. Kèo A: đội đã ghép huỷ = rút yêu cầu đã được chấp nhận (yêu cầu → `WITHDRAWN`), cùng kết quả với 2.10, trừ điểm một lần (BR-M12). Kèo B: đội đã ghép là đội nhận kèo của thương lượng đã chốt; thương lượng giữ `CONFIRMED`.

Đội `INACTIVE` vẫn huỷ được (§1.2).

| Kèo đang | Điều kiện | Lý do `reason` | Trừ điểm |
|---|---|---|---|
| `OPEN` (luồng A) | Không còn yêu cầu `PENDING` — phải từ chối hết trước (2.9) (BR-M05) | Tuỳ chọn | Không |
| `OPEN` (luồng B) | Không cần đóng thương lượng trước; mọi thương lượng đang trao đổi → `SUPERSEDED` | Tuỳ chọn | Không |
| `CONFIRMED` | Trước `startsAt` | **Bắt buộc** | Đội huỷ, theo §1.5 |

Server kiểm trạng thái kèo (`MATCH_004`, `MATCH_005`, `MATCH_015`) **trước** khi kiểm lý do (`MATCH_011`) và giờ trận (`MATCH_012`).

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` | string (UUID) | Có | Đội thực hiện huỷ | `"01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02"` |
| `expectedStatus` | string (enum) | Có | `OPEN` \| `CONFIRMED` — trạng thái kèo FE đang hiển thị (§1.4) | `"CONFIRMED"` |
| `reason` | string | Khi kèo `CONFIRMED` (thiếu/rỗng → `422 MATCH_011`) | ≤ 500 ký tự | `"Thiếu người"` |

```json
{ "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02", "expectedStatus": "CONFIRMED", "reason": "Thiếu người" }
```

**Response 200**

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `matchId` | string (UUID) | Kèo đã huỷ |
| `status` | string | Luôn `CANCELLED` |
| `cancelledByTeamId` | string (UUID) | Đội huỷ (= `teamId` gửi lên) |
| `cancelledAt` | string (thời gian) | Thời điểm huỷ |
| `cancelReason` | string \| null | Lý do đã lưu (`null` khi huỷ `OPEN` không nhập lý do) |
| `reliabilityPenalty` | integer | `0` khi huỷ `OPEN`; `-2` \| `-5` \| `-10` khi huỷ `CONFIRMED` (§1.5) |

```json
{
  "data": {
    "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b01",
    "status": "CANCELLED",
    "cancelledByTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
    "cancelledAt": "2026-11-11T09:00:00Z",
    "cancelReason": "Thiếu người",
    "reliabilityPenalty": -5
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu `teamId` / `expectedStatus`, `expectedStatus` ngoài `OPEN`/`CONFIRMED`, `reason` > 500 | Hiện lỗi cạnh field |
| 403 | `TEAM_002` | Không phải đội trưởng của `teamId` | Ẩn nút huỷ |
| 403 | `MATCH_009` | `teamId` không phải đội tạo kèo, cũng không phải đội đã ghép (khi `CONFIRMED`) | Gửi đúng `teamId` của đội mình trong kèo |
| 404 | `MATCH_001` | Không có kèo | Quay lại danh sách |
| 404 | `TEAM_001` | Không có đội `teamId` | Tải lại kèo |
| 409 | `MATCH_004` | Kèo đã hết hạn | Tải lại kèo (2.3) |
| 409 | `MATCH_005` | Kèo đã huỷ / lưu trữ / hoàn thành (kể cả bấm huỷ lần hai) | Tải lại kèo (2.3); ẩn nút huỷ |
| 409 | `MATCH_014` | Có thao tác khác đang cập nhật kèo cùng lúc | Tải lại kèo rồi cho bấm lại |
| 409 | `MATCH_015` | Trạng thái kèo khác `expectedStatus` (§1.4) — không huỷ, không trừ điểm | Hiện message, tải lại (2.3); người dùng tự bấm lại |
| 422 | `MATCH_010` | Huỷ kèo A `OPEN` khi còn yêu cầu `PENDING` (không áp cho kèo B) | Hiện message; mở danh sách yêu cầu (2.7) để từ chối hết |
| 422 | `MATCH_011` | Huỷ kèo `CONFIRMED` không có lý do | Bắt buộc nhập lý do trên hộp xác nhận |
| 422 | `MATCH_012` | Trận đã bắt đầu (giờ hiện tại ≥ `startsAt`) | Hiện message; ẩn nút huỷ |
| 422 | `TEAM_016` | Đội `teamId` đã giải tán | Hiện message; tải lại kèo |

---

### 2.6. Gửi yêu cầu ghép kèo — `POST /api/v1/matches/{matchId}/requests`

**Dùng khi:** nút "Gửi yêu cầu" trên kèo **luồng A** đang mở (UC-M03). Kèo luồng B dùng "Nhận kèo" (MATCH-002 2.1).
**Ai gọi được:** đội trưởng của đội `teamId`; đội phải `ACTIVE`; không phải đội tạo kèo.

Tạo yêu cầu `PENDING` và mở **cuộc trò chuyện** giữa đội gửi và đội tạo kèo (`conversationId` trong response — BR-N04); đội **gửi lại** sau khi rút / hết hạn → dùng tiếp cuộc trò chuyện cũ (cùng `conversationId`, giữ lịch sử). Hai đội trưởng nhắn được ngay khi yêu cầu còn `PENDING` (§1.6). Mỗi đội có tối đa một yêu cầu đang hiệu lực cho một kèo: sau khi rút (`WITHDRAWN`) hoặc hết hạn (`EXPIRED`) được gửi lại; sau khi bị từ chối (`REJECTED`) hoặc bị thay thế (`SUPERSEDED`) thì không (`409 MATCH_006`). Hai đội có cùng đội trưởng vẫn gửi được cho nhau (BR-M18).

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` | string (UUID) | Có | Đội gửi | `"01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02"` |

```json
{ "teamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02" }
```

**Response 201** — Yêu cầu ghép kèo (§4.4); `startsAt`, `authorTeamId`, `authorTeamName` = `null`.

```json
{
  "data": {
    "requestId": "01933c20-5d6e-7f80-9a1b-2c3d4e5f6a01",
    "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b01",
    "conversationId": "01933d41-1a2b-7c3d-8e4f-5a6b7c8d9e32",
    "requesterTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
    "requesterTeamName": "Team Beta",
    "requesterTeamReliabilityScore": 75,
    "status": "PENDING",
    "reason": null,
    "requestedAt": "2026-11-10T01:00:00Z",
    "respondedAt": null,
    "withdrawnAt": null,
    "startsAt": null,
    "authorTeamId": null,
    "authorTeamName": null
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu / sai định dạng `teamId` | Hiện lỗi |
| 403 | `TEAM_002` | Không phải đội trưởng của `teamId` | Chỉ cho chọn đội mình làm đội trưởng |
| 404 | `MATCH_001` | Không có kèo | Quay lại danh sách |
| 404 | `TEAM_001` | Không có đội `teamId` | Tải lại danh sách đội |
| 409 | `MATCH_003` | Kèo đã được ghép (UC-M03 3a) | Hiện "Kèo đã được ghép"; tải lại kết quả tìm kèo |
| 409 | `MATCH_004` | Kèo đã hết hạn | Hiện message; tải lại kết quả tìm kèo |
| 409 | `MATCH_005` | Kèo đã huỷ / lưu trữ / hoàn thành | Tải lại kèo |
| 409 | `MATCH_006` | Đội đã có yêu cầu cho kèo này (đang chờ, đã chấp nhận, bị từ chối hoặc bị thay thế) | Hiện message; ẩn nút gửi; xem yêu cầu ở 2.12 |
| 409 | `MATCH_014` | Có thao tác khác đang cập nhật kèo cùng lúc | Cho bấm gửi lại |
| 422 | `MATCH_007` | `teamId` là đội tạo kèo | Ẩn nút gửi trên kèo của đội mình |
| 422 | `MATCH_013` | Đội tạo kèo không còn hoạt động | Hiện message; tải lại kết quả tìm kèo |
| 422 | `MATCH_016` | Kèo luồng B | Dùng "Nhận kèo" (MATCH-002 2.1) |
| 422 | `SPORT_002` | Bộ môn của kèo đang tắt | Hiện message; tải lại kết quả tìm kèo |
| 422 | `TEAM_015` | Đội gửi đang tạm ngưng | Hiện message |
| 422 | `TEAM_016` | Đội gửi đã giải tán | Hiện message; tải lại danh sách đội |

---

### 2.7. Xem yêu cầu gửi tới kèo — `GET /api/v1/matches/{matchId}/requests`

**Dùng khi:** đội tạo kèo **luồng A** xem danh sách đội muốn ghép để chấp nhận / từ chối (UC-M04 bước 1). Kèo luồng B: xem thương lượng (MATCH-002 2.2).
**Ai gọi được:** đội trưởng đội tạo kèo (đội `INACTIVE` vẫn xem được).

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `status` | string (enum) | Không | `PENDING` \| `ALL`; mặc định `PENDING` (lọc theo trạng thái hiển thị, §1.3) | `ALL` |
| `page`, `pageSize` | integer | Không | §1.1 | `1`, `20` |

**Thứ tự:** `requestedAt` ↑, `requestId` ↑ (gửi trước lên trước).

**Response 200** — mảng Yêu cầu ghép kèo (§4.4) + `pagination`; `startsAt`, `authorTeamId`, `authorTeamName` = `null`.

```json
{
  "data": [
    {
      "requestId": "01933c20-5d6e-7f80-9a1b-2c3d4e5f6a01",
      "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b01",
      "conversationId": "01933d41-1a2b-7c3d-8e4f-5a6b7c8d9e32",
      "requesterTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
      "requesterTeamName": "Team Beta",
      "requesterTeamReliabilityScore": 75,
      "status": "PENDING",
      "reason": null,
      "requestedAt": "2026-11-10T01:00:00Z",
      "respondedAt": null,
      "withdrawnAt": null,
      "startsAt": null,
      "authorTeamId": null,
      "authorTeamName": null
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `status` ngoài `PENDING`/`ALL`; phân trang sai | Sửa tham số |
| 403 | `TEAM_002` | Không phải đội trưởng đội tạo kèo | Ẩn mục "Yêu cầu" với người không phải đội trưởng |
| 404 | `MATCH_001` | Không có kèo | Quay lại danh sách |
| 422 | `MATCH_016` | Kèo luồng B | Mở danh sách thương lượng (MATCH-002 2.2) |
| 422 | `TEAM_016` | Đội tạo kèo đã giải tán | Hiện message |

---

### 2.8. Chấp nhận yêu cầu — `POST /api/v1/match-requests/{requestId}/accept`

**Dùng khi:** nút "Chấp nhận" trên một yêu cầu `PENDING` (UC-M04, AC-M03, Scenario 6).
**Ai gọi được:** đội trưởng đội tạo kèo; đội phải `ACTIVE`.

Kết quả: yêu cầu → `ACCEPTED`; kèo → `CONFIRMED` với đội đối thủ = đội gửi; mọi yêu cầu `PENDING` khác của kèo → `SUPERSEDED`. Mỗi kèo có tối đa một yêu cầu được chấp nhận (BR-M06). Không có request body.

**Path params:** `requestId` — string (UUID), bắt buộc.

**Response 200**

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `matchId` | string (UUID) | Kèo |
| `matchStatus` | string | Luôn `CONFIRMED` |
| `requestId` | string (UUID) | Yêu cầu được chấp nhận |
| `requestStatus` | string | Luôn `ACCEPTED` |
| `opponentTeamId` | string (UUID) | Đội đối thủ (đội gửi) |
| `opponentTeamName` | string | Tên đội đối thủ |
| `respondedAt` | string (thời gian) | Thời điểm chấp nhận |

```json
{
  "data": {
    "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b01",
    "matchStatus": "CONFIRMED",
    "requestId": "01933c20-5d6e-7f80-9a1b-2c3d4e5f6a01",
    "requestStatus": "ACCEPTED",
    "opponentTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
    "opponentTeamName": "Team Beta",
    "respondedAt": "2026-11-10T02:00:00Z"
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 403 | `TEAM_002` | Không phải đội trưởng đội tạo kèo | Ẩn nút chấp nhận |
| 404 | `MATCH_002` | Không có yêu cầu `requestId` | Tải lại danh sách yêu cầu (2.7) |
| 409 | `MATCH_003` | Kèo đã được ghép bởi yêu cầu khác (kể cả hai lần chấp nhận cùng lúc, hoặc bấm chấp nhận lần hai) | Tải lại kèo (2.3) và danh sách yêu cầu |
| 409 | `MATCH_004` | Kèo đã hết hạn | Tải lại kèo (2.3) |
| 409 | `MATCH_005` | Kèo đã huỷ / lưu trữ / hoàn thành | Tải lại kèo (2.3) |
| 409 | `MATCH_008` | Yêu cầu không còn `PENDING` (đã rút, bị từ chối, bị thay thế, hết hạn) | Tải lại danh sách yêu cầu (2.7) |
| 409 | `MATCH_014` | Có thao tác khác đang cập nhật kèo cùng lúc | Tải lại danh sách yêu cầu rồi cho bấm lại |
| 422 | `MATCH_013` | Đội gửi không còn hoạt động | Hiện message; chọn yêu cầu khác |
| 422 | `SPORT_002` | Bộ môn của kèo đang tắt | Hiện message |
| 422 | `TEAM_015` | Đội tạo kèo đang tạm ngưng | Hiện message |
| 422 | `TEAM_016` | Đội tạo kèo đã giải tán | Hiện message; tải lại kèo |

---

### 2.9. Từ chối yêu cầu — `POST /api/v1/match-requests/{requestId}/reject`

**Dùng khi:** nút "Từ chối" trên yêu cầu `PENDING`; bắt buộc từ chối hết trước khi huỷ kèo `OPEN` (BR-M05). Không ảnh hưởng uy tín.
**Ai gọi được:** đội trưởng đội tạo kèo (đội `INACTIVE` vẫn từ chối được).

**Path params:** `requestId` — string (UUID), bắt buộc.

**Request body** (tuỳ chọn — có thể không gửi body)

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `reason` | string | Không | ≤ 500 ký tự | `"Đã có lịch"` |

```json
{ "reason": "Đã có lịch" }
```

**Response 204** — không body. Yêu cầu → `REJECTED`; FE tự cập nhật dòng yêu cầu hoặc tải lại 2.7.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `reason` > 500 ký tự | Hiện lỗi cạnh field |
| 403 | `TEAM_002` | Không phải đội trưởng đội tạo kèo | Ẩn nút từ chối |
| 404 | `MATCH_002` | Không có yêu cầu | Tải lại danh sách yêu cầu (2.7) |
| 409 | `MATCH_004` | Kèo đã hết hạn (kể cả kèo đã ở `EXPIRED`) | Tải lại kèo (2.3) |
| 409 | `MATCH_008` | Yêu cầu không còn `PENDING` (kể cả bấm từ chối lần hai) | Tải lại danh sách yêu cầu (2.7) |
| 409 | `MATCH_014` | Có thao tác khác đang cập nhật cùng lúc | Tải lại danh sách yêu cầu rồi cho bấm lại |
| 422 | `TEAM_016` | Đội tạo kèo đã giải tán | Hiện message |

---

### 2.10. Rút yêu cầu — `POST /api/v1/match-requests/{requestId}/withdraw`

**Dùng khi:** nút "Rút yêu cầu" trên yêu cầu đội mình đã gửi (Business_Rules §2.2).
**Ai gọi được:** đội trưởng đội gửi (đội `INACTIVE` vẫn rút được).

| Yêu cầu đang | Kết quả | Lý do `reason` | Trừ điểm |
|---|---|---|---|
| `PENDING` | Yêu cầu → `WITHDRAWN`; kèo giữ nguyên | Tuỳ chọn | Không |
| `ACCEPTED` (trước `startsAt`) | Yêu cầu → `WITHDRAWN`; kèo → `CANCELLED`, đội huỷ = đội gửi | **Bắt buộc** | Đội gửi, theo §1.5 |

Server kiểm trạng thái kèo / yêu cầu **trước**, thiếu lý do (`MATCH_011`) và giờ trận (`MATCH_012`) **sau**. Luôn trả `200` có body (kể cả rút `PENDING`).

**Path params:** `requestId` — string (UUID), bắt buộc.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `expectedStatus` | string (enum) | Có | `PENDING` \| `ACCEPTED` — trạng thái yêu cầu FE đang hiển thị (§1.4) | `"ACCEPTED"` |
| `reason` | string | Khi yêu cầu `ACCEPTED` (thiếu/rỗng → `422 MATCH_011`) | ≤ 500 ký tự | `"Không đủ người"` |

```json
{ "expectedStatus": "ACCEPTED", "reason": "Không đủ người" }
```

**Response 200**

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `requestId` | string (UUID) | Yêu cầu đã rút |
| `matchId` | string (UUID) | Kèo |
| `status` | string | Luôn `WITHDRAWN` |
| `withdrawnAt` | string (thời gian) | Thời điểm rút |
| `matchStatus` | string | Trạng thái kèo sau thao tác: giữ nguyên khi rút `PENDING` (vd `OPEN`); `CANCELLED` khi rút `ACCEPTED` |
| `reliabilityPenalty` | integer | `0` khi rút `PENDING`; `-2` \| `-5` \| `-10` khi rút `ACCEPTED` (§1.5) |

```json
{
  "data": {
    "requestId": "01933c20-5d6e-7f80-9a1b-2c3d4e5f6a01",
    "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b01",
    "status": "WITHDRAWN",
    "withdrawnAt": "2026-11-12T08:30:00Z",
    "matchStatus": "CANCELLED",
    "reliabilityPenalty": -10
  }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu `expectedStatus`, `expectedStatus` ngoài `PENDING`/`ACCEPTED`, `reason` > 500 | Hiện lỗi cạnh field |
| 403 | `TEAM_002` | Không phải đội trưởng đội gửi | Ẩn nút rút |
| 404 | `MATCH_002` | Không có yêu cầu | Tải lại danh sách yêu cầu (2.12) |
| 409 | `MATCH_004` | Kèo đã hết hạn | Tải lại (2.12) |
| 409 | `MATCH_005` | Rút yêu cầu `ACCEPTED` khi kèo không còn `CONFIRMED` (vd đội tạo kèo vừa huỷ) | Tải lại kèo (2.3) |
| 409 | `MATCH_008` | Yêu cầu không còn `PENDING`/`ACCEPTED` (đã rút, bị từ chối, bị thay thế, hết hạn) — kể cả bấm rút lần hai | Tải lại danh sách yêu cầu (2.12) |
| 409 | `MATCH_014` | Có thao tác khác đang cập nhật cùng lúc | Tải lại rồi cho bấm lại |
| 409 | `MATCH_015` | Trạng thái yêu cầu khác `expectedStatus` (§1.4), hoặc yêu cầu vừa đổi trạng thái trong lúc xử lý (vd vừa được chấp nhận) — không rút, không trừ điểm | Hiện message, tải lại (2.12); người dùng tự bấm lại |
| 422 | `MATCH_011` | Rút yêu cầu `ACCEPTED` không có lý do | Bắt buộc nhập lý do trên hộp xác nhận |
| 422 | `MATCH_012` | Trận đã bắt đầu | Hiện message; ẩn nút rút |
| 422 | `TEAM_016` | Đội gửi đã giải tán | Hiện message |

Bấm rút hai lần: lần sau nhận `MATCH_008` (gửi lần lượt) hoặc có thể `MATCH_015` (hai request gần như cùng lúc). FE xử lý hai mã như nhau — tải lại trạng thái yêu cầu; chỉ có một lần rút, không trừ điểm thêm.

---

### 2.11. Xem kèo của một đội — `GET /api/v1/teams/{teamId}/matches`

**Dùng khi:** trang "Kèo của đội" — kèo đội đã tạo hoặc đã ghép, mọi trạng thái.
**Ai gọi được:** mọi người dùng đã đăng nhập.

**Path / Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` (path) | string (UUID) | Có | — | `01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01` |
| `status` | string (enum) | Không | 1 trong 6 trạng thái kèo (§4.1) hoặc `ALL`; mặc định `ALL`; lọc theo trạng thái hiển thị (§1.3) | `CONFIRMED` |
| `role` | string (enum) | Không | `AUTHOR` (đội tạo kèo) \| `OPPONENT` (đội đã ghép) \| `ALL`; mặc định `ALL` | `AUTHOR` |
| `page`, `pageSize` | integer | Không | §1.1 | `1`, `20` |

**Thứ tự:** `startsAt` ↓, `matchId` ↓ (trận gần nhất / tương lai xa nhất lên trước).

**Response 200** — mảng Kèo rút gọn (§4.3) + `pagination` (cả kèo A và B); `distanceKm` luôn `null`.

```json
{
  "data": [
    {
      "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b01",
      "type": "HAS_VENUE",
      "status": "CONFIRMED",
      "sportId": "01930000-0000-7000-8000-000000000001",
      "sportName": "Bóng đá",
      "format": "SEVEN_A_SIDE",
      "skillLevels": [],
      "startsAt": "2026-11-12T11:00:00Z",
      "endsAt": "2026-11-12T13:00:00Z",
      "expiresAt": "2026-11-12T05:00:00Z",
      "authorTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01",
      "authorTeamName": "Team Alpha",
      "authorTeamReliabilityScore": 80,
      "opponentTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
      "opponentTeamName": "Team Beta",
      "venueId": "01933a10-4b5c-7d6e-8f70-81a2b3c4d5e1",
      "venueName": "Sân bóng Hoa Lư",
      "venueAddress": "2 Đinh Tiên Hoàng, Phường Đa Kao, TP.HCM",
      "venueOptionCount": null,
      "timeOptionCount": null,
      "distanceKm": null,
      "createdAt": "2026-11-09T03:00:00Z"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `status` / `role` ngoài danh sách; phân trang sai | Sửa tham số |
| 404 | `TEAM_001` | Không có đội | Hiện "Không tìm thấy đội" |

---

### 2.12. Theo dõi yêu cầu đội đã gửi — `GET /api/v1/teams/{teamId}/match-requests`

**Dùng khi:** trang "Yêu cầu đã gửi" — theo dõi kết quả, rút yêu cầu; tải lại sau `MATCH_015` ở 2.10.
**Ai gọi được:** đội trưởng của đội `teamId` (đội `INACTIVE` vẫn xem được).

**Path / Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `teamId` (path) | string (UUID) | Có | — | `01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02` |
| `status` | string (enum) | Không | 1 trong 6 trạng thái yêu cầu (§4.1) hoặc `ALL`; mặc định `ALL`; lọc theo trạng thái hiển thị (§1.3) | `PENDING` |
| `page`, `pageSize` | integer | Không | §1.1 | `1`, `20` |

**Thứ tự:** `requestedAt` ↓, `requestId` ↓ (mới gửi lên trước).

**Response 200** — mảng Yêu cầu ghép kèo (§4.4) + `pagination`; có thêm ngữ cảnh kèo `startsAt`, `authorTeamId`, `authorTeamName`.

```json
{
  "data": [
    {
      "requestId": "01933c20-5d6e-7f80-9a1b-2c3d4e5f6a01",
      "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b01",
      "conversationId": "01933d41-1a2b-7c3d-8e4f-5a6b7c8d9e32",
      "requesterTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
      "requesterTeamName": "Team Beta",
      "requesterTeamReliabilityScore": 75,
      "status": "ACCEPTED",
      "reason": null,
      "requestedAt": "2026-11-10T01:00:00Z",
      "respondedAt": "2026-11-10T02:00:00Z",
      "withdrawnAt": null,
      "startsAt": "2026-11-12T11:00:00Z",
      "authorTeamId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01",
      "authorTeamName": "Team Alpha"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `status` ngoài danh sách; phân trang sai | Sửa tham số |
| 403 | `TEAM_002` | Không phải đội trưởng của đội | Ẩn mục với người không phải đội trưởng |
| 404 | `TEAM_001` | Không có đội | Hiện "Không tìm thấy đội" |
| 422 | `TEAM_016` | Đội đã giải tán | Hiện message |

---

### 2.13. Danh sách bộ môn đang bật — `GET /api/v1/sports`

**Dùng khi:** chọn bộ môn ở form tạo kèo, bộ lọc tìm kèo.
**Ai gọi được:** mọi người dùng đã đăng nhập.

**Query params:** `page`, `pageSize` (§1.1). **Thứ tự:** `sportName` ↑, `sportId` ↑. Chỉ trả bộ môn đang bật.

**Response 200** — mảng Bộ môn (§4.6) + `pagination`.

```json
{
  "data": [
    { "sportId": "01930000-0000-7000-8000-000000000001", "sportName": "Bóng đá", "isActive": true, "createdAt": "2026-11-01T00:00:00Z" }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Phân trang sai | Sửa tham số |

---

### 2.14. Admin xem mọi bộ môn — `GET /api/v1/admin/sports`

**Dùng khi:** trang quản trị bộ môn.
**Ai gọi được:** admin.

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `isActive` | boolean | Không | `true` \| `false`; vắng = tất cả | `false` |
| `page`, `pageSize` | integer | Không | §1.1 | `1`, `20` |

**Thứ tự:** như 2.13.

**Response 200** — mảng Bộ môn (§4.6) + `pagination` (cùng dạng 2.13, gồm cả bộ môn `isActive = false`).

```json
{
  "data": [
    { "sportId": "01930000-0000-7000-8000-000000000002", "sportName": "Bóng rổ", "isActive": false, "createdAt": "2026-11-01T00:00:00Z" }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 1 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `isActive` không phải boolean; phân trang sai | Sửa tham số |
| 403 | `AUTH_004` | Không phải admin | Ẩn trang quản trị |

---

### 2.15. Admin bật/tắt bộ môn — `PATCH /api/v1/admin/sports/{sportId}`

**Dùng khi:** công tắc bật/tắt trên trang quản trị bộ môn (Overview §9.7, Business_Rules §5.4).
**Ai gọi được:** admin.

Tắt bộ môn: chặn tạo kèo mới; kèo `OPEN` của bộ môn ẩn khỏi tìm kèo (2.2), không gửi / chấp nhận yêu cầu được (`SPORT_002`); kèo không tự huỷ; kèo đã ghép tiếp tục vòng đời (BR-M15). Đặt giá trị đang có → `200`, không đổi gì.

**Path params:** `sportId` — string (UUID), bắt buộc.

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `isActive` | boolean | Có | — | `false` |

```json
{ "isActive": false }
```

**Response 200** — Bộ môn (§4.6) sau cập nhật.

```json
{
  "data": { "sportId": "01930000-0000-7000-8000-000000000001", "sportName": "Bóng đá", "isActive": false, "createdAt": "2026-11-01T00:00:00Z" }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu / sai kiểu `isActive` | Hiện lỗi |
| 403 | `AUTH_004` | Không phải admin | Ẩn trang quản trị |
| 404 | `SPORT_001` | Không có bộ môn | Tải lại danh sách (2.14) |

---

### 2.16. Gửi tin nhắn — `POST /api/v1/conversations/{conversationId}/messages`

**Dùng khi:** ô chat của phòng chat — giữa hai đội trong một kèo (UC-N01, AC-N01, AC-N03; luồng A từ yêu cầu ghép kèo, luồng B từ thương lượng) hoặc theo đơn ứng tuyển mercenary (MERCENARY-001 §1.7).
**Ai gọi được:** một trong hai bên của cuộc trò chuyện, khi cuộc trò chuyện đang **nhắn được** (chat hai đội: §1.6; mercenary: MERCENARY-001 §1.7). Không gửi `teamId` — người gửi luôn là người gọi API, bên gửi do hệ thống xác định.

Tin được lưu rồi mới đẩy cho cả hai bên qua frame `MESSAGE_CREATED` (§1.7). Không sửa / xoá tin. Tin được lưu tới 90 ngày sau khi kèo kết thúc (BR-N01).

**Request body**

| Field | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `content` | string | Có | Sau khi bỏ khoảng trắng đầu/cuối: 1..1000 ký tự | `"18:00 sân Y được không?"` |

```json
{ "content": "18:00 sân Y được không?" }
```

**Response 201** — Tin nhắn (§4.8).

```json
{
  "data": {
    "messageId": "01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e01",
    "conversationId": "01933d41-1a2b-7c3d-8e4f-5a6b7c8d9e31",
    "senderPartyType": "TEAM",
    "senderPartyId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
    "senderUserId": "01932e00-5a6b-7c7d-8e8f-9a0b1c2d3e12",
    "senderName": "Nam",
    "senderAvatarUrl": null,
    "content": "18:00 sân Y được không?",
    "isRead": false,
    "createdAt": "2026-11-10T08:00:00Z"
  }
}
```

FE hiển thị tin từ response; frame `MESSAGE_CREATED` của chính tin này (cùng `messageId`) bỏ qua để không hiện trùng.

**Lỗi** (thứ tự kiểm từ trên xuống)

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `content` rỗng sau trim / > 1000 ký tự | Hiện lỗi cạnh ô nhập |
| 404 | `CHAT_002` | Không có cuộc trò chuyện (hoặc đã bị xoá theo hạn lưu) | Quay lại |
| 404 | `USER_001` | Tài khoản đã xoá (token còn hạn) | Xoá token, về đăng nhập |
| 403 | `CHAT_003` | Không tham gia cuộc trò chuyện (§1.6 — vd vừa chuyển quyền đội trưởng) | Khoá ô chat, tải lại 2.19 |
| 409 | `MATCH_004` | Chat hai đội: yêu cầu còn chờ / thương lượng còn mở nhưng kèo đã quá hạn bài đăng — kèo chuyển `EXPIRED` (BR-M17) | Hiện "Kèo đã hết hạn"; khoá ô chat, vẫn cho đọc |
| 409 | `CHAT_004` | Cuộc trò chuyện chỉ còn xem (chat hai đội: đội tham gia không được ghép, hoặc người gọi là người đã giải tán đội của mình — §1.6; mercenary: MERCENARY-001 §1.7) | Khoá ô chat, vẫn cho đọc |
| 409 | `MATCH_014` | Hiếm — thao tác khác cập nhật kèo cùng lúc | Cho gửi lại |

---

### 2.17. Lịch sử chat — `GET /api/v1/conversations/{conversationId}/messages`

**Dùng khi:** mở phòng chat (trang mới nhất), cuộn lên xem tin cũ (`before`), bù tin sau khi kết nối realtime lại (`after`).
**Ai gọi được:** hai bên của cuộc trò chuyện; mọi trạng thái (kể cả khi chỉ còn xem).

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `before` | string (UUID) | Không | `messageId` trong cuộc trò chuyện này; lấy tin **cũ hơn** tin đó | `01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e01` |
| `after` | string (UUID) | Không | `messageId` trong cuộc trò chuyện này; lấy tin **mới hơn** tin đó | `01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e01` |
| `limit` | integer | Không | 1..100, mặc định 50 | `50` |

Không gửi đồng thời `before` và `after` (`400 VALIDATION_ERROR`, `field` = `after`).

| Gửi | Trả | Thứ tự `items` | `hasMore = true` khi |
|---|---|---|---|
| Không con trỏ | `limit` tin mới nhất | Mới nhất trước | Còn tin cũ hơn |
| `before` | `limit` tin ngay trước tin `before` | Mới nhất trước | Còn tin cũ hơn |
| `after` | `limit` tin ngay sau tin `after` | **Cũ nhất trước** | Còn tin mới hơn → gọi tiếp với `after` = `messageId` cuối |

Thứ tự theo thời điểm gửi `createdAt`; hai tin cùng thời điểm xếp theo `messageId`. Bản thân tin mốc (`before` / `after`) không có trong kết quả.

**Response 200** — `items`: mảng Tin nhắn (§4.8), `hasMore`.

```json
{
  "data": {
    "items": [
      { "messageId": "01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e03", "conversationId": "01933d41-1a2b-7c3d-8e4f-5a6b7c8d9e31",
        "senderPartyType": "TEAM", "senderPartyId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02", "senderUserId": "01932e00-5a6b-7c7d-8e8f-9a0b1c2d3e13",
        "senderName": "Lan", "senderAvatarUrl": "https://res.cloudinary.com/demo/image/upload/lan.jpg",
        "content": "Mình là đội trưởng mới của Beta, giữ 18:00 nhé", "isRead": false, "createdAt": "2026-11-10T09:00:00Z" },
      { "messageId": "01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e02", "conversationId": "01933d41-1a2b-7c3d-8e4f-5a6b7c8d9e31",
        "senderPartyType": "TEAM", "senderPartyId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01", "senderUserId": "01932e00-5a6b-7c7d-8e8f-9a0b1c2d3e11",
        "senderName": "Người dùng đã xóa", "senderAvatarUrl": null,
        "content": "OK", "isRead": true, "createdAt": "2026-11-10T08:01:00Z" },
      { "messageId": "01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e01", "conversationId": "01933d41-1a2b-7c3d-8e4f-5a6b7c8d9e31",
        "senderPartyType": "TEAM", "senderPartyId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02", "senderUserId": "01932e00-5a6b-7c7d-8e8f-9a0b1c2d3e12",
        "senderName": "Nam", "senderAvatarUrl": null,
        "content": "18:00 sân Y được không?", "isRead": true, "createdAt": "2026-11-10T08:00:00Z" }
    ],
    "hasMore": false
  }
}
```

Ví dụ: đội Beta, Nam nhắn rồi chuyển quyền đội trưởng cho Lan → tin cũ vẫn là "Team Beta · Nam", tin mới "Team Beta · Lan"; người gửi tin "OK" của Team Alpha đã xoá tài khoản.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Gửi cả `before` và `after`; `limit` ngoài 1..100; con trỏ không phải UUID | Sửa tham số |
| 404 | `CHAT_002` | Không có cuộc trò chuyện | Quay lại |
| 404 | `USER_001` | Tài khoản đã xoá (token còn hạn) | Xoá token, về đăng nhập |
| 403 | `CHAT_003` | Không tham gia cuộc trò chuyện | Quay lại |
| 422 | `CHAT_001` | `before` / `after` không phải tin của cuộc trò chuyện này | Tải lại trang mới nhất (bỏ con trỏ) |

---

### 2.18. Đánh dấu đã đọc — `POST /api/v1/conversations/{conversationId}/messages/read`

**Dùng khi:** người dùng mở / đang xem phòng chat.
**Ai gọi được:** hai bên của cuộc trò chuyện; mọi trạng thái. Không body.

Đánh dấu **mọi** tin của **bên kia** trong cuộc trò chuyện là đã đọc (`isRead = true`). Không có frame realtime cho việc đánh dấu đọc; bên gửi thấy `isRead` mới khi tải lại 2.17.

**Response 204** — không body. Gọi lặp → `204`, không đổi gì.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `CHAT_002` | Không có cuộc trò chuyện | Quay lại |
| 404 | `USER_001` | Tài khoản đã xoá (token còn hạn) | Xoá token, về đăng nhập |
| 403 | `CHAT_003` | Không tham gia cuộc trò chuyện | Bỏ qua (không chặn người dùng) |

---

### 2.19. Đầu phòng chat — `GET /api/v1/conversations/{conversationId}`

**Dùng khi:** mở phòng chat — hiện hai bên (tên đội + đội trưởng **hiện tại**, hoặc đội tuyển + ứng viên), bên của mình, có gửi được tin không; tải lại sau `CHAT_004` / `MATCH_004` hoặc sau khi kết nối realtime lại.
**Ai gọi được:** hai bên của cuộc trò chuyện; mọi trạng thái.

**Response 200** — Cuộc trò chuyện (§4.7). Ví dụ chat luồng A giữa Alpha (đội tạo kèo) và Beta (yêu cầu đang chờ), người gọi là đội trưởng Beta:

```json
{
  "data": {
    "conversationId": "01933d41-1a2b-7c3d-8e4f-5a6b7c8d9e32",
    "contextType": "TEAM_MATCH",
    "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b01",
    "matchType": "HAS_VENUE",
    "requestId": "01933c20-5d6e-7f80-9a1b-2c3d4e5f6a01",
    "requestStatus": "PENDING",
    "pairingId": null,
    "pairingStatus": null,
    "applicationId": null,
    "applicationStatus": null,
    "source": "SYSTEM",
    "externalMatchId": null,
    "parties": [
      { "partyType": "TEAM", "partyId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01", "name": "Team Alpha", "avatarUrl": null, "captainName": "Minh" },
      { "partyType": "TEAM", "partyId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02", "name": "Team Beta", "avatarUrl": null, "captainName": "Nam" }
    ],
    "viewerPartyId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
    "canSend": true
  }
}
```

Ví dụ cuộc trò chuyện theo đơn ứng tuyển: MERCENARY-001 `API_Spec.md` 2.14.

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 404 | `CHAT_002` | Không có cuộc trò chuyện | Quay lại |
| 404 | `USER_001` | Tài khoản đã xoá (token còn hạn) | Xoá token, về đăng nhập |
| 403 | `CHAT_003` | Không tham gia cuộc trò chuyện | Quay lại |

### 2.20. Tin nhắn của tôi — `GET /api/v1/profile/conversations`

**Dùng khi:** màn hình "Tin nhắn" — mọi cuộc trò chuyện người gọi xem được, tin mới nhất lên đầu; đường vào chat cho người không còn danh sách nào khác (vd người đã giải tán đội — §1.6). Mở một dòng → 2.19 + 2.17 với `conversationId`.
**Ai gọi được:** mọi người dùng đã đăng nhập. Danh sách gồm cuộc trò chuyện mà người gọi là một bên **ngay lúc gọi** (cùng quy tắc 2.16–2.19):
- chat giữa hai đội (luồng A, B): người gọi là đội trưởng đang hoạt động hiện tại của đội tạo kèo hoặc đội tham gia, hoặc là người đã giải tán một trong hai đội (chỉ xem);
- chat theo đơn ứng tuyển (MERCENARY-001 §1.7): người gọi là đội trưởng hiện tại của đội tuyển, người đã giải tán đội tuyển (chỉ xem), hoặc là ứng viên.

Gồm cả cuộc trò chuyện chỉ xem (`canSend = false`). Đội trưởng mới sau khi chuyển quyền thấy mọi cuộc trò chuyện của đội; đội trưởng cũ không còn thấy. Cuộc trò chuyện đã bị xoá theo hạn lưu (BR-N01) không còn. Cuộc trò chuyện chưa có tin nhắn nào vẫn có trong danh sách (`lastMessage = null`), xếp theo thứ tự dưới (user chốt 2026-11-15).

**Query params**

| Tên | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| `page`, `pageSize` | integer | Không | §1.1 (`page` ≥ 1, mặc định 1; `pageSize` 1..100, mặc định 20) | `1`, `20` |

**Thứ tự:** `lastActivityAt` ↓, `conversationId` ↓. `lastActivityAt` = thời điểm tin cuối; cuộc trò chuyện chưa có tin: luồng A → lúc **gửi yêu cầu gần nhất** của đội tham gia (kể cả gửi lại sau khi rút / hết hạn), luồng B và chat theo đơn → lúc mở cuộc trò chuyện (user chốt 2026-11-15). Danh sách đổi thứ tự khi có tin mới → FE tải lại trang 1 khi nhận frame `MESSAGE_CREATED` hoặc khi quay lại màn hình; trang sau có thể lệch một vài dòng (trùng / sót) nếu có tin mới giữa hai lần tải — FE bỏ trùng theo `conversationId`.

**Response 200** — mảng Cuộc trò chuyện rút gọn (§4.9) + `pagination`. Ví dụ đội trưởng Alpha: một chat luồng A với Beta (có tin chưa đọc), một chat theo đơn với ứng viên đã xoá tài khoản (chỉ xem):

```json
{
  "data": [
    {
      "conversationId": "01933d41-1a2b-7c3d-8e4f-5a6b7c8d9e32",
      "contextType": "TEAM_MATCH",
      "matchId": "01933b6e-8f10-7a2c-9d41-2f6c1a7e5b01",
      "externalMatchId": null,
      "startsAt": "2026-11-16T11:00:00Z",
      "requestId": "01933c20-5d6e-7f80-9a1b-2c3d4e5f6a01",
      "pairingId": null,
      "applicationId": null,
      "viewerParty": { "partyType": "TEAM", "partyId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01", "name": "Team Alpha", "avatarUrl": null },
      "otherParty": { "partyType": "TEAM", "partyId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02", "name": "Team Beta", "avatarUrl": null },
      "lastMessage": {
        "messageId": "01933e10-2a3b-7c4d-8e5f-6a7b8c9d0e09",
        "contentPreview": "Sân X có chỗ để xe không?",
        "senderPartyId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e02",
        "senderName": "Nam",
        "createdAt": "2026-11-15T09:30:00Z"
      },
      "lastActivityAt": "2026-11-15T09:30:00Z",
      "hasUnread": true,
      "canSend": true
    },
    {
      "conversationId": "019370a2-6c7d-7e8f-a001-2b3c4d5e6f01",
      "contextType": "MERCENARY_APPLICATION",
      "matchId": "01936b20-7d8e-7f90-a1b2-c3d4e5f60708",
      "externalMatchId": null,
      "startsAt": "2026-11-14T11:00:00Z",
      "requestId": null,
      "pairingId": null,
      "applicationId": "019370a1-5b6c-7d8e-9f00-1a2b3c4d5e01",
      "viewerParty": { "partyType": "TEAM", "partyId": "01932f00-1a2b-7c3d-8e4f-5a6b7c8d9e01", "name": "Team Alpha", "avatarUrl": null },
      "otherParty": { "partyType": "USER", "partyId": "01935f80-1a2b-7c3d-8e4f-5a6b7c8d9e10", "name": "Người dùng đã xóa", "avatarUrl": null },
      "lastMessage": null,
      "lastActivityAt": "2026-11-13T09:00:00Z",
      "hasUnread": false,
      "canSend": false
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "totalItems": 2 }
}
```

**Lỗi**

| HTTP | Mã | Khi nào | FE nên làm gì |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `page` / `pageSize` ngoài miền | Sửa tham số |
| 404 | `USER_001` | Tài khoản đã xoá (token còn hạn) | Xoá token, về đăng nhập |

---

## 3. Hành động theo trạng thái

Cột vai trò = **đội trưởng** của đội đó (§1.2); thành viên thường của mọi đội chỉ xem. Mọi người dùng đã đăng nhập xem được chi tiết kèo (2.3) ở mọi trạng thái — không lặp lại trong bảng.

### 3.1. Kèo

| Trạng thái (hiển thị) | Đội tạo kèo | Đội đã ghép | Đội khác |
|---|---|---|---|
| `OPEN` (luồng A) | Sửa chi phí (2.4) · xem yêu cầu (2.7) · chấp nhận (2.8) / từ chối (2.9) yêu cầu `PENDING` · huỷ (2.5, `expectedStatus = OPEN`) khi không còn yêu cầu `PENDING`, không trừ điểm | — (chưa có) | Gửi yêu cầu (2.6) nếu đội chưa có yêu cầu đang hiệu lực / bị từ chối / bị thay thế; rút yêu cầu của mình khi `PENDING` (2.10) |
| `OPEN` (luồng B) | Xem thương lượng, chat, chốt phương án, chi phí, xác nhận (MATCH-002) · huỷ (2.5, `expectedStatus = OPEN`) bất kể thương lượng đang mở, không trừ điểm | — (chưa có) | Nhận kèo (MATCH-002 2.1) nếu đội chưa nhận kèo này |
| `CONFIRMED`, trước `startsAt` | Huỷ (2.5, `expectedStatus = CONFIRMED`, lý do bắt buộc, trừ điểm §1.5) | Huỷ (2.5, `teamId` = đội mình) **hoặc** rút yêu cầu đã chấp nhận (2.10, `expectedStatus = ACCEPTED`) — cùng kết quả, lý do bắt buộc, trừ điểm §1.5 | — |
| `CONFIRMED`, từ `startsAt` | — (huỷ → `MATCH_012`) | — (huỷ/rút → `MATCH_012`) | — |
| `ARCHIVED` | — (đánh giá trận thuộc REPUTATION-001) | — (như đội tạo kèo) | — |
| `COMPLETED` | — | — | — |
| `EXPIRED` (gồm `OPEN` quá `expiresAt`) | — (mọi thao tác → `MATCH_004`) | — | — |
| `CANCELLED` | — | — | — |

Ghi chú hiển thị nút:
- Kèo `OPEN` của đội tạo kèo không `ACTIVE` hoặc bộ môn đang tắt không xuất hiện ở tìm kèo (2.2); nếu mở từ link khác, gửi yêu cầu / nhận kèo → `MATCH_013` / `SPORT_002`.
- Kèo `CONFIRMED` trở đi: hành động như nhau cho cả hai luồng; với kèo B, "đội đã ghép" huỷ bằng 2.5 (không có 2.10).
- Gọi thao tác của luồng kia (2.4, 2.6, 2.7 trên kèo B; nhận kèo trên kèo A) → `422 MATCH_016`.
- Đội tạo kèo `INACTIVE`: vẫn huỷ, từ chối được; không sửa chi phí, không chấp nhận (`TEAM_015`) — §1.2.
- Mã lỗi khi gọi thao tác ở ô "—":
  - Huỷ (2.5) với `teamId` không phải đội tạo kèo, cũng không phải đội đã ghép → `MATCH_009`.
  - Kèo `EXPIRED`: mọi thao tác ghi → `MATCH_004`.
  - Kèo `CONFIRMED`: gửi yêu cầu, sửa chi phí, chấp nhận → `MATCH_003`; từ chối (2.9) → `MATCH_008` (không còn yêu cầu `PENDING`); từ `startsAt`, huỷ/rút → `MATCH_012`.
  - Kèo `ARCHIVED`/`COMPLETED`/`CANCELLED`: sửa chi phí, huỷ, gửi yêu cầu, chấp nhận → `MATCH_005`; từ chối (2.9) → `MATCH_008`; rút (2.10) yêu cầu `ACCEPTED` → `MATCH_005`, yêu cầu ở trạng thái khác → `MATCH_008`.

### 3.2. Yêu cầu ghép kèo

| Trạng thái (hiển thị) | Đội gửi | Đội tạo kèo |
|---|---|---|
| `PENDING` | Rút (2.10, `expectedStatus = PENDING`), lý do tuỳ chọn, không trừ điểm | Chấp nhận (2.8) · từ chối (2.9) |
| `ACCEPTED` | Trước `startsAt`: rút (2.10, `expectedStatus = ACCEPTED`) = huỷ kèo, lý do bắt buộc, trừ điểm §1.5 (hoặc huỷ kèo qua 2.5) | — trên yêu cầu; huỷ kèo qua 2.5 |
| `REJECTED` | — (không gửi lại được cho kèo này: `MATCH_006`) | — |
| `WITHDRAWN` | — (gửi yêu cầu mới được nếu kèo còn `OPEN`) | — |
| `SUPERSEDED` | — (không gửi lại được: kèo đã được ghép với đội khác) | — |
| `EXPIRED` | — (kèo đã hết hạn) | — |

Gọi thao tác trên yêu cầu không ở trạng thái cho phép → `MATCH_008`.

**Chat theo trạng thái yêu cầu** (cuộc trò chuyện `conversationId`, cả hai đội trưởng — §1.6, BR-N05): `PENDING` (kèo chưa quá `expiresAt`), `ACCEPTED`, `WITHDRAWN` sau khi đã được chấp nhận (= huỷ kèo) → **nhắn được** (đã ghép: cả sau trận, khi kèo bị huỷ); `REJECTED`, `SUPERSEDED`, `EXPIRED`, `WITHDRAWN` khi còn `PENDING` → **chỉ xem** (gửi lại yêu cầu mới → nhắn được lại). Mọi trạng thái: cuộc trò chuyện đã có tin nhắn → report "giao tiếp tệ" đội kia (REPUTATION-001 2.10). FE khoá ô chat theo `canSend` của 2.19.

## 4. Kiểu dữ liệu dùng chung

### 4.1. Enum

| Kiểu / enum | Giá trị | Ý nghĩa |
|---|---|---|
| `type` (kèo) | `HAS_VENUE` | Luồng A — đội tạo kèo đã có sân |
| | `NEED_VENUE` | Luồng B — chưa có sân, nhiều lựa chọn địa điểm / khung giờ; ghép qua thương lượng (MATCH-002) |
| `status` (kèo) | `OPEN` | Đang nhận yêu cầu |
| | `CONFIRMED` | Đã ghép với một đội |
| | `ARCHIVED` | Đã qua giờ kết thúc, chờ đánh giá |
| | `COMPLETED` | Đã có đội trưởng đánh giá (REPUTATION-001) |
| | `EXPIRED` | Hết hạn bài đăng mà chưa ghép (gồm `OPEN` quá `expiresAt` — §1.3) |
| | `CANCELLED` | Đã huỷ |
| `status` (yêu cầu) | `PENDING` | Đang chờ đội tạo kèo xử lý |
| | `ACCEPTED` | Được chấp nhận — kèo đã ghép với đội gửi |
| | `REJECTED` | Bị đội tạo kèo từ chối |
| | `WITHDRAWN` | Đội gửi đã rút |
| | `SUPERSEDED` | Kèo đã chấp nhận yêu cầu của đội khác |
| | `EXPIRED` | Kèo hết hạn khi yêu cầu còn chờ |
| `format` | `FIVE_A_SIDE` \| `SEVEN_A_SIDE` \| `ELEVEN_A_SIDE` | Sân 5 / sân 7 / sân 11 người (cùng giá trị `venueType` của VENUE-001) |
| `costType` | `VENUE_ONLY` | Chỉ tiền sân |
| | `DRINKS_ONLY` | Chỉ tiền nước |
| | `BOTH` | Cả tiền sân và tiền nước |
| `costSplit` | `FIFTY_FIFTY` | Chia đôi 50-50 |
| | `SEVENTY_THIRTY_HOME` | 70-30, đội chủ nhà bao 70% |
| | `SEVENTY_THIRTY_LOSER` | 70-30, đội thua bao 70% |
| | `LOSER_PAYS_DRINKS` | Đội thua trả toàn bộ tiền nước (chỉ khi có tiền nước) |
| `role` (query 2.11) | `AUTHOR` \| `OPPONENT` \| `ALL` | Đội tạo kèo / đội đã ghép / cả hai |
| `venueStatus` | `ACTIVE` \| `INACTIVE` | Địa điểm đang hoạt động / đã ngừng (VENUE-001) |
| Nhãn trình độ (`skillLevels`, `skillLevel`) | `Chill`, `Nhiệt tình`, `Máu lửa`, `Vui chơi`, `Rèn luyện`, `Thi đấu`, `Mới bắt đầu`, `Đã có kinh nghiệm`, `Lão làng` | 9 nhãn tiếng Việt, gửi đúng chữ (có dấu, phân biệt hoa thường); cùng danh sách trình độ của đội (USER-001) |

Không có loại chi phí "tuỳ chỉnh" — khoản khác nhập qua `customItems`.

**Chat (2.16–2.20):**

| Kiểu / enum | Giá trị | Ý nghĩa |
|---|---|---|
| `contextType` | `TEAM_MATCH` \| `MERCENARY_APPLICATION` | Chat giữa hai đội trong một kèo (§1.6) / chat theo đơn ứng tuyển (MERCENARY-001 §1.7). Có thể thêm giá trị ở bản sau — FE bỏ qua cuộc trò chuyện có giá trị lạ |
| `partyType`, `senderPartyType` | `TEAM` \| `USER` | Bên của cuộc trò chuyện là đội / người dùng (ứng viên mercenary) |
| `source` | `SYSTEM` \| `EXTERNAL` | Kèo trên hệ thống / trận ngoài hệ thống (chỉ `MERCENARY_APPLICATION` — MERCENARY-001 §4.1). `TEAM_MATCH` luôn `SYSTEM` |
| `type` (frame realtime) | `MESSAGE_CREATED` \| `PAIRING_UPDATED` \| `APPLICATION_UPDATED` | §1.7 |

### 4.2. Kèo (chi tiết) — response 2.1, 2.3

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `matchId` | string (UUID) | |
| `type` | string | `HAS_VENUE` \| `NEED_VENUE` |
| `status` | string | Trạng thái hiển thị (§1.3) |
| `sportId`, `sportName` | string | Bộ môn |
| `format` | string | §4.1 |
| `skillLevels` | string[] | Trình độ mong muốn; `[]` = nhận mọi trình độ |
| `ageNote` | string \| null | Ghi chú độ tuổi |
| `matchNature` | string \| null | Tính chất trận |
| `startsAt`, `endsAt` | string (thời gian) | Giờ bắt đầu / kết thúc dự kiến. Kèo B chưa ghép: khung giờ sớm nhất; đã ghép: khung giờ đã chốt |
| `expiresAt` | string (thời gian) | Hạn nhận đối (yêu cầu / thương lượng) |
| `authorTeam` | object Đội | Đội tạo kèo |
| `opponentTeam` | object Đội \| null | Đội đã ghép; khác `null` khi kèo đã ghép (`CONFIRMED`, `ARCHIVED`, `COMPLETED`, hoặc `CANCELLED` sau khi ghép) |
| `venue` | object Địa điểm \| null | Thông tin địa điểm mới nhất. Kèo A: luôn có. Kèo B: `null` khi chưa ghép; địa điểm đã chốt khi đã ghép |
| `venueOptions` | mảng object Địa điểm | Kèo B: lựa chọn địa điểm, đúng thứ tự đội tạo kèo nhập. Kèo A: `[]` |
| `timeOptions` | mảng `{ timeOptionId, startsAt, endsAt }` | Kèo B: lựa chọn khung giờ, `startsAt` ↑. Kèo A: `[]` |
| `confirmedPairingId` | string (UUID) \| null | Kèo B đã ghép: thương lượng đã chốt (MATCH-002). Còn lại: `null` |
| `costAgreement` | object | Thoả thuận chi phí; chưa có → mọi field `null`, `customItems: []`. Kèo B: rỗng khi chưa ghép; khi đã ghép = bản chép thoả thuận của thương lượng đã chốt tại lúc ghép (khoản tự nhập có `itemId` mới, khác `itemId` trong thương lượng — MATCH-002) |
| `cancelledByTeamId` | string (UUID) \| null | Đội đã huỷ |
| `cancelledAt` | string (thời gian) \| null | |
| `cancelReason` | string \| null | |
| `createdAt`, `updatedAt` | string (thời gian) | |

**Object Đội** (`authorTeam`, `opponentTeam`): `teamId` string (UUID) · `teamName` string · `avatarUrl` string \| null · `reliabilityScore` integer 0..100.

**Object Địa điểm** (`venue`): `venueId` string (UUID) · `venueName` string · `address` string · `latitude` number · `longitude` number · `venueStatus` `ACTIVE` \| `INACTIVE` · `primaryImageUrl` string \| null (chưa có ảnh). Địa điểm đã ngừng vẫn hiện trên kèo cũ.

**Object `costAgreement`**: `costType`, `costSplit` (enum \| null) · `venueAmount`, `drinksAmount` (integer \| null) · `depositAmount` (integer \| null — tiền cọc, chỉ kèo B; kèo A luôn `null`) · `depositHolder` (string \| null — người giữ cọc, chỉ kèo B) · `customItems` (mảng `{ itemId, itemName, amount }`, đúng thứ tự đã nhập). Chỉ 7 field này — **không** có `matchId`, `updatedAt` (khác response 2.4, §4.5).

### 4.3. Kèo rút gọn — phần tử danh sách 2.2, 2.11

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `matchId` | string (UUID) | |
| `type` | string | `HAS_VENUE` \| `NEED_VENUE` |
| `status` | string | Trạng thái hiển thị (§1.3) |
| `sportId`, `sportName` | string | |
| `format` | string | |
| `skillLevels` | string[] | `[]` = mọi trình độ |
| `startsAt`, `endsAt`, `expiresAt` | string (thời gian) | Kèo B chưa ghép: `startsAt` / `endsAt` = khung giờ sớm nhất |
| `authorTeamId`, `authorTeamName` | string | Đội tạo kèo |
| `authorTeamReliabilityScore` | integer | 0..100 |
| `opponentTeamId`, `opponentTeamName` | string \| null | Đội đã ghép; `null` khi chưa ghép |
| `venueId`, `venueName`, `venueAddress` | string \| null | Địa điểm (thông tin mới nhất); kèo B chưa ghép: `null` |
| `venueOptionCount`, `timeOptionCount` | integer \| null | Kèo B: số lựa chọn địa điểm / khung giờ (1..5). Kèo A: `null` |
| `distanceKm` | number \| null | Làm tròn 2 chữ số; kèo B chưa ghép: tới lựa chọn địa điểm gần nhất; `null` khi không gửi toạ độ (luôn `null` ở 2.11) |
| `createdAt` | string (thời gian) | |

### 4.4. Yêu cầu ghép kèo — response 2.6, 2.7, 2.12

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `requestId` | string (UUID) | |
| `matchId` | string (UUID) | |
| `conversationId` | string (UUID) | Cuộc trò chuyện giữa đội gửi và đội tạo kèo (§1.6) — các yêu cầu của cùng đội cho cùng kèo (gửi lại) có cùng giá trị. Mở phòng chat bằng 2.19 + 2.17 |
| `requesterTeamId`, `requesterTeamName` | string | Đội gửi |
| `requesterTeamReliabilityScore` | integer | 0..100 |
| `status` | string | Trạng thái hiển thị (§1.3) |
| `reason` | string \| null | Lý do từ chối / rút |
| `requestedAt` | string (thời gian) | Lúc gửi |
| `respondedAt` | string (thời gian) \| null | Lúc được chấp nhận / bị từ chối / bị thay thế |
| `withdrawnAt` | string (thời gian) \| null | Lúc rút |
| `startsAt` | string (thời gian) \| null | Giờ bắt đầu kèo — chỉ có ở 2.12 (`null` ở 2.6, 2.7) |
| `authorTeamId`, `authorTeamName` | string \| null | Đội tạo kèo — chỉ có ở 2.12 (`null` ở 2.6, 2.7) |

### 4.5. Thoả thuận chi phí — response 2.4

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `matchId` | string (UUID) | |
| `costType` | string \| null | §4.1 |
| `costSplit` | string \| null | §4.1 |
| `venueAmount` | integer \| null | Tiền sân (VND) |
| `drinksAmount` | integer \| null | Tiền nước (VND) |
| `customItems` | mảng `{ itemId: string (UUID), itemName: string, amount: integer }` | Đúng thứ tự FE gửi; `itemId` cấp mới mỗi lần lưu |
| `updatedAt` | string (thời gian) | Thời điểm cập nhật kèo |

### 4.6. Bộ môn — response 2.13, 2.14, 2.15

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `sportId` | string (UUID) | |
| `sportName` | string | |
| `isActive` | boolean | Đang bật |
| `createdAt` | string (thời gian) | |

### 4.7. Cuộc trò chuyện — response 2.19

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `conversationId` | string (UUID) | |
| `contextType` | string | §4.1 |
| `matchId` | string (UUID) \| null | Kèo của cuộc trò chuyện; `null` chỉ khi cuộc trò chuyện mercenary của trận ngoài hệ thống |
| `matchType` | string \| null | `TEAM_MATCH`: `HAS_VENUE` (luồng A) \| `NEED_VENUE` (luồng B). Loại khác: `null` |
| `requestId`, `requestStatus` | string (UUID), string \| null | `TEAM_MATCH` luồng A: yêu cầu ghép kèo **gần nhất** của đội tham gia và trạng thái hiển thị của nó (§1.3, §4.1). Còn lại: `null` |
| `pairingId`, `pairingStatus` | string (UUID), string \| null | `TEAM_MATCH` luồng B: thương lượng và trạng thái hiển thị (MATCH-002 §1.3). Còn lại: `null` |
| `applicationId`, `applicationStatus` | string (UUID), string \| null | `MERCENARY_APPLICATION`: đơn ứng tuyển và trạng thái hiển thị (MERCENARY-001 §1.3). Còn lại: `null` |
| `source` | string | §4.1 |
| `externalMatchId` | string (UUID) \| null | Trận ngoài hệ thống (chỉ `MERCENARY_APPLICATION`); còn lại `null` |
| `parties` | array (2 phần tử) | Mỗi phần tử: `partyType`, `partyId` (`teamId` hoặc `userId`), `name`, `avatarUrl` (string \| null), `captainName` (chỉ bên `TEAM`: đội trưởng **hiện tại**; `"Người dùng đã xóa"` nếu tài khoản đó đã xoá; `null` khi đội đã giải tán hoặc bên `USER`). `TEAM_MATCH`: phần tử đầu = đội tạo kèo, phần tử sau = đội tham gia |
| `viewerPartyId` | string (UUID) | Bên của người gọi (`partyId` của một phần tử `parties`) |
| `canSend` | boolean | Người gọi gửi tin được ngay lúc này (§1.6; MERCENARY-001 §1.7). `false` → khoá ô chat |

### 4.8. Tin nhắn — response 2.16, 2.17, frame `MESSAGE_CREATED`

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `messageId`, `conversationId` | string (UUID) | |
| `senderPartyType`, `senderPartyId` | string, string (UUID) | Bên gửi — khớp một phần tử `parties` của 2.19; FE lấy tên đội / tên bên gửi từ đó |
| `senderUserId` | string (UUID) | Người **thực sự** đã gửi tin (đội trưởng của đội gửi tại lúc gửi, hoặc ứng viên); không đổi khi đội chuyển quyền đội trưởng sau đó |
| `senderName` | string | Tên hiển thị của người gửi; `"Người dùng đã xóa"` nếu tài khoản đó đã xoá |
| `senderAvatarUrl` | string \| null | Ảnh đại diện người gửi; `null` khi chưa có ảnh hoặc tài khoản đã xoá |
| `content` | string | 1..1000 ký tự |
| `isRead` | boolean | Bên nhận đã đánh dấu đọc (2.18) |
| `createdAt` | string (thời gian) | |

Hiển thị tin của bên đội: `<tên đội> · <senderName>` (vd "Team Beta · Nam"), tên đội hiện tại lấy từ `parties` của 2.19 theo `senderPartyId`.

### 4.9. Cuộc trò chuyện rút gọn — phần tử 2.20

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `conversationId` | string (UUID) | Mở phòng chat: 2.19 + 2.17 |
| `contextType` | string | §4.1 |
| `matchId`, `externalMatchId` | string (UUID) \| null | Kèo / trận ngoài hệ thống của cuộc trò chuyện (như §4.7) |
| `startsAt` | string (thời gian) | Giờ bắt đầu kèo / trận (kèo luồng B chưa ghép: khung giờ sớm nhất) |
| `requestId`, `pairingId`, `applicationId` | string (UUID) \| null | Yêu cầu gần nhất (luồng A) / thương lượng (luồng B) / đơn ứng tuyển — theo `contextType`, còn lại `null` |
| `viewerParty` | object | Bên của người gọi: `partyType`, `partyId`, `name`, `avatarUrl` — vd đội nào của tôi (người làm đội trưởng nhiều đội) |
| `otherParty` | object | Bên kia: `partyType` (`TEAM` \| `USER`), `partyId`, `name` (tên đội; tên người — `"Người dùng đã xóa"` nếu tài khoản đã xoá), `avatarUrl` (`null` khi chưa có ảnh / tài khoản đã xoá) |
| `lastMessage` | object \| null | Tin cuối: `messageId`, `contentPreview` (100 ký tự đầu của nội dung — nội dung đủ ở 2.17), `senderPartyId`, `senderName` (che như §4.8), `createdAt`. `null` khi chưa có tin |
| `lastActivityAt` | string (thời gian) | `lastMessage.createdAt`; chưa có tin: luồng A → lúc gửi yêu cầu gần nhất của đội tham gia, còn lại → lúc mở cuộc trò chuyện — khoá sắp xếp (2.20) |
| `hasUnread` | boolean | Có tin của bên kia chưa được phía mình đánh dấu đọc (2.18) |
| `canSend` | boolean | Như 2.19 tại lúc gọi |

## 5. Mã lỗi

Mã mới của MATCH-001:

| Mã | HTTP | Message | Endpoint |
|---|---|---|---|
| `MATCH_001` | 404 | "Không tìm thấy kèo" | 2.3, 2.4, 2.5, 2.6, 2.7 |
| `MATCH_002` | 404 | "Không tìm thấy yêu cầu ghép kèo" | 2.8, 2.9, 2.10 |
| `MATCH_003` | 409 | "Kèo đã được ghép" | 2.4, 2.6, 2.8 |
| `MATCH_004` | 409 | "Kèo đã hết hạn" | 2.4, 2.5, 2.6, 2.8, 2.9, 2.10, 2.16 |
| `MATCH_005` | 409 | "Kèo không còn ở trạng thái cho phép thao tác này" | 2.4, 2.5, 2.6, 2.8, 2.10 |
| `MATCH_006` | 409 | "Đội đã gửi yêu cầu cho kèo này" | 2.6 |
| `MATCH_007` | 422 | "Không thể gửi yêu cầu cho kèo của đội mình" | 2.6 |
| `MATCH_008` | 409 | "Yêu cầu đã được xử lý" | 2.8, 2.9, 2.10 |
| `MATCH_009` | 403 | "Đội không có quyền với kèo này" | 2.5 |
| `MATCH_010` | 422 | "Vui lòng từ chối tất cả yêu cầu trước khi hủy" | 2.5 |
| `MATCH_011` | 422 | "Vui lòng nhập lý do hủy" | 2.5, 2.10 |
| `MATCH_012` | 422 | "Trận đã bắt đầu, không thể hủy" | 2.5, 2.10 |
| `MATCH_013` | 422 | "Đội đối phương hiện không hoạt động" | 2.6, 2.8 |
| `MATCH_014` | 409 | "Dữ liệu vừa được cập nhật bởi thao tác khác, vui lòng thử lại" | 2.4, 2.5, 2.6, 2.8, 2.9, 2.10, 2.16 |
| `CHAT_001` | 422 | "Tin nhắn mốc không thuộc cuộc trò chuyện này" | 2.17 |
| `CHAT_002` | 404 | "Không tìm thấy cuộc trò chuyện" | 2.16–2.19, kênh realtime (REPUTATION-001 2.10) |
| `CHAT_003` | 403 | "Bạn không tham gia cuộc trò chuyện này" | 2.16–2.19, kênh realtime (REPUTATION-001 2.10) |
| `CHAT_004` | 409 | "Cuộc trò chuyện chỉ còn xem" | 2.16 |
| `MATCH_015` | 409 | "Trạng thái đã thay đổi, vui lòng tải lại" | 2.5, 2.10 |
| `SPORT_001` | 404 | "Không tìm thấy bộ môn" | 2.1, 2.15 |
| `SPORT_002` | 422 | "Bộ môn hiện không được hỗ trợ" | 2.1, 2.6, 2.8 |

Mã dùng lại (message theo `API_Spec.md` của mã gốc):

| Mã | HTTP | Message | Nguồn | Endpoint |
|---|---|---|---|---|
| `VALIDATION_ERROR` | 400 | "Dữ liệu không hợp lệ" | `api-convention.md` §11 | Mọi endpoint |
| `AUTH_001` | 401 | "Token không hợp lệ hoặc đã hết hạn" | USER-001 | Mọi endpoint |
| `AUTH_004` | 403 | "Cần quyền quản trị" | USER-001 | 2.14, 2.15 |
| `TEAM_001` | 404 | "Không tìm thấy đội" | USER-001 | 2.1, 2.5, 2.6, 2.11, 2.12 |
| `TEAM_002` | 403 | "Chỉ đội trưởng được thực hiện" | USER-001 | 2.1, 2.4–2.10, 2.12 |
| `USER_001` | 404 | "Không tìm thấy người dùng" | USER-001 | 2.16–2.20, kênh realtime (tài khoản đã xoá, token còn hạn) |
| `TEAM_015` | 422 | "Đội đang tạm ngưng hoạt động" | USER-001 | 2.1, 2.4, 2.6, 2.8 |
| `TEAM_016` | 422 | "Đội đã giải tán" | USER-002 | 2.1, 2.4–2.10, 2.12 |
| `VENUE_001` | 404 | "Không tìm thấy địa điểm" | VENUE-001 | 2.1 |
| `VENUE_002` | 422 | "Địa điểm đã ngừng hoạt động" | VENUE-001 | 2.1 |
| `MATCH_016` | 422 | "Thao tác không áp dụng cho loại kèo này" | MATCH-002 | 2.4, 2.6, 2.7 (gọi trên kèo luồng B); 2.1 (`type = NEED_VENUE` khi luồng B chưa được phát hành — §1) |

Lỗi hệ thống `SYS_*` theo `api-convention.md` §6. `MATCH_009` (403 nghiệp vụ: đội không liên quan tới kèo) khác `AUTH_004` (403 không phải admin).

## 6. Danh sách [cần xác nhận]

Mở (đánh số chung toàn thiết kế MATCH-001, số không dùng lại; các số khác ở tài liệu nội bộ BE):

12. Đường dẫn và hỗ trợ SockJS của kênh realtime (`/ws`): phụ thuộc phiên bản framework BE — sẽ chốt khi cài đặt; nếu không còn SockJS, FE kết nối WebSocket thuần tới `/ws` và dùng 2.17 (`after`) khi không mở được WebSocket (ADR-0014). (Chuyển từ MATCH-002.)
18. ~~Không có frame khi yêu cầu ghép kèo luồng A đổi trạng thái / kèo bị huỷ~~ — **user chốt 2026-11-15:** giữ không có frame ở bản này (§1.7). Nếu FE cần cập nhật tức thời `canSend` → bổ sung frame ở bản sau.

Các mục trước: Không còn mục mở. Quyết định nghiệp vụ chốt 2026-11-09 đã đồng bộ vào SRS `SYSTEM-001` v1.3 (BR-M09..BR-M18, BR-C03..BR-C04; AC-M06, AC-M09..AC-M12). `costAgreement` lồng trong Kèo (chi tiết): 5 field (user chốt 2026-11-10), từ v2.0 thêm `depositAmount`, `depositHolder` cho kèo luồng B (§4.2). Luồng B chốt 2026-11-10 (MATCH-002); các câu SRS liên quan sẽ được cập nhật sau.
