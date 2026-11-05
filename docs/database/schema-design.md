# Schema Database — ArenaFlow-be

> Thiết kế schema PostgreSQL chi tiết từ đặc tả nghiệp vụ trong `data-model.md`

> Cập nhật: 2026-11-05 — bảng `team`, `team_member` đồng bộ tới V4 (USER-002); các bảng khác vẫn theo V1 (vd. `user.role`/`ward_id`, bỏ `team_tag` ở V2 chưa phản ánh)

## Conventions

- **Tên bảng:** snake_case số ít (user, team, match)
- **Tên cột:** snake_case
- **ID:** UUID (Hibernate generates UUID v7 từ Java, không cần DEFAULT trong DB)
- **Timestamp:** TIMESTAMPTZ (UTC), có `created_at`, `updated_at`
- **Soft delete:** `deleted_at` TIMESTAMPTZ NULL
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
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật cuối |
| deleted_at | TIMESTAMPTZ | NULL | Thời gian soft delete |

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
| created_at | TIMESTAMPTZ | NOT NULL | |

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
| created_at | TIMESTAMPTZ | NOT NULL | |

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
| disbanded_at | TIMESTAMPTZ | NULL | Thời điểm giải tán (V4) |
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
| created_at | TIMESTAMPTZ | NOT NULL | |
| updated_at | TIMESTAMPTZ | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (created_by)
- `idx_team_status_created` INDEX (status, created_at DESC) (V2, thay `idx_team_status`) -- danh sách đội lọc status, sort mới nhất

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
| invited_at | TIMESTAMPTZ | NOT NULL | Thời gian mời |
| joined_at | TIMESTAMPTZ | NULL | Thời gian chấp nhận |
| left_at | TIMESTAMPTZ | NULL | Thời gian rời đội |
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
| created_at | TIMESTAMPTZ | NOT NULL | |

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
| transferred_at | TIMESTAMPTZ | NOT NULL | |

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
| format | VARCHAR(10) | NULL | 5v5, 7v7, 11v11 (chỉ hiển thị) |
| age_note | TEXT | NULL | Ghi chú tuổi (văn bản tự do) |
| match_nature | TEXT | NULL | Tính chất trận (văn bản tự do) |
| venue_id | UUID | NULL | FK → venue (chỉ cho luồng A) |
| match_start_time | TIMESTAMPTZ | NOT NULL | Giờ bắt đầu trận |
| match_end_time | TIMESTAMPTZ | NOT NULL | Giờ kết thúc dự kiến (để ARCHIVED/notification) |
| expires_at | TIMESTAMPTZ | NOT NULL | Thời hạn bài đăng, ≤ match_start_time |
| search_lat | DECIMAL(10,7) | NULL | Vĩ độ gốc cho gợi ý (luồng B) |
| search_lng | DECIMAL(10,7) | NULL | Kinh độ gốc cho gợi ý (luồng B) |
| search_radius_km | DECIMAL(5,2) | NULL | Bán kính tìm kiếm (default 3 km, luồng B) |
| cost_type | VARCHAR(50) | NULL | VENUE_ONLY, DRINKS_ONLY, BOTH, CUSTOM |
| cost_split | VARCHAR(50) | NULL | FIFTY_FIFTY, SEVENTY_THIRTY_HOME, SEVENTY_THIRTY_LOSER, LOSER_PAYS_DRINKS |
| cost_venue_amount | BIGINT | NULL | Tiền sân (VND, chỉ hiển thị) |
| cost_drinks_amount | BIGINT | NULL | Tiền nước (VND) |
| cost_deposit_holder | VARCHAR(100) | NULL | Ai giữ tiền cọc (luồng B, văn bản tự do) |
| confirmed_opponent_team_id | UUID | NULL | FK → team, đội đã ghép (khi CONFIRMED) |
| confirmed_venue_id | UUID | NULL | FK → venue, sân đã chốt (luồng B khi CONFIRMED) |
| confirmed_match_time | TIMESTAMPTZ | NULL | Giờ trận đã chốt (luồng B khi CONFIRMED) |
| cancelled_by_team_id | UUID | NULL | FK → team, đội hủy (khi CANCELLED) |
| cancelled_at | TIMESTAMPTZ | NULL | Thời gian hủy |
| created_at | TIMESTAMPTZ | NOT NULL | |
| updated_at | TIMESTAMPTZ | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (author_team_id, status) -- query Match của đội
- INDEX (status, expires_at) -- scheduled job xử lý expiry
- INDEX (status, match_end_time) -- scheduled job chuyển ARCHIVED
- INDEX (sport_id, status, match_start_time) -- tìm kèo theo môn
- INDEX (search_lat, search_lng) WHERE type = 'NEED_VENUE' AND status = 'OPEN' -- tìm kiếm theo GPS

**Foreign Keys:**
- FOREIGN KEY (author_team_id) REFERENCES team(id) ON DELETE RESTRICT
- FOREIGN KEY (sport_id) REFERENCES sport(id) ON DELETE RESTRICT
- FOREIGN KEY (venue_id) REFERENCES venue(id) ON DELETE RESTRICT
- FOREIGN KEY (confirmed_opponent_team_id) REFERENCES team(id) ON DELETE RESTRICT
- FOREIGN KEY (confirmed_venue_id) REFERENCES venue(id) ON DELETE RESTRICT
- FOREIGN KEY (cancelled_by_team_id) REFERENCES team(id) ON DELETE RESTRICT

**Constraints:**
- CHECK (type IN ('HAS_VENUE', 'NEED_VENUE'))
- CHECK (status IN ('OPEN', 'CONFIRMED', 'ARCHIVED', 'COMPLETED', 'EXPIRED', 'CANCELLED'))
- CHECK (expires_at <= match_start_time)
- CHECK (match_end_time > match_start_time)
- CHECK ((type = 'HAS_VENUE' AND venue_id IS NOT NULL) OR (type = 'NEED_VENUE' AND venue_id IS NULL))
- CHECK ((status = 'CONFIRMED' AND confirmed_opponent_team_id IS NOT NULL) OR (status != 'CONFIRMED'))
- CHECK ((status = 'CANCELLED' AND cancelled_by_team_id IS NOT NULL AND cancelled_at IS NOT NULL) OR (status != 'CANCELLED'))

### 3.2 Bảng: match_venue_option

Nhiều lựa chọn địa điểm cho luồng B.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| match_id | UUID | NOT NULL | FK → match |
| venue_id | UUID | NOT NULL | FK → venue |
| created_at | TIMESTAMPTZ | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (match_id)

**Foreign Keys:**
- FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE
- FOREIGN KEY (venue_id) REFERENCES venue(id) ON DELETE RESTRICT

### 3.3 Bảng: match_time_option

Nhiều lựa chọn giờ hẹn cho luồng B.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| match_id | UUID | NOT NULL | FK → match |
| proposed_start_time | TIMESTAMPTZ | NOT NULL | Giờ bắt đầu đề xuất |
| proposed_end_time | TIMESTAMPTZ | NOT NULL | Giờ kết thúc đề xuất |
| created_at | TIMESTAMPTZ | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (match_id)

**Foreign Keys:**
- FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE

### 3.4 Bảng: cost_custom_item

Các khoản tự nhập (tiền trọng tài, phí áo đấu, ...).

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| match_id | UUID | NOT NULL | FK → match |
| item_name | VARCHAR(100) | NOT NULL | Tên khoản phí |
| amount | BIGINT | NOT NULL | Số tiền (VND) |
| created_at | TIMESTAMPTZ | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (match_id)

**Foreign Keys:**
- FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE

### 3.5 Bảng: match_request

Yêu cầu bắt đối (luồng A).

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| match_id | UUID | NOT NULL | FK → match |
| requester_team_id | UUID | NOT NULL | FK → team, đội gửi yêu cầu |
| status | VARCHAR(20) | NOT NULL | PENDING, ACCEPTED, REJECTED, WITHDRAWN, SUPERSEDED, EXPIRED |
| requested_at | TIMESTAMPTZ | NOT NULL | |
| responded_at | TIMESTAMPTZ | NULL | Thời gian author phản hồi |
| withdrawn_at | TIMESTAMPTZ | NULL | Thời gian requester rút |

**Indexes:**
- PRIMARY KEY (id)
- UNIQUE (match_id, requester_team_id) WHERE status NOT IN ('WITHDRAWN', 'EXPIRED') -- một đội chỉ một request active
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

Pairing cho luồng B (nhiều đội thương lượng đồng thời).

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| match_id | UUID | NOT NULL | FK → match |
| interested_team_id | UUID | NOT NULL | FK → team, đội nhận bài |
| status | VARCHAR(20) | NOT NULL | NEGOTIATING, CONFIRMED, EXPIRED, SUPERSEDED |
| author_confirmed_at | TIMESTAMPTZ | NULL | Author xác nhận |
| interested_confirmed_at | TIMESTAMPTZ | NULL | Đội nhận xác nhận |
| final_confirmed_at | TIMESTAMPTZ | NULL | Timestamp xác nhận cuối (để xử lý race condition) |
| created_at | TIMESTAMPTZ | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- UNIQUE (match_id, interested_team_id) WHERE status NOT IN ('EXPIRED', 'SUPERSEDED') -- một đội chỉ một pairing active
- INDEX (match_id, status)
- INDEX (interested_team_id, status)
- INDEX (match_id, final_confirmed_at) WHERE status = 'CONFIRMED' -- tìm pairing thắng race condition

**Foreign Keys:**
- FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE
- FOREIGN KEY (interested_team_id) REFERENCES team(id) ON DELETE RESTRICT

**Constraints:**
- CHECK (status IN ('NEGOTIATING', 'CONFIRMED', 'EXPIRED', 'SUPERSEDED'))
- CHECK ((status = 'CONFIRMED' AND author_confirmed_at IS NOT NULL AND interested_confirmed_at IS NOT NULL AND final_confirmed_at IS NOT NULL) OR (status != 'CONFIRMED'))

### 3.7 Bảng: sport

Danh mục bộ môn.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| name | VARCHAR(50) | NOT NULL | Tên bộ môn |
| is_active | BOOLEAN | NOT NULL | Admin bật/tắt, default TRUE |
| created_at | TIMESTAMPTZ | NOT NULL | |

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
| created_at | TIMESTAMPTZ | NOT NULL | |

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
| admin_reviewed_at | TIMESTAMPTZ | NULL | Admin xem xét (chỉ cho tranh chấp bùng) |
| created_at | TIMESTAMPTZ | NOT NULL | |

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
| phone | VARCHAR(20) | NULL | Số điện thoại |
| website | VARCHAR(500) | NULL | Website |
| venue_type | VARCHAR(50) | NULL | Sân 5, sân 7, sân 11 |
| status | VARCHAR(20) | NOT NULL | ACTIVE, INACTIVE |
| last_selected_at | TIMESTAMPTZ | NULL | Lần chọn gần nhất (để cảnh báo 180 ngày) |
| admin_verified_inactive_at | TIMESTAMPTZ | NULL | Admin xác minh ngừng hoạt động |
| created_at | TIMESTAMPTZ | NOT NULL | |
| updated_at | TIMESTAMPTZ | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (latitude, longitude, status) WHERE status = 'ACTIVE' -- tìm kiếm theo GPS
- INDEX (status, last_selected_at) -- scheduled job cảnh báo 180 ngày
- INDEX (created_by_user_id)

**Foreign Keys:**
- FOREIGN KEY (created_by_user_id) REFERENCES user(id) ON DELETE SET NULL

**Constraints:**
- CHECK (created_by_type IN ('ADMIN', 'USER'))
- CHECK (status IN ('ACTIVE', 'INACTIVE'))
- CHECK ((created_by_type = 'USER' AND created_by_user_id IS NOT NULL) OR (created_by_type = 'ADMIN'))

### 5.2 Bảng: venue_image

Ảnh địa điểm (tối đa 5, có ảnh primary).

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| venue_id | UUID | NOT NULL | FK → venue |
| image_url | VARCHAR(500) | NOT NULL | URL ảnh |
| is_primary | BOOLEAN | NOT NULL | Ảnh đại diện chính, default FALSE |
| display_order | INTEGER | NOT NULL | Thứ tự hiển thị |
| uploaded_by | UUID | NULL | FK → user |
| created_at | TIMESTAMPTZ | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- UNIQUE (venue_id, is_primary) WHERE is_primary = TRUE -- chỉ một ảnh primary
- INDEX (venue_id, display_order)

**Foreign Keys:**
- FOREIGN KEY (venue_id) REFERENCES venue(id) ON DELETE CASCADE
- FOREIGN KEY (uploaded_by) REFERENCES user(id) ON DELETE SET NULL

**Constraints:**
- CHECK (display_order > 0)

### 5.3 Bảng: venue_report

Report địa điểm ngừng hoạt động.

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| venue_id | UUID | NOT NULL | FK → venue |
| reporter_id | UUID | NOT NULL | FK → user |
| reason | TEXT | NOT NULL | Lý do báo cáo |
| created_at | TIMESTAMPTZ | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (venue_id, created_at DESC) -- admin xem danh sách report
- INDEX (reporter_id)

**Foreign Keys:**
- FOREIGN KEY (venue_id) REFERENCES venue(id) ON DELETE CASCADE
- FOREIGN KEY (reporter_id) REFERENCES user(id) ON DELETE SET NULL

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
| created_at | TIMESTAMPTZ | NOT NULL | |
| updated_at | TIMESTAMPTZ | NOT NULL | |

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
| applied_at | TIMESTAMPTZ | NOT NULL | |
| responded_at | TIMESTAMPTZ | NULL | Đội trưởng phản hồi |
| withdrawn_at | TIMESTAMPTZ | NULL | Ứng viên rút |

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

### 7.1 Bảng: chat_message

Chat giữa hai đội trưởng (luồng B pairing).

| Cột | Kiểu | Nullable | Mô tả |
|---|---|---|---|
| id | UUID | NOT NULL | PK |
| pairing_id | UUID | NOT NULL | FK → pairing |
| sender_user_id | UUID | NOT NULL | FK → user, đội trưởng gửi |
| message | TEXT | NOT NULL | Nội dung tin nhắn |
| created_at | TIMESTAMPTZ | NOT NULL | |

**Indexes:**
- PRIMARY KEY (id)
- INDEX (pairing_id, created_at) -- query chat theo thời gian
- INDEX (created_at) -- scheduled job xóa sau 90 ngày

**Foreign Keys:**
- FOREIGN KEY (pairing_id) REFERENCES pairing(id) ON DELETE CASCADE
- FOREIGN KEY (sender_user_id) REFERENCES user(id) ON DELETE SET NULL

**Retention:** Xóa sau 90 ngày kể từ khi Match chuyển COMPLETED, ARCHIVED hoặc CANCELLED.

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
| created_at | TIMESTAMPTZ | NOT NULL | |

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
**Chat & Notification (2):** chat_message, notification

### 8.2 Indexes quan trọng cho hiệu năng

Các indexes đã được thiết kế để hỗ trợ:
- Tìm kiếm Match theo GPS (lat/lng)
- Scheduled jobs xử lý expiry, ARCHIVED, retention
- Query Match/Request/Pairing theo status
- Aggregate rating/report cho team/user
- Query danh sách có phân trang

### 8.3 Ràng buộc nghiệp vụ

- Tối đa một request ACCEPTED mỗi Match (xử lý ở application layer)
- Tối đa một pairing CONFIRMED mỗi Match (race condition xử lý bằng final_confirmed_at)
- Một đội chỉ một captain ACTIVE (unique constraint trên team_member)
- Tối đa 5 ảnh mỗi venue (xử lý ở application layer)
- Một venue chỉ một ảnh primary (unique constraint WHERE is_primary)
- Soft delete cho user (deleted_at)

### 8.4 Bước tiếp theo

1. **Rà soát với non-functional-standards.md:** Đảm bảo các truy vấn dự kiến đáp ứng ngưỡng 3/4/6 queries
2. **Viết migration Flyway:** Tạo các file V1__initial_schema.sql
3. **Tạo JPA entities:** Map từ schema sang Java entities
4. **Viết repositories:** Spring Data JPA repositories với custom queries cần thiết

