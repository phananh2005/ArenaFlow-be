# Schema Database — ArenaFlow-be

> Thiết kế schema PostgreSQL chi tiết từ đặc tả nghiệp vụ trong `data-model.md`

> Cập nhật: 2026-11-25 — đồng bộ V8 (MATCH-002: `match`, `match_venue_option`, `match_time_option`, `cost_custom_item`, `pairing`). 2026-11-25 — đồng bộ V7 (MATCH-001: `match`, `match_request`, `cost_custom_item`, bỏ `chat_message`, thêm `conversation` + `conversation_message`, `idx_team_disbanded_by`). 2026-11-21 — kiểu mọi cột thời gian theo V6 (SYSTEM-002). 2026-11-05 — bảng `team`, `team_member` đồng bộ tới V4 (USER-002)

## Conventions

- **Tên bảng:** snake_case số ít (user, team, match)
- **Tên cột:** snake_case
- **ID:** UUID (Hibernate generates UUID v7 từ Java, không cần DEFAULT trong DB; ngoại lệ duy nhất: câu INSERT gom lô viết tay như `cost_custom_item` dùng hàm `uuidv7()` của PostgreSQL ≥ 18 — ADR-0008 Sửa đổi 2026-11-09)
- **Timestamp:** TIMESTAMP (không múi giờ), giá trị là **giờ Việt Nam** (`Asia/Ho_Chi_Minh`) — V6 (SYSTEM-002) đổi mọi cột TIMESTAMPTZ của V1–V5 sang TIMESTAMP, giữ đúng thời điểm. DEFAULT của cột `*_at` = `now() AT TIME ZONE 'Asia/Ho_Chi_Minh'` (không phụ thuộc TimeZone phiên); ứng dụng luôn tự truyền giá trị. Có `created_at`, `updated_at`
- **Soft delete:** `deleted_at` TIMESTAMP NULL
- **Enum:** VARCHAR với CHECK constraint
- **Foreign keys:** ON DELETE RESTRICT mặc định (trừ khi có lý do rõ ràng)

---

## 1. User & Authentication

### 1.1 Bảng: user

Lưu tài khoản và hồ sơ cá nhân.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| display_name | VARCHAR(100) | NOT NULL | Tên hiển thị |
| avatar_url | VARCHAR(500) | NULL | URL ảnh đại diện |
| bio | TEXT | NULL | Tiểu sử |
| preferred_position | VARCHAR(50) | NULL | Vị trí chơi ưa thích (Thủ môn, Hậu vệ, Tiền vệ, Tiền đạo) |
| date_of_birth | DATE | NULL | Ngày sinh |
| gender | VARCHAR(20) | NULL | Giới tính |
| district_id | UUID | NULL | FK → administrative_unit, cấp phường/xã |
| phone_number | VARCHAR(20) | NULL | Số điện thoại (OAuth Phone) |
| reliability_score | INTEGER | NOT NULL | Điểm uy tín 0-100, default 80 |
| total_matches_completed | INTEGER | NOT NULL | Tổng số trận hoàn thành, default 0 |
| total_matches_confirmed | INTEGER | NOT NULL | Tổng số trận đã confirmed (mẫu số Completion Rate), default 0 |
| average_rating | DECIMAL(3,2) | NULL | Rating trung bình 1.00-5.00 |
| report_count_noshow | INTEGER | NOT NULL | Số lần bị report bùng, default 0 |
| report_count_bad_play | INTEGER | NOT NULL | Số lần bị report chơi xấu, default 0 |
| report_count_bad_communication | INTEGER | NOT NULL | Số lần bị report giao tiếp tệ, default 0 |
| report_count_skill_mismatch | INTEGER | NOT NULL | Số lần bị report không đúng trình độ, default 0 |
| clean_streak | INTEGER | NOT NULL | Số trận sạch liên tiếp, default 0 |
| created_at | TIMESTAMP | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMP | NOT NULL | Thời gian cập nhật cuối |
| deleted_at | TIMESTAMP | NULL | Thời gian soft delete |

**Indexes:**
- PRIMARY KEY (id)
- UNIQUE (phone_number) WHERE phone_number IS NOT NULL AND deleted_at IS NULL
- INDEX (district_id) -- tìm kiếm theo khu vực
- INDEX (deleted_at) -- filter user hoạt động

**Constraints:**
- CHECK (reliability_score >= 0 AND reliability_score <= 100)
- CHECK (average_rating IS NULL OR (average_rating >= 1.0 AND average_rating <= 5.0))
- CHECK (total_matches_completed <= total_matches_confirmed)
- CHECK (report_count_noshow >= 0)
- CHECK (report_count_bad_play >= 0)
- CHECK (report_count_bad_communication >= 0)
- CHECK (report_count_skill_mismatch >= 0)
- CHECK (clean_streak >= 0)

### 1.2 Bảng: oauth_account

Lưu liên kết OAuth.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| user_id | UUID | NOT NULL | FK → user |
| provider | VARCHAR(50) | NOT NULL | google, facebook, phone |
| provider_user_id | VARCHAR(255) | NOT NULL | ID từ provider |
| created_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- UNIQUE (provider, provider_user_id)
- INDEX (user_id)

**Foreign Keys:**
- FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE

### 1.3 Bảng: administrative_unit

Danh mục hành chính Việt Nam (tỉnh/quận/phường).

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| name | VARCHAR(100) | NOT NULL | Tên đơn vị |
| level | VARCHAR(20) | NOT NULL | province, district, ward |
| parent_id | UUID | NULL | FK → administrative_unit (self-reference) |
| created_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (parent_id)
- INDEX (level)

**Foreign Keys:**
- FOREIGN KEY (parent_id) REFERENCES administrative_unit(id) ON DELETE RESTRICT

**Constraints:**
- CHECK (level IN ('province', 'district', 'ward'))

---

## 2. Team

### 2.1 Bảng: team

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| name | VARCHAR(100) | NOT NULL | Tên đội (cho phép trùng) |
| created_by | UUID | NOT NULL | FK → user, người tạo |
| avatar_url | VARCHAR(500) | NULL | Logo đội |
| description | TEXT | NULL | Mô tả/giới thiệu |
| status | VARCHAR(20) | NOT NULL | ACTIVE, INACTIVE, DISBANDED (V4 — trạng thái cuối) |
| skill_levels | TEXT | NOT NULL | JSON array nhãn, default `'[]'` (V2) |
| inactive_reason | VARCHAR(500) | NULL | Lý do admin khoá (V2); giữ nguyên khi giải tán |
| disband_reason | VARCHAR(100) | NULL | Lý do giải tán (V4); NOT NULL khi DISBANDED |
| disbanded_at | TIMESTAMP | NULL | Thời điểm giải tán (V4) |
| disbanded_by | UUID | NULL | FK → user, người giải tán (V4) |
| version | BIGINT | NOT NULL | Optimistic lock, default 0 (V2) |
| reliability_score | INTEGER | NOT NULL | Điểm uy tín đội 0-100, default 80 |
| total_matches_completed | INTEGER | NOT NULL | default 0 |
| total_matches_confirmed | INTEGER | NOT NULL | default 0 |
| average_rating | DECIMAL(3,2) | NULL | Rating trung bình 1.00-5.00 |
| report_count_noshow | INTEGER | NOT NULL | default 0 |
| report_count_bad_play | INTEGER | NOT NULL | default 0 |
| report_count_bad_communication | INTEGER | NOT NULL | default 0 |
| report_count_skill_mismatch | INTEGER | NOT NULL | default 0 |
| clean_streak | INTEGER | NOT NULL | default 0 |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (created_by)
- `idx_team_status_created` INDEX (status, created_at DESC) (V2, thay `idx_team_status`) -- danh sách đội lọc status, sort mới nhất
- `idx_team_disbanded_by` INDEX (disbanded_by) WHERE disbanded_by IS NOT NULL (V7) -- tìm đội người gọi đã giải tán ("Tin nhắn của tôi")

**Foreign Keys:**
- FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE RESTRICT
- FOREIGN KEY (disbanded_by) REFERENCES user(id) ON DELETE RESTRICT (V4)

**Constraints:**
- `team_status_check`: CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISBANDED')) (V4)
- `team_disband_consistency_check` (V4): `DISBANDED` ⇔ `disband_reason`, `disbanded_at`, `disbanded_by` đều NOT NULL; trạng thái khác ⇔ cả ba NULL
- CHECK (reliability_score >= 0 AND reliability_score <= 100)

### 2.2 Bảng: team_member

Quan hệ nhiều-nhiều user-team với vai trò.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| team_id | UUID | NOT NULL | FK → team |
| user_id | UUID | NOT NULL | FK → user |
| role | VARCHAR(20) | NOT NULL | CAPTAIN, MEMBER |
| status | VARCHAR(20) | NOT NULL | PENDING, ACTIVE, LEFT, REJECTED (V2) |
| invited_at | TIMESTAMP | NOT NULL | Thời gian mời |
| joined_at | TIMESTAMP | NULL | Thời gian chấp nhận |
| left_at | TIMESTAMP | NULL | Thời gian rời đội |
| version | BIGINT | NOT NULL | Optimistic lock, default 0 (V2) |

**Indexes:**
- PRIMARY KEY (id)
- UNIQUE (team_id, user_id) WHERE status IN ('PENDING', 'ACTIVE') -- `idx_team_member_pending_or_active` (V2): tối đa một lời mời/membership mở cho mỗi user trong một team
- INDEX (team_id, role, status) -- query captain/members
- INDEX (user_id, status) -- query teams của user

**Foreign Keys:**
- FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE
- FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE

**Constraints:**
- CHECK (role IN ('CAPTAIN', 'MEMBER'))
- CHECK (status IN ('PENDING', 'ACTIVE', 'LEFT', 'REJECTED')) (V2)
- CHECK (joined_at IS NULL OR joined_at >= invited_at)
- CHECK (left_at IS NULL OR left_at >= COALESCE(joined_at, invited_at))

### 2.3 Bảng: team_tag

Tags/skill levels của đội (tối đa 3 trong 9 nhãn).

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| team_id | UUID | NOT NULL | FK → team |
| tag | VARCHAR(50) | NOT NULL | Chill, Nhiệt tình, Máu lửa, ... |
| created_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- UNIQUE (team_id, tag)
- INDEX (team_id)

**Foreign Keys:**
- FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE

**Constraints:**
- CHECK (tag IN ('Chill', 'Nhiệt tình', 'Máu lửa', 'Vui chơi', 'Rèn luyện', 'Thi đấu', 'Mới bắt đầu', 'Đã có kinh nghiệm', 'Lão làng'))

### 2.4 Bảng: team_captain_history

Lịch sử chuyển quyền đội trưởng.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| team_id | UUID | NOT NULL | FK → team |
| from_user_id | UUID | NULL | FK → user, NULL nếu là lần đầu (người tạo) |
| to_user_id | UUID | NOT NULL | FK → user |
| transferred_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (team_id, transferred_at DESC) -- lịch sử chuyển quyền

**Foreign Keys:**
- FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE
- FOREIGN KEY (from_user_id) REFERENCES user(id) ON DELETE SET NULL
- FOREIGN KEY (to_user_id) REFERENCES user(id) ON DELETE RESTRICT

---

_(Tiếp tục với các nhóm bảng khác: Match & Pairing, Rating & Report, Venue, Mercenary, Chat & Notification trong phần tiếp theo)_

---

## 3. Match & Pairing

### 3.1 Bảng: match

Bài đăng ghép kèo (luồng A hoặc B).

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| type | VARCHAR(20) | NOT NULL | HAS_VENUE (luồng A), NEED_VENUE (luồng B) |
| author_team_id | UUID | NOT NULL | FK → team, đội đăng bài |
| status | VARCHAR(20) | NOT NULL | OPEN, CONFIRMED, ARCHIVED, COMPLETED, EXPIRED, CANCELLED |
| sport_id | UUID | NOT NULL | FK → sport, bộ môn (bóng đá trong MVP) |
| format | VARCHAR(20) | NULL | `FIVE_A_SIDE`, `SEVEN_A_SIDE`, `ELEVEN_A_SIDE` (V7: VARCHAR(10) → VARCHAR(20); bắt buộc khi `type = HAS_VENUE`) |
| skill_levels | TEXT | NOT NULL | JSON array nhãn trình độ mong muốn, default `'[]'` (V7) |
| age_note | TEXT | NULL | Ghi chú tuổi (văn bản tự do) |
| match_nature | TEXT | NULL | Tính chất trận (văn bản tự do) |
| venue_id | UUID | NULL | FK → venue. HAS_VENUE: bắt buộc; NEED_VENUE: NULL khi chưa ghép, = địa điểm đã chốt khi ghép (V8) |
| match_start_time | TIMESTAMP | NOT NULL | Giờ bắt đầu trận (NEED_VENUE chưa ghép: khung giờ sớm nhất; khi ghép: khung đã chốt) |
| match_end_time | TIMESTAMP | NOT NULL | Giờ kết thúc dự kiến (để ARCHIVED/notification); NEED_VENUE như `match_start_time` |
| expires_at | TIMESTAMP | NOT NULL | Thời hạn bài đăng, ≤ match_start_time |
| search_lat | DECIMAL(10,7) | NULL | Không dùng (V8 bỏ index GPS; kèo B tìm theo lựa chọn địa điểm) |
| search_lng | DECIMAL(10,7) | NULL | Không dùng |
| search_radius_km | DECIMAL(5,2) | NULL | Không dùng |
| cost_type | VARCHAR(50) | NULL | `VENUE_ONLY`, `DRINKS_ONLY`, `BOTH` (V7: bỏ `CUSTOM`) |
| cost_split | VARCHAR(50) | NULL | `FIFTY_FIFTY`, `SEVENTY_THIRTY_HOME`, `SEVENTY_THIRTY_LOSER`, `LOSER_PAYS_DRINKS` |
| cost_venue_amount | BIGINT | NULL | Tiền sân (VND, > 0) |
| cost_drinks_amount | BIGINT | NULL | Tiền nước (VND, > 0) |
| cost_deposit_amount | BIGINT | NULL | Tiền cọc đã chốt (VND, > 0; chỉ NEED_VENUE, chép từ pairing khi ghép — V8) |
| cost_deposit_holder | VARCHAR(100) | NULL | Ai giữ tiền cọc (văn bản tự do; chỉ khi có `cost_deposit_amount` — V8) |
| confirmed_opponent_team_id | UUID | NULL | FK → team, đội đã ghép (khi CONFIRMED) |
| cancelled_by_team_id | UUID | NULL | FK → team, đội hủy (khi CANCELLED) |
| cancelled_at | TIMESTAMP | NULL | Thời gian hủy |
| cancel_reason | VARCHAR(500) | NULL | Lý do hủy kèo (V7) |
| version | BIGINT | NOT NULL | Optimistic lock, default 0 (V7) |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (author_team_id, status) -- query Match của đội (vai trò tác giả)
- `idx_match_opponent_status` INDEX (confirmed_opponent_team_id, status) (V7) -- query Match của đội (vai trò đối thủ)
- INDEX (status, expires_at) -- scheduled job xử lý expiry
- INDEX (status, match_end_time) -- scheduled job chuyển ARCHIVED
- INDEX (sport_id, status, match_start_time) -- tìm kèo theo môn
- `idx_match_open_start` INDEX (match_start_time, id) WHERE status = 'OPEN' (V7) -- tìm kèo đang mở không lọc môn
- (V8 bỏ `idx_match_search_gps` (search_lat, search_lng) — không dùng)

**Foreign Keys:**
- FOREIGN KEY (author_team_id) REFERENCES team(id) ON DELETE RESTRICT
- FOREIGN KEY (sport_id) REFERENCES sport(id) ON DELETE RESTRICT
- FOREIGN KEY (venue_id) REFERENCES venue(id) ON DELETE RESTRICT
- FOREIGN KEY (confirmed_opponent_team_id) REFERENCES team(id) ON DELETE RESTRICT
- FOREIGN KEY (cancelled_by_team_id) REFERENCES team(id) ON DELETE RESTRICT

**Constraints:**
- CHECK (type IN ('HAS_VENUE', 'NEED_VENUE'))
- CHECK (status IN ('OPEN', 'CONFIRMED', 'ARCHIVED', 'COMPLETED', 'EXPIRED', 'CANCELLED'))
- CHECK (expires_at <= match_start_time)
- CHECK (match_end_time > match_start_time)
- `match_type_venue_check` (V8, thay `match_check2` của V1): CHECK ((type = 'HAS_VENUE' AND venue_id IS NOT NULL) OR (type = 'NEED_VENUE' AND (venue_id IS NULL) = (confirmed_opponent_team_id IS NULL)))
- CHECK ((status = 'CONFIRMED' AND confirmed_opponent_team_id IS NOT NULL) OR (status != 'CONFIRMED'))
- CHECK ((status = 'CANCELLED' AND cancelled_by_team_id IS NOT NULL AND cancelled_at IS NOT NULL) OR (status != 'CANCELLED'))
- `match_format_check` (V7): CHECK (format IS NULL OR format IN ('FIVE_A_SIDE', 'SEVEN_A_SIDE', 'ELEVEN_A_SIDE'))
- `match_has_venue_format_check` (V7): CHECK (type <> 'HAS_VENUE' OR format IS NOT NULL)
- `match_cost_type_check` (V7): CHECK (cost_type IS NULL OR cost_type IN ('VENUE_ONLY', 'DRINKS_ONLY', 'BOTH'))
- `match_cost_split_check` (V7): CHECK (cost_split IS NULL OR cost_split IN ('FIFTY_FIFTY', 'SEVENTY_THIRTY_HOME', 'SEVENTY_THIRTY_LOSER', 'LOSER_PAYS_DRINKS'))
- `match_cost_amount_check` (V7): CHECK ((cost_venue_amount IS NULL OR cost_venue_amount > 0) AND (cost_drinks_amount IS NULL OR cost_drinks_amount > 0))
- `match_deposit_check` (V8): CHECK ((cost_deposit_amount IS NULL OR cost_deposit_amount > 0) AND (cost_deposit_amount IS NOT NULL OR cost_deposit_holder IS NULL) AND (type = 'NEED_VENUE' OR cost_deposit_amount IS NULL))

V8 bỏ `confirmed_venue_id`, `confirmed_match_time` (V1, không dùng): kèo B khi ghép ghi thẳng `venue_id`, `match_start_time`, `match_end_time`, chi phí + cọc.

### 3.2 Bảng: match_venue_option

Nhiều lựa chọn địa điểm cho luồng B (1–5 mỗi kèo).

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK (sinh bằng `uuidv7()` trong câu INSERT gom lô) |
| match_id | UUID | NOT NULL | FK → match |
| venue_id | UUID | NOT NULL | FK → venue |
| display_order | INTEGER | NOT NULL | Thứ tự nhập 1..5 (V8) |
| created_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- `match_venue_option_match_venue_key` UNIQUE (match_id, venue_id) (V8) -- không trùng địa điểm; đích FK phương án của pairing
- `match_venue_option_match_order_key` UNIQUE (match_id, display_order) (V8)
- `idx_match_venue_option_venue` INDEX (venue_id) (V8, thay `idx_match_venue_option_match`)

**Foreign Keys:**
- FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE
- FOREIGN KEY (venue_id) REFERENCES venue(id) ON DELETE RESTRICT

**Constraints:**
- `match_venue_option_order_check` (V8): CHECK (display_order BETWEEN 1 AND 5)

### 3.3 Bảng: match_time_option

Nhiều lựa chọn giờ hẹn cho luồng B (1–5 mỗi kèo).

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK (sinh bằng `uuidv7()` trong câu INSERT gom lô) |
| match_id | UUID | NOT NULL | FK → match |
| proposed_start_time | TIMESTAMP | NOT NULL | Giờ bắt đầu đề xuất |
| proposed_end_time | TIMESTAMP | NOT NULL | Giờ kết thúc đề xuất |
| created_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- `match_time_option_match_id_key` UNIQUE (match_id, id) (V8) -- đích FK phương án của pairing
- `match_time_option_match_start_key` UNIQUE (match_id, proposed_start_time) (V8, thay `idx_match_time_option_match`) -- không trùng giờ bắt đầu

**Foreign Keys:**
- FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE

**Constraints:**
- `match_time_option_range_check` (V8): CHECK (proposed_end_time > proposed_start_time)

### 3.4 Bảng: cost_custom_item

Các khoản tự nhập (tiền trọng tài, phí áo đấu, ...) — thuộc kèo (luồng A; luồng B sau khi ghép, chép từ pairing) hoặc thuộc pairing (luồng B đang thương lượng) (V8).

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK (sinh bằng `uuidv7()` của PostgreSQL ≥ 18 trong câu INSERT gom lô) |
| match_id | UUID | NULL | FK → match (V8: bỏ NOT NULL) |
| pairing_id | UUID | NULL | FK → pairing (V8) |
| item_name | VARCHAR(100) | NOT NULL | Tên khoản phí |
| amount | BIGINT | NOT NULL | Số tiền (VND, > 0) |
| display_order | INTEGER | NOT NULL | Thứ tự hiển thị (> 0, V7) |
| created_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- `idx_cost_custom_item_order` UNIQUE (match_id, display_order) (V7, thay `idx_cost_custom_item_match`)
- `idx_cost_custom_item_pairing_order` UNIQUE (pairing_id, display_order) (V8)

**Foreign Keys:**
- FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE
- `cost_custom_item_pairing_fkey` FOREIGN KEY (pairing_id) REFERENCES pairing(id) ON DELETE CASCADE (V8)

**Constraints:**
- `cost_custom_item_display_order_check` (V7): CHECK (display_order > 0)
- `cost_custom_item_amount_check` (V7): CHECK (amount > 0)
- `cost_custom_item_owner_check` (V8): CHECK ((match_id IS NULL) <> (pairing_id IS NULL)) -- đúng một chủ sở hữu

### 3.5 Bảng: match_request

Yêu cầu bắt đối (luồng A).

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| match_id | UUID | NOT NULL | FK → match |
| requester_team_id | UUID | NOT NULL | FK → team, đội gửi yêu cầu |
| status | VARCHAR(20) | NOT NULL | PENDING, ACCEPTED, REJECTED, WITHDRAWN, SUPERSEDED, EXPIRED |
| reason | VARCHAR(500) | NULL | Lý do từ chối hoặc rút yêu cầu (V7) |
| requested_at | TIMESTAMP | NOT NULL | |
| responded_at | TIMESTAMP | NULL | Thời gian author phản hồi |
| withdrawn_at | TIMESTAMP | NULL | Thời gian requester rút |

**Indexes:**
- PRIMARY KEY (id)
- UNIQUE (match_id, requester_team_id) WHERE status NOT IN ('WITHDRAWN', 'EXPIRED') -- một đội chỉ một request active
- `idx_match_request_accepted` UNIQUE (match_id) WHERE status = 'ACCEPTED' (V7) -- tối đa 1 ACCEPTED mỗi kèo
- INDEX (match_id, status) -- query requests của Match
- INDEX (requester_team_id, status) -- query requests của đội

**Foreign Keys:**
- FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE
- FOREIGN KEY (requester_team_id) REFERENCES team(id) ON DELETE RESTRICT

**Constraints:**
- CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN', 'SUPERSEDED', 'EXPIRED'))
- CHECK ((status = 'REJECTED' AND responded_at IS NOT NULL) OR (status != 'REJECTED'))
- CHECK ((status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL) OR (status != 'WITHDRAWN'))

### 3.6 Bảng: pairing

Pairing cho luồng B (nhiều đội thương lượng đồng thời). Mỗi pairing mang một **gói**: phương án (1 lựa chọn địa điểm + 1 khung giờ của kèo) + chi phí + cọc; `revision` tăng mỗi lần sửa gói (V8). CAS bằng `status` + `revision`, không có cột `version`.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| match_id | UUID | NOT NULL | FK → match |
| interested_team_id | UUID | NOT NULL | FK → team, đội nhận bài |
| status | VARCHAR(20) | NOT NULL | NEGOTIATING, CONFIRMED, EXPIRED, SUPERSEDED |
| revision | INTEGER | NOT NULL | Phiên bản gói, default 0, ≥ 0 (V8) |
| proposed_venue_id | UUID | NULL | Phương án: địa điểm (thuộc lựa chọn của kèo) (V8) |
| proposed_time_option_id | UUID | NULL | Phương án: khung giờ của kèo (V8) |
| cost_type | VARCHAR(50) | NULL | `VENUE_ONLY`, `DRINKS_ONLY`, `BOTH` (V8) |
| cost_split | VARCHAR(50) | NULL | `FIFTY_FIFTY`, `SEVENTY_THIRTY_HOME`, `SEVENTY_THIRTY_LOSER`, `LOSER_PAYS_DRINKS` (V8) |
| cost_venue_amount | BIGINT | NULL | Tiền sân (VND, > 0) (V8) |
| cost_drinks_amount | BIGINT | NULL | Tiền nước (VND, > 0) (V8) |
| cost_deposit_amount | BIGINT | NULL | Tiền cọc (VND, > 0) (V8) |
| cost_deposit_holder | VARCHAR(100) | NULL | Ai giữ tiền cọc; chỉ khi có `cost_deposit_amount` (V8) |
| author_confirmed_at | TIMESTAMP | NULL | Đội tạo kèo xác nhận gói (xoá khi gói đổi) |
| interested_confirmed_at | TIMESTAMP | NULL | Đội nhận kèo xác nhận gói (xoá khi gói đổi) |
| final_confirmed_at | TIMESTAMP | NULL | Thời điểm hoàn tất (pairing CONFIRMED) |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | (V8) |

**Indexes:**
- PRIMARY KEY (id)
- `idx_pairing_active` UNIQUE (match_id, interested_team_id) WHERE status NOT IN ('EXPIRED', 'SUPERSEDED') -- một đội chỉ một pairing active
- `idx_pairing_match` INDEX (match_id, status)
- `idx_pairing_team` INDEX (interested_team_id, status)
- `idx_pairing_confirmed` UNIQUE (match_id) WHERE status = 'CONFIRMED' (V8, thay `idx_pairing_race`) -- tối đa một pairing CONFIRMED mỗi kèo

**Foreign Keys:**
- FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE
- FOREIGN KEY (interested_team_id) REFERENCES team(id) ON DELETE RESTRICT
- `pairing_proposed_venue_fkey` FOREIGN KEY (match_id, proposed_venue_id) REFERENCES match_venue_option(match_id, venue_id) (V8)
- `pairing_proposed_time_fkey` FOREIGN KEY (match_id, proposed_time_option_id) REFERENCES match_time_option(match_id, id) (V8)

**Constraints:**
- CHECK (status IN ('NEGOTIATING', 'CONFIRMED', 'EXPIRED', 'SUPERSEDED'))
- CHECK ((status = 'CONFIRMED' AND author_confirmed_at IS NOT NULL AND interested_confirmed_at IS NOT NULL AND final_confirmed_at IS NOT NULL) OR (status != 'CONFIRMED'))
- `pairing_proposal_pair_check` (V8): CHECK ((proposed_venue_id IS NULL) = (proposed_time_option_id IS NULL))
- `pairing_confirmed_proposal_check` (V8): CHECK (status <> 'CONFIRMED' OR proposed_venue_id IS NOT NULL)
- `pairing_revision_check` (V8): CHECK (revision >= 0)
- `pairing_cost_type_check`, `pairing_cost_split_check` (V8): giá trị như `match`
- `pairing_cost_amount_check` (V8): các số tiền NULL hoặc > 0; `cost_deposit_holder` chỉ khi có `cost_deposit_amount`

### 3.7 Bảng: sport

Danh mục bộ môn.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| name | VARCHAR(50) | NOT NULL | Tên bộ môn |
| is_active | BOOLEAN | NOT NULL | Admin bật/tắt, default TRUE |
| created_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- UNIQUE (name)

**Data ban đầu:**
```sql
INSERT INTO sport (name, is_active) VALUES ('Bóng đá', TRUE);
```

---

## 4. Rating & Report

### 4.1 Bảng: rating

Đánh giá sau trận (đội-đội, đội-mercenary, mercenary-đội).

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| match_id | UUID | NOT NULL | FK → match |
| rater_type | VARCHAR(20) | NOT NULL | TEAM, INDIVIDUAL |
| rater_id | UUID | NOT NULL | ID của team hoặc user đánh giá |
| ratee_type | VARCHAR(20) | NOT NULL | TEAM, INDIVIDUAL |
| ratee_id | UUID | NOT NULL | ID của team hoặc user được đánh giá |
| score | DECIMAL(2,1) | NOT NULL | Điểm 1.0 - 5.0 |
| comment | TEXT | NULL | Bình luận |
| match_score_home | INTEGER | NULL | Tỷ số (tùy chọn, chỉ lưu kỷ niệm) |
| match_score_away | INTEGER | NULL | |
| created_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- UNIQUE (match_id, rater_type, rater_id, ratee_type, ratee_id) -- chống đánh giá trùng
- INDEX (ratee_type, ratee_id) -- aggregate rating cho team/user

**Constraints:**
- CHECK (rater_type IN ('TEAM', 'INDIVIDUAL'))
- CHECK (ratee_type IN ('TEAM', 'INDIVIDUAL'))
- CHECK (score >= 1.0 AND score <= 5.0)
- CHECK (match_score_home IS NULL OR match_score_home >= 0)
- CHECK (match_score_away IS NULL OR match_score_away >= 0)

### 4.2 Bảng: report

Report hành vi xấu (4 loại).

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| match_id | UUID | NOT NULL | FK → match |
| reporter_type | VARCHAR(20) | NOT NULL | TEAM, INDIVIDUAL |
| reporter_id | UUID | NOT NULL | ID của team/user report |
| reported_type | VARCHAR(20) | NOT NULL | TEAM, INDIVIDUAL |
| reported_id | UUID | NOT NULL | ID của team/user bị report |
| report_type | VARCHAR(50) | NOT NULL | NOSHOW, BAD_PLAY, BAD_COMMUNICATION, SKILL_MISMATCH |
| reason | TEXT | NULL | Lý do |
| status | VARCHAR(20) | NOT NULL | PENDING, CONFIRMED, REJECTED |
| admin_reviewed_at | TIMESTAMP | NULL | Admin xem xét (chỉ cho tranh chấp bùng) |
| created_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (match_id)
- INDEX (reported_type, reported_id, report_type) -- đếm số lần bị report từng loại
- INDEX (status, created_at) -- admin queue

**Constraints:**
- CHECK (reporter_type IN ('TEAM', 'INDIVIDUAL'))
- CHECK (reported_type IN ('TEAM', 'INDIVIDUAL'))
- CHECK (report_type IN ('NOSHOW', 'BAD_PLAY', 'BAD_COMMUNICATION', 'SKILL_MISMATCH'))
- CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED'))

---

_(Tiếp tục: Venue, Mercenary, Chat & Notification)_

---

## 5. Venue

### 5.1 Bảng: venue

Địa điểm sân.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| name | VARCHAR(200) | NOT NULL | Tên địa điểm |
| address | TEXT | NOT NULL | Địa chỉ |
| latitude | DECIMAL(10,7) | NOT NULL | Vĩ độ |
| longitude | DECIMAL(10,7) | NOT NULL | Kinh độ |
| created_by_type | VARCHAR(20) | NOT NULL | ADMIN, USER |
| created_by_user_id | UUID | NULL | FK → user (nếu USER tạo) |
| ward_id | UUID | NULL | FK → administrative_unit (phường/xã, V5). Bắt buộc khi thêm / sửa qua API (BR-V11); NULL chỉ cho địa điểm cũ |
| phone | VARCHAR(20) | NULL | Số điện thoại |
| website | VARCHAR(500) | NULL | Website |
| venue_type | VARCHAR(50) | NULL | `FIVE_A_SIDE`, `SEVEN_A_SIDE`, `ELEVEN_A_SIDE` |
| status | VARCHAR(20) | NOT NULL | ACTIVE, INACTIVE |
| last_selected_at | TIMESTAMP | NULL | Lần chọn gần nhất (kèo, trận ngoài) — để cảnh báo 180 ngày |
| admin_verified_inactive_at | TIMESTAMP | NULL | Admin xác minh ngừng hoạt động |
| version | BIGINT | NOT NULL | V5, default 0. `@Version` cho sửa địa điểm / admin; ghi ảnh khoá dòng bằng `UPDATE … version + 1` (ADR-0003 Sửa đổi 2026-11-09) |
| stale_alerted_at | TIMESTAMP | NULL | V5. Cổng cảnh báo 180 ngày: job đặt khi cảnh báo; reset khi được chọn / kích hoạt lại |
| closure_alerted_at | TIMESTAMP | NULL | V5. Cổng cảnh báo report `CLOSED`: đặt ở report `CLOSED` đầu tiên của đợt; xoá khi hết report `CLOSED` `PENDING` hoặc kích hoạt lại |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (latitude, longitude, status) WHERE status = 'ACTIVE' -- tìm kiếm theo GPS
- INDEX (status, last_selected_at) -- scheduled job cảnh báo 180 ngày
- INDEX idx_venue_status_name (status, name, id) -- danh sách theo trạng thái, sort tên (V5)
- INDEX idx_venue_ward (ward_id) (V5)
- INDEX (created_by_user_id)

**Foreign Keys:**
- FOREIGN KEY (created_by_user_id) REFERENCES user(id) ON DELETE RESTRICT (V5 — trước là SET NULL; tài khoản bị xoá mềm nên tên người tạo vẫn tra được)
- FOREIGN KEY (ward_id) REFERENCES administrative_unit(id) ON DELETE RESTRICT (V5)

**Constraints:**
- CHECK (created_by_type IN ('ADMIN', 'USER'))
- CHECK (status IN ('ACTIVE', 'INACTIVE'))
- CHECK ((created_by_type = 'USER' AND created_by_user_id IS NOT NULL) OR (created_by_type = 'ADMIN'))
- CHECK venue_type_check (venue_type IS NULL OR venue_type IN ('FIVE_A_SIDE', 'SEVEN_A_SIDE', 'ELEVEN_A_SIDE')) (V5)
- CHECK venue_inactive_consistency_check ((status = 'INACTIVE' AND admin_verified_inactive_at IS NOT NULL) OR (status = 'ACTIVE' AND admin_verified_inactive_at IS NULL)) (V5)

### 5.2 Bảng: venue_image

Ảnh địa điểm (tối đa 5, có ảnh primary). Xoá ảnh là **hard-delete** (2.13); asset Cloudinary do job dọn xoá sau.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| venue_id | UUID | NOT NULL | FK → venue |
| image_url | VARCHAR(500) | NOT NULL | URL ảnh |
| public_id | VARCHAR(255) | NOT NULL | V5. Cloudinary public ID (`<folder>/venues/<venueId>/<signedAt>-<nonce>-<tag>`); dòng cũ = `legacy/<id>` |
| is_primary | BOOLEAN | NOT NULL | Ảnh đại diện chính, default FALSE |
| display_order | INTEGER | NOT NULL | Thứ tự hiển thị |
| uploaded_by | UUID | NULL | FK → user |
| created_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- UNIQUE (venue_id, is_primary) WHERE is_primary = TRUE -- chỉ một ảnh primary
- UNIQUE uq_venue_image_public_id (public_id) -- chống lưu trùng một asset (V5, `VENUE_006`)
- INDEX (venue_id, display_order)

**Foreign Keys:**
- FOREIGN KEY (venue_id) REFERENCES venue(id) ON DELETE CASCADE
- FOREIGN KEY (uploaded_by) REFERENCES user(id) ON DELETE SET NULL

**Constraints:**
- CHECK (display_order > 0)

### 5.3 Bảng: venue_report

Report địa điểm (đã đóng cửa / sai thông tin / khác) và kết quả admin xác minh.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| venue_id | UUID | NOT NULL | FK → venue |
| reporter_id | UUID | NOT NULL | FK → user |
| reason | VARCHAR(20) | NOT NULL | `CLOSED`, `WRONG_INFO`, `OTHER` (V5: trước là TEXT; mô tả cũ chuyển sang `detail`, `reason` = `OTHER`) |
| detail | TEXT | NULL | V5. Mô tả thêm (API giới hạn 1000 ký tự) |
| status | VARCHAR(20) | NOT NULL | V5. `PENDING` (default), `VERIFIED`, `REJECTED` |
| handled_by | UUID | NULL | V5. FK → user — admin chốt report |
| handled_at | TIMESTAMP | NULL | V5. Thời điểm chốt |
| created_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (venue_id, created_at DESC) -- admin xem danh sách report
- INDEX (reporter_id)
- INDEX idx_venue_report_status_created (status, created_at DESC) -- hàng chờ admin (V5)
- UNIQUE uq_venue_report_pending (reporter_id, venue_id, reason) WHERE status = 'PENDING' -- mỗi user tối đa 1 report chờ cho mỗi (địa điểm, lý do) (V5, `VENUE_014`)

**Foreign Keys:**
- FOREIGN KEY (venue_id) REFERENCES venue(id) ON DELETE CASCADE
- FOREIGN KEY (reporter_id) REFERENCES user(id) ON DELETE RESTRICT (V5 — trước là SET NULL)
- FOREIGN KEY (handled_by) REFERENCES user(id) ON DELETE RESTRICT (V5)

**Constraints:**
- CHECK venue_report_reason_check (reason IN ('CLOSED', 'WRONG_INFO', 'OTHER')) (V5)
- CHECK venue_report_status_check (status IN ('PENDING', 'VERIFIED', 'REJECTED')) (V5)
- CHECK venue_report_handled_consistency_check ((status = 'PENDING' AND handled_by IS NULL AND handled_at IS NULL) OR (status <> 'PENDING' AND handled_by IS NOT NULL AND handled_at IS NOT NULL)) (V5)

---

## 6. Mercenary

### 6.1 Bảng: mercenary_recruitment

Tin tuyển mercenary.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| match_id | UUID | NOT NULL | FK → match |
| team_id | UUID | NOT NULL | FK → team, đội tuyển |
| position | VARCHAR(50) | NOT NULL | Thủ môn, Hậu vệ, Tiền vệ, Tiền đạo |
| quantity_needed | INTEGER | NOT NULL | Số lượng cần tuyển |
| quantity_approved | INTEGER | NOT NULL | Số lượng đã duyệt, default 0 |
| status | VARCHAR(20) | NOT NULL | OPEN, FILLED, EXPIRED, CLOSED |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (match_id)
- INDEX (team_id, status)
- INDEX (status, position) -- tìm tin tuyển

**Foreign Keys:**
- FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE
- FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE RESTRICT

**Constraints:**
- CHECK (position IN ('Thủ môn', 'Hậu vệ', 'Tiền vệ', 'Tiền đạo'))
- CHECK (status IN ('OPEN', 'FILLED', 'EXPIRED', 'CLOSED'))
- CHECK (quantity_needed > 0)
- CHECK (quantity_approved >= 0 AND quantity_approved <= quantity_needed)

### 6.2 Bảng: mercenary_application

Đơn ứng tuyển mercenary.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| recruitment_id | UUID | NOT NULL | FK → mercenary_recruitment |
| applicant_id | UUID | NOT NULL | FK → user |
| status | VARCHAR(20) | NOT NULL | PENDING, APPROVED, REJECTED, WITHDRAWN |
| applied_at | TIMESTAMP | NOT NULL | |
| responded_at | TIMESTAMP | NULL | Đội trưởng phản hồi |
| withdrawn_at | TIMESTAMP | NULL | Ứng viên rút |

**Indexes:**
- PRIMARY KEY (id)
- UNIQUE (recruitment_id, applicant_id) WHERE status NOT IN ('WITHDRAWN', 'REJECTED') -- một người chỉ một đơn active
- INDEX (recruitment_id, status)
- INDEX (applicant_id, status)

**Foreign Keys:**
- FOREIGN KEY (recruitment_id) REFERENCES mercenary_recruitment(id) ON DELETE CASCADE
- FOREIGN KEY (applicant_id) REFERENCES user(id) ON DELETE RESTRICT

**Constraints:**
- CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
- CHECK ((status IN ('APPROVED', 'REJECTED') AND responded_at IS NOT NULL) OR (status NOT IN ('APPROVED', 'REJECTED')))
- CHECK ((status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL) OR (status != 'WITHDRAWN'))

---

## 7. Chat & Notification

### 7.1 Bảng: conversation & conversation_message (V7 — thay chat_message của V1)

Cuộc trò chuyện tổng quát giữa hai bên (`TEAM` hoặc `USER`) và tin nhắn của cuộc trò chuyện đó.

#### Bảng: `conversation`

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| context_type | VARCHAR(30) | NOT NULL | `TEAM_MATCH` ở V7 (V10 thêm `MERCENARY_APPLICATION`) |
| match_id | UUID | NULL | FK → match (`ON DELETE RESTRICT`); NOT NULL khi `context_type = 'TEAM_MATCH'` |
| party_a_type | VARCHAR(10) | NOT NULL | `TEAM`, `USER` |
| party_a_id | UUID | NOT NULL | Bên A (đội tạo kèo ở `TEAM_MATCH`) |
| party_b_type | VARCHAR(10) | NOT NULL | `TEAM`, `USER` |
| party_b_id | UUID | NOT NULL | Bên B (đội tham gia ở `TEAM_MATCH`) |
| created_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- `idx_conversation_team_match` UNIQUE (match_id, party_b_id) WHERE context_type = 'TEAM_MATCH'
- `idx_conversation_party_a` INDEX (party_a_id)
- `idx_conversation_party_b` INDEX (party_b_id)

**Foreign Keys:**
- FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE RESTRICT

**Constraints:**
- `conversation_context_type_check`: CHECK (context_type IN ('TEAM_MATCH'))
- `conversation_team_match_check`: CHECK (context_type <> 'TEAM_MATCH' OR (match_id IS NOT NULL AND party_a_type = 'TEAM' AND party_b_type = 'TEAM'))
- `conversation_parties_check`: CHECK (NOT (party_a_type = party_b_type AND party_a_id = party_b_id))

#### Bảng: `conversation_message`

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| conversation_id | UUID | NOT NULL | FK → conversation (`ON DELETE CASCADE`) |
| sender_party | VARCHAR(1) | NOT NULL | `A` hoặc `B` |
| sender_user_id | UUID | NOT NULL | FK → user (`ON DELETE RESTRICT`), người thực sự gửi |
| message | TEXT | NOT NULL | Nội dung tin nhắn (1..1000 ký tự) |
| is_read | BOOLEAN | NOT NULL | Đã đọc, default FALSE |
| created_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- `idx_conversation_message_time` INDEX (conversation_id, created_at, id) -- phân trang cursor
- `idx_conversation_message_unread` INDEX (conversation_id, sender_party) WHERE NOT is_read -- đánh dấu đã đọc & kiểm chưa đọc

**Foreign Keys:**
- FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE
- FOREIGN KEY (sender_user_id) REFERENCES "user"(id) ON DELETE RESTRICT

**Retention:** Xóa sau 90 ngày kể từ khi trận kết thúc (`COMPLETED`, `ARCHIVED`, `CANCELLED`, `EXPIRED`).

### 7.2 Bảng: notification

Thông báo in-app.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| recipient_user_id | UUID | NOT NULL | FK → user, người nhận |
| type | VARCHAR(50) | NOT NULL | Loại thông báo |
| title | VARCHAR(200) | NOT NULL | Tiêu đề |
| body | TEXT | NOT NULL | Nội dung |
| reference_type | VARCHAR(50) | NULL | MATCH, TEAM, USER, ... |
| reference_id | UUID | NULL | ID của đối tượng liên quan |
| is_read | BOOLEAN | NOT NULL | Đã đọc, default FALSE |
| created_at | TIMESTAMP | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (recipient_user_id, is_read, created_at DESC) -- query notification chưa đọc
- INDEX (created_at) -- scheduled job xóa sau 30 ngày

**Foreign Keys:**
- FOREIGN KEY (recipient_user_id) REFERENCES user(id) ON DELETE CASCADE

**Constraints:**
- CHECK (type IN ('MATCH_REQUEST_RECEIVED', 'REQUEST_ACCEPTED', 'REQUEST_REJECTED', 'MATCH_CONFIRMED', 'MATCH_CANCELLED', 'PAIRING_NEW', 'REMINDER_BEFORE_MATCH', 'MATCH_COMPLETED', 'RATING_RECEIVED', 'REPORT_RECEIVED', 'MERCENARY_APPLICATION', 'TEAM_INVITATION'))

**Retention:** Xóa sau 30 ngày.

---

## 8. Tổng kết

### 8.1 Tổng số bảng: 24 bảng

**User & Auth (3):** user, oauth_account, administrative_unit  
**Team (4):** team, team_member, team_tag, team_captain_history  
**Match & Pairing (8):** match, match_venue_option, match_time_option, cost_custom_item, match_request, pairing, sport  
**Rating & Report (2):** rating, report  
**Venue (3):** venue, venue_image, venue_report  
**Mercenary (2):** mercenary_recruitment, mercenary_application  
**Chat & Notification (3):** conversation, conversation_message, notification

### 8.2 Indexes quan trọng cho hiệu năng

Các indexes đã được thiết kế để hỗ trợ:
- Tìm kèo gần vị trí (toạ độ địa điểm của kèo A / lựa chọn địa điểm của kèo B) — hiện câu tìm chưa dùng index toạ độ (đọc mọi kèo OPEN rồi lọc hộp); tech-debt ở MATCH-001 `Processing_Flow.md` §9
- Scheduled jobs xử lý expiry, ARCHIVED, retention
- Query Match/Request/Pairing theo status
- Aggregate rating/report cho team/user
- Query danh sách có phân trang

### 8.3 Ràng buộc nghiệp vụ

- Tối đa một request ACCEPTED mỗi Match (unique index `idx_match_request_accepted WHERE status = 'ACCEPTED'`, V7)
- Tối đa một pairing CONFIRMED mỗi Match (unique index `idx_pairing_confirmed WHERE status = 'CONFIRMED'`, V8; thắng race = lần hoàn tất commit trước)
- Một đội chỉ một captain ACTIVE (unique constraint trên team_member)
- Tối đa 5 ảnh mỗi venue (xử lý ở application layer)
- Một venue chỉ một ảnh primary (unique constraint WHERE is_primary)
- Soft delete cho user (deleted_at)

### 8.4 Bước tiếp theo

1. **Rà soát với non-functional-standards.md:** Đảm bảo các truy vấn dự kiến đáp ứng ngưỡng 3/4/6 queries
2. **Viết migration Flyway:** Tạo các file V1__initial_schema.sql
3. **Tạo JPA entities:** Map từ schema sang Java entities
4. **Viết repositories:** Spring Data JPA repositories với custom queries cần thiết

